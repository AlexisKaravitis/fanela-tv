package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** The shared channel line's rules (fx5): logos that read at 0.404 or the name in type, and the 44 px floor. */
public class CardKitTest {
    @Test public void aNearSquareLogoIsSetInType() {
        assertNull("ERT, SPORT FM (1.5:1)", CardKit.logoFit(1.5f, 240, 48, 640));
        assertNull(CardKit.logoFit(2.4f, 240, 48, 640));
        assertNotNull("Magenta Sport (3.4:1)", CardKit.logoFit(3.4f, 240, 48, 640));
    }

    @Test public void aWideLogoTakesTheFreeWidthToReach44() {
        // Magenta Sport at 48 tall: 163 wide, untouched
        float[] f = CardKit.logoFit(3.4f, 240, 48, 640);
        assertEquals(48, f[1], 0.01);
        // Novasports Prime (11.3:1) capped at 240: 21 px tall; Signature's line leaves 508 beside its chip: 44 tall
        f = CardKit.logoFit(11.3f, 240, 48, 508);
        assertEquals(44, f[1], 0.01);
        assertEquals(497.2f, f[0], 0.1f);
        // in Clash's gap (317) it can't: the name in type
        assertNull(CardKit.logoFit(11.3f, 240, 52, 317));
    }

    /** A fake channel line: `perPx` wide per px of size, LIKELY `likely` more in the full form, `untracked` less in TIGHT. */
    static CardKit.ChannelWidth line(float perPx, float likely, float untracked) {
        return (size, cap, form) -> size * (perPx + (form == CardKit.FULL ? likely : 0) - (form == CardKit.TIGHT ? untracked : 0));
    }

    @Test public void aWordsLineDropsLikelyBeforeGoingUnder44() {
        // LIKELY TNT SPORTS OR PRIME VIDEO in Kit's 602: 16.6 per px with LIKELY (731 at 44); 12.5 without (600 at 48)
        CardKit.ChannelSet s = CardKit.fitChannel(line(12.5f, 4.1f, 0.6f), 56, 602, false);
        assertTrue(s.fits);
        assertEquals(CardKit.BARE, s.form);
        assertEquals(48, s.size, 0);
        // a line that fits as written keeps LIKELY
        s = CardKit.fitChannel(line(6, 3, 0), 56, 602, false);
        assertEquals(CardKit.FULL, s.form);
        assertEquals(56, s.size, 0);
    }

    // ---------- the competition (LAYOUT-COMPETITION §3) ----------

    /** A fake measure: `perChar` px per character per px of size, plus the tracking between characters. */
    static CardKit.CompMeasure chars(final float perChar) {
        return (s, size, track) -> s.length() * perChar * size + (s.length() - 1) * track * size;
    }

    static final float[] ONE = {0.05f};

    @Test public void theCompetitionKeeps48WhenItFits() {
        // EUROLEAGUE, 10 chars at 0.55: 264 + 9 x 2.4 = 285.6 at 48 (the spec measured 283)
        CardKit.CompFit f = CardKit.fitComp(chars(0.55f), Arrays.asList("EUROLEAGUE"), 624, 48, 44, ONE);
        assertEquals("EUROLEAGUE", f.text);
        assertEquals(48, f.size, 0);
        assertEquals(0.05f, f.track, 0);
    }

    @Test public void itShrinks2PxAStepTo44ThenTakesItsShortName() {
        List<String> names = Arrays.asList("CHAMPIONS LEAGUE", "CHAMPIONS");
        // 16 chars: 0.55 x 16 = 8.8 + 15 x .05 = 9.55 per px of size: 458 at 48, 439 at 46, 420 at 44
        CardKit.CompFit f = CardKit.fitComp(chars(0.55f), names, 440, 48, 44, ONE);
        assertEquals("CHAMPIONS LEAGUE", f.text);
        assertEquals(46, f.size, 0);
        f = CardKit.fitComp(chars(0.55f), names, 421, 48, 44, ONE);
        assertEquals(44, f.size, 0);
        // under 44 never: C2 from 48 (9 chars: 4.95 + .4 = 5.35 per px, 257 at 48)
        f = CardKit.fitComp(chars(0.55f), names, 375, 48, 44, ONE);
        assertEquals("CHAMPIONS", f.text);
        assertEquals(48, f.size, 0);
    }

