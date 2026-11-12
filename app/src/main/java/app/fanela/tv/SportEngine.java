package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the followed sports' events up to date (F1-UFC-ARCHITECTURE §1.6-1.7), beside Engine and called by it.
 *  pass(): for every followed sport that is due (interval), its feed -> EventStore.merge -> prune -> where-to-watch
 *          (SportWhere.guess, then SportWhere.apply outside the lock) -> save -> prefetch images. A feed that fails
 *          keeps the old events.
 *  tick(): live fields while the app's home screen is visible and an event is in its live window (inWindow); one
 *          request per sport per tick through the feeds' live().
 * Timestamps in the "sports_engine" preferences: lastFull_<sport>, res_<sport>, ok_<sport>, failedAt (lastCal_<sport>
 * is the feeds' own, for their calendar listing). Clock rules (timeStates) are applied on reading, never saved, so a
 * feed's later answer is never refused as "backwards" because of a clock guess (as Engine does for football).
 */
final class SportEngine {
    private SportEngine() { }

    static final long MIN = 60000L, H = 3600000L, DAY = 24 * H;
    static final long HOT = 15 * MIN, NEAR = H, AFTER = 6 * H, NORMAL = DAY, SCREEN_OFF = 3 * H;
    static final String PREFS = Sports.ENGINE;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    // ---------- the refresh ----------
    /** One round of downloads for the followed sports; true when a sport was due (Engine then draws). */
    static boolean pass(Context c, boolean force, Engine.Stop stop) { return pass(c, force, stop, null, true); }

    /** The sports a sport-only pass (follow) is downloading now: the full pass leaves them to it. */
    private static final Set<String> SOLO = Collections.synchronizedSet(new HashSet<String>());

    /**
     * The sports a pass downloads, in the followed order. A full pass (only null): every followed sport but those a
     * sport-only pass is handling (solo). A sport-only pass: its own sports, if still followed. Pure.
     */
    static List<String> sportsFor(Set<String> follow, Set<String> only, Set<String> solo) {
        List<String> out = new ArrayList<>();
        for (String sport : follow) {
            if (only != null ? !only.contains(sport) : solo != null && solo.contains(sport)) continue;
            out.add(sport);
        }
        return out;
    }

    /**
     * A sport just followed (Settings, on its work thread): that sport's own pass at once, then the sports' rows drawn
     * (EventCards.publishAll), then the faces and flags and the rows again (a card is redrawn only when an image
     * arrived). The row appears in seconds instead of after the clubs' refresh. Engine.refresh still runs as before;
     * its pass leaves the sport to this one while it runs. Football is untouched.
     */
    static void follow(Context c, String sport) {
        if (c == null || !Sports.follows(c, sport) || !SOLO.add(sport)) return;
        try {
            Net.init(c);
            boolean did = pass(c, false, new Engine.Stop(), Collections.singleton(sport), false);
            draw(c);
            if (did) {
                prefetchImages(c, EventStore.load(c), Sports.prefs(c), System.currentTimeMillis());
                draw(c);
            }
        } catch (Throwable ignored) {
        } finally {
            SOLO.remove(sport);
        }
    }

    /** The sports' rows now (Engine.draw's sport half), when the rows are on. */
    private static void draw(Context c) {
        I18n.load(c);
        Where.load(c);
        try {
            if (HomeRows.enabled(c)) EventCards.publishAll(c, events(c, System.currentTimeMillis()));
        } catch (Throwable ignored) { }
    }

    /**
     * The pass. only: the sports to download (null: every followed one, and an unfollowed sport's traces are
     * forgotten first); images: the prefetch at the end (follow fetches them after drawing).
     */
    private static boolean pass(Context c, boolean force, Engine.Stop stop, Set<String> only, boolean images) {
        long now = System.currentTimeMillis();
        Set<String> follow = Sports.followed(c);
        SharedPreferences sp = sp(c);
        boolean forgot = false;
        if (only == null) {
            List<SportEvent> stored = EventStore.load(c);
            for (String sport : Sports.ALL) {  // a sport unfollowed: its events, files, card prefs and timestamps go
                if (follow.contains(sport) || !EventStore.traces(c, sport, stored)) continue;
                EventStore.forgetSport(c, sport);
                forgot = true;
            }
            EventStore.retain(c, follow);
        }
        // something was forgotten: true, so Engine draws and the rows' housekeeping (HomeRows.removeUnfollowedSports)
        // takes the sport's row away even when nothing else is due
        if (follow.isEmpty()) return forgot;
        List<SportEvent> cached = EventStore.load(c);
        boolean on = Engine.screenOn(c);
        List<FeedResult> results = new ArrayList<>();
        boolean due = false;
        for (String sport : sportsFor(follow, only, only == null ? SOLO : null)) {  // network, outside every lock
            if (stop.set) break;
            List<SportEvent> mine = ofSport(cached, sport);
            long last = sp.getLong("lastFull_" + sport, 0);
            if (!force && now - last < interval(sport, mine, now, on) - MIN) continue;
            due = true;
            FeedResult r;
            try {
                r = SportEvent.F1.equals(sport) ? F1Feed.fetch(c, mine, now, resultsDue(mine, sp.getLong("res_" + sport, 0), now))
                    : UfcFeed.fetch(c, mine, now);
            } catch (Throwable t) {
                r = null;
            }
            results.add(r == null ? FeedResult.failed(sport) : r);
        }
        if (!due) return forgot;
        String country = Where.country();
        List<SportEvent> merged;
        synchronized (EventStore.class) {  // one load, one save
            merged = EventStore.load(c);
            for (FeedResult r : results) {
                try { merged = EventStore.merge(merged, r, now); } catch (Throwable ignored) { }  // one sport's failure never stops the other
            }
            merged = EventStore.prune(merged, now);
            for (SportEvent e : merged) {
                try { SportWhere.guess(e, country); } catch (Throwable ignored) { }
            }
            EventStore.save(c, merged);
        }
        if (!stop.set) {
            try {  // the Greek guides (network): outside the lock, then only the watch fields are written back
                List<SportEvent> applied = new ArrayList<>();
                for (SportEvent e : merged) applied.add(e.copy());
                SportWhere.apply(c, applied, country);
                synchronized (EventStore.class) {
                    List<SportEvent> all = EventStore.load(c);
                    for (SportEvent e : all) {
                        SportEvent a = EventStore.find(applied, e.key());
                        if (a != null) EventStore.copyWatch(a, e);
                    }
                    EventStore.save(c, all);
                }
            } catch (Throwable ignored) { }
        }
        SharedPreferences.Editor ed = sp.edit();
        boolean answered = false;
        for (FeedResult r : results) {  // only sports whose feed answered (offline: retry on the next job)
            if (r.ok) ed.putLong("lastFull_" + r.sport, now).putLong("ok_" + r.sport, now);
            if (r.ok && r.results) ed.putLong("res_" + r.sport, now);
            answered |= r.ok;
        }
        if (!answered && !stop.set) ed.putLong("failedAt", System.currentTimeMillis());
        ed.apply();
        if (images && !stop.set) {
            try { prefetchImages(c, EventStore.load(c), Sports.prefs(c), now); } catch (Throwable ignored) { }
        }
        return true;
    }

    static List<SportEvent> ofSport(List<SportEvent> all, String sport) {
        List<SportEvent> out = new ArrayList<>();
        for (SportEvent e : all) if (sport.equals(e.sport)) out.add(e);
        return out;
    }

    /** The followed sports' stored events with their clock-based states (what draw and the screens use). */
    static List<SportEvent> events(Context c, long now) {
        Set<String> follow = Sports.followed(c);
        List<SportEvent> out = new ArrayList<>();
        if (follow.isEmpty()) return out;
        for (SportEvent e : EventStore.load(c)) if (follow.contains(e.sport)) out.add(e);
        timeStates(out, now);
        return out;
    }

    static void prefetchImages(Context c, List<SportEvent> all, long now) { prefetchImages(c, all, Sports.prefs(c), now); }

    /**
     * The images the cards and screens draw (imageUrls), then the channel logos of those events' sessions; a file
     * check each when cached.
     */
    static void prefetchImages(Context c, List<SportEvent> all, SportPrefs p, long now) {
        Set<String> done = new HashSet<>();
        for (String url : imageUrls(all, p, now)) fetch(c, url, done);
        for (SportEvent e : withImages(all, now))
            for (SportEvent.Session s : e.sessions)
                for (Channels.Logo l : SportWhere.logos(c, s)) fetch(c, l.url, done);
    }

    /**
     * The images fetched ahead for the events of withImages: only what a surface draws. Pure.
     *  F1: the host's flag (the Clash and Clean cards, the panel, the weekend screen). Drivers' flags are drawn nowhere.
     *  UFC: the headshots of the bouts the cards, the panel and the event screen's hero show: the main event's, a
     *       followed fighter's, and on a card under way the bout in the cage and the next one. The full card (every
     *       fighter's flag, the event screen's bout rows) only for the next card, the one the row's tile and the event
     *       screen open (EventCards.pickNow). Stance cut-outs never: no surface draws them.
     */
    static List<String> imageUrls(List<SportEvent> all, SportPrefs p, long now) {
        if (p == null) p = SportPrefs.none();
        Set<String> out = new LinkedHashSet<>();
        SportEvent full = null;
        try {
            EventCards.Pick n = EventCards.pickNow(all, SportEvent.UFC, p, now);
            if (n != null) full = n.e;
        } catch (RuntimeException ignored) { }
        for (SportEvent e : withImages(all, now)) {
            if (SportEvent.F1.equals(e.sport)) {
                add(out, EventKit.flagUrl(SportNames.flagCode(e)));
                continue;
            }
            List<SportEvent.Bout> heads = new ArrayList<>();
            heads.add(e.mainEvent());
            for (String f : p.fighters) heads.add(e.boutOf(f));
            if ("in".equals(e.state)) { heads.add(EventCardModel.inCage(e)); heads.add(EventCardModel.nextBout(e)); }
            for (SportEvent.Bout b : heads)
                if (b != null) for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b}) if (f != null) add(out, f.headshot);
            if (e != full) continue;
            for (SportEvent.Session s : e.sessions)
                for (SportEvent.Bout b : s.bouts)
                    for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b}) if (f != null) add(out, f.flag);
        }
        return new ArrayList<>(out);
    }

    private static void add(Set<String> out, String url) { if (url != null && url.startsWith("https://")) out.add(url); }

    /** The events whose images are fetched ahead (prefetchImages), in the stored order. Pure. */
    static List<SportEvent> withImages(List<SportEvent> all, long now) {
        java.util.Map<String, SportEvent> newestDone = new java.util.HashMap<>();
        for (SportEvent e : all) {
            long last = EventStore.lastStart(e);
            if (last >= now - 6 * H) continue;
            SportEvent b = newestDone.get(e.sport);
            if (b == null || last > EventStore.lastStart(b)) newestDone.put(e.sport, e);
        }
        List<SportEvent> out = new ArrayList<>();
        for (SportEvent e : all) {
            boolean window = EventStore.lastStart(e) >= now - 6 * H && e.start <= now + 30 * DAY;
            if (window || newestDone.get(e.sport) == e) out.add(e);
        }
        return out;
    }

    private static void fetch(Context c, String url, Set<String> done) {
        if (url != null && url.startsWith("https://") && done.add(url)) Net.fetchImage(c, url);
    }

    // ---------- cadence (pure) ----------
    /** A session's usual length in minutes (§1.7): practice 60, sprint qualifying 45, qualifying 60, sprint 45, race 120. */
    static int typicalMinutes(String kind) {
        if (kind == null) return 60;
        switch (kind) {
            case "SQ": case "SPRINT": return 45;
            case "RACE": return 120;
            default: return 60;
        }
    }

    /**
     * How long a sport's events stay fresh (§1.6), the shortest that applies over its events: 15 min while one is hot
     * (F1: from 6 h before a session until 4 h after its usual end; UFC: from 12 h before the first segment until the
     * card is over, at most 7 h after the main card's start), hourly in the event's week, every 6 h for 48 h after
     * the last session, daily otherwise; 3 h with the screen off.
     */
    static long interval(String sport, List<SportEvent> all, long now, boolean screenOn) {
        if (!screenOn) return SCREEN_OFF;
        long best = NORMAL;
        for (SportEvent e : all) {
            if (!sport.equals(e.sport) || EventStore.cancelled(e)) continue;
            long first = e.start, last = EventStore.lastStart(e);
            if (SportEvent.F1.equals(sport)) {
                for (SportEvent.Session s : e.sessions) {
                    if ("off".equals(s.state) || s.tbd) continue;
                    if (now >= s.start - 6 * H && now < s.start + typicalMinutes(s.kind) * MIN + 4 * H) return HOT;
                }
            } else if (now >= first - 12 * H && !"post".equals(e.state) && now < mainStart(e) + 7 * H) return HOT;
            if (now >= last && now < last + 48 * H) best = Math.min(best, AFTER);
            if (first - now <= 7 * DAY && now < last) best = Math.min(best, NEAR);
        }
        return best;
    }

    /**
     * F1 standings and roster are worth asking for: weekly, and once 2 h and once 24 h after a race's usual end (its
     * start + 2 h), within 3 days of the race.
     */
    static boolean resultsDue(List<SportEvent> all, long lastResults, long now) {
        if (now - lastResults >= 7 * DAY) return true;
        for (SportEvent e : all)
            for (SportEvent.Session s : e.sessions) {
                if (!"RACE".equals(s.kind) || "off".equals(s.state) || s.tbd || now > s.start + 3 * DAY) continue;
                long end = s.start + typicalMinutes(s.kind) * MIN;
                for (long t : new long[]{end + 2 * H, end + 24 * H}) if (now >= t && lastResults < t) return true;
            }
        return false;
    }

    // ---------- live windows (pure) ----------
    /** UFC: the main card's start (the MAIN segment, else the last segment); F1: the last session's start. */
    static long mainStart(SportEvent e) {
        long last = 0;
        for (SportEvent.Session s : e.sessions) {
            if ("MAIN".equals(s.kind) || "main".equals(s.id)) return s.start;
            if (!"off".equals(s.state)) last = Math.max(last, s.start);
        }
        return last > 0 ? last : e.end;
    }

    /** UFC: the first segment's start (off ones skipped). */
    static long firstStart(SportEvent e) {
        long first = Long.MAX_VALUE;
        for (SportEvent.Session s : e.sessions) if (!"off".equals(s.state) && !s.tbd) first = Math.min(first, s.start);
        return first == Long.MAX_VALUE ? e.start : first;
    }

    static boolean anyIn(SportEvent e) {
        for (SportEvent.Session s : e.sessions) {
            if ("in".equals(s.state)) return true;
            for (SportEvent.Bout b : s.bouts) if ("in".equals(b.state)) return true;
        }
        return false;
    }

    /**
     * An F1 session's live window: from its start − 20 min to start + its usual length + 60 min, stretched to
     * start + 4 h while it is "in" (red flags). Null for a session that is TBD, finished or off.
     */
    static long[] window(SportEvent.Session s) {
        if (s.tbd || !("pre".equals(s.state) || "in".equals(s.state))) return null;
        long close = "in".equals(s.state) ? s.start + 4 * H : s.start + (typicalMinutes(s.kind) + 60) * MIN;
        return new long[]{s.start - 20 * MIN, close};
    }

    /**
     * A UFC card's live window: from the first segment's start − 20 min to the main card's start + 4 h, stretched to
     * + 7 h while any bout is "in". Null once every segment is finished or off.
     */
    static long[] window(SportEvent e) {
        boolean open = false;
        for (SportEvent.Session s : e.sessions) open |= !s.tbd && ("pre".equals(s.state) || "in".equals(s.state));
        if (!open || "post".equals(e.state) || "off".equals(e.state)) return null;
        long main = mainStart(e);
        return new long[]{firstStart(e) - 20 * MIN, main + (anyIn(e) ? 7 : 4) * H};
    }

    /** The event is in its live window (the home screen polls tick()). */
    static boolean inWindow(SportEvent e, long now) {
        if (SportEvent.F1.equals(e.sport)) {
            for (SportEvent.Session s : e.sessions) {
                long[] w = window(s);
                if (w != null && now >= w[0] && now < w[1]) return true;
            }
            return false;
        }
        long[] w = window(e);
        return w != null && now >= w[0] && now < w[1];
    }

    /** When the event's live polling should start (F1: the earliest session in its window; UFC: the first segment). */
    static long liveFrom(SportEvent e, long now) {
        if (!SportEvent.F1.equals(e.sport)) return firstStart(e);
        long from = Long.MAX_VALUE;
        for (SportEvent.Session s : e.sessions) {
            long[] w = window(s);
            if (w != null && now >= w[0] && now < w[1]) from = Math.min(from, s.start);
        }
        return from;
    }

    /**
     * The next time an event changes by the clock alone (§1.7): a window opening or closing, a session's start (the
     * card's "next session" moves on), a result that never came (start + 6 h), a live session not seen for 30 min.
     * MAX_VALUE: none.
     */
    static long nextChange(List<SportEvent> all, long now) {
        long next = Long.MAX_VALUE;
        for (SportEvent e : all) {
            List<Long> at = new ArrayList<>();
            for (SportEvent.Session s : e.sessions) {
                if (s.tbd || !("pre".equals(s.state) || "in".equals(s.state))) continue;
                at.add(s.start);
                at.add(s.start + 6 * H);
                if (SportEvent.F1.equals(e.sport)) {
                    long[] w = window(s);
                    at.add(w[0]); at.add(w[1]);
                    if ("in".equals(s.state)) at.add(Math.max(s.start + 4 * H, s.seenLiveAt + 30 * MIN));
                }
            }
            if (!SportEvent.F1.equals(e.sport)) {
                long[] w = window(e);
                if (w != null) {
                    long main = mainStart(e);
                    at.add(w[0]); at.add(main + 4 * H); at.add(main + 7 * H);
                }
            }
            for (long t : at) if (t > now && t < next) next = t;
        }
        return next;
    }

    // ---------- states (pure) ----------
    /**
     * Clock rules, applied on reading (like Engine.timeStates): a session still "in" past its window and not seen live
     * for 30 min becomes "post"; a session still "pre" 6 h after its start, with no answer, becomes "post" with detail
     * AWAITING (the card says NO RESULT YET). Then every event's state is derived again.
     */
    static void timeStates(List<SportEvent> all, long now) {
        for (SportEvent e : all) {
            boolean f1 = SportEvent.F1.equals(e.sport);
            long ufcClose = f1 ? 0 : mainStart(e) + 7 * H;
            for (SportEvent.Session s : e.sessions) {
                if (s.tbd) continue;
                if ("in".equals(s.state)) {
                    long close = f1 ? s.start + 4 * H : ufcClose;
                    if (now >= close && now - s.seenLiveAt > 30 * MIN) s.state = "post";
                } else if ("pre".equals(s.state) && now >= s.start + 6 * H) {
                    s.state = "post";
                    s.detail = "AWAITING";
                }
            }
            derive(e);
        }
    }

    /**
     * The event's state from its sessions (§5.2): "off" when every session is off; "post" when the scoring session is
     * (F1: the race, else the last session; UFC: the main card, or its main event); "in" when a session or a bout is
     * being played, or (UFC) a segment has started; "pre" otherwise. No sessions: unchanged.
     */
    static void derive(SportEvent e) {
        if (e.sessions.isEmpty()) return;
        if (EventStore.cancelled(e)) { e.state = "off"; return; }
        boolean f1 = SportEvent.F1.equals(e.sport);
        SportEvent.Session scoring = null, last = null;
        for (SportEvent.Session s : e.byStart()) {
            if ("off".equals(s.state)) continue;
            last = s;
            if (f1 ? "RACE".equals(s.kind) : "MAIN".equals(s.kind) || "main".equals(s.id)) scoring = s;
        }
        if (scoring == null) scoring = last;  // no race (or main segment): the last session
        SportEvent.Bout main = f1 ? null : e.mainEvent();
        if ((scoring != null && "post".equals(scoring.state)) || (main != null && "post".equals(main.state))) { e.state = "post"; return; }
        if (anyIn(e)) { e.state = "in"; return; }
        if (!f1)
            for (SportEvent.Session s : e.sessions)
                if ("post".equals(s.state)) { e.state = "in"; return; }  // a segment is over, the card is not
        e.state = "pre";
    }

    // ---------- live (only while the app's home screen is visible) ----------
    /** A followed event is in its live window: the home screen keeps polling tick(). */
    static boolean live(Context c, long now) {
        for (SportEvent e : events(c, now)) if (inWindow(e, now)) return true;
        return false;
    }

    /** A live reading worth saving: the card changes, or the time it was last seen live moved on by 5 minutes. */
    static boolean worthSaving(SportEvent cur, SportEvent next) {
        return !next.signature().equals(cur.signature()) || seenLive(next) - seenLive(cur) >= 5 * MIN;
    }

    static long seenLive(SportEvent e) {
        long t = 0;
        for (SportEvent.Session s : e.sessions) t = Math.max(t, s.seenLiveAt);
        return t;
    }

    /**
     * Live fields for the events in their window: one feed call per event, the scoreboards shared through boards.
     * Saves and redraws only when a card changes. True while an event is in its window (the caller keeps polling).
     */
    static boolean tick(Context c, Map<String, String> boards, long now) {
        List<SportEvent> window = new ArrayList<>();
        for (SportEvent e : events(c, now)) if (inWindow(e, now)) window.add(e);
        if (window.isEmpty()) return false;
        if (!Engine.screenOn(c)) return true;
        List<SportEvent> updates = new ArrayList<>();
        for (SportEvent e : window) {
            if (now < liveFrom(e, now) - 5 * MIN) continue;
            SportEvent n = null;
            try { n = SportEvent.F1.equals(e.sport) ? F1Feed.live(e, boards) : UfcFeed.live(e, boards); } catch (Throwable ignored) { }
            if (n == null) continue;
            n.sport = e.sport; n.id = e.id;
            for (SportEvent.Session s : n.sessions) if ("in".equals(s.state)) s.seenLiveAt = now;
            updates.add(n);
        }
        boolean changed = false;
        if (!updates.isEmpty()) {
            synchronized (EventStore.class) {
                List<SportEvent> all = EventStore.load(c);
                for (SportEvent u : updates)
                    for (int i = 0; i < all.size(); i++) {
                        if (!all.get(i).key().equals(u.key())) continue;
                        SportEvent cur = all.get(i), next = EventStore.applyLive(cur, u, now);
                        if (worthSaving(cur, next)) { all.set(i, next); changed = true; }
                    }
                if (changed) EventStore.save(c, all);
            }
        }
        if (changed) Engine.draw(c);
        return true;
    }

    // ---------- status ----------
    /** The newest successful fetch of a followed sport (0: never): the home screen's "updated" time. */
    static long lastOk(Context c) {
        long last = 0;
        for (String s : Sports.followed(c)) last = Math.max(last, sp(c).getLong("ok_" + s, 0));
        return last;
    }

    /** The sport's feed has not answered for 6 hours (Store.STALE). */
    static boolean stale(Context c, String sport) {
        long ok = sp(c).getLong("ok_" + sport, 0);
        return ok > 0 && System.currentTimeMillis() - ok > Store.STALE;
    }

    /** When a sports pass last found no feed answering (0: never). */
    static long failedAt(Context c) { return sp(c).getLong("failedAt", 0); }
}
