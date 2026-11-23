package com.buddyapps.kokerqualizer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Car Mode companion helper:
 * - #28 Voice-Announced Preset Changes (TTS)
 * - #31 GPS Speed-Linked Highway Boost (auto-engages >= 70 km/h, eases off <= 20 km/h)
 */
public class CarExtrasHelper {
    public interface SpeedCallback {
        void onSpeedUpdated(float kmh, boolean autoEngagedHighway);
    }

    private final Context appCtx;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private LocationManager locMgr;
    private LocationListener locListener;
    private boolean gpsActive = false;
    private float lastSpeedKmh = -1f;
    private boolean autoEngagedHwy = false;

    public CarExtrasHelper(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    public void initTts() {
        if (tts != null) return;
        try {
            tts = new TextToSpeech(appCtx, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    try { tts.setLanguage(Locale.US); } catch (Throwable ignored) {}
                    ttsReady = true;
                }
            });
        } catch (Throwable ignored) {
            tts = null;
        }
    }

    public void announcePreset(String presetName) {
        if (!EqStore.get(appCtx).getSettings().carVoiceAnnounce) return;
        if (presetName == null || presetName.trim().isEmpty()) return;
        if (tts == null) initTts();
        if (tts != null && ttsReady) {
            try {
                tts.speak(presetName, TextToSpeech.QUEUE_FLUSH, null, "koker_preset");
            } catch (Throwable ignored) {}
        }
    }

    public boolean hasLocationPermission() {
        return appCtx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || appCtx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public float getLastSpeedKmh() {
        return lastSpeedKmh;
    }

    public boolean isGpsActive() {
        return gpsActive;
    }

    public void startGpsSpeed(SpeedCallback cb) {
        if (gpsActive || !hasLocationPermission()) return;
        try {
            locMgr = (LocationManager) appCtx.getSystemService(Context.LOCATION_SERVICE);
            if (locMgr == null) return;
            locListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    if (loc == null) return;
                    float kmh = loc.hasSpeed() ? (loc.getSpeed() * 3.6f) : 0f;
                    lastSpeedKmh = Math.max(0f, kmh);
                    EqStore store = EqStore.get(appCtx);
                    EqStore.Settings s = store.getSettings();
                    if (s.carGpsHighway) {
                        if (kmh >= 70f && !s.highwayBoost) {
                            s.highwayBoost = true;
                            autoEngagedHwy = true;
                            store.save();
                            EqService.startOrRefresh(appCtx);
                        } else if (kmh <= 20f && autoEngagedHwy && s.highwayBoost) {
                            s.highwayBoost = false;
                            autoEngagedHwy = false;
                            store.save();
                            EqService.startOrRefresh(appCtx);
                        }
                    }
                    if (cb != null) cb.onSpeedUpdated(lastSpeedKmh, autoEngagedHwy);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            boolean hasFine = appCtx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            String provider = (hasFine && locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    ? LocationManager.GPS_PROVIDER
                    : (locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);
            if (provider != null) {
                locMgr.requestLocationUpdates(provider, 2500L, 5f, locListener, Looper.getMainLooper());
                gpsActive = true;
            }
        } catch (Throwable ignored) {
            gpsActive = false;
        }
    }

    public void stopGpsSpeed() {
        gpsActive = false;
        if (locMgr != null && locListener != null) {
            try { locMgr.removeUpdates(locListener); } catch (Throwable ignored) {}
        }
        locListener = null;
    }

    public void release() {
        stopGpsSpeed();
        ttsReady = false;
        if (tts != null) {
            try { tts.stop(); } catch (Throwable ignored) {}
            try { tts.shutdown(); } catch (Throwable ignored) {}
            tts = null;
        }
    }
}
