package io.github.fangtianq.callsms.core;

import android.content.ContentValues;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 引擎路由：按「记住上次成功的引擎 → 直连 → 模块 → root」顺序试，
 * 任何一个成功就返回，失败原因全部收集起来给 UI 看。
 */
public final class Router {

    private static final Engine[] ALL = {new DirectEngine(), new ModuleEngine(), new RootEngine()};
    private static volatile String stickyId;

    private Router() {
    }

    private interface Call<T> {
        T run(Engine engine) throws Exception;
    }

    private static final class Attempt<T> {
        T value;
        Engine engine;
        final StringBuilder notes = new StringBuilder();

        void note(Engine e, Throwable t) {
            if (notes.length() > 0) {
                notes.append("；");
            }
            notes.append(e.id()).append(": ").append(brief(t));
        }

        String noteText() {
            return notes.length() == 0 ? null : notes.toString();
        }
    }

    private static String brief(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String m = t.getMessage();
        if (m == null) {
            m = t.getClass().getSimpleName();
        }
        if (m.length() > 160) {
            m = m.substring(0, 160) + "…";
        }
        return m;
    }

    private static List<Engine> ordered() {
        List<Engine> list = new ArrayList<>();
        String prefer = stickyId;
        if (prefer != null) {
            for (Engine e : ALL) {
                if (e.id().equals(prefer)) {
                    list.add(e);
                }
            }
        }
        for (Engine e : ALL) {
            if (!list.contains(e)) {
                list.add(e);
            }
        }
        return list;
    }

    private static <T> Attempt<T> run(String op, Call<T> call) {
        Attempt<T> a = new Attempt<>();
        for (Engine e : ordered()) {
            long t0 = System.currentTimeMillis();
            try {
                a.value = call.run(e);
                a.engine = e;
                stickyId = e.id();
                Logs.d(op + " via " + e.id() + " ok in " + (System.currentTimeMillis() - t0) + "ms"
                        + (a.notes.length() > 0 ? " (之前失败: " + a.notes + ")" : ""));
                return a;
            } catch (Throwable t) {
                a.note(e, t);
                Logs.w(op + " via " + e.id() + " failed", t);
            }
        }
        return a;
    }

    public static OpResult query(Context ctx, Target target, Filter filter, int limit) {
        Attempt<List<Map<String, String>>> a = run("query", new Call<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> run(Engine engine) throws Exception {
                return engine.query(ctx, target, filter, limit);
            }
        });
        if (a.engine == null) {
            return OpResult.fail("所有通道都失败：" + a.notes, null);
        }
        return OpResult.rows(a.engine.id(), a.value, a.noteText());
    }

    public static OpResult insert(Context ctx, Target target, ContentValues values) {
        Attempt<Long> a = run("insert", new Call<Long>() {
            @Override
            public Long run(Engine engine) throws Exception {
                return engine.insert(ctx, target, values);
            }
        });
        if (a.engine == null) {
            return OpResult.fail("所有通道都失败：" + a.notes, null);
        }
        return OpResult.inserted(a.engine.id(), a.value == null ? -1 : a.value, a.noteText());
    }

    public static OpResult delete(Context ctx, Target target, Filter filter) {
        Attempt<Integer> a = run("delete", new Call<Integer>() {
            @Override
            public Integer run(Engine engine) throws Exception {
                return engine.delete(ctx, target, filter);
            }
        });
        if (a.engine == null) {
            return OpResult.fail("所有通道都失败：" + a.notes, null);
        }
        return OpResult.deleted(a.engine.id(), a.value == null ? 0 : a.value, a.noteText());
    }

    /** 自检：逐个引擎探测可用性，返回可直接展示的文本行 */
    public static String probeAll(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Engine e : ALL) {
            String reason;
            try {
                reason = e.probe(ctx);
            } catch (Throwable t) {
                reason = "探测异常：" + t;
            }
            sb.append(reason == null ? "✅ " : "❌ ").append(e.label());
            if (reason != null) {
                sb.append(" — ").append(reason);
            }
            sb.append('\n');
        }
        sb.append('\n');
        sb.append("provider 进程（模块应答）：\n");
        for (Target t : Target.values()) {
            String r = ModuleEngine.ping(ctx, t);
            if (Json.isOk(r)) {
                sb.append("✅ ").append(t.label).append(" → ").append(t.providerPackage)
                        .append(" pid=").append(Json.optInt(r, "pid", -1))
                        .append(" uid=").append(Json.optInt(r, "uid", -1)).append('\n');
            } else {
                sb.append("❌ ").append(t.label).append(" → ").append(t.providerPackage)
                        .append(" 无应答\n");
            }
        }
        return sb.toString();
    }
}
