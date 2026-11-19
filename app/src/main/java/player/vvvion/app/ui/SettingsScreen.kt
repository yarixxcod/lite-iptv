package player.vvvion.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.vvvion.app.BuildConfig
import player.vvvion.app.R
import player.vvvion.app.data.db.PlaylistEntity
import player.vvvion.app.data.prefs.AppSettings
import player.vvvion.app.data.repo.EpgStatus
import player.vvvion.app.domain.BufferProfile
import player.vvvion.app.domain.HiddenGroups
import player.vvvion.app.update.UpdateRepository

private enum class SettingsSection { PLAYLISTS, EPG, PLAYBACK, HIDDEN, UPDATES, ABOUT }

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    playlists: List<PlaylistEntity>,
    epgStatus: EpgStatus,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val playlistId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.PLAYLISTS) }
    LaunchedEffect(playlistId) { if (playlistId >= 0) vm.refreshEpgStatus(playlistId) }
    if (wide) {
        Row(modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsNav(section, { section = it }, Modifier.width(280.dp).fillMaxHeight())
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SettingsPane(section, vm, settings, playlists, epgStatus) }
            }
        }
    } else {
        LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium) }
            SettingsSection.entries.forEach { entry ->
                item { SettingsPane(entry, vm, settings, playlists, epgStatus) }
            }
        }
    }
}

