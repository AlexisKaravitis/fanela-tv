package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * F1Feed against the saved ESPN answers (F1-UFC-ARCHITECTURE §3.1, §8.5): a normal weekend, a sprint weekend, a
 * cancelled one, Las Vegas scheduled, the calendar (ids from $ref, its 3 h offset never stored), the status table,
 * time zones, a refresh end to end on a fake network, and the live reading on synthetic frames made from the samples.
 */
public class F1FeedTest {
    private static final long NOW = FeedSamples.t("2026-11-12T12:00Z");
    private FeedResult.Http realNet;
    private SportTestKit.Ctx c;
    private TimeZone zone;

    @Before public void setUp() throws Exception {
        realNet = FeedResult.net;
        zone = TimeZone.getDefault();
        c = SportTestKit.context();
    }

    @After public void tearDown() {
        FeedResult.net = realNet;
        TimeZone.setDefault(zone);
        SportTestKit.delete(c.dir);
    }

    private static String toJson(SportEvent e) throws Exception {
        return EventStore.toJson(java.util.Collections.singletonList(e), 1L);
    }

    private static SportEvent one(String sample) throws Exception {
        List<SportEvent> l = F1Feed.parseScoreboard(FeedSamples.json(sample));
        assertEquals(1, l.size());
        return l.get(0);
    }

    private static List<String> kinds(SportEvent e) throws Exception {
        List<String> k = new ArrayList<>();
        for (SportEvent.Session s : e.sessions) k.add(s.kind);
        return k;
    }

    // ---------- parsing ----------
    @Test public void aNormalWeekendHasFiveSessionsThePodiumAndTheRaceDistance() throws Exception {
        SportEvent e = one("espn_f1_scoreboard.json");
        assertEquals("600057448", e.id);
        assertEquals(SportEvent.F1, e.sport);
        assertEquals("MSC Cruises São Paulo Grand Prix", e.name);
        assertEquals("617", e.circuitId);
        assertEquals("Autodromo Jose Carlos Pace", e.circuitName);
        assertEquals("Sao Paulo", e.city);
        assertEquals("Brazil", e.country);
        assertEquals(Arrays.asList("FP1", "FP2", "FP3", "QUAL", "RACE"), kinds(e));
        SportEvent.Session race = e.session("401839132");
        assertEquals("RACE", race.kind);
        assertEquals("Race", race.typeAbbr);
        assertEquals("post", race.state);
        assertEquals("STATUS_FINAL", race.statusName);
        assertEquals(71, race.lap);  // status.period of a finished race: the distance run
        assertEquals(FeedSamples.t("2026-11-08T17:00Z"), race.start);
        assertEquals(22, race.order.size());
        assertEquals("5579", race.order.get(0).id);
        assertEquals("Lando Norris", race.order.get(0).name);
        assertEquals("L. Norris", race.order.get(0).shortName);
        assertTrue(race.order.get(0).winner);
        assertEquals(1, race.order.get(0).pos);
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/gbr.png", race.order.get(0).flag);
        assertEquals("M. Verstappen", race.order.get(1).shortName);
        assertEquals("I. Hadjar", race.order.get(2).shortName);
        for (int i = 0; i < race.order.size(); i++) assertEquals(i + 1, race.order.get(i).pos);
        // practice and qualifying carry meaningless periods (FP1 33, Qual 24): never a lap
        for (SportEvent.Session s : e.sessions) if (!"RACE".equals(s.kind)) assertEquals(0, s.lap);
        assertEquals("post", e.state);
        assertEquals(e.sessions.get(0).start, e.start);
        assertEquals(race.start, e.end);
    }

    @Test public void aSprintWeekendMapsSsAndSrAndKeepsStartOrder() throws Exception {
        SportEvent e = one("espn_f1_scoreboard_china_sprint.json");
        assertEquals(Arrays.asList("FP1", "SQ", "SPRINT", "QUAL", "RACE"), kinds(e));
        assertEquals("SS", e.sessions.get(1).typeAbbr);
        assertEquals("SR", e.sessions.get(2).typeAbbr);
        for (int i = 1; i < e.sessions.size(); i++) assertTrue(e.sessions.get(i - 1).start < e.sessions.get(i).start);
        assertEquals(19, e.session("401839035").lap);  // the sprint's period: its laps
        assertEquals(0, e.session("401839034").lap);   // sprint qualifying: none
        assertEquals(56, e.session("401839037").lap);
        // the sprint's total from the circuit length: the fewest complete laps over 100 km (Shanghai 5.451 km -> 19)
        F1Feed.Circuit ci = new F1Feed.Circuit();
        ci.laps = 56; ci.lengthKm = 5.451f;
        F1Feed.laps(e, ci);
        assertEquals(19, e.session("401839035").laps);
        assertEquals(56, e.session("401839037").laps);
        assertEquals(0, e.session("401839034").laps);
    }

