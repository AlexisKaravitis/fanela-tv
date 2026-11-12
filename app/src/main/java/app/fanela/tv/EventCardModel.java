package app.fanela.tv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything an F1 or UFC card shows, decided once (F1-UFC-ARCHITECTURE §6.2 with SPEC-F1-UFC §12): the contract
 * between stream D, which builds it (of), and stream E, which draws it (EventStyles). A pure function of the event, the
 * viewer's sport prefs, the time, the language (I18n, per thread) and the scores switch; unit-tested in every time
 * zone. Stream D owns this file.
 *
 * The card's tiers (SPEC-F1-UFC §1.2): header = eyebrow (eyebrowNames) + state (stateLine, or the LIVE pill with
 * clock); who = the F1 session (sessionNames) or the UFC bout (bout); hero (hero + its fields) with its adjunct rows;
 * support = the channel (channel), else the date (dateLine). Name lists are mixed case, longest first: the styles set
 * them in capitals with SportNames.caps (Greek the Greek way, Latin diacritics kept: MEDIĆ), Clean as written. State
 * words are display capitals, as CardModel's.
 */
final class EventCardModel {
    /**
     * TIME: time (+ timeSuffix), at heroAlpha. PODIUM (result) / ORDER (live): slots[0] the hero code, slots[1], [2]
     * the two small rows. FOCUS: focus is the hero (code + place), slots the two best others. TEAM: focusSlots, two
     * rows (two cars), no adjunct. METHOD (UFC result): heroWord ("KO/TKO") + heroRows (["R3", "2:27"]) + the outcome
     * pill for a followed fighter (bout.outcomeWord). ROUND (UFC live): heroWord "R2" + heroRows ["OF 5"]. WORD: heroWord
     * alone at heroAlpha ("NEXT" between bouts, the month of an off-season card). SLOTS: ONE blank slot (hidden scores,
     * awaiting). NONE: nothing. BOUT is unused since the spec moved the bout to the who tier (kept for compatibility).
     */
    enum Hero { TIME, PODIUM, ORDER, FOCUS, BOUT, SLOTS, NONE, TEAM, METHOD, ROUND, WORD }

    /** A driver on the card: code (VER), surname (Verstappen), team colour as the feed gives it, place. */
    static final class Slot {
        String code = "", name = "", team = "", teamId = "";
        int color, pos;
        boolean focus;
        /** "P3", or a status (DNF, DNS, DSQ) drawn at 60 % (placeDim). */
        String place = "";
        boolean placeDim;
    }

    /** One segment of the eyebrow; gold: the title segment, in the title gold. */
    static final class Seg {
        final String text; final boolean gold;
        Seg(String text, boolean gold) { this.text = text; this.gold = gold; }
        @Override public String toString() { return text; }
    }

    /**
     * The UFC bout on the who tier. Side A is the first-billed fighter, always on the left: the main event follows the
     * event's billing ("Gane vs. Hokit"), other bouts ESPN's order (SportEvent.Bout.a); a result never swaps them.
     */
    static final class BoutView {
        String nameA = "", nameB = "";
        List<String> namesA = new ArrayList<>(), namesB = new ArrayList<>();  // longest first: [Della Maddalena, Maddalena]
        /** Pre-event records (recordBefore), with scores shown or not; "" when unknown. */
        String recordA = "", recordB = "", flagA, flagB;
        SportEvent.Fighter a, b;
        boolean title;              // a title fight: drawn before the fight and in a hidden result
        String weight = "", label = "";   // "Heavyweight" / "βαρέων βαρών"; the billing line "Heavyweight title"
        int winner;                 // 0 none, draw, no contest or hidden; 1 a; 2 b (the loser is drawn at 45 %)
        String method = "";         // "KO/TKO · R3 2:27", "DEC · SPLIT · R5"; "" when hidden or not finished
        String methodWord = "", methodDetail = "", round = "", clock = "";   // KO/TKO, SPLIT, R3, 2:27; "" hidden
        int rounds;                 // scheduled rounds (3 / 5)
        String roundsWord = "";     // "OF 5" / "ΑΠΟ 5" ("" when unknown)
        /** The followed fighter's side (0 none, 1 a, 2 b) and his result: 1 win, 0 draw, -1 loss, 9 none or hidden. */
        int followed, outcome = 9;
        String outcomeWord = "";    // WIN / LOSS / DRAW for a followed fighter's result; "" otherwise
        /** The bout's segment when it is not the main card ("PRELIMS", "EARLY"); "" on the main card. */
        String segment = "";
        boolean live, swapped;
        /** Both fighters have a headshot URL (styles draw both faces or neither: initials). */
        boolean facesOk;
    }

