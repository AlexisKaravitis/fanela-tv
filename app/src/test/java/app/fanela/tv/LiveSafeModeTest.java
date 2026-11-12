package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The live safe mode of 1.3.0: F1Feed.LIVE_LAP_TRUSTED and UfcFeed.LIVE_ROUND_TRUSTED. While false no surface shows a
 * live lap, a live round or a round clock: the cards say LIVE and the session or the bout (SPEC-F1-UFC §2.1's fallbacks:
 * the start time at 40 %), the rail "LIVE · Race" / "LIVE", the app screens the start time, the launcher's words LIVE.
 * A finished bout's round and time stay. Each switch alone brings its own back (1.3.1).
 */
public class LiveSafeModeTest {
    private TimeZone was;

    @Before public void athens() {
        was = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
        I18n.use("en");
        I18n.clock24(true);
        EventCardModel.orderTrusted = false;
        F1Feed.lapTrusted = false;
        UfcFeed.roundTrusted = false;
    }

    @After public void restore() {
        TimeZone.setDefault(was);
        I18n.use("en");
        EventCardModel.orderTrusted = F1Feed.LIVE_ORDER_TRUSTED;
        F1Feed.lapTrusted = F1Feed.LIVE_LAP_TRUSTED;
        UfcFeed.roundTrusted = UfcFeed.LIVE_ROUND_TRUSTED;
    }

    static EventCardModel model(String fixture, boolean shown) { return EventFixtures.named(fixture).model(shown); }

    @Test public void theReleaseShipsTheSafeMode() {
        // 1.3.0: both off (1.3.1 turns them on after UFC 334 and the Las Vegas GP are recorded)
        assertFalse(F1Feed.LIVE_LAP_TRUSTED);
        assertFalse(UfcFeed.LIVE_ROUND_TRUSTED);
        assertFalse(F1Feed.LIVE_ORDER_TRUSTED);
    }

    // ---------- Formula 1 ----------

    @Test public void aLiveRaceCardSaysLiveAndTheSessionNeverTheLap() {
        for (String lang : new String[]{"en", "el"}) {
            I18n.use(lang);
            for (boolean shown : new boolean[]{true, false}) {
                for (String fx : new String[]{"f1_live_race", "f1_live_race_focus"}) {
                    EventCardModel m = model(fx, shown);
                    String at = fx + " " + lang + (shown ? "" : " hidden");
                    assertEquals(at, CardModel.State.LIVE, m.state);
                    assertEquals(at, "LIVE", m.stateLine);
                    assertEquals(at, "", m.clock);      // the pill: LIVE alone
                    assertEquals(at, "", m.lapLine);    // no Ticket caption
                    assertEquals(at, "LIVE", CardKit.liveText(EventKit.shim(m)));
                    assertEquals(at, lang.equals("el") ? "ΑΓΩΝΑΣ" : "RACE", m.sessionLabel);  // the who tier
                    if (shown) {  // the spec's fallback: the time it started, at 40 %
                        assertEquals(at, EventCardModel.Hero.TIME, m.hero);
                        assertEquals(at, 0.4f, m.heroAlpha, 0.001);
                        assertEquals(at, "06:00", m.time);
                    }
                    String d = EventCards.describe(m, 0);
                    assertFalse(at + ": " + d, d.contains("Lap") || d.contains("Γύρος") || d.contains("34"));
                    assertTrue(at + ": " + d, d.contains("LIVE"));
                    assertFalse(at, m.title().contains("34"));
                }
            }
        }
    }

    @Test public void theLapSwitchAloneBringsTheLapBack() {
        F1Feed.lapTrusted = true;
        EventCardModel m = model("f1_live_race", true);
        assertEquals("34/50", m.clock);
        assertEquals("LAP 34/50", m.lapLine);
        assertTrue(EventCards.describe(m, 0).contains("Lap 34/50"));
        // the round switch does not touch F1
        F1Feed.lapTrusted = false;
        UfcFeed.roundTrusted = true;
        assertEquals("", model("f1_live_race", true).clock);
    }

