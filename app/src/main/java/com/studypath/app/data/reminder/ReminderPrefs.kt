package com.studypath.app.data.reminder

import android.content.Context
import android.content.SharedPreferences

/** 按计划独立的提醒偏好：每个学习计划可单独开关与设置时刻 */
object ReminderPrefs {
    private const val FILE = "studypath_settings"

    private fun enabledKey(planId: Long) = "notify_enabled_$planId"
    private fun hourKey(planId: Long) = "notify_hour_$planId"
    private fun minuteKey(planId: Long) = "notify_minute_$planId"

    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, planId: Long): Boolean =
        prefs(context).getBoolean(enabledKey(planId), false)

    fun time(context: Context, planId: Long): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(hourKey(planId), DEFAULT_HOUR) to p.getInt(minuteKey(planId), DEFAULT_MINUTE)
    }

    fun save(context: Context, planId: Long, enabled: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(enabledKey(planId), enabled)
            .putInt(hourKey(planId), hour)
            .putInt(minuteKey(planId), minute)
            .apply()
    }
}
