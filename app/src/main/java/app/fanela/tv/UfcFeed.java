package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * UFC from ESPN (F1-UFC-ARCHITECTURE §3.2). Stream B owns this file.
 *
 * Calls:
 *  - mma/ufc/scoreboard (no date): the season calendar (Contender Series removed; dates approximate, for listing
 *    only), daily (lastCal_ufc), cached in ufc_calendar.json; its events[] (the current card) also give the rounds
 *    (format.regulation.periods) and the live fields.
 *  - fightcenter/{id}: the card by segment (prelims2 EARLY, prelims1 PRELIMS, main MAIN, each starting at its bouts'
 *    date), bouts, fighters, results with method. Empty or 404 far ahead: the event is then not returned (no card yet).
 *  - mma/ufc/scoreboard?dates=yyyyMMdd (live): state, round, clock and winner per bout, by competition id.
 * Identity is the ESPN event id, never the name (a new main event renames the event).
 *
 * Bout fields as filled here (the cards split them, SPEC-F1-UFC §12.2):
 *  - method: the method word, "KO/TKO", "SUB", "DEC", "DQ", "DRAW", "NC", else ESPN's short name as given;
 *  - methodDetail: for DEC and DRAW the decision kind "UNANIMOUS" / "SPLIT" / "MAJORITY" (or ""); for the others
 *    ESPN's description as given ("Punch", "Rear Naked Choke");
 *  - round (R3) and clock ("2:27") when the bout ended, the round being fought and its clock while live;
 *  - rounds (3 / 5): the scoreboard's format when seen, else 5 for a main event or a title fight, 3 otherwise;
 *  - outcome 1 a won, 2 b won, 3 draw, 4 no contest; title; weightClass; the segment is the session holding it.
 */
final class UfcFeed {
    private UfcFeed() { }

    /**
     * The live round and its clock (status.period, displayClock of a bout in the cage) are drawn only once a recorded
     * event has shown them right (1.3.1, after UFC 334). While false a live bout shows LIVE with the bout in the cage,
     * never a round or a clock. A finished bout's round and time (fightcenter) are always shown. Read it through
     * shownRound and shownClock.
     */
    static final boolean LIVE_ROUND_TRUSTED = false;
    /** LIVE_ROUND_TRUSTED; tests set it. */
    static boolean roundTrusted = LIVE_ROUND_TRUSTED;

    /** The round a surface may show for a bout: a finished bout's always, a live one's only while trusted; else 0. */
    static int shownRound(SportEvent.Bout b) {
        if (b == null) return 0;
        return "in".equals(b.state) && !roundTrusted ? 0 : b.round;
    }

    /** The bout's clock as shownRound decides: "" when the round is not shown. */
    static String shownClock(SportEvent.Bout b) {
        return shownRound(b) <= 0 || b.clock == null ? "" : b.clock;
    }

    static final String BOARD = "https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard";
    static final String FIGHTCENTER = "https://site.web.api.espn.com/apis/common/v3/sports/mma/ufc/fightcenter/";
    static final String CAL_FILE = "ufc_calendar.json";
    static final long MIN = 60000L, H = 3600000L, DAY = 24 * H, CAL_EVERY = DAY, RESULTS_EVERY = 5 * MIN;
    private static final Pattern NUMBERED = Pattern.compile("^UFC (\\d+)\\b");

    /** Events whose live scoreboard named a bout the stored card lacks: the next refresh fetches their card. */
    private static final Set<String> refetch = Collections.synchronizedSet(new HashSet<String>());
    /** Event id -> when the live tick last fetched its fightcenter for results (at most every 5 minutes). */
    private static final Map<String, Long> resultsAt = new HashMap<>();

    static String fightcenter(String id) { return FIGHTCENTER + id + "?region=us&lang=en"; }

