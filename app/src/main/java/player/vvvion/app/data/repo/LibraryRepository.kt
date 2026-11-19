package player.vvvion.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.withTransaction
import player.vvvion.app.data.db.AppDatabase
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.FavoriteEntity
import player.vvvion.app.data.db.HiddenEntity
import player.vvvion.app.data.db.HistoryEntity
import player.vvvion.app.data.db.PlaylistEntity
import player.vvvion.app.data.importing.PlaylistUrls
import player.vvvion.app.domain.HiddenGroups
import player.vvvion.app.data.net.HttpClients
import player.vvvion.app.data.net.downloadToFile
import player.vvvion.app.data.net.fetchXtreamCategories
import player.vvvion.app.data.net.forEachXtreamStream
import player.vvvion.app.data.net.m3uChannelFromXtream
import player.vvvion.app.data.net.normalizeServer
import player.vvvion.app.data.net.xtreamEpgUrl
import player.vvvion.app.data.parse.M3uParser
import player.vvvion.app.data.parse.ParsedChannel
import player.vvvion.app.domain.ChannelDeduper
import player.vvvion.app.domain.FavoritesLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import java.util.concurrent.ConcurrentHashMap

data class PlaylistDraft(
    val id: Long = 0,
    val name: String,
    val type: String,
    val url: String? = null,
    val fileUri: String? = null,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val updateHours: Int = 12,
    val tzOffsetMinutes: Int = 0,
    val userAgent: String? = null,
    val pastedText: String? = null,
)

enum class ChannelSort { NUMBER, NAME }

