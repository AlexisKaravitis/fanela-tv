package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sports' home-screen rows (F1-UFC-ARCHITECTURE §5.1): one row per followed sport, keyed "sport_f1" / "sport_ufc"
 * (never "club_": club-row housekeeping never touches them), named "Formula 1" (or "Formula 1 · Norris") and "UFC",
 * with two tiles as for clubs: "now" (cs<sport>_tvnow.png) and "last" (cs<sport>_tvlast.png). The picks are pure and
 * unit-tested; a card is redrawn only when its signature changes, the row only when a tile does. Stream D owns this file.
 */
final class EventCards {
    private EventCards() { }

    static final int CARD_VERSION = 3;  // 3: the final polish (who on one line, paired names, chip 44, stale 44); 2: no race number on results (QAS-10). Events only: Cards.CARD_VERSION is untouched, football cards are not redrawn
    /** The row logo's version (EventArt.rowLogo: the monogram disc); a new value redraws it once. */
    static final String ROW_LOGO = "1";
    static final long H = 3600000L;

    /** A tile's subject: the event, the session it is about (null: the event as a whole, UFC), and whether it is the "last" tile. */
    static final class Pick {
        SportEvent e; String sessionId; boolean last;

        Pick() { }
        Pick(SportEvent e, String sessionId, boolean last) { this.e = e; this.sessionId = sessionId; this.last = last; }
    }

    // ---------- picks (pure) ----------
    /** The sport's events the viewer's lists show (Sports.shows). */
    static List<SportEvent> shown(List<SportEvent> all, String sport, SportPrefs p) {
        List<SportEvent> out = new ArrayList<>();
        if (all == null) return out;
        for (SportEvent e : all) if (e != null && sport.equals(e.sport) && Sports.shows(p, e)) out.add(e);
        return out;
    }

    /**
     * The "now" tile. F1: a live session, else the current or next weekend: before the weekend (its first session more
     * than a day away) the card is about the race, then about each next session; a cancelled weekend until 12 h after
     * its race (never in place of an earlier one). UFC: the live event, else the next event shown, else a cancelled one
     * for 12 h; the card itself picks the headline bout. Null: nothing to come (the row then shows "last" only).
     */
    static Pick pickNow(List<SportEvent> all, String sport, SportPrefs p, long now) {
        if (p == null) p = SportPrefs.none();
        List<SportEvent> evs = shown(all, sport, p);
        return SportEvent.F1.equals(sport) ? nowF1(evs, now) : nowUfc(evs, now);
    }

    private static Pick nowF1(List<SportEvent> evs, long now) {
        for (SportEvent e : evs) {
            SportEvent.Session live = e.live();
            if (live != null && !"off".equals(e.state)) return new Pick(e, live.id, false);
        }
        SportEvent pre = null, off = null;
        for (SportEvent e : evs) {
            if ("off".equals(e.state)) {
                SportEvent.Session race = EventCardModel.scoring(e);
                long at = race == null ? e.end : race.start;
                if (at >= now - 12 * H && (off == null || e.start < off.start)) off = e;
                continue;
            }
            if ("post".equals(e.state) || coming(e, now) == null) continue;
            if (pre == null || e.start < pre.start) pre = e;
        }
        if (off != null && (pre == null || off.start < pre.start)) {
            SportEvent.Session race = EventCardModel.scoring(off);
            return new Pick(off, race == null ? null : race.id, false);
        }
        if (pre == null) return null;
        SportEvent.Session n = coming(pre, now);
        boolean weekend = pre.start <= now || CardModel.daysBetween(now, pre.start) <= 1;
        SportEvent.Session race = EventCardModel.scoring(pre);
        SportEvent.Session s = weekend || race == null || "off".equals(race.state) ? n : race;
        return new Pick(pre, s.id, false);
    }

    /** The first session still to come: "pre", TBD or starting less than 3 h ago (a feed that hasn't said "in" yet). */
    static SportEvent.Session coming(SportEvent e, long now) {
        for (SportEvent.Session s : e.byStart()) if ("pre".equals(s.state) && (s.tbd || s.start > now - 3 * H)) return s;
        return null;
    }

