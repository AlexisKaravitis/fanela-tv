package app.fanela.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** QA3G-5: line-art crests whose emblem is white inside (Olympiacos) are filled white; every other crest is left alone. */
public class CrestFillTest {
    static final int RED = 0xFFE2001A, CLEAR = 0x00000000;

    /** A w x w image: a ring of red between radii r0 and r1 around the centre (an open one: a gap on the right). */
    static int[] ring(int w, float r0, float r1, boolean gap) {
        int[] px = new int[w * w];
        float c = (w - 1) / 2f;
        for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) {
            double d = Math.hypot(x - c, y - c);
            boolean on = d >= r0 && d <= r1 && !(gap && x > c && Math.abs(y - c) < 3);
            px[y * w + x] = on ? RED : CLEAR;
        }
        return px;
    }

    @Test public void aRingIsFilledWhiteInsideAndStaysClearOutside() {
        int w = 41;
        int[] px = ring(w, 15, 19, false);
        assertTrue(CrestFill.enclosedShare(px, w, w) > CrestFill.MIN_SHARE);
        assertTrue(CrestFill.fill(px, w, w));
        assertEquals(CrestFill.WHITE, px[20 * w + 20]);  // the centre
        assertEquals(CLEAR, px[0]);                       // a corner: outside
        assertEquals(RED, px[20 * w + 2]);                // the line itself
    }

    @Test public void theLinesInnerEdgeBlendsOverTheWhite() {
        int w = 41;
        int[] px = ring(w, 15, 19, false);
        px[20 * w + 6] = 0xC8E2001A;  // a half-covered pixel of the line's inner edge (r = 14), next to the enclosed area
        px[20 * w + 1] = 0xC8E2001A;  // and one on its outer edge (r = 19), next to the outside
        CrestFill.fill(px, w, w);
        int inner = px[20 * w + 6];
        assertEquals(0xFF, inner >>> 24);                    // opaque now: red over white
        assertTrue(((inner >> 8) & 0xFF) > 0x20);            // lighter than the red (white shows through)
        assertEquals(0xC8E2001A, px[20 * w + 1]);            // the outer edge keeps its alpha: no halo
        assertEquals(0xFFFFFFFF, CrestFill.overWhite(0x00123456));
        assertEquals(0xFFE2001A, CrestFill.overWhite(0xFFE2001A));
    }

    @Test public void anOpenOutlineIsLeftAlone() {
        int w = 41;
        int[] px = ring(w, 15, 19, true), was = px.clone();
        assertEquals(0, CrestFill.enclosedShare(px, w, w), 0);
        assertFalse(CrestFill.fill(px, w, w));
        assertArrayEquals(was, px);
    }

    /** A filled shield with a letter's counter (a small hole) is an ordinary crest: untouched. */
    @Test public void smallCountersAreLeftAlone() {
        int w = 40;
        int[] px = new int[w * w];
        for (int y = 4; y < 36; y++) for (int x = 4; x < 36; x++) px[y * w + x] = 0xFF006633;
        for (int y = 16; y < 22; y++) for (int x = 16; x < 22; x++) px[y * w + x] = CLEAR;  // the counter: 36 of 1024
        int[] was = px.clone();
        assertTrue(CrestFill.enclosedShare(px, w, w) < CrestFill.MIN_SHARE);
        assertFalse(CrestFill.fill(px, w, w));
        assertArrayEquals(was, px);
    }

    @Test public void anEmptyImageIsLeftAlone() {
        int[] px = new int[16];
        assertFalse(CrestFill.fill(px, 4, 4));
    }
}
