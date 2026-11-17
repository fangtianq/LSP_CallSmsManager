package cn.ftqp.callsms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 引擎调用的统一返回体：成功/失败 + 走的是哪个引擎 + 行/条数 */
public final class OpResult {

    public final boolean ok;
    public final String engine;
    public final String error;
    public final List<Map<String, String>> rows;
    public final long id;
    public final int count;
    /** 前一个引擎失败的原因串（便于判断"模块没生效 + root 兜底"这类情况） */
    public final String fallbackNote;

    private OpResult(boolean ok, String engine, String error, List<Map<String, String>> rows,
                     long id, int count, String fallbackNote) {
        this.ok = ok;
        this.engine = engine;
        this.error = error;
        this.rows = rows == null ? new ArrayList<Map<String, String>>() : rows;
        this.id = id;
        this.count = count;
        this.fallbackNote = fallbackNote;
    }

    public static OpResult rows(String engine, List<Map<String, String>> rows, String note) {
        return new OpResult(true, engine, null, rows, -1, rows.size(), note);
    }

    public static OpResult inserted(String engine, long id, String note) {
        return new OpResult(true, engine, null, null, id, 1, note);
    }

    public static OpResult deleted(String engine, int count, String note) {
        return new OpResult(true, engine, null, null, -1, count, note);
    }

    public static OpResult fail(String error, String note) {
        return new OpResult(false, null, error, null, -1, 0, note);
    }
}
