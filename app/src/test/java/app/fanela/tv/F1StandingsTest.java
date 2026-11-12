package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * F1 standings (codes, ranks, points, constructor colours), the roster (each driver's team, matched to a constructor
 * by name: all 11 teams of 2026) and the join of codes and colours into a session's order; the side files' round trip.
 */
public class F1StandingsTest {
    private FeedResult.Http realNet;
    private SportTestKit.Ctx c;

    @Before public void setUp() throws Exception {
        realNet = FeedResult.net;
        c = SportTestKit.context();
    }

    @After public void tearDown() {
        FeedResult.net = realNet;
        SportTestKit.delete(c.dir);
    }

    private static F1Feed.Standings standings() throws Exception { return F1Feed.parseStandings(FeedSamples.json("espn_f1_standings.json")); }

    @Test public void driversHaveCodesRanksAndPoints() throws Exception {
        F1Feed.Standings s = standings();
        assertEquals(25, s.drivers.size());
        SportEvent.Entrant first = s.drivers.get(0);
        assertEquals("5829", first.id);
        assertEquals("ANT", first.code);
        assertEquals("Kimi Antonelli", first.name);
        assertEquals("K. Antonelli", first.shortName);
        assertEquals(1, first.pos);
        assertEquals(390f, first.points, 0f);
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/ita.png", first.flag);
        SportEvent.Entrant nor = s.driver("5579");
        assertEquals("NOR", nor.code);
        assertEquals(6, nor.pos);
        assertEquals(246f, nor.points, 0f);
        for (int i = 0; i < s.drivers.size(); i++) {
            assertEquals(3, s.drivers.get(i).code.length());
            assertEquals(i + 1, s.drivers.get(i).pos);
        }
        assertEquals("Hülkenberg", s.driver("4396").name.substring(5));
    }

    @Test public void constructorsHaveColours() throws Exception {
        F1Feed.Standings s = standings();
        assertEquals(11, s.teams.size());
        F1Feed.Team merc = s.teams.get(0);
        assertEquals("106893", merc.id);
        assertEquals("Mercedes", merc.name);
        assertEquals(0xFF00D2BE, merc.color);
        assertEquals(1, merc.rank);
        assertEquals(652f, merc.points, 0f);
        assertEquals(0xFFFF8700, s.team("106892").color);  // McLaren
        assertEquals(0xFFFFFFFF, s.team("106967").color);  // Williams: white, still a colour
        for (F1Feed.Team t : s.teams) assertTrue(t.name, (t.color & 0xFF000000) == 0xFF000000);
        assertTrue(F1Feed.parseStandings(new JSONObject()).drivers.isEmpty());
        assertTrue(F1Feed.parseStandings(null).teams.isEmpty());
    }

    @Test public void everyDriverGetsATeamAndAllElevenTeamsMatch() throws Exception {
        F1Feed.Standings s = standings();
        F1Feed.Roster r = F1Feed.buildRoster(s, FeedSamples.athletes());
        assertEquals(25, r.byDriver.size());
        assertEquals(11, r.teams.size());
        Set<String> matched = new HashSet<>();
        for (SportEvent.Entrant d : r.byDriver.values()) {
            assertFalse(d.code, d.teamId.isEmpty());
            assertTrue(d.code, d.color != 0);
            matched.add(d.teamId);
        }
        assertEquals(11, matched.size());
        assertEquals("McLaren", r.byDriver.get("5579").team);
        assertEquals("106892", r.byDriver.get("5579").teamId);
        assertEquals("1", r.byDriver.get("5579").number);    // the race number, from vehicles[0].number
        assertEquals("44", r.byDriver.get("868").number);
        assertEquals("Racing Bulls", r.byDriver.get("5741").team);   // not Red Bull
        assertEquals("Red Bull", r.byDriver.get("4665").team);
        assertEquals("Alpine", r.byDriver.get("5799").team);         // the team wins over the manufacturer (Mercedes)
        assertEquals("Cadillac", r.byDriver.get("4520").team);
        assertEquals(0, r.byDriver.get("5579").pos);                 // a roster entry carries no rank
    }

    @Test public void teamNamesMatchByNormalisedName() throws Exception {
        F1Feed.Standings s = standings();
        assertEquals("106921", F1Feed.matchTeam("Oracle Red Bull Racing", "", s.teams).id);
        assertEquals("123988", F1Feed.matchTeam("Visa Cash App Racing Bulls F1 Team", "", s.teams).id);
        assertEquals("111427", F1Feed.matchTeam("MoneyGram Haas F1 Team", "", s.teams).id);
        assertEquals("106842", F1Feed.matchTeam("Scuderia Ferrari", "", s.teams).id);
        assertEquals("123986", F1Feed.matchTeam("Aston Martin Aramco", "", s.teams).id);
        assertEquals("106893", F1Feed.matchTeam("", "Mercedes", s.teams).id);
        assertNull(F1Feed.matchTeam("Brabham", "", s.teams));
        assertNull(F1Feed.matchTeam("", "", s.teams));
        assertEquals("redbull", F1Feed.norm("Red Bull"));
        assertEquals("haas", F1Feed.norm("Haas F1 Team"));
    }

