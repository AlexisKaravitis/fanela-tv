package app.fanela.tv;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fixtures and scores for one followed club. Each fetch reports whether it fully succeeded, so a failing feed keeps
 * its last good matches (Store). Sources: ESPN (football, every competition ESPN covers; the NBA), EuroLeague / EuroCup
 * official API (basketball), TheSportsDB next/last (Greek Basket League / BCL clubs). Missing Greek cup / basketball
 * games are added from the Sport24 TV guide (Guide.discover). The expected channel is set while parsing (Where.guess).
 */
final class Sources {
    static final class Result {
        final String source;
        boolean ok;
        final List<Match> list = new ArrayList<>();
        Result(String s) { source = s; }
    }

    static List<Result> fetch(Context c, Teams.Club k, boolean basket) {
        List<Result> r = new ArrayList<>();
        if (k.nba()) { r.add(nba(k)); return r; }
        if (k.euroOnly()) {  // basketball only, whatever the basketball setting says
            if (k.bcComp != null) r.add(euro(k));
            else { Result none = new Result("el"); none.ok = true; r.add(none); }  // in neither competition this season
            return r;
        }
        r.add(espn(k));
        if (basket && k.hasBasket) {
            boolean euroGames = false;
            if (k.bcComp != null) { Result e = euro(k); r.add(e); euroGames = !e.list.isEmpty() || !e.ok; }
            String tid = Teams.tsdbBasket(c, k);
            if (tid != null) r.add(tsdbBc(k, tid, euroGames));
        }
        return r;
    }

