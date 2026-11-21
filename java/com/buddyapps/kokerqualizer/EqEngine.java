package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.util.Log;

import java.util.Arrays;

public class EqEngine {
    private static final String TAG = "KokerEqEngine";

    public interface VisualizerListener {
        void onAudioData(float[] fftBars, float[] waveform, float vuLeft, float vuRight, boolean clipping, float bassEnergy);
    }

    public interface StatusListener {
        void onStatusChanged(String statusText, boolean active);
    }

    private static EqEngine instance;

    private final Context appCtx;
    private DynamicsProcessing dp;
    private Equalizer fallbackEq;
    private BassBoost bassBoostFx;
    private Virtualizer virtualizerFx;
    private Visualizer visualizer;

    private int activeSessionId = 0;
    private int lastOpenedMediaSession = 0;
    private int visualizerSessionId = -1;
    private boolean currentSubMode = false;
    private boolean mbcSupported = false;
    private boolean abFlatCompare = false;
    private boolean visualizerListening = false;

    private String statusText = "Initializing...";
    private boolean engineActive = false;

    // Live DSP telemetry for Headroom / Auto-Gain readout
    private float lastAutoGainDb = 0f;
    private float lastPeakBoostDb = 0f;
    private float lastMbcAmount = 0f;
    private float smoothBassEnergy = 0f;

    private VisualizerListener vizListener;
    private StatusListener statusListener;

    private final float[] smoothBars = new float[24];
    private final float[] waveBuf = new float[64];
    private float smoothVuL = 0f;
    private float smoothVuR = 0f;

    public static synchronized EqEngine get(Context ctx) {
        if (instance == null) {
            instance = new EqEngine(ctx.getApplicationContext());
        }
        return instance;
    }

    private EqEngine(Context ctx) {
        this.appCtx = ctx;
    }

    public synchronized void setStatusListener(StatusListener l) {
        this.statusListener = l;
        if (l != null) l.onStatusChanged(statusText, engineActive);
    }

    public synchronized void setVisualizerListener(VisualizerListener l) {
        this.vizListener = l;
        if (l != null) {
            startVisualizerIfPermitted();
        } else {
            stopVisualizer();
        }
    }

    public synchronized void setAbFlatCompare(boolean flat) {
        this.abFlatCompare = flat;
        applyCurrent();
    }

    public synchronized boolean isAbFlatCompare() { return abFlatCompare; }
    public synchronized String getStatusText() { return statusText; }
    public synchronized boolean isEngineActive() { return engineActive; }
    public synchronized boolean isDynamicsProcessingActive() { return dp != null && engineActive; }
    public synchronized float getLastAutoGainDb() { return lastAutoGainDb; }
    public synchronized float getLastPeakBoostDb() { return lastPeakBoostDb; }
    public synchronized float getLastMbcAmount() { return lastMbcAmount; }