    @Test public void codesAndColoursAreJoinedIntoTheOrder() throws Exception {
        F1Feed.Standings s = standings();
        F1Feed.Roster r = F1Feed.buildRoster(s, FeedSamples.athletes());
        SportEvent e = F1Feed.parseScoreboard(FeedSamples.json("espn_f1_scoreboard.json")).get(0);
        F1Feed.join(e, r, s);
        SportEvent.Session race = e.session("401839132");
        assertEquals("NOR", race.order.get(0).code);
        assertEquals("McLaren", race.order.get(0).team);
        assertEquals(0xFFFF8700, race.order.get(0).color);
        assertEquals("1", race.order.get(0).number);
        assertEquals("VER", race.order.get(1).code);
        assertEquals("3", race.order.get(1).number);
        assertEquals(0xFF00327D, race.order.get(1).color);
        for (SportEvent.Session x : e.sessions)
            for (SportEvent.Entrant d : x.order) {
                assertEquals(d.name, 3, d.code.length());
                assertEquals(d.name, r.byDriver.containsKey(d.id), d.color != 0);
            }
        // a practice stand-in who is not in the standings: the code by F1's rule, no team colour (unknown, not guessed)
        SportEvent.Entrant mini = null;
        for (SportEvent.Entrant d : e.session("401839128").order) if (d.name.startsWith("Gabriele")) mini = d;
        assertNotNull(mini);
        assertEquals("MIN", mini.code);
        assertEquals(0, mini.color);
        assertEquals("", mini.team);
        assertEquals("HUL", F1Feed.codeOf("Nico Hülkenberg"));
        assertEquals("", F1Feed.codeOf(" "));
        // standings alone still give the codes; neither: the code from the name only
        SportEvent bare = F1Feed.parseScoreboard(FeedSamples.json("espn_f1_scoreboard.json")).get(0);
        F1Feed.join(bare, null, s);
        assertEquals("NOR", bare.session("401839132").order.get(0).code);
        assertEquals(0, bare.session("401839132").order.get(0).color);
        SportEvent none = F1Feed.parseScoreboard(FeedSamples.json("espn_f1_scoreboard.json")).get(0);
        F1Feed.join(none, null, null);
        assertEquals("NOR", none.session("401839132").order.get(0).code);
        assertEquals("", none.session("401839132").order.get(0).team);
        assertEquals(0, none.session("401839132").order.get(0).color);
    }

    @Test public void theCoreCompetitorAndAthleteSamplesAgree() throws Exception {
        String[] v = F1Feed.vehicle(FeedSamples.json("espn_core_f1_athlete.json"));
        assertEquals("McLaren", v[0]);
        assertEquals("McLaren", v[1]);
        assertEquals("1", v[2]);
        JSONObject comp = FeedSamples.json("espn_core_f1_competitor.json");
        assertEquals(0xFFFF8700, F1Feed.color(comp.getJSONObject("vehicle").getString("teamColor")));
        assertEquals(0, F1Feed.color("nonsense"));
        assertEquals(0xFF123456, F1Feed.color("#123456"));
        assertEquals("FF8700", F1Feed.hex(0xFFFF8700));
        assertEquals(3, F1Feed.vehicle(new JSONObject()).length);
    }

    @Test public void theSideFilesRoundTrip() throws Exception {
        F1Feed.Standings s = standings();
        s.at = 1234L;
        F1Feed.Standings back = F1Feed.readStandings(F1Feed.standingsJson(s));
        assertEquals(1234L, back.at);
        assertEquals(25, back.drivers.size());
        assertEquals("ANT", back.drivers.get(0).code);
        assertEquals(390f, back.drivers.get(0).points, 0f);
        assertEquals(0xFF00D2BE, back.teams.get(0).color);
        F1Feed.Roster r = F1Feed.buildRoster(s, FeedSamples.athletes());
        r.at = 99L;
        F1Feed.Roster rb = F1Feed.readRoster(F1Feed.rosterJson(r));
        assertEquals(99L, rb.at);
        assertEquals("McLaren", rb.byDriver.get("5579").team);
        assertEquals(0xFFFF8700, rb.byDriver.get("5579").color);
        assertEquals("1", rb.byDriver.get("5579").number);
        assertEquals(11, rb.teams.size());
        assertNull(F1Feed.readStandings("not json"));
        assertNull(F1Feed.readRoster(null));
    }

