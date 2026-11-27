package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class FpvLinkTest {
    @Test fun urlValidatorAllowsPrivateLanAndBlocksPublicOrInjectedUrls() {
        val cam = FpvUrlValidator.parse("http://192.168.4.2:81/stream")
        assertNotNull(cam)
        assertEquals("192.168.4.2", cam!!.host)
        assertEquals(81, cam.port)
        assertEquals("/stream", cam.path)
        assertFalse(cam.isLoopback)

        assertEquals(80, FpvUrlValidator.parse("http://10.0.0.5/mjpeg")!!.port)
        assertNotNull(FpvUrlValidator.parse("http://172.16.5.4:8080/video"))
        assertNotNull(FpvUrlValidator.parse("http://172.31.255.1/stream"))
        assertNotNull(FpvUrlValidator.parse("http://169.254.10.2:81/stream"))
        assertTrue(FpvUrlValidator.parse("http://127.0.0.1:81/stream")!!.isLoopback)
        assertNotNull(FpvUrlValidator.parse("http://rovercam.local:81/stream"))

        // Rejects public IPs, public domains, non-http schemes, bad ports, credentials, and CRLF header injection.
        assertNull(FpvUrlValidator.parse("http://8.8.8.8:81/stream"))
        assertNull(FpvUrlValidator.parse("http://172.32.0.1:81/stream"))
        assertNull(FpvUrlValidator.parse("http://example.com/stream"))
        assertNull(FpvUrlValidator.parse("https://192.168.4.2:81/stream"))
        assertNull(FpvUrlValidator.parse("http://user:pass@192.168.4.2:81/stream"))
        assertNull(FpvUrlValidator.parse("http://192.168.4.2:0/stream"))
        assertNull(FpvUrlValidator.parse("http://192.168.4.2:70000/stream"))
        assertNull(FpvUrlValidator.parse("http://192.168.4.2:81/stream\r\nX-Inject: 1"))
        assertNull(FpvUrlValidator.parse(""))
    }

    @Test fun mjpegExtractorSplitsMultipartFramesAndRecoversFromOversizedGarbage() {
        fun fakeJpeg(payloadSize: Int, fill: Byte): ByteArray {
            val out = ByteArray(payloadSize + 4)
            out[0] = 0xFF.toByte()
            out[1] = 0xD8.toByte()
            for (i in 2 until payloadSize + 2) out[i] = fill
            out[payloadSize + 2] = 0xFF.toByte()
            out[payloadSize + 3] = 0xD9.toByte()
            return out
        }

        val frame1 = fakeJpeg(90, 0x11)
        val frame2 = fakeJpeg(120, 0x22)
        val header = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
        val combined = header + frame1 + header + frame2

        val extractor = MjpegFrameExtractor(maxFrameBytes = 256)
        val extracted = mutableListOf<ByteArray>()
        // Feed in small irregular 7-byte chunks to verify split SOI/EOI markers across read boundaries.
        var pos = 0
        while (pos < combined.size) {
            val len = minOf(7, combined.size - pos)
            val slice = combined.copyOfRange(pos, pos + len)
            extractor.push(slice, len) { extracted.add(it) }
            pos += len
        }
        assertEquals(2, extracted.size)
        assertArrayEquals(frame1, extracted[0])
        assertArrayEquals(frame2, extracted[1])

        // Oversized corrupted frame (>256 bytes) is dropped cleanly and next valid frame still decodes.
        val oversized = fakeJpeg(400, 0x33)
        val good = fakeJpeg(80, 0x44)
        extracted.clear()
        extractor.push(oversized + good, oversized.size + good.size) { extracted.add(it) }
        assertEquals(1, extracted.size)
        assertArrayEquals(good, extracted[0])
    }

    @Test fun supervisorDetectsStallHoldsFrameBrieflyEscalatesBackoffAndResetsWhenHealthy() {
        val sup = FpvLinkSupervisor()
        val g1 = sup.startConnecting("Wi-Fi FPV", nowMs = 1000L)
        assertEquals(FpvLinkSupervisor.State.CONNECTING, sup.state)
        assertFalse(sup.holdFrameVisible)

        // First live frame transitions to STREAMING.
        assertTrue(sup.onFrame(g1, nowMs = 1200L))
        assertEquals(FpvLinkSupervisor.State.STREAMING, sup.state)
        assertTrue(sup.holdFrameVisible)

        // Stale generation callbacks are ignored.
        assertFalse(sup.onFrame(g1 - 1, nowMs = 1300L))
        assertFalse(sup.onError(g1 - 1, "old error", nowMs = 1300L))

        // Before STALL_MS, tick does nothing.
        assertEquals(FpvLinkSupervisor.Action.NONE, sup.tick(nowMs = 5000L))

        // At STALL_MS (4000ms without frames), triggers immediate REOPEN with dimmed last-frame hold.
        assertEquals(FpvLinkSupervisor.Action.REOPEN, sup.tick(nowMs = 5200L))
        assertEquals(FpvLinkSupervisor.State.STALE_HOLD, sup.state)
        assertTrue(sup.holdFrameVisible)
        assertEquals(1, sup.consecutiveFailures)

        // If reconnect takes longer than HOLD_FRAME_MS, dimmed stale frame is cleared so it is never mistaken for live.
        val g2 = sup.startConnecting("Wi-Fi FPV", nowMs = 5200L)
        assertTrue(sup.onError(g2, "Unreachable", nowMs = 5500L))
        assertEquals(FpvLinkSupervisor.State.STALE_HOLD, sup.state)
        // Second failure -> RETRY_MS = 3000ms (retry at 8500ms), hold expires at 9500ms.
        assertEquals(FpvLinkSupervisor.Action.NONE, sup.tick(nowMs = 8499L))
        assertEquals(FpvLinkSupervisor.Action.REOPEN, sup.tick(nowMs = 8500L))
        assertEquals(FpvLinkSupervisor.Action.CLEAR_HOLD, sup.tick(nowMs = 9500L))
        assertFalse(sup.holdFrameVisible)

        // Escalate past PERSISTENT_FAILURES -> SLOW_RETRY_MS (6000ms).
        val g3 = sup.startConnecting("Wi-Fi FPV", nowMs = 10_000L)
        sup.onError(g3, "Err", nowMs = 10_000L) // failures = 3
        val g4 = sup.startConnecting("Wi-Fi FPV", nowMs = 13_000L)
        sup.onError(g4, "Err", nowMs = 13_000L) // failures = 4 -> SLOW_RETRY_MS (6000ms)
        assertEquals(FpvLinkSupervisor.Action.NONE, sup.tick(nowMs = 18_999L))
        assertEquals(FpvLinkSupervisor.Action.REOPEN, sup.tick(nowMs = 19_000L))

        // Sustained healthy streaming for HEALTHY_MS (10s) resets backoff to 0.
        val g5 = sup.startConnecting("Wi-Fi FPV", nowMs = 20_000L)
        sup.onFrame(g5, nowMs = 20_100L)
        assertEquals(4, sup.consecutiveFailures)
        sup.onFrame(g5, nowMs = 30_100L)
        assertEquals(0, sup.consecutiveFailures)
    }

    @Test fun rtl8812buDetectorRecognizesSupportedDongles() {
        assertTrue(Rtl8812buDetector.isRtl8812bu(0x0BDA, 0xB812))
        assertTrue(Rtl8812buDetector.isRtl8812bu(0x2357, 0x0115))
        assertTrue(Rtl8812buDetector.isRtl8812bu(0x2357, 0x012D))
        assertTrue(Rtl8812buDetector.isRtl8812bu(0x2357, 0x0138))
        assertEquals("Realtek RTL8812BU", Rtl8812buDetector.adapterName(0x0BDA, 0xB812))
        assertFalse(Rtl8812buDetector.isRtl8812bu(0x1234, 0x5678))
    }
}
