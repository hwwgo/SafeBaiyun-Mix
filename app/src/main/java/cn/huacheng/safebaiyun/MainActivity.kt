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
 * @property nonce  单调递增的请求序号。
 *
 * 为什么需要 [nonce]：状态用的是 `mutableStateOf`，连续两次点击同一扇门时 doorId 完全相同，
 * State 认为「值没变」→ 不会重组 → 观察该值的 LaunchedEffect 不会重新执行，
 * 表现为「第二次点快捷方式只弹出主界面、不开锁」。加上每次都递增的 nonce 后，
 * 任何一次新请求的状态值都一定不同，效果键（key）随之变化，开锁必然重新触发。
 */
data class UnlockRequest(val doorId: String, val nonce: Long)

/**
 * 应用主界面，也是**唯一的开锁入口**。
 *
 * 桌面小部件与桌面快捷方式都不再弹独立提示，而是直接进这里：
 * 主界面照常显示，然后自动执行对应门禁的开锁，过程与用户手动点
 * 「开锁 / 一键开锁」按钮完全一致；开锁结束后主界面保持不动（不自动关闭）。
 *
 * 请求送达主界面共有五条路径，任何一条活着都必须能把请求取出来执行，
 * 但整次点击只能被执行一次（[collectUnlockRequest] 取走即清空 + 已消费标记）：
 *   1) 总线观察者     —— 请求写入 [WidgetUnlockBus] 的瞬间即回调；
 *   2) [onCreate]     —— 冷启动 / Activity 被回收后重建；
 *   3) [onNewIntent]  —— App 已在运行时的标准热启动路径（singleTask）；
 *   4) [onResume]     —— 兜底：部分 ROM 丢 onNewIntent 时，任务回前台必触发；
 *   5) 快捷方式 Intent 本体 —— 见 [takeShortcutDoorId]。
 *
 * 关于快捷方式的热启动（历史上两次都没修对，最终定位）：
 * 旧实现让桌面快捷方式指向中转页 ShortcutActivity，热启动时这条链整条断掉——
 * App 已在运行时，启动器发出的 Intent 带 NEW_TASK | RESET_TASK_IF_NEEDED，系统按
 * 「前门（front door）」语义把任务重置到根 Activity（本 Activity），中转页不被创建、
 * 总线不被写入；个别 ROM 还会改写投递 Intent 的 component / 剥掉 action。
 * 现在的做法是两头堵：
 *   · 新快捷方式直接指向本 Activity（ShortcutPin 现行实现），冷启动走 onCreate、
 *     热启动走 onNewIntent，全是系统标准行为；
 *   · 主界面读请求**以 door_id extra 为主**判定（[takeShortcutDoorId]），ROM 怎么
 *     改写 component/action 都不怕，extra 在各 ROM 上都会保留；
 *   · 中转页 + 总线 + 观察者保留，兼容已钉出去的旧快捷方式（冷启动仍走它们）。
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
     *
     * 必须是字段而非 lambda 字面量：注册 / 注销按实例比对，只有持有同一稳定
     * 引用才能保证 onDestroy 时能正确移除，避免 Activity 泄漏。
     */
    private val unlockRequestListener: () -> Unit = { deliverUnlockRequest() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate ${intentSummary(intent)}")

        ConfigManager.init(this)
        UnlockRecordManager.init(this)

        // 注册开锁请求观察者：此后任何时候（含热启动、主界面已在前台）收到新请求
        // 都会立即送达主界面。冷启动时请求在下方已被取走，consume 的「取走即清空」
        // 语义保证观察者不会把同一次点击重复执行。
        WidgetUnlockBus.addRequestListener(unlockRequestListener)

        // 先取「本次点击」的请求；没有的话再考虑 Activity 重建前遗留的请求。
        // 顺序不能反：重建往往正是由一次新的点击触发的，此时新请求才是最新的。
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
     * 而不是 onCreate。必须处理，否则热启动不会触发开锁。
     *
     * 注意：App 已在运行时，**桌面快捷方式**的 Intent 会被系统按「前门」语义直接
     * 根投递到这里（ShortcutActivity 被跳过、总线不会被写入），此时 Intent 上携带着
     * 快捷方式的 door_id extra，由 [collectUnlockRequest] 的来源 2 解析。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent ${intentSummary(intent)}")
        setIntent(intent)
        deliverUnlockRequest()
    }

    override fun onResume() {
        super.onResume()
        // 兜底路径之一：ColorOS 等 ROM 上，经「无界面中转 Activity」拉起时可能不回调
        // onNewIntent；只要主界面回到前台，onResume 一定会被调用，所以在这里再取一次请求。
        // 注意：主界面**已在前台**时该钩子不会再触发，那种场景由总线观察者兜底
        // （见 [unlockRequestListener]），两者互补覆盖全部热启动形态。
        // 请求一旦被取走，持久化总线即被清空，后续再 onResume 不会再有任何重复触发。
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
     * 来源 1 —— [WidgetUnlockBus]（SharedPreferences 持久化）：
     *   中转页 / 小部件广播在拉起主界面前就已 commit 落盘。覆盖冷启动（onCreate 消费）
     *   与「中转页确实被执行」的热启动（观察者即时消费）。
     *
     * 来源 2 —— 桌面快捷方式的 Intent 本体（[takeShortcutDoorId]）：
     *   新快捷方式直接指向本 Activity；旧快捷方式指向 ShortcutActivity，热启动时
     *   中转页可能被 ROM 跳过、Intent 被根投递到这里。两种形态都靠 Intent 上的
     *   door_id extra 识别。
     */
    private fun collectUnlockRequest(): UnlockRequest? {
        val busDoorId = WidgetUnlockBus.consume(this)
        if (busDoorId != null) {
            Log.d(TAG, "collect: 命中总线 doorId=$busDoorId")
            // 总线已命中：同一次点击在 Intent 上的副本已无意义，标记掉防止被再次读到
            markShortcutIntentConsumed()
            return UnlockRequest(busDoorId, ++nonceSeq)
        }

        val shortcutDoorId = takeShortcutDoorId()
        Log.d(TAG, "collect: 总线无请求，快捷方式 Intent doorId=$shortcutDoorId")
        if (shortcutDoorId != null) {
            return UnlockRequest(shortcutDoorId, ++nonceSeq)
        }

        return null
    }

    /**
     * 读取「桌面快捷方式」投递到主界面的门禁 ID。
     *
     * 覆盖两种投递形态：
     *   · 新快捷方式直接指向 MainActivity（ShortcutPin 现行实现）：冷启动走 onCreate、
     *     热启动被系统按「前门（front door）」语义根投递到 onNewIntent；
     *   · 旧快捷方式指向 ShortcutActivity：热启动时中转页可能被 ROM 跳过，Intent 被
     *     根投递到这里。
     *
     * 判定**以 door_id extra 为主、action/component 为辅**：不同 ROM 做根投递时可能
     * 改写 component、剥掉 action，但 extra 会保留（此前只按 component/action 判定，
     * 在部分 ROM 上因此整个漏掉）。正常桌面图标打开、中转页/小部件转发的 Intent 都
     * 不携带 door_id，不会误触发。
     *
     * 取走后打「已消费」标记而不是仅移除 extra：仅移除 extra 后，action/component
     * 判定仍会命中并把缺失的 extra 当作 ""（开第一个门）重复触发一次。标记写在
     * Intent 对象上，旋转屏幕重建后同样不会重复。
     */
    private fun takeShortcutDoorId(): String? {
        val current = intent ?: return null
        if (current.getBooleanExtra(EXTRA_SHORTCUT_CONSUMED, false)) return null

        val fromShortcut = current.hasExtra(ShortcutActivity.EXTRA_DOOR_ID) ||
                current.action == ShortcutActivity.ACTION_PINNED_UNLOCK ||
                current.component?.className == ShortcutActivity::class.java.name
        if (!fromShortcut) return null

        val doorId = current.getStringExtra(ShortcutActivity.EXTRA_DOOR_ID) ?: ""
        current.removeExtra(ShortcutActivity.EXTRA_DOOR_ID)
        current.putExtra(EXTRA_SHORTCUT_CONSUMED, true)
        return doorId
    }

    /** 总线已命中时，把当前 Intent 上的快捷方式请求标记为已消费，避免同一次点击重复执行。 */
    private fun markShortcutIntentConsumed() {
        val current = intent ?: return
        val fromShortcut = current.hasExtra(ShortcutActivity.EXTRA_DOOR_ID) ||
                current.action == ShortcutActivity.ACTION_PINNED_UNLOCK ||
                current.component?.className == ShortcutActivity::class.java.name
        if (!fromShortcut) return
        current.removeExtra(ShortcutActivity.EXTRA_DOOR_ID)
        current.putExtra(EXTRA_SHORTCUT_CONSUMED, true)
    }

    /** 诊断日志用：摘要当前 Intent 的 component / action / extras。 */
    private fun intentSummary(intent: Intent?): String {
        if (intent == null) return "intent=null"
        return "component=${intent.component?.className} action=${intent.action} extras=${intent.extras}"
    }

    /**
     * Activity 被系统回收后重建时，恢复「尚未执行完」的开锁请求。
     *
     * 这里用 `getString()` 是否为 null 判断有无请求，**不能用 containsKey()**：
     * 旧实现在请求消费后仍会 `putString(key, null)`，而 Bundle 允许存 null 值，
     * containsKey() 依旧返回 true —— 于是重建时被判定为「已保存过 → 已处理」，
     * 直接跳过了 Intent / 总线里真正的本次请求，最终只显示主界面、完全不解锁。
     * 现在 [onSaveInstanceState] 只在真的有待执行请求时才写入，语义不再有歧义。
     */
    private fun takeRestoredRequest(savedInstanceState: Bundle?): UnlockRequest? {
        val doorId = savedInstanceState?.getString(STATE_PENDING_DOOR_ID) ?: return null
        return UnlockRequest(doorId, ++nonceSeq)
    }

    /**
     * 保存「待执行的开锁请求」，供 Activity 重建时恢复。
     *
     * 只在确实有待执行请求时写入；写 null 会让 key 存在但值为空，
     * 重建时会被误判成「已处理」而丢弃本次点击（见 [takeRestoredRequest]）。
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
         * （WidgetClickReceiver.EXTRA_DOOR_ID 即引用此常量）；
         * 取值为具体门禁 ID、[ShortcutActivity.ALL_DOORS_ID]（一键开锁）或 ""（开第一个门）。
         * 主界面侧的开锁请求投递已统一走 [WidgetUnlockBus]，不再读取 Activity Intent extra。
         */
        const val EXTRA_WIDGET_DOOR_ID = "widget_door_id"

        /** 内部标记：当前 Intent 上的快捷方式请求已被消费（防旋转/重复 onResume 再次触发） */
        private const val EXTRA_SHORTCUT_CONSUMED = "__shortcut_consumed__"

        /** 实例状态键：保存「尚未执行完」的开锁请求门禁 ID；无请求时**不写**该键 */
        private const val STATE_PENDING_DOOR_ID = "pending_unlock_door_id"
    }
}
