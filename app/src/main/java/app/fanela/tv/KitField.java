package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

/**
 * The environment behind the Club layout's hero, drawn once per club and state into one bitmap (SPEC-APP 3.1) and
 * cached, so the screen never blends, masks or blurs per frame: the kit field (a 160° gradient), the kit's stripes
 * and the crest watermark, both fading out before the hero ends (a white kit: a dark platter with a warm light, see
 * glow), and the fall that takes it all to near-black by
 * y 690. Live: the field goes dark with a red glow at the top edge. Neutral: the empty state's warm grey.
 */
final class KitField {
    private KitField() { }

    enum Mode { KIT, LIVE, NEUTRAL }

    /** The bitmap's height in spec px: everything below is the plain background. */
    static final float HEIGHT = 690;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    static String key(String clubId, FieldColors f, Mode mode) { return key(clubId, f, mode, true); }

    /** A kit field drawn before its crest was on the TV has no watermark: it has a key of its own, drawn again later. */
    static String key(String clubId, FieldColors f, Mode mode, boolean crest) {
        return mode == Mode.KIT ? clubId + ":" + Integer.toHexString(f.field) + ":" + mode + (crest ? "" : ":nocrest") : mode.name();
    }

    static Bitmap cached(String key) { return CACHE.get(key); }

    /**
     * The field for a club (crestUrl: its crest for the watermark; stripes: the kit is striped). Any thread except
     * the UI's. Drawn at half the screen's resolution (960 px wide at most) and scaled up when shown: the gradients and
     * the 8.5 % watermark lose nothing, the bitmap is a quarter of the memory and of the time.
     */
    static Bitmap get(Context c, String clubId, FieldColors f, String crestUrl, boolean stripes, Mode mode) {
        return get(c, clubId, f, crestUrl, stripes, mode, true);
    }

