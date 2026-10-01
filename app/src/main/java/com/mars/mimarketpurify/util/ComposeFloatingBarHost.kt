package com.mars.mimarketpurify.util

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mars.mimarketpurify.R
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.NavIcons
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.WeakHashMap

/**
 * 用 Compose + 本仓库 util.FloatingTabBar（MiuiX 悬浮底栏，对标 HyperModifier 的 MiuixFloatingTabBar）
 * 重写悬浮底栏的宿主（「MiuiX 方案」）。
 *
 * 关键点：
 * - 容器采用自建的 [FloatingTabBar]（胶囊容器 + 滑动胶囊指示器 + 图标文字竖排），图标全部用
 *   [NavIcons] 在代码中自绘的 [ImageVector]（单色、随主题着色、随暗色自动反色），不再打包或引用
 *   商店任何位图，彻底规避 AndResGuard 资源混淆、零维护。
 * - 毛玻璃沿用既有 ViewBackdropSampler + LayerBackdrop 实时采样方案（Miuix 内置模糊），
 *   采样缺失时由胶囊半透明色兜底。
 *
 * 原生 TabView 仍负责导航、埋点与页面切换（点击转发给原生 tab 的 [View.performClick]），
 * 本类只接管呈现层，复用 NativeTabBar 反射读取与 FloatingBottomBar 生命周期骨架。
 */
/**
 * 待更新应用数缓存（会话级）。
 *
 * 商店把「待更新应用数」挂在「我的」标签的红点/数字上。若用户把「我的」筛掉，原生 TabView 不复存在、
 * 该数据源随之消失，导致「更新」标签角标丢失。这里在每次同步时从「我的」TabView 读取并缓存其最新值，
 * 缓存值在本会话内持续有效——即便随后筛选掉「我的」，只要本次会话「我的」曾渲染过，待更新数仍会
 * 归并显示到「更新」标签。
 */
private object UpdateBadgeState {
    @Volatile var count: Int = 0
}

class ComposeFloatingBarHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    initBottomContainer: View,
    initBasicModeContainer: View?,
    initTabLayout: View,
    initContent: View,
    initNavPlaceholder: View?,
    samplingView: View,
) {
    private var originalBottomContainer: View = initBottomContainer
    private var basicModeContainer: View? = initBasicModeContainer
    private var nativeTabLayout: View = initTabLayout
    private var contentView: View = initContent
    private var navigationBarPlaceholder: View? = initNavPlaceholder

    private val resources = activity.resources
    private val originalBottomAlpha = originalBottomContainer.alpha
    private val originalBottomA11y = originalBottomContainer.importantForAccessibility
    private val originalPlaceholderVisibility = navigationBarPlaceholder?.visibility

    private var state by mutableStateOf(MarketNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)

    private val owner = InjectedViewTreeOwner()
    private val previousLifecycleOwner = overlayParent.findViewTreeLifecycleOwner()
    private val previousViewModelStoreOwner = overlayParent.findViewTreeViewModelStoreOwner()
    private val previousSavedStateRegistryOwner = overlayParent.findViewTreeSavedStateRegistryOwner()

    private val composeView: ComposeView = ComposeView(activity)
    private val sampler = ViewBackdropSampler(
        source = samplingView,
        excludedView = composeView,
        pixelCopyWindow = activity.window,
        usePixelCopySampling = { true },
        onSnapshotChanged = { backdropSnapshot = it },
    )
    private val visibility = FloatingNavigationVisibility(composeView)
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncNativeState()
        sampler.onFrame()
        true
    }

    init {
        overlayParent.setViewTreeLifecycleOwner(owner)
        overlayParent.setViewTreeViewModelStoreOwner(owner)
        overlayParent.setViewTreeSavedStateRegistryOwner(owner)

        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MarketNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = sampler::setNavigationBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }

        syncNativeState()
        overlayParent.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        overlayParent.viewTreeObserver.takeIf { it.isAlive }
            ?.addOnPreDrawListener(preDrawListener)
        Log.i(TAG, "ComposeFloatingBarHost 已挂载")
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        visibility.dispose()
        sampler.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        (composeView.parent as? ViewGroup)?.removeView(composeView)
        originalBottomContainer.alpha = originalBottomAlpha
        originalBottomContainer.importantForAccessibility = originalBottomA11y
        originalPlaceholderVisibility?.let { navigationBarPlaceholder?.visibility = it }
        overlayParent.setViewTreeLifecycleOwner(previousLifecycleOwner)
        overlayParent.setViewTreeViewModelStoreOwner(previousViewModelStoreOwner)
        overlayParent.setViewTreeSavedStateRegistryOwner(previousSavedStateRegistryOwner)
        owner.dispose()
        NativeTabBar.clearBarCache(activity)
        Log.i(TAG, "ComposeFloatingBarHost 已释放")
    }

    private fun updateNativeChromeReplacement(enabled: Boolean) {
        if (enabled) {
            if (originalBottomContainer.alpha != 0f) originalBottomContainer.alpha = 0f
            if (originalBottomContainer.importantForAccessibility !=
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            ) {
                originalBottomContainer.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            if (navigationBarPlaceholder?.visibility != View.GONE) {
                navigationBarPlaceholder?.visibility = View.GONE
            }
            // 崩溃兜底：被「移花接木」注入的原生「更新」tab 在部分商店版本下，其无障碍节点
            // createAccessibilityNodeInfo 会尝试解析一个不存在的字符串资源而抛
            // Resources$NotFoundException（见 AppErrorsTracking 上报）。悬浮态下该 tab 已被
            // 独立更新按钮替代，这里直接将其重要度降级，阻止无障碍预取其虚拟节点。
            NativeTabBar.tabViewsOf(nativeTabLayout)
                .firstOrNull { NativeTabBar.tagOf(it) == UPDATE_TAB_TAG }
                ?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            originalBottomContainer.alpha = originalBottomAlpha
            originalBottomContainer.importantForAccessibility = originalBottomA11y
            originalPlaceholderVisibility?.let { navigationBarPlaceholder?.visibility = it }
        }
    }

    private fun syncNativeState() {
        // 实时响应悬浮底栏总开关：关闭后即便 Activity 生命周期未重新触发（如模块内即时切换开关），
        // 也在下一帧自释放并还原原生底栏，避免「关了开关底栏还在」的残留。
        // 仅当偏好可读且开关确实关闭时才自释放；偏好暂不可读（service 抖动）时保留现状，
        // 避免误卸载导致「悬浮底栏失效」且无法自愈。
        val floatingOn = Settings.isEnabled(Settings.KEY_FLOATING_BAR, false)
        if (!floatingOn && Settings.remotePrefsAvailable()) {
            dispose()
            return
        }
        val nativeTabs = NativeTabBar.tabViewsOf(nativeTabLayout)
        val selected = NativeTabBar.selectedIndexOf(nativeTabLayout)
            .coerceIn(0, (nativeTabs.size - 1).coerceAtLeast(0))
        val tabs = nativeTabs.mapIndexed { index, tab -> readTabState(tab, index, index == selected) }
        // 待更新应用数量：优先取会话内缓存的「我的」标签待更新数（屏蔽「我的」后仍有效），
        // 再与当前可见 tab 的数字角标取最大值；移花接木开启时统一归并到「更新」标签显示，其余标签隐藏角标。
        val updateEntryOn = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)
        val updateCount = if (updateEntryOn) {
            maxOf(UpdateBadgeState.count, tabs.maxOfOrNull { it.number } ?: 0).takeIf { it > 0 } ?: 0
        } else 0
        // 移花接木开启：其余标签一律隐藏角标（红点/数字），仅「更新」标签显示待更新数量。
        val effectiveTabs = if (updateEntryOn) {
            tabs.map { tab ->
                if (tab.isUpdate) tab.copy(badge = false, badgeNumber = updateCount)
                else tab.copy(badge = false, badgeNumber = 0)
            }
        } else {
            tabs
        }
        // 移花接木（底栏更新入口）开启、且原生底栏未自带「更新」TabView 时，
        // MarketNavigationContent 会用 effectiveTabs 补一个合成「更新」项，
        // 此时有效 tab 数为「原生数 + 1」。
        val hasNativeUpdate = tabs.any { it.isUpdate }
        val syntheticUpdate = updateEntryOn && !hasNativeUpdate
        val nativeBarPresent = originalBottomContainer.visibility == View.VISIBLE &&
            nativeTabLayout.visibility == View.VISIBLE &&
            basicModeContainer?.visibility != View.VISIBLE
        // 悬浮底栏在「商店本应展示底栏」且「有可展示的 tab」时显示：
        //  - 不再要求原生 tab 数 > 1（筛选到只剩首页时也应显示悬浮胶囊，否则整条底栏消失）；
        //  - 移花接木注入「更新」后有效 tab 数 ≥ 2；即便某些版本未为注入项生成 TabView，
        //    syntheticUpdate 也会补出合成项，此时只要原生 tab 非空就照样展示悬浮胶囊。
        val visible = tabs.isNotEmpty() && (nativeBarPresent || syntheticUpdate)
        // 实时读取外观配置，纳入 state 相等性判断——配置变化即触发重组，颜色/圆角/间距即时生效。
        val showLabel = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)
        val radiusDp = Settings.floatingBarRadiusDp()
        val bottomMarginDp = Settings.floatingBarBottomMarginDp()
        val barColorArgb = Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1)
        val indicatorColorArgb = Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1)
        val textSelectedArgb = Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1)
        val textNormalArgb = Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1)
        // 液态高亮开关：选中胶囊改用毛玻璃液态效果（与设置页「液态选中高亮动画 / 3D液态效果」对应）。
        val liquid = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LIQUID, false)
        val liquid3d = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LIQUID_3D, false)
        val next = MarketNavigationState(
            tabs = effectiveTabs,
            selectedIndex = selected,
            visible = visible,
            showLabel = showLabel,
            radiusDp = radiusDp,
            bottomMarginDp = bottomMarginDp,
            barColorArgb = barColorArgb,
            indicatorColorArgb = indicatorColorArgb,
            textSelectedArgb = textSelectedArgb,
            textNormalArgb = textNormalArgb,
            updateCount = updateCount,
            liquid = liquid,
            liquid3d = liquid3d,
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        updateNativeChromeReplacement(next.visible)
        visibility.setVisible(next.visible)
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

    /** 原生 TabView 图标由 [NativeTabIconSnapshotter] 抓成四态位图（已迁移到 [readTabState]）。 */

    private fun readTabState(tab: View, index: Int, selected: Boolean): MarketTabState {
        val label = NativeTabBar.titleOf(tab)
            ?: FALLBACK_LABELS.getOrElse(index) { "入口 ${index + 1}" }
        val tag = NativeTabBar.tagOf(tab).orEmpty()
        val hasRedPoint = NativeTabBar.hasRedPoint(tab)
        val number = NativeTabBar.numberOf(tab)
        // 抓取原生图标四态（选中/未选中 + 单色），对齐 HyperModifier 的 NativeTabIconSnapshotter；
        // 单色开关由 MarketNavigationContent 按 Settings 决定使用哪一态。
        val iconView = NativeTabBar.iconViewOf(tab)
        val icons = iconSnapshotter.snapshot(tab, iconView, selected)
        // 捕获「我的」标签上的待更新数：缓存其红点/数字，供「更新」标签兜底显示。
        // （筛选掉「我的」后该 TabView 消失，但缓存值在本会话内仍有效。）
        if (tag.contains("mine", ignoreCase = true) || label.contains("我的")) {
            UpdateBadgeState.count = if (hasRedPoint) maxOf(number, 1) else number
        }
        // 角标默认按「底栏角标净化」总开关（KEY_TAB_BADGE，即设置里「我的」页的「底栏角标」）决定：
        // 关闭净化（保留角标）时显示原生红点/数字。开启移花接木后，syncNativeState 会统一把
        // 待更新数归并到「更新」标签、其余标签隐藏（覆盖此处结果）。
        val badge = !Settings.isEnabled(Settings.KEY_TAB_BADGE, true) &&
            (hasRedPoint || number > 0)
        return MarketTabState(
            nativeIndex = index,
            label = label,
            tag = tag,
            badge = badge,
            number = number,
            icons = icons,
            isUpdate = tag == UPDATE_TAB_TAG || label.contains("更新"),
        )
    }

    private fun selectDestination(index: Int, isUpdate: Boolean) {
        // 「移花接木」更新入口：直达应用商店更新页，不再依赖原生 dummy 碎片（此前点击无效果）。
        if (isUpdate) {
            openMarketUpdatePage()
            return
        }
        // 不复用 attach 时捕获的 nativeTabLayout：商店在某些场景（配置变更 / 页面重建）会重建底栏容器，
        // 旧引用已 detach，performClick 打到空 View 会导致「点了没反应」。每次点击按 activity 重新定位，
        // 命中当前存活的原生 TabView。顺序与 syncNativeState 的 nativeIndex 一致（同源 tabContainerIn）。
        val tab = NativeTabBar.tabViews(activity).getOrNull(index) ?: return
        state = state.copy(selectedIndex = index)
        runCatching { tab.performClick() }
        composeView.post { runCatching { syncNativeState() } }
        sampler.requestCaptureBurst()
    }

    /**
     * 移花接木：点击「更新」入口时直达应用商店的「应用更新 / 升级」页面。
     *
     * 注意：更新页并不是 `market://update` 这种深链（该 scheme 系统里并不存在，所以此前点了
     * 「没反应」——Intent 解析不到任何组件）。真正的更新页是一个**独立 Activity**
     * （多版本一致为 `com.xiaomi.market.ui.UpdateListActivity`），这里显式启动它。
     */
    private fun openMarketUpdatePage() {
        // 直达应用商店「应用更新 / 升级」页：先试已知 Activity 类名，再退回 Manifest 模糊匹配
        // （逻辑统一在 [MarketUpdateLauncher]，悬浮底栏与原生「更新」tab 点击共用）。
        if (MarketUpdateLauncher.launch(activity)) return
        // 兜底：触发原生「更新」TabView 的点击（若该版本确实生成了对应 TabView），避免完全无效果。
        // 同样用 activity 重新定位，避免使用已 detach 的旧 nativeTabLayout 引用。
        runCatching {
            NativeTabBar.tabViews(activity)
                .firstOrNull { NativeTabBar.tagOf(it) == UPDATE_TAB_TAG }
                ?.performClick()
        }
    }

    companion object {
        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")
        private val active = WeakHashMap<Activity, ComposeFloatingBarHost>()

        private fun findBottomBar(activity: Activity): View? =
            NativeTabBar.bottomContainer(activity)

        fun attach(activity: Activity): ComposeFloatingBarHost? {
            if (activity.isFinishing || activity.isDestroyed) return null
            active[activity]?.let { return it }
            // 启动期压制官方底栏，消除「原生先闪一帧再被替换」的闪烁；
            // 在宿主接管前还原，使宿主读取到官方真实 alpha 后再自行持续压制。
            val suppressor = EarlyBottomBarSuppressor(activity, findBottomBar = { findBottomBar(activity) })
            suppressor.start()
            suppressor.restore()
            val host = create(activity) ?: return null
            active[activity] = host
            return host
        }

        fun get(activity: Activity): ComposeFloatingBarHost? = active[activity]

        fun release(activity: Activity) {
            active.remove(activity)?.dispose()
        }

        private fun create(activity: Activity): ComposeFloatingBarHost? = runCatching {
            // 定位策略（对齐 HyperModifier MarketFloatingNavigation.create）：
            //   1) 资源名 tab_container_layout(outer) + tab_container(tabs)；
            //   2) 方法名回退：暴露 getTabViews() 的容器。
            // 返回 outer（整条原生底栏，用于隐藏）+ tabs（直接子项即 TabView 的容器，用于读取/点击）。
            val ref = NativeTabBar.locateBottomBar(activity) ?: run {
                val idLayout = activity.resources.getIdentifier("tab_container_layout", "id", activity.packageName)
                val idTabs = activity.resources.getIdentifier("tab_container", "id", activity.packageName)
                Log.w(
                    TAG,
                    "悬浮底栏：locateBottomBar 失败 id(tab_container_layout)=$idLayout " +
                        "id(tab_container)=$idTabs（两者均非 0 才会命中；getTabViews 兜底也未命中）",
                )
                return null
            }
            val bottom = ref.outer
            val tabLayout = ref.tabs
            // 内容容器：多数版本主界面用 fragment_container 承载页面碎片；但部分商店版本（或早期
            // 注入时机）该 id 并不存在。它仅用于「页面内容延伸到浮层之下」的视觉参考，并非挂载必需
            // —— 而 samplingView 主源已用 android.R.id.content 兜底，故此处同样用 decorView 的
            // content 兜底，避免「找不到 fragment_container 就整个浮层直接 return null、悬浮底栏永不挂载」。
            val content = NativeTabBar.viewByResName(activity, "fragment_container")
                ?: activity.findViewById<View>(android.R.id.content)
                ?: return null
            val samplingView =
                activity.findViewById<View>(android.R.id.content) ?: content
            val overlayParent = activity.window.decorView as? ViewGroup ?: return null
            ComposeFloatingBarHost(
                activity = activity,
                overlayParent = overlayParent,
                initBottomContainer = bottom,
                initBasicModeContainer = NativeTabBar.viewByResName(activity, "tab_basic_mode_container_layout"),
                initTabLayout = tabLayout,
                initContent = content,
                initNavPlaceholder = NativeTabBar.viewByResName(activity, "navigation_bar_placeholder"),
                samplingView = samplingView,
            )
        }.onFailure {
            Log.e(TAG, "ComposeFloatingBarHost 挂载失败", it)
        }.getOrNull()
    }
}

