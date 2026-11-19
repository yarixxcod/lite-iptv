package player.vvvion.app.data.importing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class RemoteImport(
    val name: String,
    val kind: String,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val body: String? = null,
)

class ImportServer(
    private val onImport: (RemoteImport) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(2)
    private var socket: ServerSocket? = null
    @Volatile var pageUrl: String? = null
        private set
    private val token = UUID.randomUUID().toString().take(10)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val server = ServerSocket(0)
        socket = server
        val host = lanIp() ?: "127.0.0.1"
        pageUrl = "http://$host:${server.localPort}/i/$token"
        Thread({
            while (running.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    pool.execute { handle(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }, "liteiptv-import").apply { isDaemon = true }.start()
    }

    fun stop() {
        running.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        pageUrl = null
    }

    private fun handle(socket: Socket) {
        try {
        socket.use { client ->
            client.soTimeout = 20_000
            val input = client.getInputStream()
            val request = ImportForms.readHttpLine(input) ?: return
            val parts = request.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore("?")
            val headers = HashMap<String, String>()
            while (true) {
                val line = ImportForms.readHttpLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length > MAX_BODY) return
            val body = if (length > 0) {
                String(ImportForms.readExact(input, length), Charsets.UTF_8)
            } else ""
            val expected = "/i/$token"
            val ok = path == expected || path == "$expected/"
            val response = when {
                !ok -> text(404, "Not found")
                method == "GET" -> text(200, page(), "text/html; charset=utf-8")
                method == "POST" -> {
                    val imported = ImportForms.parse(headers["content-type"].orEmpty(), body)
                    if (imported == null) text(400, "Could not read that submission")
                    else {
                        onImport(imported)
                        text(200, donePage(imported.name), "text/html; charset=utf-8")
                    }
                }
                else -> text(405, "Method not allowed")
            }
            BufferedOutputStream(client.getOutputStream()).use { out ->
                out.write(response)
                out.flush()
            }
        }
        } catch (_: Throwable) {
            // Keep the import listener running after a bad request or I/O error.
        }
    }

    private fun text(code: Int, body: String, type: String = "text/plain; charset=utf-8"): ByteArray {
        val bytes = body.toByteArray()
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        return head.toByteArray() + bytes
    }

    private fun page(): String = """
        <!doctype html>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Vvvion IPTV</title>
        <style>
          body{margin:0;font-family:sans-serif;background:#000;color:#fff}
          main{max-width:520px;margin:auto;padding:24px}
          h1{font-weight:600;letter-spacing:.18em;font-size:22px}
          label{display:block;margin:14px 0 6px;color:#9a9a9a}
          input,select,textarea,button{width:100%;box-sizing:border-box;border-radius:12px;border:1px solid #2a2a2a;background:#111;color:#fff;padding:12px;font-size:16px}
          button{background:#fff;color:#000;font-weight:700;border:0;margin-top:16px}
          .hint{color:#9a9a9a;font-size:14px}
        </style>
        <main>
          <h1>VVVION</h1>
          <p class="hint">Send a playlist to this TV. The page works only while Vvvion IPTV is waiting, and nothing is sent anywhere else.</p>
          <form method="post" enctype="multipart/form-data" id="playlist-form">
            <label>Name</label>
            <input name="name" placeholder="Living room">
            <label>Type</label>
            <select name="kind">
              <option value="url">M3U URL</option>
              <option value="xtream">Xtream Codes</option>
              <option value="file">M3U file</option>
            </select>
            <label>URL or server</label>
            <input name="url" placeholder="https://example/playlist.m3u">
            <label>Xtream server</label>
            <input name="server" placeholder="http://host:port">
            <label>Username</label>
            <input name="username">
            <label>Password</label>
            <input name="password" type="password">
            <label>Or choose an M3U file</label>
            <input id="playlist-file" type="file" accept=".m3u,.m3u8,text/plain">
            <textarea name="file" id="playlist-text" hidden></textarea>
            <button type="submit">Add on TV</button>
          </form>
          <script>
            const form = document.getElementById("playlist-form");
            form.addEventListener("submit", async function(event) {
              const type = form.elements.namedItem("kind").value;
              if (type !== "file") return;
              event.preventDefault();
              const file = document.getElementById("playlist-file").files[0];
              if (!file) { alert("Choose an M3U file"); return; }
              if (file.size > 8 * 1024 * 1024) { alert("File is too large (max 8 MB)"); return; }
              document.getElementById("playlist-text").value = await file.text();
              form.submit();
            });
          </script>
        </main>
    """.trimIndent()

    private fun donePage(name: String) = """
        <!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <body style="background:#0b0e13;color:#e8eef2;font-family:sans-serif;padding:32px">
        <h1>Added</h1><p>${escape(name)} is on the TV. You can close this page.</p>
    """.trimIndent()

    private fun escape(s: String) = s.replace("&", "&").replace("<", "<")

    companion object {
        private const val MAX_BODY = 20 * 1024 * 1024

        fun lanIp(): String? {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
            return null
        }
    }
}

fun qrBitmap(contents: String, size: Int = 640): Bitmap {
    val matrix = QRCodeWriter().encode(
        contents,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (y in 0 until size) {
        for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.rgb(11, 14, 19) else Color.WHITE)
        }
    }
    return bitmap
}
