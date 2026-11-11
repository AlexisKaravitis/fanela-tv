package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The club-colour tokens of the app screens: the field behind the hero and the ink on it. */
public class FieldColorsTest {
    static void near(int want, int got, int tol) {
        for (int sh = 0; sh <= 16; sh += 8)
            assertTrue(String.format("#%06X vs #%06X", want & 0xFFFFFF, got & 0xFFFFFF), Math.abs(((want >> sh) & 0xFF) - ((got >> sh) & 0xFF)) <= tol);
    }

    static double wcag(int a, int b) { return FieldColors.wcag(a, b); }

    @Test public void olympiacosRedIsDeepenedAsTheSpecSays() {
        FieldColors f = FieldColors.of(0xFFE2001A, 0xFFFFFFFF);
        near(0xFF9C0F1C, f.field, 6);   // spec: #9C0F1C
        near(0xFF5A0A10, f.field2, 6);  // spec: #5A0A10
        assertEquals(FieldColors.INK_LIGHT, f.ink);
        assertEquals(0xFFE2001A, f.primary);
    }

    @Test public void blackKitsAreCharcoal() {
        FieldColors paok = FieldColors.of(0xFF000000, 0xFFFFFFFF);
        assertTrue(paok.black);
        assertEquals(0xFF2A2A2E, paok.field);
        assertEquals(FieldColors.INK_LIGHT, paok.ink);
        assertEquals(0, paok.glow);
    }

    /** FB-4: a white kit is a dark warm platter lit warm (as its cards), never an ivory field with dark text. */
    @Test public void whiteKitsAreADarkPlatterLitWarm() {
        FieldColors real = FieldColors.of(0xFFFFFFFF, 0xFFFEBE10);
        assertTrue(real.white);
        assertEquals(0xFF34322E, real.field);
        assertEquals(0xFF1A1917, real.field3);
        assertEquals(FieldColors.INK_LIGHT, real.ink);
        assertEquals(0x0EFFFFFF, real.stripe);  // white 5.5 %, as on every dark field
        assertEquals(ClubTokens.LIGHT_WHITE, real.glow);
        assertEquals(0.22f, real.glowA, 0.001f);
        assertEquals(0xFFFFFFFF, real.primary);  // the rail marker and brand tint keep the kit's own colour
        // the ink reads on every part of the field, the warm light's centre included (WCAG AA 4.5)
        int lit = ClubTokens.mix(real.field, real.glow, real.glowA);
        assertTrue(wcag(lit, FieldColors.INK_LIGHT) >= 4.5);
        assertTrue(wcag(ClubTokens.mix(lit, 0xFFFFFFFF, 0.075), FieldColors.INK_LIGHT) >= 4.5);  // under a focus fill too
        for (int c : new int[]{real.field, real.field2, real.field3}) assertTrue(wcag(c, FieldColors.INK_LIGHT) >= 7);
        for (float y = 0; y <= 1000; y += 20) assertEquals(0xFFF1F1F1, real.inkAt(y));
        FieldColors ivory = FieldColors.of(0xFFDAD6CE, 0xFF141416);  // an off-white kit too
        assertTrue(ivory.white);
        assertEquals(FieldColors.INK_LIGHT, ivory.ink);
    }

    @Test public void brightKitsDarkenSoOffWhiteTextReads() {
        FieldColors aek = FieldColors.of(0xFFFFD200, 0xFF000000);
        assertEquals(FieldColors.INK_LIGHT, aek.ink);
        assertTrue(ClubTokens.contrast(aek.field, FieldColors.INK_LIGHT) >= 3);
        // FB-4 check: yellow and sky-blue kits stay chromatic (their hue, not a white kit's platter) and the ink keeps
        // WCAG AA on the brightest part of the field, its stripes included
        for (int kit : new int[]{0xFFFFD200, 0xFFF5C800, 0xFFFDE100, 0xFFFFE600, 0xFF6CABDD, 0xFF87CEEB, 0xFFFF7F00, 0xFFE2001A, 0xFF007A3D}) {
            FieldColors f = FieldColors.of(kit, 0xFF000000);
            assertTrue(!f.white && !f.black && f.glow == 0);
            assertTrue(String.format("#%06X", kit & 0xFFFFFF), wcag(ClubTokens.mix(f.field, 0xFFFFFFFF, 0.055), FieldColors.INK_LIGHT) >= 4.5);
            assertTrue(ClubTokens.hueDist(ClubTokens.hue(kit), ClubTokens.hue(f.field)) <= 6);
        }
        // the usual kits keep the spec's tokens (they read already)
        near(0xFF9C0F1C, FieldColors.of(0xFFE2001A, 0).field, 6);
    }

