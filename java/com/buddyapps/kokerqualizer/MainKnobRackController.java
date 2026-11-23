package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Owns the Mastering Rack UI card: 6 Hi-Fi rotary knobs (Bass, Punch MBC, Preamp,
 * Limiter, 3D Virtualizer, L/R Balance), global DSP toggles, Limiter Ceiling,
 * Bass Style, and Acoustic Space Reverb selectors.
 */
class MainKnobRackController {
    interface Host {
        void ensureDragSnapshot();
        void commitDragSnapshot();
        void pushUndo(EqStore.EqPreset preset);
        void resetMorphPadBase();
        void updateTelemetryStrip();
        void refreshUndoRedoPills();
        void refreshAllUi(boolean animate);
        void showLockedToast();
    }

    private interface KnobSetter { void set(float v); }

    private final Context ctx;
    private final EqStore store;
    private final EqEngine engine;
    private final Host host;

    private LinearLayout knobRackCard;
    private RotaryKnobView knobBass, knobPunch, knobPreamp, knobLimiter, knobVirt, knobBalance;
    private final TextView[] bassStyleBtns = new TextView[3];
    private final TextView[] reverbBtns = new TextView[5];
    private final TextView[] limCeilingBtns = new TextView[3];
    private TextView autoGainPill, loudnessPill, monoPill, subCutPill, warmthPill, xfeedPill, nightPill;

    MainKnobRackController(Context ctx, EqStore store, EqEngine engine, Host host) {
        this.ctx = ctx;
        this.store = store;
        this.engine = engine;
        this.host = host;
    }

