package app.fanela.tv;

import android.content.Context;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Where to watch F1 sessions and UFC card segments (F1-UFC-ARCHITECTURE §4). Writes SportEvent.SessionWatch only.
 *  1. guess(): the hand-kept rights table res/raw/sport_rights.json, per country, split by F1 session kind or UFC card
 *     segment, with from/until dates. Always "Likely", as football's rights table: a Greek channel row names the channel
 *     (its logo comes from the MagentaTV line-up, never its number); a streaming service (ANT1+, UFC Fight Pass) and every
 *     row abroad are names only (listedBy "table"). No row: listedBy "none", "Check your provider's guide".
 *  2. apply(), Greece only: the guides confirm, by time and never by title.
 *     F1: Sport24 (Motorsport airings titled "Formula 1 <year> - ...") within [start - 45 min, start + 10 min] of the
 *         session (ANT1 starts its race show at 18:45 for a 19:00 race). The MagentaTV search doesn't find ANT1's F1.
 *     UFC: the MagentaTV search "UFC" (live airings, Martial Arts) and Sport24 (Combat airings), within 30 min of the
 *         main card's start; both guides title every event "UFC Fight Night 2026". A numbered Sport24 tag ("334: ...")
 *         must agree with a numbered event. The nearest airing wins.
 *     A confirmation gives the channel, its number and logo (sure, listedBy ""), the same as football's guides.
 * A guess never replaces a Greek guide's confirmation (kept until the country changes).
 */
final class SportWhere {
    private SportWhere() { }

    static final long MIN = 60000L, H = 60 * MIN, DAY = 24 * H;
    static final String GENRE_MOTOR = "Motorsport", GENRE_COMBAT = "Combat", GENRE_MAGENTA_COMBAT = "Martial Arts";
    static final String MAGENTA_FILE = "guide_magenta_ufc.json";
    static final Pattern F1_TITLE = Pattern.compile("^Formula 1 \\d{4} - ", Pattern.CASE_INSENSITIVE);
    static final Pattern TAG_NUMBER = Pattern.compile("^\\s*(\\d+)\\s*:");

    /** One row of the rights table: the first row that matches a session wins. */
    static final class Row {
        String sport, country;
        final List<String> kinds = new ArrayList<>();     // F1 session kinds, or UFC card segments; "*" for all
        final List<String> events = new ArrayList<>();    // ESPN event ids (per-race exceptions); empty: every event
        final List<String> on = new ArrayList<>();        // channel or service names
        String app;                                       // the app's package (Greek rows only), else null
        long from, until = Long.MAX_VALUE;                // UTC ms: from that day; to the end of the until day (MAX: open)
        boolean service;                                  // a streaming service: names only, never a line-up match
        String conf = "", src = "";

        boolean covers(SportEvent e, SportEvent.Session s) {
            if (!kinds.contains("*") && !kinds.contains(s.kind)) return false;
            if (!events.isEmpty() && !events.contains(e.id)) return false;
            long t = s.start > 0 ? s.start : e.start;
            return t >= from && (until == Long.MAX_VALUE || t < until + DAY);
        }
    }

    private static volatile Map<String, List<Row>> table;