    @Test public void theOrderIsTheSessionsStartNotTheirPlaceInTheList() throws Exception {
        JSONObject d = FeedSamples.json("espn_f1_scoreboard_china_sprint.json");
        JSONArray comps = d.getJSONArray("events").getJSONObject(0).getJSONArray("competitions");
        JSONArray reversed = new JSONArray();
        for (int i = comps.length() - 1; i >= 0; i--) reversed.put(comps.get(i));
        d.getJSONArray("events").getJSONObject(0).put("competitions", reversed);
        SportEvent e = F1Feed.parseScoreboard(d).get(0);
        assertEquals(Arrays.asList("FP1", "SQ", "SPRINT", "QUAL", "RACE"), kinds(e));
    }

    @Test public void aCancelledWeekendIsOffCancelledThoughEspnSaysPost() throws Exception {
        SportEvent e = one("espn_f1_scoreboard_bahrain_cancelled.json");
        assertEquals(5, e.sessions.size());
        for (SportEvent.Session s : e.sessions) {
            assertEquals("off", s.state);
            assertEquals("CANCELLED", s.detail);
            assertEquals("STATUS_CANCELED", s.statusName);
        }
        assertEquals("off", e.state);
        assertTrue(EventStore.cancelled(e));
    }

    @Test public void lasVegasScheduledTakesEachSessionsOwnTime() throws Exception {
        SportEvent e = one("espn_f1_scoreboard_lasvegas.json");
        assertEquals("600057449", e.id);
        assertEquals("4250", e.circuitId);
        assertEquals(Arrays.asList("FP1", "FP2", "FP3", "QUAL", "RACE"), kinds(e));
        assertEquals(SportTestKit.LV_FP1, e.sessions.get(0).start);
        assertEquals(FeedSamples.t("2026-11-20T04:00Z"), e.sessions.get(1).start);
        assertEquals(FeedSamples.t("2026-11-21T00:30Z"), e.sessions.get(2).start);
        assertEquals(FeedSamples.t("2026-11-21T04:00Z"), e.sessions.get(3).start);
        assertEquals(SportTestKit.LV_RACE, e.sessions.get(4).start);
        for (SportEvent.Session s : e.sessions) {
            assertEquals("pre", s.state);
            assertFalse(s.tbd);
            assertTrue(s.order.isEmpty());
            assertEquals(0, s.lap);
        }
        assertEquals("401839137", e.sessions.get(4).id);
        assertEquals("pre", e.state);
    }

    @Test public void theCalendarListsIdsFromTheRefAndItsTimesNeverReachTheSessions() throws Exception {
        List<CalendarEntry> cal = F1Feed.parseCalendar(FeedSamples.json("espn_f1_scoreboard.json"));
        assertEquals(25, cal.size());
        assertEquals("600057427", cal.get(0).id);
        assertEquals("Qatar Airways Australian Grand Prix", cal.get(0).label);
        CalendarEntry lv = CalendarEntry.byId(cal, "600057449");
        assertNotNull(lv);
        assertEquals("Heineken Las Vegas Grand Prix", lv.label);
        assertEquals(FeedSamples.t("2026-11-20T03:30Z"), lv.approxStart);  // 3 h after the real FP1
        assertNotNull(CalendarEntry.byId(cal, "600060990"));  // Bahrain Grand Prix in Malaysia, added mid-season
        // the weekend read by date carries the real times only
        SportEvent e = one("espn_f1_scoreboard_lasvegas.json");
        for (SportEvent.Session s : e.sessions) {
            assertFalse(s.start == lv.approxStart);
            assertFalse(s.start == lv.approxEnd);
        }
        assertEquals(SportTestKit.LV_FP1, e.start);
        assertEquals(lv.approxStart - 3 * SportTestKit.H, e.start);
        assertTrue(F1Feed.parseCalendar(new JSONObject()).isEmpty());
    }