    private static Pick nowUfc(List<SportEvent> evs, long now) {
        for (SportEvent e : evs) if ("in".equals(e.state)) return new Pick(e, null, false);
        SportEvent pre = null, off = null;
        for (SportEvent e : evs) {
            long main = SportEngine.mainStart(e);
            if ("off".equals(e.state)) {
                if (main >= now - 12 * H && (off == null || e.start < off.start)) off = e;
                continue;
            }
            if (!"pre".equals(e.state) || main < now - 6 * H) continue;
            if (pre == null || e.start < pre.start) pre = e;
        }
        if (off != null && (pre == null || off.start < pre.start)) return new Pick(off, null, false);
        return pre == null ? null : new Pick(pre, null, false);
    }

    /**
     * The "last" tile. F1: the newest finished scoring session (race, sprint, qualifying, sprint qualifying; practice
     * never); a qualifying counts only until the weekend's next session starts, so after the race it is the race.
     * UFC: the newest finished event shown (the card shows the headline bout's result). Null: none.
     */
    static Pick pickLast(List<SportEvent> all, String sport, SportPrefs p, long now) {
        if (p == null) p = SportPrefs.none();
        List<SportEvent> evs = shown(all, sport, p);
        if (!SportEvent.F1.equals(sport)) {
            SportEvent best = null;
            for (SportEvent e : evs)
                if ("post".equals(e.state) && (best == null || SportEngine.mainStart(e) > SportEngine.mainStart(best))) best = e;
            return best == null ? null : new Pick(best, null, true);
        }
        SportEvent be = null;
        SportEvent.Session bs = null;
        for (SportEvent e : evs)
            for (SportEvent.Session s : e.sessions) {
                if (!"post".equals(s.state) || !SportNames.scoring(s.kind)) continue;
                if (("QUAL".equals(s.kind) || "SQ".equals(s.kind)) && laterStarted(e, s, now)) continue;
                if (bs == null || s.start > bs.start) { be = e; bs = s; }
            }
        return bs == null ? null : new Pick(be, bs.id, true);
    }

    /** Another session of the weekend, after this one, has started. */
    private static boolean laterStarted(SportEvent e, SportEvent.Session s, long now) {
        for (SportEvent.Session x : e.sessions)
            if (x != s && !"off".equals(x.state) && x.start > s.start && (x.start <= now || !"pre".equals(x.state))) return true;
        return false;
    }

    // ---------- rows ----------
    /** Every followed sport's row, then HomeRows.removeUnfollowedSports (rows of sports no longer followed). */
    static synchronized void publishAll(Context c, List<SportEvent> all) {
        if (c == null) return;
        SportPrefs p = Sports.prefs(c);
        for (String sport : Sports.followed(c)) {
            try { publish(c, sport, all == null ? new ArrayList<SportEvent>() : all, p); } catch (Throwable ignored) { }
        }
        HomeRows.removeUnfollowedSports(c);
    }

    /** Settings: creates the sport's row (rows on and shown); returns its channel id, -1 when none. */
    static long addRow(Context c, String sport) {
        if (c == null || !Sports.known(sport)) return -1;
        try {
            if (!HomeRows.enabled(c)) return -1;
            return HomeRows.channel(c, Sports.rowKey(sport), rowName(c, sport), rowLogo(c, sport));
        } catch (Throwable t) {
            return -1;
        }
    }

    /** The row's name: "Formula 1", "Formula 1 · Norris" (the first focus, from sportmeta), "UFC". */
    static String rowName(Context c, String sport) {
        String focus = "";
        if (SportEvent.F1.equals(sport)) {
            List<String> f = Sports.f1Focus(c);
            if (!f.isEmpty()) focus = Sports.name(c, f.get(0));
            if (!f.isEmpty() && f.get(0).startsWith("d:") && focus.indexOf(' ') > 0) focus = focus.substring(focus.indexOf(' ') + 1).trim();
        }
        return rowName(sport, focus);
    }

    /** Pure: the row's name from the sport and the focus's display name ("" for none). */
    static String rowName(String sport, String focus) {
        String base = SportEvent.F1.equals(sport) ? SportNames.w("formula1") : SportNames.w("ufc");
        return focus == null || focus.trim().isEmpty() ? base : base + " · " + focus.trim();
    }

