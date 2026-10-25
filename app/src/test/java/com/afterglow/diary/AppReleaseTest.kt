package com.afterglow.diary

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun release() = JSONObject().put("tag_name", "v1.10.0").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "Dream-Diary-1.10.0.apk").put("state", "uploaded")
            .put("size", 1234).put("digest", "sha256:" + "a".repeat(64))
            .put("browser_download_url", "https://github.com/KTihana/dream-diary/releases/download/v1.10.0/Dream-Diary-1.10.0.apk")))
    private fun rejected(root: JSONObject) { assertThrows(IllegalArgumentException::class.java) { AppRelease.latest(root.toString()) } }

    @Test fun versionsCompareNumericallyAndRoundTrip() {
        assertTrue(AppVersion.parse("v1.10.0") > AppVersion.parse("1.9.99"))
        assertTrue(AppVersion.parse("2.0.0") > AppVersion.parse("1.99.99"))
        val parsed = AppRelease.latest(release().toString())
        assertEquals(parsed, AppRelease.restored(parsed.json().toString()))
    }
    @Test fun rejectsPreviewAndMalformedVersions() {
        listOf("1.6.0-beta", "1.6", "01.6.0", "v9999999.0.0", "1.-2.3", "1.2.3/path").forEach {
            assertThrows(IllegalArgumentException::class.java) { AppVersion.parse(it) }
        }
        rejected(release().put("draft", true))
        rejected(release().put("prerelease", true))
    }
    @Test fun requiresExactlyOneFinishedApkAndItsChecksum() {
        rejected(release().put("assets", JSONArray()))
        val duplicated = release(); duplicated.getJSONArray("assets").put(duplicated.getJSONArray("assets").getJSONObject(0)); rejected(duplicated)
        listOf("", "sha256:1234", "md5:" + "a".repeat(64)).forEach { hash ->
            val root = release(); root.getJSONArray("assets").getJSONObject(0).put("digest", hash); rejected(root)
        }
        val pending = release(); pending.getJSONArray("assets").getJSONObject(0).put("state", "new"); rejected(pending)
    }
    @Test fun refusesOtherRepositoriesHostsCredentialsAndUrlTricks() {
        val original = release().getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        listOf(original.replace("https:", "http:"), original.replace("github.com/", "github.com.evil.test/"),
            original.replace("KTihana", "SomeoneElse"), original.replace("github.com", "user@github.com"),
            original + "?download=another", original + "#fragment", original.replace("v1.10.0/", "v2.0.0/"),
            original.replace("/KTihana/", "/x/../KTihana/")).forEach { url ->
            val root = release(); root.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url); rejected(root)
        }
    }
    @Test fun boundsDownloadsToTheDeclaredSize() {
        listOf(0L, -1L, MAX_UPDATE_BYTES + 1).forEach { size ->
            val root = release(); root.getJSONArray("assets").getJSONObject(0).put("size", size); rejected(root)
        }
    }
}
