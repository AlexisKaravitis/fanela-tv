package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The sports' events on disk: files/events1.json (F1-UFC-ARCHITECTURE §1.3-1.5), apart from matches4.json so a broken
 * F1 parse can never touch the football data. Written atomically; loading is memoised on the file's time and length,
 * and every list a caller receives holds copies.
 *
 * Merge rules (pure, unit-tested), as Store.mergeInto and keepProgress:
 *  - a feed that failed (FeedResult.ok false) keeps that sport's last good events;
 *  - never backwards: a session (or bout) "in" is not set back to "pre" within 6 h of its start, a "post" one is not
 *    set back to "in"/"pre" within 24 h; a tick's live fields win over a refresh while seenLiveAt is newer and under
 *    10 min old;
 *  - results may change for 48 h after the last session (penalties, overturned results), then they are frozen;
 *  - identity is the id only: a renamed event is the same event with a new name;
 *  - the where-to-watch fields belong to SportWhere: a refresh never touches them;
 *  - a fighter's recordBefore is set once (first seen before the bout) and never overwritten; first seen after the
 *    bout, it is rebuilt from the result (SportEvent.recordBefore).
 */
final class EventStore {
    private EventStore() { }

    static final String FILE = "events1.json";
    static final int VERSION = 1;
    static final long IN_HOLD = 6 * 3600000L, POST_HOLD = 24 * 3600000L, TICK_WINS = 10 * 60000L,
        RESULTS_OPEN = 48 * 3600000L, KEEP_PAST = 60L * 86400000L, KEEP_CANCELLED = 7L * 86400000L;
    static final int MISSES = 3, NEXT_F1 = 3, NEXT_UFC = 4;
    /** The side files of each sport (stream B / C write them); forgetSport deletes them. */
    static final String[] F1_FILES = {"f1_circuits.json", "f1_standings.json", "f1_roster.json", "f1_calendar.json"},
        UFC_FILES = {"ufc_calendar.json", "guide_magenta_ufc.json"};

    // ---------- merge (pure) ----------
    /**
     * One sport's feed answer merged into everything stored (pure: the caller loads once and saves once). A failed
     * answer changes nothing. An event the answer leaves out is kept (a fetch may cover only the weekends that were
     * due); one it asked for and missed counts a miss, and is dropped after MISSES in a row once the calendar
     * (FeedResult.listed) no longer lists it.
     */
    static List<SportEvent> merge(List<SportEvent> all, FeedResult r, long now) {
        List<SportEvent> out = new ArrayList<>();
        if (r == null || !r.ok || !Sports.known(r.sport)) { out.addAll(all); return out; }
        Map<String, SportEvent> fresh = new LinkedHashMap<>();
        for (SportEvent e : r.events) {
            if (e == null || blank(e.id)) continue;
            if (e.sport == null) e.sport = r.sport;
            if (r.sport.equals(e.sport)) fresh.put(e.id, e);
        }
        for (SportEvent old : all) {
            if (!r.sport.equals(old.sport)) { out.add(old); continue; }
            SportEvent f = fresh.remove(old.id);
            if (f != null) { out.add(keepProgress(old, f, now)); continue; }
            SportEvent k = old.copy();
            if (r.missed.contains(k.id)) k.misses++;
            if (k.misses >= MISSES && r.listed != null && !r.listed.contains(k.id)) continue;  // gone from the calendar too
            out.add(k);
        }
        for (SportEvent f : fresh.values()) out.add(keepProgress(null, f, now));
        return out;
    }

