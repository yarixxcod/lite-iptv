package player.vvvion.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.IllegalStateException

class CrashLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writesLatestReportAndKeepsOnlyFive() {
        val log = CrashLog(folder.root)
        repeat(6) { index ->
            log.write(Thread.currentThread(), IllegalStateException("boom-$index"))
        }
        val files = log.files()
        assertEquals(5, files.size)
        val latest = log.latest()
        assertNotNull(latest)
        assertTrue(latest!!.contains("boom-5"))
        assertTrue(latest.contains("IllegalStateException"))
    }

    @Test
    fun installWritesThenDelegatesToPreviousHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val seen = arrayOfNulls<Throwable>(1)
        Thread.setDefaultUncaughtExceptionHandler { _, error -> seen[0] = error }
        try {
            val log = CrashLog(folder.root)
            log.install()
            val error = IllegalArgumentException("piped")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertEquals(error, seen[0])
            assertTrue(log.latest()!!.contains("piped"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
