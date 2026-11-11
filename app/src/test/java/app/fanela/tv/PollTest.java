package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** How often Fanela asks for data: live ticks, clock wake-ups, refresh tiers and ESPN's results list. */
public class PollTest {
    static final long MIN = 60000L, HOUR = 60 * MIN, NOW = 1_800_000_000_000L;

    static Match m(String source, String state, long start) {
        Match m = StoreTest.match("1", state, null, null);
        m.source = source;
        m.key = "435|" + source + ":1";
        m.start = start;
        return m;
    }

    // ---------- the home screen's cycle ----------
    @Test public void aBusyRetryDoesNotPollTheScoresAgain() {
        assertTrue(HomeActivity.tickDue(NOW, 0));
        assertFalse("3 s later, while another refresh is busy", HomeActivity.tickDue(NOW + 3000, NOW));
        assertFalse(HomeActivity.tickDue(NOW + 27000, NOW));
        assertTrue(HomeActivity.tickDue(NOW + 30000, NOW));
    }

    @Test public void theClockIsReadAgainJustAfterEveryMinute() {
        long minute = 60000L * 29_000_000L;  // a whole minute
        assertEquals(60200L, HomeActivity.untilNextMinute(minute));
        assertEquals(201L, HomeActivity.untilNextMinute(minute + 59999L));
        assertEquals(30200L, HomeActivity.untilNextMinute(minute + 30000L));
    }

