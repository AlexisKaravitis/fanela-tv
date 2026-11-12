package app.fanela.tv;

import static app.fanela.tv.SportTestKit.DAY;
import static app.fanela.tv.SportTestKit.H;
import static app.fanela.tv.SportTestKit.LV_FP1;
import static app.fanela.tv.SportTestKit.LV_FP3;
import static app.fanela.tv.SportTestKit.LV_RACE;
import static app.fanela.tv.SportTestKit.MIN;
import static app.fanela.tv.SportTestKit.U334_EARLY;
import static app.fanela.tv.SportTestKit.U334_MAIN;
import static app.fanela.tv.SportTestKit.U334_PRELIMS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * The shared fixture matrix of F1-UFC-ARCHITECTURE §8.3 as test data: each fixture is an event, the viewer's prefs, the
 * time, and which tile (now / last) shows it. The card is built through the picks, as the rows build it. Times are
 * the real 2026 ones (Las Vegas FP1 2026-11-20T00:30Z, race 22T04:00Z; UFC 334 main card 15T02:00Z).
 */
final class EventFixtures {
    private EventFixtures() { }

    static final class Fx {
        final String name; final SportEvent e; final SportPrefs p; final long now; final boolean last, stale;
        final CardModel.State state;
        Fx(String name, SportEvent e, SportPrefs p, long now, boolean last, boolean stale, CardModel.State state) {
            this.name = name; this.e = e; this.p = p; this.now = now; this.last = last; this.stale = stale; this.state = state;
        }

        EventCards.Pick pick() {
            List<SportEvent> all = Collections.singletonList(e);
            return last ? EventCards.pickLast(all, e.sport, p, now) : EventCards.pickNow(all, e.sport, p, now);
        }

        EventCardModel model(boolean shown) {
            EventCards.Pick k = pick();
            if (k == null) throw new AssertionError(name + ": no pick");
            return EventCardModel.of(k.e, k.sessionId, k.last, p, shown, stale, now, EventCards.rosterFrom(Collections.singletonList(e)));
        }

        @Override public String toString() { return name; }
    }

    // ---------- people ----------
    static final String NOR = "5579", VER = "4665", ANT = "5829", PIA = "5752", LEC = "5498", RUS = "5503";
    static final String MCLAREN = "106892", REDBULL = "106921", MERCEDES = "106893", FERRARI = "106842";

    static SportEvent.Entrant driver(String id, String code, String name, String team, String teamId, int color, int pos) {
        SportEvent.Entrant x = new SportEvent.Entrant();
        x.id = id; x.code = code; x.name = name; x.shortName = name.charAt(0) + ". " + name.substring(name.indexOf(' ') + 1);
        x.team = team; x.teamId = teamId; x.color = color; x.pos = pos; x.winner = pos == 1;
        return x;
    }

    /** The order of a session: codes in place order (VER, NOR, ANT … the rest). */
    static void order(SportEvent.Session s, String... codes) {
        s.order.clear();
        int pos = 1;
        for (String code : codes) {
            SportEvent.Entrant x;
            switch (code) {
                case "VER": x = driver(VER, "VER", "Max Verstappen", "Red Bull", REDBULL, 0x00327D, pos); break;
                case "NOR": x = driver(NOR, "NOR", "Lando Norris", "McLaren", MCLAREN, 0xFF8700, pos); break;
                case "ANT": x = driver(ANT, "ANT", "Kimi Antonelli", "Mercedes", MERCEDES, 0x00D2BE, pos); break;
                case "PIA": x = driver(PIA, "PIA", "Oscar Piastri", "McLaren", MCLAREN, 0xFF8700, pos); break;
                case "LEC": x = driver(LEC, "LEC", "Charles Leclerc", "Ferrari", FERRARI, 0xDC0000, pos); break;
                default: x = driver(RUS, "RUS", "George Russell", "Mercedes", MERCEDES, 0x00D2BE, pos);
            }
            s.order.add(x);
            pos++;
        }
    }

