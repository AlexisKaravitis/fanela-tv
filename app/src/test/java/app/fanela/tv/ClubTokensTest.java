package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One set of colour rules for every card style (spec §2). */
public class ClubTokensTest {
    /** A square crest of one colour with a transparent border. */
    static ClubTokens.CrestInfo crest(int colour, int size, int canvas) {
        int[] px = new int[canvas * canvas];
        int o = (canvas - size) / 2;
        for (int y = o; y < o + size; y++) for (int x = o; x < o + size; x++) px[y * canvas + x] = colour;
        return ClubTokens.analyse(px, canvas, canvas);
    }

    @Test public void olympiacosRedIsChromaticOnThePlatter() {
        ClubTokens t = ClubTokens.of("435", 0xFFE2001A, 0xFFFFFFFF, crest(0xFFE2001A, 360, 400));
        assertEquals(ClubTokens.Kit.CHROMATIC, t.kit);
        assertEquals(ClubTokens.FIELD, t.field);
        assertEquals(ClubTokens.opaque(0xFFE2001A), t.light);
        assertEquals(0.70f, t.lightA, 1e-6);
        assertEquals(ClubTokens.Pattern.STRIPES, t.pattern);
        assertTrue(t.sharpCrest);
        assertEquals(ClubTokens.mix(0xFFE2001A, 0xFF000000, 0.24), t.stock);
    }

    @Test public void blackKitsGetCharcoalAndAFaintLight() {
        ClubTokens t = ClubTokens.of("605", 0xFF111111, 0xFFFFFFFF, null);
        assertEquals(ClubTokens.Kit.BLACK, t.kit);
        assertEquals(ClubTokens.FIELD_BLACK, t.field);
        assertEquals(ClubTokens.KIT_BLACK, t.kitA);
        assertEquals(ClubTokens.KIT_WHITE, t.kitB);
        assertEquals(0xFFFFFFFF, t.stockInk);
    }

    @Test public void whiteKitsUseTheirSecondColourForTheTicket() {
        ClubTokens t = ClubTokens.of("86", 0xFFFFFFFF, 0xFF1B2A6B, null);  // Real Madrid: white, navy
        assertEquals(ClubTokens.Kit.WHITE, t.kit);
        assertEquals(ClubTokens.LIGHT_WHITE, t.light);
        assertEquals(ClubTokens.mix(0xFF1B2A6B, 0xFF000000, 0.24), t.stock);
        assertTrue(ClubTokens.lum(t.field) < 0.6);
    }

    @Test public void redAgainstRedClashesBlueDoesNot() {
        ClubTokens t = ClubTokens.of("435", 0xFFE2001A, 0xFFFFFFFF, null);
        assertTrue(t.clashes(0xFFD11A2A));    // Crvena Zvezda
        assertFalse(t.clashes(0xFF1B63B5));   // Iraklis
        assertFalse(ClubTokens.of("605", 0xFF111111, 0xFFFFFFFF, null).clashes(0xFF111111));  // black kits never clash
    }

    @Test public void crestAnalysisFindsSizeColourAndTheDiscRule() {
        ClubTokens.CrestInfo red = crest(0xFFD0102A, 200, 256);
        assertEquals(200, red.size);
        assertEquals("chromatic", red.dominant);
        assertTrue(ClubTokens.needsDisc(red, 0xFFB0001A));   // red crest on red stock
        assertFalse(ClubTokens.needsDisc(red, 0xFF131316));  // red crest on the dark platter
        ClubTokens.CrestInfo white = crest(0xFFF4F4F4, 300, 320);
        assertEquals("light", white.dominant);
        ClubTokens.CrestInfo dark = crest(0xFF151515, 300, 320);
        assertTrue(ClubTokens.needsDisc(dark, ClubTokens.FIELD_BLACK));
    }

    /** A light but saturated kit is the club's colour, not white: Aris, Dortmund and AEK stay yellow. */
    @Test public void brightYellowKitsAreChromaticWhitesAndCreamsAreWhite() {
        for (int yellow : new int[]{0xFFFFD200, 0xFFFFD700, 0xFFFDE100, 0xFFF5C800, 0xFFFFFF00, 0xFFFFE000})
            assertEquals(Integer.toHexString(yellow), ClubTokens.Kit.CHROMATIC, ClubTokens.kitClass(yellow));
        for (int white : new int[]{0xFFFFFFFF, 0xFFF2F2F2, 0xFFFFF5CC, 0xFFF5F0E1, 0xFFB0B0B0})
            assertEquals(Integer.toHexString(white), ClubTokens.Kit.WHITE, ClubTokens.kitClass(white));
        assertEquals(ClubTokens.Kit.BLACK, ClubTokens.kitClass(0xFF111111));
        ClubTokens aris = ClubTokens.of("11553", 0xFFFFD200, 0xFF111111, null);
        assertEquals(ClubTokens.Kit.CHROMATIC, aris.kit);
        assertEquals(ClubTokens.opaque(0xFFFFD200), aris.light);               // a yellow light, not warm grey
        assertEquals(ClubTokens.mix(0xFFFFD200, 0xFF000000, 0.14), aris.kitA);  // yellow stripes / field
        assertEquals(ClubTokens.mix(0xFFFFD200, 0xFF000000, 0.24), aris.stock); // a yellow ticket, not near-black
        assertEquals(0xFF141416, aris.stockInk);                                // with dark ink
    }

    @Test public void ghostAlphaKeepsTheGhostDim() {
        assertEquals(0.60f, ClubTokens.of("x", 0xFFE2001A, 0xFFFFFFFF, crest(0xFFE2001A, 400, 420)).ghostAlpha, 1e-3);
        assertEquals(0.22f, ClubTokens.of("x", 0xFFE2001A, 0xFFFFFFFF, crest(0xFFFFFFFF, 400, 420)).ghostAlpha, 1e-3);
        // Olympiacos as printed (white inside its red outline, mean luminance ~.65): a watermark, not a pink disc
        assertEquals(0.31f, ClubTokens.ghostAlpha(0.65), 0.01);
        assertFalse(ClubTokens.of("x", 0xFFE2001A, 0xFFFFFFFF, crest(0xFFE2001A, 192, 200)).sharpCrest);
    }

    @Test public void theGhostUsesTheClubsLargerCrest() {
        ClubTokens.CrestInfo euroleague = crest(0xFFE2001A, 168, 192), espn = crest(0xFFE2001A, 360, 384);
        assertTrue(ClubTokens.clubCrestIsBetter(euroleague, espn));    // Olympiacos basketball: the 500 px club crest
        assertFalse(ClubTokens.clubCrestIsBetter(espn, euroleague));   // the match's own crest when it is the larger
        assertFalse(ClubTokens.clubCrestIsBetter(espn, crest(0xFFE2001A, 360, 384)));  // a tie keeps the match's
        assertFalse(ClubTokens.clubCrestIsBetter(euroleague, null));
        assertTrue(ClubTokens.clubCrestIsBetter(null, espn));
        // the sharp test and the alpha follow the ghost; the disc rule keeps the match's crest
        ClubTokens t = ClubTokens.of("435", 0xFFE2001A, 0xFFFFFFFF, euroleague, espn);
        assertTrue(t.sharpCrest);
        assertEquals(168, t.crest.size);
        assertEquals(360, t.ghost.size);
        assertFalse(ClubTokens.of("435", 0xFFE2001A, 0xFFFFFFFF, euroleague, euroleague).sharpCrest);
    }
}
