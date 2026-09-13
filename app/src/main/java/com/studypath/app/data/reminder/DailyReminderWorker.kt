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

/** 每日学习提醒：到设定时刻检查今天的任务，有未完成的就发通知 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!ReminderPrefs.isEnabled(applicationContext)) return@withContext Result.success()
        val db = AppDatabase.get(applicationContext)
        val today = LocalDate.now().toEpochDay()
        val tasks = db.taskDao().getTodayUnfinished(today)
        if (tasks.isEmpty()) return@withContext Result.success()

        val planNames = tasks.mapNotNull { db.planDao().getById(it.planId)?.title }.distinct()
        val preview = tasks.take(3).joinToString("；") { it.title } +
            if (tasks.size > 3) " 等 ${tasks.size} 项" else ""
        val text = buildString {
            if (planNames.isNotEmpty()) append("「${planNames.joinToString("、")}」")
            append("今天有 ${tasks.size} 项未完成：")
            append(preview)
        }
        showNotification("今日学习提醒", text)
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
        runCatching { nm.notify(NOTIFY_ID, notification) }
    }

    companion object {
        const val CHANNEL_ID = "daily_plan_reminder"
        const val NOTIFY_ID = 1001

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

/** 用 WorkManager 做每日定时（无需精准闹钟权限，设备重启后自动恢复） */
object ReminderScheduler {
    private const val WORK_NAME = "daily_plan_reminder"

    /** 按 hour:minute 安排每日检查；先取消旧任务再以新时刻重排 */
    fun schedule(context: Context, hour: Int, minute: Int) {
        DailyReminderWorker.ensureChannel(context)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(Duration.ofHours(24))
            .setInitialDelay(Duration.between(now, next))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
