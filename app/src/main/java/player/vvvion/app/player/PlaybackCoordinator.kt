package player.vvvion.app.player

import player.vvvion.app.data.db.ChannelEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

enum class RemoteScope {
    /** Channel list or groups: CH+/CH−, page, digits change the current channel. */
    CHANNELS,
    /** Fullscreen player: the same keys zap or enter a number. */
    PLAYER,
    /** Settings, editor, dialogs, search: channel keys must not fire. */
    UI,
}

class PlaybackCoordinator {
    val channel = MutableStateFlow<ChannelEntity?>(null)
    val fullscreen = MutableStateFlow(false)
    val textFocused = MutableStateFlow(false)
    val remoteScope = MutableStateFlow(RemoteScope.CHANNELS)
    val lastListChannelId = MutableStateFlow(-1L)
    val digits = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val zap = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    fun select(channel: ChannelEntity) {
        this.channel.value = channel
        lastListChannelId.value = channel.id
    }

    fun openFull(channel: ChannelEntity? = null) {
        if (channel != null) {
            this.channel.value = channel
            lastListChannelId.value = channel.id
        }
        fullscreen.value = true
        remoteScope.value = RemoteScope.PLAYER
    }

    fun closeFull() {
        fullscreen.value = false
        if (remoteScope.value == RemoteScope.PLAYER) remoteScope.value = RemoteScope.CHANNELS
    }

    fun allowChannelKeys(): Boolean {
        if (textFocused.value) return false
        return remoteScope.value == RemoteScope.CHANNELS || remoteScope.value == RemoteScope.PLAYER
    }
}
