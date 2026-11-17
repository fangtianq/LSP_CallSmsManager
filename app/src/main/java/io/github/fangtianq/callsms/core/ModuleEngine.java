package io.github.fangtianq.callsms.core;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 模块引擎（主力）：把请求用有序广播丢给 provider 进程，由模块代码在那边用自己的 uid 执行。
 *
 * 这条路上没有任何权限问题——provider 自己访问自己属于同 uid，AMS 直接放行，
 * 而且插入走的是 provider 的正常 insert 路径，threads 表、变更通知都由系统自己维护，
 * 不会像直接改 sqlite 那样把数据写花。
 */
public final class ModuleEngine implements Engine {

    @Override
    public String id() {
        return "module";
    }

    @Override
    public String label() {
        return "LSPosed 模块（provider 进程内执行）";
    }

    @Override
    public String probe(Context ctx) {
        String r = ping(ctx, Target.SMS);
        return Json.isOk(r) ? null : "无应答（模块未启用 / 未勾选 scope / provider 进程未启动）";
    }

    /** 单独 ping 某个 provider 包，返回原始 JSON（""=无接收者应答） */
    public static String ping(Context ctx, Target target) {
        try {
            return roundTrip(ctx, baseIntent(target, Bridge.OP_PING), 2500);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public List<Map<String, String>> query(Context ctx, Target target, Filter filter, int limit) throws Exception {
        Intent in = baseIntent(target, Bridge.OP_QUERY);
        in.putExtra(Bridge.EXTRA_URI, target.uri);
        String sel = filter.selection(target);
        if (sel != null) {
            in.putExtra(Bridge.EXTRA_SELECTION, sel);
            in.putExtra(Bridge.EXTRA_SELECTION_ARGS, filter.args(target));
        }
        in.putExtra(Bridge.EXTRA_SORT, target.dateColumn + " DESC");
        in.putExtra(Bridge.EXTRA_LIMIT, limit);
        String r = roundTrip(ctx, in, Bridge.TIMEOUT_MS);
        if (r == null || r.isEmpty()) {
            throw new IllegalStateException("模块无应答");
        }
        if (!Json.isOk(r)) {
            throw new IllegalStateException(Json.errorOf(r));
        }
        return Json.parseRows(r);
    }

    @Override
    public long insert(Context ctx, Target target, ContentValues values) throws Exception {
        Intent in = baseIntent(target, Bridge.OP_INSERT);
        in.putExtra(Bridge.EXTRA_URI, target.uri);
        in.putExtra(Bridge.EXTRA_VALUES, toBundle(values));
        String r = roundTrip(ctx, in, Bridge.TIMEOUT_MS);
        if (r == null || r.isEmpty()) {
            throw new IllegalStateException("模块无应答");
        }
        if (!Json.isOk(r)) {
            throw new IllegalStateException(Json.errorOf(r));
        }
        return Json.optLong(r, "id", -1);
    }

    @Override
    public int delete(Context ctx, Target target, Filter filter) throws Exception {
        Intent in = baseIntent(target, Bridge.OP_DELETE);
        in.putExtra(Bridge.EXTRA_URI, target.uri);
        String sel = filter.selection(target);
        if (sel != null) {
            in.putExtra(Bridge.EXTRA_SELECTION, sel);
            in.putExtra(Bridge.EXTRA_SELECTION_ARGS, filter.args(target));
        }
        String r = roundTrip(ctx, in, Bridge.TIMEOUT_MS);
        if (r == null || r.isEmpty()) {
            throw new IllegalStateException("模块无应答");
        }
        if (!Json.isOk(r)) {
            throw new IllegalStateException(Json.errorOf(r));
        }
        return Json.optInt(r, "count", 0);
    }

    private static Intent baseIntent(Target target, String op) {
        Intent in = new Intent(Bridge.ACTION);
        in.setPackage(target.providerPackage);
        in.putExtra(Bridge.EXTRA_OP, op);
        return in;
    }

    private static Bundle toBundle(ContentValues values) {
        Bundle b = new Bundle();
        for (Map.Entry<String, Object> e : values.valueSet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Integer) {
                b.putInt(k, (Integer) v);
            } else if (v instanceof Long) {
                b.putLong(k, (Long) v);
            } else if (v instanceof Double) {
                b.putDouble(k, (Double) v);
            } else if (v instanceof Float) {
                b.putDouble(k, ((Float) v).doubleValue());
            } else if (v instanceof Boolean) {
                b.putInt(k, ((Boolean) v) ? 1 : 0);
            } else {
                b.putString(k, String.valueOf(v));
            }
        }
        return b;
    }

    /** 有序广播往返，返回结果 data（"" 表示没有接收者应答） */
    static String roundTrip(Context ctx, Intent intent, int timeoutMs) throws Exception {
        final LinkedBlockingQueue<String> box = new LinkedBlockingQueue<>();
        BroadcastReceiver rr = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                String d = getResultData();
                box.offer(d == null ? "" : d);
            }
        };
        ctx.sendOrderedBroadcast(intent, null, rr, new Handler(Looper.getMainLooper()),
                Activity.RESULT_OK, null, null);
        return box.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
