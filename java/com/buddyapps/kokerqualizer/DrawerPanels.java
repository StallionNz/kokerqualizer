package com.buddyapps.kokerqualizer;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
        void onYtSyncNow();
        void onYtPlaylistChanged();
        void onYtFullWebChanged(boolean fullWeb);
    }

    // =========================================================================
    // 1. PRESETS LIBRARY
    // =========================================================================
    public static View buildPresetsPanel(Context ctx, Host host, boolean[] favOnlyRef) {
        EqStore store = EqStore.get(ctx);
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView favFilter = UiStyle.makePill(ctx, favOnlyRef[0] ? "★ Favorites Only" : "☆ All Presets", favOnlyRef[0], accent);
        favFilter.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            favOnlyRef[0] = !favOnlyRef[0];
            host.onStateMutated();
        });
        actions.addView(favFilter);

        actions.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView saveNew = UiStyle.makePill(ctx, "+ Save As New", true, accent);
        saveNew.setOnClickListener(v -> showSaveAsDialog(ctx, store, active, host));
        actions.addView(saveNew);
        wrap.addView(actions);

        LinearLayout ioRow = new LinearLayout(ctx);
        ioRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ioLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ioLp.topMargin = UiStyle.dp(ctx, 10);
        ioLp.bottomMargin = UiStyle.dp(ctx, 12);

        TextView expBtn = UiStyle.makePill(ctx, "Export JSON", false, accent);
        expBtn.setOnClickListener(v -> host.requestExportJson());
        TextView impBtn = UiStyle.makePill(ctx, "Import JSON", false, accent);
        impBtn.setOnClickListener(v -> host.requestImportJson());
        TextView shareBtn = UiStyle.makePill(ctx, "Share Active", false, accent);
        shareBtn.setOnClickListener(v -> {
            if (active == null) return;
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Kokerqualizer Preset: " + active.name);
            share.putExtra(Intent.EXTRA_TEXT, store.exportSinglePresetJson(active));
            ctx.startActivity(Intent.createChooser(share, "Share Kokerqualizer Preset"));
        });

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pLp.rightMargin = UiStyle.dp(ctx, 6);
        ioRow.addView(expBtn, pLp);
        ioRow.addView(impBtn, pLp);
        ioRow.addView(shareBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wrap.addView(ioRow, ioLp);

        List<EqStore.EqPreset> list = store.getPresets();
        for (EqStore.EqPreset p : list) {
            if (favOnlyRef[0] && !p.favorite) continue;
            boolean isActive = active != null && p.id.equals(active.id);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(UiStyle.roundRect(
                    isActive ? EqStore.withAlpha(p.accentColor, 26) : UiStyle.SURFACE_CARD,
                    isActive ? p.accentColor : UiStyle.BORDER_SUBTLE,
                    UiStyle.dp(ctx, isActive ? 1.5f : 1f),
                    UiStyle.dp(ctx, 16)));
            card.setPadding(UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 12));

            LinearLayout row1 = new LinearLayout(ctx);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(ctx);
            dot.setBackground(UiStyle.roundRect(p.accentColor, p.accentColor, 0, UiStyle.dp(ctx, 7)));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 14));
            dLp.rightMargin = UiStyle.dp(ctx, 10);
            row1.addView(dot, dLp);

            TextView nameTv = new TextView(ctx);
            nameTv.setText(p.name);
            nameTv.setTextColor(isActive ? p.accentColor : UiStyle.TEXT_PRIMARY);
            nameTv.setTextSize(15.5f);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            row1.addView(nameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView meta = new TextView(ctx);
            meta.setText((p.carSubMode ? "SUB" : "ISO") + " • " + EqStore.bassStyleName(p.bassStyle) + " • MBC " + Math.round(p.punchMbc) + "%");
            meta.setTextColor(UiStyle.TEXT_MUTED);
            meta.setTextSize(10.5f);
            row1.addView(meta);
            card.addView(row1);

            card.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                store.setActivePreset(p.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqService.startOrRefresh(ctx);
                host.onStateMutated();
            });

            HorizontalScrollView hsv = new HorizontalScrollView(ctx);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout ctrls = new LinearLayout(ctx);
            ctrls.setOrientation(LinearLayout.HORIZONTAL);
            int gap = UiStyle.dp(ctx, 6);

            addSmallAction(ctx, ctrls, p.favorite ? "★ Fav" : "☆ Fav", p.favorite, p.accentColor, gap, v -> {
                p.favorite = !p.favorite;
                store.addOrUpdatePreset(p);
                host.onStateMutated();
            });
            addSmallAction(ctx, ctrls, p.pinnedCar ? "🚗 Pinned" : "🚗 Pin Car", p.pinnedCar, p.accentColor, gap, v -> {
                p.pinnedCar = !p.pinnedCar;
                store.addOrUpdatePreset(p);
                host.onStateMutated();
            });
            addSmallAction(ctx, ctrls, "🎨 Color", false, p.accentColor, gap, v -> showColorPicker(ctx, store, p, host));
            addSmallAction(ctx, ctrls, "Duplicate", false, p.accentColor, gap, v -> {
                EqStore.EqPreset dup = p.copy("usr_" + UUID.randomUUID().toString().substring(0, 8), p.name + " Copy", false);
                store.addOrUpdatePreset(dup);
                store.setActivePreset(dup.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqService.startOrRefresh(ctx);
                host.onStateMutated();
            });
            addSmallAction(ctx, ctrls, "Rename", false, p.accentColor, gap, v -> showRenameDialog(ctx, store, p, host));
            addSmallAction(ctx, ctrls, "Share", false, p.accentColor, gap, v -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, "Kokerqualizer: " + p.name);
                share.putExtra(Intent.EXTRA_TEXT, store.exportSinglePresetJson(p));
                ctx.startActivity(Intent.createChooser(share, "Share Preset"));
            });
            if (!p.builtIn || list.size() > 1) {
                addSmallAction(ctx, ctrls, "Delete", false, 0xFFFF5252, gap, v -> {
                    if (store.deletePreset(p.id)) {
                        EqService.startOrRefresh(ctx);
                        host.onStateMutated();
                    }
                });
            }

            hsv.addView(ctrls);
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hLp.topMargin = UiStyle.dp(ctx, 10);
            card.addView(hsv, hLp);

            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cLp.bottomMargin = UiStyle.dp(ctx, 10);
            wrap.addView(card, cLp);
        }
        return wrap;
    }

    private static void addSmallAction(Context ctx, LinearLayout row, String label,
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

    public static void showSaveAsDialog(Context ctx, EqStore store, EqStore.EqPreset base, Host host) {
        EditText et = new EditText(ctx);
        et.setHint("Preset name (e.g. Car Night Sub)");
        et.setText(base != null ? base.name + " Custom" : "My Preset");
        et.setTextColor(UiStyle.TEXT_PRIMARY);
        et.setHintTextColor(UiStyle.TEXT_MUTED);
        int pad = UiStyle.dp(ctx, 16);
        et.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(ctx)
                .setTitle("Save Current Settings As Preset")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Custom Preset";
                    EqStore.EqPreset copy = (base != null ? base : new EqStore.EqPreset())
                            .copy("usr_" + UUID.randomUUID().toString().substring(0, 8), name, false);
                    store.addOrUpdatePreset(copy);
                    store.setActivePreset(copy.id);
                    EqService.startOrRefresh(ctx);
                    host.onStateMutated();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showRenameDialog(Context ctx, EqStore store, EqStore.EqPreset p, Host host) {
        EditText et = new EditText(ctx);
        et.setText(p.name);
        et.setTextColor(UiStyle.TEXT_PRIMARY);
        int pad = UiStyle.dp(ctx, 16);
        et.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(ctx)
                .setTitle("Rename Preset")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        p.name = name;
                        store.addOrUpdatePreset(p);
                        EqService.startOrRefresh(ctx);
                        host.onStateMutated();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showColorPicker(Context ctx, EqStore store, EqStore.EqPreset p, Host host) {
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 18), UiStyle.dp(ctx, 14), UiStyle.dp(ctx, 18));
        grid.setGravity(Gravity.CENTER);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Neon Glow Color • " + p.name)
                .setView(grid)
                .setNegativeButton("Cancel", null)
                .create();

        for (int color : EqStore.ACCENT_PALETTE) {
            View swatch = new View(ctx);
            swatch.setBackground(UiStyle.roundRect(color, 0xFFFFFFFF,
                    color == p.accentColor ? UiStyle.dp(ctx, 2.5f) : 0, UiStyle.dp(ctx, 14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiStyle.dp(ctx, 26), UiStyle.dp(ctx, 26));
            lp.setMargins(UiStyle.dp(ctx, 3), 0, UiStyle.dp(ctx, 3), 0);
            swatch.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                p.accentColor = color;
                store.addOrUpdatePreset(p);
                EqService.startOrRefresh(ctx);
                dialog.dismiss();
                host.onStateMutated();
            });
            grid.addView(swatch, lp);
        }
        dialog.show();
    }

    // =========================================================================
    // 2. DEVICES AUTOMATION (Wired/USB-C Headphones + Bluetooth Auto-Start + Disconnect)
    // =========================================================================
    public static View buildBluetoothPanel(Context ctx, Host host) {
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;
        List<EqStore.EqPreset> presets = store.getPresets();

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        // Live active output route pill
        TextView routeBanner = new TextView(ctx);
        routeBanner.setText("Active Output:  " + EqEngine.get(ctx).getActiveOutputRouteLabel());
        routeBanner.setTextColor(accent);
        routeBanner.setTextSize(13f);
        routeBanner.setTypeface(null, android.graphics.Typeface.BOLD);
        routeBanner.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 8));
        routeBanner.setBackground(UiStyle.roundRect(EqStore.withAlpha(accent, 24), EqStore.withAlpha(accent, 130), UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 14)));
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rbLp.bottomMargin = UiStyle.dp(ctx, 12);
        wrap.addView(routeBanner, rbLp);

        // Wired & USB-C Headphone / DAC Auto-Detection Card
        LinearLayout wiredCard = makeCard(ctx, s.wiredAutoSwitch ? accent : UiStyle.BORDER_SUBTLE);
        Switch wiredSw = new Switch(ctx);
        wiredSw.setText("🎧 Auto-Switch on Wired / USB-C Headphones");
        wiredSw.setTextColor(UiStyle.TEXT_PRIMARY);
        wiredSw.setTextSize(14f);
        wiredSw.setTypeface(null, android.graphics.Typeface.BOLD);
        wiredSw.setChecked(s.wiredAutoSwitch);
        wiredSw.setOnCheckedChangeListener((b, checked) -> {
            s.wiredAutoSwitch = checked;
            store.save();
            host.onStateMutated();
        });
        wiredCard.addView(wiredSw);

        if (s.wiredAutoSwitch) {
            TextView wLbl = new TextView(ctx);
            wLbl.setText("Preset when Wired / USB-C DAC plugs in:");
            wLbl.setTextColor(UiStyle.TEXT_MUTED);
            wLbl.setTextSize(11.5f);
            wLbl.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 4));
            wiredCard.addView(wLbl);

            wiredCard.addView(makePresetSpinner(ctx, presets, s.wiredPresetId, selectedId -> {
                s.wiredPresetId = selectedId;
                store.save();
            }));
        }
        wrap.addView(wiredCard);

        // Global Disconnect Fallback Card
        LinearLayout discCard = makeCard(ctx, accent);
        TextView discTitle = new TextView(ctx);
        discTitle.setText("On Disconnect → Switch To Phone Preset");
        discTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        discTitle.setTextSize(14f);
        discTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        discTitle.setPadding(0, 0, 0, UiStyle.dp(ctx, 6));
        discCard.addView(discTitle);

        discCard.addView(makePresetSpinner(ctx, presets, s.phonePresetId, selectedId -> {
            s.phonePresetId = selectedId;
            store.save();
        }));
        wrap.addView(discCard);

        boolean hasBtPerm = Build.VERSION.SDK_INT < 31 ||
                ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

        if (!hasBtPerm) {
            LinearLayout permCard = makeCard(ctx, 0xFFFFB300);
            TextView msg = new TextView(ctx);
            msg.setText("Grant Bluetooth permission so Kokerqualizer can list your paired car stereo and portable speakers for auto-start.");
            msg.setTextColor(UiStyle.TEXT_SECONDARY);
            msg.setTextSize(13f);
            permCard.addView(msg);

            TextView btn = UiStyle.makePill(ctx, "Grant Bluetooth Permission", true, accent);
            btn.setOnClickListener(v -> host.requestBtPermission());
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bLp.topMargin = UiStyle.dp(ctx, 10);
            permCard.addView(btn, bLp);
            wrap.addView(permCard);
            return wrap;
        }

        List<EqStore.BtDeviceRule> pairedList = new ArrayList<>();
        try {
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
            if (adapter != null) {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        EqStore.BtDeviceRule rule = store.findBtRule(d.getAddress());
                        if (rule == null) {
                            rule = new EqStore.BtDeviceRule();
                            rule.address = d.getAddress();
                            rule.name = d.getName() != null ? d.getName() : d.getAddress();
                        } else if (d.getName() != null) {
                            rule.name = d.getName();
                        }
                        pairedList.add(rule);
                    }
                }
            }
        } catch (SecurityException ignored) {}

        if (pairedList.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("No paired Bluetooth devices found. Pair your car stereo or portable speaker in Android Bluetooth settings, then come back here to enable auto-start.");
            empty.setTextColor(UiStyle.TEXT_SECONDARY);
            empty.setTextSize(13.5f);
            empty.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 12));
            wrap.addView(empty);
            return wrap;
        }

        for (EqStore.BtDeviceRule rule : pairedList) {
            LinearLayout card = makeCard(ctx, rule.autoStart ? accent : UiStyle.BORDER_SUBTLE);

            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView devName = new TextView(ctx);
            devName.setText(rule.name);
            devName.setTextColor(UiStyle.TEXT_PRIMARY);
            devName.setTextSize(15f);
            devName.setTypeface(null, android.graphics.Typeface.BOLD);
            header.addView(devName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Switch autoSw = new Switch(ctx);
            autoSw.setText("Auto-Start ");
            autoSw.setTextColor(UiStyle.TEXT_SECONDARY);
            autoSw.setChecked(rule.autoStart);
            autoSw.setOnCheckedChangeListener((b, checked) -> {
                rule.autoStart = checked;
                store.putBtRule(rule);
                host.onStateMutated();
            });
            header.addView(autoSw);
            card.addView(header);

            if (rule.autoStart) {
                TextView pLbl = new TextView(ctx);
                pLbl.setText("Assigned Preset:");
                pLbl.setTextColor(UiStyle.TEXT_MUTED);
                pLbl.setTextSize(11.5f);
                pLbl.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 4));
                card.addView(pLbl);

                card.addView(makePresetSpinner(ctx, presets, rule.presetId, pid -> {
                    rule.presetId = pid;
                    store.putBtRule(rule);
                }));

                Switch volSw = new Switch(ctx);
                volSw.setText(String.format(Locale.US, "Set Media Volume on Connect (%d%%)", rule.volumePercent));
                volSw.setTextColor(UiStyle.TEXT_SECONDARY);
                volSw.setChecked(rule.setVolume);
                volSw.setPadding(0, UiStyle.dp(ctx, 6), 0, UiStyle.dp(ctx, 4));
                card.addView(volSw);

                SeekBar volBar = new SeekBar(ctx);
                volBar.setMax(100);
                volBar.setProgress(rule.volumePercent);
                UiStyle.tintSeekBar(volBar, accent);
                volBar.setVisibility(rule.setVolume ? View.VISIBLE : View.GONE);
                volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                        if (!fromUser) return;
                        rule.volumePercent = p;
                        volSw.setText(String.format(Locale.US, "Set Media Volume on Connect (%d%%)", p));
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) {}
                    @Override public void onStopTrackingTouch(SeekBar sb) { store.putBtRule(rule); }
                });
                volSw.setOnCheckedChangeListener((b, checked) -> {
                    rule.setVolume = checked;
                    volBar.setVisibility(checked ? View.VISIBLE : View.GONE);
                    store.putBtRule(rule);
                });
                card.addView(volBar);

                Switch carSw = new Switch(ctx);
                carSw.setText("Auto-Launch Car Mode Dash");
                carSw.setTextColor(UiStyle.TEXT_SECONDARY);
                carSw.setChecked(rule.autoCarMode);
                carSw.setPadding(0, UiStyle.dp(ctx, 4), 0, UiStyle.dp(ctx, 8));
                carSw.setOnCheckedChangeListener((b, checked) -> {
                    rule.autoCarMode = checked;
                    store.putBtRule(rule);
                });
                card.addView(carSw);

                TextView testBtn = UiStyle.makePill(ctx, "▶ Test This Rule Now", true, accent);
                testBtn.setOnClickListener(v -> {
                    UiStyle.hapticTick(v, ctx);
                    EqService.applyBtRuleDirect(ctx, rule, true);
                    EqService.startOrRefresh(ctx);
                    Toast.makeText(ctx, "Applied rule for " + rule.name, Toast.LENGTH_SHORT).show();
                    host.onStateMutated();
                });
                card.addView(testBtn, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            wrap.addView(card);
        }
        return wrap;
    }

    private interface PresetPickCb { void onPick(String id); }

    private static Spinner makePresetSpinner(Context ctx, List<EqStore.EqPreset> presets,
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

    // =========================================================================
    // 3. SOUND TEST SUITE
    // =========================================================================
    public static View buildSoundTestPanel(Context ctx, Host host) {
        EqStore.EqPreset active = EqStore.get(ctx).getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;
        SoundTestEngine ste = host.getSoundTestEngine();

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout mainCard = makeCard(ctx, accent);

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
        stylePlayBtn(ctx, playBtn, ste.isPlaying(), accent);
        playBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            if (ste.isPlaying()) {
                ste.stop();
                stylePlayBtn(ctx, playBtn, false, accent);
            } else {
                // Pause in-app YouTube so test tones play crystal clear
                YouTubePlayerView.RemoteBridge yt = YouTubePlayerView.getActiveRemoteBridge();
                if (yt != null && yt.isPlaying()) yt.togglePlayPause();
                ste.start();
                stylePlayBtn(ctx, playBtn, true, accent);
            }
        });
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = UiStyle.dp(ctx, 16);
        mainCard.addView(playBtn, pbLp);

        wrap.addView(mainCard);
        return wrap;
    }

    private static void stylePlayBtn(Context ctx, TextView playBtn, boolean playing, int accent) {
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

    // =========================================================================
    // 4. ONE-TAP AUDIO HEALTH CHECK DIAGNOSTIC CHECKLIST
    // =========================================================================
    public static View buildHealthPanel(Context ctx, Host host) {
        EqEngine engine = EqEngine.get(ctx);
        EqStore.EqPreset active = EqStore.get(ctx).getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        boolean dspOk = engine.isDynamicsProcessingActive();
        boolean bubbleOk = BubbleOverlayManager.canDraw(ctx);
        boolean mediaOk = KokerMediaListenerService.hasMediaAccess(ctx);
        boolean micOk = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean btOk = Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        boolean notifOk = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean batOk = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());

        addHealthRow(ctx, wrap,
                "10-Band DSP + 3-Band MBC + Limiter",
                dspOk ? ("Active (" + engine.getStatusText() + ") • Output: " + engine.getActiveOutputRouteLabel())
                        : "Engine is currently off or waiting for audio.",
                dspOk, "Re-Init Engine", accent, v -> {
                    EqStore.get(ctx).getSettings().eqEnabled = true;
                    EqStore.get(ctx).save();
                    engine.rebuildIfNeeded(true);
                    EqService.startOrRefresh(ctx);
                    host.onStateMutated();
                });

        addHealthRow(ctx, wrap,
                "Floating Bubble Overlay (Appear on Top)",
                bubbleOk ? "Granted — Kokerqualizer bubble can float over YouTube, Spotify & Waze."
                        : "Allow 'Appear on top' so the floating Kokerqualizer bubble works over any app.",
                bubbleOk, "Allow Overlay", accent, v -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.getPackageName()));
                        ctx.startActivity(i);
                    } catch (Throwable t) {
                        Toast.makeText(ctx, "Enable Appear on top in Settings → Apps → Kokerqualizer", Toast.LENGTH_LONG).show();
                    }
                });

        addHealthRow(ctx, wrap,
                "Song Skip & Live Now Playing Control",
                mediaOk ? "Enabled — Car Mode Prev/Play/Next directly controls Spotify, YouTube & music apps."
                        : "Enable Notification Access for Kokerqualizer so Next/Prev skips songs reliably in all music apps.",
                mediaOk, "Enable Media Access", accent, v -> {
                    try {
                        ctx.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                        Toast.makeText(ctx, "Turn on Kokerqualizer Media Control", Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {
                        Toast.makeText(ctx, "Open Settings → Notification access → Kokerqualizer", Toast.LENGTH_LONG).show();
                    }
                });

        addHealthRow(ctx, wrap,
                "Live Spectrum, Wave & Bass-Reactive Glow",
                micOk ? "Granted — live FFT visualizer & reactive glow active."
                        : "Needs audio capture permission for real-time bars, wave & VU meter.",
                micOk, "Grant Mic Viz", accent, v -> host.requestAudioPermission());

        addHealthRow(ctx, wrap,
                "Bluetooth Car & Speaker Auto-Start",
                btOk ? "Granted — can detect paired car stereo & portable speakers."
                        : "Needs Bluetooth Connect permission to detect your car stereo.",
                btOk, "Grant Bluetooth", accent, v -> host.requestBtPermission());

        addHealthRow(ctx, wrap,
                "Background Controls Notification",
                notifOk ? "Granted — interactive Bypass & Prev/Next controls active."
                        : "Needs notification permission for lock-screen/shade preset controls.",
                notifOk, "Allow Notifications", accent, v -> host.requestNotifPermission());

        addHealthRow(ctx, wrap,
                "Unrestricted Background Battery",
                batOk ? "Unrestricted — Android will keep Kokerqualizer running smoothly."
                        : "Battery optimization may sleep the EQ after a few minutes.",
                batOk, "Allow Unrestricted", accent, v -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + ctx.getPackageName()));
                        ctx.startActivity(i);
                    } catch (Throwable t) {
                        try { ctx.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                        catch (Throwable ignored) {}
                    }
                });

        addHealthRow(ctx, wrap,
                "Samsung Dolby Atmos / Built-In EQ Check",
                "If audio sounds unchanged or fights your curves, turn off Samsung's built-in Dolby Atmos / Equalizer.",
                false, "Open Sound Settings", accent, v -> {
                    try { ctx.startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); }
                    catch (Throwable t) { Toast.makeText(ctx, "Open Settings → Sounds and vibration", Toast.LENGTH_SHORT).show(); }
                });

        return wrap;
    }

    private static void addHealthRow(Context ctx, LinearLayout parent, String title, String desc,
                                     boolean ok, String fixLabel, int accent, View.OnClickListener fixAction) {
        int col = ok ? 0xFF00E676 : 0xFFFFB300;
        LinearLayout card = makeCard(ctx, col);

        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(ctx);
        badge.setText(ok ? "✓" : "!");
        badge.setTextColor(col);
        badge.setTextSize(15f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(UiStyle.roundRect(EqStore.withAlpha(col, 34), col, UiStyle.dp(ctx, 1.2f), UiStyle.dp(ctx, 12)));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(UiStyle.dp(ctx, 24), UiStyle.dp(ctx, 24));
        bLp.rightMargin = UiStyle.dp(ctx, 10);
        hdr.addView(badge, bLp);

        TextView tTv = new TextView(ctx);
        tTv.setText(title);
        tTv.setTextColor(UiStyle.TEXT_PRIMARY);
        tTv.setTextSize(14f);
        tTv.setTypeface(null, android.graphics.Typeface.BOLD);
        hdr.addView(tTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(hdr);

        TextView dTv = new TextView(ctx);
        dTv.setText(desc);
        dTv.setTextColor(UiStyle.TEXT_SECONDARY);
        dTv.setTextSize(12.5f);
        dTv.setPadding(0, UiStyle.dp(ctx, 6), 0, ok ? 0 : UiStyle.dp(ctx, 10));
        card.addView(dTv);

        if (!ok) {
            TextView btn = UiStyle.makePill(ctx, fixLabel, true, accent);
            btn.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                fixAction.onClick(v);
            });
            card.addView(btn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        parent.addView(card);
    }

    // =========================================================================
    // 5. SETTINGS
    // =========================================================================
    public static View buildSettingsPanel(Context ctx, Host host) {
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hookCard = makeCard(ctx, accent);
        TextView hTitle = new TextView(ctx);
        hTitle.setText("Audio Engine Hook Mode");
        hTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        hTitle.setTextSize(14.5f);
        hTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        hookCard.addView(hTitle);

        TextView hSub = new TextView(ctx);
        hSub.setText("Dual Mode automatically hooks active music apps and falls back to Global Mix so YouTube, Spotify & Chrome all stay tuned.");
        hSub.setTextColor(UiStyle.TEXT_MUTED);
        hSub.setTextSize(12f);
        hSub.setPadding(0, UiStyle.dp(ctx, 4), 0, UiStyle.dp(ctx, 10));
        hookCard.addView(hSub);

        LinearLayout hRow = new LinearLayout(ctx);
        hRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] hNames = new String[]{"Dual (Auto)", "Global Mix", "Session Only"};
        TextView[] hBtns = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            hBtns[i] = UiStyle.makePill(ctx, hNames[i], s.hookMode == idx, accent);
            hBtns[i].setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                s.hookMode = idx;
                store.save();
                EqEngine.get(ctx).rebuildIfNeeded(true);
                EqService.startOrRefresh(ctx);
                for (int j = 0; j < 3; j++) UiStyle.stylePill(ctx, hBtns[j], j == idx, accent);
                host.onStateMutated();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 6);
            hRow.addView(hBtns[i], lp);
        }
        hookCard.addView(hRow);
        wrap.addView(hookCard);

        LinearLayout swCard = makeCard(ctx, UiStyle.BORDER_SUBTLE);
        addSettingSwitch(ctx, swCard, "Smart Auto-Gain (prevents volume jumps)", s.autoGain, val -> {
            s.autoGain = val;
            store.save();
            EqEngine.get(ctx).applyCurrent();
            host.onStateMutated();
        });
        addSettingSwitch(ctx, swCard, "Bass-Reactive Neon Glow on Deck Cards", s.reactiveGlow, val -> {
            s.reactiveGlow = val;
            store.save();
            host.onStateMutated();
        });
        addSettingSwitch(ctx, swCard, "Subtle Haptic Feedback on Knobs & Sliders", s.hapticsEnabled, val -> {
            s.hapticsEnabled = val;
            store.save();
        });
        addSettingSwitch(ctx, swCard, "Start Engine Automatically on Phone Boot", s.startOnBoot, val -> {
            s.startOnBoot = val;
            store.save();
        });
        addSettingSwitch(ctx, swCard, "Keep Screen Awake in Car Mode", s.carKeepScreenOn, val -> {
            s.carKeepScreenOn = val;
            store.save();
        });
        addSettingSwitch(ctx, swCard, "Live Visualizer Backdrop in Car Mode", s.carVizBackdrop, val -> {
            s.carVizBackdrop = val;
            store.save();
        });
        addSettingSwitch(ctx, swCard, "🫧 Floating Bubble Companion (over all apps)", s.bubbleEnabled && BubbleOverlayManager.canDraw(ctx), val -> {
            if (val && !BubbleOverlayManager.canDraw(ctx)) {
                s.bubbleEnabled = true;
                store.save();
                try {
                    ctx.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + ctx.getPackageName())));
                    Toast.makeText(ctx, "Allow 'Appear on top' for Kokerqualizer", Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
                return;
            }
            s.bubbleEnabled = val;
            store.save();
            EqService.startOrRefresh(ctx);
            host.onStateMutated();
        });
        addSettingSwitch(ctx, swCard, "Auto-Show Floating Bubble on Bluetooth Connect", s.bubbleAutoOnBt, val -> {
            s.bubbleAutoOnBt = val;
            store.save();
        });
        wrap.addView(swCard);

        return wrap;
    }

    // =========================================================================
    // 6. MULTI-PLAYLIST YOUTUBE MANAGER & LIVE TRACK BROWSER
    // =========================================================================
    public static View buildYouTubePanel(Context ctx, Host host, String[] filterRef) {
        YouTubeStore ytStore = YouTubeStore.get(ctx);
        YouTubeStore.YtState ys = ytStore.getState();
        YouTubeStore.SavedPlaylist activePl = ytStore.getActivePlaylist();
        EqStore.EqPreset eqP = EqStore.get(ctx).getActivePreset();
        int accent = eqP != null ? eqP.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        // Top action row: + Add Playlist & Sync Now
        LinearLayout topActions = new LinearLayout(ctx);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.CENTER_VERTICAL);

        TextView addBtn = UiStyle.makePill(ctx, "+ Add YouTube Playlist", true, accent);
        addBtn.setOnClickListener(v -> showAddYouTubePlaylistDialog(ctx, host));
        topActions.addView(addBtn);

        topActions.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));

        if (activePl != null) {
            TextView syncBtn = UiStyle.makePill(ctx, "↻ Sync Now", false, accent);
            syncBtn.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                host.onYtSyncNow();
            });
            topActions.addView(syncBtn);
        }
        wrap.addView(topActions);

        // Saved playlists selector strip (supports multiple playlists!)
        List<YouTubeStore.SavedPlaylist> all = ytStore.getPlaylists();
        if (!all.isEmpty()) {
            HorizontalScrollView hsv = new HorizontalScrollView(ctx);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = new LinearLayout(ctx);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            for (YouTubeStore.SavedPlaylist pl : all) {
                boolean sel = activePl != null && pl.id.equals(activePl.id);
                TextView chip = UiStyle.makePill(ctx, pl.name + " (" + pl.tracks.size() + ")", sel, accent);
                chip.setOnClickListener(v -> {
                    UiStyle.hapticTick(v, ctx);
                    ytStore.setActivePlaylist(pl.id);
                    host.onYtPlaylistChanged();
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = UiStyle.dp(ctx, 7);
                chipRow.addView(chip, clp);
            }
            hsv.addView(chipRow);
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hLp.topMargin = UiStyle.dp(ctx, 12);
            hLp.bottomMargin = UiStyle.dp(ctx, 10);
            wrap.addView(hsv, hLp);
        }

        // Startup & Playback Settings Card
        LinearLayout cfgCard = makeCard(ctx, UiStyle.BORDER_SUBTLE);
        addSettingSwitch(ctx, cfgCard, "Auto-sync playlist on app startup", ys.autoSyncOnStartup, v -> {
            ys.autoSyncOnStartup = v;
            ytStore.save();
        });
        addSettingSwitch(ctx, cfgCard, "Auto-load & play playlist on startup", ys.autoPlayOnStartup, v -> {
            ys.autoPlayOnStartup = v;
            ytStore.save();
        });
        addSettingSwitch(ctx, cfgCard, "Full YouTube Web Mode (Private playlists / Sign-in)", ys.fullWebMode, v -> {
            ys.fullWebMode = v;
            ytStore.save();
            host.onYtFullWebChanged(v);
        });
        wrap.addView(cfgCard);

        if (activePl == null) {
            LinearLayout emptyCard = makeCard(ctx, accent);
            TextView msg = new TextView(ctx);
            msg.setText("No YouTube playlists added yet. Tap '+ Add YouTube Playlist' above and paste any YouTube or YouTube Music playlist link (Public or Unlisted for live track sync, or turn on Full YouTube Web Mode to sign in for private playlists).");
            msg.setTextColor(UiStyle.TEXT_SECONDARY);
            msg.setTextSize(13.5f);
            emptyCard.addView(msg);
            wrap.addView(emptyCard);
            return wrap;
        }

        // Active Playlist Header + Delete option
        LinearLayout plMetaRow = new LinearLayout(ctx);
        plMetaRow.setOrientation(LinearLayout.HORIZONTAL);
        plMetaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pmLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pmLp.bottomMargin = UiStyle.dp(ctx, 8);

        String syncedAgo = formatSyncedAgo(activePl.lastSyncedMs);
        TextView metaTv = new TextView(ctx);
        metaTv.setText(activePl.name + "  •  " + activePl.tracks.size() + " tracks  •  " + syncedAgo);
        metaTv.setTextColor(UiStyle.TEXT_SECONDARY);
        metaTv.setTextSize(12f);
        plMetaRow.addView(metaTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView delBtn = UiStyle.makePill(ctx, "Remove", false, 0xFFFF5252);
        delBtn.setTextSize(10.5f);
        delBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            ytStore.deletePlaylist(activePl.id);
            host.onYtPlaylistChanged();
        });
        plMetaRow.addView(delBtn);
        wrap.addView(plMetaRow, pmLp);

        // Quick Search Filter Bar
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText searchEt = new EditText(ctx);
        searchEt.setHint("Filter tracks in " + activePl.name + "...");
        searchEt.setText(filterRef[0]);
        searchEt.setTextColor(UiStyle.TEXT_PRIMARY);
        searchEt.setHintTextColor(UiStyle.TEXT_MUTED);
        searchEt.setTextSize(13.5f);
        searchEt.setSingleLine(true);
        searchEt.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 14)));
        searchEt.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 9));
        searchRow.addView(searchEt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView goFilter = UiStyle.makePill(ctx, "Filter", false, accent);
        goFilter.setOnClickListener(v -> {
            filterRef[0] = searchEt.getText().toString().trim();
            host.onStateMutated();
        });
        LinearLayout.LayoutParams gfLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gfLp.leftMargin = UiStyle.dp(ctx, 6);
        searchRow.addView(goFilter, gfLp);

        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.bottomMargin = UiStyle.dp(ctx, 10);
        wrap.addView(searchRow, srLp);

        // Track list
        String q = filterRef[0] != null ? filterRef[0].toLowerCase(Locale.US) : "";
        List<YouTubeStore.Track> tracks = activePl.tracks;
        if (tracks.isEmpty()) {
            TextView noTracks = new TextView(ctx);
            noTracks.setText("No tracks synced yet. Tap '↻ Sync Now' above (make sure your playlist is Public or Unlisted).");
            noTracks.setTextColor(UiStyle.TEXT_MUTED);
            noTracks.setTextSize(13f);
            noTracks.setPadding(UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 4), UiStyle.dp(ctx, 8));
            wrap.addView(noTracks);
        } else {
            for (int i = 0; i < tracks.size(); i++) {
                YouTubeStore.Track t = tracks.get(i);
                if (!q.isEmpty()
                        && !t.title.toLowerCase(Locale.US).contains(q)
                        && !t.channel.toLowerCase(Locale.US).contains(q)) {
                    continue;
                }
                final int idx = i;
                boolean cur = (i == ys.currentIndex);

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(UiStyle.roundRect(
                        cur ? EqStore.withAlpha(accent, 30) : UiStyle.SURFACE_CARD,
                        cur ? accent : UiStyle.BORDER_SUBTLE,
                        UiStyle.dp(ctx, cur ? 1.4f : 0.8f),
                        UiStyle.dp(ctx, 14)));
                row.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 10));

                TextView numTv = new TextView(ctx);
                numTv.setText(cur ? "▶" : String.valueOf(i + 1));
                numTv.setTextColor(cur ? accent : UiStyle.TEXT_MUTED);
                numTv.setTextSize(12.5f);
                numTv.setTypeface(null, android.graphics.Typeface.BOLD);
                numTv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(UiStyle.dp(ctx, 26), LinearLayout.LayoutParams.WRAP_CONTENT);
                nLp.rightMargin = UiStyle.dp(ctx, 8);
                row.addView(numTv, nLp);

                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);

                TextView titleTv = new TextView(ctx);
                titleTv.setText(t.title);
                titleTv.setTextColor(cur ? accent : UiStyle.TEXT_PRIMARY);
                titleTv.setTextSize(13.5f);
                titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
                titleTv.setMaxLines(2);
                col.addView(titleTv);

                if (!t.channel.isEmpty() || !t.duration.isEmpty()) {
                    TextView subTv = new TextView(ctx);
                    String sub = t.channel;
                    if (!t.duration.isEmpty()) sub = sub.isEmpty() ? t.duration : (sub + "  •  " + t.duration);
                    subTv.setText(sub);
                    subTv.setTextColor(UiStyle.TEXT_MUTED);
                    subTv.setTextSize(11.5f);
                    subTv.setSingleLine(true);
                    col.addView(subTv);
                }

                row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.setOnClickListener(v -> {
                    UiStyle.hapticTick(v, ctx);
                    host.onYtPlayTrack(idx);
                    host.closePanel();
                });

                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = UiStyle.dp(ctx, 7);
                wrap.addView(row, rLp);
            }
        }
        return wrap;
    }

    public static void showAddYouTubePlaylistDialog(Context ctx, Host host) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(ctx, 16);
        box.setPadding(pad, pad, pad, pad);

        EditText urlEt = new EditText(ctx);
        urlEt.setHint("Paste YouTube / YT Music playlist URL or ID");
        urlEt.setTextColor(UiStyle.TEXT_PRIMARY);
        urlEt.setHintTextColor(UiStyle.TEXT_MUTED);
        urlEt.setSingleLine(false);
        box.addView(urlEt);

        EditText nameEt = new EditText(ctx);
        nameEt.setHint("Optional name (auto-detected if blank)");
        nameEt.setTextColor(UiStyle.TEXT_PRIMARY);
        nameEt.setHintTextColor(UiStyle.TEXT_MUTED);
        nameEt.setSingleLine(true);
        box.addView(nameEt);

        new AlertDialog.Builder(ctx)
                .setTitle("Add YouTube Playlist")
                .setView(box)
                .setPositiveButton("Add & Sync", (d, w) -> {
                    String raw = urlEt.getText().toString().trim();
                    String plId = YouTubePlaylistSync.extractPlaylistId(raw);
                    if (plId == null) {
                        Toast.makeText(ctx, "Couldn't find a playlist ID (needs ?list=...)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String customName = nameEt.getText().toString().trim();
                    YouTubeStore.get(ctx).addOrUpdatePlaylist(customName, plId, raw);
                    host.onYtPlaylistChanged();
                    host.onYtSyncNow();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String formatSyncedAgo(long ts) {
        if (ts <= 0) return "Not synced yet";
        long mins = (System.currentTimeMillis() - ts) / 60000L;
        if (mins < 1) return "Synced just now";
        if (mins < 60) return "Synced " + mins + "m ago";
        long hrs = mins / 60;
        return "Synced " + hrs + "h ago";
    }

    private interface BoolCb { void onChange(boolean v); }

    private static void addSettingSwitch(Context ctx, LinearLayout parent, String label,
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

    private static LinearLayout makeCard(Context ctx, int borderColor) {
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
