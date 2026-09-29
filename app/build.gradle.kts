plugins {
    alias(libs.plugins.android.application)
    // Compose 编译器插件：版本由根 build 的 `apply false` 声明（libs.plugins.kotlin.compose，
    // 版本与 libs.versions.toml 的 kotlin 同号 2.4.20）。AGP 9 内置 Kotlin 编译，无需 kotlin-android 插件。
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mars.mimarketpurify"
    // miuix 0.9.4-rc01 及其传递依赖（compose runtime-saveable 1.12.0-rc01、material3-window-size-class
    // 1.5.0-alpha22、materialkolor 5.0.0）均要求 compileSdk 37。
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.mars.mimarketpurify"
        // miuix-blur-android:0.9.4-rc01 在 Manifest 中声明 minSdk 33，模块必须不低于该值
        // （Mi Market 实际运行于 HyperOS / Android 14+，即 API 34+，无功能影响）。
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.2.0"
        buildConfigField("String", "APP_NAME", "\"Mi Market Purify\"")
        // 只打包用到的语言资源，丢弃 Compose / Miuix 等库自带的其余 locale，进一步压缩体积
        resourceConfigurations += listOf("zh-rCN", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        packaging {
            resources {
                excludes += "**"
                merges += "META-INF/xposed/*"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.ezxhelper.core)

    // ── Compose（BOM 统一版本，避免散落版本冲突）──
    // 注意：不再依赖 material3（全工程仅悬浮底栏用过一处 Text），改用 foundation 的 BasicText，
    // 可省下 material3 这一大块体积（约 1MB+）。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Miuix KMP：悬浮底栏主题（MiuixTheme/Colors）与毛玻璃（rememberLayerBackdrop/layerBackdrop）──
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)

    // ── Compose 浮层所需的 AndroidX lifecycle ViewTree owner 扩展 ──
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
