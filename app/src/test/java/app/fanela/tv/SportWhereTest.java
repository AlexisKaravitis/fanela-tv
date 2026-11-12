package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Where to watch F1 and UFC (SportWhere): the Greek session and segment split, the rows per country with their dates,
 * the words, and the Greek guides matched by time against the MagentaTV and Sport24 samples (8 and 15 Nov 2026).
 */
public class SportWhereTest {
    static final long H = 3600000L, MIN = 60000L, DAY = 24 * H;
    static final long SAO_PAULO_RACE = Sources.iso("2026-11-08T17:00:00Z");   // 19:00 Athens; ANT1 lists it at 18:45
    static final long FN_MAIN = Sources.iso("2026-11-08T01:00:00Z");          // UFC Fight Night, 8 Nov: main card
    static final long U334_MAIN = SportTestKit.U334_MAIN;                     // 2026-11-15T02:00Z

    @Before public void table() throws Exception {
        SportWhere.use(SportWhere.parse(SportRightsDataTest.text()));
        I18n.use("en");
    }

    @After public void reset() {
        SportWhere.use(null);  // the engine's tests run without a table
        I18n.use("en");
        Where.use("GR");
    }

    static SportEvent.Session kind(SportEvent e, String kind) {
        for (SportEvent.Session s : e.sessions) if (kind.equals(s.kind)) return s;
        throw new AssertionError(kind);
    }

    /** São Paulo 2026 as a sprint weekend: SQ and sprint on Friday/Saturday, qualifying Saturday, race Sunday 17:00Z. */
    static SportEvent saoPaulo() {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = "600057448"; e.name = "MSC Cruises São Paulo Grand Prix";
        e.sessions.add(SportTestKit.session("a1", "FP1", SAO_PAULO_RACE - 2 * DAY - 2 * H - 30 * MIN));
        e.sessions.add(SportTestKit.session("a2", "SQ", SAO_PAULO_RACE - 2 * DAY + 1 * H + 30 * MIN));
        e.sessions.add(SportTestKit.session("a3", "SPRINT", SAO_PAULO_RACE - DAY - 3 * H));
        e.sessions.add(SportTestKit.session("a4", "QUAL", SAO_PAULO_RACE - DAY + H));
        e.sessions.add(SportTestKit.session("a5", "RACE", SAO_PAULO_RACE));
        e.start = e.sessions.get(0).start; e.end = SAO_PAULO_RACE;
        return e;
    }

    static SportEvent fightNight() { return SportTestKit.ufc("600061399", "UFC Fight Night: Cortes Acosta vs. Walker", FN_MAIN - 4 * H); }

    static List<Guide.Airing> magenta() throws Exception {
        return SportWhere.parseMagenta(new JSONObject(GuideGenreTest.res("magentatv_search_UFC.json")));
    }

    static List<Guide.Airing> sport24() throws Exception {
        List<Guide.Airing> out = GuideGenreTest.sport24("sport24_guide_2026-11-08.json");
        out.addAll(GuideGenreTest.sport24("sport24_guide_2026-11-15.json"));
        return out;
    }

    // ---------- the table ----------
    @Test public void greeceSplitsF1BySession() {
        SportEvent e = SportTestKit.lasVegas();
        SportWhere.guess(e, "GR");
        for (String k : new String[]{"FP1", "FP2", "FP3", "QUAL"}) {
            SportEvent.SessionWatch w = kind(e, k).watch;
            assertTrue(k, w.known);
            assertFalse(k, w.sure);
            assertEquals(k, "ANT1+", w.channel);
            assertEquals(k, Cards.ANT1, w.app);
            assertEquals(k + ": a service, names only (never ANT1's logo or number)", "table", w.listedBy);
            assertEquals(0, w.no);
        }
        SportEvent.SessionWatch race = kind(e, "RACE").watch;
        assertEquals("ANT1", race.channel);
        assertEquals(Cards.MAGENTA, race.app);
        assertEquals("a Greek channel: the line-up's logo", "", race.listedBy);
        assertFalse(race.sure);
        assertEquals("a guess never shows a number", 0, race.no);

        SportEvent sp = saoPaulo();
        SportWhere.guess(sp, "GR");
        assertEquals("ANT1", kind(sp, "SPRINT").watch.channel);
        assertEquals("ANT1+", kind(sp, "SQ").watch.channel);
        assertEquals("ANT1+", kind(sp, "QUAL").watch.channel);
        assertEquals("ANT1", kind(sp, "RACE").watch.channel);
    }

