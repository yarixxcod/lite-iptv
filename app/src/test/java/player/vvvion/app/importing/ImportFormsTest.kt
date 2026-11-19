package player.vvvion.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import player.vvvion.app.data.importing.ImportForms
import java.io.ByteArrayInputStream

class ImportFormsTest {
    @Test
    fun readsUrlEncodedWithCyrillicAndPlus() {
        val body = "kind=url&name=%D0%93%D0%BE%D1%81%D1%82%D0%B8%D0%BD%D0%B0%D1%8F&url=http%3A%2F%2Fplist.link%2Fa.m3u8"
        val imported = ImportForms.parse("application/x-www-form-urlencoded", body)!!
        assertEquals("Гостиная", imported.name)
        assertEquals("URL", imported.kind)
        assertEquals("http://plist.link/a.m3u8", imported.url)
    }

    @Test
    fun readsMultipartFileBody() {
        val boundary = "----testbound"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="kind"
            
            file
            --$boundary
            Content-Disposition: form-data; name="name"
            
            Каналы
            --$boundary
            Content-Disposition: form-data; name="file"; filename="list.m3u"
            
            #EXTM3U
            #EXTINF:-1,Первый
            http://stream/1
            --$boundary--
        """.trimIndent().replace("\n", "\r\n")
        val imported = ImportForms.parse("multipart/form-data; boundary=$boundary", body)!!
        assertEquals("PASTE", imported.kind)
        assertEquals("Каналы", imported.name)
        assertTrue(imported.body!!.contains("#EXTINF:-1,Первый"))
    }

    @Test
    fun sanitizesUrlFromFormAndRejectsJunkPrefix() {
        val ok = ImportForms.parse("application/x-www-form-urlencoded", "kind=url&url=%E2%80%8Bhttp%3A%2F%2Fok.example%2Fa.m3u")!!
        assertEquals("http://ok.example/a.m3u", ok.url)
        assertNull(ImportForms.parse("application/x-www-form-urlencoded", "kind=url&url=1http://ok.example/a.m3u"))
    }

    @Test
    fun readsHttpLineAndExactBodyBytes() {
        val payload = "Привет"
        val raw = "POST /i/x HTTP/1.1\r\nContent-Length: ${payload.toByteArray().size}\r\n\r\n$payload"
        val input = ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8))
        assertEquals("POST /i/x HTTP/1.1", ImportForms.readHttpLine(input))
        assertEquals("Content-Length: ${payload.toByteArray().size}", ImportForms.readHttpLine(input))
        assertEquals("", ImportForms.readHttpLine(input))
        val body = ImportForms.readExact(input, payload.toByteArray().size)
        assertEquals(payload, String(body, Charsets.UTF_8))
    }
}