    @Test public void theStatusNameIsReadBeforeTheState() throws Exception {
        assertStatus("STATUS_CANCELED", "post", "off", "CANCELLED", true);
        assertStatus("STATUS_POSTPONED", "pre", "off", "POSTPONED", true);
        assertStatus("STATUS_DELAYED", "pre", "pre", "DELAYED", true);
        assertStatus("STATUS_RAIN_DELAY", "in", "in", "RED_FLAG", true);
        assertStatus("STATUS_SUSPENDED", "in", "in", "RED_FLAG", true);
        assertStatus("STATUS_RED_FLAG", "in", "in", "RED_FLAG", true);
        assertStatus("STATUS_SUSPENDED", "in", "in", "SUSPENDED", false);
        assertStatus("STATUS_FINAL", "post", "post", "", true);
        assertStatus("STATUS_IN_PROGRESS", "in", "in", "", true);
        assertStatus("STATUS_RETIRED", "in", "in", "", true);  // "RED" inside a word is not a red flag
        assertStatus("STATUS_SCHEDULED", "pre", "pre", "", true);
        assertStatus("", "", "pre", "", true);
        assertEquals("pre", F1Feed.status(null, true)[0]);
    }

    private static void assertStatus(String name, String state, String wantState, String wantDetail, boolean racing) throws Exception {
        JSONObject st = new JSONObject().put("type", new JSONObject().put("name", name).put("state", state));
        String[] m = F1Feed.status(st, racing);
        assertEquals(name, wantState, m[0]);
        assertEquals(name, wantDetail, m[1]);
        assertEquals(name, m[2]);
    }

    @Test public void kindsComeFromTheAbbreviationWithTheTypeIdAsTheCrossCheck() throws Exception {
        assertEquals("FP1", F1Feed.kind("FP1", "1"));
        assertEquals("SQ", F1Feed.kind("SS", "5"));
        assertEquals("SPRINT", F1Feed.kind("SR", "6"));
        assertEquals("QUAL", F1Feed.kind("Qual", "2"));
        assertEquals("RACE", F1Feed.kind("Race", "3"));
        assertEquals("SQ", F1Feed.kind("Shootout", "5"));  // a new label: the type id decides
        assertEquals("OTHER", F1Feed.kind("Test", "9"));
        assertEquals("OTHER", F1Feed.kind("", ""));
    }

    @Test public void brokenItemsAreSkippedNotFatal() throws Exception {
        JSONObject d = FeedSamples.json("espn_f1_scoreboard_lasvegas.json");
        JSONObject ev = d.getJSONArray("events").getJSONObject(0);
        ev.getJSONArray("competitions").getJSONObject(0).remove("date");   // no time: that session goes
        ev.getJSONArray("competitions").getJSONObject(1).put("id", "");    // no id: that session goes
        JSONArray events = d.getJSONArray("events");
        events.put(new JSONObject().put("name", "no id"));
        events.put(new JSONObject().put("id", "1").put("competitions", new JSONArray()));  // no session: skipped
        events.put("not an object");
        List<SportEvent> l = F1Feed.parseScoreboard(d);
        assertEquals(1, l.size());
        assertEquals(3, l.get(0).sessions.size());
        assertTrue(F1Feed.parseScoreboard(new JSONObject()).isEmpty());
        assertTrue(F1Feed.parseScoreboard(null).isEmpty());
    }

    @Test public void theCircuitGivesLapsAndLength() throws Exception {
        F1Feed.Circuit ci = F1Feed.parseCircuit(FeedSamples.json("espn_core_f1_circuit_lasvegas.json"));
        assertEquals("4250", ci.id);
        assertEquals(50, ci.laps);
        assertEquals(6.116f, ci.lengthKm, 0.0001f);
        assertEquals("Las Vegas Street Circuit", ci.name);
        assertEquals(17, F1Feed.sprintLaps(6.116f));
        assertEquals(19, F1Feed.sprintLaps(5.451f));
        assertEquals(0, F1Feed.sprintLaps(0));
        assertEquals(3.337f, F1Feed.km("3.337 km"), 0.0001f);
        assertEquals(5.47f, F1Feed.km("3.4 mi"), 0.01f);
        assertEquals(0f, F1Feed.km("unknown"), 0f);
        assertNull(F1Feed.parseCircuit(new JSONObject()));
    }

