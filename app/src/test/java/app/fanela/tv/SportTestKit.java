package app.fanela.tv;

import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared test helpers for the sports data core: an in-memory Context (files in a temp dir, preferences in memory)
 * and builders for F1 weekends and UFC events. Times are real ones from the samples (UTC).
 */
final class SportTestKit {
    private SportTestKit() { }

    static final long H = 3600000L, MIN = 60000L, DAY = 24 * H;
    // Las Vegas 2026 (ESPN 600057449): FP1 2026-11-20T00:30Z ... race 2026-11-22T04:00Z
    static final long LV_FP1 = 1_795_134_600_000L;           // 2026-11-20T00:30:00Z
    static final long LV_FP2 = LV_FP1 + 4 * H;
    static final long LV_FP3 = LV_FP1 + DAY;
    static final long LV_QUAL = LV_FP1 + DAY + 4 * H;
    static final long LV_RACE = LV_FP1 + 2 * DAY + 3 * H + 30 * MIN;  // 2026-11-22T04:00Z
    // UFC 334 (600061400): early prelims 2026-11-14T22:00Z, prelims 15T00:00Z, main 15T02:00Z
    static final long U334_EARLY = 1_794_693_600_000L;       // 2026-11-14T22:00:00Z
    static final long U334_PRELIMS = U334_EARLY + 2 * H;
    static final long U334_MAIN = U334_EARLY + 4 * H;

    static SportEvent.Session session(String id, String kind, long start) {
        SportEvent.Session s = new SportEvent.Session();
        s.id = id; s.kind = kind; s.typeAbbr = kind; s.start = start;
        return s;
    }

