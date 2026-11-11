package app.fanela.tv;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The followed club's colours for the cards, solved once so every style follows the same rules (spec:
 * design/spec/cards/SPEC-CARDS.md §2, model.py tokens()). Colours are 0xAARRGGBB ints; the maths is plain Java so it
 * is unit-tested without a device. Luminance is the spec's: 0.2126 R + 0.7152 G + 0.0722 B on the sRGB bytes / 255.
 */
final class ClubTokens {
    enum Kit { CHROMATIC, WHITE, BLACK }
    enum Pattern { SOLID, STRIPES, HOOPS, SASH, HALVES }

    Kit kit;
    int prim, sec;            // the kit as the club wears it: main and second colour
    int kitA, kitB;           // the kit colours as painted on TV (Kit stripes, Ticket edge)
    int field;                // Signature / Clean platter
    int light;                // the light from my side (Signature, Clean)
    float lightA, lightB;     // its strength at the centre and the shoulder
    int stock, stockInk, edge;// Ticket
    Pattern pattern = Pattern.SOLID;
    /**
     * My crest on this match (crest: its analysis, for the disc rule; null without one) and the club's best crest for
     * the ghost (ghost: the larger of the match's crest and the club's own, see CardArt.ghostCrest): whether it can be
     * drawn huge (Signature's ghost) and at which alpha.
     */
    boolean sharpCrest;
    float ghostAlpha = 0.45f;
    CrestInfo crest, ghost;

    // ---------- fixed tokens (spec §2) ----------
    static final int FIELD = 0xFF131316, FIELD_BLACK = 0xFF101114, KIT_WHITE = 0xFFDAD6CE, KIT_BLACK = 0xFF17171A,
        LIGHT_WHITE = 0xFFCFCAC0, LIGHT_BLACK = 0xFFB9C0CC, NEUTRAL_OPP = 0xFF6E7580, STOCK_BLACK = 0xFF1E2126,
        INK = 0xFF0E1013, IVORY = 0xFFF3F1EC, IVORY_KIT = 0xFFEDEAE3, IVORY_PLATE = 0xFFEBE7E0, IVORY_TICKET = 0xFFE8E4DC,
        CLASH_OPP = 0xFF8A9099, LAUNCHER_BG = 0xFF1B2024;

    /** The sharp-crest test: the alpha bounding box's longer side. The feeds' crests are 500 px; on screen the ghost is
     *  ~400 px, so 300 px is sharp enough (the spec's renders used the same threshold). */
    static final int SHARP = 300;

    /** Kit patterns by feed id (home kits, 2026-27), only where checked; everyone else is solid. */
    private static final Map<String, Pattern> PATTERNS = new HashMap<>();
    static {
        PATTERNS.put("435", Pattern.STRIPES);   // Olympiacos: red and white stripes
        PATTERNS.put("605", Pattern.STRIPES);   // PAOK: black and white stripes
        PATTERNS.put("1010", Pattern.STRIPES);  // OFI: black and white
    }

    static ClubTokens of(String clubId, int prim, int sec, CrestInfo crest) { return of(clubId, prim, sec, crest, crest); }

    /** crest: my crest on this match (the disc rule); ghost: the club's best crest (sharp test, ghost alpha). */
    static ClubTokens of(String clubId, int prim, int sec, CrestInfo crest, CrestInfo ghost) {
        ClubTokens t = new ClubTokens();
        t.prim = opaque(prim); t.sec = opaque(sec);
        t.kit = kitClass(t.prim);
        t.kitA = painted(t.prim); t.kitB = painted(t.sec);
        switch (t.kit) {
            case BLACK: t.field = FIELD_BLACK; t.light = LIGHT_BLACK; t.lightA = 0.14f; t.lightB = 0.06f; break;
            case WHITE: t.field = FIELD; t.light = LIGHT_WHITE; t.lightA = 0.33f; t.lightB = 0.13f; break;
            default: t.field = FIELD; t.light = t.prim; t.lightA = 0.70f; t.lightB = 0.35f;
        }
        if (t.kit == Kit.BLACK) { t.stock = STOCK_BLACK; t.stockInk = 0xFFFFFFFF; t.edge = painted(t.sec); }
        else if (t.kit == Kit.WHITE) {
            t.stock = lum(t.sec) < 0.35 ? mix(t.sec, 0xFF000000, 0.24) : STOCK_BLACK;
            t.stockInk = 0xFFFFFFFF; t.edge = KIT_WHITE;
        } else {
            t.stock = mix(t.prim, 0xFF000000, 0.24);
            t.stockInk = lum(t.stock) < 0.45 ? 0xFFFFFFFF : 0xFF141416;
            t.edge = painted(t.sec);
        }
        Pattern p = PATTERNS.get(clubId);
        t.pattern = p == null ? Pattern.SOLID : p;
        t.crest = crest;
        t.ghost = ghost;
        if (ghost != null) {
            t.sharpCrest = ghost.size >= SHARP;
            t.ghostAlpha = ghostAlpha(ghost.lum);
        }
        return t;
    }

