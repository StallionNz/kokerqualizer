package com.buddyapps.kokerqualizer;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Facade & shared UI helper kit for all slide-out drawer sub-panels.
 * Individual panel implementations live in {@link PresetPanels},
 * {@link RoutingAndSettingsPanels}, {@link AudioToolsPanels}, and
 * {@link YouTubeDrawerPanel}.
 */
public class DrawerPanels {
    public interface Host {
        void onStateMutated();
        void closePanel();
        void requestExportJson();
        void requestImportJson();
        void requestBtPermission();
        void requestAudioPermission();
        void requestNotifPermission();
        SoundTestEngine getSoundTestEngine();
        void onYtPlayTrack(int index);
        void onYtPlayDirectTrack(YouTubeStore.Track track);
        void onYtSyncNow();
        void onYtPlaylistChanged();
        void onYtFullWebChanged(boolean fullWeb);
    }

    interface PresetPickCb { void onPick(String id); }
    interface BoolCb { void onChange(boolean v); }

    // -------------------------------------------------------------------------
    // Panel Delegators (preserve clean public API for callers)
    // -------------------------------------------------------------------------
    public static View buildPresetsPanel(Context ctx, Host host, boolean[] favOnlyRef, String[] searchRef) {
        return PresetPanels.buildPresetsPanel(ctx, host, favOnlyRef, searchRef);
    }

    public static View buildAutoEqPanel(Context ctx, Host host, String[] filterRef) {
        return PresetPanels.buildAutoEqPanel(ctx, host, filterRef);
    }

    public static void showSaveAsDialog(Context ctx, EqStore store, EqStore.EqPreset base, Host host) {
        PresetPanels.showSaveAsDialog(ctx, store, base, host);
    }

    public static void showRenameDialog(Context ctx, EqStore store, EqStore.EqPreset p, Host host) {
        PresetPanels.showRenameDialog(ctx, store, p, host);
    }

    public static void showColorPicker(Context ctx, EqStore store, EqStore.EqPreset p, Host host) {
        PresetPanels.showColorPicker(ctx, store, p, host);
    }

    public static View buildBluetoothPanel(Context ctx, Host host) {
        return RoutingAndSettingsPanels.buildBluetoothPanel(ctx, host);
    }

    public static View buildHealthPanel(Context ctx, Host host) {
        return RoutingAndSettingsPanels.buildHealthPanel(ctx, host);
    }

    public static View buildSettingsPanel(Context ctx, Host host) {
        return RoutingAndSettingsPanels.buildSettingsPanel(ctx, host);
    }

    public static View buildSoundTestPanel(Context ctx, Host host) {
        return AudioToolsPanels.buildSoundTestPanel(ctx, host);
    }

    public static View buildCalibrationPanel(Context ctx, Host host, int[] stepRef, float[] offsetsRef) {
        return AudioToolsPanels.buildCalibrationPanel(ctx, host, stepRef, offsetsRef);
    }

    public static View buildYouTubePanel(Context ctx, Host host, String[] filterRef) {
        return YouTubeDrawerPanel.buildYouTubePanel(ctx, host, filterRef);
    }

    public static void showAddYouTubePlaylistDialog(Context ctx, Host host) {
        YouTubeDrawerPanel.showAddYouTubePlaylistDialog(ctx, host);
    }

    // -------------------------------------------------------------------------
    // Shared Panel Helpers & Backup/Restore Actions
    // -------------------------------------------------------------------------
    public static void saveBackupToDownloads(Context ctx, EqStore store) {
        try {
            // Privacy-safe local backup (presets, app rules & settings; excludes raw Bluetooth MAC addresses)
            String json = store.exportAllJson(false);
            String fileName = "kokerqualizer_backup.json";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kokerqualizer");
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(json.getBytes(StandardCharsets.UTF_8));
                            Toast.makeText(ctx, "Saved to Downloads/Kokerqualizer/" + fileName, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            }
            Toast.makeText(ctx, "Use Export JSON to pick a folder on Android 9", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(ctx, "Backup failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showRestoreFactoryDialog(Context ctx, EqStore store, Host host) {
        new AlertDialog.Builder(ctx)
                .setTitle("Restore Factory Presets?")
                .setMessage("Resets built-in presets to factory tuning and restores any deleted defaults. Your custom presets stay untouched.")
                .setPositiveButton("Restore", (d, w) -> {
                    int n = store.restoreFactoryPresets();
                    EqService.startOrRefresh(ctx);
                    Toast.makeText(ctx, "Restored " + n + " factory presets!", Toast.LENGTH_SHORT).show();
                    host.onStateMutated();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void addSmallAction(Context ctx, LinearLayout row, String label,
                               boolean active, int accent, int rightMargin, View.OnClickListener l) {
        TextView tv = UiStyle.makePill(ctx, label, active, accent);
        tv.setTextSize(11f);
        tv.setPadding(UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 5), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 5));
        tv.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = rightMargin;
        row.addView(tv, lp);
    }

    static Spinner makePresetSpinner(Context ctx, List<EqStore.EqPreset> presets,
                                     String currentId, PresetPickCb cb) {
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < presets.size(); i++) {
            names.add(presets.get(i).name);
            if (presets.get(i).id.equals(currentId)) sel = i;
        }
        Spinner sp = new Spinner(ctx);
        sp.setBackground(UiStyle.roundRect(UiStyle.SURFACE_ELEV, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 12)));
        ArrayAdapter<String> ad = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, names) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(UiStyle.TEXT_PRIMARY);
                tv.setTextSize(14f);
                tv.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 10));
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(UiStyle.TEXT_PRIMARY);
                tv.setBackgroundColor(UiStyle.SURFACE_ELEV);
                tv.setTextSize(14f);
                tv.setPadding(UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 12));
                return tv;
            }
        };
        sp.setAdapter(ad);
        sp.setSelection(sel, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < presets.size()) cb.onPick(presets.get(pos).id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return sp;
    }

    static void stylePlayBtn(Context ctx, TextView playBtn, boolean playing, int accent) {
        playBtn.setText(playing ? "■  STOP TEST TONE" : "▶  START TEST TONE");
        playBtn.setTextColor(playing ? 0xFFFF5252 : accent);
        playBtn.setTextSize(15f);
        playBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 14));
        playBtn.setBackground(UiStyle.roundRect(
                playing ? 0x33FF1744 : EqStore.withAlpha(accent, 38),
                playing ? 0xFFFF5252 : accent,
                UiStyle.dp(ctx, 1.5f), UiStyle.dp(ctx, 16)));
    }

    static void addSettingSwitch(Context ctx, LinearLayout parent, String label,
                                 boolean checked, BoolCb cb) {
        Switch sw = new Switch(ctx);
        sw.setText(label);
        sw.setTextColor(UiStyle.TEXT_PRIMARY);
        sw.setTextSize(13.5f);
        sw.setChecked(checked);
        sw.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 8));
        sw.setOnCheckedChangeListener((b, v) -> cb.onChange(v));
        parent.addView(sw);
    }

    static LinearLayout makeCard(Context ctx, int borderColor) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, EqStore.withAlpha(borderColor, 110), UiStyle.dp(ctx, 1.2f), UiStyle.dp(ctx, 18)));
        c.setPadding(UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyle.dp(ctx, 12);
        c.setLayoutParams(lp);
        return c;
    }
}
