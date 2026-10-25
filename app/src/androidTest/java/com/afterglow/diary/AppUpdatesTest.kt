package com.afterglow.diary

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppUpdatesTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private suspend fun AppUpdateController.settled() = withTimeout(30_000) { state.first { !it.busy } }
    private fun release(file: File, version: String = "1.6.1") = AppRelease(AppVersion.parse(version),
        "https://github.com/KTihana/dream-diary/releases/download/v$version/Dream-Diary-$version.apk", file.length(), file.sha256())
    private class FixtureSource(val file: File, var pause: Boolean = false) : UpdateSource {
        var unavailable = false
        var release = AppRelease(AppVersion.parse("1.6.1"), "https://github.com/KTihana/dream-diary/releases/download/v1.6.1/Dream-Diary-1.6.1.apk", file.length(), file.sha256())
        override suspend fun latest(): AppRelease {
            if (unavailable) throw java.net.UnknownHostException("Test connection failure")
            return release
        }
        override suspend fun download(release: AppRelease, destination: File, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
            if (pause) { destination.writeBytes(byteArrayOf(1, 2)); progress(2); awaitCancellation() }
            file.copyTo(destination, overwrite = true)
            progress(destination.length())
        }
    }

    @Test fun settingsOpensUpdatesAndChecksThePublicRelease() {
        app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(20_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithContentDescription("Notebook options").performClick()
        app.onNodeWithText("Settings").performClick()
        app.onNodeWithTag("settings-updates").performScrollTo().performClick()
        app.onNodeWithTag("app-updates").assertExists()
        app.waitUntil(30_000) { app.onAllNodesWithTag("check-updates").fetchSemanticsNodes().isNotEmpty() }
        capture("updates-portrait-1.6.0.png")
        app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        capture("updates-landscape-1.6.0.png")
        app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        if (InstrumentationRegistry.getArguments().getString("liveUpdateCheck") == "true") {
            app.onNodeWithTag("check-updates").performClick()
            app.waitUntil(35_000) {
                val phase = ViewModelProvider(app.activity)[DiaryViewModel::class.java].updates.state.value.phase
                phase == UpdatePhase.Current || phase == UpdatePhase.Failed
            }
            app.onNodeWithText("You’re up to date").assertExists()
            capture("updates-current-1.6.0.png")
            if (InstrumentationRegistry.getArguments().getString("liveDownloadCheck") == "true") runBlocking {
                val source = GitHubUpdateSource()
                val release = source.latest()
                val downloaded = File(context.cacheDir, "live-update-download.apk")
                try {
                    var progress = 0L
                    source.download(release, downloaded) { progress = it }
                    assertEquals(release.bytes, progress)
                    assertEquals(release.bytes, downloaded.length())
                    assertEquals(release.sha256, downloaded.sha256())
                } finally { downloaded.delete() }
            }
        }
        app.onNodeWithContentDescription("Back to settings").performClick()
        app.onNodeWithTag("notebook-settings").assertExists()
    }

    @Test fun downloadCancelVerifyRestoreAndInstallerGrantKeepTheNotebookPrivate() = runBlocking {
        val fixture = File(context.filesDir, "update-fixture.apk")
        assumeTrue("Supply the signed next-version fixture described in DEVELOPMENT.md", fixture.isFile)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val source = FixtureSource(fixture, pause = true)
        val diary = File(context.filesDir, "dreams.json")
        app.waitUntil(20_000) { ViewModelProvider(app.activity)[DiaryViewModel::class.java].state.value.saveStatus == SaveStatus.Saved }
        val before = diary.readBytes()
        try {
            File(context.cacheDir, "app-updates").deleteRecursively()
            File(context.filesDir, "app-update.json").delete()
            val controller = AppUpdateController(context, scope, source)
            controller.settled()
            source.unavailable = true
            controller.check()
            assertEquals(UpdatePhase.Failed, controller.settled().phase)
            assertTrue(controller.state.value.error!!.contains("connection"))
            source.unavailable = false
            controller.check()
            assertEquals(UpdatePhase.Available, controller.settled().phase)
            controller.download()
            withTimeout(10_000) { controller.state.first { it.downloaded > 0 } }
            controller.cancel()
            assertEquals(UpdatePhase.Available, controller.settled().phase)
            assertTrue(File(context.cacheDir, "app-updates").listFiles().orEmpty().none { it.extension == "part" })
            source.pause = false
            controller.download()
            assertEquals(UpdatePhase.Ready, controller.settled().phase)
            val restored = AppUpdateController(context, restoredScope, source)
            assertEquals(UpdatePhase.Ready, restored.settled().phase)
            val intent = restored.installerIntent()!!
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content", intent.data!!.scheme)
            assertEquals("${context.packageName}.updates", intent.data!!.authority)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            assertEquals(fixture.length(), context.contentResolver.openAssetFileDescriptor(intent.data!!, "r")!!.use { it.length })
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, "${context.packageName}.updates", diary) }
            if (InstrumentationRegistry.getArguments().getString("openUpdateInstaller") == "true") {
                app.activityRule.scenario.onActivity { it.startActivity(intent) }
                val end = android.os.SystemClock.uptimeMillis() + 15_000
                var installer = false
                while (!installer && android.os.SystemClock.uptimeMillis() < end) {
                    val pkg = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
                    installer = pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")
                    if (!installer) delay(100)
                }
                assertTrue("Android must open its installation confirmation", installer)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            File(context.cacheDir, "app-updates/update.apk").appendBytes(byteArrayOf(0))
            assertNull(restored.installerIntent())
            assertEquals(UpdatePhase.Failed, restored.state.value.phase)
            assertArrayEquals(before, diary.readBytes())
        } finally {
            scope.cancel(); restoredScope.cancel()
            File(context.cacheDir, "app-updates").deleteRecursively()
            File(context.filesDir, "app-update.json").delete()
        }
    }

    @Test fun rejectsOldVersionsDifferentPackagesAndDifferentSigningKeys() {
        val fixture = File(context.filesDir, "update-fixture.apk")
        val wrongKey = File(context.filesDir, "update-wrong-signature.apk")
        assumeTrue(fixture.isFile && wrongKey.isFile)
        val verifier = UpdateApkVerifier(context)
        verifier.verify(fixture, release(fixture))
        val own = File(context.applicationInfo.sourceDir)
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(own, release(own, verifier.installedVersion)) }
        val otherPackage = File(instrumentation.context.applicationInfo.sourceDir)
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(otherPackage, release(otherPackage)) }
        val error = assertThrows(IllegalArgumentException::class.java) { verifier.verify(wrongKey, release(wrongKey)) }
        assertTrue(error.message!!.contains("signing key"))
    }

    /** An older-version build of this updater exercises the real published APK without creating a fake release. */
    @Test fun realUpdateUiReachesPermissionSettingsAndInstaller() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("oldUpdaterFlow") == "true")
        app.waitUntil(20_000) { app.onAllNodesWithTag("dream-title").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithContentDescription("Notebook options").performClick()
        app.onNodeWithText("Settings").performClick()
        app.onNodeWithTag("settings-updates").performScrollTo().performClick()
        app.waitUntil(30_000) { app.onAllNodesWithTag("check-updates").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("check-updates").performClick()
        app.waitUntil(35_000) { app.onAllNodesWithTag("download-update").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("download-update").performClick()
        app.waitUntil(90_000) { app.onAllNodesWithTag("install-update").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithText("Ready to install").assertExists()
        if (!context.packageManager.canRequestPackageInstalls()) {
            app.onNodeWithTag("install-update").performClick()
            waitForSystemPackage("settings")
            // Test-emulator grant, while Android's permission page is open; the real app never grants this itself.
            instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            app.waitUntil(10_000) { app.onAllNodesWithText("Install update").fetchSemanticsNodes().isNotEmpty() }
        }
        app.onNodeWithTag("install-update").performClick()
        waitForSystemPackage("packageinstaller", "permissioncontroller")
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        app.waitUntil(10_000) { app.onAllNodesWithText("Ready to install").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForSystemPackage(vararg names: String) {
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < end) {
            val pkg = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
            if (names.any { pkg.contains(it) }) return
            android.os.SystemClock.sleep(100)
        }
        fail("Android did not open ${names.joinToString()}")
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureUpdates") != "true") return
        app.waitForIdle()
        val file = File(context.getExternalFilesDir("screenshots"), name)
        file.parentFile!!.mkdirs()
        file.outputStream().use { app.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
