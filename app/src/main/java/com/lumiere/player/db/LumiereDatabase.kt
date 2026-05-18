package com.lumiere.player.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ────────────────────────────────────────────────

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val uriString: String,
    val fileName: String,
    val lastPosition: Long = 0L,
    val duration: Long = 0L,
    val lastWatched: Long = System.currentTimeMillis(),
    val watchCount: Int = 1
)

@Entity(tableName = "playlist")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uriString: String,
    val fileName: String,
    val sortOrder: Int = 0
)

// ─── DAOs ─────────────────────────────────────────────────────

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatched DESC LIMIT 50")
    fun getAll(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE uriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): WatchHistory?

    @Upsert
    suspend fun upsert(item: WatchHistory)

    @Query("DELETE FROM watch_history WHERE uriString = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistItem)

    @Delete
    suspend fun delete(item: PlaylistItem)

    @Query("DELETE FROM playlist")
    suspend fun clearAll()

    @Query("UPDATE playlist SET sortOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Int, order: Int)
}

// ─── Database ─────────────────────────────────────────────────

@Database(entities = [WatchHistory::class, PlaylistItem::class], version = 1, exportSchema = false)
abstract class LumiereDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var INSTANCE: LumiereDatabase? = null

        fun getInstance(context: Context): LumiereDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LumiereDatabase::class.java,
                    "lumiere_db"
                ).build().also { INSTANCE = it }
            }
    }
}
