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
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calls.CallLifecycleState
import com.example.data.local.AyerDatabase
import com.example.data.model.ActiveCallSession
import com.example.data.model.CallQualityMode
import com.example.data.model.FriendItem
import com.example.data.model.MatchCandidate
import com.example.data.model.PrivateChatMessage
import com.example.data.model.UserEngagementState
import com.example.data.model.UserProfile
import com.example.data.remote.FirestoreChatService
import com.example.data.repository.AyerRepository
import com.example.ui.AyerViewModel
import com.example.ui.locale.AppLanguage
import com.example.ui.screens.AccountPreferencesCard
import com.example.ui.screens.CallScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.screens.UserIdentityHeaderCard
import com.example.ui.theme.AyerTheme
import com.example.util.MediaImageHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        vm.setActiveCallSessionForTesting(
            ActiveCallSession(
                callId = "call_tr_1",
                roomName = "ayer_1on1_call_tr_1",
                participantId = "cand_tr_1",
                participantName = "ندى",
                participantAge = 23,
                participantCountry = "مصر",
                participantCountryCode = "EG",
                isVideo = false,
                isOutgoing = true,
                callState = CallLifecycleState.ACTIVE,
                pointsCostPerMinute = 5,
                durationSeconds = 0,
                isMuted = false,
                isCameraEnabled = false,
                isFrontCamera = true,
                isSpeakerOn = false,
                isTranslationEnabled = true,
                qualityMode = CallQualityMode.ADAPTIVE_SMART,
                isAudioPriorityWhenWeak = true,
                isNetworkWeak = false,
                statusHint = null,
                liveSubtitles = null
            )
        )
        assertTrue(vm.activeCallSession.value?.isTranslationEnabled ?: false)

        vm.toggleCallTranslation()
        assertFalse(vm.activeCallSession.value?.isTranslationEnabled ?: true)

        vm.toggleCallTranslation()
        assertTrue(vm.activeCallSession.value?.isTranslationEnabled ?: false)
        vm.setActiveCallSessionForTesting(null)
    }

    @Test
    fun `AyerDatabase MIGRATION_9_10 drops legacy secondary_profiles table cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS secondary_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        try {
            val sqlDb = helper.writableDatabase
            AyerDatabase.MIGRATION_9_10.migrate(sqlDb)
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='secondary_profiles'").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun `AyerRepository never mints local coins without authoritative server confirmation`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AyerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.userDao().insertUser(UserProfile(id = 1L, uid = "u_local_test", name = "سارة", pointsBalance = 0))
            val repo = AyerRepository(
                context = app,
                userDao = db.userDao(),
                transactionDao = db.transactionDao(),
                notificationDao = db.notificationDao(),
                platformApi = null
            )

            repeat(5) { repo.recordMatchEngagement() }
            repo.recordStreamWatchEngagement(minutes = 30)
            repeat(3) { repo.recordGiftEngagement() }

            assertFalse(repo.claimDailyMissionReward("mission_matches"))
            assertFalse(repo.claimDailyMilestoneReward())

            val storedUser = db.userDao().getUserProfileSync()
            assertEquals(0, storedUser?.pointsBalance)

            val hostApplyResult = repo.applyForHost("وكالة اختبار")
            assertTrue(hostApplyResult.isFailure)
        } finally {
            db.close()
        }
    }

    @Test
    fun `MediaImageHelper decodes Base64 image data URIs cleanly and handles invalid inputs safely`() {
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val b64 = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
        val decoded = MediaImageHelper.toCoilModel("data:image/jpeg;base64,$b64", null)
        assertTrue(decoded is ByteArray)
        assertArrayEquals(payload, decoded as ByteArray)

        val fallback = MediaImageHelper.toCoilModel("data:image/jpeg;base64,", com.example.R.drawable.img_user_avatar)
        assertEquals(com.example.R.drawable.img_user_avatar, fallback)

        assertNull(MediaImageHelper.toCoilModel("   ", null))
        assertEquals("https://cdn.example.invalid/pic.jpg", MediaImageHelper.toCoilModel("https://cdn.example.invalid/pic.jpg", null))
    }

    @Test
    fun `UserIdentityHeaderCard hides self-report button on own profile by default`() {
        composeRule.setContent {
            AyerTheme {
                UserIdentityHeaderCard(
                    userProfile = UserProfile(uid = "me_1", name = "ليان", country = "السعودية", age = 24),
                    userEmail = "layan@example.com",
                    engagementState = UserEngagementState(),
                    onEditProfileClick = {},
                    onNotificationCenterClick = {},
                    unreadNotificationCount = 0,
                    onLeaderboardClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("user_identity_header_card").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_report_user").assertDoesNotExist()
    }

    @Test
    fun `AyerViewModel rejects oversized voice clip missing Base64 cloud payload`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AyerViewModel(app)
        vm.sendVoiceMessage(
            friendId = "friend_99",
            durationSec = 55,
            voiceFilePath = "/tmp/oversized.m4a",
            voiceAudioBase64 = null
        )
        assertEquals("المقطع الصوتي طويل جداً ولا يمكن إرساله عبر السحابة.", vm.uiNotice.value)
    }
}

