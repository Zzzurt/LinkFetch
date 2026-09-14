package com.linkfetch.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 历史记录时间格式。
 *
 * formatter 提到常量：原先每次调用都新建 SimpleDateFormat，而该方法在 LazyColumn 中逐项渲染时会被反复调用。
 * minSdk 26 起可直接使用 java.time，线程安全性也优于 SimpleDateFormat。
 *
 * 固定用 Locale.US 而非 Locale.getDefault()：该 pattern 只有数字字段，
 * 输出与语言无关；写死可避免「静态字段在运行时切换系统语言后不更新」的隐患。
 */
private val HISTORY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

fun formatHistoryTime(timestamp: Long): String =
    HISTORY_TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
