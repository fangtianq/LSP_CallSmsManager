package io.github.fangtianq.callsms.ui;

import androidx.appcompat.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.github.fangtianq.callsms.core.Filter;
import io.github.fangtianq.callsms.core.Logs;
import io.github.fangtianq.callsms.core.OpResult;
import io.github.fangtianq.callsms.core.Router;
import io.github.fangtianq.callsms.core.Target;
import io.github.fangtianq.callsms.core.Times;

/** 单个目标（通话记录 / 短信）的列表 + 新增 + 按条件删除面板 */
public final class RecordsPanel extends LinearLayout {

    private final Target target;
    private final ListView list;
    private final TextView statusView;
    private final TextView emptyView;
    private final RecordAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ftq-io");
            t.setDaemon(true);
            return t;
        }
    });

    public RecordsPanel(Context c, Target target) {
        super(c);
        this.target = target;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // ---- 工具栏
        LinearLayout bar = Ui.box(c, false);
        bar.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 6));
        bar.setBackgroundColor(Ui.CARD);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        bar.addView(toolButton(c, "刷新", Ui.PRIMARY, new OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        }));
        bar.addView(toolButton(c, "新增", Ui.PRIMARY, new OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        }));
        bar.addView(toolButton(c, "按条件删除", Ui.DANGER, new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteDialog();
            }
        }));
        bar.addView(toolButton(c, "清空", Ui.DANGER, new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearAll();
            }
        }));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = Ui.text(c, "尚未加载", 12, Ui.SUB, false);
        statusView.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        statusView.setBackgroundColor(0xFFFFFFFF);
        addView(statusView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(Ui.divider(c));

        // ---- 列表
        FrameLayout wrap = new FrameLayout(c);
        list = new ListView(c);
        list.setDividerHeight(0);
        list.setBackgroundColor(Ui.BG);
        adapter = new RecordAdapter(c, target);
        list.setAdapter(adapter);
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDeleteOne(position);
                return true;
            }
        });
        wrap.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = Ui.text(c, "暂无记录\n（点「新增」造一条，或先跑一次「自检」）", 13, Ui.SUB, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(GONE);
        wrap.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        addView(wrap, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View toolButton(Context c, String label, int color, OnClickListener l) {
        View b = Ui.button(c, label, color, 0x0F000000, l);
        LayoutParams p = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.rightMargin = Ui.dp(c, 6);
        b.setLayoutParams(p);
        return b;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    // ---------------------------------------------------------------- 列表

    public void refresh() {
        statusView.setTextColor(Ui.SUB);
        statusView.setText("加载中…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final OpResult r = Router.query(getContext(), target, new Filter(), 200);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        applyQuery(r);
                    }
                });
            }
        });
    }

    private void applyQuery(OpResult r) {
        if (!r.ok) {
            adapter.setRows(new ArrayList<Map<String, String>>());
            statusView.setTextColor(Ui.DANGER);
            statusView.setText("读取失败：" + r.error);
            emptyView.setText("读取失败\n" + r.error);
            emptyView.setVisibility(VISIBLE);
            return;
        }
        adapter.setRows(r.rows);
        emptyView.setText("暂无记录\n（点「新增」造一条，或先跑一次「自检」）");
        emptyView.setVisibility(r.rows.isEmpty() ? VISIBLE : GONE);
        statusView.setTextColor(Ui.SUB);
        String note = r.fallbackNote == null ? "" : "（备用通道：之前失败 " + r.fallbackNote + "）";
        statusView.setText("共 " + r.rows.size() + " 条" + (r.rows.size() >= 200 ? "（已达上限 200）" : "")
                + " · 通道 " + engineLabel(r.engine) + note);
    }

    private static String engineLabel(String id) {
        if ("module".equals(id)) {
            return "LSPosed 模块";
        }
        if ("root".equals(id)) {
            return "root";
        }
        if ("direct".equals(id)) {
            return "直连";
        }
        return String.valueOf(id);
    }

    // ---------------------------------------------------------------- 新增

    private void showAddDialog() {
        final Context c = getContext();
        LinearLayout form = Ui.box(c, true);
        int pad = Ui.dp(c, 16);
        form.setPadding(pad, Ui.dp(c, 6), pad, 0);

        final EditText etNumber = Ui.field(c, "号码（如 10086）", "");
        final EditText etTime = Ui.field(c, "时间 yyyy-MM-dd HH:mm", Times.nowText());
        final Spinner spKind = kindSpinner(c, false);
        final EditText etExtra = target.hasText()
                ? Ui.field(c, "短信正文", "")
                : Ui.numberField(c, "通话时长（秒，可留空）", "");
        if (target.hasText()) {
            etExtra.setMinLines(3);
            etExtra.setGravity(Gravity.TOP);
            etExtra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }

        form.addView(labelRow(c, "号码"));
        form.addView(etNumber);
        form.addView(labelRow(c, "时间"));
        form.addView(etTime);
        form.addView(labelRow(c, "类型"));
        form.addView(spKind);
        form.addView(labelRow(c, target.hasText() ? "正文" : "时长"));
        form.addView(etExtra);

        ScrollView sv = new ScrollView(c);
        sv.addView(form);

        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle("新增" + target.label)
                .setView(sv)
                .setNegativeButton("取消", null)
                .setPositiveButton("写入", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String number = etNumber.getText().toString().trim();
                if (TextUtils.isEmpty(number)) {
                    Ui.toast(c, "号码不能为空");
                    return;
                }
                Long ms = Times.parseLoose(etTime.getText().toString());
                if (ms == null) {
                    Ui.toast(c, "时间格式不对，应为 yyyy-MM-dd HH:mm");
                    return;
                }
                int kind = kindValue(spKind, 1);

                ContentValues full = new ContentValues();
                full.put(target.numberColumn, number);
                full.put(target.dateColumn, ms);
                full.put(target.typeColumn, kind);

                ContentValues minimal = new ContentValues();
                minimal.put(target.numberColumn, number);
                minimal.put(target.dateColumn, ms);
                minimal.put(target.typeColumn, kind);

                if (target.hasText()) {
                    String body = etExtra.getText().toString();
                    full.put(target.textColumn, body);
                    minimal.put(target.textColumn, body);
                    full.put("read", 1);
                } else {
                    String dur = etExtra.getText().toString().trim();
                    int duration = 0;
                    if (!TextUtils.isEmpty(dur)) {
                        try {
                            duration = Integer.parseInt(dur);
                        } catch (Exception ignored) {
                            duration = 0;
                        }
                    }
                    full.put("duration", duration);
                    minimal.put("duration", duration);
                    // 可选列：个别 ROM 没有 presentation / new / is_read，失败时会自动退到 minimal
                    full.put("presentation", 1);
                    full.put("new", 1);
                    full.put("is_read", 1);
                }

                dlg.dismiss();
                doInsert(full, minimal);
            }
        });
    }

    private void doInsert(final ContentValues full, final ContentValues minimal) {
        statusView.setTextColor(Ui.SUB);
        statusView.setText("写入中…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                OpResult r = Router.insert(getContext(), target, full);
                if (!r.ok && r.error != null && r.error.toLowerCase().contains("column")) {
                    // 该 ROM 的 calls 表可能少了 presentation / new / is_read，去掉可选列再试一次
                    Logs.w("insert full failed (schema?), retry minimal: " + r.error, null);
                    r = Router.insert(getContext(), target, minimal);
                }
                final OpResult fr = r;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fr.ok) {
                            Ui.toast(getContext(), "已写入（通道 " + engineLabel(fr.engine) + "）");
                        } else {
                            statusView.setTextColor(Ui.DANGER);
                            statusView.setText("写入失败：" + fr.error);
                            Ui.toast(getContext(), "写入失败，详见状态栏");
                        }
                        refresh();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- 删除

    private void showDeleteDialog() {
        final Context c = getContext();
        LinearLayout form = Ui.box(c, true);
        int pad = Ui.dp(c, 16);
        form.setPadding(pad, Ui.dp(c, 6), pad, 0);

        final EditText etNumber = Ui.field(c, "号码（模糊匹配，可留空）", "");
        final EditText etFrom = Ui.field(c, "起始时间 yyyy-MM-dd HH:mm（可留空）", "");
        final EditText etTo = Ui.field(c, "结束时间 yyyy-MM-dd HH:mm（可留空）", "");
        final Spinner spKind = kindSpinner(c, true);
        final EditText etKey = target.hasText() ? Ui.field(c, "内容关键字（可留空）", "") : null;

        form.addView(labelRow(c, "号码"));
        form.addView(etNumber);
        form.addView(labelRow(c, "起始时间"));
        form.addView(etFrom);
        form.addView(labelRow(c, "结束时间"));
        form.addView(etTo);
        form.addView(labelRow(c, "类型"));
        form.addView(spKind);
        if (etKey != null) {
            form.addView(labelRow(c, "内容关键字"));
            form.addView(etKey);
        }

        ScrollView sv = new ScrollView(c);
        sv.addView(form);
        new AlertDialog.Builder(c)
                .setTitle("按条件删除" + target.label)
                .setView(sv)
                .setNegativeButton("取消", null)
                .setPositiveButton("查找并删除", (d, which) -> {
                    Filter f = new Filter();
                    f.number = etNumber.getText().toString();
                    f.from = Times.parseLoose(etFrom.getText().toString());
                    f.to = Times.parseLoose(etTo.getText().toString());
                    f.type = kindValueOrNull(spKind);
                    if (etKey != null) {
                        f.keyword = etKey.getText().toString();
                    }
                    if (f.isEmpty()) {
                        Ui.toast(c, "至少填一个条件；要清空请用「清空」按钮");
                        return;
                    }
                    confirmAndDelete(f);
                })
                .show();
    }

    private void confirmDeleteOne(int position) {
        Map<String, String> row = adapter.getItem(position);
        if (row == null) {
            return;
        }
        // 优先按 _id 精确删；拿不到 _id（有的 ROM / root 通道列名不同）就退回「号码+时间+类型」组合条件，
        // 不要因为一个字段缺失就把用户堵死在这一步
        Long id = rowId(row);
        final Filter f = id != null ? Filter.byId(id) : compositeFilter(row);
        if (f == null) {
            Logs.w("delete_one: 无法构造条件，row keys=" + row.keySet(), null);
            Ui.toast(getContext(), "这条记录缺 _id 又没有号码/时间，无法定位，建议用「按条件删除」");
            return;
        }
        if (id == null) {
            Logs.i("delete_one: 无 _id，改用组合条件 " + f.describe(target) + "；row keys=" + row.keySet());
        }
        new AlertDialog.Builder(getContext())
                .setTitle("删除这一条？")
                .setMessage(describeRow(row) + "\n\n删除条件：" + f.describe(target))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> doDelete(f))
                .show();
    }

    /** 宽松取 _id：列名大小写不敏感，值必须能转成数字 */
    private static Long rowId(Map<String, String> row) {
        for (Map.Entry<String, String> e : row.entrySet()) {
            if ("_id".equalsIgnoreCase(String.valueOf(e.getKey()).trim())) {
                try {
                    return Long.parseLong(String.valueOf(e.getValue()).trim());
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 没有 _id 时的兜底条件：号码精确 + 时间精确（>= 且 <= 同一毫秒）+ 类型。
     * 号码必须用精确匹配，否则 LIKE 会把同一个号码的历史记录一起删掉。
     */
    private Filter compositeFilter(Map<String, String> row) {
        Filter f = new Filter();
        String num = row.get(target.numberColumn);
        if (num != null && !num.trim().isEmpty()) {
            f.number = num.trim();
            f.exactNumber = true;
        }
        String date = row.get(target.dateColumn);
        if (date != null) {
            try {
                long ms = Long.parseLong(date.trim());
                f.from = ms;
                f.to = ms;
            } catch (Exception ignored) {
            }
        }
        String type = row.get(target.typeColumn);
        if (type != null) {
            try {
                f.type = Integer.parseInt(type.trim());
            } catch (Exception ignored) {
            }
        }
        if (target.hasText()) {
            String body = row.get(target.textColumn);
            if (body != null) {
                f.keyword = body.trim().isEmpty() ? null : body.trim();
            }
        }
        return f.isEmpty() ? null : f;
    }

    private void confirmAndDelete(final Filter f) {
        final Context c = getContext();
        statusView.setTextColor(Ui.SUB);
        statusView.setText("统计匹配条数…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final OpResult q = Router.query(c, target, f, 200);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!q.ok) {
                            statusView.setTextColor(Ui.DANGER);
                            statusView.setText("统计失败：" + q.error);
                            return;
                        }
                        int n = q.rows.size();
                        String more = n >= 200 ? "（至少 200 条，可能更多）" : "";
                        new AlertDialog.Builder(c)
                                .setTitle("确认删除")
                                .setMessage("条件：" + f.describe(target) + "\n\n匹配 " + n + " 条" + more
                                        + "\n\n删除后不可恢复，且不会出现在系统回收站。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("确认删除", (d, w) -> doDelete(f))
                                .show();
                    }
                });
            }
        });
    }

    private void confirmClearAll() {
        final Context c = getContext();
        new AlertDialog.Builder(c)
                .setTitle("清空全部" + target.label + "？")
                .setMessage("这会删除系统数据库里所有" + target.label + "，无法撤销。\n\n建议先用「按条件删除」缩小范围。")
                .setNegativeButton("取消", null)
                .setPositiveButton("我确定，清空", (d, w) -> doDelete(new Filter()))
                .show();
    }

    private void doDelete(final Filter f) {
        statusView.setTextColor(Ui.SUB);
        statusView.setText("删除中…");
        io.execute(new Runnable() {
            @Override
            public void run() {
                final OpResult r = Router.delete(getContext(), target, f);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (r.ok) {
                            statusView.setTextColor(Ui.SUB);
                            statusView.setText("已删除 " + r.count + " 条 · 通道 " + engineLabel(r.engine));
                        } else {
                            statusView.setTextColor(Ui.DANGER);
                            statusView.setText("删除失败：" + r.error);
                        }
                        refresh();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- 控件小件

    private TextView labelRow(Context c, String s) {
        TextView t = Ui.text(c, s, 12, Ui.SUB, false);
        t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
        return t;
    }

    private Spinner kindSpinner(Context c, boolean withUnlimited) {
        Spinner sp = new Spinner(c);
        List<String> labels = new ArrayList<>();
        if (withUnlimited) {
            labels.add("不限");
        }
        for (Target.Kind k : target.kinds()) {
            labels.add(k.label);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, labels);
        sp.setAdapter(a);
        sp.setBackground(Ui.roundStroke(0xFFFBFBFC, Ui.dp(c, 8), Ui.LINE, Ui.dp(c, 1)));
        return sp;
    }

    private int kindValue(Spinner sp, int fallback) {
        int idx = sp.getSelectedItemPosition();
        Target.Kind[] kinds = target.kinds();
        if (idx >= 0 && idx < kinds.length) {
            return kinds[idx].value;
        }
        return fallback;
    }

    private Integer kindValueOrNull(Spinner sp) {
        int idx = sp.getSelectedItemPosition();
        if (idx <= 0) {
            return null;
        }
        Target.Kind[] kinds = target.kinds();
        int i = idx - 1;
        return (i >= 0 && i < kinds.length) ? kinds[i].value : null;
    }

    private String describeRow(Map<String, String> row) {
        String num = row.get(target.numberColumn);
        String date = row.get(target.dateColumn);
        String type = row.get(target.typeColumn);
        StringBuilder sb = new StringBuilder();
        sb.append("号码：").append(num == null ? "(空)" : num).append('\n');
        sb.append("时间：").append(formatDate(date)).append('\n');
        try {
            sb.append("类型：").append(target.kindLabel(Integer.parseInt(String.valueOf(type))));
        } catch (Exception ignored) {
            sb.append("类型：").append(type);
        }
        if (target.hasText()) {
            String body = row.get(target.textColumn);
            sb.append("\n正文：").append(body == null ? "" : body);
        } else {
            String d = row.get("duration");
            sb.append("\n时长：").append(d == null ? "0" : d).append(" 秒");
        }
        return sb.toString();
    }

    static String formatDate(String millis) {
        if (millis == null) {
            return "-";
        }
        try {
            return Times.format(Long.parseLong(millis.trim()));
        } catch (Exception e) {
            return millis;
        }
    }

    // ---------------------------------------------------------------- 适配器

    private static final class RecordAdapter extends BaseAdapter {

        private final Context ctx;
        private final Target target;
        private List<Map<String, String>> rows = new ArrayList<>();

        RecordAdapter(Context ctx, Target target) {
            this.ctx = ctx;
            this.target = target;
        }

        void setRows(List<Map<String, String>> data) {
            rows = data == null ? new ArrayList<Map<String, String>>() : data;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Map<String, String> getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout item = Ui.box(ctx, true);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackgroundColor(Ui.CARD);
            int padH = Ui.dp(ctx, 14);
            item.setPadding(padH, Ui.dp(ctx, 10), padH, Ui.dp(ctx, 10));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.bottomMargin = Ui.dp(ctx, 1);
            item.setLayoutParams(itemLp);

            Map<String, String> r = rows.get(position);

            LinearLayout head = Ui.box(ctx, false);
            head.setGravity(Gravity.CENTER_VERTICAL);
            String num = r.get(target.numberColumn);
            TextView title = Ui.text(ctx, num == null || num.isEmpty() ? "(未知号码)" : num, 16, Ui.TEXT, true);
            head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            int type = -1;
            try {
                type = Integer.parseInt(String.valueOf(r.get(target.typeColumn)));
            } catch (Exception ignored) {
            }
            if (type > 0) {
                int color = kindColor(type);
                head.addView(Ui.badge(ctx, target.kindLabel(type), color));
            }
            item.addView(head);

            String second = formatDate(r.get(target.dateColumn));
            if (target.hasText()) {
                String body = r.get(target.textColumn);
                if (body != null && !body.isEmpty()) {
                    second = second + " · " + oneLine(body, 60);
                }
            } else {
                String dur = r.get("duration");
                second = second + " · 通话 " + (dur == null ? "0" : dur) + " 秒";
            }
            TextView sub = Ui.text(ctx, second, 12, Ui.SUB, false);
            sub.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            item.addView(sub);

            return item;
        }

        private static int kindColor(int type) {
            switch (type) {
                case 1:
                    return 0xFF2F9E44; // 来电 / 收件
                case 2:
                    return 0xFF2F6FED; // 去电 / 发件
                case 3:
                    return 0xFFD93B3B; // 未接 / 草稿
                case 5:
                    return 0xFFB45309;
                default:
                    return 0xFF6B7280;
            }
        }

        private static String oneLine(String s, int max) {
            String t = s.replace('\n', ' ').replace('\r', ' ').trim();
            return t.length() > max ? t.substring(0, max) + "…" : t;
        }
    }
}
