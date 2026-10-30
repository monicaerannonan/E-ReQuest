package com.example.request.data.local

import com.example.request.R
import com.example.request.data.local.*
import com.example.request.data.sync.*
import com.example.request.features.checklist.*
import com.example.request.features.home.*
import com.example.request.features.notifications.*
import com.example.request.features.progress.*
import com.example.request.features.services.*
import com.example.request.features.splash.*
import com.example.request.features.transactions.*
import com.example.request.ui.custom.*

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServiceEntity::class,
        TransactionEntity::class,
        RequirementEntity::class,
        ProcedureStepEntity::class,
        DepartmentGuidelineEntity::class,
        AnnouncementEntity::class,
        UpdateNotificationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ERequestDatabase : RoomDatabase() {

    abstract fun eRequestDao(): ERequestDao

    companion object {

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE update_notifications ADD COLUMN isNotified INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: ERequestDatabase? = null

        fun getDatabase(
            context: Context
        ): ERequestDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        ERequestDatabase::class.java,
                        "erequest_database"
                    )
                        .addMigrations(MIGRATION_4_5)
                        .fallbackToDestructiveMigration()
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
