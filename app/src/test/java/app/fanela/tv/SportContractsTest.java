package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

/**
 * The F1/UFC contracts (F1-UFC-ARCHITECTURE §8.2): every stream's classes exist with their signatures, and the stubs
 * of streams B-G answer what the doc says, so the streams can build against each other. When a stream replaces its
 * stub it updates the assertion here that names it.
 */
public class SportContractsTest {
    private static final long NOW = SportTestKit.LV_FP1 - 3 * SportTestKit.DAY;

    @Test public void theFeedsAnswerNotOkWithoutAContextAndParseNothingFromNothing() {  // stream B
        List<SportEvent> known = new ArrayList<>();
        FeedResult f1 = F1Feed.fetch(null, known, NOW, true), ufc = UfcFeed.fetch(null, known, NOW);
        assertFalse(f1.ok);  // no Context: no cache, no request; the engine keeps the old events
        assertFalse(ufc.ok);
        assertEquals(SportEvent.F1, f1.sport);
        assertEquals(SportEvent.UFC, ufc.sport);
        assertTrue(f1.events.isEmpty());
        assertFalse(F1Feed.LIVE_ORDER_TRUSTED);
        assertTrue(F1Feed.parseScoreboard(new JSONObject()).isEmpty());
        assertTrue(F1Feed.parseCalendar(new JSONObject()).isEmpty());
        Map<String, String> unanswered = new HashMap<String, String>() {  // every board "failed earlier in this tick": no network
            @Override public boolean containsKey(Object k) { return true; }
            @Override public String get(Object k) { return null; }
        };
        assertNull(F1Feed.live(SportTestKit.lasVegas(), unanswered));
        assertNull(F1Feed.standings(null, false));
        assertNotNull(F1Feed.parseStandings(new JSONObject()).drivers);
        assertNull(F1Feed.roster(null, false));
        assertEquals(0, F1Feed.circuitLaps(null, "4250"));
        assertTrue(UfcFeed.parseCalendar(new JSONObject()).isEmpty());
        assertNull(UfcFeed.parseFightcenter(new JSONObject()));
        assertNull(UfcFeed.live(SportTestKit.ufc334(), unanswered));
        assertTrue(UfcFeed.upcomingFighters(known, NOW).isEmpty());
        assertEquals(8, UfcFeed.upcomingFighters(java.util.Collections.singletonList(SportTestKit.ufc334()), SportTestKit.U334_EARLY - SportTestKit.DAY).size());
        UfcFeed.applyScoreboard(SportTestKit.ufc334(), new JSONObject(), NOW);
        CalendarEntry ce = new CalendarEntry();
        ce.id = "600057449"; ce.label = "Las Vegas Grand Prix";
        assertNull(FeedResult.failed("f1").listed);
        assertNotNull(FeedResult.net);
    }

    @Test public void whereToWatchGuessesNothingKnownWithoutTheTable() {  // stream C (the table: SportWhereTest)
        SportWhere.use(null);  // the engine's tests run with no table (the test Context has no resources)
        I18n.use("en");
        SportEvent e = SportTestKit.lasVegas();
        SportWhere.guess(e, "GR");
        for (SportEvent.Session s : e.sessions) {
            assertFalse(s.watch.known);
            assertEquals("none", s.watch.listedBy);
        }
        assertTrue(SportWhere.parse("{}").isEmpty());
        assertNull(SportWhere.matchUfc(SportTestKit.ufc334(), new ArrayList<Guide.Airing>()));
        assertNull(SportWhere.matchF1(e.sessions.get(0), new ArrayList<Guide.Airing>()));
        assertEquals("Check your provider's guide", SportWhere.where(e.sessions.get(0), true));
        assertEquals("", SportWhere.where(SportTestKit.session("x", "FP1", 0), true));  // never asked
        assertNull(SportWhere.channel(e.sessions.get(0)));  // nothing known: no channel line
        assertEquals("ANT1+", SportWhere.appName(Cards.ANT1));
    }

