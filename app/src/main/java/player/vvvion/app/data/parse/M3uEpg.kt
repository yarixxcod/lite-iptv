package player.vvvion.app.data.parse

/**
 * Collects XMLTV addresses from an M3U header or comment line.
 * Understands url-tvg / x-tvg-url / tvg-url / xmltv-url attributes,
 * #URL-TVG: lines, and comma- or semicolon-separated lists.
 */
object M3uEpg {
    private val attribute = Regex(
        """(?i)\b(?:url-tvg|x-tvg-url|tvg-url|xmltv-url)\s*=\s*(?:["']([^"']+)["']|(\S+))""",
    )
    private val tagged = Regex(
        """(?i)^#(?:url-tvg|ext-x-tvg-url|tvg-url|xmltv-url)\s*:?\s*(.+)$""",
    )

    fun urlsFromLine(line: String): List<String> {
        val trimmed = line.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return emptyList()
        val found = ArrayList<String>()
        tagged.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.let { found += it }
        attribute.findAll(trimmed).forEach { match ->
            found += match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        return found.flatMap { it.split(',', ';') }
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
    }

    fun isHeaderLine(line: String): Boolean {
        val trimmed = line.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) return true
        if (tagged.matches(trimmed)) return true
        return trimmed.startsWith("#") && attribute.containsMatchIn(trimmed)
    }
}
