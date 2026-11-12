package app.fanela.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * One Formula 1 weekend or one UFC event (F1-UFC-ARCHITECTURE §1.2). Kept apart from Match, which is two-sided
 * throughout the code: an F1 weekend is a list of sessions with 22 entrants, a UFC event three card segments of bouts.
 * Stored in files/events1.json by EventStore; identity is the ESPN event id only (a UFC main-event change renames the
 * event, the id stays). Times are UTC ms; day words and dates are made in the viewer's zone when shown.
 */
final class SportEvent {
    static final String F1 = "f1", UFC = "ufc";

    String sport;                 // "f1" | "ufc"
    String id;                    // ESPN event id, e.g. "600057449"; the only identity
    String name = "", shortName = "";  // ESPN's, sponsors included; never drawn raw (SportNames)
    long start, end;              // first and last session/segment start, UTC ms
    String state = "pre";         // derived (SportEngine.derive): pre | in | post | off
    // F1
    String circuitId = "", circuitName = "", city = "", country = ""; int laps; float lengthKm;
    // UFC
    String venue = ""; boolean numbered; int number;  // numbered: name matches ^UFC \d+ ; 334
    final List<Session> sessions = new ArrayList<>();  // F1 in start order; UFC: EARLY, PRELIMS, MAIN
    long fetchedAt;               // last successful detail fetch (scoreboard / fightcenter)
    int misses;                   // detail fetches in a row that did not list this id (EventStore.merge)

    String key() { return sport + "|espn:" + id; }

    Session session(String sessionId) {
        if (sessionId == null) return null;
        for (Session s : sessions) if (sessionId.equals(s.id)) return s;
        return null;
    }

    /** The sessions by start time (a copy; the stored order is already this for parsed events). */
    List<Session> byStart() {
        List<Session> out = new ArrayList<>(sessions);
        Collections.sort(out, new Comparator<Session>() {
            public int compare(Session a, Session b) { return Long.compare(a.start, b.start); }
        });
        return out;
    }

    /** A session being played (state "in"), the earliest if two are; null when none. */
    Session live() {
        for (Session s : byStart()) if ("in".equals(s.state)) return s;
        return null;
    }

    /** The first session still to come: state "pre" and not started yet (a TBD time counts as to come); off ones skipped. */
    Session next(long now) {
        for (Session s : byStart()) if ("pre".equals(s.state) && (s.tbd || s.start > now)) return s;
        return null;
    }

    /** The newest finished ("post") session of these kinds (null or empty: any kind); null when none. */
    Session lastDone(Set<String> kinds) {
        Session best = null;
        for (Session s : sessions) {
            if (!"post".equals(s.state)) continue;
            if (kinds != null && !kinds.isEmpty() && !kinds.contains(s.kind)) continue;
            if (best == null || s.start >= best.start) best = s;
        }
        return best;
    }

    /** UFC: the main event (bout order 1, else the bout marked main); null for F1 or before a card is known. */
    Bout mainEvent() {
        Bout marked = null;
        for (Session s : sessions)
            for (Bout b : s.bouts) {
                if (b.order == 1) return b;
                if (b.main && marked == null) marked = b;
            }
        return marked;
    }

    /** UFC: this fighter's bout on the card, or null. */
    Bout boutOf(String fighterId) {
        if (fighterId == null || fighterId.isEmpty()) return null;
        for (Session s : sessions)
            for (Bout b : s.bouts)
                if ((b.a != null && fighterId.equals(b.a.id)) || (b.b != null && fighterId.equals(b.b.id))) return b;
        return null;
    }

    /** The session holding this bout, or null. */
    Session sessionOf(Bout bout) {
        for (Session s : sessions) if (s.bouts.contains(bout)) return s;
        return null;
    }

    /** A deep copy: callers may change it freely (EventStore.load hands out copies). */
    SportEvent copy() {
        SportEvent e = new SportEvent();
        e.sport = sport; e.id = id; e.name = name; e.shortName = shortName; e.start = start; e.end = end; e.state = state;
        e.circuitId = circuitId; e.circuitName = circuitName; e.city = city; e.country = country; e.laps = laps;
        e.lengthKm = lengthKm; e.venue = venue; e.numbered = numbered; e.number = number;
        e.fetchedAt = fetchedAt; e.misses = misses;
        for (Session s : sessions) e.sessions.add(s.copy());
        return e;
    }

    /**
     * Everything that changes how a card or a screen looks (like Match.signature): identical events are not redrawn.
     * Not included: fetchedAt, misses, seenLiveAt and the raw status name.
     */
    String signature() {
        StringBuilder b = new StringBuilder();
        b.append(sport).append('|').append(id).append('|').append(name).append('|').append(shortName).append('|')
            .append(start).append('|').append(end).append('|').append(state).append('|').append(circuitId).append('|')
            .append(circuitName).append('|').append(city).append('|').append(country).append('|').append(laps).append('|')
            .append(lengthKm).append('|').append(venue).append('|').append(numbered).append(number);
        for (Session s : sessions) s.sign(b);
        return b.toString();
    }

    /** "f1|espn:600057449#401839137" -> "f1|espn:600057449" (a key without a session is returned as it is). */
    static String eventKey(String anyKey) {
        if (anyKey == null) return null;
        int i = anyKey.indexOf('#');
        return i < 0 ? anyKey : anyKey.substring(0, i);
    }

    /** "f1|espn:600057449#401839137" -> "401839137"; null when the key names no session. */
    static String sessionId(String anyKey) {
        if (anyKey == null) return null;
        int i = anyKey.indexOf('#');
        return i < 0 || i == anyKey.length() - 1 ? null : anyKey.substring(i + 1);
    }

    /** A key made by key() or Session.key(): "f1|..." or "ufc|..." (OpenActivity / HomeActivity route these). */
    static boolean isEventKey(String anyKey) {
        return anyKey != null && (anyKey.startsWith(F1 + "|espn:") || anyKey.startsWith(UFC + "|espn:"));
    }

    /**
     * A fighter's record before a bout that is first seen after it ended (§1.2): W−1 for the winner, L−1 for the
     * loser, D−1 for both after a draw, unchanged after a no contest or an unknown outcome. "14-2-0" style records;
     * anything else (or a count already 0) is returned unchanged.
     */
    static String recordBefore(String record, int outcome, boolean sideA) {
        if (record == null) return null;
        String[] p = record.trim().split("-");
        if (p.length < 3) return record;
        int w, l, d;
        try { w = Integer.parseInt(p[0].trim()); l = Integer.parseInt(p[1].trim()); d = Integer.parseInt(p[2].trim().split("\\s")[0]); }
        catch (NumberFormatException x) { return record; }
        String rest = p[2].trim().contains(" ") ? p[2].trim().substring(p[2].trim().indexOf(' ')) : "";
        for (int i = 3; i < p.length; i++) rest += "-" + p[i];
        boolean won = (outcome == 1 && sideA) || (outcome == 2 && !sideA), lost = (outcome == 2 && sideA) || (outcome == 1 && !sideA);
        if (won) { if (w == 0) return record; w--; }
        else if (lost) { if (l == 0) return record; l--; }
        else if (outcome == 3) { if (d == 0) return record; d--; }
        else return record;
        return w + "-" + l + "-" + d + rest;
    }

    // ---------- nested types ----------
    static final class Session {
        String id;          // F1: ESPN competition id; UFC: "prelims2" | "prelims1" | "main"
        String kind = "";   // F1: FP1 FP2 FP3 SQ SPRINT QUAL RACE OTHER; UFC: EARLY PRELIMS MAIN
        String typeAbbr = "";  // ESPN's raw type.abbreviation (OTHER keeps its label)
        long start; boolean tbd;
        String state = "pre", detail = "";   // detail: "" | CANCELLED | POSTPONED | DELAYED | SUSPENDED | RED_FLAG | AWAITING
        String statusName = "";              // ESPN status.type.name, kept raw for diagnosis
        int lap, laps;                        // F1 race/sprint: status.period and total (0: unknown)
        long seenLiveAt;
        final List<Entrant> order = new ArrayList<>();   // F1, by position
        final List<Bout> bouts = new ArrayList<>();      // UFC, by order (1 = main event)
        SessionWatch watch = new SessionWatch();

        String key(SportEvent e) { return e.key() + "#" + id; }

        Session copy() {
            Session s = new Session();
            s.id = id; s.kind = kind; s.typeAbbr = typeAbbr; s.start = start; s.tbd = tbd; s.state = state; s.detail = detail;
            s.statusName = statusName; s.lap = lap; s.laps = laps; s.seenLiveAt = seenLiveAt;
            for (Entrant x : order) s.order.add(x.copy());
            for (Bout x : bouts) s.bouts.add(x.copy());
            s.watch = watch == null ? new SessionWatch() : watch.copy();
            return s;
        }

        void sign(StringBuilder b) {
            b.append("#S").append(id).append('|').append(kind).append('|').append(typeAbbr).append('|').append(start).append('|')
                .append(tbd).append('|').append(state).append('|').append(detail).append('|').append(lap).append('/').append(laps);
            for (Entrant x : order) x.sign(b);
            for (Bout x : bouts) x.sign(b);
            if (watch != null) watch.sign(b);
        }
    }

    static final class Entrant {
        String id = "", code = "", name = "", shortName = "", team = "", teamId = "", flag = "", gap = "";
        int color, pos; boolean winner;
        /** Championship points: standings only (F1Feed.Standings); 0 in a session's order. */
        float points;
        /** The race number ("1", "44"; the core athlete's vehicles[0].number, via F1Feed's roster); "" when unknown. */
        String number = "";

