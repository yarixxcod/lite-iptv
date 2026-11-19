package player.vvvion.app.player

import player.vvvion.app.data.db.ChannelEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class PlaybackCoordinator {
    val channel = MutableStateFlow<ChannelEntity?>(null)
    val fullscreen = MutableStateFlow(false)
    val textFocused = MutableStateFlow(false)
    val digits = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val zap = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    fun select(channel: ChannelEntity) {
        this.channel.value = channel
    }

    fun openFull(channel: ChannelEntity? = null) {
        if (channel != null) this.channel.value = channel
        fullscreen.value = true
    }

    fun closeFull() {
        fullscreen.value = false
    }
}
