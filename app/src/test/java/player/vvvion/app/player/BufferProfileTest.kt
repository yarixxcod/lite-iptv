package player.vvvion.app.player

import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.Assert.assertTrue
import org.junit.Test
import player.vvvion.app.domain.BufferProfile

class BufferProfileTest {
    @Test
    fun durationsStayWithinLoadControlRules() {
        BufferProfile.entries.forEach { profile ->
            assertTrue(profile.name, profile.minBufferMs >= profile.bufferForPlaybackMs)
            assertTrue(profile.name, profile.minBufferMs >= profile.bufferForRebufferMs)
            assertTrue(profile.name, profile.maxBufferMs >= profile.minBufferMs)
        }
    }

    @Test
    fun defaultProfilesBuildALoadControl() {
        BufferProfile.entries.forEach { profile ->
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    profile.minBufferMs,
                    profile.maxBufferMs,
                    profile.bufferForPlaybackMs,
                    profile.bufferForRebufferMs,
                )
                .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        }
    }
}
