package com.buddyapps.kokerqualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manages the system-wide floating 3D Crystal Orb overlay window, drag/snap/dismiss
 * physics, landscape auto-tuck, idle fade, and coordinates the expandable mini-mixer popup.
 */
public class BubbleOverlayManager {
    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BubblePopupController popupCtrl;

    private BubbleOrbView orbView;
    private WindowManager.LayoutParams orbParams;
    private ValueAnimator orbSnapAnim;

    private View dismissZoneView;
    private WindowManager.LayoutParams dismissParams;

    private WindowManager.LayoutParams popupParams;

    private boolean shown = false;
    private boolean expanded = false;
    private long lastCollapseMs = 0;
    private long lastOrbTapMs = 0;
    private int currentOrbPx = 0;
    private boolean currentTucked = false;

    private final Runnable idleFadeTask = new Runnable() {
        @Override
        public void run() {
            if (orbView != null && shown && !expanded && EqStore.get(ctx).getSettings().orbAutoFade) {
                orbView.animate().alpha(0.46f).setDuration(350).start();
            }
        }
    };

    private final Runnable singleTapOpenTask = new Runnable() {
        @Override
        public void run() {
            togglePopup();
        }
    };

    private final Runnable nowPlayingTick = new Runnable() {
        @Override
        public void run() {
            if (expanded && popupCtrl.getCard() != null) {
                popupCtrl.refreshNowPlaying();
                handler.postDelayed(this, 1800);
            }
        }
    };

    public BubbleOverlayManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.popupCtrl = new BubblePopupController(this.ctx, this.handler, new BubblePopupController.Host() {
            @Override
            public void onCollapseRequested() {
                collapsePopup();
            }

            @Override
            public void onPopupContentPopulated(boolean eqActive) {
                if (orbView != null) {
                    EqStore.Settings s = EqStore.get(ctx).getSettings();
                    orbView.updateVisual(EqStore.DEFAULT_ACCENT, eqActive, expanded, s.orbLetter, s.orbBeatReactive);
                    orbView.setAlpha(1.0f);
                    handler.removeCallbacks(idleFadeTask);
                }
            }
        });
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

    private int computeOrbSizePx(EqStore.Settings s) {
        int dpVal = s.orbSizeMode == 0 ? 60 : (s.orbSizeMode == 2 ? 88 : 74);
        return UiStyle.dp(ctx, dpVal);
    }

