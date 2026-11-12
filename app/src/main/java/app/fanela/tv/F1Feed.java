package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Formula 1 from ESPN (F1-UFC-ARCHITECTURE §3.1). Stream B owns this file.
 *
 * Calls (all through FeedResult.net / Sources.board):
 *  - racing/f1/scoreboard (no date): the season calendar (leagues[0].calendar, ids and labels; its times are about
 *    3 h off and are only used to choose a date to query) and the current or most recent weekend, parsed in full.
 *    Weekly (lastCal_f1 in the sports_engine preferences); cached in f1_calendar.json.
 *  - racing/f1/scoreboard?dates=yyyyMMdd: one weekend, filed under its US Eastern days; the answer counts only when
 *    it lists the id asked for.
 *  - sports.core …/circuits/{id}: laps and length (f1_circuits.json, yearly).
 *  - apis/v2/…/racing/f1/standings: drivers (code, rank, points) and constructors (colour) (f1_standings.json).
 *  - sports.core …/athletes/{id}: each standings driver's team, matched to a constructor by name (f1_roster.json,
 *    weekly).
 * Sessions are ESPN competitions, kinds from type.abbreviation (SS -> SQ, SR -> SPRINT), times from each
 * competition's own date, stored in start order (never by position in the list). The racing summary endpoint is a 404:
 * never called.
 */
final class F1Feed {
    private F1Feed() { }

    /** Live positions are drawn only once a recorded live feed shows period and order moving (§3.1, §9.1). */
    static final boolean LIVE_ORDER_TRUSTED = false;
    /**
     * The live lap (status.period) is drawn only once a recorded race has shown it right (1.3.1, after Las Vegas).
     * While false no surface shows a lap: the cards say LIVE and the session, the rail "LIVE · Race", the app screens
     * the time the session started. Read it through shownLap.
     */
    static final boolean LIVE_LAP_TRUSTED = false;
    /** LIVE_LAP_TRUSTED; tests set it. */
    static boolean lapTrusted = LIVE_LAP_TRUSTED;

    /** The lap a surface may show for a session: its lap while LIVE_LAP_TRUSTED, else 0 (no lap known). */
    static int shownLap(SportEvent.Session s) { return s == null || !lapTrusted ? 0 : s.lap; }

    static final String BOARD = "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard";
    static final String STANDINGS = "https://site.api.espn.com/apis/v2/sports/racing/f1/standings";
    static final String CORE = "https://sports.core.api.espn.com/v2/sports/racing/";
    static final String CAL_FILE = "f1_calendar.json", CIRCUITS_FILE = "f1_circuits.json", STANDINGS_FILE = "f1_standings.json",
        ROSTER_FILE = "f1_roster.json";
    static final long MIN = 60000L, H = 3600000L, DAY = 24 * H;
    static final long CAL_EVERY = 7 * DAY, STANDINGS_EVERY = 7 * DAY, ROSTER_EVERY = 7 * DAY, CIRCUIT_EVERY = 365 * DAY;

    /** A constructor in the standings / roster. */
    static final class Team {
        String id = "", name = ""; int color, rank; float points;

        Team copy() {
            Team t = new Team();
            t.id = id; t.name = name; t.color = color; t.rank = rank; t.points = points;
            return t;
        }
    }

    /** Drivers (Entrant: id, code, name, pos = rank, points) and constructors; at: when fetched. */
    static final class Standings {
        final List<SportEvent.Entrant> drivers = new ArrayList<>();
        final List<Team> teams = new ArrayList<>();
        long at;

        SportEvent.Entrant driver(String id) {
            for (SportEvent.Entrant d : drivers) if (d.id.equals(id)) return d;
            return null;
        }

        Team team(String id) {
            for (Team t : teams) if (t.id.equals(id)) return t;
            return null;
        }
    }

    /** Driver id -> entrant with team, teamId and colour; team id -> constructor. at: when fetched. */
    static final class Roster {
        final Map<String, SportEvent.Entrant> byDriver = new HashMap<>();
        final Map<String, Team> teams = new HashMap<>();
        long at;
    }

    /** A circuit's facts (core API): laps of a race, length in km. */
    static final class Circuit { String id = "", name = ""; int laps; float lengthKm; long at; }

    /** The last roster and standings this process read: the live tick (no Context) joins codes and colours from them. */
    private static volatile Roster memoRoster;
    private static volatile Standings memoStandings;

