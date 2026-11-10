package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The single source of truth for matches of all followed clubs, saved on disk.
 * Merge rules (per club): a source that failed keeps its last good matches; a live or finished match is never pushed
 * back to "upcoming" by a lagging feed; a match being played is kept even if a feed stops listing it.
 */
final class Store {
    static final long STALE = 6 * 3600000L;

    /**
     * Merges one club's fetch results into the list (pure: no file access; the caller loads once and saves once).
     * Source timestamps go to the editor.
     */
    static List<Match> mergeInto(List<Match> everything, String club, List<Sources.Result> results, List<Match> discovered,
                                 SharedPreferences.Editor ed) {
        long now = System.currentTimeMillis();
        Map<String, Match> known = new LinkedHashMap<>();
        List<Match> others = new ArrayList<>();
        for (Match m : everything) { if (club.equals(m.club)) known.put(m.key, m); else others.add(m); }
        Map<String, Match> out = new LinkedHashMap<>();
        List<String> handled = new ArrayList<>();
        for (Sources.Result r : results) {
            handled.add(r.source);
            if (r.ok) ed.putLong("ok_" + r.source + "_" + club, now);
            Map<String, Match> fresh = new LinkedHashMap<>();
            for (Match m : r.list) fresh.put(m.key, m);
            for (Match old : known.values()) {
                if (!r.source.equals(old.source) || fresh.containsKey(old.key)) continue;
                boolean playing = ("in".equals(old.state) || (!Engine.liveSource(old) && old.start <= now + 20 * 60000L))
                    && now < old.start + 6 * 3600000L;
                if (!r.ok || playing) fresh.put(old.key, old);
            }
            for (Match m : fresh.values()) out.put(m.key, keepProgress(known.get(m.key), m, now));
        }
        if (discovered != null) {
            handled.add("s24");
            for (Match m : discovered) out.put(m.key, keepProgress(known.get(m.key), m, now));
            for (Match old : known.values())
                if ("s24".equals(old.source) && !out.containsKey(old.key) && old.start > now - 2 * 86400000L) out.put(old.key, old);
        }
        for (Match old : known.values()) if (!handled.contains(old.source) && !out.containsKey(old.key)) out.put(old.key, old);

        List<Match> list = new ArrayList<>(out.values()), result = new ArrayList<>(others), kept = new ArrayList<>();
        for (Match m : list) {
            if ("s24".equals(m.source)) {  // a guide-found game is dropped once a real feed lists it (or another listing of it)
                boolean dup = false;
                for (Match o : list) if (!"s24".equals(o.source) && o.team.equals(m.team)
                    && Math.abs(o.start - m.start) < (o.tbd ? 20 : 3) * 3600000L) { dup = true; break; }  // a TBD time is only a placeholder
                for (Match o : kept) if ("s24".equals(o.source) && o.team.equals(m.team) && Math.abs(o.start - m.start) < 3 * 3600000L) { dup = true; break; }
                if (dup) continue;
            }
            if (m.start < now - 120L * 86400000L) continue;
            kept.add(m);
        }
        result.addAll(kept);
        return result;
    }

    /** Files and settings of a club that is no longer followed (its row is handled by HomeRows). */
    static void forgetClub(Context c, String club) {
        File[] fs = c.getFilesDir().listFiles();
        for (int i = 0; fs != null && i < fs.length; i++) {
            String n = fs[i].getName();
            if (n.startsWith("c" + club + "_") || n.equals("guide_magenta_" + club + ".json")) fs[i].delete();
        }
        SharedPreferences cards = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = cards.edit();
        for (String k : cards.getAll().keySet())
            if (k.startsWith("sig_c" + club + "_") || k.startsWith("uri_c" + club + "_") || k.equals("row_sig_" + club)) ed.remove(k);
        ed.apply();
        SharedPreferences st = c.getSharedPreferences("store", Context.MODE_PRIVATE);
        SharedPreferences.Editor se = st.edit();
        for (String k : st.getAll().keySet()) if (k.endsWith("_" + club)) se.remove(k);
        se.apply();
    }

    /** Never move a match backwards because one feed lags behind; keep a recent live score from the poller. */
    static Match keepProgress(Match old, Match fresh, long now) {
        if (old == null) return fresh;
        boolean freshBehind = "pre".equals(fresh.state) || ("in".equals(fresh.state) && "post".equals(old.state));
        if ("in".equals(old.state) && "pre".equals(fresh.state) && now < old.start + 6 * 3600000L) return carry(old, fresh);
        if ("post".equals(old.state) && freshBehind && now < old.start + 24 * 3600000L) return carry(old, fresh);
        if ("in".equals(old.state) && "in".equals(fresh.state) && old.seenLiveAt > fresh.seenLiveAt
            && now - old.seenLiveAt < 10 * 60000L) return carry(old, fresh);
        // TheSportsDB often has no tip-off time (a midnight placeholder): keep the time and channel a TV guide confirmed
        if ("tsdb".equals(fresh.source) && old.channelSure && !old.tbd && "pre".equals(fresh.state)
            && (fresh.tbd ? sameAthensDay(old.start, fresh.start) : Math.abs(old.start - fresh.start) <= 90 * 60000L)) {
            fresh.start = old.start; fresh.tbd = false;
            fresh.channel = old.channel; fresh.channelNo = old.channelNo; fresh.channelLogo = old.channelLogo;
            fresh.channelApp = old.channelApp; fresh.channelSure = true; fresh.channelKnown = true;
        }
        if (old.seenLiveAt > fresh.seenLiveAt) fresh.seenLiveAt = old.seenLiveAt;
        return fresh;
    }

