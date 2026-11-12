package app.fanela.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;

/**
 * The event cards' shared parts (F1-UFC-ARCHITECTURE §6.1, design/spec/f1ufc/SPEC-F1-UFC.md §2): what a card says,
 * resolved once from the EventCardModel (view), and the pieces every event style draws the same way: the eyebrow with
 * its gold TITLE segment, the state or LIVE pill, the who tier (the session, or A VS B), the hero forms (time, the
 * winner with two small rows, the focus driver and his place, two cars, the UFC method or round, the one blank slot),
 * the team-colour bar, the headshot / initials disc, the circuit outline and the octagon line. Football's own parts
 * are reused where they exist (CardKit's pills, channel line, hidden slot colour and stale warning; SignatureStyle's
 * date line; ClubTokens' colour maths) through a throwaway CardModel (shim): CardKit, CardModel and the football
 * styles are not edited for events. Stream E owns this file.
 */
final class EventKit {
    private EventKit() { }

    // ================================================================ colours (spec §5.1)
    static final int FIELD = 0xFF131316, GRAPHITE = 0xFFB9C0CC, GRAPHITE_BODY = EventCardModel.F1_GRAPHITE,
        GRAPHITE_CLASH = 0xFF2F343B, GRAPHITE_STOCK = 0xFF1E2126, STRIPE = EventCardModel.F1_IVORY,
        RED_C = EventCardModel.UFC_RED, BLUE_C = EventCardModel.UFC_BLUE, GOLD = EventCardModel.TITLE_GOLD,
        INK_DISC = 0xFF1B1B1F, NUMBER_INK = 0xFF141416, IVORY_EDGE = 0xFFDAD6CE;
    /** The hidden slot: white 18 % (spec §5.1, CardKit's hidden score). */
    static final int SLOT = 0x2EFFFFFF;
    /** Loser 45 %, a time not known 60 %, the place numeral 60 %, small-row code 88 %, second row 70 %, VS 60 %. */
    static final float LOSER_A = 0.45f, DIM_A = 0.60f, NUM_A = 0.60f, ROW_A = 0.88f, ROW2_A = 0.70f, VS_A = 0.60f;
    /** The small rows beside a hero: 0.365 em of the hero, 26 px after it; the bar 14 px beside a hero code, 8 beside a row. */
    static final float MINI = 0.365f, ADJ_GAP = 26, BAR = 14, BAR_GAP = 18, MINI_BAR = 8, MINI_NUM_GAP = 14, MINI_BAR_GAP = 12;

    /** flag-icons codes (SportNames' table) → ESPN's country PNG codes (a.espncdn.com/i/teamlogos/countries/500/<code>.png). */
    private static final String[][] FLAGS = {
        {"us", "usa"}, {"gb", "gbr"}, {"it", "ita"}, {"nl", "ned"}, {"jp", "jpn"}, {"ca", "can"}, {"at", "aut"},
        {"mc", "mon"}, {"sg", "sin"}, {"qa", "qat"}, {"br", "bra"}, {"au", "aus"}, {"be", "bel"}, {"es", "esp"},
        {"mx", "mex"}, {"hu", "hun"}, {"az", "aze"}, {"cn", "chn"}, {"bh", "brn"}, {"ae", "uae"}, {"sa", "ksa"},
        {"my", "mas"}, {"fr", "fra"}, {"de", "ger"}, {"pt", "por"}, {"tr", "tur"}, {"ru", "rus"},
    };

    /** The host flag's image: ESPN's 500 px country PNG for a flag-icons code ("us" → usa.png); null when unknown. */
    static String flagUrl(String code) {
        if (code == null || code.isEmpty()) return null;
        for (String[] r : FLAGS) if (r[0].equalsIgnoreCase(code)) return "https://a.espncdn.com/i/teamlogos/countries/500/" + r[1] + ".png";
        return null;
    }

    // ================================================================ the view: what the card says, resolved once
    enum HeroKind { TIME, WORD, CODE, TEAM, METHOD, ROUND, SLOT, NONE }

    /** A driver row: its place ("2", or "" for none), its code and its team colour (lifted, barColour). */
    static final class Row {
        final String pos, code;
        final int color;
        Row(String pos, String code, int color) { this.pos = pos; this.code = code; this.color = color; }
    }

    static final class View {
        EventCardModel m;
        CardModel shim;
        boolean f1, live, result, off, hidden, stale;
        CardModel.State state;
        /** The state words (day, FINAL, CANCELLED…) or, live, the pill's words (LIVE 34/50). */
        String stateWords = "", pill = "";
        /** A followed fighter's result: the outcome pill (1 win, 0 draw, -1 loss; 9 none) and its word. */
        int outcome = 9;
        String outcomeWord = "";
        /** The eyebrow's variants, longest first, mixed case (caps styles set SportNames.caps). */
        List<List<EventCardModel.Seg>> eyebrow = new ArrayList<>();
        /** Who: the session's names (F1), longest first, mixed case. */
        List<String> session = new ArrayList<>();
        /** Who (UFC): each fighter's card names, longest first, in capitals and mixed case; their alphas. */
        List<String> a = new ArrayList<>(), b = new ArrayList<>(), aMixed = new ArrayList<>(), bMixed = new ArrayList<>();
        float aA = 1, bA = 1;
        SportEvent.Fighter fa, fb;
        String initialsA = "", initialsB = "";
        // the hero
        HeroKind hero = HeroKind.NONE;
        String time = "", timeSuffix = "", word = "", code = "", pos = "";
        float timeA = 1, wordA = 1, posA = 1;
        int codeColor;
        final List<Row> rows = new ArrayList<>(), team = new ArrayList<>();
        /** METHOD / ROUND: the small rows beside the word (R3 / 2:27, OF 5). */
        final List<String> wordRows = new ArrayList<>();
        /** The line a style may set apart from the hero (Ticket's caption, Clash's clock row): LAP 34/50, OF 5, R3 · 2:27. */
        String caption = "";
        // the foot
        boolean channel, date;
        String dateText = "";
        boolean dateDim;
        // colours and marks
        int team1, trim, flagColour;
        String flagUrl, number = "", circuitId;
        List<String> place = new ArrayList<>();
        boolean facesOk;
    }

    /** Whether a model is Formula 1 (anything not "ufc" is drawn as F1). */
    static boolean f1(EventCardModel m) { return m == null || !SportEvent.UFC.equals(m.sport); }