    /**
     * Signature's ghost alpha for a crest's mean luminance: .20 / lum within .22–.60, so a bright crest is drawn
     * fainter and every ghost adds about the same light. It was .30 / lum within .30–.60 while the ghost was the bare
     * outline; drawn as the club prints it (CrestFill: Olympiacos white inside, lum ~.65) that put a pink disc at .46
     * behind the VS mark and the score (owner, fx6). Now ~.31 for Olympiacos, ~.37 for PAOK; a dark crest keeps .60.
     */
    static float ghostAlpha(double lum) {
        return (float) Math.max(GHOST_MIN, Math.min(GHOST_MAX, GHOST_LIGHT / Math.max(lum, 0.01)));
    }

    static final double GHOST_LIGHT = 0.20, GHOST_MIN = 0.22, GHOST_MAX = 0.60;

    /**
     * Whether the club's own crest makes the better ghost than the match's: it is larger (alpha bounding box). A tie,
     * or no club crest, keeps the match's (the sport's own design). The EuroLeague feed's Olympiacos crest is 192 px
     * while the club's ESPN crest is 500: the ghost (which stands for the club) uses the ESPN one.
     */
    static boolean clubCrestIsBetter(CrestInfo match, CrestInfo club) {
        if (club == null) return false;
        return match == null || club.size > match.size;
    }

    /** Both kits chromatic and their hues within 28° (Olympiacos v Crvena Zvezda): only Clean paints the opponent. */
    boolean clashes(int oppPrim) {
        return kit == Kit.CHROMATIC && kitClass(oppPrim) == Kit.CHROMATIC && hueDist(hue(prim), hue(oppPrim)) < 28;
    }

    // ---------- crest analysis (spec §1.7) ----------
    static final class CrestInfo {
        int size;          // longer side of the alpha bounding box
        double lum;        // mean luminance of opaque pixels (alpha > 128)
        double satFrac;    // share of opaque pixels with HSV S > .35 and V > .2
        double hue = -1;   // median hue of those pixels; -1: achromatic crest
        /** "chromatic" | "light" | "dark". */
        String dominant = "dark";
    }