    // ---------- the refresh ----------
    /**
     * The calendar when due (weekly), and the weekends that are due (known: the stored F1 events), each by its own
     * cadence: the next 3, the newest finished one while recent, and stored ones the calendar no longer lists. Circuit
     * laps, codes and team colours are joined in. resultsDue: the standings are downloaded too (results is set when
     * they came). ok when an answer came, or when nothing was due.
     */
    static FeedResult fetch(Context c, List<SportEvent> known, long now, boolean resultsDue) {
        if (c == null) return FeedResult.failed(SportEvent.F1);
        if (known == null) known = new ArrayList<>();
        FeedResult r = new FeedResult(SportEvent.F1);
        SharedPreferences sp = c.getSharedPreferences(Sports.ENGINE, Context.MODE_PRIVATE);
        boolean asked = false, answered = false;
        Map<String, SportEvent> got = new LinkedHashMap<>();
        List<CalendarEntry> cal = CalendarEntry.load(c, CAL_FILE);
        if (cal == null || cal.isEmpty() || now - sp.getLong("lastCal_" + SportEvent.F1, 0) >= CAL_EVERY - MIN) {
            asked = true;
            try {
                JSONObject d = new JSONObject(FeedResult.net.get(BOARD));
                answered = true;
                List<CalendarEntry> fresh = parseCalendar(d);
                if (!fresh.isEmpty()) {
                    cal = fresh;
                    CalendarEntry.save(c, CAL_FILE, fresh, now);
                    sp.edit().putLong("lastCal_" + SportEvent.F1, now).apply();
                }
                for (SportEvent e : parseScoreboard(d)) got.put(e.id, e);  // the current or latest weekend, in full
            } catch (Exception ignored) { }
        }
        if (cal == null) cal = new ArrayList<>();
        for (String id : CalendarEntry.wanted(cal, known, now, EventStore.NEXT_F1, SportEvent.F1)) {
            if (got.containsKey(id)) continue;
            SportEvent k = CalendarEntry.find(known, SportEvent.F1, id);
            if (!CalendarEntry.due(k, SportEvent.F1, now)) continue;
            asked = true;
            int[] answer = new int[1];
            SportEvent e = weekend(id, CalendarEntry.byId(cal, id), k, answer);
            if (answer[0] > 0) answered = true;
            if (e != null) got.put(e.id, e);
            else if (answer[0] > 0) r.missed.add(id);
        }
        Standings st = standings(c, resultsDue, now);
        Roster ro = roster(c, false, now);
        r.results = resultsDue && st != null && st.at == now;  // downloaded in this pass
        for (SportEvent e : got.values()) {
            try {
                Circuit ci = circuit(c, e.circuitId, now);
                if (ci != null) laps(e, ci);
            } catch (Exception ignored) { }
            join(e, ro, st);
            e.fetchedAt = now;
            r.events.add(e);
        }
        if (!cal.isEmpty()) r.listed = CalendarEntry.ids(cal);
        r.ok = answered || !asked;
        return r;
    }

    /**
     * One weekend by date. ESPN files a weekend under its US Eastern days (CalendarEntry.etDay), so: the stored first
     * session's Eastern day, then the calendar's start (Eastern, then UTC) and end. answer[0] is set when a request
     * was answered (the id was then missing from it when null is returned).
     */
    static SportEvent weekend(String id, CalendarEntry ce, SportEvent known, int[] answer) {
        Set<String> days = new LinkedHashSet<>();
        if (known != null && known.start > 0) days.add(CalendarEntry.etDay(known.start));
        if (ce != null && ce.approxStart > 0) { days.add(CalendarEntry.etDay(ce.approxStart)); days.add(CalendarEntry.utcDay(ce.approxStart)); }
        if (ce != null && ce.approxEnd > 0) days.add(CalendarEntry.etDay(ce.approxEnd));
        for (String day : days) {
            try {
                JSONObject d = new JSONObject(FeedResult.net.get(BOARD + "?dates=" + day));
                answer[0] = 1;
                for (SportEvent e : parseScoreboard(d)) if (id.equals(e.id)) return e;
            } catch (IOException x) {
                if (CalendarEntry.notFound(x)) answer[0] = 1;
            } catch (Exception ignored) {
                answer[0] = 1;  // answered, but not JSON we can read
            }
        }
        return null;
    }

