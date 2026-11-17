package cn.ftqp.callsms.core;

/**
 * 两类目标数据：通话记录 / 短信记录。
 *
 * 列名以 AOSP 为准，小米/HyperOS 在这两张表上基本保持 AOSP 结构（会多几列，但我们不去碰）。
 */
public enum Target {

    CALL(
            "通话记录",
            "com.android.providers.contacts",
            "com.android.providers.contacts.CallLogProvider",
            "content://call_log/calls",
            "number",
            "date",
            "type",
            null
    ),

    SMS(
            "短信记录",
            "com.android.providers.telephony",
            "com.android.providers.telephony.SmsProvider",
            "content://sms",
            "address",
            "date",
            "type",
            "body"
    );

    public final String label;
    /** provider 所在包名，也是模块 scope 与广播 targetPackage */
    public final String providerPackage;
    /** provider 实现类（仅用于日志/诊断） */
    public final String providerClass;
    public final String uri;
    /** 号码列 */
    public final String numberColumn;
    public final String dateColumn;
    public final String typeColumn;
    /** 正文列，通话记录没有 */
    public final String textColumn;

    Target(String label, String providerPackage, String providerClass, String uri,
           String numberColumn, String dateColumn, String typeColumn, String textColumn) {
        this.label = label;
        this.providerPackage = providerPackage;
        this.providerClass = providerClass;
        this.uri = uri;
        this.numberColumn = numberColumn;
        this.dateColumn = dateColumn;
        this.typeColumn = typeColumn;
        this.textColumn = textColumn;
    }

    public boolean hasText() {
        return textColumn != null;
    }

    /** 可选类型：value 落库，label 给人看 */
    public static final class Kind {
        public final int value;
        public final String label;

        Kind(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public Kind[] kinds() {
        if (this == CALL) {
            return new Kind[]{
                    new Kind(1, "来电"),
                    new Kind(2, "去电"),
                    new Kind(3, "未接"),
                    new Kind(5, "拒接"),
                    new Kind(6, "已拦截"),
                    new Kind(4, "语音信箱"),
            };
        }
        return new Kind[]{
                new Kind(1, "收件（来信）"),
                new Kind(2, "发件（去信）"),
                new Kind(3, "草稿"),
                new Kind(4, "发件箱"),
                new Kind(5, "发送失败"),
                new Kind(6, "待发送"),
        };
    }

    public String kindLabel(int value) {
        for (Kind k : kinds()) {
            if (k.value == value) {
                return k.label;
            }
        }
        return "类型" + value;
    }

    public static Target of(String name) {
        for (Target t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return CALL;
    }

    /** 该包是否为本模块的 scope */
    public static boolean isScopePackage(String pkg) {
        for (Target t : values()) {
            if (t.providerPackage.equals(pkg)) {
                return true;
            }
        }
        return false;
    }
}
