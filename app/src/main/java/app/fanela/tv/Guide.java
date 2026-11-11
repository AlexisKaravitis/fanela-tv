package app.fanela.tv;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Exact channel for each match from two live TV guides, cached on disk for an hour:
 *  1. MagentaTV's guide search, per club name (channel number + logo, e.g. Magenta Sport 1 League · 301)
 *  2. Sport24's sports guide (tv.24media.gr), shared by all clubs: every Greek channel incl. ΣΠΟΡ FM TV / ERT; also used
 *     to discover cup / basketball games the fixture feeds miss, and to correct their times.
 */
final class Guide {
    static final class Airing {
        long start;
        int number;
        String id, channel, logo, title, genre, tag;
        boolean live;

        JSONObject json() throws Exception {
            return new JSONObject().put("s", start).put("n", number).put("id", id).put("c", channel).put("l", logo)
                .put("t", title).put("g", genre).put("tag", tag).put("live", live);
        }

        static Airing of(JSONObject o) {
            Airing a = new Airing();
            a.start = o.optLong("s"); a.number = o.optInt("n"); a.id = o.optString("id"); a.channel = o.optString("c");
            a.logo = o.isNull("l") ? null : o.optString("l", null); a.title = o.optString("t"); a.genre = o.optString("g");
            a.tag = o.optString("tag"); a.live = o.optBoolean("live");
            return a;
        }
    }

    static final long TTL = 60 * 60000L;

