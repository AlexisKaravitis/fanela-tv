package app.fanela.tv;

import android.graphics.Bitmap;

/**
 * A crest as the club shows it: some feeds' files are line art on a transparent background where the real emblem is
 * filled white inside (ESPN's Olympiacos 435.png: red lines, so on Olympiacos's red field it read red on red, QA3G-5).
 * The transparent regions the crest's outline encloses (not reached from the image's border) are filled white, the
 * line's inner edge blended over the white so it stays smooth. Only when they are a real part of the emblem (a quarter
 * of its bounding box or more): a crest with letter counters or a small gap is left exactly as it is. The app's crests
 * use it (AppArt.crest); the faint watermark on the field (KitField) stays the line art. Plain Java on ARGB ints.
 */
final class CrestFill {
    private CrestFill() { }

    /** The alpha from which a pixel is part of the crest's line (the outline that encloses). */
    static final int LINE = 128;
    /** The enclosed transparent area, as a share of the crest's alpha bounding box, from which it is filled. */
    static final double MIN_SHARE = 0.25;
    static final int WHITE = 0xFFFFFFFF;

    /** The share of the crest's bounding box that is transparent and enclosed by its outline (0 when nothing is). */
    static double enclosedShare(int[] px, int w, int h) {
        boolean[] outside = outside(px, w, h);
        int l = w, t = h, r = -1, b = -1, n = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, a = px[i] >>> 24;
            if (a > 16) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y; }
            if (a < LINE && !outside[i]) n++;
        }
        if (r < 0) return 0;
        return n / (double) ((r - l + 1) * (b - t + 1));
    }

    /**
     * Fills px in place (non-premultiplied ARGB, as Bitmap.getPixels gives) when its enclosed transparent area is
     * MIN_SHARE of the bounding box or more: enclosed pixels and the line's semi-transparent inner edge are composited
     * over white (opaque); the outer edge, touching the outside, keeps its alpha. True when it filled.
     */
    static boolean fill(int[] px, int w, int h) {
        if (enclosedShare(px, w, h) < MIN_SHARE) return false;
        boolean[] outside = outside(px, w, h);
        boolean[] enclosed = new boolean[w * h];
        for (int i = 0; i < px.length; i++) enclosed[i] = !outside[i] && (px[i] >>> 24) < LINE;
        int[] out = px.clone();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, a = px[i] >>> 24;
            boolean under = enclosed[i];
            if (!under && a < 255 && !outside[i]) under = touches(enclosed, w, h, x, y) && !touches(outside, w, h, x, y);
            if (under) out[i] = overWhite(px[i]);
        }
        System.arraycopy(out, 0, px, 0, px.length);
        return true;
    }

    /** A pixel composited over white: opaque. */
    static int overWhite(int c) {
        int a = c >>> 24;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = (r * a + 255 * (255 - a) + 127) / 255;
        g = (g * a + 255 * (255 - a) + 127) / 255;
        b = (b * a + 255 * (255 - a) + 127) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The transparent pixels (alpha under LINE) reached from the image's border, 4-connected (a diagonal never leaks). */
    static boolean[] outside(int[] px, int w, int h) {
        boolean[] out = new boolean[w * h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) { tail = seed(px, out, queue, tail, x); tail = seed(px, out, queue, tail, (h - 1) * w + x); }
        for (int y = 0; y < h; y++) { tail = seed(px, out, queue, tail, y * w); tail = seed(px, out, queue, tail, y * w + w - 1); }
        while (head < tail) {
            int i = queue[head++], x = i % w, y = i / w;
            if (x > 0) tail = seed(px, out, queue, tail, i - 1);
            if (x < w - 1) tail = seed(px, out, queue, tail, i + 1);
            if (y > 0) tail = seed(px, out, queue, tail, i - w);
            if (y < h - 1) tail = seed(px, out, queue, tail, i + w);
        }
        return out;
    }

    private static int seed(int[] px, boolean[] out, int[] queue, int tail, int i) {
        if (out[i] || (px[i] >>> 24) >= LINE) return tail;
        out[i] = true;
        queue[tail] = i;
        return tail + 1;
    }

    private static boolean touches(boolean[] m, int w, int h, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int xx = x + dx, yy = y + dy;
            if ((dx != 0 || dy != 0) && xx >= 0 && yy >= 0 && xx < w && yy < h && m[yy * w + xx]) return true;
        }
        return false;
    }

    /** The crest bitmap filled (a new bitmap; src is left alone), or src itself when it needs no fill. */
    static Bitmap filled(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        if (!fill(px, w, h)) return src;
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }
}
