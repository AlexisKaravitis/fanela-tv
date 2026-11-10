package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Super League clubs, keyed by ESPN id (never by name). The list is refreshed weekly from ESPN so promoted clubs appear
 * by themselves; followed clubs are remembered even if they leave the league, so they can always be unfollowed.
 * Basketball links: EuroLeague (E) / EuroCup (U) membership re-checked each season; TheSportsDB id from the table or
 * looked up once by name (a "not found" is only remembered after real answers, and expires).
 */
final class Teams {
    static final class Club {
        String id, name, crest, color, gr;
        String bcComp, bcCode, bcTsdb;
        boolean hasBasket;
        String rowKey() { return "club_" + id; }
        String label() { return I18n.el() ? gr : name; }
    }

    /** id, English name, Greek name, colour. 2026-27 clubs, used until ESPN has answered once. */
    static final String[][] FALLBACK = {
        {"435", "Olympiacos", "Ολυμπιακός", "d01729"}, {"605", "PAOK", "ΠΑΟΚ", "000000"}, {"887", "AEK Athens", "ΑΕΚ", "ffff00"},
        {"443", "Panathinaikos", "Παναθηναϊκός", "2b6a36"}, {"11553", "Aris", "Άρης", "ffd700"}, {"1010", "OFI Crete", "ΟΦΗ", "000000"},
        {"6790", "Atromitos", "Ατρόμητος", "1e90ff"}, {"5276", "Levadiakos", "Λεβαδειακός", "008000"}, {"132429", "Kalamata", "Καλαμάτα", "000000"},
        {"8354", "Asteras Tripoli", "Αστέρας Τρ.", "ffff00"}, {"2575", "Iraklis", "Ηρακλής", "1e3a8a"},
        {"11431", "Panetolikos", "Παναιτωλικός", "ffff00"}, {"20043", "Volos NFC", "Βόλος", "1e3a8a"}, {"21969", "Kifisia", "Κηφισιά", "000000"}};

    /** Greek names by ESPN id for clubs that may come up (promoted / relegated). */
    static final Map<String, String> GR_BY_ID = new HashMap<>();
    static {
        for (String[] x : FALLBACK) GR_BY_ID.put(x[0], x[2]);
        String[][] more = {{"5279", "Πανσερραϊκός"}, {"13017", "Λαμία"}, {"18263", "Καλλιθέα"}, {"2579", "ΠΑΣ Γιάννινα"},
            {"8356", "Ιωνικός"}, {"7974", "Απόλλων Σμ."}, {"2581", "Ξάνθη"}, {"2580", "Εργοτέλης"}, {"5278", "ΑΕΛ"}};
        for (String[] x : more) GR_BY_ID.put(x[0], x[1]);
    }

    /** Basketball: ESPN id -> EuroLeague/EuroCup club code (or ""), TheSportsDB basketball id. */
    static final String[][] BASKET = {
        {"435", "OLY", "135635"}, {"443", "PAN", "135636"}, {"605", "PAO", "135637"}, {"11553", "ARI", "135628"},
        {"887", "", "135626"}, {"2575", "", "137374"}};

    private static volatile List<Club> cache;
    private static volatile long cacheAt;

