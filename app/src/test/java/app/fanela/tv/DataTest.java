package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** The where-to-watch data files themselves: they parse, and every value is something the app can show as is. */
public class DataTest {
    static final Set<String> ISO = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
    static final String DATE = "\\d{4}-\\d{2}-\\d{2}";

    static String read(String name) throws Exception {
        File f = new File("src/main/res/raw/" + name);
        if (!f.exists()) f = new File("app/src/main/res/raw/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void bothFilesParse() throws Exception {
        assertFalse(SeasonTableProvider.parse(read("broadcasters.json")).isEmpty());
        assertFalse(SeasonTableProvider.parse(read("broadcasters_wikipedia.json")).isEmpty());
    }

    @Test public void everyValueIsClean() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String file : new String[]{"broadcasters.json", "broadcasters_wikipedia.json"}) {
            JSONObject comps = new JSONObject(read(file)).getJSONObject("competitions");
            for (java.util.Iterator<String> it = comps.keys(); it.hasNext(); ) {
                String comp = it.next();
                JSONObject o = comps.getJSONObject(comp);
                String where = file + " " + comp;
                long from = SeasonTableProvider.day(o.optString("from", ""), -1), until = SeasonTableProvider.day(o.optString("until", ""), -1);
                boolean none = o.has("none");
                if (!none) {
                    if (!o.optString("from").matches(DATE) || from < 0) problems.add(where + ": bad from " + o.optString("from"));
                    if (!o.optString("until").matches(DATE) || until < 0) problems.add(where + ": bad until " + o.optString("until"));
                    if (from >= until) problems.add(where + ": from is not before until");
                }
                JSONObject cs = o.getJSONObject("countries");
                if (none && cs.length() > 0) problems.add(where + ": marked 'none' but has rows");
                if (!none && cs.length() == 0) problems.add(where + ": no rows and no 'none' marker");
                for (java.util.Iterator<String> ci = cs.keys(); ci.hasNext(); ) {
                    String cc = ci.next();
                    if (!cc.matches("[A-Z]{2}") || !ISO.contains(cc)) problems.add(where + ": not an ISO country code: " + cc);
                    Object v = cs.get(cc);
                    JSONArray names = v instanceof JSONObject ? ((JSONObject) v).getJSONArray("on") : (JSONArray) v;
                    if (names.length() == 0) problems.add(where + " " + cc + ": no names");
                    Set<String> seen = new HashSet<>();
                    for (int i = 0; i < names.length(); i++) {
                        String n = names.getString(i);
                        if (n.isEmpty() || !n.equals(n.trim()) || n.contains("  ")) problems.add(where + " " + cc + ": empty or padded name '" + n + "'");
                        if (!seen.add(n)) problems.add(where + " " + cc + ": " + n + " twice");
                    }
                    if (v instanceof JSONObject) {
                        JSONObject x = (JSONObject) v;
                        for (String k : new String[]{"from", "until"})
                            if (x.has(k) && !x.optString(k).matches(DATE)) problems.add(where + " " + cc + ": bad " + k);
                    }
                }
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    /** org.json keeps the last of two equal keys silently: look for a country listed twice in the raw text. */
    @Test public void noCountryTwiceInACompetition() throws Exception {
        for (String file : new String[]{"broadcasters.json", "broadcasters_wikipedia.json"}) {
            String text = read(file);
            Matcher comp = Pattern.compile("\"countries\"\\s*:\\s*\\{(.*?)\\n\\s*\\}", Pattern.DOTALL).matcher(text);
            while (comp.find()) {
                Set<String> seen = new HashSet<>();
                Matcher cc = Pattern.compile("\"([A-Z]{2})\"\\s*:").matcher(comp.group(1));
                while (cc.find()) assertTrue(file + ": " + cc.group(1) + " twice in one competition", seen.add(cc.group(1)));
            }
        }
    }

    @Test public void everyLeagueHasAnEntryAndACheckedDate() throws Exception {
        JSONObject comps = new JSONObject(read("broadcasters.json")).getJSONObject("competitions");
        for (Leagues.League l : Leagues.ALL) {
            assertTrue(l.id + ": an entry, or one marked \"none\"", comps.has(l.id));
            JSONObject o = comps.getJSONObject(l.id);
            assertTrue(l.id + ": checked date", o.optString("checked").matches(DATE));
            assertTrue(l.id + ": sources", o.optJSONArray("sources") != null && o.getJSONArray("sources").length() > 0);
            for (int i = 0; i < o.getJSONArray("sources").length(); i++)
                assertTrue(l.id + ": source is a web page", o.getJSONArray("sources").getString(i).startsWith("https://"));
        }
        assertEquals("checked() reads them", comps.length(), SeasonTableProvider.checked(read("broadcasters.json")).size());
    }
}
