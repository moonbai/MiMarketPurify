package com.mars.mimarketpurify.util

import com.mars.mimarketpurify.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：读取 GitHub Releases 最新版（参考 AritxOnly/HyperModifier 的 UpdateChecker 实现）。
 * 仅做版本比对与结果上报；APK 的下载与安装完全由用户自行决定。
 */
sealed interface UpdateCheckResult {
    data class Available(val versionName: String, val releaseUrl: String) : UpdateCheckResult
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
            if (compareVersions(versionName, BuildConfig.VERSION_NAME) > 0) {
                UpdateCheckResult.Available(versionName, releaseUrl)
            } else {
                UpdateCheckResult.Latest
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        UpdateCheckResult.Unavailable
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
