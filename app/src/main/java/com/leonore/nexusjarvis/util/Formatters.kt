package com.leonore.nexusjarvis.util

import java.util.Locale

object Formatters {
    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun percent(part: Long, total: Long): Int =
        if (total <= 0) 0 else ((part * 100) / total).toInt().coerceIn(0, 100)

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) String.format(Locale.US, "%dj %02dm", h, m) else String.format(Locale.US, "%dm %02ds", m, sec)
    }
}
