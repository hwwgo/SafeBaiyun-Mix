package cn.huacheng.safebaiyun

import android.app.Application
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.ContextHolder

class BaiYunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextHolder.init(this)
        ConfigManager.init(this)

        // ✅ 显式触发 DataRepo 初始化，确保冷启动时 SharedPreferences 就绪
        DataRepo.getDoors()
    }
}
