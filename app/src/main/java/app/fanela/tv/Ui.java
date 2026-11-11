package app.fanela.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Shared look for the app's screens. The 1.2 design system (SPEC-APP 1) comes first: colour roles, type scale,
 * spacing, radii, focus and motion, all in spec px on the 1920x1080 canvas (scale() turns them into screen px, so the
 * screen never follows the system font size). The 1.1 tokens below it still dress Settings until it is redesigned.
 */
final class Ui {
    // ---------- 1.2 design system: colour roles ----------
    static final int APP_BG = FieldColors.BG, S1 = 0xFF151619, S2 = 0xFF1E1F24, S3 = 0xFF292B31;
    /** Text: never pure white. TXT2 / TXT3 are its 72 % and 50 % opacities. */
    static final int TXT = 0xFFF1F1F1;
    static final float A2 = 0.72f, A3 = 0.5f;
    /** Row dividers (only on a dark surface, never as an outline); tonal fills of tiles and the where-to-watch cell. */
    static final int STROKE = 0x1AFFFFFF, TONAL = 0x12FFFFFF, TONAL_HI = 0x24FFFFFF;
    static final int LIVE = 0xFFE8102A, LIVE_TEXT = 0xFFFF5C5C;
    /** Outcome pills: the cards' own colours (CardKit), so the app and the home screen say WIN / DRAW / LOSS alike. */
    static final int WIN = CardKit.WIN, DRAW = CardKit.DRAW, LOSS = CardKit.LOSS, LOSS_TEXT = CardKit.LOSS_TEXT;

    // ---------- type scale (px) ----------
    static final float T_DISPLAY_XL = 224, T_DISPLAY = 200, T_SCORE_L = 190, T_SCORE_STRIP = 150, T_TITLE = 80,
        T_TITLE_MIN = 60, T_STRIP_NAME = 72, T_REL = 58, T_SCORE_M = 58, T_SCORE_S = 34, T_ROW_TIME = 34, T_BODY = 30,
        T_META = 28, T_META_S = 26, T_EYEBROW = 26, T_LABEL = 24, T_MIN = 24;
    /** Caps tracking: eyebrows and labels +14 %, LIKELY +12 %, outcome pills +6 %. */
    static final float TRACK_EYEBROW = 0.14f, TRACK_LIKELY = 0.12f, TRACK_PILL = 0.06f;
    /**
     * Vertical metrics of the two faces as the reference renders lay them out (CSS line boxes): Google Sans uses its
     * typo metrics, Roboto Condensed its Windows ones. Baselines computed from these land where the spec's do.
     */
    static final float GS_ASC = 0.966f, GS_DESC = 0.286f, RC_ASC = 1.048f, RC_DESC = 0.271f;

    // ---------- spacing, radii ----------
    static final float GRID = 8, SAFE_X = 96, SAFE_Y = 54, W = 1920, H = 1080;
    static final float R_HERO = 28, R_TILE = 16, R_ROW = 12, R_CELL = 12, R_STRIP = 18;

    // ---------- focus ----------
    /** A 4 px ring outside the shape, a 10 px white 6 % glow, a 7.5 % tonal fill and one soft shadow. */
    static final float RING = 4, GLOW = 10;
    static final int RING_COLOR = TXT, GLOW_COLOR = 0x0FFFFFFF, FOCUS_FILL = 0x13FFFFFF;
    static final float SCALE_CARD = 1.05f, SCALE_TILE = 1.04f;

