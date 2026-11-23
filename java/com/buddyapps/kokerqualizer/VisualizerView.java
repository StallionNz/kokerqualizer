package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Arrays;

public class VisualizerView extends View {
    private int mode = EqStore.VIZ_BARS;
    private int accentColor = 0xFF39FF14;
    private boolean eqActive = true;
    private int decaySpeed = 1;           // #13 0=Slow, 1=Med, 2=Fast
    private boolean gradientBars = false; // #14 multi-color neon gradient
    private boolean showEqOverlay = true; // #11 FabFilter-style EQ curve overlay
    private float[] eqOverlayGains = null;

    private final float[] bars = new float[24];
    private final float[] peaks = new float[24];
    private final float[] wave = new float[64];
    private float vuL = 0f;
    private float vuR = 0f;
    private float peakVuL = 0f;
    private float peakVuR = 0f;
    private long lastClipMs = 0;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final Path overlayPath = new Path();
    private final RectF rectF = new RectF();
    private int cachedBarH = -1;
    private int cachedBarW = -1;
    private int cachedBarCol = 0;
    private boolean cachedGrad = false;

    public VisualizerView(Context context) {
        super(context);
        gridPaint.setColor(0x18FFFFFF);
        gridPaint.setStrokeWidth(UiStyle.dpf(context, 0.8f));

        barPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        peakPaint.setStyle(Paint.Style.FILL);

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(UiStyle.dpf(context, 2.2f));
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);

        waveGlowPaint.setStyle(Paint.Style.STROKE);
        waveGlowPaint.setStrokeWidth(UiStyle.dpf(context, 6.5f));
        waveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        waveGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        overlayPaint.setStyle(Paint.Style.STROKE);
        overlayPaint.setStrokeWidth(UiStyle.dpf(context, 1.8f));
        overlayPaint.setStrokeCap(Paint.Cap.ROUND);
        overlayPaint.setStrokeJoin(Paint.Join.ROUND);

