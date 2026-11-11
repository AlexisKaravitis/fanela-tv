package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** The match screen's facts: last five in the competition, the other leg, the button and the sentences under the cell. */
public class MatchFactsTest {
    @After public void english() { I18n.use("en"); }

    static long at(int day, int hh, int mm) { return CardModelTest.at(day, hh, mm); }

    static Match m(String key, String comp, long start, String home, String away, boolean clubHome) {
        Match m = MatchdayModelTest.m("435", key, "BC", "pre", start, home, away, clubHome);
        m.comp = comp; m.league = comp;
        return m;
    }

    static List<Match> season() {
        List<Match> all = new ArrayList<>();
        all.add(MatchdayModelTest.res(m("baskonia", "euroleague", at(-48, 21, 0), "Baskonia", "Olympiacos", false), "89", "106"));
        all.add(MatchdayModelTest.res(m("barca", "euroleague", at(-26, 21, 0), "FC Barcelona", "Olympiacos", false), "100", "105"));
        all.add(MatchdayModelTest.res(m("fener", "euroleague", at(-19, 21, 0), "Olympiacos", "Fenerbahce", true), "61", "64"));
        all.add(MatchdayModelTest.res(m("gbl", "gbl", at(-17, 18, 0), "Olympiacos", "Kolossos", true), "90", "70"));
        all.add(MatchdayModelTest.res(m("besiktas", "euroleague", at(-14, 21, 0), "Besiktas", "Olympiacos", false), "73", "81"));
        all.add(MatchdayModelTest.res(m("milan", "euroleague", at(-12, 21, 0), "Olympiacos", "Milan", true), "84", "78"));
        all.add(MatchdayModelTest.res(m("zvezda", "euroleague", at(-5, 21, 15), "Olympiacos", "Crvena Zvezda", true), "57", "62"));
        all.add(m("dubai", "euroleague", at(1, 21, 15), "Olympiacos", "Dubai", true));
        all.add(m("real", "euroleague", at(9, 21, 15), "Olympiacos", "Real Madrid", true));
        all.add(m("real2", "euroleague", at(57, 21, 45), "Real Madrid", "Olympiacos", false));
        all.add(m("dubai2", "euroleague", at(114, 18, 0), "Dubai BC", "Olympiacos", false));
        all.get(all.size() - 1).homeCrest = "https://x/dubai.png";
        all.get(7).awayCrest = "https://x/dubai.png";
        Match football = m("dubai-fc", "gre.1", at(30, 18, 0), "Dubai", "Olympiacos", false);
        football.team = "FC";
        all.add(football);
        return all;
    }

    static Match find(List<Match> all, String key) {
        for (Match m : all) if (m.key.equals("435|x:" + key)) return m;
        return null;
    }

    @Test public void lastFiveAreTheCompetitionsFiveBeforeTheMatchOldestFirst() {
        List<Match> all = season();
        MatchFacts.Recent r = MatchFacts.recent(all, find(all, "dubai"));
        assertTrue(r.sameCompetition);
        assertEquals(5, r.cells.size());
        assertEquals("435|x:barca", r.cells.get(0).m.key);
        assertEquals("435|x:zvezda", r.cells.get(4).m.key);
        assertEquals("W", r.cells.get(0).letter());
        assertEquals("L", r.cells.get(1).letter());
        assertEquals("the Basket League game is not EuroLeague form", -1, keysOf(r).indexOf("435|x:gbl"));
        I18n.use("el");
        assertEquals("Ν", r.cells.get(0).letter());
        assertEquals("Η", r.cells.get(4).letter());
    }

    static List<String> keysOf(MatchFacts.Recent r) {
        List<String> out = new ArrayList<>();
        for (MatchFacts.Form f : r.cells) out.add(f.m.key);
        return out;
    }

