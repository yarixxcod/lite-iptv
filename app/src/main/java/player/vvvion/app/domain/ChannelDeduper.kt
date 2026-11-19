package player.vvvion.app.domain

import player.vvvion.app.data.parse.ParsedChannel

/**
 * Folds duplicate playlist rows while a streaming import is in progress.
 * Same URL is dropped. Same tvg-id (or same name+group) with a different URL
 * is kept as an alternate stream on the first row.
 */
class ChannelDeduper {
    private val urls = HashSet<String>(256)
    private val keyToUrl = HashMap<String, String>(256)
    private val alts = HashMap<String, MutableList<String>>()

    sealed interface Decision {
        data class Insert(val channel: ParsedChannel, val dedupeKey: String) : Decision
        data object Skip : Decision
    }

    fun accept(channel: ParsedChannel, fallbackNumber: Int): Decision {
        val url = channel.url.trim()
        if (url.isEmpty() || !urls.add(url)) return Decision.Skip
        val key = dedupeKey(channel)
        val primary = keyToUrl[key]
        if (primary != null) {
            alts.getOrPut(primary) { ArrayList(1) }.add(url)
            return Decision.Skip
        }
        keyToUrl[key] = url
        val numbered = if (channel.number != null) channel else channel.copy(number = fallbackNumber)
        return Decision.Insert(numbered.copy(url = url), key)
    }

    fun altUrls(): Map<String, List<String>> = alts

    fun clear() {
        urls.clear()
        keyToUrl.clear()
        alts.clear()
    }

    companion object {
        fun dedupeKey(channel: ParsedChannel): String {
            val id = channel.tvgId?.trim().orEmpty()
            if (id.isNotEmpty()) return "id:$id"
            return "ng:" + channel.name.trim().lowercase() + "|" + channel.group.trim().lowercase()
        }

        fun epgKey(tvgId: String?, name: String): String {
            val id = tvgId?.trim().orEmpty()
            if (id.isNotEmpty()) return id
            return "name:" + name.trim().lowercase()
        }
    }
}
