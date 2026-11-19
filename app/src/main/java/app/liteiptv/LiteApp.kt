package app.liteiptv

import android.app.Application
import app.liteiptv.data.net.HttpClients
import app.liteiptv.di.appModule
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LiteApp)
            modules(appModule)
        }
        val http = GlobalContext.get().get<HttpClients>()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .callFactory(http.playlist)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("logos"))
                        .maxSizeBytes(80L * 1024 * 1024)
                        .build()
                }
                .build(),
        )
    }
}
