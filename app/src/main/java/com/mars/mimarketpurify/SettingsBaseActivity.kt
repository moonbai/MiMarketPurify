package com.mars.mimarketpurify

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import com.mars.mimarketpurify.uiScaleValue
import com.mars.mimarketpurify.useDarkTheme
import io.github.libxposed.service.XposedService
import com.mars.mimarketpurify.R

/**
 * 设置页的**纯逻辑基类**（不再包含任何原生 View 构建器）。
 *
 * 仅保留：Xposed service 连接与重连补写、远程偏好读写、桌面图标隐藏/恢复、入口自愈，
 * 以及一个供 Compose 页面观察 service 状态的 [refreshSignal]。
 *
 * 所有 UI 已迁移到 Compose（见 [com.mars.mimarketpurify.ui.components]），
 * 原生构建器（addSwitchRow / addNavRow / groupCard / ColorPickerView 等）已删除，
 * 避免与主页 Compose 形成两套 UI 语汇的重复。
 */
abstract class SettingsBaseActivity : ComponentActivity(), ServiceStateListener {

    protected var service: XposedService? = null

    /** service 未连接期间的待写入布尔值，连接后补写。 */
    private val pendingWrites = mutableMapOf<String, Boolean>()

    /** service 未连接期间的待写入整型值，连接后补写。 */
    private val pendingIntWrites = mutableMapOf<String, Int>()

    /**
     * 当前 Activity 在 [applyWindowTheme] 中实际套用的明暗状态。
     * 用于 [onResume] 检测主题模式改动后是否需要重建以重新着色（[setContent] 仅执行一次）。
     */
    protected var appliedDarkTheme = false

    /**
     * 当前 Activity 在 [applyWindowTheme] 中实际套用的界面缩放。
     * 用于 [onResume] 检测「界面缩放」改动后是否需要重建以套用新缩放
     * （例如从「主题与外观」页改完缩放返回主页时，主页需重建才能生效）。
     */
    protected var appliedScale = 1f

    /**
     * Compose 页面观察此信号以重新读取偏好。
     * [onServiceStateChanged] 在补写完待写入项后自增，触发依赖它的 [androidx.compose.runtime.remember] 重新取数。
     */
    val refreshSignal = mutableStateOf(0)

    protected val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

    // ==================== 生命周期与刷新 ====================

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // 主题模式改动后，返回本页需重建以重新着色（MiuixTheme 在 setContent 仅解析一次）。
        // 注意：子页（SubSettingsActivity）自身在改动外观后会主动 recreate，此处主要兜住
        // 「从主题二级页返回主页」这一路径。重建后 appliedDarkTheme 与当前一致，不会循环。
        if (useDarkTheme() != appliedDarkTheme) {
            recreate()
            return
        }
        // 界面缩放改动后（例如从「主题与外观」页返回主页），本页需重建以套用新缩放。
        // 与 appliedDarkTheme 同理，避免「改完缩放回到主页仍是原大小」的观感不一致。
        if (uiScaleValue() != appliedScale) {
            recreate()
            return
        }
        refreshAll()
    }

    /**
     * 套用窗口底色与状态栏前景色，并记录 [appliedDarkTheme] / [appliedScale]。
     * 各设置页在 onCreate 的 setContent 之前调用一次；Compose 内容由 [ModuleTheme] 负责。
     */
    protected fun applyWindowTheme() {
        val dark = useDarkTheme()
        appliedDarkTheme = dark
        appliedScale = uiScaleValue()
        window.setBackgroundDrawable(ColorDrawable(MiuiX.bg(dark)))
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !dark
    }

    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
        runOnUiThread {
            // 补写 service 断开期间的待写入项
            if (service != null && pendingWrites.isNotEmpty()) {
                val prefs = service.getRemotePreferences(PREFS_GROUP)
                pendingWrites.forEach { (k, v) -> prefs?.edit()?.putBoolean(k, v)?.apply() }
                pendingWrites.clear()
            }
            if (service != null && pendingIntWrites.isNotEmpty()) {
                val prefs = service.getRemotePreferences(PREFS_GROUP)
                pendingIntWrites.forEach { (k, v) -> prefs?.edit()?.putInt(k, v)?.apply() }
                pendingIntWrites.clear()
            }
            refreshAll()
        }
    }

    /** 重连 / 外部改动后统一刷新入口；Compose 页面通过 [refreshSignal] 重新取数。 */
    protected open fun refreshAll() {
        onRefresh()
        refreshSignal.value++
    }

    /** 子类可重写：在刷新时重新同步本地 UI 状态。默认空实现（偏好由 [refreshSignal] 驱动）。 */
    protected open fun onRefresh() {}

    // ==================== 远程偏好 ====================

    internal fun readLocal(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    internal fun writeRemote(key: String, value: Boolean) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            pendingWrites[key] = value
            return
        }
        runCatching {
            prefs.edit()?.putBoolean(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, getString(R.string.save_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun readLocalTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(Settings.KEY_TAB_KEEP, Settings.DEFAULT_TAB_KEEP)
            ?: Settings.DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    internal fun readLocalInt(key: String, def: Int): Int =
        service?.getRemotePreferences(PREFS_GROUP)?.getInt(key, def) ?: def

    internal fun writeRemoteInt(key: String, value: Int) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            pendingIntWrites[key] = value
            return
        }
        runCatching {
            prefs.edit()?.putInt(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, getString(R.string.save_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun writeRemoteString(key: String, value: String) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) return
        runCatching {
            prefs.edit()?.putString(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, getString(R.string.save_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 模块自身（本地 SP，不经 XposedService） ====================
    // 主题模式 / 界面缩放 / 预测性返回 等「外观类」设置只影响本插件 UI，存于模块自身 SP。
    // 直接读写 getSharedPreferences(PREFS_GROUP)，不依赖 XposedService 是否连接，
    // 也避免与远程偏好通道混用导致「写入与读取不在同一视图」。

    internal fun readLocalIntDirect(key: String, def: Int): Int =
        getSharedPreferences(PREFS_GROUP, MODE_PRIVATE).getInt(key, def)

    internal fun writeLocalInt(key: String, value: Int) {
        runCatching {
            getSharedPreferences(PREFS_GROUP, MODE_PRIVATE).edit().putInt(key, value).apply()
        }.onFailure {
            Toast.makeText(this, getString(R.string.save_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun readLocalBoolDirect(key: String, def: Boolean): Boolean =
        getSharedPreferences(PREFS_GROUP, MODE_PRIVATE).getBoolean(key, def)

    internal fun writeLocalBool(key: String, value: Boolean) {
        runCatching {
            getSharedPreferences(PREFS_GROUP, MODE_PRIVATE).edit().putBoolean(key, value).apply()
        }.onFailure {
            Toast.makeText(this, getString(R.string.save_failed, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 模块自身 ====================

    internal fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    internal fun applyHideIcon(hide: Boolean) {
        runCatching {
            EntryGuardReceiver.ensureEntryEnabled(this)
            val state = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                launcherAlias, state, PackageManager.DONT_KILL_APP
            )
            Toast.makeText(
                this,
                if (hide) getString(R.string.toast_hide_icon) else getString(R.string.toast_restore_icon),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
