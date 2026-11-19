package player.vvvion.app.data.importing

/**
 * Cleans and checks playlist HTTP(S) addresses without rewriting a valid URL.
 * Only whitespace, BOM, NBSP and zero-width / format characters are stripped.
 */
object PlaylistUrls {
    enum class Reason { EMPTY, MISSING_SCHEME, LEADING_JUNK }

    sealed class Check {
        data class Ok(val url: String) : Check()
        data class Invalid(val reason: Reason, val preview: String) : Check()
    }

    fun sanitize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val out = StringBuilder(raw.length)
        var lastWasSpace = false
        for (ch in raw) {
            if (isInvisible(ch)) continue
            if (ch.isWhitespace() || ch == '\u00A0' || ch == '\u202F' || ch == '\u2007') {
                if (out.isNotEmpty() && !lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
                continue
            }
            lastWasSpace = false
            out.append(ch)
        }
        return out.toString().trim()
    }

    fun validateHttpUrl(raw: String?): Check {
        val url = sanitize(raw)
        if (url.isEmpty()) return Check.Invalid(Reason.EMPTY, "")
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return Check.Ok(url)
        }
        val lowered = url.lowercase()
        val idx = lowered.indexOf("http://").let { if (it >= 0) it else lowered.indexOf("https://") }
        return if (idx > 0) Check.Invalid(Reason.LEADING_JUNK, preview(url))
        else Check.Invalid(Reason.MISSING_SCHEME, preview(url))
    }

    fun errorMessage(reason: Reason): String = when (reason) {
        Reason.EMPTY -> "Enter a playlist URL."
        Reason.MISSING_SCHEME ->
            "The playlist address must start with http:// or https://. Check for an extra character before http."
        Reason.LEADING_JUNK ->
            "The playlist address must start with http:// or https://. Check for an extra character before http."
    }

    private fun preview(url: String): String = url.take(80)

    private fun isInvisible(ch: Char): Boolean {
        if (ch == '\uFEFF' || ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\u2060' || ch == '\u180E') return true
        val type = Character.getType(ch)
        return type == Character.FORMAT.toInt() ||
            type == Character.CONTROL.toInt() ||
            type == Character.SURROGATE.toInt()
    }
}
