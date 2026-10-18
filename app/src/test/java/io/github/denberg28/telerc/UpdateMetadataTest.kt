package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateMetadataTest {
    @Test fun preservesUtf8AcrossBufferBoundaries() {
        val text=" ".repeat(8191)+"✓ TeleRC"
        assertEquals(text,UpdateMetadata.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))))
    }
    @Test fun allowsExactLimitAndRejectsOneExtraByte() {
        assertEquals(UpdateMetadata.MAX_BYTES,UpdateMetadata.read(ByteArrayInputStream(ByteArray(UpdateMetadata.MAX_BYTES){32})).length)
        assertThrows(IllegalArgumentException::class.java) {
            UpdateMetadata.read(ByteArrayInputStream(ByteArray(UpdateMetadata.MAX_BYTES+1){32}))
        }
    }
    @Test fun rejectsGrowingStreamWithoutReadingUnboundedInput() {
        var read=0
        val endless=object:java.io.InputStream() {
            override fun read():Int { read++; return 32 }
        }
        assertThrows(IllegalArgumentException::class.java) { UpdateMetadata.read(endless) }
        assertTrue(read<=UpdateMetadata.MAX_BYTES+8192)
    }
}
