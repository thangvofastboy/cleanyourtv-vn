package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This fork's in-app update, the pure part: which asset of the latest GitHub release is an update, and whether a
 * downloaded APK is signed like the installed app. {@link UpdateClient} does the network and the install.
 * A release is tagged {@code v<versionName>} and carries {@code cleanyourtv-vn-<versionCode>.apk}.
 */
final class Updates {
    static final String REPO = "thangvofastboy/cleanyourtv-vn";
    static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String PACKAGE = "com.gathin.tvhome";
    /** Larger than any launcher build will be; a bigger asset is refused before it is downloaded. */
    static final long MAX_BYTES = 20L * 1024 * 1024;

    /** The only hosts the updater talks to: the API, release pages and where GitHub redirects downloads. */
    private static final List<String> HOSTS = Arrays.asList("github.com", "api.github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com");

    private static final Pattern ASSET = Pattern.compile("cleanyourtv-vn-(\\d{1,9})\\.apk");

    private Updates() {}

    /** The update to offer: the release's name, and the APK asset with the highest version code. */
    static final class Candidate {
        final String version;
        final String url;
        final long size;
        final long versionCode;

        Candidate(String version, String url, long size, long versionCode) {
            this.version = version;
            this.url = url;
            this.size = size;
            this.versionCode = versionCode;
        }
    }

    /** The version code in an asset name, or -1 when the name is not {@code cleanyourtv-vn-<code>.apk}. */
    static long versionCodeOf(String assetName) {
        if (assetName == null) return -1;
        Matcher m = ASSET.matcher(assetName.trim());
        return m.matches() ? Long.parseLong(m.group(1)) : -1;
    }

    /** "v1.2.0-vn" as "1.2.0-vn"; the tag as it is when it has no leading v. */
    static String versionOfTag(String tag) {
        String t = tag == null ? "" : tag.trim();
        return t.length() > 1 && (t.charAt(0) == 'v' || t.charAt(0) == 'V') ? t.substring(1) : t;
    }

    /**
     * The asset of the GitHub "latest release" answer that updates {@code currentCode}: the highest version code
     * above it, with an https download URL and a size between 1 byte and {@link #MAX_BYTES}. Null when the release
     * holds none (the TV is up to date) or the answer is not a release.
     */
    static Candidate pick(JSONObject release, long currentCode) {
        if (release == null) return null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        Candidate best = null;
        String version = versionOfTag(string(release, "tag_name"));
        if (version.isEmpty()) version = string(release, "name").trim();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            long code = versionCodeOf(string(a, "name"));
            String url = string(a, "browser_download_url");
            long size = a.optLong("size", 0);
            if (code <= currentCode || !allowedUrl(url) || size <= 0 || size > MAX_BYTES) continue;
            if (best == null || code > best.versionCode) {
                best = new Candidate(version.isEmpty() ? String.valueOf(code) : version, url, size, code);
            }
        }
        return best;
    }

    /** A JSON string field, "" when it is missing or JSON null (Android's optString gives "null" for that). */
    static String string(JSONObject o, String key) {
        return o == null || !o.has(key) || o.isNull(key) ? "" : o.optString(key, "");
    }

    /** https to one of {@link #HOSTS}; checked for the asset URL and again for every redirect. */
    static boolean allowedUrl(String url) {
        try {
            URI u = new URI(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null
                    && (u.getPort() == -1 || u.getPort() == 443)
                    && HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    /** GitHub's answer when the API's hourly quota for this address is used up. */
    static boolean rateLimited(int code, String remaining) {
        return code == 429 || (code == 403 && "0".equals(remaining == null ? null : remaining.trim()));
    }

    /** True when both apps carry the same signing certificates; an empty or missing side is never a match. */
    static boolean sameSigners(List<byte[]> installed, List<byte[]> update) {
        if (installed == null || update == null || installed.isEmpty() || installed.size() != update.size()) {
            return false;
        }
        outer:
        for (byte[] a : installed) {
            if (a == null || a.length == 0) return false;
            for (byte[] b : update) if (Arrays.equals(a, b)) continue outer;
            return false;
        }
        return true;
    }

    /**
     * Whether an update signed by {@code update} may replace an app signed by {@code installed}. The same set of
     * certificates always may. A single-signer update whose rotation history ({@code updateHistory}, oldest first,
     * from SigningInfo; null when unknown) holds the installed app's only certificate may too: that is Android's
     * own key rotation. Anything with several signers needs the exact set, and anything unreadable is refused.
     */
    static boolean trustedSigners(List<byte[]> installed, List<byte[]> update, List<byte[]> updateHistory) {
        if (sameSigners(installed, update)) return true;
        if (installed == null || installed.size() != 1 || update == null || update.size() != 1
                || updateHistory == null) {
            return false;
        }
        byte[] current = installed.get(0);
        if (current == null || current.length == 0) return false;
        for (byte[] h : updateHistory) if (Arrays.equals(current, h)) return true;
        return false;
    }

    /** How long a committed install may wait on Android's confirm screen before another press may start over. */
    static final long CONFIRM_TIMEOUT_MS = 5 * 60_000L;

    /** A commit made at {@code since} (0: none) still waits for its final status at {@code now}. */
    static boolean confirmPending(long since, long now) {
        return since != 0 && now >= since && now - since < CONFIRM_TIMEOUT_MS;
    }

    /** A download file left in the cache by an earlier run that died before deleting it (createTempFile names). */
    static boolean staleDownload(String name) {
        return name != null && name.startsWith("update-") && name.endsWith(".apk");
    }
}
