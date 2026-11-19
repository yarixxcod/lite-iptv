package player.vvvion.app.domain

/**
 * Staging rows use a negative playlist id so a failed or empty XMLTV parse
 * never wipes the programmes the user already has.
 */
object EpgSync {
    fun stagingId(playlistId: Long): Long = -playlistId - 1L

    fun shouldReplace(stored: Int): Boolean = stored > 0
}
