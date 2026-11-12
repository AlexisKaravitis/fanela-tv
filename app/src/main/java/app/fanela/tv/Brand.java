package app.fanela.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * The app's icon and TV banner in the favourite club's kit colour. Android can't redraw an app icon, so the manifest
 * has one launcher entry per kit colour (activity-alias .Launch, .Launch_red ...; the art is drawn by
 * design/make_brand.py --kits) and exactly one is enabled: the one closest to the favourite club's shirt. Blue (.Launch,
 * the original icon) when no club is followed.
 *
 * Switching makes the TV's launcher see a new app tile: on some launchers Fanela then moves to the end of the apps row,
 * or leaves the favourites row once. So it only switches when the colour really changes.
 *
 * Android removes the tasks started from a launcher entry when that entry is disabled. The entries open LaunchActivity,
 * which starts the main screen in a task of its own, so a switch can't close the app; it still waits until no Fanela
 * screen is open (hidden, boot, the home-screen rows job), so the tile never changes under the user's eyes.
 */
final class Brand {
    /** Fanela screens currently started (main thread writes; the background job reads). */
    private static volatile int visible;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** From an activity's onStart. */
    static void shown() { visible++; }

    /** A Fanela screen is started (in front, or about to be). */
    static boolean open() { return visible > 0; }

    /** From an activity's onStop: once no Fanela screen is left, the tile switches (after a moment, in case one opens). */
    static void hidden(Context c) {
        if (visible > 0) visible--;
        final Context app = c.getApplicationContext();
        MAIN.postDelayed(new Runnable() { public void run() { applyIfIdle(app); } }, 1500);
        try { HomeRows.leftFront(app); } catch (Throwable ignored) { }  // retired rows go once the launcher is in front
    }

    /** Switches the tile now if no Fanela screen is open; otherwise it waits for hidden(). */
    static void applyIfIdle(Context c) {
        if (visible > 0) return;
        Ui.theme(c);
        apply(c);
    }

    /** The kits, in the same order as design/make_brand.py KITS. "" is the original blue. */
    static final String[] NAMES = {"", "red", "maroon", "orange", "yellow", "green", "sky", "navy", "purple", "black", "white"};
    static final int[] COLORS = {Logo.BLUE, 0xD0102E, 0x7A1F2B, 0xF47A20, 0xF5C800, 0x0E8A3E, 0x6CABDD, 0x1E3A8A, 0x6B3E99,
        0x111111, 0xF4F4F4};

    private Brand() { }

