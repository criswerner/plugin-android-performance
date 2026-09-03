package com.cristianwer.plugin.gfx

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GfxInfoParserTest {

    @Test
    fun testParseSampleGfxInfoOutput() {
        val sampleOutput = """
            Applications Graphics Acceleration Info:
            Uptime: 100000 Realtime: 100000

            ** Graphics info for pid 1234 [com.example.myapp] **

            Stats since last reset:
            Total frames rendered: 1200
            Janky frames: 60 (5.00%)
            50th percentile: 8ms
            90th percentile: 14ms
            95th percentile: 20ms
            99th percentile: 45ms
            Number Missed Vsync: 8
            Number Frame deadline missed: 15
            Number Slow UI thread: 12
            Number Slow enqueue: 3
            Number Slow Bitmap uploads: 6
            Number Slow issue draw commands: 4
            Number Frozen frames: 2

            HISTOGRAM: 5ms=800 10ms=300 20ms=80 750ms=1 1000ms=1

            Draw	Prepare	Process	Execute
            1.50	0.50	3.00	2.00
            2.50	1.50	4.00	3.00
        """.trimIndent()

        val report = GfxInfoParser.parse("com.example.myapp", sampleOutput)

        assertTrue(report.hasData)
        assertEquals("com.example.myapp", report.packageName)
        assertEquals(1200, report.totalFrames)
        assertEquals(60, report.jankyFrames)
        assertEquals(5.0, report.jankyPercentage, 0.01)
        assertEquals(8.0, report.p50Ms)
        assertEquals(14.0, report.p90Ms)
        assertEquals(20.0, report.p95Ms)
        assertEquals(45.0, report.p99Ms)
        assertEquals(2, report.frozenFrames)

        assertEquals(12, report.slowRenderingStats.slowUiThreadCount)
        assertEquals(3, report.slowRenderingStats.slowSyncCount)
        assertEquals(6, report.slowRenderingStats.slowBitmapUploadCount)
        assertEquals(4, report.slowRenderingStats.slowIssueDrawCount)
        assertEquals(8, report.slowRenderingStats.missedVsyncCount)

        assertNotNull(report.phaseBreakdown)
        val phase = report.phaseBreakdown!!
        assertEquals(2.0, phase.drawMs, 0.01)
        assertEquals(1.0, phase.prepareMs, 0.01)
        assertEquals(3.5, phase.processMs, 0.01)
        assertEquals(2.5, phase.executeMs, 0.01)
        assertEquals(3.0, phase.cpuPhaseMs, 0.01)
        assertEquals(6.0, phase.gpuPhaseMs, 0.01)
        assertEquals(9.0, phase.totalFrameMs, 0.01)
    }

    @Test
    fun testParseEmptyOutput() {
        val report = GfxInfoParser.parse("com.example.myapp", "")
        assertFalse(report.hasData)
        assertEquals(0, report.totalFrames)
        assertEquals(0, report.jankyFrames)
        assertNull(report.phaseBreakdown)
    }
}