    // ---------- time zones (§3.3) ----------
    @Test public void timesAreUtcWhateverTheDefaultZone() throws Exception {
        for (String z : new String[]{"Europe/Athens", "Europe/London", "America/New_York"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(z));
            SportEvent e = one("espn_f1_scoreboard_lasvegas.json");
            assertEquals(z, SportTestKit.LV_FP1, e.sessions.get(0).start);
            assertEquals(z, "Fri 02:30", FeedSamples.athens(e.sessions.get(0).start));
            assertEquals(z, "Sun 06:00", FeedSamples.athens(e.session("401839137").start));
            assertEquals(z, "20261120", CalendarEntry.utcDay(e.start));
            assertEquals(z, "20261119", CalendarEntry.etDay(e.start));                       // ESPN's day for FP1
            assertEquals(z, "20261121", CalendarEntry.etDay(e.session("401839137").start)); // and for the race
            List<CalendarEntry> cal = F1Feed.parseCalendar(FeedSamples.json("espn_f1_scoreboard.json"));
            // Mexico and the US GP fall in the week Europe has left summer time and the US has not: still UTC
            assertEquals(z, FeedSamples.t("2026-10-23T20:30Z"), CalendarEntry.byId(cal, "600057446").approxStart);
        }
    }

    // ---------- the refresh, end to end ----------
    private FeedSamples.FakeNet net() {
        FeedSamples.FakeNet n = new FeedSamples.FakeNet()
            .on(F1Feed.BOARD + "?dates=20261119", FeedSamples.text("espn_f1_scoreboard_lasvegas.json"))  // ESPN's Eastern day
            .on(F1Feed.BOARD + "?dates=", "{\"events\":[]}")  // other weekends: answered, not listed
            .on(F1Feed.BOARD + "$", FeedSamples.text("espn_f1_scoreboard.json"))
            .on(F1Feed.CORE + "leagues/f1/circuits/4250?", FeedSamples.text("espn_core_f1_circuit_lasvegas.json"))
            .on(F1Feed.STANDINGS, FeedSamples.text("espn_f1_standings.json"));
        FeedSamples.athletesOn(n);
        FeedResult.net = n;
        return n;
    }

    @Test public void aRefreshReadsTheCalendarTheDueWeekendsAndJoinsTeams() throws Exception {
        FeedSamples.FakeNet n = net();
        FeedResult r = F1Feed.fetch(c, new ArrayList<SportEvent>(), NOW, true);
        assertTrue(r.ok);
        assertTrue(r.results);
        assertEquals(25, r.listed.size());
        Map<String, SportEvent> got = new HashMap<>();
        for (SportEvent e : r.events) got.put(e.id, e);
        // the latest weekend from the calendar call itself, Las Vegas by date; Qatar and Abu Dhabi not listed yet
        assertTrue(got.containsKey("600057448"));
        assertTrue(got.containsKey("600057449"));
        assertEquals(2, r.events.size());
        assertTrue(r.missed.contains("600057450"));
        assertTrue(r.missed.contains("600057451"));
        assertEquals(1, n.countExact(F1Feed.BOARD));
        SportEvent lv = got.get("600057449");
        assertEquals(50, lv.laps);
        assertEquals(6.116f, lv.lengthKm, 0.0001f);
        assertEquals(50, lv.session("401839137").laps);
        assertEquals(NOW, lv.fetchedAt);
        SportEvent.Entrant nor = got.get("600057448").session("401839132").order.get(0);
        assertEquals("NOR", nor.code);
        assertEquals("McLaren", nor.team);
        assertEquals("106892", nor.teamId);
        assertEquals(0xFFFF8700, nor.color);
        assertEquals("1", nor.number);
        // the race number survives the store's JSON round trip (events1.json "no")
        SportEvent back = EventStore.fromJson(toJson(got.get("600057448"))).get(0);
        assertEquals("1", back.session("401839132").order.get(0).number);
        assertEquals(got.get("600057448").signature(), back.signature());
        assertEquals(0, got.get("600057448").laps);  // São Paulo's circuit is not in the fake: unknown, not invented
        assertEquals(NOW, c.getSharedPreferences(Sports.ENGINE, 0).getLong("lastCal_f1", 0));
        assertTrue(new java.io.File(c.dir, F1Feed.CAL_FILE).exists());
        assertTrue(new java.io.File(c.dir, F1Feed.STANDINGS_FILE).exists());
        assertTrue(new java.io.File(c.dir, F1Feed.ROSTER_FILE).exists());
        assertTrue(new java.io.File(c.dir, F1Feed.CIRCUITS_FILE).exists());
        assertEquals(50, F1Feed.circuitLaps(c, "4250"));

        // an hour later: the calendar is a week fresh, Las Vegas (8 days away) is a day fresh, São Paulo is old news
        List<SportEvent> known = new ArrayList<>(r.events);
        int before = n.asked.size();
        FeedResult again = F1Feed.fetch(c, known, NOW + SportTestKit.H, false);
        assertTrue(again.ok);
        assertEquals(1, n.countExact(F1Feed.BOARD));
        assertEquals(1, n.count(F1Feed.BOARD + "?dates=20261119"));  // asked once, on the first day tried
        assertFalse(again.results);
        assertEquals(25, again.listed.size());  // the cached calendar
        assertTrue(again.events.isEmpty());
        assertEquals(4, n.asked.size() - before);  // only Qatar and Abu Dhabi, not listed yet, are asked again (two days each)
        // a week later the calendar is asked for again
        F1Feed.fetch(c, known, NOW + 7 * SportTestKit.DAY, false);
        assertEquals(2, n.countExact(F1Feed.BOARD));
    }