    private boolean isTuckedLandscape(EqStore.Settings s) {
        return s.orbAutoTuckLandscape && !expanded
                && ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void bumpOrbActivity() {
        handler.removeCallbacks(idleFadeTask);
        if (orbView != null) {
            orbView.animate().cancel();
            orbView.setAlpha(1.0f);
        }
        if (shown && !expanded && EqStore.get(ctx).getSettings().orbAutoFade) {
            handler.postDelayed(idleFadeTask, 5000L);
        }
    }

    public void onScreenConfigChanged() {
        runOnMain(() -> {
            if (!shown || orbView == null || orbParams == null || wm == null) return;
            if (orbSnapAnim != null) { orbSnapAnim.cancel(); orbSnapAnim = null; }
            hideDismissZone();
            if (expanded) collapsePopup();
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            int orbSize = computeOrbSizePx(s);
            currentOrbPx = orbSize;
            currentTucked = isTuckedLandscape(s);
            orbParams.width = orbSize;
            orbParams.height = orbSize;
            int tuckShift = currentTucked ? Math.round(orbSize * 0.42f) : -UiStyle.dp(ctx, 2);
            orbParams.x = s.bubbleRightEdge ? (dm.widthPixels - orbSize + tuckShift) : -tuckShift;
            orbParams.y = Math.max(UiStyle.dp(ctx, 48),
                    Math.min(dm.heightPixels - orbSize - UiStyle.dp(ctx, 48), orbParams.y));
            try { wm.updateViewLayout(orbView, orbParams); } catch (Throwable ignored) {}
            refreshUi();
        });
    }

    public void show() {
        runOnMain(() -> {
            if (shown || wm == null || !canDraw(ctx)) return;
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            int orbSize = computeOrbSizePx(s);
            currentOrbPx = orbSize;
            currentTucked = isTuckedLandscape(s);

            orbView = new BubbleOrbView(ctx);
            orbParams = new WindowManager.LayoutParams(
                    orbSize, orbSize,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            orbParams.gravity = Gravity.TOP | Gravity.START;
            int tuckShift = currentTucked ? Math.round(orbSize * 0.42f) : -UiStyle.dp(ctx, 2);
            orbParams.x = s.bubbleRightEdge ? (dm.widthPixels - orbSize + tuckShift) : -tuckShift;
            orbParams.y = Math.max(UiStyle.dp(ctx, 70),
                    Math.min(dm.heightPixels - orbSize - UiStyle.dp(ctx, 80), s.bubbleY));

            attachOrbTouch(orbView);
            try {
                wm.addView(orbView, orbParams);
                shown = true;
                refreshUi();
                bumpOrbActivity();
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
            boolean active = s.eqEnabled && !EqEngine.get(ctx).isAbFlatCompare();
            int desiredSize = computeOrbSizePx(s);
            boolean desiredTucked = isTuckedLandscape(s);
            if (orbView != null && orbParams != null && wm != null && (desiredSize != currentOrbPx || desiredTucked != currentTucked)) {
                currentOrbPx = desiredSize;
                currentTucked = desiredTucked;
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                orbParams.width = desiredSize;
                orbParams.height = desiredSize;
                int tuckShift = desiredTucked ? Math.round(desiredSize * 0.42f) : -UiStyle.dp(ctx, 2);
                orbParams.x = s.bubbleRightEdge ? (dm.widthPixels - desiredSize + tuckShift) : -tuckShift;
                try { wm.updateViewLayout(orbView, orbParams); } catch (Throwable ignored) {}
            }

            if (orbView != null) {
                orbView.updateVisual(EqStore.DEFAULT_ACCENT, active, expanded, s.orbLetter, s.orbBeatReactive);
                if (!s.orbAutoFade) {
                    handler.removeCallbacks(idleFadeTask);
                    orbView.animate().cancel();
                    orbView.setAlpha(1.0f);
                }
            }
            if (expanded && popupCtrl.getCard() != null && !popupCtrl.isDraggingSlider()) {
                popupCtrl.populatePopupContent();
            }
        });
    }

    private void attachOrbTouch(View v) {
        final float[] downRawX = new float[1];
        final float[] downRawY = new float[1];
        final int[] startX = new int[1];
        final int[] startY = new int[1];
        final boolean[] moved = new boolean[1];
        final int slop = UiStyle.dp(ctx, 8);

        v.setOnTouchListener((view, e) -> {
            if (orbParams == null || wm == null) return false;
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int orbSize = currentOrbPx > 0 ? currentOrbPx : UiStyle.dp(ctx, 74);
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                bumpOrbActivity();
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
                    handler.removeCallbacks(singleTapOpenTask);
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
                bumpOrbActivity();
                if (!moved[0]) {
                    UiStyle.hapticTick(view, ctx);
                    int dtAction = EqStore.get(ctx).getSettings().orbDoubleTapAction;
                    if (dtAction == EqStore.ORB_DT_POPUP || expanded) {
                        togglePopup();
                    } else {
                        long now = System.currentTimeMillis();
                        if (now - lastOrbTapMs < 290) {
                            lastOrbTapMs = 0;
                            handler.removeCallbacks(singleTapOpenTask);
                            runOrbDoubleTapShortcut(dtAction);
                        } else {
                            lastOrbTapMs = now;
                            handler.removeCallbacks(singleTapOpenTask);
                            handler.postDelayed(singleTapOpenTask, 250);
                        }
                    }
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
                    EqStore.Settings s = EqStore.get(ctx).getSettings();
                    boolean snapRight = (orbParams.x + orbSize * 0.5f) >= dm.widthPixels * 0.5f;
                    int tuckShift = isTuckedLandscape(s) ? Math.round(orbSize * 0.42f) : -UiStyle.dp(ctx, 2);
                    int targetX = snapRight ? (dm.widthPixels - orbSize + tuckShift) : -tuckShift;
                    animateOrbX(orbParams.x, targetX);
                    s.bubbleRightEdge = snapRight;
                    s.bubbleY = orbParams.y;
                    EqStore.get(ctx).save();
                }
                return true;
            }
            return false;
        });
    }

    private void runOrbDoubleTapShortcut(int dtAction) {
        if (dtAction == EqStore.ORB_DT_NEXT) {
            popupCtrl.dispatchMediaControl(1);
        } else if (dtAction == EqStore.ORB_DT_TOGGLE_EQ) {
            EqStore store = EqStore.get(ctx);
            store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
            store.save();
            EqEngine.get(ctx).triggerSmoothRamp();
            EqService.startOrRefresh(ctx);
        } else if (dtAction == EqStore.ORB_DT_CAR) {
            try {
                Intent i = new Intent(ctx, CarModeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(i);
            } catch (Throwable ignored) {}
        }
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
            // Prevent ACTION_OUTSIDE + orb ACTION_UP bounce when tapping the orb to close
            if (System.currentTimeMillis() - lastCollapseMs < 350) return;
            expandPopup();
        }
    }

    private void expandPopup() {
        if (expanded || wm == null || !shown) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int cardW = Math.min(dm.widthPixels - UiStyle.dp(ctx, 24), UiStyle.dp(ctx, 340));
        int maxH = dm.heightPixels - UiStyle.dp(ctx, 100);

        LinearLayout card = popupCtrl.buildPopupCard(maxH);
        popupParams = new WindowManager.LayoutParams(
                cardW,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        popupParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;

        card.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) {
                collapsePopup();
                return true;
            }
            return false;
        });

        try {
            wm.addView(card, popupParams);
            expanded = true;
            popupCtrl.populatePopupContent();
            handler.removeCallbacks(nowPlayingTick);
            handler.postDelayed(nowPlayingTick, 250);
        } catch (Throwable ignored) {
            expanded = false;
            popupCtrl.reset();
        }
    }

    private void collapsePopup() {
        handler.removeCallbacks(nowPlayingTick);
        LinearLayout card = popupCtrl.getCard();
        if (card != null && wm != null) {
            try { wm.removeView(card); } catch (Throwable ignored) {}
        }
        if (expanded) lastCollapseMs = System.currentTimeMillis();
        popupCtrl.reset();
        expanded = false;
        if (orbView != null && shown) {
            EqStore.Settings s = EqStore.get(ctx).getSettings();
            boolean active = s.eqEnabled && !EqEngine.get(ctx).isAbFlatCompare();
            orbView.updateVisual(EqStore.DEFAULT_ACCENT, active, false, s.orbLetter, s.orbBeatReactive);
            bumpOrbActivity();
        }
    }
}
