package cn.huacheng.safebaiyun.util

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {

    private const val PREF_NAME = "app_config"
    private lateinit var prefs: SharedPreferences

    // 默认值
    private const val DEFAULT_UNLOCK_TIMEOUT = 10000L
    private const val DEFAULT_POLL_INTERVAL = 500L
    private const val DEFAULT_RESULT_DELAY = 1500L
    private const val DEFAULT_RESET_DELAY = 2000L
    private const val DEFAULT_AUTO_POLL = false
    private const val DEFAULT_POLL_WAIT_TIME = 3000L   // 新增：等待蓝牙开启的默认时间（3秒）

    // Keys
    private const val KEY_UNLOCK_TIMEOUT = "unlock_timeout"
    private const val KEY_POLL_INTERVAL = "poll_interval"
    private const val KEY_RESULT_DELAY = "result_delay"
    private const val KEY_RESET_DELAY = "reset_delay"
    private const val KEY_AUTO_POLL = "auto_poll"
    private const val KEY_POLL_WAIT_TIME = "poll_wait_time"   // 新增

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // ---------- Getter ----------
    fun getUnlockTimeout(): Long = prefs.getLong(KEY_UNLOCK_TIMEOUT, DEFAULT_UNLOCK_TIMEOUT)
    fun getPollInterval(): Long = prefs.getLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
    fun getResultDelay(): Long = prefs.getLong(KEY_RESULT_DELAY, DEFAULT_RESULT_DELAY)
    fun getResetDelay(): Long = prefs.getLong(KEY_RESET_DELAY, DEFAULT_RESET_DELAY)
    fun getAutoPollOnStart(): Boolean = prefs.getBoolean(KEY_AUTO_POLL, DEFAULT_AUTO_POLL)
    fun getPollWaitTime(): Long = prefs.getLong(KEY_POLL_WAIT_TIME, DEFAULT_POLL_WAIT_TIME)   // 新增

    // ---------- Setter ----------
    fun setUnlockTimeout(value: Long) { prefs.edit().putLong(KEY_UNLOCK_TIMEOUT, value).apply() }
    fun setPollInterval(value: Long) { prefs.edit().putLong(KEY_POLL_INTERVAL, value).apply() }
    fun setResultDelay(value: Long) { prefs.edit().putLong(KEY_RESULT_DELAY, value).apply() }
    fun setResetDelay(value: Long) { prefs.edit().putLong(KEY_RESET_DELAY, value).apply() }
    fun setAutoPollOnStart(value: Boolean) { prefs.edit().putBoolean(KEY_AUTO_POLL, value).apply() }
    fun setPollWaitTime(value: Long) { prefs.edit().putLong(KEY_POLL_WAIT_TIME, value).apply() }   // 新增

    // ---------- 获取所有配置 ----------
    data class ConfigValues(
        val unlockTimeout: Long,
        val pollInterval: Long,
        val resultDelay: Long,
        val resetDelay: Long,
        val autoPollOnStart: Boolean,
        val pollWaitTime: Long   // 新增
    )

    fun getAllValues(): ConfigValues = ConfigValues(
        unlockTimeout = getUnlockTimeout(),
        pollInterval = getPollInterval(),
        resultDelay = getResultDelay(),
        resetDelay = getResetDelay(),
        autoPollOnStart = getAutoPollOnStart(),
        pollWaitTime = getPollWaitTime()
    )

    // ---------- 恢复默认 ----------
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_UNLOCK_TIMEOUT)
            .remove(KEY_POLL_INTERVAL)
            .remove(KEY_RESULT_DELAY)
            .remove(KEY_RESET_DELAY)
            .remove(KEY_AUTO_POLL)
            .remove(KEY_POLL_WAIT_TIME)   // 新增
            .apply()
    }

    // ---------- 默认值 ----------
    fun getDefaultUnlockTimeout(): Long = DEFAULT_UNLOCK_TIMEOUT
    fun getDefaultPollInterval(): Long = DEFAULT_POLL_INTERVAL
    fun getDefaultResultDelay(): Long = DEFAULT_RESULT_DELAY
    fun getDefaultResetDelay(): Long = DEFAULT_RESET_DELAY
    fun getDefaultAutoPoll(): Boolean = DEFAULT_AUTO_POLL
    fun getDefaultPollWaitTime(): Long = DEFAULT_POLL_WAIT_TIME   // 新增
}
