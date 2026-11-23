package com.buddyapps.kokerqualizer;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.os.Handler;
import android.os.Looper;

/**
 * Owns the collapsible in-app YouTube playlist player card, playback controls,
 * background playlist sync scheduler, and Car Mode / Floating Orb RemoteBridge.
 */
class MainYouTubeController {
    interface Host {
        void openYouTubePanel();
        void showSleepTimerDialog();
        void onRefreshDeckRequested();
        void onRefreshYouTubeSubPanelIfOpen();
        void onStopSoundTest();
        boolean isHostDestroyed();
        DrawerPanels.Host getDrawerHost();
    }

    private final Activity activity;
    private final EqStore store;
    private final YouTubeStore ytStore;
    private final Host host;

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
    private TextView ytPrevBtn, ytPlayBtn, ytNextBtn, ytShuffleBtn, ytRepeatBtn, ytSpeedBtn, ytSleepBtn;

    private YouTubeStore.Track activeDirectTrack = null;
    private int consecutiveYtErrors = 0;
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

    MainYouTubeController(Activity activity, EqStore store, YouTubeStore ytStore, Host host) {
        this.activity = activity;
        this.store = store;
        this.ytStore = ytStore;
        this.host = host;
    }

    View buildCard() {
        ytCard = new LinearLayout(activity);
        ytCard.setOrientation(LinearLayout.VERTICAL);
        ytCard.setBackground(UiStyle.roundRect(UiStyle.SURFACE_CARD, UiStyle.BORDER_SUBTLE, UiStyle.dp(activity, 1), UiStyle.dp(activity, 20)));
        ytCard.setPadding(UiStyle.dp(activity, 12), UiStyle.dp(activity, 10), UiStyle.dp(activity, 12), UiStyle.dp(activity, 10));

        // Compact prompt when no playlist added yet
        ytEmptyRow = new LinearLayout(activity);
        ytEmptyRow.setOrientation(LinearLayout.HORIZONTAL);
        ytEmptyRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView emptyLbl = new TextView(activity);
        emptyLbl.setText("🎵  Load a YouTube playlist inside Kokerqualizer");
        emptyLbl.setTextColor(UiStyle.TEXT_SECONDARY);
        emptyLbl.setTextSize(12.5f);
        ytEmptyRow.addView(emptyLbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView addBtn = UiStyle.makePill(activity, "+ Add", true, 0xFF39FF14);
        addBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            DrawerPanels.showAddYouTubePlaylistDialog(activity, host.getDrawerHost());
        });
        ytEmptyRow.addView(addBtn);
        ytCard.addView(ytEmptyRow);

