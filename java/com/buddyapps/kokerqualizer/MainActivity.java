package com.buddyapps.kokerqualizer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.Random;

public class MainActivity extends Activity implements DrawerPanels.Host {
    private static final int REQ_AUDIO_PERM = 101;
    private static final int REQ_BT_PERM = 102;
    private static final int REQ_NOTIF_PERM = 103;
    private static final int REQ_EXPORT_JSON = 201;
    private static final int REQ_IMPORT_JSON = 202;

    private static final int PANEL_NONE = 0;
    private static final int PANEL_PRESETS = 1;
    private static final int PANEL_BT = 2;
    private static final int PANEL_TEST = 3;
    private static final int PANEL_SETTINGS = 4;
    private static final int PANEL_HEALTH = 5;
    private static final int PANEL_YOUTUBE = 6;
    private static final int PANEL_CALIBRATE = 7;

    private final int[] calStepRef = new int[]{0};
    private final float[] calOffsetsRef = new float[7];

    private EqStore store;
    private YouTubeStore ytStore;
    private EqEngine engine;
    private SoundTestEngine soundTestEngine;

    // Built-in YouTube Playlist Player
    private LinearLayout ytCard;
    private LinearLayout ytEmptyRow;
    private LinearLayout ytPlayerContent;
    private TextView ytHeaderTitle;
    private TextView ytSyncBtn;
    private TextView ytVideoToggleBtn;
    private TextView ytListBtn;
    private YouTubePlayerView ytPlayerView;
    private TextView ytTrackTitle;
    private TextView ytTrackSub;
    private TextView ytPrevBtn, ytPlayBtn, ytNextBtn, ytShuffleBtn;
    private final String[] ytFilterRef = new String[]{""};
    private final Handler ytAutoSyncHandler = new Handler(Looper.getMainLooper());
    private final Random ytRandom = new Random();
    private boolean ytSyncing = false;
    private final Runnable ytPeriodicSync = new Runnable() {
        @Override
        public void run() {
            if (ytStore != null && ytStore.getState().autoSyncOnStartup && ytStore.getActivePlaylist() != null) {
                syncActiveYouTubePlaylist(false);
            }
            ytAutoSyncHandler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    private FrameLayout rootFrame;
    private LinearLayout topHeader;
    private LinearLayout stickyBottomBar;
    private LinearLayout vizCard;
    private LinearLayout eqCard;
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
    private TextView[] vizModeBtns = new TextView[3];

    private View activeColorDot;
    private TextView activePresetNameTv;
    private TextView quickFavBtn;
    private TextView quickSaveBtn;
    private HorizontalScrollView presetChipScroll;
    private LinearLayout presetChipRow;

    private TextView btnBandCount, btnFreqLayout, btnViewMode, btnBrush, btnMorph, btnHwy, btnLock, btnUndo, btnRedo, btnReset;
    private EqDeckView eqDeckView;
    private LinearLayout morphCard;
    private SoundMorphPadView morphPadView;
    private EqStore.EqPreset morphBaseSnapshot;

    // Symmetrical 2x3 Hi-Fi Retro Rotary Knob Rack (6 Knobs!)
    private RotaryKnobView knobBass, knobPunch, knobPreamp, knobLimiter, knobVirt, knobBalance;
    private TextView[] bassStyleBtns = new TextView[3];
    private TextView[] reverbBtns = new TextView[5];
    private TextView autoGainPill, loudnessPill, monoPill;

    private TextView thumbPower, thumbAb, thumbPreset, thumbCar;

    private FrameLayout drawerScrim;
    private LinearLayout drawerSheet;
    private FrameLayout subPanelOverlay;
    private LinearLayout subPanelHeader;
    private TextView subPanelTitle;
    private ScrollView subPanelScroll;
    private int openSubPanel = PANEL_NONE;
    private final boolean[] favOnlyRef = new boolean[]{false};

    private long lastGlowMs = 0;
    private long lastLockToastMs = 0;
    private Toast activeToast;

    private final Deque<EqStore.EqPreset> undoStack = new ArrayDeque<>();
    private final Deque<EqStore.EqPreset> redoStack = new ArrayDeque<>();
    private EqStore.EqPreset dragStartSnapshot = null;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (dragStartSnapshot != null) {
                updateStatusBadge(engine.getStatusText(), engine.isEngineActive());
                updateTelemetryStrip();
            } else {
                refreshAllUi(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = EqStore.get(this);
        ytStore = YouTubeStore.get(this);
        engine = EqEngine.get(this);
        soundTestEngine = new SoundTestEngine();

        getWindow().setStatusBarColor(UiStyle.BG_AMOLED);
        getWindow().setNavigationBarColor(0xFF0A0D14);

        buildRootLayout();
        setContentView(rootFrame);

        UiStyle.applyEdgeToEdgeInsets(rootFrame, topHeader, UiStyle.dp(this, 10), stickyBottomBar, UiStyle.dp(this, 10));
        UiStyle.applyEdgeToEdgeInsets(subPanelOverlay, subPanelHeader, UiStyle.dp(this, 12), subPanelScroll, UiStyle.dp(this, 20));
        UiStyle.applyEdgeToEdgeInsets(drawerScrim, drawerSheet, UiStyle.dp(this, 18), drawerSheet, UiStyle.dp(this, 20));

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

        EqService.startOrRefresh(this);
        refreshAllUi(false);

        // Register Car Mode remote bridge + auto-load & live-sync YouTube playlist on startup!
        YouTubePlayerView.setActiveRemoteBridge(new YouTubePlayerView.RemoteBridge() {
            @Override public void playNext() { runOnUiThread(() -> stepYtTrack(1)); }
            @Override public void playPrev() { runOnUiThread(() -> stepYtTrack(-1)); }
            @Override public void togglePlayPause() { runOnUiThread(() -> { if (ytPlayerView != null) ytPlayerView.togglePlayPause(); }); }
            @Override public boolean isPlaying() { return ytPlayerView != null && ytPlayerView.isPlaying(); }
            @Override public String getNowPlayingLabel() {
                YouTubeStore.Track t = ytStore.getCurrentTrack();
                if (t == null) return null;
                return t.channel.isEmpty() ? ("♪ " + t.title) : ("♪ " + t.title + " — " + t.channel);
            }
        });

        YouTubeStore.SavedPlaylist startPl = ytStore.getActivePlaylist();
        YouTubeStore.YtState ys = ytStore.getState();
        if (startPl != null) {
            ytPlayerView.setFullWebMode(ys.fullWebMode);
            if (ys.fullWebMode && ys.lastWebUrl != null && !ys.lastWebUrl.isEmpty()) {
                ytPlayerView.loadDirectUrl(ys.lastWebUrl);
            } else {
                YouTubeStore.Track cur = ytStore.getCurrentTrack();
                if (cur != null) {
                    ytPlayerView.loadTrack(cur.videoId, startPl.playlistId, ys.autoPlayOnStartup);
                } else if (ys.fullWebMode) {
                    ytPlayerView.loadTrack(null, startPl.playlistId, ys.autoPlayOnStartup);
                }
            }
            if (ys.autoSyncOnStartup) {
                syncActiveYouTubePlaylist(false);
            }
        } else if (ys.lastWebUrl != null && !ys.lastWebUrl.isEmpty()) {
            ytPlayerView.setFullWebMode(true);
            ytPlayerView.loadDirectUrl(ys.lastWebUrl);
        }
        ytAutoSyncHandler.postDelayed(ytPeriodicSync, 15 * 60 * 1000L);
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
        engine.setVisualizerListener((bars, wave, l, r, clip, bassEnergy) -> {
            visualizerView.updateAudio(bars, wave, l, r, clip);
            if (store.getSettings().reactiveGlow) {
                long now = System.currentTimeMillis();
                if (now - lastGlowMs > 50) {
                    lastGlowMs = now;
                    runOnUiThread(() -> applyReactiveGlow(bassEnergy));
                }
            }
        });
        refreshAllUi(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        YouTubePlayerView.flushCookiesNow();
        try { unregisterReceiver(stateReceiver); } catch (Throwable ignored) {}
        engine.setVisualizerListener(null);
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
        ytAutoSyncHandler.removeCallbacksAndMessages(null);
        YouTubePlayerView.setActiveRemoteBridge(null);
        if (ytPlayerView != null) ytPlayerView.destroyPlayer();
        soundTestEngine.setFreqCallback(null);
        soundTestEngine.stop();
        if (Build.VERSION.SDK_INT >= 33 && backCallbackRegistered && backCallback != null) {
            try { getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback); } catch (Throwable ignored) {}
            backCallbackRegistered = false;
        }
        super.onDestroy();
    }

    private void syncBackCallback() {
        // Back callback stays registered so Back keeps the Activity & YouTube session alive via moveTaskToBack(true)
    }

    private void handleBackAction() {
        if (drawerScrim.getVisibility() == View.VISIBLE) {
            closeDrawer();
        } else if (openSubPanel != PANEL_NONE) {
            closePanel();
        } else if (ytPlayerView != null && ytPlayerView.canGoBackInWeb()) {
            ytPlayerView.goBackInWeb();
        } else {
            YouTubePlayerView.flushCookiesNow();
            // Move to background instead of destroying so YouTube playback, page & sign-in stay intact!
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
        body.addView(buildYouTubePlayerCard());
        body.addView(buildActivePresetBanner());
        body.addView(buildPresetStrip());
        body.addView(buildEqCard());
        body.addView(buildMorphPadCard());
        body.addView(buildKnobRackCard());

        scroll.addView(body);
        mainCol.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        mainCol.addView(buildStickyBottomBar());

        rootFrame.addView(mainCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildSubPanelOverlay();
        rootFrame.addView(subPanelOverlay);

        buildSlideOutDrawer();
        rootFrame.addView(drawerScrim);
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
            openDrawer();
        });
        topHeader.addView(menuBtn);

        titleTv = new TextView(this);
        titleTv.setText("KOKERQUALIZER");
        titleTv.setTextColor(UiStyle.TEXT_PRIMARY);
        titleTv.setTextSize(15.5f);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setLetterSpacing(0.06f);
        topHeader.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        bubblePill = UiStyle.makePill(this, "🫧", false, 0xFF39FF14);
        bubblePill.setTextSize(12f);
        bubblePill.setPadding(UiStyle.dp(this, 9), UiStyle.dp(this, 4), UiStyle.dp(this, 9), UiStyle.dp(this, 4));
        bubblePill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            toggleFloatingBubble();
        });
        LinearLayout.LayoutParams bpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bpLp.rightMargin = UiStyle.dp(this, 6);
        topHeader.addView(bubblePill, bpLp);

        statusPill = new TextView(this);
        statusPill.setText("● DSP+MBC");
        statusPill.setTextSize(10.5f);
        statusPill.setTypeface(null, android.graphics.Typeface.BOLD);
        statusPill.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 5), UiStyle.dp(this, 10), UiStyle.dp(this, 5));
        statusPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            openPanel(PANEL_HEALTH);
        });
        topHeader.addView(statusPill);

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

        String[] labels = new String[]{"Bars", "Wave", "VU"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            vizModeBtns[i] = UiStyle.makePill(this, labels[i], i == 0, 0xFF39FF14);
            vizModeBtns[i].setTextSize(10.5f);
            vizModeBtns[i].setPadding(UiStyle.dp(this, 9), UiStyle.dp(this, 4), UiStyle.dp(this, 9), UiStyle.dp(this, 4));
            vizModeBtns[i].setOnClickListener(v -> {
                UiStyle.hapticTick(v, this);
                store.getSettings().visualizerMode = mode;
                store.save();
                refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = UiStyle.dp(this, 5);
            row.addView(vizModeBtns[i], lp);
        }
        vizCard.addView(row);

        visualizerView = new VisualizerView(this);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 80));
        vLp.topMargin = UiStyle.dp(this, 6);
        vizCard.addView(visualizerView, vLp);

        // Live Headroom, Auto-Gain, MBC & Output Route Telemetry Strip
        telemetryTv = new TextView(this);
        telemetryTv.setTextColor(UiStyle.TEXT_MUTED);
        telemetryTv.setTextSize(10.5f);
        telemetryTv.setSingleLine(true);
        telemetryTv.setGravity(Gravity.CENTER);
        telemetryTv.setPadding(0, UiStyle.dp(this, 4), 0, 0);
        vizCard.addView(telemetryTv);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.bottomMargin = UiStyle.dp(this, 10);
        vizCard.setLayoutParams(cLp);
        return vizCard;
    }

    private View buildYouTubePlayerCard() {
        ytCard = new LinearLayout(this);
        ytCard.setOrientation(LinearLayout.VERTICAL);
        ytCard.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 20)));
        ytCard.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 10), UiStyle.dp(this, 12), UiStyle.dp(this, 10));

        // Compact prompt when no playlist added yet
        ytEmptyRow = new LinearLayout(this);
        ytEmptyRow.setOrientation(LinearLayout.HORIZONTAL);
        ytEmptyRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView emptyLbl = new TextView(this);
        emptyLbl.setText("🎵  Load a YouTube playlist inside Kokerqualizer");
        emptyLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        emptyLbl.setTextSize(12.5f);
        ytEmptyRow.addView(emptyLbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView addBtn = UiStyle.makePill(this, "+ Add", true, 0xFF39FF14);
        addBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            DrawerPanels.showAddYouTubePlaylistDialog(this, this);
        });
        ytEmptyRow.addView(addBtn);
        ytCard.addView(ytEmptyRow);

        // Full in-app YouTube Player content when a playlist exists
        ytPlayerContent = new LinearLayout(this);
        ytPlayerContent.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        ytHeaderTitle = new TextView(this);
        ytHeaderTitle.setText("🎵 YouTube Playlist");
        ytHeaderTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        ytHeaderTitle.setTextSize(12.5f);
        ytHeaderTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        ytHeaderTitle.setSingleLine(true);
        ytHeaderTitle.setEllipsize(TextUtils.TruncateAt.END);
        hdr.addView(ytHeaderTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ytSyncBtn = UiStyle.makePill(this, "↻", false, 0xFF39FF14);
        ytSyncBtn.setTextSize(11f);
        ytSyncBtn.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        ytSyncBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            syncActiveYouTubePlaylist(true);
        });
        LinearLayout.LayoutParams hGap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hGap.leftMargin = UiStyle.dp(this, 5);
        hdr.addView(ytSyncBtn, hGap);

        ytVideoToggleBtn = UiStyle.makePill(this, "🎬 Video", true, 0xFF39FF14);
        ytVideoToggleBtn.setTextSize(10.5f);
        ytVideoToggleBtn.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        ytVideoToggleBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            ytStore.getState().videoExpanded = !ytStore.getState().videoExpanded;
            ytStore.save();
            refreshYouTubeCard();
        });
        hdr.addView(ytVideoToggleBtn, hGap);

        ytListBtn = UiStyle.makePill(this, "≡ List", false, 0xFF39FF14);
        ytListBtn.setTextSize(10.5f);
        ytListBtn.setPadding(UiStyle.dp(this, 9), UiStyle.dp(this, 4), UiStyle.dp(this, 9), UiStyle.dp(this, 4));
        ytListBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            openPanel(PANEL_YOUTUBE);
        });
        hdr.addView(ytListBtn, hGap);
        ytPlayerContent.addView(hdr);

        ytPlayerView = new YouTubePlayerView(this);
        ytPlayerView.setListener(new YouTubePlayerView.Listener() {
            @Override
            public void onPlaybackStateChanged(boolean playing) {
                refreshYouTubeCard();
            }

            @Override
            public void onTrackEnded() {
                stepYtTrack(1);
            }

            @Override
            public void onFallbackToFullWeb(String reason) {
                Toast.makeText(MainActivity.this, reason, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onWebUrlVisited(String url) {
                if (ytStore != null && url != null) {
                    ytStore.getState().lastWebUrl = url;
                    ytStore.save();
                }
            }
        });
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 190));
        pvLp.topMargin = UiStyle.dp(this, 8);
        ytPlayerContent.addView(ytPlayerView, pvLp);

        ytTrackTitle = new TextView(this);
        ytTrackTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        ytTrackTitle.setTextSize(13.5f);
        ytTrackTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        ytTrackTitle.setSingleLine(true);
        ytTrackTitle.setEllipsize(TextUtils.TruncateAt.END);
        ytTrackTitle.setPadding(0, UiStyle.dp(this, 8), 0, 0);
        ytPlayerContent.addView(ytTrackTitle);

        ytTrackSub = new TextView(this);
        ytTrackSub.setTextColor(UiStyle.TEXT_MUTED);
        ytTrackSub.setTextSize(11f);
        ytTrackSub.setSingleLine(true);
        ytTrackSub.setEllipsize(TextUtils.TruncateAt.END);
        ytPlayerContent.addView(ytTrackSub);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = UiStyle.dp(this, 8);

        ytPrevBtn = UiStyle.makePill(this, "⏮ Prev", false, 0xFF39FF14);
        ytPrevBtn.setOnClickListener(v -> { UiStyle.hapticTick(v, this); stepYtTrack(-1); });

        ytPlayBtn = UiStyle.makePill(this, "▶ Play", true, 0xFF39FF14);
        ytPlayBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            ytPlayerView.togglePlayPause();
            refreshYouTubeCard();
        });

        ytNextBtn = UiStyle.makePill(this, "Next ⏭", false, 0xFF39FF14);
        ytNextBtn.setOnClickListener(v -> { UiStyle.hapticTick(v, this); stepYtTrack(1); });

        ytShuffleBtn = UiStyle.makePill(this, "🔀", false, 0xFF39FF14);
        ytShuffleBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            ytStore.getState().shuffle = !ytStore.getState().shuffle;
            ytStore.save();
            refreshYouTubeCard();
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = UiStyle.dp(this, 6);
        ctrlRow.addView(ytPrevBtn, lp1);
        ctrlRow.addView(ytPlayBtn, lp1);
        ctrlRow.addView(ytNextBtn, lp1);
        ctrlRow.addView(ytShuffleBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ytPlayerContent.addView(ctrlRow, crLp);

        ytCard.addView(ytPlayerContent);

        LinearLayout.LayoutParams ytLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ytLp.bottomMargin = UiStyle.dp(this, 10);
        ytCard.setLayoutParams(ytLp);
        return ytCard;
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
            s.bandMode = (s.bandMode + 1) % 3; // 5B -> 10B -> 15B Pro
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
        btnMorph = addToolbarPill(tools, "✨ Morph", gap, v -> {
            store.getSettings().morphPadVisible = !store.getSettings().morphPadVisible;
            store.save();
            morphBaseSnapshot = null;
            if (morphPadView != null) morphPadView.setCoords(0f, 0f);
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
                updateTelemetryStrip();
            }

            @Override
            public void onBandChangeFinished() {
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
            public void onBandLongPress(int bandIndex, String freqLabel, float currentDb) {
                dragStartSnapshot = null;
                showPrecisionFineTuneDialog(bandIndex, freqLabel, currentDb);
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
                    dragStartSnapshot = morphBaseSnapshot.copy(p.id, p.name, p.builtIn);
                }
                // Apply smooth 2D sound sculpt over base snapshot:
                // yNorm < 0 => Deep Sub boost; yNorm > 0 => Crystal Vocal presence
                // xNorm < 0 => Warm Analog rolloff; xNorm > 0 => Punchy Bright & MBC punch
                for (int i = 0; i < 10; i++) {
                    float f = i / 9f; // 0 = sub, 1 = air
                    float subDelta = (-yNorm) * (1f - f) * 6.5f;
                    float vocDelta = (yNorm) * (float) Math.sin(f * Math.PI) * 5.0f;
                    float tiltDelta = xNorm * (f - 0.45f) * 6.5f;
                    p.bands10[i] = EqStore.clamp(
                            Math.round((morphBaseSnapshot.bands10[i] + subDelta + vocDelta + tiltDelta) * 2f) / 2f,
                            -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                }
                p.bands15 = EqStore.resample10To15(p.bands10);
                p.bassBoost = EqStore.clamp(morphBaseSnapshot.bassBoost + (-yNorm) * 25f, 0f, 100f);
                p.punchMbc = EqStore.clamp(morphBaseSnapshot.punchMbc + xNorm * 22f, 0f, 100f);

                engine.applyCurrent();
                EqStore.Settings s = store.getSettings();
                boolean act = s.eqEnabled && !engine.isAbFlatCompare();
                eqDeckView.setState(p.bands10, p.bands15, s.bandMode, p.carSubMode, s.curveViewMode,
                        s.brushMode, s.presetLocked, act, p.accentColor, false);
                knobBass.setValue(p.bassBoost, false);
                knobPunch.setValue(p.punchMbc, false);
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

    private View buildKnobRackCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 22)));
        card.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 14), UiStyle.dp(this, 12), UiStyle.dp(this, 14));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView rackTitle = new TextView(this);
        rackTitle.setText("MASTERING RACK");
        rackTitle.setTextColor(UiStyle.TEXT_MUTED);
        rackTitle.setTextSize(11f);
        rackTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(rackTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        loudnessPill = UiStyle.makePill(this, "Loudness", false, EqStore.DEFAULT_ACCENT);
        loudnessPill.setTextSize(10f);
        loudnessPill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        loudnessPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            store.getSettings().autoLoudness = !store.getSettings().autoLoudness;
            store.save();
            engine.applyCurrent();
            refreshAllUi(false);
        });
        LinearLayout.LayoutParams pGap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pGap.rightMargin = UiStyle.dp(this, 5);
        topRow.addView(loudnessPill, pGap);

        monoPill = UiStyle.makePill(this, "Mono", false, EqStore.DEFAULT_ACCENT);
        monoPill.setTextSize(10f);
        monoPill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        monoPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            store.getSettings().monoAudio = !store.getSettings().monoAudio;
            store.save();
            engine.applyCurrent();
            refreshAllUi(false);
        });
        topRow.addView(monoPill, pGap);

        autoGainPill = UiStyle.makePill(this, "Auto-Gain", true, EqStore.DEFAULT_ACCENT);
        autoGainPill.setTextSize(10f);
        autoGainPill.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 4), UiStyle.dp(this, 8), UiStyle.dp(this, 4));
        autoGainPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            store.getSettings().autoGain = !store.getSettings().autoGain;
            store.save();
            engine.applyCurrent();
            refreshAllUi(false);
        });
        topRow.addView(autoGainPill);
        card.addView(topRow);

        LinearLayout bassRow = new LinearLayout(this);
        bassRow.setOrientation(LinearLayout.HORIZONTAL);
        bassRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = UiStyle.dp(this, 10);
        brLp.bottomMargin = UiStyle.dp(this, 8);

        TextView bLbl = new TextView(this);
        bLbl.setText("Bass Style:");
        bLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        bLbl.setTextSize(12f);
        LinearLayout.LayoutParams blLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blLp.rightMargin = UiStyle.dp(this, 8);
        bassRow.addView(bLbl, blLp);

        String[] styles = new String[]{"Tight", "Deep", "Boomy"};
        for (int i = 0; i < 3; i++) {
            final int st = i;
            bassStyleBtns[i] = UiStyle.makePill(this, styles[i], false, 0xFF39FF14);
            bassStyleBtns[i].setTextSize(11f);
            bassStyleBtns[i].setOnClickListener(v -> {
                if (store.getSettings().presetLocked) { showLockedToast(); return; }
                UiStyle.hapticTick(v, this);
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                pushUndo(p);
                p.bassStyle = st;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(this);
                refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(this, 6);
            bassRow.addView(bassStyleBtns[i], lp);
        }
        card.addView(bassRow, brLp);

        // Acoustic Space Reverb Row (saved per preset!)
        LinearLayout revRow = new LinearLayout(this);
        revRow.setOrientation(LinearLayout.HORIZONTAL);
        revRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rrLp.bottomMargin = UiStyle.dp(this, 8);

        TextView rLbl = new TextView(this);
        rLbl.setText("Space:");
        rLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        rLbl.setTextSize(11.5f);
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlLp.rightMargin = UiStyle.dp(this, 6);
        revRow.addView(rLbl, rlLp);

        String[] revLabels = new String[]{"Off", "Studio", "Club", "Hall", "Cabin"};
        for (int i = 0; i < 5; i++) {
            final int rm = i;
            reverbBtns[i] = UiStyle.makePill(this, revLabels[i], i == 0, EqStore.DEFAULT_ACCENT);
            reverbBtns[i].setTextSize(10f);
            reverbBtns[i].setPadding(UiStyle.dp(this, 6), UiStyle.dp(this, 4), UiStyle.dp(this, 6), UiStyle.dp(this, 4));
            reverbBtns[i].setOnClickListener(v -> {
                if (store.getSettings().presetLocked) { showLockedToast(); return; }
                UiStyle.hapticTick(v, this);
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                pushUndo(p);
                p.reverbMode = rm;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(this);
                refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 4) lp.rightMargin = UiStyle.dp(this, 4);
            revRow.addView(reverbBtns[i], lp);
        }
        card.addView(revRow, rrLp);

        // Symmetrical 2x3 Hi-Fi Knob Grid (6 Big Knobs!)
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        int knobH = UiStyle.dp(this, 140);

        knobBass = makeKnob("BASS BOOST", 0f, 100f, 0f, false, v -> Math.round(v) + "%",
                v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.bassBoost = v; });
        knobPunch = makeKnob("PUNCH (3-BAND MBC)", 0f, 100f, 25f, false, v -> v < 3f ? "OFF" : (Math.round(v) + "%"),
                v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.punchMbc = v; });
        knobPreamp = makeKnob("PREAMP", -12f, 12f, 0f, true, v -> String.format(Locale.US, "%+.1f dB", v),
                v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.preampDb = v; });
        knobLimiter = makeKnob("LIMITER", 0f, 100f, 65f, false, v -> v < 5f ? "OFF" : (v > 75f ? "HARD " + Math.round(v) + "%" : "SOFT " + Math.round(v) + "%"),
                v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.limiter = v; });
        knobVirt = makeKnob("3D VIRTUALIZER", 0f, 100f, 0f, false, v -> Math.round(v) + "%",
                v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.virtualizer = v; });
        knobBalance = makeKnob("L / R BALANCE", -100f, 100f, 0f, true, v -> {
            int r = Math.round(v);
            if (Math.abs(r) <= 1) return "CENTER";
            return r < 0 ? ("L " + (-r) + "%") : ("R " + r + "%");
        }, v -> { EqStore.EqPreset p = store.getActivePreset(); if (p != null) p.balance = v; });

        addKnobToGrid(grid, knobBass, knobH);
        addKnobToGrid(grid, knobPunch, knobH);
        addKnobToGrid(grid, knobPreamp, knobH);
        addKnobToGrid(grid, knobLimiter, knobH);
        addKnobToGrid(grid, knobVirt, knobH);
        addKnobToGrid(grid, knobBalance, knobH);

        card.addView(grid);
        return card;
    }

    private interface KnobSetter { void set(float v); }

    private RotaryKnobView makeKnob(String title, float min, float max, float def,
                                    boolean bipolar, RotaryKnobView.Formatter fmt, KnobSetter setter) {
        RotaryKnobView k = new RotaryKnobView(this);
        k.configure(title, min, max, def, bipolar, fmt, new RotaryKnobView.Listener() {
            @Override public void onValueChanging(float value) {
                EqStore.EqPreset p = store.getActivePreset();
                if (p != null && dragStartSnapshot == null) {
                    dragStartSnapshot = p.copy(p.id, p.name, p.builtIn);
                }
                setter.set(value);
                engine.applyCurrent();
                updateTelemetryStrip();
            }
            @Override public void onValueFinished(float value) {
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
            @Override public void onLockedTouch() { showLockedToast(); }
        });
        return k;
    }

    private void addKnobToGrid(GridLayout grid, View knob, int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = h;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(UiStyle.dp(this, 4), UiStyle.dp(this, 4), UiStyle.dp(this, 4), UiStyle.dp(this, 4));
        grid.addView(knob, lp);
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
            EqService.startOrRefresh(this);
            refreshAllUi(false);
        });

        thumbAb = makeThumbBtn("A/B");
        thumbAb.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            engine.setAbFlatCompare(!engine.isAbFlatCompare());
            refreshAllUi(false);
        });

        thumbPreset = makeThumbBtn("PRESETS");
        thumbPreset.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            openPanel(PANEL_PRESETS);
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
    // SLIDE-OUT DRAWER & FULL-SCREEN SUB-PANEL OVERLAY
    // =========================================================================
    private void buildSubPanelOverlay() {
        subPanelOverlay = new FrameLayout(this);
        subPanelOverlay.setBackgroundColor(UiStyle.BG_AMOLED);
        subPanelOverlay.setVisibility(View.GONE);
        subPanelOverlay.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        subPanelHeader = new LinearLayout(this);
        subPanelHeader.setOrientation(LinearLayout.HORIZONTAL);
        subPanelHeader.setGravity(Gravity.CENTER_VERTICAL);
        subPanelHeader.setPadding(UiStyle.dp(this, 14), UiStyle.dp(this, 12), UiStyle.dp(this, 14), UiStyle.dp(this, 12));

        TextView backBtn = UiStyle.makePill(this, "◀ Back to Deck", false, 0xFF39FF14);
        backBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            closePanel();
        });
        subPanelHeader.addView(backBtn);

        subPanelTitle = new TextView(this);
        subPanelTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        subPanelTitle.setTextSize(16.5f);
        subPanelTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        subPanelTitle.setGravity(Gravity.END);
        subPanelHeader.addView(subPanelTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(subPanelHeader);

        subPanelScroll = new ScrollView(this);
        subPanelScroll.setClipToPadding(false);
        subPanelScroll.setPadding(UiStyle.dp(this, 14), UiStyle.dp(this, 4), UiStyle.dp(this, 14), UiStyle.dp(this, 24));
        col.addView(subPanelScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        subPanelOverlay.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildSlideOutDrawer() {
        drawerScrim = new FrameLayout(this);
        drawerScrim.setBackgroundColor(0xAA000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());

        drawerSheet = new LinearLayout(this);
        drawerSheet.setOrientation(LinearLayout.VERTICAL);
        drawerSheet.setBackgroundColor(0xFF0B0E16);
        drawerSheet.setPadding(UiStyle.dp(this, 18), UiStyle.dp(this, 24), UiStyle.dp(this, 18), UiStyle.dp(this, 24));
        drawerSheet.setClickable(true);

        TextView brand = new TextView(this);
        brand.setText("KOKERQUALIZER");
        brand.setTextColor(0xFF39FF14);
        brand.setTextSize(19f);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setLetterSpacing(0.1f);
        drawerSheet.addView(brand);

        TextView sub = new TextView(this);
        sub.setText("v1.6 • Pro 15-Band DSP + Morph Pad");
        sub.setTextColor(UiStyle.TEXT_MUTED);
        sub.setTextSize(12f);
        sub.setPadding(0, UiStyle.dp(this, 2), 0, UiStyle.dp(this, 18));
        drawerSheet.addView(sub);

        addDrawerItem("🎛  Main Equalizer Deck", () -> { closeDrawer(); closePanel(); });
        addDrawerItem("🫧  Toggle Floating 3D Orb", () -> { closeDrawer(); toggleFloatingBubble(); });
        addDrawerItem("✨  Toggle 2D Sound Morph Pad", () -> {
            closeDrawer();
            closePanel();
            store.getSettings().morphPadVisible = !store.getSettings().morphPadVisible;
            store.save();
            morphBaseSnapshot = null;
            refreshAllUi(false);
        });
        addDrawerItem("🎯  Speaker Calibration Wizard", () -> { closeDrawer(); openPanel(PANEL_CALIBRATE); });
        addDrawerItem("🎵  YouTube Playlists (In-App)", () -> { closeDrawer(); openPanel(PANEL_YOUTUBE); });
        addDrawerItem("⭐  Preset Library & Backup", () -> { closeDrawer(); openPanel(PANEL_PRESETS); });
        addDrawerItem("📡  Bluetooth & Wired Auto-Switch", () -> { closeDrawer(); openPanel(PANEL_BT); });
        addDrawerItem("🔊  Sound & Sub Test Suite", () -> { closeDrawer(); openPanel(PANEL_TEST); });
        addDrawerItem("🚗  Launch Car Mode Dash", () -> {
            closeDrawer();
            startActivity(new Intent(this, CarModeActivity.class));
        });
        addDrawerItem("🩺  Audio Health Check", () -> { closeDrawer(); openPanel(PANEL_HEALTH); });
        addDrawerItem("⚙  Settings", () -> { closeDrawer(); openPanel(PANEL_SETTINGS); });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UiStyle.dp(this, 290), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawerScrim.addView(drawerSheet, lp);
    }

    private void addDrawerItem(String label, Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(UiStyle.TEXT_PRIMARY);
        tv.setTextSize(14.5f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(UiStyle.dp(this, 14), UiStyle.dp(this, 13), UiStyle.dp(this, 14), UiStyle.dp(this, 13));
        tv.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(this, 1), UiStyle.dp(this, 14)));
        tv.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(this, 9);
        drawerSheet.addView(tv, lp);
    }

    private void openDrawer() {
        drawerScrim.setVisibility(View.VISIBLE);
        syncBackCallback();
        drawerSheet.setTranslationX(-UiStyle.dp(this, 300));
        drawerSheet.animate().translationX(0).setDuration(190).start();
    }

    private void closeDrawer() {
        drawerSheet.animate().translationX(-UiStyle.dp(this, 300)).setDuration(150)
                .withEndAction(() -> {
                    drawerScrim.setVisibility(View.GONE);
                    syncBackCallback();
                }).start();
    }

    private void openPanel(int panel) {
        openSubPanel = panel;
        subPanelOverlay.setVisibility(View.VISIBLE);
        subPanelOverlay.requestApplyInsets();
        syncBackCallback();
        subPanelScroll.scrollTo(0, 0);
        renderOpenSubPanel();
    }

    private void renderOpenSubPanel() {
        if (openSubPanel == PANEL_NONE) return;
        final int savedY = subPanelScroll.getScrollY();
        subPanelScroll.removeAllViews();
        if (openSubPanel == PANEL_PRESETS) {
            subPanelTitle.setText("Preset Library");
            subPanelScroll.addView(DrawerPanels.buildPresetsPanel(this, this, favOnlyRef));
        } else if (openSubPanel == PANEL_BT) {
            subPanelTitle.setText("Device Automation");
            subPanelScroll.addView(DrawerPanels.buildBluetoothPanel(this, this));
        } else if (openSubPanel == PANEL_TEST) {
            subPanelTitle.setText("Sound Test Suite");
            subPanelScroll.addView(DrawerPanels.buildSoundTestPanel(this, this));
        } else if (openSubPanel == PANEL_HEALTH) {
            subPanelTitle.setText("Audio Health Check");
            subPanelScroll.addView(DrawerPanels.buildHealthPanel(this, this));
        } else if (openSubPanel == PANEL_YOUTUBE) {
            subPanelTitle.setText("YouTube Playlists");
            subPanelScroll.addView(DrawerPanels.buildYouTubePanel(this, this, ytFilterRef));
        } else if (openSubPanel == PANEL_CALIBRATE) {
            subPanelTitle.setText("Speaker Calibration");
            subPanelScroll.addView(DrawerPanels.buildCalibrationPanel(this, this, calStepRef, calOffsetsRef));
        } else if (openSubPanel == PANEL_SETTINGS) {
            subPanelTitle.setText("Settings");
            subPanelScroll.addView(DrawerPanels.buildSettingsPanel(this, this));
        }
        if (savedY > 0) {
            subPanelScroll.post(() -> subPanelScroll.scrollTo(0, savedY));
        }
    }

    // =========================================================================
    // STATE REFRESH, TELEMETRY, REACTIVE GLOW & UNDO/REDO
    // =========================================================================
    private void applyReactiveGlow(float bassEnergy) {
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null || !store.getSettings().eqEnabled || engine.isAbFlatCompare()) return;
        int accent = p.accentColor;
        int alpha = Math.min(240, Math.round(35 + bassEnergy * 195));
        float strokeDp = 1.0f + bassEnergy * 1.4f;
        if (vizCardBg != null) {
            vizCardBg.setStroke(UiStyle.dp(this, strokeDp), EqStore.withAlpha(accent, alpha));
        }
        if (eqCardBg != null) {
            eqCardBg.setStroke(UiStyle.dp(this, 1.0f + bassEnergy * 1.0f), EqStore.withAlpha(accent, Math.max(30, alpha - 25)));
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
        String headroom = (engine.getLastPeakBoostDb() > 14f && !s.autoGain) ? "Hot" : "Safe";
        telemetryTv.setText(route + " • " + agStr + " • " + mbcStr + loudStr + " • " + headroom);
    }

    private void refreshAllUi(boolean animate) {
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int accent = p.accentColor;
        boolean active = s.eqEnabled && !engine.isAbFlatCompare();

        titleTv.setTextColor(accent);
        if (bubblePill != null) {
            boolean bOn = s.bubbleEnabled && BubbleOverlayManager.canDraw(this);
            bubblePill.setText(bOn ? "🫧 ON" : "🫧");
            UiStyle.stylePill(this, bubblePill, bOn, accent);
        }
        updateStatusBadge(engine.getStatusText(), engine.isEngineActive());
        updateTelemetryStrip();
        refreshYouTubeCard();

        if (!s.reactiveGlow || !active) {
            if (vizCardBg != null) vizCardBg.setStroke(UiStyle.dp(this, 1), UiStyle.BORDER_SUBTLE);
            if (eqCardBg != null) eqCardBg.setStroke(UiStyle.dp(this, 1), UiStyle.BORDER_SUBTLE);
        }

        visualizerView.setMode(s.visualizerMode);
        visualizerView.setAccentColor(accent);
        visualizerView.setEqActive(active);
        for (int i = 0; i < 3; i++) {
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

        btnMorph.setText(s.morphPadVisible ? "✨ Morph ON" : "✨ Morph");
        UiStyle.stylePill(this, btnMorph, s.morphPadVisible, accent);
        if (morphCard != null) {
            morphCard.setVisibility(s.morphPadVisible ? View.VISIBLE : View.GONE);
            morphPadView.setStyle(accent, active, s.presetLocked);
        }

        btnHwy.setText(s.highwayBoost ? "🛣 HWY ON" : "🛣 HWY");
        UiStyle.stylePill(this, btnHwy, s.highwayBoost, 0xFF00E676);

        btnLock.setText(s.presetLocked ? "🔒 Locked" : "🔓 Free");
        UiStyle.stylePill(this, btnLock, s.presetLocked, s.presetLocked ? 0xFFFFB300 : accent);

        refreshUndoRedoPills();
        UiStyle.stylePill(this, btnReset, false, accent);

        eqDeckView.setState(p.bands10, p.bands15, s.bandMode, p.carSubMode, s.curveViewMode,
                s.brushMode, s.presetLocked, active, accent, animate);

        UiStyle.stylePill(this, loudnessPill, s.autoLoudness, accent);
        UiStyle.stylePill(this, monoPill, s.monoAudio, 0xFFFFB300);
        UiStyle.stylePill(this, autoGainPill, s.autoGain, accent);
        for (int i = 0; i < 3; i++) {
            UiStyle.stylePill(this, bassStyleBtns[i], p.bassStyle == i, accent);
        }
        for (int i = 0; i < 5; i++) {
            UiStyle.stylePill(this, reverbBtns[i], p.reverbMode == i, accent);
        }

        knobBass.setValue(p.bassBoost, animate);
        knobBass.setStyle(accent, active, s.presetLocked);
        knobPunch.setValue(p.punchMbc, animate);
        knobPunch.setStyle(accent, active, s.presetLocked);
        knobPreamp.setValue(p.preampDb, animate);
        knobPreamp.setStyle(accent, active, s.presetLocked);
        knobLimiter.setValue(p.limiter, animate);
        knobLimiter.setStyle(accent, active, s.presetLocked);
        knobVirt.setValue(p.virtualizer, animate);
        knobVirt.setStyle(accent, active, s.presetLocked);
        knobBalance.setValue(p.balance, animate);
        knobBalance.setStyle(accent, active, s.presetLocked);

        thumbPower.setText(s.eqEnabled ? "● POWER ON" : "○ POWER OFF");
        thumbPower.setTextColor(s.eqEnabled ? accent : UiStyle.TEXT_SECONDARY);
        thumbPower.setBackground(UiStyle.roundRect(
                s.eqEnabled ? EqStore.withAlpha(accent, 42) : UiStyle.SURFACE_ELEV,
                s.eqEnabled ? accent : UiStyle.BORDER_SUBTLE,
                UiStyle.dp(this, 1.5f), UiStyle.dp(this, 14)));

        boolean ab = engine.isAbFlatCompare();
        thumbAb.setText(ab ? "A/B FLAT" : "A/B");
        thumbAb.setTextColor(ab ? 0xFFFFB300 : UiStyle.TEXT_PRIMARY);
        thumbAb.setBackground(UiStyle.roundRect(
                ab ? 0x33FFB300 : UiStyle.SURFACE_ELEV,
                ab ? 0xFFFFB300 : UiStyle.BORDER_SUBTLE,
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

        if (openSubPanel != PANEL_NONE) {
            renderOpenSubPanel();
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
        store.addOrUpdatePreset(prev);
        EqService.startOrRefresh(this);
        refreshAllUi(true);
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        EqStore.EqPreset cur = store.getActivePreset();
        if (cur != null) undoStack.push(cur.copy(cur.id, cur.name, cur.builtIn));
        EqStore.EqPreset next = redoStack.pop();
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

    private void showPrecisionFineTuneDialog(int bandIndex, String freqLabel, float currentDb) {
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int accent = p.accentColor;
        final float[] val = new float[]{Math.round(currentDb * 10f) / 10f};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(this, 16);
        box.setPadding(pad, pad, pad, pad);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setText(String.format(Locale.US, "%.1f", val[0]));
        et.setTextColor(accent);
        et.setTextSize(22f);
        et.setGravity(Gravity.CENTER);
        box.addView(et);

        LinearLayout nudgeRow = new LinearLayout(this);
        nudgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nrLp.topMargin = UiStyle.dp(this, 10);

        String[] labels = new String[]{"-1.0", "-0.1", "0.0", "+0.1", "+1.0"};
        float[] deltas = new float[]{-1.0f, -0.1f, 0f, 0.1f, 1.0f};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView btn = UiStyle.makePill(this, labels[i], i == 2, accent);
            btn.setTextSize(12f);
            btn.setOnClickListener(v -> {
                UiStyle.hapticTick(v, this);
                if (idx == 2) val[0] = 0f;
                else val[0] = EqStore.clamp(Math.round((val[0] + deltas[idx]) * 10f) / 10f, -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                et.setText(String.format(Locale.US, "%.1f", val[0]));
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 4) blp.rightMargin = UiStyle.dp(this, 5);
            nudgeRow.addView(btn, blp);
        }
        box.addView(nudgeRow, nrLp);

        new AlertDialog.Builder(this)
                .setTitle("Fine Tune • " + freqLabel + " (±18.0 dB)")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    float parsed = val[0];
                    try { parsed = Float.parseFloat(et.getText().toString().trim()); }
                    catch (Throwable ignored) {}
                    parsed = EqStore.clamp(Math.round(parsed * 10f) / 10f, -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                    pushUndo(p);
                    int bm = store.getSettings().bandMode;
                    if (bm == EqStore.BANDS_15 && bandIndex < 15) {
                        p.bands15[bandIndex] = parsed;
                        p.bands10 = EqStore.resample15To10(p.bands15);
                    } else if (bm == EqStore.BANDS_10 && bandIndex < 10) {
                        p.bands10[bandIndex] = parsed;
                        p.bands15 = EqStore.resample10To15(p.bands10);
                    } else {
                        EqStore.apply5BandChange(p, bandIndex, parsed);
                    }
                    store.addOrUpdatePreset(p);
                    EqService.startOrRefresh(this);
                    refreshAllUi(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLockedToast() {
        long now = System.currentTimeMillis();
        if (now - lastLockToastMs < 4000) return;
        lastLockToastMs = now;
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(this, "Preset locked — tap 🔒 Locked to unlock", Toast.LENGTH_SHORT);
        activeToast.show();
    }

    @Override
    public void onStateMutated() {
        refreshAllUi(true);
    }

    @Override
    public void closePanel() {
        soundTestEngine.setFreqCallback(null);
        soundTestEngine.stop();
        openSubPanel = PANEL_NONE;
        subPanelOverlay.setVisibility(View.GONE);
        syncBackCallback();
        refreshAllUi(false);
    }

    @Override
    public void requestExportJson() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "kokerqualizer_backup.json");
        try {
            startActivityForResult(i, REQ_EXPORT_JSON);
        } catch (Throwable t) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, store.exportAllJson());
            startActivity(Intent.createChooser(share, "Export Kokerqualizer Backup"));
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
    public SoundTestEngine getSoundTestEngine() {
        return soundTestEngine;
    }

    private void refreshYouTubeCard() {
        if (ytCard == null || ytStore == null) return;
        EqStore.EqPreset eqP = store.getActivePreset();
        int accent = eqP != null ? eqP.accentColor : 0xFF39FF14;
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        YouTubeStore.YtState ys = ytStore.getState();

        if (pl == null) {
            ytEmptyRow.setVisibility(View.VISIBLE);
            ytPlayerContent.setVisibility(View.GONE);
            return;
        }
        ytEmptyRow.setVisibility(View.GONE);
        ytPlayerContent.setVisibility(View.VISIBLE);

        int total = pl.tracks.size();
        int curNum = total > 0 ? (ys.currentIndex + 1) : 0;
        ytHeaderTitle.setText("🎵 " + pl.name + (total > 0 ? ("  (" + curNum + "/" + total + ")") : ""));
        ytHeaderTitle.setTextColor(accent);

        ytSyncBtn.setText(ytSyncing ? "…" : "↻");
        UiStyle.stylePill(this, ytSyncBtn, ytSyncing, accent);

        boolean expanded = ys.videoExpanded;
        ytVideoToggleBtn.setText(expanded ? "🎬 Video" : "🎧 Mini");
        UiStyle.stylePill(this, ytVideoToggleBtn, expanded, accent);
        ViewGroup.LayoutParams lp = ytPlayerView.getLayoutParams();
        int fullH = (ytPlayerView != null && ytPlayerView.isFullWebActive()) ? 330 : 205;
        // Keep >=116dp in Mini mode so YouTube IFrame never visibility-throttles audio!
        int desiredH = UiStyle.dp(this, expanded ? fullH : 116);
        if (lp != null && lp.height != desiredH) {
            lp.height = desiredH;
            ytPlayerView.setLayoutParams(lp);
        }

        UiStyle.stylePill(this, ytListBtn, false, accent);

        YouTubeStore.Track cur = ytStore.getCurrentTrack();
        if (cur != null) {
            ytTrackTitle.setText(cur.title);
            ytTrackSub.setText(cur.channel.isEmpty()
                    ? (ys.fullWebMode ? "Full YouTube Mode" : "In-App Player")
                    : (cur.channel + (cur.duration.isEmpty() ? "" : ("  •  " + cur.duration))));
        } else {
            ytTrackTitle.setText(ytSyncing ? "Syncing tracks from YouTube..." : "Tap ↻ to sync tracks (or use Full YT Mode)");
            ytTrackSub.setText("Playlist ID: " + pl.playlistId);
        }

        boolean playing = ytPlayerView != null && ytPlayerView.isPlaying();
        ytPlayBtn.setText(playing ? "⏸ Pause" : "▶ Play");
        UiStyle.stylePill(this, ytPlayBtn, playing, accent);
        UiStyle.stylePill(this, ytPrevBtn, false, accent);
        UiStyle.stylePill(this, ytNextBtn, false, accent);
        UiStyle.stylePill(this, ytShuffleBtn, ys.shuffle, accent);
    }

    private void stepYtTrack(int delta) {
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || pl.tracks.isEmpty()) {
            Toast.makeText(this, "Tap ↻ Sync to load tracks first", Toast.LENGTH_SHORT).show();
            return;
        }
        int n = pl.tracks.size();
        YouTubeStore.YtState ys = ytStore.getState();
        int nextIdx;
        if (ys.shuffle && n > 1) {
            nextIdx = ytRandom.nextInt(n);
            if (nextIdx == ys.currentIndex) nextIdx = (nextIdx + 1) % n;
        } else {
            nextIdx = (ys.currentIndex + delta + n) % n;
        }
        onYtPlayTrack(nextIdx);
    }

    private void syncActiveYouTubePlaylist(boolean showToast) {
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || ytSyncing) return;
        ytSyncing = true;
        refreshYouTubeCard();
        final String targetId = pl.id;
        final boolean hadNoTracks = pl.tracks.isEmpty();

        YouTubePlaylistSync.syncAsync(pl.playlistId, new YouTubePlaylistSync.Callback() {
            @Override
            public void onSuccess(String fetchedTitle, List<YouTubeStore.Track> tracks) {
                ytSyncing = false;
                ytStore.updatePlaylistTracks(targetId, fetchedTitle, tracks);
                if (hadNoTracks && !tracks.isEmpty()) {
                    YouTubeStore.SavedPlaylist active = ytStore.getActivePlaylist();
                    if (active != null && active.id.equals(targetId)) {
                        ytPlayerView.loadTrack(tracks.get(0).videoId, active.playlistId, ytStore.getState().autoPlayOnStartup);
                    }
                }
                if (showToast) {
                    Toast.makeText(MainActivity.this, "Synced " + tracks.size() + " tracks!", Toast.LENGTH_SHORT).show();
                }
                refreshAllUi(false);
            }

            @Override
            public void onError(String message) {
                ytSyncing = false;
                if (showToast) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
                refreshYouTubeCard();
            }
        });
    }

    @Override
    public void onYtPlayTrack(int index) {
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || pl.tracks.isEmpty()) return;
        int clamped = Math.max(0, Math.min(pl.tracks.size() - 1, index));
        ytStore.getState().currentIndex = clamped;
        ytStore.getState().lastWebUrl = null;
        ytStore.save();
        YouTubeStore.Track t = pl.tracks.get(clamped);
        ytPlayerView.loadTrack(t.videoId, pl.playlistId, true);
        refreshYouTubeCard();
        if (openSubPanel == PANEL_YOUTUBE) renderOpenSubPanel();
    }

    @Override
    public void onYtSyncNow() {
        syncActiveYouTubePlaylist(true);
    }

    @Override
    public void onYtPlaylistChanged() {
        ytStore.getState().lastWebUrl = null;
        ytStore.save();
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl != null) {
            ytPlayerView.setFullWebMode(ytStore.getState().fullWebMode);
            YouTubeStore.Track cur = ytStore.getCurrentTrack();
            if (cur != null) {
                ytPlayerView.loadTrack(cur.videoId, pl.playlistId, true);
            } else if (ytStore.getState().fullWebMode) {
                ytPlayerView.loadTrack(null, pl.playlistId, true);
            }
        } else if (ytPlayerView != null) {
            // Stop phantom audio and reset state cleanly when last playlist is removed
            ytPlayerView.stopAndReset();
        }
        refreshAllUi(false);
    }

    @Override
    public void onYtFullWebChanged(boolean fullWeb) {
        if (ytPlayerView != null) {
            ytPlayerView.setFullWebMode(fullWeb);
        }
        refreshYouTubeCard();
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
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
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