    static synchronized List<Airing> load(Context c, String name, long[] at) {
        List<Airing> out = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), name);
            if (!f.exists()) return out;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
            JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            at[0] = o.optLong("at");
            JSONArray a = o.optJSONArray("items");
            for (int i = 0; a != null && i < a.length(); i++) out.add(Airing.of(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized void save(Context c, String name, List<Airing> list, long at) {
        try {
            JSONArray a = new JSONArray();
            for (Airing x : list) a.put(x.json());
            Store.writeAtomic(new File(c.getFilesDir(), name), new JSONObject().put("at", at).put("items", a).toString());
        } catch (Exception ignored) { }
    }

    // ---------- 1. MagentaTV (per club) ----------
    static List<Airing> magenta(Context c, Teams.Club k, List<Match> known) {
        if (!BuildConfig.GUIDES) return new ArrayList<>();  // store build: channels from the rights table only
        String file = "guide_magenta_" + k.id + ".json";
        long[] at = {0};
        List<Airing> cached = load(c, file, at);
        long now = System.currentTimeMillis();
        if (now - at[0] < TTL) return cached;
        boolean soon = false;  // nothing to look up when the club has no game in the next week
        for (Match m : known)
            if (k.id.equals(m.club) && ("pre".equals(m.state) || "in".equals(m.state)) && m.start > now - 6 * 3600000L && m.start < now + 7 * 86400000L) soon = true;
        if (!soon) return cached;
        List<Airing> out = new ArrayList<>();
        try {
            int from = 0;
            for (int page = 0; page < 3 && from >= 0; page++) {
                JSONObject d = new JSONObject(Net.http("https://www.magentatv.gr/api/search/ott?q="
                    + URLEncoder.encode(k.gr, "UTF-8") + "&locale=el&vodStartFrom=-1&epgStartFrom=" + from));
                JSONArray items = d.optJSONArray("items");
                if (items == null) throw new Exception("format");
                for (int i = 0; i < items.length(); i++) {
                    try {
                        JSONObject it = items.getJSONObject(i);
                        if (!"program".equals(it.optString("type"))) continue;
                        JSONObject ch = it.optJSONObject("channel"), qoe = it.optJSONObject("qoe");
                        if (ch == null || ch.optString("title").isEmpty()) continue;
                        Airing a = new Airing();
                        a.start = Sources.iso(it.optString("startTime"));
                        a.title = it.optString("title");
                        a.genre = qoe == null ? "" : qoe.optString("genre");
                        a.live = it.optBoolean("isLiveEvent") || a.title.contains("(Ζ)");
                        a.number = ch.optInt("number");
                        a.channel = clean(ch.optString("title"));
                        JSONObject logos = ch.optJSONObject("logos");
                        a.logo = logos == null ? null : logos.optString("wide", logos.optString("square", null));
                        if (a.start > 0 && a.live) out.add(a);  // only live broadcasts are useful
                    } catch (Exception ignored) { }
                }
                from = d.optBoolean("hasMore") ? d.optInt("nextEpgStartFrom", -1) : -1;
            }
            save(c, file, out, System.currentTimeMillis());
            return out;
        } catch (Exception e) {
            return cached;
        }
    }

    // ---------- 2. Sport24 (shared) ----------
    static List<Airing> sport24(Context c) {
        if (!BuildConfig.GUIDES) return new ArrayList<>();
        long[] at = {0};
        List<Airing> cached = load(c, "guide_sport24.json", at);
        if (System.currentTimeMillis() - at[0] < TTL) return cached;
        List<Airing> out = new ArrayList<>();
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US), ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        TimeZone athens = TimeZone.getTimeZone("Europe/Athens");
        day.setTimeZone(athens);
        ts.setTimeZone(athens);
        long now = System.currentTimeMillis();
        int okDays = 0;
        for (int d = -1; d <= 7; d++) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance(athens);  // calendar days: right across clock changes
                cal.setTimeInMillis(now);
                cal.add(java.util.Calendar.DAY_OF_YEAR, d);
                JSONArray a = new JSONArray(Net.http("https://tv.24media.gr/service/events?accept=json&date="
                    + day.format(cal.getTime()) + "&days=1&pId=3"));
                okDays++;
                for (int i = 0; i < a.length(); i++) {
                    try {
                        JSONObject e = a.getJSONObject(i);
                        JSONObject ch = e.optJSONObject("channel");
                        String chName = ch == null ? "" : clean(ch.optString("name"));
                        if (chName.isEmpty()) continue;
                        Airing x = new Airing();
                        x.id = e.optString("id");
                        x.title = e.optString("title").trim();
                        x.start = ts.parse(e.optString("scheduleDate")).getTime();
                        x.channel = chName;
                        x.live = true;
                        x.genre = "";
                        x.tag = "";
                        JSONArray tags = e.optJSONArray("tags");
                        for (int t = 0; tags != null && t < tags.length(); t++) {
                            String n = tags.getJSONObject(t).optString("name"), dn = tags.getJSONObject(t).optString("displayName");
                            if ("basket".equals(n)) x.genre = "Basketball";
                            else if ("podosfairo".equals(n)) x.genre = "Football";
                            else if (x.tag.isEmpty()) x.tag = dn;
                        }
                        String tk = Cards.key(x.tag);
                        if ("Basketball".equals(x.genre) && tk.contains("SUPER LEAGUE") && !tk.contains("BASKET") && !tk.contains("GBL"))
                            x.genre = "Football";  // Sport24 sometimes tags a Super League simulcast as basketball
                        if (!x.genre.isEmpty()) out.add(x);  // football and basketball only
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        if (okDays == 0) return cached;
        if (okDays < 9) {
            for (Airing old : cached) {
                boolean dup = false;
                for (Airing n : out) if (n.id != null && n.id.equals(old.id)) { dup = true; break; }
                if (!dup && old.start > now - 2 * 86400000L) out.add(old);
            }
        }
        save(c, "guide_sport24.json", out, okDays == 9 ? now : now - TTL / 2);
        return out;
    }

    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*HD$", "").replaceAll("(Novasports)(extra)?(\\d)", "$1 $2$3").replace("  ", " ").trim();
    }

    /** The two sides of a title like "Ολυμπιακός - ΠΑΟΚ (Ζ)" or "Super League: ΑΕΚ – Άρης", as matching keys. */
    static String[] sides(String title) {
        String t = Cards.key(title).replaceAll("\\(.*?\\)", " ").trim();
        String[] s = t.split("\\s+[–-]\\s+", 2);
        if (s.length != 2) return new String[]{t};
        int cut = Math.max(s[0].lastIndexOf(':'), s[0].lastIndexOf(','));  // drop a "Competition:" prefix
        if (cut >= 0) s[0] = s[0].substring(cut + 1);
        s[0] = s[0].trim(); s[1] = s[1].trim();
        return s;
    }

    /** Words after a club name that mean another club (Cyprus, Serbia) or a B / youth / women's team. */
    static final String[] OTHER = {"Β", "Β'", "Β΄", "B", "Κ19", "Κ17", "Κ21", "U19", "U21", "ΝΕΩΝ", "ΓΥΝΑΙΚΩΝ", "ΛΕΜΕΣΟΥ",
        "ΛΑΡΝΑΚΑΣ", "ΛΕΥΚΩΣΙΑΣ", "ΑΜΜΟΧΩΣΤΟΥ", "ΠΑΦΟΥ", "ΒΕΛΙΓΡΑΔΙΟΥ", "ΚΥΠΡΟΥ", "ΑΘΗΕΝΟΥ", "ΒΟΛΟΥ"};

    /** True when this side of the title starts with the team (first 5 letters of its name) and is its first team. */
    static boolean sideIs(String side, String team) {
        String n = Cards.key(team).trim();
        if (n.isEmpty() || "?".equals(n)) return false;
        String k5 = n.length() > 5 ? n.substring(0, 5) : n;
        String x = Cards.key(side).trim().replaceFirst("^(ΠΑΕ ΚΑΕ|ΚΑΕ|ΠΑΕ)\\s+", "");
        if (!x.startsWith(k5)) return false;
        if (n.length() <= 5 && x.length() > n.length() && Character.isLetter(x.charAt(n.length()))) return false;  // ΑΕΚ, not ΑΕΚΑΡΑ
        String[] w = x.split("\\s+");
        int words = n.split("\\s+").length;
        if (w.length > words) for (String o : OTHER) if (o.equals(w[words])) return false;
        return !(x.contains("ΝΕΩΝ") || x.contains("ΓΥΝΑΙΚ") || x.contains("ΕΦΗΒ") || x.contains("ΠΑΙΔ"));
    }

    /** The followed club is one of the two sides (first team only). */
    static boolean firstTeam(String title, String clubGr) {
        String[] s = sides(title);
        if (s.length != 2) return false;
        return sideIs(s[0], clubGr) || sideIs(s[1], clubGr);
    }

    static boolean has(String title, String team) {
        if (team == null || team.isEmpty() || "?".equals(team)) return false;
        for (String side : sides(title)) if (sideIs(side, team)) return true;
        return false;
    }

    static String app(String channel) {
        String k = Cards.key(channel);
        return k.contains("ΣΠΟΡ FM") || k.contains("SPORT FM") ? Cards.SPORTFM : Cards.MAGENTA;
    }

    static Airing find(List<Airing> air, Match m, String clubGr, long tolerance) {
        String genre = "FC".equals(m.team) ? "Football" : "Basketball";
        String opp = I18n.grName(m.opponentRaw());
        Airing best = null;
        for (Airing a : air) {
            if (!genre.equalsIgnoreCase(a.genre) || !a.live) continue;
            if (Math.abs(a.start - m.start) > tolerance) continue;
            if (!firstTeam(a.title, clubGr)) continue;
            if (m.tbd && !has(a.title, opp)) continue;
            if (best == null || score(a, opp, m) > score(best, opp, m)) best = a;
        }
        return best;
    }

    private static long score(Airing a, String opp, Match m) {
        return (has(a.title, opp) ? 10 * 3600000L : 0) - Math.abs(a.start - m.start);
    }

    /** Every channel showing the same game (e.g. ΣΠΟΡ FM TV1 and ΣΚΑΪ), best first, joined with " · ". */
    static String simulcast(List<Airing> air, Airing best, Match m, String clubGr) {
        StringBuilder b = new StringBuilder(best.channel);
        String genre = "FC".equals(m.team) ? "Football" : "Basketball";
        for (Airing a : air) {
            if (a == best || !a.live || !genre.equalsIgnoreCase(a.genre) || Math.abs(a.start - best.start) > 15 * 60000L) continue;
            if (!firstTeam(a.title, clubGr) || b.toString().contains(a.channel)) continue;
            b.append(" · ").append(a.channel);
        }
        return b.toString();
    }

    /** Sets the confirmed channel on upcoming/live matches of one club; fixes weak-source times from Sport24. */
    static void apply(List<Match> all, Teams.Club k, List<Airing> mag, List<Airing> sp, List<Airing> sofa) {
        long now = System.currentTimeMillis();
        String clubGr = k.gr;
        for (Match m : all) {
            if (!k.id.equals(m.club) || (!"pre".equals(m.state) && !"in".equals(m.state))) continue;
            if (m.start < now - (m.tbd ? 30 : 6) * 3600000L || m.start > now + 14 * 86400000L) continue;  // a TBA game: all its day
            long tol = m.tbd ? 20 * 3600000L : 2 * 3600000L;
            Airing a = find(mag, m, clubGr, tol), b = find(sp, m, clubGr, tol);
            Airing s = a == null && b == null && sofa != null ? Sofa.find(sofa, m) : null;  // SofaScore: only when no guide lists it
            if (a != null) {
                m.channel = a.channel; m.channelNo = a.number; m.channelLogo = a.logo;
                m.channelApp = Cards.MAGENTA; m.channelSure = true; m.channelKnown = true; m.listedBy = "";
            } else if (b != null) {
                m.channel = simulcast(sp, b, m, clubGr); m.channelNo = Channels.numberCached(m.channel); m.channelLogo = null;
                m.channelApp = app(m.channel);
                m.channelSure = true; m.channelKnown = true; m.listedBy = "";
            } else if (s != null) {
                m.channel = s.channel; m.channelNo = Channels.numberCached(s.channel); m.channelLogo = null;
                m.channelApp = app(m.channel);
                m.channelSure = true; m.channelKnown = true; m.listedBy = "";
            }
            boolean weakTime = "tsdb".equals(m.source) || "s24".equals(m.source);
            if (b != null && "pre".equals(m.state) && (m.tbd || (weakTime && Math.abs(b.start - m.start) <= 90 * 60000L))) {
                m.start = b.start;
                m.tbd = false;
            }
        }
    }

    /** Cup / basketball games of one club that no fixture feed returned, from Sport24's listing. */
    static List<Match> discover(List<Match> known, List<Airing> sp, Teams.Club k, boolean basket) {
        List<Match> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        String clubGr = k.gr;
        for (Airing a : sp) {
            if (!a.live || a.start < now - 6 * 3600000L || a.start > now + 8 * 86400000L) continue;
            if (!firstTeam(a.title, clubGr)) continue;
            String tag = Cards.key(a.tag);
            boolean bc = "Basketball".equals(a.genre), fc = "Football".equals(a.genre);
            if (!bc && !fc) continue;
            if (bc && (!basket || !k.hasBasket)) continue;
            if (bc && k.bcComp != null && (tag.contains("EUROLEAGUE") || tag.contains("EUROCUP"))) continue;  // own API
            // football: only the Greek Cup (ESPN has everything else); never Cyprus' cup
            if (fc && (!tag.contains("ΚΥΠΕΛΛΟ") || tag.contains("ΚΥΠΡ") || !(tag.contains("ΕΛΛΑΔ") || tag.contains("BETSSON")))) continue;
            String team = bc ? "BC" : "FC";
            String[] sides = a.title.replaceAll("\\(.*?\\)", " ").trim().split("\\s+[–-]\\s+", 2);
            if (sides.length != 2) continue;
            int cut = Math.max(sides[0].lastIndexOf(':'), sides[0].lastIndexOf(','));
            String h = (cut >= 0 ? sides[0].substring(cut + 1) : sides[0]).trim(), w = sides[1].trim();
            boolean home = sideIs(h, clubGr);
            String opp = plainName(home ? w : h);
            boolean dup = false;
            for (Match m : known) {
                if (!k.id.equals(m.club) || Math.abs(m.start - a.start) >= (m.tbd ? 20 : 3) * 3600000L) continue;
                if (team.equals(m.team)) { dup = true; break; }
                // the football game against the same opponent, mislabelled as basketball (or the other way round)
                if (has(a.title, I18n.grName(m.opponentRaw()))) { dup = true; break; }
            }
            for (Match m : out) if (team.equals(m.team) && Math.abs(m.start - a.start) < 3 * 3600000L) {  // simulcast
                if (!m.channel.contains(a.channel)) m.channel += " · " + a.channel;
                m.channelApp = app(m.channel);
                if (greekLetters(opp) > greekLetters(m.opponentRaw())) { if (m.clubIsHome) m.away = opp; else m.home = opp; }
                dup = true;
            }
            if (dup) continue;
            Match m = new Match();
            m.club = k.id; m.team = team; m.source = "s24"; m.key = k.id + "|s24:" + a.id; m.start = a.start; m.state = "pre";
            m.clubIsHome = home;
            m.home = home ? k.name : opp;  // the followed side by its own name, so the English interface shows it in English
            m.away = home ? opp : k.name;
            if (tag.contains("ΚΥΠΕΛΛΟ")) m.comp = bc ? "gbl.cup" : "gre.cup";
            else if (!bc) m.comp = "gre.cup";
            else if (tag.contains("BASKETBALL CHAMPIONS")) m.comp = "bcl";
            else m.comp = Sources.bcComp(a.tag);
            m.league = bc ? (m.comp.startsWith("gbl") ? "gbl" : m.comp) : "gre.cup";
            m.channel = a.channel; m.channelSure = true; m.channelKnown = true;
            m.channelApp = app(a.channel);
            out.add(m);
        }
        return out;
    }

    static boolean greek(String w) { return w.matches(".*[\\u0370-\\u03FF\\u1F00-\\u1FFF].*"); }

    static int greekLetters(String s) {
        int n = 0;
        for (int i = 0; s != null && i < s.length(); i++) { char ch = s.charAt(i); if (ch >= 0x0370 && ch <= 0x03FF) n++; }
        return n;
    }

    /** A Greek club name without the sponsor words the guides append ("Μύκονος Betsson BC" -> "Μύκονος"). */
    static String plainName(String s) {
        String[] w = s.trim().split("\\s+");
        if (w.length < 2 || !greek(w[0])) return s.trim();
        int n = w.length;
        while (n > 1 && !greek(w[n - 1])) n--;
        StringBuilder b = new StringBuilder(w[0]);
        for (int i = 1; i < n; i++) b.append(' ').append(w[i]);
        return b.toString();
    }
}
