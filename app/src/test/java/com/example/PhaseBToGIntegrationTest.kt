package com.example

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.FriendItem
import com.example.data.model.MatchCandidate
import com.example.data.model.PrivateChatMessage
import com.example.data.remote.FirestoreChatService
import com.example.ui.AyerViewModel
import com.example.ui.locale.AppLanguage
import com.example.ui.screens.AccountPreferencesCard
import com.example.ui.screens.CallScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.theme.AyerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhaseBToGIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `FirestoreChatService privateChatId is symmetric and deterministic`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val svc = FirestoreChatService(app)
        val id1 = svc.privateChatId("user_b", "user_a")
        val id2 = svc.privateChatId("user_a", "user_b")
        assertEquals(id1, id2)
        assertEquals("user_a_user_b", id1)
    }

    @Test
    fun `PrivateChatScreen renders translated AI caption and AAC voice message bubble`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AyerViewModel(app)
        val friend = FriendItem(id = "friend_99", name = "ليلى", country = "الأردن")
        val messages = listOf(
            PrivateChatMessage(
                id = "m_txt",
                senderId = "friend_99",
                text = "Hello my friend",
                translatedText = "مرحباً يا صديقي"
            ),
            PrivateChatMessage(
                id = "m_voice",
                senderId = "me",
                isVoiceMessage = true,
                voiceDurationSeconds = 7
            )
        )

        composeRule.setContent {
            AyerTheme {
                PrivateChatScreen(
                    friend = friend,
                    viewModel = vm,
                    messages = messages,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hello my friend").assertIsDisplayed()
        composeRule.onNodeWithText("مرحباً يا صديقي").assertIsDisplayed()
        composeRule.onNodeWithText("🎤 رسالة صوتية (AAC)").assertIsDisplayed()
        composeRule.onNodeWithText("المدة: 7 ثانية").assertIsDisplayed()
    }

    @Test
    fun `CallScreen displays live AI subtitle banner and invokes toggle callback`() {
        var translationToggled = false
        composeRule.setContent {
            AyerTheme {
                CallScreen(
                    participantName = "عمر",
                    isTranslationEnabled = true,
                    liveSubtitles = "أهلاً بك، كيف حالك اليوم؟",
                    onToggleTranslation = { translationToggled = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("أهلاً بك، كيف حالك اليوم؟").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("ترجمة AI الفورية").performClick()
        assertTrue(translationToggled)
    }

    @Test
    fun `AccountPreferencesCard exposes blocked users count and Delete Account confirmation dialog`() {
        var deleteConfirmed = false
        composeRule.setContent {
            AyerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AccountPreferencesCard(
                        isDarkTheme = true,
                        onToggleTheme = {},
                        appLanguage = AppLanguage.ARABIC,
                        onOpenLanguageSelection = {},
                        onCreateSecondaryProfile = {},
                        blockedUserIds = setOf("uid_bad_1", "uid_bad_2"),
                        onUnblockUser = {},
                        onLogout = {},
                        onDeleteAccount = { deleteConfirmed = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("المستخدمون المحظورون (2)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_delete_account_action").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("تأكيد حذف الحساب نهائياً").assertIsDisplayed()
        composeRule.onNodeWithText("نعم، احذف نهائياً").performClick()
        assertTrue(deleteConfirmed)
    }

    @Test
    fun `AyerViewModel call translation toggle updates active call session state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AyerViewModel(app)
        val candidate = MatchCandidate(
            id = "cand_tr_1",
            name = "ندى",
            age = 23,
            country = "مصر",
            countryCode = "EG",
            gender = "أنثى",
            language = "العربية",
            bio = "Hello",
            interests = emptyList()
        )
        vm.startCallSession(candidate = candidate, isVideo = false)
        assertTrue(vm.activeCallSession.value?.isTranslationEnabled ?: false)

        vm.toggleCallTranslation()
        assertFalse(vm.activeCallSession.value?.isTranslationEnabled ?: true)

        vm.toggleCallTranslation()
        assertTrue(vm.activeCallSession.value?.isTranslationEnabled ?: false)
        vm.endCallSession()
    }
}
