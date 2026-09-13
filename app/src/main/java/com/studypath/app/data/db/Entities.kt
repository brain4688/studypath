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

/** 与 AI 规划师的聊天记录 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,          // "user" / "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 任务交付记录：文字与图片（学习成果凭证） */
@Entity(
    tableName = "deliveries",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"],
        childColumns = ["taskId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class DeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val planId: Long,
    val text: String = "",
    /** 应用私有目录内的图片文件名，可空 */
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 任务执行教练的历史对话：按任务归属，跨多次执行持续保存 */
@Entity(
    tableName = "coach_messages",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class, parentColumns = ["id"],
        childColumns = ["taskId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class CoachMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val role: String,          // "user" / "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
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
    val detail: String = "",      // 具体学什么/做什么（知识点、题目范围）
    val method: String = "",      // 学习方法/执行步骤
    val deliverable: String = "", // 交付物
    val checkpoint: String = "",  // 达标标准
    val pitfall: String = "",     // 常见坑
    val resource: String = "",    // 推荐学习资源
    val estimatedMinutes: Int,
    /** 计划完成日期（epoch day）；-1 表示未排期 */
    val scheduledDate: Long = -1L,
    val progress: Int = 0,     // 0..100
    val orderIndex: Int,
)
