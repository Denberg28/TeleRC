package io.github.denberg28.telerc

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bound network metadata before allocating a JSON tree. Does not own the stream. */
internal object UpdateMetadata {
    const val MAX_BYTES=1024*1024
    fun read(input:InputStream):String {
        val output=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            require(output.size()<=MAX_BYTES-count) { "Release metadata exceeds limit" }
            output.write(buffer,0,count)
        }
        return output.toString("UTF-8")
    }
}
