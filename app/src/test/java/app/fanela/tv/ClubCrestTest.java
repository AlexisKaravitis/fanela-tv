package app.fanela.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A basketball side shows its club's own crest, never the section's or a sponsor's. */
public class ClubCrestTest {
    @Test public void euroLeagueSidesOfKnownClubsTakeTheClubsCrest() {
        assertEquals(Teams.crestUrl("435"), Teams.clubCrest("OLY", null, "https://img/olympiacos-basketball.png"));
        assertEquals(Teams.crestUrl("443"), Teams.clubCrest("PAN", null, "https://img/pao-bc-aktor.png"));
        assertEquals(Teams.crestUrl("86"), Teams.clubCrest("MAD", null, "https://img/rm-baloncesto.png"));
    }

    @Test public void greekBasketLeagueSidesByTheirSportsDbId() {
        assertEquals(Teams.crestUrl("605"), Teams.clubCrest(null, "135637", "https://img/paok-bc.png"));
        assertEquals(Teams.crestUrl("887"), Teams.clubCrest(null, "135626", "https://img/aek-bc.png"));
    }

    @Test public void anyOtherSideKeepsTheFeedsCrest() {
        assertEquals("https://img/dubai.png", Teams.clubCrest("DUB", null, "https://img/dubai.png"));
        assertEquals("https://img/kolossos.png", Teams.clubCrest(null, "999999", "https://img/kolossos.png"));
        assertEquals("", Teams.clubCrest("", "", null));
    }
}
