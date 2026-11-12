package app.fanela.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Signature for events (SPEC-F1-UFC §3.1), on SignatureStyle's grid: the stack's right edge at x 902, the header band
 * (eyebrow at x 56, the state or LIVE pill at the right), who centred on y 182 (204 under the LIVE pill), the hero on
 * baseline 421, the support line centred on y 470. The crest ghost becomes the circuit outline (F1: stroke 24, white
 * .34, .40 with a focus) or a plain octagon line (UFC), at the ghost's one top (y 64, every state: the owner's fixed
 * positions across a row). F1 is lit graphite, or by the focus team's colour; UFC by the red corner from the left and
 * the blue corner in the top-right. Signature needs no images, so it never falls back. Stream E owns this file.
 */
final class SignatureEventStyle implements EventStyle {
    public String id() { return Appearance.SIGNATURE; }

    /** Type and line art only: every event card can be drawn (spec §3.6). */
    public boolean suits(EventCardModel m, EventArt art) { return true; }

    static final float RIGHT = SignatureStyle.RIGHT, BOX = 640, WATERMARK_TOP = SignatureStyle.GHOST_TOP;

    public void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art) {
        EventKit.View v = EventKit.view(m);
        field(cv, v, t, art);
        float stateW = EventKit.state(cv, v, RIGHT, SignatureStyle.STATE_CY, BOX);
        EventKit.eyebrow(cv, v, Ui.COND, true, 0.72f, CardKit.WHITE, EventKit.steps(0.05f), SignatureStyle.COMP_X,
            v.live ? Float.NaN : SignatureStyle.COMP_BASE, SignatureStyle.LIVE_PILL_CY,
            (RIGHT - SignatureStyle.COMP_X) - SignatureStyle.COMP_GAP - stateW, -1);
        EventKit.who(cv, v, RIGHT, v.live ? SignatureStyle.LIVE_OPP_CY : SignatureStyle.OPP_CY, 1, v.f1 ? BOX : EventKit.UFC_WHO, 100, 56);
        EventKit.hero(cv, v, RIGHT, SignatureStyle.HERO_BASE, 1, BOX, 200, 140, 165, 120, 110, true);
        EventKit.foot(cv, v, art, RIGHT, SignatureStyle.FOOT_CY, true, SignatureStyle.FOOT_W);
    }

    /** The field, the light, the watermark, the scrims (SignatureStyle.platter's order and values). */
    private static void field(Canvas cv, EventKit.View v, ClubTokens t, EventArt art) {
        cv.drawColor(EventKit.FIELD);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (v.f1) {
            boolean focus = v.team1 != 0;
            int col = focus ? t.light : EventKit.GRAPHITE;
            float a1 = focus ? t.lightA : 0.26f, a2 = focus ? t.lightB : 0.11f;
            light(cv, p, 192, 313, 640, 700, col, new float[]{a1, a2, 0}, new float[]{0, 0.42f, 0.74f});
            RectF box = new RectF(20, WATERMARK_TOP, 440, WATERMARK_TOP + 420);
            EventKit.circuit(cv, art, v.circuitId, box, 24, CardKit.alpha(CardKit.WHITE, focus ? 0.40f : 0.34f));
        } else {
            light(cv, p, 150, 330, 660, 720, EventKit.RED_C, new float[]{0.55f, 0.26f, 0}, new float[]{0, 0.42f, 0.76f});
            EventKit.octagon(cv, 230, WATERMARK_TOP + 210, 210, 24, CardKit.alpha(CardKit.WHITE, 0.34f));
        }
        // the horizontal scrim in the field colour (SignatureStyle's: the type side reads on the field)
        int[] sc = new int[SignatureStyle.SCRIM_A.length];
        for (int i = 0; i < sc.length; i++) sc[i] = ClubTokens.alpha(EventKit.FIELD, SignatureStyle.SCRIM_A[i]);
        p.setShader(new LinearGradient(0, 0, Cards.W, 0, sc, SignatureStyle.SCRIM_AT, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
        // UFC's blue corner: after the scrim, before the top scrim, so it tints the top-right only (840 x 660 across:
        // the spec's mock sets its lights by their diameters; this one is meant to stay in the corner)
        if (!v.f1) light(cv, p, 960, 80, 420, 330, EventKit.BLUE_C, new float[]{0.34f, 0.14f, 0}, new float[]{0, 0.5f, 1});
        // the top scrim, the vertical scrim
        float flat = SignatureStyle.TOP_SCRIM_FLAT, h = SignatureStyle.TOP_SCRIM_H;
        int top = ClubTokens.alpha(EventKit.FIELD, SignatureStyle.TOP_SCRIM_A);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{top, top, ClubTokens.alpha(EventKit.FIELD, 0)},
            new float[]{0, flat / h, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, h, p);
        p.setShader(new LinearGradient(0, 0, 0, Cards.H,
            new int[]{0, 0, ClubTokens.alpha(0xFF000000, 0.30), ClubTokens.alpha(0xFF000000, 0.42)},
            new float[]{0, 0.64f, 0.88f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
        p.setShader(null);
    }

    /** A radial light: an ellipse with radii rx x ry at (cx, cy), the colour at the alphas at the stops. */
    static void light(Canvas cv, Paint p, float cx, float cy, float rx, float ry, int color, float[] alphas, float[] stops) {
        int[] cs = new int[alphas.length];
        for (int i = 0; i < cs.length; i++) cs[i] = ClubTokens.alpha(color, alphas[i]);
        RadialGradient g = new RadialGradient(0, 0, 1, cs, stops, Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        mx.setScale(rx, ry);
        mx.postTranslate(cx, cy);
        g.setLocalMatrix(mx);
        p.setShader(g);
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
        p.setShader(null);
    }
}
