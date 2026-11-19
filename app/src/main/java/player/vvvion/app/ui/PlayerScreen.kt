@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package player.vvvion.app.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.TextureView
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import player.vvvion.app.R
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.domain.AspectMode
import player.vvvion.app.player.SignalQuality
import player.vvvion.app.player.TrackChoice
import kotlin.math.abs

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
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    var miniInfo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var hideTick by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val surfaceFocus = remember { FocusRequester() }
    val favoriteFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(false) }
    val now = currentProgram(programs)
    val next = programs.firstOrNull { program -> program.startUtc > (now?.startUtc ?: 0L) && program.id != now?.id }
        ?: programs.firstOrNull { it.startUtc > System.currentTimeMillis() }
    val addedText = stringResource(R.string.favorite_added)
    val removedText = stringResource(R.string.favorite_removed)

    fun bumpHide() {
        hideTick++
    }

    fun openHud() {
        showControls = true
        miniInfo = false
        bumpHide()
    }

    fun hideHud() {
        menu = null
        showControls = false
        miniInfo = false
    }

    fun zap(delta: Int) {
        onZap(delta)
        showControls = false
        menu = null
        miniInfo = true
    }

    fun toggleFavorite() {
        vm.toggleFavorite { added ->
            notice = if (added) addedText else removedText
        }
    }

    LaunchedEffect(showControls, hideTick, menu, controls, channel?.id) {
        if (controls && showControls && menu == null) {
            delay(8_000)
            hideHud()
        }
    }
    LaunchedEffect(showControls, controls) {
        if (!controls) return@LaunchedEffect
        delay(50)
        if (showControls) runCatching { favoriteFocus.requestFocus() }
        else runCatching { surfaceFocus.requestFocus() }
    }
    LaunchedEffect(miniInfo, channel?.id) {
        if (miniInfo && !showControls) {
            delay(3_000)
            miniInfo = false
        }
    }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        delay(2_500)
        if (notice == text) notice = null
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
                showControls || miniInfo -> hideHud()
                else -> vm.close()
            }
        }
    }
    Box(
        modifier
            .background(Color.Black)
            .focusRequester(surfaceFocus)
            .focusProperties { canFocus = !showControls }
            .focusable()
            .onKeyEvent { event ->
                if (!controls) return@onKeyEvent false
                val native = event.nativeKeyEvent
                val down = native.action == KeyEvent.ACTION_DOWN
                val up = native.action == KeyEvent.ACTION_UP
                if (showControls) {
                    if (down && (native.keyCode == KeyEvent.KEYCODE_MENU || native.keyCode == KeyEvent.KEYCODE_INFO)) {
                        bumpHide()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (down && native.repeatCount == 0) {
                            longPressFired = false
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(ViewConfiguration.getLongPressTimeout().toLong())
                                longPressFired = true
                                longPressJob = null
                                toggleFavorite()
                            }
                            true
                        } else if (up) {
                            val pending = longPressJob
                            longPressJob = null
                            pending?.cancel()
                            if (!longPressFired) openHud()
                            true
                        } else {
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (down) {
                            zap(-1)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) {
                            zap(1)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO,
                    -> {
                        if (down) {
                            openHud()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_BOOKMARK -> {
                        if (down) {
                            toggleFavorite()
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .pointerInput(controls, showControls) {
                detectTapGestures {
                    if (controls) {
                        if (showControls) hideHud() else openHud()
                    } else {
                        onOpen()
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = frameMod
                    .focusProperties { canFocus = false }
                    .graphicsLayer {
                        if (state.aspect == AspectMode.ZOOM) {
                            scaleX = 1.28f
                            scaleY = 1.28f
                        }
                    },
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = true
                        keepScreenOn = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                    }
                },
                update = { view ->
                    view.isFocusable = false
                    view.isFocusableInTouchMode = false
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
        if (typed.isNotEmpty()) {
            Text(typed, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopStart).padding(24.dp), color = Color.White)
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
                    HudAction(label = stringResource(R.string.retry), onClick = vm::retry, onUsed = { bumpHide() })
                    if (vm.hasBackup()) {
                        HudAction(label = stringResource(R.string.next_source), onClick = { vm.nextSource() }, onUsed = { bumpHide() })
                    }
                }
            }
        }
        notice?.let { text ->
            Text(
                text,
                color = AccentInk,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            qualityLabel(state.videoWidth, state.videoHeight)?.let { label ->
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            SignalBadge(quality = state.signal)
        }
        if (controls && showControls) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    )
                    .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 20.dp),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerLogo(channel?.logo, channel?.name)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (favorite) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(22.dp).padding(end = 6.dp),
                                    )
                                }
                                Text(
                                    channel?.let { "${it.number}  ${it.name}" } ?: stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val nowTitle = now?.title ?: stringResource(R.string.no_program_short)
                            val nowTimes = now?.let { clock(it.startUtc) + "–" + clock(it.stopUtc) }
                            Text(
                                stringResource(R.string.now) + "  " + (nowTimes?.let { "$it  " } ?: "") + nowTitle,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OverlayClock()
                    }
                    if (now != null) {
                        LinearProgressIndicator(
                            progress = { programProgress(now) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                            color = Accent,
                            trackColor = Color.White.copy(alpha = 0.2f),
                        )
                        val leftMin = ((now.stopUtc - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
                        Text(stringResource(R.string.remaining_minutes, leftMin), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                    }
                    next?.let {
                        Text(
                            stringResource(R.string.next) + "  " + clock(it.startUtc) + "–" + clock(it.stopUtc) + "  " + it.title,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    sleep?.let { Text(stringResource(R.string.player_sleep) + " " + it, color = Accent, style = MaterialTheme.typography.titleMedium) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudAction(
                                label = stringResource(R.string.player_favorite),
                                icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                accent = favorite,
                                focusRequester = favoriteFocus,
                                onClick = { toggleFavorite(); bumpHide() },
                                onUsed = { bumpHide() },
                            )
                            HudAction(label = stringResource(R.string.player_aspect), onClick = { menu = if (menu == "aspect") null else "aspect"; bumpHide() }, onUsed = { bumpHide() })
                            HudAction(label = stringResource(R.string.player_audio), onClick = { menu = if (menu == "audio") null else "audio"; bumpHide() }, onUsed = { bumpHide() })
                            HudAction(label = stringResource(R.string.player_subs), onClick = { menu = if (menu == "subs") null else "subs"; bumpHide() }, onUsed = { bumpHide() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudAction(label = stringResource(R.string.player_sleep), onClick = { menu = if (menu == "sleep") null else "sleep"; bumpHide() }, onUsed = { bumpHide() })
                            HudAction(label = stringResource(R.string.player_last_channel), onClick = { vm.swapLast(); bumpHide() }, onUsed = { bumpHide() })
                            HudAction(
                                label = stringResource(R.string.player_channels),
                                icon = Icons.Filled.List,
                                onClick = { vm.close() },
                                onUsed = { bumpHide() },
                            )
                            if (vm.hasBackup()) {
                                HudAction(label = stringResource(R.string.next_source), onClick = { vm.nextSource(); bumpHide() }, onUsed = { bumpHide() })
                            }
                        }
                    }
                    when (menu) {
                        "aspect" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AspectMode.entries.forEach { mode ->
                                HudAction(
                                    label = aspectLabel(mode),
                                    accent = state.aspect == mode,
                                    onClick = { vm.setAspect(mode); menu = null; bumpHide() },
                                    onUsed = { bumpHide() },
                                )
                            }
                        }
                        "audio" -> ChoiceList(state.audio, onUsed = { bumpHide() }) { vm.selectAudio(it); menu = null }
                        "subs" -> Column {
                            HudAction(label = stringResource(R.string.player_off), onClick = { vm.selectSubtitle(null); menu = null; bumpHide() }, onUsed = { bumpHide() })
                            ChoiceList(state.subtitles, onUsed = { bumpHide() }) { vm.selectSubtitle(it); menu = null }
                        }
                        "sleep" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 15, 30, 60, 90).forEach { minutes ->
                                HudAction(
                                    label = if (minutes == 0) stringResource(R.string.sleep_off) else "${minutes}m",
                                    onClick = { vm.sleep(minutes); menu = null; bumpHide() },
                                    onUsed = { bumpHide() },
                                )
                            }
                        }
                    }
                }
            }
        } else if (controls && miniInfo) {
            MiniZapInfo(channel, now, favorite, Modifier.align(Alignment.BottomStart).padding(20.dp))
        } else if (!controls) {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(channel?.name ?: stringResource(R.string.no_program), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(now?.title ?: stringResource(R.string.no_program_short), color = Muted, maxLines = 1)
                LinearProgressIndicator(progress = { programProgress(now) }, modifier = Modifier.fillMaxWidth(0.55f).padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MiniZapInfo(
    channel: ChannelEntity?,
    now: player.vvvion.app.data.db.ProgramEntity?,
    favorite: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 560.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerLogo(channel?.logo, channel?.name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp).padding(end = 4.dp))
                }
                Text(
                    channel?.let { "${it.number}  ${it.name}" }.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                now?.title ?: stringResource(R.string.no_program_short),
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HudAction(
    label: String,
    onClick: () -> Unit,
    onUsed: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (accent) Accent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f), shape)
            .tvControl(onFocused = onUsed)
            .clickable {
                onUsed()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (accent) AccentInk else Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (accent) AccentInk else Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
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
    Text(clock(nowMs), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = Color.White, modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun PlayerLogo(url: String?, name: String?, size: androidx.compose.ui.unit.Dp = 64.dp) {
    val shape = RoundedCornerShape(12.dp)
    val initials = channelInitials(name)
    val tile = initialsColor(name)
    if (url.isNullOrBlank()) {
        Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(96)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape).background(tile),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ChoiceList(items: List<TrackChoice>, onUsed: () -> Unit, onPick: (TrackChoice) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            HudAction(
                label = (if (item.selected) "● " else "") + item.label,
                accent = item.selected,
                onClick = { onPick(item) },
                onUsed = onUsed,
            )
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

private fun qualityLabel(width: Int, height: Int): String? {
    val resolved = when {
        height >= 1080 || width >= 1920 -> "1080p"
        height >= 720 || width >= 1280 -> "720p"
        height >= 480 || width >= 720 -> "480p"
        height > 0 -> "${height}p"
        else -> return null
    }
    return if (height >= 720 || width >= 1280) "$resolved · HD" else resolved
}

private fun channelInitials(name: String?): String {
    val words = name.orEmpty().trim().split(" ").filter { it.any { ch -> ch.isLetterOrDigit() } }
    return when {
        words.size >= 2 -> "${words[0].first { it.isLetterOrDigit() }}${words[1].first { it.isLetterOrDigit() }}"
        words.isNotEmpty() -> words[0].filter { it.isLetterOrDigit() }.take(2)
        else -> "TV"
    }.uppercase()
}

private fun initialsColor(name: String?): Color {
    val palette = listOf(
        Color(0xFF3D4A7A),
        Color(0xFF5A3D6E),
        Color(0xFF2F5D62),
        Color(0xFF6A3F4B),
        Color(0xFF3E5A3D),
        Color(0xFF4A4A6A),
    )
    return palette[abs((name ?: "").hashCode()) % palette.size]
}

internal fun clock(ms: Long): String {
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return format.format(java.util.Date(ms))
}
