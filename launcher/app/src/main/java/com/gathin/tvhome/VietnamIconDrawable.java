package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Vietnam skin's pill icons, drawn from paths: the national flag for Inputs (red field, gold star, 3:2) that
 * ripples once when focused, or a gold star on a red disc for Settings that turns one point when focused. The
 * motion runs only during {@link #nudge()}; reduced motion leaves the icon still.
 */
public final class VietnamIconDrawable extends Drawable {

    static final int FLAG = 0, STAR_BADGE = 1;

    private static final float BOX_DP = 24f;
    private static final long FLAG_MS = 700, BADGE_MS = 650;
    private static final int SEGMENTS = 12;
    private static final int EDGE = 0xFF8E1410;

    private final int kind;
    private final float density;
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path field = new Path();
    private final Path star = new Path();
    /** Reused on every frame of the ripple: no allocation while drawing. */
    private final float[] points = new float[20], flagBox = new float[5];
    private final ValueAnimator animator;
    private float t = -1f;
    private boolean reduced;
    private int alpha = 255;

    public VietnamIconDrawable(float density, int kind) {
        this.kind = kind;
        this.density = density;
        red.setColor(Festival.FLAG_RED);
        gold.setColor(Festival.STAR_GOLD);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(EDGE);
        edge.setStrokeJoin(Paint.Join.ROUND);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(kind == FLAG ? FLAG_MS : BADGE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                t = -1f;
                invalidateSelf();
            }
        });
    }

    /** The focus motion: one ripple through the flag, or the badge's star turning by one point. */
    public void nudge() {
        if (reduced) return;
        animator.cancel();
        animator.start();
    }

    public void setReducedMotion(boolean reducedMotion) {
        reduced = reducedMotion;
        if (reduced && animator.isRunning()) animator.cancel();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int save = canvas.save();
        canvas.translate(b.left, b.top);
        red.setAlpha(alpha);
        gold.setAlpha(alpha);
        edge.setAlpha(alpha * 3 / 4);
        edge.setStrokeWidth(Math.max(1f, 0.6f * density));
        if (kind == FLAG) drawFlag(canvas, b.width(), b.height());
        else drawBadge(canvas, b.width(), b.height());
        canvas.restoreToCount(save);
    }

    /** The wave's vertical offset at x (0 at the hoist, growing towards the fly), or 0 at rest. */
    private float wave(float x, float fw, float fh) {
        if (t < 0f) return 0f;
        float env = (float) Math.sin(Math.PI * t);
        double phase = 2 * Math.PI * (x / fw * 1.1f - t * 2f);
        return fh * 0.09f * env * (x / fw) * (float) Math.sin(phase);
    }

    private void drawFlag(Canvas c, float w, float h) {
        float[] f = Star.flag(flagBox, w, h, 0.92f);
        float left = f[0], top = f[1], fw = f[2], fh = f[3];
        field.reset();
        field.moveTo(left, top);
        for (int i = 1; i <= SEGMENTS; i++) {
            float x = fw * i / SEGMENTS;
            field.lineTo(left + x, top + wave(x, fw, fh));
        }
        for (int i = SEGMENTS; i >= 0; i--) {
            float x = fw * i / SEGMENTS;
            field.lineTo(left + x, top + fh + wave(x, fw, fh));
        }
        field.close();
        c.drawPath(field, red);
        c.drawPath(field, edge);
        float cx = left + fw / 2f, cy = top + fh / 2f + wave(fw / 2f, fw, fh);
        polygon(star, Star.points(points, cx, cy, f[4], f[4] * Star.INNER_RATIO, 0f));
        c.drawPath(star, gold);
    }

    private void drawBadge(Canvas c, float w, float h) {
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.46f;
        c.drawCircle(cx, cy, r, red);
        c.drawCircle(cx, cy, r, edge);
        float turn = 0f;
        if (t >= 0f) {
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            turn = 72f * e;
        }
        float outer = r * 0.72f;
        polygon(star, Star.points(points, cx, cy + r * 0.04f, outer, outer * Star.INNER_RATIO, turn));
        c.drawPath(star, gold);
    }

    private static void polygon(Path path, float[] p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.close();
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        red.setColorFilter(filter);
        gold.setColorFilter(filter);
        edge.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
