package com.example.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.webrtc.*

/**
 * WebRTC Listener interface providing callbacks for connection lifecycle,
 * ICE gathering, and remote audio/video tracks.
 */
interface WebRTCListener {
    fun onIceCandidate(candidate: IceCandidate)
    fun onIceConnectionChange(state: PeerConnection.IceConnectionState)
    fun onRemoteTrack(videoTrack: VideoTrack?, audioTrack: AudioTrack?)
    fun onConnectionClosed()
    fun onError(error: String)
}

/**
 * Comprehensive WebRTC Manager for peer-to-peer audio and video communication.
 * Handles:
 * - Hardware accelerated VideoEncoder/Decoder initialization via EGL context
 * - Audio processing with echo cancellation & noise suppression
 * - Configurable STUN/TURN ICE Servers
 * - Local audio/video capture & SurfaceViewRenderer binding
 * - Offer/Answer generation & SDP / ICE Candidate exchange
 */
class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val STREAM_ID = "ARDAMS"
    }

    private var rootEglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    var isNativeInitialized: Boolean = false
        private set

    // Media Sources, Streams, and Tracks
    var localMediaStream: MediaStream? = null
        private set
    var remoteMediaStream: MediaStream? = null
        private set
    var localVideoTrack: VideoTrack? = null
        private set
    var isFrontCamera: Boolean = true
        private set
    var currentCameraDeviceName: String? = null
        private set
    /**
     * Defense-in-Depth Architecture:
     * Layer 1 Guard resides in VideoCallViewModel / AyerViewModel using an AtomicBoolean compareAndSet
     * to reject rapid multi-clicks from UI buttons and prevent duplicate dispatches.
     *
     * Layer 2 Guard is this [isSwitchingCamera] AtomicBoolean inside [WebRTCManager].
     * Note: These two layers are deliberately NOT redundant:
     * - Layer 1 guards the UI state, buttons, and user interaction flow.
     * - Layer 2 guards the native media engine and hardware capturer directly.
     * If any background coroutine, WebRTC listener, or secondary caller invokes switchCamera()
     * directly without going through the ViewModel, Layer 2 guarantees that CameraVideoCapturer,
     * stopCapture(), dispose(), and OpenGL surfaces are NEVER subjected to concurrent re-entrant
     * calls or double-dispose native crashes.
     */
    private val isSwitchingCamera = AtomicBoolean(false)
    var isTorchOn: Boolean = false
        private set

    @Volatile
    var mockSwitchDelayMs: Long = 0L

    fun isSwitching(): Boolean = isSwitchingCamera.get()

    private var currentCaptureWidth: Int = 720
    private var currentCaptureHeight: Int = 1280
    private var currentCaptureFps: Int = 30
    var localAudioTrack: AudioTrack? = null
        private set
    var remoteVideoTrack: VideoTrack? = null
        private set
    var remoteAudioTrack: AudioTrack? = null
        private set

    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // PeerConnection instance
    var peerConnection: PeerConnection? = null
        private set

    // Configurable ICE Servers
    private var iceServers: List<PeerConnection.IceServer> = getDefaultIceServers()

    init {
        try {
            rootEglBase = EglBase.create()
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val factoryOptions = PeerConnectionFactory.Options()
            val eglCtx = rootEglBase?.eglBaseContext
            val encoderFactory = eglCtx?.let {
                DefaultVideoEncoderFactory(
                    it,
                    true, /* enableIntelVp8Encoder */
                    true  /* enableH264HighProfile */
                )
            }
            val decoderFactory = eglCtx?.let {
                DefaultVideoDecoderFactory(it)
            }

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(factoryOptions)
                .apply {
                    if (encoderFactory != null) setVideoEncoderFactory(encoderFactory)
                    if (decoderFactory != null) setVideoDecoderFactory(decoderFactory)
                }
                .createPeerConnectionFactory()
            isNativeInitialized = true
        } catch (e: Throwable) {
            Log.w(TAG, "Native WebRTC library not loaded (e.g. JVM host test): ${e.message}")
            isNativeInitialized = false
        }
    }

    /**
     * Returns default public Google STUN servers for NAT traversal.
     */
    fun getDefaultIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
    }

    /**
     * Configure custom ICE servers (such as self-hosted TURN / Coturn / Twilio credentials).
     */
    fun setCustomIceServers(servers: List<PeerConnection.IceServer>) {
        this.iceServers = servers
    }

    /**
     * Initializes local media (Camera video capturer and Microphone audio track).
     */
    fun initLocalMedia(
        enableVideo: Boolean = true,
        enableAudio: Boolean = true,
        isFrontFacing: Boolean = true,
        captureWidth: Int = 1280,
        captureHeight: Int = 720,
        captureFps: Int = 30
    ) {
        val factory = peerConnectionFactory ?: run {
            Log.w(TAG, "PeerConnectionFactory not initialized; skipping local media setup")
            return
        }

        currentCaptureWidth = captureWidth
        currentCaptureHeight = captureHeight
        currentCaptureFps = captureFps
        isFrontCamera = isFrontFacing

        // Initialize Audio Track
        if (enableAudio && localAudioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            audioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
            localAudioTrack?.setEnabled(true)
        }

        // Initialize Video Track
        if (enableVideo && localVideoTrack == null) {
            val eglCtx = rootEglBase?.eglBaseContext
            if (eglCtx != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("WebRTC_SurfaceTexture", eglCtx)
            }
            videoSource = factory.createVideoSource(false)

            videoCapturer = createCameraCapturer(context, isFrontFacing)
            videoCapturer?.let { capturer ->
                capturer.initialize(surfaceTextureHelper, context.applicationContext, videoSource?.capturerObserver)
                try {
                    capturer.startCapture(captureWidth, captureHeight, captureFps)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera capture: ${e.message}")
                }
            }

            localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            localVideoTrack?.setEnabled(true)
        }

        // Initialize and bundle into local MediaStream container
        if (localMediaStream == null) {
            localMediaStream = factory.createLocalMediaStream(STREAM_ID)
        }
        localAudioTrack?.let { aTrack ->
            if (localMediaStream?.audioTracks?.contains(aTrack) != true) {
                localMediaStream?.addTrack(aTrack)
            }
        }
        localVideoTrack?.let { vTrack ->
            if (localMediaStream?.videoTracks?.contains(vTrack) != true) {
                localMediaStream?.addTrack(vTrack)
            }
        }
    }

    /**
     * Convenience function to initialize local media stream for CallScreen.
     */
    fun initLocalStream(
        enableVideo: Boolean = true,
        enableAudio: Boolean = true,
        isFrontFacing: Boolean = true
    ): MediaStream? {
        initLocalMedia(enableVideo = enableVideo, enableAudio = enableAudio, isFrontFacing = isFrontFacing)
        return localMediaStream
    }

    /**
     * Creates and initializes a PeerConnection with configured ICE servers.
     */
    fun createPeerConnection(listener: WebRTCListener): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "SignalingState changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState changed: $state")
                state?.let { listener.onIceConnectionChange(it) }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "IceConnection receiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "IceGatheringState: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "New ICE candidate: ${it.sdp}")
                    listener.onIceCandidate(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream added: ${stream?.id}")
                remoteMediaStream = stream
                val vTrack = stream?.videoTracks?.firstOrNull()
                val aTrack = stream?.audioTracks?.firstOrNull()
                if (vTrack != null) remoteVideoTrack = vTrack
                if (aTrack != null) remoteAudioTrack = aTrack
                listener.onRemoteTrack(remoteVideoTrack, remoteAudioTrack)
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream removed: ${stream?.id}")
                if (remoteMediaStream == stream) {
                    remoteMediaStream = null
                }
            }

            override fun onDataChannel(dataChannel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val stream = mediaStreams?.firstOrNull()
                if (stream != null) {
                    remoteMediaStream = stream
                }
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    listener.onRemoteTrack(track, remoteAudioTrack)
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                    listener.onRemoteTrack(remoteVideoTrack, track)
                }
            }
        }

        val factory = peerConnectionFactory ?: run {
            Log.w(TAG, "PeerConnectionFactory not initialized; cannot create PeerConnection")
            return null
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)

        // Attach local tracks to the PeerConnection
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf(STREAM_ID))
        }
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf(STREAM_ID))
        }

        return peerConnection
    }

    /**
     * Generates an SDP Offer.
     */
    fun createOffer(
        mediaConstraints: MediaConstraints = defaultMediaConstraints(),
        onSuccess: (SessionDescription) -> Unit,
        onFailure: (String) -> Unit
    ) {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    onSuccess(desc)
                } else {
                    onFailure("Created SessionDescription was null")
                }
            }

            override fun onCreateFailure(error: String?) {
                onFailure(error ?: "Unknown error creating offer")
            }
        }, mediaConstraints)
    }

    /**
     * Generates an SDP Answer in response to a remote Offer.
     */
    fun createAnswer(
        mediaConstraints: MediaConstraints = defaultMediaConstraints(),
        onSuccess: (SessionDescription) -> Unit,
        onFailure: (String) -> Unit
    ) {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    onSuccess(desc)
                } else {
                    onFailure("Created SessionDescription was null")
                }
            }

            override fun onCreateFailure(error: String?) {
                onFailure(error ?: "Unknown error creating answer")
            }
        }, mediaConstraints)
    }

    /**
     * Sets Local Description.
     */
    fun setLocalDescription(
        desc: SessionDescription,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                onSuccess()
            }

            override fun onSetFailure(error: String?) {
                onFailure(error ?: "Failed to set local description")
            }
        }, desc)
    }

    /**
     * Sets Remote Description received from signaling channel.
     */
    fun setRemoteDescription(
        desc: SessionDescription,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                onSuccess()
            }

            override fun onSetFailure(error: String?) {
                onFailure(error ?: "Failed to set remote description")
            }
        }, desc)
    }

    /**
     * Adds received remote ICE Candidate to the active PeerConnection.
     */
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Initializes a SurfaceViewRenderer with the hardware EGL context.
     */
    fun initSurfaceView(renderer: SurfaceViewRenderer) {
        val eglContext = rootEglBase?.eglBaseContext ?: return
        renderer.init(eglContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)
    }

    /**
     * Binds local video track to SurfaceViewRenderer for camera preview.
     */
    fun startLocalPreview(renderer: SurfaceViewRenderer) {
        renderer.setMirror(true)
        localVideoTrack?.addSink(renderer)
    }

    /**
     * Binds remote video track to SurfaceViewRenderer.
     */
    fun attachRemoteVideo(renderer: SurfaceViewRenderer, track: VideoTrack? = remoteVideoTrack) {
        renderer.setMirror(false)
        track?.addSink(renderer)
    }

    /**
     * Toggles hardware torch/flashlight mode.
     * Note: Torch is only supported on back-facing cameras with physical flash hardware.
     * If called while on the front camera, torch is forced off.
     */
    fun setTorch(enabled: Boolean): Boolean {
        if (isFrontCamera && enabled) {
            Log.w(TAG, "Cannot turn on torch while front camera is active")
            isTorchOn = false
            return false
        }
        if (!enabled) {
            isTorchOn = false
        }
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash
                }
                if (backCameraId != null) {
                    cameraManager.setTorchMode(backCameraId, enabled)
                    isTorchOn = enabled
                    true
                } else {
                    isTorchOn = false
                    false
                }
            } else {
                isTorchOn = false
                false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware torch operation skipped or failed: ${e.message}")
            isTorchOn = false
            false
        }
    }

    /**
     * Switches between front and rear cameras during an active call session.
     *
     * Architectural Design & Concurrency Considerations:
     * 1. Re-entrancy & Race-Condition Prevention:
     *    Guarded by [AtomicBoolean] `isSwitchingCamera`. Any concurrent invocation (such as
     *    rapid multi-clicks or simultaneous touches from both the PiP overlay and the bottom control
     *    dock) is immediately rejected with `onResult(false)` to prevent native camera thread deadlocks,
     *    double-dispose crashes, or frozen video frames.
     *
     * 2. Primary WebRTC SDK Native Implementation:
     *    Leverages the official WebRTC SDK [CameraVideoCapturer.switchCamera] method with a
     *    [CameraVideoCapturer.CameraSwitchHandler]. This delegates switching natively on WebRTC's
     *    internal camera worker thread without tearing down OpenGL ES surfaces, disposing the capturer,
     *    or recreating the VideoSource / VideoTrack / PeerConnection sender.
     *
     * 3. Fallback Mechanism:
     *    If the active capturer does not implement [CameraVideoCapturer] or in mock/unit-test environments,
     *    a controlled capturer cycle is provided with graceful failure handling.
     *
     * 4. Torch/Flash Synchronization:
     *    Front-facing cameras do not possess physical flashlight LEDs. When switching to the front
     *    camera, any active torch is extinguished immediately to keep software and hardware state in sync.
     */
    fun switchCamera(onResult: ((Boolean) -> Unit)? = null): Boolean {
        // 1. Layer 2 Guard (Defense-in-depth): Reject concurrent re-entrant invocations at media engine level
        if (!isSwitchingCamera.compareAndSet(false, true)) {
            Log.w(TAG, "switchCamera already in progress (WebRTCManager Layer 2 guard); rejecting concurrent invocation")
            onResult?.invoke(false)
            return false
        }

        // 2. Extinguish torch immediately when leaving rear camera
        if (isTorchOn) {
            setTorch(false)
        }

        // Mock / Host unit test environment fallback (when native WebRTC is not initialized)
        if (!isNativeInitialized || peerConnectionFactory == null || videoSource == null) {
            return try {
                if (mockSwitchDelayMs > 0) {
                    Thread.sleep(mockSwitchDelayMs)
                }
                isFrontCamera = !isFrontCamera
                if (isFrontCamera) {
                    setTorch(false)
                }
                Log.w(TAG, "WebRTC native video source not active; toggled camera state to front=$isFrontCamera")
                onResult?.invoke(true)
                true
            } finally {
                isSwitchingCamera.set(false)
            }
        }

        val cameraCapturer = videoCapturer as? CameraVideoCapturer
        if (cameraCapturer != null) {
            // Primary Canonical Path: WebRTC SDK built-in switchCamera on dedicated camera thread
            return try {
                cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFront: Boolean) {
                        Log.i(TAG, "Native WebRTC CameraVideoCapturer.switchCamera completed: isFrontCamera=$isFront")
                        isFrontCamera = isFront
                        if (isFront) {
                            setTorch(false)
                        }
                        isSwitchingCamera.set(false)
                        onResult?.invoke(true)
                    }

                    override fun onCameraSwitchError(errorDescription: String?) {
                        Log.e(TAG, "Native WebRTC CameraVideoCapturer.switchCamera error: $errorDescription")
                        isSwitchingCamera.set(false)
                        onResult?.invoke(false)
                    }
                })
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Exception during native switchCamera invocation: ${e.message}", e)
                isSwitchingCamera.set(false)
                onResult?.invoke(false)
                false
            }
        }

        // Fallback Path: Only when videoCapturer is not a CameraVideoCapturer instance
        return try {
            val enumerator = getCameraEnumerator(context)
            val deviceNames = enumerator.deviceNames
            if (deviceNames.isNullOrEmpty()) {
                Log.w(TAG, "No camera devices found to cycle through")
                isSwitchingCamera.set(false)
                onResult?.invoke(false)
                return false
            }

            // Find current camera device index or derive from isFrontCamera
            val currentIndex = if (currentCameraDeviceName != null) {
                val idx = deviceNames.indexOf(currentCameraDeviceName)
                if (idx >= 0) idx else 0
            } else {
                val found = deviceNames.indexOfFirst {
                    if (isFrontCamera) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
                }
                if (found >= 0) found else 0
            }

            // Cycle to the next available CameraVideoCapturer instance device
            val nextIndex = (currentIndex + 1) % deviceNames.size
            val targetDeviceName = deviceNames[nextIndex]
            val isTargetFront = enumerator.isFrontFacing(targetDeviceName)

            Log.d(TAG, "Fallback cycling camera capturer from $currentCameraDeviceName (idx=$currentIndex) to $targetDeviceName (idx=$nextIndex, front=$isTargetFront)")

            // Stop and release current capturer before starting the selected camera
            val oldCapturer = videoCapturer
            try {
                oldCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w(TAG, "Notice: error stopping previous camera capturer: ${e.message}")
            }
            try {
                oldCapturer?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Notice: error disposing previous camera capturer: ${e.message}")
            }
            videoCapturer = null

            // Create the new CameraVideoCapturer instance
            val newCapturer = createCapturerForDevice(enumerator, targetDeviceName)
            if (newCapturer == null) {
                Log.e(TAG, "Failed to instantiate new CameraVideoCapturer for $targetDeviceName")
                isSwitchingCamera.set(false)
                onResult?.invoke(false)
                return false
            }

            // Ensure surface texture helper is ready
            if (surfaceTextureHelper == null) {
                val eglCtx = rootEglBase?.eglBaseContext
                if (eglCtx != null) {
                    surfaceTextureHelper = SurfaceTextureHelper.create("WebRTC_SurfaceTexture", eglCtx)
                }
            }

            // Restart local video capturer into existing videoSource.capturerObserver
            // Existing VideoTrack, VideoSource, and PeerConnection remain intact!
            newCapturer.initialize(
                surfaceTextureHelper,
                context.applicationContext,
                videoSource?.capturerObserver
            )
            newCapturer.startCapture(currentCaptureWidth, currentCaptureHeight, currentCaptureFps)

            videoCapturer = newCapturer
            currentCameraDeviceName = targetDeviceName
            isFrontCamera = isTargetFront
            if (isFrontCamera) {
                setTorch(false)
            }

            Log.i(TAG, "Fallback camera switch completed: active=$targetDeviceName (front=$isFrontCamera)")
            isSwitchingCamera.set(false)
            onResult?.invoke(true)
            true
        } catch (e: Throwable) {
            // Handle camera-switch failures gracefully without interrupting ongoing call
            Log.e(TAG, "Fallback camera switch encountered an error: ${e.message}", e)
            isSwitchingCamera.set(false)
            onResult?.invoke(false)
            false
        }
    }

    /**
     * Mute or unmute local microphone audio.
     */
    fun setAudioMuted(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    /**
     * Enable or disable local camera video.
     */
    fun setVideoEnabled(isEnabled: Boolean) {
        localVideoTrack?.setEnabled(isEnabled)
    }

    /**
     * Resets active call session peer connection while preserving factory.
     */
    /**
     * Disposes of the active WebRTC PeerConnection, local tracks (audio/video),
     * camera capturer, surface texture helper, and all audio/video resources.
     */
    fun disposeCallResources() {
        Log.d(TAG, "Disposing WebRTC call resources: PeerConnection, local tracks, camera, audio/video sources")
        disconnect()
    }

    fun disconnect() {
        try {
            setTorch(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting torch during disconnect: ${e.message}")
        }
        isSwitchingCamera.set(false)

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video capturer: ${e.message}")
        }
        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing video capturer: ${e.message}")
        }
        videoCapturer = null
        currentCameraDeviceName = null
        isFrontCamera = true

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localMediaStream?.dispose()
        localMediaStream = null
        remoteMediaStream = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        videoSource?.dispose()
        videoSource = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    /**
     * Completely release all hardware and native WebRTC resources.
     */
    fun release() {
        disconnect()
        try {
            peerConnectionFactory?.dispose()
        } catch (e: Throwable) {
            Log.e(TAG, "Error disposing peerConnectionFactory: ${e.message}")
        }
        peerConnectionFactory = null

        try {
            rootEglBase?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing rootEglBase: ${e.message}")
        }
        rootEglBase = null
    }

    fun getFactory(): PeerConnectionFactory? = peerConnectionFactory
    fun getEglBaseContext(): EglBase.Context? = rootEglBase?.eglBaseContext

    private fun getCameraEnumerator(context: Context): CameraEnumerator {
        return if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }
    }

    private fun findCameraDevice(enumerator: CameraEnumerator, isFront: Boolean): String? {
        val names = enumerator.deviceNames ?: return null
        for (name in names) {
            if (isFront && enumerator.isFrontFacing(name)) return name
            if (!isFront && enumerator.isBackFacing(name)) return name
        }
        return names.firstOrNull()
    }

    private fun createCapturerForDevice(enumerator: CameraEnumerator, deviceName: String): CameraVideoCapturer? {
        return try {
            enumerator.createCapturer(deviceName, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(errorDescription: String?) {
                    Log.e(TAG, "Camera device $deviceName error: $errorDescription")
                }

                override fun onCameraDisconnected() {
                    Log.w(TAG, "Camera device $deviceName disconnected")
                }

                override fun onCameraFreezed(errorDescription: String?) {
                    Log.w(TAG, "Camera device $deviceName freezed: $errorDescription")
                }

                override fun onCameraOpening(cameraName: String?) {
                    Log.d(TAG, "Camera device opening: $cameraName")
                }

                override fun onFirstFrameAvailable() {
                    Log.d(TAG, "First frame available from camera device: $deviceName")
                }

                override fun onCameraClosed() {
                    Log.d(TAG, "Camera device closed: $deviceName")
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create capturer for device $deviceName: ${e.message}")
            null
        }
    }

    private fun createCameraCapturer(context: Context, isFront: Boolean): CameraVideoCapturer? {
        val enumerator = getCameraEnumerator(context)
        val device = findCameraDevice(enumerator, isFront) ?: return null
        currentCameraDeviceName = device
        return createCapturerForDevice(enumerator, device)
    }

    private fun defaultMediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}

