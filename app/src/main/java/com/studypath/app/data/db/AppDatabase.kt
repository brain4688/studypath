package com.studypath.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ApiConfigEntity::class, PlanEntity::class, PhaseEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun planDao(): PlanDao
    abstract fun phaseDao(): PhaseDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studypath.db",
                ).build().also { instance = it }
            }
    }
}
