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
 * Clubs, keyed by ESPN id (never by name). Super League clubs as before: the list is refreshed weekly from ESPN so
 * promoted clubs appear by themselves. Other leagues (Leagues.ALL) are listed the same way, one file per league,
 * fetched when the clubs picker first opens them. Followed clubs are remembered even if they leave their league, so they
 * can always be unfollowed.
 * Basketball links: EuroLeague (E) / EuroCup (U) membership re-checked each season; TheSportsDB id from the table or
 * looked up once by name for Greek clubs (a "not found" is only remembered after real answers, and expires).
 * Basketball-only clubs: NBA teams ("nba" + ESPN basketball id, which overlaps football ids) and EuroLeague / EuroCup
 * clubs without a football side in the list ("el" + club code, lower case).
 */
final class Teams {
    static final class Club {
        String id, name, crest, color, gr;
        String league = "gre.1";  // the list the club was picked from: an ESPN slug, "nba", "euroleague" or "eurocup"
        String bcComp, bcCode, bcTsdb;
        boolean hasBasket;
        String rowKey() { return "club_" + id; }
        String label() { return I18n.el() ? gr : name; }
        boolean nba() { return id.startsWith("nba"); }
        boolean euroOnly() { return id.startsWith("el"); }
        boolean basketOnly() { return nba() || euroOnly(); }
        /** The ESPN id (football, or basketball for the NBA). */
        String espnId() { return nba() ? id.substring(3) : id; }
        /** The sport whose feed says whether the club's data is up to date. */
        String mainTeam() { return basketOnly() ? "BC" : "FC"; }
        /** The teams shown: football (and basketball when on and the club has a side), or basketball only. */
        String[] teams(boolean basket) {
            return basketOnly() ? new String[]{"BC"} : basket && hasBasket ? new String[]{"FC", "BC"} : new String[]{"FC"};
        }
        boolean shows(String team, boolean basket) {
            for (String t : teams(basket)) if (t.equals(team)) return true;
            return false;
        }
        boolean greek() { return "gre.1".equals(league) || GR_BY_ID.containsKey(id); }
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

    /**
     * Basketball: ESPN id -> EuroLeague/EuroCup club code (or ""), TheSportsDB basketball id (Greek clubs), the
     * 2026-27 competition (E / U, known without a network call), the name in the EuroLeague / EuroCup grids, and the
     * football league (a club followed from those grids is the football club, with its basketball side).
     */
    static final String[][] BASKET = {
        {"435", "OLY", "135635", "E", "Olympiacos", "gre.1"}, {"443", "PAN", "135636", "E", "Panathinaikos", "gre.1"},
        {"605", "PAO", "135637", "U", "PAOK", "gre.1"}, {"11553", "ARI", "135628", "U", "Aris", "gre.1"},
        {"887", "", "135626", "", "AEK Athens", "gre.1"}, {"2575", "", "137374", "", "Iraklis", "gre.1"},
        {"86", "MAD", "", "E", "Real Madrid", "esp.1"}, {"83", "BAR", "", "E", "Barcelona", "esp.1"},
        {"132", "MUN", "", "E", "Bayern Munich", "ger.1"}, {"436", "ULK", "", "E", "Fenerbahce", "tur.1"},
        {"1895", "BES", "", "E", "Besiktas", "tur.1"}};

    private static volatile List<Club> cache;
    private static volatile long cacheAt;

    /** The Super League clubs and every followed club. */
    static List<Club> all(Context c) { return withFollowed(c, greek(c), null); }

    /** The Super League clubs. */
    static List<Club> greek(Context c) {
        List<Club> cached = cache;
        File f = new File(c.getFilesDir(), "clubs.json");
        if (cached != null && f.lastModified() <= cacheAt) return cached;
        long mod = f.exists() ? f.lastModified() : 0;  // taken before reading, so a newer file is never missed
        List<Club> out = new ArrayList<>();
        try {
            if (f.exists()) {
                JSONArray a = new JSONObject(read(f)).getJSONArray("clubs");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    out.add(club(c, o.optString("id"), o.optString("name"), o.optString("crest"), o.optString("color"), "gre.1"));
                }
            }
        } catch (Exception ignored) { }
        if (out.isEmpty())
            for (String[] x : FALLBACK) out.add(club(c, x[0], x[1], crestUrl(x[0]), x[3], "gre.1"));
        cache = out;
        cacheAt = mod;
        return out;
    }

