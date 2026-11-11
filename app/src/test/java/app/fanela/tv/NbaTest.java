package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

/** NBA games from ESPN's schedule: the shape of a game not played yet (no "winner"), a live one, a final. */
public class NbaTest {
    static String event(String state, String name, int period, String clock, String home, String away, String winner) {
        return "{\"id\":\"401909946\",\"date\":\"2026-11-08T23:00Z\",\"timeValid\":true,\"competitions\":[{\"status\":{"
            + "\"clock\":0.0,\"displayClock\":\"" + clock + "\",\"period\":" + period + ",\"type\":{\"name\":\"" + name + "\","
            + "\"state\":\"" + state + "\",\"shortDetail\":\"" + ("post".equals(state) ? "Final" : "11/8 - 6:00 PM EST") + "\"}},"
            + "\"competitors\":[{\"homeAway\":\"home\"," + (winner == null ? "" : "\"winner\":" + "home".equals(winner) + ",")
            + "\"score\":" + (home == null ? "null" : "{\"value\":" + home + ",\"displayValue\":\"" + home + "\"}")
            + ",\"team\":{\"id\":\"8\",\"displayName\":\"Detroit Pistons\"}},"
            + "{\"homeAway\":\"away\"," + (winner == null ? "" : "\"winner\":" + "away".equals(winner) + ",")
            + "\"score\":" + (away == null ? "null" : "{\"value\":" + away + ",\"displayValue\":\"" + away + "\"}")
            + ",\"team\":{\"id\":\"13\",\"displayName\":\"Los Angeles Lakers\"}}]}]}";
    }

    @After public void reset() { Where.use("GR"); }

    @Test public void upcomingGameWithoutWinnerField() throws Exception {
        Where.use("US");
        SeasonTableProvider.use(WhereTest.table());
        Match m = Sources.nbaEvent(new JSONObject(event("pre", "STATUS_SCHEDULED", 0, "0.0", null, null, null)), "nba13");
        assertNotNull("a game with no winner field yet is kept", m);
        assertEquals("nba13", m.club);
        assertEquals("BC", m.team);
        assertEquals("nba", m.source);
        assertEquals("nba13|nba:401909946", m.key);
        assertFalse("the Lakers are away", m.clubIsHome);
        assertNull(m.hs);
        assertEquals("pre", m.state);
        assertEquals(Sources.iso("2026-11-08T23:00Z"), m.start);
        assertTrue(m.channelKnown);
        assertEquals("ESPN · NBC · Prime Video", m.channel);
    }

    @Test public void liveAndFinal() throws Exception {
        Match live = Sources.nbaEvent(new JSONObject(event("in", "STATUS_IN_PROGRESS", 3, "4:12", "71", "68", null)), "nba13");
        assertEquals("Q3 4:12", live.detail);
        assertEquals(3, live.quarter);
        assertEquals("71", live.hs);
        Match ot = Sources.nbaEvent(new JSONObject(event("in", "STATUS_IN_PROGRESS", 6, "1:05", "110", "110", null)), "nba13");
        assertEquals("OT2 1:05", ot.detail);
        Match half = Sources.nbaEvent(new JSONObject(event("in", "STATUS_HALFTIME", 2, "0.0", "50", "52", null)), "nba13");
        assertEquals("HT", half.detail);
        Match fin = Sources.nbaEvent(new JSONObject(event("post", "STATUS_FINAL", 4, "0.0", "118", "124", "away")), "nba13");
        assertEquals("FT", fin.detail);
        assertEquals("the Lakers won away", 1, fin.clubWon);
    }
}
