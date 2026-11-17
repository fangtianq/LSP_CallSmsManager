package cn.ftqp.callsms.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * root 执行封装。
 *
 * 这里踩过坑：直接 {@code new ProcessBuilder("su")} 在某些机型上抛
 * {@code IOException: Cannot run program "su": error=13, Permission denied}——
 * 这说明「找到了 su 但执行被拒」（SELinux 域不允许 exec，或命中了不可执行的 stub），
 * 不代表没 root。所以这里改成逐条候选路径探测，并把每条路径的真实结果原样带回给 UI，
 * 让用户在「配置」页一眼看出卡在哪。
 */
public final class Shell {

    public static final class Out {
        public final int code;
        public final String text;
        public final boolean timeout;

        Out(int code, String text, boolean timeout) {
            this.code = code;
            this.text = text;
            this.timeout = timeout;
        }

        public boolean ok() {
            return code == 0 && !timeout;
        }
    }

    /** 常见 su 落点：PATH、Magisk、KernelSU、APatch 各不相同 */
    private static final String[] CANDIDATES = {
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
    };

    private static final Object LOCK = new Object();
    private static boolean resolved = false;
    private static boolean rootOk = false;
    private static String suPath = null;
    private static boolean viaSh = false;
    private static String report = "尚未检测";

    private Shell() {
    }

    public static String report() {
        synchronized (LOCK) {
            return report;
        }
    }

    public static boolean ensureRoot() {
        return ensureRoot(3500, false);
    }

    /**
     * @param perTryMs 单条候选的超时；用户主动点「授权 root」时给长一点，留出点授权框的时间
     * @param force    重新探测（忽略上次结果）
     */
    public static boolean ensureRoot(long perTryMs, boolean force) {
        synchronized (LOCK) {
            if (resolved && !force) {
                return rootOk;
            }
            resolved = true;
            rootOk = false;
            suPath = null;
            viaSh = false;

            StringBuilder sb = new StringBuilder();
            for (String c : CANDIDATES) {
                Out o = run(new String[]{c, "-c", "id"}, perTryMs);
                if (isRootId(o)) {
                    suPath = c;
                    rootOk = true;
                    report = "su 可用：" + c;
                    return true;
                }
                sb.append("· ").append(c).append(" → ").append(brief(o)).append('\n');
            }

            // 兜底：交给 sh 去解析 su。某些机型 app 自己的 PATH 找不到，sh 能找到
            Out o = run(new String[]{"/system/bin/sh", "-c", "su -c id"}, perTryMs);
            if (isRootId(o)) {
                suPath = "su";
                viaSh = true;
                rootOk = true;
                report = "su 可用（经 sh 解析）";
                return true;
            }
            sb.append("· sh -c \"su -c id\" → ").append(brief(o)).append('\n');

            report = sb.toString().trim();
            return false;
        }
    }

    public static String suPath() {
        synchronized (LOCK) {
            return rootOk ? (viaSh ? "su (via sh)" : suPath) : "-";
        }
    }

    /** 用已探测到的 su 执行一条命令（cmd 整体作为一个 shell 脚本传入） */
    public static Out execRoot(String cmd, long timeoutMs) {
        if (!ensureRoot()) {
            return new Out(-1, "root 不可用：" + firstLine(report), false);
        }
        String path;
        boolean sh;
        synchronized (LOCK) {
            path = suPath;
            sh = viaSh;
        }
        if (!sh) {
            return run(new String[]{path, "-c", cmd}, timeoutMs);
        }
        // viaSh 模式多一层 shell 解析，需要再套一次引号
        return run(new String[]{"/system/bin/sh", "-c", "su -c " + shq(cmd)}, timeoutMs);
    }

    private static boolean isRootId(Out o) {
        return o != null && o.ok() && o.text != null && o.text.contains("uid=0");
    }

    private static String brief(Out o) {
        if (o == null) {
            return "无结果";
        }
        if (o.timeout) {
            return "超时（可能弹了授权框没点，去 root 管理器看看待授权列表）";
        }
        String t = o.text == null ? "" : o.text.trim();
        if (t.isEmpty()) {
            return "退出码 " + o.code + "（无输出）";
        }
        if (t.length() > 180) {
            t = t.substring(0, 180) + "…";
        }
        return t.replace('\n', ' ');
    }

    public static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    /** 单引号包裹 + 内部单引号转义 */
    public static String shq(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static Out run(String[] argv, long timeoutMs) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(true);
            p = pb.start();
            final Process proc = p;
            final AtomicReference<String> outRef = new AtomicReference<>("");
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    outRef.set(readAll(proc));
                }
            }, "su-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                try {
                    p.destroyForcibly();
                } catch (Throwable ignored) {
                }
                reader.join(600);
                return new Out(-1, outRef.get(), true);
            }
            reader.join(1200);
            return new Out(p.exitValue(), outRef.get(), false);
        } catch (Throwable t) {
            String msg = t.getMessage() == null ? String.valueOf(t) : t.getMessage();
            return new Out(-1, "无法执行 " + argv[0] + "：" + msg, false);
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readAll(Process p) {
        try (InputStream is = p.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
