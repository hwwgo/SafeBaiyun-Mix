package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus

/**
 * 快捷开门「中转」Activity（无任何界面）。
 *
 * 目标：桌面快捷方式点一下，效果与用户自己打开 App、再点对应门禁的「开锁」
 * 完全一致 —— 直接进主界面、在主界面里显示开锁过程，开完主界面依然在。
 * 这里**不显示任何提示/弹窗**，只做三件事：
 *   1) 读取快捷方式携带的门禁 ID；
 *   2) 先把门禁 ID 落盘到 [WidgetUnlockBus]（ColorOS 冷启动可能丢弃 Intent
 *      extra，落盘才是最可靠的来源，且必须在拉起主界面前完成）；
 *   3) **在本 Activity 还活着时**直接拉起 [MainActivity]，把 extra 一并带上。
 *
 * 为什么必须是「直接拉起」而不是「发广播让 WidgetClickReceiver 拉起」：
 *   冷启动（进程不在、Recents 无任务）时，本中转页 finish() 之后 App 进程内
 *   没有任何 Activity，也不在小部件的 PendingIntent 临时白名单里，此时任何
 *   组件再 startActivity 都会撞上 Android 10+ 的后台活动启动限制（BAL），
 *   ColorOS 直接静默拦截 —— 表现为「点了没反应 / 闪退」。
 *   而本 Activity 在 onCreate 里就是前台任务栈顶，此刻 startActivity 永远
 *   被 BAL 放行，这正是修复冷启动失效的关键。
 *   （广播路径保留给桌面小部件：小部件点击走 PendingIntent，系统会给创建方
 *     App 临时白名单，冷启动同样可靠，两条路径互不影响。）
 *
 * 为什么不再直接 startActivity 也不会丢开锁请求（历史上的 ColorOS 坑）：
 *   · 冷启动：MainActivity.onCreate 先消费 [WidgetUnlockBus] 再读 extra；
 *   · 热启动：singleTask 走 onNewIntent，同样先消费 bus；
 *   · 个别 ROM 丢 onNewIntent 时：任务回到前台会触发 onResume，其兜底逻辑
 *     再次消费 bus。三层兜底保证请求不丢、不重复。
 *
 * 主题为 Theme.NoDisplay（窗口不入屏）+ 无转场动画，因此用户看不到它的存在。
 *
 * 门禁 ID 取值：具体门禁 id / [ALL_DOORS_ID]（一键开锁）/ ""（开第一个门）。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        /** Intent extra：快捷方式携带的目标门禁 ID */
        const val EXTRA_DOOR_ID = "door_id"

        /** 一键开锁（等价于主界面上的「一键开锁」按钮） */
        const val ALL_DOORS_ID = "__ALL_DOORS__"

        /** App 内钉到桌面的快捷方式所携带的 action */
        const val ACTION_PINNED_UNLOCK = "cn.huacheng.safebaiyun.action.PINNED_UNLOCK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val doorId = intent.getStringExtra(EXTRA_DOOR_ID) ?: ""

        // 1) 先把开锁请求落盘（commit() 同步写，确保拉起主界面前已持久化）。
        //    ColorOS 冷启动偶发丢弃 Intent extra，bus 是可靠来源；
        //    热启动时 onNewIntent / onResume 也会优先消费 bus。
        WidgetUnlockBus.requestUnlock(this, doorId)

        // 2) 趁本中转页仍是前台 Activity，直接拉起主界面。
        //    若改成「先 finish 再让广播接收器拉起」，冷启动时 App 已无前台
        //    Activity 且无 Recents 任务，startActivity 会被系统后台启动限制
        //    （BAL）静默拦截，导致点了快捷方式没有任何反应。
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_DOOR_ID, doorId)
        }
        startActivity(launch)

        finish()
        // 去掉转场动画，避免中转窗口闪一下
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
