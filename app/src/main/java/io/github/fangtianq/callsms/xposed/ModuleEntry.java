package io.github.fangtianq.callsms.xposed;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.fangtianq.callsms.core.Target;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 模块入口（API 102）。
 *
 * scope 只有两个 provider 包：com.android.providers.telephony / com.android.providers.contacts。
 * 不碰 system_server、不碰 SystemUI —— 这两个进程只要 Hook 出问题就会牵连整机。
 *
 * 安装时机：onPackageReady 拿目标进程的 ClassLoader，Hook 框架类 ContentProvider.attachInfo，
 * 等它真正被 provider 调用时，把 Context 交给 ProviderBridge 去注册广播接收器。
 */
public final class ModuleEntry extends XposedModule {

    private static final String TAG = "FTQCallSms";

    private volatile String processName = "";
    private volatile boolean installed;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(Log.INFO, TAG, "module_loaded process=" + processName
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + "@" + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!Target.isScopePackage(pkg)) {
            return;
        }
        // provider 进程的进程名 == 包名；同包的其它子进程不需要装
        if (!pkg.equals(processName)) {
            log(Log.INFO, TAG, "skip process=" + processName + " pkg=" + pkg);
            return;
        }
        installHooks(param.getClassLoader(), pkg);
    }

    private synchronized void installHooks(ClassLoader cl, String pkg) {
        if (installed) {
            log(Log.INFO, TAG, "install_skipped reason=already_installed pkg=" + pkg);
            return;
        }
        installed = true;

        try {
            Class<?> cp = cl.loadClass("android.content.ContentProvider");
            Method attach = cp.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
            attach.setAccessible(true);
            // 不用 setId：API 101 的 HookBuilder 没有该方法，去掉后可在只支持 101 的 LSPosed 上加载
            hook(attach)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        // Chain 在 proceed() 之后状态不可依赖，参数必须在调用前取
                        Object ctxArg = null;
                        try {
                            ctxArg = chain.getArg(0);
                        } catch (Throwable ignored) {
                        }
                        try {
                            return chain.proceed();
                        } finally {
                            try {
                                if (ctxArg instanceof Context) {
                                    ProviderBridge.install(this, (Context) ctxArg);
                                }
                            } catch (Throwable t) {
                                log(Log.ERROR, TAG, "capture_context_failed", t);
                            }
                        }
                    });
            log(Log.INFO, TAG, "hook_registered ContentProvider.attachInfo pkg=" + pkg);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook_attachInfo_failed pkg=" + pkg, t);
        }

        // 兜底：个别 ROM 的 provider 覆写了 attachInfo 且不回调 super，
        // 这里再尝试从 ActivityThread 的系统上下文注册一次（注册是幂等的）。
        installFromSystemContext();
    }

    private void installFromSystemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method current = at.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object thread = current.invoke(null);
            if (thread == null) {
                return;
            }
            Method getCtx = at.getDeclaredMethod("getSystemContext");
            getCtx.setAccessible(true);
            Object ctx = getCtx.invoke(thread);
            if (ctx instanceof Context) {
                log(Log.INFO, TAG, "fallback system_context ok");
                ProviderBridge.install(this, (Context) ctx);
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "fallback system_context unavailable: " + t);
        }
    }
}
