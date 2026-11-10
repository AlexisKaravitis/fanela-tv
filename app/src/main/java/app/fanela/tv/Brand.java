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

    /** From an activity's onStop: once no Fanela screen is left, the tile switches (after a moment, in case one opens). */
    static void hidden(Context c) {
        if (visible > 0) visible--;
        final Context app = c.getApplicationContext();
        MAIN.postDelayed(new Runnable() { public void run() { applyIfIdle(app); } }, 1500);
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
