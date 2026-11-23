package com.buddyapps.kokerqualizer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements DrawerPanels.Host {
    private static final int REQ_AUDIO_PERM = 101;
    private static final int REQ_BT_PERM = 102;
    private static final int REQ_NOTIF_PERM = 103;
    private static final int REQ_EXPORT_JSON = 201;
    private static final int REQ_IMPORT_JSON = 202;

    private EqStore store;
    private YouTubeStore ytStore;
    private EqEngine engine;
    private SoundTestEngine soundTestEngine;

    private MainYouTubeController ytCtrl;
    private MainKnobRackController knobRackCtrl;
    private MainNavigationController navCtrl;

    private FrameLayout rootFrame;
    private LinearLayout topHeader;
    private LinearLayout stickyBottomBar;
    private View bottomBarPulseLine;
    private LinearLayout vizCard;
    private LinearLayout eqCard;
    private LinearLayout tiltCard;
    private TextView bassTiltLbl, trebleTiltLbl;
    private SeekBar bassTiltSeek, trebleTiltSeek;
    private GradientDrawable vizCardBg;
    private GradientDrawable eqCardBg;
    private OnBackInvokedCallback backCallback;
    private boolean backCallbackRegistered = false;
    private TextView statusPill;
    private TextView bubblePill;
    private TextView titleTv;
    private TextView telemetryTv;
    private VisualizerView visualizerView;
    private TextView micHintBtn;
    private final TextView[] vizModeBtns = new TextView[4];

    private View activeColorDot;
    private TextView activePresetNameTv;
    private TextView quickFavBtn;
    private TextView quickSaveBtn;
    private HorizontalScrollView presetChipScroll;
    private LinearLayout presetChipRow;

    private TextView btnBandCount, btnFreqLayout, btnViewMode, btnBrush, btnSmooth, btnTilt, btnMorph, btnZen, btnHwy, btnLock, btnUndo, btnRedo, btnReset;
    private EqDeckView eqDeckView;
    private LinearLayout morphCard;
    private SoundMorphPadView morphPadView;
    private EqStore.EqPreset morphBaseSnapshot;
    private String lastMorphPresetId = null;

    private TextView thumbPower, thumbAb, thumbPreset, thumbCar;

    private long lastGlowMs = 0;
    private long lastLockToastMs = 0;
    private Toast activeToast;

    private final Deque<EqStore.EqPreset> undoStack = new ArrayDeque<>();
    private final Deque<EqStore.EqPreset> redoStack = new ArrayDeque<>();
    private EqStore.EqPreset dragStartSnapshot = null;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isFinishing() || isDestroyed()) return;
            if (dragStartSnapshot != null) {
                updateStatusBadge(engine.getStatusText(), engine.isEngineActive());
                updateTelemetryStrip();
            } else {
                // Avoid tearing down interactive sub-panels during passive broadcasts
                boolean rebuildSub = (navCtrl.getOpenSubPanel() == MainNavigationController.PANEL_HEALTH);
                refreshAllUi(true, rebuildSub);
            }
        }
    };

    private final BroadcastReceiver shutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            soundTestEngine.setFreqCallback(null);
            soundTestEngine.stop();
            if (ytCtrl != null) ytCtrl.stopAndReset();
            finishAndRemoveTask();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = EqStore.get(this);
        ytStore = YouTubeStore.get(this);
        engine = EqEngine.get(this);
        soundTestEngine = new SoundTestEngine();

        initControllers();

        getWindow().setStatusBarColor(UiStyle.BG_AMOLED);
        getWindow().setNavigationBarColor(0xFF0A0D14);

        buildRootLayout();
        setContentView(rootFrame);

        UiStyle.applyEdgeToEdgeInsets(rootFrame, topHeader, UiStyle.dp(this, 10), stickyBottomBar, UiStyle.dp(this, 10));
        navCtrl.applyInsets();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERM);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBackAction;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            backCallbackRegistered = true;
        }

        IntentFilter sf = new IntentFilter(EqService.ACTION_APP_SHUTDOWN);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(shutdownReceiver, sf, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(shutdownReceiver, sf);
        }

        EqService.startOrRefresh(this);
        refreshAllUi(false);
        ytCtrl.initializePlaybackAndBridge();
    }

    private void initControllers() {
        ytCtrl = new MainYouTubeController(this, store, ytStore, new MainYouTubeController.Host() {
            @Override public void openYouTubePanel() { navCtrl.openPanel(MainNavigationController.PANEL_YOUTUBE); }
            @Override public void showSleepTimerDialog() {
                MainDialogsHelper.showSleepTimerDialog(MainActivity.this, () -> ytCtrl.refreshCard());
            }
            @Override public void onRefreshDeckRequested() { refreshAllUi(false); }
            @Override public void onRefreshYouTubeSubPanelIfOpen() {
                if (navCtrl.getOpenSubPanel() == MainNavigationController.PANEL_YOUTUBE) {
                    navCtrl.renderOpenSubPanel();
                }
            }
            @Override public void onStopSoundTest() {
                soundTestEngine.setFreqCallback(null);
                soundTestEngine.stop();
            }
            @Override public boolean isHostDestroyed() { return isFinishing() || isDestroyed(); }
            @Override public DrawerPanels.Host getDrawerHost() { return MainActivity.this; }
        });

        knobRackCtrl = new MainKnobRackController(this, store, engine, new MainKnobRackController.Host() {
            @Override public void ensureDragSnapshot() {
                EqStore.EqPreset p = store.getActivePreset();
                if (p != null && dragStartSnapshot == null) {
                    dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                }
            }
            @Override public void commitDragSnapshot() {
                if (dragStartSnapshot != null) {
                    undoStack.push(dragStartSnapshot);
                    if (undoStack.size() > 30) undoStack.removeLast();
                    redoStack.clear();
                    dragStartSnapshot = null;
                }
            }
            @Override public void pushUndo(EqStore.EqPreset preset) { MainActivity.this.pushUndo(preset); }
            @Override public void resetMorphPadBase() { MainActivity.this.resetMorphPadBase(); }
            @Override public void updateTelemetryStrip() { MainActivity.this.updateTelemetryStrip(); }
            @Override public void refreshUndoRedoPills() { MainActivity.this.refreshUndoRedoPills(); }
            @Override public void refreshAllUi(boolean animate) { MainActivity.this.refreshAllUi(animate); }
            @Override public void showLockedToast() { MainActivity.this.showLockedToast(); }
        });

        navCtrl = new MainNavigationController(this, this, new MainNavigationController.Callbacks() {
            @Override public void onToggleFloatingBubble() { toggleFloatingBubble(); }
            @Override public void onToggleMorphPad() {
                store.getSettings().morphPadVisible = !store.getSettings().morphPadVisible;
                store.save();
                resetMorphPadBase();
                refreshAllUi(false);
            }
            @Override public void onShowSleepTimer() {
                MainDialogsHelper.showSleepTimerDialog(MainActivity.this, () -> ytCtrl.refreshCard());
            }
            @Override public void onStopSoundTest() {
                soundTestEngine.setFreqCallback(null);
                soundTestEngine.stop();
            }
            @Override public void onPanelClosed() { refreshAllUi(false); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(EqService.ACTION_STATE_BROADCAST);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
        engine.setStatusListener((txt, active) -> runOnUiThread(() -> {
            updateStatusBadge(txt, active);
            updateTelemetryStrip();
        }));
        if (store.getSettings().bubbleEnabled && BubbleOverlayManager.canDraw(this)) {
            EqService.startOrRefresh(this);
        }
        SleepTimerManager.get(this).setListener(() -> ytCtrl.refreshCard());
        engine.setVisualizerListener((bars, wave, l, r, clip, bassEnergy) -> {
            visualizerView.updateAudio(bars, wave, l, r, clip);
            long now = System.currentTimeMillis();
            boolean wantGlow = store.getSettings().reactiveGlow || store.getSettings().bottomBarPulse;
            if (now - lastGlowMs > (wantGlow ? 50 : 200)) {
                lastGlowMs = now;
                runOnUiThread(() -> {
                    if (wantGlow) applyReactiveGlow(bassEnergy);
                    updateTelemetryStrip();
                });
            }
        });
        refreshAllUi(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        YouTubePlayerView.flushCookiesNow();
        try { unregisterReceiver(stateReceiver); } catch (Throwable ignored) {}
        SleepTimerManager.get(this).setListener(null);
        engine.setStatusListener(null);
        engine.setVisualizerListener(null);
        engine.setSoloBandIndex(-1);
        soundTestEngine.setFreqCallback(null);
        soundTestEngine.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        YouTubePlayerView.flushCookiesNow();
    }

    @Override
    protected void onDestroy() {
        YouTubePlayerView.flushCookiesNow();
        try { unregisterReceiver(shutdownReceiver); } catch (Throwable ignored) {}
        if (ytCtrl != null) ytCtrl.destroy();
        soundTestEngine.setFreqCallback(null);
        soundTestEngine.stop();
        if (Build.VERSION.SDK_INT >= 33 && backCallbackRegistered && backCallback != null) {
            try { getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback); } catch (Throwable ignored) {}
            backCallbackRegistered = false;
        }
        super.onDestroy();
    }

    private void handleBackAction() {
        if (navCtrl.isDrawerOpen()) {
            navCtrl.closeDrawer();
        } else if (navCtrl.hasOpenPanel()) {
            closePanel();
        } else if (ytCtrl != null && ytCtrl.canGoBackInWeb()) {
            ytCtrl.goBackInWeb();
        } else {
            YouTubePlayerView.flushCookiesNow();
            moveTaskToBack(true);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBackAction();
    }

    private void buildRootLayout() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(UiStyle.BG_AMOLED);

        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);

        mainCol.addView(buildTopHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(this, 14);
        body.setPadding(pad, UiStyle.dp(this, 4), pad, UiStyle.dp(this, 16));

        body.addView(buildVisualizerCard());
        body.addView(ytCtrl.buildCard());
        body.addView(buildActivePresetBanner());
        body.addView(buildPresetStrip());
        body.addView(buildQuickTiltCard());
        body.addView(buildEqCard());
        body.addView(buildMorphPadCard());
        body.addView(knobRackCtrl.buildCard());

        scroll.addView(body);
        mainCol.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBarPulseLine = new View(this);
        bottomBarPulseLine.setBackgroundColor(EqStore.withAlpha(EqStore.DEFAULT_ACCENT, 80));
        mainCol.addView(bottomBarPulseLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 2)));

        mainCol.addView(buildStickyBottomBar());

        rootFrame.addView(mainCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navCtrl.attachToRoot(rootFrame);
    }

    private View buildTopHeader() {
        topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.HORIZONTAL);
        topHeader.setGravity(Gravity.CENTER_VERTICAL);
        topHeader.setBackgroundColor(UiStyle.BG_AMOLED);
        topHeader.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 10), UiStyle.dp(this, 14), UiStyle.dp(this, 10));

        TextView menuBtn = new TextView(this);
        menuBtn.setText("≡");
        menuBtn.setTextColor(UiStyle.TEXT_PRIMARY);
        menuBtn.setTextSize(26f);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 2), UiStyle.dp(this, 12), UiStyle.dp(this, 2));
        menuBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            navCtrl.openDrawer();
        });
        topHeader.addView(menuBtn);

        titleTv = new TextView(this);
        titleTv.setText("KOKERQUALIZER");
        titleTv.setTextColor(UiStyle.TEXT_PRIMARY);
        titleTv.setTextSize(14.5f);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setLetterSpacing(0.05f);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        topHeader.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        bubblePill = UiStyle.makePill(this, "🫧", false, 0xFF39FF14);
        bubblePill.setTextSize(12f);
        bubblePill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        bubblePill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            toggleFloatingBubble();
        });
        LinearLayout.LayoutParams bpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bpLp.rightMargin = UiStyle.dp(this, 5);
        topHeader.addView(bubblePill, bpLp);

        statusPill = new TextView(this);
        statusPill.setText("● DSP+MBC");
        statusPill.setTextSize(10f);
        statusPill.setTypeface(null, android.graphics.Typeface.BOLD);
        statusPill.setSingleLine(true);
        statusPill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 5), UiStyle.dp(this, 8), UiStyle.dp(this, 5));
        statusPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            navCtrl.openPanel(MainNavigationController.PANEL_HEALTH);
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spLp.rightMargin = UiStyle.dp(this, 5);
        topHeader.addView(statusPill, spLp);

        TextView shutdownPill = UiStyle.makePill(this, "⏻", true, 0xFFFF5252);
        shutdownPill.setTextSize(13f);
        shutdownPill.setTypeface(null, android.graphics.Typeface.BOLD);
        shutdownPill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        shutdownPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            EqService.shutdownAppAndMusic(this);
        });
        topHeader.addView(shutdownPill);

        return topHeader;
    }

    private View buildVisualizerCard() {
        vizCard = new LinearLayout(this);
        vizCard.setOrientation(LinearLayout.VERTICAL);
        vizCardBg = UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 20));
        vizCard.setBackground(vizCardBg);
        vizCard.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 10), UiStyle.dp(this, 12), UiStyle.dp(this, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = new TextView(this);
        lbl.setText("SPECTRUM");
        lbl.setTextColor(UiStyle.TEXT_MUTED);
        lbl.setTextSize(11f);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        micHintBtn = new TextView(this);
        micHintBtn.setText("Enable Live Viz");
        micHintBtn.setTextColor(0xFFFFB300);
        micHintBtn.setTextSize(10.5f);
        micHintBtn.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        micHintBtn.setOnClickListener(v -> requestAudioPermission());
        row.addView(micHintBtn);

        String[] labels = new String[]{"Bars", "Wave", "VU", "Mirror"};
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            vizModeBtns[i] = UiStyle.makePill(this, labels[i], i == 0, 0xFF39FF14);
            vizModeBtns[i].setTextSize(10f);
            vizModeBtns[i].setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
            vizModeBtns[i].setOnClickListener(v -> {
                UiStyle.hapticTick(v, this);
                store.getSettings().visualizerMode = mode;
                store.save();
                refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = UiStyle.dp(this, 4);
            row.addView(vizModeBtns[i], lp);
        }
        vizCard.addView(row);

        visualizerView = new VisualizerView(this);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 80));
        vLp.topMargin = UiStyle.dp(this, 6);
        vizCard.addView(visualizerView, vLp);

        telemetryTv = new TextView(this);
        telemetryTv.setTextColor(UiStyle.TEXT_MUTED);
        telemetryTv.setTextSize(10.5f);
        telemetryTv.setSingleLine(true);
        telemetryTv.setEllipsize(TextUtils.TruncateAt.END);
        telemetryTv.setGravity(Gravity.CENTER);
        telemetryTv.setPadding(0, UiStyle.dp(this, 4), 0, 0);
        telemetryTv.setOnClickListener(v -> {
            if (engine.getClipCount() > 0) {
                UiStyle.hapticTick(v, this);
                if (engine.autoTrimPreamp()) {
                    EqService.startOrRefresh(this);
                    refreshAllUi(true);
                    Toast.makeText(this, "Auto-trimmed Preamp -2.0 dB to eliminate clipping!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        vizCard.addView(telemetryTv);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.bottomMargin = UiStyle.dp(this, 10);
        vizCard.setLayoutParams(cLp);
        return vizCard;
    }

    private View buildActivePresetBanner() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(this, 7);
        row.setLayoutParams(lp);

        activeColorDot = new View(this);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(UiStyle.dp(this, 20), UiStyle.dp(this, 20));
        dLp.rightMargin = UiStyle.dp(this, 8);
        activeColorDot.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            EqStore.EqPreset p = store.getActivePreset();
            if (p != null) DrawerPanels.showColorPicker(this, store, p, this);
        });
        row.addView(activeColorDot, dLp);

        activePresetNameTv = new TextView(this);
        activePresetNameTv.setTextColor(UiStyle.TEXT_PRIMARY);
        activePresetNameTv.setTextSize(15f);
        activePresetNameTv.setTypeface(null, android.graphics.Typeface.BOLD);
        activePresetNameTv.setSingleLine(true);
        activePresetNameTv.setEllipsize(TextUtils.TruncateAt.END);
        final float[] bannerDownX = new float[1];
        activePresetNameTv.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                bannerDownX[0] = ev.getRawX();
                return false;
            } else if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = ev.getRawX() - bannerDownX[0];
                if (Math.abs(dx) > UiStyle.dpf(this, 42f)) {
                    UiStyle.hapticTick(v, this);
                    store.cyclePreset(dx < 0 ? 1 : -1);
                    engine.setAbFlatCompare(false);
                    engine.triggerSmoothRamp();
                    EqService.startOrRefresh(this);
                    refreshAllUi(true);
                    return true;
                }
            }
            return false;
        });
        activePresetNameTv.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            EqStore.EqPreset p = store.getActivePreset();
            if (p != null) DrawerPanels.showRenameDialog(this, store, p, this);
        });
        row.addView(activePresetNameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        quickFavBtn = UiStyle.makePill(this, "★", false, 0xFF39FF14);
        quickFavBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            p.favorite = !p.favorite;
            store.addOrUpdatePreset(p);
            refreshAllUi(false);
        });
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fLp.rightMargin = UiStyle.dp(this, 6);
        row.addView(quickFavBtn, fLp);

        quickSaveBtn = UiStyle.makePill(this, "+ Save As", false, 0xFF39FF14);
        quickSaveBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            DrawerPanels.showSaveAsDialog(this, store, store.getActivePreset(), this);
        });
        row.addView(quickSaveBtn);
        return row;
    }

    private View buildPresetStrip() {
        presetChipScroll = new HorizontalScrollView(this);
        presetChipScroll.setHorizontalScrollBarEnabled(false);
        presetChipRow = new LinearLayout(this);
        presetChipRow.setOrientation(LinearLayout.HORIZONTAL);
        presetChipScroll.addView(presetChipRow);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(this, 10);
        presetChipScroll.setLayoutParams(lp);
        return presetChipScroll;
    }

    private View buildQuickTiltCard() {
        tiltCard = new LinearLayout(this);
        tiltCard.setOrientation(LinearLayout.VERTICAL);
        tiltCard.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 18)));
        tiltCard.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 8), UiStyle.dp(this, 12), UiStyle.dp(this, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout bCol = new LinearLayout(this);
        bCol.setOrientation(LinearLayout.VERTICAL);
        bassTiltLbl = new TextView(this);
        bassTiltLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        bassTiltLbl.setTextSize(11f);
        bassTiltLbl.setTypeface(null, android.graphics.Typeface.BOLD);
        bCol.addView(bassTiltLbl);
        bassTiltSeek = new SeekBar(this);
        bassTiltSeek.setMax(120);
        bassTiltSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                if (!fromUser) return;
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                if (store.getSettings().presetLocked) {
                    sb.setProgress(Math.round((p.bassTiltDb + 6f) * 10f));
                    showLockedToast();
                    return;
                }
                if (dragStartSnapshot == null) dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                p.bassTiltDb = Math.round(((prog - 60) / 10f) * 10f) / 10f;
                bassTiltLbl.setText(String.format(Locale.US, "Bass Tilt: %+.1f dB", p.bassTiltDb));
                engine.applyCurrent();
                updateTelemetryStrip();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                commitTiltDrag();
            }
        });
        bCol.addView(bassTiltSeek);

        LinearLayout tCol = new LinearLayout(this);
        tCol.setOrientation(LinearLayout.VERTICAL);
        trebleTiltLbl = new TextView(this);
        trebleTiltLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        trebleTiltLbl.setTextSize(11f);
        trebleTiltLbl.setTypeface(null, android.graphics.Typeface.BOLD);
        tCol.addView(trebleTiltLbl);
        trebleTiltSeek = new SeekBar(this);
        trebleTiltSeek.setMax(120);
        trebleTiltSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                if (!fromUser) return;
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                if (store.getSettings().presetLocked) {
                    sb.setProgress(Math.round((p.trebleTiltDb + 6f) * 10f));
                    showLockedToast();
                    return;
                }
                if (dragStartSnapshot == null) dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                p.trebleTiltDb = Math.round(((prog - 60) / 10f) * 10f) / 10f;
                trebleTiltLbl.setText(String.format(Locale.US, "Treble Tilt: %+.1f dB", p.trebleTiltDb));
                engine.applyCurrent();
                updateTelemetryStrip();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                commitTiltDrag();
            }
        });
        tCol.addView(trebleTiltSeek);

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.rightMargin = UiStyle.dp(this, 8);
        row.addView(bCol, colLp);
        row.addView(tCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tiltCard.addView(row);

        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tcLp.bottomMargin = UiStyle.dp(this, 10);
        tiltCard.setLayoutParams(tcLp);
        return tiltCard;
    }

    private void commitTiltDrag() {
        if (dragStartSnapshot != null) {
            undoStack.push(dragStartSnapshot);
            if (undoStack.size() > 30) undoStack.removeLast();
            redoStack.clear();
            dragStartSnapshot = null;
        }
        resetMorphPadBase();
        EqStore.EqPreset p = store.getActivePreset();
        if (p != null) {
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(this);
        }
        refreshUndoRedoPills();
    }

    private View buildEqCard() {
        eqCard = new LinearLayout(this);
        eqCard.setOrientation(LinearLayout.VERTICAL);
        eqCardBg = UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 22));
        eqCard.setBackground(eqCardBg);
        eqCard.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 12), UiStyle.dp(this, 10), UiStyle.dp(this, 10));

        HorizontalScrollView toolScroll = new HorizontalScrollView(this);
        toolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        int gap = UiStyle.dp(this, 6);

        btnBandCount = addToolbarPill(tools, "10-Band", gap, v -> {
            EqStore.Settings s = store.getSettings();
            s.bandMode = (s.bandMode + 1) % 3;
            store.save();
            engine.rebuildIfNeeded(true);
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });
        btnFreqLayout = addToolbarPill(tools, "ISO", gap, v -> {
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            pushUndo(p);
            p.carSubMode = !p.carSubMode;
            store.addOrUpdatePreset(p);
            engine.rebuildIfNeeded(true);
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });
        btnViewMode = addToolbarPill(tools, "Sliders", gap, v -> {
            store.getSettings().curveViewMode = !store.getSettings().curveViewMode;
            store.save();
            refreshAllUi(false);
        });
        btnBrush = addToolbarPill(tools, "Brush", gap, v -> {
            store.getSettings().brushMode = !store.getSettings().brushMode;
            store.save();
            refreshAllUi(false);
        });
        btnSmooth = addToolbarPill(tools, "∿ Smooth", gap, v -> {
            if (store.getSettings().presetLocked) { showLockedToast(); return; }
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            pushUndo(p);
            EqStore.smoothBands(p);
            resetMorphPadBase();
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(this);
            refreshAllUi(true);
        });
        btnTilt = addToolbarPill(tools, "⚖ Tilt", gap, v -> {
            store.getSettings().tiltBarVisible = !store.getSettings().tiltBarVisible;
            store.save();
            refreshAllUi(false);
        });
        btnMorph = addToolbarPill(tools, "✨ Morph", gap, v -> {
            store.getSettings().morphPadVisible = !store.getSettings().morphPadVisible;
            store.save();
            resetMorphPadBase();
            refreshAllUi(false);
        });
        btnZen = addToolbarPill(tools, "🧘 Zen", gap, v -> {
            store.getSettings().compactDeckMode = !store.getSettings().compactDeckMode;
            store.save();
            refreshAllUi(false);
        });
        btnHwy = addToolbarPill(tools, "🛣 HWY", gap, v -> {
            store.getSettings().highwayBoost = !store.getSettings().highwayBoost;
            store.save();
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });
        btnLock = addToolbarPill(tools, "Free", gap, v -> {
            store.getSettings().presetLocked = !store.getSettings().presetLocked;
            store.save();
            refreshAllUi(false);
        });
        btnUndo = addToolbarPill(tools, "↶", gap, v -> performUndo());
        btnRedo = addToolbarPill(tools, "↷", gap, v -> performRedo());
        btnReset = addToolbarPill(tools, "Flat", 0, v -> {
            if (store.getSettings().presetLocked) {
                showLockedToast();
                return;
            }
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            pushUndo(p);
            Arrays.fill(p.bands10, 0f);
            Arrays.fill(p.bands15, 0f);
            resetMorphPadBase();
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(this);
            refreshAllUi(true);
        });

        toolScroll.addView(tools);
        eqCard.addView(toolScroll);

        eqDeckView = new EqDeckView(this);
        eqDeckView.setListener(new EqDeckView.Listener() {
            @Override
            public void onBandChanging(int bandIndex, float gainDb) {
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                if (dragStartSnapshot == null) {
                    dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                }
                int bm = store.getSettings().bandMode;
                if (bm == EqStore.BANDS_15) {
                    if (bandIndex >= 0 && bandIndex < 15) {
                        p.bands15[bandIndex] = gainDb;
                        p.bands10 = EqStore.resample15To10(p.bands15);
                    }
                } else if (bm == EqStore.BANDS_10) {
                    if (bandIndex >= 0 && bandIndex < 10) {
                        p.bands10[bandIndex] = gainDb;
                        p.bands15 = EqStore.resample10To15(p.bands10);
                    }
                } else {
                    EqStore.apply5BandChange(p, bandIndex, gainDb);
                }
                engine.applyCurrent();
                visualizerView.setEqOverlayCurve(
                        bm == EqStore.BANDS_15 ? p.bands15 : p.bands10,
                        store.getSettings().vizEqOverlay);
                updateTelemetryStrip();
            }

            @Override
            public void onBandChangeFinished() {
                commitTiltDrag();
                updateTelemetryStrip();
            }

            @Override
            public void onBandLongPress(int bandIndex, String freqLabel, float currentDb) {
                if (dragStartSnapshot != null) {
                    EqStore.EqPreset p = store.getActivePreset();
                    if (p != null && p.id.equals(dragStartSnapshot.id)) {
                        p.bands10 = Arrays.copyOf(dragStartSnapshot.bands10, 10);
                        p.bands15 = Arrays.copyOf(dragStartSnapshot.bands15, 15);
                        engine.applyCurrent();
                    }
                    dragStartSnapshot = null;
                }
                EqStore.EqPreset cur = store.getActivePreset();
                MainDialogsHelper.showPrecisionFineTuneDialog(
                        MainActivity.this, store, engine, bandIndex, freqLabel, currentDb,
                        () -> {
                            if (cur != null) pushUndo(cur);
                            resetMorphPadBase();
                        },
                        () -> refreshAllUi(true));
            }

            @Override
            public void onLockedTouch() {
                showLockedToast();
            }
        });
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 276));
        eqLp.topMargin = UiStyle.dp(this, 8);
        eqCard.addView(eqDeckView, eqLp);

        TextView hint = new TextView(this);
        hint.setText("±18 dB  •  Double-tap to zero  •  Long-press any band for 0.1 dB fine tune");
        hint.setTextColor(UiStyle.TEXT_MUTED);
        hint.setTextSize(10.5f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, UiStyle.dp(this, 4), 0, 0);
        eqCard.addView(hint);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.bottomMargin = UiStyle.dp(this, 12);
        eqCard.setLayoutParams(cLp);
        return eqCard;
    }

    private View buildMorphPadCard() {
        morphCard = new LinearLayout(this);
        morphCard.setOrientation(LinearLayout.VERTICAL);
        morphCard.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 22)));
        morphCard.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 10), UiStyle.dp(this, 12), UiStyle.dp(this, 10));

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbl = new TextView(this);
        lbl.setText("✨ 2D X/Y SOUND MORPH PAD  (double-tap to center)");
        lbl.setTextColor(UiStyle.TEXT_MUTED);
        lbl.setTextSize(10.5f);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        hdr.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        morphCard.addView(hdr);

        morphPadView = new SoundMorphPadView(this);
        morphPadView.setListener(new SoundMorphPadView.Listener() {
            @Override
            public void onMorphChanging(float xNorm, float yNorm) {
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                if (morphBaseSnapshot == null) {
                    morphBaseSnapshot = p.copy(p.id, p.name, p.builtIn);
                }
                if (dragStartSnapshot == null) {
                    dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                }
                for (int i = 0; i < 10; i++) {
                    float f = i / 9f;
                    float subDelta = (-yNorm) * (1f - f) * 6.5f;
                    float vocDelta = (yNorm) * (float) Math.sin(f * Math.PI) * 5.0f;
                    float tiltDelta = xNorm * (f - 0.45f) * 6.5f;
                    p.bands10[i] = EqStore.clamp(
                            Math.round((morphBaseSnapshot.bands10[i] + subDelta + vocDelta + tiltDelta) * 2f) / 2f,
                            -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                }
                for (int i = 0; i < 15; i++) {
                    float f = i / 14f;
                    float subDelta = (-yNorm) * (1f - f) * 6.5f;
                    float vocDelta = (yNorm) * (float) Math.sin(f * Math.PI) * 5.0f;
                    float tiltDelta = xNorm * (f - 0.45f) * 6.5f;
                    p.bands15[i] = EqStore.clamp(
                            Math.round((morphBaseSnapshot.bands15[i] + subDelta + vocDelta + tiltDelta) * 2f) / 2f,
                            -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                }
                p.bassBoost = EqStore.clamp(morphBaseSnapshot.bassBoost + (-yNorm) * 25f, 0f, 100f);
                p.punchMbc = EqStore.clamp(morphBaseSnapshot.punchMbc + xNorm * 22f, 0f, 100f);

                engine.applyCurrent();
                EqStore.Settings s = store.getSettings();
                boolean act = s.eqEnabled && !engine.isAbFlatCompare();
                eqDeckView.setState(p.bands10, p.bands15, s.bandMode, p.carSubMode, s.curveViewMode,
                        s.brushMode, s.presetLocked, act, p.accentColor, false);
                visualizerView.setEqOverlayCurve(
                        s.bandMode == EqStore.BANDS_15 ? p.bands15 : p.bands10,
                        s.vizEqOverlay);
                knobRackCtrl.setBassAndPunchValues(p.bassBoost, p.punchMbc);
                updateTelemetryStrip();
            }

            @Override
            public void onMorphFinished(float xNorm, float yNorm) {
                if (dragStartSnapshot != null) {
                    undoStack.push(dragStartSnapshot);
                    if (undoStack.size() > 30) undoStack.removeLast();
                    redoStack.clear();
                    dragStartSnapshot = null;
                }
                EqStore.EqPreset p = store.getActivePreset();
                if (p != null) {
                    store.addOrUpdatePreset(p);
                    EqService.startOrRefresh(MainActivity.this);
                }
                refreshUndoRedoPills();
                updateTelemetryStrip();
            }

            @Override
            public void onLockedTouch() { showLockedToast(); }
        });
        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 168));
        mpLp.topMargin = UiStyle.dp(this, 6);
        morphCard.addView(morphPadView, mpLp);

        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mcLp.bottomMargin = UiStyle.dp(this, 12);
        morphCard.setLayoutParams(mcLp);
        return morphCard;
    }

    private View buildStickyBottomBar() {
        stickyBottomBar = new LinearLayout(this);
        stickyBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        stickyBottomBar.setBackgroundColor(0xFF0A0D14);
        stickyBottomBar.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 10), UiStyle.dp(this, 10), UiStyle.dp(this, 10));

        thumbPower = makeThumbBtn("● ON");
        thumbPower.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
            store.save();
            engine.triggerSmoothRamp();
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });

        thumbAb = makeThumbBtn("A/B");
        thumbAb.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            engine.cycleCompareMode();
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });
        thumbAb.setOnLongClickListener(v -> {
            UiStyle.hapticTick(v, this);
            MainDialogsHelper.showPickCompareSlotBDialog(this, store, engine, () -> refreshAllUi(false));
            return true;
        });

        thumbPreset = makeThumbBtn("PRESETS");
        thumbPreset.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            navCtrl.openPanel(MainNavigationController.PANEL_PRESETS);
        });

        thumbCar = makeThumbBtn("🚗 CAR");
        thumbCar.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            startActivity(new Intent(this, CarModeActivity.class));
        });

        int gap = UiStyle.dp(this, 7);
        addThumbToBar(stickyBottomBar, thumbPower, 1.1f, gap);
        addThumbToBar(stickyBottomBar, thumbAb, 0.9f, gap);
        addThumbToBar(stickyBottomBar, thumbPreset, 1.15f, gap);
        addThumbToBar(stickyBottomBar, thumbCar, 1.0f, 0);
        return stickyBottomBar;
    }

    private TextView makeThumbBtn(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        return tv;
    }

    private void addThumbToBar(LinearLayout bar, TextView tv, float weight, int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiStyle.dp(this, 46), weight);
        lp.rightMargin = rightMargin;
        bar.addView(tv, lp);
    }

    private TextView addToolbarPill(LinearLayout row, String label, int rightMargin, View.OnClickListener l) {
        TextView tv = UiStyle.makePill(this, label, false, 0xFF39FF14);
        tv.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = rightMargin;
        row.addView(tv, lp);
        return tv;
    }

    // =========================================================================
    // STATE REFRESH, TELEMETRY, REACTIVE GLOW & UNDO/REDO
    // =========================================================================
    private void applyReactiveGlow(float bassEnergy) {
        if (isFinishing() || isDestroyed()) return;
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null || !store.getSettings().eqEnabled || engine.isAbFlatCompare()) return;
        int accent = p.accentColor;
        int alpha = Math.min(240, Math.round(35 + bassEnergy * 195));
        float strokeDp = 1.0f + bassEnergy * 1.4f;
        if (store.getSettings().reactiveGlow) {
            if (vizCardBg != null) {
                vizCardBg.setStroke(UiStyle.dp(this, strokeDp), EqStore.withAlpha(accent, alpha));
            }
            if (eqCardBg != null) {
                eqCardBg.setStroke(UiStyle.dp(this, 1.0f + bassEnergy * 1.0f), EqStore.withAlpha(accent, Math.max(30, alpha - 25)));
            }
        }
        if (bottomBarPulseLine != null && store.getSettings().bottomBarPulse) {
            int barA = Math.min(255, Math.round(45 + bassEnergy * 210));
            bottomBarPulseLine.setBackgroundColor(EqStore.withAlpha(EqStore.DEFAULT_ACCENT, barA));
        }
    }

    private void updateTelemetryStrip() {
        if (telemetryTv == null) return;
        EqStore.Settings s = store.getSettings();
        String route = engine.getActiveOutputRouteLabel();
        float ag = engine.getLastAutoGainDb();
        float mbc = engine.getLastMbcAmount();
        String agStr = s.autoGain ? String.format(Locale.US, "AG %+.1fdB", ag) : "AG OFF";
        String mbcStr = mbc > 2f ? String.format(Locale.US, "MBC %d%%", Math.round(mbc)) : "MBC OFF";
        float loud = engine.getLastLoudnessBoostDb();
        String loudStr = (s.autoLoudness && loud > 0.2f) ? String.format(Locale.US, " • Loud +%.1fdB", loud) : "";
        int clips = engine.getClipCount();
        float pkDb = engine.getLastPeakDbfs();
        String clipStr = clips > 0
                ? String.format(Locale.US, " • ⚠ %d CLIPS (Tap Trim)", clips)
                : (pkDb > -55f ? String.format(Locale.US, " • Peak %.0fdB", pkDb) : " • Safe");
        telemetryTv.setText(route + " • " + agStr + " • " + mbcStr + loudStr + clipStr);
        telemetryTv.setTextColor(clips > 0 ? 0xFFFF5252 : UiStyle.TEXT_MUTED);
    }

    private void resetMorphPadBase() {
        morphBaseSnapshot = null;
        if (morphPadView != null) morphPadView.setCoords(0f, 0f);
    }

    private void refreshAllUi(boolean animate) {
        refreshAllUi(animate, true);
    }

    private void refreshAllUi(boolean animate, boolean rebuildOpenSubPanel) {
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        if (lastMorphPresetId != null && !lastMorphPresetId.equals(p.id)) {
            resetMorphPadBase();
        }
        lastMorphPresetId = p.id;
        int accent = p.accentColor;
        boolean active = s.eqEnabled && !engine.isAbFlatCompare();

        titleTv.setTextColor(accent);
        if (bubblePill != null) {
            boolean bOn = s.bubbleEnabled && BubbleOverlayManager.canDraw(this);
            bubblePill.setText(bOn ? "🫧 ON" : "🫧");
            UiStyle.stylePill(this, bubblePill, bOn, EqStore.DEFAULT_ACCENT);
        }
        updateStatusBadge(engine.getStatusText(), engine.isEngineActive());
        updateTelemetryStrip();
        ytCtrl.refreshCard();

        if (!s.reactiveGlow || !active) {
            if (vizCardBg != null) vizCardBg.setStroke(UiStyle.dp(this, 1), UiStyle.BORDER_SUBTLE);
            if (eqCardBg != null) eqCardBg.setStroke(UiStyle.dp(this, 1), UiStyle.BORDER_SUBTLE);
        }
        if (bottomBarPulseLine != null) {
            bottomBarPulseLine.setVisibility(s.bottomBarPulse ? View.VISIBLE : View.GONE);
            if (!active) bottomBarPulseLine.setBackgroundColor(0xFF1F2937);
        }

        if (vizCard != null) vizCard.setVisibility(s.compactDeckMode ? View.GONE : View.VISIBLE);
        knobRackCtrl.setVisible(!s.compactDeckMode);

        visualizerView.setMode(s.visualizerMode);
        visualizerView.setAccentColor(accent);
        visualizerView.setEqActive(active);
        visualizerView.setDecaySpeed(s.vizDecaySpeed);
        visualizerView.setGradientBars(s.vizGradientBars);
        visualizerView.setEqOverlayCurve(
                s.bandMode == EqStore.BANDS_15 ? p.bands15 : p.bands10,
                s.vizEqOverlay);
        for (int i = 0; i < 4; i++) {
            UiStyle.stylePill(this, vizModeBtns[i], s.visualizerMode == i, accent);
        }
        boolean hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        micHintBtn.setVisibility(hasMic ? View.GONE : View.VISIBLE);

        activeColorDot.setBackground(UiStyle.roundRect(accent, 0xCCFFFFFF, UiStyle.dp(this, 1.5f), UiStyle.dp(this, 10)));
        activePresetNameTv.setText(p.name);
        quickFavBtn.setText(p.favorite ? "★" : "☆");
        UiStyle.stylePill(this, quickFavBtn, p.favorite, accent);
        UiStyle.stylePill(this, quickSaveBtn, false, accent);

        presetChipRow.removeAllViews();
        List<EqStore.EqPreset> allPresets = store.getPresets();
        View[] selectedChipRef = new View[1];
        for (EqStore.EqPreset item : allPresets) {
            boolean sel = item.id.equals(p.id);
            TextView chip = UiStyle.makePill(this, (item.favorite ? "★ " : "") + item.name, sel, item.accentColor);
            chip.setOnClickListener(v -> {
                UiStyle.hapticTick(v, this);
                undoStack.clear();
                redoStack.clear();
                morphBaseSnapshot = null;
                if (morphPadView != null) morphPadView.setCoords(0f, 0f);
                store.setActivePreset(item.id);
                engine.setAbFlatCompare(false);
                engine.triggerSmoothRamp();
                EqService.startOrRefresh(this);
                refreshAllUi(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiStyle.dp(this, 7);
            presetChipRow.addView(chip, lp);
            if (sel) selectedChipRef[0] = chip;
        }
        if (selectedChipRef[0] != null) {
            presetChipScroll.post(() -> {
                int x = Math.max(0, selectedChipRef[0].getLeft() - UiStyle.dp(this, 24));
                presetChipScroll.smoothScrollTo(x, 0);
            });
        }

        String bmLbl = s.bandMode == EqStore.BANDS_15 ? "15B Pro" : (s.bandMode == EqStore.BANDS_10 ? "10-Band" : "5-Band");
        btnBandCount.setText(bmLbl);
        UiStyle.stylePill(this, btnBandCount, s.bandMode >= EqStore.BANDS_10, accent);

        btnFreqLayout.setVisibility(s.bandMode == EqStore.BANDS_15 ? View.GONE : View.VISIBLE);
        btnFreqLayout.setText(p.carSubMode ? "Car Sub" : "ISO");
        UiStyle.stylePill(this, btnFreqLayout, p.carSubMode, accent);

        btnViewMode.setText(s.curveViewMode ? "Curve" : "Sliders");
        UiStyle.stylePill(this, btnViewMode, s.curveViewMode, accent);

        btnBrush.setText(s.brushMode ? "Brush ON" : "Brush");
        UiStyle.stylePill(this, btnBrush, s.brushMode, accent);

        UiStyle.stylePill(this, btnSmooth, false, accent);

        btnTilt.setText(s.tiltBarVisible ? "⚖ Tilt ON" : "⚖ Tilt");
        UiStyle.stylePill(this, btnTilt, s.tiltBarVisible, accent);
        if (tiltCard != null) {
            tiltCard.setVisibility(s.tiltBarVisible ? View.VISIBLE : View.GONE);
            bassTiltLbl.setText(String.format(Locale.US, "Bass Tilt: %+.1f dB", p.bassTiltDb));
            bassTiltSeek.setProgress(Math.round((p.bassTiltDb + 6f) * 10f));
            UiStyle.tintSeekBar(bassTiltSeek, accent);
            trebleTiltLbl.setText(String.format(Locale.US, "Treble Tilt: %+.1f dB", p.trebleTiltDb));
            trebleTiltSeek.setProgress(Math.round((p.trebleTiltDb + 6f) * 10f));
            UiStyle.tintSeekBar(trebleTiltSeek, accent);
        }

        btnMorph.setText(s.morphPadVisible ? "✨ Morph ON" : "✨ Morph");
        UiStyle.stylePill(this, btnMorph, s.morphPadVisible, accent);
        if (morphCard != null) {
            morphCard.setVisibility(s.morphPadVisible ? View.VISIBLE : View.GONE);
            morphPadView.setStyle(accent, active, s.presetLocked);
        }

        btnZen.setText(s.compactDeckMode ? "🧘 Zen ON" : "🧘 Zen");
        UiStyle.stylePill(this, btnZen, s.compactDeckMode, accent);

        btnHwy.setText(s.highwayBoost ? "🛣 HWY ON" : "🛣 HWY");
        UiStyle.stylePill(this, btnHwy, s.highwayBoost, 0xFF00E676);

        btnLock.setText(s.presetLocked ? "🔒 Locked" : "🔓 Free");
        UiStyle.stylePill(this, btnLock, s.presetLocked, s.presetLocked ? 0xFFFFB300 : accent);

        refreshUndoRedoPills();
        UiStyle.stylePill(this, btnReset, false, accent);

        eqDeckView.setState(p.bands10, p.bands15, s.bandMode, p.carSubMode, s.curveViewMode,
                s.brushMode, s.presetLocked, active, accent, animate);

        knobRackCtrl.syncUi(p, s, accent, active, animate);

        thumbPower.setText(s.eqEnabled ? "● POWER ON" : "○ POWER OFF");
        thumbPower.setTextColor(s.eqEnabled ? accent : UiStyle.TEXT_SECONDARY);
        thumbPower.setBackground(UiStyle.roundRect(
                s.eqEnabled ? EqStore.withAlpha(accent, 42) : UiStyle.SURFACE_ELEV,
                s.eqEnabled ? accent : UiStyle.BORDER_SUBTLE,
                UiStyle.dp(this, 1.5f), UiStyle.dp(this, 14)));

        int cmpMode = engine.getCompareMode();
        boolean cmpOn = cmpMode != 0;
        if (cmpMode == 1) {
            thumbAb.setText("FLAT");
        } else if (cmpMode == 2) {
            EqStore.EqPreset bSlot = store.findPreset(s.comparePresetId);
            thumbAb.setText(bSlot != null ? ("B: " + bSlot.name) : "SLOT B");
        } else {
            thumbAb.setText(s.comparePresetId != null ? "A/B/C" : "A/B");
        }
        thumbAb.setTextColor(cmpOn ? 0xFFFFB300 : UiStyle.TEXT_PRIMARY);
        thumbAb.setBackground(UiStyle.roundRect(
                cmpOn ? 0x33FFB300 : UiStyle.SURFACE_ELEV,
                cmpOn ? 0xFFFFB300 : UiStyle.BORDER_SUBTLE,
                UiStyle.dp(this, 1.3f), UiStyle.dp(this, 14)));

        thumbPreset.setText(p.name);
        thumbPreset.setTextColor(UiStyle.TEXT_PRIMARY);
        thumbPreset.setBackground(UiStyle.roundRect(
                UiStyle.SURFACE_ELEV, EqStore.withAlpha(accent, 120),
                UiStyle.dp(this, 1.2f), UiStyle.dp(this, 14)));

        thumbCar.setTextColor(accent);
        thumbCar.setBackground(UiStyle.roundRect(
                EqStore.withAlpha(accent, 28), EqStore.withAlpha(accent, 170),
                UiStyle.dp(this, 1.3f), UiStyle.dp(this, 14)));

        if (rebuildOpenSubPanel && navCtrl.hasOpenPanel()) {
            navCtrl.renderOpenSubPanel();
        }
    }

    private void updateStatusBadge(String text, boolean isActive) {
        EqStore.EqPreset p = store.getActivePreset();
        int accent = p != null ? p.accentColor : 0xFF39FF14;
        int col = isActive ? accent : 0xFF94A3B8;
        statusPill.setText((isActive ? "● " : "○ ") + text);
        statusPill.setTextColor(col);
        statusPill.setBackground(UiStyle.roundRect(
                EqStore.withAlpha(col, 30), EqStore.withAlpha(col, 140),
                UiStyle.dp(this, 1), UiStyle.dp(this, 14)));
    }

    private void refreshUndoRedoPills() {
        EqStore.EqPreset p = store.getActivePreset();
        int accent = p != null ? p.accentColor : 0xFF39FF14;
        UiStyle.stylePill(this, btnUndo, !undoStack.isEmpty(), accent);
        UiStyle.stylePill(this, btnRedo, !redoStack.isEmpty(), accent);
    }

    private void pushUndo(EqStore.EqPreset p) {
        undoStack.push(p.copy(p.id, p.name, p.builtIn));
        if (undoStack.size() > 30) undoStack.removeLast();
        redoStack.clear();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) return;
        EqStore.EqPreset cur = store.getActivePreset();
        if (cur != null) redoStack.push(cur.copy(cur.id, cur.name, cur.builtIn));
        EqStore.EqPreset prev = undoStack.pop();
        resetMorphPadBase();
        store.addOrUpdatePreset(prev);
        EqService.startOrRefresh(this);
        refreshAllUi(true);
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        EqStore.EqPreset cur = store.getActivePreset();
        if (cur != null) undoStack.push(cur.copy(cur.id, cur.name, cur.builtIn));
        EqStore.EqPreset next = redoStack.pop();
        resetMorphPadBase();
        store.addOrUpdatePreset(next);
        EqService.startOrRefresh(this);
        refreshAllUi(true);
    }

    private void toggleFloatingBubble() {
        EqStore.Settings s = store.getSettings();
        if (!BubbleOverlayManager.canDraw(this)) {
            s.bubbleEnabled = true;
            store.save();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                Toast.makeText(this, "Turn on 'Allow display over other apps' for Kokerqualizer", Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this, "Enable 'Appear on top' in Android Settings → Apps → Kokerqualizer", Toast.LENGTH_LONG).show();
            }
            return;
        }
        s.bubbleEnabled = !s.bubbleEnabled;
        store.save();
        EqService.startOrRefresh(this);
        Toast.makeText(this, s.bubbleEnabled ? "Floating bubble companion ON" : "Floating bubble hidden", Toast.LENGTH_SHORT).show();
        refreshAllUi(false);
    }

    private void showLockedToast() {
        long now = System.currentTimeMillis();
        if (now - lastLockToastMs < 4000) return;
        lastLockToastMs = now;
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(this, "Preset locked — tap 🔒 Locked to unlock", Toast.LENGTH_SHORT);
        activeToast.show();
    }

    // =========================================================================
    // DrawerPanels.Host IMPLEMENTATION
    // =========================================================================
    @Override public void onStateMutated() { refreshAllUi(true); }
    @Override public void closePanel() { navCtrl.closePanel(); }
    @Override public SoundTestEngine getSoundTestEngine() { return soundTestEngine; }
    @Override public void onYtPlayTrack(int index) { ytCtrl.onPlayTrack(index); }
    @Override public void onYtPlayDirectTrack(YouTubeStore.Track track) { ytCtrl.onPlayDirectTrack(track); }
    @Override public void onYtSyncNow() { ytCtrl.syncActiveYouTubePlaylist(true); }
    @Override public void onYtPlaylistChanged() { ytCtrl.onPlaylistChanged(); }
    @Override public void onYtFullWebChanged(boolean fullWeb) { ytCtrl.onFullWebChanged(fullWeb); }

    @Override
    public void requestExportJson() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "kokerqualizer_backup.json");
        try {
            startActivityForResult(i, REQ_EXPORT_JSON);
        } catch (Throwable t) {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, store.exportAllJson());
                startActivity(Intent.createChooser(share, "Export Kokerqualizer Backup"));
            } catch (Throwable t2) {
                Toast.makeText(this, "No app available to export backup", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void requestImportJson() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT_JSON);
        } catch (Throwable t) {
            Toast.makeText(this, "File picker unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PERM);
        }
    }

    @Override
    public void requestAudioPermission() {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERM);
    }

    @Override
    public void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO_PERM && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            engine.startVisualizerIfPermitted();
        }
        refreshAllUi(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_JSON) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(store.exportAllJson().getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup saved!", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                Toast.makeText(this, "Export failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_IMPORT_JSON) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                        if (sb.length() > 2 * 1024 * 1024) {
                            throw new IllegalArgumentException("Backup file too large (>2 MB)");
                        }
                    }
                    int count = store.importJson(sb.toString());
                    EqService.startOrRefresh(this);
                    refreshAllUi(true);
                    Toast.makeText(this, "Imported " + count + " preset(s)!", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                Toast.makeText(this, "Import failed: invalid JSON", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