    // ---------- the refresh ----------
    /**
     * The calendar when due (daily), and the cards that are due (known: the stored UFC events), each by its own
     * cadence: the next 4, the newest finished one while recent, stored ones the calendar no longer lists, and any
     * whose live scoreboard showed an unknown bout. ok when an answer came, or when nothing was due.
     */
    static FeedResult fetch(Context c, List<SportEvent> known, long now) {
        if (c == null) return FeedResult.failed(SportEvent.UFC);
        if (known == null) known = new ArrayList<>();
        FeedResult r = new FeedResult(SportEvent.UFC);
        SharedPreferences sp = c.getSharedPreferences(Sports.ENGINE, Context.MODE_PRIVATE);
        boolean asked = false, answered = false;
        JSONObject board = null;
        List<CalendarEntry> cal = CalendarEntry.load(c, CAL_FILE);
        if (cal == null || cal.isEmpty() || now - sp.getLong("lastCal_" + SportEvent.UFC, 0) >= CAL_EVERY - MIN) {
            asked = true;
            try {
                board = new JSONObject(FeedResult.net.get(BOARD));
                answered = true;
                List<CalendarEntry> fresh = parseCalendar(board);
                if (!fresh.isEmpty()) {
                    cal = fresh;
                    CalendarEntry.save(c, CAL_FILE, fresh, now);
                    sp.edit().putLong("lastCal_" + SportEvent.UFC, now).apply();
                }
            } catch (Exception ignored) { }
        }
        if (cal == null) cal = new ArrayList<>();
        List<String> ids = CalendarEntry.wanted(cal, known, now, EventStore.NEXT_UFC, SportEvent.UFC);
        synchronized (refetch) { for (String id : refetch) if (!ids.contains(id)) ids.add(id); }
        Map<String, SportEvent> got = new LinkedHashMap<>();
        for (String id : ids) {
            SportEvent k = CalendarEntry.find(known, SportEvent.UFC, id);
            if (!refetch.contains(id) && !CalendarEntry.due(k, SportEvent.UFC, now)) continue;
            asked = true;
            try {
                JSONObject fc = new JSONObject(FeedResult.net.get(fightcenter(id)));
                answered = true;
                SportEvent e = parseFightcenter(fc);
                if (e == null || !id.equals(e.id)) { r.missed.add(id); continue; }  // no card yet (or another event)
                if (board != null) applyScoreboard(e, board, now);
                e.fetchedAt = now;
                got.put(e.id, e);
                refetch.remove(id);
            } catch (IOException x) {
                if (CalendarEntry.notFound(x)) { answered = true; r.missed.add(id); }
            } catch (Exception x) {
                answered = true;
                r.missed.add(id);
            }
        }
        r.events.addAll(got.values());
        if (!cal.isEmpty()) r.listed = CalendarEntry.ids(cal);
        r.ok = answered || !asked;
        return r;
    }

    // ---------- parsing (pure) ----------
    /** Calendar ids and labels from the scoreboard (Contender Series removed). */
    static List<CalendarEntry> parseCalendar(JSONObject board) {
        return CalendarEntry.parse(board, true);
    }

    /**
     * A fightcenter card: event id, names, venue, numbered ("UFC 334"), the segments in start order with their bouts by
     * order (1 = main event). null when it holds no event id or no bout (no card yet), or for the Contender Series.
     */
    static SportEvent parseFightcenter(JSONObject d) {
        if (d == null) return null;
        JSONObject ev = d.optJSONObject("event");
        if (ev == null || ev.optString("id", "").isEmpty()) return null;
        SportEvent e = new SportEvent();
        e.sport = SportEvent.UFC;
        e.id = ev.optString("id");
        e.name = ev.optString("name", "").trim();
        e.shortName = ev.optString("shortName", "").trim();
        if (CalendarEntry.contender(e.name)) return null;
        Matcher m = NUMBERED.matcher(e.name);
        e.numbered = m.find();
        e.number = e.numbered ? Integer.parseInt(m.group(1)) : 0;
        JSONObject v = d.optJSONObject("venue");
        if (v != null) {
            e.venue = v.optString("fullName", "").trim();
            JSONObject a = v.optJSONObject("address");
            if (a != null) { e.city = a.optString("city", "").trim(); e.country = a.optString("country", "").trim(); }
        }
        JSONObject cards = d.optJSONObject("cards");
        if (cards != null)
            for (java.util.Iterator<String> it = cards.keys(); it.hasNext(); ) {
                String key = it.next();
                try {
                    SportEvent.Session s = segment(key, cards.getJSONObject(key));
                    if (s != null && e.session(s.id) == null) e.sessions.add(s);
                } catch (Exception ignored) { }
            }
        if (e.sessions.isEmpty()) return null;
        EventStore.sortSessions(e);
        EventStore.bounds(e);
        SportEngine.derive(e);
        return e;
    }

