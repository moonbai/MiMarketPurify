# ═══════════════════════════════════════════════════════════════════════════
# R8 / ProGuard 规则（对齐 HyperModifier 的极简思路）
# 体积膨胀的根因是此前对 Compose / Miuix / 模块自身做了 `keep class X.** { *; }`
# 全量保留，R8 完全无法 tree-shake；以及 CI 长期只打未混淆的 debug 包（33M）。
# 现在统一交给 release 构建（isMinifyEnabled + isShrinkResources + R8 full mode）
# 来裁剪，这里只保留「反射入口」与「manifest 组件」，其余一律放开混淆与裁剪。
# ═══════════════════════════════════════════════════════════════════════════

# Xposed 入口：libxposed 在【编译期】把入口类的【原类名】写入 META-INF/xposed/*，
# 运行时按该原类名反射加载。因此【严禁 allowobfuscation】——一旦 R8 把 MainHook 重命名，
# 框架就找不到入口，结果就是「全部 hook 失效」。
# 这里保留类名与全部成员（不混淆、不重命名），保证入口可被框架按原类名找到。
-keep public class * extends com.mars.mimarketpurify.init.EasyXposedInit { *; }

# libxposed service：模块 App 侧通过它写入远程偏好，保留其公共 API
-keep class io.github.libxposed.service.** { *; }

# Application 与四大组件：manifest 中已声明，release 下 R8 full mode 需显式保留，
# 否则可能被误裁或重命名导致 ActivityNotFoundException / 组件不可见。
-keep class com.mars.mimarketpurify.App
-keep class com.mars.mimarketpurify.MainActivity
-keep class com.mars.mimarketpurify.AboutActivity
-keep class com.mars.mimarketpurify.SubSettingsActivity
-keep class com.mars.mimarketpurify.EntryGuardReceiver

# Compose / Miuix / libxposed 注解：库自带 consumer-rules，这里仅抑制告警
-dontwarn io.github.libxposed.annotation.**
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.savedstate.**
-dontwarn top.yukonga.miuix.**

# 死代码消除（不影响功能，进一步减小体积）
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