    @Test public void greeceSplitsUfcBySegment() {
        SportEvent e = SportTestKit.ufc334();
        SportWhere.guess(e, "GR");
        SportEvent.SessionWatch main = kind(e, "MAIN").watch;
        assertEquals("Magenta Sport 6", main.channel);
        assertEquals(Cards.MAGENTA, main.app);
        assertEquals("", main.listedBy);
        assertFalse(main.sure);
        for (String k : new String[]{"PRELIMS", "EARLY"}) {
            SportEvent.SessionWatch w = kind(e, k).watch;
            assertEquals(k, "UFC Fight Pass", w.channel);
            assertNull(k, w.app);
            assertEquals(k, "table", w.listedBy);
            assertFalse(w.sure);
        }
    }

    @Test public void theRowsPerCountry() {
        SportEvent f1 = SportTestKit.lasVegas(), ufc = SportTestKit.ufc334();
        SportWhere.guess(f1, "CY");
        assertEquals("Omega TV", kind(f1, "RACE").watch.channel);
        assertEquals("Omega TV", kind(f1, "QUAL").watch.channel);
        assertFalse("practice: no row in Cyprus", kind(f1, "FP1").watch.known);
        assertEquals("none", kind(f1, "FP1").watch.listedBy);
        SportWhere.guess(ufc, "CY");
        for (SportEvent.Session s : ufc.sessions) assertEquals("none", s.watch.listedBy);

        for (String cc : new String[]{"GB", "IE"}) {
            SportWhere.guess(f1, cc);
            for (SportEvent.Session s : f1.sessions) {
                assertEquals(cc, "Sky Sports F1", s.watch.channel);
                assertEquals("table", s.watch.listedBy);
                assertNull("no app abroad", s.watch.app);
            }
            SportWhere.guess(ufc, cc);
            assertEquals("TNT Sports", kind(ufc, "MAIN").watch.channel);
            assertEquals("TNT Sports", kind(ufc, "PRELIMS").watch.channel);
            assertEquals("UFC Fight Pass", kind(ufc, "EARLY").watch.channel);
        }
        SportWhere.guess(f1, "US");
        assertEquals("Apple TV", kind(f1, "FP2").watch.channel);
        SportWhere.guess(ufc, "US");
        for (SportEvent.Session s : ufc.sessions) assertEquals("Paramount+", s.watch.channel);
        SportWhere.guess(ufc, "AT");  // a copy of Germany
        assertEquals("DAZN", kind(ufc, "MAIN").watch.channel);
        assertEquals("UFC Fight Pass", kind(ufc, "PRELIMS").watch.channel);
        SportWhere.guess(ufc, "IT");
        assertEquals("HBO Max", kind(ufc, "MAIN").watch.channel);
        assertEquals("discovery+", kind(ufc, "PRELIMS").watch.channel);
        assertEquals("UFC Fight Pass", kind(ufc, "EARLY").watch.channel);
        SportWhere.guess(f1, "AT");
        assertEquals("ServusTV · ORF · Sky Sport F1", kind(f1, "RACE").watch.channel);
        SportWhere.guess(f1, "CA");  // no row at all
        for (SportEvent.Session s : f1.sessions) { assertFalse(s.watch.known); assertEquals("none", s.watch.listedBy); }
    }

