package com.mars.mimarketpurify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.MainScreen
import com.mars.mimarketpurify.ui.components.AboutContent
import com.mars.mimarketpurify.util.NavIcons
import com.mars.mimarketpurify.util.UpdateCheckResult
import com.mars.mimarketpurify.util.UpdateChecker
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 程序主页：**整页 Compose**，并采用底部标签栏（主页 / 关于）两种形态。
 *
 * 主页只保留**高频开关**：总开关、下载超级岛 / 细节修正 / 升级提醒三个高级功能；
 * 其余按「同一个页面」或「带子选项」为维度收进 [SubSettingsActivity]。
 * 「关于」作为底栏第二个标签页，与独立 [AboutActivity] 共用 [AboutContent]。
 *
 * 所有远程偏好读写、入口自愈、隐藏桌面图标等逻辑仍复用 [SettingsBaseActivity]。
 */
class MainActivity : SettingsBaseActivity() {

    private val KEY_FIRST_LAUNCH_CHECKED = "first_launch_update_checked"

    /** 二级页「广告移除」里的开关，用于在主页入口行显示启用数量 */
    internal val adKeys = listOf(
        Settings.KEY_SPLASH,
        Settings.KEY_MAIN_TAB,
        Settings.KEY_HOME_FEED,
        Settings.KEY_SEARCH,
        Settings.KEY_UPDATE_DL,
        Settings.KEY_DETAIL,
        Settings.KEY_RANK,
        Settings.KEY_FRUIT,
        Settings.KEY_ENTRANCE,
        Settings.KEY_DETAIL_EXTRAS,
        Settings.KEY_FLOATING_AD
    )

    /** 二级页「「我的」页」里的开关 */
    internal val mineKeys = listOf(
        Settings.KEY_MINE_RECOMMEND,
        Settings.KEY_MINE_OFFICIAL_TAB,
        Settings.KEY_MINE_CLEANUP,
        Settings.KEY_MINE_SUMMARY,
        Settings.KEY_MINE_SECURITY,
        Settings.KEY_ORCHARD_SKIN,
        Settings.KEY_TAB_BADGE,
        Settings.KEY_CARD_EXPAND
    )

    /** 二级页「其他界面净化」里的开关 */
    internal val miscKeys = listOf(
        Settings.KEY_DETAIL_FEATURED,
        Settings.KEY_UPDATE_HISTORY,
        Settings.KEY_SEARCH_ALSO_VIEW,
        Settings.KEY_SUB_TAB_FILTER,
        Settings.KEY_HIDE_UPDATE_ALL,
        Settings.KEY_HIDE_AUTO_UPDATE_SWITCH
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 入口自愈：曾被旧版本锁出的设备，覆盖安装后自动恢复
        EntryGuardReceiver.ensureEntryEnabled(this)

        // 暗色模式：状态栏图标随背景反色
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                var tab by remember { mutableStateOf(0) } // 0 = 主页，1 = 关于
                Column(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (tab) {
                            0 -> MainScreen(activity = this@MainActivity)
                            else -> AboutContent(activity = this@MainActivity, onBack = { tab = 0 })
                        }
                    }
                    BottomNavBar(selected = tab) { tab = it }
                }
            }
        }

        // 首次启动静默检查更新（不弹窗，仅在发现新版本时轻提示）
        maybeCheckUpdateOnFirstLaunch()
    }

    /** 手动检查更新：在子线程请求 GitHub Releases，结果回主线程提示并（若可用）打开发布页。 */
    internal fun checkForUpdates() {
        Thread {
            val result = UpdateChecker.check()
            runOnUiThread {
                when (result) {
                    is UpdateCheckResult.Available -> {
                        Toast.makeText(
                            this@MainActivity,
                            "发现新版本 v${result.versionName}",
                            Toast.LENGTH_LONG,
                        ).show()
                        openRelease(result.releaseUrl)
                    }

                    is UpdateCheckResult.Latest ->
                        Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()

                    is UpdateCheckResult.Unavailable ->
                        Toast.makeText(this@MainActivity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun openRelease(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /** 仅首次启动静默检查一次；在本地 SharedPreferences 记录标记，不依赖远程偏好（service 可能未连）。 */
    private fun maybeCheckUpdateOnFirstLaunch() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)) return
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
        Thread {
            val result = UpdateChecker.check()
            runOnUiThread {
                if (result is UpdateCheckResult.Available) {
                    Toast.makeText(
                        this@MainActivity,
                        "发现新版本 v${result.versionName}，可前往「检查更新」查看",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }

    // ==================== 入口行摘要（供 MainScreen 复用） ====================

    /** 「已启用 2/3」：一眼看出二级页里开了几项 */
    internal fun countText(keys: List<String>): String =
        "已启用 ${keys.count { readLocal(it, true) }}/${keys.size}"

    internal fun tabsText(): String {
        val parts = mutableListOf<String>()
        if (!readLocal(Settings.KEY_TAB_FILTER, true)) {
            parts += "筛选已关闭"
        } else {
            val hidden = Settings.TAB_ITEMS.size - readLocalTabs().size
            parts += if (hidden <= 0) "未隐藏" else "已隐藏 $hidden 个"
        }
        return parts.joinToString(" · ")
    }

    internal fun tabbarText(): String {
        return if (readLocal(Settings.KEY_FLOATING_BAR, true)) {
            "悬浮已开启"
        } else {
            "原版底栏"
        }
    }

    internal fun openPage(page: String) {
        startActivity(SubSettingsActivity.intent(this, page))
    }
}

// ==================== 底部标签栏（官方 Miuix NavigationBar 容器）====================
// 说明：容器采用官方 NavigationBar（圆角 / 分隔线 / 窗口边距 / 配色均为 MiuiX 方案），
// 内部 item 为自定义实现；图标全部用 [NavIcons] 在代码中自绘的 ImageVector（单色、随主题
// 着色），不再打包任何商店栅格 WebP，规避 AndResGuard 资源混淆、零维护。

@Composable
private fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(showDivider = true) {
        ProgramNavItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = NavIcons.Home,
            label = "主页",
        )
        ProgramNavItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = NavIcons.Person,
            label = "关于",
        )
    }
}

@Composable
private fun RowScope.ProgramNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val colors = MiuixTheme.colorScheme
    val color = if (selected) colors.primary else colors.onSurfaceSecondary
    Column(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .weight(1f)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = color)
    }
}
