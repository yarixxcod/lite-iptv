package player.vvvion.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import player.vvvion.app.data.db.GroupRow

class HiddenGroupsTest {
    @Test
    fun allGroupsCannotBeRemoved() {
        assertFalse(HiddenGroups.canRemove(""))
        assertFalse(HiddenGroups.canRemove("   "))
        assertTrue(HiddenGroups.canRemove("Кино"))
    }

    @Test
    fun hideAndRestoreAreKeyedPerPlaylist() {
        val first = HiddenGroups.hide(emptySet(), 7L, "Кино")
        val both = HiddenGroups.hide(first, 8L, "Кино")
        assertEquals(setOf("7|Кино"), HiddenGroups.titles(7L, both).map { HiddenGroups.key(7L, it) }.toSet())
        assertEquals(setOf("Кино"), HiddenGroups.titles(7L, both))
        assertEquals(setOf("Кино"), HiddenGroups.titles(8L, both))
        val restored = HiddenGroups.restore(both, 7L, "Кино")
        assertTrue(HiddenGroups.titles(7L, restored).isEmpty())
        assertEquals(setOf("Кино"), HiddenGroups.titles(8L, restored))
    }

    @Test
    fun restoreAllLeavesOtherPlaylists() {
        val keys = HiddenGroups.hide(HiddenGroups.hide(emptySet(), 1L, "A"), 2L, "B")
        val next = HiddenGroups.restoreAll(keys, 1L)
        assertTrue(HiddenGroups.titles(1L, next).isEmpty())
        assertEquals(setOf("B"), HiddenGroups.titles(2L, next))
    }

    @Test
    fun titlesKeepPipesInsideGroupName() {
        val keys = HiddenGroups.hide(emptySet(), 12L, "News|World")
        assertEquals(setOf("News|World"), HiddenGroups.titles(12L, keys))
        assertTrue(HiddenGroups.isHidden(12L, "News|World", keys))
        assertFalse(HiddenGroups.isHidden(12L, "News", keys))
    }

    @Test
    fun visibleRowsAndCountSkipHiddenTitles() {
        val rows = listOf(
            GroupRow("Azerbaijan", 17),
            GroupRow("Беларусь", 17),
            GroupRow("Кино", 157),
        )
        val visible = HiddenGroups.visible(rows, setOf("Кино"))
        assertEquals(listOf("Azerbaijan", "Беларусь"), visible.map { it.title })
        assertEquals(34, HiddenGroups.visibleCount(visible))
        assertEquals(191, HiddenGroups.visibleCount(rows))
    }

    @Test
    fun forSqlUsesSentinelWhenNothingIsHidden() {
        assertEquals(listOf(HiddenGroups.SQL_NONE), HiddenGroups.forSql(emptySet()))
        assertEquals(listOf("Кино", "News"), HiddenGroups.forSql(listOf("Кино", "News")))
    }

    @Test
    fun hideIgnoresBlankTitles() {
        assertTrue(HiddenGroups.hide(emptySet(), 1L, "").isEmpty())
        assertTrue(HiddenGroups.hide(emptySet(), 1L, "  ").isEmpty())
    }

    @Test
    fun entriesAreSortedAndFilterable() {
        val keys = setOf("2|Beta", "10|Alpha", "2|alpha", "bad", "|Nope", "x|Y")
        assertEquals(
            listOf(2L to "alpha", 2L to "Beta", 10L to "Alpha"),
            HiddenGroups.entries(keys),
        )
        assertEquals(listOf(2L to "alpha", 2L to "Beta"), HiddenGroups.entries(keys, 2L))
    }
}
