package com.example.calls

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class InitiateCallRequestDto(
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val callType: String
)

@JsonClass(generateAdapter = true)
data class CallRecordDto(
    val callId: String,
    val roomName: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val callType: String,
    val downgradedToAudio: Boolean = false,
    val status: String,
    val initiatedAt: Long,
    val acceptedAt: Long? = null,
    val endedAt: Long? = null,
    val connectedDurationSeconds: Int = 0,
    val estimatedCost: Double = 0.0,
    val currencyCode: String = "EGP",
    val failureReason: String? = null,
    val finalized: Boolean = false
)

@JsonClass(generateAdapter = true)
data class InitiateCallResponseDto(
    val call: CallRecordDto,
    val livekitUrl: String,
    val token: String,
    val expiresAtMs: Long,
    val maxDurationSeconds: Int
)

@JsonClass(generateAdapter = true)
data class ReconnectTokenResponseDto(
    val livekitUrl: String,
    val token: String,
    val expiresAtMs: Long
)

@JsonClass(generateAdapter = true)
data class ConnectionStateRequestDto(
    val state: String
)

@JsonClass(generateAdapter = true)
data class EndCallRequestDto(
    val finalStatus: String? = null,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class EndCallResponseDto(
    val call: CallRecordDto,
    val alreadyFinalized: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CallConfigDto(
    val monthlyBudgetAmount: Double = 1000.0,
    val currencyCode: String = "EGP",
    val alertThreshold1Percent: Int = 50,
    val alertThreshold2Percent: Int = 80,
    val hardStopAt100Percent: Boolean = true,
    val maxCallDurationMinutes: Int = 30,
    val perUserDailyCallLimitMinutes: Int = 60,
    val perUserDailyVideoLimitMinutes: Int = 30,
    val monthlyVideoLimitMinutes: Int = 2000,
    val disableVideoKeepAudioOnly: Boolean = false,
    val audioCostPerParticipantMinute: Double = 0.02,
    val videoCostPerParticipantMinute: Double = 0.08,
    val rateLimitCallsPerMinute: Int = 5,
    val alertFailureRatePercent: Int = 30,
    val alertMaxConcurrentCalls: Int = 25,
    val alertSingleUserDailyMinutes: Int = 120,
    val exemptUserIds: List<String> = emptyList(),
    val blockedUserIds: List<String> = emptyList()
)

interface CallBackendApi {
    @POST("api/calls/initiate")
    suspend fun initiateCall(
        @Header("Authorization") bearerToken: String,
        @Body body: InitiateCallRequestDto
    ): Response<InitiateCallResponseDto>

    @POST("api/calls/{callId}/accept")
    suspend fun acceptCall(
        @Header("Authorization") bearerToken: String,
        @Path("callId") callId: String
    ): Response<InitiateCallResponseDto>

    @POST("api/calls/{callId}/reject")
    suspend fun rejectCall(
        @Header("Authorization") bearerToken: String,
        @Path("callId") callId: String,
        @Body body: EndCallRequestDto
    ): Response<EndCallResponseDto>

    @POST("api/calls/{callId}/connection-state")
    suspend fun reportConnectionState(
        @Header("Authorization") bearerToken: String,
        @Path("callId") callId: String,
        @Body body: ConnectionStateRequestDto
    ): Response<Unit>

    @POST("api/calls/{callId}/reconnect-token")
    suspend fun getReconnectToken(
        @Header("Authorization") bearerToken: String,
        @Path("callId") callId: String
    ): Response<ReconnectTokenResponseDto>

    @POST("api/calls/{callId}/end")
    suspend fun endCall(
        @Header("Authorization") bearerToken: String,
        @Path("callId") callId: String,
        @Body body: EndCallRequestDto
    ): Response<EndCallResponseDto>

    @PUT("api/admin/calls/config")
    suspend fun updateGovernanceConfig(
        @Header("Authorization") bearerToken: String,
        @Body config: CallConfigDto
    ): Response<CallConfigDto>

    @GET("api/admin/calls/config")
    suspend fun getGovernanceConfig(
        @Header("Authorization") bearerToken: String
    ): Response<CallConfigDto>

    companion object {
        fun create(baseUrl: String): CallBackendApi {
            val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            // Never log Authorization headers or LiveKit tokens in OkHttp logs
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(CallBackendApi::class.java)
        }
    }
}