    static String crestUrl(String id) { return "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png"; }

    /**
     * Adds followed clubs that are not in the list (relegated, or from another league), from what was saved when they
     * were followed. league: only those picked from that league (null: all).
     */
    private static List<Club> withFollowed(Context c, List<Club> list, String league) {
        Set<String> ids = new LinkedHashSet<>();
        for (Club k : list) ids.add(k.id);
        List<Club> out = new ArrayList<>(list);
        for (String f : followed(c)) {
            if (ids.contains(f)) continue;
            Club k = fromMeta(c, f);
            if (k != null && (league == null || league.equals(k.league))) out.add(k);
        }
        return out;
    }

    /** A followed club from what was saved when it was followed (Greek clubs from before 1.1 saved no league). */
    private static Club fromMeta(Context c, String id) {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String name = sp.getString("meta_name_" + id, null);
        if (name == null) for (String[] x : FALLBACK) if (x[0].equals(id)) name = x[1];
        if (name == null) return null;
        return club(c, id, name, sp.getString("meta_crest_" + id, crestUrl(id)), sp.getString("meta_color_" + id, "000000"),
            sp.getString("meta_league_" + id, "gre.1"));
    }

    /** Clubs for the settings grid: followed first, then alphabetical in the current language. */
    static List<Club> sorted(Context c) { return sorted(c, "gre.1"); }

