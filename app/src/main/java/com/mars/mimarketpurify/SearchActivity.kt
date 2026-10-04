package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import kotlinx.coroutines.delay

/**
 * 搜索页：顶部搜索栏 + 搜索结果 / 底部功能推荐网格。
 * 点击搜索框打开此页，输入关键词实时过滤，底部展示分类推荐。
 */
class SearchActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, SearchActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                SearchScreen(activity = this@SearchActivity)
            }
        }
    }
}

/** 分类到主题色的映射 */
private val categoryColors = mapOf(
    "广告净化" to Color(0xFFE53935),
    "我的页精简" to Color(0xFF1E88E5),
    "标签栏" to Color(0xFF43A047),
    "界面精简" to Color(0xFFF57C00),
    "悬浮底栏" to Color(0xFF8E24AA),
)

@Composable
private fun SearchScreen(activity: SearchActivity) {
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val results = remember(query) { FeatureRegistry.search(query) }
    val isSearching = query.isNotBlank()

    // 12 个推荐，每 5 秒自动刷新
    var recommendSeed by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            recommendSeed = System.currentTimeMillis()
        }
    }
    val recommendations = remember(recommendSeed) {
        FeatureRegistry.recommendByCategory(count = 12, seed = recommendSeed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // ═══════════ 顶部搜索栏 ═══════════
        SearchTopBar(
            query = query,
            onQueryChange = { query = it },
            onBack = { activity.finish() },
        )

        // ═══════════ 内容区 ═══════════
        if (isSearching) {
            // 搜索结果
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut(),
            ) {
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到相关功能",
                            fontSize = 15.sp,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(results) { feature ->
                            FeatureSearchCard(feature = feature) {
                                activity.startActivity(
                                    SubSettingsActivity.intent(activity, feature.page, feature.key)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 底部推荐
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "功能推荐",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recommendations) { feature ->
                        FeatureSearchCard(feature = feature) {
                            activity.startActivity(
                                SubSettingsActivity.intent(activity, feature.page, feature.key)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回按钮
        Text(
            text = "‹",
            fontSize = 28.sp,
            color = colors.onSurface,
            modifier = Modifier
                .clickable { onBack() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        // 修复：TextField 内部文字被 clip 掉，改为自定义外观
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索功能…",
                    fontSize = 15.sp,
                    color = colors.onSurfaceSecondary,
                )
            }
            // 实际输入用透明 TextField 覆盖，只显示光标和文字
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.primary,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                ),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier
                                .clickable { onQueryChange("") }
                                .padding(8.dp),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FeatureSearchCard(
    feature: FeatureRegistry.Feature,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val catColor = categoryColors[feature.category] ?: colors.primary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        // 顶部色条
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(catColor),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = feature.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = feature.summary,
            fontSize = 11.sp,
            color = colors.onSurfaceSecondary,
            maxLines = 2,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = feature.category,
            fontSize = 10.sp,
            color = catColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
