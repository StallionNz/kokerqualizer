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
        void onTrackUnavailable(int errorCode);
    }

    public interface RemoteBridge {
        void playNext();
        void playPrev();
        void togglePlayPause();
        void stopAll();
        boolean isPlaying();
        String getNowPlayingLabel();
    }

    private static volatile RemoteBridge activeRemoteBridge;

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
    private String initialIframeVideoId = null;
    private String pendingVideoId = null;
    private boolean pendingAutoplay = true;
    private boolean wantAutoplay = true;
    private float playbackSpeed = 1.0f; // #34
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
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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
                if (request == null || request.getUrl() == null) return true;
                String scheme = request.getUrl().getScheme();
                String host = request.getUrl().getHost();
                if (!"https".equalsIgnoreCase(scheme)) return true; // Block file://, http://, javascript:, intent://
                if (isTrustedYouTubeHost(host)) return false;
                try {
                    android.content.Intent ext = new android.content.Intent(android.content.Intent.ACTION_VIEW, request.getUrl());
                    ext.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(ext);
                } catch (Throwable ignored) {}
                return true;
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
                    // Bind HTML5 <video> events & defeat m.youtube.com's auto-mute / persisted muted localStorage!
                    view.evaluateJavascript(
                            "(function(){"
                                    + "window.__kokerUnmuteUntil=Date.now()+5500;"
                                    + "function clearMutedStorage(){"
                                    + "  try{"
                                    + "    var now=Date.now();"
                                    + "    var payload=JSON.stringify({data:JSON.stringify({volume:100,muted:false}),expiration:now+2592000000,creation:now});"
                                    + "    localStorage.setItem('yt-player-volume',payload);"
                                    + "    sessionStorage.setItem('yt-player-volume',payload);"
                                    + "  }catch(e){}"
                                    + "}"
                                    + "function forceUnmute(v){"
                                    + "  clearMutedStorage();"
                                    + "  try{"
                                    + "    var mp=document.getElementById('movie_player');"
                                    + "    if(mp){"
                                    + "      if(typeof mp.isMuted==='function' && mp.isMuted() && typeof mp.unMute==='function') mp.unMute();"
                                    + "      if(typeof mp.setVolume==='function') mp.setVolume(100);"
                                    + "    }"
                                    + "  }catch(e){}"
                                    + "  try{"
                                    + "    if(v){ if(v.muted) v.muted=false; if(v.volume<0.99) v.volume=1.0; }"
                                    + "  }catch(e){}"
                                    + "  try{"
                                    + "    var btn=document.querySelector('.ytp-unmute,button.ytp-unmute,.ytm-unmute-button,button[aria-label*=\"Unmute\"],button[aria-label*=\"unmute\"],.ytp-mute-button[data-title-no-tooltip*=\"Unmute\"]');"
                                    + "    if(btn && btn.offsetParent!==null) btn.click();"
                                    + "  }catch(e){}"
                                    + "}"
                                    + "clearMutedStorage();"
                                    + "if(!document.__kokerNavBound){"
                                    + "  document.__kokerNavBound=true;"
                                    + "  document.addEventListener('yt-navigate-finish',function(){"
                                    + "    window.__kokerUnmuteUntil=Date.now()+5500;"
                                    + "    setTimeout(function(){ forceUnmute(document.querySelector('video')); },200);"
                                    + "  });"
                                    + "}"
                                    + "var tries=0;"
                                    + "function hook(){"
                                    + "  var v=document.querySelector('video');"
                                    + "  if(!v){ if(++tries<20) setTimeout(hook,450); return; }"
                                    + "  if(!v.__kokerBound){"
                                    + "    v.__kokerBound=true;"
                                    + "    v.addEventListener('play',function(){ if(Date.now()<(window.__kokerUnmuteUntil||0)) forceUnmute(v); if(window.KokerYT)KokerYT.onState(1); });"
                                    + "    v.addEventListener('playing',function(){ if(Date.now()<(window.__kokerUnmuteUntil||0)) forceUnmute(v); });"
                                    + "    v.addEventListener('loadedmetadata',function(){ if(Date.now()<(window.__kokerUnmuteUntil||0)) forceUnmute(v); });"
                                    + "    v.addEventListener('canplay',function(){ if(Date.now()<(window.__kokerUnmuteUntil||0)) forceUnmute(v); });"
                                    + "    v.addEventListener('volumechange',function(){ if(Date.now()<(window.__kokerUnmuteUntil||0) && (v.muted || v.volume<0.95)) forceUnmute(v); });"
                                    + "    v.addEventListener('pause',function(){ if(!v.ended && window.KokerYT)KokerYT.onState(2); });"
                                    + "    v.addEventListener('ended',function(){ if(window.KokerYT)KokerYT.onState(0); });"
                                    + "  }"
                                    + "  forceUnmute(v);"
                                    + "  try{v.playbackRate=" + playbackSpeed + ";}catch(e){}"
                                    + (wantAutoplay ? "  try{v.play();}catch(e){}" : "")
                                    + "  if(window.__kokerUnmuteInt) clearInterval(window.__kokerUnmuteInt);"
                                    + "  var uTicks=0;"
                                    + "  window.__kokerUnmuteInt=setInterval(function(){"
                                    + "    var curV=document.querySelector('video');"
                                    + "    if(curV && (curV.muted || curV.volume<0.95)) forceUnmute(curV);"
                                    + "    if(++uTicks>=12 || Date.now()>(window.__kokerUnmuteUntil||0)) clearInterval(window.__kokerUnmuteInt);"
                                    + "  },350);"
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
        try {
            android.net.Uri parsed = android.net.Uri.parse(url);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || !isTrustedYouTubeHost(parsed.getHost())) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        usingWebFallback = true;
        iframeLoaded = false;
        playerReady = false;
        initialIframeVideoId = null;
        webView.loadUrl(url);
    }
    public void stopAndReset() {
        isPlaying = false;
        usingWebFallback = false;
        iframeLoaded = false;
        playerReady = false;
        currentVideoId = null;
        currentPlaylistId = null;
        initialIframeVideoId = null;
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
        initialIframeVideoId = null;
        if (currentVideoId != null) {
            loadTrack(currentVideoId, currentPlaylistId, isPlaying);
        } else if (full && currentPlaylistId != null) {
            webView.loadUrl("https://m.youtube.com/playlist?list=" + sanitize(currentPlaylistId));
        }
    }

    public void loadTrack(String videoId, String playlistId, boolean autoplay) {
        usingWebFallback = false;
        if (videoId == null || videoId.isEmpty()) {
            if (playlistId != null && !playlistId.isEmpty() && fullWebMode) {
                currentPlaylistId = sanitize(playlistId);
                webView.loadUrl("https://m.youtube.com/playlist?list=" + currentPlaylistId);
            }
            return;
        }
        String safeVid = sanitize(videoId);
        String safePl = playlistId != null ? sanitize(playlistId) : "";
        currentVideoId = safeVid;
        currentPlaylistId = safePl.isEmpty() ? null : safePl;
        wantAutoplay = autoplay;

        if (fullWebMode) {
            String url = "https://m.youtube.com/watch?v=" + safeVid
                    + (!safePl.isEmpty() ? ("&list=" + safePl) : "");
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
        } else if (iframeLoaded) {
            pendingVideoId = videoId;
            pendingAutoplay = autoplay;
        } else {
            initialIframeVideoId = videoId;
            pendingVideoId = null;
            pendingAutoplay = autoplay;
            loadIframeShell(videoId, autoplay);
        }
    }

    public void play() {
        if (fullWebMode || usingWebFallback) {
            webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.volume=1;v.play();}"
                            + "var mp=document.getElementById('movie_player');if(mp&&mp.unMute){try{mp.unMute();mp.setVolume(100);}catch(e){}}})();", null);
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

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = EqStore.clamp(speed, 0.5f, 2.0f);
        if (fullWebMode || usingWebFallback) {
            webView.evaluateJavascript("var v=document.querySelector('video');if(v){v.playbackRate=" + this.playbackSpeed + ";}", null);
        } else if (playerReady) {
            webView.evaluateJavascript("if(window.kokerSpeed) kokerSpeed(" + this.playbackSpeed + ");", null);
        }
    }

    public void destroyPlayer() {
        listener = null;
        mainHandler.removeCallbacksAndMessages(null);
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
                + "      onReady:function(e){ try{e.target.unMute();e.target.setVolume(100);e.target.setPlaybackRate(" + playbackSpeed + ");}catch(err){} if(window.KokerYT) KokerYT.onReady(); if(wantAuto){try{e.target.playVideo();}catch(err){}} },"
                + "      onStateChange:function(e){ if(e.data===1){try{e.target.unMute();e.target.setVolume(100);e.target.setPlaybackRate(" + playbackSpeed + ");}catch(err){}} if(window.KokerYT) KokerYT.onState(e.data); },"
                + "      onError:function(e){ if(window.KokerYT) KokerYT.onError(e.data); }"
                + "    }"
                + "  });"
                + "}"
                + "window.kokerLoad=function(vid,auto){ if(!player) return; try{player.unMute();player.setVolume(100);}catch(e){} if(auto) player.loadVideoById(vid); else player.cueVideoById(vid); };"
                + "window.kokerPlay=function(){ if(player){ try{player.unMute();player.setVolume(100);}catch(e){} player.playVideo(); } };"
                + "window.kokerPause=function(){ if(player) player.pauseVideo(); };"
                + "window.kokerSpeed=function(s){ if(player){ try{player.setPlaybackRate(s);}catch(e){} } };"
                + "</script></body></html>";

        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private static String sanitize(String id) {
        return id == null ? "" : id.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static boolean isTrustedYouTubeHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(java.util.Locale.US);
        return h.equals("youtube.com") || h.endsWith(".youtube.com")
                || h.equals("youtu.be")
                || h.equals("google.com") || h.endsWith(".google.com")
                || h.endsWith(".googlevideo.com")
                || h.endsWith(".googleapis.com")
                || h.endsWith(".gstatic.com")
                || h.endsWith(".ytimg.com");
    }

    private class JsBridge {
        @JavascriptInterface
        public void onReady() {
            mainHandler.post(() -> {
                playerReady = true;
                String pending = pendingVideoId;
                pendingVideoId = null;
                if (pending != null && !pending.equals(initialIframeVideoId)) {
                    loadTrack(pending, currentPlaylistId, pendingAutoplay);
                }
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
                } else if (code == 2 || code == 5 || code == 100) {
                    if (listener != null) listener.onTrackUnavailable(code);
                }
            });
        }
    }
}
