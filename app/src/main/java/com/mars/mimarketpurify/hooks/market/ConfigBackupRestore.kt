package com.mars.mimarketpurify.hooks.market

import android.app.Application
import android.content.Context
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自动备份商店 SharedPreferences 配置，并在检测到回滚（配置被清空/丢失）时自动恢复。
 *
 * 参考 lisrain/NewFuckMarketAds_Fork 的稳定性增强逻辑（v1.3.6）：
 *   - AppGlobals 在 hook 初始化阶段尚未就绪，因此延迟到 Application.onCreate() 执行；
 *   - 正常情况：备份主配置等关键 SharedPreferences 到文件；
 *   - 检测到回滚（主配置条目数 <= 1）：从备份恢复。
 *
 * 备份目录位于应用自身的 externalFilesDir 下，不额外申请权限。
 * 本模块为纯保护性安全网，由总开关统一门控；所有读写均包 try-catch，失败不影响商店运行。
 *
 * 相对原实现的修复：原手写 JSON 解析器不识别 Set<String>（会被 toString 成字符串、
 * 恢复时类型丢失），且对 Double / 转义字符处理不完整。这里改用 Android 自带的
 * org.json（无需新增依赖），完整覆盖 SharedPreferences 的全部值类型：
 * boolean / int / long / float / String / Set<String>。
 */
object ConfigBackupRestore : BaseHook() {

    private const val BACKUP_DIR_NAME = "market_config_backup"

    private val PREF_FILES_TO_BACKUP = listOf(
        "com.xiaomi.market_preferences", // 默认主配置
        "app_update",                     // 应用更新
        "self_update",                    // 自更新
        "host"                            // 服务器地址
    )

    override val name: String
        get() = "备份恢复商店配置"

    override fun init() {
        // AppGlobals.getContext() 在 hook 初始化阶段尚未就绪
        // 需要延迟到 Application.onCreate() 执行，此时 context 一定可用
        try {
            ClassUtil.loadClass("android.app.Application")
                .methodFinder()
                .filterByName("onCreate")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val app = chain.thisObject as? Application
                        if (app != null) {
                            deferWork(app)
                        }
                        return@intercept chain.proceed()
                    }
                }
            HookEnv.base.log(Log.INFO, TAG, "ConfigBackupRestore: hook installed (deferred to onCreate)", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "ConfigBackupRestore: failed to hook Application.onCreate: ${e.message}", e)
        }
    }

    private fun deferWork(context: Context) {
        try {
            val backupDir = getBackupDir(context)
            val isRolledBack = detectRollback(context)

            if (isRolledBack) {
                HookEnv.base.log(
                    Log.WARN, TAG,
                    "Rollback detected — restoring config from backup",
                    null
                )
                restoreConfig(context, backupDir)
            } else {
                backupConfig(context, backupDir)
            }

            HookEnv.base.log(Log.INFO, TAG, "ConfigBackupRestore: done", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "ConfigBackupRestore: error: ${e.message}", e)
        }
    }

    private fun getBackupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** 主配置条目数为空或仅剩 1 条时，判定为发生了回滚 */
    private fun detectRollback(context: Context): Boolean {
        val mainPrefs = context.getSharedPreferences(
            "com.xiaomi.market_preferences", Context.MODE_PRIVATE
        )
        return mainPrefs.all.isEmpty() || mainPrefs.all.size <= 1
    }

    // ═══════════════ 备份 ═══════════════

    private fun backupConfig(context: Context, backupDir: File) {
        for (prefName in PREF_FILES_TO_BACKUP) {
            try {
                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allEntries = prefs.all
                if (allEntries.isEmpty()) continue

                val backupFile = File(backupDir, "$prefName.json")
                val json = JSONObject()
                for ((key, value) in allEntries) {
                    json.put(key, toJsonValue(value))
                }

                backupFile.writeText(json.toString())
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "Backed up $prefName (${allEntries.size} entries)",
                    null
                )
            } catch (e: Exception) {
                HookEnv.base.log(
                    Log.ERROR, TAG,
                    "Failed to backup $prefName: ${e.message}",
                    null
                )
            }
        }
    }

    /** 把 SharedPreferences 值转成可 JSON 化的值；Set<String> 转 JSONArray */
    private fun toJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Int, is Long, is Float, is Double -> value
        is Set<*> -> JSONArray(value.filterIsInstance<String>())
        else -> value.toString() // 其他罕见类型兜底为字符串，避免序列化失败
    }

    // ═══════════════ 恢复 ═══════════════

    private fun restoreConfig(context: Context, backupDir: File) {
        for (prefName in PREF_FILES_TO_BACKUP) {
            try {
                val backupFile = File(backupDir, "$prefName.json")
                if (!backupFile.exists()) continue

                val jsonStr = backupFile.readText()
                if (jsonStr.isBlank() || jsonStr == "{}") continue

                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                var count = 0

                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    when (value) {
                        JSONObject.NULL -> { /* null 值跳过 */ }

                        is Boolean -> { editor.putBoolean(key, value); count++ }

                        // JSON 数字反序列化后只会是 Integer / Long / Double；
                        // SharedPreferences 无 Double 类型，统一按 float 写入。
                        is Int -> { editor.putInt(key, value); count++ }
                        is Long -> { editor.putLong(key, value); count++ }
                        is Float -> { editor.putFloat(key, value); count++ }
                        is Double -> { editor.putFloat(key, value.toFloat()); count++ }

                        is JSONArray -> {
                            val list = mutableListOf<String>()
                            for (i in 0 until value.length()) {
                                list += value.getString(i)
                            }
                            editor.putStringSet(key, list.toSet())
                            count++
                        }

                        is String -> { editor.putString(key, value); count++ }

                        else -> { /* 未知类型跳过 */ }
                    }
                }

                editor.apply()
                HookEnv.base.log(
                    Log.INFO, TAG,
                    "Restored $prefName ($count entries)",
                    null
                )
            } catch (e: Exception) {
                HookEnv.base.log(
                    Log.ERROR, TAG,
                    "Failed to restore $prefName: ${e.message}",
                    null
                )
            }
        }
    }
}
