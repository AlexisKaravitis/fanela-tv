package app.fanela.tv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** QA3A-2: a row leaves the home screen at once on unfollow and on rows off, never for a club still followed. */
public class HomeRowsTest {
    static final Set<String> FOLLOWED = new HashSet<>(Arrays.asList("435", "605"));

    @Test public void anUnfollowedClubsRowIsDeletedAtOnce() {
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("443", FOLLOWED, true, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("443", FOLLOWED, false, true));  // whatever the home screen
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("443", new HashSet<String>(), true, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate(null, FOLLOWED, true, true));
    }

    @Test public void rowsSwitchedOffAreDeletedAtOnce() {
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("435", FOLLOWED, true, false));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("435", FOLLOWED, false, false));
    }

    @Test public void aFollowedClubKeepsItsRow() {
        // an upgrade, a refresh or a language change: rows on, the club followed, the row stays (and is refilled)
        assertEquals(HomeRows.Fate.KEEP, HomeRows.rowFate("435", FOLLOWED, true, true));
        assertEquals(HomeRows.Fate.KEEP, HomeRows.rowFate("605", FOLLOWED, true, true));
    }

    @Test public void anotherHomeScreenOnlyEmptiesAFollowedClubsRow() {
        // rows can't be seen there; emptied and kept KEEP_HIDDEN, so a switch back to the Android TV home finds them
        assertEquals(HomeRows.Fate.HIDE, HomeRows.rowFate("435", FOLLOWED, false, true));
        assertEquals(7 * 86400000L, HomeRows.KEEP_HIDDEN);
    }
}
