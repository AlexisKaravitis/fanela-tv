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
import java.util.List;

/**
 * Clean (spec: design/spec/cards/SPEC-CARDS.md §3.2, laid out again in LAYOUT-CALM.md and LAYOUT-COMPETITION.md §6):
 * an Apple-Sports platter. Both clubs on ivory plates at the sides, the numerals between them, a header over them and
 * one line at the foot; each side lit by its club's colour, the opponent's light always weaker than mine.
 *
 * One centre axis (x 480) and one plate axis a side, whose outer edges (x 56, 904) are the header's: the header (y
 * 44–110) has the competition at its left end and the one pill (the state or the day) at its right; the hero (capitals
 * centred on y 228, as the plates are) says the time or the score, the names sit under their plates (y 342–412) and
 * the foot (centred on y 464) carries the one support fact of the state: where to watch before and during a match, the
 * date after it. A soon match's date is not drawn: the launcher's description gives it under the focused card. Nothing
 * is closer than 24 px to anything else.
 */
final class CleanStyle implements CardStyle {
    static final String ID = Appearance.CLEAN;

    public String id() { return ID; }

    public boolean suits(ClubTokens t) { return true; }

    // ---------- tokens (spec §3.2) ----------
    static final int BASE_TOP = 0xFF1A1A1E, BASE_BOTTOM = 0xFF0F0F12;
    /**
     * Card text alphas: the foot's date 55 %, a postponed date 45 % (40 read as faint on a lit field at the launcher's
     * 0.404), LIKELY 66 %, TBA 60 %, the opponent's number 70 %.
     */
    static final float WHEN_A = 0.55f, WHEN_DIM_A = 0.45f, LIKELY_A = 0.66f, TBA_A = 0.60f, THEM_A = 0.70f, DASH_A = 0.45f;

    /** Google Sans with CSS line-height normal: the baseline sits 0.966 em under the box's top (typo ascender). */
    static final float GS_ASC = 0.966f;

    // ---------- the grid (card px) ----------
    /** The pill: the header's right end (x 904, the right plate's edge), y 44–110 (66 tall, GS Bold 48, fit 480 min 44). */
    static final float PILL_CY = 77, PILL_H = 66, PILL_SIZE = 48, PILL_MAX = 480, PILL_MIN = 44, PILL_R = 904;
    /**
     * The plates: 188 (corner 52) at 56 from either edge, y 134–322 (18 lower than before the competition: a 66 px pill
     * over a plate needs 24 px of air), the crest 148 in them; both the same size.
     */
    static final float PLATE = 188, PLATE_R = 52, PLATE_IN = 56, PLATE_TOP = 134, PLATE_CY = PLATE_TOP + PLATE / 2, CREST = 148;
    /** The lights' centre line: they light the plates (y 320, 186 under the plates' top). */
    static final float LIGHT_CY = PLATE_TOP + 186;
    /**
     * The competition, the header's left end: GS Medium mixed case (Champions League, Κύπελλο) 48 → 44, tracking .02,
     * white 66 %, its ink from x 56, its capitals centred on the pill's centre line (y 77) in every state; 32 px from
     * the pill at least (LAYOUT-COMPETITION §6). A club whose light is bright (Aris's, AEK's yellow) lights the
     * header too: there it is 80 %, which keeps 4.5:1 over the brightest pixel under it (66 % gave 3.8:1); the same
     * on every card of the club's row.
     */
    static final float COMP_X = 56, COMP_SIZE = 48, COMP_TRACK = 0.02f, COMP_A = 0.66f, COMP_A_BRIGHT = 0.80f, COMP_GAP = 32;
    /** A light this bright (WCAG luminance) takes the competition to COMP_A_BRIGHT. */
    static final double BRIGHT_LIGHT = 0.40;

