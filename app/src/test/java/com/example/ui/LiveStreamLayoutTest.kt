package com.example.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.StreamMessage
import com.example.ui.components.LiveStreamBasicLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LiveStreamLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLiveStreamLayoutEssentialComponentsExist() {
        var closed = false
        var followed = false
        var toppedUp = false
        var sentMessage: String? = null
        var giftClicked = false
        var liked = false

        val testMessages = listOf(
            StreamMessage(
                id = "msg_1",
                senderName = "أحمد",
                originalText = "منور البث يا غالي! 🔥",
                isSystem = false
            ),
            StreamMessage(
                id = "msg_2",
                senderName = "نظام البث",
                originalText = "انضم خالد إلى البث 🌟",
                isSystem = true
            ),
            StreamMessage(
                id = "msg_3",
                senderName = "ليلى",
                originalText = "أرسلت تاج الذهب 👑",
                isGift = true,
                giftEmoji = "👑"
            )
        )

        composeTestRule.setContent {
            LiveStreamBasicLayout(
                streamTitle = "بث مباشر تجريبي",
                hostName = "ريم محمد",
                hostCountry = "الإمارات 🇦🇪",
                viewersCount = 1850,
                pointsCount = 45200,
                userCoinsBalance = 520,
                isLive = true,
                messages = testMessages,
                pinnedAnnouncement = "📌 أهلاً وسهلاً بكم في بث ريم",
                onCloseStream = { closed = true },
                onFollowHost = { followed = true },
                onTopUpPoints = { toppedUp = true },
                onSendMessage = { sentMessage = it },
                onSendGiftClick = { giftClicked = true },
                onLikeClick = { liked = true }
            )
        }

        // 1. Verify Video Display Container exists
        composeTestRule.onNodeWithTag("video_display_container").assertExists()
        composeTestRule.onNodeWithTag("stream_quality_badge").assertExists()

        // 2. Verify Viewer Count & Points Display Placeholders exist
        composeTestRule.onNodeWithTag("viewer_count_and_points_header").assertExists()
        composeTestRule.onNodeWithTag("viewer_count_display").assertExists()
        composeTestRule.onNodeWithTag("viewer_count_text").assertExists()
        composeTestRule.onNodeWithTag("viewer_count_text").assertTextContains("1.9k", substring = true)
        composeTestRule.onNodeWithTag("points_display").assertExists()
        composeTestRule.onNodeWithTag("points_text", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("points_text", useUnmergedTree = true).assertTextContains("45.2k", substring = true)
        composeTestRule.onNodeWithTag("user_coins_balance_badge").assertExists()

        // 3. Verify Chat Overlay and Input Controls exist
        composeTestRule.onNodeWithTag("chat_overlay").assertExists()
        composeTestRule.onNodeWithTag("pinned_announcement_banner").assertExists()
        composeTestRule.onNodeWithTag("chat_messages_list").assertExists()
        composeTestRule.onNodeWithTag("chat_input_field").assertExists()
        composeTestRule.onNodeWithTag("chat_send_button").assertExists()
        composeTestRule.onNodeWithTag("stream_gift_button").assertExists()
        composeTestRule.onNodeWithTag("stream_like_button").assertExists()

        // 4. Test Chat Message Input & Send
        composeTestRule.onNodeWithTag("chat_input_field").performTextInput("تحياتي للمضيف الرائع!")
        composeTestRule.onNodeWithTag("chat_send_button").performClick()
        assertEquals("تحياتي للمضيف الرائع!", sentMessage)

        // 5. Test Like Button Click
        composeTestRule.onNodeWithTag("stream_like_button").performClick()
        assertTrue(liked)

        // 6. Test Gift Button Click
        composeTestRule.onNodeWithTag("stream_gift_button").performClick()
        assertTrue(giftClicked)

        // 7. Test Close Button Click
        composeTestRule.onNodeWithTag("close_stream_button").performClick()
        assertTrue(closed)

        // 8. Verify Control Panel with Start/Stop & Camera Switch exists
        composeTestRule.onNodeWithTag("stream_control_panel").assertExists()
        composeTestRule.onNodeWithTag("start_stream_button").assertExists()
        composeTestRule.onNodeWithTag("stop_stream_button").assertExists()
        composeTestRule.onNodeWithTag("switch_camera_button").assertExists()
        composeTestRule.onNodeWithTag("camera_switch_button", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testBroadcasterControlPanelInteractions() {
        var startStreamCalled = false
        var stopStreamCalled = false
        var switchCameraCalled = false

        composeTestRule.setContent {
            LiveStreamBasicLayout(
                isLive = false,
                isStreaming = false,
                isFrontCamera = true,
                onStartStream = { startStreamCalled = true },
                onStopStream = { stopStreamCalled = true },
                onSwitchCamera = { switchCameraCalled = true }
            )
        }

        // Verify control panel elements exist
        composeTestRule.onNodeWithTag("stream_control_panel").assertExists()
        composeTestRule.onNodeWithTag("start_stream_button").assertExists()
        composeTestRule.onNodeWithTag("stop_stream_button").assertExists()
        composeTestRule.onNodeWithTag("switch_camera_button").assertExists()

        // 1. Click Start Stream
        composeTestRule.onNodeWithTag("start_stream_button").performClick()
        assertTrue("onStartStream should be called", startStreamCalled)

        // 2. Click Stop Stream
        composeTestRule.onNodeWithTag("stop_stream_button").performClick()
        assertTrue("onStopStream should be called", stopStreamCalled)

        // 3. Click Switch Camera
        composeTestRule.onNodeWithTag("switch_camera_button").performClick()
        assertTrue("onSwitchCamera should be called", switchCameraCalled)
    }
}
