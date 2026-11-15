package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The colours of the card-style settings screens (this fork): two to four tones taken from the theme, and one chip
 * colour per row made by shifting the lightness of those tones, so the rows match the theme instead of a fixed
 * rainbow. Pure logic, computed once per screen; {@link CardStyle} turns it into drawables.
 */
final class CardPalette {
    /** No theme palette and no festive skin: calm slate and steel tones. */
    static final int[] NEUTRAL = {0xFF5B6B80, 0xFF4F7A8C, 0xFF6E6A86};
    static final int FLAG_RED = 0xFFDA251D;

    /** The tones rows are drawn from, the colour of a focused card's border and of the update badge. */
    final int[] tones;
    final int accent;
    final int secondary;

    CardPalette(int[] tones, int accent, int secondary) {
        this.tones = tones;
        this.accent = accent;
        this.secondary = secondary;
    }

    /**
     * The palette for a screen: the bundled theme's "palettes" entry in theme_overlay.json when there is one, else the
     * festive skin's accent (Vietnam: flag red with star gold), else {@link #NEUTRAL}. A festive skin keeps its accent on
     * the focus border whatever the tones are.
     */
    static CardPalette of(String theme, JSONObject overlay, int festiveAccent, boolean vietnam) {
        int[] tones = themeTones(theme, overlay);
        if (tones == null && vietnam) tones = new int[]{FLAG_RED, Festival.STAR_GOLD, 0xFFA3201A};
        if (tones == null && festiveAccent != 0) tones = new int[]{festiveAccent, shift(festiveAccent, -0.12f)};
        if (tones == null) tones = NEUTRAL;
        int accent = vietnam ? FLAG_RED : festiveAccent != 0 ? festiveAccent : withLightness(tones[0], 0.66f);
        int secondary = vietnam ? Festival.STAR_GOLD : tones.length > 1 ? tones[1] : withLightness(tones[0], 0.7f);
        return new CardPalette(tones, accent, secondary);
    }

    /** 2 to 4 valid #RRGGBB tones of {@code overlay.palettes[theme]}, or null. */
    static int[] themeTones(String theme, JSONObject overlay) {
        if (theme == null || theme.isEmpty() || overlay == null) return null;
        JSONObject palettes = overlay.optJSONObject("palettes");
        JSONArray list = palettes == null ? null : palettes.optJSONArray(theme);
        if (list == null) return null;
        int n = Math.min(4, list.length());
        int[] out = new int[n];
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int c = parse(list.optString(i, ""));
            if (c != 0) out[kept++] = c;
        }
        if (kept < 2) return null;
        int[] tones = new int[kept];
        System.arraycopy(out, 0, tones, 0, kept);
        return tones;
    }

    /** Opaque ARGB of "#RRGGBB", or 0 for anything else. */
    static int parse(String s) {
        if (s == null || s.length() != 7 || s.charAt(0) != '#') return 0;
        try {
            return 0xFF000000 | Integer.parseInt(s.substring(1), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Lightness steps the rows walk through once every tone has been used. */
    private static final float[] STEPS = {0f, -0.08f, 0.07f, -0.14f};

    /**
     * Row {@code index}'s chip colour: tone index mod n, darker or lighter on each pass, kept between 26% and 50%
     * lightness and below 85% saturation so neighbouring rows differ and none glares on the dark card.
     */
    int chip(int index) {
        int i = Math.max(0, index);
        int base = tones[i % tones.length];
        float[] hsl = hsl(base);
        float l = clamp(hsl[2] + STEPS[(i / tones.length) % STEPS.length], 0.26f, 0.5f);
        return fromHsl(hsl[0], Math.min(hsl[1], 0.85f), l);
    }

    /** Dark ink on a light chip (gold), white otherwise. */
    static int iconInk(int chip) {
        return luminance(chip) > 0.42 ? 0xFF1B1A17 : 0xFFFFFFFF;
    }

    static int shift(int argb, float dl) {
        float[] hsl = hsl(argb);
        return fromHsl(hsl[0], hsl[1], clamp(hsl[2] + dl, 0f, 1f));
    }

    static int withLightness(int argb, float l) {
        float[] hsl = hsl(argb);
        return fromHsl(hsl[0], hsl[1], clamp(l, 0f, 1f));
    }

    /** WCAG relative luminance, 0 to 1. */
    static double luminance(int argb) {
        return 0.2126 * lin((argb >> 16) & 0xFF) + 0.7152 * lin((argb >> 8) & 0xFF) + 0.0722 * lin(argb & 0xFF);
    }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static float[] hsl(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f;
        float d = max - min;
        if (d > 0f) {
            s = d / (1f - Math.abs(2f * l - 1f));
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0f) h += 360f;
        }
        return new float[]{h, Math.min(1f, s), l};
    }

    static int fromHsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r, g, b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return 0xFF000000 | (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