/** TabFilter（移花接木）注入到原生底栏的更新 tab 的 tag，需与 TabFilter.PURIFY_UPDATE 一致。 */
private const val UPDATE_TAB_TAG = "purify_update"

private data class MarketTabState(
    val nativeIndex: Int,
    val label: String,
    val tag: String,
    val badge: Boolean,
    /** 原生 tab 的数字角标（待更新应用数等），用于「更新」标签归并显示。 */
    val number: Int = 0,
    /** 数字角标；>0 时悬浮底栏显示数字（覆盖红点）。 */
    val badgeNumber: Int = 0,
    /** 抓自原生 TabView 的图标四态位图（选中/未选中 + 单色），为 null 则退化为自绘 [icon]。 */
    val icons: NativeTabIconPair? = null,
    /** 该项是不是「移花接木」注入的更新入口。 */
    val isUpdate: Boolean = false,
) {
    /** 按标题/标签把原生 Tab 映射到自绘图标；匹配不到返回 null（该项仅显示文字）。 */
    fun icon(): ImageVector? = resolveIcon(label, tag)
}

private data class MarketNavigationState(
    val tabs: List<MarketTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
    // 外观配置：纳入相等性判断，配置变化即触发重组，使「悬浮底栏配置」页的改动返回商店后即时生效。
    val showLabel: Boolean = true,
    val radiusDp: Int = 29,
    val bottomMarginDp: Int = 4,
    val barColorArgb: Int = -1,
    val indicatorColorArgb: Int = -1,
    val textSelectedArgb: Int = -1,
    val textNormalArgb: Int = -1,
    /** 待更新应用数（移花接木开启时归并到「更新」标签显示）。 */
    val updateCount: Int = 0,
    /** 选中胶囊液态高亮开关。 */
    val liquid: Boolean = false,
    /** 3D 液态高亮开关。 */
    val liquid3d: Boolean = false,
)