    @Test public void datesBoundTheRows() {
        SportEvent jan2027 = SportTestKit.ufc("1", "UFC 340: A vs. B", Sources.iso("2027-01-10T22:00:00Z"));
        SportWhere.guess(jan2027, "GR");  // the MagentaTV deal: recheck in January
        assertEquals("none", kind(jan2027, "MAIN").watch.listedBy);
        SportWhere.guess(jan2027, "GB");  // no published end: open
        assertEquals("TNT Sports", kind(jan2027, "MAIN").watch.channel);
        SportEvent spain2027 = SportTestKit.f1("2", Sources.iso("2027-03-05T01:30:00Z"));
        SportWhere.guess(spain2027, "ES");
        assertEquals("DAZN's exclusivity ends with 2026", "none", kind(spain2027, "RACE").watch.listedBy);
        SportWhere.guess(spain2027, "GR");
        assertEquals("ANT1", kind(spain2027, "RACE").watch.channel);
        SportEvent y2029 = SportTestKit.f1("3", Sources.iso("2029-03-02T01:30:00Z"));
        SportWhere.guess(y2029, "GR");
        assertEquals("Antenna's deal is 2026-2028", "none", kind(y2029, "RACE").watch.listedBy);
        // the until day itself is covered, the next day isn't
        SportEvent last = SportTestKit.ufc("4", "UFC 339: C vs. D", Sources.iso("2026-12-31T23:00:00Z"));
        SportWhere.guess(last, "GR");
        assertEquals("UFC Fight Pass", kind(last, "EARLY").watch.channel);  // 31 Dec 23:00Z
        assertEquals("none", kind(last, "PRELIMS").watch.listedBy);         // 1 Jan 01:00Z
        assertEquals("none", kind(last, "MAIN").watch.listedBy);
    }

    @Test public void perEventRowsAliasesAndAppsAbroad() {
        Map<String, List<SportWhere.Row>> t = SportWhere.parse("{\"sports\":{\"f1\":{\"countries\":{"
            + "\"DE\":[{\"events\":[\"600057449\"],\"kinds\":[\"RACE\"],\"on\":[\"RTL\",\"Sky Sport F1\"]},"
            + "{\"kinds\":[\"*\"],\"on\":[\"Sky Sport F1\"],\"app\":\"gr.cosmote.cosmotetv.androidtv\"}],"
            + "\"AT\":\"DE\",\"CH\":\"XX\"}}}}");
        assertEquals(2, t.size());  // an alias to a missing country is dropped
        SportWhere.use(t);
        SportEvent lv = SportTestKit.lasVegas(), other = SportTestKit.f1("1", SportTestKit.LV_FP1 + 7 * DAY);
        SportWhere.guess(lv, "AT");
        SportWhere.guess(other, "AT");
        assertEquals("RTL · Sky Sport F1", kind(lv, "RACE").watch.channel);
        assertEquals("Sky Sport F1", kind(lv, "QUAL").watch.channel);
        assertEquals("Sky Sport F1", kind(other, "RACE").watch.channel);
        assertNull("the app allowlist only knows the Greek apps", kind(lv, "QUAL").watch.app);
        assertTrue(SportWhere.parse("not json").isEmpty());
        assertTrue(SportWhere.parse("{}").isEmpty());
    }

    // ---------- the words and the card's channel ----------
    @Test public void footballsWords() {
        SportEvent e = SportTestKit.lasVegas();
        SportWhere.guess(e, "GR");
        assertEquals("Likely ANT1 · on Magenta TV", SportWhere.where(kind(e, "RACE"), true));
        assertEquals("Likely on ANT1+", SportWhere.where(kind(e, "FP1"), true));
        CardModel.Channel race = SportWhere.channel(kind(e, "RACE")), fp1 = SportWhere.channel(kind(e, "FP1"));
        assertTrue(race.likely);
        assertFalse(race.namesOnly);
        assertEquals(0, race.number);
        assertTrue(fp1.likely);
        assertTrue(fp1.namesOnly);
        assertEquals(Arrays.asList("ANT1+"), fp1.names);
        I18n.use("el");
        assertEquals("Πιθανό στο ANT1+", SportWhere.where(kind(e, "FP1"), true));
        I18n.use("en");
        SportWhere.guess(e, "CY");
        assertEquals("Check your provider's guide", SportWhere.where(kind(e, "FP1"), true));
        assertNull(SportWhere.channel(kind(e, "FP1")));
        assertEquals("Likely on Omega TV", SportWhere.where(kind(e, "RACE"), true));
        SportWhere.guess(e, "GB");
        assertEquals("likely on Sky Sports F1", SportWhere.where(kind(e, "RACE"), false));
        assertEquals("", SportWhere.where(null, true));
        assertEquals("ANT1+", SportWhere.appName(Cards.ANT1));
        assertEquals("Magenta TV", SportWhere.appName(Cards.MAGENTA));
    }

