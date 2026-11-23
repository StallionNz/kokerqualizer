package com.buddyapps.kokerqualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.PowerManager;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 3D Depth-of-Field Crystal Glass Orb with pulsating floating stardust particles
 * and customizable glowing monogram.
 */
public class BubbleOrbView extends View {
    private static class DustMote {
        float kx, ky;             // Target normalized position along the 'K' (-1..1)
        float scatterX, scatterY; // Outward dissolve drift vector
        float depthZ;             // -1.0 (deep bokeh) .. +1.0 (crisp foreground)
        float phase;              // Individual twinkle & drift phase
        float sizeMul;            // Relative mote radius
        boolean isAmbient;        // True for orbiting outer stardust, false for 'K' dust
    }

    private int accent = 0xFF39FF14;
    private boolean eqOn = true;
    private boolean open = false;
    private boolean beatReactive = true;
    private String monogram = "K";
    private boolean animating = false;
    private final long startTimeMs = System.currentTimeMillis();

    private final DustMote[] motes;
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bouncePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint customGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moteGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moteCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path kPath = new Path();
    private final RectF specRect = new RectF();
    private final RectF bounceRect = new RectF();

    private int cachedW = -1;
    private int cachedAccent = 0;
    private boolean cachedEqOn = false;

    private final PowerManager powerMgr;

    private final Runnable frameTick = new Runnable() {
        @Override
        public void run() {
            if (!animating) return;
            boolean interactive = (powerMgr == null || powerMgr.isInteractive()) && getWindowVisibility() == VISIBLE;
            if (interactive) {
                invalidate();
                postDelayed(this, 33); // 30fps while screen is awake
            } else {
                postDelayed(this, 1000); // Sleep quietly while screen is off (saves battery)
            }
        }
    };

    public BubbleOrbView(Context context) {
        super(context);
        powerMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        shadowPaint.setStyle(Paint.Style.FILL);
        auraPaint.setStyle(Paint.Style.FILL);
        spherePaint.setStyle(Paint.Style.FILL);
        bouncePaint.setStyle(Paint.Style.STROKE);
        bouncePaint.setStrokeCap(Paint.Cap.ROUND);
        bouncePaint.setStrokeWidth(UiStyle.dpf(context, 2.8f));

        rimGlowPaint.setStyle(Paint.Style.STROKE);
        rimGlowPaint.setStrokeWidth(UiStyle.dpf(context, 3.6f));

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(UiStyle.dpf(context, 1.3f));

        kGlowPaint.setStyle(Paint.Style.STROKE);
        kGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        kGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        kGlowPaint.setStrokeWidth(UiStyle.dpf(context, 4.5f));

        customGlyphPaint.setTextAlign(Paint.Align.CENTER);
        customGlyphPaint.setFakeBoldText(true);

        moteGlowPaint.setStyle(Paint.Style.FILL);
        moteCorePaint.setStyle(Paint.Style.FILL);
        specPaint.setStyle(Paint.Style.FILL);

        motes = createMotes();
    }

    private DustMote[] createMotes() {
        // 30 dust motes forming a crisp glowing 'K' + 14 ambient orbiting stardust motes
        List<DustMote> list = new ArrayList<>();
        Random rnd = new Random(4242L);

        // Stroke 1: Vertical spine of 'K' (x = -0.34, y from -0.58 to +0.58)
        for (int i = 0; i < 12; i++) {
            float t = i / 11f;
            list.add(makeKMote(-0.34f, -0.58f + t * 1.16f, rnd));
        }
        // Stroke 2: Upper diagonal arm of 'K' (from (-0.28, 0.02) to (0.42, -0.56))
        for (int i = 0; i < 9; i++) {
            float t = i / 8f;
            list.add(makeKMote(-0.28f + t * 0.70f, 0.02f - t * 0.58f, rnd));
        }
        // Stroke 3: Lower diagonal leg of 'K' (from (-0.20, -0.02) to (0.44, 0.58))
        for (int i = 0; i < 9; i++) {
            float t = i / 8f;
            list.add(makeKMote(-0.20f + t * 0.64f, -0.02f + t * 0.60f, rnd));
        }
        // Ambient orbiting dust motes
        for (int i = 0; i < 14; i++) {
            DustMote m = new DustMote();
            m.isAmbient = true;
            double ang = (Math.PI * 2.0 * i) / 14.0 + rnd.nextFloat() * 0.4;
            float rad = 0.72f + rnd.nextFloat() * 0.56f;
            m.kx = (float) (Math.cos(ang) * rad);
            m.ky = (float) (Math.sin(ang) * rad);
            m.scatterX = (float) Math.cos(ang + 1.2) * 0.25f;
            m.scatterY = (float) Math.sin(ang + 1.2) * 0.25f;
            m.depthZ = -1.0f + rnd.nextFloat() * 2.0f;
            m.phase = rnd.nextFloat() * 6.283f;
            m.sizeMul = 0.6f + rnd.nextFloat() * 0.9f;
            list.add(m);
        }
        return list.toArray(new DustMote[0]);
    }

