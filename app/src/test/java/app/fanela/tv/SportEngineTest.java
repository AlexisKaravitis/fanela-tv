package app.fanela.tv;

import static app.fanela.tv.SportTestKit.DAY;
import static app.fanela.tv.SportTestKit.H;
import static app.fanela.tv.SportTestKit.LV_FP1;
import static app.fanela.tv.SportTestKit.LV_FP2;
import static app.fanela.tv.SportTestKit.LV_RACE;
import static app.fanela.tv.SportTestKit.MIN;
import static app.fanela.tv.SportTestKit.U334_EARLY;
import static app.fanela.tv.SportTestKit.U334_MAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** SportEngine (§1.6-1.7): cadence, live windows, the next change, clock states, derived states, and a pass on stub feeds. */
public class SportEngineTest {
    private SportTestKit.Ctx c;

    private FeedResult.Http realNet;

    @Before public void setUp() throws Exception {
        c = SportTestKit.context();
        realNet = FeedResult.net;
        FeedResult.net = url -> { throw new java.io.IOException("offline"); };  // the feeds never reach ESPN from a unit test
    }

    @After public void tearDown() { FeedResult.net = realNet; SportTestKit.delete(c.dir); }

    private static List<SportEvent> list(SportEvent... es) { return new ArrayList<>(Arrays.asList(es)); }

    // ---------- interval ----------
    @Test public void f1Cadence() {
        List<SportEvent> lv = list(SportTestKit.lasVegas());
        assertEquals(DAY, SportEngine.interval("f1", lv, LV_FP1 - 10 * DAY, true));        // more than 7 days away: daily
        assertEquals(H, SportEngine.interval("f1", lv, LV_FP1 - 3 * DAY, true));           // the event's week: hourly
        assertEquals(H, SportEngine.interval("f1", lv, LV_FP1 - 7 * H, true));
        assertEquals(15 * MIN, SportEngine.interval("f1", lv, LV_FP1 - 5 * H, true));      // 6 h before a session: hot
        assertEquals(15 * MIN, SportEngine.interval("f1", lv, LV_FP2 + 4 * H, true));      // until 4 h after its usual end
        assertEquals(H, SportEngine.interval("f1", lv, LV_FP1 + 12 * H, true));            // between days of a weekend
        assertEquals(15 * MIN, SportEngine.interval("f1", lv, LV_RACE + 5 * H, true));
        assertEquals(6 * H, SportEngine.interval("f1", lv, LV_RACE + 7 * H, true));        // after: every 6 h for 48 h
        assertEquals(DAY, SportEngine.interval("f1", lv, LV_RACE + 49 * H, true));
        assertEquals(3 * H, SportEngine.interval("f1", lv, LV_FP1 - 5 * H, false));        // screen off: as Engine
        assertEquals(DAY, SportEngine.interval("ufc", lv, LV_FP1 - 5 * H, true));          // another sport's events don't count
        assertEquals(DAY, SportEngine.interval("f1", new ArrayList<SportEvent>(), LV_FP1, true));
        SportEvent off = SportTestKit.lasVegas();
        for (SportEvent.Session s : off.sessions) s.state = "off";
        assertEquals(DAY, SportEngine.interval("f1", list(off), LV_FP1 - 5 * H, true));     // cancelled: nothing to watch for
    }

    @Test public void ufcCadence() {
        SportEvent u = SportTestKit.ufc334();
        List<SportEvent> l = list(u);
        assertEquals(H, SportEngine.interval("ufc", l, U334_EARLY - 13 * H, true));
        assertEquals(15 * MIN, SportEngine.interval("ufc", l, U334_EARLY - 11 * H, true));  // 12 h before the early prelims
        assertEquals(15 * MIN, SportEngine.interval("ufc", l, U334_MAIN + 6 * H, true));    // until every bout is over
        assertEquals(6 * H, SportEngine.interval("ufc", l, U334_MAIN + 8 * H, true));       // at most 7 h after the main card
        for (SportEvent.Session s : u.sessions) s.state = "post";
        SportEngine.derive(u);
        assertEquals(6 * H, SportEngine.interval("ufc", l, U334_MAIN + 3 * H, true));       // over: after
    }

