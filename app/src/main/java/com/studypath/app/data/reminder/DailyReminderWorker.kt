package com.studypath.app.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.studypath.app.MainActivity
import com.studypath.app.R
import com.studypath.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** 每日学习提醒：到设定时刻检查某个计划当天的任务，有未完成的就发通知 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val planId = inputData.getLong(KEY_PLAN_ID, -1L)
        if (planId <= 0) return@withContext Result.success()
        if (!ReminderPrefs.isGlobalEnabled(applicationContext)) return@withContext Result.success()
        if (!ReminderPrefs.isEnabled(applicationContext, planId)) return@withContext Result.success()

        val db = AppDatabase.get(applicationContext)
        val plan = db.planDao().getById(planId) ?: return@withContext Result.success()
        val today = LocalDate.now().toEpochDay()
        val tasks = db.taskDao().getTodayUnfinishedByPlan(today, planId)
        if (tasks.isEmpty()) return@withContext Result.success()

        val preview = tasks.take(3).joinToString("；") { it.title } +
            if (tasks.size > 3) " 等 ${tasks.size} 项" else ""
        val text = "「${plan.title}」今天有 ${tasks.size} 项未完成：$preview"
        showNotification("今日学习提醒 · ${plan.title.take(12)}", text)
        Result.success()
    }

    private fun showNotification(title: String, text: String) {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFY_ID_BASE + (inputData.getLong(KEY_PLAN_ID, 0) % 500).toInt(), notification) }
    }

    companion object {
        const val CHANNEL_ID = "daily_plan_reminder"
        const val NOTIFY_ID_BASE = 1000
        const val KEY_PLAN_ID = "plan_id"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "每日学习提醒",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "按计划日期提醒你今天该学的内容" }
                )
            }
        }
    }
}

/** 每个计划独立的每日定时提醒（WorkManager 周期任务，重启自动恢复） */
object ReminderScheduler {
    private fun workName(planId: Long) = "daily_plan_reminder_$planId"

    /** 为某个计划按 hour:minute 安排每日检查 */
    fun schedule(context: Context, planId: Long, hour: Int, minute: Int) {
        DailyReminderWorker.ensureChannel(context)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(Duration.ofHours(24))
            .setInitialDelay(Duration.between(now, next))
            .setInputData(workDataOf(DailyReminderWorker.KEY_PLAN_ID to planId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(planId), ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }

    fun cancel(context: Context, planId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(planId))
    }
}

/** 日期显示：今天/明天/M月d日 · 周X */
fun describeDay(epochDay: Long, today: Long = LocalDate.now().toEpochDay()): String? {
    if (epochDay < 0) return null
    val d = LocalDate.ofEpochDay(epochDay)
    val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
    return when (epochDay) {
        today -> "今天 · $dow"
        today + 1 -> "明天 · $dow"
        else -> "${d.monthValue}月${d.dayOfMonth}日 · $dow"
    }
}