    /** The competition's alpha for a club: 66 %, 80 % under a bright light (a colour kit's; white and black kits' are faint). */
    static float compAlpha(ClubTokens t) {
        return t.kit == ClubTokens.Kit.CHROMATIC && ClubTokens.luminance(myLight(t).color) >= BRIGHT_LIGHT ? COMP_A_BRIGHT : COMP_A;
    }
    static final float PLATE_L_CX = PLATE_IN + PLATE / 2, PLATE_R_CX = Cards.W - PLATE_IN - PLATE / 2;
    /** The hero: RC Bold 176 (caps y 164–292) centred on the plates' centre line, in the 424 between them (min 130). */
    static final float HERO_SIZE = 176, HERO_BOX = Cards.W - 2 * (PLATE_IN + PLATE + 24), HERO_MIN = 130, HERO_CX = Cards.W / 2f;
    /** The names: GS Bold 56 mixed case from y 342 (20 under the plates), min 44, in a 320 box; the floor for no club. */
    static final float NAME_TOP = PLATE_TOP + PLATE + 20, NAME_SIZE = 56, NAME_MIN = 44, NAME_FLOOR = 36, NAME_BOX = 320;
    /** The safe edges a name never crosses. */
    static final float SAFE_L = 56, SAFE_R = Cards.W - 56;
    /**
     * The foot: centred on x 480, y 464 (40 under the names' box, 46 over the bottom); GS 48, logos 60 tall; a channel
     * line never under 44 (it lost LIKELY and took "/" first).
     */
    static final float FOOT_CY = 464, FOOT_SIZE = 48, FOOT_MIN = CardKit.CHANNEL_FLOOR, CHIP_SIZE = 46, LOGO_H = 60, FOOT_BOX = 840;

    /** A light: a colour at two strengths (centre, shoulder at 40 %), gone at 72 % of the ellipse. */
    static final class Light {
        final int color;
        final float a1, a2;
        Light(int color, float a1, float a2) { this.color = ClubTokens.opaque(color); this.a1 = a1; this.a2 = a2; }
    }

    /** My light: my kit colour (.70, .35); a black kit's grey (.30, .12); a white kit's warm grey (.33, .13). */
    static Light myLight(ClubTokens t) {
        switch (t.kit) {
            case BLACK: return new Light(t.light, 0.30f, 0.12f);
            case WHITE: return new Light(t.light, 0.33f, 0.13f);
            default: return new Light(t.light, 0.70f, 0.35f);
        }
    }

    /**
     * The opponent's light, always weaker than mine: its kit colour at (.40, .18) (a white kit's warm grey, a black
     * kit's cool grey); its second colour when the kits clash (white → #8A9099); and neutral grey when my kit is
     * black or white, so a PAOK card stays black-and-white.
     */
    static Light oppLight(ClubTokens t, int oppPrim, int oppSec) {
        if (t.kit == ClubTokens.Kit.BLACK) return new Light(ClubTokens.NEUTRAL_OPP, 0.18f, 0.08f);
        if (t.kit == ClubTokens.Kit.WHITE) return new Light(ClubTokens.NEUTRAL_OPP, 0.22f, 0.10f);
        int c;
        if (t.clashes(ClubTokens.opaque(oppPrim))) c = ClubTokens.lum(oppSec) < 0.85 ? oppSec : ClubTokens.CLASH_OPP;
        else {
            ClubTokens.Kit k = ClubTokens.kitClass(ClubTokens.opaque(oppPrim));
            c = k == ClubTokens.Kit.CHROMATIC ? oppPrim : k == ClubTokens.Kit.WHITE ? ClubTokens.LIGHT_WHITE : ClubTokens.LIGHT_BLACK;
        }
        return new Light(c, 0.40f, 0.18f);
    }

    public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        Bitmap myCrest = art.myCrest(), oppCrest = art.oppCrest();
        ClubTokens.CrestInfo oppInfo = CardStyles.crest(m.oppCrest, oppCrest);
        int[] oppKit = CardStyles.opponentKit(m.opponentRaw, oppInfo);

