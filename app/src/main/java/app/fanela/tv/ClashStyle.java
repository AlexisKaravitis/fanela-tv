package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Clash (concept 15, "Rival clash", laid out to LAYOUT-CALM): the two clubs' kit colours collide on a diagonal, each
 * crest faces the other from its own colour, and the time or the score sits in the dark gap between them. Flat: two
 * fields, the ink gap and, on a near-black field, a seam in the club's other colour; no gradient, glow or shadow.
 *
 * The gap is a parallelogram 390 wide sloping 220 px over the card's height (x 395–785 at the top, 175–565 at the
 * bottom), so the card reads the same turned half round: the home field is widest at the top, the away field at the
 * bottom. Three rows on that symmetry: row A (centre y 77) holds the home club's name at the left safe edge and the
 * state (the day, LIVE, the outcome) in the gap; the middle (y 270) the crests at the safe edges and the hero between
 * them; row C (centre y 463) where to watch (or the date) in the gap and the away club's name at the right safe edge.
 * Each item in the gap is centred on the gap's axis at its own height, so the column slides down the diagonal. Nothing
 * comes closer than 24 px to a colour edge, the safe insets (56 / 44) or another item; the competition and a soon
 * match's date are in the launcher's title and description, as on every style.
 */
final class ClashStyle implements CardStyle {
    static final String ID = Appearance.CLASH;

    public String id() { return ID; }

    /** Crests sit at 168 px on their colour (or a disc): any crest the feeds send reads at that size. */
    public boolean suits(ClubTokens t) { return true; }

    // ---------- the diagonal (card px) ----------
    /** The gap: 390 wide, its edges sloping 220 px left over the card's height; symmetric about the card's centre. */
    static final float GAP_W = 390, SLOPE = 220, GAP_TOP = (Cards.W - GAP_W + SLOPE) / 2, K = SLOPE / Cards.H;
    /** 24 px from a colour edge, measured square to the edge: 25.9 px across. */
    static final float CLEAR = 24, CLEAR_X = (float) (CLEAR * Math.sqrt(1 + K * K));
    /** The seam on a near-black field: 10 px square to the edge, inside the field. */
    static final float SEAM = 10, SEAM_X = (float) (SEAM * Math.sqrt(1 + K * K));
    static final float SAFE_L = 56, SAFE_R = Cards.W - 56, SAFE_T = 44, SAFE_B = Cards.H - 44;
    /** Row A's and row C's centre lines (turned half round, one is the other) and the middle (crests and hero). */
    static final float ROW_A = 77, ROW_C = Cards.H - ROW_A, MID = Cards.H / 2f;

    /** The gap's left edge at height y (the home field ends here). */
    static float leftEdge(float y) { return GAP_TOP - K * y; }
    /** The gap's right edge at height y (the away field starts here). */
    static float rightEdge(float y) { return leftEdge(y) + GAP_W; }
    /** The gap's centre line at height y: every item in the gap is centred on it at its own height. */
    static float axis(float y) { return leftEdge(y) + GAP_W / 2; }
    /** The widest box h tall that fits in the gap 24 px from both edges (its top-left and bottom-right corners). */
    static float room(float h) { return GAP_W - K * h - 2 * CLEAR_X; }

    // ---------- type and sizes ----------
    /** Roboto Condensed's capital height (em): the names' and numerals' rows are set by it. */
    static final float RC_CAP = 1456f / 2048;
    /** Row A's words (the day, the state) and row C's dates: GS Bold caps, 48 → 44, two lines, then 40 only for a long word. */
    static final float WORD = 48, WORD_MIN = 44, WORD_FLOOR = 40, WORD_TRACK = 0.06f, WORD_A = 0.85f, DATE_A = 0.60f, DATE_DIM_A = 0.45f;
    /** The pills (LIVE, the outcome): 66 tall on row A, GS Bold 48 → 44. */
    static final float PILL_H = 66, PILL = 48, PILL_MIN = 44;
    /** The hero: RC Bold 136 (the concept's numeral), fitted to the gap at its height; a three-digit score goes to 84. */
    static final float HERO = 136, TIME_MIN = 100, SCORE_MIN = 84, HIDDEN = 118, THEM_A = 0.70f, DASH_A = 0.45f;
    /** The live clock under the score when the pill can't hold it (Q3 5:12 in the gap): GS Bold 44 at 75 %. */
    static final float CLOCK = 44, CLOCK_A = 0.75f;
    /** The names: RC Bold caps 56 → 48 (the floor 40 only for a name no club has), lines 1.04 em apart. */
    static final float NAME = 56, NAME_TWO = 52, NAME_MIN = 48, NAME_FLOOR = 40, NAME_PITCH = 1.04f;
    /** The crests: 168 boxes at the safe edges on the middle line (158 when a field has a seam); 82 % of a disc. */
    static final float CREST = 168, DISC_CREST = 0.82f;
    /** Row C: where to watch through the shared channel line (RC Regular 48 → 44, logos ≤ 52 tall), or words (never under 44). */
    static final float FOOT = 48, FOOT_MIN = CardKit.CHANNEL_FLOOR, LOGO_H = 52, WORDS_A = 0.72f;

