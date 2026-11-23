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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Device Automation (Bluetooth, Wired/USB-C, Per-App Rules), Audio Health
 * Diagnostic Checklist, and App/Orb Settings drawer panels.
 */
class RoutingAndSettingsPanels {

    static View buildBluetoothPanel(Context ctx, DrawerPanels.Host host) {
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
        LinearLayout wiredCard = DrawerPanels.makeCard(ctx, s.wiredAutoSwitch ? accent : UiStyle.BORDER_SUBTLE);
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

            wiredCard.addView(DrawerPanels.makePresetSpinner(ctx, presets, s.wiredPresetId, selectedId -> {
                s.wiredPresetId = selectedId;
                store.save();
            }));
        }
        wrap.addView(wiredCard);

        // Global Disconnect Fallback Card
        LinearLayout discCard = DrawerPanels.makeCard(ctx, accent);
        TextView discTitle = new TextView(ctx);
        discTitle.setText("On Disconnect → Switch To Phone Preset");
        discTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        discTitle.setTextSize(14f);
        discTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        discTitle.setPadding(0, 0, 0, UiStyle.dp(ctx, 6));
        discCard.addView(discTitle);

        discCard.addView(DrawerPanels.makePresetSpinner(ctx, presets, s.phonePresetId, selectedId -> {
            s.phonePresetId = selectedId;
            store.save();
        }));
        wrap.addView(discCard);

        // Per-App Auto-Preset Profiles
        LinearLayout appCard = DrawerPanels.makeCard(ctx, s.perAppAutoSwitch ? accent : UiStyle.BORDER_SUBTLE);
        Switch appSw = new Switch(ctx);
        appSw.setText("📱 Per-App Auto-Preset Switching");
        appSw.setTextColor(UiStyle.TEXT_PRIMARY);
        appSw.setTextSize(14f);
        appSw.setTypeface(null, android.graphics.Typeface.BOLD);
        appSw.setChecked(s.perAppAutoSwitch);
        appSw.setOnCheckedChangeListener((b, checked) -> {
            s.perAppAutoSwitch = checked;
            store.save();
            EqService.startOrRefresh(ctx);
            host.onStateMutated();
        });
        appCard.addView(appSw);

        if (s.perAppAutoSwitch) {
            if (!KokerMediaListenerService.hasMediaAccess(ctx)) {
                TextView warn = new TextView(ctx);
                warn.setText("Needs Notification Access to detect which music app is playing.");
                warn.setTextColor(0xFFFFB300);
                warn.setTextSize(11.5f);
                warn.setPadding(0, UiStyle.dp(ctx, 4), 0, UiStyle.dp(ctx, 6));
                appCard.addView(warn);
            }
            for (EqStore.AppPresetRule ar : store.getAppRules()) {
                Switch rSw = new Switch(ctx);
                rSw.setText(ar.appLabel);
                rSw.setTextColor(UiStyle.TEXT_SECONDARY);
                rSw.setTextSize(13f);
                rSw.setChecked(ar.enabled);
                rSw.setPadding(0, UiStyle.dp(ctx, 6), 0, UiStyle.dp(ctx, 2));
                rSw.setOnCheckedChangeListener((b, checked) -> {
                    ar.enabled = checked;
                    store.putAppRule(ar);
                    host.onStateMutated();
                });
                appCard.addView(rSw);
                if (ar.enabled) {
                    appCard.addView(DrawerPanels.makePresetSpinner(ctx, presets, ar.presetId, pid -> {
                        ar.presetId = pid;
                        store.putAppRule(ar);
                    }));
                }
            }
        }
        wrap.addView(appCard);

        boolean hasBtPerm = Build.VERSION.SDK_INT < 31 ||
                ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