    /** "main" -> MAIN, "prelims1" -> PRELIMS, "prelims2" -> EARLY (else by the display name). */
    static String segmentKind(String key, String displayName) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT), n = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (k.startsWith("main") || n.startsWith("main")) return "MAIN";
        if ("prelims2".equals(k) || n.contains("early")) return "EARLY";
        return "PRELIMS";
    }

    static SportEvent.Session segment(String key, JSONObject card) {
        JSONArray comps = card.optJSONArray("competitions");
        if (comps == null || comps.length() == 0) return null;
        SportEvent.Session s = new SportEvent.Session();
        s.id = card.optString("cardSegment", card.optString("name", key));
        if (s.id.isEmpty()) s.id = key;
        s.typeAbbr = card.optString("displayName", "").trim();
        s.kind = segmentKind(s.id, s.typeAbbr);
        long start = Long.MAX_VALUE;
        for (int i = 0; i < comps.length(); i++) {
            try {
                JSONObject c = comps.getJSONObject(i);
                SportEvent.Bout b = bout(c);
                if (b == null) continue;
                s.bouts.add(b);
                long t = Sources.iso(c.optString("date", ""));
                if (t > 0) start = Math.min(start, t);  // every bout of a segment carries the segment's start
            } catch (Exception ignored) { }
        }
        if (s.bouts.isEmpty() || start == Long.MAX_VALUE) return null;
        s.start = start;
        sortBouts(s);
        segmentState(s);
        return s;
    }

    static void sortBouts(SportEvent.Session s) {
        Collections.sort(s.bouts, new Comparator<SportEvent.Bout>() {
            public int compare(SportEvent.Bout a, SportEvent.Bout b) {
                int c = Integer.compare(a.order <= 0 ? Integer.MAX_VALUE : a.order, b.order <= 0 ? Integer.MAX_VALUE : b.order);
                return c != 0 ? c : a.id.compareTo(b.id);
            }
        });
    }

    /** A fightcenter bout: order, weight class, note, title, main, rounds, fighters (a = ESPN order 1), status and result. */
    static SportEvent.Bout bout(JSONObject c) {
        String id = c.optString("id", "");
        if (id.isEmpty()) return null;
        SportEvent.Bout b = new SportEvent.Bout();
        b.id = id;
        b.order = c.optInt("matchNumber");
        JSONObject type = c.optJSONObject("type");
        b.weightClass = type == null ? "" : type.optString("text", type.optString("abbreviation", "")).trim();
        b.note = c.optString("note", "").trim();
        String note = b.note.toLowerCase(Locale.ROOT);
        JSONArray types = c.optJSONArray("types");
        boolean titled = false;
        for (int i = 0; types != null && i < types.length(); i++) {
            JSONObject t = types.optJSONObject(i);
            if (t != null && t.optString("text", "").toLowerCase(Locale.ROOT).contains("title")) titled = true;
        }
        b.title = titled || note.contains("title fight");
        b.main = b.order == 1 || note.contains("main event") && !note.contains("co-main");
        b.rounds = rounds(c, b);
        JSONArray cs = c.optJSONArray("competitors");
        for (int i = 0; cs != null && i < cs.length(); i++) {
            JSONObject x = cs.optJSONObject(i);
            if (x == null) continue;
            SportEvent.Fighter f = fighter(x);
            if (f == null) continue;
            int o = x.optInt("order");
            if (o == 1 && b.a == null) b.a = f;
            else if (o == 2 && b.b == null) b.b = f;
            else if (b.a == null && o != 2) b.a = f;
            else if (b.b == null) b.b = f;
        }
        if (b.a == null || b.b == null) return null;  // a bout needs both corners
        result(b, c.optJSONObject("status"), cs);
        return b;
    }

    /** format.regulation.periods when given; else 5 for a main event or a title fight, 3 otherwise (UFC rules). */
    static int rounds(JSONObject c, SportEvent.Bout b) {
        JSONObject f = c.optJSONObject("format"), reg = f == null ? null : f.optJSONObject("regulation");
        int p = reg == null ? 0 : reg.optInt("periods");
        if (p > 0) return p;
        return b.main || b.title ? 5 : 3;
    }

    static SportEvent.Fighter fighter(JSONObject comp) {
        JSONObject a = comp.optJSONObject("athlete");
        SportEvent.Fighter f = new SportEvent.Fighter();
        f.id = a != null ? a.optString("id", comp.optString("id", "")) : comp.optString("id", "");
        if (f.id.isEmpty()) return null;
        f.record = comp.optString("displayRecord", "").trim();
        if (f.record.isEmpty()) {
            JSONArray rec = comp.optJSONArray("records");
            JSONObject r0 = rec == null ? null : rec.optJSONObject(0);
            if (r0 != null) f.record = r0.optString("summary", "").trim();
        }
        if (a == null) return f;
        f.name = a.optString("displayName", a.optString("fullName", "")).trim();
        f.shortName = a.optString("shortName", "").trim();
        f.country = a.optString("country", "").trim();
        JSONObject flag = a.optJSONObject("flag");
        if (flag != null) { f.flag = flag.optString("href", ""); if (f.country.isEmpty()) f.country = flag.optString("alt", "").trim(); }
        JSONObject hs = a.optJSONObject("headshot");
        if (hs != null) f.headshot = hs.optString("href", "");
        JSONArray im = a.optJSONArray("images");
        for (int i = 0; im != null && i < im.length(); i++) {
            JSONObject o = im.optJSONObject(i);
            if (o == null) continue;
            String rel = String.valueOf(o.optJSONArray("rel"));
            if (rel.contains("leftStance")) f.stanceLeft = o.optString("href", "");
            else if (rel.contains("rightStance")) f.stanceRight = o.optString("href", "");
        }
        return f;
    }

    /**
     * State, round, clock and, once over, the outcome and method of a bout from its status and competitors (the
     * fightcenter's or the scoreboard's; the scoreboard has no method). The winner side is found by fighter id.
     */
    static void result(SportEvent.Bout b, JSONObject st, JSONArray competitors) {
        String[] m = F1Feed.status(st, false);
        b.state = m[0];
        b.detail = m[1];
        if (st == null) return;
        if ("pre".equals(b.state) || "off".equals(b.state)) { b.round = 0; b.clock = ""; return; }
        b.round = Math.max(0, st.optInt("period"));
        String clock = st.optString("displayClock", "").trim();
        b.clock = "-".equals(clock) || "0:00".equals(clock) && b.round == 0 ? "" : clock;
        if (!"post".equals(b.state)) return;
        int won = 0;
        for (int i = 0; competitors != null && i < competitors.length(); i++) {
            JSONObject x = competitors.optJSONObject(i);
            if (x == null || !x.optBoolean("winner")) continue;
            JSONObject a = x.optJSONObject("athlete");
            String id = a != null ? a.optString("id", x.optString("id", "")) : x.optString("id", "");
            if (b.a != null && id.equals(b.a.id)) won = 1;
            else if (b.b != null && id.equals(b.b.id)) won = 2;
            else won = x.optInt("order") == 1 ? 1 : x.optInt("order") == 2 ? 2 : 0;
        }
        JSONObject res = st.optJSONObject("result");
        String name = res == null ? "" : res.optString("name", "").toLowerCase(Locale.ROOT);
        String display = res == null ? "" : res.optString("displayName", "").toLowerCase(Locale.ROOT);
        if (won > 0) b.outcome = won;
        else if (name.contains("draw") || display.contains("draw")) b.outcome = 3;
        else if (noContest(name) || noContest(display)) b.outcome = 4;
        if (res != null && (!name.isEmpty() || !display.isEmpty())) {
            String[] w = method(res, b.outcome);
            b.method = w[0];
            b.methodDetail = w[1];
        }
    }

    private static boolean noContest(String s) {
        return s.contains("no-contest") || s.contains("no contest") || s.contains("nocontest") || s.equals("nc") || s.startsWith("nc ");
    }

    /**
     * {method word, detail} of an ESPN result: kotko -> KO/TKO, submission -> SUB, decision-* -> DEC with UNANIMOUS /
     * SPLIT / MAJORITY, dq -> DQ; a draw -> DRAW (with its decision kind), a no contest -> NC; anything else ESPN's
     * short display name. The detail of a KO or a submission is ESPN's description ("Punch").
     */
    static String[] method(JSONObject res, int outcome) {
        String name = res.optString("name", "").toLowerCase(Locale.ROOT);
        String display = res.optString("displayName", "");
        String all = (name + " " + display.toLowerCase(Locale.ROOT)).trim();
        String desc = res.optString("description", res.optString("displayDescription", "")).trim();
        String kind = all.contains("unanimous") ? "UNANIMOUS" : all.contains("split") ? "SPLIT" : all.contains("majority") ? "MAJORITY" : "";
        if (outcome == 3) return new String[]{"DRAW", kind};
        if (outcome == 4) return new String[]{"NC", ""};
        if (name.contains("kotko") || name.startsWith("ko") || name.startsWith("tko") || all.contains("ko/tko")) return new String[]{"KO/TKO", desc};
        if (name.contains("submission") || all.contains("submission")) return new String[]{"SUB", desc};
        if (name.startsWith("decision") || all.contains("decision")) return new String[]{"DEC", kind};
        if (name.equals("dq") || name.contains("disqualif") || all.contains("disqualif")) return new String[]{"DQ", desc};
        String s = res.optString("shortDisplayName", display).trim();
        return new String[]{s, desc};
    }

    /**
     * A segment's state from its bouts: off when every bout is off (CANCELLED), in when one is in or the segment is
     * between bouts (some over, some to come), post when every bout still on is over, pre otherwise.
     */
    static void segmentState(SportEvent.Session s) {
        if (s.bouts.isEmpty()) return;
        int pre = 0, in = 0, post = 0, off = 0;
        for (SportEvent.Bout b : s.bouts) {
            if ("in".equals(b.state)) in++;
            else if ("post".equals(b.state)) post++;
            else if ("off".equals(b.state)) off++;
            else pre++;
        }
        String was = s.state;
        if (off == s.bouts.size()) { s.state = "off"; s.detail = "CANCELLED"; }
        else if (in > 0 || (post > 0 && pre > 0)) { s.state = "in"; s.detail = ""; }
        else if (post > 0) { s.state = "post"; s.detail = ""; }
        else { s.state = "pre"; if (!"pre".equals(was)) s.detail = ""; }
    }

    // ---------- live ----------
    /**
     * Copies the scoreboard's live bout fields onto e: state, round, clock, the winner side and the rounds format, for
     * every bout found by competition id anywhere on the card (the scoreboard has no segment). A scoreboard bout the
     * card lacks is never invented: the event's card is fetched again on the next refresh. Segment and event states
     * follow; sessions being played are stamped seenLiveAt = now.
     */
    static void applyScoreboard(SportEvent e, JSONObject board, long now) {
        if (e == null || board == null) return;
        JSONArray ev = board.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) {
            JSONObject o = ev.optJSONObject(i);
            if (o == null || !String.valueOf(e.id).equals(o.optString("id"))) continue;
            Map<String, SportEvent.Bout> bouts = new HashMap<>();
            for (SportEvent.Session s : e.sessions) for (SportEvent.Bout b : s.bouts) bouts.put(b.id, b);
            JSONArray comps = o.optJSONArray("competitions");
            for (int j = 0; comps != null && j < comps.length(); j++) {
                JSONObject c = comps.optJSONObject(j);
                if (c == null) continue;
                SportEvent.Bout b = bouts.get(c.optString("id", ""));
                if (b == null) { if (!c.optString("id", "").isEmpty()) refetch.add(e.id); continue; }
                JSONObject f = c.optJSONObject("format"), reg = f == null ? null : f.optJSONObject("regulation");
                if (reg != null && reg.optInt("periods") > 0) b.rounds = reg.optInt("periods");
                String method = b.method, detail = b.methodDetail;
                int outcome = b.outcome;
                result(b, c.optJSONObject("status"), c.optJSONArray("competitors"));
                if ("post".equals(b.state)) {  // the scoreboard has no method: keep what the card said
                    if (b.method.isEmpty()) { b.method = method; b.methodDetail = detail; }
                    if (b.outcome == 0) b.outcome = outcome;
                } else {
                    b.outcome = 0; b.method = ""; b.methodDetail = "";
                }
            }
            for (SportEvent.Session s : e.sessions) {
                segmentState(s);
                if ("in".equals(s.state)) s.seenLiveAt = Math.max(s.seenLiveAt, now);
            }
            SportEngine.derive(e);
            return;
        }
    }

    /**
     * The live fields of this event's bouts (one scoreboard request per tick, shared through boards): the US Eastern
     * day of the first segment, then its UTC day and the day before (Sources.nbaDates). A bout that has just ended has
     * no method on the scoreboard: the event's fightcenter is then read too, at most every 5 minutes. null: no answer
     * listed the event.
     */
    static SportEvent live(SportEvent e, Map<String, String> boards) {
        if (e == null || e.id == null) return null;
        long now = System.currentTimeMillis();
        for (String day : Sources.nbaDates(e.start)) {
            try {
                JSONObject b = new JSONObject(Sources.board(BOARD + "?dates=" + day, boards));
                if (!lists(b, e.id)) continue;
                SportEvent x = e.copy();
                applyScoreboard(x, b, now);
                if (needsResults(x) && resultsDue(e.id, now)) {
                    try {
                        SportEvent f = parseFightcenter(new JSONObject(Sources.board(fightcenter(e.id), boards)));
                        if (f != null && e.id.equals(f.id)) takeResults(x, f);
                    } catch (Exception ignored) { }
                }
                return x;
            } catch (Exception ignored) { }
        }
        return null;
    }

    static boolean lists(JSONObject board, String id) {
        JSONArray ev = board.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) {
            JSONObject o = ev.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return true;
        }
        return false;
    }

    /** A bout is over and has no method yet (the scoreboard never gives one). */
    static boolean needsResults(SportEvent e) {
        for (SportEvent.Session s : e.sessions)
            for (SportEvent.Bout b : s.bouts) if ("post".equals(b.state) && b.method.isEmpty()) return true;
        return false;
    }

    private static boolean resultsDue(String id, long now) {
        synchronized (resultsAt) {
            Long last = resultsAt.get(id);
            if (last != null && now - last < RESULTS_EVERY) return false;
            resultsAt.put(id, now);
            return true;
        }
    }

    /** Forgets the live tick's memory (tests). */
    static void resetLive() {
        synchronized (resultsAt) { resultsAt.clear(); }
        refetch.clear();
    }

    /** True when the next refresh will fetch this event's card again (its live scoreboard named an unknown bout). */
    static boolean refetching(String id) { return refetch.contains(id); }

    /** The results of a fresh card onto the live reading: finished bouts, by id; a bout the reading lacks asks for a refetch. */
    static void takeResults(SportEvent x, SportEvent card) {
        Map<String, SportEvent.Bout> mine = new HashMap<>();
        for (SportEvent.Session s : x.sessions) for (SportEvent.Bout b : s.bouts) mine.put(b.id, b);
        for (SportEvent.Session s : card.sessions)
            for (SportEvent.Bout f : s.bouts) {
                SportEvent.Bout b = mine.get(f.id);
                if (b == null) { refetch.add(x.id); continue; }
                if (!"post".equals(f.state)) continue;
                b.state = "post"; b.detail = f.detail; b.round = f.round; b.clock = f.clock;
                if (f.outcome != 0 || b.outcome == 0) b.outcome = f.outcome;
                b.method = f.method; b.methodDetail = f.methodDetail;
            }
        for (SportEvent.Session s : x.sessions) segmentState(s);
        SportEngine.derive(x);
    }

    /**
     * The fighters on the next 4 cards (Settings › UFC), each once: cards by start (not over, not cancelled), bouts by
     * order (the main event first). Copies.
     */
    static List<SportEvent.Fighter> upcomingFighters(List<SportEvent> all, long now) {
        List<SportEvent> cards = new ArrayList<>();
        if (all != null)
            for (SportEvent e : all)
                if (SportEvent.UFC.equals(e.sport) && !"post".equals(e.state) && !"off".equals(e.state)
                    && EventStore.lastStart(e) + 7 * H > now && !e.sessions.isEmpty()) cards.add(e);
        Collections.sort(cards, new Comparator<SportEvent>() {
            public int compare(SportEvent a, SportEvent b) { return Long.compare(a.start, b.start); }
        });
        List<SportEvent.Fighter> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cards.size() && i < EventStore.NEXT_UFC; i++) {
            List<SportEvent.Bout> bouts = new ArrayList<>();
            for (SportEvent.Session s : cards.get(i).sessions) for (SportEvent.Bout b : s.bouts) if (!"off".equals(b.state)) bouts.add(b);
            SportEvent.Session all1 = new SportEvent.Session();
            all1.bouts.addAll(bouts);
            sortBouts(all1);
            for (SportEvent.Bout b : all1.bouts)
                for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b})
                    if (f != null && !f.id.isEmpty() && seen.add(f.id)) out.add(f.copy());
        }
        return out;
    }
}
