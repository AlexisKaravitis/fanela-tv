package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * The Fanela shirt in the favourite club's colours: the header logo (res/drawable-nodpi/wordmark.png) and the logo of
 * the home-screen rows (shirt.png), both drawn by design/make_brand.py in Greece blue with white trim. The shirt takes
 * the club's kit colour, its collar and cuffs the club's second colour. A dark trim (AEK's black) uses the *_inset art,
 * where the trim is set in from the edge so the sleeve keeps its outline on the dark background. The "Fanela" text is
 * left as it is. With no club followed the header is the original Greece-blue logo, untouched.
 */
final class Logo {
    /** The shirt as drawn: Greece blue, with white trim. */
    static final int BLUE = 0x0D5EAF;
    /** The shirt's right edge in wordmark.png (474 x 120 px); the text starts further right. */
    static final int SHIRT_RIGHT = 130;

    private static Bitmap cache;
    private static int cacheFill, cacheTrim;

    private Logo() { }

    /** The logo for the current theme (Ui.theme). The bitmap is shared: never recycle it. */
    static synchronized Bitmap wordmark(Context c) {
        int fill = Ui.LOGO_FILL, trim = Ui.LOGO_TRIM;
        if (cache != null && fill == cacheFill && trim == cacheTrim) return cache;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeResource(c.getResources(), fill != 0 && insetTrim(trim) ? R.drawable.wordmark_inset : R.drawable.wordmark, o);
        if (b == null || fill == 0) { cache = b; cacheFill = fill; cacheTrim = trim; return b; }  // nobody followed: as designed
        Bitmap m = b.copy(Bitmap.Config.ARGB_8888, true);
        b.recycle();
        paint(m, Math.min(SHIRT_RIGHT, m.getWidth()), fill, trim, 2);
        cache = m; cacheFill = fill; cacheTrim = trim;
        return m;
    }

    /** Recolours the shirt in the left w columns of a mutable bitmap; a faint kit gets an edge passes px wide. */
    private static void paint(Bitmap m, int w, int fill, int trim, int passes) {
        int h = m.getHeight();
        int[] px = new int[w * h];
        m.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = recolor(px[i], fill, trim);
        if (faint(fill)) outline(px, w, h, trim, passes);  // a black or navy kit on the dark background: an edge in its trim colour
        m.setPixels(px, 0, w, 0, 0, w, h);
    }

    /**
     * The logo of the home-screen rows: the shirt on a flat square in a dark tint of the kit (the launcher masks it to a
     * circle). Opaque, 320 px (80 dp at 4x). Greece blue when no club is followed.
     */
    static Bitmap rowLogo(Context c) {
        int fill = Ui.LOGO_FILL == 0 ? BLUE : Ui.LOGO_FILL & 0xFFFFFF, trim = Ui.LOGO_FILL == 0 ? 0xFFFFFF : Ui.LOGO_TRIM & 0xFFFFFF;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inMutable = true;
        Bitmap s = BitmapFactory.decodeResource(c.getResources(), insetTrim(trim) ? R.drawable.shirt_inset : R.drawable.shirt, o);
        Bitmap out = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        cv.drawColor(0xFF000000 | background(fill));
        if (s == null) return out;
        paint(s, s.getWidth(), fill, trim, Math.max(2, Math.round(s.getWidth() * 0.012f)));
        int size = 210, top = (320 - size) / 2 - 6;  // the shirt fills about 56% of the width, raised a little to look centred
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(s, null, new android.graphics.Rect((320 - size) / 2, top, (320 + size) / 2, top + size), p);
        s.recycle();
        return out;
    }

    /** What the row logo depends on (it is redrawn when this changes). */
    static String rowLogoTag() { return "shirt:" + Integer.toHexString(Ui.LOGO_FILL) + ":" + Integer.toHexString(Ui.LOGO_TRIM); }

    /** The dark square behind the row logo: the page background tinted with the kit (design/make_brand.py kit_background). */
    static int background(int shirt) {
        int bg = Ui.BG & 0xFFFFFF;
        if (faint(shirt)) return 0x282A32;
        int base = luminance(shirt) < 0.8 ? shirt : 0x2B3990;
        return mix(bg, base, 0.32f);
    }

    /** WCAG contrast ratio of two colours (RGB). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** A shirt colour that nearly disappears on the dark background (black, navy, maroon): it gets an edge. */
    static boolean faint(int shirt) { return contrast(shirt & 0xFFFFFF, Ui.BG & 0xFFFFFF) < 2; }

    /** A trim that doesn't stand out on the dark background (black, navy): the art with the trim set in. */
    static boolean insetTrim(int trim) { return contrast(trim & 0xFFFFFF, Ui.BG & 0xFFFFFF) < 3; }

    /**
     * One pixel of the shirt: the drawn colour is a mix of blue (the shirt) and white (the trim), so the same mix of the
     * club's two colours keeps the edges smooth. Transparent pixels stay transparent. ARGB in, ARGB out.
     */
    static int recolor(int argb, int fill, int trim) {
        int a = argb >>> 24;
        if (a == 0) return argb;
        int r = (argb >> 16) & 0xFF;
        float t = Math.max(0f, Math.min(1f, (r - ((BLUE >> 16) & 0xFF)) / (float) (255 - ((BLUE >> 16) & 0xFF))));
        return (a << 24) | mix(fill & 0xFFFFFF, trim & 0xFFFFFF, t);
    }

    /** An edge around the shirt in the trim colour, strong then soft at its outside (PAOK: a black shirt edged in white). */
    private static void outline(int[] px, int w, int h, int trim, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            int[] src = px.clone();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((src[i] >>> 24) > 100) continue;
                boolean near = (x > 0 && (src[i - 1] >>> 24) > 180) || (x < w - 1 && (src[i + 1] >>> 24) > 180)
                    || (y > 0 && (src[i - w] >>> 24) > 180) || (y < h - 1 && (src[i + w] >>> 24) > 180);
                if (near) px[i] = (pass < passes - 1 ? 0xD0 : 0x50) << 24 | (trim & 0xFFFFFF);
            }
        }
    }

    /**
     * The club's shirt: {kit colour, trim colour} as RGB. badge: Badges' {fill, text} when it knows the club, else null;
     * espn: the feed's club colour ("e20520") for the rest, with white or near-black trim, whichever shows on it.
     */
    static int[] shirt(int[] badge, String espn) {
        if (badge != null) return new int[]{badge[0] & 0xFFFFFF, badge[1] & 0xFFFFFF};
        int fill = espn != null && espn.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(espn, 16) : BLUE;
        return new int[]{fill, luminance(fill) > 0.45 ? 0x111111 : 0xFFFFFF};
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Relative luminance (sRGB), 0 black to 1 white. */
    static double luminance(int rgb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = ((rgb >> (16 - 8 * i)) & 0xFF) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