    static SportEvent f1(String id, long fp1) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = id; e.name = "Heineken Las Vegas Grand Prix"; e.shortName = "Heineken Las Vegas GP";
        e.circuitId = "4250"; e.circuitName = "Las Vegas Street Circuit"; e.city = "Nevada"; e.country = "USA"; e.laps = 50;
        e.lengthKm = 6.116f;
        long d = fp1 - LV_FP1;
        e.sessions.add(session(id + "1", "FP1", LV_FP1 + d));
        e.sessions.add(session(id + "2", "FP2", LV_FP2 + d));
        e.sessions.add(session(id + "3", "FP3", LV_FP3 + d));
        e.sessions.add(session(id + "4", "QUAL", LV_QUAL + d));
        e.sessions.add(session(id + "5", "RACE", LV_RACE + d));
        e.start = e.sessions.get(0).start; e.end = e.sessions.get(4).start;
        return e;
    }

    static SportEvent lasVegas() { return f1("600057449", LV_FP1); }

    static SportEvent.Entrant entrant(String id, String code, int pos) {
        SportEvent.Entrant x = new SportEvent.Entrant();
        x.id = id; x.code = code; x.name = code + " Driver"; x.shortName = code; x.team = "Team " + code; x.teamId = "t" + id;
        x.color = 0xFFFF8000; x.pos = pos; x.winner = pos == 1;
        return x;
    }

    static SportEvent.Fighter fighter(String id, String name, String record) {
        SportEvent.Fighter f = new SportEvent.Fighter();
        f.id = id; f.name = name; f.shortName = name.charAt(0) + ". " + name.substring(name.indexOf(' ') + 1);
        f.record = record; f.flag = "https://a.espncdn.com/i/teamlogos/countries/500/fra.png";
        f.headshot = "https://a.espncdn.com/i/headshots/mma/players/full/" + id + ".png";
        return f;
    }

    static SportEvent.Bout bout(String id, int order, SportEvent.Fighter a, SportEvent.Fighter b) {
        SportEvent.Bout x = new SportEvent.Bout();
        x.id = id; x.order = order; x.a = a; x.b = b; x.rounds = order == 1 ? 5 : 3; x.main = order == 1; x.title = order == 1;
        x.weightClass = "Heavyweight"; x.note = order == 1 ? "Heavyweight - Main Event - Title Fight" : "";
        return x;
    }

    static SportEvent ufc(String id, String name, long early) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.UFC; e.id = id; e.name = name; e.shortName = name; e.venue = "Madison Square Garden";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^UFC (\\d+)").matcher(name);
        e.numbered = m.find(); e.number = e.numbered ? Integer.parseInt(m.group(1)) : 0;
        SportEvent.Session s1 = session("prelims2", "EARLY", early), s2 = session("prelims1", "PRELIMS", early + 2 * H),
            s3 = session("main", "MAIN", early + 4 * H);
        s1.bouts.add(bout(id + "07", 7, fighter("11", "Al One", "5-1-0"), fighter("12", "Bo Two", "6-2-0")));
        s2.bouts.add(bout(id + "04", 4, fighter("21", "Cy Three", "10-3-0"), fighter("22", "Di Four", "9-4-1")));
        s3.bouts.add(bout(id + "02", 2, fighter("31", "Ed Five", "20-5-0"), fighter("32", "Fy Six", "18-6-0")));
        s3.bouts.add(bout(id + "01", 1, fighter("3104622", "Ciryl Gane", "14-2-0"), fighter("4049391", "Josh Hokit", "9-0-0")));
        e.sessions.add(s1); e.sessions.add(s2); e.sessions.add(s3);
        e.start = early; e.end = early + 4 * H;
        return e;
    }

    static SportEvent ufc334() { return ufc("600061400", "UFC 334: Gane vs. Hokit", U334_EARLY); }

    static FeedResult ok(String sport, SportEvent... events) {
        FeedResult r = new FeedResult(sport);
        r.ok = true;
        for (SportEvent e : events) r.events.add(e);
        return r;
    }

    // ---------- an in-memory Context ----------
    static final class Ctx extends ContextWrapper {
        final File dir;
        final Map<String, Prefs> prefs = new HashMap<>();
        Ctx(File dir) { super(null); this.dir = dir; }
        @Override public File getFilesDir() { return dir; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            Prefs p = prefs.get(name);
            if (p == null) { p = new Prefs(); prefs.put(name, p); }
            return p;
        }
    }

    static final class Prefs implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        public Map<String, ?> getAll() { return new HashMap<>(m); }
        public String getString(String k, String d) { return m.containsKey(k) ? (String) m.get(k) : d; }
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String k, Set<String> d) { return m.containsKey(k) ? new HashSet<>((Set<String>) m.get(k)) : d; }
        public int getInt(String k, int d) { return m.containsKey(k) ? (Integer) m.get(k) : d; }
        public long getLong(String k, long d) { return m.containsKey(k) ? (Long) m.get(k) : d; }
        public float getFloat(String k, float d) { return m.containsKey(k) ? (Float) m.get(k) : d; }
        public boolean getBoolean(String k, boolean d) { return m.containsKey(k) ? (Boolean) m.get(k) : d; }
        public boolean contains(String k) { return m.containsKey(k); }
        public Editor edit() { return new Ed(); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        final class Ed implements Editor {
            final Map<String, Object> put = new HashMap<>();
            final Set<String> rm = new HashSet<>();
            boolean clear;
            public Editor putString(String k, String v) { put.put(k, v); return this; }
            public Editor putStringSet(String k, Set<String> v) { put.put(k, new HashSet<>(v)); return this; }
            public Editor putInt(String k, int v) { put.put(k, v); return this; }
            public Editor putLong(String k, long v) { put.put(k, v); return this; }
            public Editor putFloat(String k, float v) { put.put(k, v); return this; }
            public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
            public Editor remove(String k) { rm.add(k); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() {
                if (clear) m.clear();
                for (String k : rm) m.remove(k);
                for (Map.Entry<String, Object> e : put.entrySet()) { if (e.getValue() == null) m.remove(e.getKey()); else m.put(e.getKey(), e.getValue()); }
                return true;
            }
            public void apply() { commit(); }
        }
    }

    static Ctx context() throws Exception {
        return new Ctx(java.nio.file.Files.createTempDirectory("fanela-sports").toFile());
    }

    static void delete(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir.delete();
    }
}
