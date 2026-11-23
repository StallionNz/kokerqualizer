package com.buddyapps.kokerqualizer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Presets Library, Auto-EQ Headphone/Speaker Profile Directory, and preset
 * management dialogs (Save As, Rename, Color Picker, Tags/Notes, Share Codes).
 */
class PresetPanels {

    static View buildPresetsPanel(Context ctx, DrawerPanels.Host host, boolean[] favOnlyRef, String[] searchRef) {
        EqStore store = EqStore.get(ctx);
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : 0xFF39FF14;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView favFilter = UiStyle.makePill(ctx, favOnlyRef[0] ? "★ Favorites" : "☆ All", favOnlyRef[0], accent);
        favFilter.setOnClickListener(v -> {
            UiStyle.hapticTick(v, ctx);
            favOnlyRef[0] = !favOnlyRef[0];
            host.onStateMutated();
        });
        actions.addView(favFilter);

        actions.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView saveNew = UiStyle.makePill(ctx, "+ Save As", true, accent);
        saveNew.setOnClickListener(v -> showSaveAsDialog(ctx, store, active, host));
        actions.addView(saveNew);

        TextView restoreBtn = UiStyle.makePill(ctx, "↺ Factory", false, accent);
        restoreBtn.setOnClickListener(v -> DrawerPanels.showRestoreFactoryDialog(ctx, store, host));
        int leftGap = UiStyle.dp(ctx, 6);
        actions.addView(restoreBtn, UiStyle.wrapLp(leftGap, 0));
        wrap.addView(actions);

        // Preset Search & Tag Filter Bar
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText sEt = new EditText(ctx);
        sEt.setHint("Search presets or tags (#Car, #Bass)...");
        sEt.setText(searchRef[0]);
        sEt.setTextColor(UiStyle.TEXT_PRIMARY);
        sEt.setHintTextColor(UiStyle.TEXT_MUTED);
        sEt.setTextSize(13f);
        sEt.setSingleLine(true);
        sEt.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 14)));
        sEt.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 8));
        searchRow.addView(sEt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView goFind = UiStyle.makePill(ctx, "Find", false, accent);
        goFind.setOnClickListener(v -> {
            searchRef[0] = sEt.getText().toString().trim();
            host.onStateMutated();
        });
        searchRow.addView(goFind, UiStyle.wrapLp(leftGap, 0));
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = UiStyle.dp(ctx, 10);
        wrap.addView(searchRow, srLp);

        // Row 1: JSON Export / Import / 1-Tap Downloads Backup
        LinearLayout ioRow = new LinearLayout(ctx);
        ioRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ioLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ioLp.topMargin = UiStyle.dp(ctx, 10);
        ioLp.bottomMargin = UiStyle.dp(ctx, 6);

        TextView expBtn = UiStyle.makePill(ctx, "Export JSON", false, accent);
        expBtn.setOnClickListener(v -> host.requestExportJson());
        TextView impBtn = UiStyle.makePill(ctx, "Import JSON", false, accent);
        impBtn.setOnClickListener(v -> host.requestImportJson());
        TextView dlBakBtn = UiStyle.makePill(ctx, "⬇ Backup", false, accent);
        dlBakBtn.setOnClickListener(v -> DrawerPanels.saveBackupToDownloads(ctx, store));

        int pGapPx = UiStyle.dp(ctx, 6);
        ioRow.addView(expBtn, UiStyle.rowLp(1f, pGapPx));
        ioRow.addView(impBtn, UiStyle.rowLp(1f, pGapPx));
        ioRow.addView(dlBakBtn, UiStyle.rowLp(1f, 0));
        wrap.addView(ioRow, ioLp);

        // Row 2: Short Copy/Paste Preset Share Codes
        LinearLayout codeRow = new LinearLayout(ctx);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        crLp.bottomMargin = UiStyle.dp(ctx, 12);

        TextView copyCodeBtn = UiStyle.makePill(ctx, "📋 Copy Code", false, accent);
        copyCodeBtn.setOnClickListener(v -> {
            if (active == null) return;
            String code = AutoEqCatalog.encodeShareCode(active);
            try {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Kokerqualizer Preset", code));
                    Toast.makeText(ctx, "Copied 1-line preset code for " + active.name + "!", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {}
        });
        TextView pasteCodeBtn = UiStyle.makePill(ctx, "📥 Paste Code", false, accent);
        pasteCodeBtn.setOnClickListener(v -> showPasteShareCodeDialog(ctx, store, host));
        codeRow.addView(copyCodeBtn, UiStyle.rowLp(1f, pGapPx));
        codeRow.addView(pasteCodeBtn, UiStyle.rowLp(1f, 0));
        wrap.addView(codeRow, crLp);

        String q = searchRef[0] != null ? searchRef[0].toLowerCase(Locale.US) : "";
        List<EqStore.EqPreset> list = store.getPresets();
        for (EqStore.EqPreset p : list) {
            if (favOnlyRef[0] && !p.favorite) continue;
            if (!q.isEmpty()
                    && !p.name.toLowerCase(Locale.US).contains(q)
                    && !(p.tags != null && p.tags.toLowerCase(Locale.US).contains(q))
                    && !(p.notes != null && p.notes.toLowerCase(Locale.US).contains(q))) {
                continue;
            }
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

            if ((p.tags != null && !p.tags.trim().isEmpty()) || (p.notes != null && !p.notes.trim().isEmpty())) {
                TextView tagTv = new TextView(ctx);
                String tLine = (p.tags != null ? p.tags.trim() : "");
                if (p.notes != null && !p.notes.trim().isEmpty()) {
                    tLine = tLine.isEmpty() ? p.notes.trim() : (tLine + "  •  " + p.notes.trim());
                }
                tagTv.setText(tLine);
                tagTv.setTextColor(UiStyle.TEXT_SECONDARY);
                tagTv.setTextSize(11f);
                tagTv.setPadding(0, UiStyle.dp(ctx, 4), 0, 0);
                card.addView(tagTv);
            }

            card.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                store.setActivePreset(p.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqEngine.get(ctx).triggerSmoothRamp();
                EqService.startOrRefresh(ctx);
                host.onStateMutated();
            });

            HorizontalScrollView hsv = new HorizontalScrollView(ctx);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout ctrls = new LinearLayout(ctx);
            ctrls.setOrientation(LinearLayout.HORIZONTAL);
            int gap = UiStyle.dp(ctx, 6);

            boolean isSlotB = p.id.equals(store.getSettings().comparePresetId);
            DrawerPanels.addSmallAction(ctx, ctrls, p.favorite ? "★ Fav" : "☆ Fav", p.favorite, p.accentColor, gap, v -> {
                p.favorite = !p.favorite;
                store.addOrUpdatePreset(p);
                host.onStateMutated();
            });
            DrawerPanels.addSmallAction(ctx, ctrls, isSlotB ? "⚖ Slot B" : "⚖ Set B", isSlotB, 0xFFFFB300, gap, v -> {
                store.getSettings().comparePresetId = isSlotB ? null : p.id;
                store.save();
                Toast.makeText(ctx, isSlotB ? "Cleared A/B/C Slot B" : ("Slot B set to " + p.name + " (tap A/B to compare!)"), Toast.LENGTH_SHORT).show();
                host.onStateMutated();
            });
            DrawerPanels.addSmallAction(ctx, ctrls, p.pinnedCar ? "🚗 Pinned" : "🚗 Pin Car", p.pinnedCar, p.accentColor, gap, v -> {
                p.pinnedCar = !p.pinnedCar;
                store.addOrUpdatePreset(p);
                host.onStateMutated();
            });
            DrawerPanels.addSmallAction(ctx, ctrls, "🏷 Tag/Note", false, p.accentColor, gap, v -> showTagsNotesDialog(ctx, store, p, host));
            DrawerPanels.addSmallAction(ctx, ctrls, "🎨 Color", false, p.accentColor, gap, v -> showColorPicker(ctx, store, p, host));
            DrawerPanels.addSmallAction(ctx, ctrls, "Duplicate", false, p.accentColor, gap, v -> {
                EqStore.EqPreset dup = p.copy("usr_" + UUID.randomUUID().toString().substring(0, 8), p.name + " Copy", false);
                store.addOrUpdatePreset(dup);
                store.setActivePreset(dup.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqService.startOrRefresh(ctx);
                host.onStateMutated();
            });
            DrawerPanels.addSmallAction(ctx, ctrls, "Rename", false, p.accentColor, gap, v -> showRenameDialog(ctx, store, p, host));
            DrawerPanels.addSmallAction(ctx, ctrls, "Share", false, p.accentColor, gap, v -> {
                try {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_SUBJECT, "Kokerqualizer: " + p.name);
                    share.putExtra(Intent.EXTRA_TEXT, "Kokerqualizer Preset (" + p.name + "):\n" + AutoEqCatalog.encodeShareCode(p));
                    ctx.startActivity(Intent.createChooser(share, "Share Preset"));
                } catch (Throwable t) {
                    Toast.makeText(ctx, "No share target available", Toast.LENGTH_SHORT).show();
                }
            });
            if (list.size() > 1) {
                DrawerPanels.addSmallAction(ctx, ctrls, "Delete", false, 0xFFFF5252, gap, v -> {
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

    private static void showPasteShareCodeDialog(Context ctx, EqStore store, DrawerPanels.Host host) {
        EditText et = new EditText(ctx);
        et.setHint("Paste KOKER1:... preset code");
        et.setTextColor(UiStyle.TEXT_PRIMARY);
        et.setHintTextColor(UiStyle.TEXT_MUTED);
        int pad = UiStyle.dp(ctx, 16);
        et.setPadding(pad, pad, pad, pad);
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                if (clip != null && clip.toString().contains("KOKER1:")) et.setText(clip.toString());
            }
        } catch (Throwable ignored) {}

        new AlertDialog.Builder(ctx)
                .setTitle("Import 1-Line Preset Code")
                .setView(et)
                .setPositiveButton("Import & Apply", (d, w) -> {
                    try {
                        EqStore.EqPreset p = AutoEqCatalog.decodeShareCode(et.getText().toString());
                        store.addOrUpdatePreset(p);
                        store.setActivePreset(p.id);
                        EqEngine.get(ctx).setAbFlatCompare(false);
                        EqService.startOrRefresh(ctx);
                        Toast.makeText(ctx, "Imported preset: " + p.name, Toast.LENGTH_SHORT).show();
                        host.onStateMutated();
                    } catch (Throwable t) {
                        Toast.makeText(ctx, "Invalid preset code (needs KOKER1:...)", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showTagsNotesDialog(Context ctx, EqStore store, EqStore.EqPreset p, DrawerPanels.Host host) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(ctx, 16);
        box.setPadding(pad, pad, pad, pad);

        EditText tagEt = new EditText(ctx);
        tagEt.setHint("Tags (e.g. #Car #Bass #Gym)");
        tagEt.setText(p.tags != null ? p.tags : "");
        tagEt.setTextColor(UiStyle.TEXT_PRIMARY);
        tagEt.setHintTextColor(UiStyle.TEXT_MUTED);
        box.addView(tagEt);

        EditText noteEt = new EditText(ctx);
        noteEt.setHint("Notes (e.g. Tuned for trunk sub at 70% vol)");
        noteEt.setText(p.notes != null ? p.notes : "");
        noteEt.setTextColor(UiStyle.TEXT_PRIMARY);
        noteEt.setHintTextColor(UiStyle.TEXT_MUTED);
        box.addView(noteEt);

        new AlertDialog.Builder(ctx)
                .setTitle("Tags & Notes • " + p.name)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    p.tags = tagEt.getText().toString().trim();
                    p.notes = noteEt.getText().toString().trim();
                    store.addOrUpdatePreset(p);
                    host.onStateMutated();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static View buildAutoEqPanel(Context ctx, DrawerPanels.Host host, String[] filterRef) {
        EqStore store = EqStore.get(ctx);
        EqStore.EqPreset active = store.getActivePreset();
        int accent = active != null ? active.accentColor : EqStore.DEFAULT_ACCENT;

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText et = new EditText(ctx);
        et.setHint("Search Galaxy Buds, Sony XM, AirPods, JBL, Car...");
        et.setText(filterRef[0]);
        et.setTextColor(UiStyle.TEXT_PRIMARY);
        et.setHintTextColor(UiStyle.TEXT_MUTED);
        et.setTextSize(13f);
        et.setSingleLine(true);
        et.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 14)));
        et.setPadding(UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 9), UiStyle.dp(ctx, 12), UiStyle.dp(ctx, 9));
        searchRow.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView go = UiStyle.makePill(ctx, "Search", false, accent);
        go.setOnClickListener(v -> {
            filterRef[0] = et.getText().toString().trim();
            host.onStateMutated();
        });
        int gGapPx = UiStyle.dp(ctx, 6);
        searchRow.addView(go, UiStyle.wrapLp(gGapPx, 0));
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.bottomMargin = UiStyle.dp(ctx, 12);
        wrap.addView(searchRow, srLp);

        String q = filterRef[0] != null ? filterRef[0].toLowerCase(Locale.US) : "";
        for (AutoEqCatalog.DeviceProfile prof : AutoEqCatalog.getAllProfiles()) {
            if (!q.isEmpty()
                    && !prof.name.toLowerCase(Locale.US).contains(q)
                    && !prof.category.toLowerCase(Locale.US).contains(q)
                    && !prof.notes.toLowerCase(Locale.US).contains(q)) {
                continue;
            }
            LinearLayout card = DrawerPanels.makeCard(ctx, UiStyle.BORDER_SUBTLE);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView t = new TextView(ctx);
            t.setText(prof.name);
            t.setTextColor(UiStyle.TEXT_PRIMARY);
            t.setTextSize(14.5f);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            col.addView(t);

            TextView s = new TextView(ctx);
            s.setText(prof.category + "  •  " + prof.notes);
            s.setTextColor(UiStyle.TEXT_SECONDARY);
            s.setTextSize(11.5f);
            col.addView(s);
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView applyBtn = UiStyle.makePill(ctx, "+ Apply", true, accent);
            applyBtn.setOnClickListener(v -> {
                UiStyle.hapticTick(v, ctx);
                EqStore.EqPreset created = prof.toPreset();
                store.addOrUpdatePreset(created);
                store.setActivePreset(created.id);
                EqEngine.get(ctx).setAbFlatCompare(false);
                EqEngine.get(ctx).triggerSmoothRamp();
                EqService.startOrRefresh(ctx);
                Toast.makeText(ctx, "Added & applied Auto-EQ: " + prof.name, Toast.LENGTH_SHORT).show();
                host.closePanel();
            });
            row.addView(applyBtn, UiStyle.wrapLp(gGapPx, 0));
            card.addView(row);
            wrap.addView(card);
        }
        return wrap;
    }

    static void showSaveAsDialog(Context ctx, EqStore store, EqStore.EqPreset base, DrawerPanels.Host host) {
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

    static void showRenameDialog(Context ctx, EqStore store, EqStore.EqPreset p, DrawerPanels.Host host) {
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

    static void showColorPicker(Context ctx, EqStore store, EqStore.EqPreset p, DrawerPanels.Host host) {
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
}
