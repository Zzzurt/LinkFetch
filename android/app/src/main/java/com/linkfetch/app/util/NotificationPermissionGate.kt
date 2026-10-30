package com.linkfetch.app.util

/**
 * 通知权限的唯一请求入口：ViewModel 在真正发起保存前调用 [ensure]，
 * Activity 负责弹出系统对话框。这样自动保存与单张保存都能覆盖，且只请求一次。
 */
class NotificationPermissionGate {
    @Volatile
    private var granted = true

    @Volatile
    private var requested = false

    @Volatile
    private var requester: (() -> Unit)? = null

    fun updateGranted(value: Boolean) {
        granted = value
    }

    fun attach(requester: () -> Unit) {
        this.requester = requester
    }

    fun detach() {
        requester = null
    }

    fun ensure() {
        if (granted || requested) return
        val request = requester ?: return
        requested = true
        request()
    }
}
