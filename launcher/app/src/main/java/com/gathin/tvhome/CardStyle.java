package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

/**
 * The card look of Launcher settings and the quick settings panel (this fork): flat rounded cards with a 1 dp border,
 * a coloured icon chip per row in the theme's tones ({@link CardPalette}), a title over one muted line, and a border in
 * the theme's accent on the focused card. Plain drawables built once per screen, no elevation, blur or bitmaps; the
 * focus lift (scale 1.02) only runs with motion on. Home never touches this class.
 */
final class CardStyle {
    static final int SURFACE = 0xF0161920;
    static final int SURFACE_FOCUSED = 0xF6222632;
    static final int BORDER = 0x24FFFFFF;
    static final int MUTED = 0xFF949CAB;
    private static final int PILL = 0xFF1D212A;

    private static String cachedKey;
    private static CardPalette cachedPalette;

    final CardPalette palette;
    /** Text colour of a row's current value: star gold with the Vietnam skin, a light accent otherwise. */
    final int valueInk;
    final boolean motion;
    private final float d;
    private final Drawable.ConstantState card;
    private final Drawable.ConstantState pill;

    private CardStyle(Context ctx, CardPalette palette, boolean motion, boolean vietnam) {
        this.palette = palette;
        this.motion = motion;
        d = ctx.getResources().getDisplayMetrics().density;
        valueInk = vietnam ? Festival.STAR_GOLD : CardPalette.withLightness(palette.secondary, 0.74f);
        StateListDrawable c = new StateListDrawable();
        c.addState(new int[]{android.R.attr.state_focused}, shape(SURFACE_FOCUSED, 16, 2, palette.accent));
        c.addState(new int[]{}, shape(SURFACE, 16, 1, BORDER));
        card = c.getConstantState();
        int chosen = palette.tones[0];
        StateListDrawable p = new StateListDrawable();
        p.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected},
                shape(chosen, 12, 2, 0xFFFFFFFF));
        p.addState(new int[]{android.R.attr.state_focused}, shape(SURFACE_FOCUSED, 12, 2, palette.accent));
        p.addState(new int[]{android.R.attr.state_selected}, shape(chosen, 12, 1, CardPalette.shift(chosen, 0.15f)));
        p.addState(new int[]{}, shape(PILL, 12, 1, BORDER));
        pill = p.getConstantState();
    }

    /** The style for a screen. The palette is cached per (bundled theme, skin), so a second screen costs nothing. */
    static CardStyle of(Context ctx, Config cfg) {
        Festival festival = cfg == null ? Festival.NONE : cfg.festival;
        boolean festive = festival.active(java.time.LocalDate.now());
        boolean vietnam = festive && festival.isVietnam();
        int accent = festive ? festival.accentColor() : 0;
        String theme = bundledTheme(ctx);
        String key = theme + "|" + vietnam + "|" + accent;
        CardPalette palette;
        synchronized (CardStyle.class) {
            if (!key.equals(cachedKey)) {
                cachedPalette = CardPalette.of(theme, ThemeInstaller.overlay(ctx), accent, vietnam);
                cachedKey = key;
            }
            palette = cachedPalette;
        }
        boolean motion = cfg != null && !cfg.reducedMotion && cfg.animations;
        return new CardStyle(ctx, palette, motion, vietnam);
    }

    /** The bundled theme the TV runs, when the theme picker applied it and nothing has replaced it since; else "". */
    private static String bundledTheme(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(LauncherSettingsActivity.BUNDLED_THEME_KEY, "");
        if (name == null || name.isEmpty()) return "";
        File dir = Config.dir(ctx);
        return dir != null && new File(dir, Config.FILE_NAME).isFile() ? name : "";
    }

    private GradientDrawable shape(int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusDp * d);
        if (strokeDp > 0) g.setStroke(Math.max(1, Math.round(strokeDp * d)), stroke);
        return g;
    }

    Drawable card() {
        return card.newDrawable();
    }

    Drawable pill() {
        return pill.newDrawable();
    }

    /** A rounded-square chip in row {@code index}'s tone with the icon centred on it. */
    Drawable chip(Context ctx, int icon, int index, int sizeDp) {
        int colour = palette.chip(index);
        GradientDrawable square = shape(colour, 10, 0, 0);
        int size = Math.round(sizeDp * d);
        square.setSize(size, size);
        Drawable glyph = ctx.getDrawable(icon).mutate();
        glyph.setTint(CardPalette.iconInk(colour));
        LayerDrawable chip = new LayerDrawable(new Drawable[]{square, glyph});
        int glyphSize = Math.round(sizeDp * 0.56f * d);
        chip.setLayerSize(1, glyphSize, glyphSize);
        chip.setLayerGravity(1, Gravity.CENTER);
        return chip;
    }

    /** Puts the chip on an ImageView (the quick panel's rows). */
    void chipOn(ImageView view, int icon, int index) {
        int colour = palette.chip(index);
        view.setBackground(shape(colour, 10, 0, 0));
        view.setImageResource(icon);
        view.setImageTintList(android.content.res.ColorStateList.valueOf(CardPalette.iconInk(colour)));
    }

    /**
     * A settings row as a card: chip at the start, chevron at the end, the title over one smaller line ({@code sub},
     * muted, or in the value colour when it is the row's current value). {@code badge} adds a pill after the title.
     */
    void row(Context ctx, TextView row, int icon, int index, CharSequence title, CharSequence sub, boolean isValue,
             CharSequence badge) {
        if (row.getTag(R.id.card_chip) == null) {
            row.setBackground(card());
            Drawable chip = chip(ctx, icon, index, 38);
            Drawable chevron = ctx.getDrawable(R.drawable.ic_chevron_end).mutate();
            chevron.setTint(MUTED);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(chip, null, chevron, null);
            row.setCompoundDrawablePadding(Math.round(16 * d));
            row.setTag(R.id.card_chip, chip);
            lift(row);
        }
        row.setText(text(title, sub, isValue, badge));
    }

    /** A group caption (numbered choices under it) with its chip; the card around it lights with its pills. */
    void caption(Context ctx, TextView caption, int icon, int index) {
        Drawable chip = chip(ctx, icon, index, 34);
        caption.setCompoundDrawablesRelativeWithIntrinsicBounds(chip, null, null, null);
        caption.setCompoundDrawablePadding(Math.round(14 * d));
        View group = (View) caption.getParent();
        group.setBackground(card());
    }

    CharSequence text(CharSequence title, CharSequence sub, boolean isValue, CharSequence badge) {
        SpannableStringBuilder s = new SpannableStringBuilder(title);
        if (badge != null && badge.length() > 0) {
            s.append("  ");
            int start = s.length();
            s.append(badge);
            s.setSpan(new Badge(palette.secondary, d), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (sub != null && sub.length() > 0) {
            s.append('\n');
            int start = s.length();
            s.append(sub);
            s.setSpan(new AbsoluteSizeSpan(14, true), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(isValue ? valueInk : MUTED), start, s.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** Scale 1.02 on focus with motion on; with reduced_motion only the border and fill change. */
    void lift(View v) {
        if (!motion) return;
        v.setOnFocusChangeListener((view, focused) -> {
            float to = focused ? 1.02f : 1f;
            view.animate().scaleX(to).scaleY(to).setDuration(120).start();
        });
    }

    /** "New" beside the update row's title: a small pill in the theme's secondary colour. */
    static final class Badge extends ReplacementSpan {
        private final int fill;
        private final float d;
        private final RectF box = new RectF();

        Badge(int fill, float d) {
            this.fill = fill;
            this.d = d;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float size = paint.getTextSize();
            paint.setTextSize(size * 0.68f);
            float w = paint.measureText(text, start, end);
            paint.setTextSize(size);
            return Math.round(w + 16 * d);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            float size = paint.getTextSize();
            int colour = paint.getColor();
            float mid = y + (paint.ascent() + paint.descent()) / 2f;   // the title's middle, at its own size
            paint.setTextSize(size * 0.68f);
            float w = paint.measureText(text, start, end);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float h = fm.descent - fm.ascent + 4 * d;
            box.set(x, mid - h / 2f, x + w + 16 * d, mid + h / 2f);
            paint.setColor(fill);
            canvas.drawRoundRect(box, h / 2f, h / 2f, paint);
            paint.setColor(CardPalette.iconInk(fill));
            canvas.drawText(text, start, end, x + 8 * d, mid - (fm.ascent + fm.descent) / 2f, paint);
            paint.setTextSize(size);
            paint.setColor(colour);
        }
    }
}
