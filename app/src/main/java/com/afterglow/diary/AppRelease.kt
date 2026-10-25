package com.afterglow.diary

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.io.File

internal const val RELEASE_API = "https://api.github.com/repos/KTihana/dream-diary/releases/latest"
internal const val RELEASE_PAGE = "https://github.com/KTihana/dream-diary/releases/latest"
internal const val MAX_UPDATE_BYTES = 150L * 1024 * 1024

internal data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(text: String): AppVersion {
            val match = Regex("v?(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})").matchEntire(text)
                ?: throw IllegalArgumentException("This release uses an unsupported version number.")
            return AppVersion(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
    }
}

internal data class AppRelease(val version: AppVersion, val url: String, val bytes: Long, val sha256: String) {
    fun json() = JSONObject().put("version", version.toString()).put("url", url).put("bytes", bytes).put("sha256", sha256)
    companion object {
        fun latest(json: String): AppRelease {
            val root = JSONObject(json)
            require(!root.getBoolean("draft") && !root.getBoolean("prerelease")) { "A stable update is not available yet." }
            val tag = root.getString("tag_name")
            val version = AppVersion.parse(tag)
            require(tag == "v$version") { "The release tag is not a supported Dream Diary version." }
            val name = "Dream-Diary-$version.apk"
            val assets = root.getJSONArray("assets")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name") == name }
            require(matches.size == 1) { "The update is still being published. Please check again shortly." }
            val asset = matches.single()
            require(asset.optString("state") == "uploaded") { "The update is still being published. Please check again shortly." }
            val digest = asset.optString("digest")
            require(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "This release does not include a verified download checksum yet." }
            return validated(version, asset.getString("browser_download_url"), asset.getLong("size"), digest.substring(7).lowercase(), tag)
        }
        fun restored(json: String): AppRelease {
            val root = JSONObject(json)
            val version = AppVersion.parse(root.getString("version"))
            return validated(version, root.getString("url"), root.getLong("bytes"), root.getString("sha256"), "v$version")
        }
        private fun validated(version: AppVersion, url: String, bytes: Long, hash: String, tag: String): AppRelease {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath == "/KTihana/dream-diary/releases/download/$tag/Dream-Diary-$version.apk") { "The update download is not from Dream Diary’s GitHub repository." }
            require(bytes in 1..MAX_UPDATE_BYTES) { "The update has an unexpected file size." }
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "The update checksum is invalid." }
            return AppRelease(version, url, bytes, hash)
        }
    }
}

internal fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