    /** The kit closest to a shirt colour (RGB): black and white kits by their lightness, the rest by colour distance. */
    static String kit(int shirt) {
        double l = Logo.luminance(shirt);
        if (l < 0.03) return "black";
        if (l > 0.75) return "white";
        String best = "";
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < NAMES.length; i++) {
            if ("black".equals(NAMES[i]) || "white".equals(NAMES[i])) continue;
            double d = distance(shirt, COLORS[i]);
            if (d < bestD) { bestD = d; best = NAMES[i]; }
        }
        return best;
    }

    /** "Redmean" colour distance: close to how different two colours look, cheap to compute. */
    static double distance(int a, int b) {
        int r1 = (a >> 16) & 0xFF, g1 = (a >> 8) & 0xFF, b1 = a & 0xFF, r2 = (b >> 16) & 0xFF, g2 = (b >> 8) & 0xFF, b2 = b & 0xFF;
        double rm = (r1 + r2) / 2.0, dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
        return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db);
    }

    static String alias(String kit) { return "app.fanela.tv.Launch" + (kit.isEmpty() ? "" : "_" + kit); }

    /** The kit for the current theme (Ui.theme sets the logo's shirt colour). */
    static String wanted() { return Ui.LOGO_FILL == 0 ? "" : kit(Ui.LOGO_FILL & 0xFFFFFF); }

    /** Enables the launcher entry for the favourite club's kit and disables the others, only if that changes anything. */
    static void apply(Context c) { apply(c, wanted()); }

    /** One switch at a time: two at once could each disable the other's entry and leave none. */
    static synchronized void apply(Context c, String want) {
        try {
            PackageManager pm = c.getPackageManager();
            ComponentName target = new ComponentName(c, alias(want));
            if (enabled(pm, target, want) && onlyOne(pm, c, want)) return;  // already right: touch nothing
            pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String k : NAMES) {
                if (k.equals(want)) continue;
                ComponentName other = new ComponentName(c, alias(k));
                if (enabled(pm, other, k))
                    pm.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        } catch (Exception ignored) { }  // never in the way of the app itself
    }

    // ---------- each club's row logo (FB-3) ----------

    /** The disc behind a row logo: the cards' dark (the launcher masks the square to a circle). */
    static final int ROW_DISC = 0xFF1B2024;
    /** The row logo's size: the launcher shows it at 120 px on a 1080p TV, focused or not. */
    static final int ROW_LOGO = 160;

    /**
     * A club's row logo: the Fanela shirt in the club's painted kit (ClubTokens.painted: the colour the cards paint it
     * in), collar and cuffs in its second colour, on the dark disc. Each row then says whose it is at a glance, where
     * every row used to carry the favourite club's shirt. {fill, trim} as ARGB.
     */
    static int[] rowShirt(ClubTokens t) {
        int fill = t.kitA, trim = t.kitB;
        // a second colour that doesn't show on the shirt (a one-colour kit): white or ink, whichever stands out
        if (ClubTokens.contrast(fill, trim) < 1.6) trim = ClubTokens.luminance(fill) > 0.4 ? 0xFF141416 : 0xFFF2F2F4;
        return new int[]{fill, trim};
    }

    /** What a club's row logo depends on (it is redrawn when this changes: the club's colours, the art). */
    static String rowLogoTag(ClubTokens t) {
        int[] c = rowShirt(t);
        return "club2:" + Integer.toHexString(c[0]) + ":" + Integer.toHexString(c[1]);
    }

    /** Draws a club's row logo (ROW_LOGO square, opaque). */
    static android.graphics.Bitmap rowLogo(Context c, ClubTokens t) {
        int[] col = rowShirt(t);
        int fill = col[0], trim = col[1];
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(ROW_LOGO, ROW_LOGO, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        cv.drawColor(ROW_DISC);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inScaled = false;
        o.inMutable = true;
        // a trim that doesn't show on the dark disc (black, navy): the art with the trim set in from the edge
        boolean inset = ClubTokens.contrast(trim, ROW_DISC) < 3;
        android.graphics.Bitmap s = android.graphics.BitmapFactory.decodeResource(c.getResources(), inset ? R.drawable.shirt_inset : R.drawable.shirt, o);
        if (s == null) return out;
        int w = s.getWidth(), h = s.getHeight();
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = Logo.recolor(px[i], fill & 0xFFFFFF, trim & 0xFFFFFF);
        // a shirt that nearly disappears on the disc (a black kit): an edge in its trim colour
        if (ClubTokens.contrast(fill, ROW_DISC) < 2) edge(px, w, h, trim, Math.max(2, Math.round(w * 0.014f)));
        s.setPixels(px, 0, w, 0, 0, w, h);
        int size = Math.round(ROW_LOGO * 0.66f), top = (ROW_LOGO - size) / 2 - 3;  // raised a little to look centred
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(s, null, new android.graphics.Rect((ROW_LOGO - size) / 2, top, (ROW_LOGO + size) / 2, top + size), p);
        s.recycle();
        return out;
    }

    /** An edge passes px wide around the shirt in the trim colour, strong then soft at its outside. */
    static void edge(int[] px, int w, int h, int trim, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            int[] src = px.clone();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((src[i] >>> 24) > 100) continue;
                boolean near = (x > 0 && (src[i - 1] >>> 24) > 180) || (x < w - 1 && (src[i + 1] >>> 24) > 180)
                    || (y > 0 && (src[i - w] >>> 24) > 180) || (y < h - 1 && (src[i + w] >>> 24) > 180);
                if (near) px[i] = (pass < passes - 1 ? 0xE0 : 0x60) << 24 | (trim & 0xFFFFFF);
            }
        }
    }

    private static boolean onlyOne(PackageManager pm, Context c, String want) {
        for (String k : NAMES) if (!k.equals(want) && enabled(pm, new ComponentName(c, alias(k)), k)) return false;
        return true;
    }

    /** As the TV sees it: the manifest enables only the original (.Launch) until the app says otherwise. */
    private static boolean enabled(PackageManager pm, ComponentName cn, String kit) {
        int s = pm.getComponentEnabledSetting(cn);
        return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && kit.isEmpty());
    }
}
