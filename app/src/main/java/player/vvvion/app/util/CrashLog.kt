package player.vvvion.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class CrashLog(private val dir: File) {
    private val sequence = AtomicInteger()

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            if (previous != null) previous.uncaughtException(thread, error)
            else throw error
        }
    }

    fun write(thread: Thread, error: Throwable) {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp-${sequence.getAndIncrement()}.txt")
        val body = StringWriter()
        body.append("Thread: ${thread.name}\n")
        body.append("Time: $stamp\n\n")
        error.printStackTrace(PrintWriter(body))
        file.writeText(body.toString())
        dir.listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }

    fun latest(): String? = files().firstOrNull()?.readText()

    fun files(): List<File> = dir.listFiles { f -> f.name.startsWith("crash-") }
        ?.sortedByDescending { it.name }
        .orEmpty()

    companion object {
        private const val KEEP = 5

        fun inApp(context: Context) = CrashLog(File(context.filesDir, "crashes"))
    }
}
