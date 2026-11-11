package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** What the home screen shows per club: the hero, the latest results, the fixtures and the rail's line. */
public class HomeModelTest {
    @After public void english() { I18n.use("en"); }

    static final String[] BOTH = {"FC", "BC"};

    static long at(int day, int hh, int mm) { return CardModelTest.at(day, hh, mm); }

    static Match m(String key, String team, String state, long start) {
        Match m = StoreTest.match(key, state, null, null);
        m.team = team; m.start = start; m.channelKnown = true; m.channelSure = false; m.channel = "Novasports";
        return m;
    }

    static Match result(String key, String team, long start, String hs, String as) {
        Match m = m(key, team, "post", start);
        m.hs = hs; m.as = as;
        return m;
    }

    @Test public void theHeroIsTheNextMatchOfEitherSport() {
        long now = at(0, 18, 30);
        List<Match> all = new ArrayList<>();
        all.add(m("fc", "FC", "pre", at(4, 19, 0)));
        all.add(m("bc", "BC", "pre", at(1, 21, 15)));
        all.add(result("old", "FC", at(-3, 21, 0), "1", "1"));
        Match h = HomeModel.hero(all, "435", BOTH, now);
        assertEquals("435|espn:bc", h.key);
        assertEquals(HomeModel.Kind.NEXT, HomeModel.kind(h));
        assertEquals("only football shown: the football match", "435|espn:fc", HomeModel.hero(all, "435", new String[]{"FC"}, now).key);
    }

    @Test public void aLiveMatchComesFirstThenAFreshResult() {
        long now = at(0, 21, 0);
        List<Match> all = new ArrayList<>();
        all.add(m("next", "FC", "pre", at(1, 19, 0)));
        all.add(result("fresh", "BC", now - 3 * 3600000L, "79", "56"));  // full time an hour ago
        assertEquals("435|espn:fresh", HomeModel.hero(all, "435", BOTH, now).key);
        Match live = m("live", "FC", "in", now - 30 * 60000L);
        live.hs = "1"; live.as = "0"; live.detail = "30'";
        all.add(live);
        Match h = HomeModel.hero(all, "435", BOTH, now);
        assertEquals("435|espn:live", h.key);
        assertEquals(HomeModel.Kind.LIVE, HomeModel.kind(h));
        // a result stops being the hero 6 hours after full time
        List<Match> later = new ArrayList<>();
        later.add(m("next", "FC", "pre", at(1, 19, 0)));
        later.add(result("old", "BC", now - 9 * 3600000L, "79", "56"));
        assertEquals("435|espn:next", HomeModel.hero(later, "435", BOTH, now).key);
    }

    @Test public void noMatchesMeansNoHero() {
        assertNull(HomeModel.hero(new ArrayList<Match>(), "435", BOTH, at(0, 12, 0)));
        assertEquals(HomeModel.Kind.NONE, HomeModel.kind(null));
    }

    @Test public void latestIsOneResultPerSportNewestFirst() {
        List<Match> all = new ArrayList<>();
        all.add(result("fc1", "FC", at(-3, 21, 0), "1", "1"));
        all.add(result("fc2", "FC", at(-10, 21, 0), "2", "0"));
        all.add(result("bc1", "BC", at(-5, 21, 15), "57", "62"));
        List<Match> l = HomeModel.latest(all, "435", BOTH, null);
        assertEquals(2, l.size());
        assertEquals("435|espn:fc1", l.get(0).key);
        assertEquals("435|espn:bc1", l.get(1).key);
        List<Match> one = HomeModel.latest(all, "435", new String[]{"FC"}, null);
        assertEquals("one sport: its last two", "435|espn:fc2", one.get(1).key);
        assertEquals("the hero's result is not repeated", "435|espn:fc2", HomeModel.latest(all, "435", new String[]{"FC"}, all.get(0)).get(0).key);
    }

