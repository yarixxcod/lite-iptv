package player.vvvion.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import player.vvvion.app.data.db.AppDatabase
import player.vvvion.app.data.prefs.AppSettings
import player.vvvion.app.data.prefs.SettingsStore
import player.vvvion.app.data.repo.EpgRepository
import player.vvvion.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        if (id < 0) return Result.failure()
        val kind = inputData.getString(KEY_KIND) ?: KIND_ALL
        val koin = GlobalContext.get()
        val library = koin.get<LibraryRepository>()
        val epg = koin.get<EpgRepository>()
        return try {
            if (kind == KIND_ALL || kind == KIND_PLAYLIST) library.syncPlaylist(id)
            if (kind == KIND_ALL || kind == KIND_EPG) epg.sync(id)
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "playlistId"
        const val KEY_KIND = "kind"
        const val KIND_ALL = "all"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_EPG = "epg"
    }
}

class SyncScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    suspend fun ensureAll() {
        val current = settings.settings.first()
        db.playlists().all().forEach { schedule(it.id, it.updateHours, it.type, current) }
    }

    fun schedule(playlistId: Long, updateHours: Int, type: String, settings: AppSettings) {
        val manager = WorkManager.getInstance(context)
        val playlistWork = "playlist-$playlistId"
        val epgWork = "epg-$playlistId"
        if (updateHours <= 0 || type == LibraryRepository.TYPE_PASTE) {
            manager.cancelUniqueWork(playlistWork)
        } else {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(updateHours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
                .setInputData(workDataOf(SyncWorker.KEY_ID to playlistId, SyncWorker.KEY_KIND to SyncWorker.KIND_PLAYLIST))
                .setConstraints(constraints(settings.epgUnmeteredOnly))
                .build()
            manager.enqueueUniquePeriodicWork(playlistWork, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        val epgHours = settings.epgRefreshHours.coerceAtLeast(1).toLong()
        val epg = PeriodicWorkRequestBuilder<SyncWorker>(epgHours, TimeUnit.HOURS)
            .setInputData(workDataOf(SyncWorker.KEY_ID to playlistId, SyncWorker.KEY_KIND to SyncWorker.KIND_EPG))
            .setConstraints(constraints(settings.epgUnmeteredOnly))
            .build()
        manager.enqueueUniquePeriodicWork(epgWork, ExistingPeriodicWorkPolicy.UPDATE, epg)
    }

    private fun constraints(unmetered: Boolean) = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
}
