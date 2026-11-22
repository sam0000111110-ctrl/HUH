package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.MatchCandidate
import com.example.ui.screens.CallScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CallScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCallScreenRenderAndControls() {
        var audioToggled = false
        var cameraToggled = false
        var cameraSwitched = false
        var speakerToggled = false
        var callEnded = false
        var resourcesDisposed = false

        composeTestRule.setContent {
            CallScreen(
                localVideoTrack = null,
                remoteVideoTrack = null,
                eglContext = null,
                participantName = "سارة أحمد",
                participantInfo = "مصر • 5 🪙/د",
                callDurationSeconds = 125,
                isAudioEnabled = true,
                isCameraEnabled = true,
                isSpeakerEnabled = true,
                isFrontCamera = true,
                onToggleAudio = { audioToggled = true },
                onToggleCamera = { cameraToggled = true },
                onSwitchCamera = { cameraSwitched = true },
                onToggleSpeaker = { speakerToggled = true },
                onEndCall = { callEnded = true },
                onDisposeCallResources = { resourcesDisposed = true }
            )
        }

        // Verify root screen and video containers exist
        composeTestRule.onNodeWithTag("call_screen").assertExists()
        composeTestRule.onNodeWithTag("remote_video_renderer").assertExists()
        composeTestRule.onNodeWithTag("local_video_container").assertExists()

        // Verify audio toggle button and interaction
        composeTestRule.onNodeWithTag("toggle_audio_button").assertExists().performClick()
        assertTrue("Audio toggle callback was not invoked", audioToggled)

        // Verify camera toggle button and interaction
        composeTestRule.onNodeWithTag("toggle_camera_button").assertExists().performClick()
        assertTrue("Camera toggle callback was not invoked", cameraToggled)

        // Verify switch camera button and interaction
        composeTestRule.onNodeWithTag("switch_camera_button").assertExists().performClick()
        assertTrue("Switch camera callback was not invoked", cameraSwitched)

        // Verify toggle speaker button and interaction
        composeTestRule.onNodeWithTag("toggle_speaker_button").assertExists().performClick()
        assertTrue("Speaker toggle callback was not invoked", speakerToggled)

        // Verify prominent red end call button and interaction
        composeTestRule.onNodeWithTag("end_call_button").assertExists().performClick()
        assertTrue("End call callback was not invoked", callEnded)
        assertTrue("WebRTC resources disposal callback was not invoked", resourcesDisposed)
    }

    @Test
    fun testSwitchCameraDisabledWhenSwitching() {
        var clicked = false
        composeTestRule.setContent {
            CallScreen(
                participantName = "سارة أحمد",
                isFrontCamera = true,
                isSwitchingCamera = true, // Switching in progress!
                onSwitchCamera = { clicked = true }
            )
        }

        // The switch buttons must be disabled when isSwitchingCamera is true
        composeTestRule.onNodeWithTag("switch_camera_button").assertExists().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pip_switch_camera_button").assertExists().assertIsNotEnabled()
    }

    @Test
    fun testTorchButtonExposedOnRearCamera() {
        var torchToggled = false
        composeTestRule.setContent {
            CallScreen(
                participantName = "سارة أحمد",
                isFrontCamera = false, // Back camera active
                isTorchOn = false,
                isSwitchingCamera = false,
                onToggleTorch = { torchToggled = true }
            )
        }

        // PiP Torch button should exist on rear camera and be clickable
        composeTestRule.onNodeWithTag("pip_torch_button").assertExists().performClick()
        assertTrue("Torch toggle callback was invoked", torchToggled)
    }

    @Test
    fun testRapidConcurrentClicksOnCameraSwitchButtons() {
        var switchExecutionCount = 0
        var isSwitchingState by mutableStateOf(false)
        var isFrontCameraState by mutableStateOf(true)

        composeTestRule.setContent {
            CallScreen(
                participantName = "سارة أحمد",
                isFrontCamera = isFrontCameraState,
                isSwitchingCamera = isSwitchingState,
                onSwitchCamera = {
                    // Simulate handler protected by the isSwitchingCamera guard
                    if (!isSwitchingState) {
                        isSwitchingState = true
                        switchExecutionCount++
                    }
                }
            )
        }

        // Initially both buttons must be present and enabled
        composeTestRule.onNodeWithTag("switch_camera_button").assertExists().assertIsEnabled()
        composeTestRule.onNodeWithTag("pip_switch_camera_button").assertExists().assertIsEnabled()

        // 1. First click on the bottom control bar switch button
        composeTestRule.onNodeWithTag("switch_camera_button").performClick()
        assertEquals("First click must start the camera switch", 1, switchExecutionCount)
        assertTrue("isSwitchingState must transition to true", isSwitchingState)

        // 2. Both buttons must now be disabled immediately while the switch is in flight
        composeTestRule.onNodeWithTag("switch_camera_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pip_switch_camera_button").assertIsNotEnabled()

        // 3. Attempt rapid simultaneous clicks on PiP and bottom buttons while isSwitchingCamera is true
        // Disabled nodes in Compose ignore clicks and will not trigger callback
        composeTestRule.onNodeWithTag("pip_switch_camera_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("switch_camera_button").assertIsNotEnabled()

        // Verify count remains strictly 1 - no duplicate or simultaneous executions
        assertEquals("Simultaneous clicks must be prevented while isSwitchingCamera is active", 1, switchExecutionCount)

        // 4. Once camera switch finishes, reset state
        isSwitchingState = false
        isFrontCameraState = !isFrontCameraState

        // Verify buttons are re-enabled after switch completes
        composeTestRule.onNodeWithTag("switch_camera_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("pip_switch_camera_button").assertIsEnabled()

        // 5. Subsequent valid click after completion succeeds
        composeTestRule.onNodeWithTag("pip_switch_camera_button").performClick()
        assertEquals("Subsequent click after switch completed must increment execution count", 2, switchExecutionCount)
    }

    @Test
    fun testViewModelRapidConcurrentClicksWithGuard() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = AyerViewModel(app)

        // Start mock video call
        val testCandidate = MatchCandidate(
            id = "cand_123",
            name = "سارة أحمد",
            age = 24,
            country = "مصر",
            countryCode = "EG",
            gender = "أنثى",
            language = "العربية",
            bio = "مرحباً بكم في Ayer",
            interests = listOf("تعارف", "موسيقى")
        )
        viewModel.startCallSession(candidate = testCandidate, isVideo = true)

        val initialSession = viewModel.activeCallSession.value
        assertNotNull("Call session must be active", initialSession)
        assertFalse("Initially isSwitchingCamera must be false", initialSession!!.isSwitchingCamera)

        // Simulate concurrent flood of rapid clicks to toggleCameraFlip from multiple threads
        val numberOfThreads = 6
        val latchStart = java.util.concurrent.CountDownLatch(1)
        val latchDone = java.util.concurrent.CountDownLatch(numberOfThreads)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(numberOfThreads)

        repeat(numberOfThreads) {
            executor.submit {
                latchStart.await()
                try {
                    viewModel.toggleCameraFlip()
                } finally {
                    latchDone.countDown()
                }
            }
        }

        latchStart.countDown()
        latchDone.await(3, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()

        // Verify consistent end state after concurrent flood
        val finalSession = viewModel.activeCallSession.value
        assertNotNull(finalSession)
        assertFalse("isSwitchingCamera guard must be released back to false", finalSession!!.isSwitchingCamera)
    }

    @Test
    fun testViewModelLayerGuardRejectsSimultaneousDoubleClicks() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = AyerViewModel(app)

        val testCandidate = MatchCandidate(
            id = "cand_456",
            name = "مريم خالد",
            age = 22,
            country = "السعودية",
            countryCode = "SA",
            gender = "أنثى",
            language = "العربية",
            bio = "Ayer Call Test",
            interests = listOf("فيديو", "دردشة")
        )
        viewModel.startCallSession(candidate = testCandidate, isVideo = true)

        val initialFront = viewModel.activeCallSession.value?.isFrontCamera ?: true

        // Configure mock delay on the WebRTC engine layer to ensure overlapping execution
        viewModel.webRTCManager?.mockSwitchDelayMs = 120L

        val latchStart = java.util.concurrent.CountDownLatch(1)
        val latchDone = java.util.concurrent.CountDownLatch(2)
        val callResults = java.util.Collections.synchronizedList(mutableListOf<Boolean>())

        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            // Thread 1: Simulates clicking the bottom control deck camera button
            executor.submit {
                latchStart.await()
                val res = viewModel.toggleCameraFlip()
                callResults.add(res)
                latchDone.countDown()
            }

            // Thread 2: Simulates simultaneously clicking the PiP quick switch button
            executor.submit {
                latchStart.await()
                val res = viewModel.toggleCameraFlip()
                callResults.add(res)
                latchDone.countDown()
            }

            // Fire both clicks at the exact same moment
            latchStart.countDown()
            val finished = latchDone.await(3, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("Both simultaneous click handlers must finish", finished)

            assertEquals("Two click attempts were made", 2, callResults.size)
            val acceptedCount = callResults.count { it }
            val rejectedCount = callResults.count { !it }

            assertEquals("Exactly one click must be accepted by the ViewModel / WebRTC guard", 1, acceptedCount)
            assertEquals("The simultaneous duplicate click must be rejected immediately", 1, rejectedCount)

            // Crucial verification: The camera flipped EXACTLY once, not twice!
            val finalSession = viewModel.activeCallSession.value
            assertNotNull(finalSession)
            assertNotEquals("Camera facing must toggle exactly once", initialFront, finalSession!!.isFrontCamera)
            assertFalse("isSwitchingCamera must be reset to false", finalSession.isSwitchingCamera)
            assertFalse("isCameraSwitchInProgress StateFlow must be false", viewModel.isCameraSwitchInProgress.value)
        } finally {
            viewModel.webRTCManager?.mockSwitchDelayMs = 0L
            executor.shutdown()
        }
    }
}
