// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Compose 编译器插件：在根工程用 `apply false` 声明带版本，app 模块用无版本 alias 继承。
    // AGP 9 内置 Kotlin 编译，不需 kotlin-android 插件；版本与 libs.versions.toml 的 kotlin 同号。
    alias(libs.plugins.kotlin.compose) apply false
}
