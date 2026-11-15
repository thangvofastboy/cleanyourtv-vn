package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies a bundled theme from the APK's assets on the TV, writing what tools/theme.sh pushes into
 * {@link Config#dir} (see {@link ThemeBundle#plan}). Runs off the main thread, one apply at a time per process
 * ({@link #BUSY}). Every new file is first written and synced into a staging folder next to the theme; only when
 * all of them are there does the plan run, renaming them into place, so a write that fails (a full disk, an
 * unreadable asset) leaves the previous theme untouched. Every delete is checked, so a file the launcher cannot
 * replace (one an older theme.sh push left owned by the shell) is reported instead of being silently kept.
 */
final class ThemeInstaller {

    /** Held by the apply in progress, whichever Launcher settings screen started it. */
    static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final String STAGING = ".theme-staging";

    /** How an apply ended: {@link #failed} is null when it is done, or the path that failed. */
    static final class Result {
        final String failed;
        /** Whether the theme folder was changed at all; false when the failure came while staging. */
        final boolean changed;

        Result(String failed, boolean changed) {
            this.failed = failed;
            this.changed = changed;
        }
    }

    private ThemeInstaller() {}

    /** The bundled themes, name to files, in the build's order; empty when the APK carries none. */
    static Map<String, List<String>> themes(Context ctx) {
        try {
            return ThemeBundle.parseIndex(new String(read(ctx.getAssets(), ThemeBundle.INDEX), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    /** The overlay this TV puts over every bundled theme, or null when it cannot be read. */
    static JSONObject overlay(Context ctx) {
        try {
            return new JSONObject(new String(read(ctx.getAssets(), ThemeBundle.OVERLAY), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Applies theme {@code name} (null: the reset). Call with {@link #BUSY} held. */
    static Result apply(Context ctx, String name) {
        File dir = Config.dir(ctx);
        if (dir == null) return new Result("Android/data", false);
        List<ThemeBundle.Step> steps = name == null ? ThemeBundle.reset() : ThemeBundle.plan(themes(ctx).get(name));
        if (steps == null) return new Result(name, false);
        if (!dir.isDirectory() && !dir.mkdirs()) return new Result(dir.getPath(), false);
        File staging = new File(dir, STAGING);
        deleteTree(staging);   // what an apply cut short (the process killed) left behind
        try {
            // 1. Stage: every file the plan writes, except the revision, fully written and synced.
            AssetManager assets = ctx.getAssets();
            JSONObject overlay = name == null ? null : overlay(ctx);
            for (ThemeBundle.Step step : steps) {
                if (step.op != ThemeBundle.Step.WRITE || ThemeBundle.REVISION.equals(step.path)) continue;
                try {
                    byte[] bytes;
                    if (Config.FILE_NAME.equals(step.path)) {
                        String own = new String(read(assets, asset(name, step.path)), StandardCharsets.UTF_8);
                        if (!own.isEmpty() && own.charAt(0) == 0xFEFF) own = own.substring(1);   // a BOM
                        bytes = ThemeBundle.merge(name, new JSONObject(own), overlay).toString(2)
                                .getBytes(StandardCharsets.UTF_8);
                    } else {
                        bytes = read(assets, asset(name, step.path));
                    }
                    if (!writeSynced(new File(staging, step.path), bytes)) return new Result(step.path, false);
                } catch (Exception e) {
                    return new Result(step.path, false);
                }
            }
            // 2. Swap, in theme.sh's order: from here on the folder changes.
            boolean changed = false;
            for (ThemeBundle.Step step : steps) {
                File target = new File(dir, step.path);
                try {
                    switch (step.op) {
                        case ThemeBundle.Step.DELETE:
                            if (target.exists()) {
                                if (!target.delete()) return new Result(step.path, changed);
                                changed = true;
                            }
                            break;
                        case ThemeBundle.Step.CLEAR_COLLECTION:
                            File[] images = target.listFiles((d, n) -> ThemeFiles.collectionImage(n));
                            if (images != null) {
                                for (File f : images) {
                                    if (!f.delete()) return new Result(step.path + "/" + f.getName(), changed);
                                    changed = true;
                                }
                            }
                            break;
                        default:
                            boolean ok;
                            if (ThemeBundle.REVISION.equals(step.path)) {
                                File tmp = new File(staging, step.path);
                                ok = writeSynced(tmp, UUID.randomUUID().toString().replace("-", "")
                                        .getBytes(StandardCharsets.US_ASCII)) && moveInto(tmp, target);
                            } else {
                                ok = moveInto(new File(staging, step.path), target);
                            }
                            if (!ok) return new Result(step.path, changed);
                            changed = true;
                            break;
                    }
                } catch (Exception e) {
                    return new Result(step.path, changed);
                }
            }
            return new Result(null, true);
        } finally {
            deleteTree(staging);
        }
    }

    private static String asset(String theme, String path) {
        return ThemeBundle.ROOT + "/" + theme + "/" + path;
    }

    /** Writes {@code bytes} to {@code file} and syncs it to disk before returning. */
    private static boolean writeSynced(File file, byte[] bytes) {
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
            return true;
        } catch (IOException e) {
            file.delete();
            return false;
        }
    }

    /** Renames a staged file over {@code target}; a target the rename cannot replace is deleted first. */
    private static boolean moveInto(File staged, File target) {
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
        long length = staged.length();
        if (staged.renameTo(target)) return true;
        if (target.exists() && !target.delete()) return false;
        return staged.renameTo(target) && target.length() == length;
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }

    private static byte[] read(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