    String sport;                    // "f1" | "ufc"
    CardModel.State state = CardModel.State.NEXT;
    /** The header's word alone (SPEC §12.1): TOMORROW / SUN 22 NOV / LIVE / FINAL / CANCELLED / SEASON 2027. */
    String stateLine = "";
    /** The support line's date (results, OFF), drawn where the channel would be; dimmed (45 %) when dateDim. */
    String dateLine = "", stateExtra = "";
    boolean dateDim, soon, stale;
    /**
     * After LIVE in the pill: "34/50" (F1 race with a total), "LAP 34" (no total); "" otherwise (UFC: the hero has the
     * round) and always while F1Feed.LIVE_LAP_TRUSTED is false.
     */
    String clock = "";
    /** The Ticket's stub caption while live: "LAP 34/50" / "ΓΥΡΟΣ 34/50"; "" when no lap. */
    String lapLine = "";
    List<String> compNames = new ArrayList<>();   // ["Formula 1"] / ["UFC"]
    List<String> titleNames = new ArrayList<>();  // ["Las Vegas Grand Prix", "Las Vegas GP"] / ["UFC 334"]
    /** The eyebrow's variants, longest first, each a list of segments joined by " · " (SPEC §4.1). */
    final List<List<Seg>> eyebrowNames = new ArrayList<>();
    /** The place for Clash's top-left (F1): ["Las Vegas"]. */
    List<String> placeNames = new ArrayList<>();
    /** The session or segment, display capitals (SportNames.caps): RACE, QUALIFYING, MAIN CARD. */
    String sessionLabel = "";
    /** The session's names, mixed case, longest first (F1's who tier): [Qualifying, Quali]; UFC: the segment. */
    List<String> sessionNames = new ArrayList<>();
    Hero hero = Hero.NONE;
    String time = "", timeSuffix = "";
    boolean heroDim, tbd;
    /** The hero's alpha: 1; .6 for TBA and "NEXT"; .4 for a cancelled or not-trusted live time. */
    float heroAlpha = 1f;
    String heroWord = "";
    final List<String> heroRows = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();      // PODIUM / ORDER: [0] hero, [1] [2] rows; FOCUS: the two best others
    Slot focus;                                       // FOCUS / TEAM: the first focus slot
    final List<Slot> focusSlots = new ArrayList<>();  // FOCUS: one; TEAM: two (by place)
    BoutView bout;                                    // UFC
    /** The session's P1 (F1, scores shown): the launcher's "Verstappen wins" when the hero shows a focus instead. */
    Slot leader;
    boolean scoresHidden;
    CardModel.Channel channel; String whereText = "";
    String circuitId = "";
    /** The card's colours: a focus team's colour and trim (themed), else the sport's house tokens (SPEC §5). */
    int accent, trim;
    boolean themed;
    /** A driver focus's race number ("" when unknown: the feed does not give it yet). */
    String number = "";
    String flagCode = ""; int flagColour;
    boolean facesOk;
    SportEvent event; SportEvent.Session session;

    static final int F1_GRAPHITE = 0xFF32353B, F1_IVORY = 0xFFE9E6DF, UFC_RED = 0xFFB5121B, UFC_BLUE = 0xFF1D4FA8,
        TITLE_GOLD = 0xFFE8C25A;
    /** The house accents (no focus). */
    static final int F1_HOUSE = F1_GRAPHITE, UFC_HOUSE = UFC_RED;
    /** An F1 weekend this far away, and the next one at that, is an off-season card ("SEASON 2027"). */
    static final long OFF_SEASON = 45 * 86400000L;

    /** The live order is drawn only once a recorded feed has shown it moving (F1Feed.LIVE_ORDER_TRUSTED); tests set it. */
    static boolean orderTrusted = F1Feed.LIVE_ORDER_TRUSTED;

    static EventCardModel of(SportEvent e, String sessionId, boolean last, SportPrefs p, boolean showScores, boolean stale, long now) {
        return of(e, sessionId, last, p, showScores, stale, now, null);
    }

    /**
     * The card. sessionId: the F1 session it is about (null: the live one, else the next, else the race); UFC decides
     * its own headline bout and segment (followed fighter, the bout in the cage, the main event). last: the "latest"
     * tile. roster: driver id -> a recent entry (team, colour), so a focus colours the card before the session has an
     * order (EventCards.rosterFrom); may be null.
     */
    static EventCardModel of(SportEvent e, String sessionId, boolean last, SportPrefs p, boolean showScores, boolean stale, long now,
                             Map<String, SportEvent.Entrant> roster) {
        EventCardModel m = new EventCardModel();
        m.event = e; m.sport = e.sport; m.stale = stale; m.scoresHidden = !showScores;
        if (p == null) p = SportPrefs.none();
        m.titleNames = SportNames.titleNames(e);
        if (SportEvent.F1.equals(e.sport)) f1(m, e, sessionId, last, p, now, roster);
        else ufc(m, e, p, now);
        m.heroDim = m.heroAlpha < 1f;
        return m;
    }

