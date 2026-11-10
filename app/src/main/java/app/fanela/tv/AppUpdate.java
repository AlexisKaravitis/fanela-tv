package app.fanela.tv;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Updates for the sideloaded APK. Fanela checks the latest GitHub release of BuildConfig.UPDATE_REPO (at most twice a
 * day), and when it is newer, Settings offers to install it: the APK is downloaded, checked to be signed with the same
 * key as this app, and handed to Android's installer (the user confirms). With no repository configured, nothing runs.
 */
final class AppUpdate {
    static final class Release { String version, apkUrl, notes; }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("update", Context.MODE_PRIVATE); }

    static boolean enabled() { return !BuildConfig.UPDATE_REPO.isEmpty(); }

    /** The newer release found by the last check, or null. Cheap: no network. */
    static Release available(Context c) {
        if (!enabled()) return null;
        SharedPreferences sp = prefs(c);
        String v = sp.getString("version", null), url = sp.getString("apk", null);
        if (v == null || url == null || compare(v, BuildConfig.VERSION_NAME) <= 0) return null;
        Release r = new Release();
        r.version = v; r.apkUrl = url; r.notes = sp.getString("notes", "");
        return r;
    }

    /** Asks GitHub for the latest release, at most every 12 hours. Background thread. */
    static void check(Context c) {
        if (!enabled()) return;
        SharedPreferences sp = prefs(c);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("checkedAt", 0) < 12 * 3600000L) return;
        sp.edit().putLong("checkedAt", now).apply();
        try {
            JSONObject rel = new JSONObject(Net.http("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest"));
            String tag = rel.optString("tag_name", "").replaceFirst("^[vV]", "");
            JSONArray assets = rel.optJSONArray("assets");
            String apk = null;
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break; }
            }
            if (tag.isEmpty() || apk == null || !apk.startsWith("https://")) return;
            String notes = rel.optString("body", "");
            sp.edit().putString("version", tag).putString("apk", apk).putString("notes", notes.length() > 600 ? notes.substring(0, 600) : notes).apply();
        } catch (Exception ignored) { }
    }

    /** 1.10.0 > 1.9.2; non-numeric parts count as 0. */
    static int compare(String a, String b) {
        String[] x = a.split("[.-]"), y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Match.toInt(x[i]) : 0, q = i < y.length ? Match.toInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    /**
     * Downloads the release and starts the system installer. Returns an error key for I18n, or null when the
     * installer was started. Background thread.
     */
    static String install(Context c, Release r) {
        File f = new File(c.getCacheDir(), "update.apk");
        final int[] session = {-1};
        try {
            byte[] apk = Net.bytes(r.apkUrl, 40 << 20, true);
            try (OutputStream o = new FileOutputStream(f)) { o.write(apk); }
            if (!sameSigner(c, f)) { f.delete(); return "update_bad_sig"; }
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            int id = pi.createSession(p);
            session[0] = id;
            try (PackageInstaller.Session s = pi.openSession(id);
                 InputStream in = new FileInputStream(f);
                 OutputStream out = s.openWrite("fanela.apk", 0, f.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
                out.close();
                Intent done = new Intent(c, Result.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                s.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
            }
            return null;
        } catch (Exception e) {
            f.delete();
            if (session[0] != -1) { try { c.getPackageManager().getPackageInstaller().abandonSession(session[0]); } catch (Exception ignored) { } }
            return "update_failed";
        }
    }

    /** The downloaded APK must be signed by the same key as the running app (no one else can push an "update"). */
    @SuppressWarnings("deprecation")
    static boolean sameSigner(Context c, File apk) {
        PackageManager pm = c.getPackageManager();
        try {
            // Android 9 reads an archive's certificates only with GET_SIGNATURES, so ask for both everywhere
            int flag = PackageManager.GET_SIGNATURES | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : 0);
            PackageInfo mine = pm.getPackageInfo(c.getPackageName(), flag), theirs = pm.getPackageArchiveInfo(apk.getPath(), flag);
            if (theirs == null || !c.getPackageName().equals(theirs.packageName)) return false;
            Signature[] a = signers(mine), b = signers(theirs);
            return a != null && b != null && a.length > 0 && Arrays.equals(a, b);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signers(PackageInfo p) {
        if (Build.VERSION.SDK_INT >= 28 && p.signingInfo != null) return p.signingInfo.getApkContentsSigners();
        return p.signatures;
    }

    /** Installer callbacks: shows Android's confirmation screen when it asks for one. */
    public static class Result extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    try { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                }
            }
        }
    }
}
