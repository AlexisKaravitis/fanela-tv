package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Settings › Appearance: which previews are drawn again, and in what order. */
public class AppearancePreviewTest {
    @Test public void aLiveMatchWithAScoreIsRedrawnWhenTheSwitchFlips() {
        assertTrue(AppearancePage.showsScore(StoreTest.match("1", "in", "1", "0")));
    }

    @Test public void aResultIsRedrawnToo() {
        assertTrue(AppearancePage.showsScore(StoreTest.match("1", "post", "2", "2")));
    }

    @Test public void aMatchStillToPlayOrWithNoScoreYetIsNot() {
        assertFalse(AppearancePage.showsScore(StoreTest.match("1", "pre", null, null)));
        assertFalse(AppearancePage.showsScore(StoreTest.match("1", "in", null, null)));
        assertFalse(AppearancePage.showsScore(null));
    }

    @Test public void theLookedAtStyleIsDrawnFirstThenItsNeighbours() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, AppearancePage.order(0));
        assertArrayEquals(new int[]{2, 1, 3, 0, 4}, AppearancePage.order(2));
        assertArrayEquals(new int[]{3, 2, 4, 1, 0}, AppearancePage.order(3));
        assertArrayEquals("Clash, the last, then back along the row", new int[]{4, 3, 2, 1, 0}, AppearancePage.order(4));
        assertArrayEquals("nothing known: the first", new int[]{0, 1, 2, 3, 4}, AppearancePage.order(-1));
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, AppearancePage.order(5));
    }

    /** Five styles, one preview and one caption word each, in the order Settings lists them. */
    @Test public void everyStyleHasAPreviewAndAWord() {
        assertArrayEquals(new String[]{"signature", "clean", "kit", "ticket", "clash"}, Appearance.STYLES);
        assertEquals(Appearance.STYLES.length, AppearancePage.STYLE_WORDS.length);
        for (int i = 0; i < Appearance.STYLES.length; i++) assertEquals("style_" + Appearance.STYLES[i], AppearancePage.STYLE_WORDS[i]);
        assertEquals(Appearance.STYLES.length, AppearancePage.N);
    }

    /** The five previews fill the page's 1728 px column in one row, 24 apart, at the card's 16:9. */
    @Test public void fivePreviewsFitThePageColumnInOneRow() {
        assertEquals(1728f, AppearancePage.N * AppearancePage.PW + (AppearancePage.N - 1) * AppearancePage.PGAP, 0.01f);
        assertEquals(326.4f, AppearancePage.PW, 0.01f);
        assertEquals(AppearancePage.PW * 9 / 16, AppearancePage.PH, 0.01f);
    }

    @Test public void aPreviewIsDrawnAgainOnlyWhenItsCardScoresOrSizeChange() {
        String k = AppearancePage.previewKey("kit|el|card", true, 424);
        assertEquals(k, AppearancePage.previewKey("kit|el|card", true, 424));
        assertNotEquals(k, AppearancePage.previewKey("kit|en|card", true, 424));
        assertNotEquals(k, AppearancePage.previewKey("kit|el|card", false, 424));
        assertNotEquals(k, AppearancePage.previewKey("kit|el|card", true, 848));
    }
}
