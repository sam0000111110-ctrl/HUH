package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SecondaryProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SecondaryProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SecondaryProfile): Long

    @Query("SELECT * FROM secondary_profiles WHERE parentUserId = :parentUserId ORDER BY createdAt DESC")
    fun getProfilesForParent(parentUserId: Long): Flow<List<SecondaryProfile>>

    @Query("SELECT * FROM secondary_profiles WHERE parentUserId = :parentUserId ORDER BY createdAt DESC")
    suspend fun getAllProfilesForParentSync(parentUserId: Long): List<SecondaryProfile>

    @Query("SELECT * FROM secondary_profiles WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): SecondaryProfile?

    @Query("UPDATE secondary_profiles SET notificationsEnabled = :enabled WHERE id = :id")
    suspend fun updateNotificationSetting(id: Long, enabled: Boolean)

    @Query("UPDATE secondary_profiles SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, timestamp: Long)

    @Query("DELETE FROM secondary_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("DELETE FROM secondary_profiles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM secondary_profiles WHERE parentUserId = :parentUserId")
    suspend fun countProfiles(parentUserId: Long): Int
}
