package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SofaScore: team crests for every club (both sports) and the Greek TV channel of each match. Used where the other
 * sources have nothing: crests the feeds leave empty, and channels the Magenta and Sport24 guides do not list yet.
 * Requests are few and cached (team ids for good, the line-up for a week, a club's channels for 3 hours).
 */
final class Sofa {
    private static final String API = "https://www.sofascore.com/api/v1/";
    static final long TTL = 3 * 3600000L;

    static String crestUrl(long teamId) { return "https://img.sofascore.com/api/v1/team/" + teamId + "/image"; }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sofa", Context.MODE_PRIVATE); }

    /**
     * SofaScore team id for a name and sport ("Football" / "Basketball"), preferring Greek teams whose name starts the
     * same way. 0 = searched, none; -1 = no answer (try again later). Cached for good once found.
     */
    static long teamId(Context c, String name, String sport) {
        String q = Crests.query(name);
        if (q.isEmpty() || q.matches(".*[\\u0370-\\u03FF].*")) return 0;
        String key = sport + "|" + Badges.key(q);
        SharedPreferences sp = prefs(c);
        long cached = sp.getLong(key, -1);
        if (cached > 0) return cached;
        if (cached == 0 && System.currentTimeMillis() - sp.getLong(key + "|at", 0) < 30L * 86400000L) return 0;
        String first = Badges.key(q).split(" ")[0];
        try {
            JSONArray rs = new JSONObject(Net.http(API + "search/all?q=" + URLEncoder.encode(q, "UTF-8"))).optJSONArray("results");
            long best = 0;
            for (int i = 0; rs != null && i < rs.length(); i++) {
                JSONObject r = rs.getJSONObject(i), e = r.optJSONObject("entity");
                if (!"team".equals(r.optString("type")) || e == null) continue;
                JSONObject s = e.optJSONObject("sport"), co = e.optJSONObject("country");
                if (s == null || !sport.equalsIgnoreCase(s.optString("name"))) continue;
                if (e.optBoolean("national") || !wordStarts(Badges.key(e.optString("name")), first)) continue;
                if (co != null && "Greece".equals(co.optString("name"))) { best = e.optLong("id"); break; }
                if (best == 0) best = e.optLong("id");
            }
            sp.edit().putLong(key, best).putLong(key + "|at", System.currentTimeMillis()).apply();
            return best;
        } catch (Exception e) {
            return -1;
        }
    }

    /** A word of the name starts with the first word of ours ("ASK KARDITSAS BC" fits "KARDITSA"). */
    static boolean wordStarts(String name, String first) {
        for (String w : name.split(" ")) if (w.startsWith(first)) return true;
        return false;
    }

    /** Greek channel names by SofaScore channel id (refreshed weekly). */
    private static Map<Long, String> greekChannels(Context c) {
        File f = new File(c.getFilesDir(), "sofa_gr_channels.json");
        Map<Long, String> out = new HashMap<>();
        try {
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86400000L) {
                String text = Net.http(API + "tv/country/GR/channels");
                new JSONObject(text).getJSONArray("channels");  // valid before it is saved
                Store.writeAtomic(f, text);
            }
            JSONArray a = new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8")).getJSONArray("channels");
            for (int i = 0; i < a.length(); i++) out.put(a.getJSONObject(i).optLong("id"), a.getJSONObject(i).optString("name"));
        } catch (Exception ignored) { }
        return out;
    }

    /** SofaScore's channel name in the guides' spelling, so the logo and the right app are found. */
    static String display(String sofaName) {
        String n = Channels.norm(sofaName);
        java.util.regex.Matcher mg = java.util.regex.Pattern.compile("MAGENTA([0-9])").matcher(n);
        if (mg.matches()) return "Magenta Sport " + mg.group(1);
        switch (n) {
            case "EPT1": return "ΕΡΤ1";
            case "EPT2": return "ΕΡΤ2 ΣΠΟΡ";
            case "EPT3": return "ΕΡΤ3";
            case "SKAITV": case "SKAITVHYBRID": return "ΣΚΑΪ";
            case "MEGATV": return "MEGA";
            case "OPENHYBRID": return "OPEN BEYOND";
            case "SPORTFMTV": return "ΣΠΟΡ FM TV";
            default: return sofaName.replace(" | ", " ").replace("MagentaSport", "Magenta Sport").trim();
        }
    }

    /**
     * Greek channels of a club's matches in the next week, as guide airings (title "Home – Away", English names).
     * Cached per club for TTL; nothing is fetched when the club has no match soon.
     */
    static List<Guide.Airing> airings(Context c, Teams.Club k, List<Match> known, boolean basket) {
        if (!BuildConfig.GUIDES) return new ArrayList<>();
        String file = "guide_sofa_" + k.id + ".json";
        long[] at = {0};
        List<Guide.Airing> cached = Guide.load(c, file, at);
        long now = System.currentTimeMillis();
        if (now - at[0] < TTL) return cached;
        boolean soon = false;
        for (Match m : known)
            if (k.id.equals(m.club) && "pre".equals(m.state) && m.start > now && m.start < now + 7 * 86400000L) soon = true;
        if (!soon) return cached;
        List<Guide.Airing> out = new ArrayList<>();
        boolean answered = false;
        Map<Long, String> names = null;
        for (String sport : basket && k.hasBasket ? new String[]{"Football", "Basketball"} : new String[]{"Football"}) {
            long id = teamId(c, k.name, sport);
            if (id <= 0) continue;
            try {
                JSONArray ev = new JSONObject(Net.http(API + "team/" + id + "/events/next/0")).optJSONArray("events");
                answered = true;
                for (int i = 0; ev != null && i < ev.length(); i++) {
                    JSONObject e = ev.getJSONObject(i);
                    long start = e.optLong("startTimestamp") * 1000L;
                    if (start < now - 3 * 3600000L || start > now + 7 * 86400000L) continue;
                    JSONObject cc = new JSONObject(Net.http(API + "tv/event/" + e.optLong("id") + "/country-channels")).optJSONObject("countryChannels");
                    JSONArray gr = cc == null ? null : cc.optJSONArray("GR");
                    if (gr == null || gr.length() == 0) continue;
                    if (names == null) names = greekChannels(c);
                    List<String> chans = new ArrayList<>();
                    for (int j = 0; j < gr.length() && chans.size() < 2; j++) {
                        String nm = names.get(gr.optLong(j));
                        if (nm != null && !chans.contains(display(nm))) chans.add(display(nm));
                    }
                    if (chans.isEmpty()) continue;
                    Guide.Airing a = new Guide.Airing();
                    a.id = "sofa:" + e.optLong("id");
                    a.start = start;
                    a.title = e.getJSONObject("homeTeam").optString("name") + " – " + e.getJSONObject("awayTeam").optString("name");
                    a.channel = android.text.TextUtils.join(" · ", chans);
                    a.genre = sport;
                    a.tag = "";
                    a.live = true;
                    out.add(a);
                }
            } catch (Exception ignored) { }
        }
        if (!answered) return cached;
        Guide.save(c, file, out, now);
        return out;
    }

    /** The airing of this club's match (same sport, kick-off within 2 hours, or the same day when the time is TBA). */
    static Guide.Airing find(List<Guide.Airing> air, Match m) {
        String genre = "FC".equals(m.team) ? "Football" : "Basketball";
        for (Guide.Airing a : air) {
            if (!genre.equals(a.genre)) continue;
            if (m.tbd ? Store.sameAthensDay(a.start, m.start) : Math.abs(a.start - m.start) <= 2 * 3600000L) return a;
        }
        return null;
    }
}
