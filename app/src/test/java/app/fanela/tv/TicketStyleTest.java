package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The Ticket's own text rules (spec §3.4): long names, the stub's words, wrapping, and the baselines. */
public class TicketStyleTest {
    @Test public void longNamesWithASpaceTakeTwoLines() {
        assertArrayEquals(new String[]{"CRVENA", "ZVEZDA"}, TicketStyle.nameLines("CRVENA ZVEZDA"));
        assertArrayEquals(new String[]{"REAL MADRID"}, TicketStyle.nameLines("REAL MADRID"));      // 11: one line
        assertArrayEquals(new String[]{"PANATHINAIKOS"}, TicketStyle.nameLines("PANATHINAIKOS"));  // no space
        assertArrayEquals(new String[]{"AEK ATHENS"}, TicketStyle.nameLines("AEK ATHENS"));
        assertArrayEquals(new String[]{"NOTTINGHAM", "FOREST UNITED"}, TicketStyle.nameLines("NOTTINGHAM FOREST UNITED"));
    }

    @Test public void aLongHomeNameRunsOnOnlyWhereTheAwayNameLeavesRoom() {
        assertEquals(262, TicketStyle.homeRoom(130), 0.01);   // PANATHINAIKOS VS ZVEZDA: the run-on
        assertEquals(256, TicketStyle.homeRoom(232), 0.01);   // a full away name: 20 px short of its column
        assertEquals(256, TicketStyle.homeRoom(260), 0.01);   // both too long: 20 px short of the away column
        assertEquals(257, TicketStyle.homeRoom(230), 0.01);   // between: up to 20 px short of the away name
    }

    @Test public void abroadNamesWrapByWords() {
        TicketStyle.Measure tenPx = s -> s.length() * 10f;
        List<String> l = TicketStyle.wrap("TNT SPORTS OR PRIME VIDEO", 140, tenPx);
        assertEquals(Arrays.asList("TNT SPORTS OR", "PRIME VIDEO"), l);
        assertEquals(Arrays.asList("NOVASPORTS"), TicketStyle.wrap("  NOVASPORTS ", 50, tenPx));  // one long word stays whole
        assertEquals(Arrays.asList("A B C"), TicketStyle.wrap("A B C", 300, tenPx));
    }

    /** The not-updated warning: amber where it reads (dark and red stocks), the stock's ink on yellow and orange. */
    @Test public void amberWarnsOnlyWhereItReads() {
        assertFalse(TicketStyle.amberReads(ClubTokens.mix(0xFFF5C800, 0xFF000000, 0.24)));  // AEK: #BA9800
        assertFalse(TicketStyle.amberReads(ClubTokens.mix(0xFFFFD200, 0xFF000000, 0.24)));  // Aris
        assertTrue(TicketStyle.amberReads(ClubTokens.mix(0xFFE2001A, 0xFF000000, 0.24)));   // Olympiacos red
        assertTrue(TicketStyle.amberReads(ClubTokens.STOCK_BLACK));                         // PAOK charcoal
        assertTrue(TicketStyle.amberReads(ClubTokens.mix(0xFF1B2A6B, 0xFF000000, 0.24)));   // Real Madrid navy
    }

    @Test public void twoChannelsShareTheStub() {
        float[] none = {0, 0}, words = {130, 130};
        // one wide logo (5:1): 250 x 50; with its number chip capped to 200 (40 tall: widened to 44 into the free room)
        float[][] one = TicketStyle.logoRow(new float[]{5}, new float[]{130}, new float[]{0}, 1);
        assertEquals(250, one[0][0], 0.01);
        assertEquals(56, one[0][1], 0.01);
        assertEquals(50, one[0][3], 0.01);
        one = TicketStyle.logoRow(new float[]{5}, new float[]{130}, new float[]{70}, 1);
        assertEquals(220, one[0][0], 0.01);
        assertEquals(44, one[0][3], 0.01);
        // a wordmark too wide to be 44 tall in the stub (Novasports Prime, 11:1): the name in type
        assertNull(TicketStyle.logoRow(new float[]{11}, new float[]{300}, new float[]{0}, 1));
        // Magenta Sport (3.4:1) and MEGA (9:1): MEGA would be 16 px tall, so it is set as its name beside the logo,
        // which takes the room left to stay 44 tall
        float[][] two = TicketStyle.logoRow(new float[]{3.4f, 9}, words, none, 2);
        assertNotNull(two);
        assertEquals(1, two[1][4], 0);
        assertEquals(0, two[0][4], 0);
        assertTrue(two[0][3] >= 44);
        assertTrue(two[0][0] + two[1][0] + TicketStyle.LOGO_GAP <= TicketStyle.STUB_IN);
        // near square (ERT, SPORT FM 1.5:1): in type; two names and no logo left: the caller writes words
        assertNull(TicketStyle.logoRow(new float[]{1.5f}, new float[]{110}, new float[]{0}, 1));
        assertNull(TicketStyle.logoRow(new float[]{9, 9}, words, new float[]{80, 80}, 2));
    }

    /** Fixed advances at 44: `em` a character, tracking adding its share (Google Sans Bold caps are .55 to .70 em). */
    static TicketStyle.Measure2 gs44(float em) { return (s, t) -> s.length() * (em + t) * 44; }

