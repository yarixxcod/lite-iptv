package app.liteiptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        HiddenEntity::class,
        HistoryEntity::class,
        ProgramEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun channels(): ChannelDao
    abstract fun favorites(): FavoriteDao
    abstract fun hidden(): HiddenDao
    abstract fun history(): HistoryDao
    abstract fun programs(): ProgramDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "liteiptv.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
