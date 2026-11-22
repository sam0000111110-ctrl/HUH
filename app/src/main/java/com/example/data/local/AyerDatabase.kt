package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.calls.CallAdminAlertEntity
import com.example.calls.CallGovernanceConfigEntity
import com.example.calls.CallRecordEntity
import com.example.data.model.AppNotification
import com.example.data.model.PointTransaction
import com.example.data.model.SecondaryProfile
import com.example.data.model.UserProfile

@Database(
    entities = [
        UserProfile::class,
        PointTransaction::class,
        AppNotification::class,
        SecondaryProfile::class,
        CallRecordEntity::class,
        CallGovernanceConfigEntity::class,
        CallAdminAlertEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AyerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun secondaryProfileDao(): SecondaryProfileDao
    abstract fun callDao(): CallDao

    companion object {
        @Volatile
        private var INSTANCE: AyerDatabase? = null

        fun getDatabase(context: Context): AyerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AyerDatabase::class.java,
                    "ayer_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
