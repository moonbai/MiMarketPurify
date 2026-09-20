package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field

/**
 * 移除应用升级页 / 下载页的软件推荐。
 *
 * 修复点（针对原实现“部分功能不生效”）：
 *  - 原先 [pageCollapseStateExpand] 通过 lazy 在首次访问时加载内部类字段，
 *    若该内部类/字段随商店版本变动而找不到，会直接抛异常，导致整个更新列表适配器
 *    的构造 hook 失败、更新页异常。这里改为失败兜底为 null，并仅在非空时才设置字段。
 *
 * 优化点：
 *  - 原实现每次构造 adapter 都通过 fieldFinder() 反射查找三个目标字段，
 *    这里惰性缓存 Field 引用（与 pageCollapseStateExpand 同一模式），构造时零反射查找。
 */
object UpdateDownloadAds : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_DL

    override val name: String
        get() = "移除升级/下载推荐"

    private val pageCollapseStateExpand by lazy {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.ui.UpdateListRvAdapter${'$'}PageCollapseState"
            ).getFieldValue("Expand")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: 未找到 PageCollapseState.Expand，跳过展开字段", it)
        }.getOrNull()
    }

    /** 三个目标字段的缓存引用：[forceExpanded, foldButtonVisible, pageCollapseState] */
    @Volatile
    private var adapterFields: Array<Field>? = null

    private fun resolveAdapterFields(): Array<Field>? {
        adapterFields?.let { return it }
        synchronized(this) {
            adapterFields?.let { return it }
            val result = runCatching {
                val clz = ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListRvAdapter")
                arrayOf(
                    clz.fieldFinder().filterByName("forceExpanded").first(),
                    clz.fieldFinder().filterByName("foldButtonVisible").first(),
                    clz.fieldFinder().filterByName("pageCollapseState").first()
                )
            }.onFailure {
                HookEnv.base.log(Log.WARN, TAG, "$name: 未找到 UpdateListRvAdapter 目标字段，跳过展开", it)
            }.getOrNull()
            adapterFields = result
            return result
        }
    }

    override fun init() {
        // 下载队列页面 / 为你优先
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.DownloadListFragment")
                .methodFinder()
                .filterByName("parseRecommendGroupResult")
                .first()
                .hooked { null }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: DownloadListFragment 拦截失败", it) }

        // 应用升级页面：移除推荐分组 + 默认展开全部
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListRvAdapter").apply {
                methodFinder()
                    .filterByName("generateRecommendGroupItems")
                    .first()
                    .hooked { null }

                constructorFinder().forEach { ctor ->
                    ctor.hooked {
                        val result = proceed()

                        pageCollapseStateExpand?.let { expandState ->
                            val fields = resolveAdapterFields()
                            if (fields != null) {
                                runCatching { fields[0].set(thisObject, true) }
                                runCatching { fields[1].set(thisObject, false) }
                                runCatching { fields[2].set(thisObject, expandState) }
                            }
                        }
                        return@hooked result
                    }
                }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: UpdateListRvAdapter 拦截失败", it) }
    }
}
