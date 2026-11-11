package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

public class SettingsLogicTest {
    // a Settings page: chips, two rows of tiles (7 + 3), then two option rows
    private static final float[] CHIPS = {200, 500, 900, 1300};
    private static final float[] ROW1 = {207, 459, 711, 963, 1215, 1467, 1719};
    private static final float[] ROW2 = {207, 459, 711};
    private static final float[] OPT = {960};
    private static final float[][] PAGE = {CHIPS, ROW1, ROW2, OPT, OPT};

    @Test public void leftRightStayInTheLineAndStopAtItsEnds() {
        assertArrayEquals(new int[]{1, 3}, SettingsLogic.move(PAGE, 1, 2, SettingsLogic.RIGHT, null, null));
        assertArrayEquals(new int[]{1, 1}, SettingsLogic.move(PAGE, 1, 2, SettingsLogic.LEFT, null, null));
        assertNull(SettingsLogic.move(PAGE, 1, 0, SettingsLogic.LEFT, null, null));
        assertNull(SettingsLogic.move(PAGE, 1, 6, SettingsLogic.RIGHT, null, null));
        assertNull(SettingsLogic.move(PAGE, 3, 0, SettingsLogic.RIGHT, null, null));
    }

    @Test public void downGoesToTheNearestItemBelow() {
        assertArrayEquals(new int[]{2, 2}, SettingsLogic.move(PAGE, 1, 2, SettingsLogic.DOWN, null, null));
        // past the shorter row's end: its last tile
        assertArrayEquals(new int[]{2, 2}, SettingsLogic.move(PAGE, 1, 6, SettingsLogic.DOWN, null, null));
        assertArrayEquals(new int[]{3, 0}, SettingsLogic.move(PAGE, 2, 1, SettingsLogic.DOWN, null, null));
        assertNull(SettingsLogic.move(PAGE, 4, 0, SettingsLogic.DOWN, null, null));
        assertNull(SettingsLogic.move(PAGE, 0, 1, SettingsLogic.UP, null, null));
    }

    @Test public void upIntoTheChipsLandsOnTheShownLeague() {
        int[] pref = {2, -1, -1, -1, -1};
        assertArrayEquals(new int[]{0, 2}, SettingsLogic.move(PAGE, 1, 6, SettingsLogic.UP, pref, null));
        assertArrayEquals(new int[]{0, 2}, SettingsLogic.move(PAGE, 1, 0, SettingsLogic.UP, pref, null));
    }

    @Test public void fromAWideRowBackToWhereTheFocusLeft() {
        int[] rem = {-1, -1, 1, -1, -1};
        assertArrayEquals(new int[]{2, 1}, SettingsLogic.move(PAGE, 3, 0, SettingsLogic.UP, null, rem));
        // without a memory: the item nearest the row's centre
        assertArrayEquals(new int[]{2, 2}, SettingsLogic.move(PAGE, 3, 0, SettingsLogic.UP, null, null));
        // between rows of tiles the memory is not used (straight up, as the eye expects)
        assertArrayEquals(new int[]{1, 2}, SettingsLogic.move(PAGE, 2, 2, SettingsLogic.UP, null, new int[]{-1, 5, -1, -1, -1}));
    }

    @Test public void emptyLinesAreSkipped() {
        float[][] page = {CHIPS, {}, OPT};
        assertArrayEquals(new int[]{2, 0}, SettingsLogic.move(page, 0, 0, SettingsLogic.DOWN, null, null));
        assertArrayEquals(new int[]{0, 2}, SettingsLogic.move(page, 2, 0, SettingsLogic.UP, null, null));
        assertNull(SettingsLogic.move(page, 1, 0, SettingsLogic.DOWN, null, null));
    }

    @Test public void nearestPrefersTheFirstOfEquals() {
        assertEquals(0, SettingsLogic.nearest(new float[]{100, 300}, 200));
        assertEquals(1, SettingsLogic.nearest(new float[]{100, 300}, 201));
    }

