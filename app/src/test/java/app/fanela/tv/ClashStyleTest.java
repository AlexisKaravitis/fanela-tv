package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Clash's pure rules: the diagonal's geometry, the colour-clash rules (concept 15's fields()), names and words. */
public class ClashStyleTest {
    static final int OLY = 0xFFD0102E, ZVEZDA = 0xFFD2232A, PAOK = 0xFF111111, OFI = 0xFF111111, KIFISIA = 0xFF1F4E9E,
        REAL = 0xFFF4F4F4, REAL_2 = 0xFF2B3990, SPURS = 0xFFF4F4F4, SPURS_2 = 0xFF132257, MILAN = 0xFFC8102E,
        DUBAI = 0xFF111111, GOLD = 0xFFC9A227, ARIS = 0xFFFFD200, WHITE = 0xFFFFFFFF;

    // ---------- the diagonal ----------

    @Test public void theGapIsTheSameTurnedHalfRound() {
        assertEquals(395f, ClashStyle.leftEdge(0), 0.01f);
        assertEquals(785f, ClashStyle.rightEdge(0), 0.01f);
        assertEquals(175f, ClashStyle.leftEdge(540), 0.01f);
        assertEquals(565f, ClashStyle.rightEdge(540), 0.01f);
        // the home field at the top is as wide as the away field at the bottom; the axis crosses the card's centre
        assertEquals(ClashStyle.leftEdge(0), Cards.W - ClashStyle.rightEdge(Cards.H), 0.01f);
        assertEquals(480f, ClashStyle.axis(ClashStyle.MID), 0.01f);
        assertEquals(Cards.H, ClashStyle.ROW_A + ClashStyle.ROW_C, 0.01f);
    }

    @Test public void theRoomInTheGapKeeps24PxFromBothEdges() {
        float h = 60, w = ClashStyle.room(h), y = 300;
        float left = ClashStyle.axis(y) - w / 2, right = ClashStyle.axis(y) + w / 2;
        double cos = Math.cos(Math.atan(ClashStyle.K));
        // the box's top-left corner and bottom-right corner are the nearest the edges: 24 px square to them
        assertEquals(24, (left - ClashStyle.leftEdge(y - h / 2)) * cos, 0.01);
        assertEquals(24, (ClashStyle.rightEdge(y + h / 2) - right) * cos, 0.01);
    }

    @Test public void theCrestsKeep24PxFromTheirEdgeOrSeam() {
        assertEquals(168f, ClashStyle.crestSize(0, 0), 0);
        float withSeam = ClashStyle.crestSize(ClashStyle.SEAM_X, 0);
        assertEquals(158f, withSeam, 0);
        assertEquals("the same for a seam on either side", withSeam, ClashStyle.crestSize(0, ClashStyle.SEAM_X), 0);
        for (float[] c : new float[][]{{0, 168}, {ClashStyle.SEAM_X, 158}}) {
            float seam = c[0], size = c[1];
            // home: its right edge at its foot; away: its left edge at its top
            float home = ClashStyle.leftEdge(ClashStyle.MID + size / 2) - seam - (ClashStyle.SAFE_L + size);
            float away = (ClashStyle.SAFE_R - size) - (ClashStyle.rightEdge(ClashStyle.MID - size / 2) + seam);
            assertTrue(home >= ClashStyle.CLEAR_X - 0.01f);
            assertTrue(away >= ClashStyle.CLEAR_X - 0.01f);
        }
    }

    // ---------- the colour-clash rules ----------

    @Test public void kitColoursBecomeFields() {
        assertEquals(OLY, ClashStyle.field(OLY));                      // a colour as the club wears it
        assertEquals(ClashStyle.GREY, ClashStyle.field(WHITE));        // white: capped grey
        assertEquals(ClashStyle.GREY, ClashStyle.field(REAL));
        assertEquals(ClashStyle.CHARCOAL, ClashStyle.field(PAOK));     // near-black: charcoal, lighter than the gap
        int aris = ClashStyle.field(ARIS);                             // yellow stays yellow, no brighter than the grey
        assertTrue(ClubTokens.luminance(aris) <= ClashStyle.LUM_CAP + 1e-9);
        assertEquals(ClubTokens.Kit.CHROMATIC, ClubTokens.kitClass(aris));
        int navy = ClashStyle.field(SPURS_2);                          // navy is lifted, never turned charcoal
        assertNotEquals(ClashStyle.CHARCOAL, navy);
        assertTrue(ClubTokens.luminance(navy) >= 0.035);
        assertTrue(ClubTokens.hsv(navy)[0] > 200 && ClubTokens.hsv(navy)[0] < 260);
    }

    @Test public void sameColourClubsFallToTheOpponentsSecondColour() {
        // Olympiacos v Crvena Zvezda: red v red, so Zvezda is its white, as the capped grey
        ClashStyle.Fields f = ClashStyle.fields(OLY, WHITE, new int[]{ZVEZDA, WHITE}, true);
        assertEquals(OLY, f.home);
        assertEquals(ClashStyle.GREY, f.away);
        assertEquals(0, f.homeSeam); assertEquals(0, f.awaySeam);
        // away from home the fields swap sides (home on the left, as the score reads)
        f = ClashStyle.fields(OLY, WHITE, new int[]{ZVEZDA, WHITE}, false);
        assertEquals(ClashStyle.GREY, f.home);
        assertEquals(OLY, f.away);
    }

    @Test public void blackClubsGetASeamInTheirOtherColour() {
        // PAOK v Kifisia: charcoal with a white seam v blue
        ClashStyle.Fields f = ClashStyle.fields(PAOK, WHITE, new int[]{KIFISIA, WHITE}, true);
        assertEquals(ClashStyle.CHARCOAL, f.home);
        assertEquals(KIFISIA, f.away);
        assertEquals(WHITE, f.homeSeam);
        assertEquals(0, f.awaySeam);
        // PAOK v OFI: black v black, so OFI is its white (grey), without a seam
        f = ClashStyle.fields(PAOK, WHITE, new int[]{OFI, WHITE}, true);
        assertEquals(ClashStyle.GREY, f.away);
        assertEquals(0, f.awaySeam);
        // Olympiacos v Dubai: red v charcoal, Dubai's seam gold
        f = ClashStyle.fields(OLY, WHITE, new int[]{DUBAI, GOLD}, true);
        assertEquals(ClashStyle.CHARCOAL, f.away);
        assertEquals(GOLD, f.awaySeam);
        // Olympiacos v AC Milan: red v red, so Milan is its black, and its seam its red
        f = ClashStyle.fields(OLY, WHITE, new int[]{MILAN, 0xFF111111}, true);
        assertEquals(ClashStyle.CHARCOAL, f.away);
        assertEquals(MILAN, f.awaySeam);
    }

    @Test public void whiteClubsAndUnknownOpponents() {
        // Real Madrid v Tottenham: white v white, so Spurs are their navy
        ClashStyle.Fields f = ClashStyle.fields(REAL, REAL_2, new int[]{SPURS, SPURS_2}, true);
        assertEquals(ClashStyle.GREY, f.home);
        assertEquals(ClashStyle.field(SPURS_2), f.away);
        // an opponent whose colours aren't known: the neutral slate, or the grey beside a charcoal field
        assertEquals(ClashStyle.SLATE, ClashStyle.fields(OLY, WHITE, null, true).away);
        assertEquals(ClashStyle.GREY, ClashStyle.fields(PAOK, WHITE, null, true).away);
        assertEquals(0, ClashStyle.fields(PAOK, WHITE, null, true).awaySeam);
    }

    @Test public void theTwoFieldsAreAlwaysToldApart() {
        int[] kits = {OLY, ZVEZDA, PAOK, KIFISIA, REAL, SPURS_2, MILAN, GOLD, ARIS, WHITE, 0xFF007A3D, 0xFF2B2B2B};
        for (int a : kits) for (int b : kits) for (int a2 : new int[]{WHITE, 0xFF111111}) {
            ClashStyle.Fields f = ClashStyle.fields(a, a2, new int[]{b, WHITE}, true);
            assertTrue(Integer.toHexString(a) + " v " + Integer.toHexString(b), ClashStyle.dist(f.home, f.away) > ClashStyle.DISTINCT);
        }
    }

    @Test public void typeIsWhiteOnDarkFieldsAndInkOnLightOnes() {
        assertEquals(CardKit.WHITE, ClashStyle.ink(OLY));
        assertEquals(CardKit.WHITE, ClashStyle.ink(ClashStyle.CHARCOAL));
        assertEquals(ClashStyle.DARK_INK, ClashStyle.ink(ClashStyle.GREY));
        assertEquals(ClashStyle.DARK_INK, ClashStyle.ink(ClashStyle.field(ARIS)));
    }

    @Test public void hlsMatchesPythonsColorsys() {
        double[] r = ClashStyle.hls(0xFFFF0000);
        assertEquals(0, r[0], 1e-9); assertEquals(0.5, r[1], 1e-9); assertEquals(1, r[2], 1e-9);
        double[] b = ClashStyle.hls(0xFF0000FF);
        assertEquals(2.0 / 3, b[0], 1e-9);
        assertEquals(0, ClashStyle.dist(OLY, OLY), 0);
    }

    // ---------- names and words ----------

    /** Roboto Condensed Bold caps, about .567 em a character. */
    static final CardModel.Measure RC = (s, size) -> s.length() * 0.567f * size;

    @Test public void olympiacosFitsItsFieldOnOneLineAt48() {
        ClashStyle.Name n = ClashStyle.fitName(RC, Arrays.asList("ΟΛΥΜΠΙΑΚΟΣ"), null, true, 0);
        assertArrayEquals(new String[]{"ΟΛΥΜΠΙΑΚΟΣ"}, n.lines);
        assertTrue(n.size >= ClashStyle.NAME_MIN);
        n = ClashStyle.fitName(RC, Arrays.asList("DUBAI"), null, false, 0);
        assertEquals(ClashStyle.NAME, n.size, 0);
    }

    @Test public void aLongNameTakesItsShortFormThenTwoLinesThenItsAbbreviation() {
        ClashStyle.Name n = ClashStyle.fitName(RC, Arrays.asList("CRVENA ZVEZDA", "ZVEZDA"), null, false, 0);
        assertArrayEquals(new String[]{"ZVEZDA"}, n.lines);
        n = ClashStyle.fitName(RC, Arrays.asList("ΡΕΑΛ ΜΑΔΡΙΤΗΣ"), null, false, 0);
        assertArrayEquals(new String[]{"ΡΕΑΛ", "ΜΑΔΡΙΤΗΣ"}, n.lines);
        assertTrue(n.size >= ClashStyle.NAME_MIN);
        n = ClashStyle.fitName(RC, Arrays.asList("PANATHINAIKOS"), "PAO", true, 0);
        assertArrayEquals(new String[]{"PAO"}, n.lines);
        // only a name no club has goes under 48
        n = ClashStyle.fitName(RC, Arrays.asList("ABCDEFGHIJKLMNOPQR"), null, true, 0);
        assertEquals(ClashStyle.NAME_FLOOR, n.size, 0);
    }

    @Test public void everyNameLineStaysInItsField() {
        for (boolean home : new boolean[]{true, false}) for (int n = 1; n <= 2; n++) for (int i = 0; i < n; i++) {
            float y = ClashStyle.lineY(home, i, n, 48), cap = ClashStyle.RC_CAP * 48;
            assertTrue(y - cap / 2 >= ClashStyle.SAFE_T && y + cap / 2 <= ClashStyle.SAFE_B);
            assertTrue(ClashStyle.nameRoom(home, y, 48, 0) >= 250);
        }
        assertEquals(ClashStyle.nameRoom(true, ClashStyle.ROW_A, 48, 0), ClashStyle.nameRoom(false, ClashStyle.ROW_C, 48, 0), 0.01f);
    }

    /** Google Sans Bold caps, about .62 em a character (tracking adds its share). */
    static final ClashStyle.Measure2 GS = (s, size, t) -> s.length() * (0.62f + t) * size;

    @Test public void wordsFitOneLineThenTwoThenGoSmaller() {
        float max = ClashStyle.room(35);
        ClashStyle.Words w = ClashStyle.fitWords("TOMORROW", GS, max, 0);
        assertEquals(Arrays.asList("TOMORROW"), w.lines);
        assertEquals(48, w.size, 0);
        w = ClashStyle.fitWords("ΑΝΑΜΟΝΗ ΣΚΟΡ", GS, max, 0);
        assertEquals(Arrays.asList("ΑΝΑΜΟΝΗ", "ΣΚΟΡ"), w.lines);
        assertTrue(w.size >= 44);
        w = ClashStyle.fitWords("ΚΑΘΥΣΤΕΡΗΣΗΣΣΣ", GS, max, 0);
        assertEquals(1, w.lines.size());
        assertEquals(ClashStyle.WORD_FLOOR, w.size, 0);
    }

    /** Roboto Condensed Regular caps, about .5 em a character (tracking adds its share). */
    static final ClashStyle.Measure2 RCR = (s, size, t) -> s.length() * (0.5f + t) * size;

    @Test public void channelWordsTakeTwoLinesAtMostAndNeverGoUnder44() {
        ClashStyle.Room room = size -> 317;
        ClashStyle.Clear clear = (n, size) -> true;
        // abroad, a guess: LIKELY over the names doesn't fit either; without LIKELY, with "/" (glued to the line before)
        ClashStyle.WordLines w = ClashStyle.wordLines("TNT SPORTS OR PRIME VIDEO", "TNT SPORTS / PRIME VIDEO", "LIKELY", RCR, room, clear);
        assertEquals(Arrays.asList("TNT SPORTS" + TicketStyle.SLASH, "PRIME VIDEO"), w.lines);
        assertEquals(48, w.size, 0);
        // a short guess keeps LIKELY, on a line of its own rather than splitting the name
        w = ClashStyle.wordLines("SKY SPORTS", "SKY SPORTS", "LIKELY", RCR, room, clear);
        assertEquals(Arrays.asList("LIKELY", "SKY SPORTS"), w.lines);
        // a wordmark set as its name, with its number: two lines
        w = ClashStyle.wordLines("NOVASPORTS PRIME 318", "NOVASPORTS PRIME 318", "", RCR, room, clear);
        assertEquals(Arrays.asList("NOVASPORTS", "PRIME 318"), w.lines);
        // nothing fits two lines: still 44, cut to two lines
        String junk = "AAAAAAAAAAAA BBBBBBBBBBBB CCCCCCCCCCCC DDDDDDDDDDDD";
        w = ClashStyle.wordLines(junk, junk, "", RCR, room, clear);
        assertEquals(2, w.lines.size());
        assertEquals(44, w.size, 0);
        // room for one line only (clear of the hero): as written when it fits
        w = ClashStyle.wordLines("TNT SPORTS OR PRIME VIDEO", "TNT SPORTS / PRIME VIDEO", "LIKELY", RCR, size -> 900, (n, size) -> n == 1);
        assertEquals(Arrays.asList("LIKELY TNT SPORTS OR PRIME VIDEO"), w.lines);
    }

    @Test public void splitsAtTheMiddleDotOrWhereTheLongerLineIsShortest() {
        TicketStyle.Measure m = s -> s.length() * 10f;
        assertArrayEquals(new String[]{"AET", "FRI 6 NOV"}, ClashStyle.split("AET · FRI 6 NOV", m));
        assertArrayEquals(new String[]{"NO RESULT", "YET"}, ClashStyle.split("NO RESULT YET", m));
        assertNull(ClashStyle.split("ΚΑΘΥΣΤΕΡΗΣΗ", m));
        List<String> l = Arrays.asList(ClashStyle.split("REAL MADRID", m));
        assertEquals(Arrays.asList("REAL", "MADRID"), l);
    }
}