    @Test public void theCardModelHasATimeHeroFromTheNextSession() {  // stream D
        SportEvent e = SportTestKit.lasVegas();
        EventCardModel m = EventCardModel.of(e, null, false, SportPrefs.none(), true, false, NOW);
        assertEquals(EventCardModel.Hero.TIME, m.hero);
        assertEquals(CardModel.State.NEXT, m.state);
        assertEquals("PRACTICE 1", m.sessionLabel);  // SportNames: FP1 is "Practice 1" on a card, its tight form FP1
        assertEquals(e.sessions.get(0), m.session);
        assertEquals(SportEvent.F1, m.sport);
        assertFalse(m.time.isEmpty());
        assertEquals("Formula 1", m.compNames.get(0));
        // a named session wins over the next one
        EventCardModel race = EventCardModel.of(e, "6000574495", false, SportPrefs.none(), true, false, NOW);
        assertEquals("RACE", race.sessionLabel);
        assertNotNull(race.title());

        // three days before the weekend: the "now" tile is about the race; nothing has finished, so no "last" tile
        EventCards.Pick now = EventCards.pickNow(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), NOW);
        assertEquals("6000574495", now.sessionId);
        assertFalse(now.last);
        assertNull(EventCards.pickLast(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), NOW));
        assertEquals(-1, EventCards.addRow(null, SportEvent.F1));
        assertEquals(3, EventCards.CARD_VERSION);  // 2: QAS-10; 3: the final polish
        assertNotNull(EventCards.rowLogo(null, SportEvent.F1).tag());
        assertEquals("f1", HomeRows.sportOf(Sports.rowKey("f1")));
        assertNull(HomeRows.sportOf("club_435"));
        assertNull(HomeRows.sportOf("sport_nascar"));
        assertEquals("Las Vegas Grand Prix", SportNames.title(e));  // never the sponsor
        EventCards.Pick p = new EventCards.Pick();
        p.e = e; p.sessionId = null; p.last = true;
    }

    @Test public void theStylesFallBackToKit() {  // stream E
        assertEquals(Appearance.KIT, EventStyles.get("nonsense").id());
        assertEquals(Appearance.KIT, EventStyles.get(null).id());
        for (String id : Appearance.STYLES) assertEquals(id, EventStyles.get(id).id());
        EventCardModel m = EventCardModel.of(SportTestKit.ufc334(), null, false, SportPrefs.none(), true, false, NOW);
        EventArt art = new EventArt(null, m);
        assertTrue(EventStyles.get(Appearance.KIT).suits(m, art));
        for (String id : Appearance.STYLES) assertTrue(EventStyles.get(id).suits(m, art));  // SPEC-F1-UFC §3.6: no style needs art
        assertNull(art.headshot(null));
        assertTrue(art.logos().isEmpty());
        art.release();
        assertNull(EventArt.rowLogo(null, SportEvent.UFC));
        assertNull(Circuits.outline(null, "4250"));
    }

    @Test public void theSportModelNamesTheSport() {  // stream F
        SportModel.SportData d = SportModel.of(new ArrayList<SportEvent>(), SportEvent.F1, SportPrefs.none(), NOW);
        assertEquals("f1", d.sport);
        assertEquals("Formula 1", d.label);
        assertNull(d.hero);
        assertEquals(HomeModel.Kind.NONE, d.kind);
        assertTrue(d.latest.isEmpty());
        // Host.openEvent is a default method: HomeActivity compiles unchanged until stream F overrides it
        HomeLayout.Host h = new HomeLayout.Host() {
            public void openMatch(Match m, Teams.Club club) { }
            public void openLive(Match m, Teams.Club club) { }
            public void openSettings() { }
            public void chooseClubs() { }
        };
        h.openEvent(SportTestKit.lasVegas(), null);
    }

    @Test public void theEngineFlagIsOnInDebugBuilds() {  // stream A (the unit tests run the debug variant)
        assertTrue(BuildConfig.SPORTS);
    }
}