    @Test public void resultsAreDueWeeklyAndAfterARace() {
        List<SportEvent> lv = list(SportTestKit.lasVegas());
        assertTrue(SportEngine.resultsDue(lv, 0, LV_FP1));
        assertTrue(SportEngine.resultsDue(lv, LV_FP1 - 8 * DAY, LV_FP1));
        assertFalse(SportEngine.resultsDue(lv, LV_RACE - DAY, LV_RACE + 3 * H));
        assertTrue(SportEngine.resultsDue(lv, LV_RACE - DAY, LV_RACE + 4 * H + MIN));      // race end + 2 h
        assertFalse(SportEngine.resultsDue(lv, LV_RACE + 4 * H + MIN, LV_RACE + 5 * H));
        assertTrue(SportEngine.resultsDue(lv, LV_RACE + 4 * H + MIN, LV_RACE + 26 * H + MIN));  // race end + 24 h
        assertFalse(SportEngine.resultsDue(lv, LV_RACE + 26 * H + MIN, LV_RACE + 30 * H));
    }

    // ---------- live windows ----------
    @Test public void anF1SessionsWindow() {
        SportEvent e = SportTestKit.lasVegas();
        assertFalse(SportEngine.inWindow(e, LV_FP1 - 21 * MIN));
        assertTrue(SportEngine.inWindow(e, LV_FP1 - 19 * MIN));
        assertTrue(SportEngine.inWindow(e, LV_FP1 + 119 * MIN));   // practice: 60 min + 60 min
        assertFalse(SportEngine.inWindow(e, LV_FP1 + 121 * MIN));
        e.sessions.get(0).state = "in";                            // red flags: stretched to 4 h
        assertTrue(SportEngine.inWindow(e, LV_FP1 + 3 * H));
        assertEquals(LV_FP1 + 4 * H, SportEngine.window(e.sessions.get(0))[1]);  // (FP2's own window is open by then)
        assertTrue(SportEngine.inWindow(e, LV_RACE + 179 * MIN));  // the race: 120 min + 60 min
        assertFalse(SportEngine.inWindow(e, LV_RACE + 181 * MIN));
        e.sessions.get(4).state = "post";
        assertFalse(SportEngine.inWindow(e, LV_RACE + 10 * MIN));
        e.sessions.get(3).tbd = true;
        assertFalse(SportEngine.inWindow(e, SportTestKit.LV_QUAL));
    }

    @Test public void aUfcCardsWindow() {
        SportEvent e = SportTestKit.ufc334();
        assertFalse(SportEngine.inWindow(e, U334_EARLY - 21 * MIN));
        assertTrue(SportEngine.inWindow(e, U334_EARLY - 19 * MIN));
        assertTrue(SportEngine.inWindow(e, U334_MAIN + 4 * H - MIN));
        assertFalse(SportEngine.inWindow(e, U334_MAIN + 4 * H));
        e.mainEvent().state = "in";
        SportEngine.derive(e);
        assertTrue(SportEngine.inWindow(e, U334_MAIN + 6 * H));    // a bout still going: + 7 h
        assertFalse(SportEngine.inWindow(e, U334_MAIN + 7 * H));
        e.mainEvent().state = "post";
        SportEngine.derive(e);
        assertFalse(SportEngine.inWindow(e, U334_MAIN + H));        // the main event is over
    }

    @Test public void theNextChange() {
        List<SportEvent> lv = list(SportTestKit.lasVegas());
        assertEquals(LV_FP1 - 20 * MIN, SportEngine.nextChange(lv, LV_FP1 - DAY));
        assertEquals(LV_FP1, SportEngine.nextChange(lv, LV_FP1 - 10 * MIN));          // the next session moves on
        assertEquals(LV_FP1 + 2 * H, SportEngine.nextChange(lv, LV_FP1 + 10 * MIN));  // FP1's window closes
        lv.get(0).sessions.get(0).state = "in";
        lv.get(0).sessions.get(0).seenLiveAt = LV_FP1 + 3 * H + 50 * MIN;
        assertEquals(LV_FP1 + 4 * H + 20 * MIN, SportEngine.nextChange(Collections.singletonList(lv.get(0)), LV_FP1 + 4 * H + MIN));
        assertEquals(U334_EARLY - 20 * MIN, SportEngine.nextChange(list(SportTestKit.ufc334()), U334_EARLY - DAY));
        assertEquals(U334_MAIN + 4 * H, SportEngine.nextChange(list(SportTestKit.ufc334()), U334_MAIN + 3 * H));
        SportEvent done = SportTestKit.lasVegas();
        for (SportEvent.Session s : done.sessions) s.state = "post";
        assertEquals(Long.MAX_VALUE, SportEngine.nextChange(list(done), LV_FP1));
        assertEquals(Long.MAX_VALUE, SportEngine.nextChange(new ArrayList<SportEvent>(), LV_FP1));
    }