    @Test public void itIsNotDrawnRatherThanCutOrSetUnder44() {
        // BASKET LEAGUE has no short name: past 44 it goes
        assertNull(CardKit.fitComp(chars(0.55f), Arrays.asList("BASKET LEAGUE"), 300, 48, 44, ONE));
        // nothing to draw, or no room at all
        assertNull(CardKit.fitComp(chars(0.55f), Collections.<String>emptyList(), 600, 48, 44, ONE));
        assertNull(CardKit.fitComp(chars(0.55f), Arrays.asList("BCL"), 0, 48, 44, ONE));
        // a start under the floor is never tried
        assertNull(CardKit.fitComp(chars(0.55f), Arrays.asList("BCL"), 600, 42, 40, ONE));
    }

    @Test public void tracksAreTriedInTurnEachFrom48To44() {
        // two tracks (.05, then .02): every size at .05 before .02, then the short name
        float[] two = {0.05f, 0.02f};
        // 16 chars: at .02, 8.8 + .3 = 9.1 per px: 437 at 48; at .05 44 is 420
        CardKit.CompFit f = CardKit.fitComp(chars(0.55f), Arrays.asList("CONFERENCE LEAGU"), 437, 48, 44, two);
        assertEquals("all of .05 first", 0.05f, f.track, 0);
        assertEquals(44, f.size, 0);
        f = CardKit.fitComp(chars(0.55f), Arrays.asList("CONFERENCE LEAGU"), 410, 48, 44, two);
        assertEquals(0.02f, f.track, 0);
        assertEquals(44, f.size, 0);  // 400.4
    }

    /** The Ticket's order (§8): 46 /.12 → 46 /.08 → 44 /.06 → 44 /.02, then the short name from 46 /.12. */
    @Test public void theTicketsStepsKeepTheirOrder() {
        float[][] steps = TicketStyle.EYEBROW_STEPS;
        assertEquals(4, steps.length);
        assertEquals(46, steps[0][0], 0); assertEquals(0.12f, steps[0][1], 0);
        assertEquals(44, steps[3][0], 0); assertEquals(0.02f, steps[3][1], 0);
        // CHAMPIONS LEAGUE (16): 46/.12 = 11.4 x 46 = 524 > 500; 46/.08 = 10.8 x 46 = 497 fits
        CardKit.CompFit f = CardKit.fitComp(chars(0.6f), Arrays.asList("CHAMPIONS LEAGUE", "CHAMPIONS"), 500, steps);
        assertEquals(46, f.size, 0);
        assertEquals(0.08f, f.track, 0);
        // nothing of C1 fits 300: C2 at 46 /.12 (9: 5.4 + .96 = 6.36 x 46 = 293)
        f = CardKit.fitComp(chars(0.6f), Arrays.asList("CHAMPIONS LEAGUE", "CHAMPIONS"), 300, steps);
        assertEquals("CHAMPIONS", f.text);
        assertEquals(0.12f, f.track, 0);
        // Clash: 48 → 44 at .05, then 44 at .02 only (never 48 at .02)
        assertEquals(0.02f, ClashStyle.COMP_STEPS[ClashStyle.COMP_STEPS.length - 1][1], 0);
        assertEquals(44, ClashStyle.COMP_STEPS[ClashStyle.COMP_STEPS.length - 1][0], 0);
        for (float[] st : ClashStyle.COMP_STEPS) assertTrue(st[0] >= CardKit.COMP_MIN);
    }

    @Test public void thenItLosesItsTrackingAndNeverGoesUnder44() {
        // SKY SPORTS MAIN EVENT 401 in Kit: 617 at 44 tracked; untracked 597 at 46
        CardKit.ChannelSet s = CardKit.fitChannel(line(14.02f, 0, 1.05f), 56, 602, false);
        assertTrue(s.fits);
        assertEquals(CardKit.TIGHT, s.form);
        assertEquals(46, s.size, 0);
        // nothing fits: 44, the last form, fits false (the style clips it)
        s = CardKit.fitChannel(line(30, 0, 1), 56, 602, false);
        assertFalse(s.fits);
        assertEquals(44, s.size, 0);
    }
}
