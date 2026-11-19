package player.vvvion.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RemoteUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

private val manifestJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseUpdateManifest(body: String, localVersionCode: Int): RemoteUpdate? {
    val root = try {
        manifestJson.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return null
    }
    val codePrim = root["versionCode"]?.jsonPrimitive ?: return null
    val code = codePrim.intOrNull ?: codePrim.content.toIntOrNull() ?: return null
    if (code <= localVersionCode) return null
    val name = root["versionName"]?.jsonPrimitive?.content?.trim().orEmpty()
    val apk = root["apkUrl"]?.jsonPrimitive?.content?.trim().orEmpty()
    if (name.isEmpty() || !apk.startsWith("https://")) return null
    val notes = root["notes"]?.jsonPrimitive?.content?.trim().orEmpty()
    return RemoteUpdate(code, name, apk, notes)
}
