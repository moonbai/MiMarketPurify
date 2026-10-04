package com.mars.mimarketpurify.hooks.market

import android.content.Intent
import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 给 download_with_checkin 按钮添加长按跳转到插件主页。
 *
 * 长按应用商店底部的下载/签到按钮 → 打开 MiMarketPurify 主页。
 */
object LongPressJumpToPlugin : BaseHook() {

    override val prefKey: String? = null
    override val name: String = "长按跳转插件主页"

    override fun init() {
        runCatching {
            val clz = ClassUtil.loadClass("com.xiaomi.market.widget.DownloadWithCheckin")
            clz.methodFinder()
                .filterByName("initView")
                .first()
                .hooked {
                    proceed()
                    // thisObject 就是 DownloadWithCheckin 实例（LinearLayout）
                    val view = thisObject as? View ?: return@hooked null
                    view.setOnLongClickListener {
                        runCatching {
                            val pkg = HookEnv.base.javaClass.packageName
                            val intent = Intent().apply {
                                setClassName(pkg, "$pkg.MainActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            view.context.startActivity(intent)
                            HookEnv.base.log(Log.INFO, TAG, "$name: 长按 download_with_checkin 跳转插件主页")
                        }.onFailure { e ->
                            HookEnv.base.log(Log.ERROR, TAG, "$name: 跳转失败", e)
                        }
                        true  // 消费事件
                    }
                    HookEnv.base.log(Log.INFO, TAG, "$name: 已挂载长按监听到 download_with_checkin")
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: 挂钩失败", it)
        }
    }
}