        if (!hasBtPerm) {
            LinearLayout permCard = DrawerPanels.makeCard(ctx, 0xFFFFB300);
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
            LinearLayout card = DrawerPanels.makeCard(ctx, rule.autoStart ? accent : UiStyle.BORDER_SUBTLE);

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

                card.addView(DrawerPanels.makePresetSpinner(ctx, presets, rule.presetId, pid -> {
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

    static View buildHealthPanel(Context ctx, DrawerPanels.Host host) {
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

        int bm = EqStore.get(ctx).getSettings().bandMode;
        String bandLabel = bm == EqStore.BANDS_15 ? "15-Band Pro" : (bm == EqStore.BANDS_10 ? "10-Band" : "5-Band");
        addHealthRow(ctx, wrap,
                bandLabel + " DSP + 3-Band MBC + Limiter",
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
        LinearLayout card = DrawerPanels.makeCard(ctx, col);

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

    static View buildSettingsPanel(Context ctx, DrawerPanels.Host host) {
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hookCard = DrawerPanels.makeCard(ctx, accent);
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

        LinearLayout swCard = DrawerPanels.makeCard(ctx, UiStyle.BORDER_SUBTLE);
        DrawerPanels.addSettingSwitch(ctx, swCard, "Smart Auto-Gain (prevents volume jumps)", s.autoGain, val -> {
            s.autoGain = val;
            store.save();
            EqService.startOrRefresh(ctx);
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Volume-Aware Auto-Loudness (richer low-vol sound)", s.autoLoudness, val -> {
            s.autoLoudness = val;
            store.save();
            EqService.startOrRefresh(ctx);
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Mono Speaker Optimization (single speaker / earbud)", s.monoAudio, val -> {
            s.monoAudio = val;
            store.save();
            EqService.startOrRefresh(ctx);
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Smooth 250ms Gain Ramp (clickless preset switch)", s.smoothGainRamp, val -> {
            s.smoothGainRamp = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "FabFilter-Style EQ Curve Overlay on Spectrum", s.vizEqOverlay, val -> {
            s.vizEqOverlay = val;
            store.save();
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Multi-Color Neon Gradient Spectrum Bars", s.vizGradientBars, val -> {
            s.vizGradientBars = val;
            store.save();
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Bass-Reactive Neon Glow on Deck Cards", s.reactiveGlow, val -> {
            s.reactiveGlow = val;
            store.save();
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Bass-Reactive Pulse on Sticky Bottom Bar", s.bottomBarPulse, val -> {
            s.bottomBarPulse = val;
            store.save();
            host.onStateMutated();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Subtle Haptic Feedback on Knobs & Sliders", s.hapticsEnabled, val -> {
            s.hapticsEnabled = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Start Engine Automatically on Phone Boot", s.startOnBoot, val -> {
            s.startOnBoot = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Keep Screen Awake in Car Mode", s.carKeepScreenOn, val -> {
            s.carKeepScreenOn = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Live Visualizer Backdrop in Car Mode", s.carVizBackdrop, val -> {
            s.carVizBackdrop = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Voice-Announce Preset Changes in Car Mode (TTS)", s.carVoiceAnnounce, val -> {
            s.carVoiceAnnounce = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, swCard, "Night-Dim Low-Glare Car Dash", s.carNightDim, val -> {
            s.carNightDim = val;
            store.save();
        });
        wrap.addView(swCard);

        // Visualizer Peak Decay Speed Card
        LinearLayout vizCfgCard = DrawerPanels.makeCard(ctx, UiStyle.BORDER_SUBTLE);
        TextView vdLbl = new TextView(ctx);
        vdLbl.setText("Visualizer Peak-Decay Speed");
        vdLbl.setTextColor(UiStyle.TEXT_PRIMARY);
        vdLbl.setTextSize(14f);
        vdLbl.setTypeface(null, android.graphics.Typeface.BOLD);
        vdLbl.setPadding(0, 0, 0, UiStyle.dp(ctx, 8));
        vizCfgCard.addView(vdLbl);
        LinearLayout vdRow = new LinearLayout(ctx);
        vdRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] vdNames = new String[]{"Slow", "Medium", "Fast"};
        for (int i = 0; i < 3; i++) {
            final int sp = i;
            TextView b = UiStyle.makePill(ctx, vdNames[i], s.vizDecaySpeed == i, accent);
            b.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                s.vizDecaySpeed = sp;
                store.save();
                host.onStateMutated();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 6);
            vdRow.addView(b, lp);
        }
        vizCfgCard.addView(vdRow);
        wrap.addView(vizCfgCard);

        // Floating 3D Crystal Orb Settings Card
        LinearLayout orbCard = DrawerPanels.makeCard(ctx, EqStore.DEFAULT_ACCENT);
        TextView oTitle = new TextView(ctx);
        oTitle.setText("🫧 Floating 3D Crystal Orb Settings");
        oTitle.setTextColor(EqStore.DEFAULT_ACCENT);
        oTitle.setTextSize(14.5f);
        oTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        oTitle.setPadding(0, 0, 0, UiStyle.dp(ctx, 6));
        orbCard.addView(oTitle);

        DrawerPanels.addSettingSwitch(ctx, orbCard, "Floating Bubble Companion (over all apps)", s.bubbleEnabled && BubbleOverlayManager.canDraw(ctx), val -> {
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
        DrawerPanels.addSettingSwitch(ctx, orbCard, "Auto-Show Floating Bubble on Bluetooth Connect", s.bubbleAutoOnBt, val -> {
            s.bubbleAutoOnBt = val;
            store.save();
        });
        DrawerPanels.addSettingSwitch(ctx, orbCard, "Beat-Reactive Aura & Stardust Pulse", s.orbBeatReactive, val -> {
            s.orbBeatReactive = val;
            store.save();
            EqService.startOrRefresh(ctx);
        });
        DrawerPanels.addSettingSwitch(ctx, orbCard, "Auto-Fade Orb to 45% Opacity When Idle", s.orbAutoFade, val -> {
            s.orbAutoFade = val;
            store.save();
            EqService.startOrRefresh(ctx);
        });
        DrawerPanels.addSettingSwitch(ctx, orbCard, "Auto-Tuck Slim Edge Tab in Landscape Fullscreen", s.orbAutoTuckLandscape, val -> {
            s.orbAutoTuckLandscape = val;
            store.save();
            EqService.startOrRefresh(ctx);
        });

        // Orb Size selector
        TextView szLbl = new TextView(ctx);
        szLbl.setText("Orb Size:");
        szLbl.setTextColor(UiStyle.TEXT_MUTED);
        szLbl.setTextSize(11.5f);
        szLbl.setPadding(0, UiStyle.dp(ctx, 8), 0, UiStyle.dp(ctx, 4));
        orbCard.addView(szLbl);
        LinearLayout szRow = new LinearLayout(ctx);
        szRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] szNames = new String[]{"Small", "Medium", "Large"};
        for (int i = 0; i < 3; i++) {
            final int sm = i;
            TextView b = UiStyle.makePill(ctx, szNames[i], s.orbSizeMode == i, EqStore.DEFAULT_ACCENT);
            b.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                s.orbSizeMode = sm;
                store.save();
                EqService.startOrRefresh(ctx);
                host.onStateMutated();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = UiStyle.dp(ctx, 6);
            szRow.addView(b, lp);
        }
        orbCard.addView(szRow);

        // Orb Double-Tap Shortcut selector
        TextView dtLbl = new TextView(ctx);
        dtLbl.setText("Double-Tap Orb Shortcut:");
        dtLbl.setTextColor(UiStyle.TEXT_MUTED);
        dtLbl.setTextSize(11.5f);
        dtLbl.setPadding(0, UiStyle.dp(ctx, 10), 0, UiStyle.dp(ctx, 4));
        orbCard.addView(dtLbl);
        LinearLayout dtRow = new LinearLayout(ctx);
        dtRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dtNames = new String[]{"Popup", "Next ⏭", "Toggle EQ", "🚗 Car"};
        for (int i = 0; i < 4; i++) {
            final int act = i;
            TextView b = UiStyle.makePill(ctx, dtNames[i], s.orbDoubleTapAction == i, EqStore.DEFAULT_ACCENT);
            b.setTextSize(10f);
            b.setPadding(UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 5), UiStyle.dp(ctx, 6), UiStyle.dp(ctx, 5));
            b.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                s.orbDoubleTapAction = act;
                store.save();
                host.onStateMutated();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 3) lp.rightMargin = UiStyle.dp(ctx, 4);
            dtRow.addView(b, lp);
        }
        orbCard.addView(dtRow);

        // Custom Monogram Letter inside the Orb
        LinearLayout letRow = new LinearLayout(ctx);
        letRow.setOrientation(LinearLayout.HORIZONTAL);
        letRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lrLp.topMargin = UiStyle.dp(ctx, 10);
        TextView letLbl = new TextView(ctx);
        letLbl.setText("Stardust Monogram inside Orb:  " + (s.orbLetter != null ? s.orbLetter : "K"));
        letLbl.setTextColor(UiStyle.TEXT_PRIMARY);
        letLbl.setTextSize(13f);
        letRow.addView(letLbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView changeLetBtn = UiStyle.makePill(ctx, "Change Letter", false, EqStore.DEFAULT_ACCENT);
        changeLetBtn.setOnClickListener(v -> {
            EditText et = new EditText(ctx);
            et.setText(s.orbLetter != null ? s.orbLetter : "K");
            et.setHint("1 letter or symbol (e.g. K, S, ⚡, ★)");
            et.setTextColor(UiStyle.TEXT_PRIMARY);
            et.setHintTextColor(UiStyle.TEXT_MUTED);
            int pad = UiStyle.dp(ctx, 16);
            et.setPadding(pad, pad, pad, pad);
            new AlertDialog.Builder(ctx)
                    .setTitle("Custom Orb Monogram")
                    .setView(et)
                    .setPositiveButton("Apply", (d, w) -> {
                        String raw = et.getText().toString().trim();
                        s.orbLetter = EqStore.firstCodePoint(raw, "K");
                        store.save();
                        EqService.startOrRefresh(ctx);
                        KokerMiniWidgetProvider.updateAll(ctx);
                        host.onStateMutated();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        letRow.addView(changeLetBtn);
        orbCard.addView(letRow, lrLp);
        wrap.addView(orbCard);

        return wrap;
    }
}
