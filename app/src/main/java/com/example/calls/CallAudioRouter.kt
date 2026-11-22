package com.example.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages Android AudioManager routing, audio focus, and earpiece vs speakerphone switching
 * during 1-on-1 audio/video calls.
 */
class CallAudioRouter(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isActive = false

    fun startCallAudio(speakerOn: Boolean) {
        val am = audioManager ?: return
        try {
            if (!isActive) {
                previousMode = am.mode
                @Suppress("DEPRECATION")
                previousSpeakerState = am.isSpeakerphoneOn
                isActive = true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            am.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(speakerOn)
        } catch (t: Throwable) {
            Log.w("CallAudioRouter", "Audio setup skipped/partial: ${t.message}")
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        val am = audioManager ?: return
        try {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enabled
        } catch (t: Throwable) {
            Log.w("CallAudioRouter", "Failed to toggle speakerphone: ${t.message}")
        }
    }

    fun stopCallAudio() {
        val am = audioManager ?: return
        if (!isActive) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = previousSpeakerState
            am.mode = previousMode
        } catch (t: Throwable) {
            Log.w("CallAudioRouter", "Audio teardown warning: ${t.message}")
        } finally {
            isActive = false
        }
    }
}
