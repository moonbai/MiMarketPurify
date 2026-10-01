package com.mars.mimarketpurify.util

import android.os.Build
import com.mars.mimarketpurify.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：读取 GitHub Releases 最新版（参考 AritxOnly/HyperModifier 的 UpdateChecker 实现）。
 *
 * 与旧实现不同，这里**不只**返回发布页网址，还会解析 `assets[]` 拿到 APK 的
 * `browser_download_url`（直链、无签名 / 无时效 token），以便模块自身「检查更新」时
 * 直接下载安装，而不必跳浏览器去发布页手动找包。
 *
 * 仅做版本比对、直链解析与结果上报；下载与安装由调用方（[checkForUpdatesManual]）完成。
 */
sealed interface UpdateCheckResult {
    data class Available(
        val versionName: String,
        val releaseUrl: String,
        /** APK 直链；为空表示解析不到可用包，调用方应退回「去发布页」 */
        val apkUrl: String = "",
        /** APK 文件名（用于落盘），可能为空 */
        val apkName: String = "",
        /** APK 字节数，0 表示未知 */
        val sizeBytes: Long = 0L,
        /** 发布说明（markdown 原文，可能很长） */
        val notes: String = "",
    ) : UpdateCheckResult

    data object Latest : UpdateCheckResult
    data object Unavailable : UpdateCheckResult
}

object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/moonbai/MiMarketPurify/releases/latest"

    fun check(): UpdateCheckResult = try {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "MiMarketPurify-UpdateChecker")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return UpdateCheckResult.Unavailable
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val versionName = release.getString("tag_name").removePrefix("v")
            val releaseUrl = release.getString("html_url")
            val notes = release.optString("body", "")
            val assets = release.optJSONArray("assets") ?: JSONArray()
            val apkAsset = pickApkAsset(assets)
            val apkUrl = apkAsset?.optString("browser_download_url").orEmpty()
            val apkName = apkAsset?.optString("name").orEmpty()
            val sizeBytes = apkAsset?.optLong("size", 0L) ?: 0L
            if (compareVersions(versionName, BuildConfig.VERSION_NAME) > 0) {
                UpdateCheckResult.Available(
                    versionName = versionName,
                    releaseUrl = releaseUrl,
                    apkUrl = apkUrl,
                    apkName = apkName,
                    sizeBytes = sizeBytes,
                    notes = notes,
                )
            } else {
                UpdateCheckResult.Latest
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        UpdateCheckResult.Unavailable
    }

    /**
     * 从发布资源里挑一个 APK：优先设备 ABI 精确匹配，其次按架构优先级
     * （arm64 > armeabi-v7a > x86_64 > x86），再退到 universal/all，最后取第一个 .apk。
     */
    private fun pickApkAsset(assets: JSONArray): JSONObject? {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        val apks = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        fun rank(o: JSONObject): Int {
            val n = o.optString("name").lowercase()
            if (preferredAbi.isNotEmpty() && n.contains(preferredAbi)) return 100
            return when {
                n.contains("arm64") -> 40
                n.contains("armeabi") || n.contains("v7a") -> 30
                n.contains("x86_64") || n.contains("x86-64") -> 20
                n.contains("x86") -> 10
                n.contains("universal") || n.contains("all") -> 5
                else -> 1
            }
        }
        return apks.maxByOrNull { rank(it) }
    }

    /** 比较常规数字版本段，忽略前导 v 与构建元数据。 */
    private fun compareVersions(left: String, right: String): Int {
        val leftParts = numericSegments(left)
        val rightParts = numericSegments(right)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = (leftParts.getOrElse(index) { 0 }).compareTo(
                rightParts.getOrElse(index) { 0 },
            )
            if (difference != 0) return difference
        }
        return 0
    }

    private fun numericSegments(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('+')
        .substringBefore('-')
        .split('.')
        .mapNotNull(String::toIntOrNull)
}
