package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** Matchday's rows: what row 1 and each club's row hold, where each row opens, and the stage's words. */
public class MatchdayModelTest {
    @After public void english() { I18n.use("en"); }

    static long at(int day, int hh, int mm) { return CardModelTest.at(day, hh, mm); }

    static Match m(String club, String key, String team, String state, long start, String home, String away, boolean clubHome) {
        Match m = StoreTest.match(key, state, null, null);
        m.club = club; m.key = club + "|x:" + key; m.team = team; m.start = start; m.home = home; m.away = away; m.clubIsHome = clubHome;
        m.channelKnown = true; m.channel = "Novasports"; m.comp = "euroleague"; m.league = "euroleague";
        return m;
    }

    static Match res(Match m, String hs, String as) { m.state = "post"; m.hs = hs; m.as = as; return m; }

    static Teams.Club club(String id, String name) {
        Teams.Club k = new Teams.Club();
        k.id = id; k.name = name; k.gr = name;
        return k;
    }

    /** A HomeModel as HomeActivity builds it: hero, fixtures, all, teams. */
    static HomeModel model(long now, List<Match> all, Teams.Club... clubs) {
        HomeModel h = new HomeModel();
        h.now = now;
        h.all = all;
        h.favourite = clubs[0].id;
        String[] both = {"FC", "BC"};
        for (Teams.Club k : clubs) {
            HomeModel.ClubData d = new HomeModel.ClubData();
            d.club = k;
            d.bothSports = true;
            d.hero = HomeModel.hero(all, k.id, both, now);
            d.kind = HomeModel.kind(d.hero);
            d.latest.addAll(HomeModel.latest(all, k.id, both, d.hero));
            d.fixtures.addAll(HomeModel.fixtures(all, k.id, both, d.hero, now));
            h.teams.put(k.id, both);
            h.clubs.add(d);
        }
        return h;
    }

    /** The spec's scenario: Olympiacos (favourite) and PAOK, Wednesday 18:30. */
    static List<Match> scenario(boolean live) {
        List<Match> all = new ArrayList<>();
        Match dubai = m("435", "dubai", "BC", "pre", at(1, 21, 15), "Olympiacos", "Dubai", true);
        if (live) { dubai.state = "in"; dubai.hs = "61"; dubai.as = "58"; dubai.detail = "Q3 5:12"; }
        all.add(dubai);
        all.add(m("435", "aris", "BC", "pre", at(4, 17, 30), "Aris", "Olympiacos", false));
        all.add(m("435", "maccabi", "BC", "pre", at(6, 19, 0), "Maccabi", "Olympiacos", false));
        all.add(m("435", "real", "BC", "pre", at(9, 21, 15), "Olympiacos", "Real Madrid", true));
        all.add(res(m("435", "paok", "FC", "pre", at(-3, 21, 0), "Olympiacos", "PAOK", true), "1", "1"));
        all.add(res(m("435", "zvezda", "BC", "pre", at(-5, 21, 15), "Olympiacos", "Crvena Zvezda", true), "57", "62"));
        all.add(res(m("435", "milan", "BC", "pre", at(-12, 21, 15), "Olympiacos", "Milan", true), "84", "78"));
        all.add(res(m("435", "besiktas", "BC", "pre", at(-14, 21, 15), "Besiktas", "Olympiacos", false), "73", "81"));
        all.add(res(m("605", "oly", "FC", "pre", at(-3, 21, 0), "Olympiacos", "PAOK", false), "1", "1"));
        all.add(res(m("605", "balkan", "BC", "pre", at(-1, 15, 0), "Balkan", "PAOK", false), "56", "79"));
        all.add(m("605", "karditsa", "BC", "pre", at(3, 17, 30), "Karditsa", "PAOK", false));
        all.add(m("605", "manresa", "BC", "pre", at(7, 20, 30), "Manresa", "PAOK", false));
        return all;
    }

    static List<String> keys(MatchdayModel.Row r) {
        List<String> out = new ArrayList<>();
        for (MatchdayModel.Item it : r.items) out.add(it.key());
        return out;
    }

