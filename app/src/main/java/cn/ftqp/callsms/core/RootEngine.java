package cn.ftqp.callsms.core;

import android.content.ContentValues;
import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * root 引擎（兜底）：su -c content ...
 *
 * uid 0 在 AMS 的 checkComponentPermission 里直接放行，所以即使没有模块也能读写系统库。
 * 代价是走的是 shell 解析，值要做引号转义；query 结果靠文本解析（body 里有换行会串行），
 * 所以只作为模块不可用时的备份通道。
 */
public final class RootEngine implements Engine {

    private static final String CONTENT = "/system/bin/content";

    @Override
    public String id() {
        return "root";
    }

    @Override
    public String label() {
        return "root（su + content 命令）";
    }

    @Override
    public String probe(Context ctx) {
        if (Shell.ensureRoot()) {
            return null;
        }
        return "su 不可用（" + Shell.firstLine(Shell.report()) + "）";
    }

    @Override
    public List<Map<String, String>> query(Context ctx, Target target, Filter filter, int limit) throws Exception {
        StringBuilder sb = new StringBuilder(CONTENT).append(" query");
        sb.append(" --uri ").append(shq(target.uri));
        sb.append(" --projection ").append(shq(projection(target)));
        String sel = filter.literalSelection(target);
        if (sel != null) {
            sb.append(" --where ").append(shq(sel));
        }
        sb.append(" --sort ").append(shq(target.dateColumn + " DESC"));
        Shell.Out out = Shell.execRoot(sb.toString(), 20000);
        if (!out.ok()) {
            throw new IllegalStateException("content query 失败: " + firstLines(out.text));
        }
        List<Map<String, String>> rows = parse(out.text);
        if (limit > 0 && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    @Override
    public long insert(Context ctx, Target target, ContentValues values) throws Exception {
        StringBuilder sb = new StringBuilder(CONTENT).append(" insert");
        sb.append(" --uri ").append(shq(target.uri));
        int n = 0;
        for (Map.Entry<String, Object> e : values.valueSet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            char type;
            if (v instanceof Integer) {
                type = 'i';
            } else if (v instanceof Long) {
                type = 'l';
            } else if (v instanceof Double || v instanceof Float) {
                type = 'd';
            } else if (v instanceof Boolean) {
                type = 'i';
                v = ((Boolean) v) ? 1 : 0;
            } else {
                type = 's';
            }
            sb.append(" --bind ").append(shq(e.getKey() + ":" + type + ":" + v));
            n++;
        }
        if (n == 0) {
            throw new IllegalArgumentException("没有可写入的字段");
        }
        Shell.Out out = Shell.execRoot(sb.toString(), 20000);
        if (!out.ok()) {
            throw new IllegalStateException("content insert 失败: " + firstLines(out.text));
        }
        return -1;
    }

    @Override
    public int delete(Context ctx, Target target, Filter filter) throws Exception {
        // 先数一遍：content delete 不回显条数，只能自己查
        int before = count(ctx, target, filter);
        StringBuilder sb = new StringBuilder(CONTENT).append(" delete");
        sb.append(" --uri ").append(shq(target.uri));
        String sel = filter.literalSelection(target);
        if (sel != null) {
            sb.append(" --where ").append(shq(sel));
        }
        Shell.Out out = Shell.execRoot(sb.toString(), 20000);
        if (!out.ok()) {
            throw new IllegalStateException("content delete 失败: " + firstLines(out.text));
        }
        return before;
    }

    /** 只拉 _id 数行数，别为了计数把 body 全搬回来 */
    private int count(Context ctx, Target target, Filter filter) {
        try {
            StringBuilder sb = new StringBuilder(CONTENT).append(" query");
            sb.append(" --uri ").append(shq(target.uri));
            sb.append(" --projection ").append(shq("_id"));
            String sel = filter.literalSelection(target);
            if (sel != null) {
                sb.append(" --where ").append(shq(sel));
            }
            Shell.Out out = Shell.execRoot(sb.toString(), 30000);
            if (!out.ok()) {
                return 0;
            }
            int n = 0;
            for (String line : out.text.split("\n")) {
                if (line.trim().startsWith("Row:")) {
                    n++;
                }
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private String projection(Target target) {
        if (target == Target.CALL) {
            return "_id:number:date:duration:type:name";
        }
        return "_id:address:date:type:read:body";
    }

    /** 单引号包裹 + 内部单引号转义，避免 shell 把值拆开 */
    static String shq(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String firstLines(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() > 400) {
            t = t.substring(0, 400) + "…";
        }
        return t.replace('\n', ' ');
    }

    /** 解析 content query 输出：Row: N col=val, col2=val2 */
    static List<Map<String, String>> parse(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("Row:")) {
                continue;
            }
            // 形如 `Row: 0 _id=1, number=10086, ...`：必须把「Row: <行号> 」整段剥掉，
            // 否则第一列的键会变成 "0 _id"，后续 row.get("_id") 永远拿不到值。
            String body = line.replaceFirst("^Row:\\s*\\d+\\s+", "").trim();
            Map<String, String> row = new LinkedHashMap<>();
            for (String pair : body.split(",(?=\\s*[A-Za-z_][A-Za-z0-9_]*=)")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = pair.substring(0, eq).trim();
                String v = pair.substring(eq + 1).trim();
                row.put(k, v);
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }
}