    // ---------- the Greek guides ----------
    @Test public void f1IsConfirmedBySport24NearTheSessionStart() throws Exception {
        List<Guide.Airing> s24 = sport24();
        SportEvent sp = saoPaulo();
        Guide.Airing a = SportWhere.matchF1(kind(sp, "RACE"), s24);
        assertNotNull(a);
        assertEquals("133628", a.id);  // 18:45 Athens, ANT1, for the 19:00 race
        assertEquals("ANT1", a.channel);
        assertNull("nothing listed for the sprint in these samples", SportWhere.matchF1(kind(sp, "SPRINT"), s24));
        // the window: 45 min before to 10 min after the session start
        SportEvent.Session s = SportTestKit.session("x", "RACE", a.start + 45 * MIN);
        assertNotNull(SportWhere.matchF1(s, s24));
        s.start = a.start + 46 * MIN;
        assertNull(SportWhere.matchF1(s, s24));
        s.start = a.start - 10 * MIN;
        assertNotNull(SportWhere.matchF1(s, s24));
        s.start = a.start - 11 * MIN;
        assertNull(SportWhere.matchF1(s, s24));
        s.start = a.start + 15 * MIN;
        s.tbd = true;
        assertNull("no time, no match", SportWhere.matchF1(s, s24));
        // the show before the race and the other motor sports never confirm F1
        List<Guide.Airing> others = new ArrayList<>();
        for (Guide.Airing x : s24) if (!"133628".equals(x.id)) others.add(x);
        Guide.Airing show = air("Formula 1 Show", SportWhere.GENRE_MOTOR, SAO_PAULO_RACE - 60 * MIN, "ANT1");
        others.add(show);
        others.add(air("Formula 2 2026 - Γκραν Πρι Κατάρ", SportWhere.GENRE_MOTOR, SAO_PAULO_RACE - 5 * MIN, "ANT1"));
        others.add(air("Formula 1 2026 - Γκραν Πρι Σάο Πάουλο", "Football", SAO_PAULO_RACE, "ANT1"));
        assertNull(SportWhere.matchF1(kind(sp, "RACE"), others));
        // MagentaTV's search doesn't find ANT1's F1 airings: never used for F1
        assertTrue(SportWhere.parseMagenta(new JSONObject(GuideGenreTest.res("magentatv_search_F1.json"))).isEmpty());
    }