    @Test public void rowOneHoldsEveryClubsNextThenTheLatestResultsThenWhatFollows() {
        long now = at(0, 18, 30);
        HomeModel h = model(now, scenario(false), club("435", "Olympiacos"), club("605", "PAOK"));
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertEquals("row 1 and one row per club", 3, rows.size());
        MatchdayModel.Row first = rows.get(0);
        assertEquals(MatchdayModel.FIRST, first.id);
        List<String> k = keys(first);
        assertEquals("435|435|x:dubai", k.get(0));
        assertEquals("605|605|x:karditsa", k.get(1));
        assertEquals("newest result first", "605|605|x:balkan", k.get(2));
        assertEquals("435|435|x:paok", k.get(3));
        assertEquals("filled to five with the soonest after", "435|435|x:aris", k.get(4));
        assertEquals(5, k.size());
        assertEquals(0, first.start);
        Match derby = h.all.get(4), theirs = h.all.get(8);
        assertTrue("the same derby, seen from both clubs", MatchdayModel.same(derby, theirs));
        assertFalse(MatchdayModel.same(derby, h.all.get(9)));
    }

    @Test public void aLiveCardLeadsRowOneAndIsWhereFocusLands() {
        long now = at(1, 22, 20);
        HomeModel h = model(now, scenario(true), club("435", "Olympiacos"), club("605", "PAOK"));
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertEquals("435|435|x:dubai", rows.get(0).items.get(0).key());
        assertTrue(rows.get(0).items.get(0).live());
        assertArrayEquals(new int[]{0, 0}, MatchdayModel.launch(rows));
        List<String> k = keys(rows.get(0));
        assertEquals("a club playing now: its live card stands for its next", "605|605|x:karditsa", k.get(1));
        assertEquals("605|605|x:balkan", k.get(2));
        assertEquals("a result of four days ago is still this week's", "435|435|x:paok", k.get(3));
        assertEquals("the live card stages; nothing says LIVE twice", "", MatchdayModel.lead(rows.get(0).items.get(0)));
        assertEquals("Q3 · 5:12", MatchdayModel.liveClock(rows.get(0).items.get(0).m));
    }

    @Test public void aClubRowOpensOnItsNextMatchWithItsResultsToTheLeft() {
        long now = at(0, 18, 30);
        HomeModel h = model(now, scenario(false), club("435", "Olympiacos"), club("605", "PAOK"));
        MatchdayModel.Row oly = MatchdayModel.rows(h).get(1);
        assertEquals("435", oly.id);
        List<String> k = keys(oly);
        assertEquals("three results, oldest first", "435|435|x:milan", k.get(0));
        assertEquals("435|435|x:zvezda", k.get(1));
        assertEquals("435|435|x:paok", k.get(2));
        assertEquals("then the next match", "435|435|x:dubai", k.get(3));
        assertEquals(3, oly.start);
        assertTrue(oly.items.get(3).next);
        assertFalse(oly.items.get(4).next);
        assertEquals("Up next", MatchdayModel.lead(oly.items.get(3)));
        assertEquals("Later", MatchdayModel.lead(oly.items.get(4)));
        assertEquals("Full time", MatchdayModel.lead(oly.items.get(2)));
    }

    @Test public void aClubWithoutFixturesOpensOnItsLastResultAndALoadingClubGreysRowOne() {
        long now = at(0, 18, 30);
        List<Match> all = new ArrayList<>();
        all.add(res(m("887", "r1", "FC", "pre", at(-20, 19, 0), "AEK", "Aris", true), "2", "0"));
        all.add(res(m("887", "r2", "FC", "pre", at(-6, 19, 0), "Volos", "AEK", false), "1", "3"));
        HomeModel h = model(now, all, club("887", "AEK"), club("443", "Panathinaikos"));
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertEquals(1, rows.get(1).start);
        assertTrue("nothing at all: an empty row", rows.get(2).items.isEmpty());
        assertEquals("a week-old result is still in row 1, an older one not", 1, rows.get(0).items.size());
        h.clubs.get(1).kind = HomeModel.Kind.LOADING;
        rows = MatchdayModel.rows(h);
        assertTrue(rows.get(0).loading);
        assertTrue(rows.get(2).loading);
        assertFalse(rows.get(1).loading);
        assertArrayEquals("focus lands on the first row with cards", new int[]{1, 1}, MatchdayModel.launch(rows));
    }

    @Test public void focusFindsTheSameCardAfterARefreshOrTheRowsOpeningCard() {
        long now = at(0, 18, 30);
        List<MatchdayModel.Row> rows = MatchdayModel.rows(model(now, scenario(false), club("435", "Olympiacos"), club("605", "PAOK")));
        assertArrayEquals(new int[]{1, 5}, MatchdayModel.find(rows, "435", "435|435|x:maccabi"));
        assertArrayEquals("gone from its row: the row's opening card", new int[]{2, 2}, MatchdayModel.find(rows, "605", "605|605|x:gone"));
        assertArrayEquals("the row is gone: the item elsewhere", new int[]{0, 0}, MatchdayModel.find(rows, "999", "435|435|x:dubai"));
        assertArrayEquals(new int[]{0, 0}, MatchdayModel.find(rows, "999", "nothing"));
    }

