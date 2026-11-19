@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package player.vvvion.app.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import player.vvvion.app.BuildConfig
import player.vvvion.app.R
import kotlinx.coroutines.delay
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.PlaylistEntity
import player.vvvion.app.data.prefs.AppSettings
import player.vvvion.app.data.prefs.lockKey
import player.vvvion.app.data.repo.ChannelSort
import player.vvvion.app.data.repo.LibraryRepository
import player.vvvion.app.data.repo.PlaylistDraft
import player.vvvion.app.domain.BufferProfile
import player.vvvion.app.domain.HiddenGroups
import player.vvvion.app.player.RemoteScope
import player.vvvion.app.update.RemoteUpdate
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun HomeScreen(
    vm: AppViewModel,
    playerVm: PlayerViewModel,
    width: WindowWidthSizeClass,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    television: Boolean = false,
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val editor by vm.editorOpen.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val epgNow by vm.epgNow.collectAsStateWithLifecycle()
    val epgStatus by vm.epgStatus.collectAsStateWithLifecycle()
    val favoriteUrls by vm.favoriteUrls.collectAsStateWithLifecycle()
    val epgSyncStamp = playlists.firstOrNull { it.id == playlistId }?.lastSyncAt
    val typed by vm.typedNumber.collectAsStateWithLifecycle()
    val pinGroup by vm.pinGroup.collectAsStateWithLifecycle()
    val pendingHideGroup by vm.pendingHideGroup.collectAsStateWithLifecycle()
    val qr by vm.qrPng.collectAsStateWithLifecycle()
    val qrUrl by vm.qrUrl.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val preview by playerVm.channel.collectAsStateWithLifecycle()
    val playingFull by playerVm.fullscreen.collectAsStateWithLifecycle()
    val offer by vm.availableUpdate.collectAsStateWithLifecycle()
    val updatePhase by vm.updatePhase.collectAsStateWithLifecycle()
    val updateFraction by vm.updateFraction.collectAsStateWithLifecycle()
    val expanded = television || width == WindowWidthSizeClass.Expanded
    val medium = television || width != WindowWidthSizeClass.Compact
    var compactList by remember { mutableStateOf(false) }

    BackHandler(enabled = !medium && compactList && !editor) { compactList = false }
    BackHandler(enabled = editor) { vm.closeEditor() }

    LaunchedEffect(editor, pinGroup, pendingHideGroup, tab) {
        val scope = when {
            editor || pinGroup != null || pendingHideGroup != null -> RemoteScope.UI
            tab == HomeTab.SETTINGS || tab == HomeTab.GUIDE -> RemoteScope.UI
            else -> RemoteScope.CHANNELS
        }
        vm.setRemoteScope(scope)
    }

    ProvideTelevision(television) {
    Box(Modifier.fillMaxSize()) {
    if (editor) {
        EditorScreen(
            existing = editing,
            busy = busy,
            qr = qr,
            qrUrl = qrUrl,
            onClose = vm::closeEditor,
            onSave = vm::save,
            onPickFile = onPickFile,
            onPaste = onPaste,
            onStartQr = vm::startPhoneLink,
            onStopQr = vm::stopPhoneLink,
            onTextFocus = vm::setTextFocused,
        )
    } else {

    val compact = !television && width == WindowWidthSizeClass.Compact
    val playlistName = playlists.firstOrNull { it.id == playlistId }?.name
    val navItems = listOf(
        Triple(HomeTab.LIVE, stringResource(R.string.tab_live), Icons.Filled.Home),
        Triple(HomeTab.FAVORITES, stringResource(R.string.tab_favorites), Icons.Filled.Favorite),
        Triple(HomeTab.GUIDE, stringResource(R.string.tab_guide), Icons.Filled.DateRange),
        Triple(HomeTab.SETTINGS, stringResource(R.string.tab_settings), Icons.Filled.Settings),
    )
    fun openTab(next: HomeTab) {
        vm.tab.value = next
        compactList = next != HomeTab.LIVE
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.padding(start = 12.dp).size(32.dp),
                    )
                },
                title = {
                    Column {
                        Text("VVVION", style = MaterialTheme.typography.titleLarge)
                        Text(
                            playlistName ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openEditor(null) }, modifier = Modifier.tvControl()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_playlist))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (compact) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    navItems.forEach { (value, label, icon) ->
                        NavigationBarItem(
                            selected = tab == value,
                            onClick = { openTab(value) },
                            modifier = Modifier.tvControl(),
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(if (television) 18.dp else 0.dp).fillMaxSize()) {
            offer?.let {
                UpdateBanner(it, updatePhase, updateFraction, vm::downloadUpdate, vm::dismissUpdate)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!compact) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        navItems.forEach { (value, label, icon) ->
                            NavigationRailItem(
                                selected = tab == value,
                                onClick = { openTab(value) },
                                modifier = Modifier.tvControl(),
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                }
                if (playlists.isEmpty() && tab != HomeTab.SETTINGS) {
                    EmptyLibrary(onAdd = { vm.openEditor(null) })
                    return@Row
                }
                when (tab) {
                    HomeTab.SETTINGS -> SettingsScreen(
                        vm = vm,
                        settings = settings,
                        playlists = playlists,
                        epgStatus = epgStatus,
                        modifier = Modifier.weight(1f),
                    )
                    HomeTab.GUIDE -> GuideScreen(
                        vm = vm,
                        programs = guide,
                        modifier = Modifier.weight(1f),
                        onOpen = { playerVm.channel.value?.let { vm.selectChannel(it, true) } },
                    )
                    else -> {
                        if (medium || !compactList) {
                            if (tab == HomeTab.LIVE) {
                                GroupColumn(
                                    groups = groups,
                                    selected = group,
                                    locked = settings.lockedGroups,
                                    playlistId = playlistId,
                                    onSelect = {
                                        vm.selectGroup(it)
                                        if (!medium) compactList = true
                                    },
                                    onLock = vm::toggleLock,
                                    onRemove = vm::requestHideGroup,
                                    television = television,
                                    modifier = if (medium) Modifier.width(if (television) 220.dp else 280.dp).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                        if (medium || compactList || tab == HomeTab.FAVORITES) {
                            ChannelColumn(
                                vm = vm,
                                query = query,
                                sort = sort,
                                epgNow = epgNow,
                                favoriteUrls = favoriteUrls,
                                epgSyncStamp = epgSyncStamp,
                                recent = recent,
                                favorites = tab == HomeTab.FAVORITES,
                                typed = typed,
                                television = television,
                                modifier = when {
                                    television -> Modifier.width(340.dp).fillMaxHeight()
                                    expanded -> Modifier.width(420.dp).fillMaxHeight()
                                    medium -> Modifier.width(360.dp).fillMaxHeight()
                                    else -> Modifier.weight(1f).fillMaxHeight()
                                },
                                onBack = if (!medium && tab == HomeTab.LIVE) {
                                    { compactList = false }
                                } else {
                                    null
                                },
                                onOpen = { vm.selectChannel(it, television || !expanded) },
                                onPreview = { vm.selectChannel(it, false) },
                            )
                        }
                        if (expanded) {
                            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                                PlayerPane(
                                    vm = playerVm,
                                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)),
                                    controls = false,
                                    attachVideo = !playingFull,
                                    onOpen = { preview?.let { vm.selectChannel(it, true) } },
                                )
                                Spacer(Modifier.height(12.dp))
                                val programs = guide
                                val now = currentProgram(programs)
                                Text(preview?.name ?: stringResource(R.string.no_program), style = MaterialTheme.typography.titleLarge)
                                Text(now?.let { stringResource(R.string.now) + "  " + it.title } ?: stringResource(R.string.no_program), color = Muted)
                                now?.description?.let { Text(it, maxLines = 3, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
        BusyStrip(busy)
        MessageBar(banner, onDismiss = vm::dismissBanner, onUndo = { message ->
            message.raw?.let(vm::undoHideGroup)
        })
    }
    pinGroup?.let {
        PinDialog(onDismiss = vm::dismissPin, onSubmit = vm::submitPin)
    }
    pendingHideGroup?.let { title ->
        HideGroupDialog(
            title = title,
            onDismiss = vm::dismissHideGroup,
            onConfirm = vm::confirmHideGroup,
        )
    }
    } // box
    } // television
}

@Composable
private fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.brand_mark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(52.dp),
        )
        Text("VVVION", fontSize = 12.sp, letterSpacing = 1.4.sp)
        Text("IPTV", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun UpdateBanner(
    offer: RemoteUpdate,
    phase: String,
    fraction: Float,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Accent).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            stringResource(R.string.update_available, offer.versionName),
            color = AccentInk,
            style = MaterialTheme.typography.titleMedium,
        )
        if (offer.notes.isNotBlank()) {
            Text(offer.notes, color = AccentInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (phase == "downloading") {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = AccentInk,
                trackColor = InkRaised,
            )
        } else {
            Row {
                TextButton(onClick = onDownload, modifier = Modifier.tvControl()) { Text(stringResource(R.string.update_download), color = AccentInk) }
                TextButton(onClick = onDismiss, modifier = Modifier.tvControl()) { Text(stringResource(R.string.update_dismiss), color = Muted) }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        BrandMark()
        Text(stringResource(R.string.no_playlists), color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onAdd, modifier = Modifier.tvControl()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  " + stringResource(R.string.add_playlist))
        }
    }
}

@Composable
private fun GroupColumn(
    groups: List<player.vvvion.app.data.db.GroupRow>,
    selected: String,
    locked: Set<String>,
    playlistId: Long,
    onSelect: (String) -> Unit,
    onLock: (String) -> Unit,
    onRemove: (String) -> Unit,
    television: Boolean,
    modifier: Modifier,
) {
    val rows = groups.distinctBy { it.title }
    val allCount = HiddenGroups.visibleCount(rows)
    LazyColumn(modifier.background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(8.dp)) {
        item {
            GroupCard(
                title = stringResource(R.string.all_groups) + "  " + allCount,
                selected = selected.isEmpty(),
                locked = false,
                television = television,
                onClick = { onSelect("") },
                onLock = null,
                onRemove = null,
            )
        }
        items(rows, key = { it.title }, contentType = { "group" }) { row ->
            val isLocked = lockKey(playlistId, row.title) in locked
            GroupCard(
                title = "${row.title}  ${row.count}",
                selected = selected == row.title,
                locked = isLocked,
                television = television,
                onClick = { onSelect(row.title) },
                onLock = { onLock(row.title) },
                onRemove = { onRemove(row.title) },
            )
        }
    }
}

private fun Modifier.tvFocus(enabled: Boolean, onFocused: () -> Unit = {}): Modifier =
    tvFocusable(enabled = enabled, shape = RoundedCornerShape(16.dp), onFocused = onFocused)


@Composable
private fun GroupCard(
    title: String,
    selected: Boolean,
    locked: Boolean,
    television: Boolean,
    onClick: () -> Unit,
    onLock: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (television) 6.dp else 4.dp)
            .tvFocus(television)
            .onKeyEvent { event ->
                if (onRemove == null || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO) {
                    onRemove()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onRemove),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        ListItem(
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = when {
                onLock != null -> {
                    {
                        IconButton(onClick = onLock, modifier = Modifier.tvControl()) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.lock_group),
                                tint = if (locked) MaterialTheme.colorScheme.primary else Muted,
                            )
                        }
                    }
                }
                locked -> {
                    { Icon(Icons.Filled.Lock, contentDescription = null) }
                }
                else -> null
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun ChannelColumn(
    vm: AppViewModel,
    query: String,
    sort: ChannelSort,
    epgNow: Map<String, List<player.vvvion.app.data.db.ProgramEntity>>,
    favoriteUrls: Set<String>,
    epgSyncStamp: Long?,
    recent: List<player.vvvion.app.data.db.HistoryEntity>,
    favorites: Boolean,
    typed: String,
    television: Boolean,
    modifier: Modifier,
    onBack: (() -> Unit)? = null,
    onOpen: (ChannelEntity) -> Unit,
    onPreview: (ChannelEntity) -> Unit,
) {
    val paging = vm.channels.collectAsLazyPagingItems()
    val restoreId by vm.lastListChannelId.collectAsStateWithLifecycle()
    var restoredFocus by remember(restoreId, favorites, query, sort) { mutableStateOf(false) }
    LaunchedEffect(paging.itemSnapshotList, epgSyncStamp) {
        vm.onVisible(paging.itemSnapshotList.items.filterNotNull())
    }
    Column(modifier) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
            }
        }
        TvTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            label = stringResource(R.string.search),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            onEditingChange = vm::setTextFocused,
        )
        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvChip(selected = sort == ChannelSort.NUMBER, onClick = { vm.sort.value = ChannelSort.NUMBER }, label = stringResource(R.string.sort_number))
            TvChip(selected = sort == ChannelSort.NAME, onClick = { vm.sort.value = ChannelSort.NAME }, label = stringResource(R.string.sort_name))
            if (typed.isNotEmpty()) Text(typed, color = Accent, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (!favorites && recent.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.playlistId.toString() + it.streamUrl }, contentType = { "recent" }) { item ->
                    TextButton(onClick = { vm.openHistory(item.channelId) }) { Text(item.channelName, maxLines = 1) }
                }
            }
        }
        if (paging.itemCount == 0) {
            Text(
                stringResource(if (favorites) R.string.favorites_empty else R.string.channels_empty),
                color = Muted,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(
                count = paging.itemCount,
                key = { index -> paging.peek(index)?.id?.let { "c$it#$index" } ?: "p$index" },
                contentType = { "channel" },
            ) { index ->
                val channel = paging[index] ?: return@items
                val itemFocus = remember(channel.id) { FocusRequester() }
                LaunchedEffect(channel.id, restoreId, restoredFocus) {
                    if (!restoredFocus && restoreId == channel.id) {
                        if (runCatching { itemFocus.requestFocus() }.isSuccess) restoredFocus = true
                    }
                }
                ListItem(
                    modifier = Modifier
                        .heightIn(min = if (television) 76.dp else 0.dp)
                        .focusRequester(itemFocus)
                        .tvFocus(television) {
                            onPreview(channel)
                            vm.rememberListChannel(channel.id)
                        }
                        .combinedClickable(
                            onClick = { onPreview(channel); onOpen(channel) },
                            onLongClick = { vm.toggleFavorite(channel) },
                        ),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = { ChannelLogo(channel.logo) },
                    headlineContent = {
                        Text("${channel.number}   ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { ChannelProgramLine(epgNow[channel.epgKey].orEmpty()) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (favorites) {
                                IconButton(onClick = { vm.moveFavorite(channel, -1) }, modifier = Modifier.tvControl()) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                                }
                                IconButton(onClick = { vm.moveFavorite(channel, 1) }, modifier = Modifier.tvControl()) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                                }
                            }
                            IconButton(onClick = { vm.toggleFavorite(channel) }, modifier = Modifier.tvControl()) {
                                Icon(
                                    if (channel.streamUrl in favoriteUrls) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { vm.hide(channel) }, modifier = Modifier.tvControl()) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.hide_channel))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelProgramLine(programs: List<player.vvvion.app.data.db.ProgramEntity>) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(programs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val now = remember(programs, nowMs) {
        programs.firstOrNull { it.startUtc <= nowMs && it.stopUtc > nowMs }
            ?: programs.firstOrNull { it.stopUtc > nowMs }
    }
    Column {
        Text(
            now?.title ?: stringResource(R.string.no_program),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (now != null) {
            LinearProgressIndicator(
                progress = { programProgress(now, nowMs) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun ChannelLogo(url: String?) {
    if (url.isNullOrBlank()) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(InkLine))
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).size(64).bitmapConfig(Bitmap.Config.RGB_565).crossfade(false).build(),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(InkLine),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun GuideScreen(
    vm: AppViewModel,
    programs: List<player.vvvion.app.data.db.ProgramEntity>,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    var rows by remember { mutableStateOf<List<GuideRow>>(emptyList()) }
    val playlistId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    LaunchedEffect(playlistId, group) { rows = vm.loadGuideRows() }
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.tab_guide), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.play)) }
        }
        if (rows.isEmpty() && programs.isEmpty()) {
            item { Text(stringResource(R.string.epg_empty), color = Muted) }
        }
        items(rows, key = { it.channel.id }, contentType = { "guide" }) { row ->
            Column {
                Text(row.channel.name, style = MaterialTheme.typography.titleMedium)
                ProgramStrip(row.programs)
            }
        }
        item {
            Text(stringResource(R.string.program_details), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
        items(programs, key = { it.id }, contentType = { "program" }) { program ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(clock(program.startUtc) + " – " + clock(program.stopUtc) + "   " + program.title, style = MaterialTheme.typography.titleMedium)
                program.description?.let { Text(it, color = Muted, maxLines = 4) }
            }
        }
    }
}

@Composable
private fun ProgramStrip(programs: List<player.vvvion.app.data.db.ProgramEntity>) {
    val start = System.currentTimeMillis() - 20 * 60_000L
    val window = 3 * 60 * 60_000f
    Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).background(InkRaised)) {
        programs.forEach { program ->
            val left = ((program.startUtc - start) / window).coerceIn(0f, 1f)
            val right = ((program.stopUtc - start) / window).coerceIn(0f, 1f)
            if (right <= left) return@forEach
            Box(
                Modifier.fillMaxWidth(right - left).padding(start = 0.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = androidx.compose.ui.unit.lerp(0.dp, 1000.dp, left))
            ) {}
            Text(
                program.title,
                maxLines = 1,
                modifier = Modifier.padding(start = androidx.compose.ui.unit.lerp(4.dp, 800.dp, left), top = 8.dp),
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, settings: AppSettings, playlists: List<PlaylistEntity>, epgStatus: player.vvvion.app.data.repo.EpgStatus, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.update_version, BuildConfig.VERSION_NAME), color = Muted)
            Text(stringResource(R.string.update_panel), color = Muted)
            Button(onClick = { vm.openEditor(null) }, modifier = Modifier.tvControl().padding(top = 8.dp)) { Text(stringResource(R.string.add_playlist)) }
            Button(onClick = { vm.checkForUpdate(false) }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.update_check)) }
        }
        item { EpgStatusCard(epgStatus) }
        item { DiagnosticsCard() }
        item { HiddenGroupsCard(vm, settings, playlists) }
        item {
            TvTextField(
                value = settings.updateManifestUrl,
                onValueChange = { raw -> vm.updateSettings { it.copy(updateManifestUrl = raw) } },
                label = stringResource(R.string.update_source),
                onEditingChange = vm::setTextFocused,
            )
        }
        item { Text(stringResource(R.string.settings_playlists), style = MaterialTheme.typography.titleLarge) }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                    Text(playlist.type + (playlist.lastError?.let { " · $it" } ?: ""), color = Muted, maxLines = 1)
                }
                TextButton(onClick = { vm.selectPlaylist(playlist.id); vm.refresh() }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.refresh)) }
                TextButton(onClick = { vm.openEditor(playlist) }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { vm.deletePlaylist(playlist.id) }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.delete)) }
            }
        }
        item { Text(stringResource(R.string.settings_playback), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        item { Text(stringResource(R.string.settings_buffer)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BufferProfile.entries.forEach { profile ->
                    TvChip(
                        selected = settings.buffer == profile,
                        onClick = { vm.updateSettings { it.copy(buffer = profile) } },
                        label = bufferLabel(profile),
                    )
                }
            }
        }
        item { HeightChoice(settings.maxHeight) { height -> vm.updateSettings { it.copy(maxHeight = height) } } }
        item {
            TvTextField(
                value = if (settings.maxBitrateKbps == 0) "" else settings.maxBitrateKbps.toString(),
                onValueChange = { raw -> vm.updateSettings { it.copy(maxBitrateKbps = raw.toIntOrNull() ?: 0) } },
                label = stringResource(R.string.settings_max_bitrate),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onEditingChange = vm::setTextFocused,
            )
        }
        item { ToggleRow(stringResource(R.string.settings_preview), settings.livePreview) { checked -> vm.updateSettings { it.copy(livePreview = checked) } } }
        item { ToggleRow(stringResource(R.string.settings_resume), settings.resumeLast) { checked -> vm.updateSettings { it.copy(resumeLast = checked) } } }
        item { ToggleRow(stringResource(R.string.settings_background), settings.backgroundAudio) { checked -> vm.updateSettings { it.copy(backgroundAudio = checked) } } }
        item { ToggleRow(stringResource(R.string.settings_theme), settings.amoled) { checked -> vm.updateSettings { it.copy(amoled = checked) } } }
        item { ToggleRow(stringResource(R.string.settings_epg_unmetered), settings.epgUnmeteredOnly) { checked -> vm.updateSettings { it.copy(epgUnmeteredOnly = checked) } } }
        item {
            TvTextField(
                value = settings.epgRefreshHours.toString(),
                onValueChange = { raw -> vm.updateSettings { it.copy(epgRefreshHours = raw.toIntOrNull()?.coerceIn(1, 48) ?: it.epgRefreshHours) } },
                label = stringResource(R.string.settings_epg_hours),
                onEditingChange = vm::setTextFocused,
            )
        }
        item { Text(stringResource(R.string.settings_language)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to R.string.lang_system, "en" to R.string.lang_en, "ru" to R.string.lang_ru).forEach { (code, label) ->
                    TvChip(selected = settings.language == code, onClick = { vm.updateSettings { it.copy(language = code) } }, label = stringResource(label))
                }
            }
        }
        item {
            TvTextField(
                value = settings.userAgent,
                onValueChange = { raw -> vm.updateSettings { it.copy(userAgent = raw) } },
                label = stringResource(R.string.settings_ua),
                onEditingChange = vm::setTextFocused,
            )
        }
        item { PinSettings(settings.pinHash != null, vm) }
        item { Button(onClick = vm::clearCache, modifier = Modifier.tvControl()) { Text(stringResource(R.string.settings_clear_cache)) } }
        item { Button(onClick = vm::refresh, modifier = Modifier.tvControl()) { Text(stringResource(R.string.refresh)) } }
    }
}