        // 1-3. base, the opponent's light, mine (CSS order: mine on top)
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, Cards.H, BASE_TOP, BASE_BOTTOM, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, bg);
        float myX = m.home ? 134 : 826, oppX = m.home ? 826 : 134;
        light(cv, oppX, LIGHT_CY, oppLight(t, oppKit[0], oppKit[1]));
        light(cv, myX, LIGHT_CY, myLight(t));
        // 4. grain (4 %), 5. vignette
        CardKit.grain(cv, 10);
        Paint vg = new Paint();
        vg.setShader(new LinearGradient(0, 0, 0, Cards.H, new int[]{0x40000000, 0, 0, 0x59000000},
            new float[]{0, 0.30f, 0.70f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, vg);

        boolean scores = m.scoreHero && m.homeScore != null && !m.scoresHidden;
        boolean hiddenScore = m.scoreHero && !scores;  // hidden by the viewer, or not known yet: the blank slots

        // 6. the header: the pill at the right, the competition in what it leaves
        Pill pill = pill(m, hiddenScore);
        if (pill != null) pill.draw(cv);
        competition(cv, m, pill == null ? 0 : pill.right - pill.left, compAlpha(t));

        // 7. plates and names: home on the left, as the score reads
        boolean mineLeft = m.home;
        side(cv, true, mineLeft ? myCrest : oppCrest, mineLeft ? m.mineRaw : m.opponentRaw, mineLeft ? m.mineNames : m.opponentNames);
        side(cv, false, mineLeft ? oppCrest : myCrest, mineLeft ? m.opponentRaw : m.mineRaw, mineLeft ? m.opponentNames : m.mineNames);

        // 8. the hero between the plates
        if (scores) score(cv, m);
        else if (hiddenScore) CardKit.hiddenScore(cv, HERO_CX, heroBaseline(HERO_SIZE), HERO_SIZE, Paint.Align.CENTER);
        else hero(cv, m);

        // 9. the foot: the state's one support fact
        foot(cv, m, art);
    }

    /** A radial light 560 x 620 (radii) centred at (cx, cy): a1 at the centre, a2 at 40 %, nothing from 72 %. */
    static void light(Canvas cv, float cx, float cy, Light l) {
        int c = l.color & 0xFFFFFF;
        RadialGradient g = new RadialGradient(cx, cy, 560,
            new int[]{CardKit.alpha(c, l.a1), CardKit.alpha(c, l.a2), c}, new float[]{0, 0.40f, 0.72f}, Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        mx.setScale(1, 620f / 560f, cx, cy);
        g.setLocalMatrix(mx);
        Paint p = new Paint();
        p.setShader(g);
        cv.drawRect(0, 0, Cards.W, Cards.H, p);
    }

    /** A paint for words (dates, LIKELY): proportional figures and spaces, as the reference sets them (tnum is for
     *  numerals; Google Sans Medium's tabular set also widens the space). */
    static Paint words(android.graphics.Typeface face, float size, int color) {
        Paint p = CardKit.text(face, size, color);
        p.setFontFeatureSettings(null);
        return p;
    }

    /** The competition at the header's left end, in what the pill leaves of it; nothing when even its C2 at 44 doesn't fit. */
    private static void competition(Canvas cv, CardModel m, float pillW, float alpha) {
        Paint p = words(Ui.MEDIUM, COMP_SIZE, CardKit.alpha(CardKit.WHITE, alpha));
        CardKit.CompFit f = CardKit.fitComp(CardKit.measure(p), m.compNames, (PILL_R - COMP_X) - COMP_GAP - pillW,
            COMP_SIZE, CardKit.COMP_MIN, new float[]{COMP_TRACK});
        if (f == null) return;
        p.setTextSize(f.size);
        p.setLetterSpacing(f.track);
        CardKit.drawLeftInk(cv, p, f.text, COMP_X, PILL_CY + CardKit.cap(p) / 2);
    }

    // ---------- 7: a side: the plate and the name under it ----------

    private static void side(Canvas cv, boolean left, Bitmap crest, String raw, List<String> names) {
        float cx = left ? PLATE_L_CX : PLATE_R_CX;
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        pp.setColor(ClubTokens.IVORY_PLATE);
        pp.setShadowLayer(25, 0, 14, 0x73000000);  // CSS 0 14px 30px rgba(0,0,0,.45): blur 30 = sigma 15 = radius 25
        cv.drawRoundRect(new RectF(cx - PLATE / 2, PLATE_TOP, cx + PLATE / 2, PLATE_TOP + PLATE), PLATE_R, PLATE_R, pp);
        if (crest != null) CardKit.crest(cv, crest, cx, PLATE_CY, CREST, null);
        else Badges.draw(cv, raw, cx, PLATE_CY, CREST / 2);
        // the name: GS Bold 56 mixed case, centred under the plate; a long one moves inward to stay off the safe edge
        if (names == null || names.isEmpty() || names.get(0).isEmpty()) return;
        Paint np = CardKit.text(Ui.BOLD, NAME_SIZE, CardKit.WHITE);
        np.setLetterSpacing(0.01f);
        CardModel.NameFit f = CardKit.name(np, names, false, NAME_BOX, NAME_BOX, NAME_SIZE, NAME_MIN, NAME_FLOOR);
        float w = np.measureText(f.text);
        cv.save();
        cv.clipRect(SAFE_L, 0, SAFE_R, Cards.H);  // never past the safe edge, whatever the feed sends
        cv.drawText(f.text, nameLeft(cx, w, SAFE_L, SAFE_R), NAME_TOP + GS_ASC * f.size, np);
        cv.restore();
    }

    /**
     * Where a name of width w starts: centred on its plate (cx), moved inward just enough to keep inside the safe
     * edges (safeL, safeR), so a long name lines up with its plate's outer edge instead of leaving the card.
     */
    static float nameLeft(float cx, float w, float safeL, float safeR) {
        float x = cx - w / 2;
        return Math.max(safeL, Math.min(x, safeR - w));
    }

    // ---------- 8: the hero ----------

    /** The hero's baseline for a numeral size: its capitals centred on the plates' centre line. */
    private static float heroBaseline(float size) {
        Paint p = CardKit.text(Ui.COND_BOLD, size, CardKit.WHITE);
        return PLATE_CY + CardKit.cap(p) / 2;
    }

    /** The kick-off (or TBA at 60 %): RC Bold 176 centred between the plates (fit 424, min 130). */
    private static void hero(Canvas cv, CardModel m) {
        String t = m.time == null || m.time.isEmpty() ? CardModel.w("tba") : m.time;
        int col = m.heroDim ? CardKit.alpha(CardKit.WHITE, TBA_A) : CardKit.WHITE;
        Paint p = CardKit.text(Ui.COND_BOLD, HERO_SIZE, col);
        p.setLetterSpacing(-0.005f);
        String suf = m.timeSuffix;  // TBA has none; an abandoned match's dim kick-off keeps its AM / PM
        Paint sp = CardKit.text(Ui.COND_BOLD, 56, CardKit.alpha(CardKit.WHITE, THEM_A));
        float size = HERO_SIZE;
        while (heroWidth(p, sp, t, suf, size) > HERO_BOX && size > HERO_MIN) { size -= 2; p.setTextSize(size); }
        sp.setTextSize(size * 0.32f);
        float w = heroWidth(p, sp, t, suf, size), x = HERO_CX - w / 2, base = heroBaseline(size);
        cv.drawText(t, x, base, p);
        if (!suf.isEmpty()) cv.drawText(suf, x + p.measureText(t) + size * 0.06f, base, sp);
    }

    private static float heroWidth(Paint p, Paint sp, String t, String suf, float size) {
        if (suf.isEmpty()) return p.measureText(t);
        sp.setTextSize(size * 0.32f);
        return p.measureText(t) + size * 0.06f + sp.measureText(suf);
    }

    /** The score in match order: mine white, theirs 70 %, the en dash 45 % with .02 em each side. */
    private static void score(Canvas cv, CardModel m) {
        Paint me = CardKit.text(Ui.COND_BOLD, HERO_SIZE, CardKit.WHITE);
        Paint them = CardKit.text(Ui.COND_BOLD, HERO_SIZE, CardKit.alpha(CardKit.WHITE, THEM_A));
        Paint dash = CardKit.text(Ui.COND_BOLD, HERO_SIZE, CardKit.alpha(CardKit.WHITE, DASH_A));
        Paint hp = m.home ? me : them, ap = m.home ? them : me;
        for (Paint p : new Paint[]{me, them, dash}) p.setLetterSpacing(-0.005f);
        String d = "–";
        float size = HERO_SIZE;  // fit 424 (min 130, then the shared floor: three digits stay clear of both plates)
        while (scoreWidth(hp, dash, ap, m, d, size) > HERO_BOX && size > CardKit.SCORE_FLOOR) {
            size -= 2;
            for (Paint p : new Paint[]{me, them, dash}) p.setTextSize(size);
        }
        float x = HERO_CX - scoreWidth(hp, dash, ap, m, d, size) / 2, base = heroBaseline(size);
        cv.drawText(m.homeScore, x, base, hp);
        x += hp.measureText(m.homeScore) + 0.02f * size;
        cv.drawText(d, x, base, dash);
        x += dash.measureText(d) + 0.02f * size;
        cv.drawText(m.awayScore, x, base, ap);
    }

    private static float scoreWidth(Paint hp, Paint dash, Paint ap, CardModel m, String d, float size) {
        return hp.measureText(m.homeScore) + dash.measureText(d) + ap.measureText(m.awayScore) + 0.04f * size;
    }

    // ---------- 6: the pill ----------

    /** The one pill: its words, colours and box (its right edge at x 904). */
    static final class Pill {
        String s;
        int bg, fg;
        boolean dot;
        float h, size, cy, left, right, top, bottom;

        void draw(Canvas cv) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(bg);
            sh.setShadowLayer(18, 0, 8, 0x59000000);  // CSS 0 8px 22px rgba(0,0,0,.35)
            cv.drawRoundRect(new RectF(left, top, right, bottom), h / 2, h / 2, sh);
            CardKit.pill(cv, s, left, cy, h, Ui.BOLD, size, bg, fg, dot, false);
        }
    }

    /**
     * The words on the one pill, by state: the day of an upcoming match (TOMORROW, SUN 22 NOV) or DELAYED alone (with
     * its day the pill shrank, DELAYED · TODAY to 46 and ΚΑΘΥΣΤΕΡΗΣΗ · ΣΗΜΕΡΑ to ~38; the launcher's description says
     * "Today 19:15 · Delayed"), LIVE with the clock, the outcome of a result (WIN / DRAW / LOSS; FINAL when scores are
     * hidden or the outcome unknown), and the state word otherwise (POSTPONED, ABANDONED, NO RESULT YET, SUSPENDED).
     * "" for none.
     */
    static String pillWords(CardModel m, boolean hiddenScore) {
        switch (m.state) {
            case LIVE: return CardKit.liveText(m);
            case RESULT: return hiddenScore || m.outcomeWord.isEmpty() ? m.stateLine : m.outcomeWord;
            default: return m.stateLine;
        }
    }

    /** The one pill, at the header's right end: neutral (#2B2B30), LIVE red, or the outcome colours; fit 480, min 44. */
    private static Pill pill(CardModel m, boolean hiddenScore) {
        Pill p = new Pill();
        p.h = PILL_H; p.size = PILL_SIZE; p.cy = PILL_CY;
        p.s = pillWords(m, hiddenScore);
        p.bg = CardKit.NEUTRAL_PILL; p.fg = CardKit.NEUTRAL_TEXT;
        if (m.state == CardModel.State.LIVE) { p.bg = CardKit.LIVE; p.fg = CardKit.WHITE; p.dot = true; }
        else if (m.state == CardModel.State.RESULT && !hiddenScore && !m.outcomeWord.isEmpty()) {
            p.bg = m.outcome > 0 ? CardKit.WIN : m.outcome == 0 ? CardKit.DRAW : CardKit.LOSS;
            p.fg = m.outcome < 0 ? CardKit.LOSS_TEXT : CardKit.WHITE;
        }
        if (p.s == null || p.s.isEmpty()) return null;
        while (CardKit.pillWidth(p.s, Ui.BOLD, p.size, p.dot) > PILL_MAX && p.size > PILL_MIN) p.size -= 2;
        float w = CardKit.pillWidth(p.s, Ui.BOLD, p.size, p.dot);
        p.left = PILL_R - w; p.right = PILL_R; p.top = p.cy - p.h / 2; p.bottom = p.cy + p.h / 2;
        return p;
    }

    // ---------- 9: the foot ----------

    /**
     * The foot, centred on x 480 and y 464: where to watch for an upcoming or live match (LIKELY GS Med 48 .04 em 66 %,
     * the trimmed logo ≤ 280 x 60, the number chip GS Bold 46 on 16 % white; abroad, LIKELY and the names as words;
     * fits 840: logos capped at 200 then 180 px, then the type shrinks to 44, then without LIKELY and with "/" between
     * the names (fitFoot); a logo that wouldn't read is set as the channel's name, CardKit.logoFit); the date of a
     * result (AET · FRI 6 NOV, YESTERDAY) or of a postponed (dimmed) or abandoned match, GS Med 48 at 55 %; nothing for
     * an awaiting or suspended match; the amber not-updated warning in place of any of them when the data is stale.
     */
    private static void foot(Canvas cv, CardModel m, CardArt art) {
        if (m.stale) { stale(cv); return; }
        switch (m.state) {
            case NEXT: case LIVE: channel(cv, m, art); break;
            case RESULT: date(cv, (m.stateExtra.isEmpty() ? "" : m.stateExtra + " · ") + m.dateLine, WHEN_A); break;
            case OFF: date(cv, m.dateLine, m.dateDim ? WHEN_DIM_A : WHEN_A); break;
            default: break;
        }
    }

    private static float footBaseline(float size) { return FOOT_CY - 1.252f * size / 2 + GS_ASC * size; }

    private static void date(Canvas cv, String s, float alpha) {
        if (s == null || s.isEmpty()) return;
        Paint p = words(Ui.MEDIUM, FOOT_SIZE, CardKit.alpha(CardKit.WHITE, alpha));
        p.setLetterSpacing(0.02f);
        CardKit.centre(cv, p, s, HERO_CX, footBaseline(FOOT_SIZE));
    }

    /** Not updated: the amber sign and the words (GS Bold, mixed case as Clean writes), centred. */
    private static void stale(Canvas cv) {
        String s = I18n.t("not_updated").replace("⚠", "").trim();
        Paint p = words(Ui.BOLD, FOOT_SIZE, CardKit.AMBER);
        float cap = CardKit.cap(p), sign = cap * 1.12f + 16, w = sign + p.measureText(s), x = HERO_CX - w / 2;
        float base = footBaseline(FOOT_SIZE);
        Cards.warning(cv, x, base, cap, p);
        cv.drawText(s, x + sign, base, p);
    }

    /** The foot's channel line laid out at a size, a logo width cap and a form (CardKit.FULL, BARE, TIGHT). */
    static final class Foot {
        float size, total;
        String likely = "", words;  // words: a channel known only by name (abroad), the whole line
        Paint lead, word, num;
        Bitmap[] img;
        float[] w, h;
    }

    /**
     * The channel line at a size, logo cap and form: LIKELY (FULL only) GS Med .04 em at 66 %, then each channel's logo
     * (≤ logoW x 60, made legible or set in type by CardKit.logoFit in what the 840 line leaves it) or its name in GS
     * Bold, each with its number chip; abroad, LIKELY and the names as one run of words (" / " between them past FULL).
     */
    private static Foot foot(CardModel m, List<Channels.Logo> logos, Bitmap[] src, int n, float size, float logoW, int form) {
        CardModel.Channel ch = m.channel;
        Foot f = new Foot();
        f.size = size;
        f.lead = words(Ui.MEDIUM, size, CardKit.alpha(CardKit.WHITE, LIKELY_A));
        f.lead.setLetterSpacing(form == CardKit.TIGHT ? 0 : 0.04f);
        f.likely = ch.likely && form == CardKit.FULL ? CardModel.w("likely") : "";
        if (ch.namesOnly) {
            f.words = (f.likely.isEmpty() ? "" : f.likely + " ") + (form == CardKit.FULL ? ch.words() : ch.bareWords());
            f.total = f.lead.measureText(f.words);
            return f;
        }
        f.word = CardKit.text(Ui.BOLD, size, CardKit.WHITE);
        f.num = CardKit.text(Ui.BOLD, size * CHIP_SIZE / FOOT_SIZE, CardKit.WHITE);
        f.img = src.clone();
        f.w = new float[n];
        f.h = new float[n];
        float fixed = f.likely.isEmpty() ? 0 : f.lead.measureText(f.likely) + 18;
        for (int i = 0; i < n; i++) {
            fixed += (i > 0 ? 18 : 0) + (logos.get(i).no > 0 ? 18 + f.num.measureText(String.valueOf(logos.get(i).no)) + 32 : 0);
            if (f.img[i] != null) {
                float sc = Math.min(logoW / f.img[i].getWidth(), LOGO_H / f.img[i].getHeight());
                f.w[i] = f.img[i].getWidth() * sc; f.h[i] = f.img[i].getHeight() * sc;
            } else f.w[i] = f.word.measureText(Cards.caps(ch.names.get(i)));
        }
        for (int i = 0; i < n; i++) {
            if (f.img[i] == null) continue;
            float others = fixed;
            for (int j = 0; j < n; j++) if (j != i) others += f.w[j];
            float[] lf = CardKit.logoFit(f.img[i].getWidth() / (float) f.img[i].getHeight(), logoW, LOGO_H, FOOT_BOX - others);
            if (lf == null) { f.img[i] = null; f.w[i] = f.word.measureText(Cards.caps(ch.names.get(i))); }
            else { f.w[i] = lf[0]; f.h[i] = lf[1]; }
        }
        f.total = fixed;
        for (int i = 0; i < n; i++) f.total += f.w[i];
        return f;
    }

    /**
     * The foot's channel line fitted to 840: each form in turn (as written; without LIKELY and with "/" between the
     * names; untracked), each at 48 with the logos capped 280 → 200 → 180, then the type down to 44 (FOOT_MIN). The
     * first that fits wins; none does: the last at 44.
     */
    private static Foot fitFoot(CardModel m, List<Channels.Logo> logos, Bitmap[] img, int n) {
        Foot f = null;
        for (int form = CardKit.FULL; form <= CardKit.TIGHT; form++) {
            float cap = 280;
            for (float c : new float[]{280, 200, 180}) {
                f = foot(m, logos, img, n, FOOT_SIZE, cap = c, form);
                if (f.total <= FOOT_BOX) return f;
                if (m.channel.namesOnly) break;
            }
            for (float size = FOOT_SIZE - 2; size >= FOOT_MIN; size -= 2) {
                f = foot(m, logos, img, n, size, cap, form);
                if (f.total <= FOOT_BOX) return f;
            }
        }
        return f;
    }

    private static void channel(Canvas cv, CardModel m, CardArt art) {
        CardModel.Channel ch = m.channel;
        if (ch == null) return;
        List<Channels.Logo> logos = ch.namesOnly ? new java.util.ArrayList<Channels.Logo>() : art.logos();
        int n = Math.min(Math.min(ch.names.size(), logos.size()), 2);
        if (n == 0 && !ch.namesOnly) return;
        Bitmap[] img = new Bitmap[n];
        for (int i = 0; i < n; i++) img[i] = art.trimmed(logos.get(i).url);
        Foot f = fitFoot(m, logos, img, n);
        float gap = 18, base = footBaseline(f.size), cy = FOOT_CY;
        if (f.words != null) {
            CardKit.centre(cv, f.lead, CardKit.cut(f.lead, f.words, FOOT_BOX), HERO_CX, base);
            return;
        }
        cv.save();
        cv.clipRect(SAFE_L, 0, SAFE_R, Cards.H);  // never past the safe edge, whatever the feed sends
        float x = HERO_CX - f.total / 2;
        if (!f.likely.isEmpty()) { cv.drawText(f.likely, x, base, f.lead); x += f.lead.measureText(f.likely) + gap; }
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < n; i++) {
            if (i > 0) x += gap;
            if (f.img[i] != null) {
                float w = f.w[i], h = f.h[i];
                Bitmap sm = Cards.smooth(f.img[i], w);
                lp.setAlpha(242);  // .95
                cv.drawBitmap(sm, null, new RectF(x, cy - h / 2, x + w, cy + h / 2), lp);
                if (sm != f.img[i]) sm.recycle();
                x += w;
            } else {
                String t = Cards.caps(ch.names.get(i));
                cv.drawText(t, x, base, f.word);
                x += f.word.measureText(t);
            }
            int no = logos.get(i).no;
            if (no > 0) {
                x += gap;
                String t = String.valueOf(no);
                Paint num = f.num;
                float bw = num.measureText(t) + 32, bh = 1.25f * num.getTextSize() + 3, top = cy - bh / 2;
                Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
                chip.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
                cv.drawRoundRect(new RectF(x, top, x + bw, top + bh), 12, 12, chip);
                float ts = num.getTextSize();
                cv.drawText(t, x + 16, top + 3 + (1.25f * ts - 1.252f * ts) / 2 + GS_ASC * ts, num);
                x += bw;
            }
        }
        cv.restore();
    }
}
