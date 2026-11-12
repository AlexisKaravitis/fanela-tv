package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Sports and SportPrefs (§2.1): the follow keys, the focus limit, the UFC scope, fighters, and shows(). */
public class SportsPrefsTest {
    private SportTestKit.Ctx c;

    @Before public void setUp() throws Exception { c = SportTestKit.context(); }
    @After public void tearDown() { SportTestKit.delete(c.dir); }

    private String setting(String key) { return c.getSharedPreferences("settings", 0).getString(key, null); }

    @Test public void anExistingUserFollowsNoSport() {
        assertTrue(Sports.followed(c).isEmpty());
        assertFalse(Sports.follows(c, "f1"));
        assertTrue(Sports.f1Focus(c).isEmpty());
        assertEquals("all", Sports.ufcScope(c));
        assertTrue(Sports.ufcFighters(c).isEmpty());
        assertTrue(Sports.nothingFollowed(c));
    }

    @Test public void followedSportsKeepTheOrderOfAll() {
        Sports.setFollowed(c, "ufc", true);
        Sports.setFollowed(c, "f1", true);
        assertEquals("f1,ufc", setting("sports"));
        assertEquals(Arrays.asList("f1", "ufc"), new java.util.ArrayList<>(Sports.followed(c)));
        Sports.setFollowed(c, "f1", false);
        assertEquals("ufc", setting("sports"));
        assertTrue(Sports.follows(c, "ufc"));
        Sports.setFollowed(c, "golf", true);  // not a sport we know
        assertEquals("ufc", setting("sports"));
        c.getSharedPreferences("settings", 0).edit().putString("sports", " ufc ,cricket,f1,f1").apply();
        assertEquals(new LinkedHashSet<>(Arrays.asList("f1", "ufc")), Sports.followed(c));
    }

    @Test public void followingClearsTheEnginesTimestamps() {
        c.getSharedPreferences("sports_engine", 0).edit().putLong("lastFull_f1", 1).putLong("lastCal_f1", 1).putLong("res_f1", 1)
            .putLong("ok_f1", 1).putLong("lastFull_ufc", 1).putLong("failedAt", 1).apply();
        Sports.setFollowed(c, "f1", true);
        assertEquals(new java.util.HashSet<>(Arrays.asList("lastFull_ufc", "failedAt")),
            c.getSharedPreferences("sports_engine", 0).getAll().keySet());
    }

    @Test public void theFocusHoldsAtMostTwo() {
        Map<String, String> names = new HashMap<>();
        names.put("d:5579", "Lando Norris");
        names.put("t:106893", "McLaren");
        names.put("d:1", "Not chosen");
        assertTrue(Sports.setF1Focus(c, Arrays.asList("d:5579", "t:106893"), names));
        assertEquals(Arrays.asList("d:5579", "t:106893"), Sports.f1Focus(c));
        assertEquals("Lando Norris", Sports.name(c, "d:5579"));
        assertEquals("McLaren", Sports.name(c, "t:106893"));
        assertEquals("", Sports.name(c, "d:1"));  // only the chosen refs are named
        assertFalse(Sports.setF1Focus(c, Arrays.asList("d:5579", "t:106893", "d:1"), names));  // a third pick is refused
        assertEquals(Arrays.asList("d:5579", "t:106893"), Sports.f1Focus(c));                  // and nothing changed
        assertFalse(Sports.setF1Focus(c, Collections.singletonList("5579"), null));             // not a ref
        assertFalse(Sports.setF1Focus(c, Collections.singletonList("d:1,d:2"), null));
        assertTrue(Sports.setF1Focus(c, Arrays.asList("d:1", "d:1"), null));                    // a repeat counts once
        assertEquals(Collections.singletonList("d:1"), Sports.f1Focus(c));
        assertTrue(Sports.setF1Focus(c, null, null));
        assertTrue(Sports.f1Focus(c).isEmpty());
        c.getSharedPreferences("settings", 0).edit().putString("f1_focus", "x:1,d:2,t:3,d:4").apply();  // edited by hand
        assertEquals(Arrays.asList("d:2", "t:3"), Sports.f1Focus(c));
    }