@Composable
private fun bufferLabel(profile: BufferProfile) = when (profile) {
    BufferProfile.AUTO -> stringResource(R.string.buffer_auto)
    BufferProfile.LOW_LATENCY -> stringResource(R.string.buffer_low)
    BufferProfile.STABLE -> stringResource(R.string.buffer_stable)
    BufferProfile.MAX_STABILITY -> stringResource(R.string.buffer_max)
}

@Composable
private fun HeightChoice(current: Int, onPick: (Int) -> Unit) {
    Column {
        Text(stringResource(R.string.settings_max_height))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to R.string.height_original, 480 to R.string.height_480, 720 to R.string.height_720, 1080 to R.string.height_1080).forEach { (height, label) ->
                TvChip(selected = current == height, onClick = { onPick(height) }, label = stringResource(label))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    TvToggleRow(label = label, checked = checked, onChange = onChange)
}

@Composable
private fun PinSettings(hasPin: Boolean, vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    Column {
        Text(stringResource(R.string.settings_pin))
        TvTextField(
            value = pin,
            onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4) },
            label = stringResource(R.string.settings_pin),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            onEditingChange = vm::setTextFocused,
        )
        Row {
            TextButton(onClick = { if (pin.length == 4) { vm.setPin(pin); pin = "" } }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.pin_set)) }
            if (hasPin) TextButton(onClick = { vm.setPin(null); pin = "" }, modifier = Modifier.tvControl()) { Text(stringResource(R.string.pin_clear)) }
        }
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_enter)) },
        text = {
            TvTextField(
                value = pin,
                onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4) },
                label = stringResource(R.string.pin_enter),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(pin) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditorScreen(
    existing: PlaylistEntity?,
    busy: Boolean,
    qr: Bitmap?,
    qrUrl: String?,
    onClose: () -> Unit,
    onSave: (PlaylistDraft) -> Unit,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    onStartQr: () -> Unit,
    onStopQr: () -> Unit,
    onTextFocus: (Boolean) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: LibraryRepository.TYPE_URL) }
    var url by remember(existing?.id) { mutableStateOf(existing?.url ?: "") }
    var user by remember(existing?.id) { mutableStateOf(existing?.username ?: "") }
    var pass by remember(existing?.id) { mutableStateOf(existing?.password ?: "") }
    var epg by remember(existing?.id) { mutableStateOf(existing?.epgUrl ?: "") }
    var hours by remember(existing?.id) { mutableStateOf((existing?.updateHours ?: 12).toString()) }
    var tz by remember(existing?.id) { mutableStateOf(((existing?.tzOffsetMinutes ?: 0) / 60).toString()) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
        Text(stringResource(R.string.add_playlist), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item {
                TvTextField(name, { name = it }, label = stringResource(R.string.playlist_name), onEditingChange = onTextFocus)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LibraryRepository.TYPE_URL to R.string.source_url,
                        LibraryRepository.TYPE_XTREAM to R.string.source_xtream,
                        LibraryRepository.TYPE_FILE to R.string.source_file,
                    ).forEach { (value, label) ->
                        TvChip(selected = type == value, onClick = { type = value }, label = stringResource(label))
                    }
                }
            }
            item {
                if (type == LibraryRepository.TYPE_XTREAM) {
                    TvTextField(url, { url = it }, label = stringResource(R.string.field_server), onEditingChange = onTextFocus)
                } else {
                    TvTextField(url, { url = it }, label = stringResource(R.string.field_url), onEditingChange = onTextFocus)
                }
            }
            if (type == LibraryRepository.TYPE_XTREAM) {
                item { TvTextField(user, { user = it }, label = stringResource(R.string.field_user), onEditingChange = onTextFocus) }
                item { TvTextField(pass, { pass = it }, label = stringResource(R.string.field_pass), visualTransformation = PasswordVisualTransformation(), onEditingChange = onTextFocus) }
            }
            item { TvTextField(epg, { epg = it }, label = stringResource(R.string.field_epg), onEditingChange = onTextFocus) }
            item { TvTextField(hours, { hours = it }, label = stringResource(R.string.field_interval), onEditingChange = onTextFocus) }
            item { TvTextField(tz, { tz = it }, label = stringResource(R.string.field_tz), onEditingChange = onTextFocus) }
            item {
                Row {
                    TextButton(onClick = onPickFile, modifier = Modifier.tvControl()) { Text(stringResource(R.string.file_pick)) }
                    TextButton(onClick = onPaste, modifier = Modifier.tvControl()) { Text(stringResource(R.string.source_clipboard)) }
                    TextButton(onClick = onStartQr, modifier = Modifier.tvControl()) { Text(stringResource(R.string.phone_link_start)) }
                }
            }
            item { Text(stringResource(R.string.stalker_future), color = Muted) }
            item { Text(stringResource(R.string.phone_link_help), color = Muted) }
            if (qr != null) {
                item {
                    androidx.compose.foundation.Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.qr_title), modifier = Modifier.size(220.dp))
                    qrUrl?.let { Text(it, color = Muted) }
                    TextButton(onClick = onStopQr, modifier = Modifier.tvControl()) { Text(stringResource(R.string.phone_link_stop)) }
                }
            }
            if (busy) item { Text(stringResource(R.string.syncing), color = Accent) }
        }
        Row {
            TextButton(onClick = onClose, modifier = Modifier.tvControl()) { Text(stringResource(R.string.cancel)) }
            Button(modifier = Modifier.tvControl(), onClick = {
                onSave(
                    PlaylistDraft(
                        id = existing?.id ?: 0,
                        name = name,
                        type = type,
                        url = url.ifBlank { null },
                        fileUri = existing?.fileUri,
                        username = user.ifBlank { null },
                        password = pass.ifBlank { null },
                        epgUrl = epg.ifBlank { null },
                        updateHours = hours.toIntOrNull() ?: 12,
                        tzOffsetMinutes = (tz.toIntOrNull() ?: 0) * 60,
                    ),
                )
            }) { Text(stringResource(R.string.save)) }
        }
    }
}



