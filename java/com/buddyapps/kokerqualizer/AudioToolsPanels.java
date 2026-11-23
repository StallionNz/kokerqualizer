package com.buddyapps.kokerqualizer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.UUID;

/**
 * Sound & Subwoofer Test Suite and Guided 7-Step Speaker/Car Calibration Wizard.
 */
class AudioToolsPanels {

    private static final float[] CAL_FREQS = new float[]{35f, 70f, 160f, 500f, 2000f, 6000f, 14000f};
    private static final String[] CAL_NAMES = new String[]{
            "35 Hz • Deep Sub-Bass",
            "70 Hz • Kick & Bass Punch",
            "160 Hz • Upper Bass / Warmth",
            "500 Hz • Lower Mid / Body",
            "2.0 kHz • Vocal Presence",
            "6.0 kHz • Treble Clarity",
            "14 kHz • Air & Sparkle"
    };

    static View buildSoundTestPanel(Context ctx, DrawerPanels.Host host) {
        EqStore.EqPreset active = EqStore.get(ctx).getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;
        SoundTestEngine ste = host.getSoundTestEngine();

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout mainCard = DrawerPanels.makeCard(ctx, accent);

        int initMode = ste.getMode();
        float initHz = ste.getTargetFreqHz();
        String initFmt = initMode == SoundTestEngine.MODE_PINK_NOISE
                ? "Pink Noise"
                : (initHz >= 1000f ? String.format(Locale.US, "%.2f kHz", initHz / 1000f) : String.format(Locale.US, "%.0f Hz", initHz));

        TextView freqReadout = new TextView(ctx);
        freqReadout.setText(initFmt + (ste.isPlaying() ? "  •  PLAYING" : "  •  Ready"));
        freqReadout.setTextColor(accent);
        freqReadout.setTextSize(22f);
        freqReadout.setTypeface(null, android.graphics.Typeface.BOLD);
        freqReadout.setGravity(Gravity.CENTER);
        mainCard.addView(freqReadout);

        ste.setFreqCallback(hz -> freqReadout.post(() -> {
            if (ste.getMode() == SoundTestEngine.MODE_PINK_NOISE) {
                freqReadout.setText(ste.isPlaying() ? "Pink Noise  •  PLAYING" : "Pink Noise  •  Stopped");
                return;
            }
            String fmt = hz >= 1000f
                    ? String.format(Locale.US, "%.2f kHz", hz / 1000f)
                    : String.format(Locale.US, "%.0f Hz", hz);
            freqReadout.setText(ste.isPlaying() ? (fmt + "  •  PLAYING") : (fmt + "  •  Stopped"));
        }));

        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mrLp.topMargin = UiStyle.dp(ctx, 12);

        String[] modeNames = new String[]{"Sine", "Sub Sweep", "Full Sweep", "Pink Noise"};
        TextView[] modeBtns = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            modeBtns[i] = UiStyle.makePill(ctx, modeNames[i], i == initMode, accent);
            modeBtns[i].setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                ste.setMode(idx);
                for (int j = 0; j < 4; j++) UiStyle.stylePill(ctx, modeBtns[j], j == idx, accent);
                if (idx == SoundTestEngine.MODE_PINK_NOISE) {
                    freqReadout.setText(ste.isPlaying() ? "Pink Noise  •  PLAYING" : "Pink Noise");
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 3) lp.rightMargin = UiStyle.dp(ctx, 5);
            modeRow.addView(modeBtns[i], lp);
        }
        mainCard.addView(modeRow, mrLp);

