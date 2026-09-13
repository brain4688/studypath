package com.studypath.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PhaseWithTasks(
    @Embedded val phase: PhaseEntity,
    @Relation(parentColumn = "id", entityColumn = "phaseId")
    val tasks: List<TaskEntity>,
)

/** 每个计划的加权进度聚合行 */
data class PlanProgressRow(
    val planId: Long,
    val weighted: Long,  // Σ(estimatedMinutes × progress)
    val total: Long,     // Σ(estimatedMinutes)
)

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<ApiConfigEntity>>

    @Query("SELECT * FROM api_configs WHERE isDefault = 1 LIMIT 1")
    fun observeDefault(): Flow<ApiConfigEntity?>

    @Query("SELECT * FROM api_configs WHERE id = :id")
    suspend fun getById(id: Long): ApiConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ApiConfigEntity): Long

    @Query("UPDATE api_configs SET isDefault = (id = :id)")
    suspend fun setDefault(id: Long)

    @Delete
    suspend fun delete(config: ApiConfigEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun observeByTask(taskId: Long): Flow<List<DeliveryEntity>>

    @Insert
    suspend fun insert(delivery: DeliveryEntity): Long

    @Query("DELETE FROM deliveries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id")
    fun observeById(id: Long): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: Long): PlanEntity?

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PhaseDao {
    @Insert
    suspend fun insert(phase: PhaseEntity): Long

    @Query("SELECT MAX(orderIndex) FROM phases WHERE planId = :planId")
    suspend fun maxOrderIndex(planId: Long): Int?

    @Transaction
    @Query("SELECT * FROM phases WHERE planId = :planId ORDER BY orderIndex ASC")
    fun observePhasesWithTasks(planId: Long): Flow<List<PhaseWithTasks>>

    @Query("DELETE FROM phases WHERE planId = :planId AND id NOT IN (SELECT phaseId FROM tasks)")
    suspend fun deleteEmptyPhases(planId: Long)
}

/** 今日任务页的聚合行：任务 + 所属计划名 + 所属阶段名 */
data class TodayTaskRow(
    @Embedded val task: TaskEntity,
    val planTitle: String,
    val phaseTitle: String,
)

@Dao
interface TaskDao {
    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET progress = :progress WHERE id = :taskId")
    suspend fun setProgress(taskId: Long, progress: Int)

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getByPlan(planId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE scheduledDate = :epochDay AND progress < 100 ORDER BY orderIndex ASC")
    suspend fun getTodayUnfinished(epochDay: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE scheduledDate = :epochDay AND progress < 100 AND planId = :planId ORDER BY orderIndex ASC")
    suspend fun getTodayUnfinishedByPlan(epochDay: Long, planId: Long): List<TaskEntity>

    @Query(
        "SELECT t.*, p.title AS planTitle, ph.title AS phaseTitle FROM tasks t " +
            "JOIN plans p ON t.planId = p.id " +
            "JOIN phases ph ON t.phaseId = ph.id " +
            "WHERE t.scheduledDate = :epochDay " +
            "ORDER BY t.progress ASC, t.planId ASC, ph.orderIndex ASC, t.orderIndex ASC"
    )
    fun observeByDay(epochDay: Long): Flow<List<TodayTaskRow>>

    @Query("DELETE FROM tasks WHERE planId = :planId AND progress < 100")
    suspend fun deleteUnfinished(planId: Long)

    @Query(
        "SELECT planId, SUM(estimatedMinutes * progress) AS weighted, SUM(estimatedMinutes) AS total " +
            "FROM tasks GROUP BY planId"
    )
    fun observePlanProgress(): Flow<List<PlanProgressRow>>
}
