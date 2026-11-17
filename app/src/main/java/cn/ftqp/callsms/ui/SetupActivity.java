package cn.ftqp.callsms.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import cn.ftqp.callsms.core.Logs;
import cn.ftqp.callsms.core.SelfCheck;
import cn.ftqp.callsms.core.Shell;
import cn.ftqp.callsms.core.Target;

/**
 * 配置与诊断页：把「为什么不通 / 现在该点哪里」直接摆在用户面前，
 * 而不是让用户自己去猜 scope、root、重启进程。
 */
public final class SetupActivity extends AppCompatActivity {

    private static final String LSPOSED_PKG = "org.lsposed.manager";

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView headline;
    private TextView nextStep;
    private TextView rowMeta;
    private TextView rowSms;
    private TextView rowCall;
    private TextView rowRoot;
    private TextView reportView;
    private LinearLayout body;
    private SelfCheck.Status status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
        runCheck();
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        body = Ui.box(this, true);
        int pad = Ui.dp(this, 14);
        body.setPadding(pad, pad, pad, Ui.dp(this, 28));
        sv.addView(body);

        TextView title = Ui.text(this, "配置与诊断", 20, Ui.TEXT, true);
        body.addView(title);
        TextView tip = Ui.text(this, "按下面提示操作，全部变 ✅ 就能正常读写了", 12, Ui.SUB, false);
        tip.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 12));
        body.addView(tip);

        headline = Ui.text(this, "检测中…", 15, Ui.TEXT, true);
        nextStep = Ui.text(this, "", 12, Ui.SUB, false);
        LinearLayout card = Ui.box(this, true);
        card.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 12)));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.addView(headline);
        nextStep.setPadding(0, Ui.dp(this, 6), 0, 0);
        nextStep.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(nextStep);
        body.addView(card, lpMatchWrap(Ui.dp(this, 10)));

        // 通道明细
        rowMeta = addRow("① 已安装 APK 的模块元数据");
        rowSms = addRow("② 短信 provider 进程");
        rowCall = addRow("③ 通话 provider 进程");
        rowRoot = addRow("④ root（su）");

        body.addView(sectionTitle("操作"));
        body.addView(buttonRow(
                mkBtn("重新检测", Ui.PRIMARY, v -> runCheck()),
                mkBtn("授权 root", Ui.PRIMARY, v -> grantRoot())
        ));
        body.addView(buttonRow(
                mkBtn("打开 LSPosed", Ui.PRIMARY, v -> openLsposed()),
                mkBtn("重启短信/通话服务", Ui.PRIMARY, v -> restartProviders())
        ));
        body.addView(buttonRow(
                mkBtn("手动强制停止", Ui.SUB, v -> manualStopDialog()),
                mkBtn("复制诊断", Ui.SUB, v -> copyReport())
        ));

        body.addView(sectionTitle("诊断详情"));
        reportView = Ui.text(this, "", 11, Ui.TEXT, false);
        reportView.setTextIsSelectable(true);
        reportView.setLineSpacing(Ui.dp(this, 2), 1f);
        reportView.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 10)));
        int p2 = Ui.dp(this, 12);
        reportView.setPadding(p2, p2, p2, p2);
        body.addView(reportView, lpMatchWrap(Ui.dp(this, 10)));

        body.addView(sectionTitle("为什么需要这些步骤"));
        TextView why = Ui.text(this, WHY, 11, Ui.SUB, false);
        why.setLineSpacing(Ui.dp(this, 3), 1f);
        body.addView(why);

        return sv;
    }

    private LinearLayout.LayoutParams lpMatchWrap(int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottomDp;
        return p;
    }

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 13, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        return t;
    }

    private TextView addRow(String name) {
        TextView t = Ui.text(this, "⏳ " + name + "：检测中…", 12, Ui.SUB, false);
        t.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 10)));
        int p = Ui.dp(this, 12);
        t.setPadding(p, p, p, p);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        body.addView(t, lpMatchWrap(Ui.dp(this, 6)));
        return t;
    }

    private TextView mkBtn(String label, int color, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 13, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));
        t.setBackground(Ui.round(0xFFFFFFFF, Ui.dp(this, 10)));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout buttonRow(View a, View b) {
        LinearLayout row = Ui.box(this, false);
        row.addView(a);
        row.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);
        return row;
    }

    // ------------------------------------------------------------------ 检测

    private void runCheck() {
        headline.setText("检测中…（会尝试一次 su，可能需要点授权）");
        nextStep.setText("");
        reportView.setText("");
        setRow(rowMeta, "⏳", "检测中…", Ui.SUB);
        setRow(rowSms, "⏳", "检测中…", Ui.SUB);
        setRow(rowCall, "⏳", "检测中…", Ui.SUB);
        setRow(rowRoot, "⏳", "检测中…", Ui.SUB);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 点「重新检测」时强制重新探一次 su：用户可能在 Magisk 里刚点了允许
                Shell.ensureRoot(4000, true);
                final SelfCheck.Status s = SelfCheck.run(SetupActivity.this);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        apply(s);
                    }
                });
            }
        }, "ftq-selfcheck").start();
    }

    private void apply(SelfCheck.Status s) {
        status = s;
        boolean ok = s.anyUsable();
        headline.setText(SelfCheck.headline(s));
        headline.setTextColor(ok ? 0xFF1B7F3B : Ui.DANGER);
        nextStep.setText(SelfCheck.nextStep(s));

        setRow(rowMeta, s.metaOk ? "✅" : "❌", s.metaOk ? "合法" : "有问题（见下方详情）",
                s.metaOk ? 0xFF1B7F3B : Ui.DANGER);
        setRow(rowSms, s.moduleSms ? "✅" : "❌",
                s.moduleSms ? "模块已应答 " + s.moduleSmsRaw : "无应答",
                s.moduleSms ? 0xFF1B7F3B : Ui.DANGER);
        setRow(rowCall, s.moduleCall ? "✅" : "❌",
                s.moduleCall ? "模块已应答 " + s.moduleCallRaw : "无应答",
                s.moduleCall ? 0xFF1B7F3B : Ui.DANGER);
        String rootLine = s.rootOk ? ("可用：" + s.rootPath) : Shell.firstLine(s.rootReport);
        setRow(rowRoot, s.rootOk ? "✅" : "❌", rootLine, s.rootOk ? 0xFF1B7F3B : Ui.DANGER);

        reportView.setText(SelfCheck.dump(s));
    }

    private void setRow(TextView row, String icon, String text, int color) {
        row.setText(icon + " " + text);
        row.setTextColor(color == Ui.SUB ? Ui.SUB : Ui.TEXT);
    }

    // ------------------------------------------------------------------ 操作

    private void grantRoot() {
        headline.setText("正在请求 root 授权…请看屏幕有没有弹出授权框");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Shell.ensureRoot(15000, true);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            Ui.toast(SetupActivity.this, "root 已通（" + Shell.suPath() + "）");
                        } else {
                            Ui.toast(SetupActivity.this, "root 仍不可用，详情见下方");
                        }
                        runCheck();
                    }
                });
            }
        }, "ftq-su").start();
    }

    private void openLsposed() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(LSPOSED_PKG, LSPOSED_PKG + ".ui.activity.MainActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(i)) {
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(LSPOSED_PKG);
        if (launch != null && tryStart(launch)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("没找到 LSPosed")
                .setMessage("可能原因：\n· LSPosed 管理器用的是隐藏图标/别名\n· 你用的是其它 Xposed 框架\n\n"
                        + "请手动从 LSPosed 通知或设置里打开，然后：启用本模块 → 作用域勾选两个 provider → 重启手机。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void restartProviders() {
        if (status != null && !status.rootOk) {
            Ui.toast(this, "需要 root 才能杀掉系统进程，先点「授权 root」");
            manualStopDialog();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String cmd = "for p in " + Target.SMS.providerPackage + " " + Target.CALL.providerPackage
                        + "; do killall $p; done; echo done";
                final Shell.Out o = Shell.execRoot(cmd, 15000);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(SetupActivity.this, o.ok() ? "已重启，正在重新检测…" : "执行失败：" + Shell.firstLine(o.text));
                        runCheck();
                    }
                });
            }
        }, "ftq-restart").start();
    }

    private void manualStopDialog() {
        final String[] items = {
                "短信存储（" + Target.SMS.providerPackage + "）",
                "联系人/通话存储（" + Target.CALL.providerPackage + "）",
        };
        new AlertDialog.Builder(this)
                .setTitle("手动强制停止")
                .setMessage("打开系统应用信息页后，点「强行停止」。\n这是没有 root 时让新 scope 生效的唯一办法。")
                .setItems(items, (d, which) -> {
                    String pkg = which == 0 ? Target.SMS.providerPackage : Target.CALL.providerPackage;
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + pkg));
                    if (!tryStart(i)) {
                        Ui.toast(SetupActivity.this, "打不开系统设置，请到「设置→应用→显示系统应用」里找");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyReport() {
        StringBuilder sb = new StringBuilder();
        if (status != null) {
            sb.append(SelfCheck.dump(status));
        } else {
            sb.append("(还没检测完)\n");
        }
        sb.append("\n== 运行日志 ==\n").append(Logs.dump());
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ftq-diagnose", sb.toString()));
            Ui.toast(this, "已复制，直接粘给我就行");
        } catch (Throwable t) {
            Ui.toast(this, "复制失败：" + t);
        }
    }

    private static final String WHY =
            "为什么普通 App 写不了：Android 10+ 把 WRITE_SMS / WRITE_CALL_LOG 列为受限权限，"
                    + "而且拒绝发生在系统服务（AMS）解析 provider 的那一刻，App 自己怎么声明都没用。\n\n"
                    + "模块通道为什么能写：注入到 provider 进程里的代码，用的是 provider 自己的 uid，"
                    + "自己访问自己不算跨应用，权限检查直接放行；写入还走 provider 正常路径，"
                    + "会话表（threads）和变更通知都由系统维护。\n\n"
                    + "root 通道为什么能写：uid 0 在权限检查里直接放行，但走的是命令行，"
                    + "值要转义、查询靠文本解析，所以只当兜底。\n\n"
                    + "三个通道任意一个通就能用，模块通道最稳。";
}
