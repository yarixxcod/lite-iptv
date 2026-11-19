package player.vvvion.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.ProgramEntity
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.data.repo.EpgRepository
import player.vvvion.app.data.repo.LibraryRepository
import player.vvvion.app.data.repo.altList
import player.vvvion.app.player.EngineState
import player.vvvion.app.player.PlaybackCoordinator
import player.vvvion.app.player.PlayerEngine
import player.vvvion.app.player.TrackChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val engine: PlayerEngine,
    private val library: LibraryRepository,
    private val epgRepo: EpgRepository,
    private val settingsStore: SettingsStore,
    private val playback: PlaybackCoordinator,
) : ViewModel() {
    val engineState = engine.state
    val fullscreen = playback.fullscreen
    val channel = playback.channel
    val sleepLabel = MutableStateFlow<String?>(null)
    val typedNumber = MutableStateFlow("")
    val programs = playback.channel.flatMapLatest { current ->
        if (current == null) flowOf(emptyList()) else epgRepo.upcoming(current.playlistId, current.epgKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastKey = ""
    private var previous: ChannelEntity? = null
    private var sleepJob: Job? = null
    private var numberBuffer = ""
    private var numberJob: Job? = null

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(playback.channel, playback.fullscreen, settingsStore.settings) { current, full, settings ->
                Triple(current, full, settings)
            }.collect { (current, full, settings) ->
                if (current == null) return@collect
                val shouldPlay = full || settings.livePreview
                if (!shouldPlay) {
                    engine.pause()
                    return@collect
                }
                val key = listOf(current.id, settings.buffer.name, settings.maxHeight, settings.maxBitrateKbps, settings.userAgent).joinToString("|")
                if (key != lastKey) {
                    val started = try {
                        engine.play(
                            urls = altList(current),
                            profile = settings.buffer,
                            maxHeight = settings.maxHeight,
                            maxBitrateKbps = settings.maxBitrateKbps,
                            userAgent = settings.effectiveUserAgent(null),
                            muted = !full,
                        )
                    } catch (_: Throwable) {
                        false
                    }
                    if (!started) return@collect
                    lastKey = key
                    if (full) {
                        try {
                            library.remember(current, 0)
                            settingsStore.update { it.copy(lastChannelId = current.id, lastPlaylistId = current.playlistId) }
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    engine.setMuted(!full)
                    engine.resume()
                }
            }
        }
        viewModelScope.launch {
            playback.zap.collect { delta -> zap(delta) }
        }
        viewModelScope.launch {
            playback.digits.collect { digit ->
                if (playback.fullscreen.value) pushDigit(digit)
            }
        }
    }

    fun zap(delta: Int) {
        val current = playback.channel.value ?: return
        viewModelScope.launch {
            val next = library.neighbor(current, delta) ?: return@launch
            previous = current
            playback.select(next)
        }
    }

    fun swapLast() {
        val current = playback.channel.value ?: return
        val target = previous ?: return
        previous = current
        playback.select(target)
    }

    fun player() = engine.player()

    fun retry() = engine.retry()

    fun cycleAspect() = engine.cycleAspect()

    fun selectAudio(choice: TrackChoice) = engine.selectAudio(choice)

    fun selectSubtitle(choice: TrackChoice?) = engine.selectSubtitle(choice)

    fun sleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepLabel.value = null
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) {
                    engine.stop()
                    sleepLabel.value = null
                    playback.closeFull()
                    break
                }
                sleepLabel.value = "${(left / 60_000L).toInt()}m"
                delay(1_000)
            }
        }
    }

    fun close() = playback.closeFull()

    private fun pushDigit(digit: Int) {
        numberBuffer = (numberBuffer + digit).takeLast(4)
        typedNumber.value = numberBuffer
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(1100)
            val number = numberBuffer.toIntOrNull()
            numberBuffer = ""
            typedNumber.value = ""
            val playlistId = playback.channel.value?.playlistId ?: return@launch
            if (number != null) library.channelByNumber(playlistId, number)?.let { playback.select(it) }
        }
    }
}

fun currentProgram(programs: List<ProgramEntity>, now: Long = System.currentTimeMillis()): ProgramEntity? =
    programs.firstOrNull { it.startUtc <= now && it.stopUtc > now } ?: programs.firstOrNull { it.stopUtc > now }

fun programProgress(program: ProgramEntity?, now: Long = System.currentTimeMillis()): Float {
    if (program == null || program.stopUtc <= program.startUtc) return 0f
    if (now < program.startUtc) return 0f
    return ((now - program.startUtc).toFloat() / (program.stopUtc - program.startUtc)).coerceIn(0f, 1f)
}
