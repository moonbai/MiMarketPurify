package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 屏蔽后台静默下载。
 *
 * 商店通过 [BackgroundDownloadPowerPolicy] 在后台自动下载应用更新，
 * 消耗流量和电量。核心链路：
 *   BackgroundDownloadPowerPolicy.checkAdmission(source, key, size)
 *     → 返回 AdmitResult（ADMIT / REJECT）
 *     → ADMIT 时触发后台下载任务
 *
 * Hook 点选在 [BackgroundDownloadPowerPolicy.checkAdmission]，
 * 直接返回 REJECT，等同于"永远不允许后台下载"。
 *
 * 兜底：同时拦截 isPolicyEnabled，始终返回 false。
 */
object BackgroundDownloadBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_BLOCK_BG_DOWNLOAD
    override val name: String = "屏蔽后台静默下载"

    /** 缓存 REJECT 枚举值，避免每次 hook 都反射 */
    private var cachedReject: Any? = null
    private var rejectResolved = false

    private fun resolveReject(): Any? {
        if (rejectResolved) return cachedReject
        rejectResolved = true
        cachedReject = runCatching {
            val cls = ClassUtil.loadClass(
                "com.xiaomi.market.business_core.downloadinstall.BackgroundDownloadAdmitResult")
            val field = cls.getDeclaredField("REJECT")
            field.isAccessible = true
            field.get(null)
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: REJECT 枚举未找到，将 fallback", null)
        }.getOrNull()
        return cachedReject
    }

    override fun init() {
        // 主 hook：拦截 checkAdmission，直接返回 REJECT
        runCatching {
            val cls = ClassUtil.loadClass(
                "com.xiaomi.market.business_core.downloadinstall.BackgroundDownloadPowerPolicy")
            cls.methodFinder()
                .filterByName("checkAdmission")
                .filterByParamCount(3)
                .first()
                .hooked {
                    debugLog("拦截 BackgroundDownloadPowerPolicy.checkAdmission → 拒绝后台下载")
                    val reject = resolveReject()
                    if (reject != null) {
                        reject
                    } else {
                        // REJECT 枚举拿不到时，fallback 走原始逻辑（不拦截）
                        // 避免返回 null 导致 ClassCastException
                        proceed()
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: checkAdmission 挂钩失败", it)
        }

        // 兜底 hook：拦截 isPolicyEnabled，始终返回 false
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_core.downloadinstall.BackgroundDownloadPowerPolicy")
                .methodFinder()
                .filterByName("isPolicyEnabled")
                .first()
                .hooked {
                    debugLog("拦截 isPolicyEnabled → 强制关闭后台下载策略")
                    false
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: isPolicyEnabled 兜底 hook（可选）", null)
        }
    }
}