    @Test public void fixturesFollowTheHeroInDateOrderWithPostponements() {
        long now = at(0, 18, 30);
        List<Match> all = new ArrayList<>();
        Match hero = m("h", "BC", "pre", at(1, 21, 15));
        all.add(hero);
        all.add(m("b", "FC", "pre", at(11, 19, 0)));
        all.add(m("a", "BC", "pre", at(4, 17, 30)));
        Match off = m("off", "FC", "off", at(6, 19, 0));
        off.detail = "POSTPONED";
        all.add(off);
        all.add(result("r", "FC", at(-3, 21, 0), "1", "1"));
        Match other = m("x", "FC", "pre", at(2, 19, 0));
        other.club = "605";
        all.add(other);
        List<Match> f = HomeModel.fixtures(all, "435", BOTH, hero, now);
        assertEquals(3, f.size());
        assertEquals("435|espn:a", f.get(0).key);
        assertEquals("435|espn:off", f.get(1).key);
        assertEquals("435|espn:b", f.get(2).key);
    }

    @Test public void theRailSaysLiveThenAFreshResultThenTheNextKickOff() {
        long now = at(0, 18, 30);
        HomeModel.ClubData d = new HomeModel.ClubData();
        d.club = new Teams.Club();
        d.club.id = "605";
        List<Match> all = new ArrayList<>();
        Match next = m("n", "BC", "pre", at(3, 17, 30));
        next.club = "605";
        all.add(next);
        HomeModel.rail(d, all, BOTH, now);
        assertTrue(d.rail, d.rail.endsWith(" 17:30"));
        assertFalse(d.railLive);
        Match won = result("w", "BC", now - 20 * 3600000L, "56", "79");
        won.club = "605"; won.clubIsHome = false;
        all.add(won);
        HomeModel.rail(d, all, BOTH, now);
        assertEquals("the followed club's score first", "Won 79–56", d.rail);
        Match live = m("l", "FC", "in", now - 3600000L);
        live.club = "605"; live.hs = "2"; live.as = "1";
        all.add(live);
        HomeModel.rail(d, all, BOTH, now);
        assertEquals("LIVE 2–1", d.rail);
        assertTrue(d.railLive);
    }

    @Test public void liveWordsForBasketballAndFootball() {
        Match b = m("b", "BC", "in", 0);
        b.hs = "61"; b.as = "58"; b.detail = "Q3 05:12"; b.home = "Olympiacos"; b.away = "Dubai";
        assertEquals("Q3", HomeModel.phase(b));
        assertEquals("5:12 left", HomeModel.clock(b));
        assertEquals("Olympiacos lead by 3", HomeModel.lead(b));
        assertEquals("Olympiacos lead by 3 · Q3 · 5:12 left", HomeModel.liveLine(b));
        Match f = m("f", "FC", "in", 0);
        f.hs = "0"; f.as = "2"; f.detail = "67'"; f.home = "Olympiacos"; f.away = "Iraklis";
        assertEquals("67′", HomeModel.phase(f));
        assertEquals("67′", HomeModel.clock(f));
        assertEquals("Iraklis lead by 2 · 67′", HomeModel.liveLine(f));
        f.hs = "1"; f.as = "1"; f.detail = "HT";
        assertEquals("All square · Half-time", HomeModel.liveLine(f));
        I18n.use("el");
        assertEquals("5:12 για το τέλος", HomeModel.clock(b));
    }

