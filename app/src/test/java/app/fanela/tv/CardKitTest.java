package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
