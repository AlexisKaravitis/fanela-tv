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
 * UfcFeed against the saved ESPN answers (F1-UFC-ARCHITECTURE §3.2, §8.5): the calendar (Contender Series removed, ids
 * from $ref), UFC 334 before the event (3 segments at 22:00Z / 00:00Z / 02:00Z, main event order 1, title, 5 rounds),
 * the Fight Night final (KO/TKO, Punch, R3 2:27, winner side, records, decisions, a submission), draws and no
 * contests, time zones, the fighters on the next cards and a refresh end to end on a fake network.
 */
public class UfcFeedTest {
    private static final long NOW = FeedSamples.t("2026-11-12T12:00Z");
    private FeedResult.Http realNet;
    private SportTestKit.Ctx c;
    private TimeZone zone;

    @Before public void setUp() throws Exception {
        realNet = FeedResult.net;
        zone = TimeZone.getDefault();
        c = SportTestKit.context();
        UfcFeed.resetLive();
    }

    @After public void tearDown() {
        FeedResult.net = realNet;
        TimeZone.setDefault(zone);
        SportTestKit.delete(c.dir);
        UfcFeed.resetLive();
    }

    static SportEvent ufc334() throws Exception { return UfcFeed.parseFightcenter(FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json")); }

    static SportEvent fightNight() throws Exception { return UfcFeed.parseFightcenter(FeedSamples.json("espn_ufc_fightcenter_fightnight_final.json")); }

    static SportEvent.Bout bout(SportEvent e, String id) throws Exception {
        for (SportEvent.Session s : e.sessions) for (SportEvent.Bout b : s.bouts) if (id.equals(b.id)) return b;
        return null;
    }

    // ---------- calendar ----------
    @Test public void theCalendarDropsTheContenderSeriesAndKeysById() throws Exception {
        List<CalendarEntry> cal = UfcFeed.parseCalendar(FeedSamples.json("espn_ufc_scoreboard.json"));
        assertEquals(53 - 10, cal.size());
        for (CalendarEntry e : cal) assertFalse(e.label, e.label.startsWith("Dana White"));
        CalendarEntry u334 = CalendarEntry.byId(cal, "600061400");
        assertEquals("UFC 334: Gane vs. Hokit", u334.label);
        assertEquals(FeedSamples.t("2026-11-15T01:00Z"), u334.approxStart);  // neither the main card (02:00) nor the early prelims (22:00)
        assertNotNull(CalendarEntry.byId(cal, "600062700"));
        assertNull(CalendarEntry.byId(cal, "600060732"));  // DWCS season 10 week 1
        for (int i = 1; i < cal.size(); i++) assertTrue(cal.get(i - 1).approxStart <= cal.get(i).approxStart);
        // the typographic apostrophe is the same series
        JSONObject b = new JSONObject().put("leagues", new JSONArray().put(new JSONObject().put("calendar", new JSONArray()
            .put(new JSONObject().put("label", "Dana White’s Contender Series: Week 2").put("event", new JSONObject().put("$ref", "http://x/events/1?a")))
            .put(new JSONObject().put("label", "UFC 999").put("event", new JSONObject().put("$ref", "http://x/events/2?a")))
            .put(new JSONObject().put("label", "no ref")))));
        List<CalendarEntry> two = UfcFeed.parseCalendar(b);
        assertEquals(1, two.size());
        assertEquals("2", two.get(0).id);
        assertTrue(UfcFeed.parseCalendar(new JSONObject()).isEmpty());
    }

    // ---------- UFC 334 before the event ----------
    @Test public void ufc334HasThreeSegmentsAtTheirOwnTimes() throws Exception {
        SportEvent e = ufc334();
        assertEquals("600061400", e.id);
        assertEquals(SportEvent.UFC, e.sport);
        assertEquals("UFC 334: Gane vs. Hokit", e.name);
        assertEquals("UFC 334", e.shortName);
        assertTrue(e.numbered);
        assertEquals(334, e.number);
        assertEquals("Madison Square Garden", e.venue);
        assertEquals("New York", e.city);
        assertEquals(3, e.sessions.size());
        assertEquals("prelims2", e.sessions.get(0).id);
        assertEquals("EARLY", e.sessions.get(0).kind);
        assertEquals("Early Prelims", e.sessions.get(0).typeAbbr);
        assertEquals(SportTestKit.U334_EARLY, e.sessions.get(0).start);
        assertEquals("prelims1", e.sessions.get(1).id);
        assertEquals("PRELIMS", e.sessions.get(1).kind);
        assertEquals(SportTestKit.U334_PRELIMS, e.sessions.get(1).start);
        assertEquals("main", e.sessions.get(2).id);
        assertEquals("MAIN", e.sessions.get(2).kind);
        assertEquals(SportTestKit.U334_MAIN, e.sessions.get(2).start);
        assertEquals(4, e.sessions.get(0).bouts.size());
        assertEquals(4, e.sessions.get(1).bouts.size());
        assertEquals(5, e.sessions.get(2).bouts.size());
        assertEquals(SportTestKit.U334_EARLY, e.start);
        assertEquals(SportTestKit.U334_MAIN, e.end);
        assertEquals("pre", e.state);
        for (SportEvent.Session s : e.sessions) assertEquals("pre", s.state);
    }

    @Test public void theMainEventIsOrderOneATitleFightOfFiveRounds() throws Exception {
        SportEvent e = ufc334();
        SportEvent.Bout m = e.mainEvent();
        assertEquals("401913646", m.id);
        assertEquals(1, m.order);
        assertTrue(m.main);
        assertTrue(m.title);
        assertEquals(5, m.rounds);
        assertEquals("Heavyweight", m.weightClass);
        assertEquals("Heavyweight - Main Event - Title Fight", m.note);
        assertEquals("pre", m.state);
        assertEquals(0, m.outcome);
        assertEquals("", m.method);
        assertEquals("", m.clock);
        assertEquals(0, m.round);
        // a = ESPN order 1 (listed second): fixed positions
        assertEquals("4426000", m.a.id);
        assertEquals("Ciryl Gane", m.a.name);
        assertEquals("C. Gane", m.a.shortName);
        assertEquals("14-2-0", m.a.record);
        assertEquals("", m.a.recordBefore);  // EventStore.merge sets it
        assertEquals("France", m.a.country);
        assertEquals("https://a.espncdn.com/i/headshots/mma/players/full/4426000.png", m.a.headshot);
        assertEquals("https://a.espncdn.com/i/headshots/mma/players/stance/left/4426000.png", m.a.stanceLeft);
        assertEquals("https://a.espncdn.com/i/headshots/mma/players/stance/right/4426000.png", m.a.stanceRight);
        assertTrue(m.a.flag.endsWith("/fra.png"));
        assertEquals("4049391", m.b.id);
        assertEquals("Josh Hokit", m.b.name);
        assertEquals("10-0-0", m.b.record);
        SportEvent.Bout co = bout(e, "401922638");
        assertEquals(2, co.order);
        assertTrue(co.title);
        assertFalse(co.main);
        assertEquals(5, co.rounds);
        assertEquals("Women's Bantamweight", co.weightClass);
        SportEvent.Bout plain = bout(e, "401922639");
        assertFalse(plain.title);
        assertEquals(3, plain.rounds);
        // bouts by order, main event first; a fighter with one stance image keeps the other empty
        assertEquals(1, e.sessions.get(2).bouts.get(0).order);
        assertEquals("", bout(e, "401922641").a.stanceRight);  // Bilal Hasan: left stance only
        assertEquals(e.sessions.get(2), e.sessionOf(m));
        assertEquals(m, e.boutOf("4049391"));
    }

    @Test public void duplicateMatchNumbersKeepAStableOrder() throws Exception {
        SportEvent e = ufc334();
        List<SportEvent.Bout> early = e.sessions.get(0).bouts;
        assertEquals(Arrays.asList(10, 11, 13, 13), Arrays.asList(early.get(0).order, early.get(1).order, early.get(2).order, early.get(3).order));
        assertEquals("401932013", early.get(2).id);
        assertEquals("401933936", early.get(3).id);
    }

    // ---------- Fight Night final ----------
    @Test public void theFightNightFinalHasMethodRoundTimeAndWinnerSide() throws Exception {
        SportEvent e = fightNight();
        assertEquals("600061268", e.id);
        assertFalse(e.numbered);
        assertEquals("Meta APEX", e.venue);
        assertEquals(2, e.sessions.size());  // main and prelims only
        assertEquals("post", e.state);
        for (SportEvent.Session s : e.sessions) assertEquals("post", s.state);
        SportEvent.Bout m = e.mainEvent();
        assertEquals("401932715", m.id);
        assertTrue(m.main);
        assertFalse(m.title);
        assertEquals(5, m.rounds);  // a main event is five rounds
        assertEquals("post", m.state);
        assertEquals("KO/TKO", m.method);
        assertEquals("Punch", m.methodDetail);
        assertEquals(3, m.round);
        assertEquals("2:27", m.clock);
        assertEquals("Waldo Cortes Acosta", m.a.name);  // order 1
        assertEquals("Johnny Walker", m.b.name);
        assertEquals(2, m.outcome);                     // b won: positions never swapped by the result
        assertEquals("17-5-0", m.a.record);
        assertEquals("24-10-0", m.b.record);            // after the fight: the cards draw recordBefore
        SportEvent.Bout dec = bout(e, "401928111");
        assertEquals("DEC", dec.method);
        assertEquals("UNANIMOUS", dec.methodDetail);
        assertEquals(3, dec.round);
        assertEquals("5:00", dec.clock);
        assertEquals("Tatiana Suarez", dec.a.name);
        assertEquals(1, dec.outcome);
        SportEvent.Bout sub = bout(e, "401933235");
        assertEquals("SUB", sub.method);
        assertEquals("Rear Naked Choke", sub.methodDetail);
        assertEquals(1, sub.round);
        assertEquals("1:07", sub.clock);
        assertEquals(1, sub.outcome);
        // the store rebuilds the records before the fight: winner W-1, loser L-1
        EventStore.fillRecords(null, e);
        assertEquals("17-4-0", m.a.recordBefore);
        assertEquals("23-10-0", m.b.recordBefore);
    }

    private static SportEvent.Bout resultBout(String name, String display, String shortName, String desc, boolean aWins, boolean bWins) throws Exception {
        JSONObject res = new JSONObject().put("name", name).put("displayName", display).put("shortDisplayName", shortName);
        if (desc != null) res.put("description", desc);
        JSONObject st = new JSONObject().put("period", 3).put("displayClock", "5:00")
            .put("type", new JSONObject().put("name", "STATUS_FINAL").put("state", "post")).put("result", res);
        JSONArray cs = new JSONArray()
            .put(new JSONObject().put("order", 1).put("winner", aWins).put("athlete", new JSONObject().put("id", "1").put("displayName", "A One")))
            .put(new JSONObject().put("order", 2).put("winner", bWins).put("athlete", new JSONObject().put("id", "2").put("displayName", "B Two")));
        JSONObject comp = new JSONObject().put("id", "9").put("matchNumber", 3).put("status", st).put("competitors", cs);
        return UfcFeed.bout(comp);
    }

    @Test public void drawsNoContestsDisqualificationsAndSplitDecisions() throws Exception {
        SportEvent.Bout draw = resultBout("decision---split-draw", "Decision - Split Draw", "S Dec Draw", null, false, false);
        assertEquals(3, draw.outcome);
        assertEquals("DRAW", draw.method);
        assertEquals("SPLIT", draw.methodDetail);
        SportEvent.Bout nc = resultBout("no-contest", "No Contest", "NC", "Accidental Eye Poke", false, false);
        assertEquals(4, nc.outcome);
        assertEquals("NC", nc.method);
        SportEvent.Bout split = resultBout("decision---split", "Decision - Split", "S Dec", null, false, true);
        assertEquals(2, split.outcome);
        assertEquals("DEC", split.method);
        assertEquals("SPLIT", split.methodDetail);
        SportEvent.Bout maj = resultBout("decision---majority", "Decision - Majority", "M Dec", null, true, false);
        assertEquals("MAJORITY", maj.methodDetail);
        SportEvent.Bout dq = resultBout("dq", "DQ", "DQ", "Illegal Knee", true, false);
        assertEquals("DQ", dq.method);
        assertEquals("Illegal Knee", dq.methodDetail);
        SportEvent.Bout odd = resultBout("overturned", "Overturned", "Overturned", null, false, false);
        assertEquals(0, odd.outcome);
        assertEquals("Overturned", odd.method);  // anything else: ESPN's short name
        // the record rule after a draw and a no contest
        assertEquals("13-2-1", SportEvent.recordBefore("13-2-2", 3, true));
        assertEquals("13-2-2", SportEvent.recordBefore("13-2-2", 4, false));
    }

    @Test public void aCancelledBoutIsOffAndAWholeCancelledSegmentToo() throws Exception {
        JSONObject d = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        JSONArray early = d.getJSONObject("cards").getJSONObject("prelims2").getJSONArray("competitions");
        for (int i = 0; i < early.length(); i++)
            early.getJSONObject(i).getJSONObject("status").put("type", new JSONObject().put("name", "STATUS_CANCELED").put("state", "post"));
        SportEvent e = UfcFeed.parseFightcenter(d);
        SportEvent.Session s = e.session("prelims2");
        assertEquals("off", s.state);
        assertEquals("CANCELLED", s.detail);
        for (SportEvent.Bout b : s.bouts) { assertEquals("off", b.state); assertEquals(0, b.outcome); assertEquals("", b.method); }
        assertEquals("pre", e.state);
    }

    @Test public void anEmptyOrBrokenCardIsNoCard() throws Exception {
        assertNull(UfcFeed.parseFightcenter(new JSONObject()));
        assertNull(UfcFeed.parseFightcenter(null));
        JSONObject d = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        d.put("cards", new JSONObject());
        assertNull(UfcFeed.parseFightcenter(d));  // far ahead: no card yet, not shown
        JSONObject dwcs = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        dwcs.getJSONObject("event").put("name", "Dana White's Contender Series: Season 10, Week 1");
        assertNull(UfcFeed.parseFightcenter(dwcs));
        JSONObject oneCorner = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        oneCorner.getJSONObject("cards").getJSONObject("main").getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors").remove(1);
        assertNull(bout(UfcFeed.parseFightcenter(oneCorner), "401913646"));  // a bout needs both corners
    }

    @Test public void aRenamedEventIsTheSameEvent() throws Exception {
        JSONObject d = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
        d.getJSONObject("event").put("name", "UFC 334: Gane vs. Someone Else");
        SportEvent renamed = UfcFeed.parseFightcenter(d), first = ufc334();
        assertEquals(first.key(), renamed.key());
        SportEvent merged = EventStore.keepProgress(first, renamed, NOW);
        assertEquals("UFC 334: Gane vs. Someone Else", merged.name);
        assertEquals("600061400", merged.id);
    }

    @Test public void numberedOnlyForUfcAndANumber() throws Exception {
        for (String[] t : new String[][]{{"UFC 334: Gane vs. Hokit", "334"}, {"UFC Fight Night: Cortes Acosta vs. Walker", "0"},
                {"Noche UFC: Silva vs. Delgado", "0"}, {"UFC Freedom 250: Topuria vs. Gaethje", "0"}}) {
            JSONObject d = FeedSamples.json("espn_ufc_fightcenter_ufc334_pre.json");
            d.getJSONObject("event").put("name", t[0]);
            SportEvent e = UfcFeed.parseFightcenter(d);
            assertEquals(t[0], Integer.parseInt(t[1]), e.number);
            assertEquals(t[0], !"0".equals(t[1]), e.numbered);
        }
    }

    // ---------- time zones (§3.3) ----------
    @Test public void ufc334MainCardIsSunday0400InAthensWhateverTheDefaultZone() throws Exception {
        for (String z : new String[]{"Europe/Athens", "Europe/London", "America/New_York"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(z));
            SportEvent e = ufc334();
            assertEquals(z, "Sun 04:00", FeedSamples.athens(e.session("main").start));
            assertEquals(z, "Sun 00:00", FeedSamples.athens(e.session("prelims2").start));
            assertEquals(z, SportTestKit.U334_MAIN, SportEngine.mainStart(e));
            // the live scoreboard is filed under the US Eastern day of the first segment
            assertEquals(z, "20261114", Sources.nbaDates(e.start).get(0));
        }
    }

    // ---------- fighters for Settings ----------
    @Test public void upcomingFightersAreEachListedOnceMainEventFirst() throws Exception {
        SportEvent u334 = ufc334(), fn = fightNight();
        SportEvent later = ufc334();
        later.id = "600061401";
        for (SportEvent.Session s : later.sessions) s.start += 28 * SportTestKit.DAY;
        later.start += 28 * SportTestKit.DAY; later.end += 28 * SportTestKit.DAY;
        List<SportEvent> all = new ArrayList<>(Arrays.asList(fn, later, u334));
        List<SportEvent.Fighter> f = UfcFeed.upcomingFighters(all, NOW);
        assertEquals(26, f.size());  // 13 bouts on UFC 334; the same fighters on the copy count once; the finished card none
        assertEquals("4426000", f.get(0).id);  // Gane, main event, corner a
        assertEquals("4049391", f.get(1).id);
        f.get(0).name = "changed";
        assertEquals("Ciryl Gane", u334.mainEvent().a.name);  // copies
        assertTrue(UfcFeed.upcomingFighters(new ArrayList<SportEvent>(), NOW).isEmpty());
        assertTrue(UfcFeed.upcomingFighters(null, NOW).isEmpty());
    }

    // ---------- the refresh, end to end ----------
    private FeedSamples.FakeNet net() {
        FeedSamples.FakeNet n = new FeedSamples.FakeNet()
            .on(UfcFeed.BOARD + "$", FeedSamples.text("espn_ufc_scoreboard.json"))
            .on(UfcFeed.fightcenter("600061400"), FeedSamples.text("espn_ufc_fightcenter_ufc334_pre.json"))
            .on(UfcFeed.fightcenter("600061268"), FeedSamples.text("espn_ufc_fightcenter_fightnight_final.json"));
        FeedResult.net = n;
        return n;
    }

    @Test public void aRefreshReadsTheCalendarAndTheDueCards() throws Exception {
        FeedSamples.FakeNet n = net();
        FeedResult r = UfcFeed.fetch(c, new ArrayList<SportEvent>(), NOW);
        assertTrue(r.ok);
        assertFalse(r.results);
        assertEquals(43, r.listed.size());
        assertFalse(r.listed.contains("600060732"));
        Map<String, SportEvent> got = new HashMap<>();
        for (SportEvent e : r.events) got.put(e.id, e);
        assertEquals(2, got.size());  // UFC 334, and the last Fight Night (not stored yet)
        assertTrue(got.containsKey("600061400"));
        assertTrue(got.containsKey("600061268"));
        // the next three have no card behind the fake (404): misses, never cards
        assertTrue(r.missed.containsAll(Arrays.asList("600061665", "600062700", "600061401")));
        SportEvent u334 = got.get("600061400");
        assertEquals(NOW, u334.fetchedAt);
        assertEquals(5, u334.mainEvent().rounds);
        assertEquals(3, bout(u334, "401922641").rounds);  // the scoreboard's format
        assertEquals(1, n.countExact(UfcFeed.BOARD));
        assertEquals(NOW, c.getSharedPreferences(Sports.ENGINE, 0).getLong("lastCal_ufc", 0));
        assertTrue(new java.io.File(c.dir, UfcFeed.CAL_FILE).exists());

        // an hour later: the calendar is a day fresh, UFC 334 (2.4 days away, its week) is an hour fresh: asked again
        List<SportEvent> known = new ArrayList<>(r.events);
        FeedResult again = UfcFeed.fetch(c, known, NOW + SportTestKit.H);
        assertTrue(again.ok);
        assertEquals(1, n.countExact(UfcFeed.BOARD));
        assertEquals(43, again.listed.size());
        assertEquals(1, again.events.size());
        assertEquals("600061400", again.events.get(0).id);
        // a day later the calendar is asked for again
        UfcFeed.fetch(c, known, NOW + SportTestKit.DAY);
        assertEquals(2, n.countExact(UfcFeed.BOARD));
    }

    @Test public void offlineTheRefreshFails() throws Exception {
        FeedSamples.FakeNet n = net();
        n.down = true;
        FeedResult r = UfcFeed.fetch(c, new ArrayList<SportEvent>(), NOW);
        assertFalse(r.ok);
        assertTrue(r.events.isEmpty());
        assertTrue(r.missed.isEmpty());
        assertFalse(UfcFeed.fetch(null, new ArrayList<SportEvent>(), NOW).ok);
    }

    @Test public void theMergeKeepsRecordsBeforeTheFight() throws Exception {
        SportEvent pre = ufc334();
        List<SportEvent> all = EventStore.merge(new ArrayList<SportEvent>(), SportTestKit.ok(SportEvent.UFC, pre), NOW);
        assertEquals("14-2-0", all.get(0).mainEvent().a.recordBefore);
        assertEquals("10-0-0", all.get(0).mainEvent().b.recordBefore);
    }
}
