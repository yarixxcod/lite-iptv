package app.liteiptv.data.net

import app.liteiptv.data.parse.ParsedChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

data class RemoteFile(
    val file: File,
    val etag: String?,
    val lastModified: String?,
    val notModified: Boolean,
)

fun downloadToFile(
    client: OkHttpClient,
    url: String,
    dest: File,
    userAgent: String?,
    etag: String?,
    lastModified: String?,
): RemoteFile {
    val builder = Request.Builder().url(url).header("User-Agent", userAgent.orEmpty().ifBlank { DEFAULT_USER_AGENT })
    if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
    if (!lastModified.isNullOrBlank()) builder.header("If-Modified-Since", lastModified)
    client.newCall(builder.build()).execute().use { response ->
        if (response.code == 304) {
            return RemoteFile(dest, etag, lastModified, true)
        }
        if (!response.isSuccessful) {
            error("HTTP ${response.code} for $url")
        }
        val body = response.body ?: error("Empty response")
        dest.parentFile?.mkdirs()
        body.byteStream().use { input -> dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
        return RemoteFile(
            file = dest,
            etag = response.header("ETag") ?: etag,
            lastModified = response.header("Last-Modified") ?: lastModified,
            notModified = false,
        )
    }
}

fun openMaybeCompressed(file: File): InputStream {
    val raw = file.inputStream()
    val buffered = raw.buffered()
    buffered.mark(8)
    val magic = ByteArray(6)
    val n = buffered.read(magic)
    buffered.reset()
    return when {
        n >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GZIPInputStream(buffered)
        n >= 6 && magic[0] == 0xFD.toByte() && magic[1] == 0x37.toByte() -> XZInputStream(buffered)
        else -> buffered
    }
}

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
)

@Serializable
data class XtreamStream(
    val num: Int? = null,
    val name: String? = null,
    @SerialName("stream_id") val streamId: Int? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("tv_archive") val tvArchive: Int? = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int? = null,
)

fun normalizeServer(raw: String): String {
    var base = raw.trim()
    if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
    base = base.substringBefore("?")
    base = base.removeSuffix("/")
    listOf("/player_api.php", "/get.php", "/xmltv.php", "/live").forEach { suffix ->
        if (base.endsWith(suffix, ignoreCase = true)) base = base.dropLast(suffix.length)
    }
    return base.trimEnd('/')
}

fun xtreamEpgUrl(base: String, user: String, pass: String): String {
    return "$base/xmltv.php?username=${enc(user)}&password=${enc(pass)}"
}

fun xtreamLiveUrl(base: String, user: String, pass: String, streamId: Int): String {
    return "$base/live/${enc(user)}/${enc(pass)}/$streamId.m3u8"
}

private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

fun fetchXtreamCategories(client: OkHttpClient, json: Json, base: String, user: String, pass: String, ua: String?): Map<String, String> {
    val url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_categories"
    val body = getString(client, url, ua)
    if (!body.trimStart().startsWith("[")) return emptyMap()
    return json.decodeFromString<List<XtreamCategory>>(body)
        .mapNotNull { cat ->
            val id = cat.categoryId ?: return@mapNotNull null
            id to (cat.categoryName?.ifBlank { null } ?: "Live")
        }
        .toMap()
}

fun forEachXtreamStream(
    client: OkHttpClient,
    json: Json,
    base: String,
    user: String,
    pass: String,
    ua: String?,
    onStream: (XtreamStream) -> Unit,
) {
    val url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_streams"
    val request = Request.Builder().url(url).header("User-Agent", ua.orEmpty().ifBlank { DEFAULT_USER_AGENT }).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Xtream HTTP ${response.code}")
        val reader = (response.body ?: error("Empty Xtream response")).charStream().buffered()
        forEachJsonObject(reader) { raw ->
            val stream = json.decodeFromString<XtreamStream>(raw)
            onStream(stream)
        }
    }
}

fun m3uChannelFromXtream(base: String, user: String, pass: String, categories: Map<String, String>, stream: XtreamStream): ParsedChannel? {
    val id = stream.streamId ?: return null
    val name = stream.name?.trim().orEmpty().ifBlank { "Channel $id" }
    val group = categories[stream.categoryId.orEmpty()] ?: "Live"
    val archive = stream.tvArchive == 1
    return ParsedChannel(
        name = name,
        url = xtreamLiveUrl(base, user, pass, id),
        tvgId = stream.epgChannelId?.takeIf { it.isNotBlank() },
        tvgName = name,
        logo = stream.streamIcon?.takeIf { it.isNotBlank() },
        group = group,
        number = stream.num,
        catchup = if (archive) "shift" else null,
        catchupDays = if (archive) stream.tvArchiveDuration else null,
        catchupSource = null,
    )
}

private fun getString(client: OkHttpClient, url: String, ua: String?): String {
    val http = url.toHttpUrlOrNull() ?: error("Bad URL")
    val request = Request.Builder().url(http).header("User-Agent", ua.orEmpty().ifBlank { DEFAULT_USER_AGENT }).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        return response.body?.string().orEmpty()
    }
}

internal fun forEachJsonObject(reader: Reader, onObject: (String) -> Unit) {
    while (true) {
        val c = reader.read()
        if (c < 0) return
        if (c.toChar() == '[') break
        if (c.toChar() == '{') return
    }
    val sb = StringBuilder()
    var depth = 0
    var quote = false
    var escape = false
    while (true) {
        val c = reader.read()
        if (c < 0) break
        val ch = c.toChar()
        if (quote) {
            sb.append(ch)
            if (escape) escape = false
            else if (ch == '\\') escape = true
            else if (ch == '"') quote = false
            continue
        }
        when (ch) {
            '"' -> {
                quote = true
                if (depth > 0) sb.append(ch)
            }
            '{' -> {
                depth++
                sb.append(ch)
            }
            '}' -> if (depth > 0) {
                sb.append(ch)
                depth--
                if (depth == 0) {
                    onObject(sb.toString())
                    sb.setLength(0)
                }
            }
            else -> if (depth > 0) sb.append(ch)
        }
    }
}