    public String getActiveOutputRouteLabel() {
        try {
            AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return "Speaker";
            AudioDeviceInfo[] outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : outs) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    CharSequence pn = d.getProductName();
                    return pn != null && pn.length() > 0 ? ("BT: " + pn) : "Bluetooth";
                }
            }
            for (AudioDeviceInfo d : outs) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    return "USB-C Audio";
                }
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    return "Wired Headphones";
                }
            }
        } catch (Throwable ignored) {}
        return "Phone Speaker";
    }

    public synchronized void onExternalSessionOpened(int sessionId) {
        if (sessionId <= 0) return;
        lastOpenedMediaSession = sessionId;
        EqStore.Settings s = EqStore.get(appCtx).getSettings();
        if (s.hookMode == EqStore.HOOK_DUAL || s.hookMode == EqStore.HOOK_SESSION) {
            rebuildIfNeeded(true);
            applyCurrent();
        }
    }

    public synchronized void onExternalSessionClosed(int sessionId) {
        if (sessionId == lastOpenedMediaSession) {
            lastOpenedMediaSession = 0;
            EqStore.Settings s = EqStore.get(appCtx).getSettings();
            if (s.hookMode == EqStore.HOOK_DUAL || s.hookMode == EqStore.HOOK_SESSION) {
                rebuildIfNeeded(true);
                applyCurrent();
            }
        }
    }

    private int chooseTargetSession() {
        EqStore.Settings s = EqStore.get(appCtx).getSettings();
        if (s.hookMode == EqStore.HOOK_GLOBAL) {
            return 0;
        } else if (s.hookMode == EqStore.HOOK_SESSION) {
            return lastOpenedMediaSession;
        } else {
            return lastOpenedMediaSession > 0 ? lastOpenedMediaSession : 0;
        }
    }

    public synchronized void rebuildIfNeeded(boolean force) {
        EqStore store = EqStore.get(appCtx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;
        int targetSession = chooseTargetSession();

        if (!s.eqEnabled) {
            releaseDsp();
            updateStatus("EQ Off", false);
            return;
        }

        if (s.hookMode == EqStore.HOOK_SESSION && targetSession == 0) {
            releaseDsp();
            updateStatus("Waiting for Audio Session", false);
            return;
        }

        if (!force && (dp != null || fallbackEq != null)
                && activeSessionId == targetSession
                && currentSubMode == p.carSubMode) {
            return;
        }

        releaseDsp();
        activeSessionId = targetSession;
        currentSubMode = p.carSubMode;

        boolean dpOk = initDynamicsProcessing(targetSession, p.carSubMode);
        if (!dpOk && targetSession != 0 && s.hookMode == EqStore.HOOK_DUAL) {
            activeSessionId = 0;
            dpOk = initDynamicsProcessing(0, p.carSubMode);
        }

        if (!dpOk) {
            initFallbackEqualizer(activeSessionId);
        }

        try { bassBoostFx = new BassBoost(0, activeSessionId); }
        catch (Throwable t) { bassBoostFx = null; }

        try { virtualizerFx = new Virtualizer(0, activeSessionId); }
        catch (Throwable t) { virtualizerFx = null; }

        if (vizListener != null) {
            startVisualizerIfPermitted();
        }
    }

    private boolean initDynamicsProcessing(int sessionId, boolean subMode) {
        if (tryCreateDynamicsProcessing(sessionId, subMode, true)) {
            mbcSupported = true;
            return true;
        }
        // Fallback tier: 10-band PreEQ + Limiter without MBC if device HAL rejects MBC on this route
        if (tryCreateDynamicsProcessing(sessionId, subMode, false)) {
            mbcSupported = false;
            return true;
        }
        mbcSupported = false;
        dp = null;
        return false;
    }

    private boolean tryCreateDynamicsProcessing(int sessionId, boolean subMode, boolean enableMbc) {
        try {
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,             // stereo channels
                    true,          // preEqInUse
                    10,            // preEqBandCount
                    enableMbc,     // mbcInUse (3-band Mastering Compressor)
                    enableMbc ? 3 : 0,
                    false,         // postEqInUse
                    0,             // postEqBandCount
                    true           // limiterInUse
            );
            DynamicsProcessing.Config config = builder.build();
            float[] centers = subMode ? EqStore.SUB_CENTERS : EqStore.ISO_CENTERS;
            float[] cutoffs = computeCutoffs(centers);
            float[] mbcCutoffs = new float[]{180f, 3200f, 20000f};

            for (int ch = 0; ch < 2; ch++) {
                DynamicsProcessing.Eq eq = config.getPreEqByChannelIndex(ch);
                eq.setEnabled(true);
                for (int b = 0; b < 10; b++) {
                    DynamicsProcessing.EqBand band = eq.getBand(b);
                    band.setEnabled(true);
                    band.setCutoffFrequency(cutoffs[b]);
                    band.setGain(0f);
                }

                if (enableMbc) {
                    DynamicsProcessing.Mbc mbc = config.getMbcByChannelIndex(ch);
                    mbc.setEnabled(true);
                    for (int m = 0; m < 3; m++) {
                        DynamicsProcessing.MbcBand mb = mbc.getBand(m);
                        mb.setEnabled(true);
                        mb.setCutoffFrequency(mbcCutoffs[m]);
                        mb.setAttackTime(m == 0 ? 12f : (m == 1 ? 6f : 3f));
                        mb.setReleaseTime(m == 0 ? 120f : (m == 1 ? 80f : 55f));
                        mb.setRatio(1.0f);
                        mb.setThreshold(-18f);
                        mb.setKneeWidth(6f);
                        mb.setNoiseGateThreshold(-90f);
                        mb.setExpanderRatio(1.0f);
                        mb.setPreGain(0f);
                        mb.setPostGain(0f);
                    }
                }

                DynamicsProcessing.Limiter lim = config.getLimiterByChannelIndex(ch);
                lim.setEnabled(true);
                lim.setAttackTime(1.5f);
                lim.setReleaseTime(60f);
                lim.setRatio(10f);
                lim.setThreshold(-1.0f);
                lim.setPostGain(0f);
            }

            dp = new DynamicsProcessing(0, sessionId, config);
            dp.setEnabled(true);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "DynamicsProcessing (mbc=" + enableMbc + ") failed on session " + sessionId + ": " + t.getMessage());
            dp = null;
            return false;
        }
    }

    private void initFallbackEqualizer(int sessionId) {
        try {
            fallbackEq = new Equalizer(0, sessionId);
            fallbackEq.setEnabled(true);
        } catch (Throwable t) {
            fallbackEq = null;
        }
    }

    private static float[] computeCutoffs(float[] centers) {
        float[] cutoffs = new float[centers.length];
        for (int i = 0; i < centers.length - 1; i++) {
            cutoffs[i] = (float) Math.sqrt(centers[i] * centers[i + 1]);
        }
        cutoffs[centers.length - 1] = 20000f;
        return cutoffs;
    }

    public synchronized void applyCurrent() {
        EqStore store = EqStore.get(appCtx);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        if (p == null) return;

        if (!s.eqEnabled) {
            releaseDsp();
            lastAutoGainDb = 0f;
            lastPeakBoostDb = 0f;
            lastMbcAmount = 0f;
            updateStatus("EQ Off", false);
            return;
        }

        rebuildIfNeeded(false);

        if (dp == null && fallbackEq == null) {
            if (s.hookMode == EqStore.HOOK_SESSION && activeSessionId == 0) {
                updateStatus("Waiting for Audio Session", false);
            } else {
                updateStatus("Audio Engine Unavailable", false);
            }
            return;
        }

        if (abFlatCompare) {
            applyFlatBypass();
            lastAutoGainDb = 0f;
            lastMbcAmount = 0f;
            updateStatus("A/B Flat Compare", false);
            return;
        }

        // Compute effective 10-band gains including Bass Boost character & Highway Noise Boost
        float[] effective = Arrays.copyOf(p.bands10, 10);
        float bassFrac = p.bassBoost / 100f;
        if (bassFrac > 0.001f) {
            if (p.bassStyle == EqStore.BASS_TIGHT) {
                effective[0] += bassFrac * 4.0f;
                effective[1] += bassFrac * 7.5f;
                effective[2] += bassFrac * 4.5f;
                effective[3] += bassFrac * 1.0f;
            } else if (p.bassStyle == EqStore.BASS_DEEP) {
                effective[0] += bassFrac * 9.5f;
                effective[1] += bassFrac * 7.5f;
                effective[2] += bassFrac * 3.5f;
            } else { // BOOMY
                effective[0] += bassFrac * 8.5f;
                effective[1] += bassFrac * 10.0f;
                effective[2] += bassFrac * 7.5f;
                effective[3] += bassFrac * 3.5f;
            }
        }

        // Highway Road-Noise Compensation (+3.5 dB lows, +2.5 dB vocal presence, +2 dB air)
        if (s.highwayBoost) {
            effective[0] += 3.5f;
            effective[1] += 3.0f;
            effective[2] += 1.5f;
            effective[5] += 2.0f;
            effective[6] += 2.5f;
            effective[7] += 2.0f;
            effective[8] += 1.5f;
        }

        float maxPeak = 0f;
        for (int i = 0; i < 10; i++) {
            effective[i] = EqStore.clamp(effective[i], -EqStore.MAX_GAIN_DB, EqStore.MAX_GAIN_DB);
            if (effective[i] > maxPeak) maxPeak = effective[i];
        }
        lastPeakBoostDb = maxPeak;

        // Smart Auto-Gain prevents volume jump & digital clipping when boosting heavily
        float autoCompDb = 0f;
        if (s.autoGain && maxPeak > 1.5f) {
            autoCompDb = -Math.min(12.0f, (maxPeak - 1.5f) * 0.55f);
        }
        lastAutoGainDb = autoCompDb;

        // Stereo L/R Balance attenuation (-100 = full Left, +100 = full Right)
        float leftBalDb = 0f;
        float rightBalDb = 0f;
        if (p.balance > 1f) {
            leftBalDb = -(p.balance / 100f) * 24f;
        } else if (p.balance < -1f) {
            rightBalDb = -(-p.balance / 100f) * 24f;
        }

        float hwyPre = s.highwayBoost ? 1.5f : 0f;
        float baseGainL = EqStore.clamp(p.preampDb + hwyPre + autoCompDb + leftBalDb, -30f, 15f);
        float baseGainR = EqStore.clamp(p.preampDb + hwyPre + autoCompDb + rightBalDb, -30f, 15f);

        // Effective Multiband Compressor amount (boosted slightly in Highway Mode)
        float mbcAmt = EqStore.clamp(p.punchMbc + (s.highwayBoost ? 18f : 0f), 0f, 100f);
        lastMbcAmount = mbcAmt;

        if (dp != null) {
            try {
                dp.setEnabled(true);
                dp.setInputGainbyChannel(0, baseGainL);
                dp.setInputGainbyChannel(1, baseGainR);

                float mbcFrac = mbcAmt / 100f;
                boolean mbcOn = mbcFrac > 0.02f;

                float limFrac = p.limiter / 100f;
                float thresh = -0.3f - limFrac * 3.2f;
                float ratio = 3.0f + limFrac * 17.0f;
                float attack = 5.0f - limFrac * 4.2f;
                float release = 110f - limFrac * 70f;

                for (int ch = 0; ch < 2; ch++) {
                    for (int b = 0; b < 10; b++) {
                        DynamicsProcessing.EqBand band = dp.getPreEqBandByChannelIndex(ch, b);
                        band.setEnabled(true);
                        band.setGain(effective[b]);
                        dp.setPreEqBandByChannelIndex(ch, b, band);
                    }

                    // 3-Band Mastering Compressor (only if HAL supports MBC on this route)
                    if (mbcSupported) {
                        for (int m = 0; m < 3; m++) {
                            DynamicsProcessing.MbcBand mb = dp.getMbcBandByChannelIndex(ch, m);
                            mb.setEnabled(mbcOn);
                            if (mbcOn) {
                                if (m == 0) {
                                    mb.setRatio(1.4f + mbcFrac * 3.2f);
                                    mb.setThreshold(-10f - mbcFrac * 14f);
                                    mb.setPostGain(mbcFrac * 2.8f);
                                } else if (m == 1) {
                                    mb.setRatio(1.25f + mbcFrac * 2.2f);
                                    mb.setThreshold(-12f - mbcFrac * 12f);
                                    mb.setPostGain(mbcFrac * 2.2f);
                                } else {
                                    mb.setRatio(1.3f + mbcFrac * 2.4f);
                                    mb.setThreshold(-14f - mbcFrac * 12f);
                                    mb.setPostGain(mbcFrac * 2.0f);
                                }
                                mb.setKneeWidth(6f + mbcFrac * 4f);
                            }
                            dp.setMbcBandByChannelIndex(ch, m, mb);
                        }
                    }

                    DynamicsProcessing.Limiter lim = dp.getLimiterByChannelIndex(ch);
                    lim.setEnabled(limFrac > 0.02f);
                    lim.setThreshold(thresh);
                    lim.setRatio(ratio);
                    lim.setAttackTime(Math.max(0.5f, attack));
                    lim.setReleaseTime(Math.max(20f, release));
                    lim.setPostGain(limFrac * 0.8f);
                    dp.setLimiterByChannelIndex(ch, lim);
                }
            } catch (Throwable t) {
                Log.w(TAG, "DP apply error: " + t.getMessage());
            }
        } else if (fallbackEq != null) {
            try {
                fallbackEq.setEnabled(true);
                short numBands = fallbackEq.getNumberOfBands();
                short[] range = fallbackEq.getBandLevelRange();
                for (short b = 0; b < numBands; b++) {
                    int idx = Math.min(9, (b * 10) / Math.max(1, numBands));
                    int mb = (int) (effective[idx] * 100f);
                    short level = (short) Math.max(range[0], Math.min(range[1], mb));
                    fallbackEq.setBandLevel(b, level);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Fallback EQ apply error: " + t.getMessage());
            }
        }

        if (bassBoostFx != null) {
            try {
                boolean useHwBass = p.bassBoost > 5f && p.bassStyle != EqStore.BASS_TIGHT;
                bassBoostFx.setEnabled(useHwBass);
                if (useHwBass) {
                    float scale = (p.bassStyle == EqStore.BASS_BOOMY) ? 8.5f : 5.0f;
                    short strength = (short) Math.min(1000, Math.round(p.bassBoost * scale));
                    bassBoostFx.setStrength(strength);
                }
            } catch (Throwable ignored) {}
        }

        if (virtualizerFx != null) {
            try {
                boolean useVirt = p.virtualizer > 2f;
                virtualizerFx.setEnabled(useVirt);
                if (useVirt) {
                    short strength = (short) Math.min(1000, Math.round(p.virtualizer * 10f));
                    virtualizerFx.setStrength(strength);
                }
            } catch (Throwable ignored) {}
        }

        String modeTag = activeSessionId > 0 ? ("Session #" + activeSessionId) : "Global";
        String engineTag = (dp != null) ? (mbcSupported ? "DSP+MBC" : "DSP") : "EQ";
        String hwyTag = s.highwayBoost ? " • HWY" : "";
        updateStatus(engineTag + " • " + modeTag + hwyTag, true);
    }

    private void applyFlatBypass() {
        if (dp != null) {
            try {
                dp.setInputGainbyChannel(0, 0f);
                dp.setInputGainbyChannel(1, 0f);
                for (int ch = 0; ch < 2; ch++) {
                    for (int b = 0; b < 10; b++) {
                        DynamicsProcessing.EqBand band = dp.getPreEqBandByChannelIndex(ch, b);
                        band.setGain(0f);
                        dp.setPreEqBandByChannelIndex(ch, b, band);
                    }
                    if (mbcSupported) {
                        for (int m = 0; m < 3; m++) {
                            DynamicsProcessing.MbcBand mb = dp.getMbcBandByChannelIndex(ch, m);
                            mb.setEnabled(false);
                            dp.setMbcBandByChannelIndex(ch, m, mb);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (fallbackEq != null) {
            try { fallbackEq.setEnabled(false); } catch (Throwable ignored) {}
        }
        if (bassBoostFx != null) {
            try { bassBoostFx.setEnabled(false); } catch (Throwable ignored) {}
        }
        if (virtualizerFx != null) {
            try { virtualizerFx.setEnabled(false); } catch (Throwable ignored) {}
        }
    }

    public synchronized void startVisualizerIfPermitted() {
        if (visualizerListening && visualizer != null && visualizerSessionId == activeSessionId) return;
        if (appCtx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        stopVisualizer();
        try {
            int session = activeSessionId;
            visualizer = new Visualizer(session);
            visualizerSessionId = session;
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.min(512, Math.max(range[0], Math.min(256, range[1])));
            visualizer.setCaptureSize(size);
            int rate = Math.min(Visualizer.getMaxCaptureRate(), 18000);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    processWave(waveform);
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    processFft(fft);
                }
            }, rate, true, true);
            visualizer.setEnabled(true);
            visualizerListening = true;
        } catch (Throwable t) {
            Log.w(TAG, "Visualizer init failed: " + t.getMessage());
            stopVisualizer();
        }
    }

    private void processWave(byte[] raw) {
        if (raw == null || raw.length == 0) return;
        int n = waveBuf.length;
        float step = (float) raw.length / n;
        float peakL = 0f;
        float peakR = 0f;
        float sumSqL = 0f;
        float sumSqR = 0f;
        int cnt = 0;

        for (int i = 0; i < n; i++) {
            int idx = Math.min(raw.length - 1, (int) (i * step));
            float s = ((raw[idx] & 0xFF) - 128) / 128f;
            waveBuf[i] = s;
            float a = Math.abs(s);
            if ((i & 1) == 0) {
                sumSqL += s * s;
                if (a > peakL) peakL = a;
            } else {
                sumSqR += s * s;
                if (a > peakR) peakR = a;
            }
            cnt++;
        }

        float rmsL = (float) Math.sqrt(sumSqL / Math.max(1, cnt / 2));
        float rmsR = (float) Math.sqrt(sumSqR / Math.max(1, cnt / 2));

        EqStore.EqPreset p = EqStore.get(appCtx).getActivePreset();
        float bal = p != null ? p.balance / 100f : 0f;
        float scaleL = bal > 0f ? (1f - bal * 0.85f) : 1f;
        float scaleR = bal < 0f ? (1f + bal * 0.85f) : 1f;

        float targetL = Math.min(1f, (rmsL * 1.8f + peakL * 0.45f) * scaleL);
        float targetR = Math.min(1f, (rmsR * 1.8f + peakR * 0.45f) * scaleR);

        smoothVuL = targetL > smoothVuL ? smoothVuL * 0.4f + targetL * 0.6f : smoothVuL * 0.82f + targetL * 0.18f;
        smoothVuR = targetR > smoothVuR ? smoothVuR * 0.4f + targetR * 0.6f : smoothVuR * 0.82f + targetR * 0.18f;

        boolean clipping = peakL > 0.94f || peakR > 0.94f;
        VisualizerListener l = vizListener;
        if (l != null) {
            l.onAudioData(smoothBars, waveBuf, smoothVuL, smoothVuR, clipping, smoothBassEnergy);
        }
    }

    private void processFft(byte[] fft) {
        if (fft == null || fft.length < 8) return;
        int bins = fft.length / 2;
        int bars = smoothBars.length;
        float bassSum = 0f;
        for (int i = 0; i < bars; i++) {
            double t0 = (double) i / bars;
            double t1 = (double) (i + 1) / bars;
            int b0 = Math.max(1, (int) Math.pow(bins, t0));
            int b1 = Math.max(b0 + 1, Math.min(bins - 1, (int) Math.pow(bins, t1)));
            float maxMag = 0f;
            for (int b = b0; b <= b1 && (b * 2 + 1) < fft.length; b++) {
                float re = fft[b * 2];
                float im = fft[b * 2 + 1];
                float mag = (float) Math.hypot(re, im);
                if (mag > maxMag) maxMag = mag;
            }
            float db = (float) (20.0 * Math.log10(maxMag + 1.0));
            float norm = EqStore.clamp((db - 2f) / 36f, 0f, 1f);
            if (norm > smoothBars[i]) {
                smoothBars[i] = smoothBars[i] * 0.35f + norm * 0.65f;
            } else {
                smoothBars[i] = smoothBars[i] * 0.78f + norm * 0.22f;
            }
            if (i < 5) bassSum += smoothBars[i];
        }
        float rawBass = bassSum / 5f;
        smoothBassEnergy = rawBass > smoothBassEnergy
                ? smoothBassEnergy * 0.35f + rawBass * 0.65f
                : smoothBassEnergy * 0.82f + rawBass * 0.18f;

        VisualizerListener l = vizListener;
        if (l != null) {
            l.onAudioData(smoothBars, waveBuf, smoothVuL, smoothVuR, smoothVuL > 0.95f || smoothVuR > 0.95f, smoothBassEnergy);
        }
    }

    public synchronized void stopVisualizer() {
        visualizerListening = false;
        visualizerSessionId = -1;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Throwable ignored) {}
            try { visualizer.release(); } catch (Throwable ignored) {}
            visualizer = null;
        }
    }

    public synchronized void releaseDsp() {
        if (dp != null) {
            try { dp.setEnabled(false); } catch (Throwable ignored) {}
            try { dp.release(); } catch (Throwable ignored) {}
            dp = null;
        }
        if (fallbackEq != null) {
            try { fallbackEq.setEnabled(false); } catch (Throwable ignored) {}
            try { fallbackEq.release(); } catch (Throwable ignored) {}
            fallbackEq = null;
        }
        if (bassBoostFx != null) {
            try { bassBoostFx.setEnabled(false); } catch (Throwable ignored) {}
            try { bassBoostFx.release(); } catch (Throwable ignored) {}
            bassBoostFx = null;
        }
        if (virtualizerFx != null) {
            try { virtualizerFx.setEnabled(false); } catch (Throwable ignored) {}
            try { virtualizerFx.release(); } catch (Throwable ignored) {}
            virtualizerFx = null;
        }
    }

    private void updateStatus(String text, boolean active) {
        this.statusText = text;
        this.engineActive = active;
        StatusListener l = statusListener;
        if (l != null) {
            l.onStatusChanged(text, active);
        }
    }
}
