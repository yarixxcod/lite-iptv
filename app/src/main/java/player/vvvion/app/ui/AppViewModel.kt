package player.vvvion.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import player.vvvion.app.data.db.ChannelEntity
import player.vvvion.app.data.db.GroupRow
import player.vvvion.app.data.db.HistoryEntity
import player.vvvion.app.data.db.PlaylistEntity
import player.vvvion.app.data.db.ProgramEntity
import player.vvvion.app.data.importing.ImportServer
import player.vvvion.app.data.importing.PlaylistUrls
import player.vvvion.app.data.importing.qrBitmap
import player.vvvion.app.data.prefs.AppSettings
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.data.prefs.hashPin
import player.vvvion.app.data.prefs.lockKey
import player.vvvion.app.data.repo.ChannelSort
import player.vvvion.app.data.repo.EpgRepository
import player.vvvion.app.data.repo.EpgStatus
import player.vvvion.app.data.repo.LibraryRepository
import player.vvvion.app.data.repo.PlaylistDraft
import player.vvvion.app.domain.BufferProfile
import player.vvvion.app.domain.HiddenGroups
import player.vvvion.app.player.PlaybackCoordinator
import player.vvvion.app.player.RemoteScope
import player.vvvion.app.update.RemoteUpdate
import player.vvvion.app.update.UpdateRepository
import player.vvvion.app.work.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab { LIVE, FAVORITES, GUIDE, SETTINGS }

