package com.example.m3cord

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import java.io.File
import kotlin.math.sqrt

data class Voice(val file: File, val secs: Float, val waveform: String)

/** Records Opus-in-Ogg (Discord voice message format). Needs Android 10+. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startAt = 0L
    private val amps = ArrayList<Int>()

    fun start() {
        val f = File(ctx.cacheDir, "voice-message.ogg")
        f.delete()
        file = f
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        r.setAudioSamplingRate(48000)
        r.setAudioEncodingBitRate(32000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        rec = r
        startAt = SystemClock.elapsedRealtime()
        amps.clear()
    }

    fun elapsed(): Long = if (rec == null) 0L else SystemClock.elapsedRealtime() - startAt

    fun sample() {
        try { rec?.let { amps.add(it.maxAmplitude) } } catch (e: Exception) { }
    }

    fun stop(): Voice? {
        val r = rec ?: return null
        val f = file ?: return null
        rec = null
        val secs = (SystemClock.elapsedRealtime() - startAt) / 1000f
        try { r.stop() } catch (e: RuntimeException) { r.release(); f.delete(); return null }
        r.release()
        if (secs < 0.6f) { f.delete(); return null }
        val n = 64
        val raw = amps.toList()
        val bytes = ByteArray(n) { i ->
            val from = i * raw.size / n
            val to = maxOf(from + 1, (i + 1) * raw.size / n)
            val peak = (from until minOf(to, raw.size)).maxOfOrNull { raw[it] } ?: 0
            (sqrt(peak.coerceAtMost(32767) / 32767f) * 255f).toInt().toByte()
        }
        return Voice(f, secs, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (e: Exception) { }
        r.release()
        file?.delete()
    }
}
