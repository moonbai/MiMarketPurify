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
 * 拦截安装后推荐弹窗（ClientAIAdReRankEngine.compute）。
 *
 * 商店的推荐场景（首页安装后、详情页安装后返回、搜索引导页）统一由
 * ClientAIAdReRankEngine.compute() 生成推荐列表，hook 此方法直接返回空列表，
 * 从源头切断所有推荐弹窗。
 */
object InstallRecommendBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH
    override val name: String = "拦截安装后推荐"

    override fun init() {
        runCatching {
            val clz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            clz.methodFinder()
                .filterByName("compute")
                .first()
                .hooked {
                    // 直接返回空列表，不执行原始逻辑
                    return@hooked emptyList<Any>()
                }
            HookEnv.base.log(Log.INFO, TAG, "$name: 已拦截 ClientAIAdReRankEngine.compute")
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: 拦截失败", it)
        }
    }
}
