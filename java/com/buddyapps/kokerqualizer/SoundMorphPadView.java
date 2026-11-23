package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class SoundMorphPadView extends View {
    public interface Listener {
        void onMorphChanging(float xNorm, float yNorm); // x: -1(Warm)..+1(Punchy), y: -1(Sub)..+1(Vocal)
        void onMorphFinished(float xNorm, float yNorm);
        void onLockedTouch();
    }

    private float posX = 0f; // -1..+1
    private float posY = 0f; // -1..+1
    private int accentColor = EqStore.DEFAULT_ACCENT;
    private boolean eqActive = true;
    private boolean locked = false;
    private Listener listener;
    private long lastTapMs = 0;
    private boolean doubleTapReset = false;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint puckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint puckRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF padRect = new RectF();

    private int cachedCol = 0;
    private float cachedR = -1f;

    public SoundMorphPadView(Context context) {
        super(context);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF090C12);

        gridPaint.setColor(0x18FFFFFF);
        gridPaint.setStrokeWidth(UiStyle.dpf(context, 1f));

        axisPaint.setStrokeWidth(UiStyle.dpf(context, 1.3f));

        glowPaint.setStyle(Paint.Style.FILL);
        puckPaint.setStyle(Paint.Style.FILL);
        puckPaint.setColor(0xFFFFFFFF);
        puckRimPaint.setStyle(Paint.Style.STROKE);
        puckRimPaint.setStrokeWidth(UiStyle.dpf(context, 2.2f));

        labelPaint.setTextSize(UiStyle.dpf(context, 10f));
        labelPaint.setFakeBoldText(true);
        labelPaint.setColor(UiStyle.TEXT_MUTED);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setStyle(int accent, boolean active, boolean locked) {
        this.accentColor = accent;
        this.eqActive = active;
        this.locked = locked;
        invalidate();
    }

    public void setCoords(float x, float y) {
        this.posX = EqStore.clamp(x, -1f, 1f);
        this.posY = EqStore.clamp(y, -1f, 1f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = UiStyle.dpf(getContext(), 6f);
        float rad = UiStyle.dpf(getContext(), 16f);
        int col = eqActive ? accentColor : 0xFF64748B;

        padRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(padRect, rad, rad, bgPaint);

        // 4x4 studio vector grid
        float innerW = padRect.width();
        float innerH = padRect.height();
        for (int i = 1; i < 4; i++) {
            float gx = padRect.left + innerW * (i / 4f);
            float gy = padRect.top + innerH * (i / 4f);
            Paint p = (i == 2) ? axisPaint : gridPaint;
            if (i == 2) axisPaint.setColor(EqStore.withAlpha(col, 65));
            canvas.drawLine(gx, padRect.top, gx, padRect.bottom, p);
            canvas.drawLine(padRect.left, gy, padRect.right, gy, p);
        }

        // Edge labels
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(posY > 0.25f ? col : UiStyle.TEXT_MUTED);
        canvas.drawText("CRYSTAL VOCAL", w * 0.5f, padRect.top + UiStyle.dpf(getContext(), 14f), labelPaint);
        labelPaint.setColor(posY < -0.25f ? col : UiStyle.TEXT_MUTED);
        canvas.drawText("DEEP SUB", w * 0.5f, padRect.bottom - UiStyle.dpf(getContext(), 6f), labelPaint);

        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(posX < -0.25f ? col : UiStyle.TEXT_MUTED);
        canvas.drawText("WARM", padRect.left + UiStyle.dpf(getContext(), 8f), h * 0.5f - UiStyle.dpf(getContext(), 5f), labelPaint);

        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(posX > 0.25f ? col : UiStyle.TEXT_MUTED);
        canvas.drawText("PUNCHY", padRect.right - UiStyle.dpf(getContext(), 8f), h * 0.5f - UiStyle.dpf(getContext(), 5f), labelPaint);

        // Glowing morph puck position
        float px = padRect.left + ((posX + 1f) * 0.5f) * innerW;
        float py = padRect.top + ((1f - posY) * 0.5f) * innerH;

        // Crosshair laser lines to puck
        axisPaint.setColor(EqStore.withAlpha(col, 90));
        canvas.drawLine(px, padRect.top, px, padRect.bottom, axisPaint);
        canvas.drawLine(padRect.left, py, padRect.right, py, axisPaint);

        float glowR = UiStyle.dpf(getContext(), 34f);
        canvas.save();
        canvas.translate(px, py);
        if (cachedCol != col || cachedR != glowR) {
            cachedCol = col;
            cachedR = glowR;
            glowPaint.setShader(new RadialGradient(
                    0f, 0f, glowR,
                    new int[]{EqStore.withAlpha(col, 150), EqStore.withAlpha(col, 35), 0x00000000},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(0f, 0f, glowR, glowPaint);
        canvas.restore();

        float puckR = UiStyle.dpf(getContext(), 9f);
        puckRimPaint.setColor(col);
        canvas.drawCircle(px, py, puckR, puckPaint);
        canvas.drawCircle(px, py, puckR + UiStyle.dpf(getContext(), 1.5f), puckRimPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) return false;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            doubleTapReset = false;
            long now = System.currentTimeMillis();
            if (now - lastTapMs < 320) {
                lastTapMs = 0;
                doubleTapReset = true;
                posX = 0f;
                posY = 0f;
                UiStyle.hapticTick(this, getContext());
                if (listener != null) {
                    listener.onMorphChanging(0f, 0f);
                    listener.onMorphFinished(0f, 0f);
                }
                invalidate();
                return true;
            }
            lastTapMs = now;
            updateFromTouch(e.getX(), e.getY());
            return true;
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (doubleTapReset) return true;
            updateFromTouch(e.getX(), e.getY());
            return true;
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (!doubleTapReset && listener != null) listener.onMorphFinished(posX, posY);
            doubleTapReset = false;
            return true;
        }
        return super.onTouchEvent(e);
    }

    private void updateFromTouch(float x, float y) {
        float w = Math.max(1f, padRect.width());
        float h = Math.max(1f, padRect.height());
        float nx = EqStore.clamp(((x - padRect.left) / w) * 2f - 1f, -1f, 1f);
        float ny = EqStore.clamp(1f - ((y - padRect.top) / h) * 2f, -1f, 1f);
        if (Math.abs(nx) < 0.06f) nx = 0f;
        if (Math.abs(ny) < 0.06f) ny = 0f;
        posX = nx;
        posY = ny;
        if (listener != null) listener.onMorphChanging(posX, posY);
        invalidate();
    }
}