    static List<Club> all(Context c) {
        List<Club> cached = cache;
        File f = new File(c.getFilesDir(), "clubs.json");
        if (cached != null && f.lastModified() <= cacheAt) return withFollowed(c, cached);
        long mod = f.exists() ? f.lastModified() : 0;  // taken before reading, so a newer file is never missed
        List<Club> out = new ArrayList<>();
        try {
            if (f.exists()) {
                JSONArray a = new JSONObject(read(f)).getJSONArray("clubs");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    out.add(club(c, o.optString("id"), o.optString("name"), o.optString("crest"), o.optString("color")));
                }
            }
        } catch (Exception ignored) { }
        if (out.isEmpty())
            for (String[] x : FALLBACK) out.add(club(c, x[0], x[1], crestUrl(x[0]), x[3]));
        cache = out;
        cacheAt = mod;
        return withFollowed(c, out);
    }

    static String crestUrl(String id) { return "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png"; }

    /** Adds followed clubs that are no longer in the league list (relegated), from what was saved when followed. */
    private static List<Club> withFollowed(Context c, List<Club> list) {
        Set<String> ids = new LinkedHashSet<>();
        for (Club k : list) ids.add(k.id);
        List<Club> out = new ArrayList<>(list);
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        for (String f : followed(c)) {
            if (ids.contains(f)) continue;
            String name = sp.getString("meta_name_" + f, null);
            if (name == null) for (String[] x : FALLBACK) if (x[0].equals(f)) name = x[1];
            if (name != null) out.add(club(c, f, name, sp.getString("meta_crest_" + f, crestUrl(f)), "000000"));
        }
        return out;
    }

    /** Clubs for the settings grid: followed first, then alphabetical in the current language. */
    static List<Club> sorted(Context c) {
        List<Club> list = new ArrayList<>(all(c));
        final Set<String> f = followed(c);
        final Collator col = Collator.getInstance(I18n.locale());
        Collections.sort(list, new Comparator<Club>() {
            public int compare(Club a, Club b) {
                boolean fa = f.contains(a.id), fb = f.contains(b.id);
                if (fa != fb) return fa ? -1 : 1;
                return col.compare(a.label(), b.label());
            }
        });
        return list;
    }

    static Club get(Context c, String id) {
        for (Club k : all(c)) if (k.id.equals(id)) return k;
        return null;
    }

    private static Club club(Context c, String id, String name, String crest, String color) {
        Club k = new Club();
        k.id = id; k.name = name == null ? "" : name.trim(); k.crest = crest; k.color = color;
        String g = GR_BY_ID.get(id);
        k.gr = g != null ? g : I18n.grName(k.name);
        for (String[] b : BASKET) if (b[0].equals(id)) {
            k.hasBasket = true;
            k.bcCode = b[1].isEmpty() ? null : b[1];
            k.bcTsdb = b[2].isEmpty() ? null : b[2];
        }
        if (!k.hasBasket) {  // a club outside the table whose basketball team was found by name (lookupBasket)
            String t = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("tsdbbc_" + id, "");
            if (!t.isEmpty()) { k.hasBasket = true; k.bcTsdb = t; }
        }
        if (k.bcCode != null) k.bcComp = euroComp(c, k);
        return k;
    }

    /** EuroLeague or EuroCup for this season (cached per season; re-checked when the season changes). */
    static String euroComp(Context c, Club k) {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int season = Sources.seasonOf(System.currentTimeMillis());
        String key = "euro_" + k.id + "_" + season;
        String v = sp.getString(key, null);
        if (v != null && !v.isEmpty()) return v;
        if (v != null) return null;  // checked: neither (re-checked weekly by resolveEuro)
        // Known for 2026-27 without a network call; other seasons are checked by resolveEuro() during a refresh.
        if (season == 2026) return "435".equals(k.id) || "443".equals(k.id) ? "E" : "U";
        return null;
    }

    /** Network check of EuroLeague/EuroCup membership for the current season (once per season per club). */
    static void resolveEuro(Context c, Club k) {
        if (k.bcCode == null) return;
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int season = Sources.seasonOf(System.currentTimeMillis());
        String key = "euro_" + k.id + "_" + season;
        if (season == 2026) return;
        String v = sp.getString(key, null);
        if (v != null && (!v.isEmpty() || System.currentTimeMillis() - sp.getLong(key + "_at", 0) < 7 * 86400000L)) return;
        int answered = 0;
        for (String comp : new String[]{"E", "U"}) {
            try {
                JSONObject d = new JSONObject(Net.http("https://api-live.euroleague.net/v2/competitions/" + comp + "/seasons/"
                    + comp + season + "/games?teamCode=" + k.bcCode));
                answered++;
                JSONArray a = d.optJSONArray("data");
                if (a != null && a.length() > 0) { sp.edit().putString(key, comp).apply(); cache = null; return; }
            } catch (Exception ignored) { }
        }
        if (answered == 2) { sp.edit().putString(key, "").putLong(key + "_at", System.currentTimeMillis()).apply(); cache = null; }  // neither yet
    }

    /** Weekly refresh of the club list from ESPN (promotions/relegations, crests). */
    static void refresh(Context c) {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("clubsAt", 0) < 7 * 86400000L) return;
        try {
            JSONObject d = new JSONObject(Net.http("https://site.api.espn.com/apis/site/v2/sports/soccer/gre.1/teams"));
            JSONArray teams = d.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams");
            JSONArray out = new JSONArray();
            for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i).getJSONObject("team");
                JSONArray logos = t.optJSONArray("logos");
                String crest = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : "";
                if (t.optString("id").isEmpty() || t.optString("displayName").isEmpty()) continue;
                out.put(new JSONObject().put("id", t.optString("id")).put("name", t.optString("displayName").trim())
                    .put("crest", crest.isEmpty() ? crestUrl(t.optString("id")) : crest).put("color", t.optString("color")));
            }
            if (out.length() >= 10) {
                Store.writeAtomic(new File(c.getFilesDir(), "clubs.json"), new JSONObject().put("clubs", out).toString());
                sp.edit().putLong("clubsAt", now).apply();
                cache = null;
            }
        } catch (Exception ignored) { }
    }

    // ---------- follows ----------
    static Set<String> followed(Context c) {
        String s = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("follow", "");
        Set<String> out = new LinkedHashSet<>();
        for (String x : s.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    static void setFollowed(Context c, Set<String> ids, Club justFollowed) {
        StringBuilder b = new StringBuilder();
        for (String x : ids) { if (b.length() > 0) b.append(','); b.append(x); }
        SharedPreferences.Editor ed = c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("follow", b.toString());
        if (justFollowed != null) ed.putString("meta_name_" + justFollowed.id, justFollowed.name).putString("meta_crest_" + justFollowed.id, justFollowed.crest);
        ed.commit();
        if (justFollowed != null)  // fetch it on the next refresh, even if it was followed (and fetched) a moment ago
            c.getSharedPreferences("engine", Context.MODE_PRIVATE).edit().remove("lastFull_" + justFollowed.id).remove("sig_" + justFollowed.id).commit();
    }

    /** The club whose colours the app wears: the one chosen in Settings while it is followed, else the first followed. */
    static String favourite(Context c) {
        Set<String> f = followed(c);
        String fav = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("favourite", null);
        if (fav != null && f.contains(fav)) return fav;
        return f.isEmpty() ? null : f.iterator().next();
    }

    static void setFavourite(Context c, String id) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("favourite", id).commit();
    }

    static boolean chosen(Context c) {  // the first-run club picker was completed (or skipped) once
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("onboarded", false);
    }

    static boolean basketball(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("basket", true);
    }

    /** TheSportsDB basketball id; table first, then a name lookup remembered for 30 days (only after real answers). */
    static String tsdbBasket(Context c, Club k) {
        if (k.bcTsdb != null) return k.bcTsdb;
        if (!k.hasBasket) return null;
        return lookup(c, k);
    }

    /** For clubs outside the basketball table (e.g. newly promoted): looks for a Greek basketball team of the same name. */
    static void lookupBasket(Context c, Club k) {
        if (k.hasBasket) return;
        if (lookup(c, k) != null) cache = null;
    }

    private static String lookup(Context c, Club k) {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String cached = sp.getString("tsdbbc_" + k.id, null);
        long at = sp.getLong("tsdbbc_at_" + k.id, 0), now = System.currentTimeMillis();
        if (cached != null && !cached.isEmpty()) return cached;
        if (cached != null && now - at < 30L * 86400000L) return null;  // "not found" (after real answers), retry monthly
        if (now - sp.getLong("tsdbbc_try_" + k.id, 0) < 2 * 3600000L || Net.blocked("www.thesportsdb.com")) return null;
        sp.edit().putLong("tsdbbc_try_" + k.id, now).apply();
        int answered = 0;
        List<String> qs = Arrays.asList(k.name.replace(" Athens", "") + " BC", k.name + " BC", k.name);
        for (String q : qs) {
            try {
                JSONObject d = new JSONObject(Net.http("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=" + URLEncoder.encode(q, "UTF-8")));
                answered++;
                JSONArray ts = d.optJSONArray("teams");
                for (int i = 0; ts != null && i < ts.length(); i++) {
                    JSONObject t = ts.getJSONObject(i);
                    String first = k.name.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
                    if ("Basketball".equals(t.optString("strSport")) && "Greece".equals(t.optString("strCountry"))
                        && t.optString("strTeam").toLowerCase(java.util.Locale.ROOT).startsWith(first)) {
                        sp.edit().putString("tsdbbc_" + k.id, t.optString("idTeam")).putLong("tsdbbc_at_" + k.id, now).apply();
                        return t.optString("idTeam");
                    }
                }
            } catch (Exception ignored) { }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return null; }
        }
        if (answered == qs.size()) sp.edit().putString("tsdbbc_" + k.id, "").putLong("tsdbbc_at_" + k.id, now).apply();
        return null;
    }

    private static String read(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
        return new String(b, StandardCharsets.UTF_8);
    }
}
