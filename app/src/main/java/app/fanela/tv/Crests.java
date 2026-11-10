package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Crests for teams whose feed gives none (Greek basketball clubs from the guides or TheSportsDB, lower-division cup
 * opponents). Looked up once by name on TheSportsDB, cached on the TV, and retried monthly when nothing was found.
 */
final class Crests {
    private static final String[] SUFFIX = {" B.C.", " BC", " FC", " F.C.", " BASKETBALL", " BASKET"};

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("crests", Context.MODE_PRIVATE); }

    /** Search name in English without club-type suffixes ("Maroussi BC" -> "Maroussi"). */
    static String query(String raw) {
        String s = I18n.enName(raw).trim();
        for (String x : SUFFIX) if (s.toUpperCase(java.util.Locale.ROOT).endsWith(x)) s = s.substring(0, s.length() - x.length()).trim();
        return s;
    }

    private static String key(String team, String raw) { return team + "|" + Badges.key(query(raw)); }

    /** Fills empty crests from the cache (no network). */
    static void fill(Context c, List<Match> all) {
        if (!BuildConfig.REMOTE_IMAGES) return;
        SharedPreferences sp = prefs(c);
        for (Match m : all) {
            if (m.homeCrest == null || m.homeCrest.isEmpty()) m.homeCrest = nz(sp.getString(key(m.team, m.home), ""));
            if (m.awayCrest == null || m.awayCrest.isEmpty()) m.awayCrest = nz(sp.getString(key(m.team, m.away), ""));
        }
    }

    private static String nz(String s) { return s == null || s.isEmpty() ? null : s; }

    /** Looks up a few missing crests of matches within a month (network, background thread). Returns true if any was found. */
    static boolean resolve(Context c, List<Match> all) {
        if (!BuildConfig.REMOTE_IMAGES) return false;
        app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        SharedPreferences sp = prefs(c);
        Map<String, String[]> todo = new LinkedHashMap<>();  // key -> {team, raw}
        for (Match m : all) {
            if (Math.abs(m.start - now) > 30L * 86400000L) continue;
            if (m.homeCrest == null || m.homeCrest.isEmpty()) todo.put(key(m.team, m.home), new String[]{m.team, m.home});
            if (m.awayCrest == null || m.awayCrest.isEmpty()) todo.put(key(m.team, m.away), new String[]{m.team, m.away});
        }
        boolean found = false;
        int budget = 8;  // a few per refresh: the rest follow on the next one
        for (Map.Entry<String, String[]> e : todo.entrySet()) {
            String k = e.getKey();
            if (sp.contains(k) && !sp.getString(k, "").isEmpty()) continue;
            if (now - sp.getLong(k + "|at", 0) < 30L * 86400000L) continue;  // not found recently
            if (budget-- <= 0) break;
            String url = search(e.getValue()[0], e.getValue()[1]);
            if (url == null) continue;  // no answer (offline, rate limit): ask again next time
            if (url.isEmpty()) {
                sp.edit().putLong(k + "|at", now).apply();  // answered, nothing found: retry in a month
            } else {
                sp.edit().putString(k, url).remove(k + "|at").apply();
                found = true;
            }
            try { Thread.sleep(800); } catch (InterruptedException x) { break; }
        }
        return found;
    }

    /** SofaScore first (every club, both sports), then TheSportsDB. "" = none found, null = no answer. */
    private static String search(String team, String raw) {
        long id = Sofa.teamId(app, raw, "BC".equals(team) ? "Basketball" : "Football");
        if (id > 0) return Sofa.crestUrl(id);
        String t = tsdb(team, raw);
        return t == null && id == -1 ? null : t;
    }

    private static volatile Context app;

    /** TheSportsDB team badge for a Greek (or any) team of this sport whose name starts like ours. */
    private static String tsdb(String team, String raw) {
        if (Net.blocked("www.thesportsdb.com")) return null;
        String q = query(raw);
        if (q.isEmpty() || q.matches(".*[\\u0370-\\u03FF].*")) return "";  // Greek-only names cannot be searched
        String sport = "BC".equals(team) ? "Basketball" : "Soccer";
        String first = Badges.key(q).split(" ")[0];
        try {
            JSONObject d = new JSONObject(Net.http("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=" + URLEncoder.encode(q, "UTF-8")));
            JSONArray ts = d.optJSONArray("teams");
            String best = null;
            for (int i = 0; ts != null && i < ts.length(); i++) {
                JSONObject t = ts.getJSONObject(i);
                String badge = t.optString("strBadge", "");
                if (!sport.equals(t.optString("strSport")) || !badge.startsWith("https://")) continue;
                if (!Sofa.wordStarts(Badges.key(t.optString("strTeam")), first)) continue;
                if ("Greece".equals(t.optString("strCountry"))) return badge;
                if (best == null) best = badge;
            }
            return best == null ? "" : best;
        } catch (Exception e) {
            return null;
        }
    }
}
