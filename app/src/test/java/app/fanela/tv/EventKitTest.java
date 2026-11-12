package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Stream E: what an event card says (EventKit.view over stream D's model), the colours and the names' rules. */
public class EventKitTest {
    private TimeZone was;

    @Before public void athens() {
        was = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
        I18n.use("en");
        I18n.clock24(true);
        EventCardModel.orderTrusted = false;
    }

    @After public void restore() {
        TimeZone.setDefault(was);
        I18n.use("en");
        EventCardModel.orderTrusted = F1Feed.LIVE_ORDER_TRUSTED;
    }

    static final long NOW = SportTestKit.LV_FP1 - 10 * SportTestKit.DAY;

    @Test public void theBarIsLiftedOnlyWhenItWouldNotRead() {
        // the spec's two examples (Red Bull, Haas) are lifted; Ferrari, McLaren, Aston Martin are not
        assertTrue(EventKit.barColour(0x00327D) != 0xFF00327D);
        assertTrue(EventKit.barColour(0x5A5A5A) != 0xFF5A5A5A);
        assertEquals(0xFFDC0000, EventKit.barColour(0xDC0000));
        assertEquals(0xFFFF8700, EventKit.barColour(0xFF8700));
        assertEquals(0xFF006F62, EventKit.barColour(0x006F62));
        // the feed's raw colour (no alpha) is drawn opaque; none is graphite
        assertEquals(0xFF, EventKit.barColour(0xFF8700) >>> 24);
        assertEquals(EventKit.GRAPHITE, EventKit.barColour(0));
        // lifted: lighter, and readable on the field
        assertTrue(ClubTokens.contrast(EventKit.barColour(0x00327D), EventKit.FIELD) >= 3);
    }

    @Test public void initialsAreTheGivenNameAndTheCardSurname() {
        assertEquals("CG", EventKit.initials("Ciryl Gane", "Gane"));
        assertEquals("WC", EventKit.initials("Waldo Cortes Acosta", "Cortes Acosta"));
        assertEquals("JD", EventKit.initials("Jack Della Maddalena", "Della Maddalena"));
        assertEquals("A", EventKit.initials("Alatengheili", "Alatengheili"));
    }

    @Test public void fighterNamesEndWithTheTightForm() {
        assertEquals(Arrays.asList("Della Maddalena", "Maddalena"), EventKit.fighterNames(Arrays.asList("Della Maddalena", "Maddalena"), "", null));
        assertEquals(Arrays.asList("Gane"), EventKit.fighterNames(Collections.<String>emptyList(), "Gane", null));
        SportEvent.Fighter f = SportTestKit.fighter("1", "Waldo Cortes Acosta", "1-0-0");
        f.shortName = "W. Cortes Acosta";
        assertEquals(Arrays.asList("Cortes Acosta", "Acosta"), EventKit.fighterNames(null, null, f));
        // people in capitals keep their diacritics (SportNames.caps, not Cards.caps)
        assertEquals(Arrays.asList("MEDIĆ", "KONDRATAVIČIUS"), EventKit.caps(Arrays.asList("Medić", "Kondratavičius")));
    }

    @Test public void flagsAreEspnsPngs() {
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/usa.png", EventKit.flagUrl("us"));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/ned.png", EventKit.flagUrl("nl"));
        assertNull(EventKit.flagUrl(""));
        assertNull(EventKit.flagUrl("zz"));
    }

    @Test public void anF1WeekAheadIsTheRaceTimeAndItsChannel() {
        SportEvent e = SportTestKit.lasVegas();
        EventCardModel m = EventCardModel.of(e, e.sessions.get(4).id, false, SportPrefs.none(), true, false, NOW);
        EventKit.View v = EventKit.view(m);
        assertTrue(v.f1);
        assertEquals(EventKit.HeroKind.TIME, v.hero);
        assertEquals("06:00", v.time);
        assertEquals(1f, v.timeA, 0);
        assertEquals("Race", v.session.get(0));
        assertEquals(0, v.team1);  // no focus: graphite, not a team colour
        assertEquals("F1 · LAS VEGAS GP", EventKit.eyebrowText(v.eyebrow.get(0), true));
        assertFalse(v.date);
    }

    @Test public void aPodiumIsTheWinnerAndTwoRowsAndHiddenIsOneSlot() {
        SportEvent e = SportTestKit.lasVegas();
        SportEvent.Session race = e.sessions.get(4);
        for (SportEvent.Session s : e.sessions) s.state = "post";
        e.state = "post";
        race.order.add(SportTestKit.entrant("1", "VER", 1));
        race.order.add(SportTestKit.entrant("2", "NOR", 2));
        race.order.add(SportTestKit.entrant("3", "ANT", 3));
        long after = SportTestKit.LV_RACE + 3 * SportTestKit.H;
        EventKit.View v = EventKit.view(EventCardModel.of(e, race.id, true, SportPrefs.none(), true, false, after));
        assertEquals(EventKit.HeroKind.CODE, v.hero);
        assertEquals("VER", v.code);
        assertEquals(2, v.rows.size());
        assertEquals("2", v.rows.get(0).pos);
        assertEquals("NOR", v.rows.get(0).code);
        assertTrue(v.date);  // a result: the date on the support line
        EventKit.View h = EventKit.view(EventCardModel.of(e, race.id, true, SportPrefs.none(), false, false, after));
        assertEquals(EventKit.HeroKind.SLOT, h.hero);
        assertTrue(h.rows.isEmpty());
        assertEquals("", h.code);
    }

    @Test public void aFocusDriverIsTheHeroWithHisPlaceAndNumber() {
        SportEvent e = SportTestKit.lasVegas();
        SportEvent.Session race = e.sessions.get(4);
        for (SportEvent.Session s : e.sessions) s.state = "post";
        e.state = "post";
        for (int i = 1; i <= 5; i++) race.order.add(SportTestKit.entrant(String.valueOf(i), new String[]{"VER", "ANT", "RUS", "PIA", "NOR"}[i - 1], i));
        race.order.get(4).number = "1";
        SportPrefs p = new SportPrefs(Collections.singletonList("d:5"), Sports.SCOPE_ALL, null);
        EventKit.View v = EventKit.view(EventCardModel.of(e, race.id, true, p, true, false, SportTestKit.LV_RACE + 3 * SportTestKit.H));
        assertEquals(EventKit.HeroKind.CODE, v.hero);
        assertEquals("NOR", v.code);
        assertEquals("P5", v.pos);
        assertEquals(Arrays.asList("VER", "ANT"), Arrays.asList(v.rows.get(0).code, v.rows.get(1).code));
        assertEquals("", v.number);  // QAS-10: no race number on a result (it reads as a place): the circuit / flag
        assertTrue(v.team1 != 0);  // the card takes the team's colour
        EventKit.View hidden = EventKit.view(EventCardModel.of(e, race.id, true, p, false, false, SportTestKit.LV_RACE + 3 * SportTestKit.H));
        assertEquals("", hidden.number);  // scores hidden too
        // before the race: the focus driver's number on the disc, as before
        for (SportEvent.Session s : e.sessions) s.state = "pre";
        e.state = "pre";
        EventKit.View next = EventKit.view(EventCardModel.of(e, race.id, false, p, true, false, SportTestKit.LV_FP1 - SportTestKit.H));
        assertFalse(next.result);
        assertEquals("1", next.number);
    }

    @Test public void ufcNextIsTheMainEventAtTheMainCardsTime() {
        SportEvent e = SportTestKit.ufc334();
        EventKit.View v = EventKit.view(EventCardModel.of(e, null, false, SportPrefs.none(), true, false, NOW));
        assertFalse(v.f1);
        assertEquals("GANE", v.a.get(0));
        assertEquals("HOKIT", v.b.get(0));
        assertEquals(EventKit.HeroKind.TIME, v.hero);
        assertEquals("04:00", v.time);
        assertEquals(1f, v.aA, 0);
        assertEquals(1f, v.bA, 0);
        // the title in gold on the longest eyebrow
        assertTrue(v.eyebrow.get(0).get(v.eyebrow.get(0).size() - 1).gold);
        assertEquals("UFC 334 · TITLE", EventKit.eyebrowText(v.eyebrow.get(0), true));
    }

    @Test public void aUfcResultDimsTheLoserAndHiddenNeverDoes() {
        SportEvent e = SportTestKit.ufc334();
        for (SportEvent.Session s : e.sessions) { s.state = "post"; for (SportEvent.Bout b : s.bouts) b.state = "post"; }
        e.state = "post";
        SportEvent.Bout main = e.mainEvent();
        main.outcome = 2; main.method = "KO/TKO"; main.methodDetail = "Punch"; main.round = 3; main.clock = "2:27";
        long after = SportTestKit.U334_MAIN + 6 * SportTestKit.H;
        EventKit.View v = EventKit.view(EventCardModel.of(e, null, true, SportPrefs.none(), true, false, after));
        assertEquals(EventKit.HeroKind.METHOD, v.hero);
        assertEquals("KO/TKO", v.word);
        assertEquals(Arrays.asList("R3", "2:27"), v.wordRows);
        assertEquals("R3 · 2:27", v.caption);
        assertEquals(EventKit.LOSER_A, v.aA, 0);  // Gane lost: first billed, at 45 %
        assertEquals(1f, v.bA, 0);
        EventKit.View h = EventKit.view(EventCardModel.of(e, null, true, SportPrefs.none(), false, false, after));
        assertEquals(EventKit.HeroKind.SLOT, h.hero);
        assertEquals(1f, h.aA, 0);
        assertEquals(1f, h.bA, 0);
        assertEquals("", h.caption);
        assertEquals("", h.outcomeWord);
    }

    @Test public void theShimCarriesTheSharedLinesFields() {
        SportEvent e = SportTestKit.lasVegas();
        EventCardModel m = EventCardModel.of(e, e.sessions.get(4).id, false, SportPrefs.none(), true, true, NOW);
        CardModel c = EventKit.shim(m);
        assertEquals(m.stateLine, c.stateLine);
        assertTrue(c.stale);
        assertEquals(CardModel.State.NEXT, c.state);
    }
}
