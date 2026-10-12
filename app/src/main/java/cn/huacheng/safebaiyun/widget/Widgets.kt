package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSPrimary
import cn.huacheng.safebaiyun.theme.ColorOSSecondary
import cn.huacheng.safebaiyun.theme.ColorOSTertiary
import cn.huacheng.safebaiyun.unlock.DataRepo

// 合并自 WidgetClickReceiver.kt / WidgetUnlockBus.kt / WidgetHelper.kt（同包，类名不变）
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import cn.huacheng.safebaiyun.MainActivity
import cn.huacheng.safebaiyun.util.ContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// ============================================================
//  桌面部件 ColorOS 配色
//
//  Glance 的 ColorProvider 需要 day/night 两色，系统自动根据
//  深浅模式挑选。这里定义所有 widget 共用的颜色常量。
// ============================================================
val doorIdKey = stringPreferencesKey("widget_door_id")

// -------- 品牌色（深浅模式一致） --------
val WidgetPrimary = ColorProvider(day = ColorOSPrimary, night = ColorOSPrimary)
val WidgetOnPrimary = ColorProvider(day = Color.White, night = Color.White)
val WidgetSecondary = ColorProvider(day = ColorOSSecondary, night = ColorOSSecondary)
val WidgetTertiary = ColorProvider(day = ColorOSTertiary, night = ColorOSTertiary)
val WidgetError = ColorProvider(day = ColorOSError, night = ColorOSError)

// -------- 表面与文字（深浅模式区分） --------
// 卡片背景
val WidgetSurface = ColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1E1E1E)
)

// 卡片主文字
val WidgetOnSurface = ColorProvider(
    day = Color(0xFF1A1A1A),
    night = Color(0xFFFFFFFF)
)

// 卡片次要背景（未激活按钮）
val WidgetSurfaceVariant = ColorProvider(
    day = Color(0xFFF2F2F2),
    night = Color(0xFF2A2A2A)
)

// 卡片次要文字
val WidgetOnSurfaceVariant = ColorProvider(
    day = Color(0xFF666666),
    night = Color(0xFFB0B0B0)
)

// ============================================================
//  公共：构造「点击开锁」的 Intent（指向 ShortcutActivity 中转页）。
//
//  历史上小部件点击走的是「发广播 → WidgetClickReceiver → 拉起 MainActivity」，
//  冷启动时该路径不可靠：广播可能不被送达；即使送达，中转链路里 App 已无前台
//  Activity，startActivity 会撞上 Android 10+ 的后台活动启动限制（BAL），
//  表现为「冷启动只打开 App 主界面、不解锁」（与桌面快捷方式曾遇到的问题相同）。
//
//  现在统一改为：桌面小部件点击直接拉起 ShortcutActivity 中转页（用户手势拉起
//  Activity 永远被 BAL 放行），由中转页落盘门禁 ID 并拉起 MainActivity。
//  该路径已在小部件/快捷方式两条链路上实测冷、热启动均稳定。
//  （WidgetClickReceiver 广播路径保留，仅用于兼容旧版本创建的小部件。）
//
//  @param doorId  要开的门禁 ID（"" 表示开第一个门）
//  @param data    可选，用于让同一个 widget 内多个按钮的 PendingIntent
//                 互不覆盖（四门禁 widget 用门禁 ID 作 data）。
// ============================================================
private fun unlockIntent(doorId: String, data: String? = null): Intent =
    Intent().apply {
        component = ComponentName(
            "cn.huacheng.safebaiyun",
            "cn.huacheng.safebaiyun.ShortcutActivity"
        )
        action = ShortcutActivity.ACTION_PINNED_UNLOCK
        putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)
        if (data != null) this.data = Uri.fromParts("widget", data, null)
    }

// ============================================================
//  小号（1×1）桌面部件：只显示一个开锁按钮。
// ============================================================
class SmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = SmallWidget
}

object SmallWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs: Preferences = currentState()
            val boundDoorId = prefs[doorIdKey]

            // 优先使用 Widget 绑定的门禁；未绑定则兼容原行为：用第一个门禁。
            val door = boundDoorId
                ?.let { doorId -> DataRepo.getDoors().find { it.id == doorId } }
                ?: DataRepo.getDoors().firstOrNull()

            GlanceTheme {
                WidgetContent(door?.id)
            }
        }
    }

    @Composable
    private fun WidgetContent(doorId: String?) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.unlock),
                contentDescription = "开门",
                backgroundColor = WidgetPrimary,
                contentColor = WidgetOnPrimary,
                onClick = actionStartActivity(unlockIntent(doorId ?: ""))
            )
        }
    }
}

// ============================================================
//  中号桌面部件：门禁名 + “点击解锁” + 圆形按钮。
// ============================================================
class MediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = MediumWidget
}

object MediumWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs: Preferences = currentState()
            val boundDoorId = prefs[doorIdKey]

            // 判断绑定的是：一键开锁 / 指定门禁 / 未绑定。
            val displayName = when {
                boundDoorId == ShortcutActivity.ALL_DOORS_ID -> "一键开锁"
                else -> boundDoorId
                    ?.let { doorId -> DataRepo.getDoors().find { it.id == doorId }?.name }
                    ?: DataRepo.getDoors().firstOrNull()?.name
                    ?: "无门禁"
            }

            GlanceTheme {
                WidgetContent(boundDoorId, displayName)
            }
        }
    }

    @Composable
    private fun WidgetContent(doorId: String?, doorName: String) {
        Row(
            modifier = GlanceModifier
                .background(WidgetSurface)
                .fillMaxWidth()
                .cornerRadius(50.dp)
                .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = doorName,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = WidgetOnSurface
                    )
                )
                Text(
                    text = "点击解锁",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = WidgetOnSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.unlock),
                contentDescription = "开门",
                backgroundColor = WidgetPrimary,
                contentColor = WidgetOnPrimary,
                onClick = actionStartActivity(unlockIntent(doorId ?: ""))
            )
        }
    }
}

// ============================================================
//  大号桌面部件：Logo + 圆形按钮在上，门禁名 + “点击解锁门禁”在下。
// ============================================================
class LargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = LargeWidget
}

object LargeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs: Preferences = currentState()
            val boundDoorId = prefs[doorIdKey]

            val displayName = when {
                boundDoorId == ShortcutActivity.ALL_DOORS_ID -> "一键开锁"
                else -> boundDoorId
                    ?.let { doorId -> DataRepo.getDoors().find { it.id == doorId }?.name }
                    ?: DataRepo.getDoors().firstOrNull()?.name
                    ?: "无门禁"
            }

            GlanceTheme {
                WidgetContent(boundDoorId, displayName)
            }
        }
    }

    @Composable
    private fun WidgetContent(doorId: String?, doorName: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(170.dp)
                .cornerRadius(24.dp)
                .background(WidgetSurface)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Image(
                    modifier = GlanceModifier.size(68.dp, 96.dp),
                    provider = ImageProvider(R.drawable.guangzhou),
                    contentScale = ContentScale.FillBounds,
                    contentDescription = null
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                Box(modifier = GlanceModifier.padding(top = 8.dp)) {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.unlock),
                        contentDescription = "开门",
                        backgroundColor = WidgetPrimary,
                        contentColor = WidgetOnPrimary,
                        onClick = actionStartActivity(unlockIntent(doorId ?: ""))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Text(
                text = doorName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = WidgetOnSurface
                )
            )

            Text(
                text = "点击解锁门禁",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = WidgetOnSurfaceVariant
                )
            )
        }
    }
}

// ============================================================
//  超大号桌面部件：最多显示 3 个门禁按钮（三键快开）。
// ============================================================
class ExtraLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = ExtraLargeWidget
}

object ExtraLargeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val doors = DataRepo.getDoors().take(3)

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(170.dp)
                .cornerRadius(24.dp)
                .background(WidgetSurface)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                doors.forEach { door ->
                    DoorButton(
                        context = context,
                        doorId = door.id,
                        name = door.name,
                        hasConfig = door.mac.isNotEmpty() && door.key.isNotEmpty()
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())
                }

                repeat(3 - doors.size) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }

            Text(
                text = "白云通 · 三键快开",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = WidgetOnSurfaceVariant
                ),
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    private fun DoorButton(
        context: Context,
        doorId: String,
        name: String,
        hasConfig: Boolean
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier
                .width(90.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.unlock),
                    contentDescription = "解锁$name",
                    backgroundColor = if (hasConfig) WidgetPrimary else WidgetSurfaceVariant,
                    contentColor = if (hasConfig) WidgetOnPrimary else WidgetOnSurfaceVariant,
                    onClick = actionStartActivity(unlockIntent(doorId))
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = name,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = WidgetOnSurface
                ),
                maxLines = 1
            )
        }
    }
}

// ============================================================
//  四门禁（4×1）桌面部件：横向最多显示 4 个门禁。
//  每个按钮用 data 区分，避免同 widget 内 PendingIntent 去重。
// ============================================================
class FourDoorReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = FourDoorWidget
}

object FourDoorWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        // 固定显示前四个门禁。
        val doors = DataRepo.getDoors().take(4)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(62.dp)
                .cornerRadius(18.dp)
                .background(WidgetSurface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doors.isNotEmpty()) {
                    doors.forEachIndexed { index, door ->
                        DoorColumn(
                            doorId = door.id,
                            name = door.name,
                            hasConfig = door.mac.isNotEmpty() && door.key.isNotEmpty()
                        )

                        if (index != doors.lastIndex) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }

                // 门禁少于 4 个时补空间。
                repeat(4 - doors.size) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun DoorColumn(
        doorId: String,
        name: String,
        hasConfig: Boolean
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.width(60.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .size(34.dp)
                    .cornerRadius(17.dp)
                    .background(if (hasConfig) WidgetPrimary else WidgetSurfaceVariant)
                    .clickable(actionStartActivity(unlockIntent(doorId, doorId))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.unlock),
                    contentDescription = "解锁$name",
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(
                        if (hasConfig) WidgetOnPrimary else WidgetOnSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            Text(
                text = name,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetOnSurface
                ),
                maxLines = 1
            )
        }
    }
}

