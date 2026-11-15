package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This fork's in-app update, the Android part: one GitHub API request, the APK download into the cache, its
 * checks (package, version code, signing certificate) and the hand-over to Android's PackageInstaller, which
 * asks the owner to confirm; the answer arrives at {@link UpdateStatusReceiver}. Everything here runs on a
 * worker thread, and only ever after a press in Launcher settings.
 */
final class UpdateClient {
    private static final int TIMEOUT_MS = 10_000;
    /** The whole download, however slowly the bytes trickle in. */
    private static final long DOWNLOAD_DEADLINE_MS = 5 * 60_000L;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "cleanyourtv-vn-updater";

    /** One check, download or install at a time in this process, whichever activity started it. */
    static final AtomicBoolean BUSY = new AtomicBoolean();

    /**
     * SystemClock.elapsedRealtime() of the last commit still waiting for its final status (0: none). Set before the
     * commit, cleared by UpdateStatusReceiver on the final status; a press meanwhile must not start a second session
     * while Android's confirm screen is up. Updates#confirmPending lets it lapse after a few minutes.
     */
    static final AtomicLong CONFIRM_SINCE = new AtomicLong();

    static boolean confirmPending() {
        return Updates.confirmPending(CONFIRM_SINCE.get(), SystemClock.elapsedRealtime());
    }

    /** A failure with a message resource the settings row can show. */
    static final class Failure extends Exception {
        final int message;

        Failure(int message) {
            super(null, null, false, false);
            this.message = message;
        }
    }

    interface Progress {
        void percent(int percent);
    }

    /** A downloaded and verified APK, and the SHA-256 of exactly the bytes that were checked. */
    static final class Download {
        final File file;
        final byte[] sha256;

        Download(File file, byte[] sha256) {
            this.file = file;
            this.sha256 = sha256;
        }
    }

    private UpdateClient() {}

    /** The update the latest release offers, or null when the installed build is the newest. */
    static Updates.Candidate check() throws Failure {
        HttpURLConnection c = null;
        try {
            c = connect(Updates.LATEST_URL, "application/vnd.github+json", System.currentTimeMillis() + 30_000L);
            int code = c.getResponseCode();
            if (code == 404) return null;   // no release published yet
            if (Updates.rateLimited(code, c.getHeaderField("X-RateLimit-Remaining"))) {
                throw new Failure(R.string.update_err_rate_limit);
            }
            if (code != 200) throw new Failure(R.string.update_err_network);
            try (InputStream in = c.getInputStream()) {
                byte[] body = readCapped(in, 1024 * 1024);
                return Updates.pick(new JSONObject(new String(body, StandardCharsets.UTF_8)), BuildConfig.VERSION_CODE);
            }
        } catch (Failure f) {
            throw f;
        } catch (Exception e) {
            throw new Failure(R.string.update_err_network);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Downloads the candidate to a temporary file of its own in the cache, hashing it as it arrives, and checks it.
     * The file is deleted on any failure; on success the caller owns it.
     */
    static Download download(Context ctx, Updates.Candidate candidate, Progress progress) throws Failure {
        File file = null;
        HttpURLConnection c = null;
        boolean ok = false;
        long deadline = System.currentTimeMillis() + DOWNLOAD_DEADLINE_MS;
        try {
            File cache = ctx.getCacheDir();
            // BUSY allows one download per process and the install session keeps its own copy, so any update-*.apk
            // still here was left by a run that died before deleting it.
            File[] stale = cache.listFiles((dir, name) -> Updates.staleDownload(name));
            if (stale != null) for (File f : stale) f.delete();
            file = File.createTempFile("update-", ".apk", cache);
            c = connect(candidate.url, null, deadline);
            int code = c.getResponseCode();
            if (Updates.rateLimited(code, c.getHeaderField("X-RateLimit-Remaining"))) {
                throw new Failure(R.string.update_err_rate_limit);
            }
            if (code != 200) throw new Failure(R.string.update_err_network);
            long expected = candidate.size;
            long declared = c.getContentLengthLong();
            if (declared > 0 && declared != expected) throw new Failure(R.string.update_err_bad);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    if (System.currentTimeMillis() > deadline) throw new Failure(R.string.update_err_network);
                    total += n;
                    if (total > expected) throw new Failure(R.string.update_err_bad);
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    int pct = (int) (total * 100 / expected);
                    if (pct != last) progress.percent(last = pct);
                }
                if (total != expected) throw new Failure(R.string.update_err_bad);
                out.getFD().sync();
            }
            byte[] digest = sha.digest();
            verify(ctx, file);
            // The file the package manager just read is still the one downloaded; install() streams it again and
            // refuses it unless the bytes still hash to this.
            if (!Arrays.equals(digest, sha256(file))) throw new Failure(R.string.update_err_bad);
            ok = true;
            return new Download(file, digest);
        } catch (Failure f) {
            throw f;
        } catch (Exception e) {
            throw new Failure(R.string.update_err_network);
        } finally {
            if (c != null) c.disconnect();
            if (!ok && file != null) file.delete();
        }
    }

