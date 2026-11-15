package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UpdatesTest {

    private static final String RELEASE = "{\"tag_name\":\"v1.2.0-vn\",\"name\":\"1.2.0\",\"assets\":["
            + "{\"name\":\"cleanyourtv-vn-1001.apk\",\"size\":2400000,"
            + "\"browser_download_url\":\"https://github.com/thangvofastboy/cleanyourtv-vn/releases/download/v1.2.0-vn/cleanyourtv-vn-1001.apk\"},"
            + "{\"name\":\"cleanyourtv-vn-1003.apk\",\"size\":2500000,"
            + "\"browser_download_url\":\"https://github.com/thangvofastboy/cleanyourtv-vn/releases/download/v1.2.0-vn/cleanyourtv-vn-1003.apk\"},"
            + "{\"name\":\"cleanyourtv-vn-1009.apk.sha256\",\"size\":64,\"browser_download_url\":\"https://x/y\"},"
            + "{\"name\":\"app-release.apk\",\"size\":2500000,\"browser_download_url\":\"https://x/app-release.apk\"},"
            + "{\"name\":\"cleanyourtv-vn-1005.apk\",\"size\":2500000,\"browser_download_url\":\"http://plain/1005.apk\"},"
            + "{\"name\":\"cleanyourtv-vn-1007.apk\",\"size\":0,\"browser_download_url\":\"https://x/1007.apk\"}"
            + "]}";

    @Test
    public void picksTheHighestNewerVersionCodeAndIgnoresOtherAssets() throws Exception {
        Updates.Candidate c = Updates.pick(new JSONObject(RELEASE), 1000);
        assertEquals(1003, c.versionCode);
        assertEquals("1.2.0-vn", c.version);
        assertEquals(2500000, c.size);
        assertTrue(c.url.endsWith("/cleanyourtv-vn-1003.apk"));
    }

    @Test
    public void nothingWhenTheInstalledBuildIsAsNewOrNewer() throws Exception {
        assertNull(Updates.pick(new JSONObject(RELEASE), 1003));
        assertNull(Updates.pick(new JSONObject(RELEASE), 2000));
        assertEquals(1003, Updates.pick(new JSONObject(RELEASE), 1001).versionCode);
    }

    @Test
    public void nothingFromAnAnswerThatIsNotARelease() throws Exception {
        assertNull(Updates.pick(null, 1000));
        assertNull(Updates.pick(new JSONObject("{\"message\":\"Not Found\"}"), 1000));
        assertNull(Updates.pick(new JSONObject("{\"assets\":[]}"), 1000));
    }

    @Test
    public void oversizedAssetsAreRefused() throws Exception {
        String big = "{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"cleanyourtv-vn-2000.apk\",\"size\":"
                + (Updates.MAX_BYTES + 1) + ",\"browser_download_url\":\"https://x/2000.apk\"}]}";
        assertNull(Updates.pick(new JSONObject(big), 1000));
    }

    @Test
    public void versionCodeComesOnlyFromTheExactAssetName() {
        assertEquals(1000, Updates.versionCodeOf("cleanyourtv-vn-1000.apk"));
        assertEquals(-1, Updates.versionCodeOf("cleanyourtv-vn-.apk"));
        assertEquals(-1, Updates.versionCodeOf("cleanyourtv-vn-1000.apk.sig"));
        assertEquals(-1, Updates.versionCodeOf("other-1000.apk"));
        assertEquals(-1, Updates.versionCodeOf(null));
    }

    @Test
    public void tagLosesItsLeadingV() {
        assertEquals("1.0.0-vn", Updates.versionOfTag("v1.0.0-vn"));
        assertEquals("1.0.0", Updates.versionOfTag("1.0.0"));
        assertEquals("", Updates.versionOfTag(null));
    }

    @Test
    public void signersMustMatchExactly() {
        byte[] a = {1, 2, 3}, b = {4, 5};
        assertTrue(Updates.sameSigners(Collections.singletonList(a), Collections.singletonList(new byte[]{1, 2, 3})));
        assertTrue(Updates.sameSigners(Arrays.asList(a, b), Arrays.asList(b, a)));
        assertFalse(Updates.sameSigners(Collections.singletonList(a), Collections.singletonList(b)));
        assertFalse(Updates.sameSigners(Collections.singletonList(a), Arrays.asList(a, b)));
        assertFalse(Updates.sameSigners(null, Collections.singletonList(a)));
        assertFalse(Updates.sameSigners(Collections.singletonList(a), null));
        assertFalse(Updates.sameSigners(Collections.emptyList(), Collections.emptyList()));
        assertFalse(Updates.sameSigners(Collections.singletonList(new byte[0]), Collections.singletonList(new byte[0])));
    }

    @Test
    public void keyRotationIsAcceptedOnlyThroughASingleSignersHistory() {
        byte[] old = {1, 1}, rotated = {2, 2}, other = {3, 3};
        java.util.List<byte[]> installed = Collections.singletonList(old);
        assertTrue(Updates.trustedSigners(installed, Collections.singletonList(old), null));
        assertTrue(Updates.trustedSigners(installed, Collections.singletonList(rotated), Arrays.asList(old, rotated)));
        assertFalse(Updates.trustedSigners(installed, Collections.singletonList(rotated), Arrays.asList(other, rotated)));
        assertFalse(Updates.trustedSigners(installed, Collections.singletonList(rotated), null));
        // several signers: the exact set or nothing, whatever the history says
        assertFalse(Updates.trustedSigners(installed, Arrays.asList(old, rotated), Arrays.asList(old, rotated)));
        assertFalse(Updates.trustedSigners(Arrays.asList(old, other), Collections.singletonList(rotated),
                Arrays.asList(old, other, rotated)));
        assertTrue(Updates.trustedSigners(Arrays.asList(old, other), Arrays.asList(other, old), null));
        // unreadable
        assertFalse(Updates.trustedSigners(null, Collections.singletonList(old), Collections.singletonList(old)));
        assertFalse(Updates.trustedSigners(installed, null, Collections.singletonList(old)));
        assertFalse(Updates.trustedSigners(Collections.singletonList(new byte[0]), Collections.singletonList(rotated),
                Collections.singletonList(new byte[0])));
    }

    @Test
    public void onlyHttpsToGithubHostsIsFollowed() {
        assertTrue(Updates.allowedUrl("https://github.com/thangvofastboy/cleanyourtv-vn/releases/download/v1/a.apk"));
        assertTrue(Updates.allowedUrl("https://api.github.com/repos/x/y/releases/latest"));
        assertTrue(Updates.allowedUrl("https://objects.githubusercontent.com/github-production-release-asset/1"));
        assertTrue(Updates.allowedUrl("https://release-assets.githubusercontent.com/x?sig=1"));
        assertFalse(Updates.allowedUrl("http://github.com/a.apk"));
        assertFalse(Updates.allowedUrl("https://github.com.evil.example/a.apk"));
        assertFalse(Updates.allowedUrl("https://evil.example/github.com/a.apk"));
        assertFalse(Updates.allowedUrl("https://user@github.com/a.apk"));
        assertFalse(Updates.allowedUrl("https://github.com:8443/a.apk"));
        assertFalse(Updates.allowedUrl(""));
        assertFalse(Updates.allowedUrl(null));
    }

    @Test
    public void anAssetOffGithubIsNotOffered() throws Exception {
        String off = "{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"cleanyourtv-vn-2000.apk\",\"size\":10,"
                + "\"browser_download_url\":\"https://example.com/2000.apk\"}]}";
        assertNull(Updates.pick(new JSONObject(off), 1000));
    }

    @Test
    public void aNullTagNeverShowsAsTheWordNull() throws Exception {
        String asset = "\"assets\":[{\"name\":\"cleanyourtv-vn-2000.apk\",\"size\":10,"
                + "\"browser_download_url\":\"https://github.com/x/2000.apk\"}]";
        assertEquals("1.3", Updates.pick(new JSONObject("{\"tag_name\":null,\"name\":\"1.3\"," + asset + "}"), 1000).version);
        assertEquals("2000", Updates.pick(new JSONObject("{\"tag_name\":null,\"name\":null," + asset + "}"), 1000).version);
        assertEquals("", Updates.string(new JSONObject("{\"a\":null}"), "a"));
        assertEquals("", Updates.string(new JSONObject("{}"), "a"));
    }

    @Test
    public void rateLimitIsA403WithNoRequestsLeftOrA429() {
        assertTrue(Updates.rateLimited(403, "0"));
        assertTrue(Updates.rateLimited(429, null));
        assertFalse(Updates.rateLimited(403, "12"));
        assertFalse(Updates.rateLimited(403, null));
        assertFalse(Updates.rateLimited(500, "0"));
    }

    @Test
    public void confirmPendingLastsUntilTheTimeout() {
        assertFalse(Updates.confirmPending(0, 5_000));
        assertTrue(Updates.confirmPending(1_000, 1_000));
        assertTrue(Updates.confirmPending(1_000, 1_000 + Updates.CONFIRM_TIMEOUT_MS - 1));
        assertFalse(Updates.confirmPending(1_000, 1_000 + Updates.CONFIRM_TIMEOUT_MS));
        // A clock behind the mark (a reboot resets elapsedRealtime) never keeps it pending.
        assertFalse(Updates.confirmPending(10_000, 5_000));
    }

    @Test
    public void staleDownloadMatchesOnlyUpdateApks() {
        assertTrue(Updates.staleDownload("update-123456789.apk"));
        assertTrue(Updates.staleDownload("update-.apk"));
        assertFalse(Updates.staleDownload("update-1.apk.part"));
        assertFalse(Updates.staleDownload("theme-1.apk"));
        assertFalse(Updates.staleDownload("wallpaper.jpg"));
        assertFalse(Updates.staleDownload(null));
    }
}
