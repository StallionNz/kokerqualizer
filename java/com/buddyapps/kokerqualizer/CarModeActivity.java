package com.buddyapps.kokerqualizer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class CarModeActivity extends Activity {
    private EqStore store;
    private EqEngine engine;
    private FrameLayout frameRoot;
    private VisualizerView backdropViz;
    private LinearLayout root;
    private TextView nowPlayingTv;
    private TextView playPauseBtn;
    private boolean userDragging = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable nowPlayingPoller = new Runnable() {
        @Override
        public void run() {
            refreshNowPlayingStrip();
            uiHandler.postDelayed(this, 1500);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!userDragging) {
                buildUi();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = EqStore.get(this);
        engine = EqEngine.get(this);
        getWindow().setStatusBarColor(UiStyle.BG_AMOLED);
        getWindow().setNavigationBarColor(UiStyle.BG_AMOLED);
        if (store.getSettings().carKeepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        frameRoot = new FrameLayout(this);
        frameRoot.setBackgroundColor(UiStyle.BG_AMOLED);

        backdropViz = new VisualizerView(this);
        backdropViz.setAlpha(0.22f);
        frameRoot.addView(backdropViz, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        frameRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(frameRoot);
        UiStyle.applyEdgeToEdgeInsets(frameRoot, root, pad, root, pad);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store.getSettings().carKeepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        IntentFilter f = new IntentFilter(EqService.ACTION_STATE_BROADCAST);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
        syncBackdropListener();
        buildUi();
        uiHandler.removeCallbacks(nowPlayingPoller);
        uiHandler.postDelayed(nowPlayingPoller, 400);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(stateReceiver); } catch (Throwable ignored) {}
        engine.setVisualizerListener(null);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
    }

    private void syncBackdropListener() {
        if (store.getSettings().carVizBackdrop) {
            backdropViz.setVisibility(View.VISIBLE);
            engine.setVisualizerListener((bars, wave, l, r, clip, bass) ->
                    backdropViz.updateAudio(bars, wave, l, r, clip));
        } else {
            backdropViz.setVisibility(View.GONE);
            engine.setVisualizerListener(null);
        }
    }

    private void buildUi() {
        root.removeAllViews();
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        backdropViz.setMode(EqStore.VIZ_BARS);
        backdropViz.setAccentColor(accent);
        backdropViz.setEqActive(s.eqEnabled && !engine.isAbFlatCompare());
        backdropViz.setVisibility(s.carVizBackdrop ? View.VISIBLE : View.GONE);

        // Top compact dash header
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("KOKERQUALIZER  •  CAR");
        title.setTextColor(accent);
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView vizBtn = makeBigAction(
                s.carVizBackdrop ? "VIZ ON" : "VIZ OFF",
                s.carVizBackdrop ? accent : UiStyle.TEXT_SECONDARY,
                s.carVizBackdrop ? EqStore.withAlpha(accent, 34) : UiStyle.SURFACE_ELEV,
                s.carVizBackdrop ? accent : UiStyle.BORDER_SUBTLE, 12f);
        vizBtn.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 9), UiStyle.dp(this, 12), UiStyle.dp(this, 9));
        vizBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            s.carVizBackdrop = !s.carVizBackdrop;
            store.save();
            syncBackdropListener();
            buildUi();
        });
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vLp.rightMargin = UiStyle.dp(this, 8);
        top.addView(vizBtn, vLp);

        TextView exitBtn = makeBigAction("EXIT", 0xFFCBD5E1, UiStyle.SURFACE_ELEV, UiStyle.BORDER_SUBTLE, 12.5f);
        exitBtn.setPadding(UiStyle.dp(this, 14), UiStyle.dp(this, 9), UiStyle.dp(this, 14), UiStyle.dp(this, 9));
        exitBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            finish();
        });
        top.addView(exitBtn);
        root.addView(top);

        if (landscape) {
            buildLandscapeBody(s, active, accent);
        } else {
            buildPortraitBody(s, active, accent);
        }
    }

    private void buildPortraitBody(EqStore.Settings s, EqStore.EqPreset active, int accent) {
        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this, 64));
        prLp.topMargin = UiStyle.dp(this, 12);
        powerRow.setLayoutParams(prLp);

        TextView powerBtn = makeBigAction(
                s.eqEnabled ? "● EQ ON" : "○ BYPASS",
                s.eqEnabled ? accent : 0xFF94A3B8,
                s.eqEnabled ? EqStore.withAlpha(accent, 44) : UiStyle.SURFACE_CARD,
                s.eqEnabled ? accent : UiStyle.BORDER_SUBTLE,
                16f);
        powerBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            s.eqEnabled = !s.eqEnabled;
            store.save();
            EqService.startOrRefresh(this);
            buildUi();
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f);
        lp1.rightMargin = UiStyle.dp(this, 8);
        powerRow.addView(powerBtn, lp1);

        TextView hwyBtn = makeBigAction(
                s.highwayBoost ? "🛣 HWY ON" : "🛣 HWY +3dB",
                s.highwayBoost ? 0xFF00E676 : UiStyle.TEXT_PRIMARY,
                s.highwayBoost ? 0x3800E676 : UiStyle.SURFACE_CARD,
                s.highwayBoost ? 0xFF00E676 : UiStyle.BORDER_SUBTLE,
                14.5f);
        hwyBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            s.highwayBoost = !s.highwayBoost;
            store.save();
            EqService.startOrRefresh(this);
            buildUi();
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f);
        lp2.rightMargin = UiStyle.dp(this, 8);
        powerRow.addView(hwyBtn, lp2);

        boolean ab = engine.isAbFlatCompare();
        TextView abBtn = makeBigAction(
                ab ? "FLAT" : "A/B",
                ab ? 0xFFFFB300 : UiStyle.TEXT_PRIMARY,
                ab ? 0x33FFB300 : UiStyle.SURFACE_CARD,
                ab ? 0xFFFFB300 : UiStyle.BORDER_SUBTLE,
                15f);
        abBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            engine.setAbFlatCompare(!engine.isAbFlatCompare());
            buildUi();
        });
        powerRow.addView(abBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.8f));
        root.addView(powerRow);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.topMargin = UiStyle.dp(this, 12);
        svLp.bottomMargin = UiStyle.dp(this, 10);
        sv.addView(buildPresetGrid(2, active));
        root.addView(sv, svLp);

        if (active != null) {
            LinearLayout bassCard = new LinearLayout(this);
            bassCard.setOrientation(LinearLayout.VERTICAL);
            bassCard.setBackground(UiStyle.roundRect(0xDD0E1118, EqStore.withAlpha(accent, 110), UiStyle.dp(this, 1.2f), UiStyle.dp(this, 18)));
            bassCard.setPadding(UiStyle.dp(this, 16), UiStyle.dp(this, 10), UiStyle.dp(this, 16), UiStyle.dp(this, 10));

            TextView bLabel = new TextView(this);
            bLabel.setText(String.format(Locale.US, "BASS BOOST (%s)  •  %d%%",
                    EqStore.bassStyleName(active.bassStyle), Math.round(active.bassBoost)));
            bLabel.setTextColor(accent);
            bLabel.setTextSize(14f);
            bLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            bassCard.addView(bLabel);

            SeekBar sb = new SeekBar(this);
            sb.setMax(100);
            sb.setProgress(Math.round(active.bassBoost));
            UiStyle.tintSeekBar(sb, accent);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    active.bassBoost = progress;
                    bLabel.setText(String.format(Locale.US, "BASS BOOST (%s)  •  %d%%",
                            EqStore.bassStyleName(active.bassStyle), progress));
                    engine.applyCurrent();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { userDragging = true; }
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    userDragging = false;
                    store.addOrUpdatePreset(active);
                    EqService.startOrRefresh(CarModeActivity.this);
                }
            });
            bassCard.addView(sb);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bLp.bottomMargin = UiStyle.dp(this, 10);
            root.addView(bassCard, bLp);
        }

        root.addView(buildNowPlayingStrip(accent));
        root.addView(buildMediaBar(accent, UiStyle.dp(this, 66)));
    }

    private void buildLandscapeBody(EqStore.Settings s, EqStore.EqPreset active, int accent) {
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        spLp.topMargin = UiStyle.dp(this, 10);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        left.setBackground(UiStyle.roundRect(0xDD0E1118, EqStore.withAlpha(accent, 90), UiStyle.dp(this, 1.2f), UiStyle.dp(this, 20)));
        left.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 8), UiStyle.dp(this, 10), UiStyle.dp(this, 8));

        RotaryKnobView bassKnob = new RotaryKnobView(this);
        if (active != null) {
            bassKnob.configure("BASS • " + EqStore.bassStyleName(active.bassStyle).toUpperCase(Locale.US),
                    0f, 100f, 0f, false,
                    v -> Math.round(v) + "%",
                    new RotaryKnobView.Listener() {
                        @Override public void onValueChanging(float value) {
                            userDragging = true;
                            active.bassBoost = value;
                            engine.applyCurrent();
                        }
                        @Override public void onValueFinished(float value) {
                            userDragging = false;
                            store.addOrUpdatePreset(active);
                            EqService.startOrRefresh(CarModeActivity.this);
                        }
                        @Override public void onLockedTouch() {}
                    });
            bassKnob.setValue(active.bassBoost);
            bassKnob.setStyle(accent, s.eqEnabled, false);
        }
        left.addView(bassKnob, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView pwr = makeBigAction(
                s.eqEnabled ? "● ON" : "○ OFF",
                s.eqEnabled ? accent : 0xFF94A3B8,
                s.eqEnabled ? EqStore.withAlpha(accent, 45) : UiStyle.SURFACE_ELEV,
                s.eqEnabled ? accent : UiStyle.BORDER_SUBTLE, 14f);
        pwr.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            s.eqEnabled = !s.eqEnabled;
            store.save();
            EqService.startOrRefresh(this);
            buildUi();
        });
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, UiStyle.dp(this, 50), 1f);
        pLp.rightMargin = UiStyle.dp(this, 6);
        btnRow.addView(pwr, pLp);

        TextView hwy = makeBigAction(
                s.highwayBoost ? "🛣 ON" : "🛣 HWY",
                s.highwayBoost ? 0xFF00E676 : UiStyle.TEXT_PRIMARY,
                s.highwayBoost ? 0x3800E676 : UiStyle.SURFACE_ELEV,
                s.highwayBoost ? 0xFF00E676 : UiStyle.BORDER_SUBTLE, 14f);
        hwy.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            s.highwayBoost = !s.highwayBoost;
            store.save();
            EqService.startOrRefresh(this);
            buildUi();
        });
        btnRow.addView(hwy, pLp);

        boolean ab = engine.isAbFlatCompare();
        TextView abBtn = makeBigAction(
                ab ? "FLAT" : "A/B",
                ab ? 0xFFFFB300 : UiStyle.TEXT_PRIMARY,
                ab ? 0x33FFB300 : UiStyle.SURFACE_ELEV,
                ab ? 0xFFFFB300 : UiStyle.BORDER_SUBTLE, 14f);
        abBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            engine.setAbFlatCompare(!engine.isAbFlatCompare());
            buildUi();
        });
        btnRow.addView(abBtn, new LinearLayout.LayoutParams(0, UiStyle.dp(this, 50), 0.9f));
        left.addView(btnRow);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f);
        leftLp.rightMargin = UiStyle.dp(this, 10);
        split.addView(left, leftLp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.addView(buildPresetGrid(3, active));
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.bottomMargin = UiStyle.dp(this, 8);
        right.addView(sv, svLp);

        right.addView(buildNowPlayingStrip(accent));
        right.addView(buildMediaBar(accent, UiStyle.dp(this, 58)));
        split.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.35f));

        root.addView(split, spLp);
    }

    private GridLayout buildPresetGrid(int cols, EqStore.EqPreset active) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        List<EqStore.EqPreset> list = store.getCarPinnedPresets();
        int m = UiStyle.dp(this, 5);

        for (EqStore.EqPreset p : list) {
            boolean sel = active != null && p.id.equals(active.id);
            TextView tile = makeBigAction(
                    p.name,
                    sel ? p.accentColor : UiStyle.TEXT_PRIMARY,
                    sel ? EqStore.withAlpha(p.accentColor, 55) : 0xDD0E1118,
                    sel ? p.accentColor : EqStore.withAlpha(p.accentColor, 85),
                    17f);
            tile.setOnClickListener(v -> {
                UiStyle.hapticTick(v, this);
                store.setActivePreset(p.id);
                engine.setAbFlatCompare(false);
                EqService.startOrRefresh(this);
                buildUi();
            });
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = UiStyle.dp(this, 76);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(m, m, m, m);
            grid.addView(tile, glp);
        }
        return grid;
    }

    private TextView buildNowPlayingStrip(int accent) {
        nowPlayingTv = new TextView(this);
        nowPlayingTv.setSingleLine(true);
        nowPlayingTv.setEllipsize(TextUtils.TruncateAt.END);
        nowPlayingTv.setGravity(Gravity.CENTER);
        nowPlayingTv.setTextSize(12.5f);
        nowPlayingTv.setTypeface(null, android.graphics.Typeface.BOLD);
        nowPlayingTv.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 6), UiStyle.dp(this, 12), UiStyle.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(this, 8);
        nowPlayingTv.setLayoutParams(lp);
        nowPlayingTv.setOnClickListener(v -> {
            if (!KokerMediaListenerService.hasMediaAccess(this)) {
                openMediaAccessSettings();
            }
        });
        refreshNowPlayingStrip();
        return nowPlayingTv;
    }

    private boolean shouldControlInAppYouTube() {
        YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
        if (inApp == null || inApp.getNowPlayingLabel() == null) return false;
        if (inApp.isPlaying()) return true;
        return !KokerMediaListenerService.isPlayingNow(this);
    }

    private void refreshNowPlayingStrip() {
        if (nowPlayingTv == null) return;
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        if (shouldControlInAppYouTube()) {
            YouTubePlayerView.RemoteBridge inApp = YouTubePlayerView.getActiveRemoteBridge();
            nowPlayingTv.setText(inApp.getNowPlayingLabel());
            nowPlayingTv.setTextColor(accent);
            nowPlayingTv.setBackground(UiStyle.roundRect(0xCC0E1118, EqStore.withAlpha(accent, 110), UiStyle.dp(this, 1), UiStyle.dp(this, 12)));
            if (playPauseBtn != null) {
                playPauseBtn.setText(inApp.isPlaying() ? "⏸  PAUSE" : "▶  PLAY");
            }
            return;
        }

        if (!KokerMediaListenerService.hasMediaAccess(this)) {
            nowPlayingTv.setText("⚠ Tap to enable Media Access for external apps (or play YouTube in-app)");
            nowPlayingTv.setTextColor(0xFFFFB300);
            nowPlayingTv.setBackground(UiStyle.roundRect(0x28FFB300, 0xAAFFB300, UiStyle.dp(this, 1), UiStyle.dp(this, 12)));
        } else {
            String np = KokerMediaListenerService.getNowPlayingLabel(this);
            nowPlayingTv.setText(np != null ? np : "♪  Ready — Play music in Kokerqualizer or your app");
            nowPlayingTv.setTextColor(np != null ? accent : UiStyle.TEXT_SECONDARY);
            nowPlayingTv.setBackground(UiStyle.roundRect(0xCC0E1118, EqStore.withAlpha(accent, 75), UiStyle.dp(this, 1), UiStyle.dp(this, 12)));
        }
        if (playPauseBtn != null && KokerMediaListenerService.hasMediaAccess(this)) {
            boolean playing = KokerMediaListenerService.isPlayingNow(this);
            playPauseBtn.setText(playing ? "⏸  PAUSE" : "▶  PLAY");
        }
    }

    private void openMediaAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Enable Kokerqualizer Media Control, then come back", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Open Android Settings → Notification access → Kokerqualizer", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout buildMediaBar(int accent, int heightPx) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx));

        TextView prev = makeBigAction("⏮  PREV", UiStyle.TEXT_PRIMARY, 0xDD0E1118, UiStyle.BORDER_SUBTLE, 15.5f);
        prev.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            if (shouldControlInAppYouTube()) {
                YouTubePlayerView.getActiveRemoteBridge().playPrev();
            } else {
                KokerMediaListenerService.skipPrev(this);
            }
            uiHandler.postDelayed(this::refreshNowPlayingStrip, 350);
        });

        playPauseBtn = makeBigAction("⏯  PLAY / PAUSE", accent, EqStore.withAlpha(accent, 42), accent, 15.5f);
        playPauseBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            if (shouldControlInAppYouTube()) {
                YouTubePlayerView.getActiveRemoteBridge().togglePlayPause();
            } else {
                KokerMediaListenerService.playPause(this);
            }
            uiHandler.postDelayed(this::refreshNowPlayingStrip, 250);
        });

        TextView next = makeBigAction("NEXT  ⏭", UiStyle.TEXT_PRIMARY, 0xDD0E1118, UiStyle.BORDER_SUBTLE, 15.5f);
        next.setOnClickListener(v -> {
            UiStyle.hapticTick(v, this);
            if (shouldControlInAppYouTube()) {
                YouTubePlayerView.getActiveRemoteBridge().playNext();
            } else {
                KokerMediaListenerService.skipNext(this);
            }
            uiHandler.postDelayed(this::refreshNowPlayingStrip, 350);
        });

        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lpL.rightMargin = UiStyle.dp(this, 8);
        LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f);
        lpM.rightMargin = UiStyle.dp(this, 8);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);

        bar.addView(prev, lpL);
        bar.addView(playPauseBtn, lpM);
        bar.addView(next, lpR);
        refreshNowPlayingStrip();
        return bar;
    }

    private TextView makeBigAction(String text, int txtColor, int bgColor, int strokeColor, float sp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(txtColor);
        tv.setTextSize(sp);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(UiStyle.roundRect(bgColor, strokeColor, UiStyle.dp(this, 1.8f), UiStyle.dp(this, 18)));
        return tv;
    }
}