    @Test public void ufcIsMatchedByTimeNeverByTitle() throws Exception {
        List<Guide.Airing> mag = magenta();
        assertEquals("live airings only: the replays on Magenta Sport Start and Sport 6 are left out", 2, mag.size());
        for (Guide.Airing a : mag) {
            assertEquals("Magenta Sport 6", a.channel);
            assertEquals(310, a.number);
            assertTrue(a.logo.startsWith("https://"));
            assertTrue(a.title.contains("UFC Fight Night 2026"));  // numbered events too
        }
        List<Guide.Airing> all = new ArrayList<>(mag);
        all.addAll(sport24());
        Guide.Airing a334 = SportWhere.matchUfc(SportTestKit.ufc334(), all);
        assertNotNull(a334);
        assertEquals(U334_MAIN, a334.start);
        assertEquals("a tie with Sport24: MagentaTV's, with its number", 310, a334.number);
        Guide.Airing afn = SportWhere.matchUfc(fightNight(), all);
        assertEquals(FN_MAIN, afn.start);
        assertEquals(310, afn.number);
        // Sport24 alone: its numbered tag must agree with a numbered event
        List<Guide.Airing> s24 = sport24();
        Guide.Airing only = SportWhere.matchUfc(SportTestKit.ufc334(), s24);
        assertEquals("334: Σιρίλ Γκάνε - Τζος Χόκιτ", only.tag);
        assertNull("UFC 335 at UFC 334's time: Sport24 says 334",
            SportWhere.matchUfc(SportTestKit.ufc("9", "UFC 335: X vs. Y", SportTestKit.U334_EARLY), s24));
        assertNotNull("MagentaTV has no number to disagree",
            SportWhere.matchUfc(SportTestKit.ufc("9", "UFC 335: X vs. Y", SportTestKit.U334_EARLY), mag));
        // ±30 min of the main card, nearest first
        assertNotNull(SportWhere.matchUfc(SportTestKit.ufc("8", "UFC Fight Night: A vs. B", SportTestKit.U334_EARLY + 30 * MIN), mag));
        assertNull(SportWhere.matchUfc(SportTestKit.ufc("8", "UFC Fight Night: A vs. B", SportTestKit.U334_EARLY + 31 * MIN), mag));
        assertNull("the prelims' time is not the main card's", SportWhere.matchUfc(
            SportTestKit.ufc("8", "UFC Fight Night: A vs. B", SportTestKit.U334_EARLY + 2 * H), all));
        assertNull("8 Nov 22:00Z is a replay on Sport 6", SportWhere.matchUfc(
            SportTestKit.ufc("8", "UFC Fight Night: A vs. B", Sources.iso("2026-11-08T18:00:00Z")), all));
        List<Guide.Airing> two = new ArrayList<>(Arrays.asList(
            air("UFC Fight Night 2026 (Ζ)", "Martial Arts", U334_MAIN - 20 * MIN, "Magenta Sport 5"),
            air("UFC Fight Night 2026 (Ζ)", "Martial Arts", U334_MAIN + 5 * MIN, "Magenta Sport 6")));
        assertEquals("Magenta Sport 6", SportWhere.matchUfc(SportTestKit.ufc334(), two).channel);
        two.add(air("PFL Europe 2026", SportWhere.GENRE_COMBAT, U334_MAIN, "Novasports 4"));
        assertEquals("Magenta Sport 6", SportWhere.matchUfc(SportTestKit.ufc334(), two).channel);  // not UFC
        assertEquals(334, SportWhere.tagNumber("334: Σιρίλ Γκάνε - Τζος Χόκιτ"));
        assertEquals(0, SportWhere.tagNumber("Βάλντο Κορτές Ακόστα - Τζόνι Γουόκερ"));
        assertEquals(0, SportWhere.tagNumber(null));
    }

