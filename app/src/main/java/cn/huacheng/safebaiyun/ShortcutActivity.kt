package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import cn.huacheng.safebaiyun.widget.WidgetClickReceiver

/**
 * 快捷开门「中转」Activity（无任何界面）。
 *
 * 目标：桌面快捷方式点一下，效果与用户自己打开 App、再点对应门禁的「开锁」
 * 完全一致 —— 直接进主界面、在主界面里显示开锁过程，开完主界面依然在。
 * 这里**不显示任何提示/弹窗**，只做两件事：
 *   1) 读取快捷方式携带的门禁 ID；
 *   2) 转发给 [WidgetClickReceiver]（发一条广播），由它落盘并拉起 MainActivity。
 *
 * 为什么转发而不是直接 start MainActivity：
 *   之前直接 start 在 ColorOS 上「热启动」（App 已在前台/后台运行）时，由于本 Activity
 *   与 MainActivity 分属不同 task 且随后 finish()，onNewIntent 经常收不到、无法触发开锁，
 *   表现为「纯打开 App 界面、不解锁」。而桌面小部件走的正好是
 *   「广播 → WidgetClickReceiver → 拉起 MainActivity」这条路，冷/热启动都已验证稳定，
 *   因此快捷方式复用同一路径即可彻底规避该问题，行为与小部件完全一致。
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

        // 复用桌面小部件已验证稳定（冷热启动均可）的路径：
        // 发广播给 WidgetClickReceiver，由它落盘 WidgetUnlockBus 并拉起 MainActivity。
        // 采用显式组件广播，绕过 intent-filter 的 data 匹配，确保一定送达。
        val broadcast = Intent(WidgetClickReceiver.ACTION).apply {
            component = WidgetClickReceiver.componentName()
            putExtra(WidgetClickReceiver.EXTRA_DOOR_ID, doorId)
        }
        sendBroadcast(broadcast)

        finish()
        // 去掉转场动画，避免中转窗口闪一下
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
