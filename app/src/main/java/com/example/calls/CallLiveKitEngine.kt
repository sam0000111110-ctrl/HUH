package com.example.calls

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time LiveKit SFU Media Engine for 1-on-1 audio & video calls.
 * Supports Adaptive Simulcast, Dynacast, automatic reconnect on weak networks,
 * audio-priority fallback, camera switching, and automatic teardown on peer exit.
 */
class CallLiveKitEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "CallLiveKitEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var eventJob: Job? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun initRenderer(renderer: TextureViewRenderer) {
        try {
            room?.initVideoRenderer(renderer)
        } catch (t: Throwable) {
            Log.w(TAG, "initRenderer skipped: ${t.message}")
        }
    }

    suspend fun connect(
        livekitUrl: String,
        token: String,
        enableVideo: Boolean,
        onReconnecting: () -> Unit,
        onReconnected: () -> Unit,
        onRemoteParticipantLeft: () -> Unit,
        onConnectionQualityWeak: (Boolean) -> Unit,
        onDisconnected: (String?) -> Unit
    ): Boolean {
        if (livekitUrl.isBlank() || token.isBlank()) {
            return false
        }

        disconnect()

        return try {
            val createdRoom = LiveKit.create(
                appContext = appContext.applicationContext,
                options = RoomOptions(
                    adaptiveStream = true,
                    dynacast = true
                )
            )
            room = createdRoom

            eventJob = scope.launch {
                createdRoom.events.events.collect { event ->
                    when (event) {
                        is RoomEvent.Reconnecting -> onReconnecting()
                        is RoomEvent.Reconnected -> onReconnected()
                        is RoomEvent.Disconnected -> {
                            _isConnected.value = false
                            _remoteVideoTrack.value = null
                            onDisconnected(event.error?.message)
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            // Strictly 1-on-1: close when remote peer leaves so empty room never stays open
                            onRemoteParticipantLeft()
                        }
                        is RoomEvent.TrackSubscribed -> {
                            val track = event.track
                            if (track is VideoTrack && event.participant != createdRoom.localParticipant) {
                                _remoteVideoTrack.value = track
                            }
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track == _remoteVideoTrack.value) {
                                _remoteVideoTrack.value = null
                            }
                        }
                        is RoomEvent.ConnectionQualityChanged -> {
                            val weak = event.quality == ConnectionQuality.POOR || event.quality == ConnectionQuality.LOST
                            onConnectionQualityWeak(weak)
                        }
                        else -> Unit
                    }
                }
            }

            createdRoom.connect(url = livekitUrl, token = token)
            _isConnected.value = true

            createdRoom.localParticipant.setMicrophoneEnabled(true)
            createdRoom.localParticipant.setCameraEnabled(enableVideo)

            val localCamPub = createdRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)
            _localVideoTrack.value = localCamPub?.track as? VideoTrack
            true
        } catch (t: Throwable) {
            Log.e(TAG, "LiveKit connect failed: ${t.message}", t)
            disconnect()
            onDisconnected(t.message)
            false
        }
    }

    suspend fun setMicrophoneMuted(muted: Boolean) {
        try {
            room?.localParticipant?.setMicrophoneEnabled(!muted)
        } catch (t: Throwable) {
            Log.w(TAG, "setMicrophoneMuted failed: ${t.message}")
        }
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        try {
            room?.localParticipant?.setCameraEnabled(enabled)
            val pub = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)
            _localVideoTrack.value = if (enabled) pub?.track as? VideoTrack else null
        } catch (t: Throwable) {
            Log.w(TAG, "setCameraEnabled failed: ${t.message}")
        }
    }

    fun switchCamera(toFront: Boolean): Boolean {
        return try {
            val localTrack = _localVideoTrack.value as? LocalVideoTrack ?: return false
            localTrack.switchCamera(
                position = if (toFront) CameraPosition.FRONT else CameraPosition.BACK
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "switchCamera failed: ${t.message}")
            false
        }
    }

    fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _isConnected.value = false
        try {
            room?.disconnect()
            room?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "LiveKit disconnect warning: ${t.message}")
        } finally {
            room = null
        }
    }
}
