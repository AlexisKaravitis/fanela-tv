package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The header logo's shirt in the favourite club's colours. */
public class LogoTest {
    static final int OLY_RED = 0xD0102E, WHITE = 0xFFFFFF;

    @Test public void shirtBecomesKitTrimBecomesSecondColour() {
        assertEquals("the blue shirt takes the kit colour", 0xFF000000 | OLY_RED, Logo.recolor(0xFF000000 | Logo.BLUE, OLY_RED, WHITE));
        assertEquals("the white collar takes the trim", 0xFFFFFFFF, Logo.recolor(0xFFFFFFFF, OLY_RED, WHITE));
        assertEquals("AEK: yellow shirt, black trim", 0xFF111111, Logo.recolor(0xFFFFFFFF, 0xF5C800, 0x111111));
    }

    @Test public void edgesKeepTheirAlphaAndBlend() {
        assertEquals("transparent stays transparent", 0, Logo.recolor(0, OLY_RED, WHITE));
        int edge = Logo.recolor(0x80000000 | Logo.BLUE, OLY_RED, WHITE);
        assertEquals(0x80, edge >>> 24);
        assertEquals(OLY_RED, edge & 0xFFFFFF);
        int half = Logo.recolor(0xFF000000 | Logo.mix(Logo.BLUE, WHITE, 0.5f), OLY_RED, WHITE);  // anti-aliased collar edge
        assertEquals(Logo.mix(OLY_RED, WHITE, 0.5f), half & 0xFFFFFF, 0x030303);
    }

    @Test public void clubsWithoutTableColoursUseTheFeedsColour() {
        assertArrayEquals("Badges' colours when it knows the club", new int[]{OLY_RED, WHITE}, Logo.shirt(new int[]{0xFF000000 | OLY_RED, 0xFFFFFFFF}, "ffffff"));
        assertArrayEquals("a dark feed colour gets white trim", new int[]{0x003da6, WHITE}, Logo.shirt(null, "003da6"));
        assertArrayEquals("a light one gets dark trim", new int[]{0xffff00, 0x111111}, Logo.shirt(null, "ffff00"));
        assertArrayEquals("no colour: the original blue", new int[]{Logo.BLUE, WHITE}, Logo.shirt(null, ""));
    }

    @Test public void luminance() {
        assertEquals(0, Logo.luminance(0), 1e-9);
        assertEquals(1, Logo.luminance(WHITE), 1e-9);
        assertTrue("PAOK black needs the outline", Logo.luminance(0x111111) < 0.05);
        assertTrue(Logo.luminance(OLY_RED) > 0.05);
    }
}
