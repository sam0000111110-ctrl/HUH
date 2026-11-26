package com.example.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Lifecycle-safe wrapper around Android's on-device/platform SpeechRecognizer
 * that captures continuous spoken utterances during an active translated call
 * and feeds them to Gemini AI translation for live subtitles.
 */
class CallSubtitleSpeechHelper(
    private val context: Context,
    private val onUtteranceRecognized: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false
    private var lastPartialRestartMs = 0L

    fun start(languageTag: String = Locale.getDefault().toLanguageTag()) {
        mainHandler.post {
            if (isActive) return@post
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic || !SpeechRecognizer.isRecognitionAvailable(context)) {
                return@post
            }
            isActive = true
            startInternal(languageTag)
        }
    }

    fun stop() {
        mainHandler.post {
            isActive = false
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun startInternal(languageTag: String) {
        if (!isActive) return
        runCatching { speechRecognizer?.destroy() }
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (recognizer == null) {
            isActive = false
            return
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val first = matches?.firstOrNull()?.trim().orEmpty()
                val now = System.currentTimeMillis()
                if (first.length >= 12 && now - lastPartialRestartMs > 2500L) {
                    lastPartialRestartMs = now
                    onUtteranceRecognized(first)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    onUtteranceRecognized(text)
                }
                scheduleRestart(languageTag, 350L)
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                    error == SpeechRecognizer.ERROR_AUDIO
                ) {
                    Log.w(TAG, "Stopping speech recognizer due to permission/audio error: $error")
                    stop()
                    return
                }
                val delayMs = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 900L
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 500L
                    else -> 1200L
                }
                scheduleRestart(languageTag, delayMs)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer.startListening(intent) }.onFailure {
            Log.w(TAG, "Failed to start listening: ${it.message}")
            isActive = false
        }
    }

    private fun scheduleRestart(languageTag: String, delayMs: Long) {
        if (!isActive) return
        mainHandler.postDelayed({
            if (isActive) startInternal(languageTag)
        }, delayMs)
    }

    companion object {
        private const val TAG = "CallSubtitleSpeech"
    }
}
