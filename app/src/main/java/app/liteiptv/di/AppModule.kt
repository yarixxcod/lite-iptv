package app.liteiptv.di

import app.liteiptv.data.db.AppDatabase
import app.liteiptv.data.net.appJson
import app.liteiptv.data.net.createHttpClients
import app.liteiptv.data.parse.M3uParser
import app.liteiptv.data.parse.XmltvParser
import app.liteiptv.data.prefs.SettingsStore
import app.liteiptv.data.repo.EpgRepository
import app.liteiptv.data.repo.LibraryRepository
import app.liteiptv.player.PlaybackCoordinator
import app.liteiptv.player.PlayerEngine
import app.liteiptv.ui.AppViewModel
import app.liteiptv.ui.PlayerViewModel
import app.liteiptv.work.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { createHttpClients(androidContext()) }
    single { appJson() }
    single { AppDatabase.build(androidContext()) }
    single { SettingsStore(androidContext()) }
    single { M3uParser() }
    single { XmltvParser() }
    single {
        LibraryRepository(
            context = androidContext(),
            db = get(),
            http = get(),
            parser = get(),
            json = get(),
        )
    }
    single { EpgRepository(androidContext(), get(), get(), get()) }
    single { PlayerEngine(androidContext(), get()) }
    single { SyncScheduler(androidContext(), get(), get()) }
    single { PlaybackCoordinator() }
    viewModel { AppViewModel(get(), get(), get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get(), get()) }
}