    @Test public void theUfcScope() {
        Sports.setUfcScope(c, "numbered");
        assertEquals("numbered", Sports.ufcScope(c));
        Sports.setUfcScope(c, "some");
        assertEquals("numbered", Sports.ufcScope(c));
        Sports.setUfcScope(c, "all");
        assertEquals("all", Sports.ufcScope(c));
        c.getSharedPreferences("settings", 0).edit().putString("ufc_scope", "junk").apply();
        assertEquals("all", Sports.ufcScope(c));
    }

    @Test public void fighters() {
        Sports.setUfcFighter(c, "4049391", "Josh Hokit", true);
        Sports.setUfcFighter(c, "3104622", "Ciryl Gane", true);
        Sports.setUfcFighter(c, "3104622", "Ciryl Gane", true);
        assertEquals(new LinkedHashSet<>(Arrays.asList("4049391", "3104622")), Sports.ufcFighters(c));
        assertEquals("Josh Hokit", Sports.name(c, Sports.fighterRef("4049391")));
        Sports.setUfcFighter(c, "4049391", null, false);
        assertEquals(Collections.singleton("3104622"), Sports.ufcFighters(c));
        Sports.setUfcFighter(c, "", "x", true);
        Sports.setUfcFighter(c, "1,2", "x", true);
        assertEquals(1, Sports.ufcFighters(c).size());
        SportPrefs p = Sports.prefs(c);
        assertTrue(p.followsFighter("3104622"));
        assertEquals("all", p.scope);
    }

    @Test public void nothingFollowedMeansNoClubAndNoSport() {
        assertTrue(Sports.nothingFollowed(c));
        Sports.setFollowed(c, "ufc", true);
        assertFalse(Sports.nothingFollowed(c));
        Sports.setFollowed(c, "ufc", false);
        c.getSharedPreferences("settings", 0).edit().putString("follow", "435").apply();
        assertFalse(Sports.nothingFollowed(c));
    }

    @Test public void prefsHelpers() {
        SportPrefs p = new SportPrefs(Arrays.asList("d:5579", "t:106893"), "numbered", null);
        assertEquals(Collections.singletonList("5579"), p.focusDrivers());
        assertEquals(Collections.singletonList("106893"), p.focusTeams());
        assertTrue(p.focusDriver("5579"));
        assertFalse(p.focusDriver("106893"));
        assertTrue(p.focusTeam("106893"));
        assertFalse(p.followsFighter("1"));
        assertEquals("all", new SportPrefs(null, "junk", null).scope);
        assertEquals("all", SportPrefs.none().scope);
        assertTrue(SportPrefs.none().focus.isEmpty());
        assertEquals("sport_f1", Sports.rowKey("f1"));
        assertEquals(Arrays.asList("f1", "ufc"), Sports.all());
    }

    @Test public void whatShows() {
        SportPrefs all = SportPrefs.none(), numbered = new SportPrefs(null, "numbered", null),
            withFighter = new SportPrefs(null, "numbered", Collections.singleton("12"));
        SportEvent f1 = SportTestKit.lasVegas(), ufc334 = SportTestKit.ufc334(),
            fn = SportTestKit.ufc("600061500", "UFC Fight Night: Tsarukyan vs. Hooker", SportTestKit.U334_EARLY + 7 * SportTestKit.DAY),
            noche = SportTestKit.ufc("600061600", "Noche UFC: Lopes vs. Silva", SportTestKit.U334_EARLY + 14 * SportTestKit.DAY),
            dwcs = SportTestKit.ufc("600061700", "Dana White's Contender Series 2026: Week 1", SportTestKit.U334_EARLY);
        for (SportPrefs p : new SportPrefs[]{all, numbered, withFighter}) assertTrue(Sports.shows(p, f1));  // F1: every event
        assertTrue(Sports.shows(all, fn));
        assertTrue(Sports.shows(all, noche));
        assertTrue(Sports.shows(numbered, ufc334));
        assertFalse(Sports.shows(numbered, fn));
        assertFalse(Sports.shows(numbered, noche));
        assertTrue(Sports.shows(withFighter, fn));  // a followed fighter's bout (Bo Two, id 12) shows any event
        assertFalse(Sports.shows(all, dwcs));       // never the Contender Series
        assertFalse(Sports.shows(withFighter, dwcs));
        assertFalse(Sports.shows(all, null));
        assertTrue(Sports.shows(null, fn));
        List<String> none = Sports.csv((String) null);
        assertTrue(none.isEmpty());
    }
}
