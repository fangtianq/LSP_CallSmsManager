package io.github.fangtianq.callsms.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.EnumMap;
import java.util.Map;

import io.github.fangtianq.callsms.core.DirectEngine;
import io.github.fangtianq.callsms.core.Json;
import io.github.fangtianq.callsms.core.Logs;
import io.github.fangtianq.callsms.core.ModuleEngine;
import io.github.fangtianq.callsms.core.Shell;
import io.github.fangtianq.callsms.core.Target;

public final class MainActivity extends AppCompatActivity {

    private FrameLayout container;
    private TextView tabCall;
    private TextView tabSms;
    private TextView banner;
    private final Map<Target, RecordsPanel> cache = new EnumMap<>(Target.class);
    private Target current = Target.CALL;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildRoot());
        show(Target.CALL);
        checkChannels();
    }

    private View buildRoot() {
        LinearLayout root = Ui.box(this, true);
        root.setBackgroundColor(Ui.BG);

        // 头部
        LinearLayout header = Ui.box(this, false);
        header.setBackgroundColor(Ui.CARD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12));

        LinearLayout titles = Ui.box(this, true);
        TextView title = Ui.text(this, "通话短信工具箱", 20, Ui.TEXT, true);
        TextView sub = Ui.text(this, "新增 / 删除通话记录与短信 · LSPosed 模块", 11, Ui.SUB, false);
        titles.addView(title);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(Ui.button(this, "配置", Ui.PRIMARY, 0x0F000000, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSetup();
            }
        }));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = Ui.dp(this, 6);
        header.addView(Ui.button(this, "日志", Ui.SUB, 0x0F000000, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showText("运行日志", Logs.dump());
            }
        }), gap);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 未配置时的提示条（点一下进配置页）
        banner = Ui.text(this, "⚠ 现在没有任何可用通道，点这里完成配置 →", 12, 0xFF8A5A00, true);
        banner.setBackgroundColor(0xFFFFF3D6);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSetup();
            }
        });
        root.addView(banner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 页签
        LinearLayout tabs = Ui.box(this, false);
        tabs.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        tabCall = makeTab("通话记录", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(Target.CALL);
            }
        });
        tabSms = makeTab("短信记录", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(Target.SMS);
            }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = Ui.dp(this, 8);
        tabs.addView(tabCall, half);
        tabs.addView(tabSms, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private TextView makeTab(String label, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 14, Ui.SUB, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setOnClickListener(l);
        return t;
    }

    private void show(Target target) {
        current = target;
        container.removeAllViews();
        RecordsPanel panel = cache.get(target);
        if (panel == null) {
            panel = new RecordsPanel(this, target);
            cache.put(target, panel);
        }
        container.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        styleTab(tabCall, target == Target.CALL);
        styleTab(tabSms, target == Target.SMS);
    }

    private void styleTab(TextView t, boolean active) {
        t.setTextColor(active ? Color.WHITE : Ui.SUB);
        t.setBackground(Ui.round(active ? Ui.PRIMARY : 0xFFEDEFF3, Ui.dp(this, 10)));
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
    }

    /**
     * 轻量通道探测：只为了让顶部提示条判断「现在能不能用」。
     * 模块没注册接收者时有序广播会立刻返回，所以正常情况下是一瞬间的事。
     */
    private void checkChannels() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean moduleOk = Json.isOk(ModuleEngine.ping(MainActivity.this, Target.SMS))
                        && Json.isOk(ModuleEngine.ping(MainActivity.this, Target.CALL));
                final boolean rootOk = Shell.ensureRoot();
                final boolean directOk = new DirectEngine().probe(MainActivity.this) == null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean usable = moduleOk || rootOk || directOk;
                        banner.setVisibility(usable ? View.GONE : View.VISIBLE);
                        if (!usable && !prefs().getBoolean("setup_shown", false)) {
                            prefs().edit().putBoolean("setup_shown", true).apply();
                            openSetup();
                        }
                    }
                });
            }
        }, "ftq-chan").start();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("ftq", Context.MODE_PRIVATE);
    }

    private void showText(String title, final String body) {
        TextView tv = Ui.text(this, body, 12, Ui.TEXT, false);
        tv.setTextIsSelectable(true);
        tv.setLineSpacing(Ui.dp(this, 3), 1f);
        ScrollView sv = new ScrollView(this);
        int pad = Ui.dp(this, 16);
        sv.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("ftq-log", body));
                    Ui.toast(MainActivity.this, "已复制");
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            // 首屏由 onAttachedToWindow 负责刷新，这里不要重复打一遍
            firstResume = false;
            return;
        }
        RecordsPanel p = cache.get(current);
        if (p != null) {
            // 从后台/配置页回来刷新一次：刚配置完或别的 App 改过数据都要反映出来
            p.refresh();
        }
        checkChannels();
    }
}