    /** The focus team's colour, 0 when the card isn't themed by a focus (F1 only; UFC has its two corners). */
    static int focusColour(EventCardModel m) {
        if (m == null || !f1(m) || !m.themed || m.accent == 0) return 0;
        return ClubTokens.opaque(m.accent);
    }

    /**
     * A team's colour as Fanela paints a surface of it (Settings' team and driver tiles, Kit's band): the football
     * kit treatment (ClubTokens.painted), except that a silver team keeps its silver (Cadillac #A2AAAD: white by
     * the kit classes, but under .80 luminance), so only a near-white team (Williams) takes the warm grey.
     */
    static int teamPaint(int c) {
        c = ClubTokens.opaque(c);
        if (ClubTokens.kitClass(c) == ClubTokens.Kit.WHITE && ClubTokens.lum(c) <= 0.80) return c;
        return ClubTokens.painted(c);
    }

    /** Whether a team paints as the warm grey (near-white): its tile then carries its trim colour as an edge. */
    static boolean nearWhite(int c) { return (c & 0xFFFFFF) != 0 && teamPaint(c) == ClubTokens.KIT_WHITE; }

    /**
     * The team-colour bar's colour (spec §2.2): the team's colour (the feed's, its alpha forced), with 35 % white mixed
     * in when it would not read on the dark field. "Would not read" is WCAG contrast under 3:1 against the field
     * (#131316): that lifts the spec's two examples, Red Bull #00327D (1.6) and Haas #5A5A5A (2.7), and nothing else on
     * the 2026 grid (Ferrari #DC0000 is 3.6, Aston Martin 3.1). Missing: graphite.
     */
    static int barColour(int c) {
        if ((c & 0xFFFFFF) == 0) return GRAPHITE;
        c = ClubTokens.opaque(c);
        return ClubTokens.contrast(c, FIELD) < 3 ? ClubTokens.mix(c, 0xFFFFFFFF, 0.35) : c;
    }

    /** People and name lists in capitals (SportNames.caps: Greek the Greek way, Latin diacritics kept: MEDIĆ). */
    static List<String> caps(List<String> l) {
        List<String> out = new ArrayList<>();
        if (l != null) for (String s : l) out.add(SportNames.caps(s));
        return out;
    }

    /** Two initials: the given name's and the card surname's (Ciryl Gane, GANE → CG; Waldo Cortes Acosta → WC). */
    static String initials(String fullName, String surname) {
        String full = fullName == null ? "" : fullName.trim(), sur = surname == null ? "" : surname.trim();
        String first = full.isEmpty() ? "" : full.substring(0, 1);
        if (sur.isEmpty()) { String[] p = full.split("\\s+"); sur = p.length > 1 ? p[p.length - 1] : ""; }
        if (!sur.isEmpty() && full.toUpperCase(java.util.Locale.ROOT).startsWith(sur.toUpperCase(java.util.Locale.ROOT))) first = "";
        return SportNames.caps(first + (sur.isEmpty() ? "" : sur.substring(0, 1)));
    }

