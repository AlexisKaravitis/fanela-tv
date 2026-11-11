package app.fanela.tv;

/**
 * The club-colour tokens of the app screens (SPEC-APP 1.2), solved once per club: the kit colour as a small mark, the
 * kit field behind the hero (falling to near-black), and the ink that reads on it. Plain Java on 0xAARRGGBB ints, so
 * the rules are unit-tested; the colour maths is ClubTokens'.
 */
final class FieldColors {
    /** The app background: near-black, never #000. */
    static final int BG = 0xFF0B0B0D;
    /** Ink on dark and on light fields. */
    static final int INK_LIGHT = 0xFFF4F0EA, INK_DARK = 0xFF141416;

    /** The kit's main colour as-is (rail marker, brand shirt, nothing large). */
    int primary;
    /** The field gradient: field at the top left, field2 at 70 %, field3 at the far corner. */
    int field, field2, field3;
    /** Text on the field. */
    int ink;
    /**
     * A white or very light kit; a black kit (charcoal field). White kits get a dark warm platter lit by a warm light,
     * as their cards do (ClubTokens WHITE: field #131316, light #CFCAC0): a near-full-width ivory field would turn the
     * app into a glaring light theme in a dark room. So every field is dark and every ink is light.
     */
    boolean white, black;
    /** The hero's eyebrow colour (alpha included): EYEBROW, or EYEBROW_LIFT on a field that can't carry it. */
    int eyebrow = EYEBROW;
    /** The stripes' colour (alpha included): ink at 5.5 %. */
    int stripe;
    /**
     * The warm light over a white kit's platter (the cards' "light from my side"): its colour and its strength at the
     * centre and the shoulder; 0 for every other kit (their field is their colour already).
     */
    int glow;
    float glowA, glowB;
    /** White kits: the platter and its light (ClubTokens.LIGHT_WHITE at .22 / .09, the cards' .33 / .13 for a wider area). */
    static final int WHITE_FIELD = 0xFF34322E, WHITE_FIELD2 = 0xFF23221F, WHITE_FIELD3 = 0xFF1A1917;
    static final float WHITE_GLOW_A = 0.22f, WHITE_GLOW_B = 0.09f;

    /**
     * The hero's eyebrow on the field (SPEC-APP 3.2: the text colour at 80 %), and the lifted eyebrow a bright kit gets
     * when its field, darkened as far as it may be, still doesn't give the 80 % eyebrow 4.5:1 (P2a).
     */
    static final int TXT = 0xFFF1F1F1, EYEBROW = 0xCCF1F1F1, EYEBROW_LIFT = 0xFFF2F0E8;
    /** The eyebrow's contrast target (WCAG AA) and the most a bright kit's field is darkened (its value x 0.6). */
    static final double EYEBROW_MIN = 4.5, DARKEST = 0.6;

    static FieldColors of(int kit, int trim) {
        FieldColors f = new FieldColors();
        int k = ClubTokens.opaque(kit);
        f.primary = k;
        double lum = ClubTokens.luminance(k), sat = ClubTokens.saturation(k);
        if (lum < 0.08 && sat < 0.35) {  // black kits (PAOK): charcoal, still a field
            f.black = true;
            f.field = 0xFF2A2A2E; f.field2 = 0xFF121214; f.field3 = 0xFF0E0E10;
        } else if (lum > 0.6 && sat < 0.25) {  // white kits (Real Madrid): a dark warm platter, lit warm
            f.white = true;
            f.field = WHITE_FIELD; f.field2 = WHITE_FIELD2; f.field3 = WHITE_FIELD3;
            f.glow = ClubTokens.LIGHT_WHITE; f.glowA = WHITE_GLOW_A; f.glowB = WHITE_GLOW_B;
        } else {
            // yellow and other bright kits (AEK, Aris, Dortmund): darker, so off-white text reads; the usual case:
            // deepened for a TV (Olympiacos #E2001A -> #9C0F1C -> #5A0A10). A kit still too light (a yellow reaches only
            // 3.1:1 at 60 %) is darkened further, its hue kept, only until the hero's eyebrow (the text colour at 80 %,
            // the faintest text on the field) reads at 4.5:1 on the field's top colour, and no further (P2a: Dortmund
            // #FDE100 -> #665A0E, #554A0C at the hero's middle). A kit that still doesn't get there at the darkest the
            // field may go keeps that and has its eyebrow lifted to #F2F0E8 instead.
            double v1 = lum > 0.35 ? 0.6 : 0.7, v2 = lum > 0.35 ? 0.36 : 0.4, v3 = lum > 0.35 ? 0.22 : 0.26, d = 1;
            while (d > DARKEST + 1e-9 && eyebrowContrast(hsv(k, 0.9, v1 * d), EYEBROW) < EYEBROW_MIN) d -= 0.005;
            f.field = hsv(k, 0.9, v1 * d); f.field2 = hsv(k, 0.9, v2 * d); f.field3 = hsv(k, 0.9, v3 * d);
        }
        if (eyebrowContrast(f.field, EYEBROW) < EYEBROW_MIN) f.eyebrow = EYEBROW_LIFT;
        f.ink = INK_LIGHT;
        f.stripe = 0x0EFFFFFF;
        return f;
    }

