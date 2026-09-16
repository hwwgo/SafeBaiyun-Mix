package cn.huacheng.safebaiyun

import android.app.Application
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.ContextHolder

class BaiYunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(this)
        // ✅ 关键修复：无论从哪个入口启动（含桌面部件直接开门），
        //    ConfigManager 都保证在 App 启动时初始化。
        ConfigManager.init(this)
    }
}
