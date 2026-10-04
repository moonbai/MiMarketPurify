plugins {
    alias(libs.plugins.android.application)
    // Compose 编译器插件：版本由根 build 的 `apply false` 声明（libs.plugins.kotlin.compose，
    // 版本与 libs.versions.toml 的 kotlin 同号 2.4.20）。AGP 9 内置 Kotlin 编译，无需 kotlin-android 插件。
    alias(libs.plugins.kotlin.compose)
}

// 条件式 release 签名：仅在 CI 注入了密钥 secret 时才签名（对齐 HyperModifier 的方案）。
// - SIGNING_KEY（base64 编码的 jks）在 CI 步骤里解码为文件，路径通过 -PmimarketSigningStoreFile 传入；
// - SIGNING_KEY_PASSWORD（密钥库密码）/ SIGNING_PASSWORD（别名密码）由环境变量读取。
// - 别名（MiMarketPurify）为非敏感项，默认硬编码于此（与 keystore 中真实别名一致），不再作为 secret 注入，
//   以免 GitHub 把「等于该别名」的字符串在日志 / 产物名 / 下载链接中统一遮罩为 ***。需要时仍可用
// 环境变量 SIGNING_KEY_ALIAS 覆盖。
// 未注入密钥时打出的 release 包仍是已混淆裁剪的小体积包，只是未签名（无法安装）。
val signingStoreFile = providers.gradleProperty("mimarketSigningStoreFile").orNull
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull ?: "MiMarketPurify"
val signingStorePassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
val signingKeyPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
val hasReleaseSigning = !signingStoreFile.isNullOrBlank() &&
    !signingKeyAlias.isNullOrBlank() &&
    !signingStorePassword.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.mars.mimarketpurify"
    // miuix 0.9.4 及其传递依赖（compose runtime-saveable 1.12.0-rc01、material3-window-size-class
    // 1.5.0-alpha22、materialkolor 5.0.0）均要求 compileSdk 37。
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.mars.mimarketpurify"
        // miuix-blur-android:0.9.4 在 Manifest 中声明 minSdk 33，模块必须不低于该值
        // （Mi Market 实际运行于 HyperOS / Android 14+，即 API 34+，无功能影响）。
        minSdk = 33
        targetSdk = 36
        versionCode = 15
        versionName = "1.2.2"
        buildConfigField("String", "APP_NAME", "\"Mi Market Purify\"")
        // 只打包用到的语言资源，丢弃 Compose / Miuix 等库自带的其余 locale，进一步压缩体积
        // （AGP 9 起 resourceConfigurations 已废弃并强制报错，改用 androidResources.localeFilters）
        androidResources {
            localeFilters += listOf("zh-rCN", "en")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = File(signingStoreFile!!)
                storePassword = signingStorePassword!!
                keyAlias = signingKeyAlias!!
                keyPassword = signingKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 保留 libxposed 在编译期生成的模块入口注册文件（META-INF/xposed/*）。
    // 该文件按入口类的【原类名】登记，release 混淆后必须仍能被框架找到，
    // 否则会出现「全部 hook 失效」。先排除全部资源再单独 merge 回 xposed 注册文件。
    packaging {
        resources {
            excludes += "**"
            merges += "META-INF/xposed/*"
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
    // 复用 HyperModifier 的方案：悬浮底栏 MiuixFloatingTabBar 与主页 Compose 直接依赖
    // material3 的 MaterialTheme / Icon / Text，故重新引入 material3（BOM 管理版本）。
    // 体积由 release 构建的 R8 full mode + shrinkResources 兜底（33M 的真因是 CI 打了未混淆的
    // debug 包，而非 Compose/miuix 本身）。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Miuix KMP：悬浮底栏主题（MiuixTheme/Colors）与毛玻璃（rememberLayerBackdrop/layerBackdrop）──
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)

    // ── HyperModifier 悬浮底栏实际依赖：io.github.kyant0:shapes（提供 Capsule 胶囊形状）──
    implementation(libs.kyant.shapes)

    // ── Compose 浮层所需的 AndroidX lifecycle ViewTree owner 扩展 ──
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