    @Test public void aConfirmationGivesTheChannelNumberAndLogo() throws Exception {
        SportTestKit.Ctx c = SportTestKit.context();
        try {
            Files.copy(new File(new File("src/main/res/raw/channels.json").exists() ? "src/main/res/raw/channels.json"
                : "app/src/main/res/raw/channels.json").toPath(), new File(c.dir, "channels.json").toPath());
            Channels.load(c);
            SportEvent ufc = SportTestKit.ufc334(), sp = saoPaulo();
            List<SportEvent> all = Arrays.asList(ufc, sp);
            for (SportEvent e : all) SportWhere.guess(e, "GR");
            // before: likely, the line-up's logo, no number
            List<Channels.Logo> l = SportWhere.logos(c, kind(ufc, "MAIN"));
            assertEquals(1, l.size());
            assertTrue(l.get(0).url.contains("magenta_sport6"));
            assertEquals(0, l.get(0).no);
            assertTrue(SportWhere.logos(c, kind(sp, "RACE")).get(0).url.contains("ant1"));
            assertNull("ANT1+ never takes ANT1's logo", SportWhere.logos(c, kind(sp, "FP1")).get(0).url);
            assertNull(SportWhere.logos(c, kind(ufc, "PRELIMS")).get(0).url);

            SportWhere.confirm(all, sport24(), magenta(), Sources.iso("2026-11-07T12:00:00Z"));
            SportEvent.SessionWatch race = kind(sp, "RACE").watch;
            assertTrue(race.sure);
            assertEquals("ANT1", race.channel);
            assertEquals(107, race.no);
            assertEquals(Cards.MAGENTA, race.app);
            assertEquals("", race.listedBy);
            assertEquals("ANT1 · ch. 107 on Magenta TV", SportWhere.where(kind(sp, "RACE"), true));
            assertEquals(107, SportWhere.logos(c, kind(sp, "RACE")).get(0).no);
            assertFalse("not listed: still the table's", kind(sp, "QUAL").watch.sure);
            assertEquals("ANT1+", kind(sp, "QUAL").watch.channel);
            SportEvent.SessionWatch main = kind(ufc, "MAIN").watch;
            assertTrue(main.sure);
            assertEquals("Magenta Sport 6", main.channel);
            assertEquals(310, main.no);
            assertTrue(main.logo.contains("magenta_sport6"));
            assertEquals("Magenta Sport 6 · ch. 310 on Magenta TV", SportWhere.where(kind(ufc, "MAIN"), true));
            CardModel.Channel ch = SportWhere.channel(kind(ufc, "MAIN"));
            assertFalse(ch.likely);
            assertEquals(310, ch.number);
            assertEquals("UFC Fight Pass", kind(ufc, "PRELIMS").watch.channel);
            assertFalse(kind(ufc, "PRELIMS").watch.sure);

            // the next pass's guess keeps the confirmation in Greece, and forgets it abroad
            SportWhere.guess(ufc, "GR");
            assertTrue(kind(ufc, "MAIN").watch.sure);
            assertEquals(310, kind(ufc, "MAIN").watch.no);
            SportWhere.guess(ufc, "GB");
            assertFalse(kind(ufc, "MAIN").watch.sure);
            assertEquals("TNT Sports", kind(ufc, "MAIN").watch.channel);

            // sessions over, cancelled or more than 8 days ahead are left alone
            SportEvent late = saoPaulo();
            SportWhere.guess(late, "GR");
            SportWhere.confirm(Collections.singletonList(late), sport24(), magenta(), SAO_PAULO_RACE + 7 * H);
            assertFalse(kind(late, "RACE").watch.sure);
            SportEvent off = saoPaulo();
            SportWhere.guess(off, "GR");
            kind(off, "RACE").state = "off";
            SportWhere.confirm(Collections.singletonList(off), sport24(), magenta(), SAO_PAULO_RACE - DAY);
            assertFalse(kind(off, "RACE").watch.sure);
            SportWhere.confirm(Collections.singletonList(off), sport24(), magenta(), SAO_PAULO_RACE - 9 * DAY);
            assertFalse(kind(off, "SPRINT").watch.sure);
        } finally {
            SportTestKit.delete(c.dir);
        }
    }

    @Test public void withoutTheTableApplyChangesNothingAndNeverGoesOnline() throws Exception {
        SportWhere.use(null);
        SportTestKit.Ctx c = SportTestKit.context();
        try {
            SportEvent e = SportTestKit.f1("1", System.currentTimeMillis() + DAY);
            SportWhere.guess(e, "GR");
            assertEquals("none", e.sessions.get(0).watch.listedBy);
            SportWhere.apply(c, Collections.singletonList(e), "GR");  // the test Context has no resources
            assertEquals("none", e.sessions.get(0).watch.listedBy);
            assertFalse(new File(c.dir, "guide_sport24.json").exists());
        } finally {
            SportTestKit.delete(c.dir);
        }
    }

    static Guide.Airing air(String title, String genre, long start, String channel) {
        Guide.Airing a = new Guide.Airing();
        a.title = title; a.genre = genre; a.start = start; a.channel = channel; a.live = true; a.tag = ""; a.id = title + start;
        return a;
    }
}