    static boolean sameAthensDay(long a, long b) {
        java.text.SimpleDateFormat d = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        d.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Athens"));
        return d.format(new java.util.Date(a)).equals(d.format(new java.util.Date(b)));
    }

    private static Match carry(Match old, Match fresh) {
        Match m = old.copy();
        m.homeCrest = fresh.homeCrest; m.awayCrest = fresh.awayCrest; m.comp = fresh.comp;
        return m;
    }

    static synchronized List<Match> put(Context c, Match m) {
        List<Match> all = load(c);
        for (int i = 0; i < all.size(); i++) if (all.get(i).key.equals(m.key)) { all.set(i, m); save(c, all); return all; }
        return all;  // removed meanwhile by a refresh: do not resurrect
    }

    /** Drops everything of clubs that are no longer followed. */
    static synchronized void retain(Context c, java.util.Set<String> clubs) {
        List<Match> all = load(c), keep = new ArrayList<>();
        for (Match m : all) if (clubs.contains(m.club)) keep.add(m);
        if (keep.size() != all.size()) save(c, keep);
    }

    static long lastOk(Context c, String source, String club) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE).getLong("ok_" + source + "_" + club, 0);
    }

    static long lastUpdate(Context c, String club, String team) {
        return "FC".equals(team) ? lastOk(c, "espn", club)
            : Math.max(Math.max(lastOk(c, "el", club), lastOk(c, "ec", club)), lastOk(c, "tsdb", club));
    }

    /** True when the club's main feed for this sport has not answered for 6 hours. */
    static boolean stale(Context c, String club, String team) {
        long ok = lastUpdate(c, club, team);
        return ok > 0 && System.currentTimeMillis() - ok > STALE;
    }

    // ---------- files ----------
    static void writeAtomic(File f, String text) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new Exception("rename failed");
    }

    static synchronized void save(Context c, List<Match> all) {
        try {
            JSONArray a = new JSONArray();
            for (Match m : all) {
                JSONObject o = new JSONObject();
                o.put("club", m.club); o.put("team", m.team); o.put("source", m.source); o.put("key", m.key);
                o.put("comp", m.comp); o.put("league", m.league); o.put("home", m.home); o.put("away", m.away);
                o.put("clubIsHome", m.clubIsHome); o.put("hs", m.hs); o.put("as", m.as); o.put("state", m.state);
                o.put("detail", m.detail); o.put("start", m.start); o.put("tbd", m.tbd); o.put("quarter", m.quarter);
                o.put("clubWon", m.clubWon); o.put("pens", m.pens); o.put("homeCrest", m.homeCrest); o.put("awayCrest", m.awayCrest);
                o.put("channel", m.channel); o.put("channelApp", m.channelApp); o.put("channelNo", m.channelNo);
                o.put("channelLogo", m.channelLogo); o.put("channelSure", m.channelSure); o.put("channelKnown", m.channelKnown);
                o.put("seenLiveAt", m.seenLiveAt);
                a.put(o);
            }
            writeAtomic(new File(c.getFilesDir(), "matches4.json"), a.toString());  // v4 format (v3 file is ignored)
        } catch (Exception ignored) { }
    }

    static synchronized List<Match> load(Context c) {
        List<Match> out = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), "matches4.json");
            if (!f.exists()) return out;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
            JSONArray a = new JSONArray(new String(b, StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                try {
                    JSONObject o = a.getJSONObject(i);
                    Match m = new Match();
                    m.club = o.optString("club"); m.team = o.optString("team"); m.source = o.optString("source");
                    m.key = o.optString("key"); m.comp = o.optString("comp"); m.league = o.optString("league");
                    m.home = o.optString("home"); m.away = o.optString("away"); m.clubIsHome = o.optBoolean("clubIsHome");
                    m.hs = o.isNull("hs") ? null : o.optString("hs", null); m.as = o.isNull("as") ? null : o.optString("as", null);
                    m.state = o.optString("state", "pre"); m.detail = o.optString("detail"); m.start = o.optLong("start");
                    m.tbd = o.optBoolean("tbd"); m.quarter = o.optInt("quarter"); m.clubWon = o.optInt("clubWon", 9);
                    m.pens = o.isNull("pens") ? null : o.optString("pens", null);
                    m.homeCrest = o.optString("homeCrest"); m.awayCrest = o.optString("awayCrest");
                    m.channel = o.optString("channel"); m.channelApp = o.optString("channelApp", Cards.MAGENTA);
                    m.channelNo = o.optInt("channelNo"); m.channelLogo = o.isNull("channelLogo") ? null : o.optString("channelLogo", null);
                    m.channelSure = o.optBoolean("channelSure"); m.channelKnown = o.optBoolean("channelKnown", true);
                    m.seenLiveAt = o.optLong("seenLiveAt");
                    if (!m.key.isEmpty() && !m.club.isEmpty() && m.start > 0) out.add(m);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }
}
