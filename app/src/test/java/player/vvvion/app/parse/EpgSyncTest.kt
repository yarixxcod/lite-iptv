package player.vvvion.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import player.vvvion.app.domain.EpgMatch
import player.vvvion.app.domain.EpgSync
import player.vvvion.app.ui.currentProgram
import player.vvvion.app.data.db.ProgramEntity

class EpgSyncTest {
    @Test
    fun stagingIdIsNegativeAndStable() {
        assertEquals(-2L, EpgSync.stagingId(1))
        assertEquals(-11L, EpgSync.stagingId(10))
        assertNotEquals(5L, EpgSync.stagingId(5))
        assertTrue(EpgSync.stagingId(5) < 0)
        assertEquals(EpgSync.stagingId(7), EpgSync.stagingId(7))
    }

    @Test
    fun replaceOnlyWhenNewRowsExist() {
        assertFalse(EpgSync.shouldReplace(0))
        assertTrue(EpgSync.shouldReplace(1))
        assertTrue(EpgSync.shouldReplace(400))
    }

    @Test
    fun listFallsBackToNextProgramme() {
        val now = 1_000_000L
        val upcoming = ProgramEntity(1, 1, "ch", "Later", null, now + 60_000, now + 120_000)
        val current = ProgramEntity(2, 1, "ch", "Now", null, now - 10_000, now + 10_000)
        assertEquals("Now", currentProgram(listOf(upcoming, current), now)?.title)
        assertEquals("Later", currentProgram(listOf(upcoming), now)?.title)
        assertEquals(null, currentProgram(emptyList(), now))
    }

    @Test
    fun bindPrefersTvgIdThenName() {
        val aliases = HashMap<String, String>()
        EpgMatch.addAliases(aliases, "RTR.HD", listOf("Россия 1 HD", "Russia 1"))
        assertEquals("rtrhd", EpgMatch.bind("rtr.hd", null, "Other", aliases))
        assertEquals("rtrhd", EpgMatch.bind(null, "Россия 1 HD", "Other", aliases))
    }
}