    // ---------- states ----------
    @Test public void aLiveSessionNeverClosedByItsFeedEnds() {
        SportEvent e = SportTestKit.lasVegas();
        SportEvent.Session race = e.sessions.get(4);
        race.state = "in"; race.seenLiveAt = LV_RACE + 3 * H + 50 * MIN;
        List<SportEvent> l = list(e);
        SportEngine.timeStates(l, LV_RACE + 4 * H + 10 * MIN);
        assertEquals("in", race.state);                             // seen 20 min ago
        SportEngine.timeStates(l, LV_RACE + 4 * H + 21 * MIN);
        assertEquals("post", race.state);
        assertEquals("post", e.state);
    }

    @Test public void aResultThatNeverCameIsAwaited() {
        SportEvent e = SportTestKit.lasVegas();
        List<SportEvent> l = list(e);
        SportEngine.timeStates(l, LV_RACE + 5 * H);
        assertEquals("pre", e.sessions.get(4).state);
        SportEngine.timeStates(l, LV_RACE + 6 * H);
        assertEquals("post", e.sessions.get(4).state);
        assertEquals("AWAITING", e.sessions.get(4).detail);
        assertEquals("post", e.state);
        SportEvent u = SportTestKit.ufc334();
        u.sessions.get(2).state = "in";
        SportEngine.timeStates(list(u), U334_MAIN + 7 * H + MIN);
        assertEquals("post", u.sessions.get(2).state);              // never seen live: closed with the window
    }

    @Test public void derivedStates() {
        SportEvent e = SportTestKit.lasVegas();
        SportEngine.derive(e);
        assertEquals("pre", e.state);
        e.sessions.get(0).state = "post";
        SportEngine.derive(e);
        assertEquals("pre", e.state);                               // F1: a finished practice isn't the weekend under way
        e.sessions.get(1).state = "in";
        SportEngine.derive(e);
        assertEquals("in", e.state);
        e.sessions.get(1).state = "post"; e.sessions.get(4).state = "post";
        SportEngine.derive(e);
        assertEquals("post", e.state);                              // the race decides
        for (SportEvent.Session s : e.sessions) s.state = "off";
        SportEngine.derive(e);
        assertEquals("off", e.state);
        SportEvent noRace = SportTestKit.lasVegas();
        noRace.sessions.get(4).state = "off";
        noRace.sessions.get(3).state = "post";
        SportEngine.derive(noRace);
        assertEquals("post", noRace.state);                         // the race cancelled: the last session held decides

        SportEvent u = SportTestKit.ufc334();
        SportEngine.derive(u);
        assertEquals("pre", u.state);
        u.sessions.get(0).state = "post";
        SportEngine.derive(u);
        assertEquals("in", u.state);                                // UFC: a segment is over, the card is not
        u.sessions.get(1).bouts.get(0).state = "in";
        SportEngine.derive(u);
        assertEquals("in", u.state);
        u.mainEvent().state = "post";
        SportEngine.derive(u);
        assertEquals("post", u.state);                              // the main event decides
        SportEvent empty = new SportEvent();
        empty.state = "pre";
        SportEngine.derive(empty);
        assertEquals("pre", empty.state);
    }

    @Test public void worthSaving() {
        SportEvent e = SportTestKit.lasVegas(), n = e.copy();
        assertFalse(SportEngine.worthSaving(e, n));
        n.sessions.get(0).seenLiveAt = 4 * MIN;
        assertFalse(SportEngine.worthSaving(e, n));
        n.sessions.get(0).seenLiveAt = 5 * MIN;
        assertTrue(SportEngine.worthSaving(e, n));
        n = e.copy();
        n.sessions.get(0).state = "in";
        assertTrue(SportEngine.worthSaving(e, n));
    }