    static void watch(SportEvent.Session s, String channel, String app, int no, boolean sure, String listedBy) {
        s.watch = new SportEvent.SessionWatch();
        s.watch.channel = channel; s.watch.app = app; s.watch.no = no; s.watch.sure = sure; s.watch.known = true; s.watch.listedBy = listedBy;
    }

    /** Greece: race on ANT1 (likely, ch. 107), the other sessions on ANT1+ (the Antenna app). */
    static SportEvent vegas() {
        SportEvent e = SportTestKit.lasVegas();
        for (SportEvent.Session s : e.sessions)
            if ("RACE".equals(s.kind)) watch(s, "ANT1", Cards.MAGENTA, 107, false, "");
            else watch(s, "ANT1+", Cards.ANT1, 0, false, "");
        return e;
    }

    static SportEvent.Session kind(SportEvent e, String kind) {
        for (SportEvent.Session s : e.sessions) if (kind.equals(s.kind)) return s;
        throw new AssertionError(kind);
    }

    static void done(SportEvent e, String... kinds) { for (String k : kinds) kind(e, k).state = "post"; }

    static SportEvent derived(SportEvent e) { SportEngine.derive(e); return e; }

    static SportPrefs focus(String... refs) { return new SportPrefs(Arrays.asList(refs), Sports.SCOPE_ALL, null); }

    static SportPrefs fighters(String... ids) { return new SportPrefs(null, Sports.SCOPE_ALL, new HashSet<>(Arrays.asList(ids))); }

    /** China 2026's sprint weekend shape: FP1, sprint qualifying, sprint, qualifying, race. */
    static SportEvent sprintWeekend() {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = "600057428"; e.name = "Heineken Chinese Grand Prix"; e.shortName = "Heineken Chinese GP";
        e.circuitId = "619"; e.city = "Shanghai"; e.country = "China"; e.laps = 56;
        long fp1 = LV_FP1;
        e.sessions.add(SportTestKit.session("c1", "FP1", fp1));
        e.sessions.add(SportTestKit.session("c2", "SQ", fp1 + 4 * H));
        e.sessions.add(SportTestKit.session("c3", "SPRINT", fp1 + DAY));
        e.sessions.add(SportTestKit.session("c4", "QUAL", fp1 + DAY + 4 * H));
        e.sessions.add(SportTestKit.session("c5", "RACE", fp1 + 2 * DAY + 4 * H));
        for (SportEvent.Session s : e.sessions) watch(s, "ANT1+", Cards.ANT1, 0, false, "");
        e.start = fp1; e.end = fp1 + 2 * DAY + 4 * H;
        return e;
    }

    // ---------- UFC ----------
    static SportEvent.Fighter fighter(String id, String name, String record) { return SportTestKit.fighter(id, name, record); }

    /** UFC 334 as ESPN has it: the main event's order 1 is Hokit, the billing says "Gane vs. Hokit". */
    static SportEvent u334() {
        SportEvent e = SportTestKit.ufc334();
        SportEvent.Bout main = e.mainEvent();
        SportEvent.Fighter gane = main.a;
        main.a = main.b; main.b = gane;  // a = Hokit (ESPN order 1), b = Gane
        for (SportEvent.Session s : e.sessions) {
            for (SportEvent.Bout b : s.bouts) {
                if (b.a != null) b.a.recordBefore = b.a.record;
                if (b.b != null) b.b.recordBefore = b.b.record;
            }
            if ("MAIN".equals(s.kind)) watch(s, "Magenta Sport 6", Cards.MAGENTA, 310, true, "");
            else watch(s, "UFC Fight Pass", null, 0, false, "");
        }
        return e;
    }

    static SportEvent.Session seg(SportEvent e, String kind) { return kind(e, kind); }

    static void segDone(SportEvent e, String kind, boolean bouts) {
        SportEvent.Session s = seg(e, kind);
        s.state = "post";
        if (bouts) for (SportEvent.Bout b : s.bouts) { b.state = "post"; b.outcome = 1; b.method = "DEC"; b.methodDetail = "Unanimous"; b.round = 3; b.clock = "5:00"; }
    }

