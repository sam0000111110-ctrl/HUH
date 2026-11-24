package com.example.data.remote

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Keep
@JsonClass(generateAdapter = true)
data class AuthSyncRequestDto(
    val displayName: String,
    val email: String
)

@Keep
@JsonClass(generateAdapter = true)
data class BackendUserDto(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val dob: String? = null,
    val ageYears: Int = 0,
    val ageVerified: Boolean = false,
    val ageVerificationStatus: String = "UNVERIFIED",
    val isAdmin: Boolean = false,
    val isAgent: Boolean = false,
    val isHost: Boolean = false,
    val hostStatus: String = "NONE",
    val agentCommissionPercent: Int = 20,
    val hostCommissionPercent: Int = 50,
    val agencyId: String = "",
    val agencyName: String = "",
    val referralCode: String = "",
    val profileVisibility: String = "PUBLIC",
    val directMessagePermission: String = "EVERYONE"
)

@Keep
@JsonClass(generateAdapter = true)
data class BackendWalletDto(
    val uid: String = "",
    val pointsBalance: Int = 0,
    val hostEarningsCoins: Int = 0,
    val subAgentsEarningsCoins: Int = 0,
    val reservedPayoutCoins: Int = 0,
    val totalAccumulatedPoints: Int = 0,
    val totalPointsSpent: Int = 0,
    val lastDailyClaimMs: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class BackendLedgerEntryDto(
    val entryUuid: String = "",
    val type: String = "",
    val amount: Int = 0,
    val balanceAfter: Int = 0,
    val description: String = "",
    val timestamp: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class WalletSummaryResponseDto(
    val user: BackendUserDto,
    val wallet: BackendWalletDto,
    val transactions: List<BackendLedgerEntryDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class DailyBonusResponseDto(
    val wallet: BackendWalletDto,
    val creditedCoins: Int = 0
)

@Keep
@JsonClass(generateAdapter = true)
data class SpendCoinsRequestDto(
    val amount: Int,
    val entryType: String,
    val description: String,
    val idempotencyKey: String,
    val referenceId: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class SpendCoinsResponseDto(
    val wallet: BackendWalletDto,
    val duplicate: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class SendGiftRequestDto(
    val recipientUid: String,
    val giftId: String,
    val giftName: String,
    val coinsCost: Int,
    val idempotencyKey: String,
    val streamId: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class SendGiftResponseDto(
    val senderWallet: BackendWalletDto,
    val recipientEarningsAdded: Int = 0,
    val agentCommissionAdded: Int = 0,
    val duplicate: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class VerifyPlayPurchaseRequestDto(
    val packageName: String,
    val productId: String,
    val purchaseToken: String,
    val orderId: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class VerifyPlayPurchaseResponseDto(
    val wallet: BackendWalletDto,
    val creditedCoins: Int = 0
)

@Keep
@JsonClass(generateAdapter = true)
data class SubmitAgeVerificationRequestDto(
    val displayName: String,
    val email: String,
    val dob: String,
    val ageYears: Int,
    val acceptedTermsAt: Long,
    val acceptedPrivacyAt: Long
)

@Keep
@JsonClass(generateAdapter = true)
data class AdminAgeVerificationItemDto(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val dob: String = "",
    val ageYears: Int = 0,
    val status: String = "PENDING_REVIEW",
    val rejectionReason: String? = null,
    val submittedAtMs: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class AdminAgeVerificationsResponseDto(
    val submissions: List<AdminAgeVerificationItemDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class ReviewAgeRequestDto(
    val decision: String,
    val reason: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class ReviewAgeResponseDto(
    val submission: AdminAgeVerificationItemDto,
    val user: BackendUserDto
)

@Keep
@JsonClass(generateAdapter = true)
data class CreatePayoutRequestDto(
    val amountCoins: Int,
    val payoutMethod: String = "BANK_TRANSFER",
    val bankAccountHolder: String,
    val bankName: String,
    val ibanOrAccount: String
)

@Keep
@JsonClass(generateAdapter = true)
data class PayoutRequestDto(
    val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val amountCoins: Int = 0,
    val usdEquivalentCents: Long = 0L,
    val payoutMethod: String = "BANK_TRANSFER",
    val bankAccountHolder: String = "",
    val bankName: String = "",
    val ibanOrAccount: String = "",
    val status: String = "PENDING_REVIEW",
    val reviewerNote: String? = null,
    val paymentReference: String? = null,
    val createdAtMs: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class CreatePayoutResponseDto(
    val request: PayoutRequestDto,
    val wallet: BackendWalletDto
)

@Keep
@JsonClass(generateAdapter = true)
data class PayoutListResponseDto(
    val payouts: List<PayoutRequestDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class ReviewPayoutRequestDto(
    val decision: String,
    val note: String? = null,
    val paymentReference: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class AgencySubAgentDto(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val country: String = "",
    val hostsCount: Int = 0,
    val monthlyRevenueCoins: Int = 0,
    val agentCommissionEarned: Int = 0,
    val status: String = "نشط"
)

@Keep
@JsonClass(generateAdapter = true)
data class AgencyDashboardResponseDto(
    val user: BackendUserDto,
    val wallet: BackendWalletDto,
    val subAgents: List<AgencySubAgentDto> = emptyList(),
    val payouts: List<PayoutRequestDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class RecruitSubAgentRequestDto(
    val subAgentName: String,
    val country: String = "",
    val subAgentUid: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class RecruitSubAgentResponseDto(
    val membership: AgencySubAgentDto,
    val subAgentsCount: Int = 0
)

@Keep
@JsonClass(generateAdapter = true)
data class AdminUpdateRolesRequestDto(
    val isAgent: Boolean? = null,
    val isHost: Boolean? = null,
    val isAdmin: Boolean? = null,
    val agentCommissionPercent: Int? = null,
    val hostCommissionPercent: Int? = null,
    val agencyName: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class AdminUpdateRolesResponseDto(
    val user: BackendUserDto
)

@Keep
@JsonClass(generateAdapter = true)
data class CreateReportRequestDto(
    val reportType: String,
    val targetUserId: String? = null,
    val streamId: String? = null,
    val reason: String,
    val details: String = ""
)

@Keep
@JsonClass(generateAdapter = true)
data class ModerationReportDto(
    val id: String = "",
    val reportType: String = "",
    val targetUserId: String? = null,
    val streamId: String? = null,
    val reporterUid: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "PENDING_REVIEW",
    val actionTaken: String? = null,
    val createdAtMs: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class ModerationReportsListResponseDto(
    val reports: List<ModerationReportDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class ResolveReportRequestDto(
    val status: String = "RESOLVED",
    val actionTaken: String = "WARNING"
)

@Keep
@JsonClass(generateAdapter = true)
data class BlockUserRequestDto(
    val blockedUid: String
)

@Keep
@JsonClass(generateAdapter = true)
data class BlockedUsersResponseDto(
    val blockedUsers: List<String> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class DeleteAccountResponseDto(
    val deleted: Boolean = false,
    val uid: String = ""
)

@Keep
@JsonClass(generateAdapter = true)
data class JoinMatchRequestDto(
    val displayName: String,
    val country: String = "",
    val countryCode: String = "SA",
    val userGender: String = "ANY",
    val bio: String = "",
    val chatType: String = "VIDEO",
    val genderFilter: String = "ANY",
    val countryFilter: String = "ANY"
)

@Keep
@JsonClass(generateAdapter = true)
data class MatchPartnerDto(
    val uid: String = "",
    val name: String = "",
    val age: Int = 21,
    val country: String = "",
    val countryCode: String = "SA",
    val gender: String = "ANY",
    val bio: String = ""
)

@Keep
@JsonClass(generateAdapter = true)
data class MatchJoinPollResponseDto(
    val status: String = "WAITING", // WAITING, MATCHED, IDLE
    val queueSize: Int = 0,
    val waitSeconds: Int = 0,
    val matchId: String? = null,
    val roomName: String? = null,
    val livekitUrl: String? = null,
    val token: String? = null,
    val expiresAtMs: Long = 0L,
    val chatType: String? = null,
    val costCoins: Int = 0,
    val partner: MatchPartnerDto? = null,
    val wallet: BackendWalletDto? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class LeaveMatchRequestDto(
    val matchId: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class LiveStreamDto(
    val id: String = "",
    val roomName: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val hostCountry: String = "",
    val hostCountryCode: String = "SA",
    val title: String = "",
    val category: String = "",
    val agencyName: String? = null,
    val isAgencyStream: Boolean = false,
    val isVip: Boolean = false,
    val viewersCount: Int = 0,
    val diamondsReceived: Int = 0,
    val status: String = "LIVE",
    val startedAtMs: Long = 0L
)

@Keep
@JsonClass(generateAdapter = true)
data class LiveStreamsListResponseDto(
    val streams: List<LiveStreamDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class StartStreamRequestDto(
    val title: String,
    val category: String = "تعارف وسوالف",
    val hostName: String = "",
    val hostCountry: String = "",
    val hostCountryCode: String = "SA"
)

@Keep
@JsonClass(generateAdapter = true)
data class StreamSessionResponseDto(
    val stream: LiveStreamDto,
    val livekitUrl: String = "",
    val token: String = "",
    val expiresAtMs: Long = 0L,
    val role: String = "SUBSCRIBER"
)

interface BackendPlatformApi {
    @POST("api/auth/sync")
    suspend fun syncAuth(
        @Header("Authorization") bearerToken: String,
        @Body body: AuthSyncRequestDto
    ): Response<WalletSummaryResponseDto>

    @GET("api/wallet/summary")
    suspend fun getWalletSummary(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50
    ): Response<WalletSummaryResponseDto>

    @POST("api/wallet/daily-bonus")
    suspend fun claimDailyBonus(
        @Header("Authorization") bearerToken: String
    ): Response<DailyBonusResponseDto>

    @POST("api/wallet/spend")
    suspend fun spendCoins(
        @Header("Authorization") bearerToken: String,
        @Body body: SpendCoinsRequestDto
    ): Response<SpendCoinsResponseDto>

    @POST("api/wallet/gift")
    suspend fun sendGift(
        @Header("Authorization") bearerToken: String,
        @Body body: SendGiftRequestDto
    ): Response<SendGiftResponseDto>

    @POST("api/billing/verify-play-purchase")
    suspend fun verifyPlayPurchase(
        @Header("Authorization") bearerToken: String,
        @Body body: VerifyPlayPurchaseRequestDto
    ): Response<VerifyPlayPurchaseResponseDto>

    @POST("api/age-verification/submit")
    suspend fun submitAgeVerification(
        @Header("Authorization") bearerToken: String,
        @Body body: SubmitAgeVerificationRequestDto
    ): Response<Unit>

    @GET("api/agency/dashboard")
    suspend fun getAgencyDashboard(
        @Header("Authorization") bearerToken: String
    ): Response<AgencyDashboardResponseDto>

    @POST("api/agency/sub-agents")
    suspend fun recruitSubAgent(
        @Header("Authorization") bearerToken: String,
        @Body body: RecruitSubAgentRequestDto
    ): Response<RecruitSubAgentResponseDto>

    @POST("api/payouts/request")
    suspend fun createPayoutRequest(
        @Header("Authorization") bearerToken: String,
        @Body body: CreatePayoutRequestDto
    ): Response<CreatePayoutResponseDto>

    @GET("api/payouts")
    suspend fun getMyPayouts(
        @Header("Authorization") bearerToken: String
    ): Response<PayoutListResponseDto>

    @POST("api/reports")
    suspend fun createReport(
        @Header("Authorization") bearerToken: String,
        @Body body: CreateReportRequestDto
    ): Response<Unit>

    @GET("api/blocks")
    suspend fun getBlocks(
        @Header("Authorization") bearerToken: String
    ): Response<BlockedUsersResponseDto>

    @POST("api/blocks")
    suspend fun blockUser(
        @Header("Authorization") bearerToken: String,
        @Body body: BlockUserRequestDto
    ): Response<BlockedUsersResponseDto>

    @DELETE("api/blocks/{blockedUid}")
    suspend fun unblockUser(
        @Header("Authorization") bearerToken: String,
        @Path("blockedUid") blockedUid: String
    ): Response<BlockedUsersResponseDto>

    @DELETE("api/account")
    suspend fun deleteAccount(
        @Header("Authorization") bearerToken: String
    ): Response<DeleteAccountResponseDto>

    // Matchmaking & LiveKit Broadcasts
    @POST("api/match/join")
    suspend fun joinMatchQueue(
        @Header("Authorization") bearerToken: String,
        @Body body: JoinMatchRequestDto
    ): Response<MatchJoinPollResponseDto>

    @GET("api/match/poll")
    suspend fun pollMatchQueue(
        @Header("Authorization") bearerToken: String
    ): Response<MatchJoinPollResponseDto>

    @POST("api/match/leave")
    suspend fun leaveMatchQueue(
        @Header("Authorization") bearerToken: String,
        @Body body: LeaveMatchRequestDto
    ): Response<Unit>

    @GET("api/streams")
    suspend fun listLiveStreams(
        @Header("Authorization") bearerToken: String,
        @Query("category") category: String? = null
    ): Response<LiveStreamsListResponseDto>

    @POST("api/streams")
    suspend fun startLiveStream(
        @Header("Authorization") bearerToken: String,
        @Body body: StartStreamRequestDto
    ): Response<StreamSessionResponseDto>

    @POST("api/streams/{id}/join")
    suspend fun joinLiveStream(
        @Header("Authorization") bearerToken: String,
        @Path("id") streamId: String
    ): Response<StreamSessionResponseDto>

    @POST("api/streams/{id}/leave")
    suspend fun leaveLiveStream(
        @Header("Authorization") bearerToken: String,
        @Path("id") streamId: String
    ): Response<Unit>

    @POST("api/streams/{id}/end")
    suspend fun endLiveStream(
        @Header("Authorization") bearerToken: String,
        @Path("id") streamId: String
    ): Response<Unit>

    // Admin Endpoints
    @GET("api/admin/age-verifications")
    suspend fun listAdminAgeVerifications(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String = "PENDING_REVIEW"
    ): Response<AdminAgeVerificationsResponseDto>

    @POST("api/admin/age-verifications/{uid}/review")
    suspend fun reviewAdminAgeVerification(
        @Header("Authorization") bearerToken: String,
        @Path("uid") uid: String,
        @Body body: ReviewAgeRequestDto
    ): Response<ReviewAgeResponseDto>

    @GET("api/admin/payouts")
    suspend fun listAdminPayouts(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String? = null
    ): Response<PayoutListResponseDto>

    @POST("api/admin/payouts/{requestId}/review")
    suspend fun reviewAdminPayout(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: ReviewPayoutRequestDto
    ): Response<CreatePayoutResponseDto>

    @POST("api/admin/users/{uid}/roles")
    suspend fun updateUserRolesAdmin(
        @Header("Authorization") bearerToken: String,
        @Path("uid") uid: String,
        @Body body: AdminUpdateRolesRequestDto
    ): Response<AdminUpdateRolesResponseDto>

    @GET("api/admin/reports")
    suspend fun listAdminReports(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String = "PENDING_REVIEW"
    ): Response<ModerationReportsListResponseDto>

    @POST("api/admin/reports/{reportId}/resolve")
    suspend fun resolveAdminReport(
        @Header("Authorization") bearerToken: String,
        @Path("reportId") reportId: String,
        @Body body: ResolveReportRequestDto
    ): Response<Unit>

    companion object {
        fun create(baseUrl: String): BackendPlatformApi {
            val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(BackendPlatformApi::class.java)
        }

        fun extractErrorMessage(response: Response<*>, fallback: String): String {
            return try {
                val raw = response.errorBody()?.string().orEmpty()
                if (raw.isBlank()) return fallback
                val json = JSONObject(raw)
                json.optString("message").takeIf { it.isNotBlank() } ?: fallback
            } catch (_: Throwable) {
                fallback
            }
        }
    }
}
