package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * What a narrow slot keeps: the rights holders in a where-to-watch cell (whole names, "+1" for the rest), the guide
 * advice in a fixture row, the 12-hour time column and the match screen's form cells next to the other leg.
 */
public class RowFitTest {
    @After public void english() { I18n.use("en"); }

    /** Every character half the size wide: "Sky Sports · TNT Sports" (23) is 276 at 24, 322 at 28. */
    static final AppKit.Measure HALF = (s, size) -> s.length() * size * 0.5f;

    static float w(AppKit.Fitted f) { return HALF.width(f.text, f.size); }

    @Test public void aNameThatFitsIsLeftAlone() {
        AppKit.Fitted f = AppKit.fitName("Sky Sports · TNT Sports", 28, 400, HALF);
        assertEquals("Sky Sports · TNT Sports", f.text);
        assertEquals(28, f.size, 0);
    }

    @Test public void theTextStepsDownBeforeAnyNameIsDropped() {
        AppKit.Fitted f = AppKit.fitName("Sky Sports · TNT Sports", 28, 300, HALF);
        assertEquals("Sky Sports · TNT Sports", f.text);
        assertEquals(26, f.size, 0);
    }

    @Test public void theSecondHolderGoesWholeWithACountNeverCutMidWord() {
        // the fixture row's sm cell: 24 px already, about 216 px for the names
        AppKit.Fitted f = AppKit.fitName("Sky Sports · TNT Sports", 24, 216, HALF);
        assertEquals("Sky Sports +1", f.text);
        assertEquals(24, f.size, 0);
        assertEquals("TNT Sports +1", AppKit.fitName("TNT Sports · Prime Video", 24, 216, HALF).text);
    }

    @Test public void asManyHoldersAsFitAreKept() {
        AppKit.Fitted f = AppKit.fitName("Sky Sports · TNT Sports · Prime Video", 24, 320, HALF);
        assertEquals("Sky Sports · TNT Sports +1", f.text);
        assertTrue(w(f) <= 320);
    }

    @Test public void aShorterLineTakesTheLargestSizeThatFits() {
        // 276 at 24 does not fit 200; "Sky Sports +1" does at the cell's own 28
        AppKit.Fitted f = AppKit.fitName("Sky Sports · TNT Sports", 28, 200, HALF);
        assertEquals("Sky Sports +1", f.text);
        assertEquals(28, f.size, 0);
    }

    @Test public void onlyASingleNameThatStillOverflowsIsEllipsised() {
        AppKit.Fitted f = AppKit.fitName("Novasports Premier League", 24, 200, HALF);
        assertTrue(f.text, f.text.endsWith("…"));
        assertTrue(f.text, f.text.startsWith("Novasports"));
        assertTrue(w(f) <= 200);
        assertEquals(24, f.size, 0);
    }

    @Test public void aFirstHolderThatFitsOnlyAloneIsShownWholeWithoutTheCount() {
        // "Premier Sports +1" is 204 at 24, "Premier Sports" 168
        AppKit.Fitted f = AppKit.fitName("Premier Sports · LaLiga TV", 24, 200, HALF);
        assertEquals("Premier Sports", f.text);
    }

    @Test public void aFirstHolderTooLongAloneIsCutWithoutACount() {
        AppKit.Fitted f = AppKit.fitName("Novasports Premier League · TNT Sports", 24, 200, HALF);
        assertTrue(f.text, f.text.endsWith("…"));
        assertTrue(w(f) <= 200);
    }

    @Test public void theGuideAdviceTakesTheBriefWordingOnlyWhenTheFullOneDoesNotFit() {
        assertEquals("Check your provider's guide", AppKit.guideText("Check your provider's guide", "Check your guide", 330, 24, HALF));
        assertEquals("Check your guide", AppKit.guideText("Check your provider's guide", "Check your guide", 300, 24, HALF));
        String cut = AppKit.guideText("Check your provider's guide", null, 300, 24, HALF);
        assertTrue(cut, cut.endsWith("…"));
    }

    @Test public void theGreekRowAdviceIsTheShortOneTheEnglishStaysWhole() {
        I18n.use("el");
        assertEquals("Δες τον οδηγό σου", I18n.t("check_guide_short"));
        assertTrue(I18n.t("check_guide_short").length() < I18n.t("check_guide").length());
        I18n.use("en");
        assertEquals(I18n.t("check_guide"), I18n.t("check_guide_short"));
    }

