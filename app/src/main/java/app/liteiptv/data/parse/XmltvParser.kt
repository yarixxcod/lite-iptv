package app.liteiptv.data.parse

import java.io.InputStream
import java.io.PushbackReader
import java.io.Reader
import java.util.Calendar
import java.util.TimeZone

class XmltvParser {
    fun parse(
        input: InputStream,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
        windowStart: Long,
        windowEnd: Long,
        offsetMinutes: Int = 0,
        batchSize: Int = 300,
        onBatch: (List<ParsedProgram>) -> Unit,
    ): Int {
        val reader = PushbackReader(input.reader(charset), 128)
        var count = 0
        val batch = ArrayList<ParsedProgram>(batchSize)
        while (true) {
            val tag = nextTag(reader) ?: break
            if (tag.closing || tag.name != "programme") continue
            val channel = tag.attrs["channel"].orEmpty()
            val start = parseXmltvTime(tag.attrs["start"]) ?: continue
            val stop = parseXmltvTime(tag.attrs["stop"]) ?: continue
            var title: String? = null
            var desc: String? = null
            while (true) {
                val inner = nextTag(reader) ?: break
                if (inner.closing && inner.name == "programme") break
                if (inner.closing) continue
                if (inner.name == "title" || inner.name == "desc" || inner.name == "sub-title") {
                    if (!inner.selfClosing) {
                        val text = readElementText(reader, inner.name).trim()
                        val decoded = decodeXmlEntities(text)
                        when (inner.name) {
                            "title" -> if (title.isNullOrBlank()) title = decoded
                            "sub-title" -> if (decoded.isNotBlank() && !title.isNullOrBlank()) {
                                title = "$title — $decoded"
                            }
                            "desc" -> if (desc.isNullOrBlank()) desc = decoded
                        }
                    }
                } else {
                    skipElement(reader, inner)
                }
            }
            if (channel.isBlank() || title.isNullOrBlank()) continue
            val shift = offsetMinutes * 60_000L
            val startUtc = start + shift
            val stopUtc = stop + shift
            if (stopUtc <= windowStart || startUtc >= windowEnd) continue
            batch += ParsedProgram(channel, title!!, desc?.ifBlank { null }, startUtc, stopUtc)
            count++
            if (batch.size >= batchSize) {
                onBatch(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
        return count
    }

    private data class Tag(
        val name: String,
        val closing: Boolean,
        val selfClosing: Boolean,
        val attrs: Map<String, String>,
    )

    private fun nextTag(reader: PushbackReader): Tag? {
        while (true) {
            val c = reader.read()
            if (c < 0) return null
            if (c.toChar() != '<') continue
            val next = reader.read()
            if (next < 0) return null
            if (next.toChar() == '!') {
                val mark = reader.read()
                if (mark.toChar() == '[') {
                    if (consumeLiteral(reader, "CDATA[")) {
                        readCdata(reader)
                    } else {
                        skipUntil(reader, '>')
                    }
                } else if (mark.toChar() == '-') {
                    skipComment(reader)
                } else {
                    skipUntil(reader, '>')
                }
                continue
            }
            if (next.toChar() == '?') {
                skipUntil(reader, '>')
                continue
            }
            reader.unread(next)
            val raw = StringBuilder()
            var quote: Char? = null
            while (true) {
                val ch = reader.read()
                if (ch < 0) break
                val char = ch.toChar()
                if (quote != null) {
                    raw.append(char)
                    if (char == quote) quote = null
                    continue
                }
                if (char == '"' || char == '\'') {
                    quote = char
                    raw.append(char)
                    continue
                }
                if (char == '>') break
                raw.append(char)
            }
            return parseTag(raw.toString())
        }
    }

    private fun parseTag(raw: String): Tag {
        var body = raw.trim()
        val closing = body.startsWith("/")
        if (closing) body = body.substring(1).trim()
        var selfClosing = false
        if (body.endsWith("/")) {
            selfClosing = true
            body = body.dropLast(1).trim()
        }
        val nameEnd = body.indexOfFirst { it.isWhitespace() }
        val rawName = if (nameEnd < 0) body else body.substring(0, nameEnd)
        val name = rawName.substringAfter(':').lowercase()
        val attrPart = if (nameEnd < 0) "" else body.substring(nameEnd + 1)
        return Tag(name, closing, selfClosing, parseAttributes(attrPart))
    }

    /**
     * Reads character data until the matching end tag. CDATA sections are expanded.
     * A raw '<' that is not CDATA and not this element's end tag is kept as text,
     * so slightly broken guides (`Hello <world>`) still parse.
     * The end tag itself is consumed.
     */
    private fun readElementText(reader: PushbackReader, element: String): String {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) break
            if (c.toChar() != '<') {
                sb.append(c.toChar())
                continue
            }
            val mark = reader.read()
            if (mark < 0) {
                sb.append('<')
                break
            }
            if (mark.toChar() == '!') {
                val mark2 = reader.read()
                if (mark2 >= 0 && mark2.toChar() == '[' && consumeLiteral(reader, "CDATA[")) {
                    sb.append(readCdata(reader))
                    continue
                }
                if (mark2 >= 0) reader.unread(mark2)
                reader.unread(mark)
                sb.append('<')
                continue
            }
            if (mark.toChar() == '/') {
                val nameChars = CharArray(element.length)
                var i = 0
                var matches = true
                while (i < element.length) {
                    val ch = reader.read()
                    if (ch < 0) {
                        matches = false
                        break
                    }
                    nameChars[i] = ch.toChar()
                    if (nameChars[i].lowercaseChar() != element[i]) {
                        matches = false
                        i++
                        break
                    }
                    i++
                }
                if (matches) {
                    val after = reader.read()
                    val afterChar = after.toChar()
                    if (after >= 0 && (afterChar.isWhitespace() || afterChar == '>' || afterChar == '/')) {
                        if (afterChar != '>') skipUntil(reader, '>')
                        break
                    }
                    if (after >= 0) reader.unread(after)
                }
                for (j in i - 1 downTo 0) reader.unread(nameChars[j].code)
                reader.unread('/'.code)
                sb.append('<')
                continue
            }
            reader.unread(mark)
            sb.append('<')
        }
        return sb.toString()
    }

    private fun consumeLiteral(reader: PushbackReader, literal: String): Boolean {
        val read = CharArray(literal.length)
        var got = 0
        while (got < literal.length) {
            val c = reader.read()
            if (c < 0) break
            read[got] = c.toChar()
            if (read[got] != literal[got]) {
                for (i in got downTo 0) reader.unread(read[i].code)
                return false
            }
            got++
        }
        return true
    }

    private fun readCdata(reader: PushbackReader): String {
        val sb = StringBuilder()
        var dashes = 0
        while (true) {
            val c = reader.read()
            if (c < 0) break
            val ch = c.toChar()
            if (ch == ']') {
                dashes++
                continue
            }
            if (dashes >= 2 && ch == '>') {
                if (dashes > 2) repeat(dashes - 2) { sb.append(']') }
                break
            }
            repeat(dashes) { sb.append(']') }
            dashes = 0
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun skipElement(reader: PushbackReader, open: Tag) {
        if (open.selfClosing || open.closing) return
        var depth = 1
        while (depth > 0) {
            val tag = nextTag(reader) ?: return
            if (tag.name != open.name) continue
            if (tag.closing) depth-- else if (!tag.selfClosing) depth++
        }
    }

    private fun skipComment(reader: PushbackReader) {
        var prev2 = 0
        var prev1 = 0
        while (true) {
            val c = reader.read()
            if (c < 0) return
            if (prev2 == '-'.code && prev1 == '-'.code && c.toChar() == '>') return
            prev2 = prev1
            prev1 = c
        }
    }

    private fun skipUntil(reader: PushbackReader, terminator: Char) {
        while (true) {
            val c = reader.read()
            if (c < 0 || c.toChar() == terminator) return
        }
    }
}

fun parseXmltvTime(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    if (s.length < 14 || !s[0].isDigit()) return null
    return try {
        val year = s.substring(0, 4).toInt()
        val month = s.substring(4, 6).toInt()
        val day = s.substring(6, 8).toInt()
        val hour = s.substring(8, 10).toInt()
        val minute = s.substring(10, 12).toInt()
        val second = s.substring(12, 14).toInt()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        var utc = cal.timeInMillis
        val rest = s.substring(14).trim()
        if (rest.isNotEmpty() && (rest[0] == '+' || rest[0] == '-')) {
            val sign = if (rest[0] == '-') -1 else 1
            val digits = rest.substring(1).filter { it.isDigit() }
            val oh = digits.take(2).toIntOrNull() ?: 0
            val om = digits.drop(2).take(2).toIntOrNull() ?: 0
            utc -= sign * (oh * 60L + om) * 60_000L
        }
        utc
    } catch (_: RuntimeException) {
        null
    }
}

fun decodeXmlEntities(input: String): String {
    if (!input.contains('&')) return input
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c != '&') {
            sb.append(c)
            i++
            continue
        }
        val semi = input.indexOf(';', i + 1)
        if (semi < 0 || semi - i > 10) {
            sb.append(c)
            i++
            continue
        }
        val token = input.substring(i + 1, semi)
        val replaced: String? = when {
            token == "amp" -> "&"
            token == "lt" -> "<"
            token == "gt" -> ">"
            token == "quot" -> "\""
            token == "apos" -> "'"
            token.startsWith("#x") || token.startsWith("#X") -> token.drop(2).toIntOrNull(16)?.toChar()?.toString()
            token.startsWith("#") -> token.drop(1).toIntOrNull()?.toChar()?.toString()
            else -> null
        }
        if (replaced == null) {
            sb.append(c)
            i++
        } else {
            sb.append(replaced)
            i = semi + 1
        }
    }
    return sb.toString()
}
