package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.junit.Test;

/**
 * Guide.sport24 keeps the Motorsport and Combat airings for F1 and UFC (SportWhere), and the clubs' guide never takes
 * them: Guide.find and Guide.discover only use Football and Basketball. Samples: Sport24 on 8 and 15 Nov 2026.
 */
public class GuideGenreTest {
    static String res(String name) throws Exception {
        File f = new File("src/test/resources/where/" + name);
        if (!f.exists()) f = new File("app/src/test/resources/where/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static SimpleDateFormat athens() {
        SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        ts.setTimeZone(TimeZone.getTimeZone("Europe/Athens"));
        return ts;
    }

    /** A Sport24 day as Guide.sport24 keeps it. */
    static List<Guide.Airing> sport24(String name) throws Exception {
        JSONArray a = new JSONArray(res(name));
        SimpleDateFormat ts = athens();
        List<Guide.Airing> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Guide.Airing x = Guide.sport24Airing(a.getJSONObject(i), ts);
            if (x != null) out.add(x);
        }
        return out;
    }

    static Guide.Airing byId(List<Guide.Airing> l, String id) {
        for (Guide.Airing a : l) if (id.equals(a.id)) return a;
        return null;
    }

    @Test public void formulaOneAndUfcAiringsAreKeptWithTheirGenres() throws Exception {
        List<Guide.Airing> nov8 = sport24("sport24_guide_2026-11-08.json");
        Guide.Airing f1 = byId(nov8, "133628");
        assertNotNull(f1);
        assertEquals(SportWhere.GENRE_MOTOR, f1.genre);
        assertEquals("Formula 1 2026 - Γκραν Πρι Σάο Πάουλο", f1.title);
        assertEquals("ANT1", f1.channel);
        assertEquals(Sources.iso("2026-11-08T16:45:00Z"), f1.start);  // 18:45 Athens, for the 19:00 race
        assertEquals("Αγώνας", f1.tag);  // the sport's own tag is a genre, never the tag
        assertTrue(f1.live);
        Guide.Airing fn = byId(nov8, "133633");
        assertEquals(SportWhere.GENRE_COMBAT, fn.genre);
        assertEquals("UFC Fight Night 2026", fn.title);
        assertEquals("Magenta Sport 6", fn.channel);
        assertEquals(Sources.iso("2026-11-08T01:00:00Z"), fn.start);
        assertEquals("Βάλντο Κορτές Ακόστα - Τζόνι Γουόκερ", fn.tag);
        assertEquals(SportWhere.GENRE_MOTOR, byId(nov8, "133656").genre);  // MotoGP: kept, SportWhere skips it by title
        assertNull("a studio show has no sport tag", byId(nov8, "133629"));

        Guide.Airing u334 = byId(sport24("sport24_guide_2026-11-15.json"), "133881");
        assertEquals(SportWhere.GENRE_COMBAT, u334.genre);
        assertEquals("334: Σιρίλ Γκάνε - Τζος Χόκιτ", u334.tag);
        assertEquals(Sources.iso("2026-11-15T02:00:00Z"), u334.start);
    }

    @Test public void footballAndBasketballAreReadAsBefore() throws Exception {
        for (String day : new String[]{"sport24_guide_2026-11-08.json", "sport24_guide_2026-11-15.json"}) {
            JSONArray a = new JSONArray(res(day));
            int fb = 0, kept = 0;
            for (int i = 0; i < a.length(); i++) {
                String tags = a.getJSONObject(i).optJSONArray("tags") == null ? "" : a.getJSONObject(i).optJSONArray("tags").toString();
                if (tags.contains("\"podosfairo\"") || tags.contains("\"basket\"")) fb++;
            }
            for (Guide.Airing x : sport24(day)) {
                assertTrue(x.genre, x.genre.equals("Football") || x.genre.equals("Basketball")
                    || x.genre.equals(SportWhere.GENRE_MOTOR) || x.genre.equals(SportWhere.GENRE_COMBAT));
                if (x.genre.equals("Football") || x.genre.equals("Basketball")) kept++;
            }
            assertEquals(day, fb, kept);  // every football and basketball listing, as before
            assertTrue(fb > 0);
        }
    }

    @Test public void theClubsGuideNeverTakesAMotorsportOrCombatAiring() throws Exception {
        // A contrived club whose Greek name starts like the F1 title's second side ("Γκραν Πρι Σάο Πάουλο") and a UFC
        // tag: without the genre gate, both would match by name.
        long now = System.currentTimeMillis();
        List<Guide.Airing> air = new ArrayList<>();
        for (Guide.Airing a : sport24("sport24_guide_2026-11-08.json")) {
            if (!SportWhere.GENRE_MOTOR.equals(a.genre) && !SportWhere.GENRE_COMBAT.equals(a.genre)) continue;
            a.start = now + 86400000L;  // inside discover's window
            air.add(a);
        }
        assertFalse(air.isEmpty());
        Teams.Club k = new Teams.Club();
        k.id = "999"; k.name = "Grand"; k.gr = "Γκραν Πρι"; k.hasBasket = true;
        assertTrue("the name alone would match", Guide.firstTeam("Formula 1 2026 - Γκραν Πρι Σάο Πάουλο", k.gr));
        assertTrue(Guide.discover(new ArrayList<Match>(), air, k, true).isEmpty());
        for (String team : new String[]{"FC", "BC"}) {
            Match m = new Match();
            m.club = k.id; m.team = team; m.home = "Grand"; m.away = "Σάο Πάουλο"; m.clubIsHome = true; m.state = "pre";
            m.start = now + 86400000L; m.league = "gre.1"; m.comp = "gre.1";
            assertNull(Guide.find(air, m, k.gr, 2 * 3600000L));
        }
    }
}
