package cn.huacheng.safebaiyun

import android.app.Application
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.widget.WidgetHelper

class BaiYunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(this)
        ConfigManager.init(this)
        // 升级后刷新一次小部件：把旧版本遗留的「广播点击」切换为
        // 「直拉 ShortcutActivity 中转页」，修复冷启动不解锁的问题。
        WidgetHelper.refreshAllAfterUpgrade(this)
    }
}