        // Full in-app YouTube Player content when a playlist exists
        ytPlayerContent = new LinearLayout(activity);
        ytPlayerContent.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hdr = new LinearLayout(activity);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        ytHeaderTitle = new TextView(activity);
        ytHeaderTitle.setText("🎵 YouTube Playlist");
        ytHeaderTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        ytHeaderTitle.setTextSize(12.5f);
        ytHeaderTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        ytHeaderTitle.setSingleLine(true);
        ytHeaderTitle.setEllipsize(TextUtils.TruncateAt.END);
        hdr.addView(ytHeaderTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ytSyncBtn = UiStyle.makePill(activity, "↻", false, 0xFF39FF14);
        ytSyncBtn.setTextSize(11f);
        ytSyncBtn.setPadding(UiStyle.dp(activity, 8), UiStyle.dp(activity, 4), UiStyle.dp(activity, 8), UiStyle.dp(activity, 4));
        ytSyncBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            syncActiveYouTubePlaylist(true);
        });
        int gap5 = UiStyle.dp(activity, 5);
        hdr.addView(ytSyncBtn, UiStyle.wrapLp(gap5, 0));

        ytVideoToggleBtn = UiStyle.makePill(activity, "🎬 Video", true, 0xFF39FF14);
        ytVideoToggleBtn.setTextSize(10.5f);
        ytVideoToggleBtn.setPadding(UiStyle.dp(activity, 8), UiStyle.dp(activity, 4), UiStyle.dp(activity, 8), UiStyle.dp(activity, 4));
        ytVideoToggleBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            ytStore.getState().videoExpanded = !ytStore.getState().videoExpanded;
            ytStore.save();
            refreshCard();
        });
        hdr.addView(ytVideoToggleBtn, UiStyle.wrapLp(gap5, 0));

        ytListBtn = UiStyle.makePill(activity, "≡ List", false, 0xFF39FF14);
        ytListBtn.setTextSize(10.5f);
        ytListBtn.setPadding(UiStyle.dp(activity, 9), UiStyle.dp(activity, 4), UiStyle.dp(activity, 9), UiStyle.dp(activity, 4));
        ytListBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            host.openYouTubePanel();
        });
        hdr.addView(ytListBtn, UiStyle.wrapLp(gap5, 0));
        ytPlayerContent.addView(hdr);

        ytPlayerView = new YouTubePlayerView(activity);
        ytPlayerView.setListener(new YouTubePlayerView.Listener() {
            @Override
            public void onPlaybackStateChanged(boolean playing) {
                if (playing) consecutiveYtErrors = 0;
                refreshCard();
            }

            @Override
            public void onTrackEnded() {
                if (SleepTimerManager.get(activity).onTrackFinished()) return;
                if (ytStore.getState().repeatOne) {
                    YouTubeStore.Track cur = activeDirectTrack != null ? activeDirectTrack : ytStore.getCurrentTrack();
                    YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
                    if (cur != null) {
                        ytPlayerView.loadTrack(cur.videoId, pl != null ? pl.playlistId : null, true);
                        return;
                    }
                }
                stepTrack(1);
            }

            @Override
            public void onFallbackToFullWeb(String reason) {
                Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onWebUrlVisited(String url) {
                if (ytStore != null && url != null) {
                    ytStore.getState().lastWebUrl = url;
                    ytStore.save();
                }
            }

            @Override
            public void onTrackUnavailable(int errorCode) {
                if (++consecutiveYtErrors <= 4) {
                    Toast.makeText(activity, "Track unavailable — auto-skipping to next...", Toast.LENGTH_SHORT).show();
                    stepTrack(1);
                }
            }
        });
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(activity, 190));
        pvLp.topMargin = UiStyle.dp(activity, 8);
        ytPlayerContent.addView(ytPlayerView, pvLp);

        ytTrackTitle = new TextView(activity);
        ytTrackTitle.setTextColor(UiStyle.TEXT_PRIMARY);
        ytTrackTitle.setTextSize(13.5f);
        ytTrackTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        ytTrackTitle.setSingleLine(true);
        ytTrackTitle.setEllipsize(TextUtils.TruncateAt.END);
        ytTrackTitle.setPadding(0, UiStyle.dp(activity, 8), 0, 0);
        ytPlayerContent.addView(ytTrackTitle);

        ytTrackSub = new TextView(activity);
        ytTrackSub.setTextColor(UiStyle.TEXT_MUTED);
        ytTrackSub.setTextSize(11f);
        ytTrackSub.setSingleLine(true);
        ytTrackSub.setEllipsize(TextUtils.TruncateAt.END);
        ytPlayerContent.addView(ytTrackSub);

        LinearLayout ctrlRow = new LinearLayout(activity);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = UiStyle.dp(activity, 8);

        ytPrevBtn = UiStyle.makePill(activity, "⏮ Prev", false, 0xFF39FF14);
        ytPrevBtn.setOnClickListener(v -> { UiStyle.hapticTick(v, activity); stepTrack(-1); });

        ytPlayBtn = UiStyle.makePill(activity, "▶ Play", true, 0xFF39FF14);
        ytPlayBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            ytPlayerView.togglePlayPause();
            refreshCard();
        });

        ytNextBtn = UiStyle.makePill(activity, "Next ⏭", false, 0xFF39FF14);
        ytNextBtn.setOnClickListener(v -> { UiStyle.hapticTick(v, activity); stepTrack(1); });

        ytShuffleBtn = UiStyle.makePill(activity, "🔀", false, 0xFF39FF14);
        ytShuffleBtn.setPadding(UiStyle.dp(activity, 8), UiStyle.dp(activity, 5), UiStyle.dp(activity, 8), UiStyle.dp(activity, 5));
        ytShuffleBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            ytStore.getState().shuffle = !ytStore.getState().shuffle;
            ytStore.save();
            refreshCard();
        });

        int cGap = UiStyle.dp(activity, 5);
        ctrlRow.addView(ytPrevBtn, UiStyle.rowLp(1f, cGap));
        ctrlRow.addView(ytPlayBtn, UiStyle.rowLp(1f, cGap));
        ctrlRow.addView(ytNextBtn, UiStyle.rowLp(1f, cGap));
        ctrlRow.addView(ytShuffleBtn, UiStyle.wrapLp(0, 0));
        ytPlayerContent.addView(ctrlRow, crLp);

        LinearLayout extraRow = new LinearLayout(activity);
        extraRow.setOrientation(LinearLayout.HORIZONTAL);
        extraRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams erLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        erLp.topMargin = UiStyle.dp(activity, 6);

        ytRepeatBtn = UiStyle.makePill(activity, "🔁 Repeat", false, 0xFF39FF14);
        ytRepeatBtn.setTextSize(10.5f);
        ytRepeatBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            ytStore.getState().repeatOne = !ytStore.getState().repeatOne;
            ytStore.save();
            refreshCard();
        });

        ytSpeedBtn = UiStyle.makePill(activity, "1.0x", false, 0xFF39FF14);
        ytSpeedBtn.setTextSize(10.5f);
        ytSpeedBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            float cur = ytStore.getState().playbackSpeed;
            float next = cur < 0.9f ? 1.0f : (cur < 1.15f ? 1.25f : (cur < 1.4f ? 1.5f : 0.75f));
            ytStore.getState().playbackSpeed = next;
            ytStore.save();
            if (ytPlayerView != null) ytPlayerView.setPlaybackSpeed(next);
            refreshCard();
        });

        ytSleepBtn = UiStyle.makePill(activity, "⏰ Sleep", false, 0xFF39FF14);
        ytSleepBtn.setTextSize(10.5f);
        ytSleepBtn.setOnClickListener(v -> {
            UiStyle.hapticTick(v, activity);
            host.showSleepTimerDialog();
        });

        extraRow.addView(ytRepeatBtn, UiStyle.rowLp(1f, cGap));
        extraRow.addView(ytSpeedBtn, UiStyle.rowLp(1f, cGap));
        extraRow.addView(ytSleepBtn, UiStyle.rowLp(1f, 0));
        ytPlayerContent.addView(extraRow, erLp);

        ytCard.addView(ytPlayerContent);

        LinearLayout.LayoutParams ytLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ytLp.bottomMargin = UiStyle.dp(activity, 10);
        ytCard.setLayoutParams(ytLp);
        return ytCard;
    }

    void initializePlaybackAndBridge() {
        YouTubePlayerView.setActiveRemoteBridge(new YouTubePlayerView.RemoteBridge() {
            @Override public void playNext() { activity.runOnUiThread(() -> stepTrack(1)); }
            @Override public void playPrev() { activity.runOnUiThread(() -> stepTrack(-1)); }
            @Override public void togglePlayPause() { activity.runOnUiThread(() -> { if (ytPlayerView != null) ytPlayerView.togglePlayPause(); }); }
            @Override public void stopAll() {
                activity.runOnUiThread(() -> {
                    host.onStopSoundTest();
                    stopAndReset();
                });
            }
            @Override public boolean isPlaying() { return ytPlayerView != null && ytPlayerView.isPlaying(); }
            @Override public String getNowPlayingLabel() {
                YouTubeStore.Track t = activeDirectTrack != null ? activeDirectTrack : ytStore.getCurrentTrack();
                if (t == null) return null;
                return t.channel.isEmpty() ? ("♪ " + t.title) : ("♪ " + t.title + " — " + t.channel);
            }
        });

        YouTubeStore.SavedPlaylist startPl = ytStore.getActivePlaylist();
        YouTubeStore.YtState ys = ytStore.getState();
        ytPlayerView.setPlaybackSpeed(ys.playbackSpeed);
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

    void stopAndReset() {
        if (ytPlayerView != null) ytPlayerView.stopAndReset();
    }

    boolean canGoBackInWeb() {
        return ytPlayerView != null && ytPlayerView.canGoBackInWeb();
    }

    void goBackInWeb() {
        if (ytPlayerView != null) ytPlayerView.goBackInWeb();
    }

    void destroy() {
        ytAutoSyncHandler.removeCallbacksAndMessages(null);
        YouTubePlayerView.setActiveRemoteBridge(null);
        if (ytPlayerView != null) ytPlayerView.destroyPlayer();
    }

    void refreshCard() {
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
        UiStyle.stylePill(activity, ytSyncBtn, ytSyncing, accent);

        boolean expanded = ys.videoExpanded;
        ytVideoToggleBtn.setText(expanded ? "🎬 Video" : "🎧 Mini");
        UiStyle.stylePill(activity, ytVideoToggleBtn, expanded, accent);
        ViewGroup.LayoutParams lp = ytPlayerView.getLayoutParams();
        int fullH = (ytPlayerView != null && ytPlayerView.isFullWebActive()) ? 330 : 205;
        int desiredH = UiStyle.dp(activity, expanded ? fullH : 116);
        if (lp != null && lp.height != desiredH) {
            lp.height = desiredH;
            ytPlayerView.setLayoutParams(lp);
        }

        UiStyle.stylePill(activity, ytListBtn, false, accent);

        YouTubeStore.Track cur = activeDirectTrack != null ? activeDirectTrack : ytStore.getCurrentTrack();
        if (cur != null) {
            ytTrackTitle.setText(cur.title);
            ytTrackSub.setText(cur.channel.isEmpty()
                    ? (activeDirectTrack != null ? "Queued / History" : (ys.fullWebMode ? "Full YouTube Mode" : "In-App Player"))
                    : (cur.channel + (cur.duration.isEmpty() ? "" : ("  •  " + cur.duration))));
        } else {
            ytTrackTitle.setText(ytSyncing ? "Syncing tracks from YouTube..." : "Tap ↻ to sync tracks (or use Full YT Mode)");
            ytTrackSub.setText("Playlist ID: " + pl.playlistId);
        }

        boolean playing = ytPlayerView != null && ytPlayerView.isPlaying();
        ytPlayBtn.setText(playing ? "⏸ Pause" : "▶ Play");
        UiStyle.stylePill(activity, ytPlayBtn, playing, accent);
        UiStyle.stylePill(activity, ytPrevBtn, false, accent);
        UiStyle.stylePill(activity, ytNextBtn, false, accent);
        UiStyle.stylePill(activity, ytShuffleBtn, ys.shuffle, accent);

        if (ytRepeatBtn != null) {
            ytRepeatBtn.setText(ys.repeatOne ? "🔂 Repeat 1" : "🔁 Repeat");
            UiStyle.stylePill(activity, ytRepeatBtn, ys.repeatOne, accent);
        }
        if (ytSpeedBtn != null) {
            ytSpeedBtn.setText(String.format(Locale.US, "%.2gx", ys.playbackSpeed));
            UiStyle.stylePill(activity, ytSpeedBtn, Math.abs(ys.playbackSpeed - 1.0f) > 0.05f, accent);
        }
        if (ytSleepBtn != null) {
            SleepTimerManager stm = SleepTimerManager.get(activity);
            ytSleepBtn.setText(stm.getStatusLabel());
            UiStyle.stylePill(activity, ytSleepBtn, stm.isActive(), 0xFFFFB300);
        }
        int qSz = ytStore.getQueueSize();
        if (qSz > 0 && ytTrackSub != null) {
            ytTrackSub.setText(ytTrackSub.getText() + "  •  " + qSz + " queued next");
        }
    }

    void stepTrack(int delta) {
        if (delta > 0) {
            YouTubeStore.Track queued = ytStore.pollPlayNextQueue();
            if (queued != null) {
                onPlayDirectTrack(queued);
                return;
            }
        }
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || pl.tracks.isEmpty()) {
            Toast.makeText(activity, "Tap ↻ Sync to load tracks first", Toast.LENGTH_SHORT).show();
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
        onPlayTrack(nextIdx);
    }

    void syncActiveYouTubePlaylist(boolean showToast) {
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || ytSyncing) return;
        ytSyncing = true;
        refreshCard();
        final String targetId = pl.id;
        final boolean hadNoTracks = pl.tracks.isEmpty();

        YouTubePlaylistSync.syncAsync(pl.playlistId, new YouTubePlaylistSync.Callback() {
            @Override
            public void onSuccess(String fetchedTitle, List<YouTubeStore.Track> tracks) {
                ytSyncing = false;
                ytStore.updatePlaylistTracks(targetId, fetchedTitle, tracks);
                if (host.isHostDestroyed()) return;
                if (hadNoTracks && !tracks.isEmpty()) {
                    YouTubeStore.SavedPlaylist active = ytStore.getActivePlaylist();
                    if (active != null && active.id.equals(targetId)) {
                        ytPlayerView.loadTrack(tracks.get(0).videoId, active.playlistId, ytStore.getState().autoPlayOnStartup);
                    }
                }
                if (showToast) {
                    Toast.makeText(activity, "Synced " + tracks.size() + " tracks!", Toast.LENGTH_SHORT).show();
                }
                host.onRefreshDeckRequested();
            }

            @Override
            public void onError(String message) {
                ytSyncing = false;
                if (host.isHostDestroyed()) return;
                if (showToast) {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
                refreshCard();
            }
        });
    }

    void onPlayTrack(int index) {
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        if (pl == null || pl.tracks.isEmpty()) return;
        activeDirectTrack = null;
        int clamped = Math.max(0, Math.min(pl.tracks.size() - 1, index));
        ytStore.getState().currentIndex = clamped;
        ytStore.getState().lastWebUrl = null;
        ytStore.save();
        YouTubeStore.Track t = pl.tracks.get(clamped);
        ytStore.recordHistory(t);
        ytPlayerView.setPlaybackSpeed(ytStore.getState().playbackSpeed);
        ytPlayerView.loadTrack(t.videoId, pl.playlistId, true);
        refreshCard();
        host.onRefreshYouTubeSubPanelIfOpen();
    }

    void onPlayDirectTrack(YouTubeStore.Track track) {
        if (track == null || track.videoId == null || track.videoId.isEmpty()) return;
        activeDirectTrack = track;
        ytStore.recordHistory(track);
        YouTubeStore.SavedPlaylist pl = ytStore.getActivePlaylist();
        String plId = pl != null ? pl.playlistId : null;
        ytPlayerView.setPlaybackSpeed(ytStore.getState().playbackSpeed);
        ytPlayerView.loadTrack(track.videoId, plId, true);
        refreshCard();
    }

    void onPlaylistChanged() {
        activeDirectTrack = null;
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
            ytPlayerView.stopAndReset();
        }
        host.onRefreshDeckRequested();
    }

    void onFullWebChanged(boolean fullWeb) {
        if (ytPlayerView != null) {
            ytPlayerView.setFullWebMode(fullWeb);
        }
        refreshCard();
    }
}
