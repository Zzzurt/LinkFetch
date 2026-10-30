package com.linkfetch.app.data.db

import com.linkfetch.app.data.model.MediaItemDto
import com.linkfetch.app.data.model.ParseResponseDto
import kotlinx.serialization.json.Json

/**
 * 历史记录的读写入库口。
 *
 * 同一条链接只保留一条历史：重复解析时原地更新内容并刷新时间，避免「最近取回」被同一作品刷屏。
 * 更新后还要校对下载任务 —— 媒体直链变了，旧的「已保存」标记就不再可信。
 */
class HistoryRepository(
    private val historyDao: HistoryDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val json: Json,
) {

    suspend fun save(url: String, response: ParseResponseDto): Long {
        val now = System.currentTimeMillis()
        val existing = historyDao.getLatestByUrl(url)
        if (existing == null) {
            return historyDao.insert(
                HistoryEntity(
                    platform = response.platform,
                    title = response.title,
                    author = response.author,
                    type = response.type,
                    coverUrl = preferredCover(response),
                    originalUrl = url,
                    mediaJson = encode(response),
                    createdAt = now,
                ),
            )
        }
        // 重新取回视为一次新的取回：时间刷新到最前，其余字段以新解析结果为准
        historyDao.update(
            existing.copy(
                platform = response.platform,
                title = response.title,
                author = response.author,
                type = response.type,
                coverUrl = preferredCover(response),
                mediaJson = encode(response),
                createdAt = now,
            ),
        )
        reconcileTasks(existing.id, response)
        return existing.id
    }

    /** 历史页「重新解析」：保持记录原有时间顺序，只刷新内容与直链。 */
    suspend fun refresh(entity: HistoryEntity, response: ParseResponseDto) {
        historyDao.update(
            entity.copy(
                platform = response.platform,
                title = response.title,
                author = response.author,
                type = response.type,
                coverUrl = preferredCover(response),
                mediaJson = encode(response),
            ),
        )
        reconcileTasks(entity.id, response)
    }

    private suspend fun reconcileTasks(historyId: Long, response: ParseResponseDto) {
        val tasks = downloadTaskDao.getForHistory(historyId)
        if (tasks.isEmpty()) return
        val removed = removedTaskIds(tasks, response.medias)
        if (removed.isNotEmpty()) downloadTaskDao.deleteByIds(removed)
        val now = System.currentTimeMillis()
        val outdated = outdatedTaskIds(tasks, response.medias).toSet()
        tasks.filter { it.id in outdated }.forEach { task ->
            val media = response.medias[task.mediaIndex]
            downloadTaskDao.updateUrls(task.id, media.url, media.liveUrl, now)
        }
    }

    private fun encode(response: ParseResponseDto): String =
        json.encodeToString(ParseResponseDto.serializer(), response)
}

/**
 * 封面选取必须避开「把视频地址当图片用」：
 *   1. 优先取第一张**图片**的 cover，没有 cover 就退到它自己的 url（图片 url 本身就是图）；
 *   2. 没有图片时，退到任意媒体的 cover（视频封面通常是 jpg/webp，能当缩略图）；
 *   3. 都没有才返回 null —— 此时列表走「平台色渐变底 + 徽标」占位。
 */
internal fun preferredCover(response: ParseResponseDto): String? =
    response.medias.firstOrNull { !it.isVideo }?.let { it.cover ?: it.url }
        ?: response.medias.firstNotNullOfOrNull { it.cover }

/**
 * 媒体条目在重新解析后消失：这些任务已没有对应内容，直接删掉。
 */
internal fun removedTaskIds(
    tasks: List<DownloadTaskEntity>,
    medias: List<MediaItemDto>,
): List<Long> = tasks.filter { medias.getOrNull(it.mediaIndex) == null }.map { it.id }

/**
 * 直链变了、但任务还没保存成功的条目：需要换成新地址再继续，而不是当作已完成。
 * 已保存（DONE）的任务保留标记 —— 文件已经在相册里，直链过期不影响它。
 */
internal fun outdatedTaskIds(
    tasks: List<DownloadTaskEntity>,
    medias: List<MediaItemDto>,
): List<Long> = tasks.filter { task ->
    val media = medias.getOrNull(task.mediaIndex) ?: return@filter false
    task.status != DownloadStatus.DONE &&
        (media.url != task.url || (task.preferLive && media.liveUrl != task.liveUrl))
}.map { it.id }
