package com.linkfetch.app.util

/**
 * 判断主机是否为回环或私网地址。
 *
 * 用途：App 默认禁止明文流量（见 res/xml/network_security_config.xml），
 * 仅对可信内网地址放行 http —— 此时 `X-API-Token` 与平台 Cookie 才会以明文发出，
 * 落到公网地址上则等同于把账号登录态公开。
 */
fun isPrivateOrLoopbackHost(host: String): Boolean {
    val normalized = host.lowercase().trim().removeSurrounding("[", "]")
    if (normalized == "localhost" || normalized == "::1") return true

    val parts = normalized.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false

    return when {
        octets[0] == 127 -> true // 回环 127.0.0.0/8
        octets[0] == 10 -> true // 10.0.0.0/8（含模拟器宿主机 10.0.2.2）
        octets[0] == 192 && octets[1] == 168 -> true // 192.168.0.0/16
        octets[0] == 172 && octets[1] in 16..31 -> true // 172.16.0.0/12
        octets[0] == 169 && octets[1] == 254 -> true // 链路本地 169.254.0.0/16
        else -> false
    }
}
