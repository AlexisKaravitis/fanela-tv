package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Settings › Formula 1 and UFC (SportsSettings): the focus limit (at most 2, drivers and teams mixed), the Events
 * option's cycle and what it lets through (Sports.shows), the fighter order, the chip ids and the grid cells.
 */
public class SportsSettingsTest {
    private SportTestKit.Ctx c;

    @Before public void setUp() throws Exception { c = SportTestKit.context(); }
    @After public void tearDown() { SportTestKit.delete(c.dir); }

    // ---------- the focus: at most two, drivers and teams mixed ----------
    @Test public void aPickJoinsAtTheEndAndASecondOkTakesItOut() {
        List<String> one = SportsSettings.toggleFocus(Collections.<String>emptyList(), "d:5579");
        assertEquals(Arrays.asList("d:5579"), one);
        List<String> two = SportsSettings.toggleFocus(one, "t:106893");
        assertEquals(Arrays.asList("d:5579", "t:106893"), two);  // a driver and a team mix
        assertEquals(Arrays.asList("t:106893"), SportsSettings.toggleFocus(two, "d:5579"));
        assertEquals(Arrays.asList("d:5579"), SportsSettings.toggleFocus(two, "t:106893"));
        assertEquals(Arrays.asList("d:5579"), one);  // the input is never changed
    }

    @Test public void aThirdPickIsRefusedButTakingOneOutStillWorks() {
        List<String> two = Arrays.asList("d:5579", "d:4665");
        assertNull(SportsSettings.toggleFocus(two, "t:106892"));
        assertNull(SportsSettings.toggleFocus(two, "d:5829"));
        assertEquals(Arrays.asList("d:4665"), SportsSettings.toggleFocus(two, "d:5579"));
        assertEquals(Sports.MAX_FOCUS, two.size());
    }

    @Test public void onlyDriverAndTeamRefsAreFocus() {
        assertNull(SportsSettings.toggleFocus(null, "f:3104622"));
        assertNull(SportsSettings.toggleFocus(null, "5579"));
        assertNull(SportsSettings.toggleFocus(null, "d:"));
        assertEquals(Arrays.asList("d:1"), SportsSettings.toggleFocus(null, "d:1"));
        // a stored list with junk or doubles is cleaned on the way
        assertEquals(Arrays.asList("d:1", "t:2"), SportsSettings.toggleFocus(Arrays.asList("d:1", "x", "d:1"), "t:2"));
    }

    @Test public void theRefusedPickLeavesThePrefsAsTheyWere() {
        assertTrue(Sports.setF1Focus(c, Arrays.asList("d:5579", "t:106893"), null));
        List<String> next = SportsSettings.toggleFocus(Sports.f1Focus(c), "d:4665");
        assertNull(next);
        assertEquals(Arrays.asList("d:5579", "t:106893"), Sports.f1Focus(c));
        // and the store refuses a third on its own too
        assertFalse(Sports.setF1Focus(c, Arrays.asList("d:5579", "t:106893", "d:4665"), null));
        assertEquals("d:5579,t:106893", c.getSharedPreferences("settings", 0).getString("f1_focus", null));
    }

    // ---------- the UFC Events option ----------
    @Test public void theEventsOptionCyclesAllAndNumbered() {
        assertEquals(Sports.SCOPE_NUMBERED, SportsSettings.nextScope(Sports.SCOPE_ALL));
        assertEquals(Sports.SCOPE_ALL, SportsSettings.nextScope(Sports.SCOPE_NUMBERED));
        assertEquals(Sports.SCOPE_NUMBERED, SportsSettings.nextScope(null));  // unset counts as All
        assertEquals(Sports.SCOPE_NUMBERED, SportsSettings.nextScope("weird"));
    }

