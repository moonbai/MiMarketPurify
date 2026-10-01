package com.mars.mimarketpurify.hooks.market

import android.content.res.Resources
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

/**
 * 崩溃兜底（AndResGuard 剥离资源导致的 [Resources.NotFoundException]）。
 *
 * 背景：商店 4.126.x 在部分版本 / 部分 ROM（如 HyperOS Android 17）下，其底栏 TabView 的
 * `createAccessibilityNodeInfo` 会按资源 id 去 [Resources.getText] / [Resources.getString]
 * 取无障碍标签，而该 id 已被 AndResGuard 剥离、并不存在于运行期资源表里，于是抛出
 * `Resources$NotFoundException`，并在无障碍服务预取节点时直接闪退（见 AppErrorsTracking 上报，
 * 设备 Android 17 / 商店 4.126.10）。
 *
 * 该崩溃与「悬浮底栏」「更新入口」等具体开关无关，属于资源层问题；单纯把注入 tab 的
 * `importantForAccessibility` 降级也未必够——若底栏容器使用自定义 AccessibilityNodeProvider，
 * 会直接为每个 TabView 构造节点而绕过子 View 的 important 标记。因此这里在资源取用这一层
 * 做**通用兜底**：仅当资源确实缺失（抛 [Resources.NotFoundException]）时返回空串，避免闪退；
 * 其余情况原样放行，不影响正常资源加载。版本无关、不受 AndResGuard / 布局结构漂移影响。
 */
object ResourcesNotFoundGuard : BaseHook() {

    /** 崩溃兜底属于安全网，不受单功能开关控制、只要模块总开关开启即生效。 */
    override val prefKey: String? = null

    override val name: String get() = "资源缺失兜底"

    override fun init() {
        // getText(int)：无障碍节点预取时正是按资源 id 取字符串标签，缺失即抛 NotFoundException
        runCatching {
            Resources::class.java.methodFinder()
                .filterByName("getText")
                .filterByParamCount(1)
                .forEach { m ->
                    m.hooked {
                        runCatching { proceed() }.getOrElse { e ->
                            if (e is Resources.NotFoundException) {
                                debugLog("getText 命中缺失资源，兜底返回空串: ${e.message}")
                                return@hooked ""
                            }
                            throw e
                        }
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 钩取 Resources.getText 失败，跳过", null)
        }

        // getString(int)：部分路径直接走 getString，同样兜底（getString 内部也会调用 getText）
        runCatching {
            Resources::class.java.methodFinder()
                .filterByName("getString")
                .filterByParamCount(1)
                .forEach { m ->
                    m.hooked {
                        runCatching { proceed() }.getOrElse { e ->
                            if (e is Resources.NotFoundException) {
                                debugLog("getString 命中缺失资源，兜底返回空串")
                                return@hooked ""
                            }
                            throw e
                        }
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 钩取 Resources.getString 失败，跳过", null)
        }
    }
}
