package app.fanela.tv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * What a match card says, worked out once (spec: design/spec/cards/SPEC-CARDS.md §1, model.py). Every card style
 * draws the same model, so a style changes how a card looks, never what it means: the state words, the hero (kick-off
 * or score), the outcome, the opponent and the channel are decided here. Plain Java, unit-tested.
 */
final class CardModel {
    /**
     * NEXT upcoming; LIVE in play (the red pill); RESULT full time; OFF postponed, cancelled or abandoned; AWAITING past
     * the usual end without a result (no live feed); SUSPENDED stopped during play. Only LIVE is drawn as live.
     */
    enum State { NEXT, LIVE, RESULT, OFF, AWAITING, SUSPENDED }

    State state;
    /**
     * The state line's main words (display capitals): TOMORROW / TODAY / SUN 22 NOV / DELAYED / FINAL / POSTPONED /
     * ABANDONED / NO RESULT YET / SUSPENDED, and LIVE (the only one drawn as the red pill).
     */
    String stateLine = "";
    /** The segment after " · ": the date of a result, a delayed, postponed or abandoned match; "" for none. */
    String dateLine = "";
    /** The segment is dimmed (40 %): the original date of a postponed match. */
    boolean dateDim;
    /** AET / PENS: a segment between FINAL and the date ("FINAL · AET · FRI 6 NOV"); never when scores are hidden. */
    String stateExtra = "";
    /**
     * The hero is the score (LIVE, RESULT, AWAITING, SUSPENDED, an abandoned match with a score), drawn as the two
     * blank slots when the viewer hides scores or none is known yet; otherwise the hero is time (kick-off or TBA).
     */
    boolean scoreHero;
    /** Live: the clock for the pill after LIVE (67′, HT, Q3 5:12, OT 2:10); "" when the feed gave none. */
    String clock = "";
    /** Upcoming or off: the hero, the kick-off ("21:15") or TBA; the 12-hour suffix apart ("PM"). */
    String time = "", timeSuffix = "";
    /** The hero is dimmed (60 %): the time isn't known (TBA) or the match is off. */
    boolean heroDim;
    boolean tbd;
    /**
     * A word leads in place of the date (TODAY, TOMORROW, DELAYED): the Ticket's caption and Clean's top line give
     * the date instead.
     */
    boolean soon;
    /** Scores in match order (home first) and from the followed club's side. */
    String homeScore, awayScore, myScore, oppScore;
    /** The viewer hides scores on the home screen: live and result cards show the state, never the numbers. */
    boolean scoresHidden;
    /** 1 win, 0 draw, -1 loss, 9 unknown or not finished. */
    int outcome = 9;
    /** WIN / DRAW / LOSS (display capitals); "" when there is none. */
    String outcomeWord = "";
    /** The followed club at home ("VS") or away ("@"): scores are always listed home first, never flipped. */
    boolean home;
    String vs = CardKit.VS;
    /** Names in the interface's language: the opponent short (cards) and full; the followed club short. */
    String opponent = "", opponentFull = "", mine = "";
    String opponentRaw = "", mineRaw = "";
    /** The names a style may set for each club, longest first (CardModel.names: the card name, then its short form). */
    List<String> opponentNames = new ArrayList<>(), mineNames = new ArrayList<>();
    String myCrest, oppCrest;
    /**
     * The competition's names for the card (compNames): mixed case, longest first, [C1] or [C1, C2]; empty when the
     * match has none. Styles draw them in capitals (Cards.caps), Clean as written; the launcher's title and
     * description keep the full name (I18n.comp), its stage and country included.
     */
    List<String> compNames = new ArrayList<>();
    /** The sport: "FC" football, "BC" basketball. */
    String team = "FC";
    /** The data couldn't be refreshed for a while: the amber warning in place of the channel or the date (the Ticket: of its eyebrow). */
    boolean stale;
    /** Where to watch; null on results, postponed matches and when nothing is known (the line is then omitted). */
    Channel channel;
    /** The channel line for the launcher's description and the app (always in words). */
    String whereText = "";
    /** The match itself (for channel logos, which need a Context to resolve). */
    Match match;

