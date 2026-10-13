package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.huacheng.safebaiyun.compose.HelpView
import cn.huacheng.safebaiyun.compose.MainView
import cn.huacheng.safebaiyun.compose.ManageDoorsView
import cn.huacheng.safebaiyun.compose.QRExportView
import cn.huacheng.safebaiyun.compose.QRImportView
import cn.huacheng.safebaiyun.compose.SettingsView
import cn.huacheng.safebaiyun.compose.UnlockHistoryView
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.UnlockRecordManager
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus

/**
 * 一次待执行的开锁请求。
 *
 * @property doorId 目标门禁 ID：具体门禁 ID / [ShortcutActivity.ALL_DOORS_ID]（一键开锁）/ ""（开第一个门）
 * @property nonce  单调递增的请求序号。状态用 `mutableStateOf` 驱动，连续两次请求同一扇门时
 *                  doorId 相同、State 认为「值没变」不会重组；nonce 每次递增保证任何一次
 *                  新请求的状态值都不同，LaunchedEffect 的效果键随之变化，开锁必然重新触发。
 */
data class UnlockRequest(val doorId: String, val nonce: Long)

/**
 * 应用主界面，也是**唯一的开锁入口**。
 *
 * 桌面小部件与桌面快捷方式都不再弹独立提示，而是直接进这里：主界面照常显示，
 * 然后自动执行对应门禁的开锁，过程与用户手动点「开锁 / 一键开锁」完全一致。
 *
 * 开锁请求有五条送达路径，任何一条活着都必须能取出请求，但一次点击只执行一次
 * （[collectUnlockRequest] 取走即清空 + Intent 已消费标记）：
 *   1) 总线观察者     —— 请求写入 [WidgetUnlockBus] 的瞬间即回调（热启动主兜底）；
 *   2) [onCreate]     —— 冷启动 / Activity 被回收后重建；
 *   3) [onNewIntent]  —— App 已在运行时的标准热启动路径（singleTask）；
 *   4) [onResume]     —— 兜底：部分 ROM 丢 onNewIntent 时，任务回前台必触发；
 *   5) 快捷方式 Intent 本体 —— 见 [takeShortcutDoorId]。
 *
 * 关于桌面快捷方式的热启动：旧实现让快捷方式指向中转页 ShortcutActivity，App 已在
 * 运行时启动器的 Intent 带 NEW_TASK | RESET_TASK_IF_NEEDED，系统按「前门（front door）」
 * 语义把任务重置到根 Activity（本页），中转页不被创建、总线不被写入，个别 ROM 还会
 * 改写投递 Intent 的 component / 剥掉 action。因此：新快捷方式直接指向本 Activity
 * （见 ShortcutPin）；主界面读请求以 door_id extra 为主判定（[takeShortcutDoorId]）；
 * 中转页 + 总线 + 观察者保留，兼容已钉出去的旧快捷方式冷启动。
 */
class MainActivity : ComponentActivity() {

    /** 待执行的开锁请求（桌面小部件 / 桌面快捷方式携带的门禁 ID）；null 表示无请求 */
    private val pendingUnlock = mutableStateOf<UnlockRequest?>(null)

    /** 当前导航控制器：小部件在其它页面被点击时，需要先回到首页再展示开锁过程 */
    private var navController: NavHostController? = null

    /** 请求序号生成器，保证每一次请求的 [UnlockRequest.nonce] 都独一无二 */
    private var nonceSeq = 0L

    /**
     * 开锁请求观察者（见 [WidgetUnlockBus.addRequestListener]）。
     * 必须是字段而非 lambda 字面量：注册 / 注销按实例比对，只有持有同一稳定
     * 引用才能保证 onDestroy 时能正确移除，避免 Activity 泄漏。
     */
    private val unlockRequestListener: () -> Unit = { deliverUnlockRequest() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate ${intentSummary(intent)}")

        ConfigManager.init(this)
        UnlockRecordManager.init(this)

        // 注册总线观察者：此后任何时候（含热启动、主界面已在前台）收到新请求都会
        // 立即送达。冷启动时请求在下方已被取走，「取走即清空」保证不会重复执行。
        WidgetUnlockBus.addRequestListener(unlockRequestListener)

        // 先取「本次点击」的请求，没有再考虑 Activity 重建前遗留的请求；
        // 顺序不能反：重建往往正是由一次新的点击触发的，新请求才是最新的。
        val request = collectUnlockRequest() ?: takeRestoredRequest(savedInstanceState)
        pendingUnlock.value = request

        setContent {
            SafeBaiyunTheme {
                MainNavContent(
                    autoUnlockRequest = pendingUnlock.value,
                    onAutoUnlockConsumed = { pendingUnlock.value = null }
                )
            }
        }
    }