/**
 * 把原生 Tab 的标题/标签映射到自绘图标（[NavIcons]）。
 * 匹配不到返回 null（该项仅显示文字）。图标随主题着色、随暗色自动反色，无需区分 n/p 双态或
 * 亮/暗资源——这正是改用矢量自绘后相比「打包栅格 WebP」最大的简化。
 */
private fun resolveIcon(label: String, tag: String): ImageVector? {
    val l = label.trim()
    val t = tag.trim().lowercase()
    return when {
        l.contains("首页") || t.contains("home") || t.contains("index") -> NavIcons.Home
        l.contains("游戏") || t.contains("game") -> NavIcons.Game
        l.contains("排行") || l.contains("榜单") || t.contains("rank") -> NavIcons.Rank
        l.contains("视频") || l.contains("短剧") || l.contains("剧") || l.contains("影视") -> NavIcons.List
        l.contains("软件") || t.contains("soft") -> NavIcons.Apps
        l.contains("我的") || t.contains("mine") || l.contains("账户") -> NavIcons.Person
        l.contains("分类") || t.contains("category") -> NavIcons.List
        // 移花接木：TabFilter 注入的更新入口（tag=purify_update，标题「更新」）
        l.contains("更新") || t.contains(UPDATE_TAB_TAG) || t.contains("update") -> NavIcons.Update
        // 兜底：未命中时使用通用应用图标，避免纯文字（短剧等标签优先以原生图标呈现，见 readTabState）。
        else -> NavIcons.Apps
    }
}