    @Test public void theRailSaysLiveAndTheSession() {
        SportEvent e = SportTestKit.lasVegas();
        SportEvent.Session race = SportModel.race(e);
        race.state = "in"; race.lap = 34; race.laps = 50;
        SportEngine.derive(e);
        long now = SportTestKit.LV_RACE + SportTestKit.H;
        assertEquals("LIVE · Race", SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now).railLine);
        I18n.use("el");
        assertEquals("LIVE · Αγώνας", SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now).railLine);
        I18n.use("en");
        F1Feed.lapTrusted = true;
        assertEquals("LIVE · 34/50", SportModel.liveLine(e, race, SportPrefs.none()));
    }

    @Test public void theAppScreensShowTheStartTimeNotTheLap() {
        SportEvent e = SportTestKit.lasVegas();
        SportEvent.Session race = SportModel.race(e);
        race.state = "in"; race.lap = 34; race.laps = 50;
        SportEngine.derive(e);
        long now = SportTestKit.LV_RACE + SportTestKit.H;
        for (boolean club : new boolean[]{true, false}) {  // the Club hero, the weekend screen's hero
            SportViews.When w = SportViews.when(e, race, HomeModel.Kind.LIVE, now, false, SportPrefs.none(), club);
            assertTrue(w.live);
            assertEquals("TIME", w.kind);
            assertTrue(w.dim);
            assertEquals(I18n.time(race.start), w.numeral);
            assertEquals("", w.label);
            assertEquals("Race", w.rel);
        }
        F1Feed.lapTrusted = true;
        SportViews.When t = SportViews.when(e, race, HomeModel.Kind.LIVE, now, false, SportPrefs.none(), true);
        assertEquals("LAP", t.kind);
        assertEquals("34/50", t.numeral);
    }

    @Test public void shownLapFollowsTheSwitch() {
        SportEvent.Session s = new SportEvent.Session();
        s.lap = 12;
        assertEquals(0, F1Feed.shownLap(s));
        assertEquals(0, F1Feed.shownLap(null));
        F1Feed.lapTrusted = true;
        assertEquals(12, F1Feed.shownLap(s));
    }

    // ---------- UFC ----------

    @Test public void aLiveBoutSaysLiveWithTheBoutNeverTheRound() {
        for (String lang : new String[]{"en", "el"}) {
            I18n.use(lang);
            for (boolean shown : new boolean[]{true, false}) {
                for (String fx : new String[]{"ufc_live_prelims", "ufc_live_main_r2"}) {
                    EventCardModel m = model(fx, shown);
                    String at = fx + " " + lang + (shown ? "" : " hidden");
                    assertEquals(at, CardModel.State.LIVE, m.state);
                    assertEquals(at, "LIVE", m.stateLine);
                    assertEquals(at, "", m.clock);
                    assertTrue(at, m.bout != null && m.bout.live);  // the bout in the cage on the who tier
                    assertFalse(at, m.bout.nameA.isEmpty() || m.bout.nameB.isEmpty());
                    assertFalse(at, m.hero == EventCardModel.Hero.ROUND);
                    assertFalse(at, m.heroWord.matches("R\\d+"));
                    assertTrue(at, m.heroRows.isEmpty());       // no OF 5 / ΑΠΟ 5
                    if (shown) {
                        assertEquals(at, EventCardModel.Hero.TIME, m.hero);
                        assertEquals(at, 0.4f, m.heroAlpha, 0.001);
                    }
                    EventKit.View v = EventKit.view(m);
                    assertEquals(at, "", v.caption);           // the Ticket's stub caption and Clash's clock row
                    assertEquals(at, "LIVE", v.pill);
                    String d = EventCards.describe(m, 0);
                    assertFalse(at + ": " + d, d.contains("Round") || d.contains("Γύρος") || d.contains("3:41") || d.contains("1:12"));
                    assertTrue(at + ": " + d, d.contains("LIVE"));
                }
            }
        }
        assertEquals("LIVE · Gane VS Hokit", model("ufc_live_main_r2", true).title());
        assertEquals("Gane", model("ufc_live_main_r2", true).bout.nameA);
    }

    @Test public void theRoundSwitchAloneBringsTheRoundBack() {
        UfcFeed.roundTrusted = true;
        EventCardModel m = model("ufc_live_main_r2", true);
        assertEquals(EventCardModel.Hero.ROUND, m.hero);
        assertEquals("R2", m.heroWord);
        assertEquals(Arrays.asList("OF 5"), m.heroRows);
        UfcFeed.roundTrusted = false;
        F1Feed.lapTrusted = true;
        assertEquals(EventCardModel.Hero.TIME, model("ufc_live_main_r2", true).hero);
    }

    @Test public void theRailAndTheAppScreensShowNoRound() {
        SportEvent u = SportTestKit.ufc334();
        SportEvent.Bout main = u.mainEvent();
        for (SportEvent.Session s : u.sessions) if (!"MAIN".equals(s.kind)) s.state = "post";
        u.session("main").state = "in";
        main.state = "in"; main.round = 2; main.clock = "3:41";
        SportEngine.derive(u);
        long now = SportTestKit.U334_MAIN + SportTestKit.H;
        assertEquals("LIVE", SportModel.of(Collections.singletonList(u), SportEvent.UFC, SportPrefs.none(), now).railLine);
        SportViews.When w = SportViews.when(u, u.session("main"), HomeModel.Kind.LIVE, now, false, SportPrefs.none(), true);
        assertEquals("TIME", w.kind);
        assertTrue(w.dim);
        assertFalse(w.numeral.startsWith("R"));
        assertEquals(SportText.bout(main), w.sub);  // the bout in the cage, not the next one
        assertEquals("", SportText.roundClock(main));  // the event screen's bout row: the LIVE pill alone
        assertEquals("", SportText.roundLine(main));
        UfcFeed.roundTrusted = true;
        assertEquals("LIVE · R2", SportModel.liveLine(u, u.session("main"), SportPrefs.none()));
        assertEquals("R2 3:41", SportText.roundClock(main));
        assertEquals("ROUND", SportViews.when(u, u.session("main"), HomeModel.Kind.LIVE, now, false, SportPrefs.none(), true).kind);
    }

    @Test public void aFinishedBoutKeepsItsRoundAndTime() {
        SportEvent.Bout b = new SportEvent.Bout();
        b.state = "post"; b.round = 3; b.clock = "2:27"; b.outcome = 1; b.method = "KO/TKO";
        assertEquals(3, UfcFeed.shownRound(b));
        assertEquals("2:27", UfcFeed.shownClock(b));
        assertEquals("R3 2:27", SportText.roundClock(b));
        assertEquals("Round 3 · 2:27", SportText.roundLine(b));
        EventCardModel m = EventCardModel.of(EventFixtures.u334Result(1, "KO/TKO", "Punches", 3, "2:27"), null, false,
            SportPrefs.none(), true, false, SportTestKit.U334_MAIN + 6 * SportTestKit.H);
        assertEquals(EventCardModel.Hero.METHOD, m.hero);
        assertEquals(Arrays.asList("R3", "2:27"), m.heroRows);
        b.state = "in";
        assertEquals(0, UfcFeed.shownRound(b));
        assertEquals("", UfcFeed.shownClock(b));
        assertEquals(0, UfcFeed.shownRound(null));
    }
}