    /** One league's grid; null while its list has never been downloaded. */
    static List<Club> sorted(Context c, String league) {
        List<Club> base = "gre.1".equals(league) ? greek(c) : league(c, league);
        if (base == null) return null;
        List<Club> list = withFollowed(c, base, league);
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

    private static Club club(Context c, String id, String name, String crest, String color, String league) {
        Club k = new Club();
        k.id = id; k.name = name == null ? "" : name.trim(); k.crest = crest; k.color = color;
        k.league = league == null || league.isEmpty() ? "gre.1" : league;
        String g = GR_BY_ID.get(id);
        k.gr = g != null ? g : I18n.grName(k.name);
        for (String[] b : BASKET) if (b[0].equals(id)) {
            if (Leagues.get(k.league) != null && Leagues.get(k.league).basketball()) k.league = b[5];  // picked from a basketball grid
            k.hasBasket = true;
            k.bcCode = b[1].isEmpty() ? null : b[1];
            k.bcTsdb = b[2].isEmpty() ? null : b[2];
        }
        if (k.nba()) k.hasBasket = true;
        if (k.euroOnly()) { k.hasBasket = true; k.bcCode = id.substring(2).toUpperCase(java.util.Locale.ROOT); }
        if (!k.hasBasket && k.greek()) {  // a club outside the table whose basketball team was found by name (lookupBasket)
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
        String known = season == 2026 ? comp2026(k.id) : null;
        if (known != null) return known;
        if (k.euroOnly()) return "eurocup".equals(k.league) ? "U" : "E";  // the competition it was listed in
        return null;
    }

    private static String comp2026(String id) {
        for (String[] b : BASKET) if (b[0].equals(id) && !b[3].isEmpty()) return b[3];
        return null;
    }

    /** Network check of EuroLeague/EuroCup membership for the current season (once per season per club). */
    static void resolveEuro(Context c, Club k) {
        if (k.bcCode == null) return;
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int season = Sources.seasonOf(System.currentTimeMillis());
        String key = "euro_" + k.id + "_" + season;
        if (season == 2026 && comp2026(k.id) != null) return;
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

    // ---------- other leagues ----------
    private static final Map<String, List<Club>> leagues = new HashMap<>();
    private static final Map<String, Long> leaguesAt = new HashMap<>();

    private static File leagueFile(Context c, String league) { return new File(c.getFilesDir(), "clubs_" + league + ".json"); }

    /** A league's clubs as last downloaded; null if never. */
    static List<Club> league(Context c, String league) {
        File f = leagueFile(c, league);
        synchronized (leagues) {
            List<Club> cached = leagues.get(league);
            Long at = leaguesAt.get(league);
            if (cached != null && at != null && f.lastModified() <= at) return cached;
        }
        if (!f.exists()) return null;
        long mod = f.lastModified();
        List<Club> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(read(f)).getJSONArray("clubs");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(club(c, o.optString("id"), o.optString("name"), o.optString("crest"), o.optString("color"), league));
            }
        } catch (Exception e) {
            return null;
        }
        synchronized (leagues) { leagues.put(league, out); leaguesAt.put(league, mod); }
        return out;
    }

    /** Downloads a league's clubs (weekly; force: now). Network, background thread. True when the list is on the TV. */
    static boolean refreshLeague(Context c, String league, boolean force) {
        if ("gre.1".equals(league)) { refresh(c); return true; }
        Leagues.League lg = Leagues.get(league);
        if (lg == null) return false;
        File f = leagueFile(c, league);
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86400000L) return true;
        try {
            JSONArray out = new JSONArray();
            if (Leagues.EURO.equals(lg.sport)) {
                String comp = "euroleague".equals(league) ? "E" : "U";
                JSONArray d = new JSONObject(Net.http("https://api-live.euroleague.net/v2/competitions/" + comp + "/seasons/" + comp
                    + Sources.seasonOf(System.currentTimeMillis()) + "/clubs")).getJSONArray("data");
                for (int i = 0; i < d.length(); i++) {
                    JSONObject t = d.getJSONObject(i);
                    String code = t.optString("code").trim();
                    if (!code.matches("[A-Z0-9]{2,5}")) continue;
                    String linked = null, name = t.optString("abbreviatedName", t.optString("name")).trim();
                    for (String[] b : BASKET) if (b[1].equals(code)) { linked = b[0]; name = b[4]; }  // the club's football side
                    JSONObject img = t.optJSONObject("images");
                    out.put(new JSONObject().put("id", linked != null ? linked : "el" + code.toLowerCase(java.util.Locale.ROOT)).put("name", name)
                        .put("crest", linked != null ? crestUrl(linked) : img == null ? "" : img.optString("crest", "")).put("color", ""));
                }
            } else {
                boolean nba = Leagues.NBA.equals(lg.sport);
                JSONObject d = new JSONObject(Net.http("https://site.api.espn.com/apis/site/v2/sports/"
                    + (nba ? "basketball/nba" : "soccer/" + league) + "/teams"));
                JSONArray teams = d.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams");
                for (int i = 0; i < teams.length(); i++) {
                    JSONObject t = teams.getJSONObject(i).getJSONObject("team");
                    if (t.optString("id").isEmpty() || t.optString("displayName").isEmpty() || !t.optBoolean("isActive", true)) continue;
                    if (t.optString("displayName").trim().matches("(?i)TBD( .*)?")) continue;  // ESPN's "TBD Home" / "TBD Away" placeholders
                    JSONArray logos = t.optJSONArray("logos");
                    String crest = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : "";
                    out.put(new JSONObject().put("id", (nba ? "nba" : "") + t.optString("id")).put("name", t.optString("displayName").trim())
                        .put("crest", crest.isEmpty() && !nba ? crestUrl(t.optString("id")) : crest).put("color", t.optString("color")));
                }
            }
            if (out.length() < 8) return f.exists();
            Store.writeAtomic(f, new JSONObject().put("clubs", out).toString());
            return true;
        } catch (Exception e) {
            return f.exists();
        }
    }

    /** A club from any list on the TV (a picker tile, a demo); followed clubs as in get(). */
    static Club find(Context c, String id) {
        Club k = get(c, id);
        if (k != null) return k;
        for (Leagues.League l : Leagues.ALL) {
            List<Club> list = "gre.1".equals(l.id) ? null : league(c, l.id);
            if (list != null) for (Club x : list) if (x.id.equals(id)) return x;
        }
        return null;
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
        if (justFollowed != null) ed.putString("meta_name_" + justFollowed.id, justFollowed.name).putString("meta_crest_" + justFollowed.id, justFollowed.crest)
            .putString("meta_league_" + justFollowed.id, justFollowed.league).putString("meta_color_" + justFollowed.id, justFollowed.color == null ? "" : justFollowed.color);
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
        if (!k.hasBasket || !k.greek()) return null;  // TheSportsDB is searched for Greek basketball teams only
        return lookup(c, k);
    }

    /** For clubs outside the basketball table (e.g. newly promoted): looks for a Greek basketball team of the same name. */
    static void lookupBasket(Context c, Club k) {
        if (k.hasBasket || !k.greek()) return;
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