    @Test public void numberedOnlyHidesFightNightsUnlessAFollowedFighterIsOnTheCard() {
        SportEvent ufc334 = SportTestKit.ufc334();
        SportEvent fn = SportTestKit.ufc("600061500", "UFC Fight Night: Holland vs. Medić", SportTestKit.U334_EARLY + 7 * SportTestKit.DAY);
        SportEvent f1 = SportTestKit.lasVegas();
        // the option as Settings writes it, read back as the cards read it
        Sports.setUfcScope(c, SportsSettings.nextScope(Sports.ufcScope(c)));
        assertEquals(Sports.SCOPE_NUMBERED, Sports.ufcScope(c));
        SportPrefs p = Sports.prefs(c);
        assertTrue(Sports.shows(p, ufc334));
        assertFalse(Sports.shows(p, fn));
        assertTrue(Sports.shows(p, f1));  // F1 is never filtered
        // a fighter on the Fight Night's card brings it back
        String someone = fn.sessions.get(0).bouts.get(0).a.id;
        Sports.setUfcFighter(c, someone, "Someone", true);
        assertTrue(Sports.shows(Sports.prefs(c), fn));
        // All again: everything
        Sports.setUfcFighter(c, someone, null, false);
        Sports.setUfcScope(c, SportsSettings.nextScope(Sports.ufcScope(c)));
        assertTrue(Sports.shows(Sports.prefs(c), fn));
    }

    // ---------- fighters ----------
    @Test public void followedFightersComeFirstThenTheCardsInBillingOrderEachOnce() {
        List<String> upcoming = Arrays.asList("10", "11", "12", "13");
        assertEquals(Arrays.asList("12", "99", "10", "11", "13"),
            SportsSettings.fighterOrder(upcoming, new LinkedHashSet<>(Arrays.asList("12", "99"))));
        assertEquals(upcoming, SportsSettings.fighterOrder(upcoming, null));
        assertEquals(Arrays.asList("99"), SportsSettings.fighterOrder(null, Arrays.asList("99", "", "99")));
    }

    @Test public void fighterFollowWritesTheIdsAndTheName() {
        Sports.setUfcFighter(c, "3104622", "Ciryl Gane", true);
        Sports.setUfcFighter(c, "4049391", "Josh Hokit", true);
        assertEquals("3104622,4049391", c.getSharedPreferences("settings", 0).getString("ufc_fighters", null));
        assertEquals("Ciryl Gane", Sports.name(c, Sports.fighterRef("3104622")));
        Sports.setUfcFighter(c, "3104622", null, false);
        assertEquals("4049391", c.getSharedPreferences("settings", 0).getString("ufc_fighters", null));
    }

    @Test public void weightClassesAreShortInEnglishAndGreekInGreek() {
        assertEquals("Heavyweight", SportsSettings.weightWord("Heavyweight", false));
        assertEquals("W. bantamweight", SportsSettings.weightWord("Women's Bantamweight", false));
        assertEquals("Βαρέων βαρών", SportsSettings.weightWord("Heavyweight", true));
        assertEquals("Πετεινού γυναικών", SportsSettings.weightWord("Women's Bantamweight", true));
        assertEquals("the tile's 194 px", "Ημιμεσαίων", SportsSettings.weightWord("Welterweight", true));
        assertEquals("Ημιβαρέων βαρών", SportsSettings.weightWord("Light Heavyweight", true));
        assertEquals("Openweight", SportsSettings.weightWord("Openweight", true));  // unknown: as ESPN writes it
        assertEquals("", SportsSettings.weightWord(null, false));
    }

    // ---------- chips and the grid ----------
    @Test public void sportChipsNeverLookLikeLeagues() {
        assertEquals("sport:f1", SportsSettings.chipId("f1"));
        assertEquals("ufc", SportsSettings.sportOfChip("sport:ufc"));
        assertTrue(SportsSettings.isChip("sport:f1"));
        assertFalse(SportsSettings.isChip("sport:nba"));
        assertFalse(SportsSettings.isChip("eng.1"));
        assertNull(SportsSettings.sportOfChip("gre.1"));
        assertNull(SportsSettings.sportOfChip(null));
    }

    @Test public void fiveTeamsBesideTheSportTileThenRowsOfSeven() {
        assertArrayEquals(new int[]{0, 2}, SportsSettings.teamCell(0));
        assertArrayEquals(new int[]{0, 6}, SportsSettings.teamCell(4));
        assertArrayEquals(new int[]{1, 0}, SportsSettings.teamCell(5));
        assertArrayEquals(new int[]{1, 5}, SportsSettings.teamCell(10));  // the 11th team
        assertArrayEquals(new int[]{2, 0}, SportsSettings.teamCell(12));
        assertEquals(1, SportsSettings.teamRows(0));
        assertEquals(1, SportsSettings.teamRows(5));
        assertEquals(2, SportsSettings.teamRows(11));
    }
}
