package app.fanela.tv;

import static app.fanela.tv.SportTestKit.DAY;
import static app.fanela.tv.SportTestKit.H;
import static app.fanela.tv.SportTestKit.LV_FP1;
import static app.fanela.tv.SportTestKit.LV_FP2;
import static app.fanela.tv.SportTestKit.LV_QUAL;
import static app.fanela.tv.SportTestKit.LV_RACE;
import static app.fanela.tv.SportTestKit.MIN;
import static app.fanela.tv.SportTestKit.U334_EARLY;
import static app.fanela.tv.SportTestKit.U334_MAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The two tiles of a sport row (F1-UFC-ARCHITECTURE §5.1): which event and session each one is about. */
public class EventPickTest {
    private TimeZone was;

    @Before public void athens() { was = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens")); }

    @After public void restore() { TimeZone.setDefault(was); }

    static String kindOf(EventCards.Pick p) { return p == null ? null : p.e.session(p.sessionId).kind; }

    static EventCards.Pick now(List<SportEvent> all, String sport, SportPrefs p, long now) { return EventCards.pickNow(all, sport, p, now); }

    static EventCards.Pick last(List<SportEvent> all, String sport, long now) { return EventCards.pickLast(all, sport, SportPrefs.none(), now); }

    // ---------- F1: now ----------

    @Test public void beforeTheWeekendTheRaceThenEachNextSession() {
        SportEvent e = EventFixtures.derived(EventFixtures.vegas());
        List<SportEvent> all = Arrays.asList(e);
        assertEquals("RACE", kindOf(now(all, "f1", null, LV_FP1 - 5 * DAY)));
        assertEquals("RACE", kindOf(now(all, "f1", null, LV_FP1 - 2 * DAY - 6 * H)));
        assertEquals("FP1", kindOf(now(all, "f1", null, LV_FP1 - 20 * H)));  // tomorrow: the weekend has begun for the card
        EventFixtures.done(e, "FP1");
        assertEquals("FP2", kindOf(now(all, "f1", null, LV_FP1 + 2 * H)));
        EventFixtures.done(e, "FP2", "FP3");
        assertEquals("QUAL", kindOf(now(all, "f1", null, LV_QUAL - H)));
        // a session that should have started but the feed hasn't said "in" yet stays the card for 3 h
        assertEquals("QUAL", kindOf(now(all, "f1", null, LV_QUAL + 2 * H)));
        EventFixtures.done(e, "QUAL");
        assertEquals("RACE", kindOf(now(all, "f1", null, LV_QUAL + 2 * H)));
    }

    @Test public void aLiveSessionWinsAndTheLastTileSkipsPractice() {
        SportEvent e = EventFixtures.vegas();
        EventFixtures.kind(e, "FP2").state = "in";
        EventFixtures.done(e, "FP1");
        List<SportEvent> all = Arrays.asList(EventFixtures.derived(e));
        assertEquals("FP2", kindOf(now(all, "f1", null, LV_FP2 + 10 * MIN)));
        assertNull("practice never makes a last tile", last(all, "f1", LV_FP2 + 10 * MIN));
    }

    @Test public void theNextWeekendAfterARaceAndTheEarliestOfTwo() {
        SportEvent done = EventFixtures.raceDone("VER", "NOR", "ANT");
        SportEvent qatar = EventFixtures.vegas();
        qatar.id = "600057450"; qatar.name = "Qatar Airways Qatar Grand Prix";
        for (SportEvent.Session s : qatar.sessions) { s.start += 7 * DAY; s.id = "q" + s.kind; }
        qatar.start += 7 * DAY; qatar.end += 7 * DAY;
        SportEvent abuDhabi = EventFixtures.vegas();
        abuDhabi.id = "600057451";
        for (SportEvent.Session s : abuDhabi.sessions) { s.start += 14 * DAY; s.id = "a" + s.kind; }
        abuDhabi.start += 14 * DAY; abuDhabi.end += 14 * DAY;
        List<SportEvent> all = Arrays.asList(EventFixtures.derived(abuDhabi), done, EventFixtures.derived(qatar));
        EventCards.Pick p = now(all, "f1", null, LV_RACE + 5 * H);
        assertSame(qatar, p.e);
        assertEquals("qRACE", p.sessionId);
        EventCards.Pick l = last(all, "f1", LV_RACE + 5 * H);
        assertSame(done, l.e);
        assertEquals("RACE", l.e.session(l.sessionId).kind);
        assertTrue(l.last);
    }

    @Test public void aCancelledWeekendShowsUntilItsRaceDayIsOverNeverBeforeAnEarlierOne() {
        SportEvent bahrain = EventFixtures.bahrainCancelled();
        List<SportEvent> all = new ArrayList<>(Arrays.asList(bahrain));
        assertSame(bahrain, now(all, "f1", null, LV_FP1 - 2 * DAY).e);
        assertSame(bahrain, now(all, "f1", null, LV_RACE + 11 * H).e);
        assertNull(now(all, "f1", null, LV_RACE + 13 * H));
        SportEvent earlier = EventFixtures.vegas();
        earlier.id = "600057449";
        for (SportEvent.Session s : earlier.sessions) s.start -= 7 * DAY;
        earlier.start -= 7 * DAY; earlier.end -= 7 * DAY;
        all.add(EventFixtures.derived(earlier));
        assertSame(earlier, now(all, "f1", null, LV_FP1 - 9 * DAY).e);
    }

    @Test public void offSeasonTheNextOpenerOrNothing() {
        SportEvent done = EventFixtures.raceDone("VER", "NOR", "ANT");
        assertNull(now(Arrays.asList(done), "f1", null, LV_RACE + 20 * DAY));
        SportEvent opener = EventFixtures.derived(EventFixtures.australia2027());
        EventCards.Pick p = now(Arrays.asList(done, opener), "f1", null, LV_RACE + 20 * DAY);
        assertSame(opener, p.e);
        assertEquals("RACE", kindOf(p));
    }

    // ---------- F1: last ----------

    @Test public void qualifyingCountsUntilTheWeekendsNextSessionStarts() {
        SportEvent e = EventFixtures.vegas();
        EventFixtures.done(e, "FP1", "FP2", "FP3", "QUAL");
        EventFixtures.order(EventFixtures.kind(e, "QUAL"), "NOR", "VER", "ANT");
        List<SportEvent> all = Arrays.asList(EventFixtures.derived(e));
        assertEquals("QUAL", kindOf(last(all, "f1", LV_QUAL + 2 * H)));
        assertNull("the race has started: qualifying no longer counts", last(all, "f1", LV_RACE + 10 * MIN));
        EventFixtures.kind(e, "RACE").state = "in";
        assertNull(last(all, "f1", LV_RACE + 10 * MIN));
        EventFixtures.done(e, "RACE");
        assertEquals("RACE", kindOf(last(all, "f1", LV_RACE + 3 * H)));
    }

    @Test public void onASprintWeekendTheSprintThenQualifyingThenTheRace() {
        SportEvent e = EventFixtures.sprintWeekend();
        EventFixtures.done(e, "FP1", "SQ");
        List<SportEvent> all = Arrays.asList(EventFixtures.derived(e));
        long sprint = EventFixtures.kind(e, "SPRINT").start, qual = EventFixtures.kind(e, "QUAL").start;
        assertEquals("SQ", kindOf(last(all, "f1", sprint - H)));
        EventFixtures.done(e, "SPRINT");
        assertEquals("SPRINT", kindOf(last(all, "f1", sprint + 2 * H)));
        EventFixtures.done(e, "QUAL");
        assertEquals("QUAL", kindOf(last(all, "f1", qual + 2 * H)));
        EventFixtures.done(e, "RACE");
        assertEquals("RACE", kindOf(last(all, "f1", qual + 2 * DAY)));
    }

    @Test public void anAwaitingRaceIsTheLastTileACancelledOneIsNot() {
        EventFixtures.Fx aw = EventFixtures.named("f1_awaiting");
        assertEquals("RACE", kindOf(aw.pick()));
        assertNull(last(Arrays.asList(EventFixtures.bahrainCancelled()), "f1", LV_RACE + DAY));
    }

    // ---------- UFC ----------

    @Test public void ufcTheLiveEventElseTheNextShownElseNothing() {
        SportEvent u334 = EventFixtures.derived(EventFixtures.u334());
        SportEvent fn = EventFixtures.derived(EventFixtures.fightNight());  // a week earlier, not numbered
        List<SportEvent> all = Arrays.asList(u334, fn);
        assertSame(fn, now(all, "ufc", SportPrefs.none(), fn.start - DAY).e);
        assertNull(now(all, "ufc", SportPrefs.none(), fn.start - DAY).sessionId);
        SportPrefs numbered = new SportPrefs(null, Sports.SCOPE_NUMBERED, null);
        assertSame("numbered only: the Fight Night is skipped", u334, now(all, "ufc", numbered, fn.start - DAY).e);
        SportPrefs walker = new SportPrefs(null, Sports.SCOPE_NUMBERED, new java.util.HashSet<>(Arrays.asList("3156612")));
        assertSame("unless a followed fighter is on it", fn, now(all, "ufc", walker, fn.start - DAY).e);
        assertSame(u334, now(all, "ufc", SportPrefs.none(), U334_EARLY - DAY).e);
        assertNull(now(all, "ufc", SportPrefs.none(), U334_MAIN + 7 * H));
        assertNull(now(all, "f1", SportPrefs.none(), U334_EARLY - DAY));
        EventFixtures.Fx live = EventFixtures.named("ufc_live_prelims");
        assertSame(live.e, live.pick().e);
    }

    @Test public void ufcLastIsTheNewestFinishedEvent() {
        SportEvent older = EventFixtures.u334Result(2, "KO/TKO", "Punch", 3, "2:27");
        older.id = "600061300";
        for (SportEvent.Session s : older.sessions) s.start -= 14 * DAY;
        older.start -= 14 * DAY; older.end -= 14 * DAY;
        SportEvent newer = EventFixtures.u334Result(1, "SUB", "Choke", 1, "0:44");
        SportEvent upcoming = EventFixtures.derived(EventFixtures.u334());
        upcoming.id = "600061500";
        List<SportEvent> all = Arrays.asList(older, upcoming, newer);
        EventCards.Pick l = last(all, "ufc", U334_MAIN + 6 * H);
        assertSame(newer, l.e);
        assertTrue(l.last);
        assertNull(last(Arrays.asList(upcoming), "ufc", U334_MAIN + 6 * H));
    }

    @Test public void theCancelledUfcEventShowsForTwelveHours() {
        EventFixtures.Fx cx = EventFixtures.named("ufc_cancelled");
        assertSame(cx.e, now(Arrays.asList(cx.e), "ufc", SportPrefs.none(), U334_MAIN + 11 * H).e);
        assertNull(now(Arrays.asList(cx.e), "ufc", SportPrefs.none(), U334_MAIN + 13 * H));
    }
}
