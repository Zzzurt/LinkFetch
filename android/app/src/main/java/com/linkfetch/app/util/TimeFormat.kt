package com.linkfetch.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatHistoryTime(timestamp: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

