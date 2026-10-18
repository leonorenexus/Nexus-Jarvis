package com.leonore.nexusjarvis

import com.leonore.nexusjarvis.voice.WakeWordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordManagerTest {
    @Test fun extractsCasualCommand() {
        assertEquals("buka WhatsApp dong", WakeWordManager.extractCommand("Hai JARVIS, buka WhatsApp dong", "jarvis"))
    }

    @Test fun recognizesTriggerWithoutCommand() {
        assertEquals("", WakeWordManager.extractCommand("JARVIS!", "jarvis"))
    }

    @Test fun ignoresTranscriptWithoutTrigger() {
        assertNull(WakeWordManager.extractCommand("buka WhatsApp dong", "jarvis"))
    }
}