    /**
     * watermark false: the first frame's field (HomeLayout.first, on the UI thread): no crest is decoded, the field
     * with its watermark replaces it once the data is loaded (crossfaded). A field drawn whole before is used as it is.
     */
    static Bitmap get(Context c, String clubId, FieldColors f, String crestUrl, boolean stripes, Mode mode, boolean watermark) {
        boolean crest = crestUrl == null || Cards.have(c, crestUrl);  // no crest at all: nothing to wait for
        String key = key(clubId, f, mode, crest);
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        if (!watermark && crest && crestUrl != null) {
            key = key(clubId, f, mode, false);
            b = CACHE.get(key);
            if (b != null) return b;
            crestUrl = null;
        }
        float s = Math.min(1f, Ui.scale(c)) / 2;
        int w = Math.round(Ui.W * s), h = Math.round(HEIGHT * s);
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(s, s);
        cv.drawColor(Ui.APP_BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (mode == Mode.LIVE) {
            p.setShader(new LinearGradient(0, 0, 0, 680, new int[]{0xFF2A0B10, 0xFF170B0D, Ui.APP_BG}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, Ui.W, 680, p);
            // the live glow: an ellipse 1300 x 460 centred above the top edge, live red 42 % fading out at 70 %
            p.setShader(new RadialGradient(0, 0, 1300, new int[]{0x6BE8102A, 0x00E8102A}, new float[]{0, 0.7f}, Shader.TileMode.CLAMP));
            Matrix m = new Matrix();
            m.setScale(1, 460f / 1300f);
            m.postTranslate(960, -41.6f);
            p.getShader().setLocalMatrix(m);
            cv.drawRect(0, 0, Ui.W, 520, p);
        } else {
            int[] cols = mode == Mode.NEUTRAL ? new int[]{0xFF2B1316, ClubTokens.mix(Ui.APP_BG, 0xFF151011, 0.5), Ui.APP_BG}
                : new int[]{f.field, f.field2, f.field3};
            float[] pos = mode == Mode.NEUTRAL ? new float[]{0, 0.6f, 1} : new float[]{0, 0.7f, 1};
            p.setShader(gradient160(Ui.W, 680, cols, pos));
            cv.drawRect(0, 0, Ui.W, 680, p);
            if (mode == Mode.KIT && f.glow != 0) glow(cv, f);
            if (mode == Mode.KIT) {
                if (stripes) stripes(cv, f);
                if (crestUrl != null && !watermark(c, cv, f, crestUrl)) key = key(clubId, f, mode, false);  // unreadable: later
            }
            // the fall: bg 0 -> 55 % at 45 % -> 100 %, from y 360 to 690
            p.setShader(new LinearGradient(0, 360, 0, 690, new int[]{Ui.APP_BG & 0xFFFFFF, Ui.alpha(Ui.APP_BG, 0.55f), Ui.APP_BG},
                new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 360, Ui.W, 690, p);
        }
        CACHE.put(key, b);
        return b;
    }

    /**
     * A white kit's warm light (FieldColors.glow), as the cards light their platter: a radial ellipse 1250 x 760
     * centred behind the hero's crest (420, 250), the light at glowA, glowB at 42 %, gone at 74 %. The field stays dark
     * (off-white ink keeps 6.5:1 at the centre) and still reads "white kit".
     */
    static void glow(Canvas cv, FieldColors f) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        RadialGradient g = new RadialGradient(0, 0, 1, new int[]{ClubTokens.alpha(f.glow, f.glowA), ClubTokens.alpha(f.glow, f.glowB),
            ClubTokens.alpha(f.glow, 0)}, new float[]{0, 0.42f, 0.74f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1250, 760);
        m.postTranslate(420, 250);
        g.setLocalMatrix(m);
        p.setShader(g);
        cv.drawRect(0, 0, Ui.W, 680, p);
    }

    /** A CSS linear-gradient(160deg, ...) over a w x h box. */
    static LinearGradient gradient160(float w, float h, int[] cols, float[] pos) {
        double a = Math.toRadians(160);
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        float len = Math.abs(w * dx) + Math.abs(h * dy);
        float cx = w / 2, cy = h / 2;
        return new LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2, cols, pos, Shader.TileMode.CLAMP);
    }

    /** 56 px stripes every 112 px, solid to y 252 and gone by 560 (a masked layer). */
    private static void stripes(Canvas cv, FieldColors f) {
        int layer = cv.saveLayer(0, 0, Ui.W, 560, null);
        Paint p = new Paint();
        p.setColor(f.stripe);
        for (float x = 0; x < Ui.W; x += 112) cv.drawRect(x, 0, x + 56, 560, p);
        Paint mask = new Paint(Paint.DITHER_FLAG);
        mask.setShader(new LinearGradient(0, 0, 0, 560, new int[]{0xFF000000, 0xFF000000, 0x00000000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        cv.drawRect(0, 0, Ui.W, 560, mask);
        cv.restoreToCount(layer);
    }

    /**
     * The crest, grey, 1120 px, screened at 8.5 %, fading out from y 436 to 626. False when the crest is not on the TV
     * (yet). Every field is dark (white kits too), so it is always SCREEN.
     */
    private static boolean watermark(Context c, Canvas cv, FieldColors f, String url) {
        Bitmap crest = url == null ? null : Net.image(c, url);
        if (crest == null) return false;
        RectF box = new RectF(1060, -180, 2180, 940);
        float k = Math.min(box.width() / crest.getWidth(), box.height() / crest.getHeight());
        float w = crest.getWidth() * k, h = crest.getHeight() * k;
        RectF dst = new RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2);
        // the crest is drawn grey into a layer, masked, then screened onto the field at 8.5 % in one go
        Paint blend = new Paint();
        blend.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        blend.setAlpha(22);
        int layer = cv.saveLayer(0, -180, Ui.W, 680, blend);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0);
        Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        cp.setColorFilter(new ColorMatrixColorFilter(gray));
        cv.drawBitmap(crest, null, dst, cp);
        Paint mask = new Paint();
        mask.setShader(new LinearGradient(0, 436, 0, 626, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        cv.drawRect(0, -180, Ui.W, 680, mask);
        cv.restoreToCount(layer);
        crest.recycle();
        return true;
    }
}
