package app.fanela.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the home screen shows, worked out once per load for every layout: per followed club the hero match (live,
 * a fresh result, or the next one), the latest results, the fixtures to come and the rail's one-line awareness, plus
 * the status slot. The choices and the words are plain Java on Match (unit-tested); a layout only draws them.
 */
final class HomeModel {
    enum Status { CLOCK, UPDATING, OFFLINE }
    enum Kind { NEXT, LIVE, RESULT, OFF, NONE, LOADING, FAILED }

    /** A result stays the hero this long after full time (6 h), and the rail's line for 24 h. FT = kick-off + 2 h. */
    static final long FT = 2 * 3600000L, RESULT_HERO = 6 * 3600000L, RESULT_RAIL = 24 * 3600000L;
    static final int MAX_ROWS = 12;

    long now;
    Status status = Status.CLOCK;
    /** The status slot's text: the clock, "Updating…", or the offline sentence. */
    String statusText = "";
    final List<ClubData> clubs = new ArrayList<>();
    /** The club the app wears when nothing else is chosen. */
    String favourite;

    boolean empty() { return clubs.isEmpty(); }

    ClubData club(String id) {
        for (ClubData d : clubs) if (d.club.id.equals(id)) return d;
        return null;
    }

    static final class ClubData {
        Teams.Club club;
        FieldColors colors;
        Kind kind = Kind.NONE;
        Match hero;
        final List<Match> latest = new ArrayList<>(), fixtures = new ArrayList<>();
        String rail = "";
        boolean railLive;
        /** The club plays both sports here: sport glyphs and words are shown. */
        boolean bothSports;
        boolean stale;
        /** The layout's prepared images for this club (HomeLayout.prepare), opaque to the model. */
        Object art;

        /** Everything that changes the club's rows or tiles (a change rebuilds them; the same keys update in place). */
        String structure() {
            StringBuilder b = new StringBuilder(club.id).append(kind).append(hero == null ? "" : hero.key);
            for (Match m : latest) b.append(',').append(m.key);
            b.append('|');
            for (Match m : fixtures) b.append(',').append(m.key);
            return b.toString();
        }
    }

    /** How long without an update before a failed refresh shows the offline line. */
    static final long OFFLINE_AFTER = 15 * 60000L;

    /**
     * Whether the status slot says the app is offline (QA3G-3): never before a refresh of ours has finished, nor with no
     * club to show; always when nothing was ever loaded or a club's feed has not answered for hours (stale); else only
     * when a refresh found no feed answering after the last success (failedAt), and that success is over 15 min old.
     * Android's own view of the network (VALIDATED) plays no part: a TV whose captive-portal check is blocked still
     * downloads, and showed the line falsely.
     */
    static boolean offline(boolean tried, boolean empty, long last, boolean stale, long failedAt, long now) {
        if (!tried || empty) return false;
        if (last == 0 || stale) return true;
        return failedAt > last && now - last > OFFLINE_AFTER;
    }

    // ---------- choosing ----------
    static boolean of(Match m, String club, String[] teams) {
        if (!club.equals(m.club)) return false;
        for (String t : teams) if (t.equals(m.team)) return true;
        return false;
    }

    /** The hero: a match in play, else a result under 6 h after full time, else the next (or a fresh postponement). */
    static Match hero(List<Match> all, String club, String[] teams, long now) {
        Match live = null, fresh = null;
        for (Match m : all) {
            if (!of(m, club, teams)) continue;
            if ("in".equals(m.state) && (live == null || m.start < live.start)) live = m;
            if ("post".equals(m.state) && m.hasScore() && m.start <= now && now - (m.start + FT) < RESULT_HERO
                && (fresh == null || m.start > fresh.start)) fresh = m;
        }
        if (live != null) return live;
        if (fresh != null) return fresh;
        Match next = null;
        for (String t : teams) {
            Match n = Cards.pickNow(all, club, t, now);
            if (n != null && (next == null || n.start < next.start)) next = n;
        }
        return next;
    }

    static Kind kind(Match m) {
        if (m == null) return Kind.NONE;
        if ("in".equals(m.state)) return Kind.LIVE;
        if ("post".equals(m.state)) return Kind.RESULT;
        if ("off".equals(m.state)) return Kind.OFF;
        return Kind.NEXT;
    }

