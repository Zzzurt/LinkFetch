package com.linkfetch.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ParseRequestDto(
    val url: String,
)

@Serializable
data class MediaItemDto(
    val kind: String,
    val url: String,
    val cover: String? = null,
    val quality: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** 是否为 Live 图（实况照片），仅 kind=image 时有效 */
    val live: Boolean = false,
    /** Live 图对应的短视频直链，live=true 时才有值 */
    val liveUrl: String? = null,
) {
    val isVideo: Boolean get() = kind == "video"
}

@Serializable
data class ParseResponseDto(
    val platform: String,
    val title: String,
    val author: String? = null,
    val type: String,
    val medias: List<MediaItemDto> = emptyList(),
) {
    val videos: List<MediaItemDto> get() = medias.filter { it.isVideo }
    val images: List<MediaItemDto> get() = medias.filter { !it.isVideo }
}

@Serializable
data class ApiErrorDto(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class HealthDto(
    val status: String = "",
    val service: String = "",
)

data class AppSettings(
    /** direct = App 直连解析（默认，无需服务器）；server = 自建服务器解析 */
    val parseMode: String = "direct",
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiToken: String = "",
    val xhsCookie: String = "",
    val douyinCookie: String = "",
    val weiboCookie: String = "",
    val downloadQuality: String = "hd",
    /** 解析成功后立即把全部内容加入保存队列 */
    val autoSave: Boolean = false,
    /** system = 跟随系统；light = 始终浅色；dark = 始终深色 */
    val themeMode: String = "system",
) {
    /** 用户是否配置过真实的自建服务器；出厂模拟器地址不算。 */
    val serverConfigured: Boolean
        get() = baseUrl.isNotBlank() && baseUrl.trim() != DEFAULT_BASE_URL

    companion object {
        /** 出厂默认地址仅供模拟器访问宿主机；用户不改它就不会被当作备用通道。 */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}