    @Test public void theStandingsCarryTheRostersTeamsAndColours() throws Exception {
        F1Feed.Standings raw = standings();
        assertTrue(raw.driver("5579").team.isEmpty());  // ESPN's table has no team per driver
        assertEquals(0, raw.driver("5579").color);
        F1Feed.Roster r = F1Feed.buildRoster(raw, FeedSamples.athletes());
        F1Feed.Standings joined = F1Feed.withTeams(raw, r);
        assertEquals("McLaren", joined.driver("5579").team);
        assertEquals(0xFFFF8700, joined.driver("5579").color);
        assertEquals("1", joined.driver("5579").number);
        assertTrue(raw.driver("5579").team.isEmpty());  // a copy: the table itself is untouched
        assertEquals(raw.drivers.size(), joined.drivers.size());
        assertEquals(raw.teams.size(), joined.teams.size());
        for (SportEvent.Entrant d : joined.drivers) assertTrue(d.code, d.color != 0);  // every 2026 driver has his team's colour
        assertTrue(F1Feed.withTeams(raw, null) == raw);
        assertNull(F1Feed.withTeams(null, r));
        // a roster entry with no colour takes its constructor's from the table
        F1Feed.Roster bare = F1Feed.buildRoster(raw, FeedSamples.athletes());
        bare.byDriver.get("5579").color = 0;
        assertEquals(0xFFFF8700, F1Feed.withTeams(raw, bare).driver("5579").color);
    }

    @Test public void standingsFromTheCacheAreJoinedWithTheRosterOnTheTv() throws Exception {
        FeedSamples.FakeNet n = new FeedSamples.FakeNet().on(F1Feed.STANDINGS, FeedSamples.text("espn_f1_standings.json"));
        FeedSamples.athletesOn(n);
        FeedResult.net = n;
        long now = FeedSamples.t("2026-11-12T12:00Z");
        F1Feed.Standings before = F1Feed.standings(c, false, now);
        assertTrue(before.driver("5579").team.isEmpty());  // no roster on the TV yet
        F1Feed.roster(c, false, now);
        F1Feed.Standings after = F1Feed.standings(c, false, now + SportTestKit.H);  // cached, joined
        assertEquals("McLaren", after.driver("5579").team);
        assertEquals(0xFFFF8700, after.driver("5579").color);
        F1Feed.Standings table = F1Feed.table(c);  // the app's screens: the same join
        assertEquals("McLaren", table.driver("5579").team);
        assertNull(F1Feed.table(null));
        // the cards' roster: the one on the TV, under this season's orders
        java.util.Map<String, SportEvent.Entrant> cards = EventCards.rosterFrom(c, new java.util.ArrayList<SportEvent>());
        assertEquals(0xFFFF8700, cards.get("5579").color);
        assertEquals("McLaren", cards.get("5579").team);
        assertTrue(EventCards.rosterFrom(null, null).isEmpty());
    }

    @Test public void standingsAndRosterAreCachedWeeklyAndKeptWhenTheNetworkFails() throws Exception {
        FeedSamples.FakeNet n = new FeedSamples.FakeNet().on(F1Feed.STANDINGS, FeedSamples.text("espn_f1_standings.json"));
        FeedSamples.athletesOn(n);
        FeedResult.net = n;
        long now = FeedSamples.t("2026-11-12T12:00Z");
        F1Feed.Roster r = F1Feed.roster(c, false, now);
        assertNotNull(r);
        assertEquals(26, n.asked.size());  // the standings, then 25 athletes
        assertEquals("McLaren", r.byDriver.get("5579").team);
        assertEquals("McLaren", F1Feed.roster(c, false, now + SportTestKit.DAY).byDriver.get("5579").team);  // a day later: cached
        assertEquals(26, n.asked.size());
        assertNotNull(F1Feed.standings(c, false, now + SportTestKit.DAY));
        assertEquals(26, n.asked.size());
        assertNotNull(F1Feed.standings(c, true, now + SportTestKit.DAY));  // forced: asked again
        assertEquals(27, n.asked.size());
        n.down = true;
        F1Feed.Standings kept = F1Feed.standings(c, true, now + 8 * SportTestKit.DAY);
        assertNotNull(kept);  // offline: the cached ones
        assertEquals("ANT", kept.drivers.get(0).code);
        F1Feed.Roster keptRoster = F1Feed.roster(c, false, now + 8 * SportTestKit.DAY);
        assertEquals("McLaren", keptRoster.byDriver.get("5579").team);
        assertNull(F1Feed.roster(null, false));
        assertNull(F1Feed.standings(null, true));
        assertEquals(0, F1Feed.circuitLaps(null, "4250"));
        Map<String, JSONObject> none = new HashMap<>();
        assertTrue(F1Feed.buildRoster(new F1Feed.Standings(), none).byDriver.isEmpty());
    }
}
