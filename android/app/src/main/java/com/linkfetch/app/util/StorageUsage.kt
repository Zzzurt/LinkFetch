package com.linkfetch.app.util

import android.content.Context
import java.io.File
import java.util.Locale

object StorageUsage {
    fun cacheBytes(context: Context): Long =
        listOfNotNull(context.cacheDir, context.externalCacheDir)
            .sumOf { directorySize(it) }

    fun clearCache(context: Context) {
        listOfNotNull(context.cacheDir, context.externalCacheDir)
            .forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
