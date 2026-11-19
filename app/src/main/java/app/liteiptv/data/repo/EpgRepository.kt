package app.liteiptv.data.repo

import android.content.Context
import app.liteiptv.data.db.AppDatabase
import app.liteiptv.data.db.ProgramEntity
import app.liteiptv.data.net.HttpClients
import app.liteiptv.data.net.downloadToFile
import app.liteiptv.data.net.openMaybeCompressed
import app.liteiptv.data.parse.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

class EpgRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val http: HttpClients,
    private val parser: XmltvParser,
) {
    suspend fun sync(playlistId: Long) = withContext(Dispatchers.IO) {
        val playlist = db.playlists().get(playlistId) ?: return@withContext
        val url = playlist.epgUrl?.takeIf { it.isNotBlank() } ?: return@withContext
        val dest = File(context.cacheDir, "epg-$playlistId.bin").apply { parentFile?.mkdirs() }
        val remote = try {
            downloadToFile(http.playlist, url, dest, playlist.userAgent, playlist.epgEtag, playlist.epgLastModified)
        } catch (t: Throwable) {
            db.playlists().updateEpgStatus(playlistId, playlist.epgUrl, playlist.epgEtag, playlist.epgLastModified, System.currentTimeMillis(), t.message)
            throw t
        }
        if (remote.notModified) {
            db.playlists().updateEpgStatus(playlistId, playlist.epgUrl, remote.etag, remote.lastModified, System.currentTimeMillis(), null)
            return@withContext
        }
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 60 * 60 * 1000L
        val windowEnd = now + 48 * 60 * 60 * 1000L
        val batch = ArrayList<ProgramEntity>(300)
        var stored = 0
        fun flush() {
            if (batch.isNotEmpty()) {
                db.programs().insertAll(batch.toList())
                stored += batch.size
                batch.clear()
            }
        }
        val stream = openMaybeCompressed(dest).buffered()
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
                offsetMinutes = playlist.tzOffsetMinutes,
            ) { programs ->
                for (program in programs) {
                    batch += ProgramEntity(
                        playlistId = playlistId,
                        channelKey = program.channelId,
                        title = program.title,
                        description = program.description,
                        startUtc = program.startUtc,
                        stopUtc = program.stopUtc,
                    )
                    if (batch.size >= 300) flush()
                }
            }
            flush()
        }
        stream.close()
        val error = if (stored == 0) "No programmes in the stored window" else null
        db.playlists().updateEpgStatus(playlistId, playlist.epgUrl, remote.etag, remote.lastModified, System.currentTimeMillis(), error)
    }

    suspend fun nowAndNext(playlistId: Long, keys: List<String>, now: Long = System.currentTimeMillis()): Map<String, List<ProgramEntity>> {
        if (keys.isEmpty()) return emptyMap()
        val until = now + 8 * 60 * 60 * 1000L
        val rows = keys.distinct().chunked(80).flatMap { chunk ->
            db.programs().window(playlistId, chunk, now, until)
        }
        return rows.groupBy { it.channelKey }
    }

    fun upcoming(playlistId: Long, key: String) =
        db.programs().upcoming(playlistId, key, System.currentTimeMillis() - 30 * 60 * 1000L, 16)
}