    // ---------- motion (ms) ----------
    static final int FOCUS_MS = 150, SCROLL_MS = 200, SWITCH_MS = 300, SWAP_MS = 120, WATCH_MS = 160, PULSE_MS = 1200,
        SCORE_MS = 180, FLASH_MS = 600, SKELETON_MS = 1200, PUSH_MS = 220;
    static final float PUSH_RISE = 16;
    static final android.view.animation.Interpolator FAST_OUT_SLOW_IN = new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f);
    static final android.view.animation.Interpolator DECELERATE = new android.view.animation.DecelerateInterpolator(1.5f);

    /** Screen px per spec px: the canvas is 1920 wide whatever the TV's density. */
    static float scale(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.max(m.widthPixels, m.heightPixels) / W;
    }

    /** Spec px to screen px. */
    static int px(Context c, float v) { return Math.round(v * scale(c)); }

    /** A text paint in spec px: anti-aliased, tabular figures. */
    static android.graphics.Paint paint(Typeface face, float size, int color) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        p.setFontFeatureSettings("tnum");
        return p;
    }

    static boolean condensed(Typeface f) { return f == COND || f == COND_BOLD; }

    /** The baseline of one line centred on cy (a CSS line box of normal height, as in a flex row). */
    static float baseline(Typeface face, float size, float cy) {
        float a = condensed(face) ? RC_ASC : GS_ASC, d = condensed(face) ? RC_DESC : GS_DESC;
        return cy - (a + d) * size / 2 + a * size;
    }

    /** The baseline of a line box starting at top with the given line height (lineHeight 0: normal). */
    static float baselineTop(Typeface face, float size, float top, float lineHeight) {
        float a = condensed(face) ? RC_ASC : GS_ASC, d = condensed(face) ? RC_DESC : GS_DESC;
        float lh = lineHeight <= 0 ? (a + d) * size : lineHeight;
        return top + (lh - (a + d) * size) / 2 + a * size;
    }

    /** The normal line height of a face at a size. */
    static float lineHeight(Typeface face, float size) {
        return (condensed(face) ? RC_ASC + RC_DESC : GS_ASC + GS_DESC) * size;
    }

    /** A colour at an opacity (0..1) of its own alpha. */
    static int alpha(int color, float a) {
        return (Math.round(((color >>> 24) & 0xFF) * a) << 24) | (color & 0xFFFFFF);
    }

    // ---------- 1.1 tokens (Settings, the match panel) ----------
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
    /** Roboto Condensed (Apache 2.0, bundled so every TV draws the cards alike): names and numerals on the cards. */
    static volatile Typeface COND = Typeface.create("sans-serif-condensed", Typeface.NORMAL),
        COND_BOLD = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static volatile boolean fontsLoaded;

    /** Loads the app's font once (any thread). Missing characters fall back to the system font. */
    static void fonts(Context c) {
        if (fontsLoaded) return;
        try {
            android.content.res.Resources r = c.getResources();
            REGULAR = r.getFont(R.font.google_sans_regular);
            MEDIUM = r.getFont(R.font.google_sans_medium);
            BOLD = r.getFont(R.font.google_sans_bold);
            COND = r.getFont(R.font.roboto_condensed_regular);
            COND_BOLD = r.getFont(R.font.roboto_condensed_bold);
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
                if (!Badges.known(k.name) && k.color != null && k.color.matches("[0-9a-fA-F]{6}")) {  // the feed's own club colour
                    int espn = Color.parseColor("#" + k.color);
                    fill = Color.luminance(espn) < 0.03f ? Color.WHITE : espn;
                }
                int[] shirt = Logo.shirt(Badges.known(k.name) ? col : null, k.color);
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

    /** The one focus motion: cards and club tiles grow to CARD, pills and chips to PILL, full-width rows not at all. */
    static final float CARD = 1.08f, PILL = 1.04f, ROW = 1f;
    private static final android.view.animation.Interpolator EASE = new android.view.animation.DecelerateInterpolator();

    /** Grows (150 ms, decelerating) and lifts (8 dp) a view as it gains focus, and back as it loses it. */
    static void focusMotion(View v, boolean has, float scale) {
        v.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(150).setInterpolator(EASE).start();
        v.setElevation(has ? dp(v.getContext(), 8) : 0);
    }

    /** Makes a view a focusable control: grows slightly and lifts when focused. on[0] is read on every focus change. */
    static void focusable(final View v, final float scale, final boolean[] on) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);  // some TV boxes report a touchscreen: focus must still work
        v.setClickable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) {
                focusMotion(x, has, scale);
                paint(x, has, on[0]);
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
                focusMotion(x, has, PILL);
            }
        });
        return b;
    }
}