    /**
     * A fighter's card names, longest first (spec §4.4): the model's own (BoutView.namesA), else its one name, else
     * ESPN's short name without the initial ("C. Gane" → Gane). The tight form (the last word) closes the list so a
     * style can always fall back to it.
     */
    static List<String> fighterNames(List<String> given, String name, SportEvent.Fighter f) {
        List<String> out = new ArrayList<>();
        if (given != null) for (String s : given) if (s != null && !s.trim().isEmpty() && !out.contains(s.trim())) out.add(s.trim());
        if (out.isEmpty() && name != null && !name.trim().isEmpty()) out.add(name.trim());
        if (out.isEmpty() && f != null) {
            String s = f.shortName == null || f.shortName.isEmpty() ? f.name : f.shortName;
            s = s == null ? "" : s.replaceFirst("^\\p{L}\\.\\s*", "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        if (!out.isEmpty()) {
            String last = out.get(out.size() - 1), tight = last.substring(last.lastIndexOf(' ') + 1);
            if (!tight.isEmpty() && !out.contains(tight)) out.add(tight);
        }
        return out;
    }

    /** A throwaway CardModel carrying what CardKit's and SignatureStyle's shared lines read (state, dates, channel, stale). */
    static CardModel shim(EventCardModel m) {
        CardModel c = new CardModel();
        c.state = m.state == null ? CardModel.State.NEXT : m.state;
        c.stateLine = m.stateLine == null ? "" : m.stateLine;
        c.dateLine = m.dateLine == null ? "" : m.dateLine;
        c.dateDim = m.dateDim;
        c.stateExtra = m.stateExtra == null ? "" : m.stateExtra;
        c.clock = m.clock == null ? "" : m.clock;
        c.stale = m.stale;
        c.soon = m.soon;
        c.scoresHidden = m.scoresHidden;
        c.channel = m.channel;
        c.whereText = m.whereText == null ? "" : m.whereText;
        c.time = m.time == null ? "" : m.time;
        c.heroDim = m.heroDim;
        c.tbd = m.tbd;
        c.compNames = new ArrayList<>(m.compNames == null ? new ArrayList<String>() : m.compNames);
        SportEvent.Session s = m.session;
        c.match = s != null && s.watch != null ? s.watch.asMatch(s.start, m.sport == null ? "" : m.sport) : null;
        return c;
    }

    /**
     * What the card says, resolved once from stream D's model: every style draws a View, so the five styles say the
     * same thing in their own grids. Pure: unit-tested.
     */
    static View view(EventCardModel m) {
        View v = new View();
        v.m = m;
        v.shim = shim(m);
        v.f1 = f1(m);
        v.state = v.shim.state;
        v.live = v.state == CardModel.State.LIVE;
        v.result = v.state == CardModel.State.RESULT;
        v.off = v.state == CardModel.State.OFF;
        v.hidden = m.scoresHidden;
        v.stale = m.stale;
        v.stateWords = v.shim.stateLine;
        if (v.live) v.pill = CardKit.liveText(v.shim);  // LIVE 34/50 / LIVE LAP 34 (F1); LIVE alone (UFC: no clock on a bitmap)
        v.circuitId = m.circuitId != null && !m.circuitId.isEmpty() ? m.circuitId : m.event == null ? null : m.event.circuitId;
        if (m.eyebrowNames != null) v.eyebrow.addAll(m.eyebrowNames);

        // colours and marks (F1)
        v.team1 = focusColour(m);
        v.trim = v.team1 != 0 && m.trim != 0 ? ClubTokens.opaque(m.trim) : 0;
        v.flagUrl = flagUrl(m.flagCode);
        v.flagColour = m.flagColour == 0 ? 0 : ClubTokens.opaque(m.flagColour);
        v.number = m.number == null || m.number.isEmpty() ? focusNumber(m) : m.number;
        // QAS-10: on a result the race number "1" reads as a place (scores hidden most of all): Kit's disc shows the
        // circuit there, Clash's right disc the flag, at the same places as on the row's other cards
        if (v.result) v.number = "";
        if (m.placeNames != null) v.place.addAll(m.placeNames);

        // who
        EventCardModel.BoutView bv = m.bout;
        if (v.f1) {
            if (m.sessionNames != null) v.session.addAll(m.sessionNames);
            if (v.session.isEmpty() && m.sessionLabel != null && !m.sessionLabel.isEmpty()) v.session.add(m.sessionLabel);
        } else if (bv != null) {
            v.fa = bv.a; v.fb = bv.b;
            v.aMixed = fighterNames(bv.namesA, bv.nameA, bv.a);
            v.bMixed = fighterNames(bv.namesB, bv.nameB, bv.b);
            v.a = caps(v.aMixed);
            v.b = caps(v.bMixed);
            v.initialsA = initials(bv.a == null ? "" : bv.a.name, v.aMixed.isEmpty() ? "" : v.aMixed.get(0));
            v.initialsB = initials(bv.b == null ? "" : bv.b.name, v.bMixed.isEmpty() ? "" : v.bMixed.get(0));
            if (v.result && !v.hidden) {  // the winner 100 %, the loser 45 %; a draw, a no contest: both 100 %
                if (bv.winner == 1) v.bA = LOSER_A;
                else if (bv.winner == 2) v.aA = LOSER_A;
            }
            v.facesOk = bv.facesOk && m.facesOk;
            if (v.result && !v.hidden && bv.outcome != 9 && bv.outcomeWord != null && !bv.outcomeWord.isEmpty()) {
                v.outcome = bv.outcome;
                v.outcomeWord = bv.outcomeWord;
            }
        }

        // the hero
        EventCardModel.Hero h = m.hero == null ? EventCardModel.Hero.NONE : m.hero;
        if (v.hidden && (h == EventCardModel.Hero.PODIUM || h == EventCardModel.Hero.ORDER || h == EventCardModel.Hero.FOCUS
            || h == EventCardModel.Hero.TEAM || h == EventCardModel.Hero.METHOD || h == EventCardModel.Hero.ROUND))
            h = EventCardModel.Hero.SLOTS;  // never a result with scores hidden, whatever the model left
        float alpha = m.heroAlpha > 0 ? m.heroAlpha : 1f;
        switch (h) {
            case TIME: case BOUT:
                v.hero = HeroKind.TIME;
                v.time = m.time == null || m.time.isEmpty() ? CardModel.w("tba") : m.time;
                v.timeSuffix = m.timeSuffix == null || v.time.equals(CardModel.w("tba")) ? "" : m.timeSuffix;
                v.timeA = alpha;
                break;
            case WORD:
                v.hero = m.heroWord == null || m.heroWord.isEmpty() ? HeroKind.NONE : HeroKind.WORD;
                v.word = m.heroWord == null ? "" : m.heroWord;
                v.wordA = alpha;
                break;
            case SLOTS:
                v.hero = HeroKind.SLOT;
                break;
            case PODIUM: case ORDER:
                if (m.slots.isEmpty()) { v.hero = HeroKind.SLOT; break; }
                v.hero = HeroKind.CODE;
                v.code = m.slots.get(0).code; v.codeColor = barColour(m.slots.get(0).color);
                for (int i = 1; i < m.slots.size() && v.rows.size() < 2; i++) v.rows.add(row(m.slots.get(i), i + 1));
                break;
            case FOCUS: {
                EventCardModel.Slot f = m.focus != null ? m.focus : m.focusSlots.isEmpty() ? null : m.focusSlots.get(0);
                if (f == null) { v.hero = HeroKind.SLOT; break; }
                v.hero = HeroKind.CODE;
                v.code = f.code; v.codeColor = barColour(f.color);
                v.pos = f.place == null || f.place.isEmpty() ? (f.pos > 0 ? "P" + f.pos : "") : f.place;
                v.posA = f.placeDim ? NUM_A : 1;
                for (EventCardModel.Slot s : m.slots) if (s != f && v.rows.size() < 2) v.rows.add(row(s, s.pos));
                break;
            }
            case TEAM:
                v.hero = m.focusSlots.size() >= 2 ? HeroKind.TEAM : m.focusSlots.isEmpty() ? HeroKind.SLOT : HeroKind.CODE;
                if (v.hero == HeroKind.CODE) {
                    EventCardModel.Slot f = m.focusSlots.get(0);
                    v.code = f.code; v.codeColor = barColour(f.color); v.pos = f.place == null ? "" : f.place; v.posA = f.placeDim ? NUM_A : 1;
                } else for (int i = 0; i < m.focusSlots.size() && i < 2; i++) {
                    EventCardModel.Slot f = m.focusSlots.get(i);
                    v.team.add(new Row(f.place == null || f.place.isEmpty() ? (f.pos > 0 ? "P" + f.pos : "") : f.place, f.code, barColour(f.color)));
                }
                break;
            case METHOD: case ROUND:
                if (m.heroWord == null || m.heroWord.isEmpty()) { v.hero = HeroKind.SLOT; break; }
                v.hero = h == EventCardModel.Hero.METHOD ? HeroKind.METHOD : HeroKind.ROUND;
                v.word = m.heroWord;
                for (String s : m.heroRows) if (s != null && !s.isEmpty() && v.wordRows.size() < 2) v.wordRows.add(s);
                break;
            default:
                v.hero = HeroKind.NONE;
        }
        // the caption a style may set apart: the lap (F1 live, the Ticket), OF 5 / R3 · 2:27 (UFC)
        if (v.f1 && v.live) v.caption = m.lapLine == null ? "" : m.lapLine;
        else if (!v.f1 && (v.hero == HeroKind.METHOD || v.hero == HeroKind.ROUND)) v.caption = CardKit.join(v.wordRows, " · ");

        // the foot: where to watch before and during; else the date (results, off events); nothing when neither
        v.channel = (v.state == CardModel.State.NEXT || v.live) && m.channel != null;
        v.dateText = v.shim.dateLine;
        v.date = !v.channel && !v.dateText.isEmpty();
        v.dateDim = m.dateDim;
        return v;
    }

    /**
     * The focus driver's race number from the event's orders (Entrant.number, stream B) when the model has none: a
     * driver focus on a FOCUS card only (a team focus has two numbers and keeps the circuit). "" when unknown.
     */
    static String focusNumber(EventCardModel m) {
        if (m.hero != EventCardModel.Hero.FOCUS || m.focus == null || m.event == null || m.focus.code.isEmpty()) return "";
        for (SportEvent.Session s : m.event.sessions)
            for (SportEvent.Entrant x : s.order)
                if (m.focus.code.equals(x.code) && x.number != null && !x.number.isEmpty()) return x.number;
        return "";
    }

    private static Row row(EventCardModel.Slot s, int pos) {
        return new Row(s.pos > 0 ? String.valueOf(s.pos) : pos > 0 ? String.valueOf(pos) : "", s.code, barColour(s.color));
    }

    static String join(List<String> parts, String sep) { return CardKit.join(parts, sep); }

    // ================================================================ text helpers
    static Paint paint(Typeface face, float size, int color, float track) {
        Paint p = CardKit.text(face, size, color);
        p.setFontFeatureSettings(null);
        if (track != 0) p.setLetterSpacing(track);
        return p;
    }

    /** Numerals (times, rounds, codes): tabular figures, as the football heroes. */
    static Paint numerals(float size, int color, float track) {
        Paint p = CardKit.text(Ui.COND_BOLD, size, color);
        if (track != 0) p.setLetterSpacing(track);
        return p;
    }

    static float cap(Paint p) { return CardKit.cap(p); }

    /** Draws s with its advance's left at x (align -1), its centre (0) or its right (1); returns its width. */
    static float text(Canvas cv, Paint p, String s, float x, float base, int align) {
        float w = p.measureText(s);
        float l = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        cv.drawText(s, l, base, p);
        return w;
    }

    // ================================================================ the eyebrow
    /**
     * The eyebrow (spec §4.1) through the competition's fit (CardKit.fitComp's loop: each variant, each size / tracking
     * step, never under 44, never cut): the segments joined by " · ", the TITLE segment in gold at 100 %, the rest at
     * alpha. x: its ink's left edge (align -1) or its centre (align 0); baseline, or its capitals centred on cy when
     * baseline is NaN. avail: what the state left it. Returns the variant drawn (0, 1, …) or -1 when none fits.
     */
    static int eyebrow(Canvas cv, View v, Typeface face, boolean caps, float alpha, int ink, float[][] steps, float x,
                       float baseline, float cy, float avail, int align) {
        Paint p = paint(face, 48, CardKit.alpha(ink, alpha), 0);
        for (int vi = 0; vi < v.eyebrow.size(); vi++) {
            List<EventCardModel.Seg> segs = v.eyebrow.get(vi);
            String whole = eyebrowText(segs, caps);
            if (whole.isEmpty()) continue;
            for (float[] st : steps) {
                if (st[0] < CardKit.COMP_MIN) continue;
                p.setTextSize(st[0]);
                p.setLetterSpacing(st[1]);
                float iw = CardKit.inkWidth(p, whole);
                if (iw > avail) continue;
                android.graphics.Rect r = new android.graphics.Rect();
                p.getTextBounds(whole, 0, whole.length(), r);
                float base = Float.isNaN(baseline) ? cy + cap(p) / 2 : baseline;
                float cx = (align == 0 ? x - iw / 2 : x) - r.left;
                Paint gold = paint(face, st[0], GOLD, st[1]);
                for (int i = 0; i < segs.size(); i++) {
                    if (i > 0) { cv.drawText(" · ", cx, base, p); cx += p.measureText(" · "); }
                    String s = caps ? SportNames.caps(segs.get(i).text) : segs.get(i).text;
                    Paint q = segs.get(i).gold ? gold : p;
                    cv.drawText(s, cx, base, q);
                    cx += q.measureText(s);
                }
                return vi;
            }
        }
        return -1;
    }

    static String eyebrowText(List<EventCardModel.Seg> segs, boolean caps) {
        StringBuilder b = new StringBuilder();
        for (EventCardModel.Seg s : segs) {
            if (s.text == null || s.text.isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(caps ? SportNames.caps(s.text) : s.text);
        }
        return b.toString();
    }

    /** The header's steps (Signature, Kit, Clean): 48 → 44 at one tracking. */
    static float[][] steps(float track) { return new float[][]{{48, track}, {46, track}, {44, track}}; }

    // ================================================================ the state (Signature, Kit)
    /**
     * The state at the right edge (x): the LIVE pill (66 / 48, centred on y 77) or the state words (GS Bold 56 → 44,
     * tracking .04, fit box), their capitals centred on cy. Returns the width drawn.
     */
    static float state(Canvas cv, View v, float right, float cy, float box) {
        if (v.live) return CardKit.pill(cv, v.pill, right, SignatureStyle.LIVE_PILL_CY, 66, Ui.BOLD, 48, CardKit.LIVE, CardKit.WHITE, true, true);
        String s = v.stateWords;
        if (s == null || s.isEmpty()) return 0;
        Paint p = paint(Ui.BOLD, 56, CardKit.WHITE, 0.04f);
        float size = 56;
        while (size > 44 && p.measureText(s) > box) p.setTextSize(size -= 2);
        CardKit.right(cv, p, s, right, cy + cap(p) / 2);
        return p.measureText(s);
    }

    // ================================================================ who
    /**
     * The who tier: F1, the session in RC Bold caps (each name from start down to 56, then the tight form); UFC,
     * A VS B (spec §2.4: one line from start to 64, the card names then the tight surnames, then both down to min, so a
     * row's who stays one line wherever it can; else two lines, A over VS B, 72 → min, capitals 24 px apart, the pair
     * centred on cy).
     * align: -1 from x, 1 to x. Returns the who's bottom (its capitals'). UFC_WHO: the UFC pair's box where the style
     * has the room (Signature: PROCHÁZKA VS STIRLING on one line).
     */
    static float who(Canvas cv, View v, float x, float cy, int align, float box, float start, float min) {
        if (v.f1) {
            Paint p = numerals(start, CardKit.WHITE, 0);
            p.setFontFeatureSettings(null);
            String s = fitList(p, caps(v.session), box, start, min);
            if (s.isEmpty()) return cy;
            text(cv, p, s, x, cy + cap(p) / 2, align);
            return cy + cap(p) / 2;
        }
        if (v.a.isEmpty() || v.b.isEmpty()) return cy;
        // one line: the full card names, then the tight ones, from start to 64; then both again down to min (a style
        // without Signature's room, Kit's 602, keeps PROCHÁZKA VS STIRLING on one line at 58)
        for (float floor : new float[]{64, min})
            for (int k = 0; k < 2; k++) {
                String a = k == 0 ? v.a.get(0) : last(v.a), b = k == 0 ? v.b.get(0) : last(v.b);
                for (float size = floor == 64 ? start : 62; size >= floor; size -= 2)
                    if (vsWidth(a, b, size) <= box) { vsLine(cv, a, b, size, x, cy, align, v.aA, v.bA); return cy + capOf(size) / 2; }
            }
        // two lines: A over VS B, 72 → 56, the full names then the tight ones
        for (int k = 0; k < 2; k++) {
            String a = k == 0 ? v.a.get(0) : last(v.a), b = k == 0 ? v.b.get(0) : last(v.b);
            for (float size = 72; size >= min; size -= 2)
                if (twoLineWidth(a, b, size) <= box) return vsTwoLines(cv, a, b, size, x, cy, align, v.aA, v.bA);
        }
        return vsTwoLines(cv, last(v.a), last(v.b), min, x, cy, align, v.aA, v.bA);
    }

    /** The UFC who's box where a style has the room (Signature, right-aligned to 902: from x 202). */
    static final float UFC_WHO = 700;

    static String last(List<String> l) { return l.isEmpty() ? "" : l.get(l.size() - 1); }

    /** The first of names that fits box from start down to min (the paint left at its size); the last at min if none. */
    static String fitList(Paint p, List<String> names, float box, float start, float min) {
        if (names.isEmpty()) return "";
        for (String n : names) {
            float size = start;
            p.setTextSize(size);
            while (p.measureText(n) > box && size > min) p.setTextSize(size -= 2);
            if (p.measureText(n) <= box) return n;
        }
        p.setTextSize(min);
        return names.get(names.size() - 1);
    }

    static float capOf(float size) { return cap(numerals(size, CardKit.WHITE, 0)); }

    /** VS: RC Regular 0.62 em of the names (spec §2.4), never under the 44 px floor. */
    static float vsSize(float size) { return Math.max(0.62f * size, 44); }

    /** Two-line who: the capitals 24 px apart (the spec's 22, raised to the 24 px gap rule). */
    static final float WHO_GAP = 24;

    static float vsWidth(String a, String b, float size) {
        Paint n = numerals(size, CardKit.WHITE, 0), vs = paint(Ui.COND, vsSize(size), CardKit.WHITE, 0);
        n.setFontFeatureSettings(null);
        return n.measureText(a) + size * 0.22f + vs.measureText(CardKit.VS) + size * 0.22f + n.measureText(b);
    }

    static float twoLineWidth(String a, String b, float size) {
        Paint n = numerals(size, CardKit.WHITE, 0), vs = paint(Ui.COND, vsSize(size), CardKit.WHITE, 0);
        n.setFontFeatureSettings(null);
        return Math.max(n.measureText(a), vs.measureText(CardKit.VS) + size * 0.22f + n.measureText(b));
    }

    private static void vsLine(Canvas cv, String a, String b, float size, float x, float cy, int align, float aA, float bA) {
        Paint pa = numerals(size, CardKit.alpha(CardKit.WHITE, aA), 0), pb = numerals(size, CardKit.alpha(CardKit.WHITE, bA), 0);
        pa.setFontFeatureSettings(null); pb.setFontFeatureSettings(null);
        Paint vs = paint(Ui.COND, vsSize(size), CardKit.alpha(CardKit.WHITE, VS_A), 0);
        float w = vsWidth(a, b, size), l = align < 0 ? x : align == 0 ? x - w / 2 : x - w, base = cy + cap(pa) / 2;
        l += text(cv, pa, a, l, base, -1) + size * 0.22f;
        l += text(cv, vs, CardKit.VS, l, base, -1) + size * 0.22f;
        text(cv, pb, b, l, base, -1);
    }

    private static float vsTwoLines(Canvas cv, String a, String b, float size, float x, float cy, int align, float aA, float bA) {
        Paint pa = numerals(size, CardKit.alpha(CardKit.WHITE, aA), 0), pb = numerals(size, CardKit.alpha(CardKit.WHITE, bA), 0);
        pa.setFontFeatureSettings(null); pb.setFontFeatureSettings(null);
        Paint vs = paint(Ui.COND, vsSize(size), CardKit.alpha(CardKit.WHITE, VS_A), 0);
        float c = cap(pa), b1 = cy - WHO_GAP / 2, b2 = cy + WHO_GAP / 2 + c;
        text(cv, pa, a, x, b1, align);
        float wb = pb.measureText(b), wv = vs.measureText(CardKit.VS);
        if (align > 0) { text(cv, pb, b, x, b2, 1); text(cv, vs, CardKit.VS, x - wb - size * 0.22f, b2, 1); }
        else { text(cv, vs, CardKit.VS, x, b2, -1); text(cv, pb, b, x + wv + size * 0.22f, b2, -1); }
        return b2;
    }

    // ================================================================ the hero forms (spec §2.3)
    /** A team-colour bar: w wide, h tall (a code's capital height), corner 4, its foot on base. */
    static void bar(Canvas cv, float x, float base, float h, float w, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float r = Math.min(4, w / 2);
        cv.drawRoundRect(new RectF(x, base - h, x + w, base), r, r, p);
    }

    /** The width of the small rows (place, bar, code) at a hero size; 0 without rows. */
    static float rowsWidth(List<Row> rows, float size) {
        if (rows.isEmpty()) return 0;
        Paint num = paint(Ui.COND, size * MINI, CardKit.WHITE, 0), code = numerals(size * MINI, CardKit.WHITE, 0);
        float pw = 0, w = 0;
        for (Row r : rows) pw = Math.max(pw, num.measureText(r.pos));
        for (Row r : rows) w = Math.max(w, pw + MINI_NUM_GAP + MINI_BAR + MINI_BAR_GAP + code.measureText(r.code));
        return w;
    }

    /** The width of word rows (R3 / 2:27) at a hero size, tracking .02; 0 without rows. */
    static float wordRowsWidth(List<String> rows, float size) {
        Paint p = numerals(size * MINI, CardKit.WHITE, 0.02f);
        float w = 0;
        for (String s : rows) w = Math.max(w, p.measureText(s));
        return w;
    }

    /**
     * Two small rows beside a hero (spec §2.3): row 1's capitals top at the hero's capitals top, row 2's baseline on
     * the hero's baseline. Driver rows: the place (RC Regular, 60 %), 14 px, an 8 px bar, 12 px, the code (RC Bold,
     * 88 %); word rows: RC Bold, 88 % then 70 %, tracking .02.
     */
    static void rows(Canvas cv, List<Row> rows, List<String> words, float x, float base, float size) {
        float heroCap = capOf(size);
        Paint num = paint(Ui.COND, size * MINI, CardKit.alpha(CardKit.WHITE, NUM_A), 0);
        float miniCap = capOf(size * MINI);
        int n = rows != null && !rows.isEmpty() ? rows.size() : words.size();
        for (int i = 0; i < n; i++) {
            float b = n > 1 ? base - (n - 1 - i) * (heroCap - miniCap) : base;
            if (rows != null && !rows.isEmpty()) {
                Row r = rows.get(i);
                float pw = 0;
                for (Row q : rows) pw = Math.max(pw, num.measureText(q.pos));
                cv.drawText(r.pos, x, b, num);
                bar(cv, x + pw + MINI_NUM_GAP, b, miniCap, MINI_BAR, r.color);
                Paint code = numerals(size * MINI, CardKit.alpha(CardKit.WHITE, ROW_A), 0);
                cv.drawText(r.code, x + pw + MINI_NUM_GAP + MINI_BAR + MINI_BAR_GAP, b, code);
            } else {
                Paint p = numerals(size * MINI, CardKit.alpha(CardKit.WHITE, i == 0 ? ROW_A : ROW2_A), 0.02f);
                cv.drawText(words.get(i), x, b, p);
            }
        }
    }

    /** A hero laid out: its size and whether its rows (or the outcome pill's rows) are kept. */
    static final class HeroFit {
        float size, width;
        boolean rows = true;
    }

    /** [bar] CODE [P3] [rows]: the width at a size. */
    static float codeWidth(View v, float size, boolean withRows) {
        Paint p = numerals(size, CardKit.WHITE, 0);
        float w = BAR + BAR_GAP + p.measureText(v.code);
        if (!v.pos.isEmpty()) w += 0.2f * size + p.measureText(v.pos);
        if (withRows && !v.rows.isEmpty()) w += ADJ_GAP + rowsWidth(v.rows, size);
        return w;
    }

    /** A word hero (METHOD, ROUND, WORD) with its rows and the outcome pill: the width at a size. */
    static float wordWidth(View v, float size, boolean withRows, boolean pill) {
        Paint p = numerals(size, CardKit.WHITE, 0);
        float w = p.measureText(v.word);
        if (withRows && !v.wordRows.isEmpty()) w += ADJ_GAP + wordRowsWidth(v.wordRows, size);
        if (pill && !v.outcomeWord.isEmpty()) w += ADJ_GAP + CardKit.pillWidth(v.outcomeWord, Ui.BOLD, 50, false);
        return w;
    }

    /**
     * Fits a code or word hero to box: with its rows from start down to rowsMin; then without them from start down to
     * min (spec §2.3: FOCUS 165 → 120 then drop the rows; METHOD with the pill to 120 then drop the rows, then to 100).
     * withRows false: the style keeps the rows elsewhere (Clean's band, the Ticket's caption, Clash's clock row).
     */
    static HeroFit fitHero(View v, float box, float start, float rowsMin, float min, boolean withRows) {
        HeroFit f = new HeroFit();
        boolean code = v.hero == HeroKind.CODE;
        f.rows = withRows && (code ? !v.rows.isEmpty() : !v.wordRows.isEmpty());
        if (f.rows) {
            for (float s = start; s >= rowsMin; s -= 2) {
                float w = code ? codeWidth(v, s, true) : wordWidth(v, s, true, true);
                if (w <= box) { f.size = s; f.width = w; return f; }
            }
            f.rows = false;
        }
        float s = start;
        while (s > min && (code ? codeWidth(v, s, false) : wordWidth(v, s, false, true)) > box) s -= 2;
        // past the style's minimum only so as not to cross the safe edge (ΥΠΟΤΑΓΗ with its ΝΙΚΗ pill in Kit's 602)
        while (s > 72 && (code ? codeWidth(v, s, false) : wordWidth(v, s, false, true)) > box) s -= 2;
        f.size = s;
        f.width = code ? codeWidth(v, s, false) : wordWidth(v, s, false, true);
        return f;
    }

    /** Draws a fitted code hero ([bar] CODE [P3] [rows]) on base; align -1 from x, 0 centred, 1 to x. Returns its left edge. */
    static float drawCode(Canvas cv, View v, HeroFit f, float x, float base, int align) {
        float l = align < 0 ? x : align == 0 ? x - f.width / 2 : x - f.width;
        Paint p = numerals(f.size, CardKit.WHITE, 0);
        float c = cap(p);
        bar(cv, l, base, c, BAR, v.codeColor);
        float xx = l + BAR + BAR_GAP;
        xx += text(cv, p, v.code, xx, base, -1);
        if (!v.pos.isEmpty()) {
            xx += 0.2f * f.size;
            Paint pp = numerals(f.size, CardKit.alpha(CardKit.WHITE, v.posA), 0);
            xx += text(cv, pp, v.pos, xx, base, -1);
        }
        if (f.rows) rows(cv, v.rows, null, xx + ADJ_GAP, base, f.size);
        return l;
    }

    /** Draws a fitted word hero (METHOD, ROUND, WORD) with its rows and the outcome pill (66 / 50, centred on the capitals). */
    static float drawWord(Canvas cv, View v, HeroFit f, float x, float base, int align) {
        float l = align < 0 ? x : align == 0 ? x - f.width / 2 : x - f.width;
        Paint p = numerals(f.size, CardKit.alpha(CardKit.WHITE, v.wordA), 0);
        float xx = l + text(cv, p, v.word, l, base, -1);
        if (f.rows && !v.wordRows.isEmpty()) {
            rows(cv, null, v.wordRows, xx + ADJ_GAP, base, f.size);
            xx += ADJ_GAP + wordRowsWidth(v.wordRows, f.size);
        }
        if (!v.outcomeWord.isEmpty()) outcomePill(cv, v, xx + ADJ_GAP, base - cap(p) / 2, 66, 50, false);
        return l;
    }

    /** The outcome pill (WIN green, DRAW grey, LOSS wine), as CardKit.outcomePill. Returns its width. */
    static float outcomePill(Canvas cv, View v, float x, float cy, float h, float size, boolean alignRight) {
        if (v.outcomeWord.isEmpty()) return 0;
        int bg = v.outcome > 0 ? CardKit.WIN : v.outcome == 0 ? CardKit.DRAW : CardKit.LOSS;
        int fg = v.outcome < 0 ? CardKit.LOSS_TEXT : CardKit.WHITE;
        return CardKit.pill(cv, v.outcomeWord, x, cy, h, Ui.BOLD, size, bg, fg, false, alignRight);
    }

    /**
     * Two cars (a team focus): `▌NOR P1` over `▌PIA P4`, RC Bold 96 → 80, the lower on base; capitals 24 px apart (the
     * spec's 18, raised to the 24 px gap rule).
     */
    static final float TEAM_GAP = 24;

    static void drawTeam(Canvas cv, View v, float x, float base, int align, float box, float start, float min) {
        Paint p = numerals(start, CardKit.WHITE, 0);
        float size = start;
        while (size > min && teamWidth(v, p) > box) p.setTextSize(size -= 2);
        float c = cap(p);
        for (int i = 0; i < v.team.size() && i < 2; i++) {
            Row r = v.team.get(i);
            float b = base - (1 - i) * (c + TEAM_GAP);
            float w = BAR + BAR_GAP + p.measureText(r.code) + (r.pos.isEmpty() ? 0 : 0.2f * size + p.measureText(r.pos));
            float l = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
            bar(cv, l, b, c, BAR, r.color);
            float xx = l + BAR + BAR_GAP + text(cv, p, r.code, l + BAR + BAR_GAP, b, -1);
            if (!r.pos.isEmpty()) text(cv, p, r.pos, xx + 0.2f * size, b, -1);
        }
    }

    private static float teamWidth(View v, Paint p) {
        float w = 0, size = p.getTextSize();
        for (Row r : v.team) w = Math.max(w, BAR + BAR_GAP + p.measureText(r.code) + (r.pos.isEmpty() ? 0 : 0.2f * size + p.measureText(r.pos)));
        return w;
    }

    /** A word hero (NEXT, the off-season month) fitted to box from start down to min; returns the size. */
    static float drawWordAlone(Canvas cv, View v, float x, float base, int align, float box, float start, float min) {
        Paint p = numerals(start, CardKit.alpha(CardKit.WHITE, v.wordA), 0);
        p.setFontFeatureSettings(null);
        float size = start;
        while (size > min && p.measureText(v.word) > box) p.setTextSize(size -= 2);
        text(cv, p, v.word, x, base, align);
        return size;
    }

    /**
     * The time hero: RC Bold, tracking −.005, at its alpha, fit box down to min; a 12-hour clock's AM / PM after it,
     * RC Bold 0.32 em at 66 % of the hero's alpha, 0.07 em apart (football's Kit). Returns the size used.
     */
    static float drawTime(Canvas cv, View v, float x, float base, int align, float box, float start, float min) {
        Paint p = numerals(start, CardKit.alpha(CardKit.WHITE, v.timeA), -0.005f);
        Paint sp = numerals(suffixSize(start), CardKit.alpha(CardKit.WHITE, 0.66f * v.timeA), 0.02f);
        String suf = v.timeSuffix == null ? "" : v.timeSuffix;
        float size = start;
        while (size > min && timeWidth(p, sp, v.time, suf, size) > box) { p.setTextSize(size -= 2); }
        float w = timeWidth(p, sp, v.time, suf, size);
        float l = align < 0 ? x : align == 0 ? x - w / 2 : x - w + 0.005f * size;  // the tracking trails the last glyph too
        cv.drawText(v.time, l, base, p);
        if (!suf.isEmpty()) cv.drawText(suf, l + p.measureText(v.time) + 0.07f * size, base, sp);
        return size;
    }

    /** The size the time hero takes in box (start down to min), for a style that places it by its size. */
    static float timeSize(View v, float box, float start, float min) {
        Paint p = numerals(start, CardKit.WHITE, -0.005f), sp = numerals(suffixSize(start), CardKit.WHITE, 0.02f);
        String suf = v.timeSuffix == null ? "" : v.timeSuffix;
        float size = start;
        while (size > min && timeWidth(p, sp, v.time, suf, size) > box) p.setTextSize(size -= 2);
        return size;
    }

    static float timeWidth(Paint p, Paint sp, String t, String suf, float size) {
        if (suf.isEmpty()) return p.measureText(t);
        sp.setTextSize(suffixSize(size));
        return p.measureText(t) + 0.07f * size + sp.measureText(suf);
    }

    /** The 12-hour suffix: a third of the numerals, never under the 44 px floor (ClashStyle.suffix's rule). */
    static float suffixSize(float size) { return Math.max(0.32f * size, 44); }

    /**
     * The one blank slot (spec §2.3): white 18 %, 1.55 em wide and the hero's capital height at size, corner 14; its
     * right edge, left edge or centre at x.
     */
    static void slot(Canvas cv, float x, float base, float size, int align) {
        float c = capOf(size), w = 1.55f * size;
        float l = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(SLOT);
        cv.drawRoundRect(new RectF(l, base - c, l + w, base), 14, 14, p);
    }

    /**
     * The hero on one baseline for the stacked styles (Signature, Kit): every form at its spec size, fitted to box.
     * withRows: the small rows beside it (false where a style sets them apart).
     */
    static void hero(Canvas cv, View v, float x, float base, int align, float box, float timeSize, float timeMin,
                     float codeSize, float codeRowsMin, float codeMin, boolean withRows) {
        switch (v.hero) {
            case TIME: drawTime(cv, v, x, base, align, box, timeSize, timeMin); break;
            case WORD:  // fitted to the box like the other forms (a long Greek month, ΦΕΒΡΟΥΑΡΙΟΣ, never past the edge)
                drawWordAlone(cv, v, x, base, align, box, 140f / 200f * timeSize, 140f / 200f * timeMin);
                break;
            case CODE: drawCode(cv, v, fitHero(v, box, codeSize, codeRowsMin, codeMin, withRows), x, base, align); break;
            case TEAM: drawTeam(cv, v, x, base, align, box, 96f / 165f * codeSize, 80f / 165f * codeSize); break;
            case METHOD: case ROUND: {
                float rowsMin = v.outcomeWord.isEmpty() ? codeMin * 100f / 110f : codeRowsMin;
                // 8 px kept back: the pill's shadow and the last glyph's overhang (ΥΠΟΤΑΓΗ + ΝΙΚΗ in Kit's 602)
                drawWord(cv, v, fitHero(v, box - 8, codeSize, rowsMin, codeMin * 100f / 110f, withRows), x, base, align);
                break;
            }
            case SLOT: slot(cv, x, base, codeSize, align); break;
            default: break;
        }
    }

    // ================================================================ the foot (Signature, Kit)
    /**
     * The support line on centre line cy (spec §2.5): the amber not-updated warning when stale; a result's or an off
     * event's date (SignatureStyle.dateFoot: RC Regular 56, 72 %, 45 % dimmed); where to watch before and during
     * (CardKit's shared channel line through its fit); nothing otherwise.
     */
    static void foot(Canvas cv, View v, EventArt art, float x, float cy, boolean alignRight, float max) {
        if (v.stale) { SignatureStyle.staleLine(cv, x, cy, SignatureStyle.FOOT_SIZE, alignRight); return; }
        if (v.date) {
            if (!v.dateText.isEmpty()) SignatureStyle.dateFoot(cv, v.shim, x, cy, alignRight, max);
            return;
        }
        if (!v.channel || art == null) return;
        CardArt ca = art.cardArt();
        CardKit.ChannelSet s = CardKit.fitChannel(v.shim, ca, SignatureStyle.FOOT_SIZE, max, SignatureStyle.LOGO_H);
        cv.save();
        cv.clipRect(alignRight ? x - max : x, 0, alignRight ? x : x + max, Cards.H);
        CardKit.channel(cv, v.shim, ca, x, cy, s, SignatureStyle.LOGO_H, max, alignRight, CardKit.alpha(CardKit.WHITE, 0.72f));
        cv.restore();
    }

    // ================================================================ marks: circuit, octagon, discs, flags
    /** The circuit outline as a stroked path (round caps and joins), fitted (contain) and centred in box. */
    static boolean circuit(Canvas cv, EventArt art, String circuitId, RectF box, float stroke, int color) {
        if (art == null) return false;
        Path path = art.circuit(circuitId, new RectF(box.left + stroke / 2, box.top + stroke / 2, box.right - stroke / 2, box.bottom - stroke / 2));
        if (path == null) return false;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        cv.drawPath(path, p);
        return true;
    }

    /** A regular octagon outline, flat side up (spec §3.1: UFC's watermark), circumradius r. */
    static void octagon(Canvas cv, float cx, float cy, float r, float stroke, int color) {
        Path o = new Path();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            float px = cx + (float) (r * Math.cos(a)), py = cy + (float) (r * Math.sin(a));
            if (i == 0) o.moveTo(px, py); else o.lineTo(px, py);
        }
        o.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        cv.drawPath(o, p);
    }

    /** An ivory disc (or squircle, corner > 0) with a soft shadow. */
    static void plate(Canvas cv, RectF r, float corner, int color, float blur, float dy, float shadowA) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        if (shadowA > 0) p.setShadowLayer(blur, 0, dy, CardKit.alpha(0xFF000000, shadowA));
        if (corner <= 0) cv.drawOval(r, p); else cv.drawRoundRect(r, corner, corner, p);
    }