@Composable
private fun EpgStatusCard(status: player.vvvion.app.data.repo.EpgStatus) {
    val updated = status.lastSyncAt?.let { clock(it) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.epg_status_title), style = MaterialTheme.typography.titleLarge)
        if (updated == null && status.programs == 0) {
            Text(stringResource(R.string.epg_status_never), color = Muted)
        } else if (updated != null) {
            Text(stringResource(R.string.epg_status_updated, updated))
        }
        Text(stringResource(R.string.epg_status_programs, status.programs))
        Text(stringResource(R.string.epg_status_matched, status.matchedChannels, status.totalChannels))
        status.epgUrl?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.epg_status_url, it), color = Muted, maxLines = 2) }
        status.lastError?.let { Text(stringResource(R.string.epg_status_error, it), color = MaterialTheme.colorScheme.error) }
    }
}


@Composable
private fun DiagnosticsCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(player.vvvion.app.util.CrashLog.inApp(context).latest()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.diagnostics), style = MaterialTheme.typography.titleLarge)
        Text(
            report ?: stringResource(R.string.diagnostics_empty),
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 16,
        )
        Button(
            onClick = { report = player.vvvion.app.util.CrashLog.inApp(context).latest() },
            modifier = Modifier.tvControl().padding(top = 8.dp),
        ) { Text(stringResource(R.string.diagnostics_refresh)) }
    }
}