data class GuideRow(val channel: ChannelEntity, val programs: List<ProgramEntity>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val library: LibraryRepository,
    private val epgRepo: EpgRepository,
    private val settingsStore: SettingsStore,
    private val scheduler: SyncScheduler,
    private val playback: PlaybackCoordinator,
    private val updates: UpdateRepository,
) : ViewModel() {
    val tab = MutableStateFlow(HomeTab.LIVE)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(ChannelSort.NUMBER)
    val group = MutableStateFlow("")
    val selectedPlaylistId = MutableStateFlow(-1L)
    val busy = MutableStateFlow(false)
    private val messageQueue = MessageQueue()
    val banner = MutableStateFlow<UiMessage?>(null)

    fun notify(kind: MessageKind, code: String? = null, raw: String? = null, dismissMs: Long? = null) {
        banner.value = messageQueue.post(
            kind,
            code,
            raw,
            dismissMs ?: MessageQueue.defaultDismiss(kind),
        ).current
    }

    fun dismissBanner(id: Long? = null) {
        banner.value = messageQueue.dismiss(id).current
    }

    private fun notifyError(error: Throwable) {
        notify(MessageKind.ERROR, raw = (error.message ?: error.javaClass.simpleName).take(140))
    }

    val editorOpen = MutableStateFlow(false)
    val editing = MutableStateFlow<PlaylistEntity?>(null)
    val qrUrl = MutableStateFlow<String?>(null)
    val qrPng = MutableStateFlow<android.graphics.Bitmap?>(null)
    val pinGroup = MutableStateFlow<String?>(null)
    val pendingHideGroup = MutableStateFlow<String?>(null)
    val epgNow = MutableStateFlow<Map<String, List<ProgramEntity>>>(emptyMap())
    val epgStatus = MutableStateFlow(EpgStatus())
    val lastListChannelId = playback.lastListChannelId
    val favoriteUrls = selectedPlaylistId.flatMapLatest { id ->
        if (id < 0) flowOf(emptySet()) else library.observeFavoriteUrls(id).map { it.toSet() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private var epgRequest: kotlinx.coroutines.Job? = null
    private val unlocked = MutableStateFlow<Set<String>>(emptySet())
    private var importServer: ImportServer? = null
    private var resumed = false
    private var numberBuffer = ""
    private var numberJob: kotlinx.coroutines.Job? = null
    val typedNumber = MutableStateFlow("")
    val availableUpdate = MutableStateFlow<RemoteUpdate?>(null)
    val updatePhase = MutableStateFlow("idle")
    val updateFraction = MutableStateFlow(0f)
    private val installEvents = MutableSharedFlow<java.io.File>(extraBufferCapacity = 1)
    val installRequests = installEvents.asSharedFlow()

    val playlists = library.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val recent = library.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = selectedPlaylistId.flatMapLatest { id ->
        if (id < 0) flowOf(emptyList())
        else combine(library.groups(id), settings) { rows, prefs ->
            HiddenGroups.visible(rows, HiddenGroups.titles(id, prefs.hiddenGroups))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        selectedPlaylistId,
        group,
        query.debounce(300),
        sort,
        tab,
        settings,
    ) { values ->
        val id = values[0] as Long
        val selectedGroup = values[1] as String
        val q = values[2] as String
        val selectedSort = values[3] as ChannelSort
        val selectedTab = values[4] as HomeTab
        val prefs = values[5] as AppSettings
        ChannelQuery(
            playlistId = id,
            group = selectedGroup,
            query = q,
            sort = selectedSort,
            favorites = selectedTab == HomeTab.FAVORITES,
            excludedGroups = HiddenGroups.titles(id, prefs.hiddenGroups).toList(),
        )
    }.flatMapLatest { params ->
        if (params.playlistId < 0) flowOf(PagingData.empty())
        else Pager(
            PagingConfig(pageSize = 40, prefetchDistance = 12, enablePlaceholders = false, maxSize = 240),
        ) {
            library.paging(params.playlistId, params.group, params.query, params.sort, params.favorites, params.excludedGroups)
        }.flow
    }.cachedIn(viewModelScope)

    val guide = playback.channel.flatMapLatest { channel ->
        if (channel == null) flowOf(emptyList())
        else epgRepo.upcoming(channel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            selectedPlaylistId.collect { id -> if (id >= 0) refreshEpgStatus(id) }
        }
        viewModelScope.launch {
            playlists.collect { list ->
                if (list.isEmpty()) return@collect
                if (list.none { it.id == selectedPlaylistId.value }) {
                    val preferred = list.firstOrNull { it.id == settings.value.lastPlaylistId } ?: list.first()
                    selectedPlaylistId.value = preferred.id
                }
            }
        }
        viewModelScope.launch {
            playback.digits.collect { digit ->
                if (!playback.fullscreen.value) pushDigit(digit)
            }
        }
    }

    fun selectPlaylist(id: Long) {
        selectedPlaylistId.value = id
        group.value = ""
        viewModelScope.launch { settingsStore.update { it.copy(lastPlaylistId = id) } }
    }

    fun selectGroup(title: String) {
        val id = selectedPlaylistId.value
        val key = lockKey(id, title)
        if (title.isNotEmpty() && settings.value.lockedGroups.contains(key) && key !in unlocked.value) {
            pinGroup.value = title
            return
        }
        group.value = title
    }

    fun submitPin(pin: String) {
        val expected = settings.value.pinHash
        val target = pinGroup.value ?: return
        if (expected != null && hashPin(pin) == expected) {
            val key = lockKey(selectedPlaylistId.value, target)
            unlocked.value = unlocked.value + key
            group.value = target
            pinGroup.value = null
        } else {
            notify(MessageKind.ERROR, code = "pin")
        }
    }

    fun dismissPin() {
        pinGroup.value = null
    }

    fun toggleLock(title: String) {
        val id = selectedPlaylistId.value
        if (id < 0 || title.isEmpty()) return
        viewModelScope.launch {
            settingsStore.update { current ->
                if (current.pinHash == null) return@update current
                val key = lockKey(id, title)
                val next = current.lockedGroups.toMutableSet()
                if (!next.add(key)) next.remove(key)
                current.copy(lockedGroups = next)
            }
        }
    }

    fun requestHideGroup(title: String) {
        if (!HiddenGroups.canRemove(title)) return
        pendingHideGroup.value = title
    }

    fun dismissHideGroup() {
        pendingHideGroup.value = null
    }

    fun confirmHideGroup() {
        val title = pendingHideGroup.value ?: return
        pendingHideGroup.value = null
        hideGroup(title)
    }

    fun hideGroup(title: String) {
        val id = selectedPlaylistId.value
        if (id < 0 || !HiddenGroups.canRemove(title)) return
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(hiddenGroups = HiddenGroups.hide(current.hiddenGroups, id, title))
            }
            if (group.value == title) group.value = ""
            notify(MessageKind.SUCCESS, code = "group_removed", raw = title, dismissMs = 6_000L)
        }
    }

    fun restoreGroup(playlistId: Long, title: String) {
        if (!HiddenGroups.canRemove(title)) return
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(hiddenGroups = HiddenGroups.restore(current.hiddenGroups, playlistId, title))
            }
            notify(MessageKind.SUCCESS, code = "group_restored", raw = title)
        }
    }

    fun restoreAllHiddenGroups(playlistId: Long = selectedPlaylistId.value) {
        if (playlistId < 0) return
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(hiddenGroups = HiddenGroups.restoreAll(current.hiddenGroups, playlistId))
            }
            notify(MessageKind.SUCCESS, code = "groups_restored")
        }
    }

    fun undoHideGroup(title: String) {
        restoreGroup(selectedPlaylistId.value, title)
    }

    fun setPin(pin: String?) {
        viewModelScope.launch {
            settingsStore.update { it.copy(pinHash = pin?.let(::hashPin)) }
        }
    }

    fun onVisible(channels: List<ChannelEntity>) {
        val id = selectedPlaylistId.value
        if (id < 0 || channels.isEmpty()) return
        epgRequest?.cancel()
        epgRequest = viewModelScope.launch {
            try {
                epgNow.value = epgRepo.nowAndNext(id, channels)
            } catch (error: Exception) {
                android.util.Log.e("VVVionEPG", "Failed to load current programmes", error)
            }
        }
    }

    fun selectChannel(channel: ChannelEntity, fullscreen: Boolean) {
        if (fullscreen) playback.openFull(channel) else playback.select(channel)
    }

    fun hide(channel: ChannelEntity) {
        viewModelScope.launch { library.hide(channel) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch { library.toggleFavorite(channel) }
    }

    suspend fun favoriteIndex(channel: ChannelEntity): Int = library.favoriteIndex(channel.playlistId, channel.streamUrl)

    fun moveFavorite(channel: ChannelEntity, delta: Int) {
        viewModelScope.launch {
            val index = library.favoriteIndex(channel.playlistId, channel.streamUrl)
            if (index >= 0) library.moveFavorite(channel.playlistId, index, index + delta)
        }
    }

    fun openEditor(existing: PlaylistEntity? = null) {
        editing.value = existing
        editorOpen.value = true
    }

    fun closeEditor() {
        editorOpen.value = false
        playback.textFocused.value = false
        stopPhoneLink()
    }

    fun save(draft: PlaylistDraft, fromPhone: Boolean = false) {
        val prepared = if (draft.type == LibraryRepository.TYPE_URL) {
            when (val check = PlaylistUrls.validateHttpUrl(draft.url)) {
                is PlaylistUrls.Check.Invalid -> {
                    notify(MessageKind.ERROR, code = if (check.reason == PlaylistUrls.Reason.EMPTY) "url_invalid_empty" else "url_invalid_scheme")
                    return
                }
                is PlaylistUrls.Check.Ok -> draft.copy(url = check.url)
            }
        } else if (draft.type == LibraryRepository.TYPE_XTREAM) {
            draft.copy(url = PlaylistUrls.sanitize(draft.url).ifBlank { null })
        } else {
            draft
        }
        viewModelScope.launch {
            busy.value = true
            /* stale errors are dropped by a later success */
            try {
                val id = library.savePlaylist(prepared)
                selectedPlaylistId.value = id
                editorOpen.value = false
                stopPhoneLink()
                val saved = library.playlist(id)
                if (saved != null) scheduler.schedule(saved.id, saved.updateHours, saved.type, settingsStore.settings.first())
                if (prepared.type != LibraryRepository.TYPE_PASTE || !prepared.pastedText.isNullOrBlank() || prepared.id == 0L) {
                    library.syncPlaylist(id)
                    epgRepo.sync(id)
                    reloadPlaying(id)
                    refreshEpgStatus(id)
                }
                notify(
                    MessageKind.SUCCESS,
                    code = when {
                        fromPhone -> "phone_import_ok"
                        prepared.id == 0L -> "playlist_added"
                        else -> "playlist_updated"
                    },
                )
            } catch (t: Throwable) {
                notifyError(t)
            } finally {
                busy.value = false
            }
        }
    }

    fun importFile(uri: String, name: String) {
        save(PlaylistDraft(name = name.ifBlank { "File" }, type = LibraryRepository.TYPE_FILE, fileUri = uri))
    }

    fun importClipboard(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            notify(MessageKind.INFO, code = "clipboard")
            return
        }
        when (val check = PlaylistUrls.validateHttpUrl(trimmed)) {
            is PlaylistUrls.Check.Ok -> save(PlaylistDraft(name = "Clipboard", type = LibraryRepository.TYPE_URL, url = check.url))
            is PlaylistUrls.Check.Invalid -> if (trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF")) {
                save(PlaylistDraft(name = "Clipboard", type = LibraryRepository.TYPE_PASTE, pastedText = trimmed))
            } else if (check.reason == PlaylistUrls.Reason.LEADING_JUNK || trimmed.contains("http", true)) {
                notify(MessageKind.ERROR, code = "url_invalid_scheme")
            } else {
                save(PlaylistDraft(name = "Clipboard", type = LibraryRepository.TYPE_PASTE, pastedText = trimmed))
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            library.deletePlaylist(id)
            settingsStore.update { current ->
                current.copy(hiddenGroups = HiddenGroups.restoreAll(current.hiddenGroups, id))
            }
            if (selectedPlaylistId.value == id) selectedPlaylistId.value = -1
        }
    }

    fun refresh() {
        val id = selectedPlaylistId.value
        if (id < 0) return
        viewModelScope.launch {
            busy.value = true
            /* stale errors are dropped by a later success */
            try {
                library.syncPlaylist(id)
                epgRepo.sync(id, force = true)
                reloadPlaying(id)
                refreshEpgStatus(id)
                notify(MessageKind.SUCCESS, code = "playlist_updated")
            } catch (t: Throwable) {
                notifyError(t)
            } finally {
                busy.value = false
            }
        }
    }

    fun startPhoneLink() {
        stopPhoneLink()
        val server = ImportServer { remote ->
            val draft = when (remote.kind) {
                "XTREAM" -> PlaylistDraft(
                    name = remote.name,
                    type = LibraryRepository.TYPE_XTREAM,
                    url = remote.url,
                    username = remote.username,
                    password = remote.password,
                )
                "PASTE" -> PlaylistDraft(name = remote.name, type = LibraryRepository.TYPE_PASTE, pastedText = remote.body)
                else -> PlaylistDraft(name = remote.name, type = LibraryRepository.TYPE_URL, url = remote.url)
            }
            save(draft, fromPhone = true)
        }
        server.start()
        importServer = server
        qrUrl.value = server.pageUrl
        qrPng.value = server.pageUrl?.let { qrBitmap(it, 480) }
    }

    fun stopPhoneLink() {
        importServer?.stop()
        importServer = null
        qrUrl.value = null
        qrPng.value = null
    }

    fun maybeResume() {
        if (resumed) return
        resumed = true
        viewModelScope.launch {
            val current = settingsStore.settings.first()
            if (current.resumeLast && current.lastChannelId > 0) {
                library.channel(current.lastChannelId)?.let { playback.openFull(it) }
            }
            if (current.epgMatcher < 2) {
                val pending = library.playlistsSnapshot()
                for (playlist in pending) {
                    if (playlist.epgUrl.isNullOrBlank()) continue
                    try {
                        epgRepo.sync(playlist.id, force = true)
                    } catch (_: Throwable) {
                    }
                }
                settingsStore.update { it.copy(epgMatcher = 2) }
                reloadPlaying(selectedPlaylistId.value)
                refreshEpgStatus(selectedPlaylistId.value)
            }
        }
    }

    private suspend fun reloadPlaying(playlistId: Long) {
        val current = playback.channel.value ?: return
        if (playlistId >= 0 && current.playlistId != playlistId) return
        library.channel(current.id)?.let { playback.select(it) }
    }

    suspend fun loadGuideRows(): List<GuideRow> {
        val id = selectedPlaylistId.value
        if (id < 0) return emptyList()
        val excluded = HiddenGroups.titles(id, settings.value.hiddenGroups)
        val selected = group.value
        val channels = library.head(id, selected, 8, excluded)
        if (channels.isEmpty()) return emptyList()
        val programs = epgRepo.nowAndNext(id, channels)
        return channels.map { channel -> GuideRow(channel, programs[channel.epgKey].orEmpty()) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(block) }
    }

    fun clearCache() {
        library.clearCaches()
        notify(MessageKind.SUCCESS, code = "cache")
    }

    fun refreshEpgStatus(id: Long = selectedPlaylistId.value) {
        if (id < 0) {
            epgStatus.value = EpgStatus()
            return
        }
        viewModelScope.launch {
            epgStatus.value = runCatching { epgRepo.status(id) }.getOrElse { EpgStatus(lastError = it.message) }
        }
    }

    fun setTextFocused(value: Boolean) {
        playback.textFocused.value = value
    }

    fun setRemoteScope(scope: RemoteScope) {
        if (!playback.fullscreen.value) playback.remoteScope.value = scope
    }

    fun rememberListChannel(id: Long) {
        if (id >= 0) playback.lastListChannelId.value = id
    }

    fun openHistory(channelId: Long) {
        viewModelScope.launch {
            val channel = library.channel(channelId) ?: return@launch
            selectChannel(channel, true)
        }
    }

    private fun pushDigit(digit: Int) {
        numberBuffer = (numberBuffer + digit).takeLast(4)
        typedNumber.value = numberBuffer
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1100)
            val number = numberBuffer.toIntOrNull()
            numberBuffer = ""
            typedNumber.value = ""
            val id = selectedPlaylistId.value
            if (number != null && id >= 0) {
                val excluded = HiddenGroups.titles(id, settings.value.hiddenGroups)
                library.channelByNumber(id, number, excluded)?.let { playback.select(it) }
            }
        }
    }

    fun checkForUpdate(quiet: Boolean = false) {
        if (updatePhase.value == "downloading") return
        viewModelScope.launch {
            updatePhase.value = "checking"
            val found = try {
                val url = settingsStore.settings.first().updateManifestUrl
                withContext(Dispatchers.IO) { updates.check(url) }
            } catch (_: Exception) {
                if (!quiet) notify(MessageKind.ERROR, code = "update_failed")
                updatePhase.value = "idle"
                return@launch
            }
            availableUpdate.value = found
            updatePhase.value = if (found == null) "idle" else "available"
            if (found == null && !quiet) notify(MessageKind.INFO, code = "update_none")
        }
    }

    fun downloadUpdate() {
        val offer = availableUpdate.value ?: return
        if (updatePhase.value == "downloading") return
        viewModelScope.launch {
            updatePhase.value = "downloading"
            updateFraction.value = 0f
            try {
                val file = withContext(Dispatchers.IO) {
                    updates.download(offer.apkUrl) { updateFraction.value = it }
                }
                updatePhase.value = "ready"
                installEvents.emit(file)
            } catch (e: Exception) {
                updatePhase.value = "available"
                notify(MessageKind.ERROR, code = "update_failed", raw = e.message?.take(140))
            }
        }
    }

    fun dismissUpdate() {
        if (updatePhase.value == "downloading") return
        availableUpdate.value = null
        updatePhase.value = "idle"
    }

    override fun onCleared() {
        stopPhoneLink()
    }

    private data class ChannelQuery(
        val playlistId: Long,
        val group: String,
        val query: String,
        val sort: ChannelSort,
        val favorites: Boolean,
        val excludedGroups: List<String> = emptyList(),
    )
}
