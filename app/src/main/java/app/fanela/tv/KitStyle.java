package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * KIT (spec: design/spec/cards/SPEC-CARDS.md §3.3): the card wears the shirt. The kit runs down the left edge as a
 * band 270 wide, symmetrical like a shirt's front: the stripes centred on its centre line (five, the same for every
 * striped club), my crest on an ivory disc on that line and on the card's middle, as a crest on the chest (owner,
 * fx6). The band is shaded like a torso, darker at both sides, and ends in a clean edge 30 px before the type; the
 * shirt's top fades into the ink like a collar in shadow, under a header across the whole card (LAYOUT-COMPETITION
 * §5): the competition at the left safe edge, the state ending at the right one. Under it the lines of the shared
 * grammar stand left-aligned from x = 300: opponent, hero, where to watch (or a result's date); one fact a line, on
 * Signature's baseline grid (24 px between lines at least). Also the automatic fallback for any club another style
 * can't draw (e.g. Signature without a sharp crest), so everything here works for any club, with or without a crest.
 *
 * Positions are the reference renders' (design/spec/cards/kit/*.png): text sits by its baseline, worked out from the
 * CSS boxes and checked against the measured glyph bands.
 */
final class KitStyle implements CardStyle {
    static final String ID = Appearance.KIT;

    // ---------- geometry (960 x 540 card pixels) ----------
    static final float STACK_X = 300, STACK_R = 902, STACK_W = STACK_R - STACK_X;
    /** The kit band: x 0–270, its centre line x 135; right of it the scrim's flat ink (30 px to the type). */
    static final float KIT_W = 270, KIT_CX = KIT_W / 2;
    /** Stripes: five across the band (54 px), colour A at both edges and in the middle; hoops 60 px, one on the middle. */
    static final int STRIPES = 5;
    static final float HOOP = 60;
    /** Sash: a band of B, 14 % of the gradient line, through the band's centre at 115° (CSS linear-gradient). */
    static final float SASH_DEG = 115, SASH_FROM = 0.43f, SASH_TO = 0.57f;
    static final int SCRIM = 0x121114;
    /** The torso's shade over the band, rgb(18, 17, 20): .44 at both sides, .24 on the centre line. */
    static final float SHADE_SIDE = 0.44f, SHADE_MID = 0.24f;
    /** My disc (204, crest 80 %): centred on the band's centre line and on the card's middle (x 33–237, y 168–372). */
    static final float MY_DISC = 204, MY_DISC_CX = KIT_CX, MY_DISC_CY = Cards.H / 2f, MY_CREST = 0.80f;
    /** The opponent's disc: 130 (y 117–247), crest 104 (104 / 83 in the spec, raised so the crest reads at 0.404). */
    static final float OPP_DISC = 130, OPP_CREST = 104f / 130f;
    /**
     * The grid Signature uses: state capitals y 48–88, the opponent line centred on 182, the hero's baseline 421 (time
     * and score alike), where to watch centred on 470.
     */
    static final float STATE_CY = 68, OPP_CY = 182, FOOT_CY = 470, HERO_BASE = 421;
    /** RC Bold / Regular: the baseline below a centred box's middle (em). */
    static final float RC_CENTRE_TO_BASE = 0.39f;

    public String id() { return ID; }

    /** Kit draws every club: any kit, any crest, none at all. */
    public boolean suits(ClubTokens t) { return true; }

    public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        kit(cv, t);
        scrim(cv);
        fade(cv);
        disc(cv, art.myCrest(), m.mineRaw, MY_DISC_CX, MY_DISC_CY, MY_DISC, MY_CREST, 14, 10, 0.45f);
        float stateW = stateLine(cv, m);
        competition(cv, m, stateW);
        opponentLine(cv, m, art, SignatureStyle.oppCy(m.state));
        hero(cv, m);
        lineFour(cv, m, art);
    }

    // ================================================================ 1. the shirt
    /** The kit pattern in the painted kit colours (§2.2), hard edges, never a gradient between the two colours. */
    static void kit(Canvas cv, ClubTokens t) {
        Paint p = new Paint();
        if (t.pattern == ClubTokens.Pattern.SASH) {
            float[] g = sashLine(SASH_DEG, KIT_W, Cards.H);
            int a = t.kitA, b = t.kitB;
            p.setShader(new LinearGradient(g[0], g[1], g[2], g[3], new int[]{a, a, b, b, a, a},
                new float[]{0, SASH_FROM, SASH_FROM, SASH_TO, SASH_TO, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, KIT_W, Cards.H, p);
            p.setShader(null);
            p.setColor(t.kitA);
            cv.drawRect(KIT_W, 0, Cards.W, Cards.H, p);
            return;
        }
        for (float[] r : patternRects(t.pattern, KIT_W, Cards.H)) {
            p.setColor(r[4] == 0 ? t.kitA : t.kitB);
            cv.drawRect(r[0], r[1], r[2], r[3], p);
        }
        p.setColor(t.kitA);
        cv.drawRect(KIT_W, 0, Cards.W, Cards.H, p);  // under the opaque scrim; keeps the bitmap opaque
    }

    /**
     * The pattern as rectangles over the band (0..w x 0..h): {left, top, right, bottom, 0 = colour A / 1 = B}, mirror
     * symmetric about the band's centre line. Stripes: STRIPES equal stripes, A at both edges and in the middle; hoops
     * 60 px, an A hoop centred on the middle (cut equally at the top and the bottom); halves A | B split on the centre
     * line; solid A. Sash is a gradient line (sashLine), not rectangles: it returns the solid fill here.
     */
    static float[][] patternRects(ClubTokens.Pattern p, float w, float h) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        switch (p) {
            case STRIPES:
                for (int i = 0; i < STRIPES; i++) out.add(new float[]{w * i / STRIPES, 0, w * (i + 1) / STRIPES, h, i % 2});
                break;
            case HOOPS: {
                int k = (int) Math.ceil((h / 2 - HOOP / 2) / HOOP);  // hoops above (and below) the middle one
                for (int i = -k; i <= k; i++) {
                    float top = h / 2 - HOOP / 2 + i * HOOP;
                    out.add(new float[]{0, Math.max(0, top), w, Math.min(h, top + HOOP), Math.abs(i) % 2});
                }
                break;
            }
            case HALVES:
                out.add(new float[]{0, 0, w / 2, h, 0});
                out.add(new float[]{w / 2, 0, w, h, 1});
                break;
            default:
                out.add(new float[]{0, 0, w, h, 0});
        }
        return out.toArray(new float[0][]);
    }

    /**
     * A CSS linear-gradient's line for an angle (0deg = up, 90deg = right) on a w x h box: {x0, y0, x1, y1}. Its
     * length is |w sin a| + |h cos a| so the 0 % and 100 % stops land on the box's corners, as in the browser.
     */
    static float[] sashLine(float deg, float w, float h) {
        double a = Math.toRadians(deg), dx = Math.sin(a), dy = -Math.cos(a);
        double len = Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a)), cx = w / 2.0, cy = h / 2.0;
        return new float[]{(float) (cx - dx * len / 2), (float) (cy - dy * len / 2), (float) (cx + dx * len / 2), (float) (cy + dy * len / 2)};
    }

    /**
     * The band's shade, rgb(18, 17, 20) mirrored about its centre line (.44 at the sides, .24 in the middle: a torso
     * in light, which also keeps a white stripe from glaring), and the flat ink from the band's edge to the card's.
     */
    static void scrim(Canvas cv) {
        int side = ((int) (SHADE_SIDE * 255 + 0.5f) << 24) | SCRIM, mid = ((int) (SHADE_MID * 255 + 0.5f) << 24) | SCRIM;
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, KIT_W, 0, new int[]{side, mid, side}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, KIT_W, Cards.H, p);
        p.setShader(null);
        p.setColor(0xFF000000 | SCRIM);
        cv.drawRect(KIT_W, 0, Cards.W, Cards.H, p);
    }

    /**
     * The shirt's top fades into the ink (LAYOUT-COMPETITION §5): rgb(18, 17, 20) solid over y 0–100, to nothing at
     * y 170, over the band only (the rest is ink already). The header's competition stands on 90 % ink or more for its
     * whole height, on a white kit too; the disc (y 168–372) is untouched.
     */
    static final float FADE_A = 1f, FADE_FLAT = 100, FADE_H = 170;

    static void fade(Canvas cv) {
        int a = ((int) (FADE_A * 255 + 0.5f) << 24) | SCRIM;
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, FADE_H, new int[]{a, a, SCRIM}, new float[]{0, FADE_FLAT / FADE_H, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, KIT_W, FADE_H, p);
    }

    /** A crest on an ivory disc (#EDEAE3) with a soft shadow; the club badge without a crest. */
    static void disc(Canvas cv, Bitmap crest, String raw, float cx, float cy, float size, float crestRatio,
                     float blur, float dy, float shadow) {
        if (crest == null) { Badges.draw(cv, raw, cx, cy, size / 2); return; }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ClubTokens.IVORY_KIT);
        p.setShadowLayer(blur, 0, dy, CardKit.alpha(0xFF000000, shadow));
        cv.drawCircle(cx, cy, size / 2, p);
        CardKit.crest(cv, crest, cx, cy, size * crestRatio, null);
    }

    // ================================================================ 2. the header: the competition, the state
    /**
     * The state as Signature sets it: GS Bold 56, the main words only, fit 602 (spec §3.3: 52), min 44; its right edge
     * at the stack's (x 902, LAYOUT-COMPETITION §5: Kit's 602 column can't hold a league and a date side by side, so
     * the header takes the card's width and the state its far end). 56 reads as 23 px at the launcher's 0.404.
     */
    static final float STATE_SIZE = 56, STATE_MIN = 44, LIVE_PILL_H = 66, LIVE_PILL_SIZE = 48;

    /** TOMORROW / IN 2 H / SUN 22 NOV / FINAL / POSTPONED / the LIVE pill, ending at x 902; returns its width. */
    static float stateLine(Canvas cv, CardModel m) {
        if (m.state == CardModel.State.LIVE)  // only a match in play is drawn as live (not awaiting, not suspended)
            // as Signature: centred on y 77 (y 44-110), the opponent line 22 lower (SignatureStyle.oppCy)
            return CardKit.livePill(cv, m, STACK_R, SignatureStyle.LIVE_PILL_CY, LIVE_PILL_H, LIVE_PILL_SIZE, true);
        final Paint a = text(Ui.BOLD, STATE_SIZE, CardKit.WHITE, 0.04f);
        a.setFontFeatureSettings(null);  // dates in proportional figures, as set in the reference (tabular: the hero)
        String main = m.stateLine;
        float size = SignatureStyle.stateSize(sz -> { a.setTextSize(sz); return a.measureText(main); }, STATE_SIZE, STATE_MIN, STACK_W);
        a.setTextSize(size);
        String s = a.measureText(main) > STACK_W ? CardKit.cut(a, main, STACK_W) : main;
        CardKit.right(cv, a, s, STACK_R, STATE_CY + CardKit.cap(a) / 2);
        return a.measureText(s);
    }

    /** The competition at the left safe edge, as Signature's (it may run past the band onto the flat ink). */
    static void competition(Canvas cv, CardModel m, float stateW) {
        boolean live = m.state == CardModel.State.LIVE;
        CardKit.headerComp(cv, m, SignatureStyle.COMP_X, live ? Float.NaN : SignatureStyle.COMP_BASE,
            SignatureStyle.LIVE_PILL_CY, (STACK_R - SignatureStyle.COMP_X) - SignatureStyle.COMP_GAP - stateW);
    }

    // ================================================================ 3. line 2: VS / @, the opponent's disc, the name
    /** The name: RC Bold 96 in a 400 box, min 56, then its short form; it may run on to the safe edge (x 902). */
    static final float NAME_SIZE = 96, NAME_MIN = 56, NAME_FLOOR = 44, NAME_BOX = 400, GAP = 22;

    /** The VS / @ mark (CardKit.vs) from x 300, the opponent's disc 22 px after its ink, then the name. */
    static void opponentLine(Canvas cv, CardModel m, CardArt art, float oppCy) {
        float dx = STACK_X + CardKit.vs(cv, m.vs, STACK_X, oppCy, CardKit.VS_SIZE, CardKit.alpha(CardKit.WHITE, CardKit.VS_A), false) + GAP;
        disc(cv, art.oppCrest(), m.opponentRaw, dx + OPP_DISC / 2, oppCy, OPP_DISC, OPP_CREST, 8, 6, 0.40f);
        float nx = dx + OPP_DISC + GAP;
        Paint n = text(Ui.COND_BOLD, NAME_SIZE, CardKit.WHITE, 0);
        // the shared rule (CardModel.fitName): the box, the short form, then on to the safe edge; never cut
        CardModel.NameFit f = CardKit.name(n, m.opponentNames, true, Math.min(NAME_BOX, STACK_R - nx), STACK_R - nx,
            NAME_SIZE, NAME_MIN, NAME_FLOOR);
        cv.save();
        cv.clipRect(0, 0, STACK_R, Cards.H);
        cv.drawText(f.text, nx, oppCy + RC_CENTRE_TO_BASE * f.size, n);
        cv.restore();
    }

    // ================================================================ 4. line 3: the hero
    static final float HERO_SIZE = 200, SCORE_SIZE = 165, HERO_MIN = 140, PILL_GAP = 26, PILL_H = 66, PILL_SIZE = 50;

    static void hero(Canvas cv, CardModel m) {
        if (!m.scoreHero) { time(cv, m); return; }
        if (m.scoresHidden || m.homeScore == null) { hidden(cv); return; }
        Paint mine = text(Ui.COND_BOLD, SCORE_SIZE, CardKit.WHITE, -0.01f);
        Paint them = text(Ui.COND_BOLD, SCORE_SIZE, CardKit.alpha(CardKit.WHITE, 0.70f), -0.01f);
        Paint dash = text(Ui.COND_BOLD, SCORE_SIZE, CardKit.alpha(CardKit.WHITE, 0.45f), -0.01f);
        Paint hp = m.home ? mine : them, ap = m.home ? them : mine;
        String hs = m.homeScore, as = m.awayScore, d = "–";
        float pill = m.outcomeWord.isEmpty() ? 0 : PILL_GAP + CardKit.pillWidth(m.outcomeWord, Ui.BOLD, PILL_SIZE, false);
        Fit f = fit((size, cap, comp) -> {
            hp.setTextSize(size); ap.setTextSize(size); dash.setTextSize(size);
            return hp.measureText(hs) + 0.06f * size + dash.measureText(d) + ap.measureText(as) + pill;
        }, STACK_W, SCORE_SIZE, CardKit.SCORE_FLOOR, false, false);  // min 140, then the floor (three digits)
        // a three-digit score with its outcome pill (100-105 LOSS) is still wider than the stack at the floor: the
        // numerals go on down (never under 96) rather than the pill past the safe edge
        while (!f.fits && f.size > 96) {
            f.size -= 2;
            hp.setTextSize(f.size); ap.setTextSize(f.size); dash.setTextSize(f.size);
            f.fits = hp.measureText(hs) + 0.06f * f.size + dash.measureText(d) + ap.measureText(as) + pill <= STACK_W;
        }
        float s = f.size, base = HERO_BASE, x = STACK_X;
        hp.setTextSize(s); ap.setTextSize(s); dash.setTextSize(s);
        cv.drawText(hs, x, base, hp); x += hp.measureText(hs) + 0.03f * s;
        cv.drawText(d, x, base, dash); x += dash.measureText(d) + 0.03f * s;
        cv.drawText(as, x, base, ap); x += ap.measureText(as);
        if (pill > 0) CardKit.outcomePill(cv, m, x + PILL_GAP, base - CardKit.cap(mine) / 2, PILL_H, PILL_SIZE, false);  // centred on the capitals
    }

    /** The kick-off (or TBA at 60 %); a 12-hour clock's AM/PM small after it, on the same baseline. */
    static void time(Canvas cv, CardModel m) {
        Paint p = text(Ui.COND_BOLD, HERO_SIZE, m.heroDim ? CardKit.alpha(CardKit.WHITE, 0.60f) : CardKit.WHITE, -0.01f);
        Paint sfx = text(Ui.COND_BOLD, HERO_SIZE * 0.32f, CardKit.alpha(CardKit.WHITE, 0.66f), 0.02f);
        String t = m.time, suffix = m.timeSuffix;  // TBA has none; an abandoned match's dim kick-off keeps its AM / PM
        Fit f = fit((size, cap, comp) -> {
            p.setTextSize(size); sfx.setTextSize(size * 0.32f);
            return p.measureText(t) + (suffix.isEmpty() ? 0 : 0.07f * size + sfx.measureText(suffix));
        }, STACK_W, HERO_SIZE, HERO_MIN, false, false);
        p.setTextSize(f.size); sfx.setTextSize(f.size * 0.32f);
        float base = HERO_BASE;
        cv.drawText(t, STACK_X, base, p);
        if (!suffix.isEmpty()) cv.drawText(suffix, STACK_X + p.measureText(t) + 0.07f * f.size, base, sfx);
    }

    /** Scores hidden by the viewer (or not given): the shared blank slots and dash, where a numeral's ink starts. */
    static void hidden(Canvas cv) {
        CardKit.hiddenScore(cv, STACK_X + 0.04f * SCORE_SIZE, HERO_BASE, SCORE_SIZE, Paint.Align.LEFT);
    }

    // ================================================================ 5. line 4: where to watch
    /**
     * Line 4 carries one fact: where to watch (RC Regular 56, 23 px at the launcher's 0.404), fit 602 by the shared
     * channel fit (CardKit.fitChannel: the logo capped at 240 → 200 → 180, the type down to 44, then without LIKELY
     * and with "/" between the names, then untracked; never under 44; a logo that wouldn't read set as the channel's
     * name); the date of a result, a postponed or abandoned match (SignatureStyle.dateFoot, from x 300). When the data
     * is stale the amber not-updated warning takes the line instead. Awaiting, suspended: no line.
     */
    static final float FOOT_SIZE = 56, LOGO_W = 240, LOGO_H = SignatureStyle.LOGO_H;

    static void lineFour(Canvas cv, CardModel m, CardArt art) {
        if (m.stale) { SignatureStyle.staleLine(cv, STACK_X, FOOT_CY, FOOT_SIZE, false); return; }
        if (!SignatureStyle.footDate(m).isEmpty()) { SignatureStyle.dateFoot(cv, m, STACK_X, FOOT_CY, false, STACK_W); return; }
        if (m.channel == null) return;
        int chText = CardKit.alpha(CardKit.WHITE, 0.72f);
        CardKit.ChannelSet s = CardKit.fitChannel(m, art, FOOT_SIZE, STACK_W, LOGO_H);
        cv.save();
        cv.clipRect(0, 0, STACK_R, Cards.H);  // never past the safe edge, whatever the feed sends
        CardKit.channel(cv, m, art, STACK_X, FOOT_CY, s, LOGO_H, STACK_W, false, chText);
        cv.restore();
    }

    // ================================================================ the fit-to-width cascade (spec §1.6)
    /** A line's width at a text size, with the channel logo's width capped, with or without the competition. */
    interface Width { float at(float size, float logoCap, boolean withComp); }

    /** The cascade's outcome: the size, the logo cap and whether the competition stays. */
    static final class Fit {
        float size, logoCap = LOGO_W;
        boolean comp = true, fits;
    }

    /**
     * Spec §1.6, as the reference renders do it: (1) shrink 2 px at a time to the midpoint of start and minimum;
     * (2) with a logo, cap it at 200 then 180 px, retrying (1) from the start size each time; (3) shrink to the
     * minimum; (4) still too wide and the line can drop its competition: drop it and fit the rest from the start size.
     * The logo cap reached stays. fits: false when even the last step overflows (the caller cuts or accepts it).
     */
    static Fit fit(Width w, float max, float start, float min, boolean hasLogo, boolean canDropComp) {
        Fit f = new Fit();
        float mid = Math.max(min, Math.round((start + min) / 2));
        if (shrink(w, f, max, start, mid)) return f;
        if (hasLogo) for (float cap : new float[]{200, 180}) { f.logoCap = cap; if (shrink(w, f, max, start, mid)) return f; }
        if (shrink(w, f, max, start, min)) return f;
        if (canDropComp) { f.comp = false; shrink(w, f, max, start, min); }
        return f;
    }

    private static boolean shrink(Width w, Fit f, float max, float start, float to) {
        float s = start;
        while (w.at(s, f.logoCap, f.comp) > max && s > to) s -= 2;
        f.size = s;
        f.fits = w.at(s, f.logoCap, f.comp) <= max;
        return f.fits;
    }

    // ================================================================ helpers
    static Paint text(Typeface face, float size, int color, float tracking) {
        Paint p = CardKit.text(face, size, color);
        if (tracking != 0) p.setLetterSpacing(tracking);
        return p;
    }
}
