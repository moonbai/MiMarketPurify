package com.mars.mimarketpurify.util

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
        val nativeTabs = NativeTabBar.tabViewsOf(nativeTabLayout)
        val selected = NativeTabBar.selectedIndexOf(nativeTabLayout)
            .coerceIn(0, (nativeTabs.size - 1).coerceAtLeast(0))
        val tabs = nativeTabs.mapIndexed { index, tab -> readTabState(tab, index) }
        val visible = originalBottomContainer.visibility == View.VISIBLE &&
            nativeTabLayout.visibility == View.VISIBLE &&
            basicModeContainer?.visibility != View.VISIBLE &&
            tabs.size > 1
        // 实时读取外观配置，纳入 state 相等性判断——配置变化即触发重组，颜色/圆角/间距即时生效。
        val showLabel = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)
        val radiusDp = Settings.floatingBarRadiusDp()
        val bottomMarginDp = Settings.floatingBarBottomMarginDp()
        val barColorArgb = Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1)
        val indicatorColorArgb = Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1)
        val textSelectedArgb = Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1)
        val textNormalArgb = Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1)
        val next = MarketNavigationState(
            tabs = tabs,
            selectedIndex = selected,
            visible = visible,
            showLabel = showLabel,
            radiusDp = radiusDp,
            bottomMarginDp = bottomMarginDp,
            barColorArgb = barColorArgb,
            indicatorColorArgb = indicatorColorArgb,
            textSelectedArgb = textSelectedArgb,
            textNormalArgb = textNormalArgb,
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        updateNativeChromeReplacement(next.visible)
        visibility.setVisible(next.visible)
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

    private fun readTabState(tab: View, index: Int): MarketTabState {
        val label = NativeTabBar.titleOf(tab)
            ?: FALLBACK_LABELS.getOrElse(index) { "入口 ${index + 1}" }
        val tag = NativeTabBar.tagOf(tab).orEmpty()
        val hasRedPoint = NativeTabBar.hasRedPoint(tab)
        val number = NativeTabBar.numberOf(tab)
        val badge = Settings.isEnabled(Settings.KEY_FLOATING_BAR_BADGE, true) &&
            !Settings.isEnabled(Settings.KEY_TAB_BADGE, true) &&
            (hasRedPoint || number > 0)
        return MarketTabState(
            nativeIndex = index,
            label = label,
            tag = tag,
            badge = badge,
            isUpdate = tag == UPDATE_TAB_TAG || label.contains("更新"),
        )
    }

    private fun selectDestination(index: Int, isUpdate: Boolean) {
        // 「移花接木」更新入口：直达应用商店更新页，不再依赖原生 dummy 碎片（此前点击无效果）。
        if (isUpdate) {
            openMarketUpdatePage()
            return
        }
        val tab = NativeTabBar.tabViewsOf(nativeTabLayout).getOrNull(index) ?: return
        state = state.copy(selectedIndex = index)
        runCatching { tab.performClick() }
        composeView.post { runCatching { syncNativeState() } }
        sampler.requestCaptureBurst()
    }

    /** 移花接木：点击「更新」入口时直接拉起应用商店更新页（market://update，即模块注入 tab 所用的深链）。 */
    private fun openMarketUpdatePage() {
        val launched = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://update"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            true
        }.getOrElse { false }
        if (launched) return
        // 兜底：仍是触发原生「更新」TabView 的点击（旧机制），避免完全无效果
        runCatching {
            NativeTabBar.tabViewsOf(nativeTabLayout)
                .firstOrNull { NativeTabBar.tagOf(it) == UPDATE_TAB_TAG }
                ?.performClick()
        }
    }

    companion object {
        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")
        private val active = WeakHashMap<Activity, ComposeFloatingBarHost>()

        private fun findBottomBar(activity: Activity): View? {
            val id = activity.resources.getIdentifier(
                "tab_container_layout",
                "id",
                activity.packageName,
            )
            return id.takeIf { it != 0 }?.let(activity::findViewById)
        }

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
            val res = activity.resources
            fun id(name: String): Int = res.getIdentifier(name, "id", activity.packageName)
            val bottom = NativeTabBar.bottomContainer(activity) ?: return null
            val tabLayout = NativeTabBar.tabContainerIn(bottom) ?: NativeTabBar.tabContainer(activity)
                ?: return null
            val content = NativeTabBar.viewByResName(activity, "fragment_container") ?: return null
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
        l.contains("软件") || t.contains("soft") -> NavIcons.Apps
        l.contains("我的") || t.contains("mine") || t.contains("账户") -> NavIcons.Person
        l.contains("分类") || t.contains("category") -> NavIcons.List
        // 移花接木：TabFilter 注入的更新入口（tag=purify_update，标题「更新」）
        l.contains("更新") || t.contains(UPDATE_TAB_TAG) || t.contains("update") -> NavIcons.Update
        else -> null
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
                isUpdate = true,
            )
        } else {
            state.tabs
        }

        // 全部原生 tab（含「移花接木」注入的更新入口）统一以胶囊内 tab 呈现，
        // 点击走原生 performClick；更新 tab 点击直达更新页（见 selectDestination）。
        val items = effectiveTabs.map { tab ->
            FloatingTabItem(
                key = tab.nativeIndex.toString(),
                label = if (showLabel) tab.label else "",
                icon = tab.icon(),
                badge = tab.badge,
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
                )
            }
        }
    }
}
