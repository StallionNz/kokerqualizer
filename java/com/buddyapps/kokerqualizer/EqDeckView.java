package com.buddyapps.kokerqualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Arrays;
import java.util.Locale;

public class EqDeckView extends View {
    public interface Listener {
        void onBandChanging(int bandIndex, float gainDb);
        void onBandChangeFinished();
        void onLockedTouch();
    }

    private float[] gains = new float[10];
    private float[] animStart = new float[10];
    private float[] animTarget = new float[10];
    private ValueAnimator morphAnim;
    private int bandCount = 10;
    private boolean carSubMode = false;
    private boolean curveMode = false;
    private boolean brushMode = false;
    private boolean locked = false;
    private boolean eqActive = true;
    private int accentColor = 0xFF39FF14;
    private Listener listener;

    private int draggingBand = -1;
    private int lastBrushBand = -1;
    private float lastBrushDb = 0f;
    private int lastTappedBand = -1;
    private long lastTapMs = 0;
    private int lastHapticStep = Integer.MIN_VALUE;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private final Path fillPath = new Path();
    private final RectF rectF = new RectF();
    private int cachedCurveH = -1;
    private int cachedCurveCol = 0;

    public EqDeckView(Context context) {
        super(context);
        gridPaint.setColor(0x16FFFFFF);
        gridPaint.setStrokeWidth(UiStyle.dpf(context, 1f));

        zeroPaint.setColor(0x38FFFFFF);
        zeroPaint.setStrokeWidth(UiStyle.dpf(context, 1.4f));

        trackPaint.setColor(0xFF1A202E);
        trackPaint.setStyle(Paint.Style.FILL);

        fillPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setStrokeWidth(UiStyle.dpf(context, 1.5f));

        labelPaint.setTextSize(UiStyle.dpf(context, 10.5f));
        labelPaint.setColor(UiStyle.TEXT_SECONDARY);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        valPaint.setTextSize(UiStyle.dpf(context, 9.5f));
        valPaint.setColor(UiStyle.TEXT_PRIMARY);
        valPaint.setTextAlign(Paint.Align.CENTER);

        hudBgPaint.setStyle(Paint.Style.FILL);
        hudTextPaint.setTextSize(UiStyle.dpf(context, 11.5f));
        hudTextPaint.setTextAlign(Paint.Align.CENTER);
        hudTextPaint.setFakeBoldText(true);

        curveLinePaint.setStyle(Paint.Style.STROKE);
        curveLinePaint.setStrokeWidth(UiStyle.dpf(context, 2.8f));
        curveLinePaint.setStrokeCap(Paint.Cap.ROUND);
        curveLinePaint.setStrokeJoin(Paint.Join.ROUND);

        curveGlowPaint.setStyle(Paint.Style.STROKE);
        curveGlowPaint.setStrokeWidth(UiStyle.dpf(context, 9f));
        curveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        curveGlowPaint.setStrokeJoin(Paint.Join.ROUND);

        curveFillPaint.setStyle(Paint.Style.FILL);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setState(float[] bands10, boolean tenBand, boolean carSub,
                         boolean curve, boolean brush, boolean locked,
                         boolean eqActive, int accentColor, boolean animate) {
        int newCount = tenBand ? 10 : 5;
        float[] nextTarget = tenBand ? Arrays.copyOf(bands10, 10) : EqStore.to5Bands(bands10);
        boolean sameCount = (this.bandCount == newCount) && (this.gains.length == newCount);

        this.bandCount = newCount;
        this.carSubMode = carSub;
        this.curveMode = curve;
        this.brushMode = brush;
        this.locked = locked;
        this.eqActive = eqActive;
        this.accentColor = accentColor;

        if (morphAnim != null) {
            morphAnim.cancel();
            morphAnim = null;
        }

        if (animate && sameCount && draggingBand < 0) {
            animStart = Arrays.copyOf(this.gains, newCount);
            animTarget = Arrays.copyOf(nextTarget, newCount);
            boolean diff = false;
            for (int i = 0; i < newCount; i++) {
                if (Math.abs(animStart[i] - animTarget[i]) > 0.1f) { diff = true; break; }
            }
            if (diff) {
                morphAnim = ValueAnimator.ofFloat(0f, 1f);
                morphAnim.setDuration(240);
                morphAnim.setInterpolator(new DecelerateInterpolator(2.0f));
                morphAnim.addUpdateListener(a -> {
                    float t = (float) a.getAnimatedValue();
                    for (int i = 0; i < newCount; i++) {
                        gains[i] = animStart[i] + (animTarget[i] - animStart[i]) * t;
                    }
                    invalidate();
                });
                morphAnim.start();
                return;
            }
        }
        this.gains = nextTarget;
        invalidate();
    }

    private float topY() { return UiStyle.dpf(getContext(), 32f); }
    private float bottomY() { return getHeight() - UiStyle.dpf(getContext(), 30f); }
    private float leftX() { return UiStyle.dpf(getContext(), 30f); }
    private float rightX() { return getWidth() - UiStyle.dpf(getContext(), 12f); }

    private float bandX(int idx) {
        float lx = leftX();
        float rx = rightX();
        float step = (rx - lx) / bandCount;
        return lx + step * (idx + 0.5f);
    }

    private float dbToY(float db) {
        float t = topY();
        float b = bottomY();
        float norm = (EqStore.MAX_GAIN_DB - db) / (2f * EqStore.MAX_GAIN_DB);
        return t + norm * (b - t);
    }

    private float yToDb(float y) {
        float t = topY();
        float b = bottomY();
        float norm = EqStore.clamp((y - t) / Math.max(1f, b - t), 0f, 1f);
        float db = EqStore.MAX_GAIN_DB - norm * (2f * EqStore.MAX_GAIN_DB);
        if (Math.abs(db) < 0.45f) db = 0f;
        return Math.round(db * 2f) / 2f;
    }

    private int xToBand(float x) {
        float lx = leftX();
        float rx = rightX();
        float step = (rx - lx) / bandCount;
        int idx = (int) ((x - lx) / Math.max(1f, step));
        return Math.max(0, Math.min(bandCount - 1, idx));
    }

    private String[] activeLabels() {
        return bandCount == 10
                ? (carSubMode ? EqStore.SUB_LABELS_10 : EqStore.ISO_LABELS_10)
                : (carSubMode ? EqStore.SUB_LABELS_5 : EqStore.ISO_LABELS_5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float t = topY();
        float b = bottomY();
        float lx = leftX();
        float rx = rightX();
        int col = eqActive ? accentColor : 0xFF475569;

        // Horizontal dB scale lines (+18, +9, 0, -9, -18)
        float[] marks = new float[]{18f, 9f, 0f, -9f, -18f};
        valPaint.setTextAlign(Paint.Align.RIGHT);
        valPaint.setColor(UiStyle.TEXT_MUTED);
        for (float m : marks) {
            float y = dbToY(m);
            canvas.drawLine(lx, y, rx, y, m == 0f ? zeroPaint : gridPaint);
            String txt = (m > 0 ? "+" : "") + (int) m;
            canvas.drawText(txt, lx - UiStyle.dpf(getContext(), 5f), y + UiStyle.dpf(getContext(), 3.2f), valPaint);
        }
        valPaint.setTextAlign(Paint.Align.CENTER);

        if (curveMode) {
            drawCurve(canvas, t, b, col);
        } else {
            drawSliders(canvas, t, b, col);
        }

        // Frequency labels along bottom
        String[] labels = activeLabels();
        for (int i = 0; i < bandCount; i++) {
            float x = bandX(i);
            labelPaint.setColor(i == draggingBand ? col : UiStyle.TEXT_SECONDARY);
            canvas.drawText(labels[i], x, getHeight() - UiStyle.dpf(getContext(), 8f), labelPaint);
        }

        // Floating Active Band HUD pill while dragging so thumb never hides exact dB value
        if (draggingBand >= 0 && draggingBand < bandCount) {
            String fLabel = labels[draggingBand];
            if (!fLabel.endsWith("k")) fLabel = fLabel + " Hz";
            else fLabel = fLabel + "Hz";
            String hudTxt = String.format(Locale.US, "%s  •  %+.1f dB", fLabel, gains[draggingBand]);
            float tw = hudTextPaint.measureText(hudTxt) + UiStyle.dpf(getContext(), 22f);
            float th = UiStyle.dpf(getContext(), 22f);
            float cx = Math.max(lx + tw * 0.5f, Math.min(rx - tw * 0.5f, bandX(draggingBand)));
            float top = UiStyle.dpf(getContext(), 2f);
            rectF.set(cx - tw * 0.5f, top, cx + tw * 0.5f, top + th);
            hudBgPaint.setColor(0xEE0B0E16);
            canvas.drawRoundRect(rectF, th * 0.5f, th * 0.5f, hudBgPaint);
            thumbBorderPaint.setColor(col);
            canvas.drawRoundRect(rectF, th * 0.5f, th * 0.5f, thumbBorderPaint);
            hudTextPaint.setColor(col);
            canvas.drawText(hudTxt, cx, top + th * 0.70f, hudTextPaint);
        }
    }

    private void drawSliders(Canvas canvas, float t, float b, int col) {
        float zeroY = dbToY(0f);
        float trackW = UiStyle.dpf(getContext(), bandCount == 10 ? 5.5f : 8f);
        float thumbW = UiStyle.dpf(getContext(), bandCount == 10 ? 18f : 28f);
        float thumbH = UiStyle.dpf(getContext(), 12f);
        float r = trackW * 0.5f;

        for (int i = 0; i < bandCount; i++) {
            float x = bandX(i);
            float y = dbToY(gains[i]);
            boolean active = (i == draggingBand);

            rectF.set(x - trackW * 0.5f, t, x + trackW * 0.5f, b);
            canvas.drawRoundRect(rectF, r, r, trackPaint);

            float fy1 = Math.min(zeroY, y);
            float fy2 = Math.max(zeroY, y);
            if (fy2 - fy1 > 1f) {
                glowPaint.setColor(EqStore.withAlpha(col, active ? 75 : 42));
                rectF.set(x - trackW, fy1, x + trackW, fy2);
                canvas.drawRoundRect(rectF, trackW, trackW, glowPaint);

                fillPaint.setColor(col);
                rectF.set(x - trackW * 0.5f, fy1, x + trackW * 0.5f, fy2);
                canvas.drawRoundRect(rectF, r, r, fillPaint);
            }

            thumbPaint.setColor(active ? 0xFF283144 : 0xFF1B2230);
            thumbBorderPaint.setColor(active ? 0xFFFFFFFF : EqStore.withAlpha(col, 220));
            rectF.set(x - thumbW * 0.5f, y - thumbH * 0.5f, x + thumbW * 0.5f, y + thumbH * 0.5f);
            canvas.drawRoundRect(rectF, UiStyle.dpf(getContext(), 4f), UiStyle.dpf(getContext(), 4f), thumbPaint);
            canvas.drawRoundRect(rectF, UiStyle.dpf(getContext(), 4f), UiStyle.dpf(getContext(), 4f), thumbBorderPaint);

            canvas.drawLine(x - thumbW * 0.25f, y, x + thumbW * 0.25f, y, thumbBorderPaint);

            if (draggingBand < 0) {
                valPaint.setColor(Math.abs(gains[i]) > 0.1f ? UiStyle.TEXT_PRIMARY : UiStyle.TEXT_MUTED);
                String dbTxt = gains[i] == 0f ? "0" : String.format(Locale.US, "%+.0f", gains[i]);
                canvas.drawText(dbTxt, x, UiStyle.dpf(getContext(), 17f), valPaint);
            }
        }
    }

    private void drawCurve(Canvas canvas, float t, float b, int col) {
        float zeroY = dbToY(0f);
        float[] xs = new float[bandCount];
        float[] ys = new float[bandCount];
        for (int i = 0; i < bandCount; i++) {
            xs[i] = bandX(i);
            ys[i] = dbToY(gains[i]);
        }

        for (int i = 0; i < bandCount; i++) {
            canvas.drawLine(xs[i], t, xs[i], b, gridPaint);
        }

        curvePath.reset();
        curvePath.moveTo(xs[0], ys[0]);
        for (int i = 0; i < bandCount - 1; i++) {
            float x0 = xs[Math.max(0, i - 1)];
            float y0 = ys[Math.max(0, i - 1)];
            float x1 = xs[i];
            float y1 = ys[i];
            float x2 = xs[i + 1];
            float y2 = ys[i + 1];
            float x3 = xs[Math.min(bandCount - 1, i + 2)];
            float y3 = ys[Math.min(bandCount - 1, i + 2)];

            float cp1x = x1 + (x2 - x0) / 6f;
            float cp1y = EqStore.clamp(y1 + (y2 - y0) / 6f, t, b);
            float cp2x = x2 - (x3 - x1) / 6f;
            float cp2y = EqStore.clamp(y2 - (y3 - y1) / 6f, t, b);
            curvePath.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2);
        }

        fillPath.set(curvePath);
        fillPath.lineTo(xs[bandCount - 1], zeroY);
        fillPath.lineTo(xs[0], zeroY);
        fillPath.close();
        if (cachedCurveH != getHeight() || cachedCurveCol != col) {
            cachedCurveH = getHeight();
            cachedCurveCol = col;
            curveFillPaint.setShader(new LinearGradient(
                    0, t, 0, b,
                    EqStore.withAlpha(col, 90),
                    EqStore.withAlpha(col, 18),
                    Shader.TileMode.CLAMP));
        }
        canvas.drawPath(fillPath, curveFillPaint);

        curveGlowPaint.setColor(EqStore.withAlpha(col, 72));
        canvas.drawPath(curvePath, curveGlowPaint);
        curveLinePaint.setColor(col);
        canvas.drawPath(curvePath, curveLinePaint);

        float nodeR = UiStyle.dpf(getContext(), bandCount == 10 ? 6f : 8f);
        for (int i = 0; i < bandCount; i++) {
            boolean active = (i == draggingBand);
            glowPaint.setColor(EqStore.withAlpha(col, active ? 130 : 60));
            canvas.drawCircle(xs[i], ys[i], nodeR * (active ? 2.1f : 1.55f), glowPaint);

            thumbPaint.setColor(active ? 0xFFFFFFFF : 0xFF0A0D14);
            canvas.drawCircle(xs[i], ys[i], nodeR, thumbPaint);
            thumbBorderPaint.setColor(col);
            canvas.drawCircle(xs[i], ys[i], nodeR, thumbBorderPaint);

            if (draggingBand < 0) {
                valPaint.setColor(Math.abs(gains[i]) > 0.1f ? UiStyle.TEXT_PRIMARY : UiStyle.TEXT_MUTED);
                String dbTxt = gains[i] == 0f ? "0" : String.format(Locale.US, "%+.0f", gains[i]);
                canvas.drawText(dbTxt, xs[i], UiStyle.dpf(getContext(), 17f), valPaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (morphAnim != null) { morphAnim.cancel(); morphAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) {
            return false; // Let parent ScrollView scroll smoothly with zero lock-toast spam
        }
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (morphAnim != null) { morphAnim.cancel(); morphAnim = null; }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            int idx = xToBand(e.getX());
            long now = System.currentTimeMillis();
            if (idx == lastTappedBand && (now - lastTapMs) < 320) {
                gains[idx] = 0f;
                lastTappedBand = -1;
                draggingBand = -1;
                UiStyle.hapticTick(this, getContext());
                if (listener != null) {
                    listener.onBandChanging(idx, 0f);
                    listener.onBandChangeFinished();
                }
                invalidate();
                return true;
            }
            lastTappedBand = idx;
            lastTapMs = now;
            draggingBand = idx;
            lastBrushBand = idx;
            lastBrushDb = yToDb(e.getY());
            lastHapticStep = Math.round(gains[idx]);
            applyDrag(idx, lastBrushDb);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (locked) return true;
            float newDb = yToDb(e.getY());
            if (brushMode) {
                int idx = xToBand(e.getX());
                if (lastBrushBand >= 0 && Math.abs(idx - lastBrushBand) > 1) {
                    // Smoothly interpolate across skipped bands during fast brush swipes
                    int step = idx > lastBrushBand ? 1 : -1;
                    int span = Math.abs(idx - lastBrushBand);
                    for (int k = 1; k <= span; k++) {
                        int bIdx = lastBrushBand + k * step;
                        float frac = (float) k / span;
                        float interp = lastBrushDb + (newDb - lastBrushDb) * frac;
                        interp = Math.round(interp * 2f) / 2f;
                        applyDrag(bIdx, interp);
                    }
                } else {
                    applyDrag(idx, newDb);
                }
                draggingBand = idx;
                lastBrushBand = idx;
                lastBrushDb = newDb;
            } else if (draggingBand >= 0 && draggingBand < bandCount) {
                applyDrag(draggingBand, newDb);
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (!locked && draggingBand >= 0 && listener != null) {
                listener.onBandChangeFinished();
            }
            draggingBand = -1;
            lastBrushBand = -1;
            invalidate();
            return true;
        }
        return super.onTouchEvent(e);
    }

    private void applyDrag(int idx, float newDb) {
        if (idx < 0 || idx >= bandCount) return;
        if (Math.abs(gains[idx] - newDb) < 0.01f) {
            invalidate();
            return;
        }
        boolean crossedZero = (gains[idx] != 0f && newDb == 0f);
        gains[idx] = newDb;
        int step = Math.round(newDb / 3f);
        if (crossedZero || step != lastHapticStep) {
            lastHapticStep = step;
            UiStyle.hapticTick(this, getContext());
        }
        if (listener != null) listener.onBandChanging(idx, newDb);
        invalidate();
    }
}
