package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserProfile(id: Long = 1L): Flow<UserProfile?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserProfileSync(id: Long = 1L): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserProfile)

    @Update
    suspend fun updateUser(user: UserProfile)

    @Query("UPDATE users SET pointsBalance = pointsBalance + :delta, totalAccumulatedPoints = totalAccumulatedPoints + CASE WHEN :delta > 0 THEN :delta ELSE 0 END, totalPointsSpent = totalPointsSpent + CASE WHEN :delta < 0 THEN -:delta ELSE 0 END WHERE id = :id")
    suspend fun adjustPoints(delta: Int, id: Long = 1L)

    @Query("UPDATE users SET pointsBalance = pointsBalance + :coins, totalAccumulatedPoints = totalAccumulatedPoints + :coins, lastDailyClaimTime = :claimTime WHERE id = :id")
    suspend fun claimDailyPoints(coins: Int, claimTime: Long, id: Long = 1L)

    @Query("UPDATE users SET hostStatus = :status, isHost = :isHost WHERE id = :id")
    suspend fun updateHostStatus(status: String, isHost: Boolean, id: Long = 1L)

    @Query("UPDATE users SET hostEarningsCoins = hostEarningsCoins - :coins WHERE id = :id")
    suspend fun withdrawHostCoins(coins: Int, id: Long = 1L)

    @Query("UPDATE users SET isAgent = :isAgent WHERE id = :id")
    suspend fun updateAgentStatus(isAgent: Boolean, id: Long = 1L)

    @Query("UPDATE users SET agentCommissionPercent = :percent WHERE id = :id")
    suspend fun updateAgentCommission(percent: Int, id: Long = 1L)

    @Query("UPDATE users SET subAgentsCount = subAgentsCount + 1, subAgentsEarningsCoins = subAgentsEarningsCoins + :commissionBonus, totalAccumulatedPoints = totalAccumulatedPoints + :commissionBonus WHERE id = :id")
    suspend fun addSubAgentWithBonus(commissionBonus: Int = 250, id: Long = 1L)

    @Query("UPDATE users SET subAgentsEarningsCoins = subAgentsEarningsCoins + :coins, pointsBalance = pointsBalance + :coins, totalAccumulatedPoints = totalAccumulatedPoints + :coins WHERE id = :id")
    suspend fun addCommissionEarnings(coins: Int, id: Long = 1L)

    @Query("UPDATE users SET name = :name, bio = :bio, country = :country, phoneNumber = :phoneNumber, avatarResId = :avatarResId, avatarCustomUri = :avatarCustomUri WHERE id = :id")
    suspend fun updateAccountSettings(
        name: String,
        bio: String,
        country: String,
        phoneNumber: String,
        avatarResId: Int = com.example.R.drawable.img_user_avatar,
        avatarCustomUri: String? = null,
        id: Long = 1L
    )

    @Query("UPDATE users SET ageVerified = :ageVerified, ageVerificationStatus = :status, dob = :dob, age = :age, acceptedTermsAt = :acceptedTermsAt, acceptedPrivacyAt = :acceptedPrivacyAt WHERE id = :id")
    suspend fun updateAgeVerificationState(
        ageVerified: Boolean,
        status: String,
        dob: String?,
        age: Int,
        acceptedTermsAt: Long?,
        acceptedPrivacyAt: Long?,
        id: Long = 1L
    )

    @Query("DELETE FROM users")
    suspend fun clearAll()
}
