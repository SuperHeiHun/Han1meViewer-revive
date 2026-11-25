package com.yenaly.han1meviewer

import android.content.Context
import com.yenaly.yenaly_libs.base.YenalyInitializer

class HInitializer : YenalyInitializer() {
    override fun create(context: Context) {
        super.create(context)
        // 初始化本地崩溃处理器。
        Thread.setDefaultUncaughtExceptionHandler(HCrashHandler)
    }
}
