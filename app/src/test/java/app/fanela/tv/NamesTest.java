package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

/**
 * Club names in Greek: a league's grid is either all Greek or, for the leagues the Greek press has no settled forms
 * for, all Latin. Never a mix. The club lists are a snapshot of the feeds (test resources, 2026-11-11).
 */
public class NamesTest {
    static final List<String> LATIN = Arrays.asList("usa.1", "bra.1", "arg.1", "ksa.1");
    static final String GREEK = ".*[\\u0370-\\u03FF].*";

    @After public void english() { I18n.use("en"); }

    static JSONObject leagues() throws Exception {
        File f = new File("src/test/resources/league_clubs.json");
        if (!f.exists()) f = new File("app/src/test/resources/league_clubs.json");
        return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getJSONObject("leagues");
    }

    @Test public void noGridMixesAlphabets() throws Exception {
        JSONObject l = leagues();
        List<String> problems = new ArrayList<>();
        for (Leagues.League lg : Leagues.ALL) {
            assertTrue(lg.id + " is in the snapshot", l.has(lg.id));
            JSONArray a = l.getJSONArray(lg.id);
            for (int i = 0; i < a.length(); i++) {
                String n = a.getString(i);
                if (n.matches("(?i)TBD( .*)?")) continue;  // the feed's placeholders, never listed (Teams)
                boolean greek = I18n.grName(n).matches(GREEK);
                if (LATIN.contains(lg.id) && greek) problems.add(lg.id + ": " + n + " has a Greek name in a Latin league");
                if (!LATIN.contains(lg.id) && !greek) problems.add(lg.id + ": " + n + " has no Greek name");
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test public void greekPressForms() {
        I18n.use("el");
        assertEquals("Ατλέτικο Μαδρίτης", I18n.team("Atlético Madrid"));
        assertEquals("Ατλέτικο", I18n.shortTeam("Atlético Madrid"));
        assertEquals("Λος Άντζελες Λέικερς", I18n.team("Los Angeles Lakers"));
        assertEquals("Λέικερς", I18n.shortTeam("Los Angeles Lakers"));
        assertEquals("Γκλάντμπαχ", I18n.shortTeam("Borussia Mönchengladbach"));
        assertEquals("Paris FC is not the basketball club", "Παρί FC", I18n.team("Paris FC"));
        assertEquals("Ολυμπιακός", I18n.team("Olympiacos"));
        I18n.use("en");
        assertEquals("Lakers", I18n.shortTeam("Los Angeles Lakers"));
        assertEquals("Atlético Madrid", I18n.team("Atlético Madrid"));
        assertEquals("a Greek guide title still finds the English name", "Atlético Madrid", I18n.team("Ατλέτικο Μαδρίτης"));
    }

    @Test public void shortFormsStayInTheirAlphabet() throws Exception {
        JSONObject l = leagues();
        for (java.util.Iterator<String> it = l.keys(); it.hasNext(); ) {
            JSONArray a = l.getJSONArray(it.next());
            for (int i = 0; i < a.length(); i++) {
                String n = a.getString(i);
                I18n.use("en");
                String en = I18n.shortTeam(n);
                assertFalse(n + " -> " + en, en.matches(GREEK));
                assertFalse(n + " short form is empty", en.trim().isEmpty());
                I18n.use("el");
                String el = I18n.shortTeam(n), full = I18n.team(n);
                assertEquals(n + ": a short Greek form of a Greek name stays Greek", full.matches(GREEK), el.matches(GREEK));
            }
        }
    }
}
