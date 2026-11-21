package com.buddyapps.kokerqualizer;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.KeyEvent;

import java.util.List;

public class KokerMediaListenerService extends NotificationListenerService {

    public static boolean hasMediaAccess(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            if (flat == null) return false;
            ComponentName cn = new ComponentName(ctx, KokerMediaListenerService.class);
            return flat.contains(cn.flattenToString()) || flat.contains(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    public static MediaController getActiveController(Context ctx) {
        if (!hasMediaAccess(ctx)) return null;
        try {
            MediaSessionManager mgr = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mgr == null) return null;
            List<MediaController> list = mgr.getActiveSessions(new ComponentName(ctx, KokerMediaListenerService.class));
            if (list == null || list.isEmpty()) return null;

            // Prefer actively playing session first
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && (st.getState() == PlaybackState.STATE_PLAYING
                        || st.getState() == PlaybackState.STATE_BUFFERING)) {
                    return mc;
                }
            }
            // Next prefer paused / active music session
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PAUSED) {
                    return mc;
                }
            }
            return list.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getNowPlayingLabel(Context ctx) {
        MediaController mc = getActiveController(ctx);
        if (mc == null) return null;
        try {
            MediaMetadata md = mc.getMetadata();
            if (md == null) return null;
            String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (title == null || title.trim().isEmpty()) return null;
            if (artist != null && !artist.trim().isEmpty()) {
                return "♪  " + title.trim() + "  —  " + artist.trim();
            }
            return "♪  " + title.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPlayingNow(Context ctx) {
        MediaController mc = getActiveController(ctx);
        if (mc == null) return false;
        PlaybackState st = mc.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    public static boolean skipNext(Context ctx) {
        MediaController mc = getActiveController(ctx);
        if (mc != null) {
            try {
                mc.getTransportControls().skipToNext();
                return true;
            } catch (Throwable ignored) {}
        }
        sendTimedMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT);
        return false;
    }

    public static boolean skipPrev(Context ctx) {
        MediaController mc = getActiveController(ctx);
        if (mc != null) {
            try {
                mc.getTransportControls().skipToPrevious();
                return true;
            } catch (Throwable ignored) {}
        }
        sendTimedMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        return false;
    }

    public static boolean playPause(Context ctx) {
        MediaController mc = getActiveController(ctx);
        if (mc != null) {
            try {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    mc.getTransportControls().pause();
                } else {
                    mc.getTransportControls().play();
                }
                return true;
            } catch (Throwable ignored) {}
        }
        sendTimedMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        return false;
    }

    private static void sendTimedMediaKey(Context ctx, int keyCode) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            long downTime = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    long upTime = SystemClock.uptimeMillis();
                    am.dispatchMediaKeyEvent(new KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0));
                } catch (Throwable ignored) {}
            }, 45);
        } catch (Throwable ignored) {}
    }
}