    /** UFC 334 finished: Gane (b) beat Hokit by KO/TKO in round 3 at 2:27. */
    static SportEvent u334Result(int outcome, String method, String detail, int round, String clock) {
        SportEvent e = u334();
        segDone(e, "EARLY", true);
        segDone(e, "PRELIMS", true);
        segDone(e, "MAIN", true);
        SportEvent.Bout m = e.mainEvent();
        m.outcome = outcome; m.method = method; m.methodDetail = detail; m.round = round; m.clock = clock;
        if (outcome == 2) { m.b.record = "15-2-0"; m.a.record = "9-1-0"; }  // post-fight records: never drawn
        return derived(e);
    }

    static SportEvent fightNight() {
        SportEvent e = SportTestKit.ufc("600061399", "UFC Fight Night: Cortes Acosta vs. Walker", U334_EARLY - 7 * DAY);
        SportEvent.Bout main = e.mainEvent();
        main.a = fighter("3156612", "Johnny Walker", "21-9-0");
        main.b = fighter("4291093", "Waldo Cortes Acosta", "15-2-0");
        main.b.shortName = "W. Cortes Acosta";
        main.title = false; main.note = "Heavyweight - Main Event";
        return e;
    }

    // ---------- the matrix ----------
    static List<Fx> all() {
        List<Fx> out = new ArrayList<>();
        out.addAll(f1());
        out.addAll(ufc());
        return out;
    }

    static List<Fx> f1() {
        List<Fx> f = new ArrayList<>();
        SportPrefs none = SportPrefs.none();
        CardModel.State NEXT = CardModel.State.NEXT, LIVE = CardModel.State.LIVE, RESULT = CardModel.State.RESULT;

        f.add(new Fx("f1_week", derived(vegas()), none, LV_FP1 - 5 * DAY, false, false, NEXT));
        f.add(new Fx("f1_tomorrow_fp1", derived(vegas()), none, LV_FP1 - 14 * H - 30 * MIN, false, false, NEXT));

        SportEvent q = vegas();
        done(q, "FP1", "FP2", "FP3");
        f.add(new Fx("f1_weekend_qual_next", derived(q), none, LV_FP3 + 2 * H, false, false, NEXT));

        SportEvent sp = sprintWeekend();
        done(sp, "FP1", "SQ");
        order(kind(sp, "SQ"), "NOR", "VER", "ANT", "PIA", "LEC");
        f.add(new Fx("f1_sprint_next", derived(sp), none, LV_FP1 + 20 * H, false, false, NEXT));

        f.add(new Fx("f1_live_race", liveRace(""), none, LV_RACE + H, false, false, LIVE));
        f.add(new Fx("f1_live_race_focus", liveRace(""), focus("d:" + NOR), LV_RACE + H, false, false, LIVE));

        SportEvent lq = vegas();
        done(lq, "FP1", "FP2", "FP3");
        kind(lq, "QUAL").state = "in";
        order(kind(lq, "QUAL"), "VER", "ANT", "NOR", "PIA", "LEC");
        f.add(new Fx("f1_live_qual", derived(lq), none, kind(lq, "QUAL").start + 20 * MIN, false, false, LIVE));

        f.add(new Fx("f1_red_flag", liveRace("RED_FLAG"), none, LV_RACE + H, false, false, CardModel.State.SUSPENDED));
        f.add(new Fx("f1_result", raceDone("VER", "NOR", "ANT", "PIA", "LEC"), none, LV_RACE + 5 * H, true, false, RESULT));
        f.add(new Fx("f1_result_focus_p5", raceDone("VER", "PIA", "ANT", "LEC", "NOR"), focus("d:" + NOR), LV_RACE + 5 * H, true, false, RESULT));
        f.add(new Fx("f1_result_hidden", raceDone("VER", "NOR", "ANT", "PIA", "LEC"), none, LV_RACE + 5 * H, true, false, RESULT));

        f.add(new Fx("f1_cancelled", bahrainCancelled(), none, LV_FP1 - 2 * DAY, false, false, CardModel.State.OFF));

        SportEvent dl = vegas();
        kind(dl, "FP1").detail = "DELAYED";
        f.add(new Fx("f1_delayed", derived(dl), none, LV_FP1 - H, false, false, NEXT));

        SportEvent aw = vegas();
        done(aw, "FP1", "FP2", "FP3", "QUAL", "RACE");
        kind(aw, "RACE").detail = "AWAITING";
        f.add(new Fx("f1_awaiting", derived(aw), none, LV_RACE + 7 * H, true, false, CardModel.State.AWAITING));

        f.add(new Fx("f1_offseason", derived(australia2027()), none, LV_RACE + 25 * DAY, false, false, NEXT));
        f.add(new Fx("f1_stale", derived(vegas()), none, LV_FP1 - 5 * DAY, false, true, NEXT));

        SportEvent ln = vegas();
        ln.id = "600099001"; ln.circuitId = "9001"; ln.name = "MSC Cruises Emilia-Romagna Grand Prix"; ln.city = "Imola";
        f.add(new Fx("f1_long_name", derived(ln), none, LV_FP1 - 5 * DAY, false, false, NEXT));

        SportEvent gb = SportTestKit.lasVegas();
        for (SportEvent.Session s : gb.sessions) watch(s, "Sky Sports F1", null, 0, false, "table");
        f.add(new Fx("f1_abroad_gb", derived(gb), none, LV_FP1 - 5 * DAY, false, false, NEXT));

        SportEvent no = vegas();
        no.id = "600099002"; no.circuitId = "9999"; no.name = "Heineken Atlantis Grand Prix"; no.city = "Atlantis";
        f.add(new Fx("f1_no_outline", derived(no), none, LV_FP1 - 5 * DAY, false, false, NEXT));
        return f;
    }

