package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.calls.CallAdminAlertEntity
import com.example.calls.CallGovernanceConfigEntity
import com.example.calls.CallRecordEntity
import com.example.data.model.AppNotification
import com.example.data.model.PointTransaction
import com.example.data.model.UserProfile

@Database(
    entities = [
        UserProfile::class,
        PointTransaction::class,
        AppNotification::class,
        CallRecordEntity::class,
        CallGovernanceConfigEntity::class,
        CallAdminAlertEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AyerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun callDao(): CallDao

    companion object {
        @Volatile
        private var INSTANCE: AyerDatabase? = null

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN ageVerificationStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE users ADD COLUMN dob TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN acceptedTermsAt INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN acceptedPrivacyAt INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN profileVisibility TEXT NOT NULL DEFAULT 'PUBLIC'")
                db.execSQL("ALTER TABLE users ADD COLUMN directMessagePermission TEXT NOT NULL DEFAULT 'EVERYONE'")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS secondary_profiles")
            }
        }

        fun getDatabase(context: Context): AyerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AyerDatabase::class.java,
                    "ayer_database"
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