class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val http: HttpClients,
    private val parser: M3uParser,
    private val json: Json,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    fun observePlaylists(): Flow<List<PlaylistEntity>> = db.playlists().observe()

    suspend fun playlistsSnapshot(): List<PlaylistEntity> = db.playlists().all()

    fun groups(playlistId: Long) = db.channels().groups(playlistId)

    fun recent() = db.history().recent()

    fun observeFavoriteUrls(playlistId: Long): Flow<List<String>> = db.favorites().observeUrls(playlistId)

    suspend fun playlist(id: Long): PlaylistEntity? = withContext(Dispatchers.IO) { db.playlists().get(id) }

    suspend fun channel(id: Long): ChannelEntity? = db.channels().byId(id)

    suspend fun channelByNumber(playlistId: Long, number: Int, excludedGroups: Collection<String> = emptyList()): ChannelEntity? =
        db.channels().byNumber(playlistId, number, HiddenGroups.forSql(excludedGroups))

    suspend fun neighbor(channel: ChannelEntity, delta: Int, excludedGroups: Collection<String> = emptyList()): ChannelEntity? {
        val excluded = HiddenGroups.forSql(excludedGroups)
        return if (delta >= 0) db.channels().next(channel.playlistId, channel.number, excluded)
        else db.channels().prev(channel.playlistId, channel.number, excluded)
    }

    fun paging(
        playlistId: Long,
        group: String,
        query: String,
        sort: ChannelSort,
        favoritesOnly: Boolean,
        excludedGroups: Collection<String> = emptyList(),
    ): PagingSource<Int, ChannelEntity> {
        val q = escapeLike(query)
        val excluded = HiddenGroups.forSql(excludedGroups)
        return if (favoritesOnly) {
            db.channels().pagingFavorites(playlistId, q)
        } else if (sort == ChannelSort.NAME) {
            db.channels().pagingByName(playlistId, group, q, excluded)
        } else {
            db.channels().pagingByNumber(playlistId, group, q, excluded)
        }
    }

    suspend fun savePlaylist(draft: PlaylistDraft): Long = withContext(Dispatchers.IO) {
        val name = draft.name.trim().ifBlank { "Playlist" }
        val id = if (draft.id == 0L) {
            db.playlists().insert(
                PlaylistEntity(
                    name = name,
                    type = draft.type,
                    url = draft.url?.let { PlaylistUrls.sanitize(it) }?.ifBlank { null },
                    fileUri = draft.fileUri,
                    username = draft.username?.trim(),
                    password = draft.password,
                    epgUrl = draft.epgUrl?.trim()?.ifBlank { null },
                    epgEtag = null,
                    epgLastModified = null,
                    playlistEtag = null,
                    playlistLastModified = null,
                    updateHours = draft.updateHours.coerceIn(0, 168),
                    tzOffsetMinutes = draft.tzOffsetMinutes.coerceIn(-12 * 60, 14 * 60),
                    userAgent = draft.userAgent?.trim()?.ifBlank { null },
                    lastSyncAt = null,
                    lastError = null,
                ),
            )
        } else {
            db.playlists().updateMeta(
                id = draft.id,
                name = name,
                url = draft.url?.let { PlaylistUrls.sanitize(it) }?.ifBlank { null },
                fileUri = draft.fileUri,
                username = draft.username?.trim(),
                password = draft.password,
                epgUrl = draft.epgUrl?.trim()?.ifBlank { null },
                updateHours = draft.updateHours.coerceIn(0, 168),
                tzOffsetMinutes = draft.tzOffsetMinutes.coerceIn(-12 * 60, 14 * 60),
                userAgent = draft.userAgent?.trim()?.ifBlank { null },
            )
            draft.id
        }
        if (!draft.pastedText.isNullOrBlank()) {
            val file = File(context.filesDir, "paste-$id.m3u")
            file.writeText(draft.pastedText)
            db.playlists().updateMeta(
                id, name, draft.url, file.absolutePath, draft.username, draft.password,
                draft.epgUrl, draft.updateHours, draft.tzOffsetMinutes, draft.userAgent,
            )
        }
        id
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            db.channels().deleteByPlaylist(id)
            db.favorites().deletePlaylist(id)
            db.hidden().deletePlaylist(id)
            db.programs().deletePlaylist(id)
            db.playlists().delete(id)
        }
        File(context.filesDir, "paste-$id.m3u").delete()
    }

    suspend fun syncPlaylist(id: Long) = lock(id).withLock {
        withContext(Dispatchers.IO) {
            val playlist = db.playlists().get(id) ?: return@withContext
            try {
                when (playlist.type) {
                    TYPE_XTREAM -> syncXtream(playlist)
                    TYPE_FILE, TYPE_PASTE -> syncLocal(playlist)
                    else -> syncUrl(playlist)
                }
            } catch (t: Throwable) {
                db.playlists().updatePlaylistStatus(
                    id = id,
                    etag = playlist.playlistEtag,
                    lastModified = playlist.playlistLastModified,
                    syncedAt = System.currentTimeMillis(),
                    error = t.message ?: t.javaClass.simpleName,
                    epgUrl = playlist.epgUrl,
                )
                throw t
            }
        }
    }

    suspend fun hide(channel: ChannelEntity) {
        db.hidden().hide(HiddenEntity(channel.playlistId, channel.streamUrl))
    }

    suspend fun toggleFavorite(channel: ChannelEntity) {
        val current = db.favorites().urls(channel.playlistId)
        val next = FavoritesLogic.toggle(current, channel.streamUrl)
        rewriteFavorites(channel.playlistId, next)
    }

    suspend fun moveFavorite(playlistId: Long, from: Int, to: Int) {
        val current = db.favorites().urls(playlistId)
        rewriteFavorites(playlistId, FavoritesLogic.move(current, from, to))
    }

    suspend fun isFavorite(playlistId: Long, url: String): Boolean =
        db.favorites().urls(playlistId).contains(url)

    suspend fun favoriteIndex(playlistId: Long, url: String): Int =
        db.favorites().urls(playlistId).indexOf(url)

    suspend fun remember(channel: ChannelEntity, positionMs: Long) {
        db.history().upsert(
            HistoryEntity(
                playlistId = channel.playlistId,
                streamUrl = channel.streamUrl,
                channelId = channel.id,
                channelName = channel.name,
                logo = channel.logo,
                playedAt = System.currentTimeMillis(),
                positionMs = positionMs,
            ),
        )
    }

    suspend fun head(playlistId: Long, group: String, limit: Int = 8, excludedGroups: Collection<String> = emptyList()): List<ChannelEntity> =
        db.channels().head(playlistId, group, limit, HiddenGroups.forSql(excludedGroups))

    fun cacheDir(): File = context.cacheDir

    fun clearCaches() {
        context.cacheDir.resolve("http").deleteRecursively()
        context.cacheDir.resolve("logos").deleteRecursively()
        context.cacheDir.resolve("epg").deleteRecursively()
    }

    private suspend fun rewriteFavorites(playlistId: Long, urls: List<String>) {
        db.withTransaction {
            db.favorites().clear(playlistId)
            if (urls.isNotEmpty()) {
                db.favorites().insertAll(urls.mapIndexed { index, url -> FavoriteEntity(playlistId, url, index) })
            }
        }
    }

    private fun syncUrl(playlist: PlaylistEntity) {
        val url = when (val check = PlaylistUrls.validateHttpUrl(playlist.url)) {
            is PlaylistUrls.Check.Ok -> check.url
            is PlaylistUrls.Check.Invalid -> error(PlaylistUrls.errorMessage(check.reason))
        }
        val dest = File(context.cacheDir, "playlist-${playlist.id}.m3u")
        val remote = downloadToFile(
            http.playlist, url, dest, playlist.userAgent, playlist.playlistEtag, playlist.playlistLastModified,
        )
        if (remote.notModified && db.channels().count(playlist.id) > 0) {
            db.playlists().updatePlaylistStatus(playlist.id, remote.etag, remote.lastModified, System.currentTimeMillis(), null, playlist.epgUrl)
            return
        }
        val epg = importM3u(playlist, dest)
        db.playlists().updatePlaylistStatus(
            playlist.id, remote.etag, remote.lastModified, System.currentTimeMillis(), null,
            epg ?: playlist.epgUrl,
        )
    }

    private fun syncLocal(playlist: PlaylistEntity) {
        val source = playlist.fileUri ?: error("No local file")
        val dest = File(context.cacheDir, "playlist-${playlist.id}.m3u")
        openLocal(source).use { input -> dest.outputStream().use { input.copyTo(it) } }
        val epg = importM3u(playlist, dest)
        db.playlists().updatePlaylistStatus(
            playlist.id, null, null, System.currentTimeMillis(), null, epg ?: playlist.epgUrl,
        )
    }

    private fun syncXtream(playlist: PlaylistEntity) {
        val base = normalizeServer(playlist.url ?: error("Missing server"))
        val user = playlist.username?.takeIf { it.isNotBlank() } ?: error("Missing username")
        val pass = playlist.password ?: error("Missing password")
        val categories = fetchXtreamCategories(http.playlist, json, base, user, pass, playlist.userAgent)
        val deduper = ChannelDeduper()
        val batch = ArrayList<ChannelEntity>(400)
        var auto = 1
        fun flush() {
            if (batch.isNotEmpty()) {
                db.channels().insertAll(batch.toList())
                batch.clear()
            }
        }
        db.runInTransaction {
            db.channels().deleteByPlaylist(playlist.id)
            forEachXtreamStream(http.playlist, json, base, user, pass, playlist.userAgent) { stream ->
                val parsed = m3uChannelFromXtream(base, user, pass, categories, stream) ?: return@forEachXtreamStream
                when (val decision = deduper.accept(parsed, auto)) {
                    is ChannelDeduper.Decision.Insert -> {
                        val ch = decision.channel
                        if (parsed.number == null) auto++ else if ((parsed.number) >= auto) auto = parsed.number + 1
                        batch += ch.toEntity(playlist.id, decision.dedupeKey)
                        if (batch.size >= 400) flush()
                    }
                    ChannelDeduper.Decision.Skip -> Unit
                }
            }
            flush()
            deduper.altUrls().forEach { (url, alts) ->
                db.channels().setAlts(playlist.id, url, alts.joinToString("\n"))
            }
        }
        val epg = playlist.epgUrl?.takeIf { it.isNotBlank() } ?: xtreamEpgUrl(base, user, pass)
        db.playlists().updatePlaylistStatus(playlist.id, null, null, System.currentTimeMillis(), null, epg)
    }

    private fun importM3u(playlist: PlaylistEntity, file: File): String? {
        return try {
            importM3u(playlist, file, Charsets.UTF_8)
        } catch (error: Exception) {
            if (error.isCharsetError()) importM3u(playlist, file, charset("windows-1251")) else throw error
        }
    }

    private fun importM3u(playlist: PlaylistEntity, file: File, charset: Charset): String? {
        val deduper = ChannelDeduper()
        var headerEpg: String? = null
        val batch = ArrayList<ChannelEntity>(400)
        var auto = 1
        fun flush() {
            if (batch.isNotEmpty()) {
                db.channels().insertAll(batch.toList())
                batch.clear()
            }
        }
        val reader = openReader(file, charset)
        db.runInTransaction {
            db.channels().deleteByPlaylist(playlist.id)
            parser.parse(reader, onHeader = { header ->
                if (headerEpg == null && header.epgUrls.isNotEmpty()) {
                    headerEpg = header.epgUrls.joinToString("\n")
                }
            }) { items ->
                for (parsed in items) {
                    when (val decision = deduper.accept(parsed, auto)) {
                        is ChannelDeduper.Decision.Insert -> {
                            if (parsed.number == null) auto++ else if (parsed.number >= auto) auto = parsed.number + 1
                            batch += decision.channel.toEntity(playlist.id, decision.dedupeKey)
                            if (batch.size >= 400) flush()
                        }
                        ChannelDeduper.Decision.Skip -> Unit
                    }
                }
            }
            flush()
            deduper.altUrls().forEach { (url, alts) ->
                db.channels().setAlts(playlist.id, url, alts.joinToString("\n"))
            }
        }
        reader.close()
        return headerEpg ?: playlist.epgUrl
    }

    private fun openLocal(source: String) = when {
        source.startsWith("content:") -> context.contentResolver.openInputStream(Uri.parse(source))
            ?: error("Cannot open file")
        else -> File(source).inputStream()
    }

    private fun lock(id: Long) = locks.getOrPut(id) { Mutex() }

    companion object {
        const val TYPE_URL = "URL"
        const val TYPE_FILE = "FILE"
        const val TYPE_XTREAM = "XTREAM"
        const val TYPE_PASTE = "PASTE"
    }
}

private fun ParsedChannel.toEntity(playlistId: Long, dedupeKey: String) = ChannelEntity(
    playlistId = playlistId,
    number = number ?: 0,
    name = name,
    streamUrl = url,
    tvgId = tvgId,
    tvgName = tvgName,
    logo = logo,
    groupTitle = group.ifBlank { "Ungrouped" },
    catchup = catchup,
    catchupDays = catchupDays,
    catchupSource = catchupSource,
    altUrls = null,
    dedupeKey = dedupeKey,
    epgKey = ChannelDeduper.epgKey(tvgId, name),
)

private fun openReader(file: File, charset: Charset) = InputStreamReader(
    FileInputStream(file),
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT),
).buffered(64 * 1024)

private fun Exception.isCharsetError(): Boolean {
    if (this is MalformedInputException) return true
    val cause = cause
    return cause is MalformedInputException || message?.contains("malformed", true) == true
}

fun escapeLike(raw: String): String =
    raw.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

fun altList(channel: ChannelEntity): List<String> =
    listOf(channel.streamUrl) + channel.altUrls.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
