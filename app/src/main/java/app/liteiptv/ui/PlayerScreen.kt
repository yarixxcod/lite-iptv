@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.liteiptv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.liteiptv.R
import app.liteiptv.domain.AspectMode
import app.liteiptv.player.TrackChoice
import kotlinx.coroutines.delay

@Composable
fun PlayerPane(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    controls: Boolean,
    onOpen: () -> Unit = {},
    onZap: (Int) -> Unit = {},
) {
    val state by vm.engineState.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val sleep by vm.sleepLabel.collectAsStateWithLifecycle()
    val typed by vm.typedNumber.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val now = currentProgram(programs)
    val next = programs.firstOrNull { it.startUtc > System.currentTimeMillis() }

    LaunchedEffect(showControls, channel?.id, controls) {
        if (controls && showControls) {
            delay(6_000)
            showControls = false
            menu = null
        }
    }
    LaunchedEffect(controls) {
        if (controls) runCatching { focus.requestFocus() }
    }

    val frameMod = when (state.aspect) {
        AspectMode.R16 -> Modifier.fillMaxHeight().aspectRatio(16f / 9f)
        AspectMode.R4 -> Modifier.fillMaxHeight().aspectRatio(4f / 3f)
        else -> Modifier.fillMaxSize()
    }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (!controls) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!showControls) {
                            onZap(-1)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!showControls) {
                            onZap(1)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        showControls = !showControls
                        true
                    }
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                        showControls = true
                        true
                    }
                    else -> false
                }
            }
            .clickable {
                if (controls) showControls = !showControls else onOpen()
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = frameMod,
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view ->
                    view.player = vm.player()
                    view.resizeMode = when (state.aspect) {
                        AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
            )
        }
        if (state.weak) {
            Text(
                stringResource(R.string.weak_connection),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Accent,
            )
        }
        if (typed.isNotEmpty()) {
            Text(typed, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
        }
        state.error?.let { error ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.video_error), style = MaterialTheme.typography.titleMedium)
                Text(error, color = Muted)
                TextButton(onClick = vm::retry) { Text(stringResource(R.string.retry)) }
            }
        }
        if (controls && showControls) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Ink.copy(alpha = 0.82f))
                    .padding(20.dp),
            ) {
                Text(
                    channel?.let { "${it.number}  ${it.name}" } ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                val title = now?.title ?: stringResource(R.string.no_program)
                Text(stringResource(R.string.now) + "  " + title, color = Mist)
                LinearProgressIndicator(progress = { programProgress(now) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                next?.let { Text(stringResource(R.string.next) + "  " + clock(it.startUtc) + "  " + it.title, color = Muted) }
                sleep?.let { Text(stringResource(R.string.player_sleep) + " " + it, color = Accent) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = vm::cycleAspect) { Text(aspectLabel(state.aspect)) }
                    TextButton(onClick = { menu = "audio" }) { Text(stringResource(R.string.player_audio)) }
                    TextButton(onClick = { menu = "subs" }) { Text(stringResource(R.string.player_subs)) }
                    TextButton(onClick = { menu = "sleep" }) { Text(stringResource(R.string.player_sleep)) }
                    TextButton(onClick = vm::swapLast) { Text(stringResource(R.string.player_last)) }
                    TextButton(onClick = vm::close) { Text(stringResource(R.string.close)) }
                }
                when (menu) {
                    "audio" -> ChoiceList(state.audio) { vm.selectAudio(it); menu = null }
                    "subs" -> Column {
                        TextButton(onClick = { vm.selectSubtitle(null); menu = null }) { Text(stringResource(R.string.player_off)) }
                        ChoiceList(state.subtitles) { vm.selectSubtitle(it); menu = null }
                    }
                    "sleep" -> Row {
                        listOf(0, 15, 30, 60, 90).forEach { minutes ->
                            TextButton(onClick = { vm.sleep(minutes); menu = null }) {
                                Text(if (minutes == 0) stringResource(R.string.sleep_off) else "${minutes}m")
                            }
                        }
                    }
                }
            }
        } else if (!controls) {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(channel?.name ?: stringResource(R.string.no_program), style = MaterialTheme.typography.titleMedium)
                Text(now?.title ?: stringResource(R.string.no_program), color = Muted, maxLines = 1)
                LinearProgressIndicator(progress = { programProgress(now) }, modifier = Modifier.fillMaxWidth(0.55f).padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ChoiceList(items: List<TrackChoice>, onPick: (TrackChoice) -> Unit) {
    Column {
        items.forEach { item ->
            TextButton(onClick = { onPick(item) }) {
                Text((if (item.selected) "● " else "") + item.label)
            }
        }
    }
}

@Composable
private fun aspectLabel(mode: AspectMode): String = when (mode) {
    AspectMode.FIT -> stringResource(R.string.aspect_fit)
    AspectMode.FILL -> stringResource(R.string.aspect_fill)
    AspectMode.ZOOM -> stringResource(R.string.aspect_zoom)
    AspectMode.R16 -> stringResource(R.string.aspect_16)
    AspectMode.R4 -> stringResource(R.string.aspect_4)
}

internal fun clock(ms: Long): String {
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return format.format(java.util.Date(ms))
}
