package com.linkfetch.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** 已保存文件数。查询时由 download_tasks 统计得出，写入值仅为兼容旧表结构。 */
    val downloadedCount: Int = 0,
    /** v3 已写入的历史库列。收藏入口已移除，保留字段以免改动已升级设备的表结构。 */
    @ColumnInfo(defaultValue = "0")
    val favorite: Boolean = false,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Update
    suspend fun update(entity: HistoryEntity)

    /**
     * 已存数量从 download_tasks 实时统计：Worker 完成、重试、删除记录都会让这个 Flow 重新发射，
     * 历史页不再依赖任何"手动回写计数"的地方（此前该计数从未被写入，列表永远显示 0）。
     */
    @Query(
        """
        SELECT
            h.id AS id,
            h.platform AS platform,
            h.title AS title,
            h.author AS author,
            h.type AS type,
            h.coverUrl AS coverUrl,
            h.originalUrl AS originalUrl,
            h.mediaJson AS mediaJson,
            h.createdAt AS createdAt,
            h.favorite AS favorite,
            (
                SELECT COUNT(*) FROM download_tasks t
                WHERE t.historyId = h.id AND t.status = 'done'
            ) AS downloadedCount
        FROM history h
        ORDER BY h.createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE originalUrl = :url ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntity?

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * 一条媒体的保存任务。
 *
 * 任务描述自带 URL，Worker 不依赖内存里的解析结果 —— 进程被回收后仍能继续。
 * 唯一键 (historyId, mediaIndex, preferLive) 让同一条媒体不会被重复排队；
 * Live 图的动态版与静态版是两个不同变体，可分别保存。
 */
@Entity(
    tableName = "download_tasks",
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["historyId"]),
        Index(value = ["historyId", "mediaIndex", "preferLive"], unique = true),
        Index(value = ["status"]),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val historyId: Long,
    val mediaIndex: Int,
    val url: String,
    val liveUrl: String? = null,
    val isVideo: Boolean,
    val live: Boolean,
    /** true = 保存 Motion Photo；false = 只保存静态图 */
    val preferLive: Boolean,
    val prefix: String,
    val status: String = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val savedUri: String? = null,
    val error: String? = null,
    val bytes: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object DownloadStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
}

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: DownloadTaskEntity): Long

    @Query("SELECT * FROM download_tasks WHERE historyId = :historyId ORDER BY mediaIndex, preferLive DESC")
    fun observeForHistory(historyId: Long): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE historyId = :historyId ORDER BY id")
    suspend fun getForHistory(historyId: Long): List<DownloadTaskEntity>

    @Query("DELETE FROM download_tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 重新解析后刷新直链：排队中/失败的任务继续用新地址下载，已保存的任务不受影响。 */
    @Query("UPDATE download_tasks SET url = :url, liveUrl = :liveUrl, updatedAt = :now WHERE id = :id")
    suspend fun updateUrls(id: Long, url: String, liveUrl: String?, now: Long)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: Long): DownloadTaskEntity?

    @Query(
        "SELECT * FROM download_tasks WHERE historyId = :historyId AND mediaIndex = :mediaIndex " +
            "AND preferLive = :preferLive LIMIT 1",
    )
    suspend fun find(historyId: Long, mediaIndex: Int, preferLive: Boolean): DownloadTaskEntity?

    @Query("SELECT id FROM download_tasks WHERE status = :status ORDER BY id")
    suspend fun idsByStatus(status: String): List<Long>

    @Query(
        "SELECT * FROM download_tasks WHERE historyId = :historyId AND status = :status ORDER BY id",
    )
    suspend fun byStatus(historyId: Long, status: String): List<DownloadTaskEntity>

    @Query(
        "UPDATE download_tasks SET status = :status, progress = :progress, savedUri = :savedUri, " +
            "error = :error, bytes = :bytes, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateState(
        id: Long,
        status: String,
        progress: Float,
        savedUri: String?,
        error: String?,
        bytes: Long?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE download_tasks SET status = :queued, progress = 0, error = NULL, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun requeue(id: Long, queued: String, now: Long)

    /** 进程被强制停止后，遗留的 running 永远不会再收到回调，启动时重新排队。 */
    @Query(
        "UPDATE download_tasks SET status = :queued, progress = 0, updatedAt = :now " +
            "WHERE status = :running",
    )
    suspend fun resetRunning(running: String, queued: String, now: Long)

}

@Database(
    entities = [HistoryEntity::class, DownloadTaskEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}

/** v1 -> v2：新增下载任务表；历史记录原样保留。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `download_tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `historyId` INTEGER NOT NULL,
                `mediaIndex` INTEGER NOT NULL,
                `url` TEXT NOT NULL,
                `liveUrl` TEXT,
                `isVideo` INTEGER NOT NULL,
                `live` INTEGER NOT NULL,
                `preferLive` INTEGER NOT NULL,
                `prefix` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `savedUri` TEXT,
                `error` TEXT,
                `bytes` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`historyId`) REFERENCES `history`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_download_tasks_historyId` " +
                "ON `download_tasks` (`historyId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_download_tasks_historyId_mediaIndex_preferLive` " +
                "ON `download_tasks` (`historyId`, `mediaIndex`, `preferLive`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_download_tasks_status` " +
                "ON `download_tasks` (`status`)",
        )
    }
}

/**
 * v2 -> v3：新增收藏列，并合并同一链接的重复历史。
 *
 * 旧版本每次解析都 insert 一条新记录，同一链接反复取回会把历史刷满。
 * 合并时保留最新一条（列表里原本就在最上面，最不容易让用户感到记录"消失"），
 * 收藏状态取组内最大值；被删掉的重复记录下的下载任务由外键级联清理。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `history` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            UPDATE `history` SET `favorite` = (
                SELECT MAX(h2.`favorite`) FROM `history` h2
                WHERE h2.`originalUrl` = `history`.`originalUrl`
            )
            WHERE `id` IN (SELECT MAX(`id`) FROM `history` GROUP BY `originalUrl`)
            """.trimIndent(),
        )
        db.execSQL(
            "DELETE FROM `history` " +
                "WHERE `id` NOT IN (SELECT MAX(`id`) FROM `history` GROUP BY `originalUrl`)",
        )
    }
}

