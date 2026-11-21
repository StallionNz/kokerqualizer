package com.buddyapps.kokerqualizer;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubePlaylistSync {
    public interface Callback {
        void onSuccess(String fetchedTitle, List<YouTubeStore.Track> tracks);
        void onError(String message);
    }

    private static final Pattern LIST_PARAM = Pattern.compile("[?&]list=([A-Za-z0-9_-]{10,80})");

    public static String extractPlaylistId(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        // Works even when pasted from YouTube Share sheet with surrounding text!
        Matcher m = LIST_PARAM.matcher(s);
        if (m.find()) {
            return m.group(1);
        }

        if (s.startsWith("http://") || s.startsWith("https://") || s.contains("youtube.com") || s.contains("youtu.be")) {
            if (!s.startsWith("http")) s = "https://" + s;
            try {
                Uri uri = Uri.parse(s);
                String list = uri.getQueryParameter("list");
                if (list != null && !list.trim().isEmpty()) return list.trim();
            } catch (Throwable ignored) {}
        }

        if (s.matches("[A-Za-z0-9_-]{10,80}")) {
            return s;
        }
        return null;
    }

    public static void syncAsync(String playlistId, Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                SyncResult res = fetchViaHtml(playlistId);
                if (res == null || res.tracks.isEmpty()) {
                    res = fetchViaRss(playlistId);
                }
                if (res != null && !res.tracks.isEmpty()) {
                    final SyncResult ok = res;
                    main.post(() -> callback.onSuccess(ok.title, ok.tracks));
                } else {
                    main.post(() -> callback.onError(
                            "No tracks found. Make sure the playlist is Public or Unlisted (or use Full YT Mode to sign in)."));
                }
            } catch (Throwable t) {
                final String msg = t.getMessage() != null ? t.getMessage() : "Network error";
                main.post(() -> callback.onError("Sync failed: " + msg));
            }
        }, "KokerYTSync").start();
    }

    private static class SyncResult {
        String title;
        List<YouTubeStore.Track> tracks = new ArrayList<>();
    }

    private static SyncResult fetchViaHtml(String playlistId) {
        try {
            String url = "https://www.youtube.com/playlist?list=" + Uri.encode(playlistId) + "&hl=en";
            String html = httpGet(url);
            if (html == null || html.isEmpty()) return null;

            String jsonStr = extractJsonAfterMarker(html, "var ytInitialData = ");
            if (jsonStr == null) jsonStr = extractJsonAfterMarker(html, "window[\"ytInitialData\"] = ");
            if (jsonStr == null) return null;

            JSONObject root = new JSONObject(jsonStr);
            SyncResult res = new SyncResult();

            JSONObject meta = root.optJSONObject("metadata");
            if (meta != null) {
                JSONObject pmr = meta.optJSONObject("playlistMetadataRenderer");
                if (pmr != null) res.title = pmr.optString("title", null);
            }

            Set<String> seen = new LinkedHashSet<>();
            collectTracksRecursive(root, res.tracks, seen);
            return res;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void collectTracksRecursive(Object node, List<YouTubeStore.Track> out, Set<String> seen) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;

            // 1. YouTube 2025/2026 modern schema: lockupViewModel
            if (obj.has("lockupViewModel")) {
                JSONObject lvm = obj.optJSONObject("lockupViewModel");
                if (lvm != null && "LOCKUP_CONTENT_TYPE_VIDEO".equals(lvm.optString("contentType", ""))) {
                    String vid = lvm.optString("contentId", "");
                    if (!vid.isEmpty() && !seen.contains(vid)) {
                        seen.add(vid);
                        String title = "";
                        String channel = "";
                        String dur = "";

                        JSONObject mdWrap = lvm.optJSONObject("metadata");
                        JSONObject lmd = mdWrap != null ? mdWrap.optJSONObject("lockupMetadataViewModel") : null;
                        if (lmd != null) {
                            JSONObject tObj = lmd.optJSONObject("title");
                            if (tObj != null) title = tObj.optString("content", "");

                            JSONObject innerMd = lmd.optJSONObject("metadata");
                            JSONObject cmd = innerMd != null ? innerMd.optJSONObject("contentMetadataViewModel") : null;
                            JSONArray rows = cmd != null ? cmd.optJSONArray("metadataRows") : null;
                            if (rows != null && rows.length() > 0) {
                                JSONObject row0 = rows.optJSONObject(0);
                                JSONArray parts = row0 != null ? row0.optJSONArray("metadataParts") : null;
                                if (parts != null && parts.length() > 0) {
                                    JSONObject p0 = parts.optJSONObject(0);
                                    JSONObject pt = p0 != null ? p0.optJSONObject("text") : null;
                                    if (pt != null) channel = pt.optString("content", "");
                                }
                            }
                        }

                        // Duration badge from thumbnailViewModel.overlays
                        JSONObject ci = lvm.optJSONObject("contentImage");
                        JSONObject tvm = ci != null ? ci.optJSONObject("thumbnailViewModel") : null;
                        JSONArray overlays = tvm != null ? tvm.optJSONArray("overlays") : null;
                        if (overlays != null) {
                            for (int k = 0; k < overlays.length(); k++) {
                                JSONObject ov = overlays.optJSONObject(k);
                                JSONObject bot = ov != null ? ov.optJSONObject("thumbnailBottomOverlayViewModel") : null;
                                JSONArray badges = bot != null ? bot.optJSONArray("badges") : null;
                                if (badges != null && badges.length() > 0) {
                                    JSONObject b0 = badges.optJSONObject(0);
                                    JSONObject tb = b0 != null ? b0.optJSONObject("thumbnailBadgeViewModel") : null;
                                    if (tb != null) {
                                        String txt = tb.optString("text", "");
                                        if (!txt.isEmpty()) { dur = txt; break; }
                                    }
                                }
                            }
                        }

                        if (!title.isEmpty() && !"[Deleted video]".equals(title) && !"[Private video]".equals(title)) {
                            out.add(new YouTubeStore.Track(vid, title, channel, dur));
                        }
                    }
                }
                return;
            }

            // 2. Classic YouTube schema: playlistVideoRenderer
            if (obj.has("playlistVideoRenderer")) {
                JSONObject r = obj.optJSONObject("playlistVideoRenderer");
                if (r != null) {
                    String vid = r.optString("videoId", "");
                    if (!vid.isEmpty() && !seen.contains(vid)) {
                        seen.add(vid);
                        String title = readText(r.optJSONObject("title"));
                        String channel = readText(r.optJSONObject("shortBylineText"));
                        String dur = readText(r.optJSONObject("lengthText"));
                        if (title != null && !title.isEmpty()
                                && !"[Deleted video]".equals(title)
                                && !"[Private video]".equals(title)) {
                            out.add(new YouTubeStore.Track(vid, title, channel, dur));
                        }
                    }
                }
                return;
            }

            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.optString(i);
                    collectTracksRecursive(obj.opt(k), out, seen);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                collectTracksRecursive(arr.opt(i), out, seen);
            }
        }
    }

    private static String readText(JSONObject o) {
        if (o == null) return "";
        if (o.has("simpleText")) return o.optString("simpleText", "");
        JSONArray runs = o.optJSONArray("runs");
        if (runs != null && runs.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                JSONObject r = runs.optJSONObject(i);
                if (r != null) sb.append(r.optString("text", ""));
            }
            return sb.toString();
        }
        return "";
    }

    private static String extractJsonAfterMarker(String html, String marker) {
        int start = html.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        while (start < html.length() && html.charAt(start) != '{') {
            if (html.charAt(start) == '\'' || html.charAt(start) == '"') return null;
            start++;
        }
        if (start >= html.length()) return null;

        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (inStr) {
                if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
            } else {
                if (c == '"') inStr = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return html.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private static SyncResult fetchViaRss(String playlistId) {
        try {
            String url = "https://www.youtube.com/feeds/videos.xml?playlist_id=" + Uri.encode(playlistId);
            String xml = httpGet(url);
            if (xml == null || xml.isEmpty()) return null;

            SyncResult res = new SyncResult();
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            boolean inEntry = false;
            String curVid = "";
            String curTitle = "";
            String curAuthor = "";
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("entry".equals(name)) {
                        inEntry = true;
                        curVid = "";
                        curTitle = "";
                        curAuthor = "";
                    } else if ("title".equals(name)) {
                        String txt = p.nextText();
                        if (inEntry) curTitle = txt;
                        else if (res.title == null) res.title = txt;
                    } else if (inEntry && ("videoId".equals(name) || "yt:videoId".equals(name))) {
                        curVid = p.nextText();
                    } else if (inEntry && "name".equals(name)) {
                        curAuthor = p.nextText();
                    }
                } else if (ev == XmlPullParser.END_TAG && "entry".equals(p.getName())) {
                    if (!curVid.isEmpty()) {
                        res.tracks.add(new YouTubeStore.Track(curVid, curTitle, curAuthor, ""));
                    }
                    inEntry = false;
                }
                ev = p.next();
            }
            return res;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            // Desktop Chrome UA ensures clean unescaped JSON ytInitialData with up to 100 tracks!
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            // Reuse signed-in YouTube WebView cookies when present so Private playlists sync too!
            String webCookies = null;
            try { webCookies = CookieManager.getInstance().getCookie("https://www.youtube.com"); }
            catch (Throwable ignored) {}
            if (webCookies != null && !webCookies.trim().isEmpty()) {
                conn.setRequestProperty("Cookie", webCookies + "; CONSENT=YES+cb");
            } else {
                conn.setRequestProperty("Cookie", "CONSENT=YES+cb");
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream is = conn.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
