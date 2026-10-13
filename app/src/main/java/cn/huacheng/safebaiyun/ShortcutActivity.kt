package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus

/**
 * 快捷开门「中转」Activity（无任何界面，Theme.NoDisplay + 无转场动画，用户看不到它）。
 *
 * 现状定位：**新钉的快捷方式已直接指向 MainActivity**（见 ShortcutPin），本页只为
 * 兼容历史上已经钉在桌面上的旧快捷方式（其冷启动仍走「中转页 + 总线」）。
 *
 * 旧快捷方式冷启动时只做两件事：
 *   1) 把快捷方式携带的门禁 ID 落盘到 [WidgetUnlockBus]（commit() 同步写，且必须在
 *      拉起主界面前完成：ColorOS 冷启动可能丢弃 Intent extra，落盘才是最可靠的来源，
 *      写入瞬间还会通知已注册的总线观察者，热启动因此不依赖任何生命周期回调）；
 *   2) 趁本 Activity 还是前台任务栈顶，直接拉起 [MainActivity]——冷启动时进程内没有
 *      其它 Activity，若先 finish 再让其它组件 startActivity，会撞上 Android 10+ 的
 *      后台活动启动限制（BAL）被 ColorOS 静默拦截；而在本页 onCreate 里启动永远被放行。
 *
 * 请求只经总线送达，总线「取走即清空」：onNewIntent / onResume 作为双保险再取一次
 * 也不会重复开锁。
 *
 * 边界（热启动）：App 已在运行时，快捷方式 Intent 带 FLAG_ACTIVITY_NEW_TASK |
 * FLAG_ACTIVITY_RESET_TASK_IF_NEEDED，系统按「前门（front door）」语义把任务重置到
 * 根 Activity，该 Intent 被直接投递给 MainActivity.onNewIntent——本页根本不会被创建、
 * 总线也不会被写入；那种场景由 MainActivity 解析 Intent 上的 door_id extra 完成开锁
 * （见 MainActivity.takeShortcutDoorId）。冷启动没有已存在的任务，本页正常执行。
 *
 * 门禁 ID 取值：具体门禁 id / [ALL_DOORS_ID]（一键开锁）/ ""（开第一个门）。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShortcutActivity"

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
        Log.d(TAG, "onCreate action=${intent.action} doorId=$doorId extras=${intent.extras}")

        // 1) 先把开锁请求落盘（commit() 同步写，确保拉起主界面前已持久化；
        //    写入瞬间即通知已注册的总线观察者，热启动不依赖生命周期兜底）。
        WidgetUnlockBus.requestUnlock(this, doorId)

        // 2) 趁本中转页仍是前台 Activity，直接拉起主界面（BAL 放行，见类注释）。
        //    门禁 ID 只经总线传递，不再往 Intent 上放 extra（冗余且会导致重复开锁）。
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launch)

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0) // 去掉转场动画，避免中转窗口闪一下
    }
}
