package app.fanela.tv;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The saved ESPN answers in src/test/resources/f1ufc/ (fetched 11-12 Nov 2026, see F1-UFC-RESEARCH §8) and a fake
 * network for the feeds' refreshes (FeedResult.net), so F1Feed.fetch and UfcFeed.fetch run end to end offline.
 */
final class FeedSamples {
    private FeedSamples() { }

    static String text(String name) {
        File f = new File("src/test/resources/f1ufc/" + name);
        if (!f.exists()) f = new File("app/src/test/resources/f1ufc/" + name);
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException x) {
            throw new AssertionError("missing sample " + name, x);
        }
    }

    static JSONObject json(String name) {
        try { return new JSONObject(text(name)); } catch (Exception x) { throw new AssertionError(name, x); }
    }

    static long t(String iso) { return Sources.iso(iso); }

    /** "Fri 02:30" in Athens (whatever the JVM's default zone). */
    static String athens(long t) {
        SimpleDateFormat f = new SimpleDateFormat("EEE HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Athens"));
        return f.format(t);
    }

    /** The core athletes of the 25 standings drivers (trimmed: id, names, vehicles), by id. */
    static Map<String, JSONObject> athletes() {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        try {
            JSONArray a = json("espn_core_f1_athletes_2026.json").getJSONArray("items");
            for (int i = 0; i < a.length(); i++) out.put(a.getJSONObject(i).getString("id"), a.getJSONObject(i));
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        return out;
    }

    /**
     * A network that answers by URL prefix, or by the exact URL for a key ending in "$" (first match wins), and 404s
     * everything else; it logs every request.
     */
    static final class FakeNet implements FeedResult.Http {
        final Map<String, String> answers = new LinkedHashMap<>();
        final List<String> asked = new ArrayList<>();
        boolean down;

        FakeNet on(String urlPrefix, String body) { answers.put(urlPrefix, body); return this; }

        public String get(String url) throws IOException {
            asked.add(url);
            if (down) throw new IOException("offline");
            for (Map.Entry<String, String> e : answers.entrySet()) {
                String k = e.getKey();  // "…$": that URL exactly; otherwise a prefix
                if (k.endsWith("$") ? url.equals(k.substring(0, k.length() - 1)) : url.startsWith(k)) return e.getValue();
            }
            throw new IOException("HTTP 404");
        }

        int count(String urlPrefix) {
            int n = 0;
            for (String u : asked) if (u.startsWith(urlPrefix)) n++;
            return n;
        }

        /** Requests that were exactly this URL's prefix with no query after it (e.g. the no-date scoreboard). */
        int countExact(String url) {
            int n = 0;
            for (String u : asked) if (u.equals(url)) n++;
            return n;
        }
    }

    /** F1 core athletes behind CORE + "athletes/<id>". */
    static void athletesOn(FakeNet n) {
        for (Map.Entry<String, JSONObject> e : athletes().entrySet()) n.on(F1Feed.CORE + "athletes/" + e.getKey() + "?", e.getValue().toString());
    }
}