    // ---------- Formula 1 ----------
    private static void f1(EventCardModel m, SportEvent e, String sid, boolean last, SportPrefs p, long now, Map<String, SportEvent.Entrant> roster) {
        m.compNames.add(SportNames.w("formula1"));
        m.circuitId = e.circuitId == null ? "" : e.circuitId;
        m.placeNames = SportNames.placeNames(e);
        m.flagCode = SportNames.flagCode(e); m.flagColour = SportNames.flagColour(e);
        String card = SportNames.gpCard(e), tight = SportNames.gpCardTight(e), f1 = SportNames.w("f1");
        m.eyebrowNames.add(segs(f1, card));
        if (tight != null) m.eyebrowNames.add(segs(f1, tight));
        m.eyebrowNames.add(segs(card));
        if (tight != null) m.eyebrowNames.add(segs(tight));
        colours(m, e, p, roster);

        SportEvent.Session s = e.session(sid);
        if (s == null) s = e.live();
        if (s == null) s = e.next(now);
        if (s == null) s = scoring(e);
        m.session = s;
        if (s == null) { m.stateLine = CardModel.cardDate(e.start); return; }
        m.sessionNames = SportNames.sessionNames(s);
        m.sessionLabel = SportNames.caps(m.sessionNames.get(0));
        m.tbd = s.tbd;

        if (!last && "pre".equals(e.state) && "pre".equals(s.state) && e.start - now > OFF_SEASON) {  // the off-season card
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(e.start);
            m.stateLine = String.format(Locale.ROOT, SportNames.w("season"), cal.get(Calendar.YEAR));
            m.hero = Hero.WORD;
            m.heroWord = SportNames.caps(new SimpleDateFormat("LLLL", I18n.el() ? new Locale("el") : Locale.UK).format(new java.util.Date(e.start)));
            return;
        }
        if ("off".equals(s.state)) { off(m, s); return; }
        if ("post".equals(s.state) && "AWAITING".equals(s.detail)) {
            m.state = CardModel.State.AWAITING;
            m.stateLine = SportNames.w("awaiting");
            m.hero = Hero.SLOTS;
            return;
        }
        if ("post".equals(s.state)) {
            m.state = CardModel.State.RESULT;
            m.stateLine = CardModel.w("final");
            m.dateLine = CardModel.resultDate(s.start, now);
            order(m, e, s, p, true);
            return;
        }
        if ("in".equals(s.state)) {
            boolean stopped = "RED_FLAG".equals(s.detail) || "SUSPENDED".equals(s.detail);
            m.state = stopped ? CardModel.State.SUSPENDED : CardModel.State.LIVE;
            m.stateLine = CardModel.w(stopped ? "suspended" : "live");
            if (!stopped) { lap(m, e, s); channel(m, e, s); }
            if (orderTrusted && !s.order.isEmpty()) order(m, e, s, p, false);
            else { kickOff(m, s.start); m.hero = Hero.TIME; m.heroAlpha = 0.4f; }
            return;
        }
        next(m, s, now);
        channel(m, e, s);
    }

    /** The scoring session: the race, else the last one by start that is not off, else the last. */
    static SportEvent.Session scoring(SportEvent e) {
        SportEvent.Session last = null, any = null;
        for (SportEvent.Session s : e.byStart()) {
            any = s;
            if ("RACE".equals(s.kind)) return s;
            if (!"off".equals(s.state)) last = s;
        }
        return last != null ? last : any;
    }

    /**
     * The lap in the pill and the Ticket's caption: races and sprints only (B gives no lap for the others), and only
     * while F1Feed.LIVE_LAP_TRUSTED: otherwise the pill says LIVE alone and the Ticket has no caption (SPEC §2.1's
     * live practice).
     */
    private static void lap(EventCardModel m, SportEvent e, SportEvent.Session s) {
        int lap = F1Feed.shownLap(s);
        if (lap <= 0 || !("RACE".equals(s.kind) || "SPRINT".equals(s.kind))) return;
        int laps = s.laps > 0 ? s.laps : "RACE".equals(s.kind) ? e.laps : 0;
        String l = laps > 0 ? lap + "/" + laps : String.valueOf(lap);
        m.clock = laps > 0 ? l : SportNames.w("lap") + " " + l;
        m.lapLine = SportNames.w("lap") + " " + l;
    }