    View buildCard() {
        knobRackCard = new LinearLayout(ctx);
        LinearLayout card = knobRackCard;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 22)));
        card.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 14));

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView rackTitle = new TextView(ctx);
        rackTitle.setText("MASTERING RACK");
        rackTitle.setTextColor(UiStyle.TEXT_MUTED);
        rackTitle.setTextSize(11f);
        rackTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(rackTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        loudnessPill = UiStyle.makePill(ctx, "Loudness", false, EqStore.DEFAULT_ACCENT);
        loudnessPill.setTextSize(10f);
        loudnessPill.setPadding(UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4));
        loudnessPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            store.getSettings().autoLoudness = !store.getSettings().autoLoudness;
            store.save();
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });
        int pGapPx = UiStyle.dp(ctx, 5);
        topRow.addView(loudnessPill, UiStyle.wrapLp(0, pGapPx));

        monoPill = UiStyle.makePill(ctx, "Mono", false, EqStore.DEFAULT_ACCENT);
        monoPill.setTextSize(10f);
        monoPill.setPadding(UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4));
        monoPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            store.getSettings().monoAudio = !store.getSettings().monoAudio;
            store.save();
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });
        topRow.addView(monoPill, UiStyle.wrapLp(0, pGapPx));

        autoGainPill = UiStyle.makePill(ctx, "Auto-Gain", true, EqStore.DEFAULT_ACCENT);
        autoGainPill.setTextSize(10f);
        autoGainPill.setPadding(UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4));
        autoGainPill.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            store.getSettings().autoGain = !store.getSettings().autoGain;
            store.save();
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });
        topRow.addView(autoGainPill);
        card.addView(topRow);

        // SubCut, Analog Warmth, Crossfeed, Night Whisper DSP Row
        LinearLayout dspRow = new LinearLayout(ctx);
        dspRow.setOrientation(LinearLayout.HORIZONTAL);
        dspRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams drLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        drLp.topMargin = UiStyle.dp(ctx, 8);

        subCutPill = UiStyle.makePill(ctx, "SubCut", false, EqStore.DEFAULT_ACCENT);
        subCutPill.setTextSize(10f);
        subCutPill.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
        subCutPill.setOnClickListener(v -> {
            if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
            UiStyle.hapticTick(v, ctx);
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            host.pushUndo(p);
            p.subSonicFilter = !p.subSonicFilter;
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });

        warmthPill = UiStyle.makePill(ctx, "Warmth", false, EqStore.DEFAULT_ACCENT);
        warmthPill.setTextSize(10f);
        warmthPill.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
        warmthPill.setOnClickListener(v -> {
            if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
            UiStyle.hapticTick(v, ctx);
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            host.pushUndo(p);
            p.analogWarmth = !p.analogWarmth;
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });

        xfeedPill = UiStyle.makePill(ctx, "XFeed", false, EqStore.DEFAULT_ACCENT);
        xfeedPill.setTextSize(10f);
        xfeedPill.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
        xfeedPill.setOnClickListener(v -> {
            if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
            UiStyle.hapticTick(v, ctx);
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            host.pushUndo(p);
            p.crossfeed = !p.crossfeed;
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });

        nightPill = UiStyle.makePill(ctx, "🌙 Night", false, EqStore.DEFAULT_ACCENT);
        nightPill.setTextSize(10f);
        nightPill.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
        nightPill.setOnClickListener(v -> {
            if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
            UiStyle.hapticTick(v, ctx);
            EqStore.EqPreset p = store.getActivePreset();
            if (p == null) return;
            host.pushUndo(p);
            p.nightWhisper = !p.nightWhisper;
            store.addOrUpdatePreset(p);
            EqService.startOrRefresh(ctx);
            host.refreshAllUi(false);
        });

        int dGapPx = UiStyle.dp(ctx, 5);
        dspRow.addView(subCutPill, UiStyle.rowLp(1f, dGapPx));
        dspRow.addView(warmthPill, UiStyle.rowLp(1f, dGapPx));
        dspRow.addView(xfeedPill, UiStyle.rowLp(1f, dGapPx));
        dspRow.addView(nightPill, UiStyle.rowLp(1f, 0));
        card.addView(dspRow, drLp);

        // Selectable Limiter Ceiling Row (-0.1 dB Loud / -1.0 dB Safe / -3.0 dB Studio)
        LinearLayout limRow = new LinearLayout(ctx);
        limRow.setOrientation(LinearLayout.HORIZONTAL);
        limRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lmrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lmrLp.topMargin = UiStyle.dp(ctx, 8);
        TextView lcLbl = new TextView(ctx);
        lcLbl.setText("Ceiling:");
        lcLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        lcLbl.setTextSize(11.5f);
        LinearLayout.LayoutParams lclLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lclLp.rightMargin = UiStyle.dp(ctx, 6);
        limRow.addView(lcLbl, lclLp);

        String[] lcNames = new String[]{"-0.1 Loud", "-1.0 Safe", "-3.0 Studio"};
        for (int i = 0; i < 3; i++) {
            final int cm = i;
            limCeilingBtns[i] = UiStyle.makePill(ctx, lcNames[i], i == 1, EqStore.DEFAULT_ACCENT);
            limCeilingBtns[i].setTextSize(10f);
            limCeilingBtns[i].setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
            limCeilingBtns[i].setOnClickListener(v -> {
                if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
                UiStyle.hapticTick(v, ctx);
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                host.pushUndo(p);
                p.limiterCeilingMode = cm;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(ctx);
                host.refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 5);
            limRow.addView(limCeilingBtns[i], lp);
        }
        card.addView(limRow, lmrLp);

        LinearLayout bassRow = new LinearLayout(ctx);
        bassRow.setOrientation(LinearLayout.HORIZONTAL);
        bassRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = UiStyle.dp(ctx, 10);
        brLp.bottomMargin = UiStyle.dp(ctx, 8);

        TextView bLbl = new TextView(ctx);
        bLbl.setText("Bass Style:");
        bLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        bLbl.setTextSize(12f);
        LinearLayout.LayoutParams blLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blLp.rightMargin = UiStyle.dp(ctx, 8);
        bassRow.addView(bLbl, blLp);

        String[] styles = new String[]{"Tight", "Deep", "Boomy"};
        for (int i = 0; i < 3; i++) {
            final int st = i;
            bassStyleBtns[i] = UiStyle.makePill(ctx, styles[i], false, 0xFF39FF14);
            bassStyleBtns[i].setTextSize(11f);
            bassStyleBtns[i].setOnClickListener(v -> {
                if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
                UiStyle.hapticTick(v, ctx);
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                host.pushUndo(p);
                p.bassStyle = st;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(ctx);
                host.refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 6);
            bassRow.addView(bassStyleBtns[i], lp);
        }
        card.addView(bassRow, brLp);

        // Acoustic Space Reverb Row
        LinearLayout revRow = new LinearLayout(ctx);
        revRow.setOrientation(LinearLayout.HORIZONTAL);
        revRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rrLp.bottomMargin = UiStyle.dp(ctx, 8);

        TextView rLbl = new TextView(ctx);
        rLbl.setText("Space:");
        rLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        rLbl.setTextSize(11.5f);
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlLp.rightMargin = UiStyle.dp(ctx, 6);
        revRow.addView(rLbl, rlLp);

        String[] revLabels = new String[]{"Off", "Studio", "Club", "Hall", "Cabin"};
        for (int i = 0; i < 5; i++) {
            final int rm = i;
            reverbBtns[i] = UiStyle.makePill(ctx, revLabels[i], i == 0, EqStore.DEFAULT_ACCENT);
            reverbBtns[i].setTextSize(10f);
            reverbBtns[i].setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 4));
            reverbBtns[i].setOnClickListener(v -> {
                if (store.getSettings().presetLocked) { host.showLockedToast(); return; }
                UiStyle.hapticTick(v, ctx);
                EqStore.EqPreset p = store.getActivePreset();
                if (p == null) return;
                host.pushUndo(p);
                p.reverbMode = rm;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(ctx);
                host.refreshAllUi(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 4) lp.rightMargin = UiStyle.dp(ctx, 4);
            revRow.addView(reverbBtns[i], lp);
        }
        card.addView(revRow, rrLp);

        // Symmetrical 2x3 Hi-Fi Knob Grid (6 Big Knobs)
        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(2);
        int knobH = UiStyle.dp(ctx, 140);

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

    void setVisible(boolean visible) {
        if (knobRackCard != null) {
            knobRackCard.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    void setBassAndPunchValues(float bass, float punch) {
        if (knobBass != null) knobBass.setValue(bass, false);
        if (knobPunch != null) knobPunch.setValue(punch, false);
    }

    void syncUi(EqStore.EqPreset p, EqStore.Settings s, int accent, boolean active, boolean animate) {
        UiStyle.stylePill(ctx, loudnessPill, s.autoLoudness, accent);
        UiStyle.stylePill(ctx, monoPill, s.monoAudio, 0xFFFFB300);
        UiStyle.stylePill(ctx, autoGainPill, s.autoGain, accent);
        UiStyle.stylePill(ctx, subCutPill, p.subSonicFilter, accent);
        UiStyle.stylePill(ctx, warmthPill, p.analogWarmth, 0xFFFF6D00);
        UiStyle.stylePill(ctx, xfeedPill, p.crossfeed, 0xFF00E5FF);
        UiStyle.stylePill(ctx, nightPill, p.nightWhisper, 0xFFB388FF);
        for (int i = 0; i < 3; i++) {
            UiStyle.stylePill(ctx, limCeilingBtns[i], p.limiterCeilingMode == i, accent);
        }
        for (int i = 0; i < 3; i++) {
            UiStyle.stylePill(ctx, bassStyleBtns[i], p.bassStyle == i, accent);
        }
        for (int i = 0; i < 5; i++) {
            UiStyle.stylePill(ctx, reverbBtns[i], p.reverbMode == i, accent);
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
    }

    private RotaryKnobView makeKnob(String title, float min, float max, float def,
                                    boolean bipolar, RotaryKnobView.Formatter fmt, KnobSetter setter) {
        RotaryKnobView k = new RotaryKnobView(ctx);
        k.configure(title, min, max, def, bipolar, fmt, new RotaryKnobView.Listener() {
            @Override public void onValueChanging(float value) {
                host.ensureDragSnapshot();
                setter.set(value);
                engine.applyCurrent();
                host.updateTelemetryStrip();
            }
            @Override public void onValueFinished(float value) {
                host.commitDragSnapshot();
                host.resetMorphPadBase();
                EqStore.EqPreset p = store.getActivePreset();
                if (p != null) {
                    store.addOrUpdatePreset(p);
                    EqService.startOrRefresh(ctx);
                }
                host.refreshUndoRedoPills();
                host.updateTelemetryStrip();
            }
            @Override public void onLockedTouch() { host.showLockedToast(); }
        });
        return k;
    }

    private void addKnobToGrid(GridLayout grid, View knob, int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = h;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 4));
        grid.addView(knob, lp);
    }
}
