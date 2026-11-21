package com.buddyapps.kokerqualizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class YouTubePlayerView extends FrameLayout {
    public interface Listener {
        void onPlaybackStateChanged(boolean playing);
        void onTrackEnded();
        void onFallbackToFullWeb(String reason);
        void onWebUrlVisited(String url);
    }

    public interface RemoteBridge {
        void playNext();
        void playPrev();
        void togglePlayPause();
        boolean isPlaying();
        String getNowPlayingLabel();
    }

    private static RemoteBridge activeRemoteBridge;

    public static void setActiveRemoteBridge(RemoteBridge bridge) {
        activeRemoteBridge = bridge;
    }

    public static RemoteBridge getActiveRemoteBridge() {
        return activeRemoteBridge;
    }

    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean playerReady = false;
    private boolean isPlaying = false;
    private boolean fullWebMode = false;
    private boolean usingWebFallback = false;
    private String currentVideoId = null;
    private String currentPlaylistId = null;
    private String pendingVideoId = null;
    private boolean pendingAutoplay = true;
    private boolean iframeLoaded = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public YouTubePlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);

        webView = new WebView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (fullWebMode || usingWebFallback) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                // Keep in-app YouTube audio playing smoothly when switching to Car Mode or other apps!
                if (visibility != VISIBLE && isPlaying) {
                    super.onWindowVisibilityChanged(VISIBLE);
                } else {
                    super.onWindowVisibilityChanged(visibility);
                }
            }
        };
        webView.setBackgroundColor(Color.BLACK);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ws.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");

        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignored) {}

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Immediately flush cookies to disk so Google/YouTube sign-in is NEVER lost!
                try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                if ((fullWebMode || usingWebFallback) && url != null && url.contains("youtube.com")) {
                    if (listener != null && !url.contains("accounts.google.com")) {
                        listener.onWebUrlVisited(url);
                    }
                }
                if ((fullWebMode || usingWebFallback) && url != null && url.contains("youtube.com/watch")) {
                    // Bind HTML5 <video> events per element so SPA track switches on m.youtube.com always stay hooked!
                    view.evaluateJavascript(
                            "(function(){"
                                    + "var tries=0;"
                                    + "function hook(){"
                                    + "  var v=document.querySelector('video');"
                                    + "  if(!v){ if(++tries<15) setTimeout(hook,600); return; }"
                                    + "  if(v.__kokerBound) return; v.__kokerBound=true;"
                                    + "  v.addEventListener('play',function(){if(window.KokerYT)KokerYT.onState(1);});"
                                    + "  v.addEventListener('pause',function(){if(!v.ended && window.KokerYT)KokerYT.onState(2);});"
                                    + "  v.addEventListener('ended',function(){if(window.KokerYT)KokerYT.onState(0);});"
                                    + "  try{v.play();}catch(e){}"
                                    + "}"
                                    + "hook();"
                                    + "})();", null);
                }
            }
        });
        webView.addJavascriptInterface(new JsBridge(), "KokerYT");

        addView(webView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setListener(Listener l) { this.listener = l; }
    public boolean isPlaying() { return isPlaying; }
    public boolean isFullWebActive() { return fullWebMode || usingWebFallback; }
    public boolean canGoBackInWeb() { return (fullWebMode || usingWebFallback) && webView != null && webView.canGoBack(); }
    public void goBackInWeb() { if (webView != null && webView.canGoBack()) webView.goBack(); }
    public void loadDirectUrl(String url) {
        if (url == null || url.isEmpty()) return;
        usingWebFallback = true;
        iframeLoaded = false;
        playerReady = false;
        webView.loadUrl(url);
    }
    public void stopAndReset() {
        isPlaying = false;
        usingWebFallback = false;
        iframeLoaded = false;
        playerReady = false;
        currentVideoId = null;
        currentPlaylistId = null;
        pendingVideoId = null;
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        } catch (Throwable ignored) {}
        if (listener != null) listener.onPlaybackStateChanged(false);
    }
    public static void flushCookiesNow() {
        try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
    }

    public void setFullWebMode(boolean full) {
        if (this.fullWebMode == full) return;
        this.fullWebMode = full;
        this.usingWebFallback = false;
        iframeLoaded = false;
        playerReady = false;
        if (currentVideoId != null) {
            loadTrack(currentVideoId, currentPlaylistId, isPlaying);
        } else if (full && currentPlaylistId != null) {
            webView.loadUrl("https://m.youtube.com/playlist?list=" + currentPlaylistId);
        }
    }

    public void loadTrack(String videoId, String playlistId, boolean autoplay) {
        usingWebFallback = false;
        if (videoId == null || videoId.isEmpty()) {
            if (playlistId != null && !playlistId.isEmpty() && fullWebMode) {
                currentPlaylistId = playlistId;
                webView.loadUrl("https://m.youtube.com/playlist?list=" + playlistId);
            }
            return;
        }
        currentVideoId = videoId;
        currentPlaylistId = playlistId;

        if (fullWebMode) {
            String url = "https://m.youtube.com/watch?v=" + videoId
                    + (playlistId != null && !playlistId.isEmpty() ? ("&list=" + playlistId) : "");
            isPlaying = autoplay;
            webView.loadUrl(url);
            if (listener != null) listener.onPlaybackStateChanged(isPlaying);
            return;
        }

        if (iframeLoaded && playerReady) {
            String js = autoplay
                    ? ("if(window.kokerLoad) kokerLoad('" + sanitize(videoId) + "', true);")
                    : ("if(window.kokerLoad) kokerLoad('" + sanitize(videoId) + "', false);");
            webView.evaluateJavascript(js, null);
        } else {
            pendingVideoId = videoId;
            pendingAutoplay = autoplay;
            loadIframeShell(videoId, autoplay);
        }
    }

    public void play() {
        if (fullWebMode || usingWebFallback) {
            webView.evaluateJavascript("var v=document.querySelector('video'); if(v){v.play();}", null);
            isPlaying = true;
            if (listener != null) listener.onPlaybackStateChanged(true);
            return;
        }
        if (playerReady) {
            webView.evaluateJavascript("if(window.kokerPlay) kokerPlay();", null);
        } else if (currentVideoId != null) {
            loadTrack(currentVideoId, currentPlaylistId, true);
        }
    }

    public void pause() {
        if (fullWebMode || usingWebFallback) {
            webView.evaluateJavascript("var v=document.querySelector('video'); if(v){v.pause();}", null);
            isPlaying = false;
            if (listener != null) listener.onPlaybackStateChanged(false);
            return;
        }
        if (playerReady) {
            webView.evaluateJavascript("if(window.kokerPause) kokerPause();", null);
        }
    }

    public void togglePlayPause() {
        if (isPlaying) pause();
        else play();
    }

    public void destroyPlayer() {
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        } catch (Throwable ignored) {}
    }

    private void loadIframeShell(String initialVideoId, boolean autoplay) {
        iframeLoaded = true;
        playerReady = false;
        String safeVid = sanitize(initialVideoId);
        String html = "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'/>"
                + "<style>html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#05060A;overflow:hidden;}</style>"
                + "</head><body><div id='player'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>"
                + "var player=null; var wantAuto=" + (autoplay ? "true" : "false") + ";"
                + "function onYouTubeIframeAPIReady(){"
                + "  player=new YT.Player('player',{"
                + "    width:'100%',height:'100%',videoId:'" + safeVid + "',"
                + "    playerVars:{playsinline:1,autoplay:" + (autoplay ? 1 : 0) + ",rel:0,modestbranding:1,origin:'https://www.youtube.com'},"
                + "    events:{"
                + "      onReady:function(e){ if(window.KokerYT) KokerYT.onReady(); if(wantAuto){try{e.target.playVideo();}catch(err){}} },"
                + "      onStateChange:function(e){ if(window.KokerYT) KokerYT.onState(e.data); },"
                + "      onError:function(e){ if(window.KokerYT) KokerYT.onError(e.data); }"
                + "    }"
                + "  });"
                + "}"
                + "window.kokerLoad=function(vid,auto){ if(!player) return; if(auto) player.loadVideoById(vid); else player.cueVideoById(vid); };"
                + "window.kokerPlay=function(){ if(player) player.playVideo(); };"
                + "window.kokerPause=function(){ if(player) player.pauseVideo(); };"
                + "</script></body></html>";

        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private static String sanitize(String id) {
        return id == null ? "" : id.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private class JsBridge {
        @JavascriptInterface
        public void onReady() {
            mainHandler.post(() -> {
                playerReady = true;
                if (pendingVideoId != null && !pendingVideoId.equals(currentVideoId)) {
                    loadTrack(pendingVideoId, currentPlaylistId, pendingAutoplay);
                }
                pendingVideoId = null;
            });
        }

        @JavascriptInterface
        public void onState(int state) {
            mainHandler.post(() -> {
                if (state == 1) {
                    isPlaying = true;
                    if (listener != null) listener.onPlaybackStateChanged(true);
                } else if (state == 2) {
                    isPlaying = false;
                    if (listener != null) listener.onPlaybackStateChanged(false);
                } else if (state == 0) {
                    isPlaying = false;
                    if (listener != null) {
                        listener.onPlaybackStateChanged(false);
                        listener.onTrackEnded();
                    }
                }
            });
        }

        @JavascriptInterface
        public void onError(int code) {
            mainHandler.post(() -> {
                if ((code == 101 || code == 150 || code == 153) && currentVideoId != null) {
                    usingWebFallback = true;
                    isPlaying = true;
                    iframeLoaded = false;
                    playerReady = false;
                    String url = "https://m.youtube.com/watch?v=" + currentVideoId
                            + (currentPlaylistId != null && !currentPlaylistId.isEmpty() ? ("&list=" + currentPlaylistId) : "");
                    webView.loadUrl(url);
                    if (listener != null) {
                        listener.onPlaybackStateChanged(true);
                        listener.onFallbackToFullWeb("Label blocked embed — switched seamlessly to in-app YouTube view");
                    }
                }
            });
        }
    }
}
