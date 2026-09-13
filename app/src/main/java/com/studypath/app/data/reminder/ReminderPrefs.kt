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

    // ---- 全局设置（我的-设置）：通知总开关 + 新计划默认提醒时间 ----

    private const val KEY_GLOBAL_ENABLED = "notify_global_enabled"
    private const val KEY_DEFAULT_HOUR = "notify_default_hour"
    private const val KEY_DEFAULT_MINUTE = "notify_default_minute"

    fun isGlobalEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLOBAL_ENABLED, true)

    fun saveGlobalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
    }

    fun defaultTime(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(KEY_DEFAULT_HOUR, DEFAULT_HOUR) to p.getInt(KEY_DEFAULT_MINUTE, DEFAULT_MINUTE)
    }

    fun saveDefaultTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_DEFAULT_HOUR, hour).putInt(KEY_DEFAULT_MINUTE, minute).apply()
    }

    // ---- 用户资料（我的） ----

    private const val KEY_USERNAME = "profile_username"
    private const val KEY_AVATAR = "profile_avatar"

    fun username(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "学习者") ?: "学习者"

    fun saveUsername(context: Context, name: String) {
        prefs(context).edit().putString(KEY_USERNAME, name).apply()
    }

    fun avatarPath(context: Context): String? = prefs(context).getString(KEY_AVATAR, null)

    fun saveAvatar(context: Context, path: String?) {
        prefs(context).edit().putString(KEY_AVATAR, path).apply()
    }
}
