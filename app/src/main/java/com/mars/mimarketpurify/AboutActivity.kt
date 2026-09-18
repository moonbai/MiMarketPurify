package com.mars.mimarketpurify

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class AboutActivity : Activity() {

    private lateinit var content: LinearLayout
    private val avatarRadiusDp = 22f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.setPadding(
                dp(Ui.PAGE_H),
                dp(Ui.PAGE_H) + bars.top,
                dp(Ui.PAGE_H),
                dp(12)
            )
            content.setPadding(
                dp(Ui.PAGE_H),
                dp(8),
                dp(Ui.PAGE_H),
                dp(Ui.PAGE_H) + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        buildTopBar(header)
        buildAppCard()

        addSection("功能")
        buildFeatureCards()

        addSection("作者")
        buildAuthor()

        addSection("参考项目")
        buildReferenceProjects()

        content.addView(TextView(this).apply {
            text = "不乱拉屎的应用商店才是好的应用商店@Mars"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setLineSpacing(0f, 1.5f)
            setPadding(dp(8), dp(16), dp(8), dp(8))
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    private fun setFixedIconRounded(iv: ImageView, radiusDp: Float) {
        val rPx = dp(radiusDp).toFloat()
        val radii = floatArrayOf(rPx, rPx, rPx, rPx, rPx, rPx, rPx, rPx)
        val shape = ShapeDrawable(RoundRectShape(radii, null, null))
        iv.background = shape
        iv.clipToOutline = true
        ViewCompat.setClipToOutline(iv, true)
    }

    private fun buildTopBar(header: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(R.drawable.bg_icon_ripple)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                dp(Ui.TOUCH_MIN),
                dp(Ui.TOUCH_MIN)
            ).also { it.marginStart = -dp(8) }
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            text = "关于"
            textSize = Ui.PAGE_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = dp(4) }
        })
        header.addView(row)
        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
    }

    private fun buildAppCard() {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6)) // ← 缩小
        }

        val appIcon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setFixedIconRounded(this@apply, avatarRadiusDp)
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).also {
                it.marginStart = dp(12)
                it.marginEnd = dp(8)
            }
        }

        info.addView(cardTitle("Mi Market Purify"))
        info.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = "小米应用商店净化与增强"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(0, dp(3), 0, 0)
        })

        val arrowTv = TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(Ui.TEXT_TERTIARY)
        }

        row.addView(appIcon)
        row.addView(info)
        row.addView(arrowTv)
        card.addView(row)
        card.tappable(this, R.drawable.bg_card_ripple)
        card.setOnClickListener { openRepo() }
        content.addView(card)
    }

    private fun buildFeatureCards() {
        val features = listOf(
            "广告净化" to "开屏、首页信息流、搜索、升级/下载页、详情页、榜单广告、领水果入口、活动入口",
            "界面精简" to "「我的」页推荐/清理/安全检测/个人信息、详情页精选、底栏角标、升级记录、搜索也在看",
            "我的页增强" to "升级卡片展开、果园背景清除",
            "功能增强" to "下载超级岛、非正版APP显示、被隐藏更新显示、升级弹窗拦截"
        )

        features.forEachIndexed { index, (title, desc) ->
            val card = card()
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6)) // ← 缩小
                addView(cardTitle(title))
                addView(TextView(this@AboutActivity).apply {
                    text = desc
                    textSize = Ui.ROW_SUMMARY
                    setTextColor(Ui.TEXT_SECONDARY)
                    setLineSpacing(0f, 1.4f)
                    setPadding(0, dp(2), 0, 0)
                })
            })

            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                if (index > 0) {
                    it.topMargin = dp(8)
                }
            }
            content.addView(card)
        }
    }

    private fun buildAuthor() {
        val weiboUrl = "https://weibo.com/u/3963594403"
        val authorName = "Mars"
        val authorSubtitle = "点此访问作者主页，点点关注"

        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6)) // ← 缩小
        }

        val authorAvatar = ImageView(this).apply {
            setImageResource(R.drawable.avatar_mars)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            scaleType = ImageView.ScaleType.CENTER_CROP
            isHardwareAccelerated = true
            setFixedIconRounded(this@apply, avatarRadiusDp)
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).also {
                it.marginStart = dp(12)
                it.marginEnd = dp(8)
            }
        }

        info.addView(cardTitle(authorName))
        info.addView(TextView(this).apply {
            text = authorSubtitle
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })

        val arrowTv = TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(Ui.TEXT_TERTIARY)
        }

        row.addView(authorAvatar)
        row.addView(info)
        row.addView(arrowTv)
        card.addView(row)

        card.tappable(this, R.drawable.bg_card_ripple)
        card.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(weiboUrl)))
            }.onFailure {
                Toast.makeText(this, "无法打开微博链接", Toast.LENGTH_SHORT).show()
            }
        }

        content.addView(card)
    }

    private fun buildReferenceProjects() {
        data class RefProject(
            val repoName: String,
            val url: String,
            val label: String
        )

        val references = listOf(
            RefProject("callng/NewFuckMarketAds", "https://github.com/callng/NewFuckMarketAds", "GPL-3.0"),
            RefProject("lisrain/NewFuckMarketAds_Fork", "https://github.com/lisrain/NewFuckMarketAds_Fork", "GPL-3.0"),
            RefProject("HowieHChen/XiaomiHelper", "https://github.com/HowieHChen/XiaomiHelper", "GPL-3.0")
        )

        val card = card()
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        references.forEachIndexed { index, item ->
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6)) // ← 缩小
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_card_ripple)
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                    }.onFailure {
                        Toast.makeText(
                            this@AboutActivity,
                            "无法打开链接：${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val textLayout = LinearLayout(this@AboutActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                textLayout.addView(TextView(this@AboutActivity).apply {
                    text = item.repoName
                    textSize = Ui.ROW_SUMMARY
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Ui.TEXT_PRIMARY)
                })
                textLayout.addView(TextView(this@AboutActivity).apply {
                    text = item.label
                    textSize = Ui.MICRO
                    setTextColor(Ui.TEXT_SECONDARY)
                    setPadding(0, dp(3), 0, 0)
                })

                val arrow = TextView(this@AboutActivity).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(Ui.TEXT_TERTIARY)
                }

                addView(textLayout)
                addView(arrow)
            }

            listLayout.addView(itemRow)
        }

        card.addView(listLayout)
        content.addView(card)
    }

    private fun openRepo() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Ui.REPO_URL)))
        }.onFailure {
            Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSection(title: String) {
        val titleView = sectionTitle(title)
        titleView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = dp(10)
            it.bottomMargin = dp(8)
        }
        content.addView(titleView)
    }

    private fun dp(value: Float): Int = (resources.displayMetrics.density * value).roundToInt()
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.ROW_TITLE
        setTypeface(null, Typeface.BOLD)
        setTextColor(Ui.TEXT_PRIMARY)
    }
    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.SECTION_TITLE
        setTextColor(Ui.TEXT_SECONDARY)
    }
    private fun View.tappable(activity: Activity, bgRes: Int) {
        background = activity.resources.getDrawable(bgRes, theme)
        isClickable = true
        isFocusable = true
    }
}
