package cn.ftqp.callsms.core;

/**
 * App 与「运行在 provider 进程里的模块代码」之间的通信协议。
 *
 * 之所以要跨进程：Android 10+ 里 WRITE_SMS / WRITE_CALL_LOG 是受限权限，
 * 普通应用拿不到，AMS 在解析 provider 时就会拒绝（Permission Denial: requires ...）。
 * 而 provider 进程自己调用自己属于「同 uid」，权限检查直接放行，
 * 所以把落库动作放进 provider 进程执行是最稳的一条路。
 */
public final class Bridge {

    /** 有序广播 action，模块在 provider 进程里动态注册接收器 */
    public static final String ACTION = "cn.ftqp.callsms.action.BRIDGE";

    public static final String EXTRA_OP = "op";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_SELECTION = "selection";
    public static final String EXTRA_SELECTION_ARGS = "selectionArgs";
    public static final String EXTRA_SORT = "sort";
    public static final String EXTRA_LIMIT = "limit";
    public static final String EXTRA_VALUES = "values";

    public static final String OP_PING = "ping";
    public static final String OP_QUERY = "query";
    public static final String OP_INSERT = "insert";
    public static final String OP_DELETE = "delete";

    /** 单次回传的最大行数：有序广播结果是 Binder 传输，别把 1MB 撑爆 */
    public static final int MAX_ROWS = 200;

    /** body 太长会撑爆广播，超长直接截断 */
    public static final int MAX_TEXT = 800;

    /** provider 进程侧等待回执的超时 */
    public static final int TIMEOUT_MS = 8000;

    private Bridge() {
    }
}
