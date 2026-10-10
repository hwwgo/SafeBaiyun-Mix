package cn.huacheng.safebaiyun.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import cn.huacheng.safebaiyun.MainActivity

/**
 * 桌面小部件点击的“广播桥”。
 *
 * 为什么需要它：
 * 通过桌面 PendingIntent 直接启动 Activity，在 ColorOS 等 ROM 的冷启动
 * （进程被杀、由系统重建任务）时，Intent 的 extra 会被系统丢弃，导致
 * Activity 的 onCreate 拿不到门禁 ID，只能走到主页。
 *
 * 广播的 extra 在冷启动是可靠送达的（不涉及 Activity 任务重建），
 * 因此本接收器在 App 进程里：
 *   1) 调用 WidgetUnlockBus.requestUnlock() 把门禁 ID 同步落盘（SharedPreferences + 内存）；
 *   2) 用普通的 startActivity 启动 MainActivity（进程已活，extra 不会被系统重建丢弃）。
 * MainActivity 冷/热启动都从 WidgetUnlockBus 读取，彻底绕开上面的坑。
 */
class WidgetClickReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetClickReceiver"

        /** 自定义广播 Action，配合 Manifest 中 receiver 的 intent-filter 使用 */
        const val ACTION = "cn.huacheng.safebaiyun.ACTION_WIDGET_UNLOCK"

        /** 与 MainActivity.EXTRA_WIDGET_DOOR_ID 同值，统一门禁 ID 的传递键 */
        const val EXTRA_DOOR_ID = MainActivity.EXTRA_WIDGET_DOOR_ID

        /** 显式组件名（不依赖 Context 构造） */
        fun componentName(): ComponentName = ComponentName(
            "cn.huacheng.safebaiyun",
            "cn.huacheng.safebaiyun.widget.WidgetClickReceiver"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1) 从广播 Intent 读取门禁 ID（冷启动可靠，广播 extra 不会被系统任务重建丢弃）
        val doorId = intent.getStringExtra(EXTRA_DOOR_ID) ?: ""

        Log.d(TAG, "onReceive doorId=$doorId")

        // 2) 同步落盘 + 内存，确保即便 ColorOS 冷启动重建进程，MainActivity 也能拿到
        WidgetUnlockBus.requestUnlock(context, doorId)

        // 3) 在当前（已被系统拉起的）App 进程里启动 MainActivity。
        //    普通 startActivity 的 extra 不会被系统重建任务时丢弃，
        //    作为 WidgetUnlockBus 之外的第二重保险。
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_DOOR_ID, doorId)
        }
        context.startActivity(launch)
    }
}
