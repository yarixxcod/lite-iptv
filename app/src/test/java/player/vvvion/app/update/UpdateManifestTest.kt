package player.vvvion.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun acceptsNewerHttpsManifest() {
        val raw = """
            {"versionCode":3,"versionName":"1.2.0","apkUrl":"https://github.com/yarixxcod/lite-iptv/releases/latest/download/VvvionIPTV.apk","notes":"Guide fix"}
        """.trimIndent()
        val update = parseUpdateManifest(raw, 2)!!
        assertEquals(3, update.versionCode)
        assertEquals("1.2.0", update.versionName)
        assertEquals("Guide fix", update.notes)
    }

    @Test
    fun ignoresCurrentOrOlderAndUnsafeUrls() {
        val same = """{"versionCode":2,"versionName":"1.1.0","apkUrl":"https://example/a.apk","notes":""}"""
        assertNull(parseUpdateManifest(same, 2))
        val clear = """{"versionCode":9,"versionName":"9.0.0","apkUrl":"http://example/a.apk","notes":""}"""
        assertNull(parseUpdateManifest(clear, 1))
        assertNull(parseUpdateManifest("not-json", 1))
    }

    @Test
    fun fallsBackFromJsdelivrPanel() {
        val github = UpdateRepository.DEFAULT_MANIFEST
        assertEquals(github, UpdateRepository.resolveManifest(""))
        assertEquals(github, UpdateRepository.resolveManifest("  "))
        assertEquals(
            github,
            UpdateRepository.resolveManifest("https://cdn.jsdelivr.net/gh/yarixxcod/lite-iptv@main/docs/"),
        )
        assertEquals(
            github,
            UpdateRepository.resolveManifest("https://cdn.jsdelivr.net/gh/yarixxcod/lite-iptv@main/docs/index.html"),
        )
        assertEquals(
            "https://example.com/latest.json",
            UpdateRepository.resolveManifest("https://example.com/latest.json"),
        )
    }
}
