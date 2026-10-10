package cn.huacheng.safebaiyun.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

@SuppressLint("StaticFieldLeak")
object ContextHolder {

    lateinit var context: Context

    fun init(context: Context) {
        ContextHolder.context = context.applicationContext
    }

    fun get() = context
}

private val mainHandler = Handler(Looper.getMainLooper())
private var currentToast: Toast? = null

/**
 * 轻量 Toast 工具（合并自 ToastUtil.kt）。
 *
 * 顶层函数，包外通过 `import cn.huacheng.safebaiyun.util.showToast` 使用。
 * 不使用协程，避免为一次 Toast 创建额外任务；使用应用 Context，不持有 Activity。
 */
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
