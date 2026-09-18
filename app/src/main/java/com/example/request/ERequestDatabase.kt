package com.example.request

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServiceEntity::class,
        TransactionEntity::class,
        RequirementEntity::class,
        ProcedureStepEntity::class,
        DepartmentGuidelineEntity::class,
        AnnouncementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ERequestDatabase : RoomDatabase() {

    abstract fun eRequestDao(): ERequestDao

    companion object {
        @Volatile
        private var INSTANCE: ERequestDatabase? = null

        fun getDatabase(context: Context): ERequestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ERequestDatabase::class.java,
                    "erequest_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}