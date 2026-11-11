package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Store.load is memoised on the file: callers still get their own copies, and every change to the file is seen. */
public class StoreTest {
    private File dir;
    private ContextWrapper c;

    @Before public void setUp() throws Exception {
        dir = Files.createTempDirectory("fanela-store").toFile();
        c = new ContextWrapper(null) { @Override public File getFilesDir() { return dir; } };
    }

    @After public void tearDown() {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir.delete();
    }

    static Match match(String key, String state, String hs, String as) {
        Match m = new Match();
        m.club = "435"; m.team = "FC"; m.source = "espn"; m.key = "435|espn:" + key; m.comp = "gre.1"; m.league = "gre.1";
        m.home = "Olympiacos"; m.away = "PAOK"; m.clubIsHome = true; m.state = state; m.hs = hs; m.as = as; m.detail = "";
        m.start = 1_800_000_000_000L; m.channel = "Cosmote Sport 1"; m.channelApp = null; m.listedBy = "";
        return m;
    }

    private static String sigs(List<Match> all) {
        StringBuilder b = new StringBuilder();
        for (Match m : all) b.append(m.signature()).append(m.channelApp).append(m.seenLiveAt).append('\n');
        return b.toString();
    }

    @Test public void loadGivesCopiesThatCallersMayChange() {
        List<Match> all = new ArrayList<>();
        all.add(match("1", "pre", null, null));
        Store.save(c, all);
        List<Match> a = Store.load(c);
        a.get(0).state = "post";  // what timeStates does to the list it is given
        a.get(0).hs = "9";
        List<Match> b = Store.load(c);
        assertEquals("pre", b.get(0).state);
        assertEquals(null, b.get(0).hs);
        assertNotSame(a.get(0), b.get(0));
    }

    @Test public void theMemoReadsBackExactlyWhatTheFileSays() throws Exception {
        List<Match> all = new ArrayList<>();
        all.add(match("1", "pre", null, null));
        all.add(match("2", "post", "2", "1"));
        Store.save(c, all);
        String memo = sigs(Store.load(c));
        // a fresh parse of the same file, as any earlier version read it (a missing channelApp is Magenta in Greece)
        new File(dir, "matches4.json").setLastModified(1000);
        String parsed = sigs(Store.load(c));
        assertEquals(parsed, memo);
        assertTrue(memo.contains(Cards.MAGENTA));
    }

    @Test public void aChangedFileIsReadAgain() throws Exception {
        List<Match> all = new ArrayList<>();
        all.add(match("1", "pre", null, null));
        Store.save(c, all);
        assertEquals(1, Store.load(c).size());
        File f = new File(dir, "matches4.json");
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        String two = "[" + text.substring(1, text.length() - 1) + "," + text.substring(1, text.length() - 1).replace("espn:1", "espn:2") + "]";
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(two.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(2, Store.load(c).size());
        assertTrue(f.delete());
        assertEquals(0, Store.load(c).size());
    }

    @Test public void saveKeepsTheMemoCurrent() {
        List<Match> all = new ArrayList<>();
        all.add(match("1", "in", "0", "0"));
        Store.save(c, all);
        Store.load(c);
        all.get(0).hs = "1";  // the same length, very likely the same time stamp: only save() can tell the memo
        new File(dir, "matches4.json").delete();  // a rename replaces the file on Android, never on Windows
        Store.save(c, all);
        assertEquals("1", Store.load(c).get(0).hs);
    }
}
