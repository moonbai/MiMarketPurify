# Xposed
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends com.mars.mimarketpurify.init.EasyXposedInit {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# libxposed service（模块 App 侧通过它写入远程偏好，需保留类名）
-keep class io.github.libxposed.service.** { *; }
-keep class com.mars.mimarketpurify.App { *; }
-keep class com.mars.mimarketpurify.MainActivity { *; }
-keep class com.mars.mimarketpurify.AboutActivity { *; }

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Strip debug log
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ═══════════════ 新增：Compose + Miuix KMP（悬浮底栏重写）═══════════════
# Compose runtime 必须保留，否则 R8 会误删重组/稳定性推断所需的类
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-dontwarn androidx.compose.**
# Miuix KMP（毛玻璃 + 图标，公开依赖，保留其公共 API）
-keep class top.yukonga.miuix.kmp.** { *; }
-dontwarn top.yukonga.miuix.kmp.**
# 模块自有包：保留类名，避免被 -repackageclasses 重命名破坏 Xposed 入口与 Compose 反射引用
-keep class com.mars.mimarketpurify.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses

# Obfuscation
-repackageclasses ''
-allowaccessmodification
-dontpreverify
-overloadaggressively
-renamesourcefileattribute *

-classobfuscationdictionary obf-dict.txt
-obfuscationdictionary obf-dict.txt