        Entrant copy() {
            Entrant e = new Entrant();
            e.id = id; e.code = code; e.name = name; e.shortName = shortName; e.team = team; e.teamId = teamId; e.flag = flag;
            e.gap = gap; e.color = color; e.pos = pos; e.winner = winner; e.points = points; e.number = number;
            return e;
        }

        void sign(StringBuilder b) {
            b.append("#E").append(id).append('|').append(code).append('|').append(name).append('|').append(shortName).append('|')
                .append(team).append('|').append(teamId).append('|').append(flag).append('|').append(gap).append('|')
                .append(color).append('|').append(pos).append('|').append(winner).append('|').append(number);
        }
    }

    static final class Bout {
        String id = ""; int order; String weightClass = "", note = ""; boolean title, main; int rounds;
        Fighter a, b;                         // a = ESPN order 1, b = order 2: fixed positions, never swapped by result
        String state = "pre", detail = ""; int round; String clock = "";
        int outcome;                          // 0 unknown/none, 1 a won, 2 b won, 3 draw, 4 no contest
        String method = "", methodDetail = "";// "KO/TKO", "Punch"

        Bout copy() {
            Bout x = new Bout();
            x.id = id; x.order = order; x.weightClass = weightClass; x.note = note; x.title = title; x.main = main;
            x.rounds = rounds; x.a = a == null ? null : a.copy(); x.b = b == null ? null : b.copy(); x.state = state;
            x.detail = detail; x.round = round; x.clock = clock; x.outcome = outcome; x.method = method;
            x.methodDetail = methodDetail;
            return x;
        }

        /** The live and result fields from another reading of the same bout (a tick, or a kept older answer). */
        void takeLive(Bout o) {
            state = o.state; detail = o.detail; round = o.round; clock = o.clock; outcome = o.outcome; method = o.method;
            methodDetail = o.methodDetail;
        }

        void sign(StringBuilder s) {
            s.append("#B").append(id).append('|').append(order).append('|').append(weightClass).append('|').append(note).append('|')
                .append(title).append(main).append(rounds).append('|').append(state).append('|').append(detail).append('|')
                .append(round).append('|').append(clock).append('|').append(outcome).append('|').append(method).append('|')
                .append(methodDetail);
            if (a != null) a.sign(s); else s.append("#F-");
            if (b != null) b.sign(s); else s.append("#F-");
        }
    }

    static final class Fighter {
        String id = "", name = "", shortName = "", flag = "", country = "", headshot = "", stanceLeft = "", stanceRight = "",
            record = "", recordBefore = "";

        Fighter copy() {
            Fighter f = new Fighter();
            f.id = id; f.name = name; f.shortName = shortName; f.flag = flag; f.country = country; f.headshot = headshot;
            f.stanceLeft = stanceLeft; f.stanceRight = stanceRight; f.record = record; f.recordBefore = recordBefore;
            return f;
        }

        void sign(StringBuilder b) {
            b.append("#F").append(id).append('|').append(name).append('|').append(shortName).append('|').append(flag).append('|')
                .append(country).append('|').append(headshot).append('|').append(stanceLeft).append('|').append(stanceRight)
                .append('|').append(record).append('|').append(recordBefore);
        }
    }

    /**
     * Where a session is on, written only by SportWhere (a feed refresh never touches it; EventStore.merge carries it
     * over and SportWhere.apply re-applies it after every merge).
     */
    static final class SessionWatch {
        String channel = "", app, logo, listedBy = ""; int no; boolean sure, known;

        SessionWatch copy() {
            SessionWatch w = new SessionWatch();
            w.channel = channel; w.app = app; w.logo = logo; w.listedBy = listedBy; w.no = no; w.sure = sure; w.known = known;
            return w;
        }

        /**
         * A throwaway Match carrying only the channel fields, the start time and league = sport, so Channels.logos,
         * Cards.whereParts and CardModel.channel work unchanged. Never stored, never drawn as a match.
         */
        Match asMatch(long start, String sport) {
            Match m = new Match();
            m.club = ""; m.team = ""; m.source = sport; m.key = ""; m.comp = ""; m.league = sport; m.home = ""; m.away = "";
            m.state = "pre"; m.detail = ""; m.start = start;
            m.channel = channel == null ? "" : channel; m.channelApp = app; m.channelLogo = logo; m.channelNo = no;
            m.channelSure = sure; m.channelKnown = known; m.listedBy = listedBy == null ? "" : listedBy;
            return m;
        }

        void sign(StringBuilder b) {
            b.append("#W").append(channel).append('|').append(app).append('|').append(logo).append('|').append(listedBy).append('|')
                .append(no).append(sure).append(known);
        }
    }
}