    /** The APK is this launcher, newer than the one running, and signed with a key it trusts. */
    private static void verify(Context ctx, File apk) throws Failure {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo update = archive(pm, apk.getPath());
        if (update == null || !Updates.PACKAGE.equals(update.packageName) || !ctx.getPackageName().equals(update.packageName)) {
            throw new Failure(R.string.update_err_bad);
        }
        if (versionCode(update) <= BuildConfig.VERSION_CODE) throw new Failure(R.string.update_err_bad);
        PackageInfo installed;
        try {
            installed = installed(pm, ctx.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new Failure(R.string.update_err_bad);
        }
        if (!Updates.trustedSigners(signers(installed), signers(update), history(update))) {
            throw new Failure(R.string.update_err_signature);
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo archive(PackageManager pm, String path) {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageArchiveInfo(path, flags);
        // Some builds give no signing info for an archive with the new flag; the old one still reads it, without a
        // rotation history, so only the exact certificates are accepted then.
        if (info != null && Build.VERSION.SDK_INT >= 28 && info.signingInfo == null) {
            info = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES);
        }
        return info;
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo installed(PackageManager pm, String pkg) throws PackageManager.NameNotFoundException {
        return pm.getPackageInfo(pkg, Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES);
    }

    /** The certificates that sign the APK now, from signingInfo or the old signatures field; null if neither. */
    @SuppressWarnings("deprecation")
    private static List<byte[]> signers(PackageInfo info) {
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            sigs = info.signingInfo.getApkContentsSigners();
        }
        if (sigs == null || sigs.length == 0) sigs = info.signatures;
        return bytes(sigs);
    }

    /** A single signer's rotation history (oldest first), or null: several signers, API 27, or not read. */
    private static List<byte[]> history(PackageInfo info) {
        if (Build.VERSION.SDK_INT < 28 || info.signingInfo == null) return null;
        SigningInfo si = info.signingInfo;
        if (si.hasMultipleSigners()) return null;
        return bytes(si.getSigningCertificateHistory());
    }

    private static List<byte[]> bytes(Signature[] sigs) {
        if (sigs == null || sigs.length == 0) return null;
        List<byte[]> out = new ArrayList<>();
        for (Signature s : sigs) out.add(s.toByteArray());
        return out;
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    /**
     * Streams the verified APK into a PackageInstaller session, checking that the bytes still hash to what was
     * verified, then commits it; Android asks the owner to confirm and the result goes to
     * {@link UpdateStatusReceiver}. Worker thread only. The file stays the caller's to delete.
     */
    static void install(Context ctx, Download apk) throws Failure {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        long size = apk.file.length();
        params.setSize(size);
        int id = -1;
        try {
            id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new FileInputStream(apk.file);
                     OutputStream out = session.openWrite("update.apk", 0, size)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        sha.update(buf, 0, n);
                    }
                    session.fsync(out);
                }
                if (!Arrays.equals(apk.sha256, sha.digest())) throw new Failure(R.string.update_err_bad);
                Intent status = new Intent(ctx, UpdateStatusReceiver.class);
                // Mutable: PackageInstaller fills in the status extras.
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pending = PendingIntent.getBroadcast(ctx, 7, status, flags);
                CONFIRM_SINCE.set(Math.max(1, SystemClock.elapsedRealtime()));
                session.commit(pending.getIntentSender());
                id = -1;
            }
        } catch (Exception e) {
            CONFIRM_SINCE.set(0);
            if (id != -1) {
                try {
                    installer.abandonSession(id);
                } catch (RuntimeException ignored) {
                }
            }
            throw e instanceof Failure ? (Failure) e : new Failure(R.string.update_err_install);
        }
    }

    private static byte[] sha256(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) sha.update(buf, 0, n);
        }
        return sha.digest();
    }

    /**
     * Opens {@code url}, following redirects by hand so every hop is checked against {@link Updates#allowedUrl}
     * (https, GitHub hosts only). The caller disconnects the returned connection.
     */
    private static HttpURLConnection connect(String url, String accept, long deadline) throws IOException, Failure {
        String next = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!Updates.allowedUrl(next)) throw new Failure(R.string.update_err_bad);
            if (System.currentTimeMillis() > deadline) throw new Failure(R.string.update_err_network);
            HttpURLConnection c = (HttpURLConnection) new URL(next).openConnection();
            boolean handedOver = false;
            try {
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setInstanceFollowRedirects(false);
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent", USER_AGENT);
                if (accept != null) c.setRequestProperty("Accept", accept);
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new Failure(R.string.update_err_network);
                    next = new URL(new URL(next), location).toString();
                    continue;
                }
                handedOver = true;
                return c;
            } finally {
                if (!handedOver) c.disconnect();
            }
        }
        throw new Failure(R.string.update_err_network);
    }

    private static byte[] readCapped(InputStream in, int cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > cap) throw new IOException("answer too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
