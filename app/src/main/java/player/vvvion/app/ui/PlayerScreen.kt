@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package player.vvvion.app.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import player.vvvion.app.R
import player.vvvion.app.domain.AspectMode
import player.vvvion.app.player.SignalQuality
import player.vvvion.app.player.TrackChoice
import kotlinx.coroutines.delay

@Composable
fun PlayerPane(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    controls: Boolean,
    attachVideo: Boolean = true,
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
    val next = programs.firstOrNull { program -> program.startUtc > (now?.startUtc ?: 0L) && program.id != now?.id }
        ?: programs.firstOrNull { it.startUtc > System.currentTimeMillis() }

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
        AspectMode.FILL, AspectMode.ZOOM -> Modifier.fillMaxSize()
        else -> {
            val ratio = if (state.videoWidth > 0 && state.videoHeight > 0) {
                state.videoWidth.toFloat() / state.videoHeight
            } else {
                16f / 9f
            }
            Modifier.fillMaxHeight().aspectRatio(ratio, matchHeightConstraintsFirst = true)
        }
    }
    if (controls) {
        BackHandler {
            when {
                menu != null -> menu = null
                showControls -> showControls = false
                else -> vm.close()
            }
        }
    }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focus)
            .focusable()
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
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls) {
                            showControls = true
                            menu = "audio"
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showControls) {
                            showControls = true
                            vm.cycleAspect()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!showControls) {
                            showControls = true
                            true
                        } else false
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
                modifier = frameMod.graphicsLayer {
                    if (state.aspect == AspectMode.ZOOM) {
                        scaleX = 1.28f
                        scaleY = 1.28f
                    }
                },
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = true
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    if (attachVideo) vm.attachTexture(view) else vm.detachTexture(view)
                },
                onRelease = { view -> vm.detachTexture(view) },
            )
        }
        if (state.rebuffering && state.error == null) {
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.buffering), color = Color.White)
            }
        }
        SignalBadge(
            quality = state.signal,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
        )
        if (typed.isNotEmpty()) {
            Text(typed, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
        }
        state.error?.let { error ->
            Column(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.video_error), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(error, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::retry, modifier = Modifier.tvControl()) { Text(stringResource(R.string.retry)) }
                    if (vm.hasBackup()) {
                        TextButton(onClick = { vm.nextSource() }, modifier = Modifier.tvControl()) {
                            Text(stringResource(R.string.next_source))
                        }
                    }
                }
            }
        }
        if (controls && showControls) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerLogo(channel?.logo)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                channel?.let { "${it.number}  ${it.name}" } ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.now) + "  " + (now?.title ?: stringResource(R.string.no_program)),
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OverlayClock()
                        IconButton(onClick = vm::close, modifier = Modifier.tvControl()) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { programProgress(now) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    next?.let {
                        Text(
                            stringResource(R.string.next) + "  " + clock(it.startUtc) + "  " + it.title,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    sleep?.let { Text(stringResource(R.string.player_sleep) + " " + it, color = Accent) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = vm::cycleAspect, modifier = Modifier.tvControl()) { Text(aspectLabel(state.aspect)) }
                        TextButton(onClick = { menu = "audio" }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.player_audio)) }
                        TextButton(onClick = { menu = "subs" }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.player_subs)) }
                        TextButton(onClick = { menu = "sleep" }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.player_sleep)) }
                        TextButton(onClick = vm::swapLast, modifier = Modifier.tvControl()) { Text(stringResource(R.string.player_last)) }
                        if (vm.hasBackup()) {
                            TextButton(onClick = { vm.nextSource() }, modifier = Modifier.tvControl()) {
                                Text(stringResource(R.string.next_source))
                            }
                        }
                    }
                    when (menu) {
                        "audio" -> ChoiceList(state.audio) { vm.selectAudio(it); menu = null }
                        "subs" -> Column {
                            TextButton(onClick = { vm.selectSubtitle(null); menu = null }, modifier = Modifier.tvControl()) {
                                Text(stringResource(R.string.player_off))
                            }
                            ChoiceList(state.subtitles) { vm.selectSubtitle(it); menu = null }
                        }
                        "sleep" -> Row {
                            listOf(0, 15, 30, 60, 90).forEach { minutes ->
                                TextButton(onClick = { vm.sleep(minutes); menu = null }, modifier = Modifier.tvControl()) {
                                    Text(if (minutes == 0) stringResource(R.string.sleep_off) else "${minutes}m")
                                }
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
private fun SignalBadge(quality: SignalQuality, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(quality) {
        if (quality == SignalQuality.HIDDEN) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (quality != SignalQuality.STALLED) {
            delay(5_000)
            visible = false
        }
    }
    if (!visible && quality != SignalQuality.STALLED) return
    val color = when (quality) {
        SignalQuality.STALLED, SignalQuality.POOR -> Color(0xFFEF9A9A)
        SignalQuality.FAIR -> Color(0xFFFFD54F)
        else -> Color(0xFF81C784)
    }
    val filled = when (quality) {
        SignalQuality.STALLED, SignalQuality.POOR -> 1
        SignalQuality.FAIR -> 2
        else -> 4
    }
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (index in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((6 + index * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index <= filled) color else Color.White.copy(alpha = 0.22f)),
            )
        }
    }
}

@Composable
private fun OverlayClock() {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000)
        }
    }
    Text(clock(nowMs), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun PlayerLogo(url: String?) {
    val shape = RoundedCornerShape(8.dp)
    if (url.isNullOrBlank()) {
        Box(Modifier.size(48.dp).clip(shape).background(Color.White.copy(alpha = 0.12f)))
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(64)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(shape).background(Color.White.copy(alpha = 0.12f)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ChoiceList(items: List<TrackChoice>, onPick: (TrackChoice) -> Unit) {
    Column {
        items.forEach { item ->
            TextButton(onClick = { onPick(item) }, modifier = Modifier.tvControl()) {
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
