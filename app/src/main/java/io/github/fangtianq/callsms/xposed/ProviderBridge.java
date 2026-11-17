package io.github.fangtianq.callsms.xposed;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.fangtianq.callsms.core.Bridge;
import io.github.fangtianq.callsms.core.Json;
import io.github.fangtianq.callsms.core.Target;
import io.github.libxposed.api.XposedModule;

/**
 * 运行在 provider 进程（com.android.providers.telephony / contacts）里的执行器。
 *
 * 关键点：这里的 getContentResolver() 用的是 provider 自己的 uid，
 * 访问自己进程的 provider 时 AMS 的权限检查直接放行（uid == owningUid），
 * 因此不需要 WRITE_SMS / WRITE_CALL_LOG 这类受限权限。
 */
public final class ProviderBridge {

    public static final String VERSION = "1.0.0";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static volatile XposedModule moduleRef;
    private static volatile String installedVia = "-";

    private ProviderBridge() {
    }

    public static void install(XposedModule module, Context ctx) {
        if (module == null || ctx == null) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        moduleRef = module;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String result = Json.err("handler crashed");
                    try {
                        if (!fromTrustedSender(this)) {
                            result = Json.err("forbidden sender");
                        } else {
                            result = handle(context, intent);
                        }
                    } catch (Throwable t) {
                        logErr("handle failed", t);
                        result = Json.err(t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                    try {
                        setResultCode(0);
                        setResultData(result);
                    } catch (Throwable ignored) {
                        // 非有序广播时没有 result 通道，忽略
                    }
                }
            };
            IntentFilter filter = new IntentFilter(Bridge.ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                // targetSdk 33+ 的进程注册"非系统广播"必须显式声明导出标记，否则直接 SecurityException
                ctx.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, filter);
            }
            installedVia = ctx.getPackageName();
            log("bridge_ready via=" + installedVia + " pid=" + Process.myPid()
                    + " uid=" + Process.myUid() + " version=" + VERSION);
        } catch (Throwable t) {
            INSTALLED.set(false);
            logErr("bridge_install_failed", t);
        }
    }

    /** API 34+ 才能拿到发送方包名；拿不到（如 shell / 老系统）时放行，仅记日志 */
    private static boolean fromTrustedSender(BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        try {
            String from = receiver.getSentFromPackage();
            if (from == null) {
                return true;
            }
            return "io.github.fangtianq.callsms".equals(from);
        } catch (Throwable t) {
            return true;
        }
    }

    public static String installedVia() {
        return installedVia;
    }

    private static String handle(Context ctx, Intent in) {
        String op = in.getStringExtra(Bridge.EXTRA_OP);
        if (op == null) {
            return Json.err("missing op");
        }
        if (Bridge.OP_PING.equals(op)) {
            try {
                JSONObject o = new JSONObject();
                o.put("version", VERSION);
                o.put("pkg", ctx.getPackageName());
                o.put("pid", Process.myPid());
                o.put("uid", Process.myUid());
                return Json.ok(o);
            } catch (Throwable t) {
                return Json.err("ping failed");
            }
        }

        String uriStr = in.getStringExtra(Bridge.EXTRA_URI);
        if (uriStr == null) {
            return Json.err("missing uri");
        }
        if (!allowedUri(uriStr)) {
            // 白名单：不让这条通道变成"任意库任意写"的后门
            return Json.err("uri not allowed: " + uriStr);
        }

        ContentResolver cr = ctx.getContentResolver();
        Uri uri = Uri.parse(uriStr);
        String selection = in.getStringExtra(Bridge.EXTRA_SELECTION);
        String[] selectionArgs = in.getStringArrayExtra(Bridge.EXTRA_SELECTION_ARGS);

        try {
            if (Bridge.OP_QUERY.equals(op)) {
                String sort = in.getStringExtra(Bridge.EXTRA_SORT);
                int limit = in.getIntExtra(Bridge.EXTRA_LIMIT, Bridge.MAX_ROWS);
                if (limit <= 0 || limit > Bridge.MAX_ROWS) {
                    limit = Bridge.MAX_ROWS;
                }
                Cursor c = cr.query(uri, null, selection, selectionArgs, sort);
                try {
                    JSONArray rows = Json.cursorToRows(c, limit);
                    JSONObject o = new JSONObject();
                    o.put("rows", rows);
                    return Json.ok(o);
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }

            if (Bridge.OP_INSERT.equals(op)) {
                ContentValues cv = fromBundle(in.getBundleExtra(Bridge.EXTRA_VALUES));
                Uri inserted = cr.insert(uri, cv);
                if (inserted == null) {
                    return Json.err("insert returned null");
                }
                JSONObject o = new JSONObject();
                try {
                    o.put("id", ContentUris.parseId(inserted));
                } catch (Throwable t) {
                    o.put("id", -1);
                }
                return Json.ok(o);
            }

            if (Bridge.OP_DELETE.equals(op)) {
                int n = cr.delete(uri, selection, selectionArgs);
                JSONObject o = new JSONObject();
                o.put("count", n);
                return Json.ok(o);
            }

            return Json.err("unknown op: " + op);
        } catch (Throwable t) {
            logErr("op_" + op + "_failed", t);
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            return Json.err(msg);
        }
    }

    private static boolean allowedUri(String uri) {
        for (Target t : Target.values()) {
            if (t.uri.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    private static ContentValues fromBundle(Bundle b) {
        ContentValues cv = new ContentValues();
        if (b == null) {
            return cv;
        }
        for (String k : b.keySet()) {
            Object v = b.get(k);
            if (v == null) {
                continue;
            }
            if (v instanceof Integer) {
                cv.put(k, (Integer) v);
            } else if (v instanceof Long) {
                cv.put(k, (Long) v);
            } else if (v instanceof Double) {
                cv.put(k, (Double) v);
            } else if (v instanceof Float) {
                cv.put(k, (Float) v);
            } else if (v instanceof Boolean) {
                cv.put(k, (Boolean) v);
            } else if (v instanceof byte[]) {
                cv.put(k, (byte[]) v);
            } else {
                cv.put(k, String.valueOf(v));
            }
        }
        return cv;
    }

    private static void log(String msg) {
        XposedModule m = moduleRef;
        if (m != null) {
            try {
                m.log(android.util.Log.INFO, "FTQCallSms", msg);
            } catch (Throwable ignored) {
            }
        }
        android.util.Log.i("FTQCallSms", msg);
    }

    private static void logErr(String msg, Throwable t) {
        XposedModule m = moduleRef;
        if (m != null) {
            try {
                m.log(android.util.Log.ERROR, "FTQCallSms", msg + " | " + t, t);
            } catch (Throwable ignored) {
            }
        }
        android.util.Log.e("FTQCallSms", msg, t);
    }
}