    // ---------- the colour-clash rules (concept fields()) ----------
    /** A white kit's field (capped: never the brightest thing in a launcher row), a black kit's, the neutral fallback. */
    static final int GREY = 0xFFB8BCC4, CHARCOAL = 0xFF1E2126, SLATE = 0xFF2F343B, DARK_INK = 0xFF111111;
    /** Two fields are told apart above this distance (hue, lightness and saturation, the concept's metric). */
    static final double DISTINCT = 0.045;
    /** Ink on a field: white under this luminance (WCAG), else #111. */
    static final double INK_SWITCH = 0.35;
    /** A chromatic field is deepened until it is no brighter than the white kit's grey (Aris's yellow). */
    static final double LUM_CAP = ClubTokens.luminance(GREY);

    /**
     * A kit colour as a field: white (and cream) as the capped grey; a near-black as charcoal, so the ink gap still
     * reads darker; a very dark colour that is still a colour (navy) lifted until it separates from the gap; any other
     * colour as the club wears it, deepened only where it is brighter than the grey.
     */
    static int field(int kit) {
        int c = ClubTokens.opaque(kit);
        if (ClubTokens.kitClass(c) == ClubTokens.Kit.WHITE) return GREY;
        if (ClubTokens.luminance(c) < 0.03) {
            if (ClubTokens.saturation(c) < 0.35) return CHARCOAL;
            for (int i = 0; i < 12 && ClubTokens.luminance(c) < 0.035; i++) c = ClubTokens.mix(c, 0xFFFFFFFF, 0.05);
            return c;
        }
        for (int i = 0; i < 16 && ClubTokens.luminance(c) > LUM_CAP; i++) c = ClubTokens.mix(c, 0xFF000000, 0.05);
        return c;
    }

    /** The concept's crude perceptual distance: hue (weighted by the lesser saturation), lightness and saturation (HLS). */
    static double dist(int a, int b) {
        double[] x = hls(a), y = hls(b);
        double dh = Math.min(Math.abs(x[0] - y[0]), 1 - Math.abs(x[0] - y[0])) * Math.sqrt(Math.min(x[2], y[2]));
        return Math.pow(dh * 3, 2) + Math.pow(x[1] - y[1], 2) + Math.pow((x[2] - y[2]) * 0.5, 2);
    }