    private static void publish(Context c, String sport, List<SportEvent> all, SportPrefs p) {
        long now = System.currentTimeMillis();
        long id = HomeRows.channel(c, Sports.rowKey(sport), rowName(c, sport), rowLogo(c, sport));
        boolean scores = Appearance.showScores(c);
        Map<String, SportEvent.Entrant> roster = rosterFrom(c, all);
        List<HomeRows.Tile> tiles = new ArrayList<>();
        StringBuilder rowSig = new StringBuilder();
        Pick[] picks = {pickNow(all, sport, p, now), pickLast(all, sport, p, now)};
        for (int i = 0; i < 2; i++) {
            Pick k = picks[i];
            if (k == null) continue;
            Drawn d = draw(c, sport, k, name(sport, k.last), now, p, roster);
            if (d == null) continue;
            HomeRows.Tile t = tile(c, k, d, scores);
            tiles.add(t);
            rowSig.append(t.id).append(t.poster).append(t.title).append(t.desc).append(t.live).append(t.app).append(t.match);
        }
        SharedPreferences sp = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        String key = "row_sig_" + Sports.rowKey(sport), rs = Cards.digest(id + ":" + rowSig);
        if (rs.equals(sp.getString(key, null))) return;
        HomeRows.setTiles(c, id, tiles);
        sp.edit().putString(key, rs).apply();
    }

    /** The card file's name: cs<sport>_tvnow / cs<sport>_tvlast (ArtProvider's pattern; never Store.forgetClub's c<clubId>_). */
    static String name(String sport, boolean last) { return "cs" + sport + (last ? "_tvlast" : "_tvnow"); }

    /** A tile from a drawn card: the session's app when installed (never on a result), the event or session key. */
    static HomeRows.Tile tile(Context c, Pick k, Drawn d, boolean scores) {
        EventCardModel m = d.m;
        HomeRows.Tile t = new HomeRows.Tile();
        t.id = d.ref.name;
        t.poster = d.ref.uri;
        SportEvent.Session s = m.session;
        t.app = !k.last && s != null && s.watch != null && HomeRows.installed(c, s.watch.app) ? s.watch.app : null;
        t.match = matchKey(k, m);
        t.title = scores ? headline(m) : headlineNoScore(m);  // the launcher's title must not spoil
        t.desc = describe(c, m);
        t.live = !k.last && m.state == CardModel.State.LIVE;
        return t;
    }

    /** What OpenActivity / HomeActivity route: the session's key ("f1|espn:…#…"), else the event's. */
    static String matchKey(Pick k, EventCardModel m) {
        if (k.sessionId != null && k.e.session(k.sessionId) != null) return k.e.session(k.sessionId).key(k.e);
        if (m != null && m.session != null) return m.session.key(k.e);
        return k.e.key();
    }

    /** A drawn card: its file (Cards.Ref, m == null for events) and the model it was drawn from. */
    static final class Drawn { Cards.Ref ref; EventCardModel m; }

    /** Draws the card unless an identical one is on disk (cs<sport>_tvnow.png / cs<sport>_tvlast.png); null: not drawn. */
    static Cards.Ref ensure(Context c, String sport, Pick p, String name, long now) {
        if (c == null || p == null || p.e == null) return null;
        List<SportEvent> one = new ArrayList<>();
        one.add(p.e);
        Drawn d = draw(c, sport, p, name, now, Sports.prefs(c), rosterFrom(c, one));
        return d == null ? null : d.ref;
    }

    static synchronized Drawn draw(Context c, String sport, Pick p, String name, long now, SportPrefs prefs, Map<String, SportEvent.Entrant> roster) {
        SharedPreferences sp = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        boolean stale = SportEngine.stale(c, sport), scores = Appearance.showScores(c);
        String style = Appearance.cardStyle(c);
        EventCardModel m = EventCardModel.of(p.e, p.sessionId, p.last, prefs, scores, stale, now, roster);
        String sig = Cards.digest(signature(m, p, prefs, style, scores) + images(c, m));
        String uri = sp.getString("uri_" + name, null);
        File f = new File(c.getFilesDir(), name + ".png");
        if (uri == null || !sig.equals(sp.getString("sig_" + name, null)) || !f.exists()) {
            try {
                Ui.fonts(c);
                Cards.saveAtomic(c, EventStyles.draw(c, m, style), name, true);
                uri = "content://" + ArtProvider.authority(c) + "/" + name + ".png?v=" + now;
                sp.edit().putString("sig_" + name, sig).putString("uri_" + name, uri).apply();
            } catch (Throwable t) {
                if (uri == null || !f.exists()) return null;
            }
        }
        Cards.Ref r = new Cards.Ref();
        r.name = name; r.uri = uri; r.last = p.last; r.stale = stale;
        Drawn d = new Drawn();
        d.ref = r; d.m = m;
        return d;
    }

