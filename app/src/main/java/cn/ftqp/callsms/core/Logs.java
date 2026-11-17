package cn.ftqp.callsms.core;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** 进程内环形日志：既打 logcat，也能在 App 里直接看（现场没电脑时很有用） */
public final class Logs {

    public static final String TAG = "FTQCallSms";

    private static final int CAPACITY = 300;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);

    private Logs() {
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        append("D", msg);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        append("I", msg);
    }

    public static void w(String msg, Throwable t) {
        Log.w(TAG, msg, t);
        append("W", msg + (t == null ? "" : " | " + t));
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        append("E", msg + (t == null ? "" : " | " + t));
    }

    private static void append(String level, String msg) {
        synchronized (LINES) {
            LINES.addLast(TS.format(new Date()) + " " + level + "/ " + msg);
            while (LINES.size() > CAPACITY) {
                LINES.removeFirst();
            }
        }
    }

    public static String dump() {
        synchronized (LINES) {
            StringBuilder sb = new StringBuilder();
            for (String l : LINES) {
                sb.append(l).append('\n');
            }
            return sb.length() == 0 ? "(暂无日志)" : sb.toString();
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }
}
