package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The UFC live reading on synthetic frames made from the saved UFC 334 scoreboard (state in, round, clock, winner)
 * until real recordings replace them (tools/record_feeds.sh): bouts found by id across the card, segments and the
 * event following, the method fetched from the fightcenter once a bout ends, nothing invented, and the store keeping
 * pre-fight records.
 */
public class UfcLiveTest {
    private static final String MAIN = "401913646", CO = "401922638", FLY = "401922641";
    private static final long T = SportTestKit.U334_MAIN + 40 * SportTestKit.MIN;

    @Before public void setUp() { UfcFeed.resetLive(); }

    @After public void tearDown() { UfcFeed.resetLive(); }

    /** The stored card, as a refresh before the event leaves it (records before the fight set by the merge). */
    private static SportEvent stored() throws Exception {
        SportEvent e = UfcFeedTest.ufc334();
        EventStore.fillRecords(null, e);
        return e;
    }

    private static JSONObject comp(JSONObject board, String id) throws Exception {
        JSONArray cs = board.getJSONArray("events").getJSONObject(0).getJSONArray("competitions");
        for (int i = 0; i < cs.length(); i++) if (id.equals(cs.getJSONObject(i).getString("id"))) return cs.getJSONObject(i);
        throw new AssertionError(id);
    }

    private static void status(JSONObject comp, String name, String state, int period, String clock) throws Exception {
        comp.put("status", new JSONObject().put("period", period).put("displayClock", clock)
            .put("type", new JSONObject().put("name", name).put("state", state).put("completed", "post".equals(state))));
    }

    /** Sets the winner by ESPN order (1 or 2). */
    private static void winner(JSONObject comp, int order) throws Exception {
        JSONArray cs = comp.getJSONArray("competitors");
        for (int i = 0; i < cs.length(); i++) cs.getJSONObject(i).put("winner", cs.getJSONObject(i).getInt("order") == order);
    }