    /** The latest results, newest first: one per sport, or the last two when the club shows one sport. */
    static List<Match> latest(List<Match> all, String club, String[] teams, Match hero) {
        List<Match> out = new ArrayList<>();
        for (String t : teams) {
            int n = 0;
            for (Match m : Cards.results(all, club, t, 3)) {
                if (hero != null && m.key.equals(hero.key)) continue;
                if (n++ < (teams.length == 1 ? 2 : 1)) out.add(m);
            }
        }
        Collections.sort(out, new Comparator<Match>() { public int compare(Match a, Match b) { return Long.compare(b.start, a.start); } });
        return out.size() > 2 ? new ArrayList<>(out.subList(0, 2)) : out;
    }

    /** The coming matches of every sport shown, after the hero, by kick-off (postponed ones keep their place). */
    static List<Match> fixtures(List<Match> all, String club, String[] teams, Match hero, long now) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) {
            if (!of(m, club, teams) || (hero != null && m.key.equals(hero.key))) continue;
            boolean coming = "pre".equals(m.state) && (m.start > now || (m.tbd && AppText.days(now, m.start) == 0));
            boolean off = "off".equals(m.state) && m.start > now - 12 * 3600000L;
            if (coming || off) out.add(m);
        }
        Collections.sort(out, new Comparator<Match>() { public int compare(Match a, Match b) { return Long.compare(a.start, b.start); } });
        return out.size() > MAX_ROWS ? new ArrayList<>(out.subList(0, MAX_ROWS)) : out;
    }

    /** The rail's line under a club: its LIVE score, a result for 24 h after full time, else the next kick-off. */
    static void rail(ClubData d, List<Match> all, String[] teams, long now) {
        String club = d.club.id;
        Match live = null, last = null, next = null;
        for (Match m : all) {
            if (!of(m, club, teams)) continue;
            if ("in".equals(m.state) && m.hasScore()) live = m;
            if ("post".equals(m.state) && m.hasScore() && m.start <= now && (last == null || m.start > last.start)) last = m;
        }
        d.railLive = live != null;
        if (live != null) { d.rail = I18n.t("live") + " " + mineFirst(live); return; }
        if (last != null && now - (last.start + FT) < RESULT_RAIL) {
            int r = last.result();
            d.rail = AppText.f(r > 0 ? "won" : r < 0 ? "lost" : "drew", mineFirst(last));
            return;
        }
        for (String t : teams) {
            Match n = Cards.pickNow(all, club, t, now);
            if (n != null && "pre".equals(n.state) && (next == null || n.start < next.start)) next = n;
        }
        d.rail = next == null ? "" : AppText.railNext(now, next);
    }

    /** "79–56": the followed club's score first (the rail's words read from its side). */
    static String mineFirst(Match m) { return m.clubScore() + "–" + m.oppScore(); }

    // ---------- words ----------
    static String sport(Match m) { return I18n.t("FC".equals(m.team) ? "football_group" : "basketball_group"); }

    /** "NEXT · EUROLEAGUE · BASKETBALL" (the sport only where the club plays both). */
    static String eyebrow(Kind k, Match m, boolean bothSports) {
        String lead = k == Kind.RESULT ? AppText.t("full_time") : k == Kind.OFF ? I18n.detail(m.detail) : AppText.t("next");
        return Cards.caps(lead + " · " + I18n.comp(m.comp) + (bothSports ? " · " + sport(m) : ""));
    }

    /** The live hero's eyebrow: "EUROLEAGUE · BASKETBALL · THURSDAY 12 NOVEMBER · 21:15". */
    static String liveEyebrow(Match m, boolean bothSports) {
        return Cards.caps(I18n.comp(m.comp) + (bothSports ? " · " + sport(m) : "") + " · " + AppText.longDate(m.start)
            + (m.tbd ? "" : " · " + I18n.time(m.start)));
    }

    /**
     * The short phase in the live cell (136 px): "Q3", "OT" / "ΠΑΡ.", "67′", "HT" / "ΗΜ.", and short words for the
     * breaks ("BREAK" / "ΔΙΑΛ.", "PENS" / "ΠΕΝ.", "90′"), as the cards write them; "" when the feed gives none (or only
     * "awaiting result", which the clock line under the score says).
     */
    static String phase(Match m) {
        String d = m.detail == null ? "" : m.detail.trim();
        if (d.isEmpty() || "IN_PROGRESS".equals(d) || "AWAITING".equals(d)) return "";
        if ("HT".equals(d)) return CardModel.w("ht");
        if (period(d)) return periodWord(d.split("\\s+")[0]);
        if (d.matches("\\d+'.*")) return minute(d);
        String w = AppText.t("ph_" + d);
        return w.equals("ph_" + d) ? Cards.caps(I18n.detail(d)) : w;
    }

    /** A basketball period with or without its clock: "Q3", "Q3 5:12", "OT", "OT2 1:05". */
    static boolean period(String detail) { return detail != null && detail.matches("(Q\\d|OT\\d*)(\\s+.*)?"); }

    /** "Q3" as it is; overtime in the interface's language, as the cards write it: "OT2" / "ΠΑΡ.2". */
    static String periodWord(String p) {
        return p.startsWith("OT") && I18n.el() ? CardModel.w("ot") + p.substring(2) : p;
    }

    /** The clock under the live score: "5:12 left", "67′", "Half-time"; "" when unknown. */
    static String clock(Match m) {
        String d = m.detail == null ? "" : m.detail.trim();
        if (d.isEmpty() || "IN_PROGRESS".equals(d)) return "";
        if ("HT".equals(d)) return AppText.t("half_time");
        if (d.matches("(Q\\d|OT\\d*)\\s+\\d{1,2}:\\d{2}")) {
            String c = d.split("\\s+")[1];
            if (c.startsWith("0") && c.length() > 4) c = c.substring(1);
            return AppText.f("left", c);
        }
        if (d.matches("\\d+'.*")) return minute(d);
        return I18n.detail(d);
    }

    /** 67' -> 67′, 90'+3' -> 90+3′ (the prime the scoreboards use). */
    static String minute(String d) {
        String s = d.replace("'", "");
        return s + "′";
    }

    /** "Olympiacos lead by 3" / "All square"; "" without a score. */
    static String lead(Match m) {
        if (!m.hasScore()) return "";
        int h = Match.toInt(m.hs), a = Match.toInt(m.as);
        if (h == a) return AppText.t("level");
        return AppText.f("lead", I18n.team(h > a ? m.home : m.away), Math.abs(h - a));
    }

    /** The live sub-line before the channel: "Olympiacos lead by 3 · Q3 · 5:12 left" (a break: only its words). */
    static String liveLine(Match m) {
        StringBuilder b = new StringBuilder(lead(m));
        String p = phase(m), c = clock(m);
        if (period(m.detail == null ? "" : m.detail.trim()) && !p.equals(c)) b.append(b.length() > 0 ? " · " : "").append(p);
        if (!c.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(c);
        return b.toString();
    }

    /** One sentence a screen reader says for a match. */
    static String speak(Match m, Kind k, long now) {
        StringBuilder b = new StringBuilder();
        if (k == Kind.LIVE) b.append(I18n.t("live")).append(". ");
        b.append(k == Kind.LIVE || k == Kind.RESULT ? m.scoreLine() : m.title()).append(". ").append(I18n.comp(m.comp));
        if (k == Kind.LIVE) { String l = liveLine(m); if (!l.isEmpty()) b.append(". ").append(l); }
        else if (k == Kind.RESULT) {
            int r = m.result();
            if (r != 9) b.append(". ").append(I18n.t(r > 0 ? "win" : r == 0 ? "draw" : "loss"));
            b.append(". ").append(AppText.shortDate(m.start));
        } else if (k == Kind.OFF) b.append(". ").append(I18n.detail(m.detail)).append(", ").append(AppText.shortDate(m.start));
        else b.append(". ").append(AppText.relative(now, m.start)).append(", ").append(AppText.longDate(m.start))
            .append(m.tbd ? ", " + I18n.t("time_tba") : ", " + I18n.time(m.start));  // spoken: "Ώρα: θα οριστεί"
        if (k == Kind.NEXT || k == Kind.LIVE) b.append(". ").append(Cards.where(m, true));
        return b.toString();
    }

    // ---- wave C home ----
    /** Every saved match the model was built from (Matchday's rows, the match screen's last five and return leg). */
    List<Match> all = new ArrayList<>();
    /** The sports shown per club id ("FC", "BC"), as the hero and the fixtures were chosen. */
    final java.util.Map<String, String[]> teams = new java.util.HashMap<>();

    String[] teams(ClubData d) {
        String[] t = teams.get(d.club.id);
        return t != null ? t : new String[]{"FC", "BC"};
    }
}
