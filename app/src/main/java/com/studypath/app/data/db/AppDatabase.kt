package com.studypath.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ApiConfigEntity::class, PlanEntity::class, PhaseEntity::class,
        TaskEntity::class, ChatMessageEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun planDao(): PlanDao
    abstract fun phaseDao(): PhaseDao
    abstract fun taskDao(): TaskDao
    abstract fun chatDao(): ChatDao

    companion object {
        /** v2：新增 chat_messages 表（AI 规划师聊天记录） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL)"""
                )
            }
        }

        /** v3：任务表增加细化字段（学什么/交付物/达标标准/常见坑） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `detail` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deliverable` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `checkpoint` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `pitfall` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4：任务表增加计划日期（epoch day，-1 未排期） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledDate` INTEGER NOT NULL DEFAULT -1")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studypath.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
