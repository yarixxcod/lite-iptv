package player.vvvion.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import player.vvvion.app.BuildConfig
import player.vvvion.app.data.net.DEFAULT_USER_AGENT
import player.vvvion.app.data.net.HttpClients
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val context: Context,
    private val http: HttpClients,
) {
    fun check(manifestUrl: String): RemoteUpdate? {
        val url = manifestUrl.trim().ifEmpty { DEFAULT_MANIFEST }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        http.player.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return parseUpdateManifest(body, BuildConfig.VERSION_CODE)
        }
    }

    fun download(apkUrl: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "VvvionIPTV.apk")
        val tmp = File(dir, "VvvionIPTV.apk.part")
        val client = downloadClient
        val request = Request.Builder().url(apkUrl).header("User-Agent", DEFAULT_USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty update")
            val total = body.contentLength().coerceAtLeast(0L)
            tmp.outputStream().use { out ->
                val input = body.byteStream()
                val buffer = ByteArray(32 * 1024)
                var readTotal = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    readTotal += n
                    if (total > 0) onProgress(readTotal.toFloat() / total.toFloat())
                }
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        verify(dest)
        onProgress(1f)
        return dest
    }

    private fun verify(file: File) {
        if (file.length() < 4096) error("Update file is incomplete")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.path, 0)
        } ?: error("File is not an APK")
        if (info.packageName != context.packageName) error("Package does not match Vvvion IPTV")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        if (code <= BuildConfig.VERSION_CODE) error("Update is not newer")
    }

    private val downloadClient: OkHttpClient by lazy {
        http.player.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    companion object {
        const val DEFAULT_MANIFEST =
            "https://github.com/yarixxcod/lite-iptv/releases/latest/download/latest.json"
        const val PANEL_URL = "https://yarixxcod.github.io/lite-iptv/"
    }
}