    /**
     * P2a: a yellow field is darkened only until the hero's eyebrow (the text colour at 80 %) reads at 4.5:1 on its top
     * colour, and no further: Dortmund #FDE100 is about #665A0E at the top and #554A0C at the hero's middle.
     */
    @Test public void yellowDarkensOnlyUntilTheEyebrowReads() {
        FieldColors bvb = FieldColors.of(0xFFFDE100, 0xFF111111);
        near(0xFF665A0E, bvb.field, 6);
        near(0xFF554A0C, bvb.at(200), 6);
        assertEquals(FieldColors.EYEBROW, bvb.eyebrow);
        for (int kit : new int[]{0xFFFDE100, 0xFFFFD200, 0xFFF5C800, 0xFFFFE600, 0xFF6CABDD, 0xFFFF7F00}) {
            FieldColors f = FieldColors.of(kit, 0xFF000000);
            String k = String.format("#%06X", kit & 0xFFFFFF);
            assertTrue(k, FieldColors.eyebrowContrast(f.field, f.eyebrow) >= FieldColors.EYEBROW_MIN);
            // not darker than it needs: a step and a bit lighter, the eyebrow would no longer read
            double d = dOf(kit, f), base = ClubTokens.luminance(kit) > 0.35 ? 0.6 : 0.7;
            if (d < 0.99) assertTrue(k, FieldColors.eyebrowContrast(FieldColors.hsv(kit, 0.9, base * (d + 0.012)), FieldColors.EYEBROW) < FieldColors.EYEBROW_MIN);
        }
        // the eyebrow lift (#F2F0E8, opaque) reads better than the 80 % eyebrow on the darkest field a yellow may get
        int darkest = FieldColors.hsv(0xFFFDE100, 0.9, 0.6 * FieldColors.DARKEST);
        assertTrue(FieldColors.eyebrowContrast(darkest, FieldColors.EYEBROW_LIFT) > FieldColors.eyebrowContrast(darkest, FieldColors.EYEBROW));
        assertTrue(FieldColors.eyebrowContrast(darkest, FieldColors.EYEBROW_LIFT) >= 4.5);
        // red keeps its spec field and the plain eyebrow
        assertEquals(FieldColors.EYEBROW, FieldColors.of(0xFFE2001A, 0).eyebrow);
    }

    /** The darkening factor a field came out with (its value over the kit's value at 60 % / 70 %). */
    private static double dOf(int kit, FieldColors f) {
        double base = ClubTokens.luminance(kit) > 0.35 ? 0.6 : 0.7;
        double vk = Math.max(ClubTokens.r(kit), Math.max(ClubTokens.g(kit), ClubTokens.b(kit))) / 255.0;
        double vf = Math.max(ClubTokens.r(f.field), Math.max(ClubTokens.g(f.field), ClubTokens.b(f.field))) / 255.0;
        return vf / vk / base;
    }

    @Test public void theFieldFallsToNearBlackBelowTheHero() {
        FieldColors f = FieldColors.of(0xFFE2001A, 0xFFFFFFFF);
        assertEquals(FieldColors.BG, f.at(700));
        assertEquals(0f, FieldColors.fall(300), 0);
        assertEquals(0.55f, FieldColors.fall(508.5f), 0.01f);
        assertEquals(0xFFF1F1F1, f.inkAt(800));
    }
}
