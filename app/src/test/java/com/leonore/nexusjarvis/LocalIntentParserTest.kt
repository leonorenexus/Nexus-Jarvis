package com.leonore.nexusjarvis

import com.leonore.nexusjarvis.ai.provider.LocalIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalIntentParserTest {
    @Test fun openApp() {
        val c = LocalIntentParser.parse("JARVIS, buka WhatsApp")!!
        assertEquals("open_app", c[0].tool)
        assertEquals("whatsapp", c[0].args["name"])
    }

    @Test fun openSettings() {
        val c = LocalIntentParser.parse("Buka pengaturan Wi-Fi")!!
        assertEquals("open_settings", c[0].tool)
        assertEquals("wi-fi", c[0].args["page"])
    }

    @Test fun deviceStatus() = assertEquals("device_status", LocalIntentParser.parse("Periksa baterai dan suhu perangkat")!![0].tool)

    @Test fun reminder() {
        val c = LocalIntentParser.parse("ingatkan saya minum obat besok jam 7 pagi")!!
        assertEquals("create_reminder", c[0].tool)
        assertEquals("minum obat", c[0].args["text"])
    }

    @Test fun multiStep() {
        val c = LocalIntentParser.parse("buka pengaturan wifi lalu periksa baterai")!!
        assertEquals(2, c.size)
        assertEquals("open_settings", c[0].tool)
        assertEquals("device_status", c[1].tool)
    }

    @Test fun message() {
        val c = LocalIntentParser.parse("kirim whatsapp ke +62812345678 isi halo apa kabar")!!
        assertEquals("compose_message", c[0].tool)
        assertEquals("whatsapp", c[0].args["channel"])
        assertEquals("halo apa kabar", c[0].args["text"])
    }

    @Test fun unknown() = assertNull(LocalIntentParser.parse("ceritakan lelucon"))
    @Test fun fileSearch() = assertNotNull(LocalIntentParser.parse("cari file laporan"))
}
