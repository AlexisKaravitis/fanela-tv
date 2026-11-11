package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.LruCache;

/**
 * The app screens' images, prepared once off the UI thread and cached: crests with their rim light and contact
 * shadow baked in (no blur is ever drawn per frame), line-art crests filled white inside as their clubs show them
 * (CrestFill), channel logos cropped to their opaque bounds, and the brand
 * shirt in a colour. Nothing here is recycled while cached: a bitmap may still be on screen, so the cache only lets go
 * of it and the collector frees it once no view holds it.
 */
final class AppArt {
    private AppArt() { }

    /** A prepared image and the margin (spec px) its baked shadow needs around the box it is drawn in. */
    static final class Decal {
        final Bitmap bitmap;
        final float box, margin;
        Decal(Bitmap b, float box, float margin) { bitmap = b; this.box = box; this.margin = margin; }

        /** Draws it centred on (cx, cy) in spec px (the canvas is scaled to spec px). */
        void draw(Canvas cv, float cx, float cy, Paint p) {
            float h = box / 2 + margin;
            cv.drawBitmap(bitmap, null, new RectF(cx - h, cy - h, cx + h, cy + h), p != null ? p : FILTER);
        }
    }

    static final Paint FILTER = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private static final LruCache<String, Decal> CRESTS = new LruCache<String, Decal>(24 << 20) {
        @Override protected int sizeOf(String k, Decal d) { return d.bitmap.getByteCount(); }
    };
    private static final LruCache<String, Bitmap> LOGOS = new LruCache<String, Bitmap>(4 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /**
     * A crest in a box of size spec px: the rim light (white 55 %, 1 px) and the contact shadow (0 4 8, black 50 %);
     * flat (rows and tiles, 52 px and under) gets only a softer shadow (0 2 5, black 40 %). Without a crest on the TV,
     * the one placeholder of the app (monogram: the club's initials on an ivory squircle, never a coloured disc, so an
     * opponent is never coloured either) stands in, cached as the badge: the crest is drawn as soon as its file has
     * been downloaded (a refresh fetches the followed clubs' first). Any thread; slow the first time.
     */
    static Decal crest(Context c, String url, String raw, float size, boolean flat) { return crest(c, url, raw, size, flat, false); }

    /**
     * trim: the crest file cropped to its opaque bounds first (Cards.trim, as the channel logos), so the shield itself
     * fills the box: for the small form cells, where a file's transparent padding would leave a 52 px box a 40 px crest.
     */
    static Decal crest(Context c, String url, String raw, float size, boolean flat, boolean trim) {
        float s = Ui.scale(c);
        String tail = "|" + size + "|" + flat + "|" + trim + "|" + s, badge = "badge:" + Badges.key(raw) + I18n.lang() + tail;
        boolean have = Cards.have(c, url);
        String key = have ? url + tail : badge;
        Decal d = CRESTS.get(key);
        if (d != null) return d;
        float margin = flat ? 10 : 16;
        int box = Math.max(1, Math.round(size * s)), m = Math.round(margin * s), side = box + 2 * m;
        Bitmap src = have ? Net.image(c, url) : null;
        if (src != null) {  // line art whose emblem is white inside (Olympiacos): filled as the club shows it (QA3G-5)
            Bitmap f = CrestFill.filled(src);
            if (f != src) { src.recycle(); src = f; }
        }
        if (src != null && trim) {
            Bitmap t = Cards.trim(src);
            if (t != src) { src.recycle(); src = t; }
        }
        boolean placeholder = src == null;
        if (placeholder) {  // no file, or one that could not be read (it is fetched again): the monogram, for now
            key = badge;
            Ui.fonts(c);
            int sq = Math.max(1, Math.round(box * 0.84f));  // a squircle reads larger than a shield: a little smaller
            src = Bitmap.createBitmap(sq, sq, Bitmap.Config.ARGB_8888);
            monogram(new Canvas(src), raw, sq / 2f, sq / 2f, sq);
        }
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        {
            float k = placeholder ? 1 : Math.min((float) box / src.getWidth(), (float) box / src.getHeight());
            float w = src.getWidth() * k, h = src.getHeight() * k;
            Bitmap smooth = Cards.smooth(src, w);
            Bitmap shape = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            RectF dst = new RectF((side - w) / 2, (side - h) / 2, (side + w) / 2, (side + h) / 2);
            new Canvas(shape).drawBitmap(smooth, null, dst, FILTER);
            if (smooth != src) smooth.recycle();
            src.recycle();
            Bitmap alpha = shape.extractAlpha();
            // contact shadow: the crest's own shape, blurred (CSS blur 8 px = sigma 4; three box passes of radius r)
            Bitmap shadow = blur(alpha, Math.max(1, Math.round((flat ? 2f : 3.5f) * s)));
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            sp.setColor(flat ? 0x66000000 : 0x80000000);
            cv.drawBitmap(shadow, 0, (flat ? 2 : 4) * s, sp);
            shadow.recycle();
            if (!flat && !placeholder) {  // the rim: a hair of light around the edge, so a red crest reads on a red field
                Bitmap rim = blur(alpha, Math.max(1, Math.round(s)));
                sp.setColor(0x8CFFFFFF);
                cv.drawBitmap(rim, 0, 0, sp);
                rim.recycle();
            }
            alpha.recycle();
            cv.drawBitmap(shape, 0, 0, null);
            shape.recycle();
        }
        d = new Decal(out, size, margin);
        CRESTS.put(key, d);
        return d;
    }

    /**
     * A crest for where it is drawn: the match's crest file when it is on the TV, else the club's own crest when that
     * one is (fallback: a followed club's crest, the one its rail shows, P2f: the EuroLeague's Olympiacos crest may be
     * missing while ESPN's is cached), else the monogram.
     */
    static Decal crest(Context c, String url, String fallback, String raw, float size, boolean flat) {
        boolean use = fallback != null && !fallback.equals(url) && !Cards.have(c, url) && Cards.have(c, fallback);
        return crest(c, use ? fallback : url, raw, size, flat, false);
    }

    /** The placeholder's ivory (the Signature card's squircle, SPEC-APP 1.2) and its monogram ink (#141416). */
    static final int IVORY = 0xFFF3F1EC, MONOGRAM = 0xFF141416;

    /**
     * The one placeholder for a crest that is not on the TV (yet), everywhere in the app: an ivory squircle (corner
     * 28 % of its side) with the club's initials (2-4 letters, Google Sans Bold at 31 % of the side, smaller when they
     * would not fit) in #141416 at 60 %. squircle false: the initials alone, on a squircle the caller drew.
     */
    static void monogram(Canvas cv, String raw, float cx, float cy, float side) { monogram(cv, raw, cx, cy, side, true, 1); }

    static void monogram(Canvas cv, String raw, float cx, float cy, float side, boolean squircle, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (squircle) {
            p.setColor(Ui.alpha(IVORY, alpha));
            float h = side / 2, r = side * 0.28f;
            cv.drawRoundRect(new RectF(cx - h, cy - h, cx + h, cy + h), r, r, p);
        }
        String t = raw == null || raw.trim().isEmpty() ? "?" : Badges.initials(raw);
        p.setTypeface(Ui.BOLD);
        p.setColor(Ui.alpha(MONOGRAM, 0.6f * alpha));
        p.setTextAlign(Paint.Align.CENTER);
        float size = side * 0.31f;
        p.setTextSize(size);
        float w = p.measureText(t), room = side * 0.74f;
        if (w > room) { size *= room / w; p.setTextSize(size); }
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2, p);
    }

