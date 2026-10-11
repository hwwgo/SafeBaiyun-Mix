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
 * 所以这里**不显示任何提示/弹窗**，只做三件事：
 *   1) 读取快捷方式携带的门禁 ID；
 *   2) 落盘到 [WidgetUnlockBus]（ColorOS 冷启动会丢 Intent extra，落盘才可靠）；
 *   3) 立即拉起 [MainActivity] 并 finish 自己。
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

        // 落盘，保证 MainActivity 冷启动也能拿到本次请求
        WidgetUnlockBus.requestUnlock(this, doorId)

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(MainActivity.EXTRA_WIDGET_DOOR_ID, doorId)
            }
        )

        finish()
        // 去掉转场动画，避免中转窗口闪一下
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