    @Test public void aFirstGameInACompetitionShowsTheSportsRecentResults() {
        List<Match> all = season();
        Match cup = m("cup", "gre.cup", at(2, 19, 0), "Olympiacos", "Kolossos", true);
        all.add(cup);
        MatchFacts.Recent r = MatchFacts.recent(all, cup);
        assertFalse(r.sameCompetition);
        assertEquals("435|x:zvezda", r.cells.get(4).m.key);
        assertEquals("435|x:gbl", r.cells.get(1).m.key);
    }

    @Test public void theReturnLegIsTheSameTieAtTheOtherGround() {
        List<Match> all = season();
        Match leg = MatchFacts.otherLeg(all, find(all, "real"));
        assertEquals("435|x:real2", leg.key);
        assertEquals("Return leg", MatchFacts.legLabel(leg, find(all, "real")));
        assertTrue(MatchFacts.legLine(leg).endsWith(" · 21:45 · at Real Madrid"));
        assertEquals("a crest names the same club when the feeds spell it differently", "435|x:dubai2", MatchFacts.otherLeg(all, find(all, "dubai")).key);
        Match first = MatchFacts.otherLeg(all, find(all, "real2"));
        assertEquals("435|x:real", first.key);
        assertEquals("First leg", MatchFacts.legLabel(first, find(all, "real2")));
        assertNull("no other leg in the competition", MatchFacts.otherLeg(all, find(all, "barca")));
        I18n.use("el");
        assertTrue(MatchFacts.legLine(leg).endsWith(" · 21:45 · εκτός έδρας"));
    }

    @Test public void theFormGuideIsSpokenInWordsWithItsOpponents() {
        List<Match> all = season();
        MatchFacts.Recent r = MatchFacts.recent(all, all.get(7));
        String s = MatchFacts.speak(r.cells);
        assertEquals("FC Barcelona, Won 105–100; Fenerbahce, Lost 61–64; Besiktas, Won 81–73; Milan, Won 84–78; Crvena Zvezda, Lost 57–62", s);
        assertFalse("no trailing separator", s.endsWith(";") || s.endsWith(", "));
        I18n.use("el");
        assertTrue(MatchFacts.speak(r.cells).contains("Νίκη 105–100"));
        assertEquals("", MatchFacts.speak(new ArrayList<MatchFacts.Form>()));
    }

    @Test public void theButtonOpensTheAppGetsItOrCloses() {
        Match m = m("dubai", "euroleague", at(1, 21, 15), "Olympiacos", "Dubai", true);
        m.channelApp = Cards.MAGENTA; m.channelSure = true; m.channel = "Novasports Prime"; m.channelNo = 318;
        assertEquals(MatchFacts.Action.OPEN, MatchFacts.action(m, true));
        assertEquals(MatchFacts.Action.GET, MatchFacts.action(m, false));
        assertEquals("Open Magenta TV", MatchFacts.buttonLabel(MatchFacts.Action.OPEN, m));
        assertEquals("Get Magenta TV", MatchFacts.buttonLabel(MatchFacts.Action.GET, m));
        assertEquals("Novasports Prime · Magenta TV", MatchFacts.channelLine(m));
        List<String> why = MatchFacts.why(m, true);
        assertEquals("From the Magenta TV guide for this day.", why.get(0));
        assertEquals("Magenta TV is installed on this TV.", why.get(1));
        m.channelSure = false;
        why = MatchFacts.why(m, false);
        assertTrue(why.get(0).startsWith("Expected channel, based on the home team's TV rights."));
        assertEquals("Magenta TV isn't installed on this TV.", why.get(1));
        Match abroad = m.copy();
        abroad.listedBy = "table"; abroad.channel = "Sky Sports · TNT Sports";
        assertEquals("abroad: no app to offer", MatchFacts.Action.CLOSE, MatchFacts.action(abroad, true));
        assertEquals("Sky Sports · TNT Sports", MatchFacts.channelLine(abroad));
        assertEquals(1, MatchFacts.why(abroad, true).size());
        Match played = m.copy();
        played.state = "post"; played.hs = "80"; played.as = "70";
        assertEquals(MatchFacts.Action.CLOSE, MatchFacts.action(played, true));
        assertTrue(MatchFacts.why(played, true).isEmpty());
    }
}
