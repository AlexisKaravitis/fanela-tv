package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Signature, the default card (spec: design/spec/cards/SPEC-CARDS.md §3.1, gen.py signature()). One dark platter lit
 * from the left by the club's colour, the club's crest as a ghost bleeding off the left and bottom and fading out to
 * the right under a horizontal scrim, and a right-aligned four-line stack at x = 902: the state, the opponent, the hero
 * (kick-off or score) and where to watch. Every line carries one fact (the competition and the date of a soon match
 * live in the launcher's description under the focused card), and the four sit on one baseline grid, 24 px apart at
 * least, so the card stays calm at the launcher's unfocused 388 x 218. A club without a sharp crest is drawn by Kit.
 */
final class SignatureStyle implements CardStyle {
    static final String ID = Appearance.SIGNATURE;

    /** The stack's right edge. */
    static final float RIGHT = 902;

    public String id() { return ID; }

    /** The ghost is 620 px: only a sharp crest can carry it. */
    public boolean suits(ClubTokens t) { return t.sharpCrest; }

    public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        platter(cv, t, art.ghostCrest());  // the ghost: the club's best crest (discs keep the match's)
        stateLine(cv, m);
        opponent(cv, m, art.oppCrest(), oppCy(m.state));
        hero(cv, m);
        foot(cv, m, art);
    }

    // ---------- 1–5: field, light, ghost, scrims ----------

    /**
     * The ghost's box top, the same in every state: a launcher row is one club's cards side by side, and a crest that
     * sat lower after full time (spec §3.1.3 had 24 / 64 / 104 by state) read as a mistake, not as variety (owner, fx6).
     */
    static final float GHOST_TOP = 64;

    private static void platter(Canvas cv, ClubTokens t, Bitmap crest) {
        cv.drawColor(t.field);
        // the light: a radial ellipse with radii 640 x 700 centred at (192, 313), my colour @ a1 → @ a2 at 42 % → 0 at 74 %
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        RadialGradient light = new RadialGradient(0, 0, 1,
            new int[]{ClubTokens.alpha(t.light, t.lightA), ClubTokens.alpha(t.light, t.lightB), ClubTokens.alpha(t.light, 0)},
            new float[]{0, 0.42f, 0.74f}, Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        mx.setScale(640, 700);
        mx.postTranslate(192, 313);
        light.setLocalMatrix(mx);
        p.setShader(light);
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
        // the ghost: 620 x 620 contained, left -170, at the crest's own alpha (ClubTokens.ghostAlpha)
        if (crest != null) ghost(cv, crest, GHOST_TOP, t.ghostAlpha);
        // horizontal scrim in the field colour (the hero never sits on the crest)
        int[] sc = new int[SCRIM_A.length];
        for (int i = 0; i < sc.length; i++) sc[i] = ClubTokens.alpha(t.field, SCRIM_A[i]);
        p.setShader(new LinearGradient(0, 0, Cards.W, 0, sc, SCRIM_AT, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
        // vertical scrim: black 0 at y 346, .30 at 475, .42 at 540
        p.setShader(new LinearGradient(0, 0, 0, Cards.H,
            new int[]{0, 0, ClubTokens.alpha(0xFF000000, 0.30), ClubTokens.alpha(0xFF000000, 0.42)},
            new float[]{0, 0.64f, 0.88f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
    }

    /**
     * The horizontal scrim in the field colour: 0 at x 192, .86 at 422, .92 at 960 (was 0 / 230, .78 / 461, .88). The
     * filled ghost (white inside Olympiacos's outline) is brighter than the line art was: the scrim now closes before
     * the VS mark and a wide score's first numeral, so the text side reads on the field, not on the crest (owner, fx6).
     */
    static final float[] SCRIM_AT = {0, 0.20f, 0.44f, 1}, SCRIM_A = {0, 0, 0.86f, 0.92f};

    /**
     * The crest contained in 620 x 620 at (-170, top), drawn with a plain paint alpha: a colour filter would take the
     * software canvas off its fast bitmap path (measured 50–145 ms against ~5 ms on the emulator).
     */
    private static void ghost(Canvas cv, Bitmap crest, float top, float alpha) {
        float sc = Math.min(620f / crest.getWidth(), 620f / crest.getHeight());
        float w = crest.getWidth() * sc, h = crest.getHeight() * sc;
        float l = -170 + (620 - w) / 2, tp = top + (620 - h) / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.round(alpha * 255));
        Cards.drawBitmap(cv, crest, new RectF(l, tp, l + w, tp + h), p);
    }

    // ---------- 6: the state line (box top 40, h 66) ----------

    /**
     * The state line: GS Bold 56 (52 in the spec, raised for the launcher's unfocused scale, 0.404: 23 px on the TV),
     * fit 640, min 44, its capitals y 48–88 (the safe top is 44); the opponent's squircle starts 24 below.
     */
    static final float STATE_CY = 68, STATE_SIZE = 56, STATE_MIN = 44;

    /**
     * The LIVE pill (66 tall, GS Bold 48) is taller than the state line's capitals: centred on y 77 it keeps the safe
     * top (y 44-110, Clean's pill band), and the opponent line moves down 22 (centre 204, the squircle y 134-274) to
     * stay 24 under it. A live hero is always a score, whose capitals start at 301, so the gap under the squircle
     * stays 27; the hero's baseline and line 4 don't move.
     */
    static final float LIVE_PILL_CY = 77, LIVE_OPP_CY = 204;

    /** The opponent line's centre: 182, or 204 under the LIVE pill. */
    static float oppCy(CardModel.State s) { return s == CardModel.State.LIVE ? LIVE_OPP_CY : OPP_CY; }

    private static void stateLine(Canvas cv, CardModel m) {
        if (m.state == CardModel.State.LIVE) {  // only a match in play is drawn as live (not awaiting, not suspended)
            CardKit.livePill(cv, m, RIGHT, LIVE_PILL_CY, 66, 48, true);
            return;
        }
        // proportional figures here (as in the renders): Google Sans's tabular setting also widens its spaces
        Paint main = CardKit.text(Ui.BOLD, STATE_SIZE, CardKit.WHITE);
        main.setLetterSpacing(0.04f);
        main.setFontFeatureSettings(null);
        final String sub0 = secondary(m);
        String sub = sub0;
        Paint sp = CardKit.text(Ui.MEDIUM, STATE_SIZE, CardKit.alpha(CardKit.WHITE, m.dateDim ? 0.40f : 0.66f));
        sp.setLetterSpacing(0.03f);
        sp.setFontFeatureSettings(null);
        float size = stateSize(w -> stateWidth(main, sp, m.stateLine, w ? sub0 : ""), main, sp, sub0.isEmpty());
        if (size < 0) { sub = ""; size = -size; }  // two segments would go under 48: the main words alone
        float base = STATE_CY + 0.346f * size;  // measured: caps y 53–91 at 52 px
        float x = RIGHT;
        if (!sub.isEmpty()) {
            CardKit.right(cv, sp, sub, x, base);
            x -= sp.measureText(sub) + 16;
        }
        CardKit.right(cv, main, m.stateLine, x, base);
    }

    /** "· FRI 6 NOV", "· AET · FRI 6 NOV", "· SUN 22 NOV" (dimmed when postponed); "" for none. */
    static String secondary(CardModel m) {
        if (m.dateLine.isEmpty() && m.stateExtra.isEmpty()) return "";
        StringBuilder b = new StringBuilder("·");
        if (!m.stateExtra.isEmpty()) b.append(' ').append(m.stateExtra).append(m.dateLine.isEmpty() ? "" : " ·");
        if (!m.dateLine.isEmpty()) b.append(' ').append(m.dateLine);
        return b.toString();
    }

    /**
     * The state line's size: both segments together from 56 while that stays at 48 or more (STATE_KEEP); under that
     * the secondary segment goes (ΚΑΘΥΣΤΕΡΗΣΗ · ΣΗΜΕΡΑ became 40 in Kit; the launcher's description gives the day, as
     * Clean's pill does) and the main words fit alone, to the style's minimum. Returns the size, negated when the
     * secondary segment is dropped. width(true): both segments at the paints' size; width(false): the main alone.
     */
    static float stateSize(StateWidth width, Paint main, Paint sub, boolean single) {
        return stateSize(width, size -> { main.setTextSize(size); sub.setTextSize(size); }, STATE_SIZE, STATE_KEEP, STATE_MIN, 640, single);
    }

    /** The minimum both segments of the state line keep together; under it the secondary segment is dropped. */
    static final float STATE_KEEP = 48;

    interface StateWidth { float at(boolean both); }

    interface Sizer { void set(float size); }

    /** As above, for any style (Kit: 602 wide, min 40): pure stepping over width and sizer, so it is unit-tested. */
    static float stateSize(StateWidth width, Sizer sizer, float start, float keep, float min, float max, boolean single) {
        float size = start;
        sizer.set(size);
        if (!single) {
            while (size > keep && width.at(true) > max) sizer.set(size -= 2);
            if (width.at(true) <= max) return size;
            size = start;
            sizer.set(size);
        }
        while (size > min && width.at(false) > max) sizer.set(size -= 2);
        return single ? size : -size;
    }

    private static float stateWidth(Paint main, Paint sub, String a, String b) {
        return main.measureText(a) + (b.isEmpty() ? 0 : 16 + sub.measureText(b));
    }

    // ---------- 7: the opponent line (centre y 182) ----------

    /**
     * The opponent's squircle (140 at y 112–252, crest 110: 132 / 104 in the spec, raised so the crest reads at 0.404)
     * and the name: RC Bold 100 in a 330 box, min 56, then its short form (CardModel.fitName); a name with no short
     * form may run on to 420, which keeps the VS mark clear of the ghost's bright core.
     */
    static final float OPP_CY = 182, SQ = 140, SQ_CREST = 110, SQ_R = 39, NAME_SIZE = 100, NAME_MIN = 56, NAME_FLOOR = 44,
        NAME_BOX = 330, NAME_RUN = 420;

    private static void opponent(Canvas cv, CardModel m, Bitmap crest, float oppCy) {
        Paint np = CardKit.text(Ui.COND_BOLD, NAME_SIZE, CardKit.WHITE);
        CardModel.NameFit f = CardKit.name(np, m.opponentNames, true, NAME_BOX, NAME_RUN, NAME_SIZE, NAME_MIN, NAME_FLOOR);
        String name = f.text;
        // right-aligned by its ink: an accent past the last letter's advance (the dialytika of the Greek DUBAI) stays inside 902
        android.graphics.Rect ink = new android.graphics.Rect();
        np.getTextBounds(name, 0, name.length(), ink);
        float nw = np.measureText(name), over = Math.max(0, ink.right - nw);
        CardKit.right(cv, np, name, RIGHT - over, oppCy + 0.39f * f.size);  // measured: caps y 148–219 at 100 px (centre 180)
        float sqR = RIGHT - over - nw - 22, sqL = sqR - SQ;
        float cx = sqL + SQ / 2;
        if (crest == null) CardKit.crestOnPlate(cv, null, m.opponentRaw, cx, oppCy, SQ, true, ClubTokens.IVORY, SQ_CREST / SQ);
        else {
            Paint sq = new Paint(Paint.ANTI_ALIAS_FLAG);
            sq.setColor(ClubTokens.IVORY);
            sq.setShadowLayer(12, 0, 10, 0x66000000);   // CSS 0 10px 24px rgba(0,0,0,.4)
            cv.drawRoundRect(new RectF(sqL, oppCy - SQ / 2, sqR, oppCy + SQ / 2), SQ_R, SQ_R, sq);
            CardKit.crest(cv, crest, cx, oppCy, SQ_CREST, null);
        }
        CardKit.vs(cv, m.vs, sqL - 24, oppCy, CardKit.VS_SIZE, CardKit.alpha(CardKit.WHITE, CardKit.VS_A), true);
    }

    // ---------- 8: the hero ----------

    /**
     * The hero's baseline: the kick-off (200) and the score (165) share it, so the numerals' feet sit on one line
     * across a row; their capitals start 24 below the squircle (time: y 276–421) or higher (score: 301–421).
     */
    static final float HERO_BASE = 421, HERO_SIZE = 200, SCORE_SIZE = 165;

    private static void hero(Canvas cv, CardModel m) {
        if (m.scoreHero) {
            if (m.scoresHidden || m.homeScore == null) {  // hidden scores: blank slots; the state line says LIVE / FINAL
                CardKit.hiddenScore(cv, RIGHT - 0.04f * SCORE_SIZE, HERO_BASE, SCORE_SIZE, Paint.Align.RIGHT);
                return;
            }
            score(cv, m);
            return;
        }
        String time = m.time.isEmpty() ? CardModel.w("tba") : m.time;
        int col = m.heroDim ? CardKit.alpha(CardKit.WHITE, 0.60f) : CardKit.WHITE;
        Paint p = CardKit.text(Ui.COND_BOLD, 200, col);
        p.setLetterSpacing(-0.01f);
        String suffix = m.timeSuffix;
        Paint sp = CardKit.text(Ui.COND_BOLD, 72, CardKit.alpha(CardKit.WHITE, 0.66f));
        float size = 200;
        while (size > 140 && p.measureText(time) + suffixWidth(sp, suffix) > 640) {
            size -= 2;
            p.setTextSize(size);
            sp.setTextSize(size * 0.36f);
        }
        float base = HERO_BASE;
        float x = RIGHT + 0.01f * size;    // CSS letter-spacing −.01 em trails the last glyph too
        if (!suffix.isEmpty()) {
            CardKit.right(cv, sp, suffix, x, base);
            x -= suffixWidth(sp, suffix);
        }
        CardKit.right(cv, p, time, x, base);
    }

    /** The 12-hour suffix (PM) set small after the time, 12 px apart. */
    private static float suffixWidth(Paint sp, String s) { return s.isEmpty() ? 0 : sp.measureText(s) + 12; }

    /** Home–away order always (never flipped); my number white, the opponent's 70 %, the dash 45 %; the outcome pill after. */
    private static void score(Canvas cv, CardModel m) {
        Paint me = CardKit.text(Ui.COND_BOLD, 165, CardKit.WHITE);
        Paint them = CardKit.text(Ui.COND_BOLD, 165, CardKit.alpha(CardKit.WHITE, 0.70f));
        Paint dash = CardKit.text(Ui.COND_BOLD, 165, CardKit.alpha(CardKit.WHITE, 0.45f));
        me.setLetterSpacing(-0.01f); them.setLetterSpacing(-0.01f); dash.setLetterSpacing(-0.01f);
        Paint hp = m.home ? me : them, ap = m.home ? them : me;
        String hs = m.homeScore, as = m.awayScore, d = "–";
        float pillW = m.outcomeWord.isEmpty() ? 0 : CardKit.pillWidth(m.outcomeWord, Ui.BOLD, 50, false) + 26;
        float size = 165;  // fit 640 (min 140, then the shared floor for three-digit scores: never further left)
        while (size > CardKit.SCORE_FLOOR && scoreWidth(hp, ap, dash, hs, as, size) + pillW > 640) {
            size -= 2;
            me.setTextSize(size); them.setTextSize(size); dash.setTextSize(size);
        }
        float base = HERO_BASE;
        float x = RIGHT + 0.01f * size;
        if (pillW > 0) {
            float cy = base - CardKit.cap(me) / 2;  // the pill centred on the numerals' capitals
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(m.outcome > 0 ? CardKit.WIN : m.outcome == 0 ? CardKit.DRAW : CardKit.LOSS);
            sh.setShadowLayer(9, 0, 6, 0x4D000000);   // CSS 0 6px 18px rgba(0,0,0,.3)
            cv.drawRoundRect(new RectF(RIGHT - pillW + 26, cy - 33, RIGHT, cy + 33), 33, 33, sh);
            CardKit.outcomePill(cv, m, RIGHT, cy, 66, 50, true);
            x -= pillW;
        }
        float margin = 0.03f * size;
        CardKit.right(cv, ap, as, x, base);
        x -= ap.measureText(as) + margin;
        CardKit.right(cv, dash, d, x, base);
        x -= dash.measureText(d) + margin;
        CardKit.right(cv, hp, hs, x, base);
    }

    private static float scoreWidth(Paint hp, Paint ap, Paint dash, String hs, String as, float size) {
        return hp.measureText(hs) + ap.measureText(as) + dash.measureText("–") + 0.06f * size;
    }

    // ---------- 9: line 4 (centre y 470): where to watch ----------

    /**
     * Line 4 carries one fact: where to watch (RC Regular 56, 23 px at the launcher's 0.404; LIKELY before the logo,
     * the number chip after it; fit 640 with the logo capped at 240 → 200 → 180, then the type down to 44, then
     * without LIKELY; a logo that wouldn't read at 44 px set as the channel's name: CardKit.fitChannel), or the
     * not-updated warning in amber when the data is stale. Results, postponed, awaiting and suspended matches have no
     * channel and no line 4. The competition is not drawn: the launcher's description names it under the focused card.
     */
    /**
     * Line 4's logo box (and number chip) is 48 tall, y 446-494: at 56 (y 442-498) it came 21 px under the hero's
     * baseline and crossed the safe bottom (496).
     */
    static final float FOOT_CY = 470, LOGO_H = 48, FOOT_SIZE = 56;

    private static void foot(Canvas cv, CardModel m, CardArt art) {
        if (m.stale) { staleLine(cv, RIGHT, FOOT_CY, FOOT_SIZE, true); return; }
        if (m.channel == null) return;
        // the shared fit (CardKit.fitChannel): 56 → 44 as written, then without LIKELY ("/" between names), then untracked
        CardKit.ChannelSet s = CardKit.fitChannel(m, art, FOOT_SIZE, FOOT_W, LOGO_H);
        cv.save();
        cv.clipRect(56, 0, RIGHT, Cards.H);  // never past the safe edge, whatever the feed sends
        CardKit.channel(cv, m, art, RIGHT, FOOT_CY, s, LOGO_H, FOOT_W, true, CardKit.alpha(CardKit.WHITE, 0.72f));
        cv.restore();
    }

    /** Line 4's width (x 262–902). */
    static final float FOOT_W = 640;

    /** The not-updated warning: an amber sign and the words, RC Regular, on centre line cy, right-aligned at x (or from x). */
    static void staleLine(Canvas cv, float x, float cy, float size, boolean alignRight) {
        String words = Cards.caps(I18n.t("not_updated").replace("⚠", "").trim());
        Paint p = CardKit.text(Ui.COND, size, CardKit.AMBER);
        p.setLetterSpacing(0.05f);
        float cap = CardKit.cap(p), sign = cap * 1.12f + 14, base = cy + cap / 2;
        float w = p.measureText(words) + sign, x0 = alignRight ? x - w : x;
        Cards.warning(cv, x0, base, cap, p);
        cv.drawText(words, x0 + sign, base, p);
    }

    /**
     * The fit-to-width cascade of spec §1.6 for a line that may hold a channel logo and a competition segment:
     * (1) shrink 2 px at a time to the midpoint of start and minimum; (2) with a logo, cap it at 200 px, then 180 px,
     * retrying (1); (3) shrink to the minimum; (4) still too wide with a competition: drop it and fit the rest from the
     * start size. Pure arithmetic over a width function, so it is unit-tested.
     */
    static final class Fit {
        /** The line's width at a text size, a logo width cap and with or without the competition. */
        interface Width { float at(float size, float logoCap, boolean comp); }

        final float size, logoCap;
        final boolean comp;

        Fit(float size, float logoCap, boolean comp) { this.size = size; this.logoCap = logoCap; this.comp = comp; }

        /**
         * The cascade down to keep (the smallest the line may get with its competition), then, if even that line
         * overflows, a little smaller still, to min (spec §1.6 steps, with the legibility floors of the 0.404 scale).
         */
        static Fit cascade(Width w, float start, float keep, float min, float max, boolean logo, boolean comp) {
            Fit f = cascade(w, start, keep, max, logo, comp);
            if (w.at(f.size, f.logoCap, f.comp) <= max) return f;
            return new Fit(shrink(w, f.size, min, max, f.logoCap, f.comp), f.logoCap, f.comp);
        }

        static Fit cascade(Width w, float start, float min, float max, boolean logo, boolean comp) {
            float mid = Math.max(min, Math.round((start + min) / 2));
            float cap = 240;
            float s = shrink(w, start, mid, max, cap, true);
            if (w.at(s, cap, true) <= max) return new Fit(s, cap, true);
            if (logo) for (float c : new float[]{200, 180}) {
                cap = c;
                s = shrink(w, start, mid, max, cap, true);
                if (w.at(s, cap, true) <= max) return new Fit(s, cap, true);
            }
            s = shrink(w, start, min, max, cap, true);
            if (w.at(s, cap, true) <= max || !comp) return new Fit(s, cap, true);
            return new Fit(shrink(w, start, min, max, cap, false), cap, false);
        }

        private static float shrink(Width w, float start, float to, float max, float cap, boolean comp) {
            float s = start;
            while (w.at(s, cap, comp) > max && s > to) s -= 2;
            return s;
        }
    }
}
