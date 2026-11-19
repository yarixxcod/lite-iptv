package player.vvvion.app.domain

/**
 * Normalizes XMLTV ids, tvg-id, tvg-name and display names so a guide that
 * works in other players is not dropped because of case, spacing or an HD suffix.
 */
object EpgMatch {
    private val trailingQuality = Regex("(uhd|fhd|hd|sd|4k|hevc|h265|h264|orig|50fps|60fps|1080p|1080|720p|720)$")

    fun norm(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            val mapped = when (ch) {
                'ё', 'Ё' -> 'е'
                else -> ch.lowercaseChar()
            }
            if (mapped.isLetterOrDigit() || mapped == '+' || mapped == '&') sb.append(mapped)
        }
        return sb.toString()
    }

    fun loose(raw: String?): String {
        val base = norm(raw)
        if (base.length < 5) return ""
        val stripped = trailingQuality.replace(base, "")
        return if (stripped.length >= 3 && stripped != base) stripped else ""
    }

    fun addAliases(map: MutableMap<String, String>, id: String, names: List<String>) {
        val canonical = norm(id)
        if (canonical.isEmpty()) return
        map.putIfAbsent(canonical, canonical)
        val looseId = loose(id)
        if (looseId.isNotEmpty()) map.putIfAbsent(looseId, canonical)
        for (name in names) {
            val exact = norm(name)
            if (exact.isNotEmpty()) map.putIfAbsent(exact, canonical)
            val looseName = loose(name)
            if (looseName.isNotEmpty()) map.putIfAbsent(looseName, canonical)
        }
    }

    fun bind(tvgId: String?, tvgName: String?, name: String, aliases: Map<String, String>): String {
        for (value in listOf(tvgId, tvgName, name)) {
            val exact = norm(value)
            if (exact.isNotEmpty()) aliases[exact]?.let { return it }
            val looseValue = loose(value)
            if (looseValue.isNotEmpty()) aliases[looseValue]?.let { return it }
        }
        return norm(tvgId).ifEmpty { norm(name) }
    }

    fun candidates(tvgId: String?, tvgName: String?, name: String, epgKey: String): List<String> {
        val out = LinkedHashSet<String>()
        fun add(raw: String?) {
            val exact = norm(raw)
            if (exact.isNotEmpty()) out += exact
            val looseValue = loose(raw)
            if (looseValue.isNotEmpty()) out += looseValue
        }
        add(epgKey)
        add(tvgId)
        add(tvgName)
        add(name)
        val legacyId = tvgId?.trim().orEmpty()
        if (legacyId.isNotEmpty()) out += legacyId
        val legacyName = name.trim().lowercase()
        if (legacyName.isNotEmpty()) out += "name:$legacyName"
        if (out.isEmpty()) out += "_"
        return out.toList()
    }
}