    /** HLS as Python's colorsys: hue 0–1, lightness, saturation. */
    static double[] hls(int c) {
        double r = ClubTokens.r(c) / 255.0, g = ClubTokens.g(c) / 255.0, b = ClubTokens.b(c) / 255.0;
        double mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), l = (mx + mn) / 2;
        if (mx == mn) return new double[]{0, l, 0};
        double s = l <= 0.5 ? (mx - mn) / (mx + mn) : (mx - mn) / (2 - mx - mn);
        double rc = (mx - r) / (mx - mn), gc = (mx - g) / (mx - mn), bc = (mx - b) / (mx - mn), h;
        if (r == mx) h = bc - gc; else if (g == mx) h = 2 + rc - bc; else h = 4 + gc - rc;
        h = (h / 6) % 1;
        if (h < 0) h += 1;
        return new double[]{h, l, s};
    }

    /** White type on a dark field, #111 on a light one. */
    static int ink(int field) { return ClubTokens.luminance(field) < INK_SWITCH ? CardKit.WHITE : DARK_INK; }

    /** The two fields in match order, and the seam each near-black field carries (0: none). */
    static final class Fields {
        int home, away, homeSeam, awaySeam;
    }

    /**
     * The colour-clash rules: my field is my kit's main colour; the opponent's is the first of their main colour, their
     * second, slate #2F343B and the grey that is distinct from mine (Olympiacos v Crvena Zvezda: red v grey; PAOK v OFI:
     * charcoal v grey; Real Madrid v Tottenham: grey v navy), slate if none is. An opponent whose colours aren't known
     * (opp null) is slate (or grey beside a dark field). A near-black field carries a seam in the club's other colour
     * along its edge, so it parts from the gap and a black-and-white club reads as one.
     */
    static Fields fields(int myMain, int mySecond, int[] opp, boolean mineHome) {
        int mine = field(myMain);
        int[] cand = opp == null ? new int[]{SLATE, GREY} : new int[]{field(opp[0]), field(opp[1]), SLATE, GREY};
        int theirs = SLATE, used = -1;
        for (int i = 0; i < cand.length; i++) if (dist(mine, cand[i]) > DISTINCT) { theirs = cand[i]; used = i; break; }
        int mySeam = mine == CHARCOAL ? seam(mySecond) : 0;
        // their seam: the colour their field doesn't use (PAOK: white; AC Milan on its second, black: red)
        int theirSeam = opp != null && theirs == CHARCOAL && used <= 1 ? seam(used == 1 ? opp[0] : opp[1]) : 0;
        Fields f = new Fields();
        f.home = mineHome ? mine : theirs;
        f.away = mineHome ? theirs : mine;
        f.homeSeam = mineHome ? mySeam : theirSeam;
        f.awaySeam = mineHome ? theirSeam : mySeam;
        return f;
    }

    /** A seam's colour: the club's other colour as it wears it; none when that is dark too. */
    static int seam(int c) {
        c = ClubTokens.opaque(c);
        return dist(c, CHARCOAL) > DISTINCT && ClubTokens.luminance(c) >= 0.03 ? c : 0;
    }

    /** The opponent's kit when it is known (the colour table, or a crest that is clearly one colour); else null. */
    static int[] opponentKit(String raw, ClubTokens.CrestInfo crest) {
        if (Badges.known(raw) || (crest != null && "chromatic".equals(crest.dominant))) return CardStyles.opponentKit(raw, crest);
        return null;
    }

    // ---------- drawing ----------

    public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
        Bitmap myCrest = art.myCrest(), oppCrest = art.oppCrest();
        ClubTokens.CrestInfo oppInfo = CardStyles.crest(m.oppCrest, oppCrest);
        Fields f = fields(t.prim, t.sec, opponentKit(m.opponentRaw, oppInfo), m.home);
        float seamL = f.homeSeam != 0 ? SEAM_X : 0, seamR = f.awaySeam != 0 ? SEAM_X : 0;

        // 1. the ink gap, the two fields, the seams
        cv.drawColor(ClubTokens.INK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(f.home);
        cv.drawPath(quad(0, leftEdge(0), leftEdge(Cards.H), 0), p);
        p.setColor(f.away);
        cv.drawPath(quad(rightEdge(0), Cards.W, Cards.W, rightEdge(Cards.H)), p);
        if (f.homeSeam != 0) { p.setColor(f.homeSeam); cv.drawPath(quad(leftEdge(0) - SEAM_X, leftEdge(0), leftEdge(Cards.H), leftEdge(Cards.H) - SEAM_X), p); }
        if (f.awaySeam != 0) { p.setColor(f.awaySeam); cv.drawPath(quad(rightEdge(0), rightEdge(0) + SEAM_X, rightEdge(Cards.H) + SEAM_X, rightEdge(Cards.H)), p); }

        // 2. the crests facing off at the safe edges, home on the left (as the score reads)
        float cs = crestSize(seamL, seamR);
        Bitmap homeCrest = m.home ? myCrest : oppCrest, awayCrest = m.home ? oppCrest : myCrest;
        ClubTokens.CrestInfo homeInfo = m.home ? t.crest : oppInfo, awayInfo = m.home ? oppInfo : t.crest;
        crest(cv, homeCrest, homeInfo, m.home ? m.mineRaw : m.opponentRaw, SAFE_L + cs / 2, cs, f.home);
        crest(cv, awayCrest, awayInfo, m.home ? m.opponentRaw : m.mineRaw, SAFE_R - cs / 2, cs, f.away);

        // 3. the names: home in row A at the left safe edge, away in row C at the right one, one size for both
        names(cv, m.home ? m.mineNames : m.opponentNames, m.home ? m.opponentNames : m.mineNames,
            ink(f.home), ink(f.away), seamL, seamR);

        // 4. the gap: the state, the hero, where to watch
        boolean scores = m.scoreHero && m.homeScore != null && !m.scoresHidden;
        boolean hiddenScore = m.scoreHero && !scores;
        String clockBelow = rowA(cv, m, hiddenScore);
        float above = hero(cv, m, scores, hiddenScore);
        if (!clockBelow.isEmpty()) above = clock(cv, clockBelow, above);
        rowC(cv, m, art, above);
    }

    /** A quadrilateral: top edge from x tl to tr (y 0), bottom edge from br to bl (y 540). */
    private static Path quad(float tl, float tr, float br, float bl) {
        Path q = new Path();
        q.moveTo(tl, 0); q.lineTo(tr, 0); q.lineTo(br, Cards.H); q.lineTo(bl, Cards.H); q.close();
        return q;
    }

    /** The crests' size: 168, or as much as keeps 24 px from a seam (both the same, so they face off as equals). */
    static float crestSize(float seamL, float seamR) {
        // home: SAFE_L + c ≤ leftEdge(MID + c / 2) − CLEAR_X − seam; the away side is the same turned half round
        float most = (GAP_TOP - K * MID - CLEAR_X - Math.max(seamL, seamR) - SAFE_L) / (1 + K / 2);
        return Math.min(CREST, (float) Math.floor(most / 2) * 2);
    }

    /** A crest on its own colour, on an ivory disc where it would vanish into it (the shared rule, ClubTokens.needsDisc). */
    private static void crest(Canvas cv, Bitmap b, ClubTokens.CrestInfo info, String raw, float cx, float size, int field) {
        if (b == null) { Badges.draw(cv, raw, cx, MID, size / 2); return; }
        if (ClubTokens.needsDisc(info, field)) {
            Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
            d.setColor(ClubTokens.IVORY);
            cv.drawCircle(cx, MID, size / 2, d);  // flat: no shadow on this card
            CardKit.crest(cv, b, cx, MID, size * DISC_CREST, null);
        } else CardKit.crest(cv, b, cx, MID, size, null);
    }

    // ---------- the names ----------

    /** A name set for its field: one line or two, at a size. */
    static final class Name {
        final String[] lines;
        final float size;
        Name(String[] lines, float size) { this.lines = lines; this.size = size; }
    }

    /** Line i (of n) of a name: home lines go down from row A, away lines end on row C. */
    static float lineY(boolean home, int i, int n, float size) {
        float pitch = NAME_PITCH * size;
        return home ? ROW_A + i * pitch : ROW_C - (n - 1 - i) * pitch;
    }

    /**
     * The width a name line may take: from the left safe edge to 24 px short of the home field's edge at the line's
     * foot, or from 24 px past the away field's edge at the line's top to the right safe edge (less a seam).
     */
    static float nameRoom(boolean home, float cy, float size, float seam) {
        float cap = RC_CAP * size;
        return home ? leftEdge(cy + cap / 2) - CLEAR_X - seam - SAFE_L : SAFE_R - (rightEdge(cy - cap / 2) + CLEAR_X + seam);
    }

    /**
     * A club's name by the shared rule (CardModel.fitName), with Clash's two-line treatment: the card name, then its
     * short form, on one line 56 → 48; then a name with a space on two lines 52 → 48 (REAL / MADRID); then the
     * abbreviation (PAO); only a name no club has goes under 48, to 40, and is clipped at its field's edge. names: display
     * capitals, longest first.
     */
    static Name fitName(final CardModel.Measure ms, List<String> names, String abbrev, boolean home, float seam) {
        float box = nameRoom(home, lineY(home, 0, 1, NAME), NAME, seam);  // at the start size: the narrowest
        CardModel.NameFit f = CardModel.fitName(ms, names, null, box, box, NAME, NAME_MIN, NAME_MIN);
        if (ms.width(f.text, f.size) <= box) return new Name(new String[]{f.text}, f.size);
        for (String n : names) {
            String[] two = split(n, s -> ms.width(s, 100));
            if (two == null) continue;
            for (float s = NAME_TWO; s >= NAME_MIN; s -= 2) {
                boolean fits = true;
                for (int i = 0; i < 2; i++) fits &= ms.width(two[i], s) <= nameRoom(home, lineY(home, i, 2, s), s, seam);
                if (fits) return new Name(two, s);
            }
        }
        f = CardModel.fitName(ms, names, abbrev, box, box, NAME, NAME_MIN, NAME_FLOOR);
        return new Name(new String[]{f.text}, f.size);
    }

    /**
     * Two lines at the space that makes the longer one shortest; at " · " when there is one (the dot goes: the line
     * break separates them); null without a space.
     */
    static String[] split(String s, TicketStyle.Measure m) {
        int dot = s.indexOf(" · ");
        if (dot > 0) return new String[]{s.substring(0, dot).trim(), s.substring(dot + 3).trim()};
        String[] best = null;
        float bestW = Float.MAX_VALUE;
        for (int i = s.indexOf(' '); i > 0; i = s.indexOf(' ', i + 1)) {
            String a = s.substring(0, i).trim(), b = s.substring(i + 1).trim();
            if (a.isEmpty() || b.isEmpty()) continue;
            float w = Math.max(m.width(a), m.width(b));
            if (w < bestW) { bestW = w; best = new String[]{a, b}; }
        }
        return best;
    }

    /** Both names, at one size (the smaller of the two fits, never under 48 for a name that fits at 48). */
    private static void names(Canvas cv, List<String> homeNames, List<String> awayNames, int homeInk, int awayInk,
                              float seamL, float seamR) {
        final Paint np = CardKit.text(Ui.COND_BOLD, NAME, CardKit.WHITE);
        np.setLetterSpacing(0.01f);
        CardModel.Measure ms = (s, size) -> { np.setTextSize(size); return np.measureText(s); };
        List<String> hn = caps(homeNames), an = caps(awayNames);
        Name h = hn.get(0).isEmpty() ? null : fitName(ms, hn, abbrev(homeNames), true, seamL);
        Name a = an.get(0).isEmpty() ? null : fitName(ms, an, abbrev(awayNames), false, seamR);
        float size = Math.min(h == null ? NAME : h.size, a == null ? NAME : a.size);
        if (h != null) name(cv, np, h, Math.max(size, Math.min(h.size, NAME_MIN)), true, homeInk, seamL);
        if (a != null) name(cv, np, a, Math.max(size, Math.min(a.size, NAME_MIN)), false, awayInk, seamR);
    }

    private static List<String> caps(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) out.add(Cards.caps(n));
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static String abbrev(List<String> names) {
        String a = names.isEmpty() ? null : CardModel.abbreviation(names.get(0));
        return a == null ? null : Cards.caps(a);
    }

    /** A name's lines, flush with the safe edge (by their ink), clipped to the field 24 px short of its edge. */
    private static void name(Canvas cv, Paint np, Name n, float size, boolean home, int ink, float seam) {
        np.setTextSize(size);
        np.setColor(ink);
        float cap = CardKit.cap(np);
        cv.save();
        Path clip = new Path();
        if (home) {
            clip.moveTo(SAFE_L, SAFE_T); clip.lineTo(leftEdge(SAFE_T) - CLEAR_X - seam, SAFE_T);
            clip.lineTo(leftEdge(SAFE_B) - CLEAR_X - seam, SAFE_B); clip.lineTo(SAFE_L, SAFE_B);
        } else {
            clip.moveTo(rightEdge(SAFE_T) + CLEAR_X + seam, SAFE_T); clip.lineTo(SAFE_R, SAFE_T);
            clip.lineTo(SAFE_R, SAFE_B); clip.lineTo(rightEdge(SAFE_B) + CLEAR_X + seam, SAFE_B);
        }
        clip.close();
        cv.clipPath(clip);
        Rect ink2 = new Rect();
        for (int i = 0; i < n.lines.length; i++) {
            String s = n.lines[i];
            float base = lineY(home, i, n.lines.length, size) + cap / 2;
            np.getTextBounds(s, 0, s.length(), ink2);
            if (home) cv.drawText(s, SAFE_L - Math.min(0, ink2.left), base, np);
            else {
                float w = np.measureText(s), over = Math.max(0, ink2.right - w);
                cv.drawText(s, SAFE_R - over - w, base, np);
            }
        }
        cv.restore();
    }

    // ---------- row A: the state ----------

    /**
     * Row A in the gap: LIVE as the red pill with the clock (● LIVE 67′; when the clock doesn't fit, ● LIVE and the clock
     * goes under the score, as on the Ticket); a result's outcome pill (WIN / DRAW / LOSS; FINAL when scores are hidden
     * or the outcome unknown); otherwise the state's words (TOMORROW, SUN 22 NOV, DELAYED, POSTPONED, NO RESULT YET).
     * Returns the clock to set under the score, or "".
     */
    private static String rowA(Canvas cv, CardModel m, boolean hiddenScore) {
        float max = room(PILL_H);
        if (m.state == CardModel.State.LIVE) {
            String s = CardKit.liveText(m), clock = "";
            float size = pillSize(s, true, max);
            if (CardKit.pillWidth(s, Ui.BOLD, size, true) > max) { s = m.stateLine; clock = m.clock; size = pillSize(s, true, max); }
            float w = CardKit.pillWidth(s, Ui.BOLD, size, true);
            CardKit.pill(cv, s, axis(ROW_A) - w / 2, ROW_A, PILL_H, Ui.BOLD, size, CardKit.LIVE, CardKit.WHITE, true, false);
            return clock;
        }
        if (m.state == CardModel.State.RESULT && !hiddenScore && !m.outcomeWord.isEmpty()) {
            float size = pillSize(m.outcomeWord, false, max);
            float w = CardKit.pillWidth(m.outcomeWord, Ui.BOLD, size, false);
            CardKit.outcomePill(cv, m, axis(ROW_A) - w / 2, ROW_A, PILL_H, size, false);
            return "";
        }
        words(cv, m.stateLine, ROW_A, true, CardKit.alpha(CardKit.WHITE, WORD_A), 0);
        return "";
    }

    private static float pillSize(String s, boolean dot, float max) {
        float size = PILL;
        while (CardKit.pillWidth(s, Ui.BOLD, size, dot) > max && size > PILL_MIN) size -= 2;
        return size;
    }

    /** How a run of words is set in the gap: its lines, size and tracking. */
    static final class Words {
        final List<String> lines;
        final float size, track;
        Words(List<String> lines, float size, float track) { this.lines = lines; this.size = size; this.track = track; }
    }

    /** Measures words at a size and tracking (a Paint on the device; fixed advances in tests). */
    interface Measure2 { float width(String s, float size, float track); }

    /**
     * Fits words to the gap: one line at 48 → 44 (tracking .06), then without tracking, then two lines (split where the
     * longer is shortest, at " · " if any) at 48 → 44, then one line down to 40 (a long single word: ΚΑΘΥΣΤΕΡΗΣΗ).
     * lead: room taken before the first line (the warning sign).
     */
    static Words fitWords(String s, Measure2 m, float max, float lead) {
        for (float t : new float[]{WORD_TRACK, 0})
            for (float size = WORD; size >= WORD_MIN; size -= 2)
                if (lead * size / WORD + m.width(s, size, t) <= max) return new Words(Arrays.asList(s), size, t);
        final float[] probe = {WORD};
        String[] two = split(s, x -> m.width(x, probe[0], 0));
        if (two != null)
            for (float size = WORD; size >= WORD_MIN; size -= 2)
                if (lead * size / WORD + m.width(two[0], size, 0) <= max && m.width(two[1], size, 0) <= max)
                    return new Words(Arrays.asList(two), size, 0);
        float size = WORD_MIN;
        while (size > WORD_FLOOR && lead * size / WORD + m.width(s, size, 0) > max) size -= 2;
        return new Words(Arrays.asList(s), size, 0);
    }

    /**
     * Words in the gap, GS Bold caps at a colour: from row A downward (down) or ending on row C. warn: the amber sign
     * before the first line. Returns the block's bottom (down) or top.
     */
    private static float words(Canvas cv, String s, float cy, boolean down, int color, int warn) {
        if (s == null || s.isEmpty()) return cy;
        final Paint p = CardKit.text(Ui.BOLD, WORD, color);
        p.setFontFeatureSettings(null);
        Measure2 ms = (x, size, t) -> { p.setTextSize(size); p.setLetterSpacing(t); return p.measureText(x); };
        p.setTextSize(WORD);
        float cap0 = CardKit.cap(p), lead = warn != 0 ? cap0 * 1.12f + 14 : 0;
        Words w = fitWords(s, ms, room(cap0), lead);
        p.setTextSize(w.size);
        p.setLetterSpacing(w.track);
        float cap = CardKit.cap(p), pitch = cap + 0.46f * w.size, signW = warn != 0 ? cap * 1.12f + 14 * w.size / WORD : 0;
        int n = w.lines.size();
        for (int i = 0; i < n; i++) {
            float y = down ? cy + i * pitch : cy - (n - 1 - i) * pitch, base = y + cap / 2;
            String line = w.lines.get(i);
            float lw = p.measureText(line) + (i == 0 ? signW : 0), x = axis(y) - lw / 2;
            if (i == 0 && warn != 0) { Cards.warning(cv, x, base, cap, p); x += signW; }
            cv.drawText(line, x, base, p);
        }
        return down ? cy + (n - 1) * pitch + cap / 2 : cy - (n - 1) * pitch - cap / 2;
    }

    // ---------- the hero ----------

    /** Draws the hero on the middle line, centred on the gap's axis; returns its foot (y). */
    private static float hero(Canvas cv, CardModel m, boolean scores, boolean hiddenScore) {
        if (scores) return score(cv, m);
        if (hiddenScore) {
            Paint d = CardKit.text(Ui.COND_BOLD, HIDDEN, CardKit.WHITE);
            float size = HIDDEN, cap = CardKit.cap(d);
            while (CardKit.hiddenScoreWidth(size) > room(cap) && size > SCORE_MIN) { d.setTextSize(size -= 2); cap = CardKit.cap(d); }
            CardKit.hiddenScore(cv, axis(MID), MID + cap / 2, size, Paint.Align.CENTER);
            return MID + cap / 2;
        }
        String t = m.time == null || m.time.isEmpty() ? CardModel.w("tba") : m.time;
        int col = m.heroDim ? CardKit.alpha(CardKit.WHITE, 0.60f) : CardKit.WHITE;
        Paint p = CardKit.text(Ui.COND_BOLD, HERO, col);
        p.setLetterSpacing(-0.005f);
        String suf = m.timeSuffix == null ? "" : m.timeSuffix;
        Paint sp = CardKit.text(Ui.COND_BOLD, HERO * 0.32f, CardKit.alpha(CardKit.WHITE, THEM_A));
        float size = HERO, cap = CardKit.cap(p);
        while (timeWidth(p, sp, t, suf, size) > room(cap) && size > TIME_MIN) { size -= 2; p.setTextSize(size); cap = CardKit.cap(p); }
        float w = timeWidth(p, sp, t, suf, size), x = axis(MID) - w / 2, base = MID + cap / 2;
        cv.drawText(t, x, base, p);
        if (!suf.isEmpty()) cv.drawText(suf, x + p.measureText(t) + size * 0.06f, base, sp);  // sp: as timeWidth set it
        return base;
    }

    private static float timeWidth(Paint p, Paint sp, String t, String suf, float size) {
        if (suf.isEmpty()) return p.measureText(t);
        sp.setTextSize(suffix(size));
        return p.measureText(t) + size * 0.06f + sp.measureText(suf);
    }

    /** The 12-hour suffix (PM): a third of the numerals, never under the 44 px floor. */
    static float suffix(float size) { return Math.max(0.32f * size, WORD_MIN);
    }

    /** The score in match order: mine white, theirs 70 %, the en dash 45 % with .02 em each side; fitted to the gap. */
    private static float score(Canvas cv, CardModel m) {
        Paint me = CardKit.text(Ui.COND_BOLD, HERO, CardKit.WHITE);
        Paint them = CardKit.text(Ui.COND_BOLD, HERO, CardKit.alpha(CardKit.WHITE, THEM_A));
        Paint dash = CardKit.text(Ui.COND_BOLD, HERO, CardKit.alpha(CardKit.WHITE, DASH_A));
        Paint[] all = {me, them, dash};
        for (Paint x : all) x.setLetterSpacing(-0.005f);
        Paint hp = m.home ? me : them, ap = m.home ? them : me;
        float size = HERO, cap = CardKit.cap(me);
        while (scoreWidth(hp, dash, ap, m, size) > room(cap) && size > SCORE_MIN) {
            size -= 2;
            for (Paint x : all) x.setTextSize(size);
            cap = CardKit.cap(me);
        }
        float x = axis(MID) - scoreWidth(hp, dash, ap, m, size) / 2, base = MID + cap / 2;
        cv.drawText(m.homeScore, x, base, hp);
        x += hp.measureText(m.homeScore) + 0.02f * size;
        cv.drawText("–", x, base, dash);
        x += dash.measureText("–") + 0.02f * size;
        cv.drawText(m.awayScore, x, base, ap);
        return base;
    }

    private static float scoreWidth(Paint hp, Paint dash, Paint ap, CardModel m, float size) {
        return hp.measureText(m.homeScore) + dash.measureText("–") + ap.measureText(m.awayScore) + 0.04f * size;
    }

    /** The live clock under the score (only when the pill can't hold it): 24 px under the numerals. Returns its foot. */
    private static float clock(Canvas cv, String s, float above) {
        Paint p = CardKit.text(Ui.BOLD, CLOCK, CardKit.alpha(CardKit.WHITE, CLOCK_A));
        p.setFontFeatureSettings(null);
        p.setLetterSpacing(0.04f);
        float cap = CardKit.cap(p), cy = above + CLEAR + 3 + cap / 2;  // 3: the figures' ink rises past the capitals
        CardKit.fit(p, s, room(cap), CLOCK, WORD_FLOOR);
        CardKit.centre(cv, p, s, axis(cy), cy + cap / 2);
        return cy + cap / 2;
    }

    // ---------- row C: where to watch, or the date ----------

    /**
     * Row C in the gap: the not-updated warning in amber when the data is stale; where to watch before and during a
     * match; a result's date (AET · FRI 6 NOV, YESTERDAY) or an off match's (dimmed when postponed); nothing for an
     * awaiting or suspended match. above: the foot of what is over it (the block keeps 24 px from it).
     */
    private static void rowC(Canvas cv, CardModel m, CardArt art, float above) {
        if (m.stale) {
            words(cv, Cards.caps(I18n.t("not_updated").replace("⚠", "").trim()), ROW_C, false, CardKit.AMBER, 1);
            return;
        }
        switch (m.state) {
            case NEXT: case LIVE: channel(cv, m, art, above); break;
            case RESULT:
                words(cv, (m.stateExtra.isEmpty() ? "" : m.stateExtra + " · ") + m.dateLine, ROW_C, false, CardKit.alpha(CardKit.WHITE, DATE_A), 0);
                break;
            case OFF:
                words(cv, m.dateLine, ROW_C, false, CardKit.alpha(CardKit.WHITE, m.dateDim ? DATE_DIM_A : DATE_A), 0);
                break;
            default: break;
        }
    }

    /**
     * Where to watch on row C: the shared channel line (LIKELY, the logo, the number chip; CardKit.channel) through the
     * shared fit (CardKit.fitChannel: 48 → 44, logos capped 240 → 200 → 180, then without LIKELY, then untracked) in the
     * gap's width at that height, with LIKELY on a line of its own over a wide logo; a channel known only by name
     * (abroad), or a line that still doesn't fit (a wide wordmark set as its name, two channels), as words over at most
     * two lines (wordLines), each centred on the axis at its own height and kept 24 px under the hero.
     */
    private static void channel(Canvas cv, CardModel m, CardArt art, float above) {
        CardModel.Channel ch = m.channel;
        if (ch == null) return;
        float max = room(LOGO_H);
        if (!ch.namesOnly) {
            CardKit.ChannelSet s = CardKit.fitChannel(m, art, FOOT, max, LOGO_H);
            // as written (FULL) on one line; past that, LIKELY over the logos reads better than losing it
            if (s.fits && (s.form == CardKit.FULL || !ch.likely)) {
                float w = CardKit.channel(null, m, art, 0, 0, s, LOGO_H, max, false, 0);
                CardKit.channel(cv, m, art, axis(ROW_C) - w / 2, ROW_C, s, LOGO_H, max, false, CardKit.alpha(CardKit.WHITE, WORDS_A));
                return;
            }
            // LIKELY and a wide logo (Magenta Sport) don't share the gap: LIKELY on its own line over the logos
            if (ch.likely && likelyOver(cv, m, art, max, above)) return;
            if (s.fits) {
                float w = CardKit.channel(null, m, art, 0, 0, s, LOGO_H, max, false, 0);
                CardKit.channel(cv, m, art, axis(ROW_C) - w / 2, ROW_C, s, LOGO_H, max, false, CardKit.alpha(CardKit.WHITE, WORDS_A));
                return;
            }
        }
        final Paint p = CardKit.text(Ui.COND, FOOT, CardKit.alpha(CardKit.WHITE, WORDS_A));
        String num = ch.number > 0 && !ch.namesOnly ? " " + ch.number : "";
        WordLines w = wordLines(ch.words() + num, ch.bareWords() + num, ch.likely ? CardModel.w("likely") : "",
            (x, size, t) -> { p.setTextSize(size); p.setLetterSpacing(t); return p.measureText(x); },
            size -> { p.setTextSize(size); return room(CardKit.cap(p)); },
            (n, size) -> { p.setTextSize(size); float cap = CardKit.cap(p); return ROW_C - (n - 1) * pitch(size, cap) - cap / 2 >= above + CLEAR; });
        p.setTextSize(w.size);
        p.setLetterSpacing(w.track);
        float cap = CardKit.cap(p);
        int n = w.lines.size();
        for (int i = 0; i < n; i++) {
            float y = ROW_C - (n - 1 - i) * pitch(w.size, cap);
            String line = CardKit.cut(p, w.lines.get(i), room(cap));
            CardKit.centre(cv, p, line, axis(y), y + cap / 2);
        }
    }

    /** Row C's words: RC Regular 48 → 44 (never under), tracked .05 (or not), one or two lines. */
    static final float WORDS_TRACK = 0.05f;

    /** How row C's words are set: their lines, size and tracking. */
    static final class WordLines {
        final List<String> lines;
        final float size, track;
        WordLines(List<String> lines, float size, float track) { this.lines = lines; this.size = size; this.track = track; }
    }

    /** The gap's width for a line at a size (it narrows with the capitals' height). */
    interface Room { float at(float size); }

    /** Whether n lines at a size keep 24 px under the hero. */
    interface Clear { boolean ok(int n, float size); }

    /**
     * Row C's channel words in at most two lines, never under 44, each step from 48 to 44: (1) as written on one line
     * (LIKELY first, OR between guesses, tracked .05); (2) LIKELY on a line of its own over the names on one (as over a
     * wide logo, likelyOver); (3) without LIKELY, " / " between the names (glued to the name before it, so a line never
     * starts with it), wrapped to one or two lines; (4) that untracked. The first inside the gap and clear of the hero
     * wins. None does: (4) at 44 cut to two lines (no real channel). full / bare: the names (with a confirmed number);
     * likely: the hedge word or "". Pure arithmetic: unit-tested.
     */
    static WordLines wordLines(String full, String bare, String likely, Measure2 m, Room room, Clear clear) {
        bare = bare.replace(" / ", TicketStyle.SLASH + " ");
        String hedged = likely.isEmpty() ? full : likely + " " + full;
        for (float size = FOOT; size >= FOOT_MIN; size -= 2) {  // (1)
            float r = room.at(size);
            if (m.width(hedged, size, WORDS_TRACK) <= r && clear.ok(1, size))
                return new WordLines(new ArrayList<>(Arrays.asList(hedged)), size, WORDS_TRACK);
        }
        if (!likely.isEmpty())
            for (float size = FOOT; size >= FOOT_MIN; size -= 2) {  // (2)
                float r = room.at(size);
                if (m.width(likely, size, WORDS_TRACK) <= r && m.width(full, size, WORDS_TRACK) <= r && clear.ok(2, size))
                    return new WordLines(new ArrayList<>(Arrays.asList(likely, full)), size, WORDS_TRACK);
            }
        for (final float t : new float[]{WORDS_TRACK, 0})  // (3), (4)
            for (float size = FOOT; size >= FOOT_MIN; size -= 2) {
                final float sz = size;
                TicketStyle.Measure ms = x -> m.width(x, sz, t);
                List<String> lines = TicketStyle.wrap(bare, room.at(size), ms);
                if (lines.size() <= 2 && fitsAll(lines, ms, room.at(size)) && clear.ok(lines.size(), size))
                    return new WordLines(lines, size, t);
            }
        TicketStyle.Measure ms = x -> m.width(x, FOOT_MIN, 0);
        List<String> lines = TicketStyle.wrap(bare, room.at(FOOT_MIN), ms);
        if (lines.size() > 2) {
            StringBuilder rest = new StringBuilder(lines.get(1));
            for (int i = 2; i < lines.size(); i++) rest.append(' ').append(lines.get(i));
            lines = new ArrayList<>(Arrays.asList(lines.get(0), rest.toString()));
        }
        return new WordLines(lines, FOOT_MIN, 0);
    }

    /**
     * LIKELY (RC Regular, 72 %) on a line of its own, 18 px over the logo row (the logos and chips without it, through
     * the same cascade), each centred on the axis at its height; false when even that doesn't fit, or would come within
     * 24 px of the hero.
     */
    private static boolean likelyOver(Canvas cv, CardModel m, CardArt art, float max, float above) {
        CardModel.Channel one = new CardModel.Channel();
        one.namesOnly = false;
        one.number = m.channel.number;
        one.names.addAll(m.channel.names);
        final CardModel bare = new CardModel();
        bare.match = m.match;
        bare.channel = one;
        CardKit.ChannelSet f = CardKit.fitChannel(bare, art, FOOT, max, LOGO_H);
        float w = CardKit.channel(null, bare, art, 0, 0, f, LOGO_H, max, false, 0);
        if (w <= 0 || !f.fits) return false;
        Paint lead = CardKit.text(Ui.COND, f.size, CardKit.alpha(CardKit.WHITE, WORDS_A));
        lead.setLetterSpacing(0.05f);
        float cap = CardKit.cap(lead), ly = ROW_C - LOGO_H / 2 - 18 - cap / 2;
        if (ly - cap / 2 < above + CLEAR) return false;
        CardKit.centre(cv, lead, CardModel.w("likely"), axis(ly), ly + cap / 2);
        CardKit.channel(cv, bare, art, axis(ROW_C) - w / 2, ROW_C, f, LOGO_H, max, false, CardKit.alpha(CardKit.WHITE, WORDS_A));
        return true;
    }

    private static float pitch(float size, float cap) { return cap + 0.42f * size; }

    private static boolean fitsAll(List<String> lines, TicketStyle.Measure m, float max) {
        for (String l : lines) if (m.width(l) > max) return false;
        return true;
    }

    /** For the audit and the tests: the corners of the gap. */
    static float[] gap() { return new float[]{leftEdge(0), rightEdge(0), rightEdge(Cards.H), leftEdge(Cards.H)}; }
}
