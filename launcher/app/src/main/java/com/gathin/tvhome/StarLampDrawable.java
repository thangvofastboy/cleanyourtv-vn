package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Vietnam skin's network pill: a gold five-pointed star that shines while the TV is online and goes dim grey
 * when the connection drops. Its brightness follows the signal level (1 to 4) like the diya's flame; while started
 * it twinkles slowly at about 15 fps, and Ethernet or reduced motion hold it still. Drawn from a path built once,
 * nothing allocated per frame.
 */
public final class StarLampDrawable extends Drawable implements FestiveLamp {

    private static final float BOX_DP = 24f;
    private static final long FRAME_MS = 66, FADE_MS = 350;
    private static final float[] LEVEL_GLOW = {0f, 0.6f, 0.75f, 0.9f, 1f};
    private static final int DIM = 0xFF7C7F86, EDGE_DIM = 0xFF4A4C52, EDGE_LIT = 0xFFB37A00;

    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private final Path haloStar = new Path();
    private final Rect built = new Rect();
    private final long epoch = SystemClock.uptimeMillis();

    private int level = 4;
    private boolean primed, running, steady, reduced;
    private float glowFrom = 1f, glowTo = 1f;
    private long fadeStart;
    private int alpha = 255;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (wantsFrames()) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    public StarLampDrawable(float density) {
        this.density = density;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void setFlame(int newLevel) {
        int lvl = Math.max(0, Math.min(4, newLevel));
        if (primed && lvl == level) return;   // the minute tick re-reads an unchanged level
        if (!primed || reduced) {
            primed = true;
            level = lvl;
            glowFrom = glowTo = LEVEL_GLOW[lvl];
            kick();
            return;
        }
        long now = SystemClock.uptimeMillis();
        glowFrom = glow(now);
        glowTo = LEVEL_GLOW[lvl];
        fadeStart = now;
        level = lvl;
        kick();
    }

    @Override
    public void setSteady(boolean steady) {
        if (this.steady == steady) return;
        this.steady = steady;
        kick();
    }

    @Override
    public void setReducedMotion(boolean reducedMotion) {
        if (reduced == reducedMotion) return;
        reduced = reducedMotion;
        if (reduced) glowFrom = glowTo;
        kick();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        kick();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        glowFrom = glowTo;
        kick();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (changed) kick();
        return changed;
    }

    private boolean fading(long now) {
        return glowFrom != glowTo && now - fadeStart < FADE_MS;
    }

    private boolean wantsFrames() {
        return running && isVisible() && !reduced
                && ((level > 0 && !steady) || fading(SystemClock.uptimeMillis()));
    }

    private void kick() {
        unscheduleSelf(tick);
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS);
        invalidateSelf();
    }

    private float glow(long now) {
        float p = Math.min(1f, Math.max(0f, (now - fadeStart) / (float) FADE_MS));
        float e = 1f - (1f - p) * (1f - p);
        return glowFrom + (glowTo - glowFrom) * e;
    }

    /** A slow twinkle around 1 while online and moving; still otherwise. */
    private float twinkle(long now) {
        if (!running || reduced || steady || level == 0) return 1f;
        float t = (now - epoch) / 1000f;
        return 0.9f + 0.1f * (float) Math.sin(t * 2.4f);
    }

    private void build(Rect b) {
        built.set(b);
        float cx = b.exactCenterX(), cy = b.exactCenterY() + b.height() * 0.04f;
        float outer = Math.min(b.width(), b.height()) * 0.46f;
        fill(star, Star.points(cx, cy, outer, outer * Star.INNER_RATIO, 0f));
        float h = outer * 1.12f;
        fill(haloStar, Star.points(cx, cy, h, h * Star.INNER_RATIO * 1.15f, 0f));
        edge.setStrokeWidth(Math.max(1f, outer * 0.07f));
    }

    private static void fill(Path path, float[] p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (!b.equals(built)) build(b);
        long now = SystemClock.uptimeMillis();
        float g = Math.max(0f, Math.min(1f, glow(now) * twinkle(now)));
        if (g > 0.05f) {
            halo.setColor(Festival.STAR_GOLD);
            halo.setAlpha(Math.round(alpha * 0.28f * g));
            canvas.drawPath(haloStar, halo);
        }
        fill.setColor(mix(DIM, Festival.STAR_GOLD, g));
        fill.setAlpha(alpha);
        canvas.drawPath(star, fill);
        edge.setColor(mix(EDGE_DIM, EDGE_LIT, g));
        edge.setAlpha(alpha);
        canvas.drawPath(star, edge);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
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
        fill.setColorFilter(filter);
        edge.setColorFilter(filter);
        halo.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
