package com.afterglow.diary

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Dedicated subclass also supports Android vendors that cannot instantiate FileProvider directly. */
class UpdateFileProvider : FileProvider()

internal interface UpdateSource {
    suspend fun latest(): AppRelease
    suspend fun download(release: AppRelease, destination: File, progress: (Long) -> Unit)
}

/** Public GitHub requests only. No notebook, account, or device identifiers are sent. */
internal class GitHubUpdateSource : UpdateSource {
    override suspend fun latest(): AppRelease = withContext(Dispatchers.IO) {
        val connection = connect(RELEASE_API)
        try {
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1_000_000) { "The release information is unexpectedly large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            ensureActive()
            AppRelease.latest(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    override suspend fun download(release: AppRelease, destination: File, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val connection = connect(release.url)
        try {
            val length = connection.contentLengthLong
            require(length == -1L || length == release.bytes) { "The download size does not match this release. Please check again." }
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.bytes && total <= MAX_UPDATE_BYTES) { "The download is larger than expected." }
                        output.write(buffer, 0, count)
                        progress(total)
                    }
                    require(total == release.bytes) { "The download was interrupted. Please try again." }
                    output.fd.sync()
                }
            }
        } finally { connection.disconnect() }
    }
    private fun connect(address: String): HttpsURLConnection {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null && url.port in setOf(-1, 443) &&
                url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                "The update was redirected outside GitHub. Please try again later."
            }
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Dream-Diary-Android-Updater")
                setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            }
            val status = try { connection.responseCode } catch (error: Exception) { connection.disconnect(); throw error }
            if (status == HttpURLConnection.HTTP_OK) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (status in listOf(301, 302, 303, 307, 308) && location != null) url = URL(url, location)
            else throw IOException(when (status) {
                403, 429 -> "GitHub is limiting update requests. Please try again later."
                404 -> "The update is not available yet. Please check again shortly."
                else -> "GitHub couldn’t finish the request. Please try again."
            })
        }
        throw IOException("The update download was redirected too many times.")
    }
}

@Suppress("DEPRECATION")
internal class UpdateApkVerifier(private val context: Context) {
    private val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    private val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    val installedVersion: String = installed.versionName ?: "Unknown"
    private fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun signers(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { it.toCharsString() }?.toSet().orEmpty()

    fun verify(file: File, release: AppRelease) {
        require(file.isFile && file.length() == release.bytes && file.sha256() == release.sha256) {
            "The update file did not pass its checksum check. Download it again."
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("Android couldn’t read this update. Please download it again.")
        require(archive.packageName == context.packageName) { "This file is not a Dream Diary update." }
        require(signers(installed).isNotEmpty() && signers(archive) == signers(installed)) { "This update uses a different signing key and cannot replace your app." }
        require(code(archive) > code(installed) && release.version > AppVersion.parse(installedVersion)) { "This update is not newer than the installed app." }
        require(archive.versionName == release.version.toString()) { "The downloaded app version does not match the release." }
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "This update needs a newer Android version." }
    }
}

internal enum class UpdatePhase { Idle, Checking, Current, Available, Downloading, Verifying, Ready, Cancelling, Failed }
internal data class AppUpdateState(val phase: UpdatePhase = UpdatePhase.Idle, val release: AppRelease? = null,
    val downloaded: Long = 0, val error: String? = null) {
    val busy: Boolean get() = phase in setOf(UpdatePhase.Checking, UpdatePhase.Downloading, UpdatePhase.Verifying, UpdatePhase.Cancelling)
}

/** Scope belongs to the diary ViewModel, so rotating or closing the panel does not lose a download. */
internal class AppUpdateController(context: Context, private val scope: CoroutineScope, private val source: UpdateSource = GitHubUpdateSource()) {
    private val app = context.applicationContext
    private val verifier = UpdateApkVerifier(app)
    val installedVersion get() = verifier.installedVersion
    private val directory = File(app.cacheDir, "app-updates")
    private val apk = File(directory, "update.apk")
    private val metadata = android.util.AtomicFile(File(app.filesDir, "app-update.json"))
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        mutableState.value = AppUpdateState(UpdatePhase.Verifying)
        job = scope.launch {
            val restored = withContext(Dispatchers.IO) {
                directory.mkdirs()
                directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
                runCatching {
                    val release = AppRelease.restored(metadata.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                    verifier.verify(apk, release)
                    release
                }.getOrElse { apk.delete(); metadata.delete(); null }
            }
            mutableState.value = if (restored == null) AppUpdateState() else AppUpdateState(UpdatePhase.Ready, restored)
        }
    }