    /** The hero of a live or finished session: leader + two rows, the focus driver, two focus cars, or one slot. */
    private static void order(EventCardModel m, SportEvent e, SportEvent.Session s, SportPrefs p, boolean result) {
        List<SportEvent.Entrant> order = sorted(s.order);
        if (m.scoresHidden || order.isEmpty()) { m.hero = Hero.SLOTS; return; }
        if (order.get(0).pos == 1) m.leader = slot(order.get(0), false);
        List<SportEvent.Entrant> focus = new ArrayList<>();
        for (String ref : p.focus)
            for (SportEvent.Entrant x : order)
                if ((ref.equals("d:" + x.id) || ref.equals("t:" + x.teamId)) && !focus.contains(x)) focus.add(x);
        Collections.sort(focus, BY_POS);
        while (focus.size() > 2) focus.remove(focus.size() - 1);
        if (focus.size() == 2) {
            m.hero = Hero.TEAM;
            for (SportEvent.Entrant x : focus) m.focusSlots.add(slot(x, true));
            m.focus = m.focusSlots.get(0);
            return;
        }
        if (focus.size() == 1) {
            m.hero = Hero.FOCUS;
            m.focus = slot(focus.get(0), true);
            m.focusSlots.add(m.focus);
            for (SportEvent.Entrant x : order) if (x != focus.get(0) && m.slots.size() < 2) m.slots.add(slot(x, false));
            return;
        }
        m.hero = result ? Hero.PODIUM : Hero.ORDER;
        for (SportEvent.Entrant x : order) if (m.slots.size() < 3) m.slots.add(slot(x, false));
    }

    private static final Comparator<SportEvent.Entrant> BY_POS = new Comparator<SportEvent.Entrant>() {
        public int compare(SportEvent.Entrant a, SportEvent.Entrant b) {
            int pa = a.pos > 0 ? a.pos : Integer.MAX_VALUE, pb = b.pos > 0 ? b.pos : Integer.MAX_VALUE;
            return Integer.compare(pa, pb);
        }
    };

    /** The order by place (unplaced last, the feed's order kept among equals). */
    static List<SportEvent.Entrant> sorted(List<SportEvent.Entrant> order) {
        List<SportEvent.Entrant> out = new ArrayList<>(order);
        Collections.sort(out, BY_POS);
        return out;
    }

    static Slot slot(SportEvent.Entrant x, boolean focus) {
        Slot s = new Slot();
        s.code = x.code == null || x.code.isEmpty() ? SportNames.caps(SportNames.tight(SportNames.surname(x))) : x.code;
        s.name = SportNames.surname(x);
        s.team = x.team == null ? "" : x.team; s.teamId = x.teamId == null ? "" : x.teamId;
        s.color = x.color; s.pos = x.pos; s.focus = focus;
        String g = x.gap == null ? "" : x.gap.trim().toUpperCase(Locale.ROOT);
        if (g.equals("DNF") || g.equals("DNS") || g.equals("DSQ")) { s.place = SportNames.w(g.toLowerCase(Locale.ROOT)); s.placeDim = true; }
        else s.place = x.pos > 0 ? "P" + x.pos : "";
        return s;
    }

    /** A focus colours the card: the first focus ref's team colour and trim (SPEC §5.2); else graphite and ivory. */
    private static void colours(EventCardModel m, SportEvent e, SportPrefs p, Map<String, SportEvent.Entrant> roster) {
        m.accent = F1_GRAPHITE; m.trim = F1_IVORY;
        if (p.focus.isEmpty()) return;
        String ref = p.focus.get(0);
        SportEvent.Entrant x = resolve(ref, e, roster);
        if (x != null && ref.startsWith("d:") && x.number != null && !x.number.isEmpty()) m.number = x.number;  // Kit, Clash
        if (x == null || x.color == 0) return;
        m.accent = x.color | 0xFF000000;
        m.themed = true;
        int t = SportNames.trim(x.teamId, x.team);
        m.trim = t != 0 ? t | 0xFF000000 : F1_IVORY;
    }

    /** A focus ref's entry: in this weekend's orders (newest session first), else the roster. */
    static SportEvent.Entrant resolve(String ref, SportEvent e, Map<String, SportEvent.Entrant> roster) {
        if (ref == null || ref.length() < 3) return null;
        boolean driver = ref.startsWith("d:");
        String id = ref.substring(2);
        List<SportEvent.Session> ss = e.byStart();
        Collections.reverse(ss);
        for (SportEvent.Session s : ss)
            for (SportEvent.Entrant x : s.order) if (driver ? id.equals(x.id) : id.equals(x.teamId)) return x;
        if (roster == null) return null;
        if (driver) return roster.get(id);
        for (SportEvent.Entrant x : roster.values()) if (id.equals(x.teamId)) return x;
        return null;
    }