    /** Every prelim and early prelim over (winner: corner a); the main card as the caller sets it. */
    private static JSONObject frame() throws Exception {
        JSONObject b = FeedSamples.json("espn_ufc_scoreboard.json");
        JSONArray cs = b.getJSONArray("events").getJSONObject(0).getJSONArray("competitions");
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            if (Sources.iso(c.getString("date")) < SportTestKit.U334_MAIN) { status(c, "STATUS_FINAL", "post", 3, "5:00"); winner(c, 1); }
        }
        return b;
    }

    private static Map<String, String> boards(JSONObject frame) throws Exception {
        Map<String, String> m = new HashMap<>();
        for (String day : Sources.nbaDates(SportTestKit.U334_EARLY)) m.put(UfcFeed.BOARD + "?dates=" + day, null);  // failed unless set
        m.put(UfcFeed.BOARD + "?dates=20261114", frame.toString());
        m.put(UfcFeed.fightcenter("600061400"), null);
        return m;
    }

    private static SportEvent.Bout bout(SportEvent e, String id) throws Exception { return UfcFeedTest.bout(e, id); }

    @Test public void theMainEventInRoundTwoWithItsClock() throws Exception {
        JSONObject f = frame();
        status(comp(f, FLY), "STATUS_FINAL", "post", 1, "4:12");
        winner(comp(f, FLY), 2);
        status(comp(f, MAIN), "STATUS_IN_PROGRESS", "in", 2, "3:12");
        Map<String, String> b = boards(f);
        b.put(UfcFeed.fightcenter("600061400"), FeedSamples.text("espn_ufc_fightcenter_ufc334_pre.json"));  // no result yet there
        SportEvent live = UfcFeed.live(stored(), b);
        assertNotNull(live);
        SportEvent.Bout m = bout(live, MAIN);
        assertEquals("in", m.state);
        assertEquals(2, m.round);
        assertEquals("3:12", m.clock);
        assertEquals(5, m.rounds);
        assertEquals(0, m.outcome);
        assertEquals("", m.method);
        assertEquals("in", live.session("main").state);
        assertEquals("post", live.session("prelims1").state);
        assertEquals("post", live.session("prelims2").state);
        assertEquals("in", live.state);
        SportEvent.Bout fly = bout(live, FLY);
        assertEquals("post", fly.state);
        assertEquals(2, fly.outcome);  // Gurule (order 2) won: positions unchanged
        assertEquals("Bilal Hasan", fly.a.name);
        assertEquals(1, fly.round);
        assertEquals("4:12", fly.clock);
        assertEquals("", fly.method);  // the scoreboard has none and the card had none yet
        assertTrue(live.session("main").seenLiveAt > 0);
        assertEquals("14-2-0", m.a.recordBefore);
    }

    @Test public void betweenBoutsTheSegmentIsStillUnderWay() throws Exception {
        JSONObject f = frame();
        status(comp(f, FLY), "STATUS_FINAL", "post", 3, "5:00");
        winner(comp(f, FLY), 1);
        SportEvent live = UfcFeed.live(stored(), boards(f));
        assertEquals("pre", bout(live, MAIN).state);
        assertEquals("in", live.session("main").state);  // one over, four to come
        assertEquals("in", live.state);
        // between segments: the prelims over, the main card not started
        SportEvent between = UfcFeed.live(stored(), boards(frame()));
        assertEquals("pre", between.session("main").state);
        assertEquals("post", between.session("prelims1").state);
        assertEquals("in", between.state);  // a segment is over, the card is not
    }

    @Test public void aBoutThatEndsGetsItsMethodFromTheCardAtMostEveryFiveMinutes() throws Exception {
        JSONObject f = frame();
        status(comp(f, MAIN), "STATUS_FINAL", "post", 3, "2:27");
        winner(comp(f, MAIN), 2);
        JSONObject card = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        JSONObject cm = card.getJSONObject("cards").getJSONObject("main").getJSONArray("competitions").getJSONObject(0);
        cm.put("status", new JSONObject().put("period", 3).put("displayClock", "2:27")
            .put("type", new JSONObject().put("name", "STATUS_FINAL").put("state", "post"))
            .put("result", new JSONObject().put("name", "kotko").put("displayName", "KO/TKO").put("shortDisplayName", "KO/TKO").put("description", "Punch")));
        JSONArray cc = cm.getJSONArray("competitors");
        for (int i = 0; i < cc.length(); i++) cc.getJSONObject(i).put("winner", cc.getJSONObject(i).getInt("order") == 2);
        Map<String, String> b = boards(f);
        b.put(UfcFeed.fightcenter("600061400"), card.toString());
        SportEvent live = UfcFeed.live(stored(), b);
        SportEvent.Bout m = bout(live, MAIN);
        assertEquals("post", m.state);
        assertEquals(2, m.outcome);  // Hokit, corner b
        assertEquals("KO/TKO", m.method);
        assertEquals("Punch", m.methodDetail);
        assertEquals(3, m.round);
        assertEquals("2:27", m.clock);
        assertEquals("post", live.state);
        // the other main-card bouts are still "pre" in the frame: their methods stay empty, nothing guessed
        assertEquals("", bout(live, CO).method);
        // the next tick within 5 minutes does not ask the card again: the scoreboard's reading only
        b.put(UfcFeed.fightcenter("600061400"), null);
        SportEvent again = UfcFeed.live(stored(), b);
        assertEquals("post", bout(again, MAIN).state);
        assertEquals(2, bout(again, MAIN).outcome);
        assertEquals("", bout(again, MAIN).method);

        // through the store: the method kept, the records before the fight kept (not the post-fight ones)
        SportEvent cur = stored();
        SportEvent merged = EventStore.applyLive(cur, live, T);
        assertEquals("KO/TKO", merged.mainEvent().method);
        assertEquals(2, merged.mainEvent().outcome);
        assertEquals("14-2-0", merged.mainEvent().a.recordBefore);
        assertEquals("10-0-0", merged.mainEvent().b.recordBefore);
        // a lagging frame (the bout "in" again) does not move it back
        JSONObject lag = frame();
        status(comp(lag, MAIN), "STATUS_IN_PROGRESS", "in", 3, "2:40");
        SportEvent lagging = UfcFeed.live(merged, boards(lag));
        SportEvent kept = EventStore.applyLive(merged, lagging, T + SportTestKit.MIN);
        assertEquals("post", kept.mainEvent().state);
        assertEquals("KO/TKO", kept.mainEvent().method);
    }

    @Test public void aBoutTheCardLacksIsNeverInventedButAsksForTheCard() throws Exception {
        JSONObject f = frame();
        JSONObject extra = new JSONObject(comp(f, MAIN).toString()).put("id", "401999999");
        f.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").put(extra);
        assertFalse(UfcFeed.refetching("600061400"));
        SportEvent live = UfcFeed.live(stored(), boards(f));
        assertNull(bout(live, "401999999"));
        assertEquals(13, live.session("prelims2").bouts.size() + live.session("prelims1").bouts.size() + live.session("main").bouts.size());
        assertTrue(UfcFeed.refetching("600061400"));
    }

    @Test public void noBoardListingTheEventGivesNull() throws Exception {
        Map<String, String> b = boards(FeedSamples.json("espn_ufc_scoreboard_past_20261107.json"));  // another event
        assertNull(UfcFeed.live(stored(), b));
        Map<String, String> failed = new HashMap<>();
        for (String day : Sources.nbaDates(SportTestKit.U334_EARLY)) failed.put(UfcFeed.BOARD + "?dates=" + day, null);
        assertNull(UfcFeed.live(stored(), failed));
        assertNull(UfcFeed.live(null, failed));
    }

    @Test public void applyScoreboardReadsAFinishedNightWithoutMethods() throws Exception {
        SportEvent fn = UfcFeedTest.fightNight();
        for (SportEvent.Session s : fn.sessions) for (SportEvent.Bout x : s.bouts) { x.state = "pre"; x.outcome = 0; x.method = ""; x.round = 0; x.clock = ""; }
        UfcFeed.applyScoreboard(fn, FeedSamples.json("espn_ufc_scoreboard_past_20261107.json"), T);
        SportEvent.Bout m = fn.mainEvent();
        assertEquals("post", m.state);
        assertEquals(2, m.outcome);
        assertEquals(3, m.round);
        assertEquals("2:27", m.clock);
        assertEquals(5, m.rounds);
        assertEquals("", m.method);  // the scoreboard has no method
        assertTrue(UfcFeed.needsResults(fn));
        assertEquals("post", fn.state);
        // a board for another event changes nothing
        SportEvent u = UfcFeedTest.ufc334();
        String before = u.signature();
        UfcFeed.applyScoreboard(u, FeedSamples.json("espn_ufc_scoreboard_past_20261107.json"), T);
        assertEquals(before, u.signature());
        UfcFeed.applyScoreboard(u, new JSONObject(), T);
        UfcFeed.applyScoreboard(null, new JSONObject(), T);
        assertEquals(before, u.signature());
    }
}