        overlayGlowPaint.setStyle(Paint.Style.STROKE);
        overlayGlowPaint.setStrokeWidth(UiStyle.dpf(context, 5.2f));
        overlayGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        overlayGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setTextSize(UiStyle.dpf(context, 10f));
        textPaint.setColor(UiStyle.TEXT_MUTED);
        textPaint.setFakeBoldText(true);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        invalidate();
    }

    public void setEqActive(boolean active) {
        this.eqActive = active;
        invalidate();
    }

    public void setDecaySpeed(int speed) {
        this.decaySpeed = Math.max(0, Math.min(2, speed));
    }

    public void setGradientBars(boolean grad) {
        if (this.gradientBars != grad) {
            this.gradientBars = grad;
            cachedBarCol = 0;
            invalidate();
        }
    }

    public void setEqOverlayCurve(float[] gains, boolean show) {
        this.eqOverlayGains = gains != null ? Arrays.copyOf(gains, gains.length) : null;
        this.showEqOverlay = show;
        invalidate();
    }

    public void updateAudio(float[] newBars, float[] newWave, float l, float r, boolean clip) {
        float peakStep = decaySpeed == 0 ? 0.007f : (decaySpeed == 2 ? 0.030f : 0.015f);
        float barFall = decaySpeed == 0 ? 0.88f : (decaySpeed == 2 ? 0.52f : 0.72f);
        if (newBars != null) {
            int n = Math.min(bars.length, newBars.length);
            for (int i = 0; i < n; i++) {
                float nb = newBars[i];
                bars[i] = nb >= bars[i] ? nb : (bars[i] * barFall + nb * (1f - barFall));
                if (bars[i] > peaks[i]) {
                    peaks[i] = bars[i];
                } else {
                    peaks[i] = Math.max(0f, peaks[i] - peakStep);
                }
            }
        }
        if (newWave != null) {
            System.arraycopy(newWave, 0, wave, 0, Math.min(wave.length, newWave.length));
        }
        vuL = l;
        vuR = r;
        peakVuL = l > peakVuL ? l : Math.max(0f, peakVuL - peakStep * 0.85f);
        peakVuR = r > peakVuR ? r : Math.max(0f, peakVuR - peakStep * 0.85f);
        if (clip) lastClipMs = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    public void clearData() {
        Arrays.fill(bars, 0f);
        Arrays.fill(peaks, 0f);
        Arrays.fill(wave, 0f);
        vuL = vuR = peakVuL = peakVuR = 0f;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = UiStyle.dpf(getContext(), 8);
        int col = eqActive ? accentColor : 0xFF475569;

        // Subtle horizontal scope grid
        for (int i = 1; i <= 3; i++) {
            float y = pad + (h - pad * 2) * (i / 4f);
            canvas.drawLine(pad, y, w - pad, y, gridPaint);
        }

        if (mode == EqStore.VIZ_BARS) {
            drawBars(canvas, w, h, pad, col);
            if (showEqOverlay) drawEqOverlay(canvas, w, h, pad, col);
        } else if (mode == EqStore.VIZ_WAVE) {
            drawWave(canvas, w, h, pad, col);
        } else if (mode == EqStore.VIZ_MIRROR) {
            drawMirrorBars(canvas, w, h, pad, col);
            if (showEqOverlay) drawEqOverlay(canvas, w, h, pad, col);
        } else {
            drawVu(canvas, w, h, pad, col);
        }
    }

    private void ensureBarShader(float w, float bottom, float pad, int col) {
        if (cachedBarH == (int) bottom && cachedBarW == (int) w && cachedBarCol == col && cachedGrad == gradientBars) return;
        cachedBarH = (int) bottom;
        cachedBarW = (int) w;
        cachedBarCol = col;
        cachedGrad = gradientBars;
        if (gradientBars && eqActive) {
            barPaint.setShader(new LinearGradient(
                    pad, 0, w - pad, 0,
                    new int[]{0xFF39FF14, 0xFF00E5FF, 0xFFB388FF, 0xFFFF2E93},
                    new float[]{0f, 0.35f, 0.70f, 1f},
                    Shader.TileMode.CLAMP));
        } else {
            barPaint.setShader(new LinearGradient(
                    0, bottom, 0, pad,
                    EqStore.withAlpha(col, 95),
                    col,
                    Shader.TileMode.CLAMP));
        }
    }

    private void drawBars(Canvas canvas, float w, float h, float pad, int col) {
        int n = bars.length;
        float availW = w - pad * 2;
        float gap = UiStyle.dpf(getContext(), 3f);
        float bw = Math.max(2f, (availW - gap * (n - 1)) / n);
        float bottom = h - pad;
        float maxH = h - pad * 2.2f;
        float rad = UiStyle.dpf(getContext(), 2.5f);

        ensureBarShader(w, bottom, pad, col);
        glowPaint.setColor(EqStore.withAlpha(col, 40));
        peakPaint.setColor(0xEEFFFFFF);

        for (int i = 0; i < n; i++) {
            float x = pad + i * (bw + gap);
            float bh = Math.max(UiStyle.dpf(getContext(), 2.5f), bars[i] * maxH);
            float top = bottom - bh;

            rectF.set(x - 1.5f, top - 2f, x + bw + 1.5f, bottom);
            canvas.drawRoundRect(rectF, rad, rad, glowPaint);

            rectF.set(x, top, x + bw, bottom);
            canvas.drawRoundRect(rectF, rad, rad, barPaint);

            if (peaks[i] > 0.03f) {
                float py = bottom - peaks[i] * maxH;
                rectF.set(x, py - UiStyle.dpf(getContext(), 2.4f), x + bw, py);
                canvas.drawRoundRect(rectF, 1.5f, 1.5f, peakPaint);
            }
        }
    }

    // #12 Symmetrical Mirror Spectrum Visualizer
    private void drawMirrorBars(Canvas canvas, float w, float h, float pad, int col) {
        int n = bars.length;
        float availW = w - pad * 2;
        float gap = UiStyle.dpf(getContext(), 3f);
        float bw = Math.max(2f, (availW - gap * (n - 1)) / n);
        float midY = h * 0.5f;
        float halfMax = (h - pad * 2.2f) * 0.5f;
        float rad = UiStyle.dpf(getContext(), 2.5f);

        ensureBarShader(w, h - pad, pad, col);
        glowPaint.setColor(EqStore.withAlpha(col, 42));
        peakPaint.setColor(0xCCFFFFFF);

        for (int i = 0; i < n; i++) {
            float x = pad + i * (bw + gap);
            float halfH = Math.max(UiStyle.dpf(getContext(), 2.0f), bars[i] * halfMax);
            rectF.set(x - 1.2f, midY - halfH - 1.5f, x + bw + 1.2f, midY + halfH + 1.5f);
            canvas.drawRoundRect(rectF, rad, rad, glowPaint);

            rectF.set(x, midY - halfH, x + bw, midY + halfH);
            canvas.drawRoundRect(rectF, rad, rad, barPaint);

            if (peaks[i] > 0.04f) {
                float py = peaks[i] * halfMax;
                rectF.set(x, midY - py - 2f, x + bw, midY - py);
                canvas.drawRoundRect(rectF, 1f, 1f, peakPaint);
                rectF.set(x, midY + py, x + bw, midY + py + 2f);
                canvas.drawRoundRect(rectF, 1f, 1f, peakPaint);
            }
        }
    }

    // #11 FabFilter-Style Live EQ Response Curve Overlay
    private void drawEqOverlay(Canvas canvas, float w, float h, float pad, int col) {
        float[] g = eqOverlayGains;
        if (g == null || g.length < 2) return;
        int n = g.length;
        float availW = w - pad * 2;
        float midY = h * 0.5f;
        float halfH = (h - pad * 2.2f) * 0.45f;

        overlayPath.reset();
        for (int i = 0; i < n; i++) {
            float x = pad + (availW * i) / (n - 1);
            float norm = EqStore.clamp(g[i] / EqStore.MAX_GAIN_DB, -1f, 1f);
            float y = midY - norm * halfH;
            if (i == 0) {
                overlayPath.moveTo(x, y);
            } else {
                float px = pad + (availW * (i - 1)) / (n - 1);
                float pn = EqStore.clamp(g[i - 1] / EqStore.MAX_GAIN_DB, -1f, 1f);
                float py = midY - pn * halfH;
                overlayPath.quadTo(px, py, (px + x) * 0.5f, (py + y) * 0.5f);
            }
        }
        float lastX = pad + availW;
        float lastY = midY - EqStore.clamp(g[n - 1] / EqStore.MAX_GAIN_DB, -1f, 1f) * halfH;
        overlayPath.lineTo(lastX, lastY);

        overlayGlowPaint.setColor(EqStore.withAlpha(0xFFFFFFFF, eqActive ? 55 : 22));
        canvas.drawPath(overlayPath, overlayGlowPaint);
        overlayPaint.setColor(EqStore.withAlpha(0xFFFFFFFF, eqActive ? 195 : 85));
        canvas.drawPath(overlayPath, overlayPaint);
    }

    private void drawWave(Canvas canvas, float w, float h, float pad, int col) {
        float midY = h * 0.5f;
        float amp = (h - pad * 2.4f) * 0.48f;
        float availW = w - pad * 2;
        int n = wave.length;

        wavePath.reset();
        for (int i = 0; i < n; i++) {
            float x = pad + (availW * i) / (n - 1);
            float y = midY - wave[i] * amp;
            if (i == 0) {
                wavePath.moveTo(x, y);
            } else {
                float prevX = pad + (availW * (i - 1)) / (n - 1);
                float prevY = midY - wave[i - 1] * amp;
                float cx = (prevX + x) * 0.5f;
                wavePath.quadTo(prevX, prevY, cx, (prevY + y) * 0.5f);
            }
        }

        waveGlowPaint.setColor(EqStore.withAlpha(col, 68));
        canvas.drawPath(wavePath, waveGlowPaint);

        wavePaint.setColor(col);
        canvas.drawPath(wavePath, wavePaint);
    }

    private void drawVu(Canvas canvas, float w, float h, float pad, int col) {
        float labelW = UiStyle.dpf(getContext(), 20f);
        float clipW = UiStyle.dpf(getContext(), 44f);
        float meterLeft = pad + labelW;
        float meterRight = w - pad - clipW;
        float meterW = Math.max(40f, meterRight - meterLeft);
        int segs = 24;
        float gap = UiStyle.dpf(getContext(), 2.5f);
        float segW = (meterW - gap * (segs - 1)) / segs;
        float rowH = Math.min(UiStyle.dpf(getContext(), 16f), (h - pad * 3.2f) * 0.42f);

        float topL = h * 0.34f - rowH * 0.5f;
        float topR = h * 0.68f - rowH * 0.5f;

        textPaint.setColor(UiStyle.TEXT_SECONDARY);
        canvas.drawText("L", pad + UiStyle.dpf(getContext(), 3), topL + rowH * 0.78f, textPaint);
        canvas.drawText("R", pad + UiStyle.dpf(getContext(), 3), topR + rowH * 0.78f, textPaint);

        drawVuRow(canvas, meterLeft, topL, rowH, segs, segW, gap, vuL, peakVuL, col);
        drawVuRow(canvas, meterLeft, topR, rowH, segs, segW, gap, vuR, peakVuR, col);

        // Clip warning LED on right
        boolean clipping = (System.currentTimeMillis() - lastClipMs) < 900;
        if (clipping) {
            postInvalidateDelayed(140);
        }
        float cx = w - pad - clipW * 0.45f;
        float cy = h * 0.45f;
        float r = UiStyle.dpf(getContext(), 6.5f);
        if (clipping) {
            ledPaint.setColor(0x55FF1744);
            canvas.drawCircle(cx, cy, r * 1.8f, ledPaint);
            ledPaint.setColor(0xFFFF1744);
        } else {
            ledPaint.setColor(0xFF28171D);
        }
        canvas.drawCircle(cx, cy, r, ledPaint);

        textPaint.setColor(clipping ? 0xFFFF5252 : UiStyle.TEXT_MUTED);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("CLIP", cx, cy + UiStyle.dpf(getContext(), 18f), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawVuRow(Canvas canvas, float left, float top, float rowH,
                           int segs, float segW, float gap, float level, float peak, int col) {
        int litCount = Math.round(level * segs);
        int peakIdx = Math.min(segs - 1, Math.round(peak * segs) - 1);
        float rad = UiStyle.dpf(getContext(), 2f);

        for (int i = 0; i < segs; i++) {
            float x = left + i * (segW + gap);
            boolean lit = i < litCount || (i == peakIdx && peak > 0.05f);
            int segCol;
            float frac = (float) i / segs;
            if (frac > 0.85f) {
                segCol = 0xFFFF1744; // Red clip zone
            } else if (frac > 0.65f) {
                segCol = 0xFFFFB300; // Amber hot zone
            } else {
                segCol = col;        // Active neon preset color
            }
            ledPaint.setColor(lit ? segCol : 0xFF1A202E);
            rectF.set(x, top, x + segW, top + rowH);
            canvas.drawRoundRect(rectF, rad, rad, ledPaint);
        }
    }
}
