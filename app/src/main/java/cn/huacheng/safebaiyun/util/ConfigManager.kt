package cn.huacheng.safebaiyun.util

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {

    private lateinit var prefs: SharedPreferences

    private const val KEY_UNLOCK_TIMEOUT = "unlock_timeout"
    private const val KEY_POLL_INTERVAL = "poll_interval"
    private const val KEY_RESULT_DELAY = "result_delay"
    private const val KEY_RESET_DELAY = "reset_delay"
    private const val KEY_AUTO_POLL = "auto_poll"
    private const val KEY_AUTO_SCAN = "auto_scan"
    private const val KEY_SCAN_DURATION = "scan_duration"
    private const val KEY_POLL_WAIT_TIME = "poll_wait_time"
    private const val KEY_LARGE_FONT = "large_font"

    // 默认值
    private const val DEFAULT_UNLOCK_TIMEOUT = 10000L
    private const val DEFAULT_POLL_INTERVAL = 300L
    private const val DEFAULT_RESULT_DELAY = 2000L
    private const val DEFAULT_RESET_DELAY = 1000L
    private const val DEFAULT_AUTO_POLL = false
    private const val DEFAULT_AUTO_SCAN = true
    private const val DEFAULT_SCAN_DURATION = 1500L   // 单个门禁探测时长
    private const val DEFAULT_POLL_WAIT_TIME = 5000L
    private const val DEFAULT_LARGE_FONT = false

    fun init(context: Context) {
        prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    }

    // ---------- 解锁超时 ----------
    fun getUnlockTimeout(): Long = prefs.getLong(KEY_UNLOCK_TIMEOUT, DEFAULT_UNLOCK_TIMEOUT)
    fun setUnlockTimeout(value: Long) = prefs.edit().putLong(KEY_UNLOCK_TIMEOUT, value).apply()
    fun getDefaultUnlockTimeout(): Long = DEFAULT_UNLOCK_TIMEOUT

    // ---------- 轮询间隔 ----------
    fun getPollInterval(): Long = prefs.getLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
    fun setPollInterval(value: Long) = prefs.edit().putLong(KEY_POLL_INTERVAL, value).apply()
    fun getDefaultPollInterval(): Long = DEFAULT_POLL_INTERVAL

    // ---------- 结果展示延迟 ----------
    fun getResultDelay(): Long = prefs.getLong(KEY_RESULT_DELAY, DEFAULT_RESULT_DELAY)
    fun setResultDelay(value: Long) = prefs.edit().putLong(KEY_RESULT_DELAY, value).apply()
    fun getDefaultResultDelay(): Long = DEFAULT_RESULT_DELAY

    // ---------- 状态复位延迟 ----------
    fun getResetDelay(): Long = prefs.getLong(KEY_RESET_DELAY, DEFAULT_RESET_DELAY)
    fun setResetDelay(value: Long) = prefs.edit().putLong(KEY_RESET_DELAY, value).apply()
    fun getDefaultResetDelay(): Long = DEFAULT_RESET_DELAY

    // ---------- 自动轮询 ----------
    fun getAutoPollOnStart(): Boolean = prefs.getBoolean(KEY_AUTO_POLL, DEFAULT_AUTO_POLL)
    fun setAutoPollOnStart(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_POLL, value).apply()
    fun getDefaultAutoPoll(): Boolean = DEFAULT_AUTO_POLL

    // ---------- 自动扫描门禁 ----------
    fun getAutoScanEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SCAN, DEFAULT_AUTO_SCAN)
    fun setAutoScanEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()
    fun getDefaultAutoScanEnabled(): Boolean = DEFAULT_AUTO_SCAN

    // ---------- 单个门禁探测时长 ----------
    fun getScanDuration(): Long = prefs.getLong(KEY_SCAN_DURATION, DEFAULT_SCAN_DURATION)
    fun setScanDuration(value: Long) = prefs.edit().putLong(KEY_SCAN_DURATION, value).apply()
    fun getDefaultScanDuration(): Long = DEFAULT_SCAN_DURATION

    // ---------- 轮询等待时间 ----------
    fun getPollWaitTime(): Long = prefs.getLong(KEY_POLL_WAIT_TIME, DEFAULT_POLL_WAIT_TIME)
    fun setPollWaitTime(value: Long) = prefs.edit().putLong(KEY_POLL_WAIT_TIME, value).apply()
    fun getDefaultPollWaitTime(): Long = DEFAULT_POLL_WAIT_TIME

    // ---------- 大字体模式 ----------
    fun isLargeFont(): Boolean = prefs.getBoolean(KEY_LARGE_FONT, DEFAULT_LARGE_FONT)
    fun setLargeFont(value: Boolean) = prefs.edit().putBoolean(KEY_LARGE_FONT, value).apply()
    fun getDefaultLargeFont(): Boolean = DEFAULT_LARGE_FONT

    // ---------- 恢复默认 ----------
    fun resetToDefaults() {
        prefs.edit()
            .putLong(KEY_UNLOCK_TIMEOUT, DEFAULT_UNLOCK_TIMEOUT)
            .putLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
            .putLong(KEY_RESULT_DELAY, DEFAULT_RESULT_DELAY)
            .putLong(KEY_RESET_DELAY, DEFAULT_RESET_DELAY)
            .putBoolean(KEY_AUTO_POLL, DEFAULT_AUTO_POLL)
            .putBoolean(KEY_AUTO_SCAN, DEFAULT_AUTO_SCAN)
            .putLong(KEY_SCAN_DURATION, DEFAULT_SCAN_DURATION)
            .putLong(KEY_POLL_WAIT_TIME, DEFAULT_POLL_WAIT_TIME)
            .putBoolean(KEY_LARGE_FONT, DEFAULT_LARGE_FONT)
            .apply()
    }
}
