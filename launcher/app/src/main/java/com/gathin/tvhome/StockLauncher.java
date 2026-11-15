package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;
import java.util.List;

/**
 * This fork's "Return to stock launcher" help: which stock launcher this TV has, and the adb commands that make it
 * Home again. Home itself never changes from here; only the commands are shown.
 */
final class StockLauncher {
    /** launcher/build-and-install.sh's KNOWN_STOCK_LAUNCHERS, in the same order. */
    static final List<String> KNOWN = Arrays.asList("com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome");
    static final String LAUNCHERX = "com.google.android.apps.tv.launcherx";
    static final String SETUPWRAITH = "com.google.android.tungsten.setupwraith";
    static final String HELP_URL = "https://github.com/thangvofastboy/cleanyourtv-vn#quay-ve-launcher-goc";

    private StockLauncher() {}

    /** stock_home_redirect when set, else the first known stock launcher installed (disabled counts), else launcherx. */
    static String find(Context ctx, String configured) {
        if (configured != null && !configured.trim().isEmpty()) return configured.trim();
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : KNOWN) {
            try {
                pm.getPackageInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
                return pkg;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            }
        }
        return LAUNCHERX;
    }

    /**
     * The adb commands, in order: the stock launcher back on, setupwraith back on, this app removed (optional), Home to
     * the stock launcher. Home is set last: Android drops the Home preference whenever the set of Home apps changes
     * (enabling setupwraith, removing this app), and on Google TV 14 the next candidate can be setupwraith's
     * RecoveryActivity, which crash-loops (launcher/build-and-install.sh).
     */
    static List<String> commands(String stock, String self) {
        // The Home activity is looked up on the TV for every launcher, launcherx included: its name differs by build.
        String home = "adb shell 'cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief"
                + " -a android.intent.action.MAIN -c android.intent.category.HOME " + stock + " | tail -n 1)'";
        return Arrays.asList("adb shell pm enable --user 0 " + stock,
                "adb shell pm enable --user 0 " + SETUPWRAITH,
                "adb shell pm uninstall " + self,
                home);
    }
}
