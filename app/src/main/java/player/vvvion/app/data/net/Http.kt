package player.vvvion.app.data.net

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

data class HttpClients(
    val playlist: OkHttpClient,
    val player: OkHttpClient,
)

const val DEFAULT_USER_AGENT = "VvvionIPTV/1.0.3 (Android)"

fun createHttpClients(context: Context): HttpClients {
    val pool = ConnectionPool(8, 5, TimeUnit.MINUTES)
    fun builder() = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectionPool(pool)
    val cacheDir = File(context.cacheDir, "http").apply { mkdirs() }
    return HttpClients(
        playlist = builder().cache(Cache(cacheDir, 64L * 1024 * 1024)).build(),
        player = builder().build(),
    )
}

fun appJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
