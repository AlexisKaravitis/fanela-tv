package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    // ---------- QAS-4: a row taken off is retired first, its channel deleted later ----------
    static final long T = 1_800_000_000_000L, MIN = 60000L;

    @Test public void aRetiredRowIsNeverDeletedWhileFanelaIsInFrontHoweverLongItWaits() {
        // the launcher, paused behind Fanela, crashed when a row it had focused left its list (HomeListView IOOBE),
        // deleted or emptied, even 3 minutes on: only once Fanela is out of the way
        assertFalse(HomeRows.deleteNow(T, T, true));
        assertFalse(HomeRows.deleteNow(T, T + 3 * MIN, true));
        assertFalse(HomeRows.deleteNow(T, T + 600 * MIN, true));
    }

    @Test public void aRetiredRowGoesOnceNoFanelaScreenIsInFront() {
        assertTrue(HomeRows.deleteNow(T, T, false));
        assertTrue(HomeRows.deleteNow(T, T + 10 * MIN, false));
        assertFalse(HomeRows.deleteNow(0, T, false));  // not retired: never deleted here
        assertFalse(HomeRows.deleteNow(T + 10 * MIN, T, false));  // a time from a wrong clock: left for a later pass
    }

    @Test public void unfollowedOrSwitchedOffRowsAreRetiredWhicheverTheirKind() {
        // clubs and sports alike (football included): REMOVE is carried out as a retirement, never a delete at once
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("443", FOLLOWED, true, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate(HomeRows.sportOf("sport_ufc"), new HashSet<>(Arrays.asList("f1")), true, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate("435", FOLLOWED, true, false));
    }

    @Test public void theRetiredListIsKeyedByChannel() {
        assertEquals(123L, HomeRows.retiredChannel("retire_123"));
        assertEquals(-1L, HomeRows.retiredChannel("club_435"));
        assertEquals(-1L, HomeRows.retiredChannel("retire_x"));
        assertEquals(-1L, HomeRows.retiredChannel(null));
        // a retired channel's provider id is out of both kinds' keys: no club or sport scan finds it, channel() neither
        assertEquals(null, HomeRows.clubOf(HomeRows.RETIRED_ID + "club_435"));
        assertEquals(null, HomeRows.sportOf(HomeRows.RETIRED_ID + "sport_f1"));
    }
}
