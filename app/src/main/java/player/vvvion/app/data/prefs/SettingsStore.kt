package player.vvvion.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import player.vvvion.app.domain.BufferProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.settingsDataStore by preferencesDataStore("settings")

data class AppSettings(
    val buffer: BufferProfile = BufferProfile.STABLE,
    val maxHeight: Int = 0,
    val maxBitrateKbps: Int = 0,
    val epgUnmeteredOnly: Boolean = false,
    val epgRefreshHours: Int = 6,
    val language: String = "system",
    val amoled: Boolean = false,
    val pinHash: String? = null,
    val lockedGroups: Set<String> = emptySet(),
    val userAgent: String = "",
    val backgroundAudio: Boolean = false,
    val resumeLast: Boolean = true,
    val livePreview: Boolean = true,
    val updateManifestUrl: String = "",
    val lastPlaylistId: Long = -1,
    val lastChannelId: Long = -1,
    val epgMatcher: Int = 0,
) {
    fun effectiveUserAgent(playlistUa: String?): String =
        playlistUa?.takeIf { it.isNotBlank() }
            ?: userAgent.takeIf { it.isNotBlank() }
            ?: player.vvvion.app.data.net.DEFAULT_USER_AGENT
}

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = block(prefs.toSettings())
            prefs[Keys.buffer] = next.buffer.name
            prefs[Keys.maxHeight] = next.maxHeight
            prefs[Keys.maxBitrate] = next.maxBitrateKbps
            prefs[Keys.epgUnmetered] = next.epgUnmeteredOnly
            prefs[Keys.epgHours] = next.epgRefreshHours
            prefs[Keys.language] = next.language
            prefs[Keys.amoled] = next.amoled
            if (next.pinHash == null) prefs.remove(Keys.pin) else prefs[Keys.pin] = next.pinHash
            prefs[Keys.locked] = next.lockedGroups
            prefs[Keys.userAgent] = next.userAgent
            prefs[Keys.background] = next.backgroundAudio
            prefs[Keys.resume] = next.resumeLast
            prefs[Keys.preview] = next.livePreview
            prefs[Keys.updateUrl] = next.updateManifestUrl
            prefs[Keys.lastPlaylist] = next.lastPlaylistId
            prefs[Keys.lastChannel] = next.lastChannelId
            prefs[Keys.epgMatcher] = next.epgMatcher
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        buffer = BufferProfile.fromKey(this[Keys.buffer]),
        maxHeight = this[Keys.maxHeight] ?: 0,
        maxBitrateKbps = this[Keys.maxBitrate] ?: 0,
        epgUnmeteredOnly = this[Keys.epgUnmetered] ?: false,
        epgRefreshHours = this[Keys.epgHours] ?: 6,
        language = this[Keys.language] ?: "system",
        amoled = this[Keys.amoled] ?: false,
        pinHash = this[Keys.pin],
        lockedGroups = this[Keys.locked] ?: emptySet(),
        userAgent = this[Keys.userAgent] ?: "",
        backgroundAudio = this[Keys.background] ?: false,
        resumeLast = this[Keys.resume] ?: true,
        livePreview = this[Keys.preview] ?: true,
        updateManifestUrl = this[Keys.updateUrl] ?: "",
        lastPlaylistId = this[Keys.lastPlaylist] ?: -1L,
        lastChannelId = this[Keys.lastChannel] ?: -1L,
        epgMatcher = this[Keys.epgMatcher] ?: 0,
    )

    private object Keys {
        val buffer = stringPreferencesKey("buffer")
        val maxHeight = intPreferencesKey("max_height")
        val maxBitrate = intPreferencesKey("max_bitrate")
        val epgUnmetered = booleanPreferencesKey("epg_unmetered")
        val epgHours = intPreferencesKey("epg_hours")
        val language = stringPreferencesKey("language")
        val amoled = booleanPreferencesKey("amoled")
        val pin = stringPreferencesKey("pin_hash")
        val locked = stringSetPreferencesKey("locked_groups")
        val userAgent = stringPreferencesKey("user_agent")
        val background = booleanPreferencesKey("background_audio")
        val resume = booleanPreferencesKey("resume_last")
        val preview = booleanPreferencesKey("live_preview")
        val updateUrl = stringPreferencesKey("update_manifest")
        val lastPlaylist = longPreferencesKey("last_playlist")
        val lastChannel = longPreferencesKey("last_channel")
        val epgMatcher = intPreferencesKey("epg_matcher")
    }
}

fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("vvvion:$pin".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

fun lockKey(playlistId: Long, group: String): String = "$playlistId|$group"
