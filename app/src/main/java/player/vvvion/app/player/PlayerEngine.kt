@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package player.vvvion.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import player.vvvion.app.data.net.HttpClients
import player.vvvion.app.domain.AspectMode
import player.vvvion.app.domain.BufferProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlin.random.Random

data class TrackChoice(val label: String, val groupIndex: Int, val trackIndex: Int, val selected: Boolean)

data class EngineState(
    val playing: Boolean = false,
    val rebuffering: Boolean = false,
    val error: String? = null,
    val weak: Boolean = false,
    val aspect: AspectMode = AspectMode.FIT,
    val audio: List<TrackChoice> = emptyList(),
    val subtitles: List<TrackChoice> = emptyList(),
    val subtitlesOff: Boolean = true,
)

class RetryPolicy : DefaultLoadErrorHandlingPolicy(8) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.errorCount > 8) return C.TIME_UNSET
        val shift = (loadErrorInfo.errorCount - 1).coerceIn(0, 6)
        val base = min(30_000L, 400L shl shift)
        return base + Random.nextInt(0, 350)
    }
}

class PlayerEngine(
    context: Context,
    private val http: HttpClients,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var profile: BufferProfile? = null
    private var userAgent: String? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var networkHooked = false
    private val bandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(appContext)
            .setInitialBitrateEstimate(350_000L)
            .build()
    }

    fun player(): ExoPlayer? = player

    fun play(
        urls: List<String>,
        profile: BufferProfile,
        maxHeight: Int,
        maxBitrateKbps: Int,
        userAgent: String,
        muted: Boolean,
    ): Boolean {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return false
        return try {
            this.urls = clean
            urlIndex = 0
            ensurePlayer(profile, userAgent)
            applyCaps(maxHeight, maxBitrateKbps)
            val exo = player ?: return false
            exo.volume = if (muted) 0f else 1f
            prepare(exo, clean.first())
            hookNetwork()
            _state.value = _state.value.copy(error = null, rebuffering = true)
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                error = t.message?.take(180) ?: t.javaClass.simpleName,
                rebuffering = false,
                playing = false,
            )
            false
        }
    }

    fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun stop() {
        player?.stop()
        _state.value = _state.value.copy(playing = false, rebuffering = false)
    }

    fun retry() {
        val exo = player ?: return
        val url = urls.getOrNull(urlIndex) ?: return
        exo.playWhenReady = true
        prepare(exo, url)
    }

    fun reconnectIfNeeded() {
        val exo = player ?: return
        if (exo.playerError != null || exo.playbackState == Player.STATE_IDLE) {
            retry()
        }
    }

    fun cycleAspect() {
        val next = AspectMode.entries[( _state.value.aspect.ordinal + 1) % AspectMode.entries.size]
        _state.value = _state.value.copy(aspect = next)
    }

    fun selectAudio(choice: TrackChoice) = select(choice, C.TRACK_TYPE_AUDIO)

    fun selectSubtitle(choice: TrackChoice?) {
        val exo = player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (choice == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
            _state.value = _state.value.copy(subtitlesOff = true)
        } else {
            val group = exo.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
            _state.value = _state.value.copy(subtitlesOff = false)
        }
        exo.trackSelectionParameters = builder.build()
    }

    fun release() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        player?.release()
        player = null
    }

    private fun select(choice: TrackChoice, type: Int) {
        val exo = player ?: return
        val group = exo.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
        if (group.type != type) return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
            .build()
    }

    private fun ensurePlayer(profile: BufferProfile, userAgent: String) {
        if (player != null && this.profile == profile && this.userAgent == userAgent) return
        player?.release()
        this.profile = profile
        this.userAgent = userAgent
        val dataSource = OkHttpDataSource.Factory(http.player).setUserAgent(userAgent)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.bufferForPlaybackMs,
                profile.bufferForRebufferMs,
            )
            .setBackBuffer(15_000, true)
            .build()
        val selector = DefaultTrackSelector(appContext)
        val factory = DefaultMediaSourceFactory(dataSource).setLoadErrorHandlingPolicy(RetryPolicy())
        player = ExoPlayer.Builder(appContext)
            .setRenderersFactory(DefaultRenderersFactory(appContext).setEnableDecoderFallback(true))
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(factory)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _state.value = _state.value.copy(
                            playing = exo.isPlaying,
                            rebuffering = playbackState == Player.STATE_BUFFERING,
                            error = if (playbackState == Player.STATE_READY) null else _state.value.error,
                        )
                        publishWeak()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(playing = isPlaying, rebuffering = exo.playbackState == Player.STATE_BUFFERING && !isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                            return
                        }
                        if (!advanceUrl()) {
                            _state.value = _state.value.copy(error = error.errorCodeName, rebuffering = false, playing = false)
                        }
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        publishTracks(exo)
                    }
                })
            }
    }

    private fun applyCaps(maxHeight: Int, maxBitrateKbps: Int) {
        val exo = player ?: return
        val selector = exo.trackSelector as? DefaultTrackSelector ?: return
        val height = if (maxHeight > 0) maxHeight else Int.MAX_VALUE
        val bitrate = if (maxBitrateKbps > 0) maxBitrateKbps * 1000 else Int.MAX_VALUE
        selector.parameters = selector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, height)
            .setMaxVideoBitrate(bitrate)
            .build()
    }

    private fun prepare(exo: ExoPlayer, url: String) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeFor(url))
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(if (profile == BufferProfile.LOW_LATENCY) 2_500 else 12_000)
                    .setMinPlaybackSpeed(0.98f)
                    .setMaxPlaybackSpeed(1.02f)
                    .build(),
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun advanceUrl(): Boolean {
        if (urlIndex + 1 >= urls.size) return false
        urlIndex++
        val exo = player ?: return false
        prepare(exo, urls[urlIndex])
        _state.value = _state.value.copy(error = null, rebuffering = true)
        return true
    }

    private fun publishTracks(exo: ExoPlayer) {
        val audio = ArrayList<TrackChoice>()
        val subs = ArrayList<TrackChoice>()
        exo.currentTracks.groups.forEachIndexed { groupIndex, group ->
            val target = when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> subs
                else -> null
            } ?: return@forEachIndexed
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val label = format.label ?: format.language ?: if (group.type == C.TRACK_TYPE_AUDIO) "Audio ${i + 1}" else "Subtitle ${i + 1}"
                target += TrackChoice(label, groupIndex, i, group.isTrackSelected(i))
            }
        }
        _state.value = _state.value.copy(audio = audio, subtitles = subs)
    }

    private fun publishWeak() {
        val estimate = bandwidthMeter.bitrateEstimate
        val weak = _state.value.rebuffering || (estimate in 1 until 700_000)
        if (weak != _state.value.weak) _state.value = _state.value.copy(weak = weak)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            reconnectIfNeeded()
            _state.value = _state.value.copy(weak = false)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val down = caps.linkDownstreamBandwidthKbps
            if (down in 1 until 800) _state.value = _state.value.copy(weak = true)
        }

        override fun onLost(network: Network) {
            _state.value = _state.value.copy(weak = true)
        }
    }

    private fun hookNetwork() {
        if (networkHooked) return
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            if (Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkCallback)
            else cm.registerNetworkCallback(request, networkCallback)
            networkHooked = true
        } catch (_: Throwable) {
        }
    }

    private fun mimeFor(url: String): String? {
        val lower = url.lowercase().substringBefore('?')
        return when {
            lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            lower.startsWith("rtsp://") -> MimeTypes.APPLICATION_RTSP
            else -> null
        }
    }
}
