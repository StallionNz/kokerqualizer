package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YouTubeStore {
    private static final String PREFS = "kokerqualizer_yt_prefs";
    private static final String KEY_PLAYLISTS = "playlists_json";
    private static final String KEY_STATE = "yt_state_json";
    private static final String KEY_HISTORY = "yt_history_json";

    public static class Track {
        public String videoId;
        public String title;
        public String channel;
        public String duration;

        public Track(String videoId, String title, String channel, String duration) {
            this.videoId = videoId != null ? videoId : "";
            this.title = title != null ? title : "Untitled";
            this.channel = channel != null ? channel : "";
            this.duration = duration != null ? duration : "";
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("videoId", videoId);
                o.put("title", title);
                o.put("channel", channel);
                o.put("duration", duration);
            } catch (Exception ignored) {}
            return o;
        }

        public static Track fromJson(JSONObject o) {
            return new Track(
                    o.optString("videoId", ""),
                    o.optString("title", "Untitled"),
                    o.optString("channel", ""),
                    o.optString("duration", "")
            );
        }
    }

    public static class SavedPlaylist {
        public String id;
        public String name;
        public String playlistId;
        public String sourceUrl;
        public long lastSyncedMs;
        public List<Track> tracks = new ArrayList<>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("playlistId", playlistId);
                o.put("sourceUrl", sourceUrl);
                o.put("lastSyncedMs", lastSyncedMs);
                JSONArray arr = new JSONArray();
                for (Track t : tracks) arr.put(t.toJson());
                o.put("tracks", arr);
            } catch (Exception ignored) {}
            return o;
        }

        public static SavedPlaylist fromJson(JSONObject o) {
            SavedPlaylist p = new SavedPlaylist();
            p.id = o.optString("id", UUID.randomUUID().toString());
            p.name = o.optString("name", "YouTube Playlist");
            p.playlistId = o.optString("playlistId", "");
            p.sourceUrl = o.optString("sourceUrl", "");
            p.lastSyncedMs = o.optLong("lastSyncedMs", 0L);
            JSONArray arr = o.optJSONArray("tracks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject to = arr.optJSONObject(i);
                    if (to != null) p.tracks.add(Track.fromJson(to));
                }
            }
            return p;
        }
    }

    public static class YtState {
        public String activePlaylistId = null;
        public int currentIndex = 0;
        public boolean autoSyncOnStartup = true;
        public boolean autoPlayOnStartup = true;
        public boolean shuffle = false;
        public boolean repeatOne = false;      // #33
        public float playbackSpeed = 1.0f;     // #34 (0.75 / 1.0 / 1.25 / 1.5)
        public boolean fullWebMode = false;
        public boolean videoExpanded = true;
        public String lastWebUrl = null;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                if (activePlaylistId != null) o.put("activePlaylistId", activePlaylistId);
                o.put("currentIndex", currentIndex);
                o.put("autoSyncOnStartup", autoSyncOnStartup);
                o.put("autoPlayOnStartup", autoPlayOnStartup);
                o.put("shuffle", shuffle);
                o.put("repeatOne", repeatOne);
                o.put("playbackSpeed", (double) playbackSpeed);
                o.put("fullWebMode", fullWebMode);
                o.put("videoExpanded", videoExpanded);
                if (lastWebUrl != null) o.put("lastWebUrl", lastWebUrl);
            } catch (Exception ignored) {}
            return o;
        }

        public static YtState fromJson(JSONObject o) {
            YtState s = new YtState();
            if (o == null) return s;
            s.activePlaylistId = o.has("activePlaylistId") ? o.optString("activePlaylistId", null) : null;
            s.currentIndex = Math.max(0, o.optInt("currentIndex", 0));
            s.autoSyncOnStartup = o.optBoolean("autoSyncOnStartup", true);
            s.autoPlayOnStartup = o.optBoolean("autoPlayOnStartup", true);
            s.shuffle = o.optBoolean("shuffle", false);
            s.repeatOne = o.optBoolean("repeatOne", false);
            s.playbackSpeed = EqStore.clamp((float) o.optDouble("playbackSpeed", 1.0), 0.5f, 2.0f);
            s.fullWebMode = o.optBoolean("fullWebMode", false);
            s.videoExpanded = o.optBoolean("videoExpanded", true);
            s.lastWebUrl = o.has("lastWebUrl") ? o.optString("lastWebUrl", null) : null;
            return s;
        }
    }

    private static YouTubeStore instance;
    private final SharedPreferences prefs;
    private final List<SavedPlaylist> playlists = new ArrayList<>();
    private final List<Track> recentHistory = new ArrayList<>();
    private final List<Track> playNextQueue = new ArrayList<>();
    private YtState state = new YtState();

    public static synchronized YouTubeStore get(Context ctx) {
        if (instance == null) {
            instance = new YouTubeStore(ctx.getApplicationContext());
        }
        return instance;
    }

    private YouTubeStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        playlists.clear();
        String pJson = prefs.getString(KEY_PLAYLISTS, null);
        if (pJson != null) {
            try {
                JSONArray arr = new JSONArray(pJson);
                for (int i = 0; i < arr.length(); i++) {
                    playlists.add(SavedPlaylist.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
        }
        String sJson = prefs.getString(KEY_STATE, null);
        if (sJson != null) {
            try {
                state = YtState.fromJson(new JSONObject(sJson));
            } catch (Exception ignored) {
                state = new YtState();
            }
        }
        if (getActivePlaylist() == null && !playlists.isEmpty()) {
            state.activePlaylistId = playlists.get(0).id;
        }
        recentHistory.clear();
        String hJson = prefs.getString(KEY_HISTORY, null);
        if (hJson != null) {
            try {
                JSONArray arr = new JSONArray(hJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject to = arr.optJSONObject(i);
                    if (to != null) recentHistory.add(Track.fromJson(to));
                }
            } catch (Exception ignored) {}
        }
    }

    public synchronized void save() {
        JSONArray arr = new JSONArray();
        for (SavedPlaylist p : playlists) arr.put(p.toJson());
        JSONArray hArr = new JSONArray();
        for (Track t : recentHistory) hArr.put(t.toJson());
        prefs.edit()
                .putString(KEY_PLAYLISTS, arr.toString())
                .putString(KEY_STATE, state.toJson().toString())
                .putString(KEY_HISTORY, hArr.toString())
                .apply();
    }

    public synchronized void recordHistory(Track t) {
        if (t == null || t.videoId == null || t.videoId.isEmpty()) return;
        for (int i = recentHistory.size() - 1; i >= 0; i--) {
            if (t.videoId.equals(recentHistory.get(i).videoId)) recentHistory.remove(i);
        }
        recentHistory.add(0, new Track(t.videoId, t.title, t.channel, t.duration));
        while (recentHistory.size() > 25) recentHistory.remove(recentHistory.size() - 1);
        save();
    }

    public synchronized List<Track> getRecentHistory() {
        return new ArrayList<>(recentHistory);
    }

    public synchronized void queuePlayNext(Track t) {
        if (t == null || t.videoId == null || t.videoId.isEmpty()) return;
        if (playNextQueue.size() >= 50) playNextQueue.remove(0);
        playNextQueue.add(new Track(t.videoId, t.title, t.channel, t.duration));
    }

    public synchronized Track pollPlayNextQueue() {
        if (playNextQueue.isEmpty()) return null;
        return playNextQueue.remove(0);
    }

    public synchronized int getQueueSize() {
        return playNextQueue.size();
    }

    public synchronized void clearPlayNextQueue() {
        playNextQueue.clear();
    }

    public synchronized YtState getState() { return state; }

    public synchronized List<SavedPlaylist> getPlaylists() {
        return new ArrayList<>(playlists);
    }

    public synchronized SavedPlaylist findPlaylist(String id) {
        if (id == null) return null;
        for (SavedPlaylist p : playlists) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public synchronized SavedPlaylist getActivePlaylist() {
        SavedPlaylist p = findPlaylist(state.activePlaylistId);
        if (p == null && !playlists.isEmpty()) {
            p = playlists.get(0);
            state.activePlaylistId = p.id;
        }
        return p;
    }

    public synchronized void setActivePlaylist(String id) {
        if (findPlaylist(id) != null) {
            state.activePlaylistId = id;
            state.currentIndex = 0;
            save();
        }
    }

    public synchronized SavedPlaylist addOrUpdatePlaylist(String customName, String playlistId, String rawUrl) {
        for (SavedPlaylist existing : playlists) {
            if (existing.playlistId.equals(playlistId)) {
                if (customName != null && !customName.trim().isEmpty()) existing.name = customName.trim();
                existing.sourceUrl = rawUrl;
                state.activePlaylistId = existing.id;
                save();
                return existing;
            }
        }
        SavedPlaylist p = new SavedPlaylist();
        p.id = "yt_" + UUID.randomUUID().toString().substring(0, 8);
        p.playlistId = playlistId;
        p.sourceUrl = rawUrl;
        p.name = (customName != null && !customName.trim().isEmpty()) ? customName.trim() : "YouTube Playlist";
        playlists.add(p);
        state.activePlaylistId = p.id;
        state.currentIndex = 0;
        save();
        return p;
    }

    public synchronized void updatePlaylistTracks(String id, String fetchedTitle, List<Track> newTracks) {
        SavedPlaylist p = findPlaylist(id);
        if (p == null) return;
        if (fetchedTitle != null && !fetchedTitle.trim().isEmpty()
                && ("YouTube Playlist".equals(p.name) || p.name.isEmpty())) {
            p.name = fetchedTitle.trim();
        }
        p.tracks = new ArrayList<>(newTracks);
        p.lastSyncedMs = System.currentTimeMillis();
        if (id.equals(state.activePlaylistId) && state.currentIndex >= p.tracks.size()) {
            state.currentIndex = 0;
        }
        save();
    }

    public synchronized boolean deletePlaylist(String id) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).id.equals(id)) {
                playlists.remove(i);
                if (id.equals(state.activePlaylistId)) {
                    state.activePlaylistId = playlists.isEmpty() ? null : playlists.get(0).id;
                    state.currentIndex = 0;
                }
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized Track getCurrentTrack() {
        SavedPlaylist p = getActivePlaylist();
        if (p == null || p.tracks.isEmpty()) return null;
        if (state.currentIndex < 0 || state.currentIndex >= p.tracks.size()) {
            state.currentIndex = 0;
        }
        return p.tracks.get(state.currentIndex);
    }
}
