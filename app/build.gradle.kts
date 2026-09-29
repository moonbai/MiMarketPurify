plugins {
    alias(libs.plugins.android.application)
    // Kotlin 2.x 已内置 Compose 编译器；本模块 compose 由该 Gradle 插件接线。
    // 版本与真实仓库里已声明的 Kotlin Gradle 插件保持一致（CI 使用 2.3.0）。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mars.mimarketpurify"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.mars.mimarketpurify"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.1"
        buildConfigField("String", "APP_NAME", "\"Mi Market Purify\"")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Miuix KMP：悬浮底栏主题（MiuixTheme/Colors）与毛玻璃（rememberLayerBackdrop/layerBackdrop）──
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)
}
