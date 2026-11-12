package app.fanela.tv;

import static app.fanela.tv.SportTestKit.DAY;
import static app.fanela.tv.SportTestKit.H;
import static app.fanela.tv.SportTestKit.LV_FP1;
import static app.fanela.tv.SportTestKit.LV_RACE;
import static app.fanela.tv.SportTestKit.MIN;
import static app.fanela.tv.SportTestKit.U334_EARLY;
import static app.fanela.tv.SportTestKit.U334_MAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** EventStore (§1.3-1.5): merge never moves backwards, a failed feed keeps the old events, prune, rename by id, files. */
public class EventStoreTest {
    private SportTestKit.Ctx c;

    @Before public void setUp() throws Exception { c = SportTestKit.context(); }
    @After public void tearDown() { SportTestKit.delete(c.dir); }

    private static List<SportEvent> list(SportEvent... es) { return new ArrayList<>(Arrays.asList(es)); }

    private static SportEvent only(List<SportEvent> all, String sport) {
        SportEvent found = null;
        for (SportEvent e : all) if (sport.equals(e.sport)) { assertNull("one event of " + sport, found); found = e; }
        return found;
    }

    // ---------- failed feeds and identity ----------
    @Test public void aFailedFeedKeepsThatSportsEvents() {
        SportEvent lv = SportTestKit.lasVegas(), ufc = SportTestKit.ufc334();
        List<SportEvent> all = list(lv, ufc);
        List<SportEvent> out = EventStore.merge(all, FeedResult.failed(SportEvent.F1), LV_FP1);
        assertEquals(2, out.size());
        assertEquals(lv.signature(), out.get(0).signature());
        assertEquals(all.size(), EventStore.merge(all, null, LV_FP1).size());
    }

    @Test public void aSportsAnswerNeverTouchesTheOtherSport() {
        SportEvent lv = SportTestKit.lasVegas(), ufc = SportTestKit.ufc334();
        SportEvent fresh = lv.copy();
        fresh.sessions.get(0).state = "post";
        List<SportEvent> out = EventStore.merge(list(lv, ufc), SportTestKit.ok(SportEvent.F1, fresh, SportTestKit.ufc334()), LV_FP1 + 2 * H);
        assertEquals(2, out.size());  // the UFC event in an F1 answer is ignored
        assertEquals("post", only(out, "f1").sessions.get(0).state);
        assertEquals(ufc.signature(), only(out, "ufc").signature());
    }

