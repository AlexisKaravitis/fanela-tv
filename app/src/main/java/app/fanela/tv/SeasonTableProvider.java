package app.fanela.tv;

import android.content.Context;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The season table: who holds each competition's rights in each country this season. Two layers:
 *  res/raw/broadcasters.json            kept by hand from the leagues' and UEFA's own broadcaster pages;
 *  res/raw/broadcasters_wikipedia.json  from Wikipedia's lists (CC BY-SA 4.0, tools/wiki_broadcasters.py), used only
 *                                       where the first has no row. Credited on the Sources screen.
 * Both are checked every August and January. It only says "usually on": a match's own channel can differ, so it never
 * claims to be sure.
 */
final class SeasonTableProvider implements Watch {
    static final class Row {
        String country, comp, source;
        List<String> on = new ArrayList<>();
        long from, until;
        boolean wikipedia;  // from the CC BY-SA layer
    }

    private static volatile Map<String, Row> table;  // "GB|eng.1" -> row
    private static volatile List<String> wikiTitles = new ArrayList<>();
    /** competition -> {date the hand-kept rows were last checked against the official pages, "none" or ""}. */
    private static volatile Map<String, String[]> checked = new HashMap<>();
    private final String country;

    SeasonTableProvider(String country) { this.country = country; }

    public String id() { return "table"; }

    /** Loads the bundled tables once. */
    static void load(Context c) {
        if (table != null) return;
        Map<String, Row> hand = new HashMap<>(), wiki = new HashMap<>();
        try {
            String h = raw(c, R.raw.broadcasters);
            hand = parse(h);
            checked = checked(h);
        } catch (Exception ignored) { }
        try {
            String w = raw(c, R.raw.broadcasters_wikipedia);
            wiki = parse(w);
            wikiTitles = titles(w);
        } catch (Exception ignored) { }
        table = merge(hand, wiki);
    }

    private static String raw(Context c, int id) throws Exception {
        try (InputStream in = c.getResources().openRawResource(id)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    /** The hand-kept rows, and the Wikipedia rows for every (country, competition) they leave empty. */
    static Map<String, Row> merge(Map<String, Row> hand, Map<String, Row> wiki) {
        Map<String, Row> out = new HashMap<>(wiki);
        out.putAll(hand);
        return out;
    }

    /**
     * When each competition's rows were last checked, and whether its league publishes no international list at all
     * ({"checked": "2026-11-11", "none": "why"}): for the Sources and credits page.
     */
    static Map<String, String[]> checked() { return checked; }

    static Map<String, String[]> checked(String json) throws Exception {
        Map<String, String[]> out = new HashMap<>();
        JSONObject comps = new JSONObject(json).getJSONObject("competitions");
        for (java.util.Iterator<String> it = comps.keys(); it.hasNext(); ) {
            String comp = it.next();
            JSONObject o = comps.getJSONObject(comp);
            out.put(comp, new String[]{o.optString("checked", ""), o.has("none") ? "none" : ""});
        }
        return out;
    }

    /** The Wikipedia pages the second layer came from, for the credits. */
    static List<String> wikiTitles() { return wikiTitles; }

    static List<String> titles(String json) throws Exception {
        List<String> out = new ArrayList<>();
        JSONObject comps = new JSONObject(json).getJSONObject("competitions");
        for (java.util.Iterator<String> it = comps.keys(); it.hasNext(); ) {
            String t = comps.getJSONObject(it.next()).optString("title", "");
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** For tests and checks: use this table instead of the bundled one. */
    static void use(Map<String, Row> t) { table = t; }

    /**
     * {"competitions": {"eng.1": {"from": "2025-07-01", "until": "2029-06-30", "sources": ["..."],
     *   "countries": {"GB": ["Sky Sports", "TNT Sports"], ...}}, ...}}
     * A country may also be {"on": [...], "from": "...", "until": "..."} to override the season dates.
     */
    static Map<String, Row> parse(String json) throws Exception {
        Map<String, Row> out = new HashMap<>();
        JSONObject doc = new JSONObject(json);
        boolean wiki = doc.optString("license").startsWith("CC BY-SA");
        JSONObject comps = doc.getJSONObject("competitions");
        for (java.util.Iterator<String> it = comps.keys(); it.hasNext(); ) {
            String comp = it.next();
            JSONObject o = comps.getJSONObject(comp);
            JSONObject cs = o.getJSONObject("countries");
            for (java.util.Iterator<String> ci = cs.keys(); ci.hasNext(); ) {
                String cc = ci.next();
                Row r = new Row();
                JSONArray src = o.optJSONArray("sources");
                r.country = cc; r.comp = comp; r.source = src != null && src.length() > 0 ? src.optString(0) : o.optString("source", "");
                r.wikipedia = wiki;
                r.from = day(o.optString("from", ""), 0);
                r.until = day(o.optString("until", ""), Long.MAX_VALUE);
                Object v = cs.get(cc);
                JSONArray names;
                if (v instanceof JSONObject) {
                    JSONObject x = (JSONObject) v;
                    names = x.getJSONArray("on");
                    // "app" is not read: the card, the app allowlist and the manifest queries only know the Greek apps
                    if (x.has("from")) r.from = day(x.optString("from"), r.from);
                    if (x.has("until")) r.until = day(x.optString("until"), r.until);
                } else names = (JSONArray) v;
                for (int i = 0; i < names.length(); i++) if (!names.optString(i).trim().isEmpty()) r.on.add(names.optString(i).trim());
                if (!r.on.isEmpty()) out.put(cc + "|" + comp, r);
            }
        }
        return out;
    }

    /** yyyy-MM-dd (UTC midnight; "until" covers that whole day). */
    static long day(String s, long fallback) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return fallback;
        long t = Sources.iso(s + "T00:00Z");
        return t == 0 ? fallback : t;
    }

    /** The row for a match, by its league slug first, then its competition code. */
    static Row find(Map<String, Row> t, String country, Match m) {
        if (t == null || country == null) return null;
        for (String comp : Arrays.asList(m.league, m.comp)) {
            if (comp == null || comp.isEmpty()) continue;
            Row r = t.get(country + "|" + comp);
            if (r != null && m.start >= r.from && (r.until == Long.MAX_VALUE || m.start < r.until + 86400000L)) return r;  // no "until": open-ended
        }
        return null;
    }

    public boolean guess(Match m) {
        Row r = find(table, country, m);
        if (r == null) return false;
        m.channel = String.join(" · ", r.on);
        m.channelKnown = true; m.channelSure = false; m.channelNo = 0; m.channelLogo = null;
        m.channelApp = null;
        m.listedBy = "table";
        return true;
    }

    public void prepare(Context c) { load(c); }

    public Object fetch(Context c, Teams.Club k, List<Match> known, boolean basket) { return null; }

    public void apply(List<Match> all, Teams.Club k, Object listings) { }  // guessed while parsing
}
