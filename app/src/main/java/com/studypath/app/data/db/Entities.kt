package com.studypath.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 用户配置的大模型 API（OpenAI 兼容格式） */
@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val isDefault: Boolean = false,
)

/** 学习计划 */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goal: String,          // 用户原始学习需求
    val overview: String,      // AI 生成的总体说明
    val createdAt: Long = System.currentTimeMillis(),
    val configName: String = "", // 生成时使用的模型（仅展示）
)

/** 计划阶段 */
@Entity(
    tableName = "phases",
    foreignKeys = [ForeignKey(
        entity = PlanEntity::class, parentColumns = ["id"],
        childColumns = ["planId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId")],
)
data class PhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val title: String,
    val summary: String,
    val orderIndex: Int,
)

/**
 * 小任务：progress 取 0..100，支持"部分完成"。
 * 总进度 = Σ(estimatedMinutes × progress) / Σ(estimatedMinutes)，按预估时长加权。
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = PhaseEntity::class, parentColumns = ["id"],
        childColumns = ["phaseId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("phaseId"), Index("planId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phaseId: Long,
    val planId: Long,
    val title: String,
    val method: String,        // 学习方法建议
    val resource: String,      // 推荐学习资源
    val estimatedMinutes: Int,
    val progress: Int = 0,     // 0..100
    val orderIndex: Int,
)
