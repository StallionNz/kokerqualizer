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
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final RectF rectF = new RectF();
    private int cachedBarH = -1;
    private int cachedBarCol = 0;

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

    public void updateAudio(float[] newBars, float[] newWave, float l, float r, boolean clip) {
        if (newBars != null) {
            int n = Math.min(bars.length, newBars.length);
            for (int i = 0; i < n; i++) {
                bars[i] = newBars[i];
                if (bars[i] > peaks[i]) {
                    peaks[i] = bars[i];
                } else {
                    peaks[i] = Math.max(0f, peaks[i] - 0.015f);
                }
            }
        }
        if (newWave != null) {
            System.arraycopy(newWave, 0, wave, 0, Math.min(wave.length, newWave.length));
        }
        vuL = l;
        vuR = r;
        peakVuL = l > peakVuL ? l : Math.max(0f, peakVuL - 0.012f);
        peakVuR = r > peakVuR ? r : Math.max(0f, peakVuR - 0.012f);
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
        } else if (mode == EqStore.VIZ_WAVE) {
            drawWave(canvas, w, h, pad, col);
        } else {
            drawVu(canvas, w, h, pad, col);
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

        if (cachedBarH != (int) h || cachedBarCol != col) {
            cachedBarH = (int) h;
            cachedBarCol = col;
            barPaint.setShader(new LinearGradient(
                    0, bottom, 0, pad,
                    EqStore.withAlpha(col, 95),
                    col,
                    Shader.TileMode.CLAMP));
        }
        glowPaint.setColor(EqStore.withAlpha(col, 40));
        peakPaint.setColor(0xEEFFFFFF);

        for (int i = 0; i < n; i++) {
            float x = pad + i * (bw + gap);
            float bh = Math.max(UiStyle.dpf(getContext(), 2.5f), bars[i] * maxH);
            float top = bottom - bh;

            // Subtle outer glow bar
            rectF.set(x - 1.5f, top - 2f, x + bw + 1.5f, bottom);
            canvas.drawRoundRect(rectF, rad, rad, glowPaint);

            // Crisp neon bar
            rectF.set(x, top, x + bw, bottom);
            canvas.drawRoundRect(rectF, rad, rad, barPaint);

            // Peak hold cap
            if (peaks[i] > 0.03f) {
                float py = bottom - peaks[i] * maxH;
                rectF.set(x, py - UiStyle.dpf(getContext(), 2.4f), x + bw, py);
                canvas.drawRoundRect(rectF, 1.5f, 1.5f, peakPaint);
            }
        }
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