    @Test public void theRelativeLineCountsHoursUntilTomorrowsMatch() {
        long now = at(0, 18, 30);
        assertEquals("Tomorrow · in 26 h", AppText.relativeHours(now, at(1, 21, 15)));
        assertEquals("In 9 days", AppText.relativeHours(now, at(9, 21, 15)));
        assertEquals("Today · in 2 h 15 min", AppText.relativeHours(now, at(0, 20, 45)));
        I18n.use("el");
        assertEquals("Αύριο · σε 26 ώρες", AppText.relativeHours(now, at(1, 21, 15)));
    }

    /** Followed clubs whose data never arrived: nothing to show, and their loads failed. */
    static HomeModel failed(HomeModel.Kind second) {
        long now = at(1, 18, 30);
        HomeModel h = model(now, new ArrayList<Match>(), club("435", "Olympiacos"), club("605", "PAOK"));
        h.clubs.get(0).kind = HomeModel.Kind.FAILED;
        h.clubs.get(1).kind = second;
        return h;
    }

    @Test public void row1SaysTheLoadFailedWhenNoFollowedClubLoaded() {
        HomeModel h = failed(HomeModel.Kind.FAILED);
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertTrue(rows.get(0).items.isEmpty());
        assertEquals("not 'no fixtures scheduled': nothing is known", "load_failed", MatchdayLayout.emptyWord(rows.get(0), h));
        assertEquals("load_failed", MatchdayLayout.emptyWord(rows.get(1), h));
    }

    @Test public void row1SaysNoFixturesWhenAClubLoadedWithNothingScheduled() {
        HomeModel h = failed(HomeModel.Kind.NONE);
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertEquals("no_fixtures", MatchdayLayout.emptyWord(rows.get(0), h));
        assertEquals("the failed club's own row still says so", "load_failed", MatchdayLayout.emptyWord(rows.get(1), h));
        assertEquals("no_fixtures", MatchdayLayout.emptyWord(rows.get(2), h));
    }

    @Test public void aSlimRowKeepsTheFullStageAndCardRowsBelowRow1TakeTheRibbon() {
        HomeModel h = failed(HomeModel.Kind.NONE);
        List<MatchdayModel.Row> rows = MatchdayModel.rows(h);
        assertTrue(MatchdayLayout.slim(rows.get(1)));
        assertFalse("row 1: the full stage", MatchdayLayout.ribbon(0, false));
        assertTrue("a row of cards below row 1: the ribbon", MatchdayLayout.ribbon(1, false));
        assertFalse("a slim row below row 1: the full stage, showing that club", MatchdayLayout.ribbon(2, MatchdayLayout.slim(rows.get(2))));
    }

    @Test public void anEmptyRowsLineTellsTalkBackWhoseRowItIs() {
        assertEquals("AEK Athens. Couldn't load fixtures. Check the TV's internet connection. Fanela will try again shortly.",
            MatchdayLayout.spoken("AEK Athens", I18n.t("load_failed")));
        assertEquals("AEK Athens. No fixtures scheduled right now.", MatchdayLayout.spoken("AEK Athens", I18n.t("no_fixtures")));
        assertEquals("No fixtures scheduled right now.", MatchdayLayout.spoken("", I18n.t("no_fixtures")));
    }

    @Test public void theFirstFrameWearsTheLaunchCardsClubFromLastTime() {
        HomeModel h = model(at(0, 18, 30), scenario(false), club("435", "Olympiacos"), club("605", "PAOK"));
        assertEquals("the launch card's club, saved last time", "605", MatchdayLayout.firstClub("605", h));
        assertEquals("no longer followed: the favourite", "435", MatchdayLayout.firstClub("999", h));
        assertEquals("the first start: the favourite", "435", MatchdayLayout.firstClub(null, h));
        h.favourite = "999";
        assertEquals("no favourite among them: the first club", "435", MatchdayLayout.firstClub(null, h));
    }

    @Test public void aLiveFirstStageIsBelievedForThreeHours() {
        long t = at(0, 18, 30), hour = 3600000L;
        assertTrue(MatchdayLayout.firstLive(t, t + 2 * hour));
        assertFalse("over by now: the club's colour", MatchdayLayout.firstLive(t, t + 3 * hour));
        assertFalse("not live", MatchdayLayout.firstLive(0, t));
        assertFalse("a clock set back", MatchdayLayout.firstLive(t + hour, t));
    }
}
