package com.leonore.nexusjarvis.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Parser waktu sederhana (Indonesia/Inggris) untuk pengingat. Mengembalikan null jika tidak ada petunjuk waktu. */
object TimeParser {
    data class Result(val triggerAtMs: Long, val remainingText: String)

    private val relative1 = Regex("""\b(?:dalam|in)\s+(\d{1,3})\s*(menit|mnt|jam|hari|minutes?|mins?|hours?|days?)\b""")
    private val relative2 = Regex("""\b(\d{1,3})\s*(menit|jam|hari)\s+lagi\b""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})[ t](\d{1,2}):(\d{2})\b""")
    private val clock = Regex("""\b(?:jam|pukul|at)\s*(\d{1,2})(?:[:.](\d{2}))?\s*(pagi|siang|sore|malam|am|pm)?\b""")
    private val dayWord = Regex("""\b(besok|lusa|tomorrow|hari ini|today|nanti malam|tonight)\b""")

    private fun unitMs(unit: String): Long = when {
        unit.startsWith("m") -> 60_000L
        unit.startsWith("j") || unit.startsWith("hou") -> 3_600_000L
        else -> 86_400_000L
    }

    private fun to24(h: Int, part: String): Int = when (part) {
        "pagi", "am" -> if (h == 12) 0 else h
        "siang" -> if (h < 11) h + 12 else h
        "sore", "pm" -> if (h < 12) h + 12 else h
        "malam" -> if (h == 12) 0 else if (h < 12) h + 12 else h
        else -> h
    }

    private fun clean(s: String): String =
        s.replace(Regex("""\s+"""), " ").trim().trim(',', '.', ';', ':').trim()

    private fun removeRanges(text: String, ranges: List<IntRange>): String {
        var out = text
        for (r in ranges.sortedByDescending { it.first }) {
            if (r.first >= 0 && r.last < out.length) out = out.removeRange(r.first, r.last + 1)
        }
        return clean(out)
    }

    fun parse(input: String, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Result? {
        val text = input.trim()
        val lower = text.lowercase(Locale.ROOT)

        iso.find(lower)?.let { m ->
            val c = Calendar.getInstance(zone)
            c.clear()
            c.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt(), 0)
            return Result(c.timeInMillis, removeRanges(text, listOf(m.range)))
        }

        (relative1.find(lower) ?: relative2.find(lower))?.let { m ->
            val n = m.groupValues[1].toLong()
            return Result(nowMs + n * unitMs(m.groupValues[2]), removeRanges(text, listOf(m.range)))
        }

        val day = dayWord.find(lower)
        val clk = clock.find(lower)
        if (day == null && clk == null) return null

        val dayValue = day?.value
        val offsetDays = when (dayValue) {
            "besok", "tomorrow" -> 1
            "lusa" -> 2
            else -> 0
        }
        var hour = if (dayValue == "nanti malam" || dayValue == "tonight") 20 else 9
        var minute = 0
        if (clk != null) {
            val h = clk.groupValues[1].toInt()
            minute = clk.groupValues[2].ifEmpty { "0" }.toInt()
            hour = to24(h, clk.groupValues[3])
        }
        if (hour > 23 || minute > 59) return null

        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = nowMs
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val explicitFuture = dayValue == "besok" || dayValue == "lusa" || dayValue == "tomorrow"
        if (!explicitFuture && cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)

        val ranges = listOfNotNull(day?.range, clk?.range)
        return Result(cal.timeInMillis, removeRanges(text, ranges))
    }
}