    @Test public void offlineTheRefreshFailsAndKeepsNothingNew() throws Exception {
        FeedSamples.FakeNet n = net();
        n.down = true;
        FeedResult r = F1Feed.fetch(c, new ArrayList<SportEvent>(), NOW, true);
        assertFalse(r.ok);
        assertTrue(r.events.isEmpty());
        assertTrue(r.missed.isEmpty());  // a network failure is not a miss
        assertNull(F1Feed.standings(c, false));
        assertFalse(F1Feed.fetch(null, new ArrayList<SportEvent>(), NOW, true).ok);
    }

    @Test public void aWeekendIsAcceptedOnlyWhenTheAnswerListsItsId() throws Exception {
        FeedSamples.FakeNet n = new FeedSamples.FakeNet()
            .on(F1Feed.BOARD + "?dates=20261120", FeedSamples.text("espn_f1_scoreboard.json"))  // the wrong weekend
            .on(F1Feed.BOARD + "?dates=20261122", FeedSamples.text("espn_f1_scoreboard_lasvegas.json"));
        FeedResult.net = n;
        CalendarEntry ce = new CalendarEntry();
        ce.id = "600057449"; ce.approxStart = FeedSamples.t("2026-11-20T03:30Z"); ce.approxEnd = FeedSamples.t("2026-11-22T07:00Z");
        int[] answer = new int[1];
        SportEvent e = F1Feed.weekend("600057449", ce, null, answer);
        assertNotNull(e);
        assertEquals("600057449", e.id);
        // the Eastern start day (404 here), the UTC start day (another weekend: refused), the Eastern end day
        assertEquals(Arrays.asList(F1Feed.BOARD + "?dates=20261119", F1Feed.BOARD + "?dates=20261120", F1Feed.BOARD + "?dates=20261122"), n.asked);
        int[] none = new int[1];
        assertNull(F1Feed.weekend("999", ce, null, none));
        assertEquals(1, none[0]);  // answered, not there: a miss
    }

