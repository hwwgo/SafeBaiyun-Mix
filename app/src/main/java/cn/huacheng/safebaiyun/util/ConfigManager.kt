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
    private const val KEY_UNLOCK_VIBRATION = "unlock_vibration"
    private const val KEY_UNLOCK_VIBRATION_DURATION = "unlock_vibration_duration"
    private const val KEY_UNLOCK_RECORD = "unlock_record"

    // 默认值
    private const val DEFAULT_UNLOCK_TIMEOUT = 2000L
    private const val DEFAULT_POLL_INTERVAL = 100L   // 门禁切换间隔（探测/轮询共用）
    private const val DEFAULT_RESULT_DELAY = 1000L
    private const val DEFAULT_RESET_DELAY = 1000L
    private const val DEFAULT_AUTO_POLL = false
    private const val DEFAULT_AUTO_SCAN = true
    private const val DEFAULT_SCAN_DURATION = 2000L   // 单个门禁探测时长
    private const val DEFAULT_POLL_WAIT_TIME = 5000L
    private const val DEFAULT_LARGE_FONT = false
    private const val DEFAULT_UNLOCK_VIBRATION = true
    private const val DEFAULT_UNLOCK_VIBRATION_DURATION = 150L
    private const val DEFAULT_UNLOCK_RECORD = true

    fun init(context: Context) {
        prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    }

    // ---------- 解锁超时 ----------
    fun getUnlockTimeout(): Long = prefs.getLong(KEY_UNLOCK_TIMEOUT, DEFAULT_UNLOCK_TIMEOUT)
    fun setUnlockTimeout(value: Long) = prefs.edit().putLong(KEY_UNLOCK_TIMEOUT, value).apply()
    fun getDefaultUnlockTimeout(): Long = DEFAULT_UNLOCK_TIMEOUT

    // ---------- 门禁切换间隔（探测/轮询共用） ----------
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

    // ---------- 开锁成功震动 ----------
    fun isUnlockVibrationEnabled(): Boolean = prefs.getBoolean(KEY_UNLOCK_VIBRATION, DEFAULT_UNLOCK_VIBRATION)
    fun setUnlockVibrationEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_UNLOCK_VIBRATION, value).apply()
    fun getDefaultUnlockVibrationEnabled(): Boolean = DEFAULT_UNLOCK_VIBRATION

    fun getUnlockVibrationDuration(): Long = prefs.getLong(KEY_UNLOCK_VIBRATION_DURATION, DEFAULT_UNLOCK_VIBRATION_DURATION)
    fun setUnlockVibrationDuration(value: Long) = prefs.edit().putLong(KEY_UNLOCK_VIBRATION_DURATION, value).apply()
    fun getDefaultUnlockVibrationDuration(): Long = DEFAULT_UNLOCK_VIBRATION_DURATION

    // ---------- 开锁记录 ----------
    fun isUnlockRecordEnabled(): Boolean = prefs.getBoolean(KEY_UNLOCK_RECORD, DEFAULT_UNLOCK_RECORD)
    fun setUnlockRecordEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_UNLOCK_RECORD, value).apply()
    fun getDefaultUnlockRecordEnabled(): Boolean = DEFAULT_UNLOCK_RECORD

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
            .putBoolean(KEY_UNLOCK_VIBRATION, DEFAULT_UNLOCK_VIBRATION)
            .putLong(KEY_UNLOCK_VIBRATION_DURATION, DEFAULT_UNLOCK_VIBRATION_DURATION)
            .putBoolean(KEY_UNLOCK_RECORD, DEFAULT_UNLOCK_RECORD)
            .apply()
    }
}
