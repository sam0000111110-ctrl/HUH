package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import kotlin.math.max

data class RecordedVoiceClip(
    val filePath: String,
    val durationSeconds: Int,
    val base64Audio: String?
)

/**
 * Hardware-backed AAC voice note recorder & player for 1-on-1 private chat.
 */
class VoiceAudioHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var activeOutputFile: File? = null
    private var recordingStartMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activePlaybackId: String? = null

    @Synchronized
    fun startRecording(): Result<Unit> {
        stopRecordingInternal(discardFile = true)
        return try {
            val outFile = File(context.cacheDir, "huh_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setMaxDuration(60_000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            activeOutputFile = outFile
            recordingStartMs = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AAC voice recording: ${e.message}", e)
            stopRecordingInternal(discardFile = true)
            Result.failure(e)
        }
    }

    @Synchronized
    fun stopRecordingAndSave(): Result<RecordedVoiceClip> {
        val currentRecorder = recorder ?: return Result.failure(IllegalStateException("لا يوجد تسجيل نشط حالياً."))
        val file = activeOutputFile
        val elapsedSec = max(1, ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt())
        return try {
            currentRecorder.stop()
            currentRecorder.release()
            recorder = null
            activeOutputFile = null
            if (file == null || !file.exists() || file.length() == 0L) {
                return Result.failure(IllegalStateException("لم يتم التقاط مقطع صوتي صالح."))
            }
            // Limit Firestore inline Base64 payload to ~180 KB (~60s at 24kbps AAC)
            val base64 = if (file.length() <= 190_000L) {
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            } else {
                null
            }
            Result.success(
                RecordedVoiceClip(
                    filePath = file.absolutePath,
                    durationSeconds = elapsedSec,
                    base64Audio = base64
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AAC voice recording: ${e.message}", e)
            runCatching { currentRecorder.release() }
            recorder = null
            runCatching { file?.delete() }
            activeOutputFile = null
            Result.failure(e)
        }
    }

    @Synchronized
    fun cancelRecording() {
        stopRecordingInternal(discardFile = true)
    }

    @Synchronized
    fun togglePlayback(
        messageId: String,
        filePath: String?,
        base64Audio: String?,
        onPlaybackStarted: () -> Unit,
        onPlaybackStopped: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activePlaybackId == messageId && player != null) {
            stopPlayback()
            onPlaybackStopped()
            return
        }
        stopPlayback()
        try {
            val playableFile: File? = when {
                !filePath.isNullOrBlank() && File(filePath).exists() -> File(filePath)
                !base64Audio.isNullOrBlank() -> {
                    val temp = File(context.cacheDir, "play_${messageId.hashCode()}.m4a")
                    if (!temp.exists()) {
                        temp.writeBytes(Base64.decode(base64Audio, Base64.DEFAULT))
                    }
                    temp
                }
                else -> null
            }

            if (playableFile == null || !playableFile.exists()) {
                onError("تعذر العثور على ملف الصوت للتشغيل.")
                return
            }

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(playableFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onPlaybackStopped()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onPlaybackStopped()
                    true
                }
                prepare()
                start()
            }
            player = mediaPlayer
            activePlaybackId = messageId
            onPlaybackStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice note: ${e.message}", e)
            stopPlayback()
            onError("تعذر تشغيل الرسالة الصوتية.")
        }
    }

    @Synchronized
    fun stopPlayback() {
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
        }
        player = null
        activePlaybackId = null
    }

    @Synchronized
    fun release() {
        stopRecordingInternal(discardFile = true)
        stopPlayback()
    }

    private fun stopRecordingInternal(discardFile: Boolean) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        if (discardFile) {
            runCatching { activeOutputFile?.delete() }
        }
        activeOutputFile = null
    }

    companion object {
        private const val TAG = "VoiceAudioHelper"
    }
}