    @Test public void theLiveCellSaysThePhaseInAShortWordInBothLanguages() {
        Match b = m("b", "BC", "in", 0);
        b.hs = "70"; b.as = "70"; b.home = "Olympiacos"; b.away = "Dubai";
        String[][] en = {{"OT 2:10", "OT"}, {"OT2", "OT2"}, {"HT", "HT"}, {"BREAK", "BREAK"}, {"ET_HT", "ET HT"},
            {"SHOOTOUT", "PENS"}, {"END_90", "90′"}, {"END_120", "120′"}, {"SUSPENDED", "SUSP."}, {"AWAITING", ""}};
        for (String[] c : en) { b.detail = c[0]; assertEquals(c[0], c[1], HomeModel.phase(b)); }
        b.detail = "BREAK";
        assertEquals("a break: its words once, no phase beside them", "All square · Break", HomeModel.liveLine(b));
        b.detail = "OT 2:10";
        assertEquals("All square · OT · 2:10 left", HomeModel.liveLine(b));
        assertEquals("OT · 2:10", MatchdayModel.liveClock(b));
        I18n.use("el");
        String[][] el = {{"OT 2:10", "ΠΑΡ."}, {"OT2 1:05", "ΠΑΡ.2"}, {"HT", "ΗΜ."}, {"BREAK", "ΔΙΑΛ."}, {"ET_HT", "ΔΙΑΛ."},
            {"SHOOTOUT", "ΠΕΝ."}, {"SUSPENDED", "ΔΙΑΚ."}, {"Q3 5:12", "Q3"}};
        for (String[] c : el) { b.detail = c[0]; assertEquals(c[0], c[1], HomeModel.phase(b)); }
        b.detail = "OT 2:10";
        assertEquals("the stage's clock as the cards write overtime", "ΠΑΡ. · 2:10", MatchdayModel.liveClock(b));
        b.detail = "HT";
        assertEquals("ΗΜ.", MatchdayModel.liveClock(b));
        assertEquals("Ισόπαλο · Ημίχρονο", HomeModel.liveLine(b));
    }

    @Test public void theRailsNextKickOffIsShort() {
        long now = at(0, 18, 30);
        Match m = m("n", "BC", "pre", at(0, 21, 15));
        assertEquals("Today 21:15", AppText.railNext(now, m));
        m.start = at(1, 17, 30);
        assertEquals("tomorrow: the weekday ('Tomorrow 17:30' is wider than the rail)", AppText.weekday(m.start) + " 17:30", AppText.railNext(now, m));
        m.start = at(3, 17, 30);
        assertEquals(AppText.weekday(m.start) + " 17:30", AppText.railNext(now, m));
        m.start = at(9, 17, 30);
        assertEquals("a week or more away: the date alone", AppText.shortDate(m.start), AppText.railNext(now, m));
        m.start = at(1, 17, 30);
        m.tbd = true;
        assertEquals(AppText.weekday(m.start), AppText.railNext(now, m));
        I18n.use("el");
        m.tbd = false;
        assertEquals("Αύριο 17:30", AppText.railNext(now, m));
    }

    @Test public void anUnknownTimeIsTbaInTheSlotsAndWordsElsewhere() {
        assertEquals("TBA", AppText.t("tba"));
        assertEquals("Time TBA", AppText.t("time_tba"));
        I18n.use("el");
        assertEquals("the compact slots read as the cards do", "TBA", AppText.t("tba"));
        assertEquals("Θα οριστεί", AppText.t("time_tba"));
    }

    @Test public void eyebrowsNameTheSportOnlyForClubsWithBoth() {
        Match b = m("b", "BC", "pre", 0);
        b.comp = "euroleague";
        assertEquals("NEXT · EUROLEAGUE · BASKETBALL", HomeModel.eyebrow(HomeModel.Kind.NEXT, b, true));
        assertEquals("NEXT · EUROLEAGUE", HomeModel.eyebrow(HomeModel.Kind.NEXT, b, false));
        I18n.use("el");
        assertEquals("ΕΠΟΜΕΝΟ · EUROLEAGUE · ΜΠΑΣΚΕΤ", HomeModel.eyebrow(HomeModel.Kind.NEXT, b, true));
    }

    @Test public void relativeTimesReadAsSpoken() {
        long now = at(0, 18, 30);
        assertEquals("Tomorrow", AppText.relative(now, at(1, 21, 15)));
        assertEquals("In 9 days", AppText.relative(now, at(9, 21, 15)));
        assertEquals("Today · in 2 h 15 min", AppText.relative(now, at(0, 20, 45)));
        assertEquals("Today · in 40 min", AppText.relative(now, at(0, 19, 10)));
        assertEquals("3 h", AppText.duration(3 * 3600000L));
        I18n.use("el");
        assertEquals("Αύριο", AppText.relative(now, at(1, 21, 15)));
        assertEquals("Σε 9 ημέρες", AppText.relative(now, at(9, 21, 15)));
    }
}
