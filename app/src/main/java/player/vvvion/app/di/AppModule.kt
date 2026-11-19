package player.vvvion.app.di

import player.vvvion.app.data.db.AppDatabase
import player.vvvion.app.data.net.appJson
import player.vvvion.app.data.net.createHttpClients
import player.vvvion.app.data.parse.M3uParser
import player.vvvion.app.data.parse.XmltvParser
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.data.repo.EpgRepository
import player.vvvion.app.data.repo.LibraryRepository
import player.vvvion.app.player.PlaybackCoordinator
import player.vvvion.app.player.PlayerEngine
import player.vvvion.app.ui.AppViewModel
import player.vvvion.app.ui.PlayerViewModel
import player.vvvion.app.update.UpdateRepository
import player.vvvion.app.work.SyncScheduler
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
    single { UpdateRepository(androidContext(), get()) }
    viewModel { AppViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get(), get()) }
}
