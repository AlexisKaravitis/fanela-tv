package app.fanela.tv;

import android.content.Context;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Ronin Sport API (roninsport.io): per-match TV and streaming channels by country. A stub until the trial key arrives:
 * it stays off unless RONIN_KEY is set (keystore.properties "roninKey", which is not in git).
 *
 * What Ronin publishes (https://roninsport.io/api-feeds, read 7 Nov 2026): a JSON API with /fixtures, /teams,
 * /channels, /sport and /leagues; a fixture has its teams, competition, kick-off (UTC) and channels. The base URL, how
 * the key is sent and the field names are given only once access is agreed, so everything assumed here sits in
 * BASE, KEY_HEADER and parse(): check them against the documentation that comes with the key.
 *
 * For a public release the key belongs on Fanela's own server, not in the APK.
 */
final class RoninProvider implements Watch {
    /** Assumed until the documentation arrives. */
    static final String BASE = "https://api.roninsport.io", KEY_HEADER = "X-API-Key";
    static final long TTL = 3 * 3600000L;
    static final int DAYS = 7;

    static final class Listing {
        String id, home, away, league;
        long start;
        final List<String> channels = new ArrayList<>(), logos = new ArrayList<>();
    }

    private final String country;
    private List<Listing> listings = new ArrayList<>();

    RoninProvider(String country) { this.country = country; }

    static boolean enabled() { return !BuildConfig.RONIN_KEY.isEmpty(); }

    public String id() { return "ronin"; }

    public boolean guess(Match m) { return false; }  // nothing on the TV: listings arrive in prepare()

    /** The country's fixtures with channels for the next week: one request per day, shared by every club, cached 3 h. */
    public void prepare(Context c) {
        if (!enabled()) return;
        File f = new File(c.getFilesDir(), "guide_ronin_" + country + ".json");
        try {
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < TTL) {
                listings = parse(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"), country);
                return;
            }
            SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            day.setTimeZone(TimeZone.getTimeZone("UTC"));
            JSONArray all = new JSONArray();
            long now = System.currentTimeMillis();
            for (int d = 0; d < DAYS; d++) {
                String body = Net.http(BASE + "/fixtures?date=" + day.format(new java.util.Date(now + d * 86400000L))
                    + "&country=" + country, KEY_HEADER, BuildConfig.RONIN_KEY);
                JSONArray a = fixtures(body);
                for (int i = 0; i < a.length(); i++) all.put(a.get(i));
            }
            String text = new JSONObject().put("data", all).toString();
            Store.writeAtomic(f, text);
            listings = parse(text, country);
        } catch (Exception e) {  // keep the last good listings
            try { if (f.exists()) listings = parse(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"), country); }
            catch (Exception ignored) { }
        }
    }

    private static JSONArray fixtures(String body) throws Exception {
        String t = body.trim();
        if (t.startsWith("[")) return new JSONArray(t);
        JSONObject o = new JSONObject(t);
        for (String k : new String[]{"data", "fixtures", "results", "items"}) if (o.optJSONArray(k) != null) return o.getJSONArray(k);
        return new JSONArray();
    }

    /**
     * Assumed fixture shape (adjust to the real field names): {id, kickoff|start|date (UTC ISO), home:{name}|homeTeam,
     * away:{name}|awayTeam, league:{name}|competition, channels:[{name, country, logo}]}. Channels of other countries
     * are dropped.
     */
    static List<Listing> parse(String body, String country) throws Exception {
        List<Listing> out = new ArrayList<>();
        JSONArray a = fixtures(body);
        for (int i = 0; i < a.length(); i++) {
            JSONObject f = a.optJSONObject(i);
            if (f == null) continue;
            Listing l = new Listing();
            l.id = f.optString("id");
            l.start = Sources.iso(first(f, "kickoff", "start", "startTime", "date"));
            l.home = name(f, "home", "homeTeam");
            l.away = name(f, "away", "awayTeam");
            l.league = name(f, "league", "competition");
            JSONArray chs = f.optJSONArray("channels");
            if (chs == null) chs = f.optJSONArray("broadcasts");
            for (int j = 0; chs != null && j < chs.length(); j++) {
                JSONObject ch = chs.optJSONObject(j);
                if (ch == null) continue;
                String cc = first(ch, "country", "countryCode");
                if (!cc.isEmpty() && !cc.equalsIgnoreCase(country)) continue;
                String n = first(ch, "name", "title");
                if (n.isEmpty() || l.channels.contains(n)) continue;
                l.channels.add(n);
                l.logos.add(first(ch, "logo", "logoUrl", "image"));
            }
            if (l.start > 0 && !l.home.isEmpty() && !l.away.isEmpty() && !l.channels.isEmpty()) out.add(l);
        }
        return out;
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k, ""); if (!v.isEmpty() && !"null".equals(v)) return v; }
        return "";
    }

    private static String name(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof JSONObject) { String n = first((JSONObject) v, "name", "title"); if (!n.isEmpty()) return n; }
            else if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        }
        return "";
    }

    /** The listing of a match: kick-off within 2 hours and both teams' names match (accent-free, first word). */
    static Listing find(List<Listing> ls, Match m) {
        for (Listing l : ls) {
            if (Math.abs(l.start - m.start) > 2 * 3600000L) continue;
            if (same(l.home, m.home) && same(l.away, m.away)) return l;
        }
        return null;
    }

    /** Same team: equal, or one name inside the other, once club-type words are dropped ("Arsenal FC" = "Arsenal"). */
    static boolean same(String a, String b) {
        String x = plain(a), y = plain(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        return x.equals(y) || (" " + x + " ").contains(" " + y + " ") || (" " + y + " ").contains(" " + x + " ");
    }

    private static String plain(String s) {
        String k = " " + Cards.key(s).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", "  ") + " ";
        return k.replaceAll(" (FC|CF|AFC|SC|AC|CD|SV|FK|SK|BC|CLUB) ", " ").replaceAll("\\s+", " ").trim();
    }

    public Object fetch(Context c, Teams.Club k, List<Match> known, boolean basket) { return null; }

    /** Confirms the channel of the club's upcoming matches that Ronin lists. */
    public void apply(List<Match> all, Teams.Club k, Object ignored) {
        for (Match m : all) {
            if (!k.id.equals(m.club) || !("pre".equals(m.state) || "in".equals(m.state))) continue;
            Listing l = find(listings, m);
            if (l == null) continue;
            m.channel = String.join(" · ", l.channels);
            m.channelLogo = l.logos.contains("") ? null : String.join(" · ", l.logos);
            m.channelKnown = true; m.channelSure = true; m.channelNo = 0; m.channelApp = null;
            m.listedBy = "ronin";
        }
    }
}
