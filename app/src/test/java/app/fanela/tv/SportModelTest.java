package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * The app's model of a followed sport (SportModel): the hero (live, a fresh result, the next), the latest results,
 * the timetable and the rail's line; the words SportText gives them; how HomeModel and Matchday's rows take them in.
 */
public class SportModelTest {
    @After public void english() {
        I18n.use("en");
        F1Feed.lapTrusted = F1Feed.LIVE_LAP_TRUSTED; UfcFeed.roundTrusted = UfcFeed.LIVE_ROUND_TRUSTED;
    }

    static final long H = SportTestKit.H, MIN = SportTestKit.MIN, DAY = SportTestKit.DAY;

    static SportEvent.Entrant x(String code, int pos) { return SportTestKit.entrant("d" + code, code, pos); }

    /** Las Vegas with every session done up to (not including) index `upTo`, with podiums. */
    static SportEvent lasVegas(int upTo) {
        SportEvent e = SportTestKit.lasVegas();
        List<SportEvent.Session> by = e.byStart();
        for (int i = 0; i < upTo && i < by.size(); i++) {
            SportEvent.Session s = by.get(i);
            s.state = "post";
            s.order.add(x("NOR", 1)); s.order.add(x("VER", 2)); s.order.add(x("ANT", 3));
        }
        SportEngine.derive(e);
        return e;
    }

    @Test public void nothingListedIsNoHero() {
        SportModel.SportData d = SportModel.of(new ArrayList<SportEvent>(), SportEvent.UFC, SportPrefs.none(), SportTestKit.LV_FP1);
        assertEquals("UFC", d.label);
        assertEquals("sport:ufc", d.id());
        assertNull(d.hero);
        assertEquals(HomeModel.Kind.NONE, d.kind);
        assertTrue(d.upcoming.isEmpty());
        assertEquals("", d.railLine);
    }

    @Test public void beforeTheWeekendTheHeroIsTheRaceAsOnTheCardAndTheRailTheFirstSession() {
        long now = SportTestKit.LV_FP1 - 2 * DAY;
        SportModel.SportData d = SportModel.of(Collections.singletonList(SportTestKit.lasVegas()), SportEvent.F1, SportPrefs.none(), now);
        assertEquals(HomeModel.Kind.NEXT, d.kind);
        assertEquals("RACE", d.hero.e.session(d.hero.sessionId).kind);  // EventCards.pickNow: the race until the weekend
        assertEquals(5, d.upcoming.size());
        assertEquals(SportText.railNext(now, SportTestKit.LV_FP1, false), d.railLine);
        assertFalse(d.railLive);
    }

    @Test public void midWeekendTheHeroIsTheNextSessionAndDoneSessionsLeaveTheTimetable() {
        long now = SportTestKit.LV_FP3 + 3 * H;  // FP1-FP3 done, qualifying next
        SportModel.SportData d = SportModel.of(Collections.singletonList(lasVegas(3)), SportEvent.F1, SportPrefs.none(), now);
        assertEquals("QUAL", d.hero.e.session(d.hero.sessionId).kind);
        assertEquals(2, d.upcoming.size());
        // practice is never a result tile
        assertTrue(d.latest.isEmpty());
    }

