package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class CardPaletteTest {

    private static JSONObject overlay(String json) throws Exception {
        return new JSONObject(json);
    }

    @Test
    public void aBundledThemesPaletteWins() throws Exception {
        JSONObject o = overlay("{\"palettes\":{\"aurora\":[\"#22C3A6\",\"#7C5CE0\",\"bad\",\"#3BA0E6\"]}}");
        CardPalette p = CardPalette.of("aurora", o, 0, false);
        assertArrayEquals(new int[]{0xFF22C3A6, 0xFF7C5CE0, 0xFF3BA0E6}, p.tones);
        assertEquals(0xFF7C5CE0, p.secondary);
    }

    @Test
    public void vietnamKeepsFlagRedAndStarGoldWhateverTheTones() throws Exception {
        JSONObject o = overlay("{\"palettes\":{\"dong-son\":[\"#B87333\",\"#C9A227\"]}}");
        CardPalette p = CardPalette.of("dong-son", o, CardPalette.FLAG_RED, true);
        assertEquals(CardPalette.FLAG_RED, p.accent);
        assertEquals(Festival.STAR_GOLD, p.secondary);
        assertEquals(0xFFB87333, p.tones[0]);
        CardPalette none = CardPalette.of("", null, CardPalette.FLAG_RED, true);
        assertEquals(CardPalette.FLAG_RED, none.tones[0]);
        assertEquals(Festival.STAR_GOLD, none.tones[1]);
    }

    @Test
    public void noThemeAndNoSkinIsNeutral() {
        CardPalette p = CardPalette.of(null, null, 0, false);
        assertArrayEquals(CardPalette.NEUTRAL, p.tones);
    }

    @Test
    public void tooFewOrBrokenTonesFallBack() throws Exception {
        assertNull(CardPalette.themeTones("x", overlay("{\"palettes\":{\"x\":[\"#112233\"]}}")));
        assertNull(CardPalette.themeTones("x", overlay("{\"palettes\":{\"x\":\"#112233\"}}")));
        assertNull(CardPalette.themeTones("y", overlay("{\"palettes\":{\"x\":[\"#112233\",\"#445566\"]}}")));
        assertEquals(0, CardPalette.parse("#12345"));
        assertEquals(0, CardPalette.parse("112233"));
        assertEquals(0, CardPalette.parse("#GG0000"));
    }

    @Test
    public void neighbouringChipsDifferAndStayReadable() {
        CardPalette p = CardPalette.of(null, null, 0, false);
        for (int i = 0; i < 16; i++) {
            int chip = p.chip(i);
            assertNotEquals(chip, p.chip(i + 1));
            float l = CardPalette.hsl(chip)[2];
            assertTrue("row " + i + " lightness " + l, l >= 0.255f && l <= 0.505f);
            assertEquals(chip, p.chip(i));   // the same row always gets the same colour
        }
    }

    @Test
    public void goldChipsTakeDarkInk() {
        assertEquals(0xFF1B1A17, CardPalette.iconInk(Festival.STAR_GOLD));
        assertEquals(0xFFFFFFFF, CardPalette.iconInk(CardPalette.FLAG_RED));
    }

    @Test
    public void hslRoundTrips() {
        for (int c : new int[]{0xFFDA251D, 0xFFFFCD00, 0xFF3BA0E6, 0xFF5B6B80, 0xFF000000, 0xFFFFFFFF}) {
            float[] hsl = CardPalette.hsl(c);
            int back = CardPalette.fromHsl(hsl[0], hsl[1], hsl[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs(((c >> shift) & 0xFF) - ((back >> shift) & 0xFF)) <= 1);
            }
        }
    }
}
