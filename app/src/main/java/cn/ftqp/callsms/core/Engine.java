package cn.ftqp.callsms.core;

import android.content.ContentValues;
import android.content.Context;

import java.util.List;
import java.util.Map;

/** 一种"把记录写进系统数据库"的执行通道 */
public interface Engine {

    /** 稳定 id，用于日志和 sticky 记录 */
    String id();

    /** 给人看的名字 */
    String label();

    /**
     * 可用性探测，必须便宜且不抛异常。
     * @return null 表示可用，否则返回不可用原因
     */
    String probe(Context ctx);

    List<Map<String, String>> query(Context ctx, Target target, Filter filter, int limit) throws Exception;

    long insert(Context ctx, Target target, ContentValues values) throws Exception;

    int delete(Context ctx, Target target, Filter filter) throws Exception;
}
