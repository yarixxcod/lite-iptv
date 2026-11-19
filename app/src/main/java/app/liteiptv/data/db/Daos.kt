package app.liteiptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun get(id: Long): PlaylistEntity?

    @Insert
    fun insert(entity: PlaylistEntity): Long

    @Query(
        """
        UPDATE playlists SET name = :name, url = :url, fileUri = :fileUri, username = :username,
        password = :password, epgUrl = :epgUrl, updateHours = :updateHours, tzOffsetMinutes = :tzOffsetMinutes,
        userAgent = :userAgent WHERE id = :id
        """,
    )
    fun updateMeta(
        id: Long,
        name: String,
        url: String?,
        fileUri: String?,
        username: String?,
        password: String?,
        epgUrl: String?,
        updateHours: Int,
        tzOffsetMinutes: Int,
        userAgent: String?,
    )

    @Query(
        """
        UPDATE playlists SET epgUrl = COALESCE(:epgUrl, epgUrl), epgEtag = :etag,
        epgLastModified = :lastModified, lastSyncAt = :syncedAt, lastError = :error WHERE id = :id
        """,
    )
    fun updateEpgStatus(id: Long, epgUrl: String?, etag: String?, lastModified: String?, syncedAt: Long?, error: String?)

    @Query(
        """
        UPDATE playlists SET playlistEtag = :etag, playlistLastModified = :lastModified,
        lastSyncAt = :syncedAt, lastError = :error, epgUrl = COALESCE(:epgUrl, epgUrl) WHERE id = :id
        """,
    )
    fun updatePlaylistStatus(id: Long, etag: String?, lastModified: String?, syncedAt: Long?, error: String?, epgUrl: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    fun deleteByPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<ChannelEntity>)

    @Query("UPDATE channels SET altUrls = :alts WHERE playlistId = :playlistId AND streamUrl = :url")
    fun setAlts(playlistId: Long, url: String, alts: String)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    fun count(playlistId: Long): Int

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels WHERE playlistId = :playlistId AND number > :number
        ORDER BY number ASC LIMIT 1
        """,
    )
    suspend fun next(playlistId: Long, number: Int): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels WHERE playlistId = :playlistId AND number < :number
        ORDER BY number DESC LIMIT 1
        """,
    )
    suspend fun prev(playlistId: Long, number: Int): ChannelEntity?

    @Query(
        """
        SELECT c.groupTitle AS title, COUNT(*) AS count FROM channels c
        WHERE c.playlistId = :playlistId
        AND NOT EXISTS (
            SELECT 1 FROM hidden h WHERE h.playlistId = c.playlistId AND h.streamUrl = c.streamUrl
        )
        GROUP BY c.groupTitle
        ORDER BY c.groupTitle COLLATE NOCASE
        """,
    )
    fun groups(playlistId: Long): Flow<List<GroupRow>>

    @Query(
        """
        SELECT c.* FROM channels c
        WHERE c.playlistId = :playlistId
        AND NOT EXISTS (
            SELECT 1 FROM hidden h WHERE h.playlistId = c.playlistId AND h.streamUrl = c.streamUrl
        )
        AND (:group = '' OR c.groupTitle = :group)
        AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\'
             OR IFNULL(c.tvgName, '') LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY c.number ASC, c.name COLLATE NOCASE ASC
        """,
    )
    fun pagingByNumber(playlistId: Long, group: String, query: String): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        WHERE c.playlistId = :playlistId
        AND NOT EXISTS (
            SELECT 1 FROM hidden h WHERE h.playlistId = c.playlistId AND h.streamUrl = c.streamUrl
        )
        AND (:group = '' OR c.groupTitle = :group)
        AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\'
             OR IFNULL(c.tvgName, '') LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY c.name COLLATE NOCASE ASC, c.number ASC
        """,
    )
    fun pagingByName(playlistId: Long, group: String, query: String): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN favorites f ON f.playlistId = c.playlistId AND f.streamUrl = c.streamUrl
        WHERE c.playlistId = :playlistId
        AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY f.sortOrder ASC
        """,
    )
    fun pagingFavorites(playlistId: Long, query: String): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN favorites f ON f.playlistId = c.playlistId AND f.streamUrl = c.streamUrl
        WHERE c.playlistId = :playlistId
        ORDER BY f.sortOrder ASC
        """,
    )
    suspend fun favoriteChannels(playlistId: Long): List<ChannelEntity>

    @Query(
        """
        SELECT streamUrl FROM channels
        WHERE playlistId = :playlistId AND groupTitle = :group
        ORDER BY number ASC LIMIT :limit
        """,
    )
    suspend fun urlsForGroup(playlistId: Long, group: String, limit: Int): List<String>

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND (:group = '' OR groupTitle = :group)
        ORDER BY number ASC LIMIT :limit
        """,
    )
    suspend fun head(playlistId: Long, group: String, limit: Int): List<ChannelEntity>
}

@Dao
interface FavoriteDao {
    @Query("SELECT streamUrl FROM favorites WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun urls(playlistId: Long): List<String>

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :playlistId AND streamUrl = :url")
    fun isFavorite(playlistId: Long, url: String): Flow<Int>

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FavoriteEntity>)
}

@Dao
interface HiddenDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entity: HiddenEntity)

    @Query("DELETE FROM hidden WHERE playlistId = :playlistId AND streamUrl = :url")
    suspend fun unhide(playlistId: Long, url: String)

    @Query("DELETE FROM hidden WHERE playlistId = :playlistId")
    fun deletePlaylist(playlistId: Long)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface ProgramDao {
    @Query("DELETE FROM programs WHERE playlistId = :playlistId")
    fun deletePlaylist(playlistId: Long)

    @Insert
    fun insertAll(items: List<ProgramEntity>)

    @Query(
        """
        SELECT * FROM programs
        WHERE playlistId = :playlistId AND channelKey IN (:keys)
        AND stopUtc > :now AND startUtc < :until
        ORDER BY channelKey, startUtc
        """,
    )
    suspend fun window(playlistId: Long, keys: List<String>, now: Long, until: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE playlistId = :playlistId AND channelKey = :key AND stopUtc > :now
        ORDER BY startUtc ASC LIMIT :limit
        """,
    )
    fun upcoming(playlistId: Long, key: String, now: Long, limit: Int = 12): Flow<List<ProgramEntity>>
}