    /** Analyses a crest's ARGB pixels once (when the crest is cached). */
    static CrestInfo analyse(int[] px, int w, int h) {
        CrestInfo c = new CrestInfo();
        int l = w, t = h, r = -1, b = -1, n = 0;
        double s = 0;
        double[] hues = new double[px.length];
        int nh = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = px[y * w + x], a = p >>> 24;
            if (a > 16) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y; }
            if (a > 128) {
                n++;
                s += lum(p);
                double[] hsv = hsv(p);
                if (hsv[1] > 0.35 && hsv[2] > 0.2) hues[nh++] = hsv[0];
            }
        }
        c.size = r < 0 ? 0 : Math.max(r - l + 1, b - t + 1);
        c.lum = n == 0 ? 0 : s / n;
        c.satFrac = n == 0 ? 0 : nh / (double) n;
        if (c.satFrac >= 0.25) {
            double[] hs = Arrays.copyOf(hues, nh);
            Arrays.sort(hs);
            c.hue = nh % 2 == 1 ? hs[nh / 2] : (hs[nh / 2 - 1] + hs[nh / 2]) / 2;
            c.dominant = "chromatic";
        } else c.dominant = c.lum >= 0.5 ? "light" : "dark";
        return c;
    }

    /** The crest goes on an ivory disc when its dominant colour matches the field it sits on. */
    static boolean needsDisc(CrestInfo c, int field) {
        if (c == null) return true;
        double fl = lum(field), fs = hsv(field)[1];
        if ("chromatic".equals(c.dominant)) return fs > 0.30 && hueDist(c.hue, hue(field)) < 28;
        if ("dark".equals(c.dominant)) return fl < 0.20;
        return fl > 0.60;
    }

    // ---------- colour maths ----------
    /**
     * Black below luminance .12; white when light and unsaturated (above .80 with saturation under .35: white, cream,
     * pale yellow; or above .60 with saturation under .12: greys); else chromatic. A light but saturated colour is
     * the club's colour: Aris #FFD200 (luminance .80), Dortmund #FDE100, Villarreal and Fenerbahçe yellows stay yellow.
     */
    static Kit kitClass(int c) {
        double l = lum(c), s = hsv(c)[1];
        if (l < 0.12) return Kit.BLACK;
        if ((l > 0.80 && s < 0.35) || (s < 0.12 && l > 0.60)) return Kit.WHITE;
        return Kit.CHROMATIC;
    }

    /** A kit colour as painted on TV: deepened 14 %, white as warm grey, black as lifted charcoal. */
    static int painted(int c) {
        Kit k = kitClass(c);
        return k == Kit.WHITE ? KIT_WHITE : k == Kit.BLACK ? KIT_BLACK : mix(c, 0xFF000000, 0.14);
    }

    static int opaque(int c) { return 0xFF000000 | (c & 0xFFFFFF); }
    static int r(int c) { return (c >> 16) & 0xFF; }
    static int g(int c) { return (c >> 8) & 0xFF; }
    static int b(int c) { return c & 0xFF; }
    static int rgb(long r, long g, long b) { return 0xFF000000 | ((int) clamp(r) << 16) | ((int) clamp(g) << 8) | (int) clamp(b); }
    private static long clamp(long v) { return Math.max(0, Math.min(255, v)); }

    /** The spec's luminance: weighted sRGB bytes / 255, no gamma. */
    static double lum(int c) { return (0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)) / 255.0; }

    /** Relative luminance with sRGB gamma (WCAG): used by the app screens' contrast decisions (FieldColors). */
    static double luminance(int c) { return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c)); }
    private static double lin(int v) { double s = v / 255.0; return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4); }

    /** HSV saturation (0–1). */
    static double saturation(int c) { return hsv(c)[1]; }

    /** WCAG contrast ratio of two colours. */
    static double contrast(int a, int b) {
        double la = luminance(a) + 0.05, lb = luminance(b) + 0.05;
        return Math.max(la, lb) / Math.min(la, lb);
    }

    /** HSV: hue in degrees (0–360), saturation and value 0–1. */
    static double[] hsv(int c) {
        double rr = r(c) / 255.0, gg = g(c) / 255.0, bb = b(c) / 255.0;
        double mx = Math.max(rr, Math.max(gg, bb)), mn = Math.min(rr, Math.min(gg, bb)), d = mx - mn;
        double h = 0;
        if (d > 0) {
            if (mx == rr) h = ((gg - bb) / d) % 6; else if (mx == gg) h = (bb - rr) / d + 2; else h = (rr - gg) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new double[]{h, mx == 0 ? 0 : d / mx, mx};
    }

    static double hue(int c) { return hsv(c)[0]; }
    static double hueDist(double a, double b) { double d = Math.abs(a - b) % 360; return Math.min(d, 360 - d); }

    /** a blended toward b by t (linear RGB bytes). */
    static int mix(int a, int b, double t) {
        return rgb(Math.round(r(a) + (r(b) - r(a)) * t), Math.round(g(a) + (g(b) - g(a)) * t), Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** a colour at an alpha (0–1). */
    static int alpha(int c, double a) { return ((int) Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF); }
}
