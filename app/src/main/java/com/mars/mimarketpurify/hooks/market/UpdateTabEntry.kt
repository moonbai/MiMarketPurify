package com.mars.mimarketpurify.hooks.market

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object UpdateTabEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "底栏更新入口"

    private const val PURIFY_UPDATE = "purify_update"

    override fun init() {
        runCatching {
            val tabInfoClz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")

            val tagField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("tag")
                    .filterByType(String::class.java)
                    .firstOrNull()
            }.getOrNull()

            // 打印 sDefaultTabIconResource 内容，了解默认图标结构
            runCatching {
                val mapField = tabInfoClz.fieldFinder()
                    .filterByName("sDefaultTabIconResource").first()
                val map = mapField.get(null) as? Map<*, *>
                debugLog("sDefaultTabIconResource keys: ${map?.keys}")
                map?.forEach { (k, v) ->
                    debugLog("  $k → ${v?.let { it::class.java.simpleName }} = $v")
                }
            }.onFailure {
                debugLog("sDefaultTabIconResource 读取失败: ${it.message}")
            }

            tabInfoClz.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val raw = proceed()
                    if (!enabled()) return@hooked raw

                    val origList = raw as? List<*>
                        ?: return@hooked raw
                    val list = origList.toMutableList<Any?>()

                    debugLog("fromJSON: origList.size=${origList.size}")

                    val already = list.any { item ->
                        runCatching {
                            tagField?.get(item) as? String
                                ?: (item as? Any?)?.invokeAs("getTag") as? String
                        }.getOrDefault(null) == PURIFY_UPDATE
                    }
                    if (already) {
                        debugLog("fromJSON: 已存在，跳过")
                        return@hooked list
                    }

                    val tab = tabInfoClz.newInstance()
                    tagField?.set(tab, PURIFY_UPDATE)
                    debugLog("fromJSON: tag 设置完成")

                    // titles
                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("titles").first()
                            .set(tab, mapOf("cn" to "更新", "en" to "Update"))
                        debugLog("fromJSON: titles 设置完成")
                    }.onFailure {
                        debugLog("fromJSON: titles 失败: ${it.message}")
                    }

                    // showTitle
                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("showTitle").first()
                            .set(tab, false)
                    }

                    // ========== 图标方案 ==========
                    // 方案1: 从 sDefaultTabIconResource 读取 purify_update 对应的资源 ID
                    var iconSet = false
                    runCatching {
                        val mapField = tabInfoClz.fieldFinder()
                            .filterByName("sDefaultTabIconResource").first()
                        val map = mapField.get(null) as? Map<*, *>
                        val resId = map?.get(PURIFY_UPDATE) as? Int
                        if (resId != null && resId != 0) {
                            debugLog("fromJSON: 从 sDefaultTabIconResource 获取 resId=0x${resId.toString(16)}")
                            // 设到 mNormalIconBg
                            val ctx = getAppContext()
                            if (ctx != null) {
                                val drawable = ctx.getDrawable(resId)
                                if (drawable is BitmapDrawable) {
                                    tabInfoClz.fieldFinder()
                                        .filterByName("mNormalIconBg").first()
                                        .set(tab, drawable.bitmap)
                                    iconSet = true
                                    debugLog("fromJSON: icon from sDefaultTabIconResource OK")
                                }
                            }
                        }
                    }.onFailure {
                        debugLog("fromJSON: sDefaultTabIconResource 读取失败: ${it.message}")
                    }

                    // 方案2: 从 ic_purify_update drawable 资源
                    if (!iconSet) {
                        runCatching {
                            val ctx = getAppContext()
                            if (ctx == null) {
                                debugLog("fromJSON: appCtx null, icon 方案2 跳过")
                                return@runCatching
                            }
                            val resId = ctx.resources.getIdentifier(
                                "ic_purify_update", "drawable", ctx.packageName
                            )
                            debugLog("fromJSON: ic_purify_update resId=0x${resId.toString(16)}")
                            if (resId != 0) {
                                val drawable = ctx.getDrawable(resId)
                                if (drawable is BitmapDrawable) {
                                    tabInfoClz.fieldFinder()
                                        .filterByName("mNormalIconBg").first()
                                        .set(tab, drawable.bitmap)
                                    iconSet = true
                                    debugLog("fromJSON: icon from ic_purify_update OK")
                                }
                            }
                        }.onFailure {
                            debugLog("fromJSON: icon 方案2 异常: ${it.message}")
                        }
                    }

                    // 方案3: 用默认图标（下载管理器图标）兜底
                    if (!iconSet) {
                        runCatching {
                            val ctx = getAppContext()
                            if (ctx == null) return@runCatching
                            // android.R.drawable.stat_sys_download 作为默认图标
                            val defaultResId = android.R.drawable.stat_sys_download
                            val drawable = ctx.getDrawable(defaultResId)
                            if (drawable is BitmapDrawable) {
                                tabInfoClz.fieldFinder()
                                    .filterByName("mNormalIconBg").first()
                                    .set(tab, drawable.bitmap)
                                iconSet = true
                                debugLog("fromJSON: icon 兜底 stat_sys_download")
                            }
                        }.onFailure {
                            debugLog("fromJSON: icon 兜底失败: ${it.message}")
                        }
                    }

                    if (!iconSet) {
                        debugLog("fromJSON: 所有图标方案均失败")
                    }

                    list.add(tab)
                    debugLog("fromJSON: 注入完成, total=${list.size}")
                    return@hooked list
                }

            debugLog("hook 已安装")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG,
                "[UpdateTabEntry] hook 失败: ${it.message}", it)
        }
    }

    /** 多种方式尝试获取 Application Context */
    private fun getAppContext(): android.content.Context? {
        // 方案1: ActivityThread.currentApplication()
        runCatching {
            val app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? android.content.Context
            if (app != null) return app
        }

        // 方案2: 通过当前类的 ClassLoader 找到已加载的 ActivityThread
        runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            // 反射获取 sCurrentActivityThread
            val field = atClass.getDeclaredField("sCurrentActivityThread")
            field.isAccessible = true
            val at = field.get(null)
            if (at != null) {
                val getApplication = atClass.getMethod("getApplication")
                val app = getApplication.invoke(at) as? android.content.Context
                if (app != null) return app
            }
        }

        // 方案3: 通过 LoadedApk 获取
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentThread = activityThread.getMethod("currentActivityThread").invoke(null)
            if (currentThread != null) {
                val mBoundApplication = activityThread
                    .getDeclaredField("mBoundApplication")
                mBoundApplication.isAccessible = true
                val data = mBoundApplication.get(currentThread)
                if (data != null) {
                    val infoField = data.javaClass.getDeclaredField("info")
                    infoField.isAccessible = true
                    val loadedApk = infoField.get(data)
                    if (loadedApk != null) {
                        val getApplication = loadedApk.javaClass.getMethod("getApplication")
                        val app = getApplication.invoke(loadedApk) as? android.content.Context
                        if (app != null) return app
                    }
                }
            }
        }

        return null
    }
}
