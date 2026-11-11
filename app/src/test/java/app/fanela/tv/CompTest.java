package app.fanela.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which competition a basketball game gets, from TheSportsDB's league names and Sport24's tags. */
public class CompTest {
    @Test public void greekGuideKeepsGreekGamesAsBefore() {
        assertEquals("gbl", Sources.bcCompGreekGuide("Stoiximan Basket League 2026-27"));
        assertEquals("gbl", Sources.bcCompGreekGuide("Μπάσκετ Α1"));
        assertEquals("gbl.cup", Sources.bcCompGreekGuide("Κύπελλο Ελλάδας μπάσκετ cup"));
        assertEquals("euroleague", Sources.bcCompGreekGuide("EuroLeague 2026/27"));
        assertEquals("eurocup", Sources.bcCompGreekGuide("EuroCup 2026/27"));
        assertEquals("bcl", Sources.bcCompGreekGuide("Basketball Champions League"));
    }

    @Test public void greekGuideNamesForeignLeagues() {
        assertEquals("esp.acb", Sources.bcCompGreekGuide("ACB Liga Endesa 2026-27"));
        assertEquals("ita.lba", Sources.bcCompGreekGuide("Lega Basket Serie A"));
        assertEquals("aba", Sources.bcCompGreekGuide("ABA League"));
    }

    @Test public void sportsDbLeagues() {
        assertEquals("gbl", Sources.bcComp("Greek Basket League"));
        assertEquals("esp.acb", Sources.bcComp("Spanish Liga ACB"));
        assertEquals("tur.bsl", Sources.bcComp("Turkish Basketball Super League"));
        assertEquals("ger.bbl", Sources.bcComp("German BBL"));
        assertEquals("fra.proa", Sources.bcComp("French LNB Pro A"));
        assertEquals("Israeli Basketball Premier League", Sources.bcComp("Israeli Basketball Premier League"));
    }
}
