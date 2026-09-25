package io.github.denberg28.telerc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Game-only motor loop and collision cue. All sound uses the phone's media stream. */
internal class GameEffects {
    private var engine: AudioTrack? = null
    private var collision: ToneGenerator? = null

    fun update(drive: Float, steering: Float) {
        val demand = maxOf(abs(drive), abs(steering)).coerceIn(0f, 1f)
        if (demand < .09f) {
            engine?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) runCatching { it.pause() } }
            return
        }
        val audio = engine ?: createEngine()?.also { engine = it } ?: return
        runCatching {
            audio.setPlaybackRate((44_100 * (0.75f + demand * .5f)).toInt())
            audio.setVolume(.12f + demand * .28f)
            if (audio.playState != AudioTrack.PLAYSTATE_PLAYING) audio.play()
        }
    }

    private fun createEngine(): AudioTrack? {
        var track: AudioTrack? = null
        return try {
            val rate = 44_100
            // A seamless 0.2 second loop with harmonic content sounds like a small electric motor.
            val samples = ShortArray(rate / 5) { index ->
                val phase = 2.0 * PI * 120 * index / rate
                ((sin(phase) + .28 * sin(phase * 2) + .12 * sin(phase * 3)) * 8_000).toInt().toShort()
            }
            val audio = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC).build()
            track = audio
            check(audio.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size)
            check(audio.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
            audio
        } catch (_: Exception) {
            track?.let { runCatching { it.release() } }
            null
        }
    }

    fun hit() {
        runCatching {
            if (collision == null) collision = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            collision?.startTone(ToneGenerator.TONE_PROP_NACK, 260)
        }
    }

    fun stop() {
        engine?.let { runCatching { it.pause() }; runCatching { it.release() } }
        engine = null
        collision?.let { runCatching { it.release() } }
        collision = null
    }
}
