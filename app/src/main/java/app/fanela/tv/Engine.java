package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the followed clubs' matches up to date.
 *  refresh(): for every followed club that is due (15 min from about 6 h before a match until just after it, hourly
 *             otherwise, 3 h with the screen off), fixtures + where-to-watch for the viewer's country (Where) -> one
 *             merge -> home-screen rows (when used). A settings change made during a refresh is picked up by an
 *             immediate second pass.
 *  tick():    live scores, called every 30 s by the app's home screen while it is visible and a match with a live
 *             feed is on. Never blocked by refresh(). Nothing polls in the background.
 * Everything runs at background CPU priority.
 */
final class Engine {
    private static final Object DRAW = new Object();
    private static final AtomicBoolean refreshing = new AtomicBoolean(), again = new AtomicBoolean();

    /** Set when the system stops the job that started a refresh: finish the current club, then save and return. */
    static final class Stop { volatile boolean set; }

    static void background() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
    }

    static boolean screenOn(Context c) {
        android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    /** A refresh is running (Settings, the job or the home screen). */
    static boolean busy() { return refreshing.get(); }

    static boolean liveSource(Match m) {
        return "espn".equals(m.source) || "el".equals(m.source) || "ec".equals(m.source) || "nba".equals(m.source);
    }

    /** Event id without the club, so a match between two followed clubs is one event. */
    static String event(Match m) { return m.key.substring(m.key.indexOf('|') + 1); }

    // ---------- full refresh ----------
    static List<Match> refresh(Context c, boolean force) { return refresh(c, force, new Stop()); }

    static List<Match> refresh(Context c, boolean force, Stop stop) {
        background();
        Net.init(c);
        I18n.load(c);
        Where.load(c);
        if (force) Net.reset();
        if (!refreshing.compareAndSet(false, true)) {
            again.set(true);  // the running refresh does one more pass with the new settings
            return Store.load(c);
        }
        List<Match> all;
        try {
            followedCrests(c);  // the rail's crests before any feed (only those missing: a file check each)
            boolean first = true;
            do {  // a settings change during the pass, the drawing or the scheduling triggers one more round
                again.set(false);
                I18n.load(c);
                Where.load(c);
                boolean due = pass(c, force && first, stop);
                first = false;
                // nothing to draw for: the job was stopped, or nothing was due while nobody is watching
                all = stop.set || (!due && !screenOn(c)) ? stored(c) : draw(c);
            } while (again.get() && !stop.set);
        } finally {
            refreshing.set(false);
        }
        if (again.get() && !stop.set) return refresh(c, false, stop);  // a request that arrived just as we released the lock
        return all;
    }

    /** The stored matches with their clock-based states, as draw() returns them, without drawing. */
    private static List<Match> stored(Context c) {
        List<Match> all = Store.load(c);
        timeStates(all, System.currentTimeMillis());
        return all;
    }

    /** One round of downloads. True when at least one club was due. */
    private static boolean pass(Context c, boolean force, Stop stop) {
        long now = System.currentTimeMillis();
        final Set<String> follow = Teams.followed(c);
        boolean basket = Teams.basketball(c);
        String country = Where.country();
        SharedPreferences sp = c.getSharedPreferences("engine", Context.MODE_PRIVATE);
        SharedPreferences.Editor e0 = sp.edit();  // a club followed again later must be due at once
        Set<String> gone = new HashSet<>();
        for (String key : sp.getAll().keySet()) {
            boolean ours = key.startsWith("lastFull_") || key.startsWith("sig_") || key.startsWith("res_");
            String id = key.substring(key.indexOf('_') + 1);
            if (ours && !follow.contains(id)) { e0.remove(key); gone.add(id); }
        }
        e0.apply();
        for (String id : gone) Store.forgetClub(c, id);  // its card files, guide caches and timestamps, with or without rows
        Store.retain(c, follow);
        if (follow.isEmpty()) return false;  // nothing to download
        List<Match> cached = Store.load(c);
        boolean on = screenOn(c);

        // which clubs are due, oldest first (a new follow or a basketball switch makes a club due at once)
        final Map<String, Long> last = new HashMap<>();
        List<String> due = new ArrayList<>();
        final java.util.Set<String> resign = new java.util.HashSet<>();  // clubs whose country (or basketball) setting changed
        for (String id : follow) {
            String sig = sig(basket, country);
            boolean changed = !sig.equals(sp.getString("sig_" + id, null));
            if (changed) resign.add(id);
            long l = sp.getLong("lastFull_" + id, 0);
            long interval = !on ? 3 * 3600000L : soon(cached, id, now) ? 15 * 60000L : 60 * 60000L;
            last.put(id, l);
            if (force || changed || now - l >= interval - 60000L) due.add(id);
        }
        if (due.isEmpty()) return false;
        Collections.sort(due, new Comparator<String>() { public int compare(String a, String b) { return Long.compare(last.get(a), last.get(b)); } });

        Teams.refresh(c);
        List<Watch> watch = Where.providers(c, country);
        for (Watch w : watch) w.prepare(c);  // Greece: the MagentaTV line-up and the Sport24 guide, as before
        GreeceProvider greece = Where.greece(watch);
        List<Teams.Club> clubs = new ArrayList<>();
        List<List<Sources.Result>> results = new ArrayList<>();
        List<List<Object>> listings = new ArrayList<>();
        List<List<Match>> founds = new ArrayList<>();
        for (String id : due) {  // network, outside every lock
            if (stop.set) break;
            // the same lookup as the home screen (HomeActivity.model): a club it shows is always fetched
            Teams.Club k = Teams.find(c, id);
            if (k == null) continue;
            try {
                if (basket) { Teams.resolveEuro(c, k); Teams.lookupBasket(c, k); k = Teams.find(c, id); if (k == null) continue; }
                boolean askResults = force || resign.contains(id) || resultsDue(cached, id, sp.getLong("res_" + id, 0), now);
                List<Sources.Result> rs = Sources.fetch(c, k, basket, cached, askResults);
                List<Match> known = new ArrayList<>(cached);
                for (Sources.Result r : rs) known.addAll(r.list);
                List<Object> ls = new ArrayList<>();
                for (Watch w : watch) ls.add(w.fetch(c, k, known, basket));
                List<Match> found = greece == null ? null : greece.discover(known, k, basket);
                clubs.add(k); results.add(rs); listings.add(ls); founds.add(found);
            } catch (Throwable ignored) { }  // one club's failure never stops the others
        }
        SharedPreferences.Editor st = c.getSharedPreferences("store", Context.MODE_PRIVATE).edit();
        synchronized (Store.class) {  // one load, one save
            List<Match> all = Store.load(c);
            for (int i = 0; i < clubs.size(); i++) {
                Teams.Club k = clubs.get(i);
                try {
                    all = Store.mergeInto(all, k.id, results.get(i), founds.get(i), st);
                    if (resign.contains(k.id))  // the country changed: no match keeps the old country's channel
                        for (Match m : all) {
                            if (!k.id.equals(m.club)) continue;
                            if (Where.greek(country) && m.channelSure && "".equals(m.listedBy)) continue;  // a Greek guide's own listing
                            Where.reguess(m);
                        }
                    for (int w = 0; w < watch.size(); w++) watch.get(w).apply(all, k, listings.get(i).get(w));
                    if (!Where.greek(country))  // a kept match (found earlier in a Greek guide) still showing a Greek channel
                        for (Match m : all) if (k.id.equals(m.club) && m.channelKnown && !Where.plain(m)) Where.guess(m);
                    if (!k.shows("BC", basket)) {
                        List<Match> keep = new ArrayList<>();
                        for (Match m : all) if (!(k.id.equals(m.club) && "BC".equals(m.team))) keep.add(m);
                        all = keep;
                    }
                } catch (Throwable ignored) { }
            }
            shareCrests(all);
            Crests.fill(c, all);
            Store.save(c, all);
        }
        if (!stop.set && Crests.resolve(c, Store.load(c))) {  // crests the feeds did not give, looked up by name (a few per pass)
            synchronized (Store.class) { List<Match> all = Store.load(c); Crests.fill(c, all); Store.save(c, all); }
        }
        st.commit();
        SharedPreferences.Editor ed = sp.edit();
        boolean answered = false;
        for (int i = 0; i < clubs.size(); i++) {  // only clubs whose feeds answered (offline: retry on the next job)
            boolean ok = false;
            boolean res = false;
            for (Sources.Result r : results.get(i)) { ok |= r.ok; res |= r.results; }
            if (ok) ed.putLong("lastFull_" + clubs.get(i).id, now).putString("sig_" + clubs.get(i).id, sig(basket, country));
            if (res) ed.putLong("res_" + clubs.get(i).id, now);
            answered |= ok;
        }
        if (!answered && !stop.set) ed.putLong("failedAt", System.currentTimeMillis());  // asked, and nothing answered
        if (sp.getLong("cleaned", 0) < now - 7 * 86400000L) { Net.cleanImages(c); ed.putLong("cleaned", now); }
        ed.apply();
        if (!stop.set) prefetchImages(c, Store.load(c), follow);
        return true;
    }

    /** What a club's last fetch depended on: a change makes it due at once (Greece keeps the old value). */
    static String sig(boolean basket, String country) { return Where.greek(country) ? basket + "" : basket + ":" + country; }

    /**
     * The club's 15-minute tier: a match within about 6 h (kick-off soon, being played, or put off), or one that ended
     * a moment ago (its result and the next fixtures settle then). Otherwise hourly.
     */
    static boolean soon(List<Match> all, String club, long now) {
        for (Match m : all) {
            if (!club.equals(m.club)) continue;
            boolean open = "pre".equals(m.state) || "in".equals(m.state) || "off".equals(m.state);
            if (open && m.start > now - 6 * 3600000L && m.start < now + 6 * 3600000L) return true;
            if ("post".equals(m.state) && m.start > now - 4 * 3600000L) return true;
        }
        return false;
    }

    /**
     * Whether ESPN's football results are worth asking for: every 6 h, and from about 100 minutes after a kick-off
     * until one fetch has been made 3 h after it (extra time and penalties included). Sources.espn also asks when a
     * match it listed as coming or being played has dropped off the fixtures list.
     */
    static boolean resultsDue(List<Match> all, String club, long lastResults, long now) {
        if (now - lastResults >= 6 * 3600000L) return true;
        for (Match m : all)
            if (club.equals(m.club) && "espn".equals(m.source) && !m.tbd && !"off".equals(m.state) && m.start <= now
                && m.start > lastResults - 3 * 3600000L && (now >= m.start + 100 * 60000L || "post".equals(m.state)))
                return true;
        return false;
    }

    // ---------- live scores (only while the app is on screen) ----------
    /**
     * True when a followed match with a live feed is in its live window (the caller keeps polling). Games without one
     * change by the clock only: the caller wakes at nextChange() instead.
     */
    static boolean live(Context c) {
        long now = System.currentTimeMillis();
        for (Match m : Store.load(c)) if (liveSource(m) && inWindow(m, now)) return true;
        return false;
    }

    /**
     * The next time a match changes by the clock alone: a live window opening, a game without a live feed starting,
     * waiting for its result or ending (timeStates), a suspension or an unclosed live game running out. MAX_VALUE: none.
     */
    static long nextChange(List<Match> all, long now) {
        long next = Long.MAX_VALUE;
        for (Match m : all) {
            if (m.tbd || !("pre".equals(m.state) || "in".equals(m.state))) continue;
            long[] at = "SUSPENDED".equals(m.detail) ? new long[]{m.start + 5 * 3600000L}
                : liveSource(m) ? new long[]{m.start - 20 * 60000L, m.start + 5 * 3600000L}
                : new long[]{m.start - 20 * 60000L, m.start, m.start + 135 * 60000L, m.start + 6 * 3600000L};
            for (long t : at) if (t > now && t < next) next = t;
        }
        return next;
    }

    /** A live update worth saving: the card changes, or the time it was last seen live moved on by 5 minutes. */
    static boolean worthSaving(Match cur, Match next) {
        return !next.signature().equals(cur.signature()) || next.seenLiveAt - cur.seenLiveAt >= 5 * 60000L;
    }

    static boolean tick(Context c) {
        background();
        I18n.load(c);
        Where.load(c);
        long now = System.currentTimeMillis();
        List<Match> window = new ArrayList<>();
        for (Match m : Store.load(c)) if (inWindow(m, now)) window.add(m);
        if (window.isEmpty()) return false;
        if (!screenOn(c)) return true;
        Map<String, Match> fetched = new HashMap<>();  // one request per event, even when two followed clubs meet
        Map<String, String> boards = new HashMap<>();  // one request per scoreboard: three clubs at 15:00 share it
        List<Match> updates = new ArrayList<>();
        for (Match m : window) {
            if (now < m.start - 5 * 60000L || !liveSource(m)) continue;
            String ev = event(m);
            Match n;
            if (fetched.containsKey(ev)) n = fetched.get(ev);
            else {
                n = "espn".equals(m.source) ? Sources.espnLive(m, boards) : "nba".equals(m.source) ? Sources.nbaLive(m, boards) : Sources.euroLive(m);
                fetched.put(ev, n);
            }
            if (n == null) continue;
            Match u = n.copy();
            if (!m.club.equals(u.club)) u.clubIsHome = !u.clubIsHome;  // the other club's point of view
            if (u.clubWon != 9 && !m.club.equals(n.club)) u.clubWon = -u.clubWon;
            u.channel = m.channel; u.channelNo = m.channelNo; u.channelLogo = m.channelLogo; u.channelApp = m.channelApp;
            u.channelSure = m.channelSure; u.channelKnown = m.channelKnown; u.listedBy = m.listedBy;
            u.source = m.source; u.key = m.key; u.club = m.club;
            if ("in".equals(u.state)) u.seenLiveAt = now;
            updates.add(u);
        }
        boolean changed = false;
        if (!updates.isEmpty()) {
            synchronized (Store.class) {
                List<Match> all = Store.load(c);
                for (Match u : updates)
                    for (int i = 0; i < all.size(); i++)
                        if (all.get(i).key.equals(u.key)) {
                            // only the live fields: channel, time and names may have been updated by a refresh meanwhile
                            Match cur = all.get(i), x = cur.copy();
                            x.state = u.state; x.hs = u.hs; x.as = u.as; x.detail = u.detail; x.quarter = u.quarter;
                            x.clubWon = u.clubWon; x.pens = u.pens; x.seenLiveAt = Math.max(cur.seenLiveAt, u.seenLiveAt);
                            if ("espn".equals(cur.source) && !u.tbd) { x.start = u.start; x.tbd = false; }  // never a placeholder over a known time
                            Match next = Store.keepProgress(cur, x, now);
                            if (worthSaving(cur, next)) { all.set(i, next); changed = true; }  // the same card: no write, no redraw
                        }
                if (changed) Store.save(c, all);
            }
        }
        if (changed) draw(c);
        return true;
    }

    static boolean inWindow(Match m, long now) {
        if (m.tbd || "off".equals(m.state) || "post".equals(m.state)) return false;
        if (now < m.start - 20 * 60000L) return false;
        if (!liveSource(m)) return now < m.start + 135 * 60000L;
        if ("in".equals(m.state)) return now < m.start + 5 * 3600000L;
        return now < m.start + 3 * 3600000L + 15 * 60000L;
    }

    /** Clock-based states for games without a live feed, and live states the feed never closed. */
    static void timeStates(List<Match> all, long now) {
        for (Match m : all) {
            if ("SUSPENDED".equals(m.detail)) {  // stays suspended until a feed reports a result or a new date
                if (now > m.start + 5 * 3600000L) m.state = "off";  // no longer "now": shown like a postponement, then dropped
                continue;
            }
            if (!liveSource(m) && !m.tbd && ("pre".equals(m.state) || "in".equals(m.state))) {
                if (now >= m.start + 6 * 3600000L) { m.state = "post"; if (!"tsdb".equals(m.source) || !m.hasScore()) { m.hs = null; m.as = null; } m.detail = "FT"; }
                else if (now >= m.start + 135 * 60000L) { m.state = "in"; m.detail = "AWAITING"; }
                else if (now >= m.start && !m.hasScore()) { m.state = "in"; m.detail = "IN_PROGRESS"; }
            }
            if (liveSource(m) && "in".equals(m.state) && now > m.start + 5 * 3600000L && now - m.seenLiveAt > 30 * 60000L) {
                m.state = "post";
                if (m.clubWon == 9 && m.hasScore()) m.clubWon = Integer.compare(Match.toInt(m.clubScore()), Match.toInt(m.oppScore()));
            }
        }
    }

    // ---------- home-screen rows ----------
    static List<Match> draw(Context c) {
        synchronized (DRAW) {
            I18n.load(c);
            Where.load(c);
            List<Match> all = Store.load(c);
            timeStates(all, System.currentTimeMillis());
            try {
                if (HomeRows.enabled(c)) Cards.publishAll(c, all);
                else HomeRows.removeUnfollowed(c);  // rows switched off: every club row is hidden, then deleted
            } catch (Throwable ignored) { }
            return all;
        }
    }

    /**
     * The followed clubs' own crests (the Club rail, Matchday's row labels), downloaded first: they are on the TV from
     * the moment a club is followed (Teams.setFollowed asks too), so the rail never shows a placeholder for long.
     */
    static void followedCrests(Context c) {
        for (String id : Teams.followed(c)) {
            Teams.Club k = Teams.find(c, id);
            if (k != null && k.crest != null) Net.fetchImage(c, k.crest);
        }
    }

    /**
     * The images the screens draw, downloaded ahead of drawing: every crest of every saved match (QA3G-1: the fixtures
     * list, the latest results and the match screen's form reach past a month, and a crest missing there stays a
     * monogram), the nearest matches first; the channel logos of the coming month's matches; the followed clubs' crests.
     * A crest already on the TV costs a file check.
     */
    static void prefetchImages(Context c, List<Match> all, Set<String> follow) {
        final long now = System.currentTimeMillis();
        List<Match> near = new ArrayList<>(all);
        Collections.sort(near, new Comparator<Match>() {
            public int compare(Match a, Match b) { return Long.compare(Math.abs(a.start - now), Math.abs(b.start - now)); }
        });
        Set<String> done = new HashSet<>();
        for (Match m : near) {
            for (String u : new String[]{m.homeCrest, m.awayCrest}) if (u != null && done.add(u)) Net.fetchImage(c, u);
            if (m.start > now - 6 * 3600000L && m.start < now + 30L * 86400000L)  // channel logos of upcoming matches
                for (Channels.Logo l : Channels.logos(c, m)) if (done.add(l.url)) Net.fetchImage(c, l.url);
        }
        for (String id : follow) { Teams.Club k = Teams.find(c, id); if (k != null) Net.fetchImage(c, k.crest); }
    }

    /**
     * When a refresh last found no feed answering for any club it asked about (0: never): the home screen's offline
     * line shows only when this is later than the last success (HomeModel.offline), not because Android has not
     * validated the network (a blocked captive-portal check while downloads work, QA3G-3).
     */
    static long failedAt(Context c) { return c.getSharedPreferences("engine", Context.MODE_PRIVATE).getLong("failedAt", 0); }

    static void shareCrests(List<Match> all) {
        Map<String, String> crest = new HashMap<>();
        for (Match m : all) {
            if (m.homeCrest != null && !m.homeCrest.isEmpty()) crest.put(m.team + Cards.key(I18n.grName(m.home)), m.homeCrest);
            if (m.awayCrest != null && !m.awayCrest.isEmpty()) crest.put(m.team + Cards.key(I18n.grName(m.away)), m.awayCrest);
        }
        for (Match m : all) {
            if (m.homeCrest == null || m.homeCrest.isEmpty()) m.homeCrest = crest.get(m.team + Cards.key(I18n.grName(m.home)));
            if (m.awayCrest == null || m.awayCrest.isEmpty()) m.awayCrest = crest.get(m.team + Cards.key(I18n.grName(m.away)));
        }
    }

}