    static SportEvent liveRace(String detail) {
        SportEvent e = vegas();
        done(e, "FP1", "FP2", "FP3", "QUAL");
        order(kind(e, "QUAL"), "NOR", "VER", "ANT", "PIA", "LEC");
        SportEvent.Session r = kind(e, "RACE");
        r.state = "in"; r.detail = detail; r.lap = 34; r.laps = 50;
        order(r, "ANT", "VER", "NOR", "PIA", "LEC");
        return derived(e);
    }

    static SportEvent raceDone(String... codes) {
        SportEvent e = vegas();
        done(e, "FP1", "FP2", "FP3", "QUAL", "RACE");
        order(kind(e, "QUAL"), "NOR", "VER", "ANT", "PIA", "LEC");
        order(kind(e, "RACE"), codes);
        SportEvent.Session r = kind(e, "RACE");
        r.lap = 50; r.laps = 50;
        return derived(e);
    }

    static SportEvent bahrainCancelled() {
        SportEvent e = vegas();
        e.id = "600057430"; e.circuitId = "603"; e.name = "Gulf Air Bahrain Grand Prix"; e.city = "Sakhir"; e.country = "Bahrain";
        for (SportEvent.Session s : e.sessions) { s.state = "off"; s.detail = "CANCELLED"; }
        return derived(e);
    }

    static SportEvent australia2027() {
        SportEvent e = vegas();
        long shift = 112 * DAY;  // FP1 2027-03-12T00:30Z, race Sunday 14 March
        e.id = "600070000"; e.circuitId = "9100"; e.name = "Qatar Airways Australian Grand Prix"; e.city = "Melbourne";
        for (SportEvent.Session s : e.sessions) s.start += shift;
        e.start += shift; e.end += shift;
        return e;
    }