    /** A fresh reading of an event against the stored one (null: new), by the rules above. Returns a new object. */
    static SportEvent keepProgress(SportEvent old, SportEvent fresh, long now) {
        SportEvent e = fresh.copy();
        e.misses = 0;
        if (old != null) {
            if (e.fetchedAt < old.fetchedAt) e.fetchedAt = old.fetchedAt;
            if (blank(e.name)) e.name = old.name;
            if (blank(e.shortName)) e.shortName = old.shortName;
            if (blank(e.circuitId)) e.circuitId = old.circuitId;
            if (blank(e.circuitName)) e.circuitName = old.circuitName;
            if (blank(e.city)) e.city = old.city;
            if (blank(e.country)) e.country = old.country;
            if (blank(e.venue)) e.venue = old.venue;
            if (e.laps <= 0) e.laps = old.laps;
            if (e.lengthKm <= 0) e.lengthKm = old.lengthKm;
            boolean resultsOpen = now < lastStart(old) + RESULTS_OPEN;
            Map<String, SportEvent.Bout> oldBouts = new HashMap<>();
            Map<String, SportEvent.Session> oldBoutSession = new HashMap<>();
            for (SportEvent.Session os : old.sessions)
                for (SportEvent.Bout ob : os.bouts) { oldBouts.put(ob.id, ob); oldBoutSession.put(ob.id, os); }
            for (SportEvent.Session fs : e.sessions) {
                SportEvent.Session os = old.session(fs.id);
                boolean kept = false;
                if (os != null) {
                    fs.watch = os.watch == null ? new SportEvent.SessionWatch() : os.watch.copy();  // SportWhere's, never the feed's
                    kept = keepSession(os, fs, now, resultsOpen);
                }
                for (SportEvent.Bout fb : fs.bouts) {  // a bout may have moved between segments: found by id anywhere
                    SportEvent.Bout ob = oldBouts.get(fb.id);
                    if (ob != null) keepBout(ob, fb, oldBoutSession.get(fb.id).start, now, resultsOpen, kept);
                }
            }
            for (SportEvent.Session os : old.sessions)  // a session being played that this answer left out stays
                if (e.session(os.id) == null && "in".equals(os.state) && now < os.start + IN_HOLD) e.sessions.add(os.copy());
        }
        fillRecords(old, e);
        sortSessions(e);
        bounds(e);
        SportEngine.derive(e);
        return e;
    }

    /** Session-level rules; true when the old live fields were kept (its bouts then keep theirs too). */
    private static boolean keepSession(SportEvent.Session os, SportEvent.Session fs, long now, boolean resultsOpen) {
        boolean keep = ("in".equals(os.state) && "pre".equals(fs.state) && now < os.start + IN_HOLD)
            || ("post".equals(os.state) && ("pre".equals(fs.state) || "in".equals(fs.state)) && now < os.start + POST_HOLD)
            || ("in".equals(os.state) && "in".equals(fs.state) && os.seenLiveAt > fs.seenLiveAt && now - os.seenLiveAt < TICK_WINS);
        if (keep) {
            fs.state = os.state; fs.detail = os.detail; fs.statusName = os.statusName; fs.lap = os.lap;
            if (os.laps > 0) fs.laps = os.laps;
            fs.order.clear();
            for (SportEvent.Entrant x : os.order) fs.order.add(x.copy());
        } else if ("post".equals(os.state) && "post".equals(fs.state) && !resultsOpen && !os.order.isEmpty()) {
            fs.order.clear();  // results frozen 48 h after the last session
            for (SportEvent.Entrant x : os.order) fs.order.add(x.copy());
        }
        if (fs.laps <= 0) fs.laps = os.laps;
        if (os.seenLiveAt > fs.seenLiveAt) fs.seenLiveAt = os.seenLiveAt;
        return keep;
    }

    private static void keepBout(SportEvent.Bout ob, SportEvent.Bout fb, long start, long now, boolean resultsOpen, boolean sessionKept) {
        if (sessionKept
            || ("in".equals(ob.state) && "pre".equals(fb.state) && now < start + IN_HOLD)
            || ("post".equals(ob.state) && ("pre".equals(fb.state) || "in".equals(fb.state)) && now < start + POST_HOLD)) {
            fb.takeLive(ob);
        } else if ("post".equals(ob.state) && "post".equals(fb.state) && !resultsOpen) {
            fb.outcome = ob.outcome; fb.method = ob.method; fb.methodDetail = ob.methodDetail; fb.round = ob.round; fb.clock = ob.clock;
        }
    }