    // ---------- the engine on stub feeds (and a Context) ----------
    @Test public void nothingFollowedIsNotDueAndForgetsAnUnfollowedSport() throws Exception {
        assertFalse(SportEngine.pass(c, true, new Engine.Stop()));
        EventStore.save(c, list(SportTestKit.lasVegas()));
        c.getSharedPreferences("sports_engine", 0).edit().putLong("ok_f1", 5).apply();
        assertTrue(new java.io.File(c.dir, "f1_standings.json").createNewFile());
        assertTrue(SportEngine.pass(c, false, new Engine.Stop()));  // forgotten: reported, so Engine draws
        assertTrue(EventStore.load(c).isEmpty());
        assertFalse(new java.io.File(c.dir, "f1_standings.json").exists());
        assertFalse(c.getSharedPreferences("sports_engine", 0).contains("ok_f1"));
    }

    @Test public void theNewestResultKeepsItsImages() {
        long now = SportTestKit.U334_EARLY - 7 * DAY;
        SportEvent older = SportTestKit.ufc("1", "UFC Fight Night: A vs. B", now - 20 * DAY),
            newest = SportTestKit.ufc("2", "UFC Fight Night: C vs. D", now - 5 * DAY),
            next = SportTestKit.ufc("3", "UFC 334: Gane vs. Hokit", now + 7 * DAY),
            far = SportTestKit.ufc("4", "UFC 336: E vs. F", now + 40 * DAY),
            f1Done = SportTestKit.f1("5", now - 12 * DAY), f1Next = SportTestKit.f1("6", now + 3 * DAY);
        List<SportEvent> got = SportEngine.withImages(list(older, newest, next, far, f1Done, f1Next), now);
        assertEquals(list(newest, next, f1Done, f1Next), got);  // the result tiles' events, then the window
        assertTrue(SportEngine.withImages(new java.util.ArrayList<SportEvent>(), now).isEmpty());
    }

    @Test public void aSportJustFollowedHasAPassOfItsOwn() {
        java.util.Set<String> follow = new java.util.LinkedHashSet<>(Arrays.asList("f1", "ufc"));
        assertEquals(Arrays.asList("ufc"), SportEngine.sportsFor(follow, Collections.singleton("ufc"), null));
        // the full pass leaves the sport to that pass while it runs, and has it again after
        assertEquals(Arrays.asList("f1"), SportEngine.sportsFor(follow, null, Collections.singleton("ufc")));
        assertEquals(Arrays.asList("f1", "ufc"), SportEngine.sportsFor(follow, null, Collections.<String>emptySet()));
        assertEquals(Arrays.asList("f1", "ufc"), SportEngine.sportsFor(follow, null, null));
        // unfollowed again before it ran: nothing to download
        assertTrue(SportEngine.sportsFor(Collections.singleton("f1"), Collections.singleton("ufc"), null).isEmpty());
    }

    @Test public void aSportNotFollowedHasNoPassOfItsOwn() {
        SportEngine.follow(c, "ufc");  // returns at once: no feed, no file
        assertTrue(EventStore.load(c).isEmpty());
        assertFalse(c.getSharedPreferences("sports_engine", 0).contains("lastFull_ufc"));
    }

