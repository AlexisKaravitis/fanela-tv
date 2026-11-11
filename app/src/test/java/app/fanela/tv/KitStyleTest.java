package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Kit card's pure parts: the kit pattern library and the fit-to-width cascade (spec §3.3, §1.6). */
public class KitStyleTest {
    /** Owner, fx6: the shirt is symmetrical, five equal stripes on the band's centre line, A at both edges. */
    @Test public void stripesAreFiveEqualAndMirroredAboutTheCentreLine() {
        float[][] r = KitStyle.patternRects(ClubTokens.Pattern.STRIPES, 270, 540);
        assertEquals(5, r.length);                       // A B A B A, 54 px each
        for (int i = 0; i < r.length; i++) {
            assertEquals(54f, r[i][2] - r[i][0], 0.001);
            assertEquals(i % 2, (int) r[i][4]);
            assertEquals(540f, r[i][3], 0);
            float[] mirror = r[r.length - 1 - i];       // the same colour and width at the same distance from the centre
            assertEquals(270f - r[i][2], mirror[0], 0.001);
            assertEquals((int) r[i][4], (int) mirror[4]);
        }
        assertEquals(0f, r[0][0], 0);
        assertEquals(270f, r[4][2], 0.001);              // the last stripe ends with the band
        assertEquals(135f, (r[2][0] + r[2][2]) / 2, 0.001);  // the middle stripe is on the disc's centre line
        assertEquals(KitStyle.KIT_CX, KitStyle.MY_DISC_CX, 0);
        assertEquals(270f, KitStyle.MY_DISC_CY, 0);      // the disc on the card's middle, as a crest on a chest
    }

    @Test public void hoopsAreCentredOnTheMiddle() {
        float[][] r = KitStyle.patternRects(ClubTokens.Pattern.HOOPS, 270, 540);
        assertEquals(9, r.length);
        float[] mid = r[4];
        assertEquals(240f, mid[1], 0);                   // the middle hoop, colour A, centred on y 270
        assertEquals(300f, mid[3], 0);
        assertEquals(0, (int) mid[4]);
        assertEquals(0f, r[0][1], 0);
        assertEquals(540f, r[8][3], 0);
        assertEquals(r[0][3] - r[0][1], r[8][3] - r[8][1], 0.001);  // cut equally at the top and the bottom
        assertEquals(270f, r[0][2], 0);
    }

    @Test public void halvesAndSolidCoverTheKit() {
        float[][] h = KitStyle.patternRects(ClubTokens.Pattern.HALVES, 270, 540);
        assertEquals(2, h.length);
        assertEquals(135f, h[0][2], 0);
        assertEquals(1, (int) h[1][4]);
        float[][] s = KitStyle.patternRects(ClubTokens.Pattern.SOLID, 270, 540);
        assertEquals(1, s.length);
        assertEquals(0, (int) s[0][4]);
    }

    @Test public void sashFollowsTheCssGradientLine() {
        float[] g = KitStyle.sashLine(115, 960, 540);
        double len = Math.hypot(g[2] - g[0], g[3] - g[1]);
        assertEquals(960 * Math.sin(Math.toRadians(115)) + 540 * Math.abs(Math.cos(Math.toRadians(115))), len, 0.01);
        assertTrue(g[2] > g[0] && g[3] > g[1]);          // 115deg points right and a little down
        assertEquals(480f, (g[0] + g[2]) / 2, 0.01);     // centred on the card
        float[] up = KitStyle.sashLine(0, 960, 540);
        assertEquals(540f, up[1], 0.01);                 // 0deg runs bottom to top
        assertEquals(0f, up[3], 0.01);
    }

    /** A line of fixed text width per px of size plus a logo of up to 240 px and an optional competition. */
    static KitStyle.Width line(float textPerPx, float logo, float compPerPx) {
        return (size, cap, comp) -> textPerPx * size + Math.min(logo, cap) + (comp && compPerPx > 0 ? compPerPx * size + 40 : 0);
    }

    @Test public void fitKeepsTheStartSizeWhenTheLineFits() {
        KitStyle.Fit f = KitStyle.fit(line(5, 240, 0), 602, 46, 38, true, false);
        assertEquals(46f, f.size, 0);
        assertEquals(240f, f.logoCap, 0);
        assertTrue(f.comp);
        assertTrue(f.fits);
    }

    @Test public void fitShrinksTwoPxAtATimeToTheMidpointBeforeCappingTheLogo() {
        // 46 px: 8*46 + 240 = 608 > 602; 44 px: 352 + 240 = 592 fits, logo untouched
        KitStyle.Fit f = KitStyle.fit(line(8, 240, 0), 602, 46, 38, true, false);
        assertEquals(44f, f.size, 0);
        assertEquals(240f, f.logoCap, 0);
    }

    @Test public void fitCapsTheLogoAndRetriesFromTheStartSize() {
        // even 42 px (mid) with a 240 logo is too wide (9*42+240 = 618); with 200: 46 px = 614, 44 = 596 fits
        KitStyle.Fit f = KitStyle.fit(line(9, 240, 0), 602, 46, 38, true, false);
        assertEquals(200f, f.logoCap, 0);
        assertEquals(44f, f.size, 0);
    }

    @Test public void fitDropsTheCompetitionLastAndStartsAgainFromTheStartSize() {
        // SUPER LEAGUE · LIKELY + logo: too wide at 38 px even with the logo at 180; without the competition it fits at 46
        KitStyle.Fit f = KitStyle.fit(line(4, 240, 8), 602, 46, 38, true, true);
        assertFalse(f.comp);
        assertEquals(46f, f.size, 0);
        assertEquals(180f, f.logoCap, 0);                // the cap reached stays, as in the reference
        assertTrue(f.fits);
    }

    @Test public void fitWithoutADroppableCompetitionStopsAtTheMinimum() {
        KitStyle.Fit f = KitStyle.fit((s, c, comp) -> 20 * s, 400, 96, 56, false, false);
        assertEquals(56f, f.size, 0);
        assertTrue(f.comp);
        assertFalse(f.fits);
    }

    @Test public void fitGoesBelowTheMidpointOnlyWhenItMust() {
        // a name: 7 px per px of size in a 400 box from 96 -> 56 = 392 fits; 58 = 406 doesn't
        KitStyle.Fit f = KitStyle.fit((s, c, comp) -> 7 * s, 400, 96, 56, false, false);
        assertEquals(56f, f.size, 0);
        KitStyle.Fit g = KitStyle.fit((s, c, comp) -> 4 * s, 400, 96, 56, false, false);
        assertEquals(96f, g.size, 0);
    }
}
