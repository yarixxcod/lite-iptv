package player.vvvion.app

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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.player.PlaybackCoordinator
import player.vvvion.app.player.PlaybackService
import player.vvvion.app.player.PlayerEngine
import player.vvvion.app.ui.AppViewModel
import player.vvvion.app.ui.HomeScreen
import player.vvvion.app.ui.PlayerPane
import player.vvvion.app.ui.PlayerViewModel
import player.vvvion.app.ui.VvvionTheme
import player.vvvion.app.update.UpdateInstaller
import player.vvvion.app.work.SyncScheduler
import kotlinx.coroutines.flow.catch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            settingsStore.settings.catch { }.collect { settings ->
                backgroundAudio = settings.backgroundAudio
                applyLanguage(settings.language)
            }
        }
        lifecycleScope.launch {
            appVm.installRequests.collect { file ->
                if (!UpdateInstaller.canInstall(this@MainActivity)) {
                    UpdateInstaller.openSettings(this@MainActivity)
                    appVm.message.value = "install_permission"
                } else {
                    UpdateInstaller.install(this@MainActivity, file)
                }
            }
        }
        setContent {
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val fullscreen by playerVm.fullscreen.collectAsStateWithLifecycle()
            VvvionTheme(amoled = settings.amoled) {
                val context = LocalContext.current
                val dp = LocalConfiguration.current.screenWidthDp
                val uiMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK
                val television = uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                val width = when {
                    television -> WindowWidthSizeClass.Expanded
                    dp < 600 -> WindowWidthSizeClass.Compact
                    dp < 840 -> WindowWidthSizeClass.Medium
                    else -> WindowWidthSizeClass.Expanded
                }
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        vm = appVm,
                        playerVm = playerVm,
                        width = width,
                        television = television,
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
                    try {
                        scheduler.ensureAll()
                    } catch (error: Exception) {
                        android.util.Log.e("VVVionStartup", "Scheduler initialization failed", error)
                    }
                    try {
                        appVm.maybeResume()
                    } catch (error: Exception) {
                        android.util.Log.e("VVVionStartup", "Playback resume failed", error)
                    }
                    try {
                        appVm.checkForUpdate(quiet = true)
                    } catch (error: Exception) {
                        android.util.Log.e("VVVionStartup", "Update check failed", error)
                    }
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
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                    playback.zap.tryEmit(1)
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    playback.zap.tryEmit(-1)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (engine.state.value.playing) engine.pause() else engine.resume()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    engine.resume()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    engine.pause()
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
        val desired = if (code == "system" || code.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        val current = try {
            AppCompatDelegate.getApplicationLocales()
        } catch (_: Throwable) {
            null
        }
        if (current == desired) return
        try {
            AppCompatDelegate.setApplicationLocales(desired)
        } catch (_: Throwable) {
        }
    }

    private fun inPip(): Boolean = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
}
