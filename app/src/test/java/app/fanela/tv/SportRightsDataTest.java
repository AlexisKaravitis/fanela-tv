package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** res/raw/sport_rights.json: every row valid, dates sane, the countries covered, nothing lost by the parser. */
public class SportRightsDataTest {
    static final Set<String> F1_KINDS = new HashSet<>(Arrays.asList("*", "FP1", "FP2", "FP3", "SQ", "SPRINT", "QUAL", "RACE", "OTHER"));
    static final Set<String> UFC_SEGMENTS = new HashSet<>(Arrays.asList("*", "EARLY", "PRELIMS", "MAIN"));
    static final Set<String> APPS = new HashSet<>(Arrays.asList(Cards.MAGENTA, Cards.ANT1));  // OpenActivity.APPS' Greek apps
    static final Set<String> KEYS = new HashSet<>(Arrays.asList("kinds", "segments", "events", "on", "app", "service", "from", "until", "conf", "src"));
    /** The countries the research covered for both sports. */
    static final List<String> COVERED = Arrays.asList("GR", "GB", "IE", "US", "DE", "AT", "ES", "IT", "FR");

    static String text() throws Exception {
        File f = new File("src/main/res/raw/sport_rights.json");
        if (!f.exists()) f = new File("app/src/main/res/raw/sport_rights.json");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static boolean isDay(String s) { return s.matches("\\d{4}-\\d{2}-\\d{2}") && Sources.iso(s + "T00:00Z") > 0; }

    @Test public void everyRowIsValidAndItsDatesAreSane() throws Exception {
        JSONObject doc = new JSONObject(text());
        String updated = doc.getString("updated");
        assertTrue(isDay(updated));
        JSONObject sports = doc.getJSONObject("sports");
        assertEquals(new HashSet<>(Arrays.asList("f1", "ufc")), keys(sports));
        long upd = Sources.iso(updated + "T00:00Z");
        int rows = 0;
        for (String sport : keys(sports)) {
            JSONObject o = sports.getJSONObject(sport);
            assertTrue(sport, isDay(o.getString("checked")));
            assertTrue(sport, Sources.iso(o.getString("checked") + "T00:00Z") <= upd);
            JSONArray src = o.getJSONArray("sources");
            Set<String> sources = new HashSet<>();
            for (int i = 0; i < src.length(); i++) { assertTrue(src.getString(i).startsWith("https://")); sources.add(src.getString(i)); }
            JSONObject cs = o.getJSONObject("countries");
            boolean f1 = "f1".equals(sport);
            for (String cc : keys(cs)) {
                String where = sport + "|" + cc;
                assertTrue(where, cc.matches("[A-Z]{2}"));
                Object v = cs.get(cc);
                if (v instanceof String) {  // a copy of another country's rows, one level only
                    assertTrue(where, cs.has((String) v));
                    assertTrue(where, cs.get((String) v) instanceof JSONArray);
                    continue;
                }
                JSONArray a = (JSONArray) v;
                assertTrue(where, a.length() > 0);
                for (int i = 0; i < a.length(); i++, rows++) {
                    JSONObject r = a.getJSONObject(i);
                    for (String k : keys(r)) assertTrue(where + " unknown key " + k, KEYS.contains(k));
                    assertFalse(where + ": F1 rows use kinds, UFC rows segments", r.has(f1 ? "segments" : "kinds"));
                    JSONArray kinds = r.getJSONArray(f1 ? "kinds" : "segments");
                    assertTrue(where, kinds.length() > 0);
                    for (int k = 0; k < kinds.length(); k++)
                        assertTrue(where + " " + kinds.getString(k), (f1 ? F1_KINDS : UFC_SEGMENTS).contains(kinds.getString(k)));
                    JSONArray on = r.getJSONArray("on");
                    assertTrue(where, on.length() > 0 && on.length() <= 3);  // a card shows at most three names
                    for (int k = 0; k < on.length(); k++) {
                        assertFalse(where, on.getString(k).trim().isEmpty());
                        assertFalse(where + ": the names are joined with ·", on.getString(k).contains("·"));
                    }
                    if (r.has("app")) {
                        assertEquals(where + ": apps only in Greece (the allowlist knows the Greek apps)", "GR", cc);
                        assertTrue(where, APPS.contains(r.getString("app")));
                    }
                    if ("GR".equals(cc) && !r.optBoolean("service")) assertTrue(where + ": a Greek channel opens in an app", r.has("app"));
                    JSONArray ev = r.optJSONArray("events");
                    for (int k = 0; ev != null && k < ev.length(); k++) assertTrue(where, ev.getString(k).matches("\\d+"));
                    // dates: a start always; an end on or after the check, at most ten years on
                    assertTrue(where, isDay(r.getString("from")));
                    long from = Sources.iso(r.getString("from") + "T00:00Z");
                    assertTrue(where, from >= Sources.iso("2025-01-01T00:00Z") && from <= upd);
                    if (r.has("until")) {
                        assertTrue(where, isDay(r.getString("until")));
                        long until = Sources.iso(r.getString("until") + "T00:00Z");
                        assertTrue(where + ": until before from", until >= from);
                        assertTrue(where + ": already over when checked", until >= upd);
                        assertTrue(where, until <= upd + 10L * 366 * 86400000L);
                    }
                    assertTrue(where, Arrays.asList("high", "medium", "low").contains(r.getString("conf")));
                    assertTrue(where + ": the row's evidence is one of the sources", sources.contains(r.getString("src")));
                }
            }
        }
        assertTrue(rows >= 20);
    }

    @Test public void theCountriesAreCovered() throws Exception {
        Map<String, List<SportWhere.Row>> t = SportWhere.parse(text());
        for (String sport : new String[]{"f1", "ufc"}) {
            for (String cc : COVERED) assertNotNull(sport + "|" + cc, t.get(sport + "|" + cc));
        }
        assertNotNull("Cyprus: F1 on Omega TV", t.get("f1|CY"));
        assertEquals("Cyprus UFC: check your provider's guide", null, t.get("ufc|CY"));
        // Greece: every F1 session and every UFC segment has a row
        for (String k : SportRightsDataTest.F1_KINDS) {
            if ("*".equals(k)) continue;
            boolean found = false;
            for (SportWhere.Row r : t.get("f1|GR")) found |= r.kinds.contains("*") || r.kinds.contains(k);
            assertTrue(k, found);
        }
        for (String k : new String[]{"EARLY", "PRELIMS", "MAIN"}) {
            boolean found = false;
            for (SportWhere.Row r : t.get("ufc|GR")) found |= r.kinds.contains("*") || r.kinds.contains(k);
            assertTrue(k, found);
        }
    }

    @Test public void theParserKeepsEveryRow() throws Exception {
        JSONObject sports = new JSONObject(text()).getJSONObject("sports");
        Map<String, List<SportWhere.Row>> t = SportWhere.parse(text());
        int expected = 0;
        for (String sport : keys(sports)) {
            JSONObject cs = sports.getJSONObject(sport).getJSONObject("countries");
            for (String cc : keys(cs)) {
                Object v = cs.get(cc);
                JSONArray a = v instanceof String ? cs.getJSONArray((String) v) : (JSONArray) v;
                assertEquals(sport + "|" + cc, a.length(), t.get(sport + "|" + cc).size());
                expected++;
            }
        }
        assertEquals(expected, t.size());
        SportWhere.Row ant1 = t.get("f1|GR").get(0);
        assertEquals(Arrays.asList("RACE", "SPRINT"), ant1.kinds);
        assertEquals(Arrays.asList("ANT1"), ant1.on);
        assertEquals(Cards.MAGENTA, ant1.app);
        assertFalse(ant1.service);
        assertEquals(Sources.iso("2026-01-01T00:00Z"), ant1.from);
        assertEquals(Sources.iso("2028-12-31T00:00Z"), ant1.until);
        SportWhere.Row plus = t.get("f1|GR").get(1);
        assertEquals(Arrays.asList("ANT1+"), plus.on);
        assertEquals(Cards.ANT1, plus.app);
        assertTrue(plus.service);
        assertEquals(Long.MAX_VALUE, t.get("f1|DE").get(0).until);  // no published end: open
    }

    private static Set<String> keys(JSONObject o) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) out.add(it.next());
        return out;
    }
}
