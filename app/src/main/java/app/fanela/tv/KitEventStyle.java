package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Kit for events (SPEC-F1-UFC §3.3), on KitStyle's grid: the band x 0–270 with its torso shade and the fade under the
 * header, the stack from x 300 to 902 (the state right-aligned at 902). F1's band is a livery: the focus team's colour
 * deepened 14 % (graphite #32353B without a focus) with two racing stripes in the team's trim (ivory without), and one
 * disc 204 on the chest: the focus driver's race number, else the circuit, else the flag. UFC's band is the two
 * corners, red over blue, with a headshot disc 148 on each (initials when either face is missing). Kit draws every
 * card: it is also every event style's fallback. Stream E owns this file.
 *
 * The disc and stripes sit on the band's centre line (x 135), as football Kit's crest does since the owner's fx6
 * (symmetry), not at x 162 as the spec's mock has them.
 */
final class KitEventStyle implements EventStyle {
    public String id() { return Appearance.KIT; }

    public boolean suits(EventCardModel m, EventArt art) { return true; }

    static final float X = KitStyle.STACK_X, RIGHT = KitStyle.STACK_R, BOX = KitStyle.STACK_W, CX = KitStyle.KIT_CX;
    /** The racing stripes: 26 wide, 20 apart, centred on the band's centre line. */
    static final float STRIPE_W = 26, STRIPE_GAP = 20;
    /** UFC's discs: 148, on the centre line at y 222 and 402 (the upper's top at 148, below the header). */
    static final float FACE = 148, FACE_A_CY = 222, FACE_B_CY = 402;

    public void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art) {
        EventKit.View v = EventKit.view(m);
        band(cv, v, t);
        marks(cv, v, art);
        float stateW = EventKit.state(cv, v, RIGHT, KitStyle.STATE_CY, BOX);
        EventKit.eyebrow(cv, v, Ui.COND, true, 0.72f, CardKit.WHITE, EventKit.steps(0.05f), SignatureStyle.COMP_X,
            v.live ? Float.NaN : SignatureStyle.COMP_BASE, SignatureStyle.LIVE_PILL_CY,
            (RIGHT - SignatureStyle.COMP_X) - SignatureStyle.COMP_GAP - stateW, -1);
        EventKit.who(cv, v, X, v.live ? SignatureStyle.LIVE_OPP_CY : KitStyle.OPP_CY, -1, BOX, 96, 56);
        EventKit.hero(cv, v, X, KitStyle.HERO_BASE, -1, BOX, 200, 140, 165, 120, 110, true);
        EventKit.foot(cv, v, art, X, KitStyle.FOOT_CY, false, BOX);
    }

    /** The band (livery or corners) with Kit's torso shade, the flat ink beside it and the fade under the header. */
    private static void band(Canvas cv, EventKit.View v, ClubTokens t) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (v.f1) {
            p.setColor(v.team1 != 0 ? EventKit.teamPaint(v.team1) : EventKit.GRAPHITE_BODY);
            cv.drawRect(0, 0, KitStyle.KIT_W, Cards.H, p);
            p.setColor(v.trim != 0 ? v.trim : CardKit.alpha(EventKit.STRIPE, 0.92f));
            float l = CX - STRIPE_GAP / 2 - STRIPE_W, r = CX + STRIPE_GAP / 2;
            cv.drawRect(l, 0, l + STRIPE_W, Cards.H, p);
            cv.drawRect(r, 0, r + STRIPE_W, Cards.H, p);
        } else {
            p.setColor(EventKit.RED_C);
            cv.drawRect(0, 0, KitStyle.KIT_W, Cards.H / 2f, p);
            p.setColor(EventKit.BLUE_C);
            cv.drawRect(0, Cards.H / 2f, KitStyle.KIT_W, Cards.H, p);
        }
        KitStyle.scrim(cv);
        KitStyle.fade(cv);
    }

    /** F1: one disc 204 on the chest; UFC: a face disc on each corner. */
    private static void marks(Canvas cv, EventKit.View v, EventArt art) {
        if (v.f1) {
            float d = KitStyle.MY_DISC, cy = KitStyle.MY_DISC_CY;
            EventKit.plate(cv, new RectF(CX - d / 2, cy - d / 2, CX + d / 2, cy + d / 2), 0, ClubTokens.IVORY_KIT, 14, 10, 0.45f);
            EventKit.f1Mark(cv, v, art, CX, cy, 156, 9, 132, 150, 12, true);
            return;
        }
        boolean faces = faces(v, art);
        Bitmap a = faces ? art.headshot(v.fa) : null, b = faces ? art.headshot(v.fb) : null;
        for (int i = 0; i < 2; i++) {
            float cy = i == 0 ? FACE_A_CY : FACE_B_CY;
            RectF r = new RectF(CX - FACE / 2, cy - FACE / 2, CX + FACE / 2, cy + FACE / 2);
            EventKit.plate(cv, r, 0, ClubTokens.IVORY_KIT, 10, 8, 0.40f);
            EventKit.face(cv, i == 0 ? a : b, i == 0 ? v.initialsA : v.initialsB, r, 0);
        }
    }

    /** Both faces or neither (spec §3.6). */
    static boolean faces(EventKit.View v, EventArt art) {
        return v.facesOk && art != null && art.faces(v.fa, v.fb);  // the model's URLs, and both bitmaps cached and sharp
    }
}
