package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Debug only: the home screen in a fixed state, so every state of SPEC-APP can be screenshotted and compared with the
 * reference renders. The scenario is the spec's: Olympiacos (favourite) and PAOK followed, Olympiacos–Dubai tomorrow
 * at 21:15 on Novasports Prime 318, their real fixtures after it, and the clock at 18:30 today. Modes:
 *   home, live (Olympiacos–Dubai 61–58, Q3 5:12, at 22:20 tomorrow), loading, offline (saved at 17:55), empty,
 *   many (five clubs: AEK, Panathinaikos and Aris added, with no fixtures in the scenario), failed (the same five,
 *   the three added ones never loaded: "Couldn't load fixtures"), ticking (live, but the score and the clock move
 *   with the real clock: a new state at every load, every 30 s, to watch live updates).
 * Sports (F1-UFC-ARCHITECTURE §8.5), the same two clubs plus Formula 1 and UFC followed, with fixed events whatever the
 * feeds say: f1weekend (the Las Vegas weekend from tomorrow, São Paulo's result, UFC 334 in three days), f1live (the
 * Las Vegas race at lap 34/50, the practices and qualifying done), ufcnight (UFC 334 tonight: prelims done, the main
 * card's third bout in round 2).
 * Set with DebugReceiver SCENARIO (--es name <mode>|off [--es lang el]); "off" gives the real data back. While one is
 * on, HomeActivity reads it instead of the saved matches and runs no refresh. Release builds have a stub (src/release).
 */
final class Scenario {
    private Scenario() { }

    static final class Snapshot {
        List<Match> matches;
        List<String> follow;
        /** The followed sports' events, the sports and their choices (F1 / UFC); empty for the club-only modes. */
        List<SportEvent> events = new ArrayList<>();
        List<String> sports = new ArrayList<>();
        SportPrefs prefs = SportPrefs.none();
        String favourite, mode, lang;
        long now, savedAt;
    }

    static final String[] MODES = {"home", "live", "loading", "offline", "empty", "many", "failed", "ticking", "f1weekend", "f1live", "ufcnight"};

    /** The modes with sports. */
    static boolean sports(String mode) { return "f1weekend".equals(mode) || "f1live".equals(mode) || "ufcnight".equals(mode); }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("scenario", Context.MODE_PRIVATE); }

    /**
     * Turns a mode on (lang en / el written to the app's language setting, the country set to Greece as in the spec) or
     * off ("off": the language and country from before).
     */
    static void set(Context c, String mode, String lang) {
        SharedPreferences settings = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (mode == null || !Arrays.asList(MODES).contains(mode)) {
            String was = sp(c).getString("lang_before", null), cc = sp(c).getString("country_before", null);
            if (was != null) settings.edit().putString("lang", was).commit();
            if (cc != null) settings.edit().putString("country", cc).commit();
            sp(c).edit().clear().commit();
            return;
        }
        if (!sp(c).contains("lang_before")) sp(c).edit().putString("lang_before", settings.getString("lang", "en"))
            .putString("country_before", settings.getString("country", "GR")).commit();
        settings.edit().putString("lang", "el".equals(lang) ? "el" : "en").putString("country", "GR").commit();
        sp(c).edit().putString("mode", mode).commit();
        if (sports(mode)) {  // the faces and flags the screens draw (DebugReceiver runs this off the UI thread)
            Snapshot sn = active(c);
            try {
                SportEngine.prefetchImages(c, sn.events, sn.now);
                for (SportEvent e : sn.events) { String f = SportPanel.flagUrl(e); if (f != null) Net.fetchImage(c, f); }
            } catch (Throwable t) { android.util.Log.w("Fanela", "scenario images", t); }
        }
    }

    /**
     * After I18n.load: a scenario reads in 24-hour time like the references, whatever the emulator's clock format
     * (I18n follows the TV's setting; the setting itself is left alone).
     */
    static void apply(Context c) {
        if (sp(c).getString("mode", null) == null) return;
        try {
            java.lang.reflect.Field f = I18n.class.getDeclaredField("h24");
            f.setAccessible(true);
            f.setBoolean(null, true);
        } catch (Exception ignored) { }
    }

    static Snapshot active(Context c) {
        String mode = sp(c).getString("mode", null);
        if (mode == null) return null;
        Snapshot s = new Snapshot();
        s.mode = mode;
        s.lang = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "en");
        s.favourite = "435";
        s.follow = "empty".equals(mode) ? Collections.<String>emptyList()
            : "many".equals(mode) || "failed".equals(mode) ? Arrays.asList("435", "605", "887", "443", "11553") : Arrays.asList("435", "605");
        boolean ticking = "ticking".equals(mode), live = ticking || "live".equals(mode);
        s.now = live ? at(1, 22, 20) : "ufcnight".equals(mode) ? at(0, 23, 40) : at(0, 18, 30);
        s.savedAt = at(0, 17, 55);
        s.matches = matches(live, s.now);
        if (ticking) tick(s.matches, System.currentTimeMillis());
        if (sports(mode)) {
            s.sports = Arrays.asList(SportEvent.F1, SportEvent.UFC);
            s.events = events(mode);
            s.prefs = new SportPrefs(Arrays.asList("d:5579"), Sports.SCOPE_ALL, new java.util.HashSet<>(Arrays.asList("3146944")));
        }
        return s;
    }

    // ---------- sports ----------
    static final String FLAGS = "https://a.espncdn.com/i/teamlogos/countries/500/", HEADS = "https://a.espncdn.com/i/headshots/mma/players/full/";

    /** The scenario's F1 weekends and UFC events for a sports mode (times relative to today, as the matches'). */
    static List<SportEvent> events(String mode) {
        List<SportEvent> out = new ArrayList<>();
        boolean f1live = "f1live".equals(mode), ufcnight = "ufcnight".equals(mode);
        // São Paulo, the last race: its podium is the latest tile
        SportEvent sp = f1("600057448", "MSC Cruises Grand Prix de Sao Paulo", "Sao Paulo", "Brazil", "Autodromo Jose Carlos Pace", "4240", 71, 4.309f);
        sp.sessions.add(post(session("sp1", "FP1", at(-12, 17, 30)), order("NOR", "PIA", "VER")));
        sp.sessions.add(post(session("sp2", "SQ", at(-12, 21, 30)), order("NOR", "VER", "ANT")));
        sp.sessions.add(post(session("sp3", "SPRINT", at(-11, 16, 0)), order("NOR", "ANT", "PIA")));
        sp.sessions.add(post(session("sp4", "QUAL", at(-11, 20, 0)), order("NOR", "ANT", "LEC")));
        sp.sessions.add(post(session("sp5", "RACE", at(-10, 19, 0)), order("NOR", "VER", "ANT", "PIA", "LEC", "RUS", "HAM", "ALO", "SAI", "ALB")));
        out.add(done(sp));
        // Las Vegas: from tomorrow (f1weekend, ufcnight), or its race at lap 34/50 (f1live)
        SportEvent lv = f1("600057449", "Heineken Las Vegas Grand Prix", "Las Vegas", "USA", "Las Vegas Strip Circuit", "4250", 50, 6.201f);
        if (f1live) {
            lv.sessions.add(post(session("lv1", "FP1", at(-2, 2, 30)), order("NOR", "VER", "LEC")));
            lv.sessions.add(post(session("lv2", "FP2", at(-2, 6, 0)), order("VER", "NOR", "PIA")));
            lv.sessions.add(post(session("lv3", "FP3", at(-1, 2, 30)), order("NOR", "ANT", "VER")));
            lv.sessions.add(post(session("lv4", "QUAL", at(-1, 6, 0)), order("ANT", "VER", "NOR")));
            SportEvent.Session race = session("lv5", "RACE", at(0, 17, 45));
            race.state = "in"; race.lap = 34; race.laps = 50;
            race.order.addAll(order("ANT", "VER", "NOR", "LEC", "PIA", "RUS", "HAM", "ALO", "SAI", "ALB"));
            String[] gaps = {"", "+1.8", "+4.2", "+9.0", "+11.4", "+15.7", "+21.3", "+30.1", "+33.9", "+41.0"};
            for (int i = 0; i < race.order.size(); i++) { race.order.get(i).gap = gaps[i]; race.order.get(i).winner = false; }
            lv.sessions.add(race);
        } else {
            lv.sessions.add(session("lv1", "FP1", at(1, 2, 30)));
            lv.sessions.add(session("lv2", "FP2", at(1, 6, 0)));
            lv.sessions.add(session("lv3", "FP3", at(2, 2, 30)));
            lv.sessions.add(session("lv4", "QUAL", at(2, 6, 0)));
            lv.sessions.add(session("lv5", "RACE", at(3, 6, 0)));
        }
        out.add(done(lv));
        // Qatar, a sprint weekend after it
        SportEvent q = f1("600057450", "Qatar Airways Qatar Grand Prix", "Lusail", "Qatar", "Lusail International Circuit", "4260", 57, 5.419f);
        q.sessions.add(session("qa1", "FP1", at(8, 16, 30)));
        q.sessions.add(session("qa2", "SQ", at(8, 20, 30)));
        q.sessions.add(session("qa3", "SPRINT", at(9, 17, 0)));
        q.sessions.add(session("qa4", "QUAL", at(9, 21, 0)));
        q.sessions.add(session("qa5", "RACE", at(10, 19, 0)));
        out.add(done(q));
        for (SportEvent e : out) for (SportEvent.Session s : e.sessions) watchF1(s);
        // UFC: last weekend's Fight Night, UFC 334 (in three days, or tonight), and the next two
        SportEvent fn = ufc("600061300", "UFC Fight Night: Walker vs. Cortes Acosta", false, 0, "Las Vegas, NV", at(-4, 3, 0));
        SportEvent.Bout w = bout("fn1", 1, fighter("3146944", "Johnny Walker", "bra", "21-9-0"), fighter("4903365", "Waldo Cortes Acosta", "dom", "14-1-0"), "Light Heavyweight", false, 5);
        result(w, 1, "KO/TKO", "", 3, "2:27");
        main(fn).bouts.add(w);
        out.add(doneUfc(fn));
        SportEvent u = ufc("600061400", "UFC 334: Gane vs. Hokit", true, 334, "Madison Square Garden, New York", ufcnight ? at(0, 19, 0) : at(3, 0, 0));
        SportEvent.Session early = u.sessions.get(0), pre = u.sessions.get(1), mc = u.sessions.get(2);
        String[][] card = {
            {"1", "4426000", "Ciryl Gane", "fra", "14-2-0", "4049391", "Josh Hokit", "usa", "10-0-0", "Heavyweight", "t", "5"},
            {"2", "4332765", "Kayla Harrison", "usa", "19-1-0", "2516131", "Amanda Nunes", "bra", "23-5-0", "Women's Bantamweight", "t", "5"},
            {"3", "3943695", "Kevin Holland", "usa", "28-14-0", "4685870", "Uroš Medić", "srb", "12-3-0", "Welterweight", "", "3"},
            {"4", "e1", "Fighter E", "gbr", "17-4-0", "f1", "Fighter F", "usa", "15-2-0", "Lightweight", "", "3"},
            {"5", "g1", "Fighter G", "bra", "21-6-0", "h1", "Fighter H", "ned", "13-1-0", "Featherweight", "", "3"},
            {"6", "j1", "Fighter J", "usa", "11-2-0", "k1", "Fighter K", "mex", "9-3-0", "Middleweight", "", "3"},
            {"7", "l1", "Fighter L", "aus", "8-1-0", "m1", "Fighter M", "jpn", "10-4-0", "Flyweight", "", "3"},
            {"8", "n1", "Fighter N", "pol", "16-5-0", "o1", "Fighter O", "usa", "14-6-0", "Light Heavyweight", "", "3"},
            {"9", "p1", "Fighter P", "can", "7-0-0", "q1", "Fighter Q", "bra", "9-2-0", "Bantamweight", "", "3"},
            {"10", "r1", "Fighter R", "usa", "12-3-0", "s1", "Fighter S", "rus", "13-1-0", "Women's Strawweight", "", "3"}};
        for (String[] r : card) {
            int order = Integer.parseInt(r[0]);
            SportEvent.Bout b = bout(u.id + r[0], order, fighter(r[1], r[2], r[3], r[4]), fighter(r[5], r[6], r[7], r[8]), r[9], "t".equals(r[10]), Integer.parseInt(r[11]));
            (order <= 5 ? mc : order <= 8 ? pre : early).bouts.add(b);
            if (!ufcnight) continue;
            if (order >= 6) { early.state = "post"; pre.state = "post"; result(b, order % 2 == 0 ? 1 : 2, order % 3 == 0 ? "Submission" : "Decision - Unanimous", "", 3, "5:00"); }
            else if (order >= 4) result(b, order == 4 ? 1 : 2, "KO/TKO", "Punch", order == 4 ? 1 : 2, order == 4 ? "0:58" : "4:11");
            else if (order == 3) { b.state = "in"; b.round = 2; b.clock = "3:41"; }
        }
        if (ufcnight) mc.state = "in";
        out.add(doneUfc(u));
        SportEvent fn2 = ufc("600061500", "UFC Fight Night: Fighter L vs. Fighter M", false, 0, "UFC APEX, Las Vegas", at(10, 1, 0));
        main(fn2).bouts.add(bout("fn21", 1, fighter("l2", "Fighter L", "usa", "15-3-0"), fighter("m2", "Fighter M", "bra", "18-4-0"), "Middleweight", false, 5));
        out.add(doneUfc(fn2));
        SportEvent u335 = ufc("600061600", "UFC 335: Rakhmonov vs. Della Maddalena", true, 335, "T-Mobile Arena, Las Vegas", at(24, 3, 0));
        main(u335).bouts.add(bout("u3351", 1, fighter("4020699", "Shavkat Rakhmonov", "kaz", "19-0-0"), fighter("4828707", "Jack Della Maddalena", "aus", "18-2-0"), "Welterweight", true, 5));
        out.add(doneUfc(u335));
        for (SportEvent e : out) if (SportEvent.UFC.equals(e.sport)) for (SportEvent.Session s : e.sessions) watchUfc(s);
        return out;
    }

    private static SportEvent f1(String id, String name, String city, String country, String circuit, String circuitId, int laps, float km) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = id; e.name = name; e.shortName = name.replace("Grand Prix", "GP"); e.city = city; e.country = country;
        e.circuitName = circuit; e.circuitId = circuitId; e.laps = laps; e.lengthKm = km;
        return e;
    }

    private static SportEvent.Session session(String id, String kind, long start) {
        SportEvent.Session s = new SportEvent.Session();
        s.id = id; s.kind = kind; s.typeAbbr = kind; s.start = start;
        return s;
    }

    private static SportEvent.Session post(SportEvent.Session s, List<SportEvent.Entrant> order) { s.state = "post"; s.order.addAll(order); return s; }

    private static final String[][] DRIVERS = {
        {"NOR", "5579", "Lando Norris", "McLaren", "FF8000"}, {"VER", "4665", "Max Verstappen", "Red Bull", "00327D"},
        {"ANT", "5592", "Kimi Antonelli", "Mercedes", "00D2BE"}, {"PIA", "5752", "Oscar Piastri", "McLaren", "FF8000"},
        {"LEC", "5498", "Charles Leclerc", "Ferrari", "DC0000"}, {"RUS", "5503", "George Russell", "Mercedes", "00D2BE"},
        {"HAM", "868", "Lewis Hamilton", "Ferrari", "DC0000"}, {"ALO", "348", "Fernando Alonso", "Aston Martin", "006F62"},
        {"SAI", "4686", "Carlos Sainz", "Williams", "1868DB"}, {"ALB", "4678", "Alexander Albon", "Williams", "1868DB"}};

    private static List<SportEvent.Entrant> order(String... codes) {
        List<SportEvent.Entrant> out = new ArrayList<>();
        for (String code : codes) for (String[] d : DRIVERS) if (d[0].equals(code)) {
            SportEvent.Entrant x = new SportEvent.Entrant();
            x.code = d[0]; x.id = d[1]; x.name = d[2]; x.shortName = d[2].charAt(0) + ". " + d[2].substring(d[2].indexOf(' ') + 1);
            x.team = d[3]; x.color = 0xFF000000 | Integer.parseInt(d[4], 16); x.pos = out.size() + 1; x.winner = x.pos == 1;
            out.add(x);
        }
        return out;
    }

    private static SportEvent done(SportEvent e) {
        List<SportEvent.Session> by = e.byStart();
        e.start = by.get(0).start; e.end = by.get(by.size() - 1).start;
        SportEngine.derive(e);
        return e;
    }

    private static void watchF1(SportEvent.Session s) {
        SportEvent.SessionWatch w = s.watch;
        w.known = true; w.sure = true;
        if ("RACE".equals(s.kind) || "SPRINT".equals(s.kind)) { w.channel = "ANT1"; w.no = 107; w.app = Cards.MAGENTA; }
        else { w.channel = "ANT1+"; w.no = 0; w.app = Cards.ANT1; w.listedBy = "table"; }
    }

    private static SportEvent ufc(String id, String name, boolean numbered, int number, String venue, long early) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.UFC; e.id = id; e.name = name; e.shortName = name; e.numbered = numbered; e.number = number; e.venue = venue;
        e.sessions.add(session("prelims2", "EARLY", early));
        e.sessions.add(session("prelims1", "PRELIMS", early + 2 * 3600000L));
        e.sessions.add(session("main", "MAIN", early + 4 * 3600000L));
        return e;
    }

    private static SportEvent.Session main(SportEvent e) { return e.sessions.get(2); }

    private static SportEvent.Fighter fighter(String id, String name, String flag, String record) {
        SportEvent.Fighter f = new SportEvent.Fighter();
        f.id = id; f.name = name; f.shortName = name.charAt(0) + ". " + name.substring(name.indexOf(' ') + 1);
        f.flag = FLAGS + flag + ".png"; f.headshot = id.matches("\\d+") ? HEADS + id + ".png" : "";
        f.record = record; f.recordBefore = record;
        return f;
    }

    private static SportEvent.Bout bout(String id, int order, SportEvent.Fighter a, SportEvent.Fighter b, String weight, boolean title, int rounds) {
        SportEvent.Bout x = new SportEvent.Bout();
        x.id = id; x.order = order; x.a = a; x.b = b; x.weightClass = weight; x.title = title; x.main = order == 1; x.rounds = rounds;
        return x;
    }

    private static void result(SportEvent.Bout b, int outcome, String method, String detail, int round, String clock) {
        b.state = "post"; b.outcome = outcome; b.method = method; b.methodDetail = detail; b.round = round; b.clock = clock;
    }

    private static SportEvent doneUfc(SportEvent e) {
        boolean allPost = true;
        for (SportEvent.Session s : e.sessions) {
            boolean any = false, pre = false;
            for (SportEvent.Bout b : s.bouts) { any |= "post".equals(b.state); pre |= !"post".equals(b.state); }
            if (any && !pre) s.state = "post";
            allPost &= "post".equals(s.state) || s.bouts.isEmpty();
        }
        if (allPost && !e.sessions.get(2).bouts.isEmpty()) for (SportEvent.Session s : e.sessions) s.state = "post";
        return done(e);
    }

    private static void watchUfc(SportEvent.Session s) {
        SportEvent.SessionWatch w = s.watch;
        w.known = true; w.sure = true;
        if ("MAIN".equals(s.kind)) { w.channel = "Magenta Sport 6"; w.no = 310; w.app = Cards.MAGENTA; }
        else { w.channel = "UFC Fight Pass"; w.no = 0; w.app = null; w.listedBy = "table"; }
    }

    /**
     * The ticking mode: the live match's score and clock from the real clock (a new state every 30 s), and every other
     * 30 s PAOK has one fixture more (its fixture list is rebuilt while it may hold the focus).
     */
    static void tick(List<Match> all, long wall) {
        int k = (int) ((wall / 30000L) % 40);
        long left = 600 - (wall / 1000L) % 600;
        for (Match m : all) if ("in".equals(m.state)) {
            m.hs = String.valueOf(61 + k);
            m.as = String.valueOf(58 + k / 2 + (k % 3 == 0 ? 1 : 0));
            m.detail = "Q3 " + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60;
        }
        if (k % 2 == 1) all.add(ch(m("605", "FC", "gre.1", "Volos", "PAOK", false, VOLOS, PAOK_FC, at(18, 17, 0)), "Novasports", 0, false));
    }

    static final String VOLOS = "https://a.espncdn.com/i/teamlogos/soccer/500/11745.png";

    /** Today's date plus days, at hh:mm (today = Wednesday 11 November in the spec). */
    static long at(int days, int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static final String OLY_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/435.png",
        OLY_BC = "https://media-cdn.incrowdsports.com/789423ac-3cdf-4b89-b11c-b458aa5f59a6.png",
        DUBAI = "https://media-cdn.incrowdsports.com/1efae090-16e2-4963-ae47-4b94f249c244.png",
        ZVEZDA = "https://media-cdn.incrowdsports.com/d2eef4a8-62df-4fdd-9076-276004268515.png",
        PAOK_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/605.png",
        PAOK_BC = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/0506e7c4-ec4b-460a-bc59-b033ad9e336c.png",
        PAOK_GBL = "https://r2.thesportsdb.com/images/media/team/badge/xojprw1792765762.png",
        KARDITSA = "https://r2.thesportsdb.com/images/media/team/badge/jbnxms1757948183.png",
        BALKAN = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjCgOCpC/ba6e2d99-3ea9-4730-a0ce-830cccca4657.png",
        ARIS_BC = "https://img.sofascore.com/api/v1/team/3505/image",
        MACCABI = "https://media-cdn.cortextech.io/1b533342-78f5-4932-b714-a7d80b5826b5.png",
        REAL = "https://media-cdn.incrowdsports.com/371b0d9b-9250-4c09-bda7-0686cf024657.png",
        IRAKLIS = "https://a.espncdn.com/i/teamlogos/soccer/500/2575.png",
        HAPOEL = "https://media-cdn.incrowdsports.com/cbb1c3ad-03d5-426a-b5ef-2832a4eee484.png",
        MILAN = "https://a.espncdn.com/i/teamlogos/soccer/500/103.png",
        ARIS_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/11553.png",
        KIFISIA = "https://a.espncdn.com/i/teamlogos/soccer/500/21969.png",
        BARCA = "https://media-cdn.incrowdsports.com/35dfa503-e417-481f-963a-bdf6f013763e.png",
        FENER = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjCiezrk/aa39750a-6203-49ee-a87d-edd0c6f0397d.png",
        BESIKTAS = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/b05e3c54-e672-4b31-9f86-c91e9de7715c.png",
        MILAN_BC = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/5c2f0ab6-f86a-4df0-b2f3-f9ab92f7267b.png";

    private static Match m(String club, String team, String league, String home, String away, boolean clubHome, String hc, String ac, long start) {
        Match m = new Match();
        m.club = club; m.team = team; m.comp = league; m.league = league; m.source = "fx";
        m.home = home; m.away = away; m.clubIsHome = clubHome; m.homeCrest = hc; m.awayCrest = ac; m.start = start;
        m.key = club + "|fx:" + team + home + away + start; m.state = "pre"; m.detail = ""; m.listedBy = "";
        m.channelKnown = false; m.channel = "";
        return m;
    }

    private static Match ch(Match m, String channel, int no, boolean sure) {
        m.channel = channel; m.channelNo = no; m.channelSure = sure; m.channelKnown = true; m.channelApp = Cards.MAGENTA;
        return m;
    }

    private static Match result(Match m, String hs, String as, int won) { m.state = "post"; m.hs = hs; m.as = as; m.clubWon = won; m.detail = "FT"; return m; }

    static List<Match> matches(boolean live, long now) {
        List<Match> out = new ArrayList<>();
        Match dubai = ch(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI, at(1, 21, 15)), "Novasports Prime", 318, true);
        if (live) { dubai.state = "in"; dubai.hs = "61"; dubai.as = "58"; dubai.detail = "Q3 5:12"; dubai.seenLiveAt = now; }
        out.add(dubai);
        out.add(ch(m("435", "BC", "gbl", "Aris", "Olympiacos", false, ARIS_BC, OLY_BC, at(4, 17, 30)), "ΣΚΑΪ", 110, true));
        out.add(ch(m("435", "BC", "euroleague", "Maccabi", "Olympiacos", false, MACCABI, OLY_BC, at(6, 19, 0)), "Novasports Prime", 318, true));
        out.add(ch(m("435", "BC", "euroleague", "Olympiacos", "Real Madrid", true, OLY_BC, REAL, at(9, 21, 15)), "Novasports", 0, false));
        out.add(ch(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS, at(11, 19, 0)), "Magenta Sport", 0, false));
        out.add(ch(m("435", "BC", "euroleague", "Olympiacos", "Hapoel Tel Aviv", true, OLY_BC, HAPOEL, at(14, 21, 15)), "Novasports", 0, false));
        out.add(ch(m("435", "FC", "uefa.europa", "Olympiacos", "AC Milan", true, OLY_FC, MILAN, at(15, 19, 45)), "Magenta Sport · ANT1", 0, false));
        out.add(ch(m("435", "FC", "gre.1", "Aris", "Olympiacos", false, ARIS_FC, OLY_FC, at(18, 19, 0)), "Novasports", 0, false));
        out.add(result(m("435", "FC", "gre.1", "Olympiacos", "PAOK", true, OLY_FC, PAOK_FC, at(-3, 21, 0)), "1", "1", 0));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Crvena Zvezda", true, OLY_BC, ZVEZDA, at(-5, 21, 15)), "57", "62", -1));
        // the season's EuroLeague form before it (the match screen's last five) and the return legs (real dates)
        out.add(result(m("435", "BC", "euroleague", "FC Barcelona", "Olympiacos", false, BARCA, OLY_BC, at(-26, 21, 0)), "100", "105", 1));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Fenerbahce", true, OLY_BC, FENER, at(-19, 21, 15)), "61", "64", -1));
        out.add(result(m("435", "BC", "euroleague", "Besiktas", "Olympiacos", false, BESIKTAS, OLY_BC, at(-14, 19, 0)), "73", "81", 1));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Milan", true, OLY_BC, MILAN_BC, at(-12, 21, 15)), "84", "78", 1));
        out.add(ch(m("435", "BC", "euroleague", "Real Madrid", "Olympiacos", false, REAL, OLY_BC, at(57, 21, 45)), "Novasports", 0, false));
        out.add(ch(m("435", "BC", "euroleague", "Dubai", "Olympiacos", false, DUBAI, OLY_BC, at(114, 18, 0)), "Novasports", 0, false));
        // PAOK: its next game on Saturday; in the live state, a win that finished last night
        out.add(result(m("605", "FC", "gre.1", "Olympiacos", "PAOK", false, OLY_FC, PAOK_FC, at(-3, 21, 0)), "1", "1", 0));
        out.add(result(m("605", "BC", "eurocup", "Balkan", "PAOK", false, BALKAN, PAOK_BC, live ? now - 20 * 3600000L : at(-1, 15, 0)), "56", "79", 1));
        out.add(ch(m("605", "BC", "gbl", "Karditsa", "PAOK", false, KARDITSA, PAOK_GBL, at(3, 17, 30)), "ΣΚΑΪ", 110, true));
        out.add(ch(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA, at(11, 19, 30)), "Novasports", 0, false));
        return out;
    }
}
