package cn.huacheng.safebaiyun.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 轻量 Toast 工具。
 *
 * 不使用 GlobalScope/协程，避免为一次 Toast 创建额外协程任务。
 * Toast 使用应用 Context，不持有 Activity。
 */
private val mainHandler = Handler(Looper.getMainLooper())
private var currentToast: Toast? = null

fun showToast(msg: String) {
    if (Looper.myLooper() === Looper.getMainLooper()) {
        showToastOnMain(msg)
    } else {
        mainHandler.post { showToastOnMain(msg) }
    }
}

private fun showToastOnMain(msg: String) {
    currentToast?.cancel()
    currentToast = Toast.makeText(ContextHolder.get(), msg, Toast.LENGTH_SHORT).also { it.show() }
}
