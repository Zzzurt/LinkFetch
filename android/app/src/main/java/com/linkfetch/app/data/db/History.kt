package com.linkfetch.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val platform: String,
    val title: String,
    val author: String? = null,
    val type: String,
    val coverUrl: String? = null,
    val originalUrl: String,
    val mediaJson: String,
    val createdAt: Long,
    val downloadedCount: Int = 0,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Update
    suspend fun update(entity: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE originalUrl = :url ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByUrl(url: String): HistoryEntity?

    @Query("UPDATE history SET downloadedCount = :count WHERE id = :id")
    suspend fun updateDownloadedCount(id: Long, count: Int)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