    // ---------- UFC ----------
    private static void ufc(EventCardModel m, SportEvent e, SportPrefs p, long now) {
        m.compNames.add(SportNames.w("ufc"));
        m.accent = UFC_RED; m.trim = UFC_BLUE;
        SportEvent.Bout main = e.mainEvent(), followed = followedBout(e, p), cage = inCage(e), head;
        SportEvent.Session seg;
        String es = e.state == null ? "pre" : e.state;
        if ("off".equals(es)) {
            head = followed != null ? followed : main;
            seg = segmentOf(e, head);
            setBout(m, e, head, p, seg);
            off(m, seg);
        } else if ("in".equals(es)) {
            if (cage != null) {
                head = cage;
                seg = segmentOf(e, head);
                setBout(m, e, head, p, seg);
                m.state = CardModel.State.LIVE;
                m.stateLine = CardModel.w("live");
                if (m.scoresHidden) m.hero = Hero.SLOTS;
                else if (UfcFeed.shownRound(head) > 0) {  // the round only while UfcFeed.LIVE_ROUND_TRUSTED
                    m.hero = Hero.ROUND;
                    m.heroWord = "R" + UfcFeed.shownRound(head);
                    if (!m.bout.roundsWord.isEmpty()) m.heroRows.add(m.bout.roundsWord);
                } else { kickOff(m, seg == null ? e.start : seg.start); m.hero = Hero.TIME; m.heroAlpha = 0.4f; }
            } else {
                head = nextBout(e);
                seg = segmentOf(e, head);
                setBout(m, e, head, p, seg);
                m.state = CardModel.State.LIVE;
                m.stateLine = CardModel.w("live");
                if (seg != null && seg.start > now && !seg.tbd) { kickOff(m, seg.start); m.hero = Hero.TIME; }
                else { m.hero = Hero.WORD; m.heroWord = SportNames.w("next"); m.heroAlpha = 0.6f; }
            }
            // the channel of the segment now on; between bouts, of the next bout's segment once it is the one to come
            SportEvent.Session on = e.live();
            boolean upcoming = cage == null && seg != null && seg.start > now;
            channel(m, e, cage != null || upcoming || on == null ? seg : on);
        } else if ("post".equals(es)) {
            head = followed != null && "post".equals(followed.state) ? followed : main;
            seg = segmentOf(e, head);
            setBout(m, e, head, p, seg);
            boolean done = head != null && "post".equals(head.state) && (head.outcome != 0 || !head.method.isEmpty());
            if (!done) {
                m.state = CardModel.State.AWAITING;
                m.stateLine = SportNames.w("awaiting");
                m.hero = Hero.SLOTS;
            } else {
                m.state = CardModel.State.RESULT;
                m.stateLine = CardModel.w("final");
                m.dateLine = CardModel.resultDate(seg == null ? e.end : seg.start, now);
                if (m.scoresHidden || m.bout.methodWord.isEmpty()) m.hero = Hero.SLOTS;
                else {
                    m.hero = Hero.METHOD;
                    m.heroWord = m.bout.methodWord;
                    m.heroRows.addAll(methodRows(m.bout));
                }
            }
        } else {
            head = followed != null ? followed : main;
            seg = segmentOf(e, head);
            setBout(m, e, head, p, seg);
            if (head != null && "off".equals(head.state)) off(m, seg);
            else if (seg != null) { next(m, seg, now); channel(m, e, seg); }
            else { m.stateLine = CardModel.cardDate(e.start); kickOff(m, e.start); m.hero = Hero.TIME; }
        }
        m.session = seg;
        m.facesOk = m.bout != null && m.bout.facesOk;
        eyebrow(m, e, m.bout, seg);
    }

    /** The followed fighter's bout on this card (the most prominent if two are followed); null when none. */
    static SportEvent.Bout followedBout(SportEvent e, SportPrefs p) {
        SportEvent.Bout best = null;
        for (String f : p.fighters) {
            SportEvent.Bout b = e.boutOf(f);
            if (b != null && (best == null || b.order < best.order)) best = b;
        }
        return best;
    }

    /** The bout in the cage (state "in"), or null. */
    static SportEvent.Bout inCage(SportEvent e) {
        SportEvent.Bout best = null;
        for (SportEvent.Session s : e.sessions)
            for (SportEvent.Bout b : s.bouts)
                if ("in".equals(b.state) && (best == null || b.order < best.order)) best = b;
        return best;
    }