    static final class Channel {
        /** A guess (the rights table or a season list), not a guide listing. */
        boolean likely;
        /** Season-level names only (abroad): drawn as words, never as logos or numbers. */
        boolean namesOnly;
        final List<String> names = new ArrayList<>();
        /** The channel number on Magenta TV (0: none). */
        int number;

        /** The names as one run of words (display capitals): "OR" between guesses, "/" between confirmed channels. */
        String words() { return Cards.caps(String.join(likely ? " " + w("or") + " " : " / ", names)); }

        /**
         * The names joined by " / " whether guessed or confirmed (TNT SPORTS / PRIME VIDEO): the line a card falls back
         * to, without LIKELY, before it would go under 44 px; the launcher's description still says "Likely …".
         */
        String bareWords() { return Cards.caps(String.join(" / ", names)); }
    }

    // ---------- the card vocabulary (spec §1.2), EN / EL ----------
    private static final String[][] WORDS = {
        {"tomorrow", "TOMORROW", "ΑΥΡΙΟ"}, {"today", "TODAY", "ΣΗΜΕΡΑ"}, {"yesterday", "YESTERDAY", "ΧΘΕΣ"},
        {"in_h", "IN %d H", "ΣΕ %d Ω"}, {"in_min", "IN %d MIN", "ΣΕ %d Λ"},
        {"live", "LIVE", "LIVE"}, {"ht", "HT", "ΗΜ."}, {"ot", "OT", "ΠΑΡ."},
        {"final", "FINAL", "ΤΕΛΙΚΟ"}, {"aet", "AET", "ΠΑΡ."}, {"pens", "PENS", "ΠΕΝ."},
        {"win", "WIN", "ΝΙΚΗ"}, {"draw", "DRAW", "ΙΣΟΠΑΛΙΑ"}, {"loss", "LOSS", "ΗΤΤΑ"},
        {"likely", "LIKELY", "ΠΙΘΑΝΟ"}, {"or", "OR", "Η"}, {"postponed", "POSTPONED", "ΑΝΑΒΟΛΗ"},
        {"cancelled", "CANCELLED", "ΑΚΥΡΩΣΗ"}, {"suspended", "SUSPENDED", "ΔΙΑΚΟΠΗ"}, {"tba", "TBA", "TBA"},
        {"abandoned", "ABANDONED", "ΟΡΙΣΤ. ΔΙΑΚΟΠΗ"}, {"awaiting", "NO RESULT YET", "ΑΝΑΜΟΝΗ ΣΚΟΡ"},
        {"delayed", "DELAYED", "ΚΑΘΥΣΤΕΡΗΣΗ"},
        // live clock words for the feeds' phase codes (the pill: LIVE ET HT, LIVE BREAK, LIVE PENS)
        {"et_ht", "ET HT", "ΗΜ. ΠΑΡ."}, {"et", "ET", "ΠΑΡ."}, {"break", "BREAK", "ΔΙΑΛΕΙΜΜΑ"},
    };

    // ---------- the competition on a card (LAYOUT-COMPETITION §2) ----------
    /**
     * The card's names for a competition: ids (space-separated), C1 in Greek, C1 in English, C2 in Greek, C2 in English
     * ("" for none). C2 is what fans say ("Champions", "the Conference"), used only where C1 doesn't fit. No country
     * (the card sits in the club's own row: SUPER LEAGUE can't be mistaken), no stage (the description keeps both).
     */
    private static final String[][] COMP = {
        {"gre.1", "Super League", "Super League", "", ""},
        {"gre.cup gbl.cup", "Κύπελλο", "Greek Cup", "", ""},
        {"gre.super_cup", "Super Cup", "Greek Super Cup", "", "Super Cup"},
        {"uefa.champions uefa.champions_qual", "Champions League", "Champions League", "Champions", "Champions"},
        {"uefa.europa uefa.europa_qual", "Europa League", "Europa League", "Europa", "Europa"},
        {"uefa.europa.conf uefa.europa.conf_qual", "Conference League", "Conference League", "Conference", "Conference"},
        {"uefa.super_cup", "UEFA Super Cup", "UEFA Super Cup", "Super Cup", "Super Cup"},
        {"club.friendly", "Φιλικό", "Friendly", "", ""},
        {"euroleague euroleague.po euroleague.ff euroleague.pi", "EuroLeague", "EuroLeague", "", ""},
        {"eurocup", "EuroCup", "EuroCup", "", ""},
        {"gbl", "Basket League", "Basket League", "", ""},
        {"bcl", "BCL", "BCL", "", ""},
        {"fiba.europe_cup", "FIBA Europe Cup", "FIBA Europe Cup", "Europe Cup", "Europe Cup"},
        {"tur.bsl", "Basketbol Süper Ligi", "Basketbol Süper Ligi", "BSL", "BSL"},
        {"concacaf.champions", "Concacaf Champions Cup", "Concacaf Champions Cup", "Champions Cup", "Champions Cup"},
        {"afc.champions", "AFC Champions League", "AFC Champions League", "AFC Champions", "AFC Champions"},
    };

