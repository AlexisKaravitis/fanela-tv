package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The sport rows on the home screen (F1-UFC-ARCHITECTURE §5.1): keys, fates, names, tiles' words and signatures. */
public class EventRowsTest {
    private TimeZone was;

    @Before public void athens() { was = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens")); I18n.use("en"); }

    @After public void restore() {
        TimeZone.setDefault(was); I18n.use("en");
        F1Feed.lapTrusted = F1Feed.LIVE_LAP_TRUSTED; UfcFeed.roundTrusted = UfcFeed.LIVE_ROUND_TRUSTED;
    }

    @Test public void sportRowKeysAreNeverClubRows() {
        assertEquals("sport_f1", Sports.rowKey("f1"));
        assertEquals("f1", HomeRows.sportOf("sport_f1"));
        assertEquals("ufc", HomeRows.sportOf("sport_ufc"));
        assertNull(HomeRows.sportOf("sport_"));
        assertNull(HomeRows.sportOf("sport_nba"));
        assertNull(HomeRows.sportOf("club_435"));
        assertNull(HomeRows.sportOf(null));
        assertNull(HomeRows.clubOf("sport_f1"));  // removeUnfollowed (clubs) never touches a sport row
    }

    @Test public void aSportRowsFateIsTheClubRule() {
        Set<String> f1 = new HashSet<>(Collections.singletonList("f1"));
        assertEquals(HomeRows.Fate.KEEP, HomeRows.rowFate(HomeRows.sportOf("sport_f1"), f1, true, true));
        assertEquals(HomeRows.Fate.HIDE, HomeRows.rowFate(HomeRows.sportOf("sport_f1"), f1, false, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate(HomeRows.sportOf("sport_f1"), f1, true, false));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate(HomeRows.sportOf("sport_ufc"), f1, true, true));
        assertEquals(HomeRows.Fate.REMOVE, HomeRows.rowFate(HomeRows.sportOf("sport_ufc"), f1, false, true));
    }

    @Test public void aBackgroundRefreshIsWorthItForASportWithNoRowYet() throws Exception {
        SportTestKit.Ctx c = SportTestKit.context();
        try {
            assertFalse("nothing followed, no rows", HomeRows.worthRefreshing(c));
            Sports.setFollowed(c, "f1", true);
            assertTrue("F1 followed, its row not made yet", HomeRows.worthRefreshing(c));
            c.getSharedPreferences("row", 0).edit().putLong("sport_f1", 12L).apply();
            assertFalse("the row exists but is not on the home screen (no provider here)", HomeRows.worthRefreshing(c));
        } finally {
            SportTestKit.delete(c.dir);
        }
    }

    @Test public void theRowsNameCarriesTheFocus() throws Exception {
        assertEquals("Formula 1", EventCards.rowName("f1", ""));
        assertEquals("Formula 1 · Norris", EventCards.rowName("f1", "Norris"));
        assertEquals("UFC", EventCards.rowName("ufc", null));
        SportTestKit.Ctx c = SportTestKit.context();
        try {
            assertEquals("Formula 1", EventCards.rowName(c, "f1"));
            java.util.Map<String, String> names = new java.util.HashMap<>();
            names.put("d:5579", "Lando Norris");
            Sports.setF1Focus(c, Arrays.asList("d:5579"), names);
            assertEquals("Formula 1 · Norris", EventCards.rowName(c, "f1"));
            names.put("t:106892", "McLaren");
            Sports.setF1Focus(c, Arrays.asList("t:106892", "d:5579"), names);
            assertEquals("Formula 1 · McLaren", EventCards.rowName(c, "f1"));
            assertEquals("UFC", EventCards.rowName(c, "ufc"));
        } finally {
            SportTestKit.delete(c.dir);
        }
    }

    @Test public void cardFilesMatchTheArtProvidersPatternAndNeverAClubsPrefix() {
        for (String sport : Sports.ALL)
            for (boolean last : new boolean[]{false, true}) {
                String n = EventCards.name(sport, last) + ".png";
                assertTrue(n, n.matches("c[a-z0-9]+_[a-z0-9_]+\\.png"));
                assertTrue(n, n.startsWith("cs" + sport + "_"));
            }
        assertEquals("csf1_tvnow", EventCards.name("f1", false));
        assertEquals("csufc_tvlast", EventCards.name("ufc", true));
    }

    @Test public void aTileOpensTheSessionOrTheEvent() {
        EventFixtures.Fx fx = EventFixtures.named("f1_weekend_qual_next");
        EventCards.Pick p = fx.pick();
        EventCardModel m = fx.model(true);
        assertEquals("f1|espn:600057449#6000574494", EventCards.matchKey(p, m));
        assertTrue(SportEvent.isEventKey(EventCards.matchKey(p, m)));
        EventFixtures.Fx u = EventFixtures.named("ufc_next_followed_fighter");
        assertEquals("ufc|espn:600061400#prelims1", EventCards.matchKey(u.pick(), u.model(true)));
    }

    @Test public void theLaunchersWordsFollowTheSpec() {
        EventCardModel q = EventFixtures.named("f1_weekend_qual_next").model(true);
        long qual = q.session.start;
        assertEquals("Formula 1 · Las Vegas Grand Prix · Qualifying · " + I18n.day(qual) + " " + I18n.time(qual) + " · Likely ANT1+",
            EventCards.describe(q, 0));
        assertEquals("Las Vegas Grand Prix", EventCards.headline(q));
        EventCardModel u = EventFixtures.named("ufc_next_numbered").model(true);
        long main = u.session.start;
        assertEquals("UFC 334 · Heavyweight title · " + I18n.day(main) + " 04:00 · Magenta Sport 6 · ch. 310", EventCards.describe(u, 0));
        assertEquals("Gane VS Hokit", EventCards.headline(u));
        assertEquals("Gane VS Hokit", EventCards.headlineNoScore(u));
        // results: the result segment only with scores shown
        EventCardModel r = EventFixtures.named("f1_result").model(true);
        assertEquals("Formula 1 · Las Vegas Grand Prix · Race · " + I18n.day(r.session.start) + " · VER P1, NOR P2, ANT P3", EventCards.describe(r, 0));
        EventCardModel rh = EventFixtures.named("f1_result").model(false);
        assertEquals("Formula 1 · Las Vegas Grand Prix · Race · " + I18n.day(r.session.start), EventCards.describe(rh, 0));
        EventCardModel ur = EventFixtures.named("ufc_result").model(true);
        assertTrue(EventCards.describe(ur, 0), EventCards.describe(ur, 0).endsWith(" · Gane wins · KO/TKO · R3 2:27"));
        EventCardModel urh = EventFixtures.named("ufc_result").model(false);
        assertEquals("UFC 334 · Heavyweight title · " + I18n.day(main), EventCards.describe(urh, 0));
        // live (1.3.1, once trusted): the lap stays when hidden, the round goes
        F1Feed.lapTrusted = true; UfcFeed.roundTrusted = true;
        EventCardModel lr = EventFixtures.named("f1_live_race").model(false);
        assertTrue(EventCards.describe(lr, 0), EventCards.describe(lr, 0).contains(" · LIVE · Lap 34/50"));
        EventCardModel lu = EventFixtures.named("ufc_live_main_r2").model(true);
        assertTrue(EventCards.describe(lu, 0), EventCards.describe(lu, 0).contains("LIVE · Round 2"));
        assertFalse(EventCards.describe(EventFixtures.named("ufc_live_main_r2").model(false), 0).contains("Round"));
        // the safe mode (1.3.0): LIVE and the session or the bout, never a lap or a round
        F1Feed.lapTrusted = false; UfcFeed.roundTrusted = false;
        String sr = EventCards.describe(EventFixtures.named("f1_live_race").model(true), 0);
        assertTrue(sr, sr.contains(" · Race · LIVE") && !sr.contains("Lap"));
        String su = EventCards.describe(EventFixtures.named("ufc_live_main_r2").model(true), 0);
        assertTrue(su, su.contains("LIVE") && !su.contains("Round"));
        // a stale card says so
        EventCardModel st = EventFixtures.named("f1_stale").model(true);
        assertTrue(EventCards.describe(st, 0).endsWith(I18n.t("not_updated")));
        I18n.use("el");
        EventCardModel el = EventFixtures.named("f1_weekend_qual_next").model(true);
        assertTrue(EventCards.describe(el, 0), EventCards.describe(el, 0).startsWith("Formula 1 · Γκραν Πρι Λας Βέγκας · Κατατακτήριες · "));
        assertTrue(EventCards.describe(el, 0).endsWith(" · Πιθανό ANT1+"));
    }

    @Test public void theAntennaAppIsAnt1PlusOnEveryPath() {
        EventCardModel q = EventFixtures.named("f1_weekend_qual_next").model(true);
        assertTrue(EventCards.describe(q, 0).endsWith(" · Likely ANT1+"));
        assertTrue(q.whereText, q.whereText.equals("Likely ANT1+"));
        EventCardModel lq = EventFixtures.named("f1_live_qual").model(true);
        for (EventCardModel m : new EventCardModel[]{q, lq, EventFixtures.named("f1_live_qual").model(false), EventFixtures.named("f1_tomorrow_fp1").model(true)}) {
            assertFalse(EventCards.describe(m, 0), EventCards.describe(m, 0).contains("Magenta"));
            assertFalse(m.whereText, m.whereText.contains("Magenta"));
        }
        assertTrue(EventCards.describe(lq, 0), EventCards.describe(lq, 0).contains("ANT1+"));
        assertEquals("ANT1+", SportWhere.appName(Cards.ANT1));
        assertEquals("Magenta TV", SportWhere.appName(Cards.MAGENTA));
        I18n.use("el");
        assertTrue(EventFixtures.named("f1_live_qual").model(true).whereText.endsWith("ANT1+"));
        I18n.use("en");
    }

    @Test public void theSignatureChangesWithWhatTheCardShows() {
        EventFixtures.Fx fx = EventFixtures.named("f1_week");
        EventCards.Pick p = fx.pick();
        EventCardModel m = fx.model(true);
        String base = EventCards.signature(m, p, fx.p, Appearance.SIGNATURE, true);
        assertEquals(base, EventCards.signature(fx.model(true), p, fx.p, Appearance.SIGNATURE, true));
        assertNotEquals(base, EventCards.signature(m, p, fx.p, Appearance.KIT, true));
        assertNotEquals(base, EventCards.signature(m, p, fx.p, Appearance.SIGNATURE, false));
        assertNotEquals(base, EventCards.signature(m, p, EventFixtures.focus("d:5579"), Appearance.SIGNATURE, true));
        I18n.use("el");
        assertNotEquals(base, EventCards.signature(fx.model(true), p, fx.p, Appearance.SIGNATURE, true));
        I18n.use("en");
        fx.e.sessions.get(4).watch.channel = "ANT1 HD";
        assertNotEquals(base, EventCards.signature(m, p, fx.p, Appearance.SIGNATURE, true));
    }

    @Test public void theRosterIsTheNewestEntryOfEachDriver() {
        SportEvent a = EventFixtures.raceDone("VER", "NOR", "ANT");
        java.util.Map<String, SportEvent.Entrant> r = EventCards.rosterFrom(Arrays.asList(a, EventFixtures.u334()));
        assertEquals(0x00327D, r.get(EventFixtures.VER).color);
        assertEquals("McLaren", r.get(EventFixtures.NOR).team);
        assertTrue(EventCards.rosterFrom(null).isEmpty());
    }

    @Test public void noContextNoRow() {
        assertEquals(-1, EventCards.addRow(null, "f1"));
        assertNull(EventCards.ensure(null, "f1", null, "csf1_tvnow", 0));
        EventCards.publishAll(null, null);  // nothing to do, nothing thrown
    }
}
