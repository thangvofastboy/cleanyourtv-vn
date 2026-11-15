package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class StockLauncherTest {

    @Test
    public void launcherxGetsTheExactCommands() {
        assertEquals(Arrays.asList(
                "adb shell pm enable --user 0 com.google.android.apps.tv.launcherx",
                "adb shell pm enable --user 0 com.google.android.tungsten.setupwraith",
                "adb shell pm uninstall com.gathin.tvhome",
                "adb shell 'cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief"
                        + " -a android.intent.action.MAIN -c android.intent.category.HOME"
                        + " com.google.android.apps.tv.launcherx | tail -n 1)'"),
                StockLauncher.commands("com.google.android.apps.tv.launcherx", "com.gathin.tvhome"));
    }

    @Test
    public void anotherStockLauncherHasItsHomeActivityLookedUpOnTheTv() {
        List<String> c = StockLauncher.commands("com.mitv.tvhome", "com.gathin.tvhome");
        assertEquals("adb shell pm enable --user 0 com.mitv.tvhome", c.get(0));
        assertTrue(c.get(3).contains("resolve-activity --brief -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME com.mitv.tvhome"));
    }

    @Test
    public void theKnownListMatchesBuildAndInstall() {
        assertEquals(Arrays.asList("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
                "com.google.android.leanbacklauncher", "com.mitv.tvhome"), StockLauncher.KNOWN);
    }
}