    /**
     * The competition's names on a card, in the viewer's language, mixed case, longest first: [C1] or [C1, C2]; empty
     * for none. An id the table doesn't list is its I18n.comp name (ESPN's own name for a league Fanela has no label
     * for) with any stage cut at " · ". Pure: unit-tested.
     */
    static List<String> compNames(String id) {
        List<String> out = new ArrayList<>();
        if (id == null || id.trim().isEmpty()) return out;
        boolean el = I18n.el();
        for (String[] r : COMP) {
            if (!(" " + r[0] + " ").contains(" " + id + " ")) continue;
            out.add(el ? r[1] : r[2]);
            String c2 = el ? r[3] : r[4];
            if (!c2.isEmpty() && !c2.equals(out.get(0))) out.add(c2);
            return out;
        }
        String n = I18n.comp(id);
        int dot = n.indexOf(" · ");
        if (dot >= 0) n = n.substring(0, dot);
        n = n.trim();
        if (!n.isEmpty()) out.add(n);
        return out;
    }

    /** A word of the card vocabulary in the interface's language. */
    static String w(String key) {
        for (String[] r : WORDS) if (r[0].equals(key)) return I18n.el() ? r[2] : r[1];
        return key;
    }

    /**
     * The card for a match drawn now. Bitmap cards (the launcher's, Matchday's rows, the previews) are redrawn only when
     * a refresh runs: the 15-minute job (which the system may defer; nothing redraws when the screen comes back on),
     * so a countdown drawn into them goes stale. They say TODAY and the kick-off hero carries the time.
     */
    static CardModel of(Match m, boolean showScores, boolean stale, long now) { return of(m, showScores, stale, now, false); }

