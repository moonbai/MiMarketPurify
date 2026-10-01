package com.mars.mimarketpurify.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.mars.mimarketpurify.TAG

/**
 * 拉起小米应用商店「应用更新 / 升级」页。
 *
 * 更新页**不是** `market://update` 这种深链（该 scheme 系统并未注册，此前点击「更新」毫无反应），
 * 而是一个独立 Activity（多版本一致为 `com.xiaomi.market.ui.UpdateListActivity`）。这里显式启动它；
 * 先试已知类名，再退回从商店自身 Manifest 的 Activity 列表里模糊匹配（Activity 类名不受
 * AndResGuard 资源混淆影响，跨版本也较稳）。
 *
 * 由两处复用，保证悬浮底栏开启/关闭两种场景下「更新」入口都能正确跳转：
 *  - [com.mars.mimarketpurify.util.ComposeFloatingBarHost] 中悬浮底栏「更新」入口的点击；
 *  - [com.mars.mimarketpurify.hooks.market.TabFilter] 中悬浮底栏关闭时，原生「更新」tab 的点击兜底。
 */
object MarketUpdateLauncher {

    private val TAG_LAUNCH = "[MarketUpdateLauncher]"

    /** 商店「应用更新 / 升级」页 Activity 候选（按命中概率排序；跨版本兜底）。 */
    private val UPDATE_PAGE_ACTIVITIES = listOf(
        "com.xiaomi.market.ui.UpdateListActivity",
        "com.xiaomi.market.business_ui.update.UpdateListActivity",
        "com.xiaomi.market.business_ui.main.update.UpdateListActivity",
        "com.xiaomi.market.ui.UpdateActivity",
    )

    /**
     * 尝试拉起更新页，命中任一候选即返回 true。
     * [context] 应为商店自身 Activity（同进程，可拉起未导出的内部 Activity）。
     */
    fun launch(context: Context): Boolean {
        val pkg = context.packageName
        for (name in UPDATE_PAGE_ACTIVITIES) {
            if (startExplicit(context, pkg, name)) return true
        }
        // 跨版本兜底：从商店 Manifest 的 Activity 里挑「名字像更新页」的候选，UpdateList* 优先。
        for (name in queryUpdateActivities(context, pkg)) {
            if (startExplicit(context, pkg, name)) return true
        }
        return false
    }

    /** 显式启动商店内某个 Activity（同进程上下文，可拉起未导出的内部 Activity）；失败返回 false。 */
    private fun startExplicit(context: Context, pkg: String, name: String): Boolean = runCatching {
        context.startActivity(Intent().setComponent(ComponentName(pkg, name)))
        true
    }.onFailure { e ->
        Log.v(TAG, "$TAG_LAUNCH 启动 $name 失败: ${e.message}")
    }.getOrDefault(false)

    /** 从商店 Manifest 的 Activity 列表里挑「名字像更新页」的候选，UpdateList* 优先。 */
    private fun queryUpdateActivities(context: Context, pkg: String): List<String> {
        val info = runCatching {
            context.packageManager.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
            )
        }.getOrNull() ?: return emptyList()
        return (info.activities ?: emptyArray())
            .mapNotNull { it.name }
            .filter { n ->
                val simple = n.substringAfterLast('.').lowercase()
                simple.contains("update") && simple.endsWith("activity") &&
                    !simple.contains("download") && !simple.contains("version")
            }
            .sortedByDescending { it.contains("updatelist", ignoreCase = true) }
    }
}
