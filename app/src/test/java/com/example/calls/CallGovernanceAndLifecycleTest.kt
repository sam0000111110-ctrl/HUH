package com.example.calls

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AyerDatabase
import com.example.data.model.FriendItem
import com.example.data.model.PrivateChatMessage
import com.example.ui.AyerViewModel
import com.example.ui.components.IncomingCallOverlay
import com.example.ui.screens.CallAdminDashboardScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.theme.AyerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallGovernanceAndLifecycleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AyerDatabase
    private var fakeWallClockMs = 1_780_000_000_000L
    private var fakeMonoClockMs = 10_000L
    private lateinit var manager: CallGovernanceManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AyerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeWallClockMs = 1_780_000_000_000L
        fakeMonoClockMs = 10_000L
        manager = CallGovernanceManager(
            callDao = db.callDao(),
            notificationDao = db.notificationDao(),
            remoteApi = null,
            wallClockProvider = { fakeWallClockMs },
            elapsedRealtimeProvider = { fakeMonoClockMs },
            allowLocalPreviewForTests = true
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `1_on_1 audio and video call lifecycles calculate exact connected cost and duration`() = runBlocking {
        // 1. Audio call: ring for 25s (NOT billed), connect for 120s -> 4 participant mins * 0.02 = 0.08
        val audioInit = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(audioInit is CallInitiationOutcome.Ready)
        val audioReady = audioInit as CallInitiationOutcome.Ready

        fakeWallClockMs += 25_000L
        fakeMonoClockMs += 25_000L
        assertNotNull(manager.markCallAccepted(audioReady.callId, "u2"))

        fakeWallClockMs += 120_000L
        fakeMonoClockMs += 120_000L
        val audioFinal = manager.finalizeCall(audioReady.callId, "u1")!!
        assertEquals(CallLifecycleState.ENDED.name, audioFinal.status)
        assertEquals(120, audioFinal.connectedDurationSeconds)
        assertEquals(0.08, audioFinal.estimatedCost, 0.0001)

        // 2. Video call: ring 10s, talk 60s -> 2 participant mins * 0.08 = 0.16
        val videoInit = manager.initiateCall("u1", "سامر", "u3", "خالد", CallType.VIDEO) as CallInitiationOutcome.Ready
        fakeWallClockMs += 10_000L
        fakeMonoClockMs += 10_000L
        manager.markCallAccepted(videoInit.callId, "u3")

        fakeWallClockMs += 60_000L
        fakeMonoClockMs += 60_000L
        val videoFinal = manager.finalizeCall(videoInit.callId, "u3")!!
        assertEquals(60, videoFinal.connectedDurationSeconds)
        assertEquals(0.16, videoFinal.estimatedCost, 0.0001)
    }

    @Test
    fun `ringing missed rejected and double finalization produce zero cost and stay idempotent`() = runBlocking {
        // Missed call (hangup before accept after 18s ringing)
        val missedInit = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.VIDEO) as CallInitiationOutcome.Ready
        fakeWallClockMs += 18_000L
        fakeMonoClockMs += 18_000L
        val missedFinal = manager.finalizeCall(missedInit.callId, "u1", CallLifecycleState.MISSED)!!
        assertEquals(CallLifecycleState.MISSED.name, missedFinal.status)
        assertEquals(0, missedFinal.connectedDurationSeconds)
        assertEquals(0.0, missedFinal.estimatedCost, 0.0)

        // Idempotent second end attempt must not mutate or double-record
        val duplicate = manager.finalizeCall(missedInit.callId, "u2", CallLifecycleState.ENDED)!!
        assertEquals(CallLifecycleState.MISSED.name, duplicate.status)
        assertEquals(0, duplicate.connectedDurationSeconds)

        // Rejected incoming invite
        val invite = IncomingCallInvite(
            callId = "inc_1",
            roomName = "ayer_1on1_inc_1",
            callerId = "u2",
            callerName = "سارة",
            callType = CallType.AUDIO,
            receivedAtMs = fakeWallClockMs
        )
        fakeWallClockMs += 7_000L
        fakeMonoClockMs += 7_000L
        val rejectedFinal = manager.rejectIncomingInvite(invite, "u1", "سامر")!!
        assertEquals(CallLifecycleState.REJECTED.name, rejectedFinal.status)
        assertEquals(0, rejectedFinal.connectedDurationSeconds)
        assertEquals(0.0, rejectedFinal.estimatedCost, 0.0)
    }

    @Test
    fun `reconnection gaps and tampered wall clock do not inflate call duration`() = runBlocking {
        val init = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.markCallAccepted(init.callId, "u2")

        // Talk for 40s
        fakeMonoClockMs += 40_000L
        fakeWallClockMs += 40_000L

        // Disconnect / Reconnecting for 15s
        manager.markConnectionState(init.callId, "u1", isReconnecting = true)
        fakeMonoClockMs += 15_000L
        // Also simulate user trying to tamper with device wall clock (+5 hours forward)
        fakeWallClockMs += 5 * 3_600_000L
        manager.markConnectionState(init.callId, "u1", isReconnecting = false)

        // Talk 20s more -> total connected = 40 + 20 = 60s (15s reconnect gap excluded, clock tamper ignored)
        fakeMonoClockMs += 20_000L
        val finalized = manager.finalizeCall(init.callId, "u2", reason = "PEER_LEFT")!!
        assertEquals(60, finalized.connectedDurationSeconds)
        assertEquals(15_000L, finalized.disconnectedGapMs)
        assertEquals(0.04, finalized.estimatedCost, 0.0001)
    }

    @Test
    fun `unauthorized third party is blocked from accepting or ending 1_on_1 call`() = runBlocking {
        val init = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.VIDEO) as CallInitiationOutcome.Ready
        assertNull(manager.markCallAccepted(init.callId, "intruder_99"))
        assertNull(manager.finalizeCall(init.callId, "intruder_99"))

        // Authorized callee can still accept and end cleanly
        assertNotNull(manager.markCallAccepted(init.callId, "u2"))
        fakeMonoClockMs += 30_000L
        val ended = manager.finalizeCall(init.callId, "u2")!!
        assertEquals(CallLifecycleState.ENDED.name, ended.status)
        assertEquals(30, ended.connectedDurationSeconds)
    }

    @Test
    fun `rate limit user daily limit budget hard stop VIP exemption and video-off downgrade work end to end`() = runBlocking {
        manager.updateGovernanceConfig(
            CallGovernanceConfigEntity(
                monthlyBudgetAmount = 1.0,
                currencyCode = "EGP",
                alertThreshold1Percent = 50,
                alertThreshold2Percent = 80,
                hardStopAt100Percent = true,
                maxCallDurationMinutes = 30,
                perUserDailyCallLimitMinutes = 20,
                perUserDailyVideoLimitMinutes = 10,
                monthlyVideoLimitMinutes = 500,
                disableVideoKeepAudioOnly = false,
                audioCostPerParticipantMinute = 0.05,
                videoCostPerParticipantMinute = 0.10,
                rateLimitCallsPerMinute = 3,
                exemptUserIdsCsv = "vip_user"
            )
        )

        // 1. Test disableVideoKeepAudioOnly downgrades video -> audio without blocking audio
        manager.updateGovernanceConfig(
            db.callDao().getGovernanceConfigSync()!!.copy(disableVideoKeepAudioOnly = true)
        )
        val downgraded = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.VIDEO) as CallInitiationOutcome.Ready
        assertEquals(CallType.AUDIO, downgraded.effectiveCallType)
        assertTrue(downgraded.downgradedToAudio)
        manager.finalizeCall(downgraded.callId, "u1", CallLifecycleState.CANCELLED)

        // Restore video and test rate limit (3 calls/min allowed, 4th blocked + alert)
        manager.updateGovernanceConfig(
            db.callDao().getGovernanceConfigSync()!!.copy(disableVideoKeepAudioOnly = false)
        )
        val c2 = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.finalizeCall(c2.callId, "u1", CallLifecycleState.CANCELLED)
        val c3 = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.finalizeCall(c3.callId, "u1", CallLifecycleState.CANCELLED)

        val rateBlocked = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(rateBlocked is CallInitiationOutcome.Blocked)
        assertEquals("RATE_LIMITED", (rateBlocked as CallInitiationOutcome.Blocked).code)

        // Advance monotonic clock past 1 minute window
        fakeMonoClockMs += 65_000L
        fakeWallClockMs += 65_000L

        // 2. Consume 5 minutes of video -> 5 * 2 * 0.10 = 1.00 EGP (hits 50%, 80%, 100%!)
        // Also keeps an active second call running BEFORE 100% exhaustion to prove active calls are NOT dropped!
        val activeNonStop = manager.initiateCall("u_other", "ندى", "u3", "خالد", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.markCallAccepted(activeNonStop.callId, "u3")

        val bigVideo = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.VIDEO) as CallInitiationOutcome.Ready
        manager.markCallAccepted(bigVideo.callId, "u2")
        fakeMonoClockMs += 300_000L
        fakeWallClockMs += 300_000L
        val endedBig = manager.finalizeCall(bigVideo.callId, "u1")!!
        assertEquals(1.0, endedBig.estimatedCost, 0.001)

        // Verify already-active call is still active & can finish cleanly (300s concurrent + 30s = 330s)
        fakeMonoClockMs += 30_000L
        val activeFinished = manager.finalizeCall(activeNonStop.callId, "u3")!!
        assertEquals(CallLifecycleState.ENDED.name, activeFinished.status)
        assertEquals(330, activeFinished.connectedDurationSeconds)

        // 3. New call from normal user MUST be blocked at 100% budget
        val budgetBlocked = manager.initiateCall("u4", "علي", "u5", "منى", CallType.AUDIO)
        assertTrue(budgetBlocked is CallInitiationOutcome.Blocked)
        assertEquals("BUDGET_EXCEEDED", (budgetBlocked as CallInitiationOutcome.Blocked).code)

        // 4. VIP exempt user MUST still be allowed even at 100% budget
        val vipAllowed = manager.initiateCall("vip_user", "مدير VIP", "u5", "منى", CallType.VIDEO)
        assertTrue(vipAllowed is CallInitiationOutcome.Ready)

        // 5. Verify admin alerts + notification DAO entries emitted
        val alerts = db.callDao().getAlertsSync()
        assertTrue(alerts.any { it.code == "ABNORMAL_USAGE_SPIKE" })
        assertTrue(alerts.any { it.code == "BUDGET_100_STOP" })
        val adminNotifs = db.notificationDao().getAllNotifications().first()
        assertTrue(adminNotifs.any { it.title.contains("مراقبة المكالمات") })
    }

    @Test
    fun `monitoring snapshot aggregates KPIs top users and alerts accurately`() = runBlocking {
        val c1 = manager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.markCallAccepted(c1.callId, "u2")
        fakeMonoClockMs += 60_000L
        manager.finalizeCall(c1.callId, "u1")

        val c2 = manager.initiateCall("u3", "خالد", "u4", "ريم", CallType.VIDEO) as CallInitiationOutcome.Ready
        manager.markCallAccepted(c2.callId, "u4")
        fakeMonoClockMs += 120_000L
        manager.finalizeCall(c2.callId, "u3")

        val c3 = manager.initiateCall("u1", "سامر", "u4", "ريم", CallType.AUDIO) as CallInitiationOutcome.Ready
        manager.finalizeCall(c3.callId, "u4", CallLifecycleState.REJECTED)

        val snap = manager.getMonitoringSnapshotSync()
        assertEquals(3, snap.todayCallsCount)
        assertEquals(2, snap.todayAudioCallsCount)
        assertEquals(1, snap.todayVideoCallsCount)
        assertEquals(2, snap.successfulCallsCount)
        assertEquals(1, snap.rejectedCallsCount)
        assertEquals(90, snap.averageCallDurationSeconds)
        assertEquals(1.0, snap.totalAudioMinutes, 0.01)
        assertEquals(2.0, snap.totalVideoMinutes, 0.01)
        assertTrue(snap.topConsumingUsers.isNotEmpty())
        assertTrue(snap.dailyEstimatedCost > 0.0)
        assertTrue(snap.monthlyEstimatedCost > 0.0)
    }

    @Test
    fun `incoming call overlay renders and dispatches accept callback`() {
        var acceptedInvite: IncomingCallInvite? = null
        val testInvite = IncomingCallInvite(
            callId = "ui_inc_1",
            roomName = "ayer_1on1_ui_inc_1",
            callerId = "f1",
            callerName = "سارة أحمد",
            callType = CallType.VIDEO
        )

        composeRule.setContent {
            AyerTheme {
                IncomingCallOverlay(
                    invite = testInvite,
                    onAccept = { acceptedInvite = it },
                    onReject = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("incoming_call_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("incoming_call_accept_button").performClick()
        assertEquals("ui_inc_1", acceptedInvite?.callId)
    }

    @Test
    fun `private chat screen preserves text messages and renders call log card`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AyerViewModel(app)
        val friend = FriendItem(id = "f1", name = "سارة أحمد", country = "دبي، الإمارات")
        val msgs = listOf(
            PrivateChatMessage(id = "t1", senderId = "f1", text = "أهلاً بك في الدردشة النصية!"),
            PrivateChatMessage(
                id = "c1",
                senderId = "me",
                text = "مكالمة فيديو (01:25)",
                callLogRecordId = "rec_1",
                callLogIsVideo = true,
                callLogStatus = CallLifecycleState.ENDED.name,
                callLogDurationSeconds = 85
            )
        )
        composeRule.setContent {
            AyerTheme {
                PrivateChatScreen(friend = friend, viewModel = vm, messages = msgs, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("أهلاً بك في الدردشة النصية!").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_call_log_card").assertIsDisplayed()
    }

    @Test
    fun `call admin dashboard renders monitoring title and budget tab`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AyerViewModel(app)
        composeRule.setContent {
            AyerTheme {
                CallAdminDashboardScreen(viewModel = vm, onDismiss = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("call_admin_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Call Management & Monitoring").assertIsDisplayed()
        composeRule.onNodeWithText("الميزانية والحدود").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("call_budget_lazy_column")
            .performScrollToNode(hasTestTag("save_call_budget_config_button"))
        composeRule.onNodeWithTag("save_call_budget_config_button").assertIsDisplayed()
    }

    @Test
    fun `default CallGovernanceManager fails closed when server URL auth or LiveKit token is missing`() = runBlocking {
        // 1. Default production mode (allowLocalPreviewForTests = false) with no remoteApi -> Blocked
        val unconfiguredManager = CallGovernanceManager(
            callDao = db.callDao(),
            notificationDao = db.notificationDao(),
            remoteApi = null
        )
        val noServerOutcome = unconfiguredManager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(noServerOutcome is CallInitiationOutcome.Blocked)
        assertEquals("CALL_SERVER_NOT_CONFIGURED", (noServerOutcome as CallInitiationOutcome.Blocked).code)

        // 2. Remote API present, blank auth token -> Blocked
        var mode = "ERROR_500"
        val stubApi = object : CallBackendApi {
            override suspend fun initiateCall(
                bearerToken: String,
                body: InitiateCallRequestDto
            ): retrofit2.Response<InitiateCallResponseDto> {
                return when (mode) {
                    "BLANK_TOKEN" -> retrofit2.Response.success(
                        InitiateCallResponseDto(
                            call = CallRecordDto(
                                callId = "c_1",
                                roomName = "r_1",
                                callerId = "u1",
                                callerName = "سامر",
                                calleeId = "u2",
                                calleeName = "سارة",
                                callType = "AUDIO",
                                status = "DIALING",
                                initiatedAt = 1_780_000_000_000L
                            ),
                            livekitUrl = "",
                            token = "",
                            expiresAtMs = 1_780_000_300_000L,
                            maxDurationSeconds = 1800
                        )
                    )
                    else -> retrofit2.Response.error(
                        500,
                        "{\"error\":\"boom\"}".toResponseBody("application/json".toMediaType())
                    )
                }
            }

            override suspend fun acceptCall(bearerToken: String, callId: String) = throw UnsupportedOperationException()
            override suspend fun rejectCall(bearerToken: String, callId: String, body: EndCallRequestDto) = throw UnsupportedOperationException()
            override suspend fun reportConnectionState(bearerToken: String, callId: String, body: ConnectionStateRequestDto) = throw UnsupportedOperationException()
            override suspend fun getReconnectToken(bearerToken: String, callId: String) = throw UnsupportedOperationException()
            override suspend fun endCall(bearerToken: String, callId: String, body: EndCallRequestDto) = throw UnsupportedOperationException()
            override suspend fun updateGovernanceConfig(bearerToken: String, config: CallConfigDto) = throw UnsupportedOperationException()
            override suspend fun getGovernanceConfig(bearerToken: String) = throw UnsupportedOperationException()
        }

        val noAuthManager = CallGovernanceManager(
            callDao = db.callDao(),
            notificationDao = db.notificationDao(),
            remoteApi = stubApi,
            authTokenProvider = { null }
        )
        val noAuthOutcome = noAuthManager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(noAuthOutcome is CallInitiationOutcome.Blocked)
        assertEquals("AUTH_REQUIRED_FOR_CALL", (noAuthOutcome as CallInitiationOutcome.Blocked).code)

        // 3. Remote API returns HTTP 500 -> Blocked
        val authedManager = CallGovernanceManager(
            callDao = db.callDao(),
            notificationDao = db.notificationDao(),
            remoteApi = stubApi,
            authTokenProvider = { "valid-firebase-token" }
        )
        val error500Outcome = authedManager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(error500Outcome is CallInitiationOutcome.Blocked)
        assertEquals("BACKEND_ERROR_500", (error500Outcome as CallInitiationOutcome.Blocked).code)

        // 4. Remote API returns HTTP 200 with blank LiveKit URL/token -> Blocked
        mode = "BLANK_TOKEN"
        val blankSessionOutcome = authedManager.initiateCall("u1", "سامر", "u2", "سارة", CallType.AUDIO)
        assertTrue(blankSessionOutcome is CallInitiationOutcome.Blocked)
        assertEquals("INVALID_CALL_SESSION", (blankSessionOutcome as CallInitiationOutcome.Blocked).code)
    }
}