    @Test public void channelWordsKeep44AndDropLikelyBeforeASecondLine() {
        // one name on one line: as written, LIKELY goes over it
        TicketStyle.Lines l = TicketStyle.nameLines("NOVASPORTS", Arrays.asList("NOVASPORTS"), 312, gs44(0.70f));
        assertEquals(Arrays.asList("NOVASPORTS"), l.lines);
        assertEquals(0, l.track, 0);  // 10 x .70 x 44 = 308 untracked; tracked .06 it is 334
        // two guesses: one a line, " /" after the first, no LIKELY and no OR
        l = TicketStyle.nameLines("TNT SPORTS OR PRIME VIDEO", Arrays.asList("TNT SPORTS", "PRIME VIDEO"), 312, gs44(0.55f));
        assertEquals(Arrays.asList("TNT SPORTS" + TicketStyle.SLASH, "PRIME VIDEO"), l.lines);
        // a long name wraps by words, tracked while it fits
        l = TicketStyle.nameLines("SKY SPORTS MAIN EVENT", Arrays.asList("SKY SPORTS MAIN EVENT"), 312, gs44(0.55f));
        assertEquals(Arrays.asList("SKY SPORTS", "MAIN EVENT"), l.lines);
        assertEquals(0.06f, l.track, 0);
    }

    @Test public void aStateWordUnder40TakesTwoLinesAt48() {
        ClashStyle.Measure2 gs = (s, size, t) -> s.length() * (0.66f + t) * size;
        TicketStyle.Word w = TicketStyle.fitWord("TOMORROW", gs);
        assertEquals(1, w.lines.length);
        assertEquals(48, w.size, 0);
        w = TicketStyle.fitWord("ΑΝΑΜΟΝΗ ΣΚΟΡ", gs);  // 12 x .68 x 40 = 326 > 312: two lines at 48
        assertArrayEquals(new String[]{"ΑΝΑΜΟΝΗ", "ΣΚΟΡ"}, w.lines);
        assertEquals(48, w.size, 0);
        w = TicketStyle.fitWord("NO RESULT YET", gs);
        assertArrayEquals(new String[]{"NO RESULT", "YET"}, w.lines);
        assertEquals(48, w.size, 0);
        w = TicketStyle.fitWord("ΟΡΙΣΤ. ΔΙΑΚΟΠΗ", gs);
        assertArrayEquals(new String[]{"ΟΡΙΣΤ.", "ΔΙΑΚΟΠΗ"}, w.lines);
        // one long word can't split: it loses its tracking before going under 40 (ΚΑΘΥΣΤΕΡΗΣΗ: 42)
        w = TicketStyle.fitWord("ΚΑΘΥΣΤΕΡΗΣΗ", gs);
        assertEquals(1, w.lines.length);
        assertEquals(0, w.track, 0);
        assertTrue(w.size >= 40);
        assertEquals(176, 140 + TicketStyle.WORD_TWO_SHIFT, 0);
    }

    @Test public void baselinesMatchTheMeasuredReference() {
        // hero: RC Bold 150, box top 140, line-height 1 -> measured baseline 273 (caps y 165-273)
        assertEquals(273.3, TicketStyle.baseline(140, 150, 150, TicketStyle.RC_ASC, TicketStyle.RC_DESC), 0.5);
        // state word: GS Bold 48 centred in the 66 px box at top 40
        assertEquals(89.3, TicketStyle.baseline(40, 66, 48, TicketStyle.GS_ASC, TicketStyle.GS_DESC), 0.5);
        assertEquals(74.8, TicketStyle.baselineNormal(40, 36, TicketStyle.GS_ASC), 0.1);
    }

    /** FB-7: the tear line is printed in the ticket's inks (the launcher flattens a poster onto #212121, never the wallpaper). */
    @Test public void perforationIsPrintedInTheTicketsOwnInks() {
        int red = ClubTokens.of("435", 0xFFD0102E, 0xFFFFFFFF, null).stock;
        int body = TicketStyle.perfBody(red);
        assertTrue("deeper than the stock", ClubTokens.lum(body) < ClubTokens.lum(red));
        assertTrue("still the stock's hue", ClubTokens.hueDist(ClubTokens.hue(body), ClubTokens.hue(red)) < 10);
        int black = ClubTokens.STOCK_BLACK;
        assertTrue("a black stock is lifted, not lost", ClubTokens.lum(TicketStyle.perfBody(black)) > ClubTokens.lum(black));
        assertTrue("the stub's side is lifted ink", ClubTokens.lum(TicketStyle.perfStub()) > ClubTokens.lum(ClubTokens.INK));
        assertTrue("never the old launcher grey", TicketStyle.perfStub() != ClubTokens.LAUNCHER_BG && body != ClubTokens.LAUNCHER_BG);
    }

    @Test public void theFullNameComesFirstThenTheCardsNames() {
        assertEquals(java.util.Arrays.asList("Partizan Belgrade", "Partizan"),
            TicketStyle.fullFirst("Partizan Belgrade", CardModel.names("Partizan Belgrade")));
        assertEquals(java.util.Arrays.asList("AEK Athens", "AEK"), TicketStyle.fullFirst("AEK Athens", CardModel.names("AEK")));
    }

    /** LAYOUT-CALM: the live stub is three tiers, the clock folded into the pill while it fits the stub at 36. */
    @Test public void theLivePillCarriesTheClockWhileItFits() {
        CardModel m = new CardModel();
        m.state = CardModel.State.LIVE; m.stateLine = "LIVE"; m.clock = "Q3 5:12";
        TicketStyle.Measure tenPx = s -> s.length() * 10f + 60;
        assertEquals("LIVE Q3 5:12", TicketStyle.livePillText(m, tenPx));
        m.clock = "2Η ΠΑΡ. 1:00 ΚΑΙ ΚΑΤΙ";  // too long for the stub even at the smallest size: LIVE alone
        assertEquals("LIVE", TicketStyle.livePillText(m, tenPx));
        m.clock = "";
        assertEquals("LIVE", TicketStyle.livePillText(m, tenPx));
        // the hero with nothing captioned under it (upcoming and live): its capitals start at ~214
        assertEquals(188f, TicketStyle.HERO_TOP_ALONE, 0);
    }
}
