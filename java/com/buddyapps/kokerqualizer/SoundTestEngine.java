package com.buddyapps.kokerqualizer;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

public class SoundTestEngine {
    public static final int MODE_SINE = 0;
    public static final int MODE_SUB_SWEEP = 1;
    public static final int MODE_FULL_SWEEP = 2;
    public static final int MODE_PINK_NOISE = 3;

    public static final int CH_LEFT = -1;
    public static final int CH_BOTH = 0;
    public static final int CH_RIGHT = 1;

    public interface FreqCallback {
        void onCurrentFreq(float hz);
    }

    private static final int SAMPLE_RATE = 44100;
    private volatile boolean playing = false;
    private volatile int mode = MODE_SINE;
    private volatile float targetFreqHz = 50f;
    private volatile int channelMode = CH_BOTH;
    private volatile float volume = 0.55f;
    private volatile FreqCallback freqCallback;
    private Thread thread;

    public void setMode(int mode) { this.mode = mode; }
    public int getMode() { return mode; }
    public void setTargetFreqHz(float hz) { this.targetFreqHz = Math.max(20f, Math.min(20000f, hz)); }
    public float getTargetFreqHz() { return targetFreqHz; }
    public void setChannelMode(int ch) { this.channelMode = ch; }
    public int getChannelMode() { return channelMode; }
    public void setVolume(float vol) { this.volume = Math.max(0.05f, Math.min(0.95f, vol)); }
    public float getVolume() { return volume; }
    public void setFreqCallback(FreqCallback cb) { this.freqCallback = cb; }
    public boolean isPlaying() { return playing; }

    public synchronized void start() {
        if (playing) return;
        playing = true;
        thread = new Thread(this::runLoop, "KokerSoundTest");
        thread.start();
    }

    public synchronized void stop() {
        playing = false;
        if (thread != null) {
            try { thread.join(350); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }

    private void runLoop() {
        int minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufBytes = Math.max(minBuf, 4096);
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            int framesPerChunk = 1024;
            short[] pcm = new short[framesPerChunk * 2];
            double phase = 0.0;
            double sweepPos = 0.0;
            float env = 0f;
            Random rnd = new Random();

            // Paul Kellet pink noise state
            double b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;

            track.play();
            long lastReportMs = 0;

            while (playing) {
                int m = mode;
                int ch = channelMode;
                float vol = volume;
                float freq = targetFreqHz;

                for (int i = 0; i < framesPerChunk; i++) {
                    env = Math.min(1f, env + 0.002f); // smooth clickless fade-in
                    double sample;

                    if (m == MODE_PINK_NOISE) {
                        double white = rnd.nextDouble() * 2.0 - 1.0;
                        b0 = 0.99886 * b0 + white * 0.0555179;
                        b1 = 0.99332 * b1 + white * 0.0750759;
                        b2 = 0.96900 * b2 + white * 0.1538520;
                        b3 = 0.86650 * b3 + white * 0.3104856;
                        b4 = 0.55000 * b4 + white * 0.5329522;
                        b5 = -0.7616 * b5 - white * 0.0168980;
                        sample = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11;
                        b6 = white * 0.115926;
                    } else {
                        if (m == MODE_SUB_SWEEP) {
                            sweepPos += 1.0 / (SAMPLE_RATE * 6.0); // 6 sec sweep 20..120 Hz
                            if (sweepPos >= 1.0) sweepPos -= 1.0;
                            freq = (float) (20.0 * Math.pow(120.0 / 20.0, sweepPos));
                        } else if (m == MODE_FULL_SWEEP) {
                            sweepPos += 1.0 / (SAMPLE_RATE * 10.0); // 10 sec sweep 20..16000 Hz
                            if (sweepPos >= 1.0) sweepPos -= 1.0;
                            freq = (float) (20.0 * Math.pow(16000.0 / 20.0, sweepPos));
                        }
                        phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;
                        sample = Math.sin(phase);
                    }

                    double amp = sample * vol * env;
                    amp = Math.max(-0.98, Math.min(0.98, amp));
                    short val = (short) (amp * 32767.0);
                    pcm[i * 2] = (ch == CH_RIGHT) ? 0 : val;
                    pcm[i * 2 + 1] = (ch == CH_LEFT) ? 0 : val;
                }

                track.write(pcm, 0, pcm.length);

                long now = System.currentTimeMillis();
                FreqCallback cb = freqCallback;
                if (cb != null && now - lastReportMs > 90) {
                    lastReportMs = now;
                    cb.onCurrentFreq(freq);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Throwable ignored) {}
                try { track.release(); } catch (Throwable ignored) {}
            }
            playing = false;
        }
    }
}
