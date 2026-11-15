package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class QuickTargetsTest {

    @Test
    public void actionsAndComponentsParse() {
        QuickTargets.Target a = QuickTargets.parse(" android.settings.SOUND_SETTINGS ");
        assertFalse(a.isComponent());
        assertEquals("android.settings.SOUND_SETTINGS", a.action);
        QuickTargets.Target c = QuickTargets.parse("com.android.tv.settings/.device.sound.SoundActivity");
        assertTrue(c.isComponent());
        assertEquals("com.android.tv.settings", c.pkg);
        assertEquals("com.android.tv.settings.device.sound.SoundActivity", c.cls);
        assertEquals("com.x.y/com.x.y.Main", QuickTargets.parse("com.x.y/com.x.y.Main").toString());
    }

    @Test
    public void malformedCandidatesAreSkipped() {
        assertNull(QuickTargets.parse(null));
        assertNull(QuickTargets.parse(""));
        assertNull(QuickTargets.parse("   "));
        assertNull(QuickTargets.parse("SETTINGS"));
        assertNull(QuickTargets.parse("a/b/c"));
        assertNull(QuickTargets.parse("/com.x.Main"));
        assertNull(QuickTargets.parse("com.x/"));
        assertNull(QuickTargets.parse("com.x/."));
        assertNull(QuickTargets.parse("pkg/Main"));
        assertNull(QuickTargets.parse("android.settings.SOUND SETTINGS"));
    }

    @Test
    public void theFirstAvailableCandidateWinsInOrder() {
        Set<String> onTv = new HashSet<>(Arrays.asList("android.settings.WIFI_SETTINGS",
                "com.android.tv.settings/com.android.tv.settings.connectivity.NetworkActivity"));
        String[] list = {"bad", "android.settings.WIRELESS_SETTINGS",
                "com.android.tv.settings/.connectivity.NetworkActivity", "android.settings.WIFI_SETTINGS"};
        assertEquals("com.android.tv.settings/com.android.tv.settings.connectivity.NetworkActivity",
                QuickTargets.first(list, t -> onTv.contains(t.toString())).toString());
    }

    @Test
    public void noAvailableCandidateHidesTheRow() {
        assertNull(QuickTargets.first(new String[]{"android.settings.SOUND_SETTINGS", "com.x/.Y"}, t -> false));
        assertNull(QuickTargets.first(new String[0], t -> true));
        assertNull(QuickTargets.first(null, t -> true));
    }

    @Test
    public void aCheckThatThrowsCountsAsMissing() {
        String[] list = {"com.hidden.pkg/.Main", "android.settings.SOUND_SETTINGS"};
        QuickTargets.Target t = QuickTargets.first(list, c -> {
            if (c.isComponent()) throw new SecurityException("not visible");
            return true;
        });
        assertEquals("android.settings.SOUND_SETTINGS", t.action);
    }
}
