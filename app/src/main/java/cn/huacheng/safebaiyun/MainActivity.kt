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
 * 应用主界面，也是**唯一的开锁入口**。
 *
 * 桌面小部件与桌面快捷方式都不再弹独立提示，而是直接进这里：
 * 主界面照常显示，然后自动执行对应门禁的开锁，过程与用户手动点
 * 「开锁 / 一键开锁」按钮完全一致；开锁结束后主界面保持不动（不自动关闭）。
 */
class MainActivity : ComponentActivity() {

    /** 待执行的开锁请求（桌面小部件 / 桌面快捷方式携带的门禁 ID）；null 表示无请求 */
    private val pendingUnlockDoorId = mutableStateOf<String?>(null)

    /** 当前导航控制器：小部件在其它页面被点击时，需要先回到首页再展示开锁过程 */
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConfigManager.init(this)
        UnlockRecordManager.init(this)

        // 桌面小部件 / 快捷方式拉起时解析出的门禁 ID，交给主界面自动执行开锁。
        // Activity 被系统重建（如旋转屏幕）时优先恢复已保存的请求状态：
        //   · 已保存（含 null）→ 用保存值，避免重复解析/重复触发开锁；
        //   · 未保存（首次创建）→ 正常解析 Intent 与 WidgetUnlockBus。
        pendingUnlockDoorId.value = restoreOrResolveUnlockDoorId(savedInstanceState, intent)

        setContent {
            SafeBaiyunTheme {
                MainNavContent(
                    autoUnlockDoorId = pendingUnlockDoorId.value,
                    onAutoUnlockConsumed = { pendingUnlockDoorId.value = null }
                )
            }
        }
    }

    /**
     * App 已在运行（singleTask）时，小部件 / 快捷方式再次点击会把 Intent 送到这里，
     * 而不是 onCreate。必须处理，否则重复点击不会触发开锁。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val doorId = resolveUnlockDoorId(intent) ?: return

        // 若此刻停留在其它页面（设置、门禁管理等），先回到首页，
        // 这样用户才能看到和手动点击时一样的开锁过程。
        navController?.let { nav ->
            if (nav.currentDestination?.route != "main") {
                nav.popBackStack("main", inclusive = false)
            }
        }

        pendingUnlockDoorId.value = doorId
    }

    override fun onResume() {
        super.onResume()
        // 兜底：个别 ROM（如 ColorOS）上，经「无界面中转 Activity」拉起时
        // onNewIntent 可能不被回调，导致热启动只打开界面、不触发开锁。
        // 这里在回到前台时再检查一次待处理的开锁请求：
        //   · onNewIntent / onCreate 已消费过 → bus 为空，此处直接返回，不会重复解锁；
        //   · 请求被 ROM 丢掉 → 此处兜底拿到，正常触发。
        // bus 内请求 15 秒自动过期，日常打开 App 时这里永远为空，无副作用。
        // 如果当前 Intent 已明确携带本次快捷方式请求，交由 onCreate/onNewIntent
        // 处理，避免 onResume 抢先消费 bus 后，onNewIntent 再写入相同 ID，
        // 导致 Compose 的 LaunchedEffect 不重新执行（热启动只显示主界面）。
        if (intent?.hasExtra(EXTRA_WIDGET_DOOR_ID) == true) return

        val doorId = WidgetUnlockBus.consume(this) ?: return

        navController?.let { nav ->
            if (nav.currentDestination?.route != "main") {
                nav.popBackStack("main", inclusive = false)
            }
        }

        pendingUnlockDoorId.value = doorId
    }

    /**
     * 保存「待执行的开锁请求」，供 Activity 重建时恢复：
     *   · 重建时若请求仍在（开锁进行中被重建），恢复后继续执行，不丢请求；
     *   · 重建时若请求已被消费（保存为 null），恢复后保持 null，
     *     避免又被 Intent 里残留的 extra 重新触发一次开锁。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_WIDGET_DOOR_ID, pendingUnlockDoorId.value)
    }

    /**
     * 解析本次要执行的开锁门禁 ID；无请求返回 null。
     *
     * 优先级：
     *  1) [WidgetUnlockBus]（SharedPreferences 持久化）—— ColorOS 等 ROM 冷启动会
     *     丢弃 Intent extra，落盘才是可靠来源；小部件与快捷方式都会先落盘。
     *  2) Intent extra —— 热启动（onNewIntent）/ 进程已活时的兜底。
     */
    private fun resolveUnlockDoorId(intent: Intent): String? {
        // 当前启动/新 Intent 的 extra 属于本次点击，优先级高于持久化 bus。
        // 先读 extra 可避免热启动时 onResume 与 onNewIntent 竞争消费同一请求。
        val intentId = intent.getStringExtra(EXTRA_WIDGET_DOOR_ID)
        if (intentId != null) {
            WidgetUnlockBus.consume(this) // 清理对应的持久化副本，避免稍后重复执行
            return intentId
        }
        return WidgetUnlockBus.consume(this)
    }

    /** Activity 重建时恢复开锁请求；首次创建时正常解析。 */
    private fun restoreOrResolveUnlockDoorId(savedInstanceState: Bundle?, intent: Intent): String? =
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_WIDGET_DOOR_ID)) {
            savedInstanceState.getString(EXTRA_WIDGET_DOOR_ID)
        } else {
            resolveUnlockDoorId(intent)
        }

    @Composable
    private fun MainNavContent(
        autoUnlockDoorId: String?,
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
                    autoUnlockDoorId = autoUnlockDoorId,
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
    }
}
