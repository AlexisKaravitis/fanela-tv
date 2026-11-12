package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Test;

/** Stream E: the bundled circuit outlines (res/raw/circuits.json, tools/circuits.py) and their fit. */
public class CircuitsTest {
    static String raw(String name) throws Exception {
        File f = new File("src/main/res/raw/" + name);
        if (!f.exists()) f = new File("app/src/main/res/raw/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void every2026CircuitHasAnOutline() throws Exception {
        Map<String, float[]> all = Circuits.parse(raw("circuits.json"));
        // the 2026 calendar's ESPN circuit ids (Las Vegas, São Paulo, Bahrain, Shanghai, Melbourne, Suzuka, Jeddah,
        // Miami, Montreal, Monaco, Barcelona, Spielberg, Silverstone, Spa, Budapest, Zandvoort, Monza, Madring, Baku,
        // Marina Bay, Austin, Mexico City, Lusail, Yas Marina)
        for (String id : new String[]{"4250", "617", "603", "619", "601", "618", "4069", "4243", "608", "606", "5826", "779",
            "611", "616", "613", "1102", "615", "605", "607", "740", "609", "782", "4211", "744"}) {
            float[] p = all.get(id);
            assertNotNull(id, p);
            assertTrue(id, p.length >= 60 && p.length <= 240);  // 30..120 points
            float maxX = 0, maxY = 0;
            for (int i = 0; i < p.length; i += 2) {
                assertTrue(p[i] >= 0 && p[i] <= 1 && p[i + 1] >= 0 && p[i + 1] <= 1);
                maxX = Math.max(maxX, p[i]); maxY = Math.max(maxY, p[i + 1]);
            }
            assertEquals(id, 1f, Math.max(maxX, maxY), 0.002f);  // the longer side spans 0..1
        }
        assertNull(all.get("3984"));  // Bahrain's outer track: a different layout, left out on purpose
    }

    @Test public void theFileCreditsTheSourceAndShipsItsLicence() throws Exception {
        String j = raw("circuits.json");
        assertTrue(j.contains("bacinger/f1-circuits") && j.contains("MIT"));
        String lic = raw("circuits_license.txt");
        assertTrue(lic.contains("Tomislav Bacinger") && lic.contains("Permission is hereby granted"));
    }

    @Test public void fitContainsAndCentres() {
        float[] square = {0, 0, 1, 0, 1, 0.5f, 0, 0.5f};  // 2:1
        float[] f = Circuits.fit(square, 100, 100, 300, 300);
        assertEquals(100, f[0], 0.01f);
        assertEquals(150, f[1], 0.01f);   // centred vertically: 200 wide, 100 tall in a 200 box
        assertEquals(300, f[2], 0.01f);
        assertEquals(250, f[5], 0.01f);
        assertNull(Circuits.fit(null, 0, 0, 1, 1));
        assertNull(Circuits.fit(new float[]{0, 0}, 0, 0, 1, 1));
    }
}
