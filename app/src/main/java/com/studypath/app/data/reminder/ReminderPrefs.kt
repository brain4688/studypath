package com.studypath.app.data.reminder

import android.content.Context
import android.content.SharedPreferences

/** 提醒相关偏好（全局：开关 + 提醒时刻） */
object ReminderPrefs {
    private const val FILE = "studypath_settings"

    const val KEY_ENABLED = "notify_enabled"
    const val KEY_HOUR = "notify_hour"
    const val KEY_MINUTE = "notify_minute"
    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun time(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(KEY_HOUR, DEFAULT_HOUR) to p.getInt(KEY_MINUTE, DEFAULT_MINUTE)
    }

    fun save(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }
}
