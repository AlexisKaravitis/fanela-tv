package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Debug only: the F1 and UFC card matrix (F1-UFC-ARCHITECTURE §8.3, SPEC-F1-UFC §11) as SportEvents with real
 * names, ESPN ids (headshots, flags) and Greek channels, turned into cards by the app's own EventCardModel.of (stream
 * D), so every event style can be drawn in every state on a device and audited like the football matrix (Fixtures).
 * Dates are relative to the device's clock. Each fixture is drawn in the language set on this thread (I18n.use) and
 * with scores shown or hidden.
 *
 * Entry point for DebugReceiver's MATRIX action (stream G adds the hook):
 *   ... -a app.fanela.tv.MATRIX --es style signature|clean|kit|ticket|clash|all --es sport f1|ufc|all --es lang en [--ez scores false]
 * writes files/matrix/<lang>/<style>[_hidden]/sport/<name>.png and .tsv (Fixtures.Probe's element log), then DONE there.
 */
final class SportFixtures {
    private SportFixtures() { }

    static final class Fx {
        final String name;
        final EventCardModel m;
        Fx(String name, EventCardModel m) { this.name = name; this.m = m; }
    }

    // ---------------------------------------------------------------- the matrix
    /**
     * Draws the fixtures of a sport (f1, ufc or all) in a style (or all five) and a language, scores shown or hidden,
     * into files/matrix/<lang>/<style>[_hidden]/sport/, with each card's probe log (.tsv) and the rows' logos; then DONE.
     */
    static void matrix(Context c, String style, String sport, String lang, boolean scores) throws Exception {
        String l = "el".equals(lang) ? "el" : "en";
        String[] styles = style == null || "all".equals(style) ? Appearance.STYLES : new String[]{style};
        I18n.load(c);
        I18n.use(l);
        Ui.fonts(c);
        prefetch(c);
        for (String st : styles) {
            if (!st.matches("[a-z]{1,16}")) continue;
            File dir = new File(c.getFilesDir(), "matrix/" + l + "/" + st + (scores ? "" : "_hidden") + "/sport");
            dir.mkdirs();
            File[] old = dir.listFiles();
            for (int i = 0; old != null && i < old.length; i++) old[i].delete();
            long now = System.currentTimeMillis();
            for (Fx f : all(sport, scores, now)) {
                StringBuilder log = new StringBuilder();
                long t0 = System.nanoTime();
                Bitmap b = probed(c, f.m, st, log);
                write(b, log, dir, f.name);
                android.util.Log.i("Fanela", "sport matrix " + l + "/" + st + "/" + f.name + " " + (System.nanoTime() - t0) / 1000000 + " ms");
            }
            for (String s : SportEvent.UFC.equals(sport) ? new String[]{"ufc"} : SportEvent.F1.equals(sport) ? new String[]{"f1"} : new String[]{"f1", "ufc"}) {
                Bitmap logo = EventArt.rowLogo(c, s);
                if (logo != null) write(logo, "", dir, "rowlogo_" + s);
            }
            new File(dir, "DONE").createNewFile();
        }
    }

    /** EventStyles.drawOn through Fixtures' Probe: every element's box in card px, one line a draw call. */
    static Bitmap probed(Context c, EventCardModel m, String styleId, StringBuilder log) {
        Bitmap b = Bitmap.createBitmap(Cards.W, Cards.H, Bitmap.Config.ARGB_8888);
        Fixtures.Probe p = new Fixtures.Probe(b, log);
        int at = log.length();
        EventStyle s = EventStyles.drawOn(c, p, m, styleId);
        log.insert(at, "STYLE\t" + s.id() + "\n");
        return b;
    }

    private static void write(Bitmap b, CharSequence log, File dir, String name) throws java.io.IOException {
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        if (log.length() > 0)
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, name + ".tsv"))) { o.write(log.toString().getBytes("UTF-8")); }
        b.recycle();
    }

    /** Fetches every headshot, flag and channel logo the matrix draws (a fresh device draws the real images). */
    static void prefetch(Context c) {
        Channels.load(c);
        for (Fx f : all("all", true, System.currentTimeMillis())) {
            EventCardModel m = f.m;
            if (m.bout != null) {
                if (m.bout.a != null) Net.fetchImage(c, m.bout.a.headshot);
                if (m.bout.b != null) Net.fetchImage(c, m.bout.b.headshot);
            }
            EventKit.View v = EventKit.view(m);
            if (v.flagUrl != null) Net.fetchImage(c, v.flagUrl);
            if (m.session != null && m.session.watch != null)
                for (Channels.Logo l : Channels.logos(c, m.session.watch.asMatch(m.session.start, m.sport))) Net.fetchImage(c, l.url);
        }
    }

    // ---------------------------------------------------------------- data
    static final String HEAD = "https://a.espncdn.com/i/headshots/mma/players/full/";
    // teams as ESPN's standings give them (Nov 2026): id, colour (raw, no alpha, as the feed)
    static final String MCL = "106892", MER = "106893", RBR = "106921", FER = "106842", WIL = "106967";
    static final int MCLAREN = 0xFF8700, MERCEDES = 0x00D2BE, REDBULL = 0x00327D, FERRARI = 0xDC0000, WILLIAMS = 0xFFFFFF;

    static long at(int day, int hh, int mm) { return Fixtures.at(day, hh, mm); }

    /** An F1 weekend (FP1, FP2, FP3, Qualifying, Race; a sprint weekend FP1, SQ, Sprint, Qualifying, Race) ending on race. */
    static SportEvent weekend(String id, String circuit, String name, String country, long race, boolean sprint) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = id; e.circuitId = circuit; e.name = name; e.shortName = name; e.country = country;
        e.laps = 50; e.lengthKm = 6.116f;
        String[] kinds = sprint ? new String[]{"FP1", "SQ", "SPRINT", "QUAL", "RACE"} : new String[]{"FP1", "FP2", "FP3", "QUAL", "RACE"};
        long[] off = {-50, -46, -26, -22, 0};  // hours before the race
        for (int i = 0; i < kinds.length; i++) {
            SportEvent.Session s = new SportEvent.Session();
            s.id = id + "-" + i; s.kind = kinds[i]; s.typeAbbr = kinds[i]; s.start = race + off[i] * 3600000L;
            watch(s, "RACE".equals(kinds[i]) || "SPRINT".equals(kinds[i]) ? "ANT1" : "ANT1+");
            e.sessions.add(s);
        }
        e.start = e.sessions.get(0).start; e.end = race;
        return e;
    }

    static SportEvent vegas(long race) { return weekend("600057449", "4250", "Heineken Las Vegas Grand Prix", "USA", race, false); }

    /** Greek where-to-watch as SportWhere's table gives it: ANT1 · 107 / Magenta Sport 6 · 310; ANT1+ and UFC Fight Pass as names. */
    static void watch(SportEvent.Session s, String channel) {
        SportEvent.SessionWatch w = s.watch;
        w.channel = channel; w.known = true; w.sure = true;
        if ("ANT1".equals(channel)) { w.no = 107; w.app = Cards.MAGENTA; w.listedBy = ""; }
        else if ("Magenta Sport 6".equals(channel)) { w.no = 310; w.app = Cards.MAGENTA; w.listedBy = ""; }
        else { w.no = 0; w.app = "ANT1+".equals(channel) ? Cards.ANT1 : null; w.listedBy = "table"; }
    }

    /** Abroad: a season table's names, a guess (LIKELY, words only). */
    static void abroad(SportEvent.Session s, String channel) {
        SportEvent.SessionWatch w = s.watch;
        w.channel = channel; w.known = true; w.sure = false; w.no = 0; w.app = null; w.listedBy = "table";
    }

    static SportEvent.Session kind(SportEvent e, String kind) {
        for (SportEvent.Session s : e.sessions) if (kind.equals(s.kind)) return s;
        return e.sessions.get(e.sessions.size() - 1);
    }

    static SportEvent.Entrant car(String id, String code, String name, String team, int color, int pos, String gap) {
        SportEvent.Entrant x = new SportEvent.Entrant();
        x.id = id; x.code = code; x.name = name; x.shortName = name; x.teamId = team; x.color = color; x.pos = pos;
        x.winner = pos == 1; x.gap = gap == null ? "" : gap;
        x.number = "VER".equals(code) ? "3" : "NOR".equals(code) ? "1" : "ANT".equals(code) ? "12" : "PIA".equals(code) ? "81"
            : "RUS".equals(code) ? "63" : "LEC".equals(code) ? "16" : "HAD".equals(code) ? "6" : "ALB".equals(code) ? "23" : "";
        x.team = MCL.equals(team) ? "McLaren" : MER.equals(team) ? "Mercedes" : RBR.equals(team) ? "Red Bull" : FER.equals(team) ? "Ferrari" : "Williams";
        return x;
    }

    static final SportEvent.Entrant VER = car("4665", "VER", "Max Verstappen", RBR, REDBULL, 0, ""),
        NOR = car("5579", "NOR", "Lando Norris", MCL, MCLAREN, 0, ""), ANT = car("5829", "ANT", "Kimi Antonelli", MER, MERCEDES, 0, ""),
        PIA = car("5752", "PIA", "Oscar Piastri", MCL, MCLAREN, 0, ""), RUS = car("5503", "RUS", "George Russell", MER, MERCEDES, 0, ""),
        LEC = car("5498", "LEC", "Charles Leclerc", FER, FERRARI, 0, ""), HAD = car("5790", "HAD", "Isack Hadjar", RBR, REDBULL, 0, ""),
        ALB = car("5592", "ALB", "Alexander Albon", WIL, WILLIAMS, 0, "");

    /** A session's order: the drivers in this order get places 1, 2, 3…; "DNF" retires the last one. */
    static void order(SportEvent.Session s, boolean dnfLast, SportEvent.Entrant... cars) {
        s.order.clear();
        for (int i = 0; i < cars.length; i++) {
            SportEvent.Entrant x = cars[i].copy();
            x.pos = i + 1; x.winner = i == 0;
            if (dnfLast && i == cars.length - 1) x.gap = "DNF";
            s.order.add(x);
        }
    }

    static SportPrefs focus(String... refs) { return new SportPrefs(Arrays.asList(refs), Sports.SCOPE_ALL, null); }

    static SportPrefs fighters(String... ids) { return new SportPrefs(null, Sports.SCOPE_ALL, new java.util.HashSet<>(Arrays.asList(ids))); }

    /** The card as the app builds it (stream D's EventCardModel.of). */
    static EventCardModel of(SportEvent e, SportEvent.Session s, SportPrefs p, boolean scores, boolean stale, long now,
                             SportEvent.Entrant... roster) {
        java.util.Map<String, SportEvent.Entrant> r = new java.util.HashMap<>();
        for (SportEvent.Entrant x : roster) r.put(x.id, x);
        return EventCardModel.of(e, s == null ? null : s.id, false, p == null ? SportPrefs.none() : p, scores, stale, now, r);
    }

    /** As of, with the live order trusted (F1Feed.LIVE_ORDER_TRUSTED is false until a recorded feed shows it moving). */
    static synchronized EventCardModel trusted(SportEvent e, SportEvent.Session s, SportPrefs p, boolean scores, long now) {
        boolean was = EventCardModel.orderTrusted;
        EventCardModel.orderTrusted = true;
        try { return of(e, s, p, scores, false, now); } finally { EventCardModel.orderTrusted = was; }
    }

    // ---------------------------------------------------------------- UFC
    static SportEvent.Fighter fighter(String id, String name, String shortName, String record, String country, String flag) {
        SportEvent.Fighter f = new SportEvent.Fighter();
        f.id = id.isEmpty() ? name.replace(' ', '_') : id; f.name = name; f.shortName = shortName; f.record = record;
        f.recordBefore = record; f.country = country;
        f.flag = "https://a.espncdn.com/i/teamlogos/countries/500/" + flag + ".png";
        f.headshot = id.isEmpty() ? "" : HEAD + id + ".png";
        return f;
    }

    static SportEvent.Bout bout(SportEvent.Fighter a, SportEvent.Fighter b, int order, String weight, boolean title, int rounds) {
        SportEvent.Bout x = new SportEvent.Bout();
        x.id = "b" + order + a.id; x.order = order; x.a = a; x.b = b; x.weightClass = weight; x.title = title; x.main = order == 1;
        x.rounds = rounds;
        return x;
    }

    /** A UFC event: early prelims, prelims, main card (2 h apart), the main card at main; its state pre unless set. */
    static SportEvent card(String id, String name, boolean numbered, int number, long main) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.UFC; e.id = id; e.name = name; e.shortName = name; e.numbered = numbered; e.number = number;
        String[][] segs = {{"prelims2", "EARLY"}, {"prelims1", "PRELIMS"}, {"main", "MAIN"}};
        for (int i = 0; i < 3; i++) {
            SportEvent.Session s = new SportEvent.Session();
            s.id = segs[i][0]; s.kind = segs[i][1]; s.start = main - (2 - i) * 2 * 3600000L;
            watch(s, i == 2 ? "Magenta Sport 6" : "UFC Fight Pass");
            e.sessions.add(s);
        }
        e.start = e.sessions.get(0).start; e.end = main;
        return e;
    }

    static SportEvent.Session seg(SportEvent e, String kind) { return kind(e, kind); }

    /** A finished bout: outcome (1 a, 2 b, 3 draw, 4 NC), ESPN's method and detail, the round and the clock. */
    static SportEvent.Bout done(SportEvent.Bout b, int outcome, String method, String detail, int round, String clock) {
        b.state = "post"; b.outcome = outcome; b.method = method; b.methodDetail = detail; b.round = round; b.clock = clock;
        return b;
    }

    static void post(SportEvent e) {
        e.state = "post";
        for (SportEvent.Session s : e.sessions) s.state = "post";
    }

    // ---------------------------------------------------------------- the list
    /** The fixtures of a sport ("f1", "ufc", "all") in the thread's language, scores shown or hidden. */
    static List<Fx> all(String sport, boolean scores, long now) {
        List<Fx> out = new ArrayList<>();
        if (!SportEvent.UFC.equals(sport)) f1Fixtures(out, scores, now);
        if (!SportEvent.F1.equals(sport)) ufcFixtures(out, scores, now);
        return out;
    }

    private static void f1Fixtures(List<Fx> out, boolean scores, long now) {
        SportEvent e;
        SportEvent.Session s;
        // f1_week: the race a week away, the card about the race (its day, 06:00-ish, ANT1 · 107)
        e = vegas(at(7, 6, 0));
        out.add(new Fx("f1_week", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_tomorrow_fp1: the weekend's first session tomorrow (ANT1+ in the Antenna app)
        e = vegas(at(3, 6, 0));
        kind(e, "FP1").start = at(1, 2, 30);
        out.add(new Fx("f1_tomorrow_fp1", of(e, kind(e, "FP1"), null, scores, false, now)));
        // f1_weekend_qual_next: during the weekend, qualifying later today
        e = vegas(at(1, 6, 0));
        for (String k : new String[]{"FP1", "FP2", "FP3"}) { kind(e, k).start = now - 20 * 3600000L; kind(e, k).state = "post"; }
        kind(e, "QUAL").start = now + 5 * 3600000L;
        e.state = "in";
        out.add(new Fx("f1_weekend_qual_next", of(e, kind(e, "QUAL"), null, scores, false, now)));
        // f1_sprint_next: a sprint weekend, sprint qualifying tomorrow (Qatar)
        e = weekend("600057450", "4211", "Qatar Airways Qatar Grand Prix", "Qatar", at(3, 19, 0), true);
        kind(e, "SQ").start = at(1, 20, 30);
        out.add(new Fx("f1_sprint_next", of(e, kind(e, "SQ"), null, scores, false, now)));
        // f1_live_race: the race live, the order trusted: the leader and P2, P3; LIVE 34/50
        e = vegas(now - 50 * 60000L);
        e.state = "in";
        s = kind(e, "RACE");
        s.state = "in"; s.lap = 34; s.laps = 50;
        order(s, false, ANT, VER, NOR, RUS, PIA);
        out.add(new Fx("f1_live_race", trusted(e, s, null, scores, now)));
        // f1_live_race_focus: a driver focus (Norris): NOR P3 and the two best others, McLaren's colours
        out.add(new Fx("f1_live_race_focus", trusted(e, s, focus("d:" + NOR.id), scores, now)));
        // f1_live_untrusted: the order not trusted (the default): the time it started at 40 %, the lap in the pill
        out.add(new Fx("f1_live_untrusted", of(e, s, null, scores, false, now)));
        // f1_live_qual: qualifying live, untrusted
        e = vegas(at(1, 6, 0));
        e.state = "in";
        s = kind(e, "QUAL");
        s.start = now - 20 * 60000L; s.state = "in";
        out.add(new Fx("f1_live_qual", of(e, s, null, scores, false, now)));
        // f1_red_flag: the race suspended: the word, the time at 40 %, no support line
        e = vegas(now - 70 * 60000L);
        e.state = "in";
        s = kind(e, "RACE");
        s.state = "in"; s.detail = "RED_FLAG"; s.lap = 12; s.laps = 50;
        out.add(new Fx("f1_red_flag", of(e, s, null, scores, false, now)));
        // f1_result: the podium (VER, NOR, ANT) in São Paulo
        e = weekend("600057448", "617", "MSC Cruises São Paulo Grand Prix", "Brazil", at(-1, 19, 0), false);
        post(e);
        s = kind(e, "RACE");
        order(s, false, VER, NOR, ANT, RUS, PIA, LEC);
        out.add(new Fx("f1_result", of(e, s, null, scores, false, now)));
        // f1_result_focus_p5: the focus off the podium: LEC P6 → here Piastri P5 and the two best
        out.add(new Fx("f1_result_focus_p5", of(e, s, focus("d:" + PIA.id), scores, false, now)));
        // f1_result_hidden: as f1_result, always hidden (one slot)
        out.add(new Fx("f1_result_hidden", of(e, s, null, false, false, now)));
        // f1_result_team_focus: a team focus (McLaren): two cars, two rows
        out.add(new Fx("f1_result_team_focus", of(e, s, focus("t:" + MCL), scores, false, now)));
        // f1_result_dnf: the focus driver retired (Leclerc, Ferrari): DNF at 60 %
        SportEvent.Session s2 = kind(e, "RACE");
        order(s2, true, VER, NOR, ANT, RUS, PIA, LEC);
        out.add(new Fx("f1_result_dnf", of(e, s2, focus("d:" + LEC.id), scores, false, now)));
        // f1_cancelled: Bahrain (STATUS_CANCELED): the planned time at 40 %, the planned date at 45 %
        e = weekend("600057430", "603", "Gulf Air Bahrain Grand Prix", "Bahrain", at(-200, 18, 0), false);
        for (SportEvent.Session x : e.sessions) { x.state = "off"; x.detail = "CANCELLED"; }
        e.state = "off";
        out.add(new Fx("f1_cancelled", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_delayed: the race start delayed
        e = vegas(now + 30 * 60000L);
        kind(e, "RACE").detail = "DELAYED";
        out.add(new Fx("f1_delayed", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_awaiting: past the window, no result yet
        e = vegas(now - 7 * 3600000L);
        e.state = "in";
        kind(e, "RACE").state = "post"; kind(e, "RACE").detail = "AWAITING";
        out.add(new Fx("f1_awaiting", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_offseason: SEASON 2027, the first race's month
        e = weekend("600057427", "601", "Louis Vuitton Australian Grand Prix", "Australia", at(118, 6, 0), false);
        out.add(new Fx("f1_offseason", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_stale: not refreshed for a while
        e = vegas(at(2, 6, 0));
        out.add(new Fx("f1_stale", of(e, kind(e, "RACE"), null, scores, true, now)));
        // f1_long_name: the longest place and session (Emilia-Romagna, sprint qualifying), tomorrow
        e = weekend("600060010", "3985", "Emilia Romagna Grand Prix", "Italy", at(3, 17, 0), true);
        kind(e, "SQ").start = at(1, 18, 30);
        out.add(new Fx("f1_long_name", of(e, kind(e, "SQ"), null, scores, false, now)));
        // f1_abroad_gb: a viewer in Britain: LIKELY SKY SPORTS F1, in words
        e = vegas(at(7, 6, 0));
        abroad(kind(e, "RACE"), "Sky Sports F1");
        out.add(new Fx("f1_abroad_gb", of(e, kind(e, "RACE"), null, scores, false, now)));
        // f1_no_outline: a circuit with no outline (Bahrain's outer track): the flag in its place
        e = weekend("600057430", "3984", "Bahrain Grand Prix", "Bahrain", at(5, 18, 0), false);
        out.add(new Fx("f1_no_outline", of(e, kind(e, "RACE"), null, scores, false, now)));
        // focus colours before the race: a white team (Williams) and a dark one (Red Bull: the bar lifted)
        e = vegas(at(7, 6, 0));
        out.add(new Fx("f1_focus_williams", of(e, kind(e, "RACE"), focus("d:" + ALB.id), scores, false, now, ALB)));
        out.add(new Fx("f1_focus_redbull", of(e, kind(e, "RACE"), focus("d:" + VER.id), scores, false, now, VER)));
    }

    private static void ufcFixtures(List<Fx> out, boolean scores, long now) {
        SportEvent.Fighter gane = fighter("4426000", "Ciryl Gane", "C. Gane", "14-2-0", "France", "fra"),
            hokit = fighter("4049391", "Josh Hokit", "J. Hokit", "10-0-0", "USA", "usa"),
            nunes = fighter("2516131", "Amanda Nunes", "A. Nunes", "23-5-0", "Brazil", "bra"),
            harrison = fighter("4332765", "Kayla Harrison", "K. Harrison", "19-1-0", "USA", "usa"),
            holland = fighter("3943695", "Kevin Holland", "K. Holland", "29-15-0", "USA", "usa"),
            medic = fighter("4685870", "Uroš Medić", "U. Medić", "14-3-0", "Serbia", "srb"),
            duncan = fighter("4867356", "Chris Duncan", "C. Duncan", "15-3-0", "Scotland", "sco"),
            dober = fighter("2951202", "Drew Dober", "D. Dober", "29-15-0", "USA", "usa"),
            acosta = fighter("4903365", "Waldo Cortes Acosta", "W. Cortes Acosta", "17-5-0", "Dominican Republic", "dom"),
            walker = fighter("3146944", "Johnny Walker", "J. Walker", "24-10-0", "Brazil", "bra"),
            jandiroba = fighter("4289516", "Virna Jandiroba", "V. Jandiroba", "23-5-0", "Brazil", "bra"),
            suarez = fighter("4020435", "Tatiana Suarez", "T. Suarez", "14-1-0", "USA", "usa"),
            kondra = fighter("5264925", "Mantas Kondratavičius", "M. Kondratavičius", "9-2-0", "Lithuania", "ltu"),
            rakhmonov = fighter("", "Shavkat Rakhmonov", "S. Rakhmonov", "19-0-0", "Kazakhstan", "kaz"),
            della = fighter("", "Jack Della Maddalena", "J. Della Maddalena", "18-3-0", "Australia", "aus");
        SportEvent e;
        SportEvent.Bout b;
        // UFC 334, the main card on Sunday 04:00 in Athens (three days on); Holland VS Medić on the prelims
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, at(3, 4, 0));
        seg(e, "MAIN").bouts.addAll(Arrays.asList(bout(gane, hokit, 1, "Heavyweight", true, 5), bout(nunes, harrison, 2, "Women's Bantamweight", true, 5)));
        seg(e, "PRELIMS").bouts.add(bout(holland, medic, 6, "Welterweight", false, 3));
        out.add(new Fx("ufc_next_numbered", of(e, null, null, scores, false, now)));
        // a followed fighter's bout on the prelims (Holland VS Medić; UFC Fight Pass, his segment's time)
        out.add(new Fx("ufc_next_followed_fighter", of(e, null, fighters(holland.id), scores, false, now)));
        out.add(new Fx("ufc_stale", of(e, null, null, scores, true, now)));
        // a Fight Night tomorrow (not numbered, no title)
        e = card("600061268", "UFC Fight Night: Cortes Acosta vs. Walker", false, 0, at(1, 3, 0));
        seg(e, "MAIN").bouts.add(bout(acosta, walker, 1, "Heavyweight", false, 5));
        out.add(new Fx("ufc_next_fightnight", of(e, null, null, scores, false, now)));
        // long names: CORTES ACOSTA VS KONDRATAVIČIUS
        e = card("600061269", "UFC Fight Night: Cortes Acosta vs. Kondratavičius", false, 0, at(1, 3, 0));
        seg(e, "MAIN").bouts.add(bout(acosta, kondra, 1, "Heavyweight", false, 5));
        out.add(new Fx("ufc_long_names", of(e, null, null, scores, false, now)));
        // today: the prelims soon, the card about the main event at the main card's time
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, now + 5 * 3600000L);
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        out.add(new Fx("ufc_today_prelims_soon", of(e, null, null, scores, false, now)));
        // live on the prelims: a bout in the cage, R2 of 3
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, now + 90 * 60000L);
        e.state = "in";
        seg(e, "EARLY").state = "post"; seg(e, "PRELIMS").state = "in";
        b = bout(duncan, dober, 7, "Lightweight", false, 3);
        b.state = "in"; b.round = 2;
        seg(e, "PRELIMS").bouts.add(b);
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        out.add(new Fx("ufc_live_prelims", of(e, null, null, scores, false, now)));
        // live between segments: the prelims done, the main card at its time
        b.state = "post"; b.outcome = 1; b.method = "KO/TKO"; b.round = 2; b.clock = "1:12";
        seg(e, "PRELIMS").state = "post";
        out.add(new Fx("ufc_between_segments", of(e, null, null, scores, false, now)));
        // live on the early prelims: the eyebrow's longest segment (UFC 334 · EARLY PRELIMS / ΠΡΩΤΑ ΠΡΟΚΡΙΜΑΤΙΚΑ)
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, now + 3 * 3600000L);
        e.state = "in";
        seg(e, "EARLY").state = "in";
        b = bout(medic, holland, 9, "Welterweight", false, 3);
        b.state = "in"; b.round = 1;
        seg(e, "EARLY").bouts.add(b);
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        out.add(new Fx("ufc_live_early", of(e, null, null, scores, false, now)));
        // live between two bouts of the main card: the hero says NEXT / ΑΚΟΛΟΥΘΕΙ
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, now - 30 * 60000L);
        e.state = "in";
        seg(e, "EARLY").state = "post"; seg(e, "PRELIMS").state = "post"; seg(e, "MAIN").state = "in";
        seg(e, "MAIN").bouts.add(done(bout(nunes, harrison, 2, "Women's Bantamweight", true, 5), 1, "KO/TKO", "Punches", 1, "0:42"));
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        out.add(new Fx("ufc_between_bouts", of(e, null, null, scores, false, now)));
        // live, the main event in the cage: R2 of 5
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, now - 80 * 60000L);
        e.state = "in";
        seg(e, "EARLY").state = "post"; seg(e, "PRELIMS").state = "post"; seg(e, "MAIN").state = "in";
        b = bout(gane, hokit, 1, "Heavyweight", true, 5);
        b.state = "in"; b.round = 2;
        seg(e, "MAIN").bouts.add(b);
        out.add(new Fx("ufc_live_main_r2", of(e, null, null, scores, false, now)));
        // the Fight Night result: Walker by KO/TKO in R3 at 2:27 (Cortes Acosta, first billed, at 45 %)
        e = card("600061268", "UFC Fight Night: Cortes Acosta vs. Walker", false, 0, at(-1, 3, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(acosta, walker, 1, "Heavyweight", false, 5), 2, "KO/TKO", "Punch", 3, "2:27"));
        out.add(new Fx("ufc_result", of(e, null, null, scores, false, now)));
        out.add(new Fx("ufc_result_hidden", of(e, null, null, false, false, now)));
        // a split decision over five rounds
        e = card("600061268", "UFC Fight Night: Cortes Acosta vs. Walker", false, 0, at(-1, 3, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(acosta, walker, 1, "Heavyweight", false, 5), 1, "Decision", "Decision - Split", 5, "5:00"));
        out.add(new Fx("ufc_result_dec_split", of(e, null, null, scores, false, now)));
        // a majority decision: the longest decision word (MAJORITY / ΠΛΕΙΟΨΗΦΙΑ)
        e = card("600061268", "UFC Fight Night: Cortes Acosta vs. Walker", false, 0, at(-1, 3, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(acosta, walker, 1, "Heavyweight", false, 5), 2, "Decision", "Decision - Majority", 5, "5:00"));
        out.add(new Fx("ufc_result_dec_majority", of(e, null, null, scores, false, now)));
        // a draw and a no contest: both names at 100 %
        e = card("600061270", "UFC Fight Night: Jandiroba vs. Suarez", false, 0, at(-1, 3, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(jandiroba, suarez, 1, "Women's Strawweight", false, 3), 3, "Draw", "Decision - Split Draw", 3, "5:00"));
        out.add(new Fx("ufc_result_draw", of(e, null, null, scores, false, now)));
        e = card("600061270", "UFC Fight Night: Jandiroba vs. Suarez", false, 0, at(-1, 3, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(jandiroba, suarez, 1, "Women's Strawweight", false, 3), 4, "No Contest", "Accidental foul", 1, "2:10"));
        out.add(new Fx("ufc_result_nc", of(e, null, null, scores, false, now)));
        // a followed fighter's win (Holland on the prelims, SUB R2 4:11): the WIN pill
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, at(-1, 4, 0));
        post(e);
        seg(e, "MAIN").bouts.add(done(bout(gane, hokit, 1, "Heavyweight", true, 5), 1, "KO/TKO", "Punches", 2, "3:03"));
        seg(e, "PRELIMS").bouts.add(done(bout(holland, medic, 6, "Welterweight", false, 3), 1, "Submission", "Rear Naked Choke", 2, "4:11"));
        out.add(new Fx("ufc_result_followed_win", of(e, null, fighters(holland.id), scores, false, now)));
        // renamed: the main event changed (the same event id, a new headline)
        e = card("600061400", "UFC 334: Nunes vs. Harrison", true, 334, at(3, 4, 0));
        seg(e, "MAIN").bouts.add(bout(nunes, harrison, 1, "Women's Bantamweight", true, 5));
        out.add(new Fx("ufc_renamed", of(e, null, null, scores, false, now)));
        // cancelled: the time at 40 %, the date at 45 %
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, at(3, 4, 0));
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        e.state = "off";
        for (SportEvent.Session x : e.sessions) { x.state = "off"; x.detail = "CANCELLED"; }
        out.add(new Fx("ufc_cancelled", of(e, null, null, scores, false, now)));
        // abroad (Britain): LIKELY TNT SPORTS in words
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, at(3, 4, 0));
        seg(e, "MAIN").bouts.add(bout(gane, hokit, 1, "Heavyweight", true, 5));
        abroad(seg(e, "MAIN"), "TNT Sports");
        out.add(new Fx("ufc_abroad_gb", of(e, null, null, scores, false, now)));
        // no headshots (both initials): RAKHMONOV VS DELLA MADDALENA, two lines
        e = card("600061500", "UFC 335: Rakhmonov vs. Della Maddalena", true, 335, at(24, 5, 0));
        seg(e, "MAIN").bouts.add(bout(rakhmonov, della, 1, "Welterweight", true, 5));
        out.add(new Fx("ufc_no_headshots", of(e, null, null, scores, false, now)));
        // one headshot missing: both show initials (symmetry beats one face)
        SportEvent.Fighter hokitNoFace = hokit.copy();
        hokitNoFace.headshot = "";
        e = card("600061400", "UFC 334: Gane vs. Hokit", true, 334, at(3, 4, 0));
        seg(e, "MAIN").bouts.add(bout(gane, hokitNoFace, 1, "Heavyweight", true, 5));
        out.add(new Fx("ufc_one_headshot", of(e, null, null, scores, false, now)));
    }
}
