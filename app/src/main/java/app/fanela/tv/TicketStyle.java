package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ticket card (spec: design/spec/cards/SPEC-CARDS.md §3.4): a body on the club's kit stock (x 0–600) with the
 * competition, both crests and both names, and an ink stub (x 600–960) with the state word, the hero (kick-off or
 * score), a caption and where to watch, torn along a perforation in the launcher's own background colour.
 *
 * Text is placed as the reference renders place it: every box below is the reference's CSS box, and a baseline is
 * worked out from the box with the fonts' metrics as the browser read them (Roboto Condensed: Windows ascent 2146 and
 * descent 555 per 2048; Google Sans: typo ascent .966 and descent .286), so the device lines up with the references.
 */
final class TicketStyle implements CardStyle {
    static final String ID = Appearance.TICKET;

    // ---------- geometry (card px) ----------
    static final float BODY_W = 600, STUB_X = 600, STUB_CX = 780, STUB_IN = 312, STUB_L = 624;
    /**
     * The crests on one centre line, y 226: a bare crest 160, a crest on its ivory disc 168 at 132. They were 176 /
     * 196 at x 170 / 430, which left 64 px between two discs: the VS mark (CardKit.vs, 59 px of ink) needs 24 px of
     * air either side (owner, fx6), so the columns moved 8 px out and the crests came down a step.
     */
    static final float CREST_BOX = 168, CREST_BARE = 160, CREST_ON_DISC = 132, CREST_CY = 226;
    /** Name columns: 232 px (x 46–278, 322–554); a name too long even at 38 px (without a shorter form) may run on to 262. */
    static final float HOME_CX = 162, AWAY_CX = 438, NAME_W = 232, NAME_OVER = 262;
    static final float EDGE_H = 14, PERF_X = 594, PERF_W = 12, DASH = 12, DASH_GAP = 14, NOTCH_R = 22;
    /** Names longer than this with a space break into two lines (CRVENA / ZVEZDA). */
    static final int TWO_LINES_OVER = 11;

    // ---------- the fonts' vertical metrics, as the reference renders laid them out (em) ----------
    static final float RC_ASC = 2146f / 2048, RC_DESC = 555f / 2048, RC_CAP = 1456f / 2048, GS_ASC = 0.966f, GS_DESC = 0.286f;

    /** The baseline of one line of text of a size in a box of a line height (CSS: half-leading above and below). */
    static float baseline(float top, float lineHeight, float size, float asc, float desc) {
        return top + lineHeight / 2 + (asc - desc) / 2 * size;
    }

    /** The baseline of a line with line-height normal (the font's ascent + descent; no line gap in these fonts). */
    static float baselineNormal(float top, float size, float asc) { return top + asc * size; }

    public String id() { return ID; }
    public boolean suits(ClubTokens t) { return true; }