    /** The WCAG contrast of a text colour (alpha included: composited over the field first) on a field colour. */
    static double eyebrowContrast(int field, int text) {
        int a = text >>> 24;
        int over = ClubTokens.mix(field, text | 0xFF000000, a / 255.0);
        return wcag(over, field);
    }

    /** The WCAG 2 contrast ratio of two colours (relative luminance of linearised sRGB, unlike ClubTokens' card maths). */
    static double wcag(int a, int b) {
        double la = relative(a) + 0.05, lb = relative(b) + 0.05;
        return Math.max(la, lb) / Math.min(la, lb);
    }

    static double relative(int c) {
        double[] v = {ClubTokens.r(c) / 255.0, ClubTokens.g(c) / 255.0, ClubTokens.b(c) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** The colour with its saturation and value scaled (hue kept). */
    static int hsv(int c, double satScale, double valScale) {
        double r = ClubTokens.r(c) / 255.0, g = ClubTokens.g(c) / 255.0, b = ClubTokens.b(c) / 255.0;
        double mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        if (mx <= 0) return ClubTokens.rgb(0, 0, 0);
        double s = (mx - mn) / mx, v = mx * valScale, s2 = s * satScale;
        // each channel keeps its place between the min and the max
        double[] in = {r, g, b}, out = new double[3];
        for (int i = 0; i < 3; i++) {
            double t = mx == mn ? 1 : (in[i] - mn) / (mx - mn);
            double lo = v * (1 - s2);
            out[i] = lo + (v - lo) * t;
        }
        return ClubTokens.rgb((int) Math.round(out[0] * 255), (int) Math.round(out[1] * 255), (int) Math.round(out[2] * 255));
    }

    /** The colour the field shows at a height (spec px), over bg: the gradient's left edge under the fall to near-black. */
    int at(float y) {
        float t = Math.max(0, Math.min(1, y / 680f));
        int c = t < 0.7f ? ClubTokens.mix(field, field2, t / 0.7f) : ClubTokens.mix(field2, field3, (t - 0.7f) / 0.3f);
        return ClubTokens.mix(c, BG, fall(y));
    }

    /** The fall's alpha at a height: 0 above 360, .55 at 508, 1 from 690. */
    static float fall(float y) {
        if (y <= 360) return 0;
        if (y >= 690) return 1;
        float t = (y - 360) / 330f;
        return t < 0.45f ? 0.55f * t / 0.45f : 0.55f + 0.45f * (t - 0.45f) / 0.55f;
    }

    /** The text colour that reads at a height: the ink while the field is light, else the app's text colour. */
    int inkAt(float y) {
        return ClubTokens.luminance(at(y)) > 0.3 ? INK_DARK : 0xFFF1F1F1;
    }

    /** Whether a height of the screen still shows a light field (rings and tonal fills turn dark there). */
    boolean lightAt(float y) { return ClubTokens.luminance(at(y)) > 0.3; }
}
