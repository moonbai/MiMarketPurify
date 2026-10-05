package com.mars.mimarketpurify

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块自身 App 的 Application。
 *
 * 通过 [XposedServiceHelper] 绑定框架提供的 [XposedService]，从而能够：
 *  1. 把用户在主页设置的开关写入远程偏好（[XposedService.getRemotePreferences]），
 *     供 hooked 进程内的 hook 读取；
 *  2. 向外暴露框架状态（名称 / 版本 / 是否支持 remote preferences 等），供主页展示。
 *
 * 注意：只有当框架（如 LSPosed）真正加载了本模块后，[onServiceBind] 才会被回调，
 * 否则 [mService] 为 null，主页会提示“模块未激活”。
 */
class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        @Volatile
        var mService: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(
            listener: ServiceStateListener,
            notifyImmediately: Boolean
        ) {
            listeners.add(listener)
            if (notifyImmediately) listener.onServiceStateChanged(mService)
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            listeners.remove(listener)
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        // 运行时按用户偏好动态设置 App 级「预测性返回」开关（Android 14+）；
        // 低于 API 34 由 manifest 的 enableOnBackInvokedCallback="true" 兜底。
        // 参考附件 AppMarket 项目：仅 manifest 静态开启无法随开关切换，
        // 需用 ApplicationInfo.setEnableOnBackInvokedCallback 反射控制。
        setPredictiveBackEnabled(
            this,
            getSharedPreferences(Settings.PREFS_GROUP, Context.MODE_PRIVATE)
                .getBoolean(Settings.KEY_PREDICTIVE_BACK, true),
        )
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        listeners.forEach { it.onServiceStateChanged(mService) }
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        listeners.forEach { it.onServiceStateChanged(mService) }
    }
}
