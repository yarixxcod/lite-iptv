package app.liteiptv

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.liteiptv.data.prefs.SettingsStore
import app.liteiptv.player.PlaybackCoordinator
import app.liteiptv.player.PlaybackService
import app.liteiptv.player.PlayerEngine
import app.liteiptv.ui.AppViewModel
import app.liteiptv.ui.HomeScreen
import app.liteiptv.ui.LiteTheme
import app.liteiptv.ui.PlayerPane
import app.liteiptv.ui.PlayerViewModel
import app.liteiptv.work.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val playback: PlaybackCoordinator by inject()
    private val engine: PlayerEngine by inject()
    private val settingsStore: SettingsStore by inject()
    private val scheduler: SyncScheduler by inject()
    private val appVm: AppViewModel by viewModel()
    private val playerVm: PlayerViewModel by viewModel()
    private var backgroundAudio = false
    private var appliedLanguage = ""
    private var booted = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "File"
        appVm.importFile(uri.toString(), name)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            settingsStore.settings.collect { settings ->
                backgroundAudio = settings.backgroundAudio
                applyLanguage(settings.language)
            }
        }
        setContent {
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val fullscreen by playerVm.fullscreen.collectAsStateWithLifecycle()
            LiteTheme(amoled = settings.amoled) {
                val width = calculateWindowSizeClass(this@MainActivity).widthSizeClass
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        vm = appVm,
                        playerVm = playerVm,
                        width = width,
                        onPickFile = { filePicker.launch(arrayOf("*/*")) },
                        onPaste = { pasteClipboard() },
                    )
                    if (fullscreen) {
                        PlayerPane(
                            vm = playerVm,
                            modifier = Modifier.fillMaxSize(),
                            controls = true,
                            onZap = { delta -> playback.zap.tryEmit(delta) },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, PlaybackService::class.java))
        if (!booted) {
            booted = true
            window.decorView.post {
                lifecycleScope.launch {
                    scheduler.ensureAll()
                    appVm.maybeResume()
                }
            }
        }
    }

    override fun onStop() {
        if (backgroundAudio && engine.state.value.playing && !inPip()) {
            val title = playback.channel.value?.name ?: getString(R.string.app_name)
            val intent = Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_TITLE, title)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } else if (!inPip()) {
            engine.pause()
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val television = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        if (!television && playback.fullscreen.value && Build.VERSION.SDK_INT >= 26 && engine.state.value.playing) {
            val params = android.app.PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !playback.textFocused.value) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    playback.zap.tryEmit(1)
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    playback.zap.tryEmit(-1)
                    return true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    playback.digits.tryEmit(event.keyCode - KeyEvent.KEYCODE_0)
                    return true
                }
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                    playback.digits.tryEmit(event.keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        appVm.importClipboard(text)
    }

    private fun applyLanguage(code: String) {
        if (appliedLanguage == code) return
        appliedLanguage = code
        val locales = if (code == "system" || code.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(code)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun inPip(): Boolean = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
}
