# ═══════════════════════════════════════════════════════════════════════════
# Xposed / LSPosed 入口
# 仅保留 Xposed 入口子类与 libxposed service 的公共 API，其余全部交给 R8 裁剪。
# （体积膨胀的根因是此前对 Compose / Miuix / 模块自身做了 `-keep class X.** { *; }`
#  全量保留，R8 完全无法 tree-shake。参考 HyperModifier：只保留 XposedModule 子类。）
# ═══════════════════════════════════════════════════════════════════════════
-adaptresourcefilecontents META-INF/xposed/java_init.list

# Xposed 入口：libxposed 通过反射实例化并调用，必须保留类名与方法签名
-keep,allowobfuscation,allowoptimization public class * extends com.mars.mimarketpurify.init.EasyXposedInit {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# libxposed service：模块 App 侧通过它写入远程偏好，保留其公共 API
-keep class io.github.libxposed.service.** { *; }

# Application 与四大组件：manifest 中已声明，AGP 默认会保留；此处显式补一遍，
# 防止 R8 full mode 下把被 -repackageclasses 重命名的类与 manifest 对应错。
-keep class com.mars.mimarketpurify.App
-keep class com.mars.mimarketpurify.MainActivity
-keep class com.mars.mimarketpurify.AboutActivity
-keep class com.mars.mimarketpurify.SubSettingsActivity
# EntryGuardReceiver 由 manifest 声明，同样显式保留
-keep class com.mars.mimarketpurify.EntryGuardReceiver

# ═══════════════════════════════════════════════════════════════════════════
# Kotlin / JDK 死代码消除（不影响功能，减小体积）
# ═══════════════════════════════════════════════════════════════════════════
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# 剥离调试日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ═══════════════════════════════════════════════════════════════════════════
# Compose / Miuix / lifecycle / savedstate
# 不再对它们做 `keep class X.** { *; }` 全量保留——Compose 与 Miuix 的 AAR 自带
# consumer-rules，且 AGP 默认的 proguard-android-optimize.txt 已包含 Compose 运行
# 所需规则。配合 R8 full mode 即可大幅裁剪。这里只抑制告警。
# ═══════════════════════════════════════════════════════════════════════════
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.savedstate.**
-dontwarn top.yukonga.miuix.**

# 注解 / 签名 / 内部类：保留以便 Compose 稳定性推断与反射安全（开销极小）
-keepattributes *Annotation*,Signature,InnerClasses

# ═══════════════════════════════════════════════════════════════════════════
# 混淆
# ═══════════════════════════════════════════════════════════════════════════
-repackageclasses ''
-allowaccessmodification
-dontpreverify
-overloadaggressively
-renamesourcefileattribute *

-classobfuscationdictionary obf-dict.txt
-obfuscationdictionary obf-dict.txt