    override fun onDestroy() {
        WidgetUnlockBus.removeRequestListener(unlockRequestListener)
        super.onDestroy()
    }

    /**
     * App 已在运行（singleTask）时，小部件 / 快捷方式再次点击会把 Intent 送到这里，
     * 而不是 onCreate。桌面快捷方式的 Intent 还会被系统按「前门」语义直接根投递到
     * 这里（中转页被跳过、总线不会被写入），其 door_id extra 由 [collectUnlockRequest] 解析。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent ${intentSummary(intent)}")
        setIntent(intent)
        deliverUnlockRequest()
    }

    override fun onResume() {
        super.onResume()
        // 兜底：ColorOS 等 ROM 上可能不回调 onNewIntent，而主界面回前台必触发 onResume。
        // 主界面已在前台时该钩子不触发，那种场景由总线观察者兜底（见 unlockRequestListener），
        // 两者互补覆盖全部热启动形态。请求取走即清空，后续 onResume 不会重复触发。
        deliverUnlockRequest()
    }

    /** 取出待执行请求并派发给主界面；没有待处理请求时静默返回。 */
    private fun deliverUnlockRequest() {
        val request = collectUnlockRequest() ?: return

        // 若此刻停留在其它页面（设置、门禁管理等），先回到首页，
        // 这样用户才能看到和手动点击时一样的开锁过程。
        navController?.let { nav ->
            if (nav.currentDestination?.route != "main") {
                nav.popBackStack("main", inclusive = false)
            }
        }

        pendingUnlock.value = request
    }

    /**
     * 取出本次点击的开锁请求；两个来源都是「取走即清空」，因此 onCreate /
     * onNewIntent / onResume / 总线观察者无论谁先到，一次点击只会执行一次开锁。
     *
     * 来源 1 —— [WidgetUnlockBus]（SharedPreferences 持久化）：中转页 / 小部件广播
     *   在拉起主界面前就已 commit 落盘，覆盖冷启动与「中转页确实被执行」的热启动。
     * 来源 2 —— 快捷方式 Intent 本体（[takeShortcutDoorId]）：新快捷方式直接指向
     *   本 Activity；旧快捷方式热启动时中转页可能被 ROM 跳过、Intent 被根投递到这里。
     */
    private fun collectUnlockRequest(): UnlockRequest? {
        val busDoorId = WidgetUnlockBus.consume(this)
        if (busDoorId != null) {
            Log.d(TAG, "collect: 命中总线 doorId=$busDoorId")
            takeShortcutDoorId() // 总线已命中：取走并标记 Intent 上的副本，防止被再次读到
            return UnlockRequest(busDoorId, ++nonceSeq)
        }
        val shortcutDoorId = takeShortcutDoorId()
        Log.d(TAG, "collect: 总线无请求，快捷方式 Intent doorId=$shortcutDoorId")
        return shortcutDoorId?.let { UnlockRequest(it, ++nonceSeq) }
    }

    /**
     * 读取「桌面快捷方式」投递到主界面的门禁 ID，取走即清空。
     *
     * 覆盖两种投递形态：新快捷方式直接指向本 Activity（冷启动走 onCreate、热启动被
     * 根投递到 onNewIntent）；旧快捷方式指向 ShortcutActivity，热启动时中转页可能
     * 被 ROM 跳过、Intent 被根投递到这里。两种都靠 Intent 上的 door_id extra 识别。
     *
     * 判定以 door_id extra 为主、action/component 为辅：ROM 根投递时可能改写
     * component、剥掉 action，但 extra 会保留；正常桌面图标打开、中转页/小部件转发
     * 的 Intent 都不携带 door_id，不会误触发。
     *
     * 取走后打「已消费」标记而不是仅移除 extra：仅移除后 action/component 判定仍会
     * 命中，把缺失的 extra 当作 ""（开第一个门）重复触发；标记写在 Intent 对象上，
     * 旋转屏幕重建后同样不会重复。
     */
    private fun takeShortcutDoorId(): String? {
        val current = intent ?: return null
        if (current.getBooleanExtra(EXTRA_SHORTCUT_CONSUMED, false)) return null
        if (!isShortcutIntent(current)) return null
        val doorId = current.getStringExtra(ShortcutActivity.EXTRA_DOOR_ID) ?: ""
        current.removeExtra(ShortcutActivity.EXTRA_DOOR_ID)
        current.putExtra(EXTRA_SHORTCUT_CONSUMED, true)
        return doorId
    }

