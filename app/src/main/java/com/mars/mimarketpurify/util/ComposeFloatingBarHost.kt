package com.mars.mimarketpurify.util

import android.app.Activity
import android.content.res.Configuration
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mars.mimarketpurify.R
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.WeakHashMap

/**
 * 用 Compose + Miuix 官方 [FloatingNavigationBar] 重写悬浮底栏的宿主（「MiuiX 方案」）。
 *
 * 关键点：
 * - 容器采用官方 [FloatingNavigationBar]（悬浮圆角 + 阴影 + 窗口边距 + 分隔线），内部 item 为
 *   自定义实现，以便直接渲染**应用商店安装包内的官方 Tab 图标**——这些图标已拷贝进本模块
 *   res/drawable-nodpi（规避商店 AndResGuard 资源混淆；混淆映射把 tab_icon_index_n 重命名为
 *   res/7Lo.webp 等，运行时按名引用必然失败，故打包自带最稳）。
 * - 图标保留未选/选中双态（_n/_p WebP）、多色原色（tint = Unspecified）、文字标签、角标与
 *   Role.Tab 无障碍语义；暗色用商店官方 _darkmode PNG，分类无暗色资源则复用亮色 WebP。
 * - 毛玻璃沿用既有 ViewBackdropSampler + LayerBackdrop 实时采样方案（Miuix 内置模糊），
 *   采样缺失时回退半透明色块。
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
        val next = MarketNavigationState(tabs = tabs, selectedIndex = selected, visible = visible)
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
        )
    }

    private fun selectDestination(index: Int) {
        val tab = NativeTabBar.tabViewsOf(nativeTabLayout).getOrNull(index) ?: return
        state = state.copy(selectedIndex = index)
        runCatching { tab.performClick() }
        composeView.post { runCatching { syncNativeState() } }
        sampler.requestCaptureBurst()
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

private data class MarketTabState(
    val nativeIndex: Int,
    val label: String,
    val tag: String,
    val badge: Boolean,
) {
    /** 按标题/标签把原生 Tab 映射到官方图标键；匹配不到返回 null（该项仅显示文字）。 */
    fun iconKey(): String? = resolveIconKey(label, tag)
}

private data class MarketNavigationState(
    val tabs: List<MarketTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
)

/**
 * 官方 Tab 图标（已拷贝进本模块 res/drawable-nodpi，规避商店 AndResGuard 资源混淆）。
 * 亮色用 WebP（index/game/rank/soft/mine/category 的 _n/_p），暗色用官方 _darkmode PNG。
 */
private val ICON_PAIRS_LIGHT = mapOf(
    "index" to (R.drawable.tab_index_n to R.drawable.tab_index_p),
    "game" to (R.drawable.tab_game_n to R.drawable.tab_game_p),
    "rank" to (R.drawable.tab_rank_n to R.drawable.tab_rank_p),
    "soft" to (R.drawable.tab_soft_n to R.drawable.tab_soft_p),
    "mine" to (R.drawable.tab_mine_n to R.drawable.tab_mine_p),
    "category" to (R.drawable.tab_category_n to R.drawable.tab_category_p),
)

private val ICON_PAIRS_DARK = mapOf(
    "index" to (R.drawable.tab_index_n_dark to R.drawable.tab_index_p_dark),
    "game" to (R.drawable.tab_game_n_dark to R.drawable.tab_game_p_dark),
    "rank" to (R.drawable.tab_rank_n_dark to R.drawable.tab_rank_p_dark),
    "soft" to (R.drawable.tab_soft_n_dark to R.drawable.tab_soft_p_dark),
    "mine" to (R.drawable.tab_mine_n_dark to R.drawable.tab_mine_p_dark),
    // 分类无暗色资源，复用亮色 WebP
)

private fun resolveIconKey(label: String, tag: String): String? {
    val l = label.trim()
    val t = tag.trim().lowercase()
    return when {
        l.contains("首页") || t.contains("home") || t.contains("index") -> "index"
        l.contains("游戏") || t.contains("game") -> "game"
        l.contains("排行") || l.contains("榜单") || t.contains("rank") -> "rank"
        l.contains("软件") || t.contains("soft") -> "soft"
        l.contains("我的") || t.contains("mine") || t.contains("账户") -> "mine"
        l.contains("分类") || t.contains("category") -> "category"
        else -> null
    }
}

/** 解析出官方 Tab 图标的 drawable 资源 id（0 表示无对应图标，仅显示文字）。 */
private fun iconRes(key: String?, selected: Boolean, dark: Boolean): Int {
    if (key == null) return 0
    val pair = (if (dark) ICON_PAIRS_DARK[key] else null) ?: ICON_PAIRS_LIGHT[key] ?: return 0
    return if (selected) pair.second else pair.first
}

@Composable
private fun MarketNavigationContent(
    state: MarketNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = (LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val backdrop = rememberLayerBackdrop()
    val showLabel = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)
    val selectedKey = state.tabs.firstOrNull { it.nativeIndex == state.selectedIndex }?.nativeIndex
        ?: state.tabs.first().nativeIndex

    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        ) {
            // 毛玻璃：复用 Miuix 内置模糊，采样缺失时由 FloatingNavigationBar 半透明色块兜底。
            ViewBackdropLayer(backdropSnapshot, backdrop)
            FloatingNavigationBar(
                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
                cornerRadius = 28.dp,
                horizontalOutSidePadding = 0.dp,
                shadowElevation = 1.dp,
                showDivider = false,
                defaultWindowInsetsPadding = true,
                modifier = Modifier
                    .fillMaxWidth()
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
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.tabs.forEach { tab ->
                        val selected = tab.nativeIndex == selectedKey
                        MarketFloatingTabItem(
                            selected = selected,
                            onClick = { onDestinationSelected(tab.nativeIndex) },
                            iconRes = iconRes(tab.iconKey(), selected, dark),
                            label = if (showLabel) tab.label else "",
                            badge = tab.badge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 悬浮底栏的单个入口：官方多色 Tab 图标（_n/_p 双态）+ 可选文字标签 + 可选角标 + Role.Tab 语义。
 * 选中态用中性半透明胶囊背景，避免拍平官方图标原有的多色。
 */
@Composable
private fun MarketFloatingTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    badge: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.onSurface.luminance() > 0.5f
    val contentColor = if (selected) colors.primary else colors.onSurfaceSecondary
    val indicatorColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) indicatorColor else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.TopEnd) {
                if (iconRes != 0) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (badge) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.error),
                    )
                }
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
