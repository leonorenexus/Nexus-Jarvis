package com.leonore.nexusjarvis

import com.leonore.nexusjarvis.util.TimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeParserTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(utc); c.clear(); c.set(y, mo - 1, d, h, mi, 0); return c.timeInMillis
    }
    private val now = at(2026, 10, 17, 10, 0)

    @Test fun relativeMinutes() {
        val r = TimeParser.parse("dalam 10 menit minum obat", now, utc)
        assertNotNull(r)
        assertEquals(now + 10 * 60_000L, r!!.triggerAtMs)
        assertEquals("minum obat", r.remainingText)
    }

    @Test fun tomorrowMorning() {
        val r = TimeParser.parse("besok jam 7 pagi", now, utc)
        assertEquals(at(2026, 10, 18, 7, 0), r!!.triggerAtMs)
    }

    @Test fun eveningToday() {
        val r = TimeParser.parse("jam 9 malam", now, utc)
        assertEquals(at(2026, 10, 17, 21, 0), r!!.triggerAtMs)
    }

    @Test fun pastClockRollsToTomorrow() {
        val r = TimeParser.parse("jam 8 pagi", now, utc)
        assertEquals(at(2026, 10, 18, 8, 0), r!!.triggerAtMs)
    }

    @Test fun isoFormat() {
        val r = TimeParser.parse("2026-12-31 08:30", now, utc)
        assertEquals(at(2026, 12, 31, 8, 30), r!!.triggerAtMs)
    }

    @Test fun noTime() = assertNull(TimeParser.parse("minum obat", now, utc))
}
