package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.LruCache;

/**
 * Matchday's stage (SPEC-APP 4.1), drawn once per club and state into a bitmap and cached: my club's colour wrapping
 * the top-left and falling to near-black towards the opponent's side (a 100° gradient, no opponent colour), with a
 * scrim to the background under it. Live: one dark red stage for every club with the red glow at the top edge. Full:
 * the match screen's stage over the whole height. Neutral: the empty state's warm grey. Drawn at half resolution and
 * scaled up when shown (gradients lose nothing), so nothing is blended per frame.
 */
final class MatchdayStage {
    private MatchdayStage() { }

    enum Mode { CLUB, LIVE, NEUTRAL, FULL, FULL_LIVE }

    /** The stage's height (spec px); FULL covers the screen. */
    static final float HEIGHT = 600;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    static String key(String clubId, FieldColors f, Mode mode) {
        return mode == Mode.CLUB || mode == Mode.FULL ? mode + ":" + clubId + ":" + Integer.toHexString(f.field) : mode.name();
    }

    static Bitmap cached(String key) { return CACHE.get(key); }

    /**
     * The stage's four colours for a club: its field, deepened, then to near-black. White kits: their dark warm
     * platter (FieldColors), lit by the warm light at the top left (see get), so off-white text reads everywhere.
     */
    static int[] colours(FieldColors f) {
        int c1 = f.field;
        int c2 = f.white ? f.field2 : FieldColors.hsv(c1, 1, 0.705);
        int c3 = ClubTokens.mix(f.white ? f.field3 : f.field2, FieldColors.BG, 0.6);
        return new int[]{c1, c2, c3, FieldColors.BG};
    }

    /** Any thread but the UI's. */
    static Bitmap get(Context c, String clubId, FieldColors f, Mode mode) {
        String key = key(clubId, f, mode);
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        boolean full = mode == Mode.FULL || mode == Mode.FULL_LIVE;
        float h = full ? Ui.H : HEIGHT;
        float s = Math.min(1f, Ui.scale(c)) / 2;
        b = Bitmap.createBitmap(Math.round(Ui.W * s), Math.round(h * s), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(s, s);
        cv.drawColor(Ui.APP_BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (mode == Mode.LIVE || mode == Mode.FULL_LIVE) {
            p.setShader(gradient(100, Ui.W, h, new int[]{0xFF3A0A10, 0xFF260B0E, 0xFF120B0C, Ui.APP_BG}, new float[]{0, 0.35f, 0.65f, 1}));
            cv.drawRect(0, 0, Ui.W, h, p);
            // the glow: an ellipse 1400 x 420 centred 10 % above the top edge, live red 50 % fading out at 70 %
            p.setShader(new RadialGradient(0, 0, 1400, new int[]{0x80E8102A, 0x00E8102A}, new float[]{0, 0.7f}, Shader.TileMode.CLAMP));
            Matrix m = new Matrix();
            m.setScale(1, 420f / 1400f);
            m.postTranslate(960, -42);
            p.getShader().setLocalMatrix(m);
            cv.drawRect(0, 0, Ui.W, 420, p);
        } else if (mode == Mode.NEUTRAL) {
            p.setShader(gradient(100, Ui.W, h, new int[]{0xFF2B1316, 0xFF1A1012, Ui.APP_BG}, new float[]{0, 0.4f, 1}));
            cv.drawRect(0, 0, Ui.W, h, p);
        } else {
            int[] col = colours(f);
            p.setShader(gradient(100, Ui.W, h, col, full ? new float[]{0, 0.24f, 0.52f, 1} : new float[]{0, 0.28f, 0.6f, 1}));
            cv.drawRect(0, 0, Ui.W, h, p);
            if (f.glow != 0) {  // a white kit's warm light wraps the top left as its colour would (an ellipse 1250 x 700 at 300, 120)
                RadialGradient g = new RadialGradient(0, 0, 1, new int[]{ClubTokens.alpha(f.glow, f.glowA), ClubTokens.alpha(f.glow, f.glowB),
                    ClubTokens.alpha(f.glow, 0)}, new float[]{0, 0.42f, 0.74f}, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                m.setScale(1250, 700);
                m.postTranslate(300, 120);
                g.setLocalMatrix(m);
                p.setShader(g);
                cv.drawRect(0, 0, Ui.W, h, p);
            }
        }
        p.setShader(null);
        if (!full) {  // the scrim: y 380 -> 600 to the background
            p.setShader(new LinearGradient(0, 380, 0, 600, Ui.APP_BG & 0xFFFFFF, Ui.APP_BG, Shader.TileMode.CLAMP));
            cv.drawRect(0, 380, Ui.W, 600, p);
        }
        CACHE.put(key, b);
        return b;
    }

    /** A CSS linear-gradient(deg, ...) over a w x h box. */
    static LinearGradient gradient(float deg, float w, float h, int[] cols, float[] pos) {
        double a = Math.toRadians(deg);
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        float len = Math.abs(w * dx) + Math.abs(h * dy);
        float cx = w / 2, cy = h / 2;
        return new LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2, cols, pos, Shader.TileMode.CLAMP);
    }
}
