package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class UiStyle {
    public static final int BG_AMOLED = 0xFF05060A;
    public static final int SURFACE_CARD = 0xFF0E1118;
    public static final int SURFACE_ELEV = 0xFF161B26;
    public static final int BORDER_SUBTLE = 0xFF232B3D;
    public static final int TEXT_PRIMARY = 0xFFF1F5F9;
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    public static final int TEXT_MUTED = 0xFF64748B;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable roundRect(int fillColor, int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            d.setStroke(strokePx, strokeColor);
        }
        return d;
    }

    public static TextView makePill(Context c, String label, boolean active, int accentColor) {
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(11.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        int ph = dp(c, 11);
        int pv = dp(c, 6);
        tv.setPadding(ph, pv, ph, pv);
        stylePill(c, tv, active, accentColor);
        return tv;
    }

    public static void stylePill(Context c, TextView tv, boolean active, int accentColor) {
        if (active) {
            tv.setTextColor(accentColor);
            tv.setBackground(roundRect(
                    EqStore.withAlpha(accentColor, 42),
                    EqStore.withAlpha(accentColor, 210),
                    dp(c, 1.2f),
                    dp(c, 16)));
        } else {
            tv.setTextColor(TEXT_SECONDARY);
            tv.setBackground(roundRect(
                    SURFACE_ELEV,
                    BORDER_SUBTLE,
                    dp(c, 1),
                    dp(c, 16)));
        }
    }

    public static void tintSeekBar(SeekBar sb, int accentColor) {
        ColorStateList csl = ColorStateList.valueOf(accentColor);
        sb.setProgressTintList(csl);
        sb.setThumbTintList(csl);
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF232B3D));
    }

    @SuppressWarnings("deprecation")
    public static void applyEdgeToEdgeInsets(View root, View topInsetTarget, int baseTopPx,
                                             View bottomInsetTarget, int baseBottomPx) {
        final int topBaseL = topInsetTarget != null ? topInsetTarget.getPaddingLeft() : 0;
        final int topBaseR = topInsetTarget != null ? topInsetTarget.getPaddingRight() : 0;
        final int botBaseL = bottomInsetTarget != null ? bottomInsetTarget.getPaddingLeft() : 0;
        final int botBaseR = bottomInsetTarget != null ? bottomInsetTarget.getPaddingRight() : 0;

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sys = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = sys.top;
                bottom = sys.bottom;
                left = sys.left;
                right = sys.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            if (topInsetTarget != null && topInsetTarget == bottomInsetTarget) {
                topInsetTarget.setPadding(
                        topBaseL + left,
                        baseTopPx + top,
                        topBaseR + right,
                        baseBottomPx + bottom);
            } else {
                if (topInsetTarget != null) {
                    topInsetTarget.setPadding(
                            topBaseL + left,
                            baseTopPx + top,
                            topBaseR + right,
                            topInsetTarget.getPaddingBottom());
                }
                if (bottomInsetTarget != null) {
                    bottomInsetTarget.setPadding(
                            botBaseL + left,
                            bottomInsetTarget.getPaddingTop(),
                            botBaseR + right,
                            baseBottomPx + bottom);
                }
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    public static void hapticTick(View v, Context ctx) {
        if (v == null || ctx == null) return;
        if (EqStore.get(ctx).getSettings().hapticsEnabled) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    public static LinearLayout.LayoutParams rowLp(float weight, int rightMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.rightMargin = rightMarginPx;
        return lp;
    }

    public static LinearLayout.LayoutParams wrapLp(int leftMarginPx, int rightMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = leftMarginPx;
        lp.rightMargin = rightMarginPx;
        return lp;
    }
}
