package cn.ftqp.callsms.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询 / 删除条件。空条件的语义由调用方决定：查询=全部，删除=清空（UI 会二次确认）。
 */
public final class Filter {

    /** 号码；默认模糊匹配（LIKE %x%），exactNumber=true 时精确匹配（=） */
    public String number;
    /** 精确匹配号码——按单条删除时用，避免 LIKE 误删同号码的其它记录 */
    public boolean exactNumber;
    /** 起始时间（毫秒，含） */
    public Long from;
    /** 结束时间（毫秒，含） */
    public Long to;
    /** 类型（来电/去电/收件/发件…），null=不限 */
    public Integer type;
    /** 正文关键字（仅短信） */
    public String keyword;
    /** 精确删除单条（按 _id），优先级最高 */
    public Long id;

    public boolean isEmpty() {
        return id == null && isBlank(number) && from == null && to == null
                && type == null && isBlank(keyword);
    }

    public static Filter byId(long id) {
        Filter f = new Filter();
        f.id = id;
        return f;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public String selection(Target target) {
        List<String> parts = new ArrayList<>();
        if (id != null) {
            parts.add("_id = ?");
        }
        if (!isBlank(number)) {
            parts.add(target.numberColumn + (exactNumber ? " = ?" : " LIKE ?"));
        }
        if (from != null) {
            parts.add(target.dateColumn + " >= ?");
        }
        if (to != null) {
            parts.add(target.dateColumn + " <= ?");
        }
        if (type != null) {
            parts.add(target.typeColumn + " = ?");
        }
        if (!isBlank(keyword) && target.hasText()) {
            parts.add(target.textColumn + " LIKE ?");
        }
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public String[] args(Target target) {
        List<String> args = new ArrayList<>();
        if (id != null) {
            args.add(String.valueOf(id));
        }
        if (!isBlank(number)) {
            args.add(exactNumber ? number.trim() : "%" + number.trim() + "%");
        }
        if (from != null) {
            args.add(String.valueOf(from));
        }
        if (to != null) {
            args.add(String.valueOf(to));
        }
        if (type != null) {
            args.add(String.valueOf(type));
        }
        if (!isBlank(keyword) && target.hasText()) {
            args.add("%" + keyword.trim() + "%");
        }
        return args.isEmpty() ? null : args.toArray(new String[0]);
    }

    /**
     * 内联字面量的 SQL 片段，给 root 引擎的 `content --where` 用
     * （那条链路没有参数绑定，只能自己拼；单引号做 '' 转义）。
     */
    public String literalSelection(Target target) {
        List<String> parts = new ArrayList<>();
        if (id != null) {
            parts.add("_id = " + id);
        }
        if (!isBlank(number)) {
            parts.add(target.numberColumn + (exactNumber ? " = " : " LIKE ")
                    + quote(exactNumber ? number.trim() : "%" + number.trim() + "%"));
        }
        if (from != null) {
            parts.add(target.dateColumn + " >= " + from);
        }
        if (to != null) {
            parts.add(target.dateColumn + " <= " + to);
        }
        if (type != null) {
            parts.add(target.typeColumn + " = " + type);
        }
        if (!isBlank(keyword) && target.hasText()) {
            parts.add(target.textColumn + " LIKE " + quote("%" + keyword.trim() + "%"));
        }
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /** 给人看的条件摘要，用于删除确认弹窗 */
    public String describe(Target target) {
        if (isEmpty()) {
            return "全部" + target.label;
        }
        List<String> parts = new ArrayList<>();
        if (id != null) {
            parts.add("#" + id);
        }
        if (!isBlank(number)) {
            parts.add((exactNumber ? "号码 = " : "号码含「") + number.trim()
                    + (exactNumber ? "" : "」"));
        }
        if (from != null) {
            parts.add("时间 ≥ " + Times.format(from));
        }
        if (to != null) {
            parts.add("时间 ≤ " + Times.format(to));
        }
        if (type != null) {
            parts.add("类型 = " + target.kindLabel(type));
        }
        if (!isBlank(keyword) && target.hasText()) {
            parts.add("内容含「" + keyword.trim() + "」");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(" 且 ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
