package com.buddyapps.kokerqualizer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Multi-Playlist YouTube Manager, live track browser, Play-Next queueing,
 * and Recently Played history drawer panel.
 */
class YouTubeDrawerPanel {

    static View buildYouTubePanel(Context ctx, DrawerPanels.Host host, String[] filterRef) {
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

        // Saved playlists selector strip
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
        LinearLayout cfgCard = DrawerPanels.makeCard(ctx, UiStyle.BORDER_SUBTLE);
        DrawerPanels.addSettingSwitch(ctx, cfgCard, "Auto-sync playlist on app startup", ys.autoSyncOnStartup, v -> {
            ys.autoSyncOnStartup = v;
            ytStore.save();
        });
        DrawerPanels.addSettingSwitch(ctx, cfgCard, "Auto-load & play playlist on startup", ys.autoPlayOnStartup, v -> {
            ys.autoPlayOnStartup = v;
            ytStore.save();
        });
        DrawerPanels.addSettingSwitch(ctx, cfgCard, "Full YouTube Web Mode (Private playlists / Sign-in)", ys.fullWebMode, v -> {
            ys.fullWebMode = v;
            ytStore.save();
            host.onYtFullWebChanged(v);
        });
        wrap.addView(cfgCard);

        if (activePl == null) {
            LinearLayout emptyCard = DrawerPanels.makeCard(ctx, accent);
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
                row.setOnLongClickListener(v -> {
                    UiStyle.hapticTick(v, ctx);
                    ytStore.queuePlayNext(t);
                    Toast.makeText(ctx, "Queued next: " + t.title + " (" + ytStore.getQueueSize() + " in queue)", Toast.LENGTH_SHORT).show();
                    host.onStateMutated();
                    return true;
                });

                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = UiStyle.dp(ctx, 7);
                wrap.addView(row, rLp);
            }
        }

        // Recently Played History section
        List<YouTubeStore.Track> history = ytStore.getRecentHistory();
        if (!history.isEmpty()) {
            TextView hLbl = new TextView(ctx);
            hLbl.setText("RECENTLY PLAYED (" + history.size() + ")  •  Long-press playlist tracks to Queue Next");
            hLbl.setTextColor(UiStyle.TEXT_MUTED);
            hLbl.setTextSize(11f);
            hLbl.setTypeface(null, android.graphics.Typeface.BOLD);
            hLbl.setPadding(0, UiStyle.dp(ctx, 14), 0, UiStyle.dp(ctx, 6));
            wrap.addView(hLbl);

            for (int i = 0; i < Math.min(10, history.size()); i++) {
                YouTubeStore.Track ht = history.get(i);
                TextView hr = new TextView(ctx);
                hr.setText("↺  " + ht.title + (ht.channel.isEmpty() ? "" : (" — " + ht.channel)));
                hr.setTextColor(UiStyle.TEXT_SECONDARY);
                hr.setTextSize(12.5f);
                hr.setSingleLine(true);
                hr.setPadding(UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 8), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 8));
                hr.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(ctx, 1), UiStyle.dp(ctx, 12)));
                hr.setOnClickListener(v -> {
                    UiStyle.hapticTick(v, ctx);
                    host.onYtPlayDirectTrack(ht);
                    host.closePanel();
                });
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.bottomMargin = UiStyle.dp(ctx, 6);
                wrap.addView(hr, hlp);
            }
        }
        return wrap;
    }

    static void showAddYouTubePlaylistDialog(Context ctx, DrawerPanels.Host host) {
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
}
