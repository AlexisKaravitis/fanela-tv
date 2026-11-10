package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/** Which kit-coloured app icon each club gets, and that every kit has its art and its launcher entry. */
public class BrandTest {
    @Test public void greekClubs() {
        assertEquals("Olympiacos", "red", Brand.kit(0xD0102E));
        assertEquals("PAOK", "black", Brand.kit(0x111111));
        assertEquals("AEK", "yellow", Brand.kit(0xF5C800));
        assertEquals("Aris", "yellow", Brand.kit(0xFFD200));
        assertEquals("Panathinaikos", "green", Brand.kit(0x007A3D));
        assertEquals("Atromitos: the original blue", "", Brand.kit(0x1D5FBF));
        assertEquals("Volos", "navy", Brand.kit(0x1E3A8A));
        assertEquals("AEL", "maroon", Brand.kit(0x7A1F2B));
    }

    @Test public void otherClubs() {
        assertEquals("Real Madrid", "white", Brand.kit(0xF4F4F4));
        assertEquals("Manchester City", "sky", Brand.kit(0x6CABDD));
        assertEquals("Arsenal", "red", Brand.kit(0xEF0107));
        assertEquals("Valencia Basket", "orange", Brand.kit(0xF47A20));
        assertEquals("Fiorentina-like purple", "purple", Brand.kit(0x5B2C86));
    }

    @Test public void everyKitHasItsArtAndLauncherEntry() throws Exception {
        File res = new File("src/main/res");
        if (!res.exists()) res = new File("app/src/main/res");
        String manifest = new String(Files.readAllBytes(new File(res.getParentFile(), "AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
        assertEquals(Brand.NAMES.length, Brand.COLORS.length);
        assertTrue("the original is enabled", manifest.contains("android:name=\".Launch\""));
        for (String k : Brand.NAMES) {
            if (k.isEmpty()) continue;
            assertTrue(k + " banner", new File(res, "drawable-xhdpi/banner_" + k + ".png").exists());
            for (String dpi : new String[]{"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"})
                assertTrue(k + " icon " + dpi, new File(res, "mipmap-" + dpi + "/ic_launcher_" + k + ".png").exists());
            assertTrue(k + " launcher entry", manifest.contains("android:name=\".Launch_" + k + "\""));
            assertEquals("app.fanela.tv.Launch_" + k, Brand.alias(k));
        }
        assertEquals("one launcher entry only in the manifest is on", 1, manifest.split("android:enabled=\"true\"", -1).length - 1);
    }
}
