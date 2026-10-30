package com.linkfetch.app.data.update

import com.linkfetch.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isNewer: Boolean,
    val notes: String,
    val apkUrl: String?,
)

class UpdateChecker(
    private val client: OkHttpClient,
    private val json: Json,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LinkFetch/$currentVersion")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("响应为空")
                val release = json.decodeFromString<GithubRelease>(body)
                val latest = release.tagName.removePrefix("v")
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latest,
                    isNewer = isVersionNewer(latest, currentVersion),
                    notes = cleanReleaseNotes(release.body.orEmpty()),
                    apkUrl = release.assets
                        .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        ?.downloadUrl,
                )
            }
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Zzzurt/LinkFetch/releases/latest"
    }
}

/** 按数字段比较版本号，忽略开头的 v 与预发布后缀；任意非数字段按 0 处理。 */
internal fun isVersionNewer(latest: String, current: String): Boolean {
    val latestParts = versionParts(latest)
    val currentParts = versionParts(current)
    for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
        val left = latestParts.getOrElse(index) { 0 }
        val right = currentParts.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionParts(version: String): List<Int> = version
    .trim()
    .removePrefix("v")
    .substringBefore('-')
    .split('.')
    .map { it.toIntOrNull() ?: 0 }