    static List<Fx> ufc() {
        List<Fx> f = new ArrayList<>();
        SportPrefs none = SportPrefs.none();
        CardModel.State NEXT = CardModel.State.NEXT, LIVE = CardModel.State.LIVE, RESULT = CardModel.State.RESULT;

        f.add(new Fx("ufc_next_numbered", derived(u334()), none, U334_EARLY - 3 * DAY, false, false, NEXT));
        f.add(new Fx("ufc_next_fightnight", derived(fightNight()), none, U334_EARLY - 10 * DAY, false, false, NEXT));
        f.add(new Fx("ufc_next_followed_fighter", derived(u334()), fighters("21"), U334_EARLY - 3 * DAY, false, false, NEXT));
        f.add(new Fx("ufc_today_prelims_soon", derived(u334()), fighters("11"), U334_EARLY - H, false, false, NEXT));

        SportEvent lp = u334();
        segDone(lp, "EARLY", true);
        seg(lp, "PRELIMS").state = "in";
        SportEvent.Bout b4 = seg(lp, "PRELIMS").bouts.get(0);
        b4.state = "in"; b4.round = 2; b4.clock = "3:41";
        f.add(new Fx("ufc_live_prelims", derived(lp), none, U334_PRELIMS + 30 * MIN, false, false, LIVE));

        SportEvent lm = u334();
        segDone(lm, "EARLY", true);
        segDone(lm, "PRELIMS", true);
        seg(lm, "MAIN").state = "in";
        seg(lm, "MAIN").bouts.get(0).state = "post";  // the co-main is over
        seg(lm, "MAIN").bouts.get(0).outcome = 2;
        SportEvent.Bout me = lm.mainEvent();
        me.state = "in"; me.round = 2; me.clock = "1:12";
        f.add(new Fx("ufc_live_main_r2", derived(lm), none, U334_MAIN + 90 * MIN, false, false, LIVE));

        SportEvent bs = u334();
        segDone(bs, "EARLY", true);
        segDone(bs, "PRELIMS", true);
        f.add(new Fx("ufc_between_segments", derived(bs), none, U334_MAIN - 20 * MIN, false, false, LIVE));

        f.add(new Fx("ufc_result", u334Result(2, "KO/TKO", "Punch", 3, "2:27"), none, U334_MAIN + 6 * H, true, false, RESULT));
        f.add(new Fx("ufc_result_hidden", u334Result(2, "KO/TKO", "Punch", 3, "2:27"), none, U334_MAIN + 6 * H, true, false, RESULT));
        f.add(new Fx("ufc_result_draw", u334Result(3, "DEC", "Split Decision", 5, "5:00"), none, U334_MAIN + 6 * H, true, false, RESULT));
        f.add(new Fx("ufc_result_nc", u334Result(4, "NC", "Overturned", 2, "1:05"), none, U334_MAIN + 6 * H, true, false, RESULT));

        SportEvent rn = u334();
        rn.name = "UFC 334: Gane vs. Volkov";
        rn.mainEvent().a = fighter("2504207", "Alexander Volkov", "38-11-0");
        f.add(new Fx("ufc_renamed", derived(rn), none, U334_EARLY - 3 * DAY, false, false, NEXT));

        SportEvent cx = u334();
        for (SportEvent.Session s : cx.sessions) { s.state = "off"; s.detail = "CANCELLED"; }
        f.add(new Fx("ufc_cancelled", derived(cx), none, U334_EARLY - 2 * DAY, false, false, CardModel.State.OFF));

        f.add(new Fx("ufc_stale", derived(u334()), none, U334_EARLY - 3 * DAY, false, true, NEXT));

        SportEvent lg = SportTestKit.ufc("600061500", "UFC 335: Rakhmonov vs. Della Maddalena", U334_EARLY + 21 * DAY);
        SportEvent.Bout lm2 = lg.mainEvent();
        lm2.a = fighter("4374400", "Shavkat Rakhmonov", "19-0-0");
        lm2.b = fighter("4239928", "Jack Della Maddalena", "18-2-0");
        lm2.b.shortName = "J. Della Maddalena";
        f.add(new Fx("ufc_long_names", derived(lg), none, U334_EARLY + 18 * DAY, false, false, NEXT));

        SportEvent ab = SportTestKit.ufc334();
        watch(seg(ab, "MAIN"), "TNT Sports", null, 0, false, "table");
        f.add(new Fx("ufc_abroad_gb", derived(ab), none, U334_EARLY - 3 * DAY, false, false, NEXT));

        SportEvent nh = u334();
        nh.mainEvent().a.headshot = "";
        f.add(new Fx("ufc_no_headshots", derived(nh), none, U334_EARLY - 3 * DAY, false, false, NEXT));
        return f;
    }

    static Fx named(String name) {
        for (Fx x : all()) if (x.name.equals(name)) return x;
        throw new AssertionError(name);
    }
}