    /** Loads sport_rights.json once; false when it can't be read (then nothing is known anywhere). */
    static boolean load(Context c) {
        if (table != null) return true;
        try (InputStream in = c.getResources().openRawResource(R.raw.sport_rights)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            Map<String, List<Row>> t = parse(b.toString("UTF-8"));
            if (t.isEmpty()) return false;
            table = t;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** For tests: use this table instead of the bundled one (null: unload). */
    static void use(Map<String, List<Row>> t) { table = t; }

    /** The table's rows by "<sport>|<COUNTRY>" (pure, for tests). Unparseable: empty. */
    static Map<String, List<Row>> parse(String json) {
        Map<String, List<Row>> out = new HashMap<>();
        try {
            JSONObject sports = new JSONObject(json).optJSONObject("sports");
            if (sports == null) return out;
            for (Iterator<String> si = sports.keys(); si.hasNext(); ) {
                String sport = si.next();
                JSONObject cs = sports.getJSONObject(sport).optJSONObject("countries");
                if (cs == null) continue;
                Map<String, String> alias = new HashMap<>();
                for (Iterator<String> ci = cs.keys(); ci.hasNext(); ) {
                    String cc = ci.next();
                    Object v = cs.get(cc);
                    if (v instanceof String) { alias.put(cc, (String) v); continue; }
                    JSONArray a = (JSONArray) v;
                    List<Row> rows = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) {
                        Row r = row(sport, cc, a.getJSONObject(i));
                        if (r != null) rows.add(r);
                    }
                    if (!rows.isEmpty()) out.put(sport + "|" + cc, rows);
                }
                for (Map.Entry<String, String> x : alias.entrySet()) {  // "IE": "GB" (one level)
                    List<Row> rows = out.get(sport + "|" + x.getValue());
                    if (rows != null) out.put(sport + "|" + x.getKey(), rows);
                }
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
        return out;
    }

    private static Row row(String sport, String cc, JSONObject o) {
        Row r = new Row();
        r.sport = sport; r.country = cc;
        JSONArray k = o.optJSONArray(SportEvent.UFC.equals(sport) ? "segments" : "kinds");
        for (int i = 0; k != null && i < k.length(); i++) if (!k.optString(i).trim().isEmpty()) r.kinds.add(k.optString(i).trim());
        JSONArray ev = o.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) if (!ev.optString(i).trim().isEmpty()) r.events.add(ev.optString(i).trim());
        JSONArray on = o.optJSONArray("on");
        for (int i = 0; on != null && i < on.length(); i++) if (!on.optString(i).trim().isEmpty()) r.on.add(on.optString(i).trim());
        r.app = o.isNull("app") ? null : o.optString("app", null);
        r.service = o.optBoolean("service");
        r.from = SeasonTableProvider.day(o.optString("from", ""), 0);
        r.until = SeasonTableProvider.day(o.optString("until", ""), Long.MAX_VALUE);
        r.conf = o.optString("conf", "");
        r.src = o.optString("src", "");
        return r.kinds.isEmpty() || r.on.isEmpty() ? null : r;
    }

    /** The first row of the table for this session, or null. */
    static Row find(Map<String, List<Row>> t, String country, SportEvent e, SportEvent.Session s) {
        if (t == null || country == null || e == null || s == null) return null;
        List<Row> rows = t.get(e.sport + "|" + country);
        if (rows == null) return null;
        for (Row r : rows) if (r.covers(e, s)) return r;
        return null;
    }

    // ---------- 1. the table ----------
    /** The table's guess for every session (no network). Keeps a Greek guide's confirmation while the country is Greece. */
    static void guess(SportEvent e, String country) {
        Map<String, List<Row>> t = table;
        for (SportEvent.Session s : e.sessions) {
            if (s.watch == null) s.watch = new SportEvent.SessionWatch();
            if (Where.greek(country) && confirmed(s.watch)) continue;
            set(s.watch, find(t, country, e, s), Where.greek(country));
        }
    }

    /** A Greek guide's listing: sure, with the channel, not from a table. */
    static boolean confirmed(SportEvent.SessionWatch w) {
        return w != null && w.sure && w.known && (w.listedBy == null || w.listedBy.isEmpty()) && w.channel != null && !w.channel.isEmpty();
    }

    static void set(SportEvent.SessionWatch w, Row r, boolean greece) {
        w.sure = false; w.no = 0; w.logo = null;
        if (r == null) {
            w.known = false; w.channel = ""; w.app = null; w.listedBy = "none";  // "Check your provider's guide"
            return;
        }
        w.known = true;
        w.channel = String.join(" · ", r.on);
        w.app = greece ? r.app : null;  // the app allowlist only knows the Greek apps
        // a Greek channel: the MagentaTV line-up gives its logo; a service or a row abroad: names only
        w.listedBy = greece && !r.service ? "" : "table";
    }

    // ---------- 2. the Greek guides ----------
    /** The Greek guides' confirmations (network for the MagentaTV "UFC" search only); the table again first. */
    static void apply(Context c, List<SportEvent> all, String country) {
        if (!load(c)) return;
        for (SportEvent e : all) guess(e, country);  // the table may have been loaded only now
        if (!Where.greek(country)) return;  // abroad: the table only (the TV's own guide: a later version)
        long now = System.currentTimeMillis();
        boolean f1 = false, ufc = false;
        for (SportEvent e : all)
            for (SportEvent.Session s : e.sessions)
                if (open(s, now)) {
                    if (SportEvent.F1.equals(e.sport)) f1 = true;
                    else if (SportEvent.UFC.equals(e.sport) && isMain(s)) ufc = true;
                }
        if (!f1 && !ufc) return;
        Channels.load(c);
        List<Guide.Airing> s24 = Guide.sport24(c), mag = ufc ? magentaUfc(c) : new ArrayList<Guide.Airing>();
        confirm(all, s24, mag, now);
    }

    /** A session the guides can still confirm: not over, not cancelled, its time known, at most 8 days ahead. */
    static boolean open(SportEvent.Session s, long now) {
        return s.start > now - 6 * H && s.start < now + 8 * DAY && !s.tbd && !"off".equals(s.state) && !"post".equals(s.state);
    }

    static boolean isMain(SportEvent.Session s) { return "MAIN".equals(s.kind) || "main".equals(s.id); }

    /** Sets the guides' confirmations on the open sessions (pure apart from the line-up's numbers). */
    static void confirm(List<SportEvent> all, List<Guide.Airing> s24, List<Guide.Airing> mag, long now) {
        List<Guide.Airing> fights = new ArrayList<>(mag);
        fights.addAll(s24);
        for (SportEvent e : all) {
            for (SportEvent.Session s : e.sessions) {
                if (!open(s, now)) continue;
                Guide.Airing a = null;
                if (SportEvent.F1.equals(e.sport)) a = matchF1(s, s24);
                else if (SportEvent.UFC.equals(e.sport) && isMain(s)) a = matchUfc(e, fights);
                if (a == null) continue;
                if (s.watch == null) s.watch = new SportEvent.SessionWatch();
                listed(s.watch, a);
            }
        }
    }

    /** A guide's airing on the session: the channel, its number (the line-up's when the guide gives none) and logo. */
    static void listed(SportEvent.SessionWatch w, Guide.Airing a) {
        w.channel = a.channel;
        w.no = a.number > 0 ? a.number : Channels.numberCached(a.channel);
        w.logo = a.logo != null && a.logo.startsWith("https://") ? a.logo : null;
        w.app = Guide.app(a.channel);
        w.sure = true; w.known = true; w.listedBy = "";
    }

    /** The airing that confirms an F1 session, or null (pure): Sport24's "Formula 1 <year> - ..." near its start. */
    static Guide.Airing matchF1(SportEvent.Session s, List<Guide.Airing> airings) {
        if (s == null || airings == null || s.start <= 0 || s.tbd) return null;
        Guide.Airing best = null;
        for (Guide.Airing a : airings) {
            if (!a.live || !GENRE_MOTOR.equals(a.genre) || a.title == null || !F1_TITLE.matcher(a.title.trim()).find()) continue;
            if (a.start < s.start - 45 * MIN || a.start > s.start + 10 * MIN) continue;
            if (best == null || Math.abs(a.start - s.start) < Math.abs(best.start - s.start)) best = a;
        }
        return best;
    }

    /** The airing that confirms a UFC event's main card, or null (pure): by time, never by title. */
    static Guide.Airing matchUfc(SportEvent e, List<Guide.Airing> airings) {
        if (e == null || airings == null) return null;
        SportEvent.Session main = null;
        for (SportEvent.Session s : e.sessions) if (isMain(s)) main = s;
        if (main == null || main.start <= 0 || main.tbd) return null;
        Guide.Airing best = null;
        for (Guide.Airing a : airings) {
            if (!a.live || a.title == null || !a.title.toUpperCase(Locale.ROOT).contains("UFC")) continue;
            if (!GENRE_COMBAT.equals(a.genre) && !GENRE_MAGENTA_COMBAT.equalsIgnoreCase(a.genre)) continue;
            long d = Math.abs(a.start - main.start);
            if (d > 30 * MIN) continue;
            int n = tagNumber(a.tag);
            if (e.numbered && n > 0 && n != e.number) continue;  // Sport24's "334: ..." is another numbered event
            if (best == null || d < Math.abs(best.start - main.start)
                || (d == Math.abs(best.start - main.start) && a.number > 0 && best.number == 0)) best = a;  // a tie: the one with a number
        }
        return best;
    }

    /** "334: Σιρίλ Γκάνε - Τζος Χόκιτ" -> 334; 0 when the tag has no number. */
    static int tagNumber(String tag) {
        if (tag == null) return 0;
        Matcher m = TAG_NUMBER.matcher(tag);
        try { return m.find() ? Integer.parseInt(m.group(1)) : 0; } catch (NumberFormatException x) { return 0; }
    }

    /** The MagentaTV search "UFC", cached in guide_magenta_ufc.json for Guide.TTL (an hour); the cache when offline. */
    static List<Guide.Airing> magentaUfc(Context c) {
        if (!BuildConfig.GUIDES) return new ArrayList<>();
        long[] at = {0};
        List<Guide.Airing> cached = Guide.load(c, MAGENTA_FILE, at);
        long now = System.currentTimeMillis();
        if (now - at[0] < Guide.TTL) return cached;
        List<Guide.Airing> out = new ArrayList<>();
        try {
            int from = 0;
            for (int page = 0; page < 3 && from >= 0; page++) {
                JSONObject d = new JSONObject(Net.http("https://www.magentatv.gr/api/search/ott?q="
                    + URLEncoder.encode("UFC", "UTF-8") + "&locale=el&vodStartFrom=-1&epgStartFrom=" + from));
                if (d.optJSONArray("items") == null) throw new Exception("format");
                out.addAll(parseMagenta(d));
                from = d.optBoolean("hasMore") ? d.optInt("nextEpgStartFrom", -1) : -1;
            }
            Guide.save(c, MAGENTA_FILE, out, now);
            return out;
        } catch (Exception x) {
            return cached;
        }
    }

    /** One page of the MagentaTV search: its live UFC airings (pure). Replays (Magenta Sport Start, not live) are left out. */
    static List<Guide.Airing> parseMagenta(JSONObject d) {
        List<Guide.Airing> out = new ArrayList<>();
        JSONArray items = d == null ? null : d.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            try {
                JSONObject it = items.getJSONObject(i);
                if (!"program".equals(it.optString("type"))) continue;
                JSONObject ch = it.optJSONObject("channel"), qoe = it.optJSONObject("qoe");
                if (ch == null || ch.optString("title").isEmpty()) continue;
                Guide.Airing a = new Guide.Airing();
                a.start = Sources.iso(it.optString("startTime"));
                a.title = it.optString("title");
                a.genre = qoe == null ? "" : qoe.optString("genre");
                a.live = it.optBoolean("isLiveEvent") || a.title.contains("(Ζ)");
                a.number = ch.optInt("number");
                a.channel = Guide.clean(ch.optString("title"));
                a.tag = "";
                a.id = it.optString("id", "");
                JSONObject logos = ch.optJSONObject("logos");
                a.logo = logos == null ? null : logos.optString("wide", logos.optString("square", null));
                if (a.start > 0 && a.live && a.title.toUpperCase(Locale.ROOT).contains("UFC")) out.add(a);
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ---------- words, channel and logos ----------
    /**
     * Where to watch, in football's words (Cards.where): "Likely ANT1 · on Magenta TV", "ANT1 · ch. 107 on Magenta TV",
     * "Likely on ANT1+", "Likely on Sky Sports F1"; "Check your provider's guide" where the table has no row; "" before
     * the table was asked.
     */
    static String where(SportEvent.Session s, boolean lineStart) {
        if (s == null || s.watch == null) return "";
        Match m = s.watch.asMatch(s.start, "");
        if (!m.channelKnown) return Where.noData(m) ? I18n.t("check_guide") : "";
        return Cards.where(m, lineStart);
    }

    /** The card's channel, through SessionWatch.asMatch and CardModel.channel; null when nothing is known. */
    static CardModel.Channel channel(SportEvent.Session s) {
        if (s == null || s.watch == null) return null;
        return CardModel.channel(s.watch.asMatch(s.start, ""));
    }

    /** Logos from the MagentaTV line-up for Greek channels (ANT1, Magenta Sport 6); services and abroad: none. */
    static List<Channels.Logo> logos(Context c, SportEvent.Session s) {
        if (s == null || s.watch == null) return new ArrayList<>();
        return Channels.logos(c, s.watch.asMatch(s.start, ""));
    }

    /** The app's name for the launcher's words ("Magenta TV", "ANT1+", "SPORT FM TV"). */
    static String appName(String pkg) {
        return Cards.ANT1.equals(pkg) ? "ANT1+" : Cards.appName(pkg);
    }
}
