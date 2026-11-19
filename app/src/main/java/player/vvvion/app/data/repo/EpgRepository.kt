package player.vvvion.app.data.repo

import android.content.Context
import player.vvvion.app.data.db.AppDatabase
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.ProgramEntity
import player.vvvion.app.data.net.HttpClients
import player.vvvion.app.data.net.downloadToFile
import player.vvvion.app.data.net.openMaybeCompressed
import player.vvvion.app.data.parse.XmltvParser
import player.vvvion.app.domain.EpgMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

class EpgRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val http: HttpClients,
    private val parser: XmltvParser,
) {
    suspend fun sync(playlistId: Long, force: Boolean = false) = withContext(Dispatchers.IO) {
        val playlist = db.playlists().get(playlistId) ?: return@withContext
        val urls = playlist.epgUrl.orEmpty()
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { resolveUrl(playlist.url, it) }
            .distinct()
        if (urls.isEmpty()) return@withContext
        if (force) db.playlists().clearEpgValidators(playlistId)
        val etag = if (force) null else playlist.epgEtag
        val modified = if (force) null else playlist.epgLastModified
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val stored = syncUrl(playlistId, playlist.userAgent, playlist.tzOffsetMinutes, url, etag, modified)
                if (stored > 0) return@withContext
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (lastError != null) throw lastError
    }

    private fun syncUrl(
        playlistId: Long,
        userAgent: String?,
        offsetMinutes: Int,
        url: String,
        etag: String?,
        lastModified: String?,
    ): Int {
        val fresh = db.playlists().get(playlistId)
        val dest = File(context.cacheDir, "epg-$playlistId.bin").apply { parentFile?.mkdirs() }
        val remote = try {
            downloadToFile(http.playlist, url, dest, userAgent, if (fresh?.epgUrl == url) etag else null, if (fresh?.epgUrl == url) lastModified else null)
        } catch (t: Throwable) {
            db.playlists().updateEpgStatus(playlistId, url, etag, lastModified, System.currentTimeMillis(), t.message)
            throw t
        }
        if (remote.notModified) {
            db.playlists().updateEpgStatus(playlistId, url, remote.etag, remote.lastModified, System.currentTimeMillis(), null)
            return 1
        }
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 60 * 60 * 1000L
        val windowEnd = now + 48 * 60 * 60 * 1000L
        val batch = ArrayList<ProgramEntity>(300)
        var stored = 0
        val aliases = HashMap<String, String>(1024)
        fun flush() {
            if (batch.isNotEmpty()) {
                db.programs().insertAll(batch.toList())
                stored += batch.size
                batch.clear()
            }
        }
        val stream = openMaybeCompressed(dest).buffered()
        try {
            stream.mark(512)
            val probe = ByteArray(400)
            val read = stream.read(probe)
            stream.reset()
            val head = if (read > 0) String(probe, 0, read, Charsets.ISO_8859_1) else ""
            val charset = when {
                head.contains("windows-1251", true) || head.contains("cp1251", true) -> charset("windows-1251")
                head.contains("iso-8859-1", true) -> Charsets.ISO_8859_1
                else -> Charsets.UTF_8
            }
            db.runInTransaction {
                db.programs().deletePlaylist(playlistId)
                parser.parse(
                    input = stream,
                    charset = charset,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    offsetMinutes = offsetMinutes,
                    onChannel = { id, names -> EpgMatch.addAliases(aliases, id, names) },
                ) { programs ->
                    for (program in programs) {
                        val key = EpgMatch.norm(program.channelId)
                        if (key.isEmpty()) continue
                        EpgMatch.putAlias(aliases, key, key)
                        EpgMatch.putAlias(aliases, EpgMatch.loose(program.channelId), key)
                        batch += ProgramEntity(
                            playlistId = playlistId,
                            channelKey = key,
                            title = program.title,
                            description = program.description,
                            startUtc = program.startUtc,
                            stopUtc = program.stopUtc,
                        )
                        if (batch.size >= 300) flush()
                    }
                }
                flush()
                if (stored > 0) rebind(playlistId, aliases)
            }
        } finally {
            stream.close()
        }
        val error = if (stored == 0) "No programmes in the stored window" else null
        db.playlists().updateEpgStatus(playlistId, url, remote.etag, remote.lastModified, System.currentTimeMillis(), error)
        return stored
    }

    private fun rebind(playlistId: Long, aliases: Map<String, String>) {
        val refs = db.channels().epgRefs(playlistId)
        for (ref in refs) {
            val key = EpgMatch.bind(ref.tvgId, ref.tvgName, ref.name, aliases)
            if (key.isNotEmpty() && key != ref.epgKey) db.channels().setEpgKey(ref.id, key)
        }
    }

    suspend fun nowAndNext(playlistId: Long, channels: List<ChannelEntity>, now: Long = System.currentTimeMillis()): Map<String, List<ProgramEntity>> {
        if (channels.isEmpty()) return emptyMap()
        val until = now + 8 * 60 * 60 * 1000L
        val allKeys = LinkedHashSet<String>()
        val perChannel = HashMap<String, List<String>>(channels.size)
        for (channel in channels) {
            val keys = EpgMatch.candidates(channel.tvgId, channel.tvgName, channel.name, channel.epgKey)
            perChannel[channel.epgKey + "#" + channel.id] = keys
            allKeys += keys
        }
        val rows = allKeys.toList().chunked(80).flatMap { chunk ->
            db.programs().window(playlistId, chunk, now, until)
        }
        val grouped = rows.groupBy { it.channelKey }
        val out = HashMap<String, List<ProgramEntity>>()
        for (channel in channels) {
            val merged = ArrayList<ProgramEntity>()
            val seen = HashSet<Long>()
            for (key in perChannel[channel.epgKey + "#" + channel.id].orEmpty()) {
                for (program in grouped[key].orEmpty()) {
                    if (seen.add(program.startUtc)) merged += program
                }
            }
            if (merged.isNotEmpty()) out[channel.epgKey] = merged.sortedBy { it.startUtc }
        }
        return out
    }

    fun upcoming(channel: ChannelEntity) =
        db.programs().upcomingAny(
            channel.playlistId,
            EpgMatch.candidates(channel.tvgId, channel.tvgName, channel.name, channel.epgKey),
            System.currentTimeMillis() - 30 * 60 * 1000L,
            16,
        )

    private fun resolveUrl(playlistUrl: String?, raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = playlistUrl?.toHttpUrlOrNull() ?: return raw
        return base.resolve(raw)?.toString() ?: raw
    }
}
