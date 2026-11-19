package player.vvvion.app.data.parse

import java.io.BufferedReader

/**
 * Streaming M3U parser. Lines are consumed one at a time and channels are
 * emitted in batches so a 100k playlist never becomes one giant list.
 */
class M3uParser {
    fun parse(
        reader: BufferedReader,
        batchSize: Int = 400,
        onHeader: (M3uHeader) -> Unit = {},
        onBatch: (List<ParsedChannel>) -> Unit,
    ): Int {
        var count = 0
        val batch = ArrayList<ParsedChannel>(batchSize)
        var pending: Partial? = null
        var extGroup: String? = null
        var headerSent = false
        val headerEpg = LinkedHashSet<String>()
        var catchup: String? = null
        var catchupDays: Int? = null
        var lineNo = 0
        fun emitHeader() {
            if (headerSent) return
            onHeader(M3uHeader(headerEpg.toList(), catchup, catchupDays))
            headerSent = true
        }
        reader.forEachLine { raw ->
            lineNo++
            var line = raw.trim()
            if (lineNo == 1) line = line.removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEachLine
            if (M3uEpg.isHeaderLine(line)) {
                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    val attrs = parseAttributes(line.substring(7))
                    if (catchup == null) catchup = attrs["catchup"]
                    if (catchupDays == null) catchupDays = attrs["catchup-days"]?.toIntOrNull()
                }
                headerEpg += M3uEpg.urlsFromLine(line)
                return@forEachLine
            }
            when {
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(":").trim().ifEmpty { null }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    emitHeader()
                    pending = parseExtInf(line.substringAfter(":"), extGroup)
                }
                line.startsWith("#") -> {
                    headerEpg += M3uEpg.urlsFromLine(line)
                }
                pending != null -> {
                    val channel = pending!!.toChannel(line.trim())
                    pending = null
                    if (channel.url.isNotEmpty()) {
                        batch += channel
                        count++
                        if (batch.size >= batchSize) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                }
            }
        }
        emitHeader()
        if (batch.isNotEmpty()) onBatch(batch.toList())
        return count
    }

    private data class Partial(
        val attrs: Map<String, String>,
        val name: String,
        val extGroup: String?,
    ) {
        fun toChannel(url: String): ParsedChannel {
            val group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                ?: extGroup?.takeIf { it.isNotBlank() }
                ?: "Ungrouped"
            return ParsedChannel(
                name = name,
                url = url,
                tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                group = group,
                number = attrs["tvg-chno"]?.toIntOrNull(),
                catchup = attrs["catchup"]?.takeIf { it.isNotBlank() },
                catchupDays = attrs["catchup-days"]?.toIntOrNull(),
                catchupSource = (attrs["catchup-source"] ?: attrs["catchup-template"])?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun parseExtInf(body: String, extGroup: String?): Partial {
        val comma = indexOfUnquotedComma(body)
        val left: String
        val rawName: String
        if (comma >= 0) {
            left = body.substring(0, comma)
            rawName = body.substring(comma + 1).trim()
        } else {
            left = body
            rawName = ""
        }
        val trimmed = left.trim()
        val space = trimmed.indexOf(' ')
        val attrPart = if (space >= 0) trimmed.substring(space + 1) else ""
        val attrs = parseAttributes(attrPart)
        val name = rawName.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { "Channel" }
        return Partial(attrs, name, extGroup)
    }
}

internal fun parseAttributes(input: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val s = input
    var i = 0
    while (i < s.length) {
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) break
        val keyStart = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == '_')) i++
        if (i == keyStart || i >= s.length || s[i] != '=') {
            while (i < s.length && !s[i].isWhitespace()) i++
            continue
        }
        val key = s.substring(keyStart, i).lowercase()
        i++
        if (i >= s.length) break
        val value = if (s[i] == '"' || s[i] == '\'') {
            val quote = s[i]
            i++
            val sb = StringBuilder()
            while (i < s.length && s[i] != quote) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    sb.append(s[i + 1])
                    i += 2
                } else {
                    sb.append(s[i])
                    i++
                }
            }
            if (i < s.length && s[i] == quote) i++
            sb.toString()
        } else {
            val start = i
            while (i < s.length && !s[i].isWhitespace()) i++
            s.substring(start, i)
        }
        out[key] = decodeXmlEntities(value)
    }
    return out
}

internal fun indexOfUnquotedComma(s: String): Int {
    var quote: Char? = null
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (quote != null) {
            if (c == '\\' && i + 1 < s.length) {
                i += 2
                continue
            }
            if (c == quote) quote = null
        } else if (c == '"' || c == '\'') {
            quote = c
        } else if (c == ',') {
            return i
        }
        i++
    }
    return -1
}