    private DustMote makeKMote(float x, float y, Random rnd) {
        DustMote m = new DustMote();
        m.isAmbient = false;
        m.kx = x + (rnd.nextFloat() - 0.5f) * 0.07f;
        m.ky = y + (rnd.nextFloat() - 0.5f) * 0.07f;
        double ang = rnd.nextDouble() * Math.PI * 2.0;
        float dist = 0.35f + rnd.nextFloat() * 0.55f;
        m.scatterX = (float) (Math.cos(ang) * dist);
        m.scatterY = (float) (Math.sin(ang) * dist);
        m.depthZ = -0.85f + rnd.nextFloat() * 1.85f;
        m.phase = rnd.nextFloat() * 6.283f;
        m.sizeMul = 0.75f + rnd.nextFloat() * 0.75f;
        return m;
    }

    public void updateVisual(int accentColor, boolean eqActive, boolean isExpanded,
                             String customLetter, boolean beatReact) {
        this.accent = accentColor;
        this.eqOn = eqActive;
        this.open = isExpanded;
        this.beatReactive = beatReact;
        this.monogram = EqStore.firstCodePoint(customLetter, "K");
        cachedW = -1; // refresh gradients
        postInvalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animating = true;
        removeCallbacks(frameTick);
        post(frameTick);
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(frameTick);
        super.onDetachedFromWindow();
    }

