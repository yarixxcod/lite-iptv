package player.vvvion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.vvvion.app.R
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.ProgramEntity
import java.util.Calendar

@Composable
fun GuideScreen(
    vm: AppViewModel,
    programs: List<ProgramEntity>,
    modifier: Modifier = Modifier,
) {
    val television = LocalTelevision.current
    val playlistId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    var channels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var dayOffset by remember { mutableIntStateOf(0) }
    var dayPrograms by remember { mutableStateOf<List<ProgramEntity>>(emptyList()) }
    var focusedProgram by remember { mutableStateOf<ProgramEntity?>(null) }
    val currentChannel = channels.firstOrNull { it.id == selectedId } ?: channels.firstOrNull()
    val (dayStart, dayEnd) = remember(dayOffset) { dayBounds(dayOffset) }

    LaunchedEffect(playlistId) {
        channels = vm.loadGuideChannels()
        if (selectedId == null || channels.none { it.id == selectedId }) {
            selectedId = channels.firstOrNull()?.id
        }
    }
    LaunchedEffect(currentChannel?.id, dayStart, dayEnd) {
        val channel = currentChannel
        dayPrograms = if (channel == null) emptyList() else vm.loadProgramsForDay(channel, dayStart, dayEnd)
        val nowMs = System.currentTimeMillis()
        focusedProgram = dayPrograms.firstOrNull { it.startUtc <= nowMs && it.stopUtc > nowMs }
            ?: dayPrograms.firstOrNull { it.startUtc >= nowMs }
            ?: dayPrograms.firstOrNull()
    }

    if (channels.isEmpty() && programs.isEmpty()) {
        Column(modifier.padding(24.dp)) {
            Text(stringResource(R.string.tab_guide), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.epg_empty), color = Muted)
        }
        return
    }

    Row(modifier.fillMaxSize().padding(if (television) 12.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideChannelList(
            channels = channels,
            selectedId = currentChannel?.id,
            television = television,
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).fillMaxHeight().weight(0.38f, fill = false),
            onSelect = { selectedId = it.id },
            onPlay = { channel -> vm.selectChannel(channel, true) },
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(stringResource(R.string.tab_guide), style = MaterialTheme.typography.headlineMedium)
            currentChannel?.let {
                Text("${it.number}  ${it.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to R.string.day_yesterday, 0 to R.string.day_today, 1 to R.string.day_tomorrow).forEach { (offset, label) ->
                    TvChip(
                        selected = dayOffset == offset,
                        onClick = { dayOffset = offset },
                        label = stringResource(label),
                    )
                }
            }
            if (dayPrograms.isEmpty()) {
                Text(stringResource(R.string.epg_empty), color = Muted, modifier = Modifier.padding(top = 16.dp))
            } else {
                val listState = rememberLazyListState()
                val currentId = focusedProgram?.id
                LaunchedEffect(currentChannel?.id, dayOffset, dayPrograms) {
                    val index = dayPrograms.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
                    listState.scrollToItem(index)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(dayPrograms, key = { it.id }, contentType = { "program" }) { program ->
                        GuideProgramRow(
                            program = program,
                            selected = focusedProgram?.id == program.id,
                            television = television,
                            onFocus = { focusedProgram = program },
                            onClick = {
                                focusedProgram = program
                                val nowMs = System.currentTimeMillis()
                                val current = program.startUtc <= nowMs && program.stopUtc > nowMs
                                if (current) {
                                    currentChannel?.let { vm.selectChannel(it, true) }
                                }
                            },
                        )
                    }
                }
                val details = focusedProgram
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(14.dp),
                ) {
                    Text(stringResource(R.string.program_details), style = MaterialTheme.typography.titleSmall, color = Muted)
                    if (details == null) {
                        Text(stringResource(R.string.guide_no_description), color = Muted)
                    } else {
                        Text(
                            clock(details.startUtc) + "–" + clock(details.stopUtc) + "  " + details.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            details.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.guide_no_description),
                            color = Muted,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelList(
    channels: List<ChannelEntity>,
    selectedId: Long?,
    television: Boolean,
    modifier: Modifier,
    onSelect: (ChannelEntity) -> Unit,
    onPlay: (ChannelEntity) -> Unit,
) {
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(channels, key = { it.id }, contentType = { "guide-ch" }) { channel ->
            val selected = channel.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow)
                    .tvFocusable(enabled = television, onFocused = { onSelect(channel) })
                    .clickable { onSelect(channel); onPlay(channel) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelLogo(channel.logo, channel.name)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${channel.number}  ${channel.name}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideProgramRow(
    program: ProgramEntity,
    selected: Boolean,
    television: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val nowMs = System.currentTimeMillis()
    val current = program.startUtc <= nowMs && program.stopUtc > nowMs
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.surfaceContainerHighest
                    current -> MaterialTheme.colorScheme.surfaceContainer
                    else -> MaterialTheme.colorScheme.background
                },
            )
            .tvFocusable(enabled = television, onFocused = onFocus)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clock(program.startUtc) + "–" + clock(program.stopUtc),
                style = MaterialTheme.typography.labelLarge,
                color = if (current) Accent else Muted,
                modifier = Modifier.widthIn(min = 96.dp),
            )
            if (current) {
                Text(
                    stringResource(R.string.now_live),
                    color = AccentInk,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(program.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        if (current) {
            LinearProgressIndicator(
                progress = { programProgress(program, nowMs) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(99.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

private fun dayBounds(offsetDays: Int): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.add(Calendar.DATE, offsetDays)
    val start = calendar.timeInMillis
    calendar.add(Calendar.DATE, 1)
    return start to calendar.timeInMillis
}