    // ---------- time ----------
    static long iso(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0;
        s = s.trim();
        if (s.length() == 19 && s.charAt(10) == 'T') s = s + "Z";
        for (String p : new String[]{"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                f.setLenient(false);
                return f.parse(s).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    static int seasonOf(long t) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Athens"));
        c.setTimeInMillis(t);
        return c.get(Calendar.MONTH) >= Calendar.JULY ? c.get(Calendar.YEAR) : c.get(Calendar.YEAR) - 1;
    }

    // ---------- football: ESPN ----------
    static Result espn(Teams.Club k) {
        Result r = new Result("espn");
        Map<String, Match> seen = new LinkedHashMap<>();
        boolean a = espnSchedule(k, "all", "?fixture=true", seen), b = espnSchedule(k, "all", "", seen);
        r.ok = a && b;
        String lg = Leagues.get(k.league) != null && !Leagues.get(k.league).basketball() ? k.league : "gre.1";
        if (!a) espnSchedule(k, lg, "?fixture=true", seen);  // fallback: at least the league
        if (!b) espnSchedule(k, lg, "", seen);
        r.list.addAll(seen.values());
        return r;
    }

    private static boolean espnSchedule(Teams.Club k, String lg, String q, Map<String, Match> into) {
        try {
            JSONObject d = new JSONObject(Net.http("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg + "/teams/"
                + k.id + "/schedule" + q));
            JSONArray ev = d.optJSONArray("events");
            if (ev == null) return false;
            int parsed = 0;
            for (int i = 0; i < ev.length(); i++) {
                try {
                    Match m = espn(ev.getJSONObject(i), lg, k.id);
                    if (m != null) { parsed++; if (!into.containsKey(m.key)) into.put(m.key, m); }
                } catch (Exception ignored) { }
            }
            return ev.length() == 0 || parsed > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static String shootout(JSONObject x) {
        Object s = x.opt("shootoutScore");
        if (s == null && x.optJSONObject("score") != null) s = x.optJSONObject("score").opt("shootoutScore");
        if (s == null || s == JSONObject.NULL) return null;
        try { return String.valueOf((int) Double.parseDouble(s.toString())); } catch (Exception e) { return null; }
    }

    static Match espn(JSONObject e, String lgFallback, String clubId) throws Exception {
        JSONObject league = e.optJSONObject("league");
        String lg = league != null && !league.optString("slug").isEmpty() ? league.optString("slug") : lgFallback;
        if ("all".equals(lg)) return null;
        Match m = new Match();
        m.club = clubId; m.team = "FC"; m.source = "espn"; m.league = lg; m.comp = lg;
        if (!I18n.has(lg) && league != null)  // a competition we have no label for: ESPN's own name, never a raw slug
            m.comp = league.optString("name", league.optString("abbreviation", lg));
        m.key = clubId + "|espn:" + e.optString("id");
        m.start = iso(e.optString("date"));
        if (m.start == 0) return null;
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = c.getJSONObject("status").getJSONObject("type");
        String name = type.optString("name", "");
        m.state = type.optString("state", "pre");
        String sd = type.optString("shortDetail", "");
        m.tbd = sd.contains("TBD") || !e.optBoolean("timeValid", true) || !c.optBoolean("timeValid", true);
        m.detail = sd;
        if (name.contains("POSTPONED")) { m.state = "off"; m.detail = "POSTPONED"; }
        else if (name.contains("CANCEL")) { m.state = "off"; m.detail = "CANCELLED"; }
        else if (name.contains("ABANDONED") || name.contains("FORFEIT")) { m.state = "off"; m.detail = "ABANDONED"; }
        else if (name.contains("SUSPENDED")) { m.state = "in"; m.detail = "SUSPENDED"; }
        else if (name.contains("DELAYED") && "pre".equals(m.state)) { m.detail = "DELAYED"; }
        else if ("post".equals(m.state)) m.detail = name.contains("PEN") ? "FT_PEN" : name.contains("AET") ? "FT_AET" : "FT";
        else if ("in".equals(m.state)) {
            if (name.contains("HALFTIME_ET")) m.detail = "ET_HT";
            else if (name.contains("SHOOTOUT")) m.detail = "SHOOTOUT";
            else if (name.contains("END_OF_REGULATION")) m.detail = "END_90";
            else if (name.contains("END_OF_EXTRATIME")) m.detail = "END_120";
            else if (name.contains("HALFTIME") || "HT".equals(sd)) m.detail = "HT";
            else m.detail = sd;
        }

        JSONArray cs = c.getJSONArray("competitors");
        String hp = null, ap = null;
        Boolean clubWinner = null, oppWinner = null;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject x = cs.getJSONObject(i);
            JSONObject tm = x.getJSONObject("team");
            String nm = tm.optString("displayName");
            String crest = tm.optString("logo", "");
            JSONArray logos = tm.optJSONArray("logos");
            if (crest.isEmpty() && logos != null && logos.length() > 0) crest = logos.getJSONObject(0).optString("href", "");
            Object sc = x.opt("score");
            String s = null;
            if (sc instanceof JSONObject) s = ((JSONObject) sc).optString("displayValue", null);
            else if (sc != null && sc != JSONObject.NULL) s = sc.toString();
            if ("pre".equals(m.state) || ("off".equals(m.state) && !name.contains("ABANDONED"))) s = null;
            boolean home = "home".equals(x.optString("homeAway"));
            boolean mine = clubId.equals(tm.optString("id"));
            // boxed on purpose: a game not played yet may have no "winner" at all (NBA), and boolean ? : null unboxes to a crash
            Boolean winner = x.has("winner") ? Boolean.valueOf(x.optBoolean("winner")) : sc instanceof JSONObject && ((JSONObject) sc).has("winner")
                ? Boolean.valueOf(((JSONObject) sc).optBoolean("winner")) : null;
            if (home) { m.home = nm; m.hs = s; m.homeCrest = crest; hp = shootout(x); m.clubIsHome = mine; m.homeId = tm.optString("id"); }
            else { m.away = nm; m.as = s; m.awayCrest = crest; ap = shootout(x); }
            if (mine) clubWinner = winner; else oppWinner = winner;
        }
        if (hp != null && ap != null) m.pens = hp + "–" + ap;
        if ("post".equals(m.state) && m.hasScore()) {
            if (Boolean.TRUE.equals(clubWinner)) m.clubWon = 1;
            else if (Boolean.TRUE.equals(oppWinner)) m.clubWon = -1;
            else if (m.pens != null) {
                int ph = Match.toInt(hp), pa = Match.toInt(ap);
                m.clubWon = Integer.compare(m.clubHome() ? ph : pa, m.clubHome() ? pa : ph);
            } else m.clubWon = Integer.compare(Match.toInt(m.clubScore()), Match.toInt(m.oppScore()));
        }
        Where.guess(m);
        return m;
    }

    /** Live refresh of one football match from its league scoreboard (UTC date, then the day before). */
    static Match espnLive(Match m) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String id = m.key.substring(m.key.indexOf("espn:") + 5);
        for (long day : new long[]{m.start, m.start - 86400000L}) {
            try {
                JSONArray ev = new JSONObject(Net.httpLive("https://site.api.espn.com/apis/site/v2/sports/soccer/" + m.league
                    + "/scoreboard?dates=" + f.format(day))).optJSONArray("events");
                for (int i = 0; ev != null && i < ev.length(); i++)
                    if (id.equals(ev.getJSONObject(i).optString("id"))) {
                        Match n = espn(ev.getJSONObject(i), m.league, m.club);
                        if (n != null) return n;
                    }
            } catch (Exception ignored) { }
        }
        return null;
    }

    // ---------- basketball: NBA (ESPN) ----------
    static Result nba(Teams.Club k) {
        Result r = new Result("nba");
        try {
            JSONObject d = new JSONObject(Net.http("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/" + k.espnId() + "/schedule"));
            JSONArray ev = d.optJSONArray("events");
            if (ev == null) return r;
            for (int i = 0; i < ev.length(); i++) {
                try { Match m = nbaEvent(ev.getJSONObject(i), k.id); if (m != null) r.list.add(m); } catch (Exception ignored) { }
            }
            r.ok = ev.length() == 0 || !r.list.isEmpty();
        } catch (Exception ignored) { }
        return r;
    }

    /** An NBA game from ESPN's schedule or scoreboard; club is the followed club's id ("nba13"). */
    static Match nbaEvent(JSONObject e, String club) throws Exception {
        Match m = espn(e, "nba", club.substring(3));
        if (m == null) return null;
        m.club = club; m.team = "BC"; m.source = "nba"; m.league = "nba"; m.comp = "nba";
        m.key = club + "|nba:" + e.optString("id");
        JSONObject st = e.getJSONArray("competitions").getJSONObject(0).getJSONObject("status");
        String name = st.getJSONObject("type").optString("name", ""), sd = st.getJSONObject("type").optString("shortDetail", "");
        if ("in".equals(m.state) && !"SUSPENDED".equals(m.detail)) {
            int p = st.optInt("period");
            String clock = st.optString("displayClock", "").trim();
            m.quarter = p;
            if (name.contains("HALFTIME")) m.detail = "HT";
            else if (name.contains("END_PERIOD")) m.detail = "BREAK";
            else m.detail = (p > 4 ? "OT" + (p - 4 > 1 ? (p - 4) : "") : "Q" + Math.max(1, p)) + (clock.isEmpty() ? "" : " " + clock);
        } else if ("post".equals(m.state)) m.detail = sd.contains("OT") ? "FT_AET" : "FT";
        return m;
    }

    /** Live refresh of one NBA game from the scoreboard (ESPN dates are US days: the UTC date, then the day before). */
    static Match nbaLive(Match m) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String id = m.key.substring(m.key.indexOf("nba:") + 4);
        for (long day : new long[]{m.start, m.start - 86400000L}) {
            try {
                JSONArray ev = new JSONObject(Net.httpLive("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates="
                    + f.format(day))).optJSONArray("events");
                for (int i = 0; ev != null && i < ev.length(); i++)
                    if (id.equals(ev.getJSONObject(i).optString("id"))) {
                        Match n = nbaEvent(ev.getJSONObject(i), m.club);
                        if (n != null) return n;
                    }
            } catch (Exception ignored) { }
        }
        return null;
    }

    // ---------- basketball: EuroLeague (E) / EuroCup (U) ----------
    static Result euro(Teams.Club k) {
        String comp = k.bcComp;
        Result r = new Result("E".equals(comp) ? "el" : "ec");
        String season = comp + seasonOf(System.currentTimeMillis());
        try {
            JSONObject root = new JSONObject(Net.http("https://api-live.euroleague.net/v2/competitions/" + comp + "/seasons/" + season
                + "/games?teamCode=" + k.bcCode));
            JSONArray d = root.optJSONArray("data");
            if (d == null) return r;
            for (int i = 0; i < d.length(); i++) {
                try {
                    JSONObject g = d.getJSONObject(i);
                    JSONObject lc = g.getJSONObject("local").getJSONObject("club"), rc = g.getJSONObject("road").getJSONObject("club");
                    Match m = new Match();
                    m.club = k.id; m.team = "BC"; m.source = r.source; m.league = "E".equals(comp) ? "euroleague" : "eurocup";
                    JSONObject ph = g.optJSONObject("phaseType");
                    String phase = ph == null ? "RS" : ph.optString("code", "RS");
                    m.comp = "E".equals(comp) ? ("PO".equals(phase) ? "euroleague.po" : "FF".equals(phase) ? "euroleague.ff"
                        : "PI".equals(phase) ? "euroleague.pi" : "euroleague") : "eurocup";
                    m.key = k.id + "|" + r.source + ":" + season + ":" + g.optInt("gameCode");
                    m.start = iso(g.optString("utcDate"));
                    if (m.start == 0) continue;
                    m.clubIsHome = k.bcCode.equals(lc.optString("code"));
                    m.home = lc.optString("abbreviatedName", lc.optString("name")).trim();
                    m.away = rc.optString("abbreviatedName", rc.optString("name")).trim();
                    JSONObject li = lc.optJSONObject("images"), ri = rc.optJSONObject("images");
                    m.homeCrest = li == null ? "" : li.optString("crest", "");
                    m.awayCrest = ri == null ? "" : ri.optString("crest", "");
                    if (g.optBoolean("played")) {
                        m.state = "post";
                        m.hs = String.valueOf(g.getJSONObject("local").optInt("score"));
                        m.as = String.valueOf(g.getJSONObject("road").optInt("score"));
                        m.detail = "FT";
                        m.clubWon = Integer.compare(Match.toInt(m.clubScore()), Match.toInt(m.oppScore()));
                    } else m.state = "pre";
                    Where.guess(m);
                    r.list.add(m);
                } catch (Exception ignored) { }
            }
            r.ok = d.length() == 0 || !r.list.isEmpty();
        } catch (Exception ignored) { }
        return r;
    }

    /** Live state from the EuroLeague/EuroCup live header. Final only when the clock really ended. */
    static Match euroLive(Match m) {
        try {
            String[] k = m.key.substring(m.key.indexOf('|') + 1).split(":");  // el:E2026:107
            JSONObject h = new JSONObject(Net.httpLive("https://live.euroleague.net/api/Header?gamecode=" + k[2] + "&seasoncode=" + k[1]));
            Match n = m.copy();
            String a = h.optString("ScoreA", "").trim(), b = h.optString("ScoreB", "").trim();
            String q = h.optString("Quarter", "").trim(), rem = h.optString("RemainingPartialTime", "").trim();
            if (h.optBoolean("Live")) {
                int qn = parseQuarter(q);
                if (qn >= 1 || !a.isEmpty() && !"0".equals(a)) {
                    n.state = "in"; n.quarter = qn;
                    if (!a.isEmpty()) { n.hs = a; n.as = b; }
                    n.detail = (qn > 4 ? "OT" + (qn - 4 > 1 ? (qn - 4) : "") : qn > 0 ? "Q" + qn : "") + (rem.isEmpty() ? "" : " " + rem);
                    n.seenLiveAt = System.currentTimeMillis();
                }
            } else if (q.isEmpty() && "00:00".equals(rem) && !a.isEmpty() && !a.equals(b) && System.currentTimeMillis() > m.start + 60 * 60000L) {
                n.state = "post"; n.hs = a; n.as = b; n.detail = "FT";
                n.clubWon = Integer.compare(Match.toInt(n.clubScore()), Match.toInt(n.oppScore()));
            }
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    static int parseQuarter(String q) {
        if (q == null || q.isEmpty()) return 0;
        String u = q.toUpperCase(Locale.ROOT), digits = u.replaceAll("[^0-9]", "");
        int d = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        if (u.startsWith("OT") || u.contains("EXTRA")) return 4 + Math.max(1, d);
        return d;
    }

    // ---------- basketball: TheSportsDB next/last (Greek Basket League, BCL) ----------
    /** Basketball competition code from TheSportsDB's league name. */
    static String bcComp(String lg) {
        String l = lg.toLowerCase(Locale.ROOT);
        if (l.contains("champions")) return "bcl";
        if (l.contains("europe cup")) return "fiba.europe_cup";
        if (l.contains("eurocup")) return "eurocup";
        if (l.contains("euroleague")) return "euroleague";
        if (l.contains("cup")) return "gbl.cup";
        return "gbl";
    }

    static Result tsdbBc(Teams.Club k, String tid, boolean euroCovered) {
        Result r = new Result("tsdb");
        boolean ok = true;
        for (String ep : new String[]{"eventsnext.php", "eventslast.php"}) {
            try {
                JSONObject d = new JSONObject(Net.http("https://www.thesportsdb.com/api/v1/json/123/" + ep + "?id=" + tid));
                JSONArray ev = d.optJSONArray("events");
                if (ev == null) ev = d.optJSONArray("results");
                for (int i = 0; ev != null && i < ev.length(); i++) {
                    try {
                        JSONObject e = ev.getJSONObject(i);
                        String lg = e.optString("strLeague").toLowerCase(Locale.ROOT);
                        String comp = bcComp(lg);
                        if (euroCovered && ("euroleague".equals(comp) || "eurocup".equals(comp))) continue;  // own API
                        Match m = tsdb(e, k.id, tid);
                        m.comp = comp;
                        m.league = comp.startsWith("gbl") ? "gbl" : comp;
                        Where.guess(m);
                        r.list.add(m);
                    } catch (Exception ignored) { }
                }
                Thread.sleep(1500);  // TheSportsDB's free key is rate-limited: space the calls
            } catch (Exception e) { ok = false; }
        }
        r.ok = ok;
        return r;
    }

    static Match tsdb(JSONObject e, String clubId, String tid) {
        Match m = new Match();
        m.club = clubId; m.team = "BC"; m.source = "tsdb";
        m.key = clubId + "|tsdb:" + e.optString("idEvent");
        String ts = e.optString("strTimestamp", ""), time = e.optString("strTime", "");
        m.tbd = time.isEmpty() || "null".equals(time) || time.startsWith("00:00");
        m.start = iso(ts.isEmpty() || "null".equals(ts) ? e.optString("dateEvent") + "T" + (m.tbd ? "12:00:00" : time) : ts);
        m.clubIsHome = tid.equals(e.optString("idHomeTeam"));
        m.home = e.optString("strHomeTeam");
        m.away = e.optString("strAwayTeam");
        m.homeCrest = e.optString("strHomeTeamBadge", "");
        m.awayCrest = e.optString("strAwayTeamBadge", "");
        String h = e.optString("intHomeScore", ""), a = e.optString("intAwayScore", ""), status = e.optString("strStatus", "");
        boolean scored = !h.isEmpty() && !"null".equals(h) && !a.isEmpty() && !"null".equals(a);
        boolean finished = "FT".equals(status) || "AOT".equals(status) || "AP".equals(status)
            || status.toLowerCase(Locale.ROOT).contains("finished") || (scored && System.currentTimeMillis() > m.start + 150 * 60000L);
        String st = status.toUpperCase(Locale.ROOT);
        if ("yes".equalsIgnoreCase(e.optString("strPostponed")) || "PST".equals(st) || "POST".equals(st)) { m.state = "off"; m.detail = "POSTPONED"; }
        else if ("CANC".equals(st)) { m.state = "off"; m.detail = "CANCELLED"; }
        else if ("ABD".equals(st)) { m.state = "off"; m.detail = "ABANDONED"; }
        else if ("SUSP".equals(st) && scored) { m.state = "in"; m.hs = h; m.as = a; m.detail = "SUSPENDED"; }
        else if (scored && finished) {
            m.state = "post"; m.hs = h; m.as = a; m.detail = "AOT".equals(st) ? "FT_AET" : "FT";
            m.clubWon = Integer.compare(Match.toInt(m.clubScore()), Match.toInt(m.oppScore()));
        } else if (scored) {
            m.state = "in"; m.hs = h; m.as = a;
            m.detail = "HT".equals(st) ? "HT" : "BT".equals(st) ? "BREAK" : st.matches("Q[1-4]") ? st : st.startsWith("OT") ? st : "IN_PROGRESS";
        }
        else m.state = "pre";
        return m;
    }
}