    @Test public void aLiveRaceLeadsTheRailWithItsLap() {
        SportEvent e = lasVegas(4);
        SportEvent.Session race = SportModel.race(e);
        race.state = "in"; race.lap = 34; race.laps = 50;
        SportEngine.derive(e);
        long now = SportTestKit.LV_RACE + H;
        SportModel.SportData d = SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now);
        assertEquals(HomeModel.Kind.LIVE, d.kind);
        assertEquals(race.id, d.hero.sessionId);
        assertTrue(d.railLive);
        assertEquals("LIVE · Race", d.railLine);  // the safe mode (F1Feed.LIVE_LAP_TRUSTED false)
        F1Feed.lapTrusted = true;
        assertEquals("LIVE · 34/50", SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now).railLine);
        F1Feed.lapTrusted = false;
        // the qualifying podium is the latest tile while the race is on
        assertEquals(1, d.latest.size());
        assertEquals("QUAL", d.latest.get(0).e.session(d.latest.get(0).sessionId).kind);
    }

    @Test public void aFreshRaceResultIsTheHeroAndTheRailSaysWhoWon() {
        SportEvent e = lasVegas(5);
        long now = SportTestKit.LV_RACE + 3 * H;
        SportModel.SportData d = SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now);
        assertEquals(HomeModel.Kind.RESULT, d.kind);
        assertTrue(d.hero.last);
        assertEquals("NOR won", d.railLine);
        // after the race, the race is the weekend's result: its qualifying is not listed beside it
        assertTrue(d.latest.isEmpty());
        // hidden (the in-app switch, when the owner turns it on): Final, no name
        SportModel.SportData h = SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now, true, "");
        assertEquals("Final", h.railLine);
        // a day later the result is no longer news: no hero, no line
        SportModel.SportData later = SportModel.of(Collections.singletonList(e), SportEvent.F1, SportPrefs.none(), now + 2 * DAY);
        assertNull(later.hero);
        assertEquals("", later.railLine);
        assertEquals(1, later.latest.size());
    }

    @Test public void theNextWeekendFollowsInTheTimetable() {
        SportEvent lv = lasVegas(3), qatar = SportTestKit.f1("600057450", SportTestKit.LV_FP1 + 7 * DAY);
        long now = SportTestKit.LV_FP3 + 3 * H;
        SportModel.SportData d = SportModel.of(Arrays.asList(qatar, lv), SportEvent.F1, SportPrefs.none(), now);
        assertEquals(7, d.upcoming.size());
        assertEquals(lv, d.upcoming.get(0).e);
        assertEquals(qatar, d.upcoming.get(6).e);
    }

    @Test public void theFocusNamesTheRow() {
        SportModel.SportData d = SportModel.of(new ArrayList<SportEvent>(), SportEvent.F1, SportPrefs.none(), 0, false, "Norris");
        assertEquals("Formula 1 · Norris", d.label);
    }

    @Test public void ufcTheHeroIsTheMainCardOrAFollowedFightersSegment() {
        SportEvent u = SportTestKit.ufc334();
        long now = SportTestKit.U334_EARLY - 3 * DAY;
        SportModel.SportData d = SportModel.of(Collections.singletonList(u), SportEvent.UFC, SportPrefs.none(), now);
        assertEquals("main", d.hero.sessionId);
        assertEquals(SportText.railNext(now, SportTestKit.U334_MAIN, false), d.railLine);
        // the card's segments in the order of interest
        assertEquals(Arrays.asList("main", "prelims1", "prelims2"), Arrays.asList(d.upcoming.get(0).sessionId, d.upcoming.get(1).sessionId, d.upcoming.get(2).sessionId));
        SportPrefs p = new SportPrefs(null, Sports.SCOPE_ALL, new HashSet<>(Collections.singletonList("21")));  // Cy Three, in the prelims
        SportModel.SportData f = SportModel.of(Collections.singletonList(u), SportEvent.UFC, p, now);
        assertEquals("prelims1", f.hero.sessionId);
    }

    @Test public void ufcLiveSaysTheRoundAndAResultTheWinner() {
        SportEvent u = SportTestKit.ufc334();
        SportEvent.Bout m = u.mainEvent();
        for (SportEvent.Session s : u.sessions) if (!"MAIN".equals(s.kind)) s.state = "post";
        u.session("main").state = "in";
        m.state = "in"; m.round = 2;
        SportEngine.derive(u);
        long now = SportTestKit.U334_MAIN + H;
        SportModel.SportData d = SportModel.of(Collections.singletonList(u), SportEvent.UFC, SportPrefs.none(), now);
        assertEquals(HomeModel.Kind.LIVE, d.kind);
        assertEquals("LIVE", d.railLine);  // the safe mode (UfcFeed.LIVE_ROUND_TRUSTED false)
        UfcFeed.roundTrusted = true;
        assertEquals("LIVE · R2", SportModel.of(Collections.singletonList(u), SportEvent.UFC, SportPrefs.none(), now).railLine);
        UfcFeed.roundTrusted = false;
        m.state = "post"; m.outcome = 1; m.method = "KO/TKO"; m.round = 3; m.clock = "2:27";
        for (SportEvent.Bout b : u.session("main").bouts) b.state = "post";
        u.session("main").state = "post";
        SportEngine.derive(u);
        SportModel.SportData r = SportModel.of(Collections.singletonList(u), SportEvent.UFC, SportPrefs.none(), now + 2 * H);
        assertEquals(HomeModel.Kind.RESULT, r.kind);
        assertEquals("Gane won", r.railLine);
        assertEquals("KO/TKO", SportText.methodShort(m));
        assertEquals("R3 2:27", SportText.roundClock(m));
        assertEquals("Gane wins", SportText.wins(m));
    }

    @Test public void ufcNumberedOnlyHidesFightNights() {
        SportEvent fn = SportTestKit.ufc("600061300", "UFC Fight Night: Walker vs. Cortes Acosta", SportTestKit.U334_EARLY + 7 * DAY);
        SportPrefs numbered = new SportPrefs(null, Sports.SCOPE_NUMBERED, null);
        SportModel.SportData d = SportModel.of(Collections.singletonList(fn), SportEvent.UFC, numbered, SportTestKit.U334_EARLY);
        assertNull(d.hero);
        assertTrue(d.events.isEmpty());
    }

    @Test public void aSportAloneIsNotTheEmptyState() {
        HomeModel h = new HomeModel();
        assertTrue(h.empty());
        h.sports.add(SportModel.of(new ArrayList<SportEvent>(), SportEvent.F1, SportPrefs.none(), 0));
        assertFalse(h.empty());
        assertNotNull(h.sport("sport:f1"));
        assertNull(h.sport("435"));
        assertEquals("", HomeModel.speak(null, HomeModel.Kind.NEXT, 0));
    }

    @Test public void matchdayASportsRowOpensOnItsHeroWithTheResultsLeft() {
        SportEvent sp = lasVegas(5);  // a finished weekend a week before
        for (SportEvent.Session s : sp.sessions) s.start -= 9 * DAY;
        sp.id = "600057448";
        SportEvent lv = lasVegas(3);
        long now = SportTestKit.LV_FP3 + 3 * H;
        HomeModel h = new HomeModel();
        h.now = now;
        h.sports.add(SportModel.of(Arrays.asList(sp, lv), SportEvent.F1, SportPrefs.none(), now));
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertEquals(1, rows.size());  // no club: row 1 only while a sport is live
        MatchdayModel.Row r = rows.get(0);
        assertEquals("sport:f1", r.id);
        MatchdayModel.Item first = r.items.get(r.start);
        assertEquals("QUAL", first.ev.session().kind);
        assertTrue(first.next);
        assertTrue(r.items.get(r.start - 1).result());
        assertEquals("sport:f1", first.stageId());
        // live: the sport joins row 1
        SportModel.race(lv).state = "in";
        lv.session(SportModel.race(lv).id).lap = 3;
        for (SportEvent.Session s : lv.sessions) if ("QUAL".equals(s.kind)) s.state = "post";
        SportEngine.derive(lv);
        HomeModel live = new HomeModel();
        live.now = SportTestKit.LV_RACE + H;
        live.sports.add(SportModel.of(Arrays.asList(sp, lv), SportEvent.F1, SportPrefs.none(), live.now));
        List<MatchdayModel.Row> lr = MatchdayModel.rows(live);
        assertEquals(MatchdayModel.FIRST, lr.get(0).id);
        assertEquals(1, lr.get(0).items.size());
        assertTrue(lr.get(0).items.get(0).live());
    }

    @Test public void matchdayTheRaceAsHeroKeepsTheWeekendsPracticesAndTrimsTheNextWeekend() {
        // QAS-1: before the weekend the hero is the race; FP1 and FP2 stay on the row, the next weekend is cut instead
        SportEvent lv = SportTestKit.lasVegas(), qatar = SportTestKit.f1("600057450", SportTestKit.LV_FP1 + 7 * DAY);
        long now = SportTestKit.LV_FP1 - 2 * DAY;
        HomeModel h = new HomeModel();
        h.now = now;
        h.sports.add(SportModel.of(Arrays.asList(qatar, lv), SportEvent.F1, SportPrefs.none(), now));
        MatchdayModel.Row r = MatchdayModel.rows(h).get(0);
        assertEquals(MatchdayModel.COMING, r.items.size());
        assertEquals("FP1", r.items.get(0).ev.session().kind);
        assertEquals("FP2", r.items.get(1).ev.session().kind);
        MatchdayModel.Item hero = r.items.get(r.start);
        assertEquals("RACE", hero.ev.session().kind);
        assertEquals(lv, hero.ev.e);
        assertTrue(hero.next);
        for (int i = 0; i < 5; i++) assertEquals(lv, r.items.get(i).ev.e);
        for (int i = 5; i < r.items.size(); i++) assertEquals(qatar, r.items.get(i).ev.e);
    }

    @Test public void theHeroEyebrowBillsTheBoutOnStage() {
        // QAS-2: live, the gold billing is the bout in the cage's, and gold only for a title fight
        SportEvent u = SportTestKit.ufc334();
        SportEvent.Session main = u.session("main");
        assertEquals("Heavyweight title", SportPanel.goldBilling(u, main, SportPrefs.none()));  // before: the main event
        u.state = "in";
        SportEvent.Bout co = null;
        for (SportEvent.Bout b : main.bouts) if (b.order == 2) co = b;
        co.state = "in";
        assertEquals("", SportPanel.goldBilling(u, main, SportPrefs.none()));  // a non-title bout in the cage: no gold
        co.state = "post";
        u.mainEvent().state = "in";
        assertEquals("Heavyweight title", SportPanel.goldBilling(u, main, SportPrefs.none()));
    }

    @Test public void hiddenInTheAppTheSpokenWordsHideTheResultsToo() {
        // QAS-3: should HIDE_IN_APP be turned on, what TalkBack hears follows it (the rail, the hero, the rows)
        SportEvent lv = lasVegas(5);
        long now = SportTestKit.LV_RACE + 3 * H;
        SportEvent.Session race = SportModel.race(lv);
        assertTrue(SportText.speak(lv, race, now, false).contains("wins"));
        assertFalse(SportText.speak(lv, race, now, true).contains("wins"));
        assertFalse(SportText.speak(lv, race, now, true).contains("NOR"));
        SportModel.SportData d = SportModel.of(Collections.singletonList(lv), SportEvent.F1, SportPrefs.none(), now, true, "");
        assertTrue(d.hidden);
        assertEquals(SportText.t("final"), d.railLine);
        SportEvent u = SportTestKit.ufc334();
        SportEvent.Session prelims = u.session("prelims1");
        prelims.state = "post";
        for (SportEvent.Bout b : prelims.bouts) { b.state = "post"; b.outcome = 1; b.method = "KO/TKO"; }
        assertFalse(SportText.speak(u, prelims, SportTestKit.U334_MAIN, true).contains("wins"));
    }

    @Test public void words() {
        assertEquals("Women's Bantamweight", SportText.weight("Women's Bantamweight"));
        assertEquals("Heavyweight title", SportText.billing(SportTestKit.ufc334().mainEvent()));
        assertEquals("Gane VS Hokit", SportText.bout(SportTestKit.ufc334().mainEvent()));
        assertEquals("UFC 334", SportText.ufcEvent(SportTestKit.ufc334()));
        assertEquals("50 laps · 6.116 km", SportText.facts(SportTestKit.lasVegas()));
        SportEvent.Bout b = new SportEvent.Bout();
        b.method = "Decision - Split"; b.outcome = 1; b.state = "post";
        assertEquals("Decision · split", SportText.methodLine(b));
        assertEquals("DEC", SportText.methodShort(b));
        I18n.use("el");
        assertEquals("Βάρος πετεινού γυναικών", SportText.weight("Women's Bantamweight"));
        assertEquals("Ημιμεσαίων βαρών", SportText.weight("Welterweight"));
        assertEquals("Τίτλος βαρέων βαρών", SportText.billing(SportTestKit.ufc334().mainEvent()));
        assertEquals("50 γύροι · 6,116 χλμ.", SportText.facts(SportTestKit.lasVegas()));
        SportEvent.Session q = new SportEvent.Session();
        q.kind = "QUAL";
        assertEquals("Κατατακτήριες", SportText.session(q));
        assertEquals("Διχασμένη απόφαση", SportText.methodLine(b));
        assertEquals("ΑΠΟΦΑΣΗ", SportText.methodShort(b));
        b.method = "Decision - Majority";
        assertEquals("Απόφαση κατά πλειοψηφία", SportText.methodLine(b));
        assertEquals("Μεσαίων βαρών", SportText.billing(nonTitle("Middleweight")));
        I18n.use("en");
        assertEquals("Decision · majority", SportText.methodLine(b));
    }

    @Test public void hideScoresInTheAppIsOneSwitch() {
        assertEquals(SportText.HIDE_IN_APP, SportText.hidden(false));
        assertFalse(SportText.hidden(true));
    }

    static SportEvent.Bout nonTitle(String weightClass) {
        SportEvent.Bout b = SportTestKit.bout("9", 3, null, null);
        b.title = false; b.weightClass = weightClass;
        return b;
    }
}
