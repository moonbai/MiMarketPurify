package com.mars.mimarketpurify.util

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
        // 待更新应用数量：取所有原生 tab 数字角标的最大值（通常来自「我的」页标签），
        // 移花接木开启时统一归并到「更新」标签显示，其余标签隐藏角标。
        val updateEntryOn = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)
        val updateCount = if (updateEntryOn) tabs.maxOfOrNull { it.number }?.takeIf { it > 0 } ?: 0 else 0
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
        // 默认要求「原生底栏可见且 tab 数 > 1」才接管悬浮胶囊；
        // 但移花接木会补出「更新」项（有效 tab 数 ≥ 2），此时即便商店把原生底栏因
        // 「只剩 1 个 tab」而隐藏/置灰，也必须展示悬浮胶囊，否则整条底栏会消失。
        val nativeBarPresent = originalBottomContainer.visibility == View.VISIBLE &&
            nativeTabLayout.visibility == View.VISIBLE &&
            basicModeContainer?.visibility != View.VISIBLE
        val visible = (nativeBarPresent && tabs.size > 1) ||
            (syntheticUpdate && tabs.isNotEmpty())
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

    /** 原生 TabView 图标 → ImageBitmap（带缓存，避免每帧重建）。取不到返回 null（退化为自绘图标）。 */
    private val iconBitmapCache = WeakHashMap<View, ImageBitmap?>()
    private fun tabIconBitmap(tab: View): ImageBitmap? {
        iconBitmapCache[tab]?.let { return it }
        val bmp = runCatching {
            val iv = NativeTabBar.iconViewOf(tab) ?: return@runCatching null
            val d = iv.drawable ?: return@runCatching null
            val w = iv.width.takeIf { it > 0 } ?: 48
            val h = iv.height.takeIf { it > 0 } ?: 48
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
        iconBitmapCache[tab] = bmp
        return bmp
    }

    private fun readTabState(tab: View, index: Int): MarketTabState {
        val label = NativeTabBar.titleOf(tab)
            ?: FALLBACK_LABELS.getOrElse(index) { "入口 ${index + 1}" }
        val tag = NativeTabBar.tagOf(tab).orEmpty()
        val hasRedPoint = NativeTabBar.hasRedPoint(tab)
        val number = NativeTabBar.numberOf(tab)
        val iconBitmap = tabIconBitmap(tab)
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
            iconBitmap = iconBitmap,
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

    /**
     * 移花接木：点击「更新」入口时直达应用商店的「应用更新 / 升级」页面。
     *
     * 注意：更新页并不是 `market://update` 这种深链（该 scheme 系统里并不存在，所以此前点了
     * 「没反应」——Intent 解析不到任何组件）。真正的更新页是一个**独立 Activity**
     * （多版本一致为 `com.xiaomi.market.ui.UpdateListActivity`），这里显式启动它。
     */
    private fun openMarketUpdatePage() {
        if (launchMarketUpdatePage()) return
        // 兜底一：老版本深链（部分机型/版本确实注册过该 scheme）
        if (launchByUri("market://update")) return
        // 兜底二：触发原生「更新」TabView 的点击（若该版本确实生成了对应 TabView），避免完全无效果
        runCatching {
            NativeTabBar.tabViewsOf(nativeTabLayout)
                .firstOrNull { NativeTabBar.tagOf(it) == UPDATE_TAB_TAG }
                ?.performClick()
        }
    }

    /**
     * 拉起商店「应用更新」页：先试已知类名，再退回从商店自身 Manifest 的 Activity 列表里
     * 模糊匹配（Activity 类名不受 AndResGuard 资源混淆影响，跨版本也较稳）。命中即返回 true。
     */
    private fun launchMarketUpdatePage(): Boolean {
        val pkg = activity.packageName
        UPDATE_PAGE_ACTIVITIES.forEach { name ->
            if (startExplicit(pkg, name)) return true
        }
        queryUpdateActivities(pkg).forEach { name ->
            if (startExplicit(pkg, name)) return true
        }
        return false
    }

    /** 显式启动商店内某个 Activity（同进程上下文，可拉起未导出的内部 Activity）；失败返回 false。 */
    private fun startExplicit(pkg: String, name: String): Boolean = runCatching {
        activity.startActivity(Intent().setComponent(ComponentName(pkg, name)))
        true
    }.getOrDefault(false)

    /** 从商店 Manifest 的 Activity 列表里挑「名字像更新页」的候选，UpdateList* 优先。 */
    private fun queryUpdateActivities(pkg: String): List<String> {
        val info = runCatching {
            activity.packageManager.getPackageInfo(
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

    /** 按深链拉起；解析不到组件时返回 false。 */
    private fun launchByUri(uri: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")
        private val active = WeakHashMap<Activity, ComposeFloatingBarHost>()

        /** 商店「应用更新 / 升级」页 Activity 候选（按命中概率排序；跨版本兜底）。 */
        private val UPDATE_PAGE_ACTIVITIES = listOf(
            "com.xiaomi.market.ui.UpdateListActivity",
            "com.xiaomi.market.business_ui.update.UpdateListActivity",
            "com.xiaomi.market.business_ui.main.update.UpdateListActivity",
            "com.xiaomi.market.ui.UpdateActivity",
        )

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
    /** 原生 tab 的数字角标（待更新应用数等），用于「更新」标签归并显示。 */
    val number: Int = 0,
    /** 数字角标；>0 时悬浮底栏显示数字（覆盖红点）。 */
    val badgeNumber: Int = 0,
    /** 原生 TabView 的图标位图（短剧等无自绘图标时复用商店自带图标），为 null 则退化为 [icon]。 */
    val iconBitmap: ImageBitmap? = null,
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
        val items = effectiveTabs.map { tab ->
            FloatingTabItem(
                key = tab.nativeIndex.toString(),
                label = if (showLabel) tab.label else "",
                icon = tab.icon(),
                iconBitmap = tab.iconBitmap,
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
