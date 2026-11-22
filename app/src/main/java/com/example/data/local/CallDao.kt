package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.calls.CallAdminAlertEntity
import com.example.calls.CallGovernanceConfigEntity
import com.example.calls.CallRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM call_records ORDER BY initiatedAtMs DESC")
    fun observeAllCallRecords(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records ORDER BY initiatedAtMs DESC")
    suspend fun getAllCallRecordsSync(): List<CallRecordEntity>

    @Query("SELECT * FROM call_records WHERE callId = :callId LIMIT 1")
    suspend fun getCallById(callId: String): CallRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallRecord(record: CallRecordEntity)

    @Query("SELECT * FROM call_governance_config WHERE id = 1 LIMIT 1")
    fun observeGovernanceConfig(): Flow<CallGovernanceConfigEntity?>

    @Query("SELECT * FROM call_governance_config WHERE id = 1 LIMIT 1")
    suspend fun getGovernanceConfigSync(): CallGovernanceConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGovernanceConfig(config: CallGovernanceConfigEntity)

    @Query("SELECT * FROM call_admin_alerts ORDER BY createdAtMs DESC")
    fun observeAlerts(): Flow<List<CallAdminAlertEntity>>

    @Query("SELECT * FROM call_admin_alerts ORDER BY createdAtMs DESC")
    suspend fun getAlertsSync(): List<CallAdminAlertEntity>

    @Query("SELECT COUNT(*) FROM call_admin_alerts WHERE dedupKey = :dedupKey")
    suspend fun countAlertsByDedupKey(dedupKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: CallAdminAlertEntity)

    @Query("UPDATE call_admin_alerts SET acknowledged = 1 WHERE id = :alertId")
    suspend fun acknowledgeAlert(alertId: String)

    @Query("DELETE FROM call_records")
    suspend fun clearAllCallRecords()
}
