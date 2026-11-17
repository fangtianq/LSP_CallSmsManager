# libxposed / LSPosed 模块必备规则
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 入口与桥接类（反射/框架按名加载）
-keep class cn.ftqp.callsms.xposed.** { *; }

# appcompat 常规
-keep class androidx.appcompat.** { *; }
