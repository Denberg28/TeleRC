package io.github.denberg28.telerc

/** Observed CH7 override permission; it does not assert ELRS receiver health. */
internal class RcTakeover {
    private var at: Long? = null
    private var boot: Long? = null
    private var permitted = false
    @Synchronized fun reset() { at = null; boot = null; permitted = false }
    @Synchronized fun allowed(now: Long) = at?.let { now >= it && now-it < 750 && permitted } == true
    @Synchronized fun label(now: Long): String = when {
        at == null || now < at!! || now-at!! >= 750 -> "RC authority unknown"
        permitted -> "RC permits phone"
        else -> "RC blocks phone"
    }
    @Synchronized fun receive(f: ByteArray, target: Int, now: Long) {
        if (f.size < 8) return
        val v1 = f[0] == 254.toByte(); val v2 = f[0] == 253.toByte()
        if (!v1 && !v2) return
        val h = if(v1) 6 else 10; val n = f[1].toInt() and 255
        if(f.size != h+n+2 || n !in (if(v1) 42 else 41)..42) return
        if(v2 && (f[2].toInt()!=0 || f[8].toInt()!=0 || f[9].toInt()!=0)) return
        if((f[if(v1) 5 else 7].toInt() and 255)!=65 ||
            (f[if(v1) 3 else 5].toInt() and 255)!=target || f[if(v1) 4 else 6].toInt()!=1) return
        var crc=65535
        for(value in f.copyOfRange(1,h+n).map { it.toInt() and 255 } + 118) {
            var t=value xor (crc and 255);t=(t xor (t shl 4)) and 255
            crc=((crc ushr 8) xor (t shl 8) xor (t shl 3) xor (t ushr 4)) and 65535
        }
        if((f[h+n].toInt() and 255)!=(crc and 255) || (f[h+n+1].toInt() and 255)!=(crc ushr 8) || (f[h+40].toInt() and 255)<7) return
        val stamp=(0..3).fold(0L) { acc,i -> acc or ((f[h+i].toLong() and 255) shl(i*8)) }
        boot?.let { previous ->
            if(stamp==previous) return
            if(stamp<previous) { reset(); return }
        }
        boot=stamp;at=now
        val channel=(f[h+16].toInt() and 255) or ((f[h+17].toInt() and 255) shl 8)
        permitted=channel in 1801..2200
    }
}
