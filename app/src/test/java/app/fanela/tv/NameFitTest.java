package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** FB-2: one rule sets a club's name on every card style (CardModel.fitName), measured here with a fake font. */
public class NameFitTest {
    @After public void english() { I18n.use("en"); }

    /** Every character half the text size wide (a condensed capital). */
    static final CardModel.Measure HALF = (s, size) -> s.length() * size * 0.5f;

    @Test public void aNameThatFitsKeepsTheStartSize() {
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("DUBAI"), 330, 420, 100, 56, 44);
        assertEquals("DUBAI", f.text);
        assertEquals(100f, f.size, 0);
        assertFalse(f.runsOn);
    }

    @Test public void aLongerNameStepsDownToTheLargestSizeThatFits() {
        // 8 characters: 4 px per px of size, 330 / 4 = 82.5 -> 82 (2 px steps from 100)
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("KARDITSA"), 330, 420, 100, 56, 44);
        assertEquals(82f, f.size, 0);
        assertFalse(f.runsOn);
    }

    @Test public void pastTheMinimumTheShortFormTakesOverAtItsLargestSize() {
        // PARTIZAN BELGRADE (17) needs 476 at 56 > 330: PARTIZAN (8) fits at 82
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("PARTIZAN BELGRADE", "PARTIZAN"), 330, 420, 100, 56, 44);
        assertEquals("PARTIZAN", f.text);
        assertEquals(82f, f.size, 0);
        assertFalse(f.runsOn);
    }

    @Test public void aNameWithoutAShortFormRunsOnAtTheMinimumOrAbove() {
        // PANATHINAIKOS (13): 364 at 56 > 330, fits the 420 run-on at 64 (416)
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS"), 330, 420, 100, 56, 44);
        assertEquals("PANATHINAIKOS", f.text);
        assertEquals(64f, f.size, 0);
        assertTrue(f.runsOn);
    }

    @Test public void neverBelowTheMinimumWhileAnyNameCanRunOn() {
        // the card name can run on at 56 (420 / 15 chars / .5 = 56): it stays itself rather than the short form
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("ABCDEFGHIJKLMNO", "ABCDEFGHIJKLMNOP"), 330, 420, 100, 56, 44);
        assertEquals("ABCDEFGHIJKLMNO", f.text);
        assertEquals(56f, f.size, 0);
    }

    @Test public void onlyANameNoClubHasGoesBelowTheMinimumAndNeverPastTheFloor() {
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("ABCDEFGHIJKLMNOPQRSTUVWXYZ"), 330, 420, 100, 56, 44);
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", f.text);  // never an ellipsis
        assertEquals(44f, f.size, 0);
        assertTrue(f.runsOn);
        CardModel.NameFit g = CardModel.fitName(HALF, Arrays.asList("ABCDEFGHIJKLMNOPQRS"), 330, 420, 100, 56, 40);
        assertEquals(44f, g.size, 0);  // 19 chars: 418 at 44, the first size under the minimum that fits
    }

    @Test public void theCuratedShortFormsInBothLanguages() {
        assertEquals(Arrays.asList("Partizan Belgrade", "Partizan"), CardModel.names("Partizan Belgrade"));
        assertEquals(Arrays.asList("Crvena Zvezda", "Zvezda"), CardModel.names("Crvena Zvezda"));
        assertEquals(Arrays.asList("Hapoel Tel Aviv", "Hapoel TA"), CardModel.names("Hapoel Tel Aviv"));
        assertEquals(Arrays.asList("Asteras Tripoli", "Asteras"), CardModel.names("Asteras Tripoli"));
        assertEquals(Arrays.asList("Ερυθρός Αστέρας", "Ερυθρός"), CardModel.names("Ερυθρός Αστέρας"));
        assertEquals(Arrays.asList("Χάποελ Τελ Αβίβ", "Χάποελ ΤΑ"), CardModel.names("Χάποελ Τελ Αβίβ"));
        // Panathinaikos (football and basketball share the card name): PAO / ΠΑΟ, so it keeps its neighbours' size
        assertEquals(Arrays.asList("Panathinaikos", "PAO"), CardModel.names("Panathinaikos"));
        assertEquals(Arrays.asList("Παναθηναϊκός", "ΠΑΟ"), CardModel.names("Παναθηναϊκός"));
    }

    @Test public void theModelCarriesBothNamesForEveryStyle() {
        Match m = CardModelTest.next(CardModelTest.at(3, 17, 45));
        m.home = "PAOK"; m.away = "Partizan Belgrade"; m.comp = "club.friendly"; m.team = "FC";
        CardModel c = CardModel.of(m, true, false, CardModelTest.at(0, 12, 0));
        List<String> n = c.opponentNames;
        assertEquals(Arrays.asList("Partizan Belgrade", "Partizan"), n);
        I18n.use("el");
        CardModel g = CardModel.of(m, true, false, CardModelTest.at(0, 12, 0));
        assertEquals("Partizan", g.opponentNames.get(g.opponentNames.size() - 1));
    }

    @Test public void panathinaikosTakesPaoBeforeRunningOnSmall() {
        // Signature's box (330, run-on 420, 100 → 56): PANATHINAIKOS needs 364 at 56, so PAO at the start size
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS", "PAO"), "PAO", 330, 420, 100, 56, 44);
        assertEquals("PAO", f.text);
        assertEquals(100f, f.size, 0);
        assertFalse(f.runsOn);
        // where the full name fits its box at the minimum or above (Clean's 320 at 44+), it stays itself
        CardModel.NameFit g = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS", "PAO"), "PAO", 320, 320, 56, 44, 36);
        assertEquals("PANATHINAIKOS", g.text);
    }

    @Test public void anAbbreviationOnlyWhereEvenRunningOnFails() {
        // Clean's 300 column has no run-on: PANATHINAIKOS (6.5 px per px) is 286 at 44 -> fits; at a 250 room it doesn't
        CardModel.NameFit f = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS"), "PAO", 300, 300, 60, 44, 36);
        assertEquals("PANATHINAIKOS", f.text);
        CardModel.NameFit g = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS"), "PAO", 250, 250, 60, 44, 36);
        assertEquals("PAO", g.text);
        assertEquals(60f, g.size, 0);
        // where it can run on (Signature), it stays itself
        CardModel.NameFit h = CardModel.fitName(HALF, Arrays.asList("PANATHINAIKOS"), "PAO", 330, 420, 100, 56, 44);
        assertEquals("PANATHINAIKOS", h.text);
        assertEquals("PAO", CardModel.abbreviation("Panathinaikos"));
        assertEquals("ΠΑΟ", CardModel.abbreviation("Παναθηναϊκός"));
    }
}
