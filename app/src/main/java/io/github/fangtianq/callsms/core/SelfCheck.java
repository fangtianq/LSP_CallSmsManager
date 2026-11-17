package io.github.fangtianq.callsms.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 一次完整的可用性自检。
 *
 * 关键在于「自我体检」：读已安装 APK 里的 META-INF/xposed/*，
 * 直接判断你装进去的这个包到底有没有被 LSPosed 认成模块的资格
 * （踩过的坑：java_init.list 里多一行 # 注释，LSPosed 会把注释当类名 → 模块静默不加载）。
 */
public final class SelfCheck {

    public static final class Status {
        public String versionName = "-";
        public long lastUpdate = 0;

        /** 已安装 APK 的模块元数据是否合法 */
        public boolean metaOk;
        public String metaReport = "";

        public boolean moduleSms;
        public boolean moduleCall;
        public String moduleSmsRaw = "";
        public String moduleCallRaw = "";

        /** null 表示直连可用 */
        public String directReason;
        public boolean rootOk;
        public String rootReport = "";

        public String rootPath = "-";

        public boolean moduleReady() {
            return moduleSms && moduleCall;
        }

        /** 至少有一条通道能干活 */
        public boolean anyUsable() {
            return moduleReady() || rootOk || directReason == null;
        }
    }

    private SelfCheck() {
    }

    public static Status run(Context ctx) {
        Status s = new Status();
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            s.versionName = pi.versionName;
            s.lastUpdate = pi.lastUpdateTime;
        } catch (Throwable ignored) {
        }

        checkInstalledApk(ctx, s);

        String a = ModuleEngine.ping(ctx, Target.SMS);
        s.moduleSms = Json.isOk(a);
        s.moduleSmsRaw = a == null || a.isEmpty() ? "(无接收者应答)" : a;

        String b = ModuleEngine.ping(ctx, Target.CALL);
        s.moduleCall = Json.isOk(b);
        s.moduleCallRaw = b == null || b.isEmpty() ? "(无接收者应答)" : b;

        s.directReason = new DirectEngine().probe(ctx);

        s.rootOk = Shell.ensureRoot(3000, false);
        s.rootReport = Shell.report();
        s.rootPath = Shell.suPath();
        return s;
    }

    /** 读自己已安装 APK 的 META-INF/xposed，校验入口清单格式 */
    private static void checkInstalledApk(Context ctx, Status s) {
        ZipFile z = null;
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), 0);
            z = new ZipFile(ai.publicSourceDir);
            String init = read(z, "META-INF/xposed/java_init.list");
            String prop = read(z, "META-INF/xposed/module.prop");
            String scope = read(z, "META-INF/xposed/scope.list");

            if (init == null || prop == null || scope == null) {
                s.metaOk = false;
                s.metaReport = "❌ 已安装 APK 缺少模块元数据（init=" + (init != null)
                        + " prop=" + (prop != null) + " scope=" + (scope != null) + "）";
                return;
            }

            StringBuilder bad = new StringBuilder();
            int classCount = 0;
            for (String line : init.split("\n")) {
                String l = line.trim();
                if (l.isEmpty()) {
                    continue;
                }
                if (l.startsWith("#")) {
                    bad.append("入口清单里有注释行（LSPosed 会把它当类名加载）：").append(l).append('\n');
                } else if (!l.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+")) {
                    bad.append("入口类名不合法：").append(l).append('\n');
                } else {
                    classCount++;
                }
            }
            if (classCount == 0) {
                bad.append("入口清单里没有任何类名\n");
            }
            s.metaOk = bad.length() == 0;
            s.metaReport = (s.metaOk ? "✅ 元数据合法" : "❌ " + bad.toString().trim())
                    + "\n入口：" + oneLine(init)
                    + "\n作用域：" + oneLine(scope.replace("\n", ", "));
        } catch (Throwable t) {
            s.metaOk = false;
            s.metaReport = "读取自身 APK 失败：" + t;
        } finally {
            if (z != null) {
                try {
                    z.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String read(ZipFile z, String name) throws Exception {
        ZipEntry e = z.getEntry(name);
        if (e == null) {
            return null;
        }
        try (InputStream is = z.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = is.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toString("UTF-8");
        }
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "-";
        }
        return s.replace("\r", "").replace("\n", " | ").trim();
    }

    /** 给人看的整体结论 */
    public static String headline(Status s) {
        if (s.moduleReady()) {
            return "✅ 模块通道就绪，可以正常读写";
        }
        if (s.rootOk) {
            return "⚠ 模块没生效，正在用 root 兜底（能写，但 root 通道对换行正文解析不全）";
        }
        if (s.directReason == null) {
            return "⚠ 只有直连可用（权限随时可能被收回）";
        }
        return "❌ 没有任何可用通道：现在既写不进去也读不出来";
    }

    /** 下一步该干什么（按优先级给一条最关键的） */
    public static String nextStep(Status s) {
        if (s.moduleReady()) {
            return "无需处理。";
        }
        if (!s.metaOk) {
            return "你装的这个 APK 模块元数据有问题 → 重新安装最新版 APK（1.1.0+）后重启手机。";
        }
        if (!s.rootOk) {
            return "先解决 root：「配置」页点「授权 root」，弹框里选允许。\n"
                    + "如果弹不出授权框，看下面的 su 探测明细——说明 su 对应用进程执行被拒，"
                    + "通常是 Magisk 里本应用没有授权，或装了隐藏/拦截类模块。";
        }
        return "root 已通，但模块没应答 → 依次检查：\n"
                + "① LSPosed 里「通话短信工具箱」是否已启用（不是只装了）；\n"
                + "② 作用域是否勾了 com.android.providers.telephony 和 com.android.providers.contacts；\n"
                + "③ 勾完必须重启手机（或点本页「重启短信/通话服务」）；\n"
                + "④ 仍不行：LSPosed → 日志，搜 FTQCallSms 看有没有 module_loaded。";
    }

    /** 完整诊断文本，供复制粘贴 */
    public static String dump(Status s) {
        StringBuilder sb = new StringBuilder();
        sb.append("== 通话短信工具箱诊断 ==\n");
        sb.append("App 版本：").append(s.versionName).append('\n');
        if (s.lastUpdate > 0) {
            sb.append("安装时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(s.lastUpdate))).append('\n');
        }
        sb.append('\n');
        sb.append("模块元数据：").append(s.metaOk ? "OK" : "BAD").append('\n');
        sb.append(s.metaReport).append('\n');
        sb.append('\n');
        sb.append("模块应答：\n");
        sb.append("· 短信 provider：").append(s.moduleSms ? "OK " : "NO ").append(s.moduleSmsRaw).append('\n');
        sb.append("· 通话 provider：").append(s.moduleCall ? "OK " : "NO ").append(s.moduleCallRaw).append('\n');
        sb.append('\n');
        sb.append("直连：").append(s.directReason == null ? "OK" : s.directReason).append('\n');
        sb.append('\n');
        sb.append("root：").append(s.rootOk ? "OK (" + s.rootPath + ")" : "FAIL").append('\n');
        sb.append(s.rootReport).append('\n');
        return sb.toString();
    }
}