        TextView fLbl = new TextView(ctx);
        fLbl.setText("Manual Frequency (20 Hz – 20 kHz):");
        fLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        fLbl.setTextSize(12f);
        fLbl.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 4));
        mainCard.addView(fLbl);

        SeekBar freqSeek = new SeekBar(ctx);
        freqSeek.setMax(1000);
        int initProg = (int) Math.round(1000.0 * Math.log(Math.max(20f, initHz) / 20.0) / Math.log(1000.0));
        freqSeek.setProgress(Math.max(0, Math.min(1000, initProg)));
        UiStyle.tintSeekBar(freqSeek, accent);
        freqSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                float hz = (float) (20.0 * Math.pow(1000.0, p / 1000.0));
                ste.setTargetFreqHz(hz);
                if (ste.getMode() == SoundTestEngine.MODE_SINE) {
                    String fmt = hz >= 1000f ? String.format(Locale.US, "%.2f kHz", hz / 1000f) : String.format(Locale.US, "%.0f Hz", hz);
                    freqReadout.setText(fmt + (ste.isPlaying() ? "  •  PLAYING" : ""));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        mainCard.addView(freqSeek);

        HorizontalScrollView subScroll = new HorizontalScrollView(ctx);
        subScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout subRow = new LinearLayout(ctx);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        float[] quickHz = new float[]{25f, 30f, 40f, 50f, 60f, 80f, 120f, 1000f};
        for (float q : quickHz) {
            String lbl = q >= 1000f ? "1 kHz" : ((int) q + " Hz");
            TextView qb = UiStyle.makePill(ctx, lbl, false, accent);
            qb.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                ste.setMode(SoundTestEngine.MODE_SINE);
                for (int j = 0; j < 4; j++) UiStyle.stylePill(ctx, modeBtns[j], j == 0, accent);
                ste.setTargetFreqHz(q);
                int prog = (int) Math.round(1000.0 * Math.log(q / 20.0) / Math.log(1000.0));
                freqSeek.setProgress(Math.max(0, Math.min(1000, prog)));
                freqReadout.setText(lbl + (ste.isPlaying() ? "  •  PLAYING" : ""));
            });
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            qlp.rightMargin = UiStyle.dp(ctx, 6);
            subRow.addView(qb, qlp);
        }
        subScroll.addView(subRow);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ssLp.topMargin = UiStyle.dp(ctx, 10);
        mainCard.addView(subScroll, ssLp);

        TextView vLbl = new TextView(ctx);
        int initVolPct = Math.round(ste.getVolume() * 100f);
        vLbl.setText(String.format(Locale.US, "Test Signal Level: %d%% (start low for subs!)", initVolPct));
        vLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        vLbl.setTextSize(12f);
        vLbl.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 4));
        mainCard.addView(vLbl);

        SeekBar volSeek = new SeekBar(ctx);
        volSeek.setMax(95);
        volSeek.setProgress(initVolPct);
        UiStyle.tintSeekBar(volSeek, accent);
        volSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int clamped = Math.max(5, p);
                ste.setVolume(clamped / 100f);
                vLbl.setText(String.format(Locale.US, "Test Signal Level: %d%%", clamped));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        mainCard.addView(volSeek);

        TextView cLbl = new TextView(ctx);
        cLbl.setText("Speaker Channel Check:");
        cLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        cLbl.setTextSize(12f);
        cLbl.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 6));
        mainCard.addView(cLbl);

        LinearLayout chRow = new LinearLayout(ctx);
        chRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] chLabels = new String[]{"LEFT ONLY", "BOTH (STEREO)", "RIGHT ONLY"};
        int[] chVals = new int[]{SoundTestEngine.CH_LEFT, SoundTestEngine.CH_BOTH, SoundTestEngine.CH_RIGHT};
        int curCh = ste.getChannelMode();
        TextView[] chBtns = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            chBtns[i] = UiStyle.makePill(ctx, chLabels[i], chVals[i] == curCh, accent);
            chBtns[i].setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                ste.setChannelMode(chVals[idx]);
                for (int j = 0; j < 3; j++) UiStyle.stylePill(ctx, chBtns[j], j == idx, accent);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 6);
            chRow.addView(chBtns[i], lp);
        }
        mainCard.addView(chRow);

        TextView playBtn = new TextView(ctx);
        DrawerPanels.stylePlayBtn(ctx, playBtn, ste.isPlaying(), accent);
        playBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            if (ste.isPlaying()) {
                ste.stop();
                DrawerPanels.stylePlayBtn(ctx, playBtn, false, accent);
            } else {
                YouTubePlayerView.RemoteBridge yt = YouTubePlayerView.getActiveRemoteBridge();
                if (yt != null && yt.isPlaying()) yt.togglePlayPause();
                ste.start();
                DrawerPanels.stylePlayBtn(ctx, playBtn, true, accent);
            }
        });
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = UiStyle.dp(ctx, 16);
        mainCard.addView(playBtn, pbLp);

        wrap.addView(mainCard);
        return wrap;
    }

    static View buildCalibrationPanel(Context ctx, DrawerPanels.Host host, int[] stepRef, float[] offsetsRef) {
        EqStore store = EqStore.get(ctx);
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : EqStore.DEFAULT_ACCENT;
        SoundTestEngine ste = host.getSoundTestEngine();

        int step = Math.max(0, Math.min(CAL_FREQS.length - 1, stepRef[0]));
        float hz = CAL_FREQS[step];

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card = DrawerPanels.makeCard(ctx, accent);

        TextView progressTv = new TextView(ctx);
        progressTv.setText(String.format(Locale.US, "STEP %d OF %d  •  SPEAKER CALIBRATION", step + 1, CAL_FREQS.length));
        progressTv.setTextColor(UiStyle.TEXT_MUTED);
        progressTv.setTextSize(11.5f);
        progressTv.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(progressTv);

        TextView freqTv = new TextView(ctx);
        freqTv.setText(CAL_NAMES[step]);
        freqTv.setTextColor(accent);
        freqTv.setTextSize(20f);
        freqTv.setTypeface(null, android.graphics.Typeface.BOLD);
        freqTv.setPadding(0, UiStyle.dp(ctx, 6), 0, UiStyle.dp(ctx, 6));
        card.addView(freqTv);

        TextView guideTv = new TextView(ctx);
        guideTv.setText("Play the reference tone on your car stereo, portable speaker, or headphones, then tap how loud it sounds compared to normal music. Kokerqualizer will auto-build a compensated preset.");
        guideTv.setTextColor(UiStyle.TEXT_SECONDARY);
        guideTv.setTextSize(12.5f);
        card.addView(guideTv);

        TextView toneBtn = new TextView(ctx);
        boolean playingThis = ste.isPlaying();
        DrawerPanels.stylePlayBtn(ctx, toneBtn, playingThis, accent);
        toneBtn.setText(playingThis ? ("■  STOP " + (int) hz + " Hz TONE") : ("▶  PLAY " + (int) hz + " Hz TONE"));
        toneBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            if (ste.isPlaying()) {
                ste.stop();
                DrawerPanels.stylePlayBtn(ctx, toneBtn, false, accent);
                toneBtn.setText("▶  PLAY " + (int) hz + " Hz TONE");
            } else {
                YouTubePlayerView.RemoteBridge yt = YouTubePlayerView.getActiveRemoteBridge();
                if (yt != null && yt.isPlaying()) yt.togglePlayPause();
                ste.setMode(SoundTestEngine.MODE_SINE);
                ste.setChannelMode(SoundTestEngine.CH_BOTH);
                ste.setTargetFreqHz(hz);
                ste.start();
                DrawerPanels.stylePlayBtn(ctx, toneBtn, true, accent);
                toneBtn.setText("■  STOP " + (int) hz + " Hz TONE");
            }
        });
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tbLp.topMargin = UiStyle.dp(ctx, 12);
        card.addView(toneBtn, tbLp);

        TextView vLbl = new TextView(ctx);
        int volPct = Math.round(ste.getVolume() * 100f);
        vLbl.setText(String.format(Locale.US, "Reference Level: %d%%", volPct));
        vLbl.setTextColor(UiStyle.TEXT_MUTED);
        vLbl.setTextSize(11.5f);
        vLbl.setPadding(0, UiStyle.dp(ctx, 10), 0, UiStyle.dp(ctx, 2));
        card.addView(vLbl);

        SeekBar volSeek = new SeekBar(ctx);
        volSeek.setMax(90);
        volSeek.setProgress(volPct);
        UiStyle.tintSeekBar(volSeek, accent);
        volSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int c = Math.max(5, p);
                ste.setVolume(c / 100f);
                vLbl.setText(String.format(Locale.US, "Reference Level: %d%%", c));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        card.addView(volSeek);

        TextView qLbl = new TextView(ctx);
        qLbl.setText("How does this frequency sound on your speakers?");
        qLbl.setTextColor(UiStyle.TEXT_PRIMARY);
        qLbl.setTextSize(13.5f);
        qLbl.setTypeface(null, android.graphics.Typeface.BOLD);
        qLbl.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 8));
        card.addView(qLbl);

        String[] optLabels = new String[]{
                "Way Too Quiet  →  Boost +6 dB",
                "Slightly Quiet  →  Boost +3 dB",
                "Balanced / Clear  →  0 dB",
                "Slightly Loud  →  Cut -3 dB",
                "Too Harsh / Boomy  →  Cut -6 dB"
        };
        float[] optVals = new float[]{6f, 3f, 0f, -3f, -6f};
        for (int i = 0; i < optLabels.length; i++) {
            final float dbVal = optVals[i];
            boolean picked = Math.abs(offsetsRef[step] - dbVal) < 0.1f;
            TextView opt = UiStyle.makePill(ctx, optLabels[i], picked, accent);
            opt.setTextSize(13f);
            opt.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 11), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 11));
            opt.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                offsetsRef[step] = dbVal;
                if (step < CAL_FREQS.length - 1) {
                    stepRef[0] = step + 1;
                    if (ste.isPlaying()) ste.setTargetFreqHz(CAL_FREQS[step + 1]);
                    host.onStateMutated();
                } else {
                    ste.stop();
                    finalizeCalibrationPreset(ctx, store, offsetsRef, stepRef, host);
                }
            });
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            olp.bottomMargin = UiStyle.dp(ctx, 7);
            card.addView(opt, olp);
        }

        LinearLayout navRow = new LinearLayout(ctx);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.topMargin = UiStyle.dp(ctx, 6);

        if (step > 0) {
            TextView prev = UiStyle.makePill(ctx, "◀ Previous Band", false, accent);
            prev.setOnClickListener(v -> {
                stepRef[0] = step - 1;
                if (ste.isPlaying()) ste.setTargetFreqHz(CAL_FREQS[step - 1]);
                host.onStateMutated();
            });
            navRow.addView(prev);
        }
        navRow.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView restart = UiStyle.makePill(ctx, "↺ Restart", false, 0xFF94A3B8);
        restart.setOnClickListener(v -> {
            ste.stop();
            stepRef[0] = 0;
            for (int k = 0; k < offsetsRef.length; k++) offsetsRef[k] = 0f;
            host.onStateMutated();
        });
        navRow.addView(restart);
        card.addView(navRow, nLp);

        wrap.addView(card);
        return wrap;
    }

    private static void finalizeCalibrationPreset(Context ctx, EqStore store, float[] offsets,
                                                  int[] stepRef, DrawerPanels.Host host) {
        EditText et = new EditText(ctx);
        et.setText("Calibrated Speaker");
        et.setHint("Name your calibrated preset (e.g. My Car)");
        et.setTextColor(UiStyle.TEXT_PRIMARY);
        et.setHintTextColor(UiStyle.TEXT_MUTED);
        int pad = UiStyle.dp(ctx, 16);
        et.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(ctx)
                .setTitle("Calibration Complete! Save Preset")
                .setView(et)
                .setPositiveButton("Create & Apply", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Calibrated Speaker";
                    EqStore.EqPreset p = new EqStore.EqPreset();
                    p.id = "cal_" + UUID.randomUUID().toString().substring(0, 8);
                    p.name = name;
                    p.accentColor = EqStore.DEFAULT_ACCENT;
                    p.builtIn = false;
                    p.favorite = true;
                    p.pinnedCar = true;
                    p.punchMbc = 35f;
                    p.limiter = 72f;
                    for (int i = 0; i < 10; i++) {
                        p.bands10[i] = interpolateCal(EqStore.ISO_CENTERS[i], offsets);
                    }
                    for (int i = 0; i < 15; i++) {
                        p.bands15[i] = interpolateCal(EqStore.PRO15_CENTERS[i], offsets);
                    }
                    store.addOrUpdatePreset(p);
                    store.setActivePreset(p.id);
                    EqEngine.get(ctx).setAbFlatCompare(false);
                    EqService.startOrRefresh(ctx);
                    stepRef[0] = 0;
                    for (int k = 0; k < offsets.length; k++) offsets[k] = 0f;
                    Toast.makeText(ctx, "Applied calibrated preset: " + name, Toast.LENGTH_LONG).show();
                    host.closePanel();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static float interpolateCal(float targetHz, float[] offsets) {
        if (targetHz <= CAL_FREQS[0]) return offsets[0];
        if (targetHz >= CAL_FREQS[CAL_FREQS.length - 1]) return offsets[offsets.length - 1];
        double logT = Math.log(targetHz);
        for (int i = 0; i < CAL_FREQS.length - 1; i++) {
            if (targetHz >= CAL_FREQS[i] && targetHz <= CAL_FREQS[i + 1]) {
                double logA = Math.log(CAL_FREQS[i]);
                double logB = Math.log(CAL_FREQS[i + 1]);
                float frac = (float) ((logT - logA) / Math.max(0.0001, logB - logA));
                return Math.round((offsets[i] + (offsets[i + 1] - offsets[i]) * frac) * 2f) / 2f;
            }
        }
        return 0f;
    }
}
