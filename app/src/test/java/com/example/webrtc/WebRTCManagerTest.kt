package com.example.webrtc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WebRTCManagerTest {

    private lateinit var context: Context
    private lateinit var webRTCManager: WebRTCManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        webRTCManager = WebRTCManager(context)
    }

    @Test
    fun testPeerConnectionFactoryInitialization() {
        assertNotNull("WebRTCManager instance must be created", webRTCManager)
        
        val defaultIceServers = webRTCManager.getDefaultIceServers()
        assertTrue("Must provide default Google STUN servers", defaultIceServers.isNotEmpty())
        assertTrue("First server should be STUN", defaultIceServers.first().urls.any { it.contains("stun") })

        // Test configuring custom ICE servers
        val customServer = PeerConnection.IceServer.builder("turn:turn.example.com:3478")
            .setUsername("user")
            .setPassword("pass")
            .createIceServer()
        webRTCManager.setCustomIceServers(listOf(customServer))
        
        if (webRTCManager.isNativeInitialized) {
            assertNotNull("PeerConnectionFactory must be encapsulated and initialized", webRTCManager.getFactory())
            assertNotNull("EglBase.Context must be initialized for hardware rendering", webRTCManager.getEglBaseContext())
        }
    }

    @Test
    fun testLocalStreamInitialization() {
        if (webRTCManager.isNativeInitialized) {
            // Test local stream initialization with audio enabled
            webRTCManager.initLocalMedia(enableVideo = false, enableAudio = true)

            assertNotNull("Local audio track must be initialized", webRTCManager.localAudioTrack)
            assertNotNull("Local MediaStream must be initialized", webRTCManager.localMediaStream)
            assertTrue("Local MediaStream must contain the local audio track", 
                webRTCManager.localMediaStream?.audioTracks?.contains(webRTCManager.localAudioTrack) == true)

            // Test audio mute/unmute
            webRTCManager.setAudioMuted(true)
            assertFalse("Local audio track should be disabled when muted", webRTCManager.localAudioTrack?.enabled() ?: true)

            webRTCManager.setAudioMuted(false)
            assertTrue("Local audio track should be enabled when unmuted", webRTCManager.localAudioTrack?.enabled() ?: false)
        } else {
            // In JVM host environments without native .so, it must degrade gracefully
            webRTCManager.initLocalMedia(enableVideo = false, enableAudio = true)
            webRTCManager.setAudioMuted(true)
            webRTCManager.setVideoEnabled(false)
            // No exception thrown
        }
    }

    @Test
    fun testRemoteStreamHandlingAndListener() {
        var remoteTrackReceived = false
        var iceCandidateReceived = false

        val listener = object : WebRTCListener {
            override fun onIceCandidate(candidate: IceCandidate) {
                iceCandidateReceived = true
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}

            override fun onRemoteTrack(videoTrack: org.webrtc.VideoTrack?, audioTrack: org.webrtc.AudioTrack?) {
                remoteTrackReceived = true
            }

            override fun onConnectionClosed() {}

            override fun onError(error: String) {}
        }

        if (webRTCManager.isNativeInitialized) {
            val pc = webRTCManager.createPeerConnection(listener)
            assertNotNull("PeerConnection should be created", pc)
            assertEquals("PeerConnection property should point to created instance", pc, webRTCManager.peerConnection)
        } else {
            val pc = webRTCManager.createPeerConnection(listener)
            assertNull("PeerConnection should be null when native library is absent in JVM test", pc)
        }
    }

    @Test
    fun testDisposeCallResources() {
        webRTCManager.initLocalMedia(enableVideo = false, enableAudio = true)

        // Cleanup
        webRTCManager.disposeCallResources()

        assertNull("Local audio track must be cleared", webRTCManager.localAudioTrack)
        assertNull("Local video track must be cleared", webRTCManager.localVideoTrack)
        assertNull("Local media stream must be cleared", webRTCManager.localMediaStream)
        assertNull("Remote media stream must be cleared", webRTCManager.remoteMediaStream)
        assertNull("PeerConnection must be disposed and nullified", webRTCManager.peerConnection)
    }

    @Test
    fun testSwitchCamera() {
        // Test camera flip toggle in host JVM
        val initialIsFront = webRTCManager.isFrontCamera
        var switchResult: Boolean? = null
        val returned = webRTCManager.switchCamera { success ->
            switchResult = success
        }

        assertTrue("switchCamera should execute gracefully and return boolean", returned)
        assertTrue("Callback should be notified", switchResult == true)
        assertNotEquals("Camera facing state should toggle", initialIsFront, webRTCManager.isFrontCamera)

        // Switch back
        webRTCManager.switchCamera()
        assertEquals("Camera facing state should return to initial after second switch", initialIsFront, webRTCManager.isFrontCamera)
    }

    @Test
    fun testConcurrencyGuardRejectsConcurrentCameraSwitch() {
        // Verify that Layer 2 AtomicBoolean guard inside WebRTCManager strictly prevents concurrent execution
        assertFalse("Initially isSwitching should be false", webRTCManager.isSwitching())
        val initialFront = webRTCManager.isFrontCamera

        // Simulate a camera hardware transition delay of 100ms to guarantee concurrency overlap
        webRTCManager.mockSwitchDelayMs = 100L

        val latchStart = java.util.concurrent.CountDownLatch(1)
        val latchDone = java.util.concurrent.CountDownLatch(2)
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())

        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            repeat(2) {
                executor.submit {
                    latchStart.await()
                    val res = webRTCManager.switchCamera()
                    results.add(res)
                    latchDone.countDown()
                }
            }

            // Release both threads simultaneously
            latchStart.countDown()
            val completed = latchDone.await(3, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("Both concurrent threads must finish within timeout", completed)

            assertEquals("Both calls must record a result", 2, results.size)
            // Exactly one call must succeed, and the concurrent call must be rejected
            val successCount = results.count { it }
            val rejectedCount = results.count { !it }
            assertEquals("Exactly one concurrent switchCamera call must succeed", 1, successCount)
            assertEquals("Exactly one concurrent switchCamera call must be rejected by AtomicBoolean guard", 1, rejectedCount)

            // Crucial: The camera switch executed exactly ONCE, toggling facing state once, NOT twice!
            assertNotEquals("Camera facing must have flipped exactly once", initialFront, webRTCManager.isFrontCamera)
            assertFalse("isSwitching must be cleanly reset to false after completion", webRTCManager.isSwitching())
        } finally {
            webRTCManager.mockSwitchDelayMs = 0L
            executor.shutdown()
        }
    }

    @Test
    fun testTorchSynchronizationOnCameraSwitch() {
        // Torch on front camera must be disallowed
        assertTrue("Initially front camera is active", webRTCManager.isFrontCamera)
        val torchOnFront = webRTCManager.setTorch(true)
        assertFalse("Torch should not be enabled on front camera", torchOnFront)
        assertFalse("isTorchOn should remain false", webRTCManager.isTorchOn)

        // Switch to rear camera
        webRTCManager.switchCamera()
        assertFalse("Should be on rear camera", webRTCManager.isFrontCamera)

        // Switch back to front camera - torch must be forced off
        webRTCManager.switchCamera()
        assertTrue("Should be on front camera", webRTCManager.isFrontCamera)
        assertFalse("Torch must be extinguished when switching to front camera", webRTCManager.isTorchOn)
    }
}