@Composable
private fun HiddenGroupsCard(
    vm: AppViewModel,
    settings: AppSettings,
    playlists: List<PlaylistEntity>,
) {
    val selectedId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    val names = remember(playlists) { playlists.associate { it.id to it.name } }
    val entries = HiddenGroups.entries(settings.hiddenGroups)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.hidden_groups), style = MaterialTheme.typography.titleLarge)
        if (entries.isEmpty()) {
            Text(stringResource(R.string.hidden_groups_empty), color = Muted)
        } else {
            entries.forEach { (playlistId, title) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        names[playlistId]?.let { Text(it, color = Muted, maxLines = 1) }
                    }
                    TextButton(
                        onClick = { vm.restoreGroup(playlistId, title) },
                        modifier = Modifier.tvControl(),
                    ) { Text(stringResource(R.string.hidden_groups_restore)) }
                }
            }
        }
        if (selectedId >= 0 && HiddenGroups.titles(selectedId, settings.hiddenGroups).isNotEmpty()) {
            Button(
                onClick = { vm.restoreAllHiddenGroups(selectedId) },
                modifier = Modifier.tvControl().padding(top = 8.dp),
            ) { Text(stringResource(R.string.hidden_groups_restore_all)) }
        }
    }
}

@Composable
private fun HideGroupDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { cancelFocus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_remove_title)) },
        text = { Text(stringResource(R.string.group_remove_body, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.tvControl()) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus).tvControl()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