    /**
     * Every fighter's record before the bout: the stored one when there is one (never overwritten), else the feed's
     * own, else the record as it stands before the bout, rebuilt from the result when first seen after it.
     */
    static void fillRecords(SportEvent old, SportEvent e) {
        Map<String, String> before = new HashMap<>();  // "<boutId>|<fighterId>" -> stored recordBefore
        if (old != null)
            for (SportEvent.Session s : old.sessions)
                for (SportEvent.Bout b : s.bouts)
                    for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b})
                        if (f != null && !blank(f.recordBefore)) before.put(b.id + "|" + f.id, f.recordBefore);
        for (SportEvent.Session s : e.sessions)
            for (SportEvent.Bout b : s.bouts) {
                fill(b, b.a, true, before);
                fill(b, b.b, false, before);
            }
    }

    private static void fill(SportEvent.Bout b, SportEvent.Fighter f, boolean sideA, Map<String, String> before) {
        if (f == null) return;
        String kept = before.get(b.id + "|" + f.id);
        if (kept != null) { f.recordBefore = kept; return; }
        if (!blank(f.recordBefore) || blank(f.record)) return;
        f.recordBefore = "post".equals(b.state) ? SportEvent.recordBefore(f.record, b.outcome, sideA) : f.record;
    }

    /**
     * The live fields of a tick's reading (F1Feed.live / UfcFeed.live) applied to the stored event: session state,
     * detail, lap, order and seenLiveAt; bout state, round, clock and result, bouts found by id across the card (the
     * scoreboard has no segment). Never invents a session or a bout. The merge rules then apply.
     */
    static SportEvent applyLive(SportEvent cur, SportEvent live, long now) {
        SportEvent x = cur.copy();
        Map<String, SportEvent.Bout> bouts = new HashMap<>();
        for (SportEvent.Session s : x.sessions) for (SportEvent.Bout b : s.bouts) bouts.put(b.id, b);
        for (SportEvent.Session ls : live.sessions) {
            SportEvent.Session xs = x.session(ls.id);
            if (xs != null) {
                xs.state = ls.state; xs.detail = ls.detail; xs.statusName = ls.statusName; xs.lap = ls.lap;
                if (ls.laps > 0) xs.laps = ls.laps;
                if (!ls.order.isEmpty()) { xs.order.clear(); for (SportEvent.Entrant o : ls.order) xs.order.add(o.copy()); }
                xs.seenLiveAt = Math.max(xs.seenLiveAt, ls.seenLiveAt);
            }
            for (SportEvent.Bout lb : ls.bouts) {
                SportEvent.Bout xb = bouts.get(lb.id);
                if (xb != null) xb.takeLive(lb);
            }
        }
        return keepProgress(cur, x, now);
    }

    /** The where-to-watch fields of every session of `from` copied onto the same sessions of `to` (SportWhere's only). */
    static void copyWatch(SportEvent from, SportEvent to) {
        for (SportEvent.Session s : to.sessions) {
            SportEvent.Session f = from.session(s.id);
            if (f != null && f.watch != null) s.watch = f.watch.copy();
        }
    }

    static long lastStart(SportEvent e) {
        long t = e.end;
        for (SportEvent.Session s : e.sessions) if (!s.tbd && s.start > t) t = s.start;
        return t > 0 ? t : e.start;
    }

    static void sortSessions(SportEvent e) {
        List<SportEvent.Session> s = e.byStart();
        e.sessions.clear();
        e.sessions.addAll(s);
    }

    /** start / end: the first and last session start (kept as they are when there are no sessions). */
    static void bounds(SportEvent e) {
        long first = Long.MAX_VALUE, last = 0;
        for (SportEvent.Session s : e.sessions) {
            if (s.start <= 0) continue;
            first = Math.min(first, s.start);
            last = Math.max(last, s.start);
        }
        if (last > 0) { e.start = first; e.end = last; }
        if (e.end < e.start) e.end = e.start;
    }

    static boolean cancelled(SportEvent e) {
        if (e.sessions.isEmpty()) return "off".equals(e.state);
        for (SportEvent.Session s : e.sessions) if (!"off".equals(s.state)) return false;
        return true;
    }

    // ---------- prune (pure) ----------
    /**
     * After every merge (§1.5): drops events whose last session is more than 60 days ago, cancelled events (every
     * session off) 7 days after their original end, and keeps at most the next 3 F1 weekends and the next 4 UFC events
     * still to come (an event with a session still to start). Sorted by start.
     */
    static List<SportEvent> prune(List<SportEvent> all, long now) {
        List<SportEvent> sorted = new ArrayList<>(all), out = new ArrayList<>();
        Collections.sort(sorted, new Comparator<SportEvent>() {
            public int compare(SportEvent a, SportEvent b) { return Long.compare(a.start, b.start); }
        });
        Map<String, Integer> ahead = new HashMap<>();
        for (SportEvent e : sorted) {
            long end = Math.max(e.end, e.start);
            if (end < now - KEEP_PAST) continue;
            boolean off = cancelled(e);
            if (off && end < now - KEEP_CANCELLED) continue;
            if (!off && end > now) {  // still to come (or under way with a session to start)
                int n = ahead.containsKey(e.sport) ? ahead.get(e.sport) : 0;
                if (n >= (SportEvent.F1.equals(e.sport) ? NEXT_F1 : NEXT_UFC)) continue;
                ahead.put(e.sport, n + 1);
            }
            out.add(e);
        }
        return out;
    }

    static SportEvent find(List<SportEvent> all, String anyKey) {
        String k = SportEvent.eventKey(anyKey);
        if (k == null) return null;
        for (SportEvent e : all) if (k.equals(e.key())) return e;
        return null;
    }

    // ---------- files ----------
    /** Drops the events of sports no longer followed. */
    static synchronized void retain(Context c, Set<String> sports) {
        List<SportEvent> all = load(c), keep = new ArrayList<>();
        for (SportEvent e : all) if (sports.contains(e.sport)) keep.add(e);
        if (keep.size() != all.size()) save(c, keep);
    }

    /**
     * Whether anything of a sport is still on the TV (pure but for the files and preferences it reads): the unfollow
     * mark (Sports.FORGET), an engine timestamp, a stored event, a card file (cs<sport>_*.png), a side file, a card
     * preference (sig_/uri_ of its cards, its row signature) or its home-screen row. Independent of the timestamps, which an unfollow
     * once cleared: a sport unfollowed before any event was stored is still cleaned up.
     */
    static boolean traces(Context c, String sport, List<SportEvent> stored) {
        if (!Sports.known(sport)) return false;
        SharedPreferences eng = c.getSharedPreferences(Sports.ENGINE, Context.MODE_PRIVATE);
        for (String k : new String[]{Sports.FORGET, "lastFull_", "lastCal_", "res_", "ok_"}) if (eng.contains(k + sport)) return true;
        if (stored != null) for (SportEvent e : stored) if (sport.equals(e.sport)) return true;
        List<String> side = Arrays.asList(SportEvent.F1.equals(sport) ? F1_FILES : UFC_FILES);
        String[] names = c.getFilesDir().list();
        for (int i = 0; names != null && i < names.length; i++)
            if (names[i].startsWith("cs" + sport + "_") || side.contains(names[i])) return true;
        for (String k : c.getSharedPreferences("cards", Context.MODE_PRIVATE).getAll().keySet())
            if (k.startsWith("sig_cs" + sport + "_") || k.startsWith("uri_cs" + sport + "_") || k.equals("row_sig_" + Sports.rowKey(sport)))
                return true;
        // its home-screen row is still listed: the pass then reports a change, so Engine draws and the rows'
        // housekeeping (HomeRows.removeUnfollowedSports) deletes it
        return c.getSharedPreferences("row", Context.MODE_PRIVATE).contains(Sports.rowKey(sport));
    }

    /**
     * A sport no longer followed: its card files (cs<sport>_*.png), its "cards" preferences (sig_/uri_ of those
     * cards, row_sig_sport_<sport>), its side files and its engine timestamps. Its row is HomeRows' (stream D).
     */
    static void forgetSport(Context c, String sport) {
        if (!Sports.known(sport)) return;
        List<String> side = Arrays.asList(SportEvent.F1.equals(sport) ? F1_FILES : UFC_FILES);
        File[] fs = c.getFilesDir().listFiles();
        for (int i = 0; fs != null && i < fs.length; i++) {
            String n = fs[i].getName();
            if (n.startsWith("cs" + sport + "_") || side.contains(n)) fs[i].delete();
        }
        SharedPreferences cards = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = cards.edit();
        for (String k : cards.getAll().keySet())
            if (k.startsWith("sig_cs" + sport + "_") || k.startsWith("uri_cs" + sport + "_") || k.equals("row_sig_" + Sports.rowKey(sport)))
                ed.remove(k);
        ed.apply();
        Sports.clearTimestamps(c, sport);
    }

    /**
     * Store.writeAtomic, with the replace done by Files.move: an atomic rename over the old file on every platform
     * (File.renameTo does not replace an existing file everywhere, e.g. on the build machine's JVM).
     */
    static void writeAtomic(File f, String text) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static List<SportEvent> memo;
    private static String memoPath;
    private static long memoMod = -1, memoLen = -1;

    /** The stored events (copies: callers may change them). Empty when there is no file or it can't be read. */
    static synchronized List<SportEvent> load(Context c) {
        List<SportEvent> out = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), FILE);
            if (!f.exists()) { memo = null; return out; }
            long mod = f.lastModified(), len = f.length();
            if (memo == null || mod != memoMod || len != memoLen || !f.getPath().equals(memoPath)) {
                byte[] b = new byte[(int) len];
                try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
                memo = fromJson(new String(b, StandardCharsets.UTF_8));
                memoPath = f.getPath(); memoMod = mod; memoLen = len;
            }
            for (SportEvent e : memo) out.add(e.copy());
        } catch (Exception ignored) { }
        return out;
    }

    /** Writes every event atomically. */
    static synchronized void save(Context c, List<SportEvent> all) {
        try {
            String text = toJson(all, System.currentTimeMillis());
            File f = new File(c.getFilesDir(), FILE);
            writeAtomic(f, text);
            memo = fromJson(text);  // exactly what load() reads back
            memoPath = f.getPath(); memoMod = f.lastModified(); memoLen = f.length();
        } catch (Exception ignored) {
            memo = null;  // read the file again next time
        }
    }

    // ---------- JSON ----------
    static String toJson(List<SportEvent> all, long savedAt) throws Exception {
        JSONArray a = new JSONArray();
        for (SportEvent e : all) a.put(json(e));
        return new JSONObject().put("v", VERSION).put("savedAt", savedAt).put("events", a).toString();
    }

    /** The events of a file's text; a broken event is skipped, a broken file (or another version) gives none. */
    static List<SportEvent> fromJson(String text) {
        List<SportEvent> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(text);
            if (root.optInt("v") != VERSION) return out;
            JSONArray a = root.optJSONArray("events");
            for (int i = 0; a != null && i < a.length(); i++) {
                try {
                    SportEvent e = event(a.getJSONObject(i));
                    if (e != null) out.add(e);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    static JSONObject json(SportEvent e) throws Exception {
        JSONObject o = new JSONObject();
        o.put("sport", e.sport).put("id", e.id).put("name", e.name).put("shortName", e.shortName).put("start", e.start)
            .put("end", e.end).put("state", e.state).put("fetchedAt", e.fetchedAt).put("misses", e.misses);
        if (!blank(e.circuitId) || !blank(e.circuitName) || !blank(e.city) || !blank(e.country) || e.laps > 0 || e.lengthKm > 0)
            o.put("circuit", new JSONObject().put("id", e.circuitId).put("name", e.circuitName).put("city", e.city)
                .put("country", e.country).put("laps", e.laps).put("lengthKm", (double) e.lengthKm));
        if (SportEvent.UFC.equals(e.sport) || e.numbered || !blank(e.venue))
            o.put("numbered", e.numbered).put("number", e.number).put("venue", e.venue);
        JSONArray ss = new JSONArray();
        for (SportEvent.Session s : e.sessions) ss.put(json(s));
        o.put("sessions", ss);
        return o;
    }

    static JSONObject json(SportEvent.Session s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", s.id).put("kind", s.kind).put("abbr", s.typeAbbr).put("start", s.start).put("tbd", s.tbd).put("state", s.state)
            .put("detail", s.detail).put("status", s.statusName).put("lap", s.lap).put("laps", s.laps).put("seenLiveAt", s.seenLiveAt);
        JSONArray order = new JSONArray();
        for (SportEvent.Entrant x : s.order) order.put(json(x));
        o.put("order", order);
        if (!s.bouts.isEmpty()) {
            JSONArray bouts = new JSONArray();
            for (SportEvent.Bout b : s.bouts) bouts.put(json(b));
            o.put("bouts", bouts);
        }
        SportEvent.SessionWatch w = s.watch == null ? new SportEvent.SessionWatch() : s.watch;
        JSONObject wo = new JSONObject().put("channel", w.channel).put("by", w.listedBy).put("no", w.no).put("sure", w.sure)
            .put("known", w.known);
        if (w.app != null) wo.put("app", w.app);
        if (w.logo != null) wo.put("logo", w.logo);
        o.put("watch", wo);
        return o;
    }

    static JSONObject json(SportEvent.Entrant x) throws Exception {
        JSONObject o = new JSONObject().put("id", x.id).put("code", x.code).put("name", x.name).put("short", x.shortName)
            .put("team", x.team).put("teamId", x.teamId).put("color", hex(x.color)).put("flag", x.flag).put("pos", x.pos)
            .put("winner", x.winner).put("gap", x.gap);
        if (x.points != 0) o.put("pts", (double) x.points);
        if (x.number != null && !x.number.isEmpty()) o.put("no", x.number);
        return o;
    }

    static JSONObject json(SportEvent.Bout b) throws Exception {
        JSONObject o = new JSONObject().put("id", b.id).put("order", b.order).put("weight", b.weightClass).put("note", b.note)
            .put("title", b.title).put("main", b.main).put("rounds", b.rounds).put("state", b.state).put("detail", b.detail)
            .put("round", b.round).put("clock", b.clock).put("outcome", b.outcome).put("method", b.method)
            .put("methodDetail", b.methodDetail);
        if (b.a != null) o.put("a", json(b.a));
        if (b.b != null) o.put("b", json(b.b));
        return o;
    }

    static JSONObject json(SportEvent.Fighter f) throws Exception {
        return new JSONObject().put("id", f.id).put("name", f.name).put("short", f.shortName).put("record", f.record)
            .put("recordBefore", f.recordBefore).put("flag", f.flag).put("country", f.country).put("headshot", f.headshot)
            .put("left", f.stanceLeft).put("right", f.stanceRight);
    }

    /** An event from its JSON; null when it has no sport or id. */
    static SportEvent event(JSONObject o) {
        SportEvent e = new SportEvent();
        e.sport = o.optString("sport"); e.id = o.optString("id");
        if (!Sports.known(e.sport) || e.id.isEmpty()) return null;
        e.name = o.optString("name"); e.shortName = o.optString("shortName"); e.start = o.optLong("start"); e.end = o.optLong("end");
        e.state = o.optString("state", "pre"); e.fetchedAt = o.optLong("fetchedAt"); e.misses = o.optInt("misses");
        JSONObject ci = o.optJSONObject("circuit");
        if (ci != null) {
            e.circuitId = ci.optString("id"); e.circuitName = ci.optString("name"); e.city = ci.optString("city");
            e.country = ci.optString("country"); e.laps = ci.optInt("laps"); e.lengthKm = (float) ci.optDouble("lengthKm", 0);
        }
        e.numbered = o.optBoolean("numbered"); e.number = o.optInt("number"); e.venue = o.optString("venue");
        JSONArray ss = o.optJSONArray("sessions");
        for (int i = 0; ss != null && i < ss.length(); i++) {
            JSONObject so = ss.optJSONObject(i);
            if (so == null || so.optString("id").isEmpty()) continue;
            e.sessions.add(session(so));
        }
        return e;
    }

    static SportEvent.Session session(JSONObject o) {
        SportEvent.Session s = new SportEvent.Session();
        s.id = o.optString("id"); s.kind = o.optString("kind"); s.typeAbbr = o.optString("abbr"); s.start = o.optLong("start");
        s.tbd = o.optBoolean("tbd"); s.state = o.optString("state", "pre"); s.detail = o.optString("detail");
        s.statusName = o.optString("status"); s.lap = o.optInt("lap"); s.laps = o.optInt("laps"); s.seenLiveAt = o.optLong("seenLiveAt");
        JSONArray order = o.optJSONArray("order");
        for (int i = 0; order != null && i < order.length(); i++) { JSONObject x = order.optJSONObject(i); if (x != null) s.order.add(entrant(x)); }
        JSONArray bouts = o.optJSONArray("bouts");
        for (int i = 0; bouts != null && i < bouts.length(); i++) { JSONObject x = bouts.optJSONObject(i); if (x != null) s.bouts.add(bout(x)); }
        JSONObject w = o.optJSONObject("watch");
        if (w != null) {
            s.watch.channel = w.optString("channel"); s.watch.listedBy = w.optString("by");
            s.watch.app = w.isNull("app") ? null : w.optString("app"); s.watch.logo = w.isNull("logo") ? null : w.optString("logo");
            s.watch.no = w.optInt("no"); s.watch.sure = w.optBoolean("sure"); s.watch.known = w.optBoolean("known");
        }
        return s;
    }

    static SportEvent.Entrant entrant(JSONObject o) {
        SportEvent.Entrant x = new SportEvent.Entrant();
        x.id = o.optString("id"); x.code = o.optString("code"); x.name = o.optString("name"); x.shortName = o.optString("short");
        x.team = o.optString("team"); x.teamId = o.optString("teamId"); x.flag = o.optString("flag"); x.gap = o.optString("gap");
        x.color = color(o.opt("color")); x.pos = o.optInt("pos"); x.winner = o.optBoolean("winner");
        x.points = (float) o.optDouble("pts", 0);
        x.number = o.optString("no", "");
        return x;
    }

    static SportEvent.Bout bout(JSONObject o) {
        SportEvent.Bout b = new SportEvent.Bout();
        b.id = o.optString("id"); b.order = o.optInt("order"); b.weightClass = o.optString("weight"); b.note = o.optString("note");
        b.title = o.optBoolean("title"); b.main = o.optBoolean("main"); b.rounds = o.optInt("rounds"); b.state = o.optString("state", "pre");
        b.detail = o.optString("detail"); b.round = o.optInt("round"); b.clock = o.optString("clock"); b.outcome = o.optInt("outcome");
        b.method = o.optString("method"); b.methodDetail = o.optString("methodDetail");
        JSONObject a = o.optJSONObject("a"), bb = o.optJSONObject("b");
        b.a = a == null ? null : fighter(a);
        b.b = bb == null ? null : fighter(bb);
        return b;
    }

    static SportEvent.Fighter fighter(JSONObject o) {
        SportEvent.Fighter f = new SportEvent.Fighter();
        f.id = o.optString("id"); f.name = o.optString("name"); f.shortName = o.optString("short"); f.record = o.optString("record");
        f.recordBefore = o.optString("recordBefore"); f.flag = o.optString("flag"); f.country = o.optString("country");
        f.headshot = o.optString("headshot"); f.stanceLeft = o.optString("left"); f.stanceRight = o.optString("right");
        return f;
    }

    /** A colour as ESPN writes it ("FF8000"); "" for none. */
    static String hex(int color) { return color == 0 ? "" : String.format(java.util.Locale.ROOT, "%06X", color & 0xFFFFFF); }

    /** "FF8000" / "#FF8000" -> opaque colour; a number as it is; 0 for none or junk. */
    static int color(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (!(v instanceof String)) return 0;
        String s = ((String) v).trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return 0;
        try { return 0xFF000000 | Integer.parseInt(s, 16); } catch (NumberFormatException x) { return 0; }
    }

    static boolean blank(String s) { return s == null || s.isEmpty(); }
}