    /** 判断 Intent 是否来自桌面快捷方式（door_id extra 为主，action/component 为辅）。 */
    private fun isShortcutIntent(intent: Intent): Boolean =
        intent.hasExtra(ShortcutActivity.EXTRA_DOOR_ID) ||
                intent.action == ShortcutActivity.ACTION_PINNED_UNLOCK ||
                intent.component?.className == ShortcutActivity::class.java.name

    /** 诊断日志用：摘要当前 Intent 的 component / action / extras。 */
    private fun intentSummary(intent: Intent?): String {
        if (intent == null) return "intent=null"
        return "component=${intent.component?.className} action=${intent.action} extras=${intent.extras}"
    }

    /**
     * Activity 被系统回收后重建时，恢复「尚未执行完」的开锁请求。
     *
     * 用 `getString()` 是否为 null 判断有无请求，**不能用 containsKey()**：旧实现在
     * 请求消费后仍会 `putString(key, null)`，而 Bundle 允许存 null 值，containsKey()
     * 依旧返回 true，重建时会被误判为「已保存过 → 已处理」，直接跳过本次点击真正的
     * 请求。现在 [onSaveInstanceState] 只在真的有待执行请求时才写入，语义不再有歧义。
     */
    private fun takeRestoredRequest(savedInstanceState: Bundle?): UnlockRequest? {
        val doorId = savedInstanceState?.getString(STATE_PENDING_DOOR_ID) ?: return null
        return UnlockRequest(doorId, ++nonceSeq)
    }

    /**
     * 保存「待执行的开锁请求」，供 Activity 重建时恢复。
     * 无请求时不写该键：写 null 会让 key 存在但值为空，重建时被误判成「已处理」
     * 而丢弃本次点击（见 [takeRestoredRequest]）。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingUnlock.value?.let { outState.putString(STATE_PENDING_DOOR_ID, it.doorId) }
    }

    @Composable
    private fun MainNavContent(
        autoUnlockRequest: UnlockRequest?,
        onAutoUnlockConsumed: () -> Unit
    ) {
        val nav = rememberNavController()
        navController = nav

        NavHost(
            navController = nav,
            startDestination = "main",
        ) {
            composable(
                route = "main",
                enterTransition = { slideIn { IntOffset(-it.width, 0) } },
                exitTransition = { slideOut { IntOffset(-it.width, 0) } }
            ) {
                MainView(
                    navController = nav,
                    autoUnlockRequest = autoUnlockRequest,
                    onAutoUnlockConsumed = onAutoUnlockConsumed
                )
            }

            composable(
                route = "manage_doors",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                ManageDoorsView(
                    navController = nav,
                    onSaved = { }
                )
            }

            composable(
                route = "helper",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                HelpView(nav)
            }

            composable(
                route = "qr_export",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                QRExportView(nav)
            }

            composable(
                route = "qr_import",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                QRImportView(nav)
            }

            composable(
                route = "settings",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                SettingsView(nav)
            }

            composable(
                route = "unlock_history",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                UnlockHistoryView(nav)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * 门禁 ID 传递键，现仅用于桌面小部件 → WidgetClickReceiver 的**广播 extra**
         * （WidgetClickReceiver.EXTRA_DOOR_ID 即引用此常量）；取值为具体门禁 ID、
         * [ShortcutActivity.ALL_DOORS_ID]（一键开锁）或 ""（开第一个门）。
         * 主界面侧的开锁请求已统一走 [WidgetUnlockBus]，不再读取 Activity Intent extra。
         */
        const val EXTRA_WIDGET_DOOR_ID = "widget_door_id"

        /** 内部标记：当前 Intent 上的快捷方式请求已被消费（防旋转/重复 onResume 再次触发） */
        private const val EXTRA_SHORTCUT_CONSUMED = "__shortcut_consumed__"

        /** 实例状态键：保存「尚未执行完」的开锁请求门禁 ID；无请求时**不写**该键 */
        private const val STATE_PENDING_DOOR_ID = "pending_unlock_door_id"
    }
}
