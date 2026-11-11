package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * The Club screen's fitting rules: the hero's fixture title (both clubs always named, P2e), the live strip's score
 * well (QA3A-3), the fixtures list scrolled on whole rows (QA3A-4) and the offline line (QA3G-3).
 */
public class ClubFitTest {
    @After public void english() { I18n.use("en"); }

    /** Every character half the size wide. */
    static final AppKit.Measure HALF = (s, size) -> s.length() * size * 0.5f;

    @Test public void aTitleThatFitsKeepsTheFullNames() {
        AppKit.Title t = AppKit.title(HALF, Arrays.asList("Olympiacos"), Arrays.asList("Dubai"), 860, 80, 60);
        assertEquals("Olympiacos VS Dubai", t.text);
        assertEquals(80, t.size, 0);
        assertFalse(t.cut);
    }

    @Test public void aTitleStepsDownBeforeAnyNameIsShortened() {
        // 23 characters and the mark (about one em with its air): 1000 at 80, 849 at 68
        AppKit.Title t = AppKit.title(HALF, Arrays.asList("Olympiacos"), Arrays.asList("Crvena Zvezda"), 860, 80, 60);
        assertEquals("Olympiacos VS Crvena Zvezda", t.text);
        assertEquals(68, t.size, 0);
    }

    /** P2e: "Μπορούσια Μένχενγκλαντμπαχ – Ντόρτμουντ" gives way to the card name, never "…". */
    @Test public void longNamesTakeTheCardsShortFormsBeforeAnEllipsis() {
        I18n.use("el");
        List<String> home = AppKit.forms("Borussia Mönchengladbach"), away = AppKit.forms("Borussia Dortmund");
        assertEquals(Arrays.asList("Μπορούσια Μένχενγκλαντμπαχ", "Γκλάντμπαχ"), home);
        assertEquals(Arrays.asList("Ντόρτμουντ"), away);
        AppKit.Title t = AppKit.title(HALF, home, away, 860, 80, 60);
        assertFalse(t.cut);
        assertEquals("Γκλάντμπαχ VS Ντόρτμουντ", t.text);  // the longer name gave way
        assertEquals(78, t.size, 0);
        I18n.use("en");  // and the cards' tight form after the card name: "Crvena Zvezda" -> "Zvezda"
        assertEquals("Zvezda", AppKit.forms("Crvena Zvezda").get(AppKit.forms("Crvena Zvezda").size() - 1));
        assertTrue(AppKit.titleWidth(HALF, t.home, t.away, t.size) <= 860);
        assertTrue(t.size >= 60);
    }

    @Test public void onlyATitleTooLongInItsShortestFormsIsCut() {
        AppKit.Title t = AppKit.title(HALF, Arrays.asList("A very long club name indeed"), Arrays.asList("Another very long club name"), 400, 80, 60);
        assertTrue(t.cut);
        assertEquals(60, t.size, 0);
    }

    @Test public void theLiveStripNamesShortenTheLongerOneFirst() {
        String[] p = AppKit.pair(HALF, Arrays.asList("Olympiacos", "Oly"), Arrays.asList("Crvena Zvezda", "Zvezda"), 10 * 22 + 6 * 22, 44);
        assertArrayEquals(new String[]{"Olympiacos", "Zvezda"}, p);
        String[] fits = AppKit.pair(HALF, Arrays.asList("PAOK"), Arrays.asList("Aris"), 1000, 44);
        assertArrayEquals(new String[]{"PAOK", "Aris"}, fits);
    }

    /** QA3A-3: a two-digit score keeps 150 in the 400 well; three digits step down to 120 and the well grows. */
    @Test public void aThreeDigitScoreFitsTheWell() {
        float[] two = ClubHero.scoreFit(300);
        assertEquals(150, two[0], 0);
        assertEquals(400, two[1], 0);
        // "100–78" and "110–108": wider than the 344 the well leaves at 150
        for (float w150 : new float[]{400, 470, 520}) {
            float[] f = ClubHero.scoreFit(w150);
            assertTrue(f[0] >= ClubHero.SCORE_MIN && f[0] < 150);
            float shown = w150 * f[0] / 150;
            assertTrue("score " + shown + " in well " + f[1], shown + 2 * ClubHero.WELL_PAD <= f[1] + 0.5f);
            assertTrue(f[1] >= 400);
        }
        assertEquals(400, ClubHero.scoreFit(400)[1], 0);  // 400 at 150 fits at 129 in the 400 well
    }

    /** QA3A-4: the list scrolls on whole rows, and its end still starts on a row with the last one above the fade. */
    @Test public void theFixturesScrollOnWholeRows() {
        float fade = 1000 - 736.05f, h = 1080 - 736.05f;
        int n = 12;
        float max = n * 60 + ClubLayout.listFoot(h, fade) - h;
        assertEquals(0, max % 60, 0.01);
        float sy = 0;
        for (int i = 0; i < n; i++) {  // down the list
            sy = Math.min(max, ClubLayout.rowScroll(i, n, sy, fade));
            assertEquals("row " + i, 0, sy % 60, 0.01);
            float bottom = (i + 1) * 60 - sy;
            assertTrue("row " + i + " clear", bottom <= (i < n - 1 ? fade - 60 : fade) + 0.01);
        }
        for (int i = n - 1; i >= 0; i--) {  // and back up
            sy = Math.min(max, ClubLayout.rowScroll(i, n, sy, fade));
            assertEquals(0, sy % 60, 0.01);
            assertTrue(i * 60 - sy >= (i > 0 ? 60 : 0) - 0.01);
        }
        // a list that fits never scrolls
        assertEquals(0, ClubLayout.rowScroll(3, 4, 0, fade), 0);
    }

    /** QA3G-3: the offline line only after a refresh failed since the last success, whatever Android says. */
    @Test public void offlineOnlyWhenARefreshFailedAfterTheLastSuccess() {
        long now = 1_800_000_000_000L, min = 60000L;
        long last = now - 40 * min;
        assertFalse(HomeModel.offline(true, false, last, false, 0, now));               // never failed: online
        assertFalse(HomeModel.offline(true, false, last, false, last - min, now));      // failed before the success
        assertTrue(HomeModel.offline(true, false, last, false, now - min, now));        // failed since, 40 min old
        assertFalse(HomeModel.offline(true, false, now - 5 * min, false, now - min, now));  // failed, but fresh data
        assertFalse(HomeModel.offline(false, false, last, false, now - min, now));      // no refresh of ours yet
        assertFalse(HomeModel.offline(true, true, 0, false, now, now));                 // nothing to show
        assertTrue(HomeModel.offline(true, false, 0, false, 0, now));                   // never loaded
        assertTrue(HomeModel.offline(true, false, last, true, 0, now));                 // a feed silent for hours
    }
}