    @Test public void theSectionHeadingGoesToTheTopWhenTheItemFits() {
        // Options heading at 940: the page scrolls 886 so it sits on the title-safe edge
        assertEquals(886, SettingsLogic.scrollTarget(940, 1008, 1118, 1080, 3000), 0.01);
        // the first section is the page's top: no scroll
        assertEquals(0, SettingsLogic.scrollTarget(54, 369, 619, 1080, 3000), 0.01);
    }

    @Test public void aLowItemPullsThePageUpToTheBottomEdge() {
        // the third row of tiles ends at 1175: it rests 74 px above the bottom edge
        assertEquals(1175 - 1006, SettingsLogic.scrollTarget(54, 925, 1175, 1080, 3000), 0.01);
        // never past the content's end
        assertEquals(480, SettingsLogic.scrollTarget(54, 1300, 1500, 1080, 1560), 0.01);
        // an item taller than the page keeps its top in view
        assertEquals(600 - 54, SettingsLogic.scrollTarget(54, 600, 1800, 1080, 3000), 0.01);
    }

    @Test public void revealXKeepsTheFocusedChipWhole() {
        assertEquals(0, SettingsLogic.revealX(0, 100, 400, 1728, 3000, 0), 0.01);
        assertEquals(1900 - 1728, SettingsLogic.revealX(0, 1600, 1900, 1728, 3000, 0), 0.01);
        assertEquals(300, SettingsLogic.revealX(800, 300, 600, 1728, 3000, 0), 0.01);
        assertEquals(3000 - 1728, SettingsLogic.revealX(0, 2800, 3100, 1728, 3000, 0), 0.01);
    }

    @Test public void thePanelScrollsUntilItsLastLineClearsTheFade() {
        assertEquals(0, SettingsLogic.panelMax(500, 770, 92), 0.01);
        assertEquals(1200 - (770 - 92), SettingsLogic.panelMax(1200, 770, 92), 0.01);
        float max = SettingsLogic.panelMax(1200, 770, 92);
        float[] top = SettingsLogic.thumb(0, max, 64, 844, 770), end = SettingsLogic.thumb(max, max, 64, 844, 770);
        assertEquals(64, top[0], 0.01);
        assertEquals(64 + 844, end[0] + end[1], 0.01);
        assertEquals(top[1], end[1], 0.01);
        assertArrayEquals(new float[]{64, 844}, SettingsLogic.thumb(0, 0, 64, 844, 770), 0.01f);
    }

    @Test public void nextCycles() {
        List<String> v = Arrays.asList("435", "605", "887");
        assertEquals("605", SettingsLogic.next(v, "435"));
        assertEquals("435", SettingsLogic.next(v, "887"));
        assertEquals("435", SettingsLogic.next(v, "999"));
        assertEquals("el", SettingsLogic.next(Arrays.asList("en", "el"), "en"));
        assertEquals("x", SettingsLogic.next(Arrays.<String>asList(), "x"));
    }

    /** One unit per character: easy widths. */
    private static final SettingsLogic.Measure CHARS = new SettingsLogic.Measure() { public float width(String s) { return s.length(); } };

    @Test public void wrapKeepsWordsWhole() {
        assertEquals(Arrays.asList("Asteras", "Tripoli"), SettingsLogic.wrap(CHARS, "Asteras Tripoli", 10, 2));
        assertEquals(Arrays.asList("OFI Crete"), SettingsLogic.wrap(CHARS, "OFI Crete", 10, 2));
        assertNull(SettingsLogic.wrap(CHARS, "Brighton and Hove Albion", 8, 2));
        // a word longer than the line keeps a line of its own
        assertEquals(Arrays.asList("Panathinaikos"), SettingsLogic.wrap(CHARS, "Panathinaikos", 8, 2));
    }

    @Test public void tileNames() {
        // English: always the short form
        assertEquals("Man City", SettingsLogic.tileName("Manchester City", "Man City", false, CHARS, 20));
        // Greek: the full name when it fits on two lines with whole words
        assertEquals("Μάντσεστερ Σίτι", SettingsLogic.tileName("Μάντσεστερ Σίτι", "Μάντσεστερ Σ.", true, CHARS, 12));
        // a word wider than the tile: the short form
        assertEquals("Ερυθρός Αστ.", SettingsLogic.tileName("Ερυθρός Αστέρας Βελιγραδίου", "Ερυθρός Αστ.", true, CHARS, 9));
    }