    /**
     * countdown: the caller redraws the card at least once a minute until kick-off, so the soon words may count down
     * (IN 45 MIN, IN 2 H) instead of TODAY. Without that guarantee they would be wrong by the time they are read.
     */
    static CardModel of(Match m, boolean showScores, boolean stale, long now, boolean countdown) {
        CardModel c = new CardModel();
        c.match = m;
        c.team = m.team;
        c.stale = stale;
        c.home = m.clubHome();
        c.vs = c.home ? CardKit.VS : CardKit.AT;
        c.mineRaw = m.clubHome() ? m.home : m.away;
        c.opponentRaw = m.opponentRaw();
        c.mine = I18n.shortTeam(c.mineRaw);
        c.opponent = I18n.shortTeam(c.opponentRaw);
        c.opponentFull = I18n.team(c.opponentRaw);
        c.mineNames = names(c.mine);
        c.opponentNames = names(c.opponent);
        c.myCrest = m.clubHome() ? m.homeCrest : m.awayCrest;
        c.oppCrest = m.clubHome() ? m.awayCrest : m.homeCrest;
        c.compNames = compNames(m.comp);
        c.tbd = m.tbd;

        boolean abandoned = "ABANDONED".equals(m.detail);
        if ("SUSPENDED".equals(m.detail)) c.state = State.SUSPENDED;  // stopped during play, the score kept
        else if ("off".equals(m.state)) c.state = State.OFF;
        else if ("AWAITING".equals(m.detail)) c.state = State.AWAITING;
        else if (Cards.live(m)) c.state = State.LIVE;
        else if ("post".equals(m.state)) c.state = State.RESULT;
        else c.state = State.NEXT;

        c.scoreHero = c.state == State.LIVE || c.state == State.RESULT || c.state == State.AWAITING
            || c.state == State.SUSPENDED || (c.state == State.OFF && abandoned && m.hasScore());
        if (c.scoreHero && m.hasScore()) {
            c.homeScore = m.hs; c.awayScore = m.as;
            c.myScore = m.clubScore(); c.oppScore = m.oppScore();
        }
        c.scoresHidden = !showScores && c.homeScore != null;

        switch (c.state) {
            case OFF:
                c.dateLine = cardDate(m.start);
                if (abandoned) {  // over for good: its own date at full strength; the score it stopped at, or its kick-off
                    c.stateLine = w("abandoned");
                    if (!c.scoreHero) { kickOff(c, m); c.heroDim = true; }
                } else {          // to be played another day: the original date dimmed, the new time unknown
                    c.stateLine = w("CANCELLED".equals(m.detail) ? "cancelled" : "postponed");
                    c.dateDim = true;
                    c.time = w("tba");
                    c.heroDim = true;
                }
                break;
            case AWAITING:  // past the usual end, no result yet: not live (no pill); the last score if a feed gave one
                c.stateLine = w("awaiting");
                break;
            case SUSPENDED:  // stopped during play: the score it stopped at, never a kick-off or TBA
                c.stateLine = w("suspended");
                break;
            case LIVE:
                c.stateLine = w("live");
                // scores hidden: a phase that says the match is level (extra time, penalties, overtime) isn't shown
                c.clock = c.scoresHidden && revealsLevel(m.detail) ? "" : liveClock(m.detail);
                c.channel = channel(m);
                break;
            case RESULT:
                c.stateLine = w("final");
                // extra time or penalties tell a viewer who hides scores that the match was level: only with scores
                c.stateExtra = c.scoresHidden ? ""
                    : "FT_AET".equals(m.detail) ? w("aet") : m.pens != null || "FT_PEN".equals(m.detail) ? w("pens") : "";
                c.dateLine = resultDate(m.start, now);
                int r = m.result();
                c.outcome = r;
                if (r != 9 && !c.scoresHidden) c.outcomeWord = w(r > 0 ? "win" : r == 0 ? "draw" : "loss");
                break;
            default:
                long left = m.start - now;
                int days = daysBetween(now, m.start);
                c.soon = !m.tbd && (days == 0 || days == 1);
                if (m.tbd) c.stateLine = cardDate(m.start);
                else if ("DELAYED".equals(m.detail)) {  // the kick-off was put back: say so, with its day beside it
                    c.stateLine = w("delayed");
                    c.dateLine = dayWord(m.start, now);
                    c.soon = true;
                } else if (countdown && days == 0 && left > 0 && left < 3600000L)
                    c.stateLine = String.format(Locale.ROOT, w("in_min"), minutesLeft(left));
                else if (countdown && days == 0 && left > 0 && left <= 3 * 3600000L)  // as a person says it: 2 h 05 is "in 2 h"
                    c.stateLine = String.format(Locale.ROOT, w("in_h"), Math.max(1, (int) Math.round(left / 3600000.0)));
                else if (days == 0) c.stateLine = w("today");
                else if (days == 1) c.stateLine = w("tomorrow");
                else c.stateLine = cardDate(m.start);
                if (m.tbd) { c.time = w("tba"); c.heroDim = true; }
                else kickOff(c, m);
                c.channel = channel(m);
                break;
        }
        c.whereText = c.state == State.NEXT || c.state == State.LIVE ? Cards.where(m) : "";
        return c;
    }

    /** The countdown's minutes: rounded, 1 to 59 (59 min 45 s is "in 59 min": a full hour is the next band's word). */
    static int minutesLeft(long left) { return Math.min(59, Math.max(1, (int) Math.round(left / 60000.0))); }

    /** The kick-off as the hero: "21:15", or "9:15" with its 12-hour suffix apart ("PM"). */
    private static void kickOff(CardModel c, Match m) {
        String t = I18n.time(m.start);
        int sp = t.indexOf(' ');
        if (sp > 0) { c.time = t.substring(0, sp); c.timeSuffix = t.substring(sp + 1).toUpperCase(Locale.ROOT); }
        else c.time = t;
    }

