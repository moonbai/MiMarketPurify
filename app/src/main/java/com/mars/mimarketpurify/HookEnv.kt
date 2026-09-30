package com.mars.mimarketpurify

import io.github.libxposed.api.XposedModule

internal object HookEnv {

    lateinit var base: XposedModule
        private set

    lateinit var hostClassLoader: ClassLoader
        private set

    fun setBase(base: XposedModule) {
        this.base = base
        // 供 Settings 在 hook 进程内取远程偏好（App 侧走 XposedService，不依赖此 provider）。
        // 闭包捕获 XposedModule，仅在本对象所在的注入进程创建，不会在 standalone App 触发 XposedModule 加载。
        Settings.remotePrefsProvider = { group -> base.getRemotePreferences(group) }
    }

    fun setHostClassLoader(cl: ClassLoader) {
        this.hostClassLoader = cl
    }
}
