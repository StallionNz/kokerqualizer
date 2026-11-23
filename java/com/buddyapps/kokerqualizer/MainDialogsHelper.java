package com.buddyapps.kokerqualizer;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Modal dialogs launched from the main equalizer deck:
 * 0.1 dB Band Fine-Tune (+ Per-Band Solo Audition), A/B/C Slot B picker,
 * and Sleep Timer selector.
 */
class MainDialogsHelper {

    static void showPrecisionFineTuneDialog(Context ctx, EqStore store, EqEngine engine,
                                            int bandIndex, String freqLabel, float currentDb,
                                            Runnable pushUndoAndResetMorph, Runnable onApplied) {
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int accent = p.accentColor;
        final float[] val = new float[]{Math.round(currentDb * 10f) / 10f};

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(ctx, 16);
        box.setPadding(pad, pad, pad, pad);

        EditText et = new EditText(ctx);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setText(String.format(Locale.US, "%.1f", val[0]));
        et.setTextColor(accent);
        et.setTextSize(22f);
        et.setGravity(Gravity.CENTER);
        box.addView(et);

        LinearLayout nudgeRow = new LinearLayout(ctx);
        nudgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nrLp.topMargin = UiStyle.dp(ctx, 10);

        String[] labels = new String[]{"-1.0", "-0.1", "0.0", "+0.1", "+1.0"};
        float[] deltas = new float[]{-1.0f, -0.1f, 0f, 0.1f, 1.0f};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView btn = UiStyle.makePill(ctx, labels[i], i == 2, accent);
            btn.setTextSize(12f);
            btn.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                try { val[0] = Float.parseFloat(et.getText().toString().trim()); }
                catch (Throwable ignored) {}
                if (idx == 2) val[0] = 0f;
                else val[0] = EqStore.clamp(Math.round((val[0] + deltas[idx]) * 10f) / 10f, -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                et.setText(String.format(Locale.US, "%.1f", val[0]));
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 4) blp.rightMargin = UiStyle.dp(ctx, 5);
            nudgeRow.addView(btn, blp);
        }
        box.addView(nudgeRow, nrLp);

        int soloIdx = (store.getSettings().bandMode == EqStore.BANDS_5) ? (bandIndex * 2) : bandIndex;
        final boolean[] soloOn = new boolean[]{false};
        TextView soloBtn = UiStyle.makePill(ctx, "🎧 Solo " + freqLabel + " Band", false, accent);
        soloBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            soloOn[0] = !soloOn[0];
            engine.setSoloBandIndex(soloOn[0] ? soloIdx : -1);
            soloBtn.setText(soloOn[0] ? ("🎧 SOLOING " + freqLabel + " (Tap to Stop)") : ("🎧 Solo " + freqLabel + " Band"));
            UiStyle.stylePill(ctx, soloBtn, soloOn[0], 0xFFFFB300);
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = UiStyle.dp(ctx, 10);
        box.addView(soloBtn, slp);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setTitle("Fine Tune • " + freqLabel + " (±18.0 dB)")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    engine.setSoloBandIndex(-1);
                    float parsed = val[0];
                    try { parsed = Float.parseFloat(et.getText().toString().trim()); }
                    catch (Throwable ignored) {}
                    parsed = EqStore.clamp(Math.round(parsed * 10f) / 10f, -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
                    pushUndoAndResetMorph.run();
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
                    EqService.startOrRefresh(ctx);
                    onApplied.run();
                })
                .setNegativeButton("Cancel", (d, w) -> engine.setSoloBandIndex(-1))
                .create();
        dlg.setOnDismissListener(d -> engine.setSoloBandIndex(-1));
        dlg.show();
    }

    static void showPickCompareSlotBDialog(Context ctx, EqStore store, EqEngine engine, Runnable onPicked) {
        List<EqStore.EqPreset> list = store.getPresets();
        String[] names = new String[list.size() + 1];
        names[0] = "None (A vs Flat only)";
        for (int i = 0; i < list.size(); i++) names[i + 1] = list.get(i).name;
        new AlertDialog.Builder(ctx)
                .setTitle("Pick A/B/C Comparison Slot B")
                .setItems(names, (d, which) -> {
                    store.getSettings().comparePresetId = which == 0 ? null : list.get(which - 1).id;
                    store.save();
                    engine.setAbFlatCompare(false);
                    EqService.startOrRefresh(ctx);
                    onPicked.run();
                    Toast.makeText(ctx, which == 0 ? "Slot B cleared" : ("Slot B: " + names[which] + " — tap A/B/C to compare"), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    static void showSleepTimerDialog(Context ctx, Runnable onChanged) {
        SleepTimerManager stm = SleepTimerManager.get(ctx);
        String[] opts = new String[]{
                "15 Minutes (fade out & shut down)",
                "30 Minutes (fade out & shut down)",
                "60 Minutes (fade out & shut down)",
                "Stop After Current Track",
                "Cancel Sleep Timer"
        };
        new AlertDialog.Builder(ctx)
                .setTitle("⏰ Sleep Timer  •  " + (stm.isActive() ? stm.getStatusLabel() : "Off"))
                .setItems(opts, (d, which) -> {
                    if (which == 0) { stm.startMinutes(15); Toast.makeText(ctx, "Sleep timer set for 15 minutes", Toast.LENGTH_SHORT).show(); }
                    else if (which == 1) { stm.startMinutes(30); Toast.makeText(ctx, "Sleep timer set for 30 minutes", Toast.LENGTH_SHORT).show(); }
                    else if (which == 2) { stm.startMinutes(60); Toast.makeText(ctx, "Sleep timer set for 60 minutes", Toast.LENGTH_SHORT).show(); }
                    else if (which == 3) { stm.setStopAfterCurrentTrack(); Toast.makeText(ctx, "Will stop & exit after current track", Toast.LENGTH_SHORT).show(); }
                    else { stm.cancel(); Toast.makeText(ctx, "Sleep timer cancelled", Toast.LENGTH_SHORT).show(); }
                    onChanged.run();
                })
                .show();
    }
}
