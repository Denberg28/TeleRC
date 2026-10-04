package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class SerialDatagramTest {
    @Test fun fragmentedAndConcatenatedFrames() {
        val parser = SerialDatagram.Parser()
        val first = SerialDatagram.encode("neutral".toByteArray())
        assertTrue(parser.feed(first.copyOfRange(0, 3), 3, 100).isEmpty())
        val rest = first.copyOfRange(3, first.size) + SerialDatagram.encode("arm".toByteArray())
        val decoded = parser.feed(rest, rest.size, 105)
        assertEquals(2, decoded.size)
        assertArrayEquals("neutral".toByteArray(), decoded[0])
        assertArrayEquals("arm".toByteArray(), decoded[1])
    }
    @Test fun corruptionRejectedThenParserRecovers() {
        val bad = SerialDatagram.encode("drive".toByteArray())
        bad[5] = (bad[5].toInt() xor 1).toByte()
        val input = bad + SerialDatagram.encode("stop".toByteArray())
        val result = SerialDatagram.Parser().feed(input, input.size, 100)
        assertEquals(1, result.size)
        assertArrayEquals("stop".toByteArray(), result[0])
    }
    @Test fun staleFragmentNeverBecomesCommand() {
        val parser = SerialDatagram.Parser()
        val frame = SerialDatagram.encode("drive".toByteArray())
        parser.feed(frame.copyOfRange(0, 4), 4, 100)
        val stale = frame.copyOfRange(4, frame.size)
        assertTrue(parser.feed(stale, stale.size, 121).isEmpty())
        val stop = SerialDatagram.encode("stop".toByteArray())
        assertArrayEquals("stop".toByteArray(), parser.feed(stop, stop.size, 122).single())
    }
    @Test fun firmwareCompatibleKnownVectorAndMaximumSize() {
        // CRC16-CCITT/FFFF over LE length + ASCII payload, checked independently against Python binascii.
        assertArrayEquals(byteArrayOf(0xa5.toByte(), 0x5a, 1, 0, 0x41, 0x49.toByte(), 0xa3.toByte()), SerialDatagram.encode(byteArrayOf(0x41)))
        val payload = ByteArray(280) { it.toByte() }
        val frame = SerialDatagram.encode(payload)
        assertArrayEquals(payload, SerialDatagram.Parser().feed(frame, frame.size, 100).single())
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedRejected() { SerialDatagram.encode(ByteArray(281)) }
    @Test(expected = IllegalArgumentException::class) fun emptyRejected() { SerialDatagram.encode(byteArrayOf()) }
}