    /**
     * Whether the "Show scores" setting changes this match's card: it has a score the card would draw (live, a result,
     * suspended, awaiting or abandoned with a score). Settings uses it to redraw its previews when the switch flips.
     */
    static boolean showsScore(Match m) {
        return m != null && of(m, true, false, System.currentTimeMillis()).homeScore != null;
    }

    private static final java.util.regex.Pattern OT = java.util.regex.Pattern.compile("OT(\\d*)\\s*(.*)");

    /**
     * The live clock as the cards write it: 67′ (a prime, not an apostrophe), HT / ΗΜ., Q3 5:12, OT 2:10 / ΠΑΡ. 2:10,
     * OT2 1:00 / 2Η ΠΑΡ. 1:00. The feeds' phase codes become card words: ET_HT → ET HT, END_90 → ET (extra time
     * follows), END_120 and SHOOTOUT → PENS, BREAK → BREAK / ΔΙΑΛΕΙΜΜΑ. Any other code (capitals and "_") is left
     * out rather than shown raw.
     */
    static String liveClock(String detail) {
        if (detail == null || detail.isEmpty() || "IN_PROGRESS".equals(detail)) return "";
        switch (detail) {
            case "HT": return w("ht");
            case "ET_HT": return w("et_ht");
            case "END_90": return w("et");
            case "END_120": case "SHOOTOUT": return w("pens");
            case "BREAK": return w("break");
            default: break;
        }
        if (detail.matches("[A-Z]+(_[A-Z0-9]+)+")) return "";  // a code with no card word: never shown raw
        String d = detail.replace('\'', '′');
        java.util.regex.Matcher ot = OT.matcher(d);
        if (ot.matches() && I18n.el()) {
            String n = ot.group(1), rest = ot.group(2);
            d = (n.isEmpty() ? "" : n + "η ") + w("ot") + (rest.isEmpty() ? "" : " " + rest);
        }
        return Cards.caps(d);
    }

    private static final java.util.regex.Pattern MINUTE = java.util.regex.Pattern.compile("(\\d+)['′].*");

    /**
     * A live phase that tells the match was level at the end of normal time: football's extra time (ET_HT, END_90,
     * a minute past 90 such as 105′; 90′+3′ is stoppage time) and penalties (END_120, SHOOTOUT), basketball's
     * overtime (OT, OT2 …). With scores hidden it is left out, as AET / PENS are on a result.
     */
    static boolean revealsLevel(String detail) {
        if (detail == null) return false;
        if (detail.equals("ET_HT") || detail.equals("END_90") || detail.equals("END_120") || detail.equals("SHOOTOUT")) return true;
        if (detail.startsWith("OT")) return true;
        java.util.regex.Matcher mm = MINUTE.matcher(detail.trim());
        if (!mm.matches()) return false;
        return Integer.parseInt(mm.group(1)) > 90;  // the minute before any "+": 90'+3' is 90
    }

    /** A day as a word beside a state: TODAY / TOMORROW / YESTERDAY within a day, else the card date. */
    static String dayWord(long t, long now) {
        int d = daysBetween(now, t);
        return d == 0 ? w("today") : d == 1 ? w("tomorrow") : d == -1 ? w("yesterday") : cardDate(t);
    }

    /** "SUN 22 NOV" / "ΚΥΡ 22 ΝΟΕ": weekday, day, month, all caps (never TODAY/TOMORROW: those are soon words). */
    static String cardDate(long t) {
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM", I18n.el() ? new Locale("el") : Locale.UK);
        return Cards.caps(f.format(new java.util.Date(t)).replace(".", ""));
    }

    /** A result's date: YESTERDAY / TODAY within a day, else the card date. */
    static String resultDate(long start, long now) {
        int d = daysBetween(now, start);
        return d == 0 ? w("today") : d == -1 ? w("yesterday") : cardDate(start);
    }

    /** Calendar days from a to b in the viewer's time zone (b tomorrow = 1, yesterday = -1). */
    static int daysBetween(long a, long b) {
        Calendar ca = Calendar.getInstance(), cb = Calendar.getInstance();
        ca.setTimeInMillis(a); cb.setTimeInMillis(b);
        return (int) Math.round((I18n.startOfDay(cb) - I18n.startOfDay(ca)) / 86400000.0);
    }

