package player.vvvion.app.domain

import player.vvvion.app.data.db.GroupRow

object HiddenGroups {
    const val SQL_NONE = "\u0000"

    fun key(playlistId: Long, title: String): String = "$playlistId|$title"

    fun canRemove(title: String): Boolean = title.isNotBlank()

    fun titles(playlistId: Long, keys: Set<String>): Set<String> {
        val prefix = "$playlistId|"
        return keys.mapNotNull { key ->
            if (key.startsWith(prefix)) key.substring(prefix.length).takeIf { it.isNotEmpty() } else null
        }.toSet()
    }

    fun visible(rows: List<GroupRow>, hiddenTitles: Set<String>): List<GroupRow> =
        if (hiddenTitles.isEmpty()) rows else rows.filter { it.title !in hiddenTitles }

    fun visibleCount(rows: List<GroupRow>): Int = rows.sumOf { it.count }

    fun hide(keys: Set<String>, playlistId: Long, title: String): Set<String> {
        if (!canRemove(title)) return keys
        return keys + key(playlistId, title)
    }

    fun restore(keys: Set<String>, playlistId: Long, title: String): Set<String> =
        keys - key(playlistId, title)

    fun restoreAll(keys: Set<String>, playlistId: Long): Set<String> {
        val prefix = "$playlistId|"
        return keys.filterNot { it.startsWith(prefix) }.toSet()
    }

    fun entries(keys: Set<String>, playlistId: Long? = null): List<Pair<Long, String>> =
        keys.mapNotNull { key ->
            val bar = key.indexOf('|')
            if (bar <= 0) return@mapNotNull null
            val id = key.substring(0, bar).toLongOrNull() ?: return@mapNotNull null
            val title = key.substring(bar + 1)
            if (title.isEmpty()) return@mapNotNull null
            if (playlistId != null && id != playlistId) return@mapNotNull null
            id to title
        }.sortedWith(compareBy({ it.first }, { it.second.lowercase() }))

    fun forSql(hiddenTitles: Collection<String>): List<String> =
        hiddenTitles.toList().ifEmpty { listOf(SQL_NONE) }

    fun isHidden(playlistId: Long, title: String, keys: Set<String>): Boolean =
        canRemove(title) && key(playlistId, title) in keys
}