    @Test public void onlyTheImagesASurfaceDrawsAreFetchedAhead() {
        long now = U334_EARLY - 2 * DAY;
        SportEvent next = SportTestKit.ufc334(), later = SportTestKit.ufc("600061500", "UFC Fight Night: Ed vs. Fy", U334_EARLY + 7 * DAY);
        for (SportEvent e : list(next, later))
            for (SportEvent.Session s : e.sessions)
                for (SportEvent.Bout b : s.bouts)
                    for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b}) {
                        String k = e.id + "-" + f.id;
                        f.headshot = "https://a.espncdn.com/head/" + k + ".png";
                        f.flag = "https://a.espncdn.com/flag/" + k + ".png";
                        f.stanceLeft = "https://a.espncdn.com/stance/" + k + "-l.png";
                        f.stanceRight = "https://a.espncdn.com/stance/" + k + "-r.png";
                    }
        SportEvent gp = SportTestKit.f1("6", now + 3 * DAY);
        SportEvent.Entrant x = SportTestKit.entrant("4665", "NOR", 1);
        x.flag = "https://a.espncdn.com/flag/driver-gbr.png";
        gp.sessions.get(0).order.add(x);
        SportPrefs p = new SportPrefs(null, Sports.SCOPE_ALL, Collections.singleton("22"));  // Di Four, on the prelims

        List<String> got = SportEngine.imageUrls(list(gp, next, later), p, now);
        for (String u : got) assertFalse(u, u.contains("stance") || u.contains("driver"));
        assertTrue("the grand prix's host flag (the Clash and Clean cards)", got.contains(EventKit.flagUrl("us")));
        for (SportEvent e : list(next, later)) {
            for (String id : new String[]{"3104622", "4049391", "21", "22"})  // the main event, the followed fighter's bout
                assertTrue(e.id + " " + id, got.contains("https://a.espncdn.com/head/" + e.id + "-" + id + ".png"));
            for (String id : new String[]{"11", "12", "31", "32"})  // a bout no surface heads with
                assertFalse(e.id + " " + id, got.contains("https://a.espncdn.com/head/" + e.id + "-" + id + ".png"));
        }
        for (String id : new String[]{"11", "12", "21", "22", "31", "32", "3104622", "4049391"}) {
            assertTrue("the next card's every flag (the event screen)", got.contains("https://a.espncdn.com/flag/600061400-" + id + ".png"));
            assertFalse("a later card's: on opening it", got.contains("https://a.espncdn.com/flag/600061500-" + id + ".png"));
        }
        assertEquals(1 + 2 * 4 + 8, got.size());

        // under way: the bout in the cage and the next one too
        next.state = "in";
        next.sessions.get(0).bouts.get(0).state = "in";
        got = SportEngine.imageUrls(list(next), SportPrefs.none(), U334_EARLY + 10 * MIN);
        assertTrue(got.contains("https://a.espncdn.com/head/600061400-11.png"));
        assertTrue(got.contains("https://a.espncdn.com/head/600061400-12.png"));
        assertFalse(got.contains("https://a.espncdn.com/head/600061400-31.png"));
    }

    @Test public void anUnfollowWithNothingStoredStillForgetsTheSport() throws Exception {
        android.content.SharedPreferences eng = c.getSharedPreferences("sports_engine", 0);
        Sports.setFollowed(c, "ufc", true);
        assertFalse(eng.contains("forget_ufc"));
        // a card drawn and the calendar saved, but no event stored (the card feed failed, say)
        for (String n : new String[]{"csufc_tvnow.png", "ufc_calendar.json", "csf1_tvnow.png"})
            assertTrue(new java.io.File(c.dir, n).createNewFile());
        c.getSharedPreferences("cards", 0).edit().putString("sig_csufc_tvnow", "x").putString("uri_csufc_tvnow", "y")
            .putString("row_sig_sport_ufc", "z").apply();
        Sports.setFollowed(c, "ufc", false);
        assertTrue(eng.getBoolean("forget_ufc", false));  // the mark outlives the cleared timestamps
        assertTrue(SportEngine.pass(c, false, new Engine.Stop()));  // something forgotten: Engine draws (the row goes)
        assertFalse(new java.io.File(c.dir, "csufc_tvnow.png").exists());
        assertFalse(new java.io.File(c.dir, "ufc_calendar.json").exists());
        assertTrue(c.getSharedPreferences("cards", 0).getAll().isEmpty());
        assertFalse(eng.contains("forget_ufc"));
        // F1 was never followed here, but its card file is a trace too: forgotten as well
        assertFalse(new java.io.File(c.dir, "csf1_tvnow.png").exists());
        assertFalse(SportEngine.pass(c, false, new Engine.Stop()));  // nothing left: nothing to do
        // the row is HomeRows' (removeUnfollowedSports, in the draw): while it is listed, a pass reports a change
        c.getSharedPreferences("row", 0).edit().putLong("sport_ufc", 7L).apply();
        assertTrue(EventStore.traces(c, "ufc", null));
        assertTrue(SportEngine.pass(c, false, new Engine.Stop()));
        c.getSharedPreferences("row", 0).edit().remove("sport_ufc").apply();
        assertFalse(EventStore.traces(c, "ufc", null));
        assertFalse(EventStore.traces(c, "nba", null));
        // followed again: due at once, the mark gone
        Sports.setFollowed(c, "ufc", false);  // not followed: no mark
        assertFalse(eng.contains("forget_ufc"));
        Sports.setFollowed(c, "ufc", true);
        Sports.setFollowed(c, "ufc", false);
        Sports.setFollowed(c, "ufc", true);
        assertFalse(eng.contains("forget_ufc"));
    }

    @Test public void aFailedFeedKeepsTheEventsAndIsAskedAgain() {
        long now = System.currentTimeMillis();
        Sports.setFollowed(c, "f1", true);
        SportEvent e = SportTestKit.f1("1", now + 2 * DAY);
        EventStore.save(c, list(e));
        assertTrue(SportEngine.pass(c, false, new Engine.Stop()));  // never fetched: due
        List<SportEvent> after = EventStore.load(c);
        assertEquals(1, after.size());
        assertEquals("1", after.get(0).id);
        assertEquals("none", after.get(0).sessions.get(0).watch.listedBy);  // SportWhere.guess (stub) ran
        android.content.SharedPreferences sp = c.getSharedPreferences("sports_engine", 0);
        assertFalse(sp.contains("lastFull_f1"));  // not answered: due again on the next pass
        assertTrue(sp.getLong("failedAt", 0) > 0);
        assertTrue(SportEngine.pass(c, false, new Engine.Stop()));
        assertEquals(0, SportEngine.lastOk(c));
        // answered a moment ago: not due
        sp.edit().putLong("lastFull_f1", System.currentTimeMillis()).apply();
        assertFalse(SportEngine.pass(c, false, new Engine.Stop()));
        assertTrue(SportEngine.pass(c, true, new Engine.Stop()));  // unless forced
        Engine.Stop stop = new Engine.Stop();
        stop.set = true;
        assertFalse(SportEngine.pass(c, true, stop));  // stopped before anything was asked
    }

    @Test public void eventsAreTheFollowedSportsWithClockStates() {
        long now = System.currentTimeMillis();
        SportEvent old = SportTestKit.f1("1", now - 3 * DAY);  // never answered: its sessions are awaited by now
        EventStore.save(c, list(old, SportTestKit.ufc("2", "UFC Fight Night", now + DAY)));
        assertTrue(SportEngine.events(c, now).isEmpty());
        Sports.setFollowed(c, "f1", true);
        List<SportEvent> l = SportEngine.events(c, now);
        assertEquals(1, l.size());
        assertEquals("AWAITING", l.get(0).sessions.get(4).detail);
        assertEquals("pre", EventStore.load(c).get(0).sessions.get(4).state);  // clock states are never saved
    }

    @Test public void theTickPollsAnEventInItsWindow() {
        long now = System.currentTimeMillis();
        Sports.setFollowed(c, "f1", true);
        EventStore.save(c, list(SportTestKit.f1("1", now + 5 * MIN)));
        assertTrue(SportEngine.live(c, now));
        assertTrue(SportEngine.tick(c, new HashMap<String, String>(), now));  // the stub feed has no answer: nothing saved
        assertEquals("pre", EventStore.load(c).get(0).sessions.get(0).state);
        EventStore.save(c, list(SportTestKit.f1("1", now + 2 * DAY)));
        assertFalse(SportEngine.live(c, now));
        assertFalse(SportEngine.tick(c, new HashMap<String, String>(), now));
    }

    @Test public void theEngineHooksSeeTheSportsOnlyWhenOneIsFollowed() {
        long now = System.currentTimeMillis();
        EventStore.save(c, list(SportTestKit.f1("1", now + 5 * MIN)));
        assertFalse(Engine.live(c));                                     // no club, no sport: as before
        assertEquals(Long.MAX_VALUE, Engine.nextChangeAll(c, now));
        assertTrue(Sports.nothingFollowed(c));
        Sports.setFollowed(c, "f1", true);
        assertTrue(Engine.live(c));                                      // only F1 followed: the home screen polls
        assertEquals(now + 5 * MIN, Engine.nextChangeAll(c, now));       // FP1's start
        assertFalse(Sports.nothingFollowed(c));                          // the job runs (UpdateJob, Updater)
    }

    @Test public void lastOkAndStale() {
        Sports.setFollowed(c, "f1", true);
        long now = System.currentTimeMillis();
        android.content.SharedPreferences sp = c.getSharedPreferences("sports_engine", 0);
        sp.edit().putLong("ok_f1", now - 7 * H).putLong("ok_ufc", now - H).apply();
        assertEquals(now - 7 * H, SportEngine.lastOk(c));  // ufc isn't followed
        assertTrue(SportEngine.stale(c, "f1"));
        assertFalse(SportEngine.stale(c, "ufc"));
        assertFalse(SportEngine.stale(c, "nothing"));
    }
}
