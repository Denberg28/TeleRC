package io.github.denberg28.telerc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Short generated melody held in a static audio buffer and looped by Android's audio engine. */
internal class GameMusic {
    private var track: AudioTrack? = null
    fun start(): Boolean {
        if (track != null) return true
        var created: AudioTrack? = null
        return try {
            val sampleRate = 44100
            val noteFrames = sampleRate / 2
            val notes = intArrayOf(330, 392, 440, 392, 294, 349, 392, 440)
            val samples = ShortArray(noteFrames * notes.size)
            for (i in samples.indices) {
                val offset = i % noteFrames
                val frequency = notes[i / noteFrames]
                val phase = 2.0 * PI * frequency * offset / sampleRate
                val fadeIn = min(1.0, offset / 220.0)
                val fadeOut = min(1.0, (noteFrames - offset) / 1100.0)
                samples[i] = ((sin(phase) + .22 * sin(2 * phase)) * 5000 * fadeIn * fadeOut).toInt().toShort()
            }
            val audio = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC).build()
            created = audio
            check(audio.state == AudioTrack.STATE_NO_STATIC_DATA || audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size)
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
            audio.setVolume(.8f)
            audio.play()
            track = audio
            true
        } catch (_: Exception) {
            created?.let { runCatching { it.release() } }; false
        }
    }
    fun stop() { track?.let { runCatching { it.pause() }; runCatching { it.release() } }; track = null }
}
