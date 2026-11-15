package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The themes bundled in the APK's assets (copied from themes/ at build time) and what applying one writes into
 * {@link Config#dir}: the same files, in the same order, as tools/theme.sh. Pure logic, no Android calls, so the
 * plan and the overlay merge are unit-tested; {@link ThemeInstaller} carries it out on the TV.
 */
final class ThemeBundle {
    static final String ROOT = "themes";
    static final String INDEX = ROOT + "/index.txt";
    static final String OVERLAY = "theme_overlay.json";
    static final String REVISION = "theme_revision";

    private ThemeBundle() {}

    /** One file operation, relative to the theme directory. */
    static final class Step {
        static final int WRITE = 0, DELETE = 1, CLEAR_COLLECTION = 2;
        final int op;
        final String path;

        Step(int op, String path) {
            this.op = op;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Step && ((Step) o).op == op && ((Step) o).path.equals(path);
        }

        @Override
        public int hashCode() {
            return op * 31 + path.hashCode();
        }

        @Override
        public String toString() {
            return (op == WRITE ? "write " : op == DELETE ? "delete " : "clear ") + path;
        }
    }

    /** index.txt ("name<TAB>path" per line) as name to its files, in file order; malformed lines are skipped. */
    static Map<String, List<String>> parseIndex(String text) {
        Map<String, List<String>> themes = new LinkedHashMap<>();
        if (text == null) return themes;
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split("\t");
            if (parts.length != 2 || parts[0].isEmpty()) continue;
            List<String> files = themes.get(parts[0]);
            if (files == null) themes.put(parts[0], files = new ArrayList<>());
            files.add(parts[1]);
        }
        return themes;
    }

    /**
     * What applying a theme with these files does, in theme.sh's order: config.json; wallpaper.jpg or its removal;
     * the collection emptied, then its pictures; captions.txt or its removal; {@link #REVISION} last, which tells
     * the launcher a theme was pushed. Null when there is no config.json or a file ThemeFiles would refuse.
     */
    static List<Step> plan(List<String> files) {
        if (files == null || !files.contains(Config.FILE_NAME)) return null;
        List<String> pictures = new ArrayList<>();
        boolean wallpaper = false, captions = false;
        for (String f : files) {
            String path = ThemeFiles.path(Arrays.asList(f.split("/", -1)));
            if (path == null || REVISION.equals(path)) return null;
            if (Config.WALLPAPER_NAME.equals(path)) wallpaper = true;
            else if (path.equals(ThemeFiles.WALLPAPERS + "/" + WallpaperCaptions.FILE_NAME)) captions = true;
            else if (path.startsWith(ThemeFiles.WALLPAPERS + "/")) pictures.add(path);
        }
        Collections.sort(pictures);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Step.WRITE, Config.FILE_NAME));
        steps.add(new Step(wallpaper ? Step.WRITE : Step.DELETE, Config.WALLPAPER_NAME));
        steps.add(new Step(Step.CLEAR_COLLECTION, ThemeFiles.WALLPAPERS));
        for (String p : pictures) steps.add(new Step(Step.WRITE, p));
        String captionsPath = ThemeFiles.WALLPAPERS + "/" + WallpaperCaptions.FILE_NAME;
        steps.add(new Step(captions ? Step.WRITE : Step.DELETE, captionsPath));
        steps.add(new Step(Step.WRITE, REVISION));
        return steps;
    }

    /** theme.sh --reset: the overlay, the pictures and the captions go, and a new revision is written. */
    static List<Step> reset() {
        return Arrays.asList(new Step(Step.DELETE, Config.FILE_NAME), new Step(Step.DELETE, Config.WALLPAPER_NAME),
                new Step(Step.DELETE, ThemeFiles.WALLPAPERS + "/" + WallpaperCaptions.FILE_NAME),
                new Step(Step.CLEAR_COLLECTION, ThemeFiles.WALLPAPERS), new Step(Step.WRITE, REVISION));
    }

    /**
     * The theme's config.json with this TV's overlay on top: the overlay's "all" keys always, its "layout" keys
     * unless the theme is single_row (a one-row dock keeps its own columns), then "themes".{name}. The theme
     * object itself is left unchanged.
     */
    static JSONObject merge(String name, JSONObject theme, JSONObject overlay) throws JSONException {
        JSONObject out = new JSONObject(theme.toString());
        if (overlay == null) return out;
        put(out, overlay.optJSONObject("all"));
        if (!theme.optBoolean("single_row", false)) put(out, overlay.optJSONObject("layout"));
        JSONObject per = overlay.optJSONObject("themes");
        if (per != null) put(out, per.optJSONObject(name));
        return out;
    }

    /** The picker's name for a theme: the overlay's label, or the folder name. */
    static String label(String name, JSONObject overlay) {
        JSONObject labels = overlay == null ? null : overlay.optJSONObject("labels");
        String l = labels == null ? "" : labels.optString(name, "").trim();
        return l.isEmpty() ? name : l;
    }

    private static void put(JSONObject out, JSONObject keys) throws JSONException {
        if (keys == null) return;
        for (Iterator<String> it = keys.keys(); it.hasNext(); ) {
            String key = it.next();
            out.put(key, keys.get(key));
        }
    }
}