    /**
     * A face in a disc or squircle (spec §3.6): the headshot drawn cover and top-aligned (the face in the upper
     * two-thirds), clipped to the shape; with faces off, the initials (RC Bold 0.46 x the disc, ink #1B1B1F). The plate
     * under it is the caller's.
     */
    static void face(Canvas cv, Bitmap head, String initials, RectF r, float corner) {
        if (head != null) {
            cv.save();
            Path clip = new Path();
            if (corner <= 0) clip.addOval(r, Path.Direction.CW); else clip.addRoundRect(r, corner, corner, Path.Direction.CW);
            cv.clipPath(clip);
            float sc = Math.max(r.width() / head.getWidth(), r.height() / head.getHeight());
            float w = head.getWidth() * sc, h = head.getHeight() * sc;
            Cards.drawBitmap(cv, head, new RectF(r.centerX() - w / 2, r.top, r.centerX() + w / 2, r.top + h));
            cv.restore();
            return;
        }
        if (initials == null || initials.isEmpty()) return;
        Paint p = numerals(0.46f * r.width(), INK_DISC, 0);
        p.setFontFeatureSettings(null);
        text(cv, p, initials, r.centerX(), r.centerY() + cap(p) / 2, 0);
    }

    /** A flag (ESPN's country PNG, trimmed) drawn cover into r with corner radius; false when there is none. */
    static boolean flag(Canvas cv, Bitmap flag, RectF r, float corner) {
        if (flag == null) return false;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap s = Cards.smooth(flag, r.width());
        BitmapShader sh = new BitmapShader(s, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float sc = Math.max(r.width() / s.getWidth(), r.height() / s.getHeight());
        Matrix mx = new Matrix();
        mx.setScale(sc, sc);
        mx.postTranslate(r.centerX() - s.getWidth() * sc / 2, r.centerY() - s.getHeight() * sc / 2);
        sh.setLocalMatrix(mx);
        p.setShader(sh);
        cv.drawRoundRect(r, corner, corner, p);
        if (s != flag) s.recycle();
        return true;
    }

    /** The race number on a disc: RC Bold, ink #141416, capitals centred. */
    static void number(Canvas cv, String n, float cx, float cy, float size) {
        Paint p = numerals(size, NUMBER_INK, 0);
        text(cv, p, n, cx, cy + cap(p) / 2, 0);
    }

    /**
     * An F1 mark on an ivory disc or plate (spec §3.6's cascade): the race number (driver focus, when asked), else
     * the circuit outline in ink, else the flag, else nothing (an empty disc).
     */
    static void f1Mark(Canvas cv, View v, EventArt art, float cx, float cy, float circuitBox, float stroke,
                       float numberSize, float flagW, float flagCorner, boolean useNumber) {
        if (useNumber && !v.number.isEmpty()) { number(cv, v.number, cx, cy, numberSize); return; }
        RectF box = new RectF(cx - circuitBox / 2, cy - circuitBox / 2, cx + circuitBox / 2, cy + circuitBox / 2);
        if (circuit(cv, art, v.circuitId, box, stroke, INK_DISC)) return;
        flagMark(cv, v, art, cx, cy, flagW, flagCorner);
    }

    /** The host flag (4:3) centred at (cx, cy), w wide; false when there is none. */
    static boolean flagMark(Canvas cv, View v, EventArt art, float cx, float cy, float w, float corner) {
        Bitmap f = art == null || v.flagUrl == null ? null : art.flag(v.flagUrl);
        float h = w * 3 / 4;
        return flag(cv, f, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), corner);
    }
}