    @Test public void theWantedWeekendsAreTheNextThreeAndTheLatestResult() throws Exception {
        List<CalendarEntry> cal = F1Feed.parseCalendar(FeedSamples.json("espn_f1_scoreboard.json"));
        List<SportEvent> none = new ArrayList<>();
        // 12 Nov: Las Vegas, Qatar, Abu Dhabi ahead; São Paulo (ended 8 Nov) because it is not stored yet
        assertEquals(Arrays.asList("600057449", "600057450", "600057451", "600057448"),
            CalendarEntry.wanted(cal, none, NOW, 3, SportEvent.F1));
        // stored and older than 3 days: not asked again
        SportEvent sp = one("espn_f1_scoreboard.json");
        List<SportEvent> known = new ArrayList<>();
        known.add(sp);
        assertEquals(Arrays.asList("600057449", "600057450", "600057451"), CalendarEntry.wanted(cal, known, NOW, 3, SportEvent.F1));
        // during Las Vegas' race and the day after: still wanted, ahead of the next two
        long raceDay = FeedSamples.t("2026-11-22T05:00Z");
        assertEquals(Arrays.asList("600057449", "600057450", "600057451"), CalendarEntry.wanted(cal, known, raceDay, 3, SportEvent.F1));
        long after = FeedSamples.t("2026-11-23T12:00Z");
        assertEquals(Arrays.asList("600057450", "600057451", "600057449"), CalendarEntry.wanted(cal, known, after, 3, SportEvent.F1));
        // a stored weekend the calendar dropped, not over yet: asked for (its misses count)
        SportEvent gone = SportTestKit.f1("600099999", SportTestKit.LV_FP1 + 14 * SportTestKit.DAY);
        known.add(gone);
        assertTrue(CalendarEntry.wanted(cal, known, NOW, 3, SportEvent.F1).contains("600099999"));
        // off-season: nothing ahead, the latest result only while recent
        long winter = FeedSamples.t("2027-01-15T12:00Z");
        known.clear();
        SportEvent ad = SportTestKit.f1("600057451", FeedSamples.t("2026-12-04T09:30Z"));
        known.add(ad);
        assertTrue(CalendarEntry.wanted(cal, known, winter, 3, SportEvent.F1).isEmpty());
    }

    @Test public void aStoredWeekendIsDueByItsOwnCadence() throws Exception {
        SportEvent lv = SportTestKit.lasVegas();
        lv.fetchedAt = NOW;
        assertTrue(CalendarEntry.due(null, SportEvent.F1, NOW));
        assertFalse(CalendarEntry.due(lv, SportEvent.F1, NOW + SportTestKit.H));       // 8 days away: daily
        assertTrue(CalendarEntry.due(lv, SportEvent.F1, NOW + SportTestKit.DAY));
        long raceWeek = SportTestKit.LV_FP1 - 2 * SportTestKit.DAY;
        lv.fetchedAt = raceWeek;
        assertTrue(CalendarEntry.due(lv, SportEvent.F1, raceWeek + SportTestKit.H));   // the event's week: hourly
        long hot = SportTestKit.LV_FP1 - 2 * SportTestKit.H;
        lv.fetchedAt = hot;
        assertFalse(CalendarEntry.due(lv, SportEvent.F1, hot + 10 * SportTestKit.MIN));
        assertTrue(CalendarEntry.due(lv, SportEvent.F1, hot + 15 * SportTestKit.MIN));  // near a session: 15 min
    }

    // ---------- live: synthetic frames from the samples ----------
    /** Las Vegas with its race in progress at lap `lap`: the São Paulo race order as the running order. */
    static String raceFrame(String name, String state, int lap, boolean reorder) throws Exception {
        JSONObject d = FeedSamples.json("espn_f1_scoreboard_lasvegas.json");
        JSONObject race = d.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").getJSONObject(4);
        race.put("status", new JSONObject().put("period", lap).put("displayClock", "0:00")
            .put("type", new JSONObject().put("name", name).put("state", state).put("completed", "post".equals(state))));
        JSONArray order = FeedSamples.json("espn_f1_scoreboard.json").getJSONArray("events").getJSONObject(0)
            .getJSONArray("competitions").getJSONObject(4).getJSONArray("competitors");
        if (reorder) {  // Verstappen leads, Norris second
            order.getJSONObject(0).put("order", 2).put("winner", false);
            order.getJSONObject(1).put("order", 1);
        }
        for (int i = 0; i < order.length(); i++) order.getJSONObject(i).put("winner", "post".equals(state) && order.getJSONObject(i).getInt("order") == 1);
        race.put("competitors", order);
        // a session the stored weekend does not have: never invented
        JSONObject extra = new JSONObject(race.toString()).put("id", "401839199");
        d.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").put(extra);
        return d.toString();
    }

    /** The stored weekend as a refresh leaves it: circuit laps, codes and colours joined (FP1 classified). */
    private static SportEvent storedLasVegas() throws Exception {
        SportEvent e = one("espn_f1_scoreboard_lasvegas.json");
        F1Feed.Circuit ci = F1Feed.parseCircuit(FeedSamples.json("espn_core_f1_circuit_lasvegas.json"));
        F1Feed.laps(e, ci);
        F1Feed.Standings st = F1Feed.parseStandings(FeedSamples.json("espn_f1_standings.json"));
        F1Feed.Roster ro = F1Feed.buildRoster(st, FeedSamples.athletes());
        SportEvent sp = one("espn_f1_scoreboard.json");  // FP1 classified with São Paulo's FP1 order
        for (SportEvent.Entrant x : sp.session("401839128").order) e.sessions.get(0).order.add(x.copy());
        e.sessions.get(0).state = "post";
        F1Feed.join(e, ro, st);
        return e;
    }

