package cn.ftqp.callsms.core;

import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 工具：统一「Cursor → JSON」与「JSON → 行列表」的编解码。
 * 依赖系统自带的 org.json，不引入 gson，减少打包体积和离线依赖。
 */
public final class Json {

    private Json() {
    }

    public static String ok() {
        return "{\"ok\":true}";
    }

    public static String ok(JSONObject extra) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            if (extra != null) {
                Iterator<String> it = extra.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    o.put(k, extra.get(k));
                }
            }
            return o.toString();
        } catch (JSONException e) {
            return "{\"ok\":true}";
        }
    }

    public static String err(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", message == null ? "unknown" : message);
            return o.toString();
        } catch (JSONException e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }

    /** Cursor 全部列转 JSON 行数组，超长文本截断，行数封顶 */
    public static JSONArray cursorToRows(Cursor c, int maxRows) throws JSONException {
        JSONArray arr = new JSONArray();
        if (c == null) {
            return arr;
        }
        String[] names = c.getColumnNames();
        int n = 0;
        while (c.moveToNext() && n < maxRows) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < names.length; i++) {
                switch (c.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL:
                        row.put(names[i], JSONObject.NULL);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        row.put(names[i], c.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        row.put(names[i], c.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        row.put(names[i], "<blob>");
                        break;
                    default:
                        String s = c.getString(i);
                        if (s != null && s.length() > Bridge.MAX_TEXT) {
                            s = s.substring(0, Bridge.MAX_TEXT) + "…(截断)";
                        }
                        row.put(names[i], s == null ? JSONObject.NULL : s);
                        break;
                }
            }
            arr.put(row);
            n++;
        }
        return arr;
    }

    /** 解析 {"ok":true,"rows":[...]} 的 rows 部分 */
    public static List<Map<String, String>> parseRows(String json) {
        List<Map<String, String>> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("rows");
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.optJSONObject(i);
                if (r == null) {
                    continue;
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                Iterator<String> it = r.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object v = r.opt(k);
                    row.put(k, (v == null || v == JSONObject.NULL) ? null : String.valueOf(v));
                }
                out.add(row);
            }
        } catch (JSONException ignored) {
            // 解析失败按空结果处理，调用方会看到 0 行
        }
        return out;
    }

    public static boolean isOk(String json) {
        try {
            return new JSONObject(json).optBoolean("ok", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static String errorOf(String json) {
        try {
            return new JSONObject(json).optString("error", "未知错误");
        } catch (Exception e) {
            return json == null ? "空应答" : json;
        }
    }

    public static long optLong(String json, String key, long def) {
        try {
            return new JSONObject(json).optLong(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static int optInt(String json, String key, int def) {
        try {
            return new JSONObject(json).optInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static String optString(String json, String key, String def) {
        try {
            return new JSONObject(json).optString(key, def);
        } catch (Exception e) {
            return def;
        }
    }
}
