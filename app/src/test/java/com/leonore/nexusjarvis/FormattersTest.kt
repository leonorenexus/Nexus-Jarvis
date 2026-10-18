package com.leonore.nexusjarvis

import com.leonore.nexusjarvis.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun bytesSmall() = assertEquals("512 B", Formatters.bytes(512))
    @Test fun bytesKb() = assertEquals("1.0 KB", Formatters.bytes(1024))
    @Test fun bytesGb() = assertEquals("1.5 GB", Formatters.bytes(1536L * 1024 * 1024))
    @Test fun percentZeroTotal() = assertEquals(0, Formatters.percent(5, 0))
    @Test fun percentHalf() = assertEquals(50, Formatters.percent(50, 100))
}