    /** Between bouts: the next bout to be fought (the earliest segment, then the highest bout number); else the main event. */
    static SportEvent.Bout nextBout(SportEvent e) {
        SportEvent.Bout best = null;
        long bestStart = Long.MAX_VALUE;
        for (SportEvent.Session s : e.byStart())
            for (SportEvent.Bout b : s.bouts) {
                if (!"pre".equals(b.state)) continue;
                if (best == null || s.start < bestStart || (s.start == bestStart && b.order > best.order)) { best = b; bestStart = s.start; }
            }
        return best != null ? best : e.mainEvent();
    }

    /** A bout's segment; the main card when the bout is unknown. */
    static SportEvent.Session segmentOf(SportEvent e, SportEvent.Bout b) {
        SportEvent.Session s = b == null ? null : e.sessionOf(b);
        if (s != null) return s;
        for (SportEvent.Session x : e.sessions) if ("MAIN".equals(SportNames.segment(x))) return x;
        List<SportEvent.Session> by = e.byStart();
        return by.isEmpty() ? null : by.get(by.size() - 1);
    }

    private static void setBout(EventCardModel m, SportEvent e, SportEvent.Bout b, SportPrefs p, SportEvent.Session seg) {
        if (seg != null) {
            m.sessionNames = SportNames.sessionNames(seg.kind.isEmpty() ? SportNames.segment(seg) : seg.kind, seg.typeAbbr);
            m.sessionLabel = SportNames.caps(m.sessionNames.get(0));
        }
        if (b == null) return;
        m.bout = view(e, b, p, seg, m.scoresHidden);
    }

    /** The bout as the card shows it (SPEC §2.1, §2.4, §6). */
    static BoutView view(SportEvent e, SportEvent.Bout b, SportPrefs p, SportEvent.Session seg, boolean hidden) {
        BoutView v = new BoutView();
        String[] billed = b == e.mainEvent() ? SportNames.billing(e) : null;
        boolean swap = billed != null && SportNames.names(billed[0], b.b) && SportNames.names(billed[1], b.a);
        boolean billedOk = billed != null && (swap || (SportNames.names(billed[0], b.a) && SportNames.names(billed[1], b.b)));
        v.swapped = swap;
        v.a = swap ? b.b : b.a;
        v.b = swap ? b.a : b.b;
        v.nameA = billedOk ? billed[0] : SportNames.surname(v.a);
        v.nameB = billedOk ? billed[1] : SportNames.surname(v.b);
        v.namesA = SportNames.personNames(v.nameA);
        v.namesB = SportNames.personNames(v.nameB);
        v.recordA = record(v.a, b);
        v.recordB = record(v.b, b);
        v.flagA = v.a == null ? null : v.a.flag;
        v.flagB = v.b == null ? null : v.b.flag;
        v.title = b.title;
        v.weight = SportNames.weightClass(b.weightClass);
        v.label = SportNames.billingLine(b);
        v.rounds = b.rounds;
        v.roundsWord = b.rounds > 0 ? String.format(Locale.ROOT, SportNames.w("of"), b.rounds) : "";
        String sg = SportNames.segment(seg);
        v.segment = sg.isEmpty() || "MAIN".equals(sg) ? "" : sg;
        v.live = "in".equals(b.state);
        v.facesOk = v.a != null && v.b != null && !blank(v.a.headshot) && !blank(v.b.headshot);
        if (p != null) {
            if (v.a != null && p.followsFighter(v.a.id)) v.followed = 1;
            else if (v.b != null && p.followsFighter(v.b.id)) v.followed = 2;
        }
        if (hidden || !"post".equals(b.state)) return v;
        int side = b.outcome == 1 ? 1 : b.outcome == 2 ? 2 : 0;
        if (swap && side != 0) side = 3 - side;
        v.winner = side;
        String dec = Cards.key(b.method + " " + b.methodDetail).toLowerCase(Locale.ROOT);
        String mk = Cards.key(b.method).trim();
        boolean decision = mk.startsWith("DEC") || dec.contains("decision");
        if (b.outcome == 3) v.methodWord = SportNames.w("draw");
        else if (b.outcome == 4) v.methodWord = SportNames.w("nc");
        else if (mk.startsWith("KO") || mk.contains("TKO") || dec.contains("kotko")) v.methodWord = SportNames.w("ko");
        else if (mk.startsWith("SUB") || dec.contains("submission")) v.methodWord = SportNames.w("sub");
        else if (decision) v.methodWord = SportNames.w("dec");
        else if (mk.equals("DQ") || dec.contains("disqualif")) v.methodWord = SportNames.w("dq");
        else v.methodWord = SportNames.caps(b.method == null ? "" : b.method.trim());
        if (decision || b.outcome == 3)
            v.methodDetail = dec.contains("split") ? SportNames.w("split") : dec.contains("majority") ? SportNames.w("majority")
                : dec.contains("unanimous") ? SportNames.w("unanimous") : "";
        v.round = b.round > 0 ? "R" + b.round : "";
        String clock = b.clock == null ? "" : b.clock.trim();
        v.clock = decision || b.outcome == 3 || clock.equals("-") || clock.equals("0:00") ? "" : clock;
        StringBuilder mm = new StringBuilder(v.methodWord);
        if (!v.methodDetail.isEmpty()) mm.append(" · ").append(v.methodDetail);
        if (!v.round.isEmpty()) mm.append(" · ").append(v.round).append(v.clock.isEmpty() ? "" : " " + v.clock);
        v.method = mm.toString();
        if (v.followed != 0) {
            v.outcome = b.outcome == 3 ? 0 : v.winner == 0 ? 9 : v.winner == v.followed ? 1 : -1;
            v.outcomeWord = v.outcome == 9 ? "" : CardModel.w(v.outcome > 0 ? "win" : v.outcome == 0 ? "draw" : "loss");
        }
        return v;
    }