    /**
     * Everything that changes the card but the images (pure, as Cards.ensure's): the version, language, the event,
     * the pick, style, scores, staleness, the day and time words, the focus and fighters, and the model's state line.
     */
    static String signature(EventCardModel m, Pick p, SportPrefs prefs, String style, boolean scores) {
        StringBuilder b = new StringBuilder();
        b.append(CARD_VERSION).append(I18n.lang()).append(I18n.clock24()).append(p.e.signature()).append('|').append(p.sessionId)
            .append(p.last).append('|').append(style).append(scores).append(m.stale).append('|').append(m.stateLine).append('|')
            .append(m.dateLine).append('|').append(m.time).append(m.timeSuffix).append('|').append(m.heroWord).append(m.clock)
            .append('|').append(EventCardModel.orderTrusted).append('|').append(m.accent).append(m.trim);
        if (prefs != null) b.append('|').append(prefs.focus).append(prefs.fighters).append(prefs.scope);
        return b.toString();
    }

    /**
     * Which of the card's images are on the TV now (a card is redrawn when one arrives): faces, flags (the grand prix
     * host's too: Clash and Clean draw it), channel logos.
     */
    static String images(Context c, EventCardModel m) {
        StringBuilder b = new StringBuilder();
        if (m.flagCode != null && !m.flagCode.isEmpty()) b.append(Cards.have(c, EventKit.flagUrl(m.flagCode)));
        if (m.bout != null) {
            for (SportEvent.Fighter f : new SportEvent.Fighter[]{m.bout.a, m.bout.b})
                if (f != null) b.append(Cards.have(c, f.headshot)).append(Cards.have(c, f.flag));
        }
        if (m.session != null && m.session.watch != null)
            for (Channels.Logo l : SportWhere.logos(c, m.session)) b.append(Cards.have(c, l.url)).append(l.url).append(l.no);
        return b.toString();
    }

    /**
     * rosterFrom(all) over the roster on the TV (F1Feed.rosterOnFile, no network): a focus colours its card in the
     * off-season and before a weekend has any order, with the same team colours Settings and the app's screens use.
     */
    static Map<String, SportEvent.Entrant> rosterFrom(Context c, List<SportEvent> all) {
        Map<String, SportEvent.Entrant> out = new HashMap<>();
        F1Feed.Roster r = null;
        if (c != null) { try { r = F1Feed.rosterOnFile(c); } catch (Throwable ignored) { } }
        if (r != null)
            for (SportEvent.Entrant x : r.byDriver.values()) {
                if (x.id == null || x.id.isEmpty()) continue;
                SportEvent.Entrant y = x.copy();
                if (y.color == 0 && r.teams.containsKey(y.teamId)) y.color = r.teams.get(y.teamId).color;
                if (y.color != 0) out.put(y.id, y);
            }
        out.putAll(rosterFrom(all));  // this season's orders win: a driver who changed teams
        return out;
    }

    /** Driver id -> the newest entry of the stored weekends (team, colour): a focus colours a card before its session has an order. */
    static Map<String, SportEvent.Entrant> rosterFrom(List<SportEvent> all) {
        Map<String, SportEvent.Entrant> out = new HashMap<>();
        Map<String, Long> at = new HashMap<>();
        if (all == null) return out;
        for (SportEvent e : all) {
            if (!SportEvent.F1.equals(e.sport)) continue;
            for (SportEvent.Session s : e.sessions)
                for (SportEvent.Entrant x : s.order) {
                    if (x.id == null || x.id.isEmpty() || x.color == 0) continue;
                    Long t = at.get(x.id);
                    if (t == null || s.start >= t) { out.put(x.id, x); at.put(x.id, s.start); }
                }
        }
        return out;
    }

    // ---------- launcher texts ----------
    static String headline(EventCardModel m) { return m == null ? "" : m.headline(); }

    static String headlineNoScore(EventCardModel m) { return m == null ? "" : m.title(); }

