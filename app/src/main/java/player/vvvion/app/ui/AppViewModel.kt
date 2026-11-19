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
import player.vvvion.app.data.importing.qrBitmap
import player.vvvion.app.data.prefs.AppSettings
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.data.prefs.hashPin
import player.vvvion.app.data.prefs.lockKey
import player.vvvion.app.data.repo.ChannelSort
import player.vvvion.app.data.repo.EpgRepository
import player.vvvion.app.data.repo.LibraryRepository
import player.vvvion.app.data.repo.PlaylistDraft
import player.vvvion.app.domain.BufferProfile
import player.vvvion.app.player.PlaybackCoordinator
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
    val message = MutableStateFlow<String?>(null)
    val editorOpen = MutableStateFlow(false)
    val editing = MutableStateFlow<PlaylistEntity?>(null)
    val qrUrl = MutableStateFlow<String?>(null)
    val qrPng = MutableStateFlow<android.graphics.Bitmap?>(null)
    val pinGroup = MutableStateFlow<String?>(null)
    val epgNow = MutableStateFlow<Map<String, List<ProgramEntity>>>(emptyMap())
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
        if (id < 0) flowOf(emptyList()) else library.groups(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        selectedPlaylistId,
        group,
        query.debounce(300),
        sort,
        tab,
    ) { id, selectedGroup, q, selectedSort, selectedTab ->
        ChannelQuery(id, selectedGroup, q, selectedSort, selectedTab == HomeTab.FAVORITES)
    }.flatMapLatest { params ->
        if (params.playlistId < 0) flowOf(PagingData.empty())
        else Pager(
            PagingConfig(pageSize = 40, prefetchDistance = 12, enablePlaceholders = false, maxSize = 240),
        ) {
            library.paging(params.playlistId, params.group, params.query, params.sort, params.favorites)
        }.flow
    }.cachedIn(viewModelScope)

    val guide = playback.channel.flatMapLatest { channel ->
        if (channel == null) flowOf(emptyList())
        else epgRepo.upcoming(channel.playlistId, channel.epgKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
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
            message.value = "pin"
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

    fun setPin(pin: String?) {
        viewModelScope.launch {
            settingsStore.update { it.copy(pinHash = pin?.let(::hashPin)) }
        }
    }

    fun onVisible(channels: List<ChannelEntity>) {
        val id = selectedPlaylistId.value
        if (id < 0 || channels.isEmpty()) return
        viewModelScope.launch {
            try {
                epgNow.value = epgRepo.nowAndNext(id, channels.map { it.epgKey })
            } catch (_: Throwable) {
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
        stopPhoneLink()
    }

    fun save(draft: PlaylistDraft) {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val id = library.savePlaylist(draft)
                selectedPlaylistId.value = id
                editorOpen.value = false
                stopPhoneLink()
                val saved = library.playlist(id)
                if (saved != null) scheduler.schedule(saved.id, saved.updateHours, saved.type, settingsStore.settings.first())
                if (draft.type != LibraryRepository.TYPE_PASTE || !draft.pastedText.isNullOrBlank() || draft.id == 0L) {
                    library.syncPlaylist(id)
                    epgRepo.sync(id)
                }
            } catch (t: Throwable) {
                message.value = t.message ?: t.javaClass.simpleName
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
            message.value = "clipboard"
            return
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            save(PlaylistDraft(name = "Clipboard", type = LibraryRepository.TYPE_URL, url = trimmed))
        } else {
            save(PlaylistDraft(name = "Clipboard", type = LibraryRepository.TYPE_PASTE, pastedText = trimmed))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            library.deletePlaylist(id)
            if (selectedPlaylistId.value == id) selectedPlaylistId.value = -1
        }
    }

    fun refresh() {
        val id = selectedPlaylistId.value
        if (id < 0) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                library.syncPlaylist(id)
                epgRepo.sync(id)
            } catch (t: Throwable) {
                message.value = t.message ?: t.javaClass.simpleName
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
            save(draft)
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
            val current = settings.value
            if (!current.resumeLast || current.lastChannelId <= 0) return@launch
            val channel = library.channel(current.lastChannelId) ?: return@launch
            playback.openFull(channel)
        }
    }

    suspend fun loadGuideRows(): List<GuideRow> {
        val id = selectedPlaylistId.value
        if (id < 0) return emptyList()
        val selected = group.value.ifBlank { groups.value.firstOrNull()?.title ?: "" }
        val channels = library.head(id, selected, 8)
        if (channels.isEmpty()) return emptyList()
        val programs = epgRepo.nowAndNext(id, channels.map { it.epgKey })
        return channels.map { channel -> GuideRow(channel, programs[channel.epgKey].orEmpty()) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(block) }
    }

    fun clearCache() {
        library.clearCaches()
        message.value = "cache"
    }

    fun setTextFocused(value: Boolean) {
        playback.textFocused.value = value
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
                library.channelByNumber(id, number)?.let { playback.select(it) }
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
                if (!quiet) message.value = "update_failed"
                updatePhase.value = "idle"
                return@launch
            }
            availableUpdate.value = found
            updatePhase.value = if (found == null) "idle" else "available"
            if (found == null && !quiet) message.value = "update_none"
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
                message.value = e.message?.take(140) ?: "update_failed"
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
    )
}