    /** A box blur (three passes, so close to a Gaussian) of an alpha mask; returns a new ALPHA_8 bitmap. */
    static Bitmap blur(Bitmap alpha, int r) {
        int w = alpha.getWidth(), h = alpha.getHeight();
        Bitmap argb = alpha.copy(Bitmap.Config.ARGB_8888, false);
        int[] px = new int[w * h];
        argb.getPixels(px, 0, w, 0, 0, w, h);
        argb.recycle();
        int[] a = new int[w * h], t = new int[w * h];
        for (int i = 0; i < px.length; i++) a[i] = px[i] >>> 24;
        for (int pass = 0; pass < 3; pass++) {
            boxH(a, t, w, h, r);
            boxV(t, a, w, h, r);
        }
        for (int i = 0; i < px.length; i++) px[i] = a[i] << 24;
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap mask = out.extractAlpha();
        out.recycle();
        return mask;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int span = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, sum = 0;
            for (int x = -r; x <= r; x++) sum += in[row + Math.min(w - 1, Math.max(0, x))];
            for (int x = 0; x < w; x++) {
                out[row + x] = sum / span;
                sum += in[row + Math.min(w - 1, x + r + 1)] - in[row + Math.max(0, x - r)];
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int span = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int y = -r; y <= r; y++) sum += in[Math.min(h - 1, Math.max(0, y)) * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / span;
                sum += in[Math.min(h - 1, y + r + 1) * w + x] - in[Math.max(0, y - r) * w + x];
            }
        }
    }

    /** A channel logo cropped to its opaque bounds (cached); null when it is not on the TV yet. */
    static Bitmap logo(Context c, String url) {
        if (url == null || url.isEmpty()) return null;
        Bitmap b = LOGOS.get(url);
        if (b != null) return b;
        Bitmap src = Net.image(c, url);
        if (src == null) return null;
        b = Cards.trim(src);
        if (b != src) src.recycle();
        LOGOS.put(url, b);
        return b;
    }

    private static Bitmap shirtMask;
    private static final LruCache<Integer, Bitmap> SHIRTS = new LruCache<>(4);

    /** The Fanela shirt in one colour (the masthead's mark in the club's ink, the empty state's red shirt). */
    static synchronized Bitmap shirt(Context c, int color) {
        Bitmap b = SHIRTS.get(color);
        if (b != null) return b;
        if (shirtMask == null) shirtMask = BitmapFactory.decodeResource(c.getResources(), R.drawable.shirt);
        b = Bitmap.createBitmap(shirtMask.getWidth(), shirtMask.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        new Canvas(b).drawBitmap(shirtMask, 0, 0, p);
        SHIRTS.put(color, b);
        return b;
    }
}
