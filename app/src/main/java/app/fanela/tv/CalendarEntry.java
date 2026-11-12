package app.fanela.tv;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One event in a sport's calendar listing (ESPN leagues[0].calendar[]): ids and labels only. The approximate times
 * choose which date to query and order the listing; they are never shown or stored as a time (the F1 calendar is
 * about 3 h off). Stream B owns this file.
 *
 * Also the calendar helpers both feeds share: parsing the listing, its cache file (f1_calendar.json /
 * ufc_calendar.json) and the choice of the events a refresh asks for in detail.
 */
final class CalendarEntry {
    String id, label;
    long approxStart, approxEnd;  // approx: never shown as a time

    static final long H = 3600000L, DAY = 24 * H;
    /** A finished event is asked for again for this long after its listed end (penalties, overturned results). */
    static final long RECENT = 3 * DAY;
    private static final Pattern EVENT_ID = Pattern.compile("events/(\\d+)");

    // ---------- parsing (pure) ----------
    /**
     * The calendar of a no-date scoreboard: ids from event.$ref ("…/events/600057449?…"), labels as given, sorted by
     * the listed start. dropContender: Dana White's Contender Series entries are left out (UFC). Entries with no id
     * are skipped.
     */
    static List<CalendarEntry> parse(JSONObject board, boolean dropContender) {
        List<CalendarEntry> out = new ArrayList<>();
        if (board == null) return out;
        JSONArray leagues = board.optJSONArray("leagues");
        JSONObject league = leagues == null ? null : leagues.optJSONObject(0);
        JSONArray cal = league == null ? null : league.optJSONArray("calendar");
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; cal != null && i < cal.length(); i++) {
            JSONObject o = cal.optJSONObject(i);
            if (o == null) continue;
            JSONObject ev = o.optJSONObject("event");
            String ref = ev == null ? "" : ev.optString("$ref", "");
            Matcher m = EVENT_ID.matcher(ref);
            if (!m.find()) continue;
            CalendarEntry e = new CalendarEntry();
            e.id = m.group(1);
            e.label = o.optString("label", "").trim();
            if (dropContender && contender(e.label)) continue;
            if (!seen.add(e.id)) continue;
            e.approxStart = Sources.iso(o.optString("startDate", ""));
            e.approxEnd = Math.max(e.approxStart, Sources.iso(o.optString("endDate", "")));
            out.add(e);
        }
        sort(out);
        return out;
    }

    /** Sports.contender with the typographic apostrophe ESPN sometimes uses ("Dana White’s Contender Series"). */
    static boolean contender(String label) {
        return label != null && Sports.contender(label.replace('’', '\'').replace('‘', '\''));
    }

    static void sort(List<CalendarEntry> l) {
        Collections.sort(l, new Comparator<CalendarEntry>() {
            public int compare(CalendarEntry a, CalendarEntry b) { return Long.compare(a.approxStart, b.approxStart); }
        });
    }

    static Set<String> ids(List<CalendarEntry> l) {
        Set<String> out = new LinkedHashSet<>();
        if (l != null) for (CalendarEntry e : l) out.add(e.id);
        return out;
    }

    static CalendarEntry byId(List<CalendarEntry> l, String id) {
        if (l != null) for (CalendarEntry e : l) if (e.id.equals(id)) return e;
        return null;
    }

    // ---------- what a refresh asks for (pure) ----------
    /**
     * The ids a refresh wants in detail, in this order: the next `ahead` listed events still to come (or under way),
     * the newest finished one while it is recent (RECENT after its listed end) or not stored yet (the "latest" tile
     * after a first follow), then stored events of the sport the calendar no longer lists that are not over yet (so a
     * cancelled or removed event counts its misses and is dropped, EventStore.merge). Whether each is due is due().
     */
    static List<String> wanted(List<CalendarEntry> cal, List<SportEvent> known, long now, int ahead, String sport) {
        List<CalendarEntry> sorted = new ArrayList<>(cal == null ? new ArrayList<CalendarEntry>() : cal);
        sort(sorted);
        Set<String> out = new LinkedHashSet<>();
        CalendarEntry newestPast = null;
        int n = 0;
        for (CalendarEntry e : sorted) {
            if (e.approxEnd + 6 * H <= now) { newestPast = e; continue; }  // over (the listed end is up to 3 h late)
            if (n++ < ahead) out.add(e.id);
        }
        if (newestPast != null && (now - newestPast.approxEnd < RECENT || find(known, sport, newestPast.id) == null)) out.add(newestPast.id);
        Set<String> listed = ids(sorted);
        if (known != null)
            for (SportEvent k : known)
                if (sport.equals(k.sport) && k.id != null && !listed.contains(k.id) && EventStore.lastStart(k) + DAY > now) out.add(k.id);
        return new ArrayList<>(out);
    }

    /** A stored event is due for a detail fetch by its own cadence (SportEngine.interval over it alone); a new one is. */
    static boolean due(SportEvent known, String sport, long now) {
        if (known == null) return true;
        List<SportEvent> one = Collections.singletonList(known);
        return now - known.fetchedAt >= SportEngine.interval(sport, one, now, true) - 60000L;
    }

    static SportEvent find(List<SportEvent> known, String sport, String id) {
        if (known != null) for (SportEvent k : known) if (sport.equals(k.sport) && id.equals(k.id)) return k;
        return null;
    }

    /** yyyyMMdd of t in UTC. */
    static String utcDay(long t) { return day(t, "UTC"); }

    /**
     * yyyyMMdd of t in US Eastern time: the day ESPN files a scoreboard event under, for F1 as for UFC and the NBA
     * (probed 12 Nov 2026: the Las Vegas weekend, FP1 00:30Z 20 Nov to the race 04:00Z 22 Nov, is listed for
     * dates=20261119 to 20261121 and not for 20261122; Australia, 06-08 Mar UTC, for 0305 to 0307).
     */
    static String etDay(long t) { return day(t, "America/New_York"); }

    private static String day(long t, String zone) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return f.format(t);
    }

    /** An HTTP answer that says "not there" (404, 400): the request was answered, the item does not exist (yet). */
    static boolean notFound(Exception x) {
        String m = x.getMessage();
        return m != null && (m.startsWith("HTTP 404") || m.startsWith("HTTP 400") || m.startsWith("HTTP 410"));
    }

    // ---------- cache file ----------
    static String toJson(List<CalendarEntry> l, long at) throws Exception {
        JSONArray a = new JSONArray();
        for (CalendarEntry e : l)
            a.put(new JSONObject().put("id", e.id).put("label", e.label).put("start", e.approxStart).put("end", e.approxEnd));
        return new JSONObject().put("v", 1).put("at", at).put("entries", a).toString();
    }

    /** The entries of a cache file's text; null when it can't be read. */
    static List<CalendarEntry> fromJson(String text) {
        try {
            JSONObject o = new JSONObject(text);
            JSONArray a = o.optJSONArray("entries");
            if (a == null) return null;
            List<CalendarEntry> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null || x.optString("id", "").isEmpty()) continue;
                CalendarEntry e = new CalendarEntry();
                e.id = x.optString("id");
                e.label = x.optString("label", "");
                e.approxStart = x.optLong("start");
                e.approxEnd = x.optLong("end");
                out.add(e);
            }
            sort(out);
            return out;
        } catch (Exception x) {
            return null;
        }
    }

    /** The cached calendar (null when none). */
    static List<CalendarEntry> load(Context c, String file) {
        String t = read(new File(c.getFilesDir(), file));
        return t == null ? null : fromJson(t);
    }

    static void save(Context c, String file, List<CalendarEntry> l, long at) {
        try { EventStore.writeAtomic(new File(c.getFilesDir(), file), toJson(l, at)); } catch (Exception ignored) { }
    }

    /** A small side file's text, or null. */
    static String read(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception x) {
            return null;
        }
    }
}
