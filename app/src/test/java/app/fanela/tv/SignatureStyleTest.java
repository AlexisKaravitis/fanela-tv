package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Signature's pure rules: the line-4 fit cascade (spec §1.6), the state line's segment, the ghost's height. */
public class SignatureStyleTest {
    /** A line of text that is `perPx` wide per px of size, a logo up to its cap, and a competition of `comp` per px. */
    static SignatureStyle.Fit.Width line(float perPx, float logo, float comp) {
        return (size, cap, withComp) -> size * perPx + Math.min(logo, cap) + (withComp ? size * comp : 0);
    }

    @Test public void aStateLineKeeps48OrDropsItsSecondSegment() {
        // a fake: the main words 6 per px of size, the second segment 6 more; the line 640
        final float[] size = {56};
        SignatureStyle.Sizer sizer = s -> size[0] = s;
        // 12 x 52 = 624 fits at 52: both kept
        float s = SignatureStyle.stateSize(both -> size[0] * (both ? 12 : 6), sizer, 56, 48, 44, 640, false);
        assertEquals(52, s, 0);
        // 16 x 48 = 768 > 640: the second goes, the main words alone at 56 (8 x 56 = 448), the sign says so
        s = SignatureStyle.stateSize(both -> size[0] * (both ? 16 : 8), sizer, 56, 48, 44, 640, false);
        assertEquals(-56, s, 0);
        // a main word alone shrinks to fit, never under the style's minimum (Kit: 40) even when it overflows there
        s = SignatureStyle.stateSize(both -> size[0] * 15.5f, sizer, 56, 48, 40, 602, true);
        assertEquals(40, s, 0);
    }

    @Test public void aLineThatFitsKeepsItsStartSize() {
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(5, 200, 4), 46, 38, 640, true, true);
        assertEquals(46, f.size, 0);
        assertEquals(240, f.logoCap, 0);
        assertTrue(f.comp);
    }

    @Test public void shrinksToTheMidpointBeforeCappingTheLogo() {
        // 46 px: 46*9 + 230 = 644 > 640; 44 px: 626 fits, above the midpoint 42, logo untouched
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(5, 230, 4), 46, 38, 640, true, true);
        assertEquals(44, f.size, 0);
        assertEquals(240, f.logoCap, 0);
    }

    @Test public void capsTheLogoAt200Then180BeforeGoingBelowTheMidpoint() {
        // at 42 px the text is 42*10 = 420; logo 240 → 660, 200 → 620 fits
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(6, 240, 4), 46, 38, 640, true, true);
        assertEquals(200, f.logoCap, 0);
        assertTrue(f.size >= 42);
        // a wider line needs the 180 cap
        f = SignatureStyle.Fit.cascade(line(6, 240, 4.8f), 46, 38, 640, true, true);
        assertEquals(180, f.logoCap, 0);
        assertTrue(f.comp);
    }

    @Test public void dropsTheCompetitionOnlyWhenTheMinimumIsTooWide() {
        // even 38 px with a 180 logo: 38*20 + 180 > 640 → the competition goes, the rest fits from 46 px
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(4, 300, 16), 46, 38, 640, true, true);
        assertFalse(f.comp);
        assertEquals(46, f.size, 0);
        assertEquals(180, f.logoCap, 0);
    }

    @Test public void withoutACompetitionTheLineStaysAtTheMinimum() {
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(30, 0, 0), 46, 38, 640, false, false);
        assertEquals(38, f.size, 0);
        assertTrue(f.comp);
    }

    @Test public void textLinesWithoutALogoNeverCapIt() {
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(17, 0, 0), 46, 38, 640, false, true);
        assertEquals(240, f.logoCap, 0);
        assertEquals(38, f.size, 0);
    }

    @Test public void theSecondarySegmentJoinsExtraAndDate() {
        CardModel m = new CardModel();
        assertEquals("", SignatureStyle.secondary(m));
        m.dateLine = "FRI 6 NOV";
        assertEquals("· FRI 6 NOV", SignatureStyle.secondary(m));
        m.stateExtra = "AET";
        assertEquals("· AET · FRI 6 NOV", SignatureStyle.secondary(m));
    }

    /** Owner, fx6: the ghost sat lower on results, so a row's crests had different heights; one position now. */
    @Test public void theGhostSitsInOnePlaceInEveryState() {
        assertEquals(64, SignatureStyle.GHOST_TOP, 0);
    }

    @Test public void needsASharpCrest() {
        ClubTokens t = new ClubTokens();
        assertFalse(new SignatureStyle().suits(t));
        t.sharpCrest = true;
        assertTrue(new SignatureStyle().suits(t));
    }

    /** FB-1: at the launcher's 0.404 the competition goes before line 4 drops under 50; the channel alone may go to 44. */
    @Test public void theCompetitionGoesBeforeLineFourGetsSmall() {
        // channel 5 px per px + a logo, competition 6 px per px: 730 at 50 with it (logo at 180); 480 at 60 without
        SignatureStyle.Fit f = SignatureStyle.Fit.cascade(line(5, 200, 6), 60, 50, 44, 640, true, true);
        assertFalse(f.comp);
        assertEquals(60f, f.size, 0);
        // a line that keeps its competition at 50 or more keeps it
        f = SignatureStyle.Fit.cascade(line(2, 180, 3), 60, 50, 44, 640, true, true);
        assertTrue(f.comp);
        assertTrue(f.size >= 50);
        // a channel too wide alone even at 50 shrinks on to 44, never below
        f = SignatureStyle.Fit.cascade(line(14, 0, 0), 60, 50, 44, 640, false, false);
        assertEquals(44f, f.size, 0);
    }

    /** LAYOUT-CALM: the LIVE pill (y 44-110) keeps the safe top; the opponent line moves down to stay 24 under it. */
    @Test public void theOpponentLineMakesRoomForTheLivePill() {
        assertEquals(182f, SignatureStyle.oppCy(CardModel.State.NEXT), 0);
        assertEquals(182f, SignatureStyle.oppCy(CardModel.State.RESULT), 0);
        assertEquals(204f, SignatureStyle.oppCy(CardModel.State.LIVE), 0);
        float pillBottom = SignatureStyle.LIVE_PILL_CY + 33, squircleTop = SignatureStyle.LIVE_OPP_CY - SignatureStyle.SQ / 2;
        assertEquals(44f, SignatureStyle.LIVE_PILL_CY - 33, 0);
        assertTrue(squircleTop - pillBottom >= 24);
        // and 24 or more over the score's capitals (y 301), the hero's baseline unchanged
        assertTrue(301 - (SignatureStyle.LIVE_OPP_CY + SignatureStyle.SQ / 2) >= 24);
        // line 4's logo box (48) keeps 24 under the hero's baseline and inside the safe bottom
        assertTrue(SignatureStyle.FOOT_CY - SignatureStyle.LOGO_H / 2 - SignatureStyle.HERO_BASE >= 24);
        assertTrue(SignatureStyle.FOOT_CY + SignatureStyle.LOGO_H / 2 <= 540 - 44);
    }
}
