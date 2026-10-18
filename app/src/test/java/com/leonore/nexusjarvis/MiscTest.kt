package com.leonore.nexusjarvis

import com.leonore.nexusjarvis.ai.provider.ModelOutputParser
import com.leonore.nexusjarvis.automation.RoutineRepository
import com.leonore.nexusjarvis.monitoring.AlertLevel
import com.leonore.nexusjarvis.monitoring.DeviceSnapshot
import com.leonore.nexusjarvis.monitoring.MonitorAlerts
import com.leonore.nexusjarvis.util.AppIndex
import com.leonore.nexusjarvis.util.LaunchableApp
import com.leonore.nexusjarvis.voice.WakeWordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test fun modelJson() {
        val r = ModelOutputParser.parse("```json\n{\"reply\":\"ok\",\"actions\":[{\"tool\":\"open_app\",\"args\":{\"name\":\"Chrome\"}}]}\n```")
        assertEquals("ok", r.text)
        assertEquals("open_app", r.toolCalls[0].tool)
        assertEquals("Chrome", r.toolCalls[0].args["name"])
    }

    @Test fun modelPlainText() = assertEquals("halo", ModelOutputParser.parse("halo").text)

    @Test fun wakeWord() {
        assertEquals("buka whatsapp", WakeWordManager.extractCommand("Jarvis, buka whatsapp", "jarvis"))
        assertEquals("", WakeWordManager.extractCommand("jarvis", "jarvis"))
        assertNull(WakeWordManager.extractCommand("halo dunia", "jarvis"))
    }

    @Test fun appMatch() {
        val apps = listOf(LaunchableApp("WhatsApp", "a.b", false), LaunchableApp("WhatsApp Business", "a.c", false), LaunchableApp("Chrome", "d.e", false))
        assertEquals("a.b", AppIndex.match(apps, "whatsapp").best?.packageName)
        assertNull(AppIndex.match(apps, "whats").best)
        assertEquals(2, AppIndex.match(apps, "whats").candidates.size)
    }

    @Test fun nextTrigger() {
        assertNull(RoutineRepository.nextTrigger(0, 0, 1000))
        assertEquals(3_600_000L, RoutineRepository.nextTrigger(0, 60, 1000))
    }

    @Test fun alerts() {
        val s = DeviceSnapshot("x", "14", 34, 8, false, 46f, null, null, null, 100, 50, 100, 50, "Wi-Fi", 0)
        val a = MonitorAlerts.evaluate(s)
        assertTrue(a.any { it.level == AlertLevel.CRITICAL })
    }
}
