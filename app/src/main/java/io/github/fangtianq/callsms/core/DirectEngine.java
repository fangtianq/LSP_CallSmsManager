package io.github.fangtianq.callsms.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直连引擎：直接用自己的 uid 去 query/insert/delete。
 *
 * 现实情况：WRITE_SMS / WRITE_CALL_LOG 在 Android 10+ 是受限权限，
 * 普通应用即使声明了也拿不到，AMS 会在解析 provider 阶段直接 Permission Denial。
 * 所以这条通道大多数机型上只能"读"甚至读也不行，留着是因为：
 *  1）万一 ROM 放水 / 用户把它切成默认短信应用；
 *  2）失败原因对用户可见，便于判断"到底缺什么"。
 */
public final class DirectEngine implements Engine {

    @Override
    public String id() {
        return "direct";
    }

    @Override
    public String label() {
        return "直连 ContentResolver";
    }

    @Override
    public String probe(Context ctx) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse(Target.SMS.uri);
            c = ctx.getContentResolver().query(uri, new String[]{"_id"}, null, null, "_id LIMIT 1");
            return c == null ? "provider 返回空 Cursor" : null;
        } catch (Throwable t) {
            return "不可用（" + t.getClass().getSimpleName() + "）";
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public List<Map<String, String>> query(Context ctx, Target target, Filter filter, int limit) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String sort = target.dateColumn + " DESC";
        if (limit > 0) {
            sort = sort + " LIMIT " + limit;
        }
        Cursor c = cr.query(Uri.parse(target.uri), null, filter.selection(target), filter.args(target), sort);
        if (c == null) {
            throw new IllegalStateException("provider 返回空 Cursor");
        }
        try {
            List<Map<String, String>> rows = new java.util.ArrayList<>();
            String[] names = c.getColumnNames();
            while (c.moveToNext()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < names.length; i++) {
                    row.put(names[i], c.isNull(i) ? null : c.getString(i));
                }
                rows.add(row);
            }
            return rows;
        } finally {
            c.close();
        }
    }

    @Override
    public long insert(Context ctx, Target target, ContentValues values) throws Exception {
        Uri u = ctx.getContentResolver().insert(Uri.parse(target.uri), values);
        if (u == null) {
            throw new IllegalStateException("insert 返回 null（被 provider 拒绝）");
        }
        try {
            return android.content.ContentUris.parseId(u);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public int delete(Context ctx, Target target, Filter filter) throws Exception {
        return ctx.getContentResolver().delete(Uri.parse(target.uri),
                filter.selection(target), filter.args(target));
    }
}
