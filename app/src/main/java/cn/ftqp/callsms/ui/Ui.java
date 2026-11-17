package cn.ftqp.callsms.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 纯代码建 View 的小工具，避免引入 material 依赖、也免得为几个控件写一堆 xml */
public final class Ui {

    public static final int BG = 0xFFF4F5F7;
    public static final int CARD = 0xFFFFFFFF;
    public static final int TEXT = 0xFF171A1F;
    public static final int SUB = 0xFF6B7280;
    public static final int PRIMARY = 0xFF2F6FED;
    public static final int DANGER = 0xFFD93B3B;
    public static final int LINE = 0xFFE6E8EB;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static LinearLayout box(Context c, boolean vertical) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }

    public static GradientDrawable round(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable roundStroke(int color, int radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    public static Button button(Context c, String label, int textColor, int bgColor, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(textColor);
        b.setBackground(round(bgColor, dp(c, 10)));
        int padH = dp(c, 12);
        int padV = dp(c, 6);
        b.setPadding(padH, padV, padH, padV);
        b.setMinHeight(dp(c, 36));
        b.setMinimumHeight(dp(c, 36));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    public static EditText field(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF9AA1AC);
        e.setBackground(roundStroke(0xFFFBFBFC, dp(c, 8), LINE, dp(c, 1)));
        int padH = dp(c, 10);
        e.setPadding(padH, dp(c, 8), padH, dp(c, 8));
        return e;
    }

    public static EditText numberField(Context c, String hint, String value) {
        EditText e = field(c, hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpWeight(int w, float weight) {
        return new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static TextView badge(Context c, String s, int color) {
        TextView t = text(c, s, 11, Color.WHITE, false);
        t.setBackground(round(color, dp(c, 6)));
        t.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2));
        return t;
    }

    public static void marginBottom(View v, Context c, int dpValue) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).bottomMargin = dp(c, dpValue);
        }
    }

    public static void marginTop(View v, Context c, int dpValue) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = dp(c, dpValue);
        }
    }

    public static void centerGravity(TextView t) {
        t.setGravity(Gravity.CENTER);
    }
}
