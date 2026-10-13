package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
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
 * 请求送达主界面共有三条路径，任何一条活着都必须能把请求取出来执行，
 * 但整次点击只能被执行一次（参见 [collectUnlockRequest]）：
 *   1) [onCreate]     —— 冷启动 / Activity 被回收后重建；
 *   2) [onNewIntent]  —— App 已在运行时的标准热启动路径（singleTask）；
 *   3) [onResume]     —— 兜底：部分 ROM 会把这次点击的 Intent 丢掉、不回调 onNewIntent，
 *                        但只要主界面回到前台，onResume 一定会被调用。
 */
class MainActivity : ComponentActivity() {

    /** 待执行的开锁请求（桌面小部件 / 桌面快捷方式携带的门禁 ID）；null 表示无请求 */
    private val pendingUnlock = mutableStateOf<UnlockRequest?>(null)

    /** 当前导航控制器：小部件在其它页面被点击时，需要先回到首页再展示开锁过程 */
    private var navController: NavHostController? = null

    /** 请求序号生成器，保证每一次请求的 [UnlockRequest.nonce] 都独一无二 */
    private var nonceSeq = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConfigManager.init(this)
        UnlockRecordManager.init(this)

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

    /**
     * App 已在运行（singleTask）时，小部件 / 快捷方式再次点击会把 Intent 送到这里，
     * 而不是 onCreate。必须处理，否则热启动不会触发开锁。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverUnlockRequest()
    }

    override fun onResume() {
        super.onResume()
        // 兜底：ColorOS 等 ROM 上，经「无界面中转 Activity」拉起时 Intent 可能被丢弃、
        // onNewIntent 不被回调（此时 getIntent() 仍是上一次的旧 Intent），
        // 表现为「热启动只打开主界面、不解锁」。
        // 只要主界面回到前台，onResume 一定会被调用，所以在这里再取一次请求。
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
     * 取出本次点击的开锁请求；三种来源互斥且都具备「取走即清空」语义，
     * 因此 onCreate / onNewIntent / onResume 无论谁先到，都不会重复执行一次点击。
     *
     * 优先级：
     *  1) [WidgetUnlockBus]（SharedPreferences 持久化）—— 发起方 ShortcutActivity /
     *     WidgetClickReceiver 在拉起主界面**之前**就已 commit 落盘，既不受 Intent extra
     *     丢失影响，也不依赖 onNewIntent 是否被回调，是最可靠的来源。
     *  2) Intent extra —— 标准 onNewIntent 路径下的兜底补充。读取后立即 removeExtra，
     *     避免 extra 长期滞留在 Intent 里干扰后续判断（旧实现正是被残留的 extra
     *     永久关闭了 onResume 兜底分支）。
     */
    private fun collectUnlockRequest(): UnlockRequest? {
        val busDoorId = WidgetUnlockBus.consume(this)
        if (busDoorId != null) {
            clearIntentExtra() // 同一次点击的 extra 副本已无意义，清掉防止被再次读到
            return UnlockRequest(busDoorId, ++nonceSeq)
        }

        val extraDoorId = takeIntentExtra()
        if (extraDoorId != null) {
            WidgetUnlockBus.consume(this) // 清掉总线里同一点击的副本，避免稍后再执行一次
            return UnlockRequest(extraDoorId, ++nonceSeq)
        }

        return null
    }

    /** 读取并清除 Intent 上的开锁请求 extra，确保一次 extra 只会被消费一次。 */
    private fun takeIntentExtra(): String? {
        val current = intent ?: return null
        val doorId = current.getStringExtra(EXTRA_WIDGET_DOOR_ID)
        current.removeExtra(EXTRA_WIDGET_DOOR_ID)
        return doorId
    }

    private fun clearIntentExtra() {
        intent?.removeExtra(EXTRA_WIDGET_DOOR_ID)
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
        /** 桌面小部件 / 快捷方式通过该 extra 传递要开的门禁 ID；
         *  取值为具体门禁 ID、[ShortcutActivity.ALL_DOORS_ID]（一键开锁）或 ""（开第一个门） */
        const val EXTRA_WIDGET_DOOR_ID = "widget_door_id"

        /** 实例状态键：保存「尚未执行完」的开锁请求门禁 ID；无请求时**不写**该键 */
        private const val STATE_PENDING_DOOR_ID = "pending_unlock_door_id"
    }
}