    @Test public void followedTilesTakeALittleClubColour() {
        int red = SettingsLogic.followedFill(0xFF151619, 0xFFE2001A);
        assertEquals(0x38, (red >> 16) & 0xFF);  // close to the spec's #3A1A1F
        assertEquals(0xFF151619, SettingsLogic.followedFill(0xFF151619, 0xFF151619));
    }

    @Test public void theCoalescerRunsOnceForABurstAndAgainForALateRequest() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Executor ex = new Executor() { public void execute(Runnable r) { queue.add(r); } };
        final int[] runs = {0};
        final SettingsLogic.Coalescer[] c = new SettingsLogic.Coalescer[1];
        c[0] = new SettingsLogic.Coalescer(ex, new Runnable() { public void run() { runs[0]++; if (runs[0] == 1) c[0].request(); } });
        c[0].request(); c[0].request(); c[0].request();
        assertEquals(1, queue.size());
        queue.poll().run();          // the first run; a request made while it runs queues one more
        assertEquals(1, queue.size());
        queue.poll().run();
        assertEquals(2, runs[0]);
        assertEquals(0, queue.size());
    }

    @Test public void backTakesTheTopScreenOffThenEndsTheFirstStartOrLeaves() {
        assertEquals(SettingsLogic.BACK_POP, SettingsLogic.back(1, false, false));   // Appearance, Country, a panel
        assertEquals(SettingsLogic.BACK_POP, SettingsLogic.back(2, true, false));    // the first start's country list
        assertEquals(SettingsLogic.BACK_FINISH_SETUP, SettingsLogic.back(0, true, false));  // Back = Skip
        assertEquals(SettingsLogic.BACK_LEAVE, SettingsLogic.back(0, false, false));
    }

    @Test public void backWhileThePageFallsAwayIsTheSystems() {
        assertEquals(SettingsLogic.BACK_SYSTEM, SettingsLogic.back(0, false, true));
        assertEquals(SettingsLogic.BACK_SYSTEM, SettingsLogic.back(0, true, true));
        assertEquals(SettingsLogic.BACK_SYSTEM, SettingsLogic.back(1, false, true));
    }

    @Test public void theFirstStartShowsOnlyUntilItWasDoneOrSkipped() {
        assertTrue(SettingsLogic.firstStart(true, false));    // a new install
        assertFalse(SettingsLogic.firstStart(true, true));    // "Choose clubs" after unfollowing every club
        assertFalse(SettingsLogic.firstStart(false, false));  // the gear
        assertFalse(SettingsLogic.firstStart(false, true));
    }

    @Test public void appearancesStylesAreReEnteredOnTheStyleLastLookedAt() {
        // Appearance: five previews, two layouts, the scores switch; the previews' line is kept
        float[][] page = {{259, 610, 960, 1310, 1661}, {522, 1398}, {960}};
        int[] none = {-1, -1, -1};
        boolean[] kept = {true, false, false};
        // the first time down there: the nearest
        assertArrayEquals(new int[]{0, 3}, SettingsLogic.move(page, 1, 1, SettingsLogic.UP, none, none, kept));
        // left on Clash: back to Clash from either layout, and from the switch (two lines down)
        int[] rem = {4, -1, -1};
        assertArrayEquals(new int[]{0, 4}, SettingsLogic.move(page, 1, 1, SettingsLogic.UP, none, rem, kept));
        assertArrayEquals(new int[]{0, 4}, SettingsLogic.move(page, 1, 0, SettingsLogic.UP, none, rem, kept));
        assertArrayEquals(new int[]{1, 1}, SettingsLogic.move(page, 2, 0, SettingsLogic.UP, none, new int[]{4, 1, -1}, kept));
        // a line that is not kept still goes to the nearest from a line of several items
        assertArrayEquals(new int[]{1, 1}, SettingsLogic.move(page, 0, 4, SettingsLogic.DOWN, none, new int[]{4, 0, -1}, kept));
        assertArrayEquals(new int[]{0, 3}, SettingsLogic.move(page, 1, 1, SettingsLogic.UP, none, rem, null));
    }
}
