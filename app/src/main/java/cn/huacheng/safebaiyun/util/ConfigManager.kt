package cn.huacheng.safebaiyun.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 统一管理所有可配置参数
 * 支持存储到 SharedPreferences，并支持恢复默认值
 */
object ConfigManager {

    private const val PREF_NAME = "app_config"
    private lateinit var prefs: SharedPreferences

    // ---------- 默认值（硬编码） ----------
    private const val DEFAULT_UNLOCK_TIMEOUT = 10000L      // 单次开锁超时：10秒
    private const val DEFAULT_POLL_INTERVAL = 500L         // 轮询间隔：0.5秒
    private const val DEFAULT_RESULT_DELAY = 1500L         // 结果展示延迟：1.5秒
    private const val DEFAULT_RESET_DELAY = 2000L          // 状态复位延迟：2秒

    // ---------- Key 定义 ----------
    private const val KEY_UNLOCK_TIMEOUT = "unlock_timeout"
    private const val KEY_POLL_INTERVAL = "poll_interval"
    private const val KEY_RESULT_DELAY = "result_delay"
    private const val KEY_RESET_DELAY = "reset_delay"

    /** 初始化，在 Application 或 MainActivity 中调用 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // ---------- Getter（优先使用用户自定义值，否则返回默认值） ----------

    fun getUnlockTimeout(): Long {
        return prefs.getLong(KEY_UNLOCK_TIMEOUT, DEFAULT_UNLOCK_TIMEOUT)
    }

    fun getPollInterval(): Long {
        return prefs.getLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
    }

    fun getResultDelay(): Long {
        return prefs.getLong(KEY_RESULT_DELAY, DEFAULT_RESULT_DELAY)
    }

    fun getResetDelay(): Long {
        return prefs.getLong(KEY_RESET_DELAY, DEFAULT_RESET_DELAY)
    }

    // ---------- Setter（保存用户自定义值） ----------

    fun setUnlockTimeout(value: Long) {
        prefs.edit().putLong(KEY_UNLOCK_TIMEOUT, value).apply()
    }

    fun setPollInterval(value: Long) {
        prefs.edit().putLong(KEY_POLL_INTERVAL, value).apply()
    }

    fun setResultDelay(value: Long) {
        prefs.edit().putLong(KEY_RESULT_DELAY, value).apply()
    }

    fun setResetDelay(value: Long) {
        prefs.edit().putLong(KEY_RESET_DELAY, value).apply()
    }

    // ---------- 获取当前所有配置（用于 UI 展示） ----------

    data class ConfigValues(
        val unlockTimeout: Long,
        val pollInterval: Long,
        val resultDelay: Long,
        val resetDelay: Long
    )

    fun getAllValues(): ConfigValues {
        return ConfigValues(
            unlockTimeout = getUnlockTimeout(),
            pollInterval = getPollInterval(),
            resultDelay = getResultDelay(),
            resetDelay = getResetDelay()
        )
    }

    // ---------- 恢复默认 ----------

    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_UNLOCK_TIMEOUT)
            .remove(KEY_POLL_INTERVAL)
            .remove(KEY_RESULT_DELAY)
            .remove(KEY_RESET_DELAY)
            .apply()
    }

    // ---------- 获取默认值（用于 UI 显示“默认值”标签） ----------

    fun getDefaultUnlockTimeout(): Long = DEFAULT_UNLOCK_TIMEOUT
    fun getDefaultPollInterval(): Long = DEFAULT_POLL_INTERVAL
    fun getDefaultResultDelay(): Long = DEFAULT_RESULT_DELAY
    fun getDefaultResetDelay(): Long = DEFAULT_RESET_DELAY
}
