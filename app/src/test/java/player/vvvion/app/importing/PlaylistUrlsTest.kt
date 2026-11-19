package player.vvvion.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import player.vvvion.app.data.importing.PlaylistUrls

class PlaylistUrlsTest {
    private val good = "http://plist.link/BJDfTS66o751/alltv.m3u8"

    @Test
    fun leavesValidUrlUnchanged() {
        val check = PlaylistUrls.validateHttpUrl(good)
        assertEquals(PlaylistUrls.Check.Ok(good), check)
        assertEquals(good, PlaylistUrls.sanitize(good))
        val https = "https://cdn.example/list.m3u"
        assertEquals(PlaylistUrls.Check.Ok(https), PlaylistUrls.validateHttpUrl(https))
    }

    @Test
    fun stripsBomNbspZeroWidthAndNewlines() {
        val dirty = "\uFEFF\u200B  \u00A0$good\n\r\t"
        assertEquals(good, PlaylistUrls.sanitize(dirty))
        assertEquals(PlaylistUrls.Check.Ok(good), PlaylistUrls.validateHttpUrl(dirty))
    }

    @Test
    fun rejectsLeadingOneWithoutChangingItIntoAValidUrl() {
        val dirty = "1$good"
        val check = PlaylistUrls.validateHttpUrl(dirty)
        assertTrue(check is PlaylistUrls.Check.Invalid)
        check as PlaylistUrls.Check.Invalid
        assertEquals(PlaylistUrls.Reason.LEADING_JUNK, check.reason)
        assertEquals(dirty, PlaylistUrls.sanitize(dirty))
    }

    @Test
    fun rejectsMissingSchemeAndEmpty() {
        assertEquals(PlaylistUrls.Reason.EMPTY, (PlaylistUrls.validateHttpUrl("   ") as PlaylistUrls.Check.Invalid).reason)
        assertEquals(PlaylistUrls.Reason.MISSING_SCHEME, (PlaylistUrls.validateHttpUrl("ftp://x") as PlaylistUrls.Check.Invalid).reason)
        assertEquals(PlaylistUrls.Reason.MISSING_SCHEME, (PlaylistUrls.validateHttpUrl("plist.link/a.m3u8") as PlaylistUrls.Check.Invalid).reason)
    }

    @Test
    fun acceptsSchemeRegardlessOfCase() {
        val mixed = "HTTP://plist.link/list.m3u8"
        assertEquals(PlaylistUrls.Check.Ok(mixed), PlaylistUrls.validateHttpUrl(mixed))
    }
}