    /**
     * The hero's small rows for a method: KO/TKO [R3, 2:27]; a decision its kind alone [SPLIT] (a decision is always
     * the last round: R5 would only crowd it, and the Greek kind, ΔΙΧΑΣΜΕΝΗ, needs the room), [R5] when ESPN gives no kind;
     * a draw none (DRAW / ΙΣΟΠΑΛΙΑ stands alone: beside it ΔΙΧΑΣΜΕΝΗ fell to 37 px in Signature; the app has the kind).
     */
    static List<String> methodRows(BoutView v) {
        List<String> out = new ArrayList<>();
        if (v.methodWord.equals(SportNames.w("draw"))) return out;
        if (!v.methodDetail.isEmpty()) out.add(v.methodDetail);
        else if (!v.round.isEmpty()) out.add(v.round);
        if (!v.clock.isEmpty() && out.size() < 2) out.add(v.clock);
        return out;
    }

    /** The record a card draws: always the pre-event one (a pre bout's current record is that). */
    private static String record(SportEvent.Fighter f, SportEvent.Bout b) {
        if (f == null) return "";
        if (!blank(f.recordBefore)) return f.recordBefore;
        return "pre".equals(b.state) && f.record != null ? f.record : "";
    }

    private static void eyebrow(EventCardModel m, SportEvent e, BoutView v, SportEvent.Session seg) {
        String name = SportNames.ufcName(e);
        boolean numbered = name.matches("UFC \\d+");
        if (!numbered) { m.eyebrowNames.add(segs(name)); return; }
        if (v != null && v.title) {
            List<Seg> t = segs(name);
            t.add(new Seg(SportNames.w2("title"), true));
            m.eyebrowNames.add(t);
        } else if (v != null && !v.segment.isEmpty()) {  // the segment's names, longest first (EARLY PRELIMS, PRELIMS)
            for (String n : SportNames.sessionNames(v.segment, "")) m.eyebrowNames.add(segs(name, n));
        }
        m.eyebrowNames.add(segs(name));
    }

    // ---------- shared ----------
    /** A session or segment that is off: CANCELLED with the planned time at 40 %, or POSTPONED with TBA at 60 %. */
    private static void off(EventCardModel m, SportEvent.Session s) {
        m.state = CardModel.State.OFF;
        if (s == null) { m.stateLine = SportNames.w("cancelled"); m.hero = Hero.NONE; return; }
        boolean postponed = "POSTPONED".equals(s.detail);
        m.stateLine = SportNames.w(postponed ? "postponed" : "cancelled");
        m.dateLine = CardModel.cardDate(s.start);
        m.dateDim = true;
        m.hero = Hero.TIME;
        if (postponed || s.tbd) { m.time = CardModel.w("tba"); m.heroAlpha = 0.6f; }
        else { kickOff(m, s.start); m.heroAlpha = 0.4f; }
    }

    /** A session to come: the day word (TODAY / TOMORROW / SUN 22 NOV / DELAYED) and the start time, or TBA at 60 %. */
    private static void next(EventCardModel m, SportEvent.Session s, long now) {
        m.state = CardModel.State.NEXT;
        m.hero = Hero.TIME;
        int days = CardModel.daysBetween(now, s.start);
        m.soon = !s.tbd && (days == 0 || days == 1);
        if (s.tbd) m.stateLine = CardModel.cardDate(s.start);
        else if ("DELAYED".equals(s.detail)) { m.stateLine = CardModel.w("delayed"); m.soon = true; }
        else m.stateLine = days == 0 ? CardModel.w("today") : days == 1 ? CardModel.w("tomorrow") : CardModel.cardDate(s.start);
        if (s.tbd) { m.time = CardModel.w("tba"); m.heroAlpha = 0.6f; }
        else kickOff(m, s.start);
    }

