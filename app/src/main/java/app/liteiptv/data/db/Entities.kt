package app.liteiptv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val url: String?,
    val fileUri: String?,
    val username: String?,
    val password: String?,
    val epgUrl: String?,
    val epgEtag: String?,
    val epgLastModified: String?,
    val playlistEtag: String?,
    val playlistLastModified: String?,
    val updateHours: Int,
    val tzOffsetMinutes: Int,
    val userAgent: String?,
    val lastSyncAt: Long?,
    val lastError: String?,
)

@Entity(
    tableName = "channels",
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "number"]),
        Index(value = ["playlistId", "groupTitle"]),
        Index(value = ["playlistId", "streamUrl"], unique = true),
        Index(value = ["playlistId", "dedupeKey"]),
        Index(value = ["playlistId", "epgKey"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val number: Int,
    val name: String,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val groupTitle: String,
    val catchup: String?,
    val catchupDays: Int?,
    val catchupSource: String?,
    val altUrls: String?,
    val dedupeKey: String,
    val epgKey: String,
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "streamUrl"])
data class FavoriteEntity(
    val playlistId: Long,
    val streamUrl: String,
    val sortOrder: Int,
)

@Entity(tableName = "hidden", primaryKeys = ["playlistId", "streamUrl"])
data class HiddenEntity(
    val playlistId: Long,
    val streamUrl: String,
)

@Entity(tableName = "history", primaryKeys = ["playlistId", "streamUrl"])
data class HistoryEntity(
    val playlistId: Long,
    val streamUrl: String,
    val channelId: Long,
    val channelName: String,
    val logo: String?,
    val playedAt: Long,
    val positionMs: Long,
)

@Entity(
    tableName = "programs",
    indices = [Index(value = ["playlistId", "channelKey", "startUtc"])],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelKey: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val stopUtc: Long,
)

data class GroupRow(
    val title: String,
    val count: Int,
)
