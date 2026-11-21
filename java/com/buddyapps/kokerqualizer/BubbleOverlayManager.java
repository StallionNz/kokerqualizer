package com.buddyapps.kokerqualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class BubbleOverlayManager {
    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BubbleOrbView orbView;
    private WindowManager.LayoutParams orbParams;
    private ValueAnimator orbSnapAnim;

    private View dismissZoneView;
    private WindowManager.LayoutParams dismissParams;

    private LinearLayout popupCard;
    private WindowManager.LayoutParams popupParams;

    private boolean shown = false;
    private boolean expanded = false;
    private boolean userDraggingSlider = false;
    private long lastCollapseMs = 0;

    private TextView popBrandTv, popPresetTv, popNowPlayingTv, popPlayBtn;
    private TextView popPowerBtn, popAbBtn, popHwyBtn;
    private TextView popBassLbl, popMbcLbl, popPreampLbl;
    private SeekBar popBassSeek, popMbcSeek, popPreampSeek;
    private LinearLayout popChipRow;

    private final Runnable nowPlayingTick = new Runnable() {
        @Override
        public void run() {
            if (expanded && popupCard != null) {
                refreshNowPlaying();
                handler.postDelayed(this, 1800);
            }
        }
    };

    public BubbleOverlayManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShown() { return shown; }

    public static boolean canDraw(Context ctx) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else handler.post(r);
    }

    public void syncVisibility() {
        runOnMain(() -> {
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            if (s.bubbleEnabled && canDraw(ctx)) {
                if (!shown) show();
                else refreshUi();
            } else {
                if (shown) hide();
            }
        });
    }

    public void onScreenConfigChanged() {
        runOnMain(() -> {
            if (!shown || orbView == null || orbParams == null || wm == null) return;
            if (orbSnapAnim != null) { orbSnapAnim.cancel(); orbSnapAnim = null; }
            if (expanded) collapsePopup();
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            int orbSize = UiStyle.dp(ctx, 74);
            orbParams.x = s.bubbleRightEdge ? (dm.widthPixels - orbSize + UiStyle.dp(ctx, 2)) : -UiStyle.dp(ctx, 2);
            orbParams.y = Math.max(UiStyle.dp(ctx, 48),
                    Math.min(dm.heightPixels - orbSize - UiStyle.dp(ctx, 48), orbParams.y));
            try { wm.updateViewLayout(orbView, orbParams); } catch (Throwable ignored) {}
        });
    }

    public void show() {
        runOnMain(() -> {
            if (shown || wm == null || !canDraw(ctx)) return;
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            int orbSize = UiStyle.dp(ctx, 74);

            orbView = new BubbleOrbView(ctx);
            orbParams = new WindowManager.LayoutParams(
                    orbSize, orbSize,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            orbParams.gravity = Gravity.TOP | Gravity.START;
            orbParams.x = s.bubbleRightEdge ? (dm.widthPixels - orbSize + UiStyle.dp(ctx, 2)) : -UiStyle.dp(ctx, 2);
            orbParams.y = Math.max(UiStyle.dp(ctx, 70),
                    Math.min(dm.heightPixels - orbSize - UiStyle.dp(ctx, 80), s.bubbleY));

            attachOrbTouch(orbView, orbSize);
            try {
                wm.addView(orbView, orbParams);
                shown = true;
                refreshUi();
            } catch (Throwable ignored) {
                shown = false;
            }
        });
    }

    public void hide() {
        runOnMain(() -> {
            if (orbSnapAnim != null) { orbSnapAnim.cancel(); orbSnapAnim = null; }
            handler.removeCallbacksAndMessages(null);
            collapsePopup();
            hideDismissZone();
            if (shown && orbView != null && wm != null) {
                try { wm.removeView(orbView); } catch (Throwable ignored) {}
            }
            orbView = null;
            shown = false;
        });
    }

    public void refreshUi() {
        runOnMain(() -> {
            if (!shown) return;
            EqStore store = EqStore.get(ctx);
            EqStore.Settings s = store.getSettings();
            EqStore.EqPreset p = store.getActivePreset();
            int accent = p != null ? p.accentColor : 0xFF39FF14;
            boolean active = s.eqEnabled && !EqEngine.get(ctx).isAbFlatCompare();

            if (orbView != null) {
                orbView.updateVisual(accent, active, expanded);
            }
            if (expanded && popupCard != null && !userDraggingSlider) {
                populatePopupContent();
            }
        });
    }

    private void attachOrbTouch(View v, int orbSize) {
        final float[] downRawX = new float[1];
        final float[] downRawY = new float[1];
        final int[] startX = new int[1];
        final int[] startY = new int[1];
        final boolean[] moved = new boolean[1];
        final int slop = UiStyle.dp(ctx, 8);

        v.setOnTouchListener((view, e) -> {
            if (orbParams == null || wm == null) return false;
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (orbSnapAnim != null) { orbSnapAnim.cancel(); orbSnapAnim = null; }
                downRawX[0] = e.getRawX();
                downRawY[0] = e.getRawY();
                startX[0] = orbParams.x;
                startY[0] = orbParams.y;
                moved[0] = false;
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - downRawX[0];
                float dy = e.getRawY() - downRawY[0];
                if (!moved[0] && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    moved[0] = true;
                    if (expanded) collapsePopup();
                    showDismissZone();
                }
                if (moved[0]) {
                    orbParams.x = Math.round(startX[0] + dx);
                    orbParams.y = Math.max(UiStyle.dp(ctx, 36),
                            Math.min(dm.heightPixels - orbSize - UiStyle.dp(ctx, 24), Math.round(startY[0] + dy)));
                    try { wm.updateViewLayout(orbView, orbParams); } catch (Throwable ignored) {}
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                hideDismissZone();
                if (!moved[0]) {
                    UiStyle.hapticTick(view, ctx);
                    togglePopup();
                } else {
                    boolean inDismissZone = (orbParams.y > dm.heightPixels - UiStyle.dp(ctx, 145))
                            && Math.abs((orbParams.x + orbSize * 0.5f) - dm.widthPixels * 0.5f) < UiStyle.dp(ctx, 85);
                    if (inDismissZone) {
                        UiStyle.hapticTick(view, ctx);
                        EqStore.get(ctx).getSettings().bubbleEnabled = false;
                        EqStore.get(ctx).save();
                        hide();
                        Intent b = new Intent(EqService.ACTION_STATE_BROADCAST);
                        b.setPackage(ctx.getPackageName());
                        ctx.sendBroadcast(b);
                        return true;
                    }
                    boolean snapRight = (orbParams.x + orbSize * 0.5f) >= dm.widthPixels * 0.5f;
                    int targetX = snapRight ? (dm.widthPixels - orbSize + UiStyle.dp(ctx, 2)) : -UiStyle.dp(ctx, 2);
                    animateOrbX(orbParams.x, targetX);
                    EqStore.Settings s = EqStore.get(ctx).getSettings();
                    s.bubbleRightEdge = snapRight;
                    s.bubbleY = orbParams.y;
                    EqStore.get(ctx).save();
                }
                return true;
            }
            return false;
        });
    }

    private void animateOrbX(int fromX, int toX) {
        if (orbSnapAnim != null) orbSnapAnim.cancel();
        orbSnapAnim = ValueAnimator.ofInt(fromX, toX);
        orbSnapAnim.setDuration(190);
        orbSnapAnim.setInterpolator(new DecelerateInterpolator(2.0f));
        orbSnapAnim.addUpdateListener(a -> {
            if (orbView == null || orbParams == null || !shown) return;
            orbParams.x = (int) a.getAnimatedValue();
            try { wm.updateViewLayout(orbView, orbParams); } catch (Throwable ignored) {}
        });
        orbSnapAnim.start();
    }

    private void showDismissZone() {
        if (dismissZoneView != null || wm == null) return;
        TextView tv = new TextView(ctx);
        tv.setText("✕  Drop to Hide Bubble");
        tv.setTextColor(0xFFFF5252);
        tv.setTextSize(13f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(UiStyle.dp(ctx, 18), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 18), UiStyle.dp(ctx, 10));
        tv.setBackground(UiStyle.roundRect(0xEE180A10, 0xFFFF5252, UiStyle.dp(ctx, 1.5f), UiStyle.dp(ctx, 22)));
        dismissZoneView = tv;

        dismissParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        dismissParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dismissParams.y = UiStyle.dp(ctx, 56);
        try { wm.addView(dismissZoneView, dismissParams); } catch (Throwable ignored) {}
    }

    private void hideDismissZone() {
        if (dismissZoneView != null && wm != null) {
            try { wm.removeView(dismissZoneView); } catch (Throwable ignored) {}
        }
        dismissZoneView = null;
    }

    private void togglePopup() {
        if (expanded) {
            collapsePopup();
        } else {
            // Prevent ACTION_OUTSIDE + orb ACTION_UP bounce when tapping the orb to close!
            if (System.currentTimeMillis() - lastCollapseMs < 350) return;
            expandPopup();
        }
    }

    private void expandPopup() {
        if (expanded || wm == null || !shown) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int cardW = Math.min(dm.widthPixels - UiStyle.dp(ctx, 24), UiStyle.dp(ctx, 340));
        int maxH = dm.heightPixels - UiStyle.dp(ctx, 100);

        popupCard = buildPopupCard(maxH);
        popupParams = new WindowManager.LayoutParams(
                cardW,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        popupParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;

        popupCard.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) {
                collapsePopup();
                return true;
            }
            return false;
        });

        try {
            wm.addView(popupCard, popupParams);
            expanded = true;
            populatePopupContent();
            handler.removeCallbacks(nowPlayingTick);
            handler.postDelayed(nowPlayingTick, 250);
        } catch (Throwable ignored) {
            expanded = false;
        }
    }

    private void collapsePopup() {
        handler.removeCallbacks(nowPlayingTick);
        if (popupCard != null && wm != null) {
            try { wm.removeView(popupCard); } catch (Throwable ignored) {}
        }
        if (expanded) lastCollapseMs = System.currentTimeMillis();
        popupCard = null;
        expanded = false;
        if (orbView != null && shown) {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            boolean active = EqStore.get(ctx).getSettings().eqEnabled && !EqEngine.get(ctx).isAbFlatCompare();
            orbView.updateVisual(p != null ? p.accentColor : 0xFF39FF14, active, false);
        }
    }

    private LinearLayout buildPopupCard(int maxHeightPx) {
        // Intercept Back key to collapse the floating popup smoothly!
        LinearLayout outer = new LinearLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    collapsePopup();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int cappedH = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedH);
            }
        };
        outer.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(ctx, 14);
        outer.setPadding(pad, UiStyle.dp(ctx, 12), pad, UiStyle.dp(ctx, 12));

        // Scrollable inner body so popup NEVER clips in landscape!
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        // 1. Header: Brand + Open Full App + Car Mode + Close
        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        popBrandTv = new TextView(ctx);
        popBrandTv.setText("KOKERQUALIZER");
        popBrandTv.setTextColor(0xFF39FF14);
        popBrandTv.setTextSize(13f);
        popBrandTv.setTypeface(null, android.graphics.Typeface.BOLD);
        popBrandTv.setLetterSpacing(0.08f);
        hdr.addView(popBrandTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView fullBtn = UiStyle.makePill(ctx, "Deck", false, 0xFF39FF14);
        fullBtn.setTextSize(10.5f);
        fullBtn.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
        fullBtn.setOnClickListener(v -> {
            collapsePopup();
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = UiStyle.dp(ctx, 5);
        hdr.addView(fullBtn, gap);

        TextView carBtn = UiStyle.makePill(ctx, "🚗 Car", false, 0xFF39FF14);
        carBtn.setTextSize(10.5f);
        carBtn.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
        carBtn.setOnClickListener(v -> {
            collapsePopup();
            Intent i = new Intent(ctx, CarModeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        });
        hdr.addView(carBtn, gap);

        TextView closeBtn = UiStyle.makePill(ctx, "✕", false, 0xFF94A3B8);
        closeBtn.setTextSize(11f);
        closeBtn.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
        closeBtn.setOnClickListener(v -> collapsePopup());
        hdr.addView(closeBtn, gap);
        card.addView(hdr);

        // 2. Active Preset Switcher Row
        LinearLayout pRow = new LinearLayout(ctx);
        pRow.setOrientation(LinearLayout.HORIZONTAL);
        pRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prLp.topMargin = UiStyle.dp(ctx, 10);

        TextView prevP = UiStyle.makePill(ctx, "◀", false, 0xFF39FF14);
        prevP.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.get(ctx).cyclePreset(-1);
            EqEngine.get(ctx).setAbFlatCompare(false);
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });
        pRow.addView(prevP);

        popPresetTv = new TextView(ctx);
        popPresetTv.setTextSize(16f);
        popPresetTv.setTypeface(null, android.graphics.Typeface.BOLD);
        popPresetTv.setGravity(Gravity.CENTER);
        popPresetTv.setSingleLine(true);
        popPresetTv.setEllipsize(TextUtils.TruncateAt.END);
        pRow.addView(popPresetTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nextP = UiStyle.makePill(ctx, "▶", false, 0xFF39FF14);
        nextP.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.get(ctx).cyclePreset(1);
            EqEngine.get(ctx).setAbFlatCompare(false);
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });
        pRow.addView(nextP);
        card.addView(pRow, prLp);

        // 3. Scrollable Quick Preset Chips
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        popChipRow = new LinearLayout(ctx);
        popChipRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(popChipRow);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = UiStyle.dp(ctx, 8);
        card.addView(hsv, hLp);

        // 4. Quick Toggles: Power | A/B | HWY
        LinearLayout tRow = new LinearLayout(ctx);
        tRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = UiStyle.dp(ctx, 10);

        popPowerBtn = UiStyle.makePill(ctx, "● ON", true, 0xFF39FF14);
        popPowerBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            s.eqEnabled = !s.eqEnabled;
            EqStore.get(ctx).save();
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });

        popAbBtn = UiStyle.makePill(ctx, "A/B", false, 0xFFFFB300);
        popAbBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqEngine.get(ctx).setAbFlatCompare(!EqEngine.get(ctx).isAbFlatCompare());
            populatePopupContent();
        });

        popHwyBtn = UiStyle.makePill(ctx, "🛣 HWY", false, 0xFF00E676);
        popHwyBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            s.highwayBoost = !s.highwayBoost;
            EqStore.get(ctx).save();
            EqService.startOrRefresh(ctx);
            populatePopupContent();
        });

        LinearLayout.LayoutParams w1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        w1.rightMargin = UiStyle.dp(ctx, 6);
        tRow.addView(popPowerBtn, w1);
        tRow.addView(popAbBtn, w1);
        tRow.addView(popHwyBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tRow, tLp);

        // 5. Quick Sliders: Bass Boost, Punch (MBC), Preamp
        popBassLbl = makeSliderLabel("Bass Boost");
        card.addView(popBassLbl);
        popBassSeek = makePopupSeek(100, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                p.bassBoost = progress;
                popBassLbl.setText(String.format(Locale.US, "Bass Boost (%s): %d%%", EqStore.bassStyleName(p.bassStyle), progress));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popBassSeek);

        popMbcLbl = makeSliderLabel("Punch (3-Band MBC)");
        card.addView(popMbcLbl);
        popMbcSeek = makePopupSeek(100, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                p.punchMbc = progress;
                popMbcLbl.setText(String.format(Locale.US, "Punch (MBC): %d%%", progress));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popMbcSeek);

        popPreampLbl = makeSliderLabel("Preamp");
        card.addView(popPreampLbl);
        popPreampSeek = makePopupSeek(240, progress -> {
            EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
            if (p != null) {
                float db = (progress - 120) / 10f;
                p.preampDb = db;
                popPreampLbl.setText(String.format(Locale.US, "Preamp: %+.1f dB", db));
                EqEngine.get(ctx).applyCurrent();
            }
        });
        card.addView(popPreampSeek);

        // 6. Now Playing + Media Transport Controls
        popNowPlayingTv = new TextView(ctx);
        popNowPlayingTv.setTextSize(11.5f);
        popNowPlayingTv.setTextColor(UiStyle.TEXT_SECONDARY);
        popNowPlayingTv.setSingleLine(true);
        popNowPlayingTv.setEllipsize(TextUtils.TruncateAt.END);
        popNowPlayingTv.setGravity(Gravity.CENTER);
        popNowPlayingTv.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 6));
        card.addView(popNowPlayingTv);

        LinearLayout mRow = new LinearLayout(ctx);
        mRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView mPrev = UiStyle.makePill(ctx, "⏮ Prev", false, 0xFF39FF14);
        mPrev.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(-1);
        });

        popPlayBtn = UiStyle.makePill(ctx, "⏯ Play", true, 0xFF39FF14);
        popPlayBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(0);
        });

        TextView mNext = UiStyle.makePill(ctx, "Next ⏭", false, 0xFF39FF14);
        mNext.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            dispatchMediaControl(1);
        });

        mRow.addView(mPrev, w1);
        mRow.addView(popPlayBtn, w1);
        mRow.addView(mNext, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(mRow);

        sv.addView(card);
        outer.addView(sv);
        return outer;
    }

    private TextView makeSliderLabel(String txt) {
        TextView tv = new TextView(ctx);
        tv.setText(txt);
        tv.setTextColor(UiStyle.TEXT_SECONDARY);
        tv.setTextSize(11.5f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(UiStyle.dp(ctx, 2), UiStyle.dp(ctx, 8), 0, 0);
        return tv;
    }

    private interface IntCb { void onChange(int val); }

    private SeekBar makePopupSeek(int max, IntCb cb) {
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                cb.onChange(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userDraggingSlider = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userDraggingSlider = false;
                EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
                if (p != null) {
                    EqStore.get(ctx).addOrUpdatePreset(p);
                    EqService.startOrRefresh(ctx);
                }
            }
        });
        return sb;
    }

    private void populatePopupContent() {
        if (popupCard == null) return;
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int accent = p.accentColor;
        boolean ab = EqEngine.get(ctx).isAbFlatCompare();

        if (orbView != null) {
            orbView.updateVisual(accent, s.eqEnabled && !ab, expanded);
        }

        popupCard.setBackground(UiStyle.roundRect(0xF40A0D14, EqStore.withAlpha(accent, 195), UiStyle.dp(ctx, 1.5f), UiStyle.dp(ctx, 22)));
        if (popBrandTv != null) popBrandTv.setTextColor(accent);
        popPresetTv.setText(p.name);
        popPresetTv.setTextColor(accent);

        popPowerBtn.setText(s.eqEnabled ? "● EQ ON" : "○ EQ OFF");
        UiStyle.stylePill(ctx, popPowerBtn, s.eqEnabled, accent);

        popAbBtn.setText(ab ? "A/B FLAT" : "A/B");
        UiStyle.stylePill(ctx, popAbBtn, ab, 0xFFFFB300);

        popHwyBtn.setText(s.highwayBoost ? "🛣 HWY ON" : "🛣 HWY");
        UiStyle.stylePill(ctx, popHwyBtn, s.highwayBoost, 0xFF00E676);

        popBassLbl.setText(String.format(Locale.US, "Bass Boost (%s): %d%%", EqStore.bassStyleName(p.bassStyle), Math.round(p.bassBoost)));
        popBassSeek.setProgress(Math.round(p.bassBoost));
        UiStyle.tintSeekBar(popBassSeek, accent);

        popMbcLbl.setText(String.format(Locale.US, "Punch (MBC): %d%%", Math.round(p.punchMbc)));
        popMbcSeek.setProgress(Math.round(p.punchMbc));
        UiStyle.tintSeekBar(popMbcSeek, accent);

        popPreampLbl.setText(String.format(Locale.US, "Preamp: %+.1f dB", p.preampDb));
        popPreampSeek.setProgress(Math.round((p.preampDb + 12f) * 10f));
        UiStyle.tintSeekBar(popPreampSeek, accent);

        popChipRow.removeAllViews();
        List<EqStore.EqPreset> list = store.getPresets();
        for (EqStore.EqPreset item : list) {
            boolean sel = item.id.equals(p.id);
            TextView c = UiStyle.makePill(ctx, item.name, sel, item.accentColor);
            c.setTextSize(10.5f);
            c.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 4));
            c.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                store.setActivePreset(item.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqService.startOrRefresh(ctx);
                populatePopupContent();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiStyle.dp(ctx, 5);
            popChipRow.addView(c, lp);
        }

        refreshNowPlaying();
    }

    private boolean shouldUseInAppYt() {
        YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
        if (inApp == null || inApp.getNowPlayingLabel() == null) return false;
        if (inApp.isPlaying()) return true;
        return !KokerMediaListenerService.isPlayingNow(ctx);
    }

    private void dispatchMediaControl(int dir) {
        if (shouldUseInAppYt()) {
            YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
            if (dir < 0) inApp.playPrev();
            else if (dir > 0) inApp.playNext();
            else inApp.togglePlayPause();
        } else {
            if (dir < 0) KokerMediaListenerService.skipPrev(ctx);
            else if (dir > 0) KokerMediaListenerService.skipNext(ctx);
            else KokerMediaListenerService.playPause(ctx);
        }
        handler.postDelayed(this::refreshNowPlaying, 300);
    }

    private void refreshNowPlaying() {
        if (popNowPlayingTv == null || popPlayBtn == null) return;
        EqStore.EqPreset p = EqStore.get(ctx).getActivePreset();
        int accent = p != null ? p.accentColor : 0xFF39FF14;
        if (shouldUseInAppYt()) {
            YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
            popNowPlayingTv.setText(inApp.getNowPlayingLabel());
            popNowPlayingTv.setTextColor(accent);
            popPlayBtn.setText(inApp.isPlaying() ? "⏸ Pause" : "▶ Play");
            UiStyle.stylePill(ctx, popPlayBtn, inApp.isPlaying(), accent);
        } else {
            String np = KokerMediaListenerService.getNowPlayingLabel(ctx);
            boolean playing = KokerMediaListenerService.isPlayingNow(ctx);
            popNowPlayingTv.setText(np != null ? np : "♪  Ready — Media Controls");
            popNowPlayingTv.setTextColor(np != null ? accent : UiStyle.TEXT_MUTED);
            popPlayBtn.setText(playing ? "⏸ Pause" : "▶ Play");
            UiStyle.stylePill(ctx, popPlayBtn, playing, accent);
        }
    }

    // 3D Depth-of-Field Crystal Glass Orb with Pulsating Floating 'K' Stardust Particles!
    private static class BubbleOrbView extends View {
        private static class DustMote {
            float kx, ky;        // Target normalized position along the 'K' (-1..1)
            float scatterX, scatterY; // Outward dissolve drift vector
            float depthZ;        // -1.0 (deep bokeh) .. +1.0 (crisp foreground)
            float phase;         // Individual twinkle & drift phase
            float sizeMul;       // Relative mote radius
            boolean isAmbient;   // True for orbiting outer stardust, false for 'K' dust
        }

        private int accent = 0xFF39FF14;
        private boolean eqOn = true;
        private boolean open = false;
        private boolean animating = false;
        private long startTimeMs = System.currentTimeMillis();

        private final DustMote[] motes;
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bouncePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint kGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint moteGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint moteCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path kPath = new Path();
        private final RectF specRect = new RectF();
        private final RectF bounceRect = new RectF();

        private int cachedW = -1;
        private int cachedAccent = 0;
        private boolean cachedEqOn = false;

        private final PowerManager powerMgr;

        private final Runnable frameTick = new Runnable() {
            @Override
            public void run() {
                if (!animating) return;
                boolean interactive = (powerMgr == null || powerMgr.isInteractive()) && getWindowVisibility() == VISIBLE;
                if (interactive) {
                    invalidate();
                    postDelayed(this, 33); // 30fps while screen is awake
                } else {
                    postDelayed(this, 1000); // Sleep quietly while screen is off (saves battery!)
                }
            }
        };

        public BubbleOrbView(Context context) {
            super(context);
            powerMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            shadowPaint.setStyle(Paint.Style.FILL);
            auraPaint.setStyle(Paint.Style.FILL);
            spherePaint.setStyle(Paint.Style.FILL);
            bouncePaint.setStyle(Paint.Style.STROKE);
            bouncePaint.setStrokeCap(Paint.Cap.ROUND);
            bouncePaint.setStrokeWidth(UiStyle.dpf(context, 2.8f));

            rimGlowPaint.setStyle(Paint.Style.STROKE);
            rimGlowPaint.setStrokeWidth(UiStyle.dpf(context, 3.6f));

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(UiStyle.dpf(context, 1.3f));

            kGlowPaint.setStyle(Paint.Style.STROKE);
            kGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            kGlowPaint.setStrokeJoin(Paint.Join.ROUND);
            kGlowPaint.setStrokeWidth(UiStyle.dpf(context, 4.5f));

            moteGlowPaint.setStyle(Paint.Style.FILL);
            moteCorePaint.setStyle(Paint.Style.FILL);
            specPaint.setStyle(Paint.Style.FILL);

            motes = createMotes();
        }

        private DustMote[] createMotes() {
            // 30 dust motes forming a crisp, recognizable glowing 'K' + 14 ambient orbiting stardust motes
            java.util.List<DustMote> list = new java.util.ArrayList<>();
            java.util.Random rnd = new java.util.Random(4242L);

            // Stroke 1: Vertical spine of 'K' (x = -0.34, y from -0.58 to +0.58)
            for (int i = 0; i < 12; i++) {
                float t = i / 11f;
                list.add(makeKMote(-0.34f, -0.58f + t * 1.16f, rnd));
            }
            // Stroke 2: Upper diagonal arm of 'K' (from (-0.28, 0.02) to (0.42, -0.56))
            for (int i = 0; i < 9; i++) {
                float t = i / 8f;
                list.add(makeKMote(-0.28f + t * 0.70f, 0.02f - t * 0.58f, rnd));
            }
            // Stroke 3: Lower diagonal leg of 'K' (from (-0.20, -0.02) to (0.44, 0.58))
            for (int i = 0; i < 9; i++) {
                float t = i / 8f;
                list.add(makeKMote(-0.20f + t * 0.64f, -0.02f + t * 0.60f, rnd));
            }
            // Ambient orbiting dust motes (some deep inside, some floating near & outside the rim)
            for (int i = 0; i < 14; i++) {
                DustMote m = new DustMote();
                m.isAmbient = true;
                double ang = (Math.PI * 2.0 * i) / 14.0 + rnd.nextFloat() * 0.4;
                float rad = 0.72f + rnd.nextFloat() * 0.56f;
                m.kx = (float) (Math.cos(ang) * rad);
                m.ky = (float) (Math.sin(ang) * rad);
                m.scatterX = (float) Math.cos(ang + 1.2) * 0.25f;
                m.scatterY = (float) Math.sin(ang + 1.2) * 0.25f;
                m.depthZ = -1.0f + rnd.nextFloat() * 2.0f;
                m.phase = rnd.nextFloat() * 6.283f;
                m.sizeMul = 0.6f + rnd.nextFloat() * 0.9f;
                list.add(m);
            }
            return list.toArray(new DustMote[0]);
        }

        private DustMote makeKMote(float x, float y, java.util.Random rnd) {
            DustMote m = new DustMote();
            m.isAmbient = false;
            m.kx = x + (rnd.nextFloat() - 0.5f) * 0.07f;
            m.ky = y + (rnd.nextFloat() - 0.5f) * 0.07f;
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            float dist = 0.35f + rnd.nextFloat() * 0.55f;
            m.scatterX = (float) (Math.cos(ang) * dist);
            m.scatterY = (float) (Math.sin(ang) * dist);
            m.depthZ = -0.85f + rnd.nextFloat() * 1.85f;
            m.phase = rnd.nextFloat() * 6.283f;
            m.sizeMul = 0.75f + rnd.nextFloat() * 0.75f;
            return m;
        }

        public void updateVisual(int accentColor, boolean eqActive, boolean isExpanded) {
            this.accent = accentColor;
            this.eqOn = eqActive;
            this.open = isExpanded;
            cachedW = -1; // refresh gradients
            postInvalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animating = true;
            removeCallbacks(frameTick);
            post(frameTick);
        }

        @Override
        protected void onDetachedFromWindow() {
            animating = false;
            removeCallbacks(frameTick);
            super.onDetachedFromWindow();
        }

        private void ensureShaders(float cx, float cy, float r, int col) {
            if (cachedW == getWidth() && cachedAccent == col && cachedEqOn == eqOn) return;
            cachedW = getWidth();
            cachedAccent = col;
            cachedEqOn = eqOn;

            // Grounded 3D drop shadow
            shadowPaint.setShader(new RadialGradient(
                    cx + r * 0.10f, cy + r * 0.16f, r * 1.24f,
                    new int[]{0xBB000000, 0x55000000, 0x00000000},
                    new float[]{0.45f, 0.78f, 1.0f},
                    Shader.TileMode.CLAMP));

            // Deep 3D obsidian-crystal volumetric sphere shading (off-center highlight -> deep dark core -> tinted rim scatter)
            int midTint = EqStore.withAlpha(col, eqOn ? 58 : 22);
            int rimTint = EqStore.withAlpha(col, eqOn ? 115 : 40);
            spherePaint.setShader(new RadialGradient(
                    cx - r * 0.30f, cy - r * 0.34f, r * 1.38f,
                    new int[]{0xFF2B3548, 0xFF101624, 0xFF04060B, midTint, rimTint},
                    new float[]{0.0f, 0.28f, 0.66f, 0.88f, 1.0f},
                    Shader.TileMode.CLAMP));

            // Cached outer volumetric neon aura shader (modulate alpha per frame -> ZERO allocations in onDraw!)
            auraPaint.setShader(new RadialGradient(
                    cx, cy, r * 1.38f,
                    new int[]{EqStore.withAlpha(col, 255), EqStore.withAlpha(col, 85), 0x00000000},
                    new float[]{0.55f, 0.82f, 1.0f},
                    Shader.TileMode.CLAMP));

            // Glossy curved upper-left glass dome highlight
            specPaint.setShader(new RadialGradient(
                    cx - r * 0.34f, cy - r * 0.42f, r * 0.56f,
                    new int[]{0x99FFFFFF, 0x38FFFFFF, 0x00FFFFFF},
                    new float[]{0.0f, 0.48f, 1.0f},
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float r = Math.min(w, h) * 0.36f; // Room around orb for outer bokeh dust & 3D shadow
            int col = eqOn ? accent : 0xFF64748B;
            ensureShaders(cx, cy, r, col);

            float tSec = (System.currentTimeMillis() - startTimeMs) / 1000f;

            // Hypnotic 4.2-second materialize -> glow pulse -> dissolve & vanish cycle for the 'K'!
            double cycle = (tSec * (Math.PI * 2.0 / 4.2));
            // Smooth 0..1 visibility curve where K cleanly shows, pulses brightly, then fades almost completely away
            float rawWave = (float) (0.5 - 0.5 * Math.cos(cycle));
            float kPresence = (float) Math.pow(rawWave, 1.45); // lingers softly in vanished dust, peaks crisply as glowing 'K'
            float fastPulse = 0.82f + 0.18f * (float) Math.sin(tSec * 6.5);
            float kAlpha = (eqOn ? kPresence : kPresence * 0.45f) * fastPulse;
            float scatterAmt = 1.0f - kPresence; // 0 when tightly formed as 'K', 1 when dissolved into floating dust

            // 1. Soft 3D ambient drop shadow
            canvas.drawCircle(cx + r * 0.08f, cy + r * 0.12f, r * 1.24f, shadowPaint);

            // 2. Pulsating volumetric neon aura behind the orb (zero allocations!)
            int auraA = Math.min(255, Math.round((eqOn ? (open ? 95 : 52) : 18) + kAlpha * 48));
            auraPaint.setAlpha(auraA);
            canvas.drawCircle(cx, cy, r * 1.38f, auraPaint);

            // 3. Back-layer deep bokeh dust (depthZ < -0.15) behind the crystal sphere
            drawMotesLayer(canvas, cx, cy, r, col, tSec, kAlpha, scatterAmt, -2f, -0.15f);

            // 4. 3D Volumetric Crystal Glass Sphere Body
            canvas.drawCircle(cx, cy, r, spherePaint);

            // Subtle lower-right subsurface caustic bounce light (gives intense 3D depth)
            bounceRect.set(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f);
            bouncePaint.setColor(EqStore.withAlpha(col, Math.round(55 + kAlpha * 90)));
            canvas.drawArc(bounceRect, 22f, 76f, false, bouncePaint);

            // 5. Ghostly soft neon 'K' core mist that breathes in & vanishes with the particles
            if (kAlpha > 0.06f) {
                float ks = r * 0.82f;
                kPath.reset();
                kPath.moveTo(cx - ks * 0.34f, cy - ks * 0.56f);
                kPath.lineTo(cx - ks * 0.34f, cy + ks * 0.56f);
                kPath.moveTo(cx - ks * 0.28f, cy + ks * 0.02f);
                kPath.lineTo(cx + ks * 0.42f, cy - ks * 0.54f);
                kPath.moveTo(cx - ks * 0.20f, cy - ks * 0.02f);
                kPath.lineTo(cx + ks * 0.44f, cy + ks * 0.56f);

                kGlowPaint.setColor(EqStore.withAlpha(col, Math.round(kAlpha * 88)));
                canvas.drawPath(kPath, kGlowPaint);
            }

            // 6. Mid-depth & Foreground 3D Stardust Particles forming the pulsating 'K' & floating around the orb
            drawMotesLayer(canvas, cx, cy, r, col, tSec, kAlpha, scatterAmt, -0.15f, 2f);

            // 7. Outer Fresnel glass rim + crisp specular edge
            rimGlowPaint.setColor(EqStore.withAlpha(col, Math.round((open ? 120 : 62) + kAlpha * 55)));
            canvas.drawCircle(cx, cy, r, rimGlowPaint);
            rimPaint.setColor(open ? 0xEEFFFFFF : EqStore.withAlpha(col, 215));
            canvas.drawCircle(cx, cy, r, rimPaint);

            // 8. Curved glossy 3D upper-left glass dome reflection (locks particles INSIDE the 3D glass!)
            canvas.save();
            canvas.rotate(-32f, cx, cy);
            specRect.set(cx - r * 0.58f, cy - r * 0.86f, cx + r * 0.42f, cy - r * 0.22f);
            canvas.drawOval(specRect, specPaint);
            canvas.restore();
        }

        private void drawMotesLayer(Canvas canvas, float cx, float cy, float r, int col,
                                    float tSec, float kAlpha, float scatterAmt, float zMin, float zMax) {
            float baseDp = UiStyle.dpf(getContext(), 1.0f);
            for (DustMote m : motes) {
                if (m.depthZ < zMin || m.depthZ >= zMax) continue;

                // Depth-of-field parallax & focus blur factor:
                // Foreground (z ~ +1): tiny crisp white-hot sparkle
                // Mid-plane (z ~ 0): focused glowing particle
                // Deep background (z ~ -1): wider, softer out-of-focus bokeh circle
                float dofBlur = Math.abs(m.depthZ - 0.35f); // sharpest near front-mid plane
                float scale = 0.85f + (m.depthZ + 1f) * 0.22f;

                float wobbleX = (float) Math.sin(tSec * 1.8 + m.phase) * 0.08f;
                float wobbleY = (float) Math.cos(tSec * 1.5 + m.phase * 1.3) * 0.08f;

                float px, py, alpha;
                if (m.isAmbient) {
                    // Gently orbiting ambient stardust around & over the orb
                    double orbitAng = tSec * (0.35 + m.depthZ * 0.12) + m.phase;
                    float ox = m.kx * (float) Math.cos(orbitAng * 0.35) - m.ky * (float) Math.sin(orbitAng * 0.35);
                    float oy = m.kx * (float) Math.sin(orbitAng * 0.35) + m.ky * (float) Math.cos(orbitAng * 0.35);
                    px = cx + (ox + wobbleX) * r;
                    py = cy + (oy + wobbleY) * r;
                    float twinkle = 0.30f + 0.70f * (0.5f + 0.5f * (float) Math.sin(tSec * 3.1 + m.phase * 2f));
                    alpha = (eqOn ? 0.55f : 0.22f) * twinkle;
                } else {
                    // 'K' stardust motes: drift in -> glow as K -> scatter & fade away
                    float localWave = (float) Math.sin(tSec * 3.4 + m.phase);
                    float nx = m.kx + m.scatterX * scatterAmt + wobbleX * (0.4f + scatterAmt);
                    float ny = m.ky + m.scatterY * scatterAmt + wobbleY * (0.4f + scatterAmt);
                    px = cx + nx * r * 0.82f;
                    py = cy + ny * r * 0.82f;
                    // Smooth fade-in and fade-out with staggered sparkle per grain
                    float sparkle = 0.72f + 0.28f * localWave;
                    alpha = Math.min(1f, (0.04f + kAlpha * 0.96f) * sparkle);
                }

                if (alpha < 0.04f) continue;

                float coreR = baseDp * (0.95f + m.sizeMul * 0.85f) * scale;
                float haloR = coreR * (2.1f + dofBlur * 1.6f);

                // Soft bokeh glow halo
                int haloA = Math.round(alpha * (115f / (1f + dofBlur * 0.5f)));
                moteGlowPaint.setColor(EqStore.withAlpha(col, haloA));
                canvas.drawCircle(px, py, haloR, moteGlowPaint);

                // Crisp white-tinted neon stardust core (sharper in focus, softer in deep DoF)
                int coreA = Math.round(alpha * (245f - dofBlur * 95f));
                moteCorePaint.setColor(m.depthZ > 0.15f
                        ? EqStore.withAlpha(0xFFFFFFFF, coreA)
                        : EqStore.withAlpha(col, coreA));
                canvas.drawCircle(px, py, coreR, moteCorePaint);
            }
        }
    }
}
