package com.mcpai.chat.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A finished voice note: absolute file path and the name shown in the bubble. */
data class VoiceRecording(
    val path: String,
    val name: String,
)

private const val SAMPLE_RATE = 16_000
private const val CHANNELS = 1

/**
 * Records a voice note as a real 16 kHz mono WAV file while reporting live
 * loudness (0..1) for the composer's level meter.
 *
 * The PCM loop runs on [Dispatchers.IO] inside [scope]; [onLevel] is called
 * from that thread about ten times a second. [finish]/[cancel] are safe from
 * any thread once [start] returned true.
 */
class VoiceRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLevel: (Float) -> Unit,
) {

    companion object {
        private const val SAMPLE_RATE = 16_000
    }

    private var record: AudioRecord? = null
    private var job: Job? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var active = false

    /** True between a successful [start] and [finish]/[cancel]. */
    val recording: Boolean get() = active

    /**
     * Starts capturing the microphone. Returns false when the recorder cannot
     * be initialized (no mic, blocked by another app) - nothing is left behind.
     */
    fun start(): Boolean {
        if (active) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE), // at least ~100 ms of headroom
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }
        pcm.reset()
        record = audioRecord
        active = true
        audioRecord.startRecording()
        job = scope.launch(Dispatchers.IO) { captureLoop(audioRecord) }
        return true
    }

    private suspend fun captureLoop(audioRecord: AudioRecord) {
        val chunk = ShortArray(SAMPLE_RATE / 10) // 100 ms of mono 16-bit
        val bytes = ByteArray(chunk.size * 2)
        while (active) {
            val read = audioRecord.read(chunk, 0, chunk.size)
            if (read <= 0) break
            // Little-endian PCM bytes for the WAV payload.
            for (i in 0 until read) {
                bytes[i * 2] = (chunk[i].toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((chunk[i].toInt() shr 8) and 0xFF).toByte()
            }
            pcm.write(bytes, 0, read * 2)

            var sum = 0.0
            for (i in 0 until read) {
                val v = chunk[i].toDouble() / 32767.0
                sum += v * v
            }
            val rms = sqrt(sum / read)
            // Speech sits around 0.02..0.3 RMS; stretch it into 0..1 so the
            // meter has visible travel, with a floor so idle never dies.
            onLevel((rms * 6.0).coerceIn(0.06, 1.0).toFloat())
        }
    }

    /**
     * Stops recording, writes the WAV into the app's files dir and returns it.
     * Returns null when nothing usable was captured.
     */
    suspend fun finish(): VoiceRecording? = withContext(Dispatchers.IO) {
        val bytes = stopCapture()
        if (bytes == null || bytes.size < SAMPLE_RATE / 2) {
            // Nothing meaningful (under half a second): treat as cancelled.
            null
        } else {
            val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "voice-$stamp.wav")
            writeWav(file, bytes)
            VoiceRecording(path = file.absolutePath, name = file.name)
        }
    }

    /** Stops and throws the captured audio away. */
    fun cancel() {
        stopCapture()
    }

    /** Shared stop path; returns the captured PCM bytes, or null if not running. */
    private fun stopCapture(): ByteArray? {
        if (!active) return null
        active = false
        val audio = record
        record = null
        audio?.runCatching { stop() }
        audio?.release()
        job?.cancel()
        job = null
        val bytes = if (audio == null) null else pcm.toByteArray()
        pcm.reset()
        onLevel(0f)
        return bytes
    }
}

/** Writes RIFF/WAVE headers around 16-bit little-endian PCM data. */
private fun writeWav(file: File, data: ByteArray) {
    FileOutputStream(file).use { out ->
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeLe(data.size + 36)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeLe(16) // fmt chunk size
        out.writeLe(1) // PCM
        out.writeLe(CHANNELS)
        out.writeLe(SAMPLE_RATE)
        out.writeLe(SAMPLE_RATE * CHANNELS * 2) // byte rate
        out.writeLe(CHANNELS * 2) // block align
        out.writeLe(16) // bits per sample
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeLe(data.size)
        out.write(data)
    }
}

private fun FileOutputStream.writeLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}