    fun check() {
        if (state.value.busy) return
        mutableState.value = AppUpdateState(UpdatePhase.Checking)
        job = scope.launch {
            try {
                val release = source.latest()
                mutableState.value = AppUpdateState(if (release.version > AppVersion.parse(installedVersion)) UpdatePhase.Available else UpdatePhase.Current, release)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = AppUpdateState(UpdatePhase.Failed, error = message(error)) }
        }
    }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.busy) return
        mutableState.value = AppUpdateState(UpdatePhase.Downloading, release)
        job = scope.launch {
            // A cancelled request from an earlier Activity must not delete a later request's file.
            val part = File(directory, "${java.util.UUID.randomUUID()}.part")
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    require(directory.usableSpace > release.bytes * 2 + 5 * 1024 * 1024) { "There isn’t enough free space to download the update." }
                    source.download(release, part) { total ->
                        if (mutableState.value.phase != UpdatePhase.Cancelling) mutableState.value = AppUpdateState(UpdatePhase.Downloading, release, total)
                    }
                    ensureActive()
                    mutableState.value = AppUpdateState(UpdatePhase.Verifying, release)
                    verifier.verify(part, release)
                    ensureActive()
                    check(part.renameTo(apk)) { "The downloaded update couldn’t be saved. Please try again." }
                    val stream = metadata.startWrite()
                    try { stream.write(release.json().toString().toByteArray()); metadata.finishWrite(stream) }
                    catch (error: Exception) { metadata.failWrite(stream); throw error }
                }
                mutableState.value = AppUpdateState(UpdatePhase.Ready, release)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = AppUpdateState(UpdatePhase.Failed, release, error = message(error)) }
            finally { withContext(NonCancellable + Dispatchers.IO) { part.delete() } }
        }
    }

    fun cancel() {
        val running = job ?: return
        if (!state.value.busy || state.value.phase == UpdatePhase.Cancelling) return
        val release = state.value.release
        mutableState.value = state.value.copy(phase = UpdatePhase.Cancelling)
        scope.launch {
            running.cancelAndJoin()
            mutableState.value = AppUpdateState(if (release == null) UpdatePhase.Idle else UpdatePhase.Available, release)
        }
    }

    suspend fun installerIntent(): Intent? {
        val release = state.value.release ?: return null
        if (state.value.phase != UpdatePhase.Ready) return null
        mutableState.value = state.value.copy(phase = UpdatePhase.Verifying, error = null)
        return try {
            withContext(Dispatchers.IO) { verifier.verify(apk, release) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
            mutableState.value = AppUpdateState(UpdatePhase.Ready, release)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Dream Diary update", uri) }
        } catch (cancelled: CancellationException) {
            mutableState.value = AppUpdateState(UpdatePhase.Ready, release)
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = AppUpdateState(UpdatePhase.Failed, release, error = message(error)); null
        }
    }

    fun installerUnavailable() { mutableState.value = state.value.copy(error = "Android couldn’t open the installer. You can try again or open the GitHub release.") }
    private fun message(error: Exception): String = when (error) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> "Couldn’t connect to GitHub. Check your connection and try again."
        is IllegalArgumentException, is IllegalStateException, is IOException -> error.message ?: "The update couldn’t finish. Please try again."
        else -> "The update couldn’t be read. Please check again later."
    }
}
