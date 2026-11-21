package com.buddyapps.kokerqualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class RotaryKnobView extends View {
    public interface Listener {
        void onValueChanging(float value);
        void onValueFinished(float value);
        void onLockedTouch();
    }

    public interface Formatter {
        String format(float value);
    }

    private String title = "KNOB";
    private float minVal = 0f;
    private float maxVal = 100f;
    private float defaultVal = 0f;
    private float value = 0f;
    private boolean bipolar = false; // true for Preamp / L-R Balance
    private boolean locked = false;
    private boolean enabledVisual = true;
    private int accentColor = 0xFF39FF14;
    private Formatter formatter;
    private Listener listener;

    private float downY;
    private float startVal;
    private long lastTapMs = 0;
    private int lastTickBucket = Integer.MIN_VALUE;
    private ValueAnimator knobAnim;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private int cachedDialW = -1;
    private int cachedDialH = -1;

    public RotaryKnobView(Context context) {
        super(context);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0xFF1F2738);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowArcPaint.setStyle(Paint.Style.STROKE);
        glowArcPaint.setStrokeCap(Paint.Cap.ROUND);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(UiStyle.dpf(context, 1.5f));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(UiStyle.dpf(context, 1.2f));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeWidth(UiStyle.dpf(context, 3f));
        pointerPaint.setStrokeCap(Paint.Cap.ROUND);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setColor(UiStyle.TEXT_SECONDARY);
        titlePaint.setTextSize(UiStyle.dpf(context, 11.5f));
        titlePaint.setFakeBoldText(true);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(UiStyle.TEXT_PRIMARY);
        valuePaint.setTextSize(UiStyle.dpf(context, 12.5f));
        valuePaint.setFakeBoldText(true);
    }

    public void configure(String title, float min, float max, float defVal,
                          boolean bipolar, Formatter fmt, Listener listener) {
        this.title = title;
        this.minVal = min;
        this.maxVal = max;
        this.defaultVal = defVal;
        this.bipolar = bipolar;
        this.formatter = fmt;
        this.listener = listener;
        invalidate();
    }

    public void setValue(float v) {
        setValue(v, false);
    }

    public void setValue(float v, boolean animate) {
        float target = EqStore.clamp(v, minVal, maxVal);
        if (knobAnim != null) {
            knobAnim.cancel();
            knobAnim = null;
        }
        if (animate && Math.abs(target - value) > 0.25f) {
            float from = value;
            knobAnim = ValueAnimator.ofFloat(from, target);
            knobAnim.setDuration(220);
            knobAnim.setInterpolator(new DecelerateInterpolator(2.0f));
            knobAnim.addUpdateListener(a -> {
                value = (float) a.getAnimatedValue();
                invalidate();
            });
            knobAnim.start();
        } else {
            this.value = target;
            invalidate();
        }
    }

    public void setStyle(int accentColor, boolean enabledVisual, boolean locked) {
        this.accentColor = accentColor;
        this.enabledVisual = enabledVisual;
        this.locked = locked;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        int col = enabledVisual ? accentColor : 0xFF475569;
        float topLabelH = UiStyle.dpf(getContext(), 16f);
        float botLabelH = UiStyle.dpf(getContext(), 18f);
        float dialAreaH = h - topLabelH - botLabelH;
        float r = Math.min(w * 0.36f, dialAreaH * 0.42f);
        float cx = w * 0.5f;
        float cy = topLabelH + dialAreaH * 0.52f;

        // Title on top
        canvas.drawText(title, cx, UiStyle.dpf(getContext(), 13f), titlePaint);

        // Outer tick ring (21 retro hi-fi detents across 270 deg)
        float tickInner = r + UiStyle.dpf(getContext(), 8f);
        float tickOuter = r + UiStyle.dpf(getContext(), 12f);
        for (int i = 0; i <= 20; i++) {
            double deg = 135.0 + (270.0 * i / 20.0);
            double rad = Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            boolean major = (i % 5 == 0);
            tickPaint.setColor(major ? 0x55FFFFFF : 0x26FFFFFF);
            canvas.drawLine(
                    cx + cos * tickInner, cy + sin * tickInner,
                    cx + cos * (major ? tickOuter + 2f : tickOuter), cy + sin * (major ? tickOuter + 2f : tickOuter),
                    tickPaint);
        }

        // Neon progress arc around the brushed dial
        float arcR = r + UiStyle.dpf(getContext(), 4f);
        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
        float strokeW = UiStyle.dpf(getContext(), 3.8f);
        trackPaint.setStrokeWidth(strokeW);
        arcPaint.setStrokeWidth(strokeW);
        glowArcPaint.setStrokeWidth(strokeW * 2.4f);

        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint);

        float norm = (value - minVal) / Math.max(0.0001f, maxVal - minVal);
        float startAngle = bipolar ? 270f : 135f;
        float sweepAngle = bipolar ? ((norm - 0.5f) * 270f) : (norm * 270f);
        if (Math.abs(sweepAngle) > 0.5f) {
            glowArcPaint.setColor(EqStore.withAlpha(col, 62));
            canvas.drawArc(arcRect, startAngle, sweepAngle, false, glowArcPaint);
            arcPaint.setColor(col);
            canvas.drawArc(arcRect, startAngle, sweepAngle, false, arcPaint);
        }

        // Brushed-metal retro knob body (cached RadialGradient -> zero allocations in onDraw!)
        if (cachedDialW != getWidth() || cachedDialH != getHeight()) {
            cachedDialW = getWidth();
            cachedDialH = getHeight();
            dialPaint.setShader(new RadialGradient(
                    cx - r * 0.28f, cy - r * 0.28f, r * 1.45f,
                    new int[]{0xFF343E52, 0xFF191F2C, 0xFF0C0F16},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(cx, cy, r, dialPaint);

        rimPaint.setColor(EqStore.withAlpha(col, 110));
        canvas.drawCircle(cx, cy, r, rimPaint);

        // Pointer notch
        double pRad = Math.toRadians(135.0 + norm * 270.0);
        float px1 = cx + (float) Math.cos(pRad) * (r * 0.36f);
        float py1 = cy + (float) Math.sin(pRad) * (r * 0.36f);
        float px2 = cx + (float) Math.cos(pRad) * (r * 0.80f);
        float py2 = cy + (float) Math.sin(pRad) * (r * 0.80f);
        pointerPaint.setColor(col);
        canvas.drawLine(px1, py1, px2, py2, pointerPaint);

        // Bottom numeric readout
        String txt = formatter != null ? formatter.format(value) : String.valueOf(Math.round(value));
        valuePaint.setColor(enabledVisual ? UiStyle.TEXT_PRIMARY : UiStyle.TEXT_MUTED);
        canvas.drawText(txt, cx, h - UiStyle.dpf(getContext(), 4f), valuePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (knobAnim != null) { knobAnim.cancel(); knobAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) {
            return false; // Let parent ScrollView scroll smoothly with zero lock-toast spam
        }
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            if (knobAnim != null) { knobAnim.cancel(); knobAnim = null; }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            long now = System.currentTimeMillis();
            if (now - lastTapMs < 320) {
                value = defaultVal;
                lastTapMs = 0;
                UiStyle.hapticTick(this, getContext());
                if (listener != null) {
                    listener.onValueChanging(value);
                    listener.onValueFinished(value);
                }
                invalidate();
                return true;
            }
            lastTapMs = now;
            downY = e.getY();
            startVal = value;
            lastTickBucket = Math.round(((value - minVal) / (maxVal - minVal)) * 18f);
            return true;
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (locked) return true;
            float dy = downY - e.getY();
            float range = maxVal - minVal;
            float sensitivity = UiStyle.dpf(getContext(), 150f);
            float next = EqStore.clamp(startVal + (dy / sensitivity) * range, minVal, maxVal);
            if (bipolar && Math.abs(next - defaultVal) < range * 0.025f) {
                next = defaultVal;
            }
            if (Math.abs(next - value) > 0.01f) {
                value = next;
                int bucket = Math.round(((value - minVal) / range) * 18f);
                if (bucket != lastTickBucket) {
                    lastTickBucket = bucket;
                    UiStyle.hapticTick(this, getContext());
                }
                if (listener != null) listener.onValueChanging(value);
                invalidate();
            }
            return true;
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (!locked && listener != null) {
                listener.onValueFinished(value);
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
