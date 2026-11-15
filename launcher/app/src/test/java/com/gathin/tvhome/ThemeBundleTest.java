package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ThemeBundleTest {

    private static List<String> steps(List<ThemeBundle.Step> plan) {
        List<String> out = new ArrayList<>();
        for (ThemeBundle.Step s : plan) out.add(s.toString());
        return out;
    }

    @Test
    public void indexKeepsThemesAndFilesInOrderAndSkipsJunk() {
        Map<String, List<String>> m = ThemeBundle.parseIndex(
                "dong-son\tconfig.json\ndong-son\twallpapers/01.jpg\n\nbad line\naurora\tconfig.json\naurora\twallpaper.jpg\n");
        assertEquals(Arrays.asList("dong-son", "aurora"), new ArrayList<>(m.keySet()));
        assertEquals(Arrays.asList("config.json", "wallpapers/01.jpg"), m.get("dong-son"));
        assertTrue(ThemeBundle.parseIndex(null).isEmpty());
    }

    @Test
    public void aSingleWallpaperThemeWritesWhatThemeShWrites() {
        assertEquals(Arrays.asList("write config.json", "write wallpaper.jpg", "clear wallpapers",
                        "delete wallpapers/captions.txt", "write theme_revision"),
                steps(ThemeBundle.plan(Arrays.asList("config.json", "wallpaper.jpg"))));
    }

    @Test
    public void aCollectionClearsTheOldPicturesAndRemovesTheSingleWallpaper() {
        assertEquals(Arrays.asList("write config.json", "delete wallpaper.jpg", "clear wallpapers",
                        "write wallpapers/01.jpg", "write wallpapers/02.jpg", "write wallpapers/captions.txt",
                        "write theme_revision"),
                steps(ThemeBundle.plan(Arrays.asList("config.json", "wallpapers/captions.txt",
                        "wallpapers/02.jpg", "wallpapers/01.jpg"))));
    }

    @Test
    public void aThemeWithoutPicturesStillEmptiesThePreviousOnes() {
        assertEquals(Arrays.asList("write config.json", "delete wallpaper.jpg", "clear wallpapers",
                        "delete wallpapers/captions.txt", "write theme_revision"),
                steps(ThemeBundle.plan(Collections.singletonList("config.json"))));
    }

    @Test
    public void refusesWhatThemeFilesRefuses() {
        assertNull(ThemeBundle.plan(null));
        assertNull(ThemeBundle.plan(Collections.singletonList("wallpaper.jpg")));
        assertNull(ThemeBundle.plan(Arrays.asList("config.json", "screenshot.jpg")));
        assertNull(ThemeBundle.plan(Arrays.asList("config.json", "../config.json")));
        assertNull(ThemeBundle.plan(Arrays.asList("config.json", "wallpapers/run.sh")));
        assertNull(ThemeBundle.plan(Arrays.asList("config.json", "theme_revision")));
    }

    @Test
    public void resetIsThemeShReset() {
        assertEquals(Arrays.asList("delete config.json", "delete wallpaper.jpg", "delete wallpapers/captions.txt",
                "clear wallpapers", "write theme_revision"), steps(ThemeBundle.reset()));
    }

    private static final String OVERLAY = "{\"all\":{\"dock_apps\":[\"a\",\"b\"],\"hidden_apps\":[\"x\"],"
            + "\"settings_pill_panel\":true},\"layout\":{\"columns\":4},"
            + "\"themes\":{\"cinematic\":{\"wallpaper_true_color\":false,\"advanced_effects\":false}},"
            + "\"labels\":{\"dong-son\":\"Đông Sơn\"}}";

    @Test
    public void overlayReplacesAppsAndColumnsAndKeepsTheRest() throws Exception {
        JSONObject theme = new JSONObject("{\"columns\":6,\"dock_apps\":[\"n\"],\"background_color\":\"#000000\"}");
        JSONObject out = ThemeBundle.merge("glass", theme, new JSONObject(OVERLAY));
        assertEquals(4, out.getInt("columns"));
        assertEquals("a", out.getJSONArray("dock_apps").getString(0));
        assertEquals(2, out.getJSONArray("dock_apps").length());
        assertEquals("x", out.getJSONArray("hidden_apps").getString(0));
        assertTrue(out.getBoolean("settings_pill_panel"));
        assertEquals("#000000", out.getString("background_color"));
        assertEquals(6, theme.getInt("columns"));   // the theme itself is not changed
        assertFalse(out.has("labels"));
    }

    @Test
    public void singleRowThemesKeepTheirColumns() throws Exception {
        JSONObject theme = new JSONObject("{\"single_row\":true,\"columns\":6}");
        JSONObject out = ThemeBundle.merge("dock-glass", theme, new JSONObject(OVERLAY));
        assertEquals(6, out.getInt("columns"));
        assertEquals("a", out.getJSONArray("dock_apps").getString(0));
    }

    @Test
    public void perThemeOverridesApplyOnlyToThatTheme() throws Exception {
        JSONObject theme = new JSONObject("{\"wallpaper_true_color\":true,\"advanced_effects\":true}");
        JSONObject cinematic = ThemeBundle.merge("cinematic", theme, new JSONObject(OVERLAY));
        assertFalse(cinematic.getBoolean("wallpaper_true_color"));
        assertFalse(cinematic.getBoolean("advanced_effects"));
        JSONObject redRain = ThemeBundle.merge("red-rain", theme, new JSONObject(OVERLAY));
        assertTrue(redRain.getBoolean("wallpaper_true_color"));
    }

    @Test
    public void noOverlayIsTheThemeAsItIs() throws Exception {
        assertEquals(6, ThemeBundle.merge("x", new JSONObject("{\"columns\":6}"), null).getInt("columns"));
    }

    @Test
    public void labelsComeFromTheOverlay() throws Exception {
        assertEquals("Đông Sơn", ThemeBundle.label("dong-son", new JSONObject(OVERLAY)));
        assertEquals("aurora", ThemeBundle.label("aurora", new JSONObject(OVERLAY)));
        assertEquals("aurora", ThemeBundle.label("aurora", null));
    }
}