    private void ensureShaders(float cx, float cy, float r, int col) {
        if (cachedW == getWidth() && cachedAccent == col && cachedEqOn == eqOn) return;
        cachedW = getWidth();
        cachedAccent = col;
        cachedEqOn = eqOn;

        // Grounded 3D drop shadow
        shadowPaint.setShader(new RadialGradient(
                cx + r * 0.10f, cy + r * 0.16f, r * 1.24f,
                new int[]{0xBB000000, 0x55000000, 0x00000000},
                new float[]{0.45f, 0.78f, 1.0f},
                Shader.TileMode.CLAMP));

        // Deep 3D emerald-obsidian crystal volumetric sphere shading
        int hiCore = eqOn ? 0xFF226119 : 0xFF143810;
        int midBody = eqOn ? 0xFF0A2807 : 0xFF071A06;
        int deepCore = 0xFF030D03;
        int midTint = eqOn ? 0xF0115E09 : 0xE60B3606;
        int rimTint = eqOn ? 0xFF2EE012 : 0xFF196E0B;
        spherePaint.setShader(new RadialGradient(
                cx - r * 0.30f, cy - r * 0.34f, r * 1.32f,
                new int[]{hiCore, midBody, deepCore, midTint, rimTint},
                new float[]{0.0f, 0.28f, 0.62f, 0.84f, 1.0f},
                Shader.TileMode.CLAMP));

        // Cached outer volumetric neon-green aura shader (modulate alpha per frame -> zero allocations in onDraw)
        auraPaint.setShader(new RadialGradient(
                cx, cy, r * 1.38f,
                new int[]{EqStore.withAlpha(col, 255), EqStore.withAlpha(col, 130), 0x00000000},
                new float[]{0.54f, 0.80f, 1.0f},
                Shader.TileMode.CLAMP));

        // Glossy curved upper-left crystal glass dome highlight
        specPaint.setShader(new RadialGradient(
                cx - r * 0.34f, cy - r * 0.42f, r * 0.58f,
                new int[]{0xAAEEFFEA, 0x4839FF14, 0x00FFFFFF},
                new float[]{0.0f, 0.50f, 1.0f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = Math.min(w, h) * 0.36f;
        int col = eqOn ? accent : 0xFF1F8F0C;
        ensureShaders(cx, cy, r, col);

        float tSec = (System.currentTimeMillis() - startTimeMs) / 1000f;
        float beatBoost = (eqOn && beatReactive) ? EqEngine.get(getContext()).getLiveBassEnergy() : 0f;

        // Hypnotic 4.2-second materialize -> glow pulse -> dissolve & vanish cycle for the monogram
        double cycle = (tSec * (Math.PI * 2.0 / 4.2));
        float rawWave = (float) (0.5 - 0.5 * Math.cos(cycle));
        float kPresence = (float) Math.pow(rawWave, 1.45);
        float fastPulse = 0.82f + 0.18f * (float) Math.sin(tSec * 6.5);
        float kAlpha = Math.min(1f, ((eqOn ? kPresence : kPresence * 0.55f) * fastPulse) + beatBoost * 0.25f);
        float scatterAmt = 1.0f - kPresence;

        // 1. Soft 3D ambient drop shadow
        canvas.drawCircle(cx + r * 0.08f, cy + r * 0.12f, r * 1.24f, shadowPaint);

        // 2. Pulsating volumetric neon aura behind the orb
        int auraA = Math.min(255, Math.round((eqOn ? (open ? 175 : 118) : 52) + kAlpha * 68 + beatBoost * 85));
        auraPaint.setAlpha(auraA);
        canvas.drawCircle(cx, cy, r * (1.38f + beatBoost * 0.08f), auraPaint);

        // 3. Back-layer deep bokeh dust behind the crystal sphere
        drawMotesLayer(canvas, cx, cy, r, col, tSec, kAlpha, scatterAmt, -2f, -0.15f);

        // 4. 3D Volumetric Crystal Glass Sphere Body
        canvas.drawCircle(cx, cy, r, spherePaint);

        // Subsurface caustic bounce light
        bounceRect.set(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f);
        bouncePaint.setColor(EqStore.withAlpha(col, Math.min(255, Math.round((eqOn ? 95 : 48) + kAlpha * 105 + beatBoost * 65))));
        canvas.drawArc(bounceRect, 22f, 76f, false, bouncePaint);

        // 5. Ghostly soft neon monogram core
        boolean isDefaultK = "K".equalsIgnoreCase(monogram);
        if (kAlpha > 0.06f) {
            if (isDefaultK) {
                float ks = r * 0.82f;
                kPath.reset();
                kPath.moveTo(cx - ks * 0.34f, cy - ks * 0.56f);
                kPath.lineTo(cx - ks * 0.34f, cy + ks * 0.56f);
                kPath.moveTo(cx - ks * 0.28f, cy + ks * 0.02f);
                kPath.lineTo(cx + ks * 0.42f, cy - ks * 0.54f);
                kPath.moveTo(cx - ks * 0.20f, cy - ks * 0.02f);
                kPath.lineTo(cx + ks * 0.44f, cy + ks * 0.56f);

                kGlowPaint.setColor(EqStore.withAlpha(col, Math.round(kAlpha * 125)));
                canvas.drawPath(kPath, kGlowPaint);
            } else {
                customGlyphPaint.setTextSize(r * 1.18f);
                Paint.FontMetrics fm = customGlyphPaint.getFontMetrics();
                float textY = cy - (fm.ascent + fm.descent) * 0.5f;
                customGlyphPaint.setColor(EqStore.withAlpha(col, Math.round(kAlpha * 185)));
                canvas.drawText(monogram, cx, textY, customGlyphPaint);
                customGlyphPaint.setTextSize(r * 1.06f);
                customGlyphPaint.setColor(EqStore.withAlpha(0xFFFFFFFF, Math.round(kAlpha * 215)));
                canvas.drawText(monogram, cx, textY, customGlyphPaint);
            }
        }

        // 6. Mid-depth & Foreground 3D Stardust Particles
        drawMotesLayer(canvas, cx, cy, r, col, tSec, kAlpha, isDefaultK ? scatterAmt : 1.0f, -0.15f, 2f);

        // 7. Outer Fresnel glass rim + crisp neon specular edge
        rimGlowPaint.setColor(EqStore.withAlpha(col, Math.min(255, Math.round((open ? 175 : (eqOn ? 115 : 58)) + kAlpha * 65 + beatBoost * 55))));
        canvas.drawCircle(cx, cy, r, rimGlowPaint);
        rimPaint.setColor(open ? 0xEECCFFBF : EqStore.withAlpha(col, eqOn ? 245 : 175));
        canvas.drawCircle(cx, cy, r, rimPaint);

        // 8. Curved glossy 3D upper-left glass dome reflection
        canvas.save();
        canvas.rotate(-32f, cx, cy);
        specRect.set(cx - r * 0.58f, cy - r * 0.86f, cx + r * 0.42f, cy - r * 0.22f);
        canvas.drawOval(specRect, specPaint);
        canvas.restore();
    }

    private void drawMotesLayer(Canvas canvas, float cx, float cy, float r, int col,
                                float tSec, float kAlpha, float scatterAmt, float zMin, float zMax) {
        float baseDp = UiStyle.dpf(getContext(), 1.0f);
        for (DustMote m : motes) {
            if (m.depthZ < zMin || m.depthZ >= zMax) continue;

            float dofBlur = Math.abs(m.depthZ - 0.35f);
            float scale = 0.85f + (m.depthZ + 1f) * 0.22f;

            float wobbleX = (float) Math.sin(tSec * 1.8 + m.phase) * 0.08f;
            float wobbleY = (float) Math.cos(tSec * 1.5 + m.phase * 1.3) * 0.08f;

            float px, py, alpha;
            if (m.isAmbient) {
                double orbitAng = tSec * (0.35 + m.depthZ * 0.12) + m.phase;
                float ox = m.kx * (float) Math.cos(orbitAng * 0.35) - m.ky * (float) Math.sin(orbitAng * 0.35);
                float oy = m.kx * (float) Math.sin(orbitAng * 0.35) + m.ky * (float) Math.cos(orbitAng * 0.35);
                px = cx + (ox + wobbleX) * r;
                py = cy + (oy + wobbleY) * r;
                float twinkle = 0.30f + 0.70f * (0.5f + 0.5f * (float) Math.sin(tSec * 3.1 + m.phase * 2f));
                alpha = (eqOn ? 0.55f : 0.22f) * twinkle;
            } else {
                float localWave = (float) Math.sin(tSec * 3.4 + m.phase);
                float nx = m.kx + m.scatterX * scatterAmt + wobbleX * (0.4f + scatterAmt);
                float ny = m.ky + m.scatterY * scatterAmt + wobbleY * (0.4f + scatterAmt);
                px = cx + nx * r * 0.82f;
                py = cy + ny * r * 0.82f;
                float sparkle = 0.72f + 0.28f * localWave;
                alpha = Math.min(1f, (0.04f + kAlpha * 0.96f) * sparkle);
            }

            if (alpha < 0.04f) continue;

            float coreR = baseDp * (0.95f + m.sizeMul * 0.85f) * scale;
            float haloR = coreR * (2.1f + dofBlur * 1.6f);

            int haloA = Math.round(alpha * (115f / (1f + dofBlur * 0.5f)));
            moteGlowPaint.setColor(EqStore.withAlpha(col, haloA));
            canvas.drawCircle(px, py, haloR, moteGlowPaint);

            int coreA = Math.round(alpha * (245f - dofBlur * 95f));
            moteCorePaint.setColor(m.depthZ > 0.15f
                    ? EqStore.withAlpha(0xFFFFFFFF, coreA)
                    : EqStore.withAlpha(col, coreA));
            canvas.drawCircle(px, py, coreR, moteCorePaint);
        }
    }
}