    private static Map<String, String> boards(String frame) throws Exception {
        Map<String, String> b = new HashMap<>();
        for (String day : new String[]{"20261119", "20261120", "20261121", "20261122"}) b.put(F1Feed.BOARD + "?dates=" + day, frame);
        return b;
    }

    @Test public void aLiveRaceGivesTheLapTheTotalAndTheRunningOrderWithCodes() throws Exception {
        SportEvent stored = storedLasVegas();
        SportEvent live = F1Feed.live(stored, boards(raceFrame("STATUS_IN_PROGRESS", "in", 34, true)));
        assertNotNull(live);
        assertEquals("600057449", live.id);
        assertEquals(5, live.sessions.size());  // the unknown competition was dropped
        assertNull(live.session("401839199"));
        SportEvent.Session race = live.session("401839137");
        assertEquals("in", race.state);
        assertEquals("", race.detail);
        assertEquals(34, race.lap);
        assertEquals(50, race.laps);
        assertEquals("VER", race.order.get(0).code);
        assertEquals("NOR", race.order.get(1).code);
        assertEquals(0xFFFF8700, race.order.get(1).color);  // joined from the stored FP1 classification
        assertEquals("McLaren", race.order.get(1).team);
        assertEquals("1", race.order.get(1).number);
        assertFalse(F1Feed.LIVE_ORDER_TRUSTED);  // the card decides not to draw it yet

        // through the store: the tick's reading lands on the stored weekend
        long t = SportTestKit.LV_RACE + 50 * SportTestKit.MIN;
        for (SportEvent.Session s : live.sessions) if ("in".equals(s.state)) s.seenLiveAt = t;
        SportEvent merged = EventStore.applyLive(stored, live, t);
        assertEquals("in", merged.session("401839137").state);
        assertEquals(34, merged.session("401839137").lap);
        assertEquals(50, merged.session("401839137").laps);
        assertEquals("in", merged.state);
        assertEquals("NOR", merged.sessions.get(0).order.get(0).code);  // FP1's classification untouched
    }

    @Test public void aRedFlagAndTheFinishAreRead() throws Exception {
        SportEvent stored = storedLasVegas();
        SportEvent.Session red = F1Feed.live(stored, boards(raceFrame("STATUS_SUSPENDED", "in", 12, false))).session("401839137");
        assertEquals("in", red.state);
        assertEquals("RED_FLAG", red.detail);
        assertEquals(12, red.lap);
        SportEvent.Session fin = F1Feed.live(stored, boards(raceFrame("STATUS_FINAL", "post", 50, false))).session("401839137");
        assertEquals("post", fin.state);
        assertEquals(50, fin.lap);
        assertTrue(fin.order.get(0).winner);
        assertEquals("NOR", fin.order.get(0).code);
    }

    @Test public void noAnswerListingTheWeekendGivesNull() throws Exception {
        SportEvent stored = storedLasVegas();
        assertNull(F1Feed.live(stored, boards(FeedSamples.text("espn_f1_scoreboard.json"))));  // another weekend
        Map<String, String> failed = new HashMap<>();
        for (String k : boards("").keySet()) failed.put(k, null);  // failed earlier in this tick
        assertNull(F1Feed.live(stored, failed));
        assertNull(F1Feed.live(null, failed));
    }

    @Test public void practiceLiveHasNoLap() throws Exception {
        JSONObject d = FeedSamples.json("espn_f1_scoreboard_lasvegas.json");
        d.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").getJSONObject(0)
            .put("status", new JSONObject().put("period", 33).put("type", new JSONObject().put("name", "STATUS_IN_PROGRESS").put("state", "in")));
        SportEvent live = F1Feed.live(one("espn_f1_scoreboard_lasvegas.json"), boards(d.toString()));
        assertEquals("in", live.sessions.get(0).state);
        assertEquals(0, live.sessions.get(0).lap);
    }
}