    /** The tile's description (SPEC §4.1): "Formula 1 · Las Vegas Grand Prix · Qualifying · Tomorrow 06:00 · ANT1+". */
    static String describe(Context c, EventCardModel m) {
        if (m == null) return "";
        long last = 0;
        if (m.stale && c != null) { try { last = SportEngine.lastOk(c); } catch (Throwable ignored) { } }
        return describe(m, last);
    }

    /** Pure: the description; lastOk is the stale card's "last updated" time (0: none). Never a result when hidden. */
    static String describe(EventCardModel m, long lastOk) {
        List<String> parts = new ArrayList<>();
        SportEvent e = m.event;
        SportEvent.Session s = m.session;
        boolean f1 = SportEvent.F1.equals(m.sport);
        long at = s != null ? s.start : e.start;
        if (f1) {
            parts.add(SportNames.w("formula1"));
            parts.add(SportNames.gpLong(e));
            if (s != null) parts.add(SportNames.sessionLong(s.kind, s.typeAbbr));
        } else {
            parts.add(SportNames.ufcName(e));
            if (m.bout != null) {
                if (!m.bout.label.isEmpty()) parts.add(m.bout.label);
                if (!m.bout.segment.isEmpty()) parts.add(SportNames.sessionNames(m.bout.segment, "").get(0));
            }
        }
        switch (m.state) {
            case LIVE:
                parts.add(I18n.t("live"));
                if (!m.lapLine.isEmpty()) parts.add(String.format(java.util.Locale.ROOT, SportNames.w2("lap"), m.lapLine.substring(m.lapLine.indexOf(' ') + 1)));
                if (m.hero == EventCardModel.Hero.ROUND && m.heroWord.matches("R\\d+"))  // never when hidden: the hero is a slot then
                    parts.add(String.format(java.util.Locale.ROOT, SportNames.w2("round"), Integer.parseInt(m.heroWord.substring(1))));
                if (!m.whereText.isEmpty()) parts.add(m.whereText);
                break;
            case SUSPENDED: parts.add(I18n.t("SUSPENDED")); break;
            case AWAITING: parts.add(SportNames.w2("no_result")); break;
            case OFF:
                parts.add(I18n.t(m.stateLine.equals(SportNames.w("postponed")) ? "POSTPONED" : "CANCELLED"));
                parts.add(I18n.day(at));
                break;
            case RESULT:
                parts.add(I18n.day(at));
                if (!m.scoresHidden) {
                    String r = result(m);
                    if (!r.isEmpty()) parts.add(r);
                }
                break;
            default:
                parts.add(m.tbd ? I18n.day(at) + " · " + I18n.t("time_tba") : I18n.day(at) + " " + I18n.time(at));
                if (s != null && "DELAYED".equals(s.detail)) parts.add(I18n.t("DELAYED"));
                if (!m.whereText.isEmpty()) parts.add(m.whereText);
        }
        String out = String.join(" · ", parts);
        if (m.stale) out += " · " + I18n.t("not_updated") + (lastOk > 0 ? " (" + I18n.t("last_update") + " " + I18n.day(lastOk) + " " + I18n.time(lastOk) + ")" : "");
        return out;
    }

    /** The result words of a description with scores shown: "VER P1, NOR P2, ANT P3"; "Gane wins · KO/TKO · R3 2:27". */
    private static String result(EventCardModel m) {
        if (SportEvent.F1.equals(m.sport)) {
            List<String> xs = new ArrayList<>();
            for (EventCardModel.Slot x : m.focusSlots) xs.add(x.code + " " + x.place);
            for (EventCardModel.Slot x : m.slots) xs.add(x.code + " " + x.place);
            return String.join(", ", xs).trim();
        }
        EventCardModel.BoutView v = m.bout;
        if (v == null || v.methodWord.isEmpty()) return "";
        String how = v.method.isEmpty() ? v.methodWord : v.method;
        if (v.winner == 0) return how;
        String who = v.winner == 1 ? v.nameA : v.nameB;
        return String.format(java.util.Locale.ROOT, SportNames.w2("by"), who, how);
    }

    /** The row's logo, drawn by EventArt.rowLogo (the monogram disc, no trademarks) only when the stored one is out of date. */
    static HomeRows.Logo rowLogo(final Context c, final String sport) {
        return new HomeRows.Logo() {
            public String tag() { return "sport:" + sport + ":" + ROW_LOGO; }
            public Bitmap get() { return EventArt.rowLogo(c, sport); }
        };
    }
}