    /** Where to watch, or null when nothing is known. */
    static Channel channel(Match m) {
        if (!m.channelKnown || m.channel == null || m.channel.trim().isEmpty()) return null;
        Cards.WhereParts wp = Cards.whereParts(m);
        Channel ch = new Channel();
        ch.likely = !m.channelSure;
        ch.namesOnly = Where.plain(m) && !"tv".equals(m.listedBy);
        ch.names.addAll(wp.channels);
        ch.number = wp.no;
        return ch.names.isEmpty() ? null : ch;
    }

    // ---------- a club's name on a card: one rule for every style (FB-2) ----------

    /** A name's width at a text size (a Paint on the device; a fixed advance per character in tests). */
    interface Measure { float width(String s, float size); }

    /** How a name is set: the words, the size, and whether it runs past its box (into the room the style allows). */
    static final class NameFit {
        final String text;
        final float size;
        final boolean runsOn;
        NameFit(String text, float size, boolean runsOn) { this.text = text; this.size = size; this.runsOn = runsOn; }
    }

    /** The names a card may use for a club, longest first: its card name, then the curated short form (if any). */
    static List<String> names(String cardName) {
        List<String> out = new ArrayList<>();
        if (cardName == null || cardName.isEmpty()) { out.add(""); return out; }
        out.add(cardName);
        String t = I18n.tightName(cardName);
        if (t != null && !t.equals(cardName)) out.add(t);
        return out;
    }

    /** A club's abbreviation (I18n.abbreviation), the last form before a name goes under a style's minimum; or null. */
    static String abbreviation(String cardName) { return I18n.abbreviation(cardName); }

    /**
     * Sets a club's name on a card, the same way in every style: (1) the card name in its box, from the start size down
     * to the style's minimum, 2 px a step; (2) the curated short name the same way (PARTIZAN BELGRADE → PARTIZAN,
     * CRVENA ZVEZDA → ZVEZDA); (3) a name that still doesn't fit runs on past the box into the room the style allows
     * (runOn ≥ box), at the minimum or above, the card name first; (4) where even that fails, the club's abbreviation
     * (I18n.abbreviation) if it has one. A style with a
     * two-line treatment (Ticket) tries it before (3). Never an ellipsis. Only a name too long for all of that (no real
     * club) goes below the minimum, to floor, and is clipped by the style's edge. names: from names().
     */
    static NameFit fitName(Measure m, List<String> names, float box, float runOn, float start, float min, float floor) {
        return fitName(m, names, null, box, runOn, start, min, floor);
    }

    /** As above, with the club's abbreviation (abbreviation(), or null) for step (4). */
    static NameFit fitName(Measure m, List<String> names, String abbrev, float box, float runOn, float start, float min, float floor) {
        for (String n : names) {
            float s = largest(m, n, box, start, min);
            if (s > 0) return new NameFit(n, s, false);
        }
        for (String n : names) {
            float s = largest(m, n, runOn, start, min);
            if (s > 0) return new NameFit(n, s, true);
        }
        if (abbrev != null) {
            float s = largest(m, abbrev, runOn, start, min);
            if (s > 0) return new NameFit(abbrev, s, false);
        }
        String last = names.get(names.size() - 1);
        float s = min;
        while (m.width(last, s) > runOn && s > floor) s -= 2;
        return new NameFit(last, Math.max(s, floor), true);
    }

    /** The largest size from start down to min (2 px steps) at which the name fits w; 0 when none does. */
    private static float largest(Measure m, String n, float w, float start, float min) {
        for (float s = start; s >= min; s -= 2) if (m.width(n, s) <= w) return s;
        return 0;
    }

    /** The launcher's title for this card: "Olympiacos vs Dubai", "Olympiacos 2–0 Iraklis · 67'", "Final: …". */
    String title() {
        Match m = match;
        if (scoresHidden) return m.title();
        if (state == State.LIVE && homeScore != null) return I18n.team(m.home) + " " + homeScore + "–" + awayScore + " " + I18n.team(m.away) + (clock.isEmpty() ? "" : " · " + I18n.detail(m.detail));
        if (state == State.RESULT && homeScore != null) return m.scoreLine();
        return m.title();
    }
}
