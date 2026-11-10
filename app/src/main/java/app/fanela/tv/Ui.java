package app.fanela.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Shared look for the app's screens: 10-foot type sizes, colours, rounded surfaces and the focus treatment. */
final class Ui {
    static final int BG = Color.rgb(17, 19, 24), SURFACE = Color.rgb(35, 38, 46);
    static final int TEXT = Color.WHITE, SUB = Color.rgb(166, 172, 184);
    /** Greece blue: the app's own colour until a club is followed. */
    static final int GREECE = Color.rgb(13, 94, 175);
    /** Accent colours, taken from the first club the user follows (theme()). */
    static volatile int ACCENT = Color.rgb(74, 144, 226), ON_ACCENT = Color.WHITE, SURFACE_ON = Color.rgb(28, 44, 70);
    /** The header logo's shirt in the favourite club's kit (Logo); 0 = the original Greece-blue logo. */
    static volatile int LOGO_FILL, LOGO_TRIM;

    /** Everything the theme changes, to tell when a screen must be rebuilt. */
    static String key() { return ACCENT + ":" + LOGO_FILL + ":" + LOGO_TRIM; }
    static final int INK = Color.rgb(17, 17, 17), AMBER = Color.rgb(245, 166, 35);
    /** TV overscan-safe margins (Android TV design guidelines: 48 dp at the sides, 27 dp at the top and bottom). */
    static final int SIDE = 48, TOP = 27;

    private Ui() { }

    // ---------- type: Google Sans (SIL Open Font License, res/raw/ofl_google_sans.txt), as on Google TV ----------
    static volatile Typeface REGULAR = Typeface.DEFAULT, MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL),
        BOLD = Typeface.DEFAULT_BOLD;
    private static volatile boolean fontsLoaded;

    /** Loads the app's font once (any thread). Missing characters fall back to the system font. */
    static void fonts(Context c) {
        if (fontsLoaded) return;
        try {
            android.content.res.Resources r = c.getResources();
            REGULAR = r.getFont(R.font.google_sans_regular);
            MEDIUM = r.getFont(R.font.google_sans_medium);
            BOLD = r.getFont(R.font.google_sans_bold);
        } catch (Throwable ignored) { }  // the system font then
        fontsLoaded = true;
    }

    /**
     * Picks the accent from the favourite club's colours (Teams.favourite: the one chosen in Settings, else the first
     * followed; Greece blue when none), brightened just enough to stand out on the dark background; black kits (PAOK,
     * OFI) use their white. The header logo's shirt takes the same club's kit (Logo).
     */
    static void theme(Context c) {
        int fill = GREECE, logoFill = 0, logoTrim = 0;
        String fav = Teams.favourite(c);
        if (fav != null) {
            Teams.Club k = Teams.get(c, fav);
            if (k != null) {
                int[] col = Badges.colors(k.name);
                fill = Color.luminance(col[0]) < 0.03f ? col[1] : col[0];
                int[] shirt = Logo.shirt(col, null);
                logoFill = 0xFF000000 | shirt[0];
                logoTrim = 0xFF000000 | shirt[1];
            }
        }
        LOGO_FILL = logoFill;
        LOGO_TRIM = logoTrim;
        // the TV's app tile follows later (Brand.hidden / applyIfIdle): switching it while a Fanela screen is open closes it
        int a = fill;
        for (int i = 0; i < 10 && Color.luminance(a) < 0.16f; i++) a = mix(a, Color.WHITE, 0.15f);
        ACCENT = a;
        ON_ACCENT = Color.luminance(a) > 0.45f ? INK : Color.WHITE;
        // the "followed" surface: a quiet tint of the club colour (bright kits tint less, so yellow never turns olive)
        float lum = Color.luminance(fill);
        SURFACE_ON = lum > 0.35f ? Color.rgb(48, 52, 62) : mix(SURFACE, fill, 0.22f);  // bright kits: neutral tile, coloured outline
    }

    /**
     * Back handling on Android 13+ (predictive back: onKeyDown no longer sees BACK there). Registers the action while
     * it is non-null, unregisters it when null. Pass the returned token back in next time. Older versions use onKeyDown.
     */
    static Object onBack(android.app.Activity a, Object token, final Runnable action) {
        if (android.os.Build.VERSION.SDK_INT < 33) return null;
        android.window.OnBackInvokedDispatcher d = a.getOnBackInvokedDispatcher();
        if (token != null) d.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) token);
        if (action == null) return null;
        android.window.OnBackInvokedCallback cb = new android.window.OnBackInvokedCallback() { public void onBackInvoked() { action.run(); } };
        d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        return cb;
    }

    static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        fonts(c);
        t.setTypeface(bold ? MEDIUM : REGULAR);
        return t;
    }

    static GradientDrawable rounded(Context c, int color, float radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        if (strokeColor != 0) g.setStroke(dp(c, 3), strokeColor);
        return g;
    }

    static int lighten(int c) {
        return Color.rgb(Math.min(255, Color.red(c) + 18), Math.min(255, Color.green(c) + 18), Math.min(255, Color.blue(c) + 18));
    }

    /** Surface for a control: lighter with a white outline when focused, a green outline when "on". */
    static void paint(View v, boolean focused, boolean on) {
        int base = on ? SURFACE_ON : SURFACE;
        v.setBackground(rounded(v.getContext(), focused ? lighten(base) : base, 14, focused ? Color.WHITE : on ? ACCENT : 0));
    }

    /** Makes a view a focusable control: grows slightly and lifts when focused. on[0] is read on every focus change. */
    static void focusable(final View v, final float scale, final boolean[] on) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);  // some TV boxes report a touchscreen: focus must still work
        v.setClickable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) {
                x.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(120).start();
                paint(x, has, on[0]);
                x.setElevation(has ? dp(x.getContext(), 8) : 0);
            }
        });
        paint(v, false, on[0]);
    }

    /** A button-like pill. */
    static TextView button(Context c, String label) {
        final TextView b = text(c, label, 16, TEXT, true);
        int h = dp(c, 22), v = dp(c, 10);
        b.setPadding(h, v, h, v);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setClickable(true);
        b.setBackground(rounded(c, SURFACE, 24, 0));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) {
                ((TextView) x).setTextColor(has ? INK : TEXT);
                x.setBackground(rounded(x.getContext(), has ? Color.WHITE : SURFACE, 24, 0));
                x.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
            }
        });
        return b;
    }
}
