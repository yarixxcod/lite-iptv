package player.vvvion.app.domain

enum class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForRebufferMs: Int,
) {
    AUTO(15_000, 55_000, 2_500, 5_000),
    LOW_LATENCY(2_000, 15_000, 500, 1_000),
    STABLE(15_000, 60_000, 2_500, 5_000),
    MAX_STABILITY(30_000, 120_000, 5_000, 10_000),
    ;

    companion object {
        fun fromKey(key: String?): BufferProfile =
            entries.firstOrNull { it.name == key } ?: STABLE
    }
}

enum class AspectMode { FIT, FILL, ZOOM, R16, R4 }