    @Test public void anEventLeftOutOfAnAnswerIsKept() {
        SportEvent lv = SportTestKit.lasVegas(), next = SportTestKit.f1("600057450", LV_FP1 + 7 * DAY);
        List<SportEvent> out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, next), LV_FP1 - DAY);
        assertEquals(2, out.size());
    }

    @Test public void aRenamedEventIsTheSameEvent() {
        SportEvent ufc = SportTestKit.ufc334();
        ufc.sessions.get(2).watch.channel = "Magenta Sport 6";
        SportEvent fresh = SportTestKit.ufc334();
        fresh.name = "UFC 334: Gane vs. Aspinall";  // a main-event change renames the event; the id stays
        List<SportEvent> out = EventStore.merge(list(ufc), SportTestKit.ok(SportEvent.UFC, fresh), U334_EARLY - DAY);
        assertEquals(1, out.size());
        assertEquals("UFC 334: Gane vs. Aspinall", out.get(0).name);
        assertEquals("Magenta Sport 6", out.get(0).sessions.get(2).watch.channel);
    }

    @Test public void aRefreshNeverTouchesTheWatchFields() {
        SportEvent lv = SportTestKit.lasVegas();
        SportEvent.SessionWatch w = lv.sessions.get(4).watch;
        w.channel = "ANT1"; w.app = Cards.MAGENTA; w.no = 107; w.sure = true; w.known = true;
        SportEvent fresh = SportTestKit.lasVegas();
        fresh.sessions.get(4).watch.channel = "junk from a feed";
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), LV_FP1 - DAY).get(0);
        assertEquals("ANT1", out.sessions.get(4).watch.channel);
        assertEquals(107, out.sessions.get(4).watch.no);
        assertTrue(out.sessions.get(4).watch.sure);
    }

    @Test public void fieldsAFetchLeftOutAreKept() {
        SportEvent lv = SportTestKit.lasVegas(), fresh = SportTestKit.lasVegas();
        fresh.circuitName = ""; fresh.laps = 0; fresh.lengthKm = 0; fresh.fetchedAt = 5;
        lv.fetchedAt = 10;
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), LV_FP1 - DAY).get(0);
        assertEquals("Las Vegas Street Circuit", out.circuitName);
        assertEquals(50, out.laps);
        assertEquals(6.116f, out.lengthKm, 0.0001);
        assertEquals(10, out.fetchedAt);
    }

    // ---------- never backwards ----------
    @Test public void aLiveSessionIsNotSetBackToPreWithinSixHours() {
        SportEvent lv = SportTestKit.lasVegas();
        SportEvent.Session race = lv.sessions.get(4);
        race.state = "in"; race.lap = 30; race.order.add(SportTestKit.entrant("5579", "NOR", 1));
        SportEvent lagging = SportTestKit.lasVegas();  // a cached answer: still "pre", no order
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, lagging), LV_RACE + H).get(0);
        assertEquals("in", out.sessions.get(4).state);
        assertEquals(30, out.sessions.get(4).lap);
        assertEquals(1, out.sessions.get(4).order.size());
        assertEquals("in", out.state);
        // past 6 h the feed is believed (timeStates closes it otherwise)
        out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, lagging), LV_RACE + 6 * H + MIN).get(0);
        assertEquals("pre", out.sessions.get(4).state);
    }

    @Test public void aFinishedSessionIsNotSetBackWithinADay() {
        SportEvent lv = SportTestKit.lasVegas();
        SportEvent.Session qual = lv.sessions.get(3);
        qual.state = "post"; qual.order.add(SportTestKit.entrant("5579", "NOR", 1));
        for (String lag : new String[]{"pre", "in"}) {
            SportEvent fresh = SportTestKit.lasVegas();
            fresh.sessions.get(3).state = lag;
            SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), qual.start + 3 * H).get(0);
            assertEquals("post", out.sessions.get(3).state);
            assertEquals("NOR", out.sessions.get(3).order.get(0).code);
        }
        SportEvent fresh = SportTestKit.lasVegas();
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), qual.start + 25 * H).get(0);
        assertEquals("pre", out.sessions.get(3).state);  // a day on, a re-run (or a correction) is believed
        // and the other way is always allowed: pre -> in -> post, post -> off
        fresh = SportTestKit.lasVegas();
        fresh.sessions.get(3).state = "off"; fresh.sessions.get(3).detail = "CANCELLED";
        out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), qual.start + 3 * H).get(0);
        assertEquals("off", out.sessions.get(3).state);
    }

    @Test public void aTicksLiveFieldsWinWhileNewerAndFresh() {
        SportEvent lv = SportTestKit.lasVegas();
        SportEvent.Session race = lv.sessions.get(4);
        race.state = "in"; race.lap = 34; race.seenLiveAt = LV_RACE + H;
        SportEvent refresh = SportTestKit.lasVegas();
        refresh.sessions.get(4).state = "in"; refresh.sessions.get(4).lap = 30;  // an older reading
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, refresh), LV_RACE + H + 5 * MIN).get(0);
        assertEquals(34, out.sessions.get(4).lap);
        out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, refresh), LV_RACE + H + 11 * MIN).get(0);
        assertEquals(30, out.sessions.get(4).lap);  // 10 min on, the tick is no longer fresher than the refresh
        assertEquals(LV_RACE + H, out.sessions.get(4).seenLiveAt);  // the newest seenLiveAt is kept
    }

    @Test public void aBoutIsNotSetBackEvenWhenItsSegmentMoves() {
        SportEvent ufc = SportTestKit.ufc334();
        SportEvent.Bout main = ufc.mainEvent();
        main.state = "post"; main.outcome = 2; main.method = "KO/TKO"; main.round = 1; main.clock = "0:42";
        ufc.sessions.get(2).state = "in";
        SportEvent fresh = SportTestKit.ufc334();
        fresh.sessions.get(2).state = "in";
        fresh.sessions.get(2).seenLiveAt = U334_MAIN + H;  // a newer session reading, but the bout lags
        SportEvent out = EventStore.merge(list(ufc), SportTestKit.ok(SportEvent.UFC, fresh), U334_MAIN + H).get(0);
        assertEquals("post", out.mainEvent().state);
        assertEquals(2, out.mainEvent().outcome);
        assertEquals("KO/TKO", out.mainEvent().method);
        assertEquals("post", out.state);  // the main event is over: the event is
    }

    @Test public void aBoutListIsReplacedWholesale() {
        SportEvent ufc = SportTestKit.ufc334();
        SportEvent fresh = SportTestKit.ufc334();
        fresh.sessions.get(1).bouts.clear();  // the prelims bout was cancelled
        SportEvent.Bout moved = fresh.sessions.get(0).bouts.remove(0);
        fresh.sessions.get(1).bouts.add(moved);  // and the early bout moved up to the prelims
        SportEvent out = EventStore.merge(list(ufc), SportTestKit.ok(SportEvent.UFC, fresh), U334_EARLY - DAY).get(0);
        assertEquals(0, out.sessions.get(0).bouts.size());
        assertEquals(1, out.sessions.get(1).bouts.size());
        assertEquals("Al One", out.sessions.get(1).bouts.get(0).a.name);
        assertNull(out.boutOf("21"));
    }

    @Test public void aSessionBeingPlayedThatAnAnswerLeftOutStays() {
        SportEvent lv = SportTestKit.lasVegas();
        lv.sessions.get(4).state = "in";
        SportEvent fresh = SportTestKit.lasVegas();
        fresh.sessions.remove(4);
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, fresh), LV_RACE + H).get(0);
        assertEquals(5, out.sessions.size());
        assertEquals("in", out.sessions.get(4).state);
        out = EventStore.merge(list(SportTestKit.lasVegas()), SportTestKit.ok(SportEvent.F1, fresh), LV_RACE + H).get(0);
        assertEquals(4, out.sessions.size());  // one that isn't being played goes
        assertEquals(SportTestKit.LV_QUAL, out.end);
    }

    // ---------- results after the event ----------
    @Test public void resultsMayChangeFor48HoursThenAreFrozen() {
        SportEvent lv = SportTestKit.lasVegas();
        for (SportEvent.Session s : lv.sessions) s.state = "post";
        lv.sessions.get(4).order.add(SportTestKit.entrant("5579", "NOR", 1));
        lv.sessions.get(4).order.add(SportTestKit.entrant("1", "VER", 2));
        SportEvent stewards = lv.copy();
        stewards.sessions.get(4).order.clear();  // a penalty swaps the top two
        stewards.sessions.get(4).order.add(SportTestKit.entrant("1", "VER", 1));
        stewards.sessions.get(4).order.add(SportTestKit.entrant("5579", "NOR", 2));
        SportEvent out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, stewards), LV_RACE + 30 * H).get(0);
        assertEquals("VER", out.sessions.get(4).order.get(0).code);
        out = EventStore.merge(list(lv), SportTestKit.ok(SportEvent.F1, stewards), LV_RACE + 49 * H).get(0);
        assertEquals("NOR", out.sessions.get(4).order.get(0).code);

        SportEvent ufc = SportTestKit.ufc334();
        for (SportEvent.Session s : ufc.sessions) { s.state = "post"; for (SportEvent.Bout b : s.bouts) b.state = "post"; }
        ufc.mainEvent().outcome = 1; ufc.mainEvent().method = "SUB";
        SportEvent overturned = ufc.copy();
        overturned.mainEvent().outcome = 4; overturned.mainEvent().method = "NC";
        out = EventStore.merge(list(ufc), SportTestKit.ok(SportEvent.UFC, overturned), U334_MAIN + 40 * H).get(0);
        assertEquals(4, out.mainEvent().outcome);
        out = EventStore.merge(list(ufc), SportTestKit.ok(SportEvent.UFC, overturned), U334_MAIN + 50 * H).get(0);
        assertEquals(1, out.mainEvent().outcome);
        assertEquals("SUB", out.mainEvent().method);
    }

    // ---------- recordBefore ----------
    @Test public void aRecordBeforeIsCapturedOnceAndNeverOverwritten() {
        SportEvent first = SportTestKit.ufc334();  // first seen before the bouts
        SportEvent stored = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.UFC, first), U334_EARLY - DAY).get(0);
        assertEquals("14-2-0", stored.mainEvent().a.recordBefore);
        assertEquals("9-0-0", stored.mainEvent().b.recordBefore);
        SportEvent after = SportTestKit.ufc334();  // after the bout ESPN shows the updated records
        SportEvent.Bout m = after.mainEvent();
        m.state = "post"; m.outcome = 2; m.a.record = "14-3-0"; m.b.record = "10-0-0";
        SportEvent out = EventStore.merge(list(stored), SportTestKit.ok(SportEvent.UFC, after), U334_MAIN + 2 * H).get(0);
        assertEquals("14-2-0", out.mainEvent().a.recordBefore);
        assertEquals("9-0-0", out.mainEvent().b.recordBefore);
        assertEquals("10-0-0", out.mainEvent().b.record);
    }

    @Test public void aRecordBeforeIsRebuiltWhenFirstSeenAfterTheBout() {
        SportEvent after = SportTestKit.ufc334();
        SportEvent.Bout m = after.mainEvent();
        m.state = "post"; m.outcome = 2; m.a.record = "14-3-0"; m.b.record = "10-0-0";
        SportEvent.Bout draw = after.boutOf("21");
        draw.state = "post"; draw.outcome = 3; draw.a.record = "10-3-1"; draw.b.record = "9-4-2";
        SportEvent.Bout nc = after.boutOf("31");
        nc.state = "post"; nc.outcome = 4;
        SportEvent out = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.UFC, after), U334_MAIN + 3 * H).get(0);
        assertEquals("14-2-0", out.mainEvent().a.recordBefore);  // loser: L-1
        assertEquals("9-0-0", out.mainEvent().b.recordBefore);   // winner: W-1
        assertEquals("10-3-0", out.boutOf("21").a.recordBefore);  // draw: D-1 for both
        assertEquals("9-4-1", out.boutOf("21").b.recordBefore);
        assertEquals("20-5-0", out.boutOf("31").a.recordBefore);  // no contest: unchanged
        assertEquals("5-1-0", out.boutOf("11").a.recordBefore);   // still to come: as it stands
    }

    @Test public void aRecordBeforeFollowsABoutThatMovedSegment() {
        SportEvent stored = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.UFC, SportTestKit.ufc334()),
            U334_EARLY - DAY).get(0);
        SportEvent fresh = SportTestKit.ufc334();
        SportEvent.Bout b = fresh.sessions.get(0).bouts.remove(0);
        b.a.record = "6-1-0";  // somehow updated already
        fresh.sessions.get(1).bouts.add(b);
        SportEvent out = EventStore.merge(list(stored), SportTestKit.ok(SportEvent.UFC, fresh), U334_EARLY - H).get(0);
        assertEquals("5-1-0", out.boutOf("11").a.recordBefore);
    }

    // ---------- misses ----------
    @Test public void anEventGoneFromTheCalendarIsDroppedAfterThreeMisses() {
        SportEvent bahrain = SportTestKit.f1("600057400", LV_FP1 + 30 * DAY);
        List<SportEvent> all = list(bahrain);
        FeedResult r = SportTestKit.ok(SportEvent.F1);
        r.missed.add(bahrain.id);
        r.listed = new HashSet<>(Collections.singletonList("600057449"));
        all = EventStore.merge(all, r, LV_FP1);
        assertEquals(1, all.get(0).misses);
        all = EventStore.merge(all, r, LV_FP1);
        assertEquals(2, all.get(0).misses);
        FeedResult listed = SportTestKit.ok(SportEvent.F1);
        listed.missed.add(bahrain.id);
        listed.listed = new HashSet<>(Collections.singletonList(bahrain.id));
        assertEquals(1, EventStore.merge(EventStore.merge(all, listed, LV_FP1), listed, LV_FP1).size());  // still listed: kept
        FeedResult noCalendar = SportTestKit.ok(SportEvent.F1);
        noCalendar.missed.add(bahrain.id);
        assertEquals(1, EventStore.merge(EventStore.merge(all, noCalendar, LV_FP1), noCalendar, LV_FP1).size());  // unknown: kept
        assertTrue(EventStore.merge(all, r, LV_FP1).isEmpty());  // third miss, not listed: gone
        // an answer that returns it resets the count
        List<SportEvent> back = EventStore.merge(all, SportTestKit.ok(SportEvent.F1, bahrain.copy()), LV_FP1);
        assertEquals(0, back.get(0).misses);
        // a failed fetch counts nothing
        assertEquals(2, EventStore.merge(all, FeedResult.failed(SportEvent.F1), LV_FP1).get(0).misses);
    }

    // ---------- prune ----------
    @Test public void pruneKeepsSixtyDaysAndTheNextFewEvents() {
        long now = LV_FP1 - DAY;
        List<SportEvent> all = new ArrayList<>();
        all.add(SportTestKit.f1("old", LV_FP1 - 70 * DAY));   // ended 68 days ago: dropped
        all.add(SportTestKit.f1("recent", LV_FP1 - 50 * DAY));  // kept: the app's latest results
        for (int i = 0; i < 5; i++) all.add(SportTestKit.f1("n" + i, LV_FP1 + i * 7 * DAY));  // only the next 3
        for (int i = 0; i < 6; i++) all.add(SportTestKit.ufc("u" + i, "UFC Fight Night " + i, U334_EARLY + i * 7 * DAY));
        List<SportEvent> out = EventStore.prune(all, now);
        List<String> ids = new ArrayList<>();
        for (SportEvent e : out) ids.add(e.id);
        assertFalse(ids.contains("old"));
        assertTrue(ids.contains("recent"));
        assertTrue(ids.containsAll(Arrays.asList("n0", "n1", "n2")));
        assertFalse(ids.contains("n3"));
        assertFalse(ids.contains("n4"));
        // UFC: u0 is over by now (14 Nov), so the next four are u1-u4
        assertTrue(ids.containsAll(Arrays.asList("u0", "u1", "u2", "u3", "u4")));
        assertFalse(ids.contains("u5"));
        for (int i = 1; i < out.size(); i++) assertTrue(out.get(i - 1).start <= out.get(i).start);
    }

    @Test public void aCancelledEventGoesAWeekAfterItsDate() {
        SportEvent bahrain = SportTestKit.f1("bahrain", LV_FP1);
        for (SportEvent.Session s : bahrain.sessions) { s.state = "off"; s.detail = "CANCELLED"; }
        assertEquals(1, EventStore.prune(list(bahrain), LV_RACE + 6 * DAY).size());
        assertTrue(EventStore.prune(list(bahrain), LV_RACE + 8 * DAY).isEmpty());
        // a cancelled event ahead does not use up one of the next three
        List<SportEvent> all = list(bahrain);
        for (int i = 1; i <= 3; i++) all.add(SportTestKit.f1("n" + i, LV_FP1 + i * 7 * DAY));
        assertEquals(4, EventStore.prune(all, LV_FP1 - DAY).size());
    }

    // ---------- live ----------
    @Test public void aTickCopiesOnlyTheLiveFields() {
        SportEvent ufc = SportTestKit.ufc334();
        ufc.sessions.get(2).watch.channel = "Magenta Sport 6";
        SportEvent live = ufc.copy();
        live.name = "something else";  // not a live field
        live.sessions.get(2).watch.channel = "junk";
        SportEvent.Bout m = live.mainEvent();
        m.state = "in"; m.round = 2; m.clock = "3:10";
        SportEvent.Bout ghost = SportTestKit.bout("999", 9, SportTestKit.fighter("1", "No Body", "0-0-0"), SportTestKit.fighter("2", "Some One", "0-0-0"));
        ghost.state = "in";
        live.sessions.get(0).bouts.add(ghost);  // a scoreboard bout the card doesn't have: never invented
        live.sessions.get(2).state = "in"; live.sessions.get(2).seenLiveAt = U334_MAIN + H;
        SportEvent out = EventStore.applyLive(ufc, live, U334_MAIN + H);
        assertEquals("UFC 334: Gane vs. Hokit", out.name);
        assertEquals("Magenta Sport 6", out.sessions.get(2).watch.channel);
        assertEquals(2, out.mainEvent().round);
        assertEquals("3:10", out.mainEvent().clock);
        assertNull(out.boutOf("1"));
        assertEquals("in", out.state);
        assertEquals(U334_MAIN + H, out.sessions.get(2).seenLiveAt);
    }

    @Test public void aTickFindsABoutWhateverSegmentItCameIn() {
        SportEvent ufc = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.UFC, SportTestKit.ufc334()),
            U334_EARLY - DAY).get(0);  // stored a day before: the records as they stood
        SportEvent live = new SportEvent();
        live.sport = "ufc"; live.id = ufc.id;
        SportEvent.Session shell = SportTestKit.session("scoreboard", "MAIN", 0);  // no such session: no session fields copied
        SportEvent.Bout b = ufc.boutOf("21").copy();
        b.state = "post"; b.outcome = 1;
        shell.bouts.add(b);
        live.sessions.add(shell);
        SportEvent out = EventStore.applyLive(ufc, live, SportTestKit.U334_PRELIMS + H);
        assertEquals("post", out.boutOf("21").state);
        assertEquals(1, out.boutOf("21").outcome);
        assertEquals(3, out.sessions.size());
        assertEquals("10-3-0", out.boutOf("21").a.recordBefore);  // the record from before the bout
    }

    // ---------- files ----------
    @Test public void savedEventsLoadBackAsCopies() {
        List<SportEvent> all = list(SportTestKit.lasVegas(), SportTestKit.ufc334());
        EventStore.save(c, all);
        assertTrue(new File(c.dir, "events1.json").exists());
        List<SportEvent> a = EventStore.load(c);
        assertEquals(2, a.size());
        assertEquals(all.get(0).signature(), a.get(0).signature());
        a.get(0).sessions.get(0).state = "post";
        List<SportEvent> b = EventStore.load(c);
        assertEquals("pre", b.get(0).sessions.get(0).state);
        assertNotSame(a.get(0), b.get(0));
        // a second save replaces the file (atomically) and is seen at once
        b.get(0).name = "Las Vegas Grand Prix";
        EventStore.save(c, b);
        assertEquals("Las Vegas Grand Prix", EventStore.load(c).get(0).name);
        assertFalse(new File(c.dir, "events1.json.tmp").exists());
    }

    @Test public void noFileMeansNoEvents() {
        assertTrue(EventStore.load(c).isEmpty());
    }

    @Test public void retainDropsUnfollowedSports() {
        EventStore.save(c, list(SportTestKit.lasVegas(), SportTestKit.ufc334()));
        EventStore.retain(c, new HashSet<>(Collections.singletonList("ufc")));
        List<SportEvent> left = EventStore.load(c);
        assertEquals(1, left.size());
        assertEquals("ufc", left.get(0).sport);
    }

    @Test public void forgetSportDeletesItsFilesAndPrefs() throws Exception {
        for (String n : new String[]{"csf1_tvnow.png", "csf1_tvlast.png", "f1_standings.json", "f1_roster.json", "f1_circuits.json",
            "csufc_tvnow.png", "ufc_calendar.json", "c435_tvnow.png", "matches4.json"})
            assertTrue(new File(c.dir, n).createNewFile());
        c.getSharedPreferences("cards", 0).edit().putString("sig_csf1_tvnow", "x").putString("uri_csf1_tvnow", "y")
            .putString("row_sig_sport_f1", "z").putString("sig_csufc_tvnow", "u").putString("sig_c435_tvnow", "v").apply();
        c.getSharedPreferences("sports_engine", 0).edit().putLong("lastFull_f1", 1).putLong("ok_f1", 1).putLong("res_f1", 1)
            .putLong("lastCal_f1", 1).putLong("ok_ufc", 1).apply();
        EventStore.forgetSport(c, "f1");
        List<String> left = Arrays.asList(c.dir.list());
        assertFalse(left.contains("csf1_tvnow.png"));
        assertFalse(left.contains("csf1_tvlast.png"));
        assertFalse(left.contains("f1_standings.json"));
        assertFalse(left.contains("f1_roster.json"));
        assertFalse(left.contains("f1_circuits.json"));
        assertTrue(left.containsAll(Arrays.asList("csufc_tvnow.png", "ufc_calendar.json", "c435_tvnow.png", "matches4.json")));
        assertEquals(new HashSet<>(Arrays.asList("sig_csufc_tvnow", "sig_c435_tvnow")), c.getSharedPreferences("cards", 0).getAll().keySet());
        assertEquals(Collections.singleton("ok_ufc"), c.getSharedPreferences("sports_engine", 0).getAll().keySet());
    }

    @Test public void findByAnyKey() {
        List<SportEvent> all = list(SportTestKit.lasVegas(), SportTestKit.ufc334());
        assertEquals("600061400", EventStore.find(all, "ufc|espn:600061400#main").id);
        assertEquals("600057449", EventStore.find(all, "f1|espn:600057449").id);
        assertNull(EventStore.find(all, "ufc|espn:1"));
        assertNull(EventStore.find(all, null));
    }

    @Test public void boundsFollowTheSessions() {
        SportEvent fresh = SportTestKit.lasVegas();
        fresh.start = 0; fresh.end = 0;
        Collections.reverse(fresh.sessions);
        SportEvent out = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.F1, fresh), LV_FP1 - DAY).get(0);
        assertEquals(LV_FP1, out.start);
        assertEquals(LV_RACE, out.end);
        assertEquals("FP1", out.sessions.get(0).kind);  // kept in start order
    }
}