// ============================================================
//  桌面小部件点击广播桥（合并自 WidgetClickReceiver.kt）
//
//  【兼容通道】仅用于接收旧版本 App 创建的小部件遗留的广播 PendingIntent。
//  新版小部件点击已改为直接拉起 ShortcutActivity 中转页（见 unlockIntent），
//  不再走本接收器 —— 广播链路在冷启动下不可靠：广播可能不被送达；即使送达，
//  收到广播时 App 往往已无前台 Activity，startActivity 会被系统后台活动
//  启动限制（BAL）拦截，表现为「冷启动只打开 App 主界面、不解锁」。
//  类名不变，Manifest 仍按类名引用，故保留。
// ============================================================
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
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_DOOR_ID, doorId)
        }
        context.startActivity(launch)
    }
}

// ============================================================
//  Widget 开锁请求通信总线（合并自 WidgetUnlockBus.kt）
//
//  ColorOS 冷启动时可能先启动 MainActivity，仅用内存字段不可靠，
//  因此同时把请求持久化到 SharedPreferences。requestUnlock() 使用 commit()，
//  确保 startActivity() 之前请求已落盘。
// ============================================================
object WidgetUnlockBus {

    private const val PREFS_NAME = "widget_unlock_bus"
    private const val KEY_DOOR_ID = "pending_door_id"
    private const val KEY_TIMESTAMP = "pending_timestamp"
    private const val MAX_AGE_MS = 15_000L

    @Volatile
    private var pendingDoorId: String? = null

    @Volatile
    private var pendingTimestamp: Long = 0L

    fun requestUnlock(context: Context, doorId: String) {
        val now = System.currentTimeMillis()

        pendingDoorId = doorId
        pendingTimestamp = now

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOOR_ID, doorId)
            .putLong(KEY_TIMESTAMP, now)
            .commit()
    }

    @Synchronized
    fun consume(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val persistedId = prefs.getString(KEY_DOOR_ID, null)
        val persistedTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        val id = persistedId ?: pendingDoorId
        val timestamp = if (persistedTimestamp != 0L) {
            persistedTimestamp
        } else {
            pendingTimestamp
        }

        val valid = id != null &&
                timestamp > 0L &&
                System.currentTimeMillis() - timestamp in 0L..MAX_AGE_MS

        // 无论是否有效，都只消费一次。
        prefs.edit()
            .remove(KEY_DOOR_ID)
            .remove(KEY_TIMESTAMP)
            .commit()

        pendingDoorId = null
        pendingTimestamp = 0L

        return if (valid) id else null
    }

    /**
     * 仅读取待解锁的门禁 ID，不消费（不清除）。
     * 用于在 MainActivity.onCreate 早期判断是否由小部件拉起，以便切换透明主题；
     * 实际消费仍由 [consume] 完成。
     */
    @Synchronized
    fun peek(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val persistedId = prefs.getString(KEY_DOOR_ID, null)
        val persistedTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        val id = persistedId ?: pendingDoorId
        val timestamp = if (persistedTimestamp != 0L) {
            persistedTimestamp
        } else {
            pendingTimestamp
        }

        val valid = id != null &&
                timestamp > 0L &&
                System.currentTimeMillis() - timestamp in 0L..MAX_AGE_MS

        return if (valid) id else null
    }
}

// ============================================================
//  Widget 辅助（合并自 WidgetHelper.kt）
// ============================================================
object WidgetHelper {

    fun requestPermission() {
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", ContextHolder.get().packageName, null)
        intent.setData(uri)
        ContextHolder.get().startActivity(intent)
    }

    fun createShortcut() {
        val context = ContextHolder.get()
        val intent = Intent(context, ShortcutActivity::class.java)
        intent.setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfoCompat.Builder(context, "Unlock").setIcon(
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        ).setShortLabel("开门").setLongLabel("解锁门禁").setIntent(intent).build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * App 升级后刷新一次所有小部件（每个版本只做一次）。
     *
     * 旧版本小部件的点击走「发广播 → WidgetClickReceiver」，冷启动不可靠
     * （请求丢失 / startActivity 被后台启动限制拦截）；新版本改为点击直拉
     * ShortcutActivity 中转页。已放在桌面上的旧小部件持有旧的广播 PendingIntent，
     * 必须等小部件刷新才会换成新路径 —— 这里在升级后首次启动时主动刷新，
     * 保证老部件立即切换到冷、热启动均可靠的链路。
     */
    fun refreshAllAfterUpgrade(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("app_meta", Context.MODE_PRIVATE)
        val currentVersion = runCatching {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") info.versionCode
            }
        }.getOrDefault(0)
        if (prefs.getInt("last_widget_refresh_version", -1) == currentVersion) return
        prefs.edit().putInt("last_widget_refresh_version", currentVersion).apply()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                SmallWidget.updateAll(appContext)
                MediumWidget.updateAll(appContext)
                LargeWidget.updateAll(appContext)
                ExtraLargeWidget.updateAll(appContext)
                FourDoorWidget.updateAll(appContext)
            }
        }
    }
}
