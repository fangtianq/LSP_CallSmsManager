package cn.ftqp.callsms.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 时间格式化 / 解析，统一用本地时区、精确到分钟展示。 */
public final class Times {

    public static final String PATTERN = "yyyy-MM-dd HH:mm";

    private static final ThreadLocal<SimpleDateFormat> FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat f = new SimpleDateFormat(PATTERN, Locale.CHINA);
            f.setLenient(false);
            return f;
        }
    };

    private Times() {
    }

    public static String format(long millis) {
        return FMT.get().format(new Date(millis));
    }

    public static String format(Long millis) {
        return millis == null ? "" : format(millis.longValue());
    }

    /** 解析 "yyyy-MM-dd HH:mm"，失败返回 null */
    public static Long parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return FMT.get().parse(text.trim()).getTime();
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析时间且允许「只有日期」 */
    public static Long parseLoose(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        Long v = parse(s);
        if (v != null) {
            return v;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            f.setLenient(false);
            return f.parse(s).getTime();
        } catch (Exception e) {
            return null;
        }
    }

    public static String nowText() {
        return format(System.currentTimeMillis());
    }
}