    @Test public void theStatusClockIsNeverAnnouncedTheRestIs() {
        assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_NONE, AppViews.Masthead.liveRegion(HomeModel.Status.CLOCK));
        assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE, AppViews.Masthead.liveRegion(HomeModel.Status.UPDATING));
        assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE, AppViews.Masthead.liveRegion(HomeModel.Status.OFFLINE));
    }

    @Test public void theNextCycle() {
        assertEquals(3000L, HomeActivity.nextCycle(true, true, Long.MAX_VALUE));
        assertEquals(30000L, HomeActivity.nextCycle(true, false, Long.MAX_VALUE));
        assertEquals(5 * MIN, HomeActivity.nextCycle(false, false, Long.MAX_VALUE));
        assertEquals("woken just after a clock change", 2 * MIN + 1000, HomeActivity.nextCycle(false, false, 2 * MIN));
        assertEquals(1000L, HomeActivity.nextCycle(false, false, -5000));
    }

    // ---------- live ticks ----------
    @Test public void onlyAChangedCardIsSaved() {
        Match cur = m("espn", "in", NOW - 30 * MIN);
        cur.hs = "0"; cur.as = "0"; cur.detail = "30'"; cur.seenLiveAt = NOW - MIN;
        Match same = cur.copy();
        same.seenLiveAt = NOW;
        assertFalse("the same card, seen live a minute later", Engine.worthSaving(cur, same));
        Match clock = cur.copy();
        clock.detail = "31'";
        assertTrue(Engine.worthSaving(cur, clock));
        Match goal = cur.copy();
        goal.hs = "1";
        assertTrue(Engine.worthSaving(cur, goal));
        Match later = cur.copy();
        later.seenLiveAt = cur.seenLiveAt + 5 * MIN;  // half-time: kept fresh enough for keepProgress and timeStates
        assertTrue(Engine.worthSaving(cur, later));
    }

    @Test public void gamesWithoutALiveFeedWakeAtTheirNextClockChange() {
        Match tsdb = m("tsdb", "pre", NOW + HOUR);
        assertEquals(NOW + 40 * MIN, Engine.nextChange(Collections.singletonList(tsdb), NOW));
        assertEquals(NOW + HOUR, Engine.nextChange(Collections.singletonList(tsdb), NOW + 45 * MIN));
        tsdb.state = "in";
        assertEquals(NOW + HOUR + 135 * MIN, Engine.nextChange(Collections.singletonList(tsdb), NOW + HOUR + MIN));
        assertEquals(NOW + 7 * HOUR, Engine.nextChange(Collections.singletonList(tsdb), NOW + 4 * HOUR));
        tsdb.state = "post";
        assertEquals(Long.MAX_VALUE, Engine.nextChange(Collections.singletonList(tsdb), NOW));
        Match tbd = m("tsdb", "pre", NOW + HOUR);
        tbd.tbd = true;
        assertEquals(Long.MAX_VALUE, Engine.nextChange(Collections.singletonList(tbd), NOW));
    }

    @Test public void aLiveFeedMatchWakesWhenItsWindowOpens() {
        Match espn = m("espn", "pre", NOW + HOUR);
        List<Match> all = Arrays.asList(espn, m("tsdb", "pre", NOW + 3 * HOUR));
        assertEquals(NOW + 40 * MIN, Engine.nextChange(all, NOW));
    }

    @Test public void nbaGamesAreLookedUpUnderTheirUsDateFirst() {
        // 19:30 in New York on 1 March 2027 is 00:30 UTC on 2 March
        long start = java.time.ZonedDateTime.of(2027, 3, 1, 19, 30, 0, 0, java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        assertEquals(Arrays.asList("20270301", "20270302"), Sources.nbaDates(start));
        long early = java.time.ZonedDateTime.of(2027, 3, 1, 13, 0, 0, 0, java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        assertEquals(Arrays.asList("20270301", "20270228"), Sources.nbaDates(early));
    }

    @Test public void liveFeedsPauseBrieflyAfterAServerError() {
        assertEquals(MIN, Net.liveWait(1));
        assertEquals(2 * MIN, Net.liveWait(2));
        assertEquals(4 * MIN, Net.liveWait(3));
        assertEquals(5 * MIN, Net.liveWait(4));
        assertEquals(5 * MIN, Net.liveWait(40));
    }

    // ---------- full refreshes ----------
    @Test public void fifteenMinutesOnlyAroundAMatch() {
        assertTrue(Engine.soon(Collections.singletonList(m("espn", "pre", NOW + 5 * HOUR)), "435", NOW));
        assertFalse("a day away: hourly", Engine.soon(Collections.singletonList(m("espn", "pre", NOW + 24 * HOUR)), "435", NOW));
        assertTrue("being played", Engine.soon(Collections.singletonList(m("espn", "in", NOW - HOUR)), "435", NOW));
        assertTrue("just over", Engine.soon(Collections.singletonList(m("espn", "post", NOW - 3 * HOUR)), "435", NOW));
        assertFalse("over hours ago", Engine.soon(Collections.singletonList(m("espn", "post", NOW - 5 * HOUR)), "435", NOW));
        assertFalse("another club's", Engine.soon(Collections.singletonList(m("espn", "pre", NOW + HOUR)), "443", NOW));
    }

    @Test public void resultsAfterAMatchEndsAndEverySixHours() {
        List<Match> none = Collections.emptyList();
        assertTrue("never fetched", Engine.resultsDue(none, "435", 0, NOW));
        assertFalse(Engine.resultsDue(none, "435", NOW - HOUR, NOW));
        assertTrue(Engine.resultsDue(none, "435", NOW - 6 * HOUR, NOW));
        Match kick = m("espn", "in", NOW - HOUR);
        assertFalse("still being played", Engine.resultsDue(Collections.singletonList(kick), "435", NOW - 30 * MIN, NOW));
        assertTrue("about over", Engine.resultsDue(Collections.singletonList(kick), "435", NOW - 30 * MIN, NOW + 45 * MIN));
        assertTrue("extra time", Engine.resultsDue(Collections.singletonList(kick), "435", NOW + 45 * MIN, NOW + 100 * MIN));
        assertFalse("fetched 3 h after kick-off: done", Engine.resultsDue(Collections.singletonList(kick), "435", NOW + 2 * HOUR + MIN, NOW + 2 * HOUR + 20 * MIN));
        Match over = m("espn", "post", NOW - 20 * MIN);  // the live tick saw it end
        assertTrue(Engine.resultsDue(Collections.singletonList(over), "435", NOW - 30 * MIN, NOW));
    }

    @Test public void aMatchThatLeftTheFixturesHasEnded() {
        Match gone = m("espn", "in", NOW - 2 * HOUR);
        assertTrue(Sources.ended(Collections.singletonList(gone), "435", new HashSet<String>(), NOW));
        assertFalse("still listed", Sources.ended(Collections.singletonList(gone), "435", new HashSet<>(Collections.singletonList(gone.key)), NOW));
        assertFalse("not kicked off yet", Sources.ended(Collections.singletonList(m("espn", "pre", NOW + HOUR)), "435", new HashSet<String>(), NOW));
        assertFalse("already a result", Sources.ended(Collections.singletonList(m("espn", "post", NOW - 3 * HOUR)), "435", new HashSet<String>(), NOW));
    }
}