    public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        drawBody(cv, m, t, art);
        drawStub(cv, m, art);
        drawPerforation(cv, t);
    }

    // ================================================================== body
    private void drawBody(Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(t.stock);
        cv.drawRect(0, 0, BODY_W, Cards.H, fill);
        int ink = t.stockInk;

        // competition (or the not-updated warning): GS Bold 46 caps, tracking .12 em, 80 % ink, fit 500 min 36
        // (spec: 36 / .14 em / 28, which read as 15 px at the launcher's unfocused 0.404)
        Paint comp = words(CardKit.text(Ui.BOLD, EYEBROW, CardKit.alpha(ink, 0.80f)));
        comp.setLetterSpacing(0.12f);
        String label = m.stale ? Cards.caps(I18n.t("not_updated").replace("⚠", "").trim()) : m.competition;
        float x = 58, maxW = 500;
        if (m.stale) {
            // amber where it reads (dark and red stocks); on a yellow or orange stock the words take the stock's ink
            // and the amber sign sits on a small dark chip
            boolean amber = amberReads(t.stock);
            comp.setColor(amber ? CardKit.AMBER : ink);
            float s = CardKit.fit(comp, label, maxW - 50, EYEBROW, EYEBROW_MIN), cap = CardKit.cap(comp), base = baselineNormal(EYEBROW_TOP, s, GS_ASC);
            float pad = amber ? 0 : cap * 0.30f;
            if (!amber) {
                Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
                chip.setColor(WARN_CHIP);
                cv.drawRoundRect(new RectF(x - pad, base - cap - pad, x + cap * 1.12f + pad, base + pad), pad, pad, chip);
            }
            Cards.warning(cv, x, base, cap, comp);
            x += cap * 1.12f + 14 + pad; maxW -= cap * 1.12f + 14 + pad;
        }
        float cs = CardKit.fit(comp, label, maxW, EYEBROW, EYEBROW_MIN);
        left(cv, comp, CardKit.cut(comp, label, maxW), x, baselineNormal(EYEBROW_TOP, cs, GS_ASC));

        // crests: bare at 176, or on an ivory disc (crest 150) when the crest's colour matches the stock (spec §1.7)
        ClubTokens.CrestInfo oppInfo = CardStyles.crest(m.oppCrest, art.oppCrest());
        Bitmap mine = art.myCrest(), opp = art.oppCrest();
        float cy = CREST_CY;
        crest(cv, m.home ? mine : opp, m.home ? t.crest : oppInfo, m.match.home, HOME_CX, cy, t.stock);
        crest(cv, m.home ? opp : mine, m.home ? oppInfo : t.crest, m.match.away, AWAY_CX, cy, t.stock);

        // VS (CardKit.vs) centred between the crests on their centre line. The body lists the clubs in match order
        // (home left, as the score in the stub), so it reads "HOME VS AWAY" whoever is at home: never "@"
        float vw = CardKit.vsWidth(CardKit.VS, CardKit.VS_SIZE);
        CardKit.vs(cv, CardKit.VS, BODY_W / 2 - vw / 2, cy, CardKit.VS_SIZE, CardKit.alpha(ink, CardKit.VS_A), false);

        // names: RC Bold 56 caps, tracking .01 em, centred in 232 boxes; long names with a space on two lines at 46
        List<String> mineNames = fullFirst(I18n.team(m.mineRaw), m.mineNames), oppNames = fullFirst(m.opponentFull, m.opponentNames);
        Name hn = name(m.home ? mineNames : oppNames, ink);
        Name an = name(m.home ? oppNames : mineNames, ink);
        if (hn.w > NAME_W) hn.shrink(homeRoom(an.w));
        if (an.w > NAME_W) an.shrink(NAME_OVER);
        hn.draw(cv, HOME_CX);
        an.draw(cv, AWAY_CX);

        // edge: the kit's second colour, 14 px along the bottom at 90 %
        fill.setColor(CardKit.alpha(t.edge, 0.90f));
        cv.drawRect(0, Cards.H - EDGE_H, BODY_W, Cards.H, fill);
    }

    /**
     * The hero's box top when nothing is captioned under it (an upcoming match: day / kick-off / channel; a live one:
     * pill / score / channel): its capitals (y 214–322) sit with equal air between the word or pill (bottom 106) and
     * the channel block (top ~430), instead of leaving a hole where a caption used to be.
     */
    static final float HERO_TOP_ALONE = 188;

    /** The competition over the body (the eyebrow): its size, its smallest and its box top (spec: 36, 28, 40). */
    static final float EYEBROW = 46, EYEBROW_MIN = 36, EYEBROW_TOP = 36;

    /** The chip under the warning sign where amber doesn't read on the stock. */
    static final int WARN_CHIP = 0xFF141416;

    /** Amber (the not-updated warning) reads on the stock: WCAG contrast 3:1 or more (not on AEK's or Aris's yellow). */
    static boolean amberReads(int stock) { return ClubTokens.contrast(CardKit.AMBER, stock) >= 3; }

    private static void crest(Canvas cv, Bitmap b, ClubTokens.CrestInfo info, String raw, float cx, float cy, int stock) {
        if (b == null) { Badges.draw(cv, raw, cx, cy, CREST_BOX / 2 - 10); return; }
        if (ClubTokens.needsDisc(info, stock)) {
            Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
            d.setColor(ClubTokens.IVORY_TICKET);
            cv.drawCircle(cx, cy, CREST_BOX / 2, d);
            CardKit.crest(cv, b, cx, cy, CREST_ON_DISC, null);
        } else CardKit.crest(cv, b, cx, cy, CREST_BARE, null);
    }

    /** The full name first (the Ticket has the room for it), then the card's names (CardModel.names). */
    static List<String> fullFirst(String full, List<String> cardNames) {
        List<String> out = new ArrayList<>();
        out.add(full);
        for (String n : cardNames) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** A club name set for its 232 px column (spec §3.4.4): one line fitted 56 → 38, or two lines at 46. */
    private static final class Name {
        String[] lines;
        Paint p;
        float size, w;

        /** A one-line name too long at 38 px runs on into maxW, smaller only if it must (to 28); never cut. */
        void shrink(float maxW) {
            if (lines.length != 1) return;
            while (w > maxW && size > 28) { p.setTextSize(size -= 2); w = p.measureText(lines[0]); }
        }

        void draw(Canvas cv, float cx) {
            if (lines.length == 2) {
                float lh = 0.95f * size, b0 = baseline(342, lh, size, RC_ASC, RC_DESC);
                centre(cv, p, lines[0], cx, b0);
                centre(cv, p, lines[1], cx, b0 + lh);
                return;
            }
            float base = baseline(352, 0.95f * size, size, RC_ASC, RC_DESC);
            if (w <= NAME_W) centre(cv, p, lines[0], cx, base);
            else left(cv, p, lines[0], cx - NAME_W / 2, base);  // as the reference: runs on from the box's left
        }
    }

    /**
     * A name by the shared rule (CardModel.fitName) with the Ticket's two-line treatment: each name in turn (the full
     * name, the card name, its short form), as two lines at 46 → 38 when it is over 11 characters with a space
     * (CRVENA / ZVEZDA), else one line at 56 → 38; the first that fits its column wins. Past them all, the shortest
     * on one line at 38, which may run on (shrink).
     */
    private static Name name(List<String> names, int ink) {
        Name r = new Name();
        r.p = CardKit.text(Ui.COND_BOLD, 56, ink);
        r.p.setLetterSpacing(0.01f);
        for (String raw : names) {
            String n = Cards.caps(raw);
            r.lines = nameLines(n);
            if (r.lines.length == 2) {
                r.size = 46;
                r.p.setTextSize(r.size);
                while ((r.p.measureText(r.lines[0]) > NAME_W || r.p.measureText(r.lines[1]) > NAME_W) && r.size > 38) r.p.setTextSize(r.size -= 2);
                r.w = Math.max(r.p.measureText(r.lines[0]), r.p.measureText(r.lines[1]));
            } else {
                r.size = CardKit.fit(r.p, n, NAME_W, 56, 38);
                r.w = r.p.measureText(n);
            }
            if (r.w <= NAME_W) return r;
        }
        String n = Cards.caps(names.get(names.size() - 1));
        r.lines = new String[]{n};
        r.size = 38;
        r.p.setTextSize(38);
        r.w = r.p.measureText(n);
        return r;
    }

    /**
     * The room for a home name too long for its column: it runs on from the column's left (x 46) as far as 20 px short
     * of the away name, at most 262 px, never less than its 232 px column.
     */
    static float homeRoom(float awayW) {
        float awayLeft = awayW <= NAME_W ? AWAY_CX - awayW / 2 : AWAY_CX - NAME_W / 2;
        return Math.max(NAME_W, Math.min(NAME_OVER, awayLeft - 20 - (HOME_CX - NAME_W / 2)));
    }

    /** A name in display capitals as the Ticket sets it: two lines (at the first space) when over 11 characters. */
    static String[] nameLines(String n) {
        int sp = n.indexOf(' ');
        if (n.length() > TWO_LINES_OVER && sp > 0) return new String[]{n.substring(0, sp), n.substring(sp + 1)};
        return new String[]{n};
    }

    // ================================================================== stub
    private void drawStub(Canvas cv, CardModel m, CardArt art) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ClubTokens.INK);
        cv.drawRect(STUB_X, 0, Cards.W, Cards.H, fill);

        boolean scores = m.homeScore != null && !m.scoresHidden;
        switch (m.state) {
            case LIVE: {  // three tiers: the pill with the clock (● LIVE 67′), the score, where to watch
                String s = livePillText(m, w -> CardKit.pillWidth(w, Ui.BOLD, PILL_MIN, true));
                float size = pillSize(s, true);
                float pw = CardKit.pillWidth(s, Ui.BOLD, size, true);
                CardKit.pill(cv, s, STUB_CX - pw / 2, 73, 66, Ui.BOLD, size, CardKit.LIVE, CardKit.WHITE, true, false);
                if (scores) score(cv, m, 150, HERO_TOP_ALONE);
                else hiddenScore(cv, 150, HERO_TOP_ALONE);  // scores hidden: blank slots (the pill keeps a clock that tells nothing)
                drawChannel(cv, m, art);
                break;
            }
            case AWAITING:
            case SUSPENDED: {  // not in play: the state word (never the LIVE pill) over the score it stands at
                float top = 140 + (topWord(cv, m.stateLine) > 1 ? WORD_TWO_SHIFT : 0);  // ΑΝΑΜΟΝΗ / ΣΚΟΡ: 176
                if (scores) score(cv, m, 150, top);
                else hiddenScore(cv, 150, top);
                break;
            }
            case RESULT: {  // three tiers: the outcome pill in the word's place (FINAL when hidden or unknown), the score, the date
                String date = m.stateExtra.isEmpty() ? m.dateLine : m.stateExtra + " · " + m.dateLine;
                if (scores && !m.outcomeWord.isEmpty()) {
                    float size = pillSize(m.outcomeWord, false);
                    float pw = CardKit.pillWidth(m.outcomeWord, Ui.BOLD, size, false);
                    CardKit.outcomePill(cv, m, STUB_CX - pw / 2, 73, 66, size, false);
                } else topWord(cv, m.stateLine);
                if (scores) score(cv, m, 124, 156);
                else hiddenScore(cv, 124, 156);  // scores hidden: blank slots; FINAL over them, the day under
                caption(cv, date, 306, 0.75f);
                break;
            }
            case OFF: {  // postponed / cancelled: TBA over the original date (40 %); abandoned: its score or kick-off, its date
                float shift = topWord(cv, m.stateLine) > 1 ? WORD_TWO_SHIFT : 0;  // ΟΡΙΣΤ. / ΔΙΑΚΟΠΗ: all 36 lower
                if (m.scoreHero) {
                    if (scores) score(cv, m, 150, 140 + shift);
                    else hiddenScore(cv, 150, 140 + shift);
                } else hero(cv, m.time, m.timeSuffix, 150, 140 + shift, 0.60f);
                caption(cv, m.dateLine, 306 + shift, m.dateDim ? 0.40f : 0.75f);
                break;
            }
            default:  // upcoming: the day as the state word (TOMORROW, SUN 22 NOV) or DELAYED, the kick-off, where to
                      // watch. No date under the word, a delayed match's day included: the launcher's description gives
                      // it ("Today 19:15 · Delayed"), and the stub stays three tiers
                float top = Math.max(HERO_TOP_ALONE, topWord(cv, m.stateLine) > 1 ? 140 + WORD_TWO_SHIFT : 0);
                hero(cv, m.time, m.timeSuffix, 150, top, m.heroDim ? 0.60f : 1f);
                drawChannel(cv, m, art);
        }
    }

    /** A pill in the word's place (LIVE with its clock, WIN / DRAW / LOSS): GS Bold 48, fit the stub's 312, min 36. */
    static final float PILL_SIZE = 48, PILL_MIN = 36;

    /**
     * The live pill's words: LIVE with the clock (LIVE 67′, LIVE Q3 5:12) when that fits the stub at 36 or more,
     * else LIVE alone (Greek LIVE ΔΙΑΛΕΙΜΜΑ, LIVE 2Η ΠΑΡ. 1:00): the launcher's description gives the clock.
     * widthAtMin: the pill's width at the smallest size.
     */
    static String livePillText(CardModel m, Measure widthAtMin) {
        String s = CardKit.liveText(m);
        return widthAtMin.width(s) <= STUB_IN ? s : m.stateLine;
    }

    private static float pillSize(String s, boolean dot) {
        float size = PILL_SIZE;
        while (CardKit.pillWidth(s, Ui.BOLD, size, dot) > STUB_IN && size > PILL_MIN) size -= 2;
        return size;
    }

    /**
     * The state word: GS Bold 48, tracking .06 em, 75 % white, centred on y 73 (the 66 px box at top 40); fit the
     * stub's 312 to 40. Under 40 a word with a space takes two lines at 48 (cy 73 and 125: ΟΡΙΣΤ. / ΔΙΑΚΟΠΗ, ΑΝΑΜΟΝΗ /
     * ΣΚΟΡ, NO RESULT / YET) and the hero under it moves down (WORD_TWO_HERO); a single word drops its tracking first
     * (ΚΑΘΥΣΤΕΡΗΣΗ: 42 instead of 38). Returns the number of lines.
     */
    private static int topWord(Canvas cv, String s) {
        final Paint p = words(CardKit.text(Ui.BOLD, WORD, CardKit.alpha(CardKit.WHITE, 0.75f)));
        Word w = fitWord(s, (x, size, track) -> { p.setTextSize(size); p.setLetterSpacing(track); return p.measureText(x); });
        p.setTextSize(w.size);
        p.setLetterSpacing(w.track);
        for (int i = 0; i < w.lines.length; i++) {
            float cy = i == 0 ? WORD_CY : WORD_CY2;
            centre(cv, p, CardKit.cut(p, w.lines[i], STUB_IN), STUB_CX, cy + (GS_ASC - GS_DESC) / 2 * w.size);
        }
        return w.lines.length;
    }

    /** The state word's size, its floor on one line, and the two lines' centres. */
    static final float WORD = 48, WORD_MIN = 40, WORD_FLOOR = 32, WORD_TRACK = 0.06f, WORD_CY = 73, WORD_CY2 = 125;

    /**
     * Under a two-line word the hero's box top moves from 140 to 176 (its capitals ~203, 60 under the second line) and a
     * caption under it from 306 to 342: the same air, 36 px lower.
     */
    static final float WORD_TWO_SHIFT = 36;

    /** How the state word is set: its lines, size and tracking. */
    static final class Word {
        final String[] lines;
        final float size, track;
        Word(String[] lines, float size, float track) { this.lines = lines; this.size = size; this.track = track; }
    }

    /**
     * The state word in the stub's 312: one line at 48 → 40 tracked .06; then, with a space, two lines (split where
     * the longer line is shortest) at 48, untracked if the tracked lines don't fit, down to 40; a single word untracked
     * 48 → 40, then to the old floor 32 (no real state needs it). Pure arithmetic over m: unit-tested.
     */
    static Word fitWord(String s, ClashStyle.Measure2 m) {
        for (float size = WORD; size >= WORD_MIN; size -= 2)
            if (m.width(s, size, WORD_TRACK) <= STUB_IN) return new Word(new String[]{s}, size, WORD_TRACK);
        final float[] probe = {WORD};
        String[] two = s.indexOf(' ') > 0 ? ClashStyle.split(s, x -> m.width(x, probe[0], WORD_TRACK)) : null;
        if (two != null)
            for (float t : new float[]{WORD_TRACK, 0})
                for (float size = WORD; size >= WORD_MIN; size -= 2)
                    if (m.width(two[0], size, t) <= STUB_IN && m.width(two[1], size, t) <= STUB_IN) return new Word(two, size, t);
        float size = WORD;
        while (size > WORD_FLOOR && m.width(s, size, 0) > STUB_IN) size -= 2;
        return new Word(new String[]{s}, size, 0);
    }

    /**
     * The caption: GS Bold 50, tracking .06 em, white at an alpha, box top; fit the stub's inner 312; min 40 (spec:
     * 40 / .08 em / 30, which read as 16 px at the launcher's unfocused 0.404). Only a long segment (AET · MON 9 NOV)
     * goes under 40.
     */
    static final float CAPTION = 50, CAPTION_MIN = 32;

    private static void caption(Canvas cv, String s, float top, float alpha) {
        if (s == null || s.isEmpty()) return;
        Paint p = words(CardKit.text(Ui.BOLD, CAPTION, CardKit.alpha(CardKit.WHITE, alpha)));
        p.setLetterSpacing(0.06f);
        float sz = CardKit.fit(p, s, STUB_IN, CAPTION, CAPTION_MIN);
        centre(cv, p, CardKit.cut(p, s, STUB_IN), STUB_CX, baselineNormal(top, sz, GS_ASC));
    }

    /**
     * The hero text (kick-off, TBA): RC Bold at size, box top, line-height 1, centred on the stub; wider than the
     * stub's inner 312 px it shrinks (13:35 and 10:00 PM used to run to x 954, 6 px from the card's edge). A 12-hour
     * suffix (PM) follows small, on the same baseline.
     */
    private static void hero(Canvas cv, String s, String suffix, float size, float top, float alpha) {
        if (s == null || s.isEmpty()) return;
        Paint p = CardKit.text(Ui.COND_BOLD, size, CardKit.alpha(CardKit.WHITE, alpha));
        p.setLetterSpacing(-0.01f);
        Paint sp = null;
        float gap = 0;
        if (suffix != null && !suffix.isEmpty()) {
            sp = CardKit.text(Ui.BOLD, size * 0.30f, CardKit.alpha(CardKit.WHITE, 0.75f * alpha));
            sp.setLetterSpacing(0.04f);
            gap = size * 0.06f;
        }
        float sz = size, maxW = STUB_IN;  // inside the stub's inner column (624-936), never to the card's edge
        while (true) {
            p.setTextSize(sz);
            if (sp != null) sp.setTextSize(sz * 0.30f);
            float w = p.measureText(s) + (sp == null ? 0 : gap * sz / size + sp.measureText(suffix));
            if (w <= maxW || sz <= 56) break;
            sz -= 2;
        }
        float w = p.measureText(s) + (sp == null ? 0 : gap * sz / size + sp.measureText(suffix));
        float x = STUB_CX - w / 2;
        float base = baseline(top, sz, sz, RC_ASC, RC_DESC);
        left(cv, p, s, x, base);
        if (sp != null) left(cv, sp, suffix, x + p.measureText(s) + gap * sz / size, base);
    }

    /**
     * The score, home first: my number white, theirs at 70 %, the en dash at 45 % with .02 em margins; fit 312 min 80
     * (100–105 needs 88 to stay inside the stub's column).
     */
    private static void score(Canvas cv, CardModel m, float size, float top) {
        Paint me = CardKit.text(Ui.COND_BOLD, size, CardKit.WHITE);
        me.setLetterSpacing(-0.01f);
        Paint them = CardKit.text(Ui.COND_BOLD, size, CardKit.alpha(CardKit.WHITE, 0.70f));
        them.setLetterSpacing(-0.01f);
        Paint dash = CardKit.text(Ui.COND_BOLD, size, CardKit.alpha(CardKit.WHITE, 0.45f));
        dash.setLetterSpacing(-0.01f);
        Paint hp = m.home ? me : them, ap = m.home ? them : me;
        String h = m.homeScore, a = m.awayScore, d = "–";
        float sz = size, w;
        while (true) {
            hp.setTextSize(sz); ap.setTextSize(sz); dash.setTextSize(sz);
            w = hp.measureText(h) + ap.measureText(a) + dash.measureText(d) + 0.04f * sz;
            if (w <= STUB_IN || sz <= 80) break;
            sz -= 2;
        }
        float x = w <= STUB_IN ? STUB_CX - w / 2 : STUB_L;
        float base = baseline(top, sz, sz, RC_ASC, RC_DESC);
        left(cv, hp, h, x, base);
        x += hp.measureText(h) + 0.02f * sz;
        left(cv, dash, d, x, base);
        x += dash.measureText(d) + 0.02f * sz;
        left(cv, ap, a, x, base);
    }

    /** Scores hidden: the shared blank slots and dash, centred on the stub where the score's capitals would be. */
    private static void hiddenScore(Canvas cv, float size, float top) {
        CardKit.hiddenScore(cv, STUB_CX, baseline(top, size, size, RC_ASC, RC_DESC), size, Paint.Align.CENTER);
    }

    // ================================================================== channel
    /**
     * Where to watch, at the foot of the stub (spec §3.4.6). Confirmed: the logo (56 px tall when wider than 3:1, else
     * 72; at most 250 wide) and the number chip, centred on the stub, bottom 36. Likely: LIKELY (GS Bold 38, tracking
     * .10 em, 62 %) over the logo, bottom 28, gap 8. Two channels (Champions League: Magenta Sport and MEGA): side by
     * side, each narrowed until the row fits the stub. A logo that wouldn't read (CardKit.logoFit: nearer square than
     * 2.5:1, or under 44 px tall even given the row's free width) is its name in type in the row (MEGA); a row with no
     * logo left, a channel without a logo and abroad: the names as words (wordsBlock).
     */
    private static void drawChannel(Canvas cv, CardModel m, CardArt art) {
        CardModel.Channel ch = m.channel;
        if (ch == null) return;
        Paint likely = CardKit.text(Ui.BOLD, LIKELY, CardKit.alpha(CardKit.WHITE, 0.62f));
        likely.setLetterSpacing(0.10f);
        String lw = CardModel.w("likely");
        List<Channels.Logo> logos = ch.namesOnly ? new ArrayList<Channels.Logo>() : art.logos();
        int n = Math.min(2, Math.min(ch.names.size(), logos.size()));
        Bitmap[] img = new Bitmap[n];
        int[] no = new int[n];
        float[] ar = new float[n];
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            img[i] = art.trimmed(logos.get(i).url);
            no[i] = logos.get(i).no;
            ar[i] = img[i] == null ? 0 : img[i].getWidth() / (float) img[i].getHeight();
            names[i] = Cards.caps(ch.names.get(i));
        }
        Paint num = words(CardKit.text(Ui.BOLD, CHIP, CardKit.WHITE));
        float chipH = CHIP * (GS_ASC + GS_DESC) + 7;
        float[] chipW = new float[n];
        for (int i = 0; i < n; i++) chipW[i] = no[i] > 0 ? num.measureText(String.valueOf(no[i])) + 28 : 0;
        // the names a row may set in type: tracked .06, then untracked if that is what lets a logo keep 44 px
        Paint np = words(CardKit.text(Ui.BOLD, WORDS, CardKit.alpha(CardKit.WHITE, 0.90f)));
        float[][] row = null;
        for (float t : new float[]{WORDS_TRACK, 0}) {
            if (n == 0) break;
            np.setLetterSpacing(t);
            float[] wordW = new float[n];
            for (int i = 0; i < n; i++) wordW[i] = np.measureText(names[i]);
            row = logoRow(ar, wordW, chipW, n);
            if (row != null) break;
        }
        if (row == null) { wordsBlock(cv, ch, n, no, likely, lw); return; }
        // row[i] = {box width, box height, drawn width, drawn height, 1 = the name in type}; the row centred on the stub
        float total = 0, rowH = 0;
        for (int i = 0; i < n; i++) {
            total += row[i][0] + (chipW[i] > 0 ? CHIP_GAP + chipW[i] : 0) + (i > 0 ? LOGO_GAP : 0);
            rowH = Math.max(rowH, Math.max(row[i][1], chipW[i] > 0 ? chipH : 0));
        }
        float cy = ch.likely ? 512 - rowH / 2 : 504 - rowH / 2;
        float x = STUB_CX - total / 2;
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        chip.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
        for (int i = 0; i < n; i++) {
            if (i > 0) x += LOGO_GAP;
            float bw = row[i][0], dw = row[i][2], dh = row[i][3];
            if (row[i][4] > 0) left(cv, np, names[i], x, cy + CardKit.cap(np) / 2);
            else Cards.drawBitmap(cv, img[i], new RectF(x + (bw - dw) / 2, cy - dh / 2, x + (bw + dw) / 2, cy + dh / 2), lp);
            x += bw;
            if (chipW[i] > 0) {
                float cx0 = x + CHIP_GAP, ct = cy - chipH / 2;
                cv.drawRoundRect(new RectF(cx0, ct, cx0 + chipW[i], ct + chipH), 10, 10, chip);
                cv.drawText(String.valueOf(no[i]), cx0 + 14, ct + 5 + GS_ASC * CHIP, num);
                x = cx0 + chipW[i];
            }
        }
        if (ch.likely) {
            float lineTop = cy - rowH / 2 - 8 - LIKELY * (GS_ASC + GS_DESC);
            centre(cv, likely, lw, STUB_CX, baselineNormal(lineTop, LIKELY, GS_ASC));
        }
    }

    /**
     * The channels as words (abroad, a channel without a logo, or a row no logo of which reads): GS Bold 44 (never
     * under: LAYOUT-CALM §1.4), 90 %, set from the bottom (512), 1.2 em apart. On one line as written (tracked .06, else untracked)
     * with LIKELY (38, as over a logo) over it; otherwise without LIKELY, one channel a line with " /" after all but the
     * last (the launcher's description keeps "Likely …"), a long name wrapped by words (nameLines). A confirmed
     * channel's number sits in its chip after the last line, or on a line of its own.
     */
    private static void wordsBlock(Canvas cv, CardModel.Channel ch, int n, int[] no, Paint likely, String lw) {
        CardModel.Channel names = ch.namesOnly ? ch : firstTwo(ch);
        final Paint np = words(CardKit.text(Ui.BOLD, WORDS, CardKit.alpha(CardKit.WHITE, 0.90f)));
        Measure2 mw = (s, t) -> { np.setLetterSpacing(t); return np.measureText(s); };
        List<String> caps = new ArrayList<>();
        for (String s : names.names) caps.add(Cards.caps(s));
        Lines l = nameLines(names.words(), caps, STUB_IN, mw);
        boolean hedge = ch.likely && l.lines.size() == 1;
        List<String> lines = l.lines;
        np.setLetterSpacing(l.track);
        float ws = WORDS;
        int number = number(ch, n, no);
        Paint cn = words(CardKit.text(Ui.BOLD, CHIP, CardKit.WHITE));
        String nt = number > 0 ? String.valueOf(number) : "";
        float cw = nt.isEmpty() ? 0 : cn.measureText(nt) + 20;
        if (cw > 0 && np.measureText(lines.get(lines.size() - 1)) + 12 + cw > STUB_IN) lines.add("");
        float lh = ws * 1.2f, lead = (lh - ws * (GS_ASC + GS_DESC)) / 2;
        float top = 512 - lh * lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            float base = top + lh * i + lead + GS_ASC * ws;
            if (i < lines.size() - 1 || cw == 0) { centre(cv, np, CardKit.cut(np, s, STUB_IN), STUB_CX, base); continue; }
            float lw0 = s.isEmpty() ? 0 : np.measureText(s) + 12, x0 = STUB_CX - (lw0 + cw) / 2;
            if (!s.isEmpty()) left(cv, np, s, x0, base);
            Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
            chip.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
            float c = CardKit.cap(np), ct = base - c / 2 - CHIP_WORDS_H / 2;  // the chip as tall as the words need, clear of the line above
            cv.drawRoundRect(new RectF(x0 + lw0, ct, x0 + lw0 + cw, ct + CHIP_WORDS_H), 8, 8, chip);
            cv.drawText(nt, x0 + lw0 + 10, base - c / 2 + CardKit.cap(cn) / 2, cn);
        }
        if (hedge) {
            likely.setTextSize(LIKELY);
            centre(cv, likely, lw, STUB_CX, top - 8 - lh + lead + GS_ASC * ws);
        }
    }

    /** Measures a line at a tracking (a Paint on the device; fixed advances in tests). */
    interface Measure2 { float width(String s, float track); }

    /** The words block's lines and tracking. */
    static final class Lines {
        final List<String> lines;
        final float track;
        Lines(List<String> lines, float track) { this.lines = lines; this.track = track; }
    }

    /** A channel's name with " /" glued to it by a no-break space when another follows, so wrap never breaks there. */
    private static String glued(List<String> names, int i) { return names.get(i) + (i < names.size() - 1 ? SLASH : ""); }

    static final String SLASH = "\u00A0/";

    /**
     * The channels' lines in the stub: the line as written (full, "LIKELY" goes over it) on one line, tracked .06 or
     * else untracked; otherwise one channel a line, " /" (glued by a no-break space) after all but the last, a name
     * too long for a line wrapped by words, tracked or untracked, whichever needs fewer lines with every line inside
     * max (tracked on a tie). Pure arithmetic over m: unit-tested.
     */
    static Lines nameLines(String full, List<String> names, float max, Measure2 m) {
        for (float t : new float[]{WORDS_TRACK, 0})
            if (m.width(full, t) <= max) return new Lines(new ArrayList<>(java.util.Collections.singletonList(full)), t);
        Lines best = null;
        for (final float t : new float[]{WORDS_TRACK, 0}) {
            Measure one = s -> m.width(s, t);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) out.addAll(wrap(glued(names, i), max, one));
            boolean fits = true;
            for (String s : out) fits &= one.width(s) <= max;
            if (fits && (best == null || out.size() < best.lines.size())) best = new Lines(out, t);
        }
        if (best != null) return best;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) out.addAll(wrap(glued(names, i), max, s -> m.width(s, 0)));
        return new Lines(out, 0);
    }

    /**
     * The stub's channel type: LIKELY GS Bold 38 (spec 30), the number chip 42 (spec 34), names in type 44 tracked .06
     * (spec 30; 38 until fx5, now on the 44 px floor every other style keeps).
     */
    static final float LIKELY = 38, CHIP = 42, WORDS = 44, WORDS_TRACK = 0.06f;

    /** The number chip after a line of words: 44 tall (the line's capitals and 6 px a side), its figures at CHIP. */
    static final float CHIP_WORDS_H = 44;

    /** The number in a chip after the words: a confirmed single channel's (a guide's listing abroad). */
    private static int number(CardModel.Channel ch, int n, int[] no) { return !ch.namesOnly && ch.names.size() == 1 && n == 1 ? no[0] : 0; }

    /** Between a logo and its number chip; between two channels' logos. */
    static final float CHIP_GAP = 14, LOGO_GAP = 24;
    /** The narrowest a logo's box may get when two share the stub; below that the channels are written as words. */
    static final float LOGO_MIN_W = 96;

    /** The channel with its first two names only (the stub has room for two). */
    private static CardModel.Channel firstTwo(CardModel.Channel ch) {
        CardModel.Channel c = new CardModel.Channel();
        c.likely = ch.likely;
        c.names.addAll(ch.names.subList(0, Math.min(2, ch.names.size())));
        return c;
    }

    /**
     * The row's items {box width, box height, drawn width, drawn height, 1 = the name in type} for logos of aspect
     * ratios ar (0: no logo), their names' widths in type (wordW) and their number chips' widths (chipW, 0: none),
     * n = 1 or 2, inside the stub's 312 px. One logo: 56 px tall when wider than 3:1, else 72, at most 250 wide, capped at
     * 200 then 180 beside a chip (spec §1.6); two: the same heights, the width cap lowered 10 px at a time from 150 until
     * the row fits (not under LOGO_MIN_W). A logo that wouldn't read (CardKit.logoFit: nearer square than 2.5:1, or under
     * 44 px tall even widened into the row's free width) is set as its name instead. null when no logo is left or the
     * row can't fit: the caller writes the channels as words. Pure arithmetic: unit-tested.
     */
    static float[][] logoRow(float[] ar, float[] wordW, float[] chipW, int n) {
        boolean[] word = new boolean[n];
        for (int i = 0; i < n; i++) word[i] = !(ar[i] >= CardKit.LOGO_MIN_ASPECT);
        while (true) {
            boolean any = false;
            for (int i = 0; i < n; i++) any |= !word[i];
            if (!any) return null;
            float[][] out = row(ar, wordW, chipW, word, n);
            if (out == null) return null;
            float total = rowWidth(out, chipW, n);
            int unread = -1;  // of the logos that can't reach 44 px, the smallest goes to type first (MEGA before Magenta)
            for (int i = 0; i < n; i++) {
                if (word[i] || out[i][3] >= CardKit.LOGO_MIN_H) continue;
                float[] f = CardKit.logoFit(ar[i], out[i][0], out[i][1], STUB_IN - (total - out[i][0]));
                if (f == null && (unread < 0 || out[i][3] < out[unread][3])) unread = i;
            }
            if (unread >= 0) { word[unread] = true; continue; }
            for (int i = 0; i < n; i++) {  // every short logo can reach 44 px: widen them into the free room
                if (word[i] || out[i][3] >= CardKit.LOGO_MIN_H) continue;
                float[] f = CardKit.logoFit(ar[i], out[i][0], out[i][1], STUB_IN - (total - out[i][0]));
                if (f == null) return null;
                total += f[0] - out[i][0]; out[i][0] = f[0]; out[i][2] = f[0]; out[i][3] = f[1];
            }
            return total <= STUB_IN || n == 1 ? out : null;
        }
    }

    /** The row at the widest cap that fits (logoRow's arithmetic before legibility); null when two can't share it. */
    private static float[][] row(float[] ar, float[] wordW, float[] chipW, boolean[] word, int n) {
        float[][] out = new float[n][5];
        float[] caps = n == 1 ? new float[]{250, 200, 180} : null;
        float cap = n == 1 ? 250 : 150;
        for (int step = 0; ; step++) {
            for (int i = 0; i < n; i++) {
                if (word[i]) { out[i] = new float[]{wordW[i], WORD_ROW_H, wordW[i], WORD_ROW_H, 1}; continue; }
                float boxH = ar[i] > 3 ? 56 : 72, boxW = Math.min(cap, boxH * ar[i]);
                float dw = Math.min(boxW, boxH * ar[i]), dh = dw / ar[i];
                out[i] = new float[]{boxW, boxH, dw, dh, 0};
            }
            if (rowWidth(out, chipW, n) <= STUB_IN) return out;
            if (n == 1) {
                if (step + 1 >= caps.length) return out;  // as before: one logo is always drawn (180 wide at most)
                cap = caps[step + 1];
            } else {
                cap -= 10;
                if (cap < LOGO_MIN_W) return null;
            }
        }
    }

    /** A name's line box in the row (GS Bold 44, line-height normal). */
    static final float WORD_ROW_H = WORDS * (GS_ASC + GS_DESC);

    private static float rowWidth(float[][] out, float[] chipW, int n) {
        float total = 0;
        for (int i = 0; i < n; i++) total += out[i][0] + (chipW[i] > 0 ? CHIP_GAP + chipW[i] : 0) + (i > 0 ? LOGO_GAP : 0);
        return total;
    }

    /** Measures a line of text (a Paint on the device; a fixed advance in tests). */
    interface Measure { float width(String s); }

    /** Greedy word wrap into lines no wider than maxW (a single word wider than maxW keeps its own line). */
    static List<String> wrap(String text, float maxW, Measure m) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && m.width(next) > maxW) { out.add(line.toString()); line.setLength(0); line.append(word); }
            else { line.setLength(0); line.append(next); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ================================================================== perforation
    /**
     * The tear line, printed in the ticket's own inks (FB-7): a column of 12 px dashes (12 on, 14 off) at x 594–606 and
     * two 44 px notches at the seam. They used to be painted in the launcher's background (#1B2024) to look cut out,
     * but the launcher never shows a card's transparency or its wallpaper: Android TV's home screen loads every poster
     * through AddBackgroundColorTransformation, which flattens it onto its own #212121 (TVLauncher 'program_default_
     * background'), and the wallpaper behind a row changes (maroon, blue, a photo). So a cut-out reads as a grey
     * sticker. Printed instead: the stock deepened on the body side, the stub's ink lifted on the stub side, so the
     * notches read as a punched edge in shadow and the dashes as perforation, on any wallpaper.
     */
    private static void drawPerforation(Canvas cv, ClubTokens t) {
        int body = perfBody(t.stock), stub = perfStub();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        cv.save();
        cv.clipRect(0, 0, STUB_X, Cards.H);  // the body's half: deepened stock
        p.setColor(body);
        for (float y = 0; y < Cards.H; y += DASH + DASH_GAP) cv.drawRect(PERF_X, y, PERF_X + PERF_W, Math.min(y + DASH, Cards.H), p);
        cv.drawCircle(STUB_X, 0, NOTCH_R, p);
        cv.drawCircle(STUB_X, Cards.H, NOTCH_R, p);
        cv.restore();
        cv.save();
        cv.clipRect(STUB_X, 0, Cards.W, Cards.H);  // the stub's half: its ink, lifted
        p.setColor(stub);
        for (float y = 0; y < Cards.H; y += DASH + DASH_GAP) cv.drawRect(PERF_X, y, PERF_X + PERF_W, Math.min(y + DASH, Cards.H), p);
        cv.drawCircle(STUB_X, 0, NOTCH_R, p);
        cv.drawCircle(STUB_X, Cards.H, NOTCH_R, p);
        cv.restore();
    }

    /** The perforation on the body: the stock deepened 45 % toward the stub's ink (a black or grey stock is lifted 10 % instead). */
    static int perfBody(int stock) {
        return ClubTokens.lum(stock) < 0.18 && ClubTokens.saturation(stock) < 0.3 ? ClubTokens.mix(stock, 0xFFFFFFFF, 0.10) : ClubTokens.mix(stock, ClubTokens.INK, 0.45);
    }

    /** The perforation on the stub: its ink lifted 10 % toward white. */
    static int perfStub() { return ClubTokens.mix(ClubTokens.INK, 0xFFFFFFFF, 0.10); }

    // ================================================================== text helpers
    /** Words in Google Sans keep proportional figures (THU 12 NOV, 318), as the reference sets them; only the RC
     *  numerals (kick-off, score) are tabular. */
    private static Paint words(Paint p) { p.setFontFeatureSettings(null); return p; }

    /**
     * Text from a left edge. Android spreads letter spacing half before and half after each glyph, the browser puts it
     * all after: the half before is taken back so the glyphs land where the reference has them.
     */
    private static void left(Canvas cv, Paint p, String s, float x, float base) {
        cv.drawText(s, x - p.getLetterSpacing() * p.getTextSize() / 2, base, p);
    }

    /** Text centred on cx, the trailing letter spacing counted in the width as the browser does. */
    private static void centre(Canvas cv, Paint p, String s, float cx, float base) {
        left(cv, p, s, cx - p.measureText(s) / 2, base);
    }
}