    // ---------- parsing (pure) ----------
    /** Every weekend in a scoreboard answer, each with its sessions in start order; broken items are skipped. */
    static List<SportEvent> parseScoreboard(JSONObject d) {
        List<SportEvent> out = new ArrayList<>();
        JSONArray ev = d == null ? null : d.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) {
            try {
                SportEvent e = event(ev.getJSONObject(i));
                if (e != null) out.add(e);
            } catch (Exception ignored) { }
        }
        return out;
    }

    static SportEvent event(JSONObject o) {
        String id = o.optString("id", "");
        if (id.isEmpty()) return null;
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1;
        e.id = id;
        e.name = o.optString("name", "").trim();
        e.shortName = o.optString("shortName", "").trim();
        JSONObject ci = o.optJSONObject("circuit");
        if (ci != null) {
            e.circuitId = ci.optString("id", "");
            e.circuitName = ci.optString("fullName", "").trim();
            JSONObject a = ci.optJSONObject("address");
            if (a != null) { e.city = a.optString("city", "").trim(); e.country = a.optString("country", "").trim(); }
        }
        JSONArray comps = o.optJSONArray("competitions");
        for (int j = 0; comps != null && j < comps.length(); j++) {
            try {
                SportEvent.Session s = session(comps.getJSONObject(j));
                if (s != null && e.session(s.id) == null) e.sessions.add(s);
            } catch (Exception ignored) { }
        }
        if (e.sessions.isEmpty()) return null;  // no session time: nothing to show
        EventStore.sortSessions(e);
        EventStore.bounds(e);
        SportEngine.derive(e);
        return e;
    }

    static SportEvent.Session session(JSONObject c) {
        String id = c.optString("id", "");
        long start = Sources.iso(c.optString("date", ""));
        if (id.isEmpty() || start <= 0) return null;
        SportEvent.Session s = new SportEvent.Session();
        s.id = id;
        s.start = start;
        s.tbd = !c.optBoolean("timeValid", true);
        JSONObject type = c.optJSONObject("type");
        s.typeAbbr = type == null ? "" : type.optString("abbreviation", "").trim();
        s.kind = kind(s.typeAbbr, type == null ? "" : type.optString("id", ""));
        JSONObject st = c.optJSONObject("status");
        String[] m = status(st, true);
        s.state = m[0]; s.detail = m[1]; s.statusName = m[2];
        // status.period is the lap only in a race or a sprint (practice and qualifying carry meaningless values)
        if (("RACE".equals(s.kind) || "SPRINT".equals(s.kind)) && st != null && !"pre".equals(s.state)) s.lap = Math.max(0, st.optInt("period"));
        JSONArray cs = c.optJSONArray("competitors");
        for (int k = 0; cs != null && k < cs.length(); k++) {
            try {
                SportEvent.Entrant x = entrant(cs.getJSONObject(k));
                if (x != null) s.order.add(x);
            } catch (Exception ignored) { }
        }
        Collections.sort(s.order, new Comparator<SportEvent.Entrant>() {  // by place; unplaced (0) last, stable
            public int compare(SportEvent.Entrant a, SportEvent.Entrant b) {
                return Integer.compare(a.pos <= 0 ? Integer.MAX_VALUE : a.pos, b.pos <= 0 ? Integer.MAX_VALUE : b.pos);
            }
        });
        return s;
    }

    static SportEvent.Entrant entrant(JSONObject c) {
        JSONObject a = c.optJSONObject("athlete");
        String id = c.optString("id", "");
        if (id.isEmpty() && a != null) id = a.optString("id", "");
        if (id.isEmpty()) return null;
        SportEvent.Entrant x = new SportEvent.Entrant();
        x.id = id;
        x.pos = c.optInt("order");
        x.winner = c.optBoolean("winner");
        if (a != null) {
            x.name = a.optString("displayName", a.optString("fullName", "")).trim();
            x.shortName = a.optString("shortName", "").trim();
            x.code = a.optString("abbreviation", "").trim();
            JSONObject f = a.optJSONObject("flag");
            if (f != null) x.flag = f.optString("href", "");
        }
        return x;
    }

    /**
     * A session kind from ESPN's type: abbreviation first (FP1/FP2/FP3, SS -> SQ, SR -> SPRINT, Qual -> QUAL,
     * Race -> RACE), the type id as the cross-check when the abbreviation is new (5 SQ, 6 SPRINT, 2 QUAL, 3 RACE).
     * Anything else is OTHER (typeAbbr keeps its label).
     */
    static String kind(String abbr, String typeId) {
        String a = abbr == null ? "" : abbr.trim().toUpperCase(Locale.ROOT);
        switch (a) {
            case "FP1": case "FP2": case "FP3": return a;
            case "SS": case "SQ": case "SPRINT QUALIFYING": case "SPRINT SHOOTOUT": return "SQ";
            case "SR": case "SPRINT": return "SPRINT";
            case "QUAL": case "Q": case "QUALIFYING": return "QUAL";
            case "RACE": case "R": return "RACE";
            default: break;
        }
        switch (typeId == null ? "" : typeId.trim()) {
            case "5": return "SQ";
            case "6": return "SPRINT";
            case "2": return "QUAL";
            case "3": return "RACE";
            default: return "OTHER";
        }
    }

    /**
     * {state, detail, ESPN name} from an ESPN status, by status.type.name before state (STATUS_CANCELED comes with
     * state post): CANCEL -> off/CANCELLED, POSTPONED -> off/POSTPONED, while in: RED / SUSPENDED / DELAY ->
     * in/RED_FLAG (racing) or in/SUSPENDED (a bout), DELAYED before the start -> pre/DELAYED, FINAL -> post, otherwise
     * the state as given (pre when unknown).
     */
    static String[] status(JSONObject st, boolean racing) {
        JSONObject t = st == null ? null : st.optJSONObject("type");
        String name = t == null ? "" : t.optString("name", "");
        String state = t == null ? "" : t.optString("state", "");
        String up = name.toUpperCase(Locale.ROOT);
        Set<String> tok = new HashSet<>();
        for (String x : up.split("_")) tok.add(x);
        if (up.contains("CANCEL")) return new String[]{"off", "CANCELLED", name};
        if (up.contains("POSTPONED")) return new String[]{"off", "POSTPONED", name};
        if ("in".equals(state) && (tok.contains("RED") || up.contains("SUSPENDED") || up.contains("DELAY")))
            return new String[]{"in", racing ? "RED_FLAG" : "SUSPENDED", name};
        if (up.contains("DELAY") && !"post".equals(state)) return new String[]{"pre", "DELAYED", name};
        if (up.contains("FINAL")) return new String[]{"post", "", name};
        if ("pre".equals(state) || "in".equals(state) || "post".equals(state)) return new String[]{state, "", name};
        return new String[]{"pre", "", name};
    }

    /** The calendar of a no-date scoreboard (ids from event.$ref, labels, approximate dates). */
    static List<CalendarEntry> parseCalendar(JSONObject d) {
        return CalendarEntry.parse(d, false);
    }

    /** The core API's circuit: laps, length ("6.116 km"), full name; null without an id. */
    static Circuit parseCircuit(JSONObject d) {
        if (d == null || d.optString("id", "").isEmpty()) return null;
        Circuit x = new Circuit();
        x.id = d.optString("id");
        x.name = d.optString("fullName", "").trim();
        x.laps = Math.max(0, d.optInt("laps"));
        x.lengthKm = km(d.optString("length", ""));
        return x;
    }

    /** "6.116 km" -> 6.116; "3.4 mi" -> km; 0 when unreadable. */
    static float km(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(km|mi)?").matcher(s.trim().toLowerCase(Locale.ROOT));
        if (!m.find()) return 0;
        try {
            float v = Float.parseFloat(m.group(1));
            return "mi".equals(m.group(2)) ? v * 1.609344f : v;
        } catch (NumberFormatException x) {
            return 0;
        }
    }

    /** The fewest complete laps over 100 km (the sprint's distance); 0 when the length is unknown. Medium confidence. */
    static int sprintLaps(float lengthKm) {
        return lengthKm > 0.5f ? (int) Math.ceil(100.0 / lengthKm - 1e-6) : 0;
    }

    /** The circuit's facts onto the weekend: its laps and length, the race's and the sprint's total laps. */
    static void laps(SportEvent e, Circuit ci) {
        if (ci.laps > 0) e.laps = ci.laps;
        if (ci.lengthKm > 0) e.lengthKm = ci.lengthKm;
        if (e.circuitName.isEmpty()) e.circuitName = ci.name;
        for (SportEvent.Session s : e.sessions) {
            if ("RACE".equals(s.kind) && e.laps > 0) s.laps = e.laps;
            else if ("SPRINT".equals(s.kind)) s.laps = sprintLaps(e.lengthKm);
        }
    }

    // ---------- standings and roster ----------
    /** Drivers (children "Driver Standings") and constructors ("Constructor Standings"), by rank. at is left 0. */
    static Standings parseStandings(JSONObject d) {
        Standings out = new Standings();
        JSONArray ch = d == null ? null : d.optJSONArray("children");
        if (ch == null) return out;
        JSONArray drivers = null, teams = null;
        for (int i = 0; i < ch.length(); i++) {
            JSONObject c = ch.optJSONObject(i);
            if (c == null) continue;
            String n = (c.optString("name", "") + " " + c.optString("abbreviation", "")).toLowerCase(Locale.ROOT);
            JSONObject s = c.optJSONObject("standings");
            JSONArray en = s == null ? null : s.optJSONArray("entries");
            if (n.contains("constructor") || n.contains("team")) teams = en;
            else if (n.contains("driver")) drivers = en;
            else if (i == 0 && drivers == null) drivers = en;
            else if (i == 1 && teams == null) teams = en;
        }
        for (int i = 0; drivers != null && i < drivers.length(); i++) {
            try {
                JSONObject e = drivers.getJSONObject(i), a = e.getJSONObject("athlete");
                SportEvent.Entrant x = new SportEvent.Entrant();
                x.id = a.optString("id", "");
                if (x.id.isEmpty()) continue;
                x.code = a.optString("abbreviation", "").trim();
                x.name = a.optString("displayName", a.optString("name", "")).trim();
                x.shortName = a.optString("shortName", "").trim();
                JSONObject f = a.optJSONObject("flag");
                if (f != null) x.flag = f.optString("href", "");
                x.pos = (int) stat(e, "rank");
                x.points = stat(e, "championshipPts");
                out.drivers.add(x);
            } catch (Exception ignored) { }
        }
        for (int i = 0; teams != null && i < teams.length(); i++) {
            try {
                JSONObject e = teams.getJSONObject(i), t = e.getJSONObject("team");
                Team x = new Team();
                x.id = t.optString("id", "");
                if (x.id.isEmpty()) continue;
                x.name = t.optString("displayName", t.optString("name", "")).trim();
                x.color = color(t.optString("color", ""));
                x.rank = (int) stat(e, "rank");
                x.points = stat(e, "points");
                out.teams.add(x);
            } catch (Exception ignored) { }
        }
        Collections.sort(out.drivers, new Comparator<SportEvent.Entrant>() {
            public int compare(SportEvent.Entrant a, SportEvent.Entrant b) { return Integer.compare(rankKey(a.pos), rankKey(b.pos)); }
        });
        Collections.sort(out.teams, new Comparator<Team>() {
            public int compare(Team a, Team b) { return Integer.compare(rankKey(a.rank), rankKey(b.rank)); }
        });
        return out;
    }

    private static int rankKey(int r) { return r <= 0 ? Integer.MAX_VALUE : r; }

    private static float stat(JSONObject entry, String name) {
        JSONArray st = entry.optJSONArray("stats");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject s = st.optJSONObject(i);
            if (s != null && name.equals(s.optString("name"))) {
                double v = s.optDouble("value", Double.NaN);
                if (Double.isNaN(v)) {
                    try { v = Double.parseDouble(s.optString("displayValue", "").trim()); } catch (NumberFormatException x) { v = 0; }
                }
                return (float) v;
            }
        }
        return 0;
    }

    /** "FF8700" / "#ff8700" -> opaque ARGB; 0 when unreadable. */
    static int color(String hex) {
        if (hex == null) return 0;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return 0;
        try { return 0xFF000000 | Integer.parseInt(h, 16); } catch (NumberFormatException x) { return 0; }
    }

    static String hex(int color) { return color == 0 ? "" : String.format(Locale.US, "%06X", color & 0xFFFFFF); }

    /**
     * The constructor of an athlete's vehicles[0] (core API): its "team", then its "manufacturer", matched to a
     * standings constructor by normalised name (exact, then one inside the other). Null when none matches.
     */
    static Team matchTeam(String team, String manufacturer, Collection<Team> teams) {
        for (String want : new String[]{team, manufacturer}) {
            String w = norm(want);
            if (w.isEmpty()) continue;
            for (Team t : teams) if (w.equals(norm(t.name))) return t;
            for (Team t : teams) {
                String n = norm(t.name);
                if (n.length() >= 3 && w.length() >= 3 && (w.contains(n) || n.contains(w))) return t;
            }
        }
        return null;
    }

    /** Lower case, accents and everything but letters and digits removed, "f1" and "team" words dropped. */
    static String norm(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = (" " + n.replaceAll("[^a-z0-9]+", " ") + " ").replace(" f1 ", " ").replace(" team ", " ").replace(" formula one ", " ");
        return n.replace(" ", "");
    }

    /** {team, manufacturer, number} of a core athlete's first vehicle; empty strings when it has none. */
    static String[] vehicle(JSONObject athlete) {
        JSONArray v = athlete == null ? null : athlete.optJSONArray("vehicles");
        JSONObject o = v == null ? null : v.optJSONObject(0);
        if (o == null) return new String[]{"", "", ""};
        return new String[]{o.optString("team", "").trim(), o.optString("manufacturer", "").trim(), o.optString("number", "").trim()};
    }

    /** The roster from the standings and each driver's core athlete (id -> JSON); drivers with no match keep no team. */
    static Roster buildRoster(Standings st, Map<String, JSONObject> athletes) {
        Roster r = new Roster();
        for (Team t : st.teams) r.teams.put(t.id, t.copy());
        for (SportEvent.Entrant d : st.drivers) {
            SportEvent.Entrant x = d.copy();
            x.pos = 0; x.points = 0;
            JSONObject a = athletes.get(d.id);
            if (a != null) {
                String[] v = vehicle(a);
                Team t = matchTeam(v[0], v[1], st.teams);
                if (t != null) { x.team = t.name; x.teamId = t.id; x.color = t.color; }
                if (!v[2].isEmpty()) x.number = v[2];
                if (x.code.isEmpty()) x.code = a.optString("abbreviation", "").trim();
            }
            r.byDriver.put(x.id, x);
        }
        return r;
    }

    /**
     * Codes, teams and colours onto every session's order: the roster first (driver -> team), then the standings
     * (driver code, team colour). Either may be null.
     */
    static void join(SportEvent e, Roster ro, Standings st) {
        for (SportEvent.Session s : e.sessions)
            for (SportEvent.Entrant x : s.order) joinOne(x, ro, st);
    }

    private static void joinOne(SportEvent.Entrant x, Roster ro, Standings st) {
        SportEvent.Entrant r = ro == null ? null : ro.byDriver.get(x.id);
        if (r != null) {
            if (x.code.isEmpty()) x.code = r.code;
            if (x.team.isEmpty()) { x.team = r.team; x.teamId = r.teamId; }
            if (x.color == 0) x.color = r.color;
            if (x.flag.isEmpty()) x.flag = r.flag;
            if (x.number.isEmpty()) x.number = r.number;
        }
        SportEvent.Entrant d = st == null ? null : st.driver(x.id);
        if (d != null) {
            if (x.code.isEmpty()) x.code = d.code;
            if (x.flag.isEmpty()) x.flag = d.flag;
        }
        if (x.color == 0 && st != null && !x.teamId.isEmpty()) {
            Team t = st.team(x.teamId);
            if (t != null) x.color = t.color;
        }
        if (x.code.isEmpty()) x.code = codeOf(x.name);  // a stand-in in practice (not in the standings): F1's own rule
    }

    /** The three-letter code F1 gives a driver: the surname's first three letters ("Gabriele Mini" -> MIN); "" when none. */
    static String codeOf(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] w = name.trim().split("\\s+");
        String last = java.text.Normalizer.normalize(w[w.length - 1], java.text.Normalizer.Form.NFD).replaceAll("[^A-Za-z]", "");
        return last.length() < 3 ? last.toUpperCase(Locale.ROOT) : last.substring(0, 3).toUpperCase(Locale.ROOT);
    }

    /**
     * Cached standings (f1_standings.json), downloaded when a week old, missing or forced; the cached ones when the
     * download fails; null when there are none (or no Context). Each driver comes with his team, team id and colour
     * from the roster on the TV (ESPN's table has none; withTeams), so cards, Settings and the app's screens colour
     * drivers the same way. Network (the table only, never the roster): call off the UI thread.
     */
    static Standings standings(Context c, boolean force) { return standings(c, force, System.currentTimeMillis()); }

    static synchronized Standings standings(Context c, boolean force, long now) {
        if (c == null) return null;
        File f = new File(c.getFilesDir(), STANDINGS_FILE);
        Standings cached = readStandings(CalendarEntry.read(f));
        if (cached != null && !force && now - cached.at < STANDINGS_EVERY) return memoStandings = withTeams(cached, rosterOnFile(c));
        try {
            Standings s = parseStandings(new JSONObject(FeedResult.net.get(STANDINGS)));
            if (!s.drivers.isEmpty()) {
                s.at = now;
                EventStore.writeAtomic(f, standingsJson(s));
                return memoStandings = withTeams(s, rosterOnFile(c));
            }
        } catch (Exception ignored) { }
        if (cached != null) memoStandings = cached = withTeams(cached, rosterOnFile(c));
        return cached;
    }

    /**
     * What the app's screens show: the roster brought up to date when it is a week old (one request per driver), then
     * the standings joined with it. Network: call off the UI thread.
     */
    static Standings table(Context c) {
        if (c == null) return null;
        roster(c, false);
        return standings(c, false);
    }

    /** The roster on the TV (f1_roster.json; no network); null when there is none. */
    static Roster rosterOnFile(Context c) {
        return c == null ? null : readRoster(CalendarEntry.read(new File(c.getFilesDir(), ROSTER_FILE)));
    }

    /**
     * The standings with each driver's team, team id and colour from the roster where the table has none (a copy;
     * pure). A driver whose roster entry has no colour takes his constructor's from the table. st itself when either
     * is missing.
     */
    static Standings withTeams(Standings st, Roster r) {
        if (st == null || r == null) return st;
        Standings out = new Standings();
        out.at = st.at;
        out.teams.addAll(st.teams);
        for (SportEvent.Entrant x : st.drivers) {
            SportEvent.Entrant y = x.copy();
            SportEvent.Entrant ro = r.byDriver.get(x.id);
            if (ro != null) {
                if (y.team.isEmpty()) y.team = ro.team;
                if (y.teamId.isEmpty()) y.teamId = ro.teamId;
                if (y.color == 0) y.color = ro.color;
                if (y.number.isEmpty()) y.number = ro.number;
                if (y.code.isEmpty()) y.code = ro.code;
            }
            if (y.color == 0 && !y.teamId.isEmpty()) {
                Team t = st.team(y.teamId);
                if (t == null) t = r.teams.get(y.teamId);
                if (t != null) y.color = t.color;
            }
            out.drivers.add(y);
        }
        return out;
    }

    /**
     * Cached roster (f1_roster.json), weekly: every standings driver's core athlete, matched to a constructor. A
     * driver whose request fails keeps the cached entry. Null when there is none. Network: call off the UI thread.
     */
    static Roster roster(Context c, boolean force) { return roster(c, force, System.currentTimeMillis()); }

    static synchronized Roster roster(Context c, boolean force, long now) {
        if (c == null) return null;
        File f = new File(c.getFilesDir(), ROSTER_FILE);
        Roster cached = readRoster(CalendarEntry.read(f));
        if (cached != null && !force && now - cached.at < ROSTER_EVERY) return memoRoster = cached;
        Standings st = standings(c, false, now);
        if (st == null || st.drivers.isEmpty()) return cached;
        Map<String, JSONObject> athletes = new HashMap<>();
        for (SportEvent.Entrant d : st.drivers) {
            try { athletes.put(d.id, new JSONObject(FeedResult.net.get(CORE + "athletes/" + d.id + "?lang=en&region=us"))); }
            catch (Exception ignored) { }
        }
        if (athletes.isEmpty()) {
            if (cached != null) memoRoster = cached;
            return cached;
        }
        Roster r = buildRoster(st, athletes);
        if (cached != null)
            for (Map.Entry<String, SportEvent.Entrant> en : cached.byDriver.entrySet())
                if (!athletes.containsKey(en.getKey()) && r.byDriver.containsKey(en.getKey())) r.byDriver.put(en.getKey(), en.getValue());
        r.at = now;
        try { EventStore.writeAtomic(f, rosterJson(r)); } catch (Exception ignored) { }
        return memoRoster = r;
    }

    /** A circuit's race laps (f1_circuits.json); 0 when unknown. Network when not cached: call off the UI thread. */
    static int circuitLaps(Context c, String circuitId) {
        Circuit x = c == null ? null : circuit(c, circuitId);
        return x == null ? 0 : x.laps;
    }

    /** A circuit from f1_circuits.json, downloaded when missing or a year old; the cached one when that fails. */
    static Circuit circuit(Context c, String id) { return circuit(c, id, System.currentTimeMillis()); }

    static synchronized Circuit circuit(Context c, String id, long now) {
        if (c == null || id == null || id.isEmpty()) return null;
        File f = new File(c.getFilesDir(), CIRCUITS_FILE);
        JSONObject all;
        try { String t = CalendarEntry.read(f); all = t == null ? new JSONObject() : new JSONObject(t); } catch (Exception x) { all = new JSONObject(); }
        JSONObject o = all.optJSONObject(id);
        Circuit cached = null;
        if (o != null) {
            cached = new Circuit();
            cached.id = id; cached.name = o.optString("name", ""); cached.laps = o.optInt("laps"); cached.lengthKm = (float) o.optDouble("km", 0);
            cached.at = o.optLong("at");
            if (now - cached.at < CIRCUIT_EVERY) return cached;
        }
        try {
            Circuit x = parseCircuit(new JSONObject(FeedResult.net.get(CORE + "leagues/f1/circuits/" + id + "?lang=en&region=us")));
            if (x != null && x.laps > 0) {
                x.at = now;
                all.put(id, new JSONObject().put("name", x.name).put("laps", x.laps).put("km", (double) x.lengthKm).put("at", now));
                EventStore.writeAtomic(f, all.toString());
                return x;
            }
        } catch (Exception ignored) { }
        return cached;
    }

    // ---------- side files ----------
    static String standingsJson(Standings s) throws Exception {
        JSONArray d = new JSONArray(), t = new JSONArray();
        for (SportEvent.Entrant x : s.drivers)
            d.put(new JSONObject().put("id", x.id).put("code", x.code).put("name", x.name).put("short", x.shortName).put("flag", x.flag)
                .put("rank", x.pos).put("pts", (double) x.points));
        for (Team x : s.teams)
            t.put(new JSONObject().put("id", x.id).put("name", x.name).put("color", hex(x.color)).put("rank", x.rank).put("pts", (double) x.points));
        return new JSONObject().put("v", 1).put("at", s.at).put("drivers", d).put("teams", t).toString();
    }

    static Standings readStandings(String text) {
        if (text == null) return null;
        try {
            JSONObject o = new JSONObject(text);
            Standings s = new Standings();
            s.at = o.optLong("at");
            JSONArray d = o.optJSONArray("drivers"), t = o.optJSONArray("teams");
            for (int i = 0; d != null && i < d.length(); i++) {
                JSONObject x = d.getJSONObject(i);
                SportEvent.Entrant e = new SportEvent.Entrant();
                e.id = x.optString("id"); e.code = x.optString("code"); e.name = x.optString("name"); e.shortName = x.optString("short");
                e.flag = x.optString("flag"); e.pos = x.optInt("rank"); e.points = (float) x.optDouble("pts", 0);
                s.drivers.add(e);
            }
            for (int i = 0; t != null && i < t.length(); i++) {
                JSONObject x = t.getJSONObject(i);
                Team e = new Team();
                e.id = x.optString("id"); e.name = x.optString("name"); e.color = color(x.optString("color")); e.rank = x.optInt("rank");
                e.points = (float) x.optDouble("pts", 0);
                s.teams.add(e);
            }
            return s.drivers.isEmpty() ? null : s;
        } catch (Exception x) {
            return null;
        }
    }

    static String rosterJson(Roster r) throws Exception {
        JSONObject d = new JSONObject(), t = new JSONObject();
        for (SportEvent.Entrant x : r.byDriver.values())
            d.put(x.id, new JSONObject().put("code", x.code).put("name", x.name).put("short", x.shortName).put("flag", x.flag)
                .put("team", x.team).put("teamId", x.teamId).put("color", hex(x.color)).put("no", x.number));
        for (Team x : r.teams.values())
            t.put(x.id, new JSONObject().put("name", x.name).put("color", hex(x.color)).put("rank", x.rank).put("pts", (double) x.points));
        return new JSONObject().put("v", 1).put("at", r.at).put("drivers", d).put("teams", t).toString();
    }

    static Roster readRoster(String text) {
        if (text == null) return null;
        try {
            JSONObject o = new JSONObject(text);
            Roster r = new Roster();
            r.at = o.optLong("at");
            JSONObject d = o.optJSONObject("drivers"), t = o.optJSONObject("teams");
            if (d != null)
                for (java.util.Iterator<String> it = d.keys(); it.hasNext(); ) {
                    String id = it.next();
                    JSONObject x = d.getJSONObject(id);
                    SportEvent.Entrant e = new SportEvent.Entrant();
                    e.id = id; e.code = x.optString("code"); e.name = x.optString("name"); e.shortName = x.optString("short");
                    e.flag = x.optString("flag"); e.team = x.optString("team"); e.teamId = x.optString("teamId"); e.color = color(x.optString("color"));
                    e.number = x.optString("no", "");
                    r.byDriver.put(id, e);
                }
            if (t != null)
                for (java.util.Iterator<String> it = t.keys(); it.hasNext(); ) {
                    String id = it.next();
                    JSONObject x = t.getJSONObject(id);
                    Team e = new Team();
                    e.id = id; e.name = x.optString("name"); e.color = color(x.optString("color")); e.rank = x.optInt("rank");
                    e.points = (float) x.optDouble("pts", 0);
                    r.teams.put(id, e);
                }
            return r.byDriver.isEmpty() ? null : r;
        } catch (Exception x) {
            return null;
        }
    }

    // ---------- live ----------
    /**
     * The live fields of this weekend's sessions (one scoreboard request per tick, shared through boards): the US
     * Eastern day of the session being played (else the nearest one), then of the weekend's first session, then the
     * session's UTC day (CalendarEntry.etDay: the race at 04:00Z on 22 Nov is filed under the 21st). Only
     * sessions the stored weekend has come back (never an invented one): state, detail, lap, total laps and the order,
     * with codes and colours joined from the stored weekend and the last roster. null: no answer listed the weekend.
     * The order is copied whatever LIVE_ORDER_TRUSTED says; the cards decide whether to draw it.
     */
    static SportEvent live(SportEvent e, Map<String, String> boards) {
        if (e == null || e.id == null) return null;
        long now = System.currentTimeMillis();
        SportEvent.Session focus = e.live();
        if (focus == null)
            for (SportEvent.Session s : e.sessions) {
                if ("off".equals(s.state) || s.tbd) continue;
                if (focus == null || Math.abs(s.start - now) < Math.abs(focus.start - now)) focus = s;
            }
        Set<String> days = new LinkedHashSet<>();
        if (focus != null) days.add(CalendarEntry.etDay(focus.start));
        if (e.start > 0) days.add(CalendarEntry.etDay(e.start));
        if (focus != null) days.add(CalendarEntry.utcDay(focus.start));
        for (String day : days) {
            try {
                JSONObject d = new JSONObject(Sources.board(BOARD + "?dates=" + day, boards));
                for (SportEvent p : parseScoreboard(d)) if (e.id.equals(p.id)) return liveOf(e, p, memoRoster, memoStandings);
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** The live reading of `parsed` for the stored weekend (pure): its sessions only, codes joined. */
    static SportEvent liveOf(SportEvent stored, SportEvent parsed, Roster ro, Standings st) {
        SportEvent out = new SportEvent();
        out.sport = SportEvent.F1;
        out.id = stored.id;
        Map<String, SportEvent.Entrant> known = new HashMap<>();
        for (SportEvent.Session s : stored.sessions)
            for (SportEvent.Entrant x : s.order) if (!x.code.isEmpty() || x.color != 0) known.put(x.id, x);
        for (SportEvent.Session ps : parsed.sessions) {
            SportEvent.Session ss = stored.session(ps.id);
            if (ss == null) continue;
            SportEvent.Session ls = new SportEvent.Session();
            ls.id = ss.id; ls.kind = ss.kind; ls.typeAbbr = ss.typeAbbr; ls.start = ss.start; ls.tbd = ss.tbd;
            ls.state = ps.state; ls.detail = ps.detail; ls.statusName = ps.statusName; ls.lap = ps.lap;
            ls.laps = ss.laps > 0 ? ss.laps : "RACE".equals(ss.kind) ? stored.laps : "SPRINT".equals(ss.kind) ? sprintLaps(stored.lengthKm) : 0;
            for (SportEvent.Entrant x : ps.order) {
                SportEvent.Entrant y = x.copy(), k = known.get(x.id);
                if (k != null) {
                    if (y.code.isEmpty()) y.code = k.code;
                    if (y.team.isEmpty()) { y.team = k.team; y.teamId = k.teamId; }
                    if (y.color == 0) y.color = k.color;
                    if (y.number.isEmpty()) y.number = k.number;
                }
                joinOne(y, ro, st);
                ls.order.add(y);
            }
            out.sessions.add(ls);
        }
        return out;
    }
}