@Composable
private fun MarketNavigationContent(
    state: MarketNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int, Boolean) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = (LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val backdrop = rememberLayerBackdrop()
    val showLabel = state.showLabel
    val bottomMargin = state.bottomMarginDp

    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        val colors = MiuixTheme.colorScheme
        // 外观配置统一取自 state：由 syncNativeState 实时读取并纳入相等性判断，
        // 因此「悬浮底栏配置」页的改动在返回商店后立即生效，无需重启。
        val radius = state.radiusDp.dp
        val indicatorColor = if (state.indicatorColorArgb == -1) null else Color(state.indicatorColorArgb)
        val textSelected = if (state.textSelectedArgb == -1) null else Color(state.textSelectedArgb)
        val textNormal = if (state.textNormalArgb == -1) null else Color(state.textNormalArgb)
        val barColor = if (state.barColorArgb == -1) null else Color(state.barColorArgb)

        // 「移花接木」更新入口：原生底栏已注入该 tab 时直接复用（isUpdate=true）；
        // 若某些商店版本未为注入项生成对应 TabView（导致胶囊里看不到「更新」），
        // 这里补一个合成项兜底，保证入口一定可见、可点。
        val hasUpdate = state.tabs.any { it.isUpdate }
        val showUpdateEntry = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)
        val effectiveTabs = if (showUpdateEntry && !hasUpdate) {
            state.tabs + MarketTabState(
                nativeIndex = -1,
                label = "更新",
                tag = UPDATE_TAB_TAG,
                badge = false,
                badgeNumber = state.updateCount,
                isUpdate = true,
            )
        } else {
            state.tabs
        }

        // 全部原生 tab（含「移花接木」注入的更新入口）统一以胶囊内 tab 呈现，
        // 点击走原生 performClick；更新 tab 点击直达更新页（见 selectDestination）。
        val monochrome = Settings.isEnabled(Settings.KEY_FLOATING_BAR_MONOCHROME, true)
        val items = effectiveTabs.map { tab ->
            FloatingTabItem(
                key = tab.nativeIndex.toString(),
                label = if (showLabel) tab.label else "",
                icon = tab.icon(),
                iconBitmapSelected = tab.icons?.selected,
                iconBitmapUnselected = tab.icons?.unselected,
                iconMonochrome = if (monochrome) tab.icons?.selectedMonochrome else null,
                preserveOriginalIconColors = !monochrome && tab.icons != null,
                badge = tab.badge,
                badgeNumber = tab.badgeNumber,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = bottomMargin.dp)
                // 关键：容器显式固定为底栏高度。ViewBackdropLayer 会按采样区域 requiredSize，
                // 若父容器高度由内容决定，就会被它撑高并随采样范围不断放大（正反馈），最终
                // 撑满整个 decorView，把底栏顶到屏幕顶部。固定高度后不再受其影响。
                .height(FloatingTabBarDefaults.Height)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    onBackdropBoundsChanged(
                        ViewBackdropBounds(
                            left = pos.x.roundToInt(),
                            top = pos.y.roundToInt(),
                            width = coords.size.width,
                            height = coords.size.height,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // 毛玻璃内容源：图像 alpha ≈ 0（不可见），只为 LayerBackdrop 提供底图。
            // 它不再参与上层尺寸测量，缺帧时由底栏自身的半透明色兜底。
            ViewBackdropLayer(backdropSnapshot, backdrop)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FloatingTabBar(
                    items = items,
                    selectedIndex = state.selectedIndex,
                    onSelect = { index ->
                        effectiveTabs.getOrNull(index)?.let {
                            onDestinationSelected(it.nativeIndex, it.isUpdate)
                        }
                    },
                    layout = FloatingTabLayout.Stacked,
                    showLabel = showLabel,
                    radius = radius,
                    barColor = barColor,
                    indicatorColor = indicatorColor,
                    contentSelectedColor = textSelected,
                    contentNormalColor = textNormal,
                    backdrop = backdrop,
                    expandWidth = false,
                    liquid = state.liquid,
                    liquid3d = state.liquid3d,
                )
            }
        }
    }
}
