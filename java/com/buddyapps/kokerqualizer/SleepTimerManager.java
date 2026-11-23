package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

/**
 * Sleep Timer (#32): Supports 15m / 30m / 60m / End of Track countdown
 * with smooth 5-second DSP fade-out before triggering Stop All & Exit!
 */
public class SleepTimerManager {
    public interface Listener {
        void onSleepTimerChanged();
    }

    private static SleepTimerManager instance;
    private final Context appCtx;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long endTimeMs = 0L;
    private boolean stopAfterCurrentTrack = false;
    private volatile Listener listener;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (endTimeMs <= 0L) return;
            long remMs = endTimeMs - System.currentTimeMillis();
            if (remMs <= 0L) {
                endTimeMs = 0L;
                stopAfterCurrentTrack = false;
                EqEngine.get(appCtx).setSleepFadeDb(0f);
                notifyListener();
                EqService.shutdownAppAndMusic(appCtx);
                return;
            }
            // Smooth 5-second fade-out right before sleep shutdown
            if (remMs <= 5000L) {
                float frac = 1f - (remMs / 5000f); // 0 -> 1
                EqEngine.get(appCtx).setSleepFadeDb(-frac * 32f);
                EqEngine.get(appCtx).applyCurrent();
                notifyListener();
                handler.postDelayed(this, 250);
            } else {
                notifyListener();
                handler.postDelayed(this, 1000);
            }
        }
    };

    public static synchronized SleepTimerManager get(Context ctx) {
        if (instance == null) instance = new SleepTimerManager(ctx.getApplicationContext());
        return instance;
    }

    private SleepTimerManager(Context ctx) {
        this.appCtx = ctx;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public boolean isActive() {
        return endTimeMs > 0L || stopAfterCurrentTrack;
    }

    public boolean isStopAfterTrack() {
        return stopAfterCurrentTrack;
    }

    public void startMinutes(int minutes) {
        cancel();
        if (minutes <= 0) return;
        endTimeMs = System.currentTimeMillis() + minutes * 60_000L;
        handler.post(tick);
        notifyListener();
    }

    public void setStopAfterCurrentTrack() {
        cancel();
        stopAfterCurrentTrack = true;
        notifyListener();
    }

    public void cancel() {
        cancelSilent();
        EqEngine.get(appCtx).applyCurrent();
    }

    public void cancelSilent() {
        handler.removeCallbacks(tick);
        endTimeMs = 0L;
        stopAfterCurrentTrack = false;
        EqEngine.get(appCtx).setSleepFadeDb(0f);
        notifyListener();
    }

    /**
     * Called when an in-app track finishes. Returns true if Sleep Timer handled shutdown.
     */
    public boolean onTrackFinished() {
        if (!stopAfterCurrentTrack) return false;
        stopAfterCurrentTrack = false;
        endTimeMs = 0L;
        EqEngine.get(appCtx).setSleepFadeDb(0f);
        notifyListener();
        EqService.shutdownAppAndMusic(appCtx);
        return true;
    }

    public String getStatusLabel() {
        if (stopAfterCurrentTrack) return "⏰ Track End";
        if (endTimeMs <= 0L) return "⏰ Sleep";
        long remSec = Math.max(0L, (endTimeMs - System.currentTimeMillis()) / 1000L);
        long mins = remSec / 60L;
        long secs = remSec % 60L;
        if (mins >= 1) return String.format(Locale.US, "⏰ %d:%02d", mins, secs);
        return String.format(Locale.US, "⏰ %ds", secs);
    }

    private void notifyListener() {
        Listener l = listener;
        if (l != null) handler.post(l::onSleepTimerChanged);
    }
}