@Composable
private fun SettingsNav(selected: SettingsSection, onSelect: (SettingsSection) -> Unit, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        items(SettingsSection.entries, key = { it.name }) { entry ->
            val active = selected == entry
            Card(
                modifier = Modifier.fillMaxWidth().tvControl().clickable { onSelect(entry) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(sectionIcon(entry), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(sectionTitle(entry)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun SettingsPane(
    section: SettingsSection,
    vm: AppViewModel,
    settings: AppSettings,
    playlists: List<PlaylistEntity>,
    epgStatus: EpgStatus,
) {
    when (section) {
        SettingsSection.PLAYLISTS -> PlaylistsCard(vm, playlists)
        SettingsSection.EPG -> EpgCard(vm, playlists, epgStatus)
        SettingsSection.PLAYBACK -> PlaybackCard(vm, settings)
        SettingsSection.HIDDEN -> HiddenGroupsCard(vm, settings, playlists)
        SettingsSection.UPDATES -> UpdatesCard(vm)
        SettingsSection.ABOUT -> AboutCard(vm)
    }
}

private fun sectionTitle(section: SettingsSection) = when (section) {
    SettingsSection.PLAYLISTS -> R.string.settings_section_playlists
    SettingsSection.EPG -> R.string.settings_section_epg
    SettingsSection.PLAYBACK -> R.string.settings_section_playback
    SettingsSection.HIDDEN -> R.string.settings_section_hidden
    SettingsSection.UPDATES -> R.string.settings_section_updates
    SettingsSection.ABOUT -> R.string.settings_section_about
}

private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.PLAYLISTS -> Icons.Filled.Star
    SettingsSection.EPG -> Icons.Filled.DateRange
    SettingsSection.PLAYBACK -> Icons.Filled.PlayArrow
    SettingsSection.HIDDEN -> Icons.Filled.Settings
    SettingsSection.UPDATES -> Icons.Filled.KeyboardArrowRight
    SettingsSection.ABOUT -> Icons.Filled.Info
}

@Composable
private fun SettingsCard(title: Int, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun AccentButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String) {
    Button(
        onClick = onClick,
        modifier = modifier.tvControl(),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk),
    ) { Text(label) }
}

@Composable
private fun PlaylistsCard(vm: AppViewModel, playlists: List<PlaylistEntity>) {
    SettingsCard(R.string.settings_section_playlists, Icons.Filled.Star) {
        AccentButton(onClick = { vm.openEditor(null) }, label = stringResource(R.string.add_playlist))
        playlists.forEach { playlist ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                    Text(playlist.type + (playlist.lastError?.let { " · $it" } ?: ""), color = Muted, maxLines = 1)
                }
                TextButton(onClick = { vm.selectPlaylist(playlist.id); vm.refresh() }, modifier = Modifier.tvControl(), colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                    Text(stringResource(R.string.refresh))
                }
                TextButton(onClick = { vm.openEditor(playlist) }, modifier = Modifier.tvControl(), colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                    Text(stringResource(R.string.edit))
                }
                TextButton(onClick = { vm.deletePlaylist(playlist.id) }, modifier = Modifier.tvControl(), colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun EpgCard(vm: AppViewModel, playlists: List<PlaylistEntity>, status: EpgStatus) {
    val playlistId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var epgUrl by remember(playlistId, playlist?.epgUrl, status.epgUrl) {
        mutableStateOf(status.epgUrl ?: playlist?.epgUrl.orEmpty())
    }
    SettingsCard(R.string.settings_section_epg, Icons.Filled.DateRange) {
        if (status.programs > 0 && status.lastSyncAt != null) {
            Text(stringResource(R.string.epg_status_updated, clock(status.lastSyncAt)))
        } else if (status.epgUrl.isNullOrBlank()) {
            Text(stringResource(R.string.epg_no_url), color = Muted)
        } else {
            Text(stringResource(R.string.epg_status_never), color = Muted)
        }
        Text(stringResource(R.string.epg_status_programs, status.programs))
        Text(stringResource(R.string.epg_status_matched, status.matchedChannels, status.totalChannels))
        if (status.lastError != null && status.lastError != "No EPG URL") {
            Text(stringResource(R.string.epg_status_error, status.lastError), color = MaterialTheme.colorScheme.error)
        }
        if (playlist != null) {
            TvTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                label = stringResource(R.string.epg_url_label),
                onEditingChange = { focused ->
                    vm.setTextFocused(focused)
                    if (!focused) vm.setPlaylistEpgUrl(playlist.id, epgUrl)
                },
            )
            AccentButton(
                onClick = {
                    vm.setPlaylistEpgUrl(playlist.id, epgUrl)
                    vm.refreshEpg(playlist.id)
                },
                label = stringResource(R.string.epg_refresh),
            )
        }
    }
}

@Composable
private fun PlaybackCard(vm: AppViewModel, settings: AppSettings) {
    SettingsCard(R.string.settings_section_playback, Icons.Filled.PlayArrow) {
        Text(stringResource(R.string.settings_buffer))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BufferProfile.entries.forEach { profile ->
                TvChip(
                    selected = settings.buffer == profile,
                    onClick = { vm.updateSettings { it.copy(buffer = profile) } },
                    label = bufferLabel(profile),
                )
            }
        }
        Text(stringResource(R.string.settings_buffer_auto_hint), color = Muted, style = MaterialTheme.typography.bodyMedium)
        HeightChoice(settings.maxHeight) { height -> vm.updateSettings { it.copy(maxHeight = height) } }
        TvTextField(
            value = if (settings.maxBitrateKbps == 0) "" else settings.maxBitrateKbps.toString(),
            onValueChange = { raw -> vm.updateSettings { it.copy(maxBitrateKbps = raw.toIntOrNull() ?: 0) } },
            label = stringResource(R.string.settings_max_bitrate),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onEditingChange = vm::setTextFocused,
        )
        TvToggleRow(stringResource(R.string.settings_preview), settings.livePreview, onChange = { checked -> vm.updateSettings { it.copy(livePreview = checked) } })
        TvToggleRow(stringResource(R.string.settings_resume), settings.resumeLast, onChange = { checked -> vm.updateSettings { it.copy(resumeLast = checked) } })
        TvToggleRow(stringResource(R.string.settings_background), settings.backgroundAudio, onChange = { checked -> vm.updateSettings { it.copy(backgroundAudio = checked) } })
        TvToggleRow(stringResource(R.string.settings_theme), settings.amoled, onChange = { checked -> vm.updateSettings { it.copy(amoled = checked) } })
        TvToggleRow(stringResource(R.string.settings_epg_unmetered), settings.epgUnmeteredOnly, onChange = { checked -> vm.updateSettings { it.copy(epgUnmeteredOnly = checked) } })
        TvTextField(
            value = settings.epgRefreshHours.toString(),
            onValueChange = { raw -> vm.updateSettings { it.copy(epgRefreshHours = raw.toIntOrNull()?.coerceIn(1, 48) ?: it.epgRefreshHours) } },
            label = stringResource(R.string.settings_epg_hours),
            onEditingChange = vm::setTextFocused,
        )
        Text(stringResource(R.string.settings_language))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to R.string.lang_system, "en" to R.string.lang_en, "ru" to R.string.lang_ru).forEach { (code, label) ->
                TvChip(selected = settings.language == code, onClick = { vm.updateSettings { it.copy(language = code) } }, label = stringResource(label))
            }
        }
        TvTextField(
            value = settings.userAgent,
            onValueChange = { raw -> vm.updateSettings { it.copy(userAgent = raw) } },
            label = stringResource(R.string.settings_ua),
            onEditingChange = vm::setTextFocused,
        )
        PinSettings(settings.pinHash != null, vm)
    }
}

@Composable
private fun UpdatesCard(vm: AppViewModel) {
    SettingsCard(R.string.settings_section_updates, Icons.Filled.KeyboardArrowRight) {
        AccentButton(onClick = { vm.checkForUpdate(false) }, label = stringResource(R.string.update_check))
    }
}

@Composable
private fun AboutCard(vm: AppViewModel) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(player.vvvion.app.util.CrashLog.inApp(context).latest()) }
    SettingsCard(R.string.settings_section_about, Icons.Filled.Info) {
        Text(stringResource(R.string.update_version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.app_name), color = Muted)
        Text(UpdateRepository.DEFAULT_MANIFEST.substringAfter("https://").substringBefore("/releases"), color = Muted, maxLines = 2)
        AccentButton(onClick = { report = player.vvvion.app.util.CrashLog.inApp(context).latest() }, label = stringResource(R.string.diagnostics_refresh))
        Text(report ?: stringResource(R.string.diagnostics_empty), color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 12)
        AccentButton(onClick = vm::clearCache, label = stringResource(R.string.settings_clear_cache))
    }
}

@Composable
private fun HiddenGroupsCard(vm: AppViewModel, settings: AppSettings, playlists: List<PlaylistEntity>) {
    val selectedId by vm.selectedPlaylistId.collectAsStateWithLifecycle()
    val names = remember(playlists) { playlists.associate { it.id to it.name } }
    val entries = HiddenGroups.entries(settings.hiddenGroups)
    SettingsCard(R.string.settings_section_hidden, Icons.Filled.Settings) {
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
                        colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                    ) { Text(stringResource(R.string.hidden_groups_restore)) }
                }
            }
        }
        if (selectedId >= 0 && HiddenGroups.titles(selectedId, settings.hiddenGroups).isNotEmpty()) {
            AccentButton(onClick = { vm.restoreAllHiddenGroups(selectedId) }, label = stringResource(R.string.hidden_groups_restore_all))
        }
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
            TextButton(onClick = { if (pin.length == 4) { vm.setPin(pin); pin = "" } }, modifier = Modifier.tvControl(), colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                Text(stringResource(R.string.pin_set))
            }
            if (hasPin) TextButton(onClick = { vm.setPin(null); pin = "" }, modifier = Modifier.tvControl(), colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                Text(stringResource(R.string.pin_clear))
            }
        }
    }
}