    /** The start as the hero: "06:00", or "6:00" with its 12-hour suffix apart ("AM"). */
    private static void kickOff(EventCardModel m, long t) {
        String s = I18n.time(t);
        int sp = s.indexOf(' ');
        if (sp > 0) { m.time = s.substring(0, sp); m.timeSuffix = s.substring(sp + 1).toUpperCase(Locale.ROOT); }
        else { m.time = s; m.timeSuffix = ""; }
    }

    /** The channel line: SessionWatch.asMatch through CardModel.channel (null when nothing is known), and its words. */
    private static void channel(EventCardModel m, SportEvent e, SportEvent.Session s) {
        m.channel = null; m.whereText = "";
        if (s == null || s.watch == null) return;
        Match as = s.watch.asMatch(s.start, e.sport);
        m.channel = CardModel.channel(as);
        // the card's own words name the right app (ANT1+, not Magenta TV); SportWhere's only when no channel is known
        // ("Check your provider's guide")
        String own = words(m.channel), w = SportWhere.where(s, false);
        m.whereText = !own.isEmpty() ? own : w == null ? "" : w;
    }

    /**
     * The channel in words when SportWhere has none: "Likely ANT1+", "Magenta Sport 6 · ch. 310". Not Cards.where: its
     * app name is football's (Magenta TV for any package), wrong for ANT1+.
     */
    static String words(CardModel.Channel ch) {
        if (ch == null || ch.names.isEmpty()) return "";
        String names = String.join(ch.likely ? " " + I18n.t("or") + " " : " / ", ch.names);
        return (ch.likely ? I18n.t("likely_channel_cap") + " " : "") + names + (ch.number > 0 ? " · " + I18n.f("channel_no", ch.number) : "");
    }

    private static List<Seg> segs(String... parts) {
        List<Seg> out = new ArrayList<>();
        for (String s : parts) out.add(new Seg(s, false));
        return out;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    // ---------- launcher texts ----------
    /** The event's name in the launcher's words: "Las Vegas Grand Prix" / "Gane VS Hokit" (UFC 334 when no bout). */
    String subject() {
        if (SportEvent.UFC.equals(sport) && bout != null && !bout.nameA.isEmpty() && !bout.nameB.isEmpty())
            return bout.nameA + " " + CardKit.VS + " " + bout.nameB;
        return titleNames.isEmpty() ? "" : titleNames.get(0);
    }

    /** The launcher's title, spoiler-safe (SPEC §4.1): "Las Vegas Grand Prix", "LIVE · …", "Final · …". */
    String title() {
        String s = subject();
        switch (state) {
            case LIVE: return I18n.t("live") + " · " + s;
            case RESULT: case AWAITING: return I18n.t("final") + " · " + s;
            case OFF: return s + " · " + I18n.t(stateLine.equals(SportNames.w("postponed")) ? "POSTPONED" : "CANCELLED");
            case SUSPENDED: return I18n.t("SUSPENDED") + " · " + s;
            default: return s;
        }
    }

    /**
     * The launcher's title with scores shown: "Norris wins · Las Vegas Grand Prix", "Gane VS Hokit · KO/TKO R3";
     * otherwise as title(). Never a result when the model was built with scores hidden.
     */
    String headline() {
        if (scoresHidden || state != CardModel.State.RESULT) return title();
        if (SportEvent.F1.equals(sport)) {
            Slot first = leader;
            if (first == null && hero == Hero.PODIUM && !slots.isEmpty()) first = slots.get(0);
            if (first == null || first.name.isEmpty()) return title();
            String kind = session == null ? "" : session.kind;
            String pat = "QUAL".equals(kind) ? "pole" : "SQ".equals(kind) ? "sprint_pole" : "SPRINT".equals(kind) ? "wins_sprint" : "wins";
            return String.format(Locale.ROOT, SportNames.w2(pat), first.name) + " · " + subject();
        }
        if (bout == null || bout.methodWord.isEmpty()) return title();
        return subject() + " · " + bout.methodWord + (bout.round.isEmpty() ? "" : " " + bout.round);
    }

    @Override public String toString() {
        return sport + " " + state + " [" + stateLine + "|" + clock + "] " + sessionLabel + " " + hero + " " + time + heroWord + heroRows
            + " slots=" + slots.size() + " date=" + dateLine;
    }
}