    @Test public void theTimeColumnRunsToTheGlyphOrTheCrest() {
        assertEquals(148, ClubViews.FixtureRow.timeRoom(false, true), 0);
        assertEquals(148, ClubViews.FixtureRow.timeRoom(false, false), 0);
        assertEquals("24-hour: the glyph at its column's start, as the spec", 100, ClubViews.FixtureRow.timeRoom(true, false), 0);
        assertEquals("12-hour: the glyph centred in its 48 px column", 110, ClubViews.FixtureRow.timeRoom(true, true), 0);
        assertEquals(336, ClubViews.FixtureRow.glyphLeft(true) + 14, 0);
    }

    @Test public void theF1SessionRowGivesATwelveHourTimeItsRoom() {
        // "10:30" in RC Bold 36 is about 82 px (77 at 34), "am" at 24 about 30: about 115 with the 3 px gap
        float hour = 77f * 36 / 34;
        assertFalse("the old 110 px column ran it into the session", ClubViews.FixtureRow.keepSuffix(hour, 30, 110));
        assertTrue(ClubViews.FixtureRow.keepSuffix(hour, 31, EventScreen.SessionRow.timeRoom(true)));
        assertEquals(EventScreen.SessionRow.nameX(true) - 8, EventScreen.SessionRow.TIME_X + EventScreen.SessionRow.timeRoom(true), 0);
        assertEquals("24-hour (Greek): the session where it always was", 336, EventScreen.SessionRow.nameX(false), 0);
        assertTrue("21:00 fits as before", EventScreen.SessionRow.timeRoom(false) >= 110);
        assertEquals("the name's right edge is fixed", EventScreen.SessionRow.nameX(false) + EventScreen.SessionRow.nameRoom(false),
            EventScreen.SessionRow.nameX(true) + EventScreen.SessionRow.nameRoom(true), 0);
    }

    @Test public void theSuffixStaysWholeOrGoesNeverShrunk() {
        // "10:00" RC Bold 34 is 77 px, "pm" at 24 is 30, "PM" 31
        assertTrue(ClubViews.FixtureRow.keepSuffix(77, 30, ClubViews.FixtureRow.timeRoom(true, true)));
        assertTrue(ClubViews.FixtureRow.keepSuffix(77, 31, ClubViews.FixtureRow.timeRoom(false, true)));
        assertFalse(ClubViews.FixtureRow.keepSuffix(77, 31, ClubViews.FixtureRow.timeRoom(true, true)));
        assertTrue(ClubViews.FixtureRow.keepSuffix(60, 31, ClubViews.FixtureRow.timeRoom(true, true)));
    }

    @Test public void twelveHourTimesSplitAtAnySpace() {
        assertEquals(5, ClubViews.FixtureRow.suffixAt("10:00 PM"));
        assertEquals(4, ClubViews.FixtureRow.suffixAt("9:00 PM"));
        assertEquals(5, ClubViews.FixtureRow.suffixAt("10:00 μ.μ."));
        assertEquals(5, ClubViews.FixtureRow.suffixAt("10:00 p. m."));
        assertEquals(-1, ClubViews.FixtureRow.suffixAt("21:00"));
    }

    @Test public void theFormRowDropsTheOldestNeverTheLatest() {
        float[] five = {100, 100, 100, 100, 100};
        assertEquals("all five fit", 0, MatchFacts.firstShown(five, 10, 540));
        assertEquals("one short: the oldest goes", 1, MatchFacts.firstShown(five, 10, 539));
        assertEquals(3, MatchFacts.firstShown(five, 10, 210));
        assertEquals("the newest alone", 4, MatchFacts.firstShown(five, 10, 100));
        assertEquals("nothing fits", 5, MatchFacts.firstShown(five, 10, 99));
        assertEquals(0, MatchFacts.firstShown(new float[0], 10, 500));
        // a wide newest cell (a three-digit basketball score) still comes first
        assertEquals(3, MatchFacts.firstShown(new float[]{90, 90, 90, 90, 300}, 36, 430));
    }
}
