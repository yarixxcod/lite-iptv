package player.vvvion.app.data.importing

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder

object ImportForms {
    fun parse(contentType: String, body: String): RemoteImport? {
        val fields = if (contentType.startsWith("multipart/", ignoreCase = true)) {
            parseMultipart(contentType, body)
        } else {
            parseUrlEncoded(body)
        }
        return toImport(fields)
    }

    fun toImport(fields: Map<String, String>): RemoteImport? {
        val name = fields["name"].orEmpty().ifBlank { "Phone import" }
        val kind = fields["kind"].orEmpty()
        return when (kind.lowercase()) {
            "xtream" -> {
                val server = PlaylistUrls.sanitize(fields["server"])
                if (server.isBlank()) null
                else RemoteImport(name, "XTREAM", url = server, username = fields["username"], password = fields["password"])
            }
            "file" -> {
                val text = fields["file"].orEmpty()
                if (!text.contains("#EXTINF", true) && !text.contains("#EXTM3U", true)) null
                else RemoteImport(name, "PASTE", body = text)
            }
            else -> {
                val raw = fields["url"].orEmpty()
                if (raw.contains("#EXTM3U") || raw.contains("#EXTINF")) {
                    RemoteImport(name, "PASTE", body = raw)
                } else {
                    when (val check = PlaylistUrls.validateHttpUrl(raw)) {
                        is PlaylistUrls.Check.Ok -> RemoteImport(name, "URL", url = check.url)
                        is PlaylistUrls.Check.Invalid -> null
                    }
                }
            }
        }
    }

    fun parseUrlEncoded(body: String): Map<String, String> =
        body.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else decode(part.substring(0, idx)) to decode(part.substring(idx + 1))
        }.toMap()

    fun parseMultipart(contentType: String, body: String): Map<String, String> {
        val boundary = contentType.substringAfter("boundary=", "").trim().trim('"')
        if (boundary.isEmpty()) return emptyMap()
        val chunks = body.split("--$boundary")
        val out = HashMap<String, String>()
        for (chunk in chunks) {
            val trimmed = chunk.trim('\r', '\n', '-', ' ')
            if (trimmed.isEmpty() || trimmed.equals("end", true)) continue
            val headerEnd = trimmed.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: trimmed.indexOf("\n\n")
            if (headerEnd < 0) continue
            val headers = trimmed.substring(0, headerEnd)
            val content = trimmed.substring(headerEnd).trimStart('\r', '\n').trimEnd('\r', '\n', '-')
            val name = headers.substringAfter("name=\"", "").substringBefore("\"")
            if (name.isNotEmpty()) out[name] = content
        }
        return out
    }

    fun readHttpLine(input: InputStream, max: Int = 8192): String? {
        val out = ByteArrayOutputStream()
        while (out.size() < max) {
            val byte = input.read()
            if (byte == -1) return if (out.size() == 0) null else out.toString("UTF-8")
            if (byte == 10) return out.toString("UTF-8").trimEnd('\r')
            out.write(byte)
        }
        return null
    }

    fun readExact(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(buf, off, length - off)
            if (n < 0) break
            off += n
        }
        return if (off == length) buf else buf.copyOf(off)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
