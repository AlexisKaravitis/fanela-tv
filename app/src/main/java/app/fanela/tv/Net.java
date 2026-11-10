package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Network with guard rails: 8 s connect / 12 s read / 20 s total per request, 3 MB cap, HTTPS-only redirects,
 * and per-host backoff after failures (15 min doubling to 6 h) so a broken or blocking site is not hammered.
 */
final class Net {
    private static final Map<String, long[]> backoff = new HashMap<>();  // host -> {until, failures}

    /** Skipped because the host is in backoff (not a network error, not the image's fault). */
    static final class Backoff extends IOException { Backoff(String h) { super("backoff " + h); } }
    /** The transfer took longer than the total limit (slow line, not the server's fault). */
    static final class TooSlow extends IOException { TooSlow() { super("too slow"); } }

    /** Forget every backoff: the user is looking at the app, or asked for a refresh. */
    static synchronized void reset() { backoff.clear(); }

    private static volatile Context app;  // for the connectivity check; set by the first image fetch or refresh
    static void init(Context c) { if (app == null) app = c.getApplicationContext(); }

    /** True when the TV has a working internet connection (so a timeout is the server's fault, not ours). */
    @SuppressWarnings("deprecation")
    static boolean online() {
        Context c = app;
        if (c == null) return true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Throwable t) {
            return true;
        }
    }

    static synchronized boolean blocked(String host) {
        long[] b = backoff.get(host);
        return b != null && System.currentTimeMillis() < b[0];
    }

    static synchronized void result(String host, boolean ok) {
        if (ok) { backoff.remove(host); return; }
        long[] b = backoff.get(host);
        long n = b == null ? 1 : b[1] + 1;
        long wait = Math.min(6 * 3600000L, 2 * 60000L * (1L << Math.min(8, n - 1)));  // 2, 4, 8 ... min, max 6 h
        backoff.put(host, new long[]{System.currentTimeMillis() + wait, n});
    }

    static String http(String url) throws IOException {
        return new String(bytes(url, 3 << 20, false), "UTF-8");
    }

    /** Live score polls: never skipped by backoff and never cause it (a goal must not wait for a timer). */
    static String httpLive(String url) throws IOException {
        return new String(bytes(url, 3 << 20, true), "UTF-8");
    }

    static byte[] bytes(String url, int cap) throws IOException { return bytes(url, cap, false); }

    static byte[] bytes(String url, int cap, boolean live) throws IOException {
        URL u = new URL(url);
        String host = live ? "" : u.getHost();
        if (!live && blocked(host)) throw new Backoff(host);
        long deadline = System.currentTimeMillis() + 20000;
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) Fanela/" + BuildConfig.VERSION_NAME);
            c.setRequestProperty("Accept", "application/json, image/*;q=0.9, */*;q=0.5");
            int code = c.getResponseCode();
            if (!"https".equals(c.getURL().getProtocol()) && url.startsWith("https")) throw new IOException("downgrade");
            if (code == 403 || code == 429 || code >= 500) { if (!live) result(host, false); throw new IOException("HTTP " + code); }
            if (code >= 400) throw new IOException("HTTP " + code);  // e.g. 404 for a missing day: not the host's fault
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > cap) throw new IOException("too large");
                    if (System.currentTimeMillis() > deadline) throw new TooSlow();
                }
                if (!live) result(host, true);
                return b.toByteArray();
            }
        } catch (IOException e) {
            // only the host's fault counts (no answer while the TV is online); being offline never blocks a host
            if (e instanceof java.net.SocketTimeoutException && !live && online()) result(host, false);
            throw e;
        } finally {
            c.disconnect();
        }
    }

    // ---------- images (crests, channel logos): downloaded ahead of drawing, never during it ----------
    static File imageFile(Context c, String url) {
        return new File(c.getFilesDir(), "img_" + Integer.toHexString(url.hashCode()) + "_" + url.length() + ".png");
    }

    /** Downloads and validates an image once; a failed URL is retried at most every 6 hours. */
    static void fetchImage(Context c, String url) {
        init(c);
        if (!BuildConfig.REMOTE_IMAGES) return;  // store build: no crests or channel logos (club badges are drawn)
        if (url == null || url.isEmpty() || !url.startsWith("http")) return;
        File f = imageFile(c, url);
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return; }
        File fail = new File(f.getPath() + ".fail");
        if (fail.exists() && System.currentTimeMillis() - fail.lastModified() < 6 * 3600000L) return;
        try {
            byte[] b = bytes(url, 4 << 20);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(b, 0, b.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("not an image");
            Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, sample(b, 384));  // keep it small in memory
            if (bm == null) throw new IOException("decode");
            int side = Math.max(bm.getWidth(), bm.getHeight());
            if (side > 384) {  // still sharp at twice the largest size it is drawn (180 px row logo)
                Bitmap s = Bitmap.createScaledBitmap(bm, bm.getWidth() * 384 / side, bm.getHeight() * 384 / side, true);
                if (s != bm) { bm.recycle(); bm = s; }
            }
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { bm.compress(Bitmap.CompressFormat.PNG, 100, out); }
            bm.recycle();
            if (!tmp.renameTo(f)) throw new IOException("rename");
            fail.delete();
        } catch (Throwable t) {
            // network trouble (offline, slow, backoff) or a cancelled fetch: try again next time, no 6-hour pause
            if (Thread.currentThread().isInterrupted() || t instanceof java.io.InterruptedIOException || t instanceof Backoff
                || t instanceof TooSlow || t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketException || t instanceof javax.net.ssl.SSLException) return;
            try { new FileOutputStream(fail).close(); fail.setLastModified(System.currentTimeMillis()); } catch (IOException ignored) { }
        }
    }

    private static BitmapFactory.Options sample(byte[] b, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2;
        BitmapFactory.Options r = new BitmapFactory.Options();
        r.inSampleSize = s;
        return r;
    }

    /** Only reads the local cache (drawing never waits for the network). */
    static Bitmap image(Context c, String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("res:")) {  // a logo that ships with the app
            int id = c.getResources().getIdentifier(url.substring(4), "drawable", c.getPackageName());
            return id == 0 ? null : BitmapFactory.decodeResource(c.getResources(), id);
        }
        File f = imageFile(c, url);
        if (!f.exists()) return null;
        Bitmap b = BitmapFactory.decodeFile(f.getPath());
        if (b == null) f.delete();  // a broken cached file is downloaded again next time
        return b;
    }

    /** Removes images not used for 90 days and old failure markers. */
    static void cleanImages(Context c) {
        File[] fs = c.getFilesDir().listFiles();
        if (fs == null) return;
        long now = System.currentTimeMillis();
        for (File f : fs) {
            String n = f.getName();
            if ((n.startsWith("img_") || n.startsWith("crest_")) && now - f.lastModified() > 90L * 86400000L) f.delete();
        }
    }
}
