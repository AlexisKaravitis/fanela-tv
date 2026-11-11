package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Which where-to-watch source each country gets, the season table, and the viewer's country by default. */
public class WhereTest {
    static final List<String> SEEDED = Arrays.asList("GB", "IE", "US", "CA", "ES", "IT", "DE", "AT", "FR", "PT", "NL", "BE", "TR", "CY", "GR");

    static Map<String, SeasonTableProvider.Row> table() throws Exception {
        File f = new File("src/main/res/raw/broadcasters.json");
        if (!f.exists()) f = new File("app/src/main/res/raw/broadcasters.json");
        return SeasonTableProvider.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    static Match match(String league, String home, String away, String homeId, boolean clubHome, String when) {
        Match m = new Match();
        m.club = clubHome ? homeId : "999"; m.team = "FC"; m.source = "espn"; m.key = m.club + "|espn:1";
        m.league = league; m.comp = league; m.home = home; m.away = away; m.homeId = homeId; m.clubIsHome = clubHome;
        m.state = "pre"; m.start = Sources.iso(when);
        return m;
    }

    @Before public void load() throws Exception { SeasonTableProvider.use(table()); }

    @After public void reset() { Where.use("GR"); I18n.use("en"); }

    // ---------- provider choice ----------
    @Test public void greeceKeepsItsOwnSources() {
        assertEquals(Arrays.asList("gr", "table"), Where.chain("GR", false, false));
        assertEquals("Ronin and the TV guide never replace the Greek sources", Arrays.asList("gr", "table"), Where.chain("GR", true, true));
    }

    @Test public void elsewhereRoninOnlyWithAKey() {
        assertEquals(Arrays.asList("table"), Where.chain("GB", false, false));
        assertEquals(Arrays.asList("ronin", "table"), Where.chain("GB", true, false));
        assertEquals(Arrays.asList("ronin", "table"), Where.chain("US", true, false));
        assertEquals("the TV's own guide applies last", Arrays.asList("ronin", "tv", "table"), Where.chain("GB", true, true));
        assertEquals(Arrays.asList("tv", "table"), Where.chain("US", false, true));
    }

    @Test public void roninIsOffWithoutAKey() {
        assertFalse(RoninProvider.enabled());  // the test build has no key
        assertFalse("off without the switch", TvGuideProvider.enabled(null));
        assertEquals(2, Where.providers(null, "GR").size());
        assertTrue(Where.providers(null, "GR").get(0) instanceof GreeceProvider);
        assertTrue(Where.providers(null, "GB").get(0) instanceof SeasonTableProvider);
    }

    // ---------- the Greek path is unchanged ----------
    @Test public void olympiacosLooksAsBefore() {
        Where.use("GR");
        Match m = match("gre.1", "Olympiacos", "PAOK", "435", true, "2026-11-22T17:00Z");
        Where.guess(m);
        assertEquals("Magenta Sport", m.channel);
        assertEquals(Cards.MAGENTA, m.channelApp);
        assertTrue(m.channelKnown);
        assertFalse(m.channelSure);
        assertFalse(Where.plain(m));
        Match away = match("gre.1", "PAOK", "Olympiacos", "605", false, "2026-11-22T17:00Z");
        Where.guess(away);
        assertEquals("Novasports", away.channel);
        Match ucl = match("uefa.champions", "Olympiacos", "Arsenal", "435", true, "2026-11-25T20:00Z");
        Where.guess(ucl);
        assertEquals("the rights table wins over the season table in Greece", "Magenta Sport · MEGA", ucl.channel);
        assertFalse(Where.plain(ucl));
        I18n.use("en");
        assertEquals("Likely Magenta Sport · on Magenta TV", Cards.where(m, true));
    }

    @Test public void greekViewerFollowingAnEnglishClub() {
        Where.use("GR");
        Match m = match("eng.1", "Arsenal", "Chelsea", "359", true, "2026-11-28T15:00Z");
        Where.guess(m);
        assertEquals("Nova", m.channel);
        assertTrue(Where.plain(m));
        assertFalse(m.channelSure);
    }

    // ---------- the season table ----------
    @Test public void arsenalInTheUk() {
        Where.use("GB");
        Match m = match("eng.1", "Arsenal", "Manchester City", "359", true, "2026-11-29T16:30Z");
        Where.guess(m);
        assertTrue(m.channelKnown);
        assertFalse(m.channelSure);
        assertEquals("table", m.listedBy);
        assertNull("no app to open outside Greece", m.channelApp);
        assertEquals(Arrays.asList("Sky Sports", "TNT Sports"), Cards.names(m));
        I18n.use("en");
        assertEquals("Likely on Sky Sports or TNT Sports", Cards.where(m, true));
        assertEquals("likely on Sky Sports or TNT Sports", Cards.where(m, false));
        I18n.use("el");
        assertEquals("Πιθανό στο Sky Sports ή TNT Sports", Cards.where(m, true));
    }

    @Test public void noDataSaysCheckYourGuide() {
        Where.use("GB");
        Match m = match("ksa.1", "Al Nassr", "Al Hilal", "817", true, "2026-11-29T16:30Z");
        Where.guess(m);
        assertFalse(m.channelKnown);
        assertTrue(Where.noData(m));
        I18n.use("en");
        assertEquals("never a promise nobody will keep", "Check your provider's guide", Cards.where(m, true));
        I18n.use("el");
        assertEquals("Δες τον οδηγό του παρόχου σου", Cards.where(m, true));
        Where.use("AU");  // a country with no table at all
        Match a = match("eng.1", "Arsenal", "Chelsea", "359", true, "2026-11-29T16:30Z");
        Where.guess(a);
        assertFalse(a.channelKnown);
        assertTrue(Where.noData(a));
    }

    @Test public void greeceStillSaysToBeAnnounced() {
        Where.use("GR");
        Match m = match("gre.1", "Olympiacos", "PAOK", "435", true, "2027-09-20T17:00Z");  // a season the rights table doesn't know
        Where.guess(m);
        assertFalse(m.channelKnown);
        assertFalse("Greece is unchanged", Where.noData(m));
        I18n.use("en");
        assertEquals("Channel: to be announced", Cards.where(m, true));
    }

    @Test public void rowsExpireWithTheirSeason() throws Exception {
        Map<String, SeasonTableProvider.Row> t = table();
        Match now = match("esp.1", "Barcelona", "Sevilla", "83", true, "2027-03-01T20:00Z");
        Match later = match("esp.1", "Barcelona", "Sevilla", "83", true, "2027-08-20T20:00Z");
        assertNotNull(SeasonTableProvider.find(t, "ES", now));
        assertNull("2027-28 is not in the table yet", SeasonTableProvider.find(t, "ES", later));
        Match lastDay = match("esp.1", "Barcelona", "Sevilla", "83", true, "2027-06-30T21:00Z");
        assertNotNull("until covers its whole day", SeasonTableProvider.find(t, "ES", lastDay));
        Match uk = match("eng.1", "Arsenal", "Chelsea", "359", true, "2028-10-01T15:00Z");
        assertNotNull("UK rights run a year longer", SeasonTableProvider.find(t, "GB", uk));
        assertNull(SeasonTableProvider.find(t, "FR", uk));
    }

    @Test public void competitionCodeIsTheFallbackKey() throws Exception {
        Match m = match("", "Arsenal", "Chelsea", "359", true, "2026-11-29T16:30Z");
        m.league = null; m.comp = "uefa.champions";
        assertEquals("TNT Sports", SeasonTableProvider.find(table(), "GB", m).on.get(0));
    }

    @Test public void everySeededCountryIsInTheTable() throws Exception {
        Map<String, SeasonTableProvider.Row> t = table();
        for (String cc : SEEDED) {
            int rows = 0;
            for (SeasonTableProvider.Row r : t.values()) if (r.country.equals(cc)) rows++;
            assertTrue(cc + " has rows", rows >= 4);
            for (String comp : new String[]{"uefa.champions", "uefa.europa", "uefa.europa.conf"})
                assertNotNull(cc + " " + comp, t.get(cc + "|" + comp));
        }
    }

    @Test public void tableIsClean() throws Exception {
        for (SeasonTableProvider.Row r : table().values()) {
            String id = r.country + "|" + r.comp;
            assertTrue(id + " country code", r.country.matches("[A-Z]{2}"));
            assertTrue(id + " competition has a label", I18n.has(r.comp));
            assertTrue(id + " has a source", r.source.startsWith("https://"));
            assertTrue(id + " dates", r.from < r.until);
            assertEquals(id + " no duplicate names", r.on.size(), new java.util.HashSet<>(r.on).size());
            for (String n : r.on) {
                assertFalse(id + " no Greek letters (shown as is in English)", n.matches(".*[\\u0370-\\u03FF].*"));
                assertFalse(id + " one name per entry: " + n, n.contains("·") || n.contains(";") || n.contains("/"));
            }
        }
    }

    // ---------- Ronin (shape assumed until the documentation arrives) ----------
    @Test public void roninListingConfirmsTheChannel() throws Exception {
        String body = "{\"data\":[{\"id\":\"f1\",\"kickoff\":\"2026-11-29T16:30:00Z\",\"home\":{\"name\":\"Arsenal FC\"},"
            + "\"away\":{\"name\":\"Manchester City\"},\"league\":{\"name\":\"Premier League\"},\"channels\":["
            + "{\"name\":\"Sky Sports Main Event\",\"country\":\"GB\",\"logo\":\"https://x/sky.png\"},"
            + "{\"name\":\"NBC\",\"country\":\"US\"}]}]}";
        List<RoninProvider.Listing> ls = RoninProvider.parse(body, "GB");
        assertEquals(1, ls.size());
        assertEquals(Arrays.asList("Sky Sports Main Event"), ls.get(0).channels);
        Match m = match("eng.1", "Arsenal", "Manchester City", "359", true, "2026-11-29T16:30Z");
        assertNotNull(RoninProvider.find(ls, m));
        assertNull(RoninProvider.find(ls, match("eng.1", "Arsenal", "Chelsea", "359", true, "2026-11-29T16:30Z")));
        assertNull("kick-off too far off", RoninProvider.find(ls, match("eng.1", "Arsenal", "Manchester City", "359", true, "2026-11-30T16:30Z")));
        assertTrue(RoninProvider.parse(body, "FR").isEmpty());
    }

    @Test public void sameTeamNames() {
        assertTrue(RoninProvider.same("Arsenal FC", "Arsenal"));
        assertTrue(RoninProvider.same("Atlético Madrid", "Atletico Madrid"));
        assertFalse(RoninProvider.same("Manchester United", "Manchester City"));
        assertFalse(RoninProvider.same("Real Madrid", "Real Betis"));
    }

    // ---------- the viewer's country ----------
    @Test public void defaultCountry() {
        // following clubs plays no part (review #1): only the TV's region and, failing that, the language
        assertEquals("a Greek app on a US TV is in the US", "US", Leagues.defaultCountry("el", Locale.US));
        assertEquals("GR", Leagues.defaultCountry(null, new Locale("el", "GR")));
        assertEquals("GB", Leagues.defaultCountry(null, Locale.UK));
        assertEquals("US", Leagues.defaultCountry("en", Locale.US));
        assertEquals("DE", Leagues.defaultCountry(null, Locale.GERMANY));
        assertEquals("GB", Leagues.defaultCountry(null, new Locale("en", "UK")));
    }

    @Test public void anUpdateFromFanela10IsInGreece() {
        // Fanela 1.0 was Greek-only: someone who finished its setup (and so has no saved country) is in Greece,
        // whatever the TV's region; a new install starts from the TV
        assertEquals("GR", Leagues.firstCountry(true, "en", Locale.US));
        assertEquals("GR", Leagues.firstCountry(true, null, new Locale("en")));
        assertEquals("US", Leagues.firstCountry(false, "el", Locale.US));
        assertEquals("GB", Leagues.firstCountry(false, null, Locale.UK));
        assertEquals(Leagues.NONE, Leagues.firstCountry(false, "en", new Locale("en")));
    }

    @Test public void greekCompetitionsReadAsBeforeInGreece() {
        Where.use("GR");
        assertEquals("Super League", I18n.comp("gre.1"));
        assertEquals("Basket League", I18n.comp("gbl"));
        Where.use("GB");
        assertEquals("Greek Super League", I18n.comp("gre.1"));
        assertEquals("Greek Basket League", I18n.comp("gbl"));
        I18n.use("el");
        assertEquals("in Greek it is always the Super League", "Super League", I18n.comp("gre.1"));
    }

    @Test public void greekInCyprusIsCyprus() {  // review #2: the region comes before the language
        assertEquals("CY", Leagues.defaultCountry(null, new Locale("el", "CY")));
        assertEquals("CY", Leagues.defaultCountry("el", new Locale("en", "CY")));
        assertEquals("Greek with no region", "GR", Leagues.defaultCountry(null, new Locale("el")));
        assertEquals("GR", Leagues.defaultCountry("el", new Locale("en")));
    }

    @Test public void noRegionIsNoCountryNeverTheUk() {  // review #3
        assertEquals(Leagues.NONE, Leagues.defaultCountry(null, new Locale("en")));
        assertEquals("es-419", Leagues.NONE, Leagues.defaultCountry("en", new Locale("es", "419")));
        assertEquals("eng.1", Leagues.first(Leagues.NONE));
        Where.use(Leagues.NONE);
        Match m = match("eng.1", "Arsenal", "Chelsea", "359", true, "2026-11-28T15:00Z");
        Where.guess(m);
        assertFalse("no country, no channel", m.channelKnown);
        assertTrue("so the card says to check the guide", Where.noData(m));
        assertFalse("not on offer", Arrays.asList(Leagues.COUNTRIES).contains(Leagues.NONE));
        assertFalse("countries without TV data aren't offered", Leagues.countries(null, null).contains("BR"));
    }

    @Test public void theTvsCountryIsPinnedFirst() {
        List<String> l = Leagues.countries("GB", "DE");
        assertEquals("DE", l.get(0));
        assertEquals("once", 1, java.util.Collections.frequency(l, "DE"));
        assertTrue("an unlisted current country can be kept", Leagues.countries("AU", null).contains("AU"));
    }

    @Test public void homeLeaguesComeFirst() {
        assertEquals("gre.1", Leagues.first("GR"));
        assertEquals("gre.1", Leagues.first("CY"));
        assertEquals("eng.1", Leagues.first("GB"));
        assertEquals("eng.1", Leagues.first("IE"));
        assertEquals("usa.1", Leagues.first("US"));
        assertEquals("ger.1", Leagues.first("AT"));
        assertEquals("no league of its own", "eng.1", Leagues.first("AU"));
        List<Leagues.League> us = Leagues.ordered("US", false);
        assertEquals("usa.1", us.get(0).id);
        assertEquals("then the European cups", "uefa.champions", us.get(1).id);
        assertEquals(Leagues.ALL.length, us.size());
        assertEquals("the NBA leads the basketball group", "nba", us.get(us.size() - 3).id);
        List<Leagues.League> gb = Leagues.ordered("GB", false);
        assertEquals(Arrays.asList("eng.1", "uefa.champions", "uefa.europa", "uefa.europa.conf", "esp.1"),
            Arrays.asList(gb.get(0).id, gb.get(1).id, gb.get(2).id, gb.get(3).id, gb.get(4).id));
        boolean basket = false;
        for (Leagues.League l : gb) { if (l.basketball()) basket = true; else assertFalse("football before basketball: " + l.id, basket); }
        List<Leagues.League> gbGreek = Leagues.ordered("GB", true);
        assertEquals("a Greek speaker in the UK: the Super League next", "gre.1", gbGreek.get(1).id);
        for (Leagues.League l : Leagues.ALL) assertTrue(l.id + " has a label", I18n.has(l.id));
    }

    @Test public void aRowWithoutAnEndDateIsOpenEnded() throws Exception {  // review #5: no overflow past Long.MAX_VALUE
        Map<String, SeasonTableProvider.Row> t = SeasonTableProvider.parse(
            "{\"competitions\":{\"eng.1\":{\"from\":\"2025-07-01\",\"countries\":{\"GB\":[\"Sky Sports\"]}}}}");
        Match m = match("eng.1", "Arsenal", "Chelsea", "359", true, "2031-03-01T15:00Z");
        assertNotNull(SeasonTableProvider.find(t, "GB", m));
        Match before = match("eng.1", "Arsenal", "Chelsea", "359", true, "2025-05-01T15:00Z");
        assertNull("still not before it starts", SeasonTableProvider.find(t, "GB", before));
    }

    @Test public void aCountryChangeForgetsTheOldChannel() {  // review #4
        Where.use("GB");
        Match m = match("eng.1", "Arsenal", "Chelsea", "359", true, "2026-11-28T15:00Z");
        Where.guess(m);
        assertTrue(m.channel.contains("Sky Sports"));
        Where.use("US");
        Where.reguess(m);
        assertFalse(m.channel.contains("Sky Sports"));
        assertTrue(m.channel, m.channel.contains("NBC"));
        Where.use("BR");
        Where.reguess(m);
        assertTrue("no table for Brazil: no channel at all", Where.noData(m));
    }

    // ---------- the Wikipedia layer (CC BY-SA) ----------
    static Map<String, SeasonTableProvider.Row> wiki() throws Exception {
        File f = new File("src/main/res/raw/broadcasters_wikipedia.json");
        if (!f.exists()) f = new File("app/src/main/res/raw/broadcasters_wikipedia.json");
        return SeasonTableProvider.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void officialRowsWinWikipediaFillsGaps() throws Exception {
        Map<String, SeasonTableProvider.Row> t = SeasonTableProvider.merge(table(), wiki());
        assertEquals("the league's own page", Arrays.asList("Sky Sports", "TNT Sports"), t.get("GB|eng.1").on);
        assertFalse(t.get("GB|eng.1").wikipedia);
        assertEquals("UEFA's page, not Wikipedia's company names", Arrays.asList("Paramount+", "TUDN", "DAZN"), t.get("US|uefa.champions").on);
        assertFalse("Serie A in Spain: DAZN's own release", t.get("ES|ita.1").wikipedia);
        assertTrue("Serie A in Germany only comes from Wikipedia", t.get("DE|ita.1").wikipedia);
        assertEquals("DAZN", t.get("DE|ita.1").on.get(0));
        assertNotNull("LaLiga in Greece", t.get("GR|esp.1"));
        assertEquals("the NBA in Germany, from Sky's and the NBA's own releases", Arrays.asList("Sky Sport", "Prime Video"), t.get("DE|nba").on);
        assertFalse(t.get("DE|nba").wikipedia);
        assertNull("the Saudi league publishes no list: no row anywhere", t.get("GB|ksa.1"));
    }

    @Test public void wikipediaLayerIsCleanAndCredited() throws Exception {
        Map<String, SeasonTableProvider.Row> w = wiki();
        assertTrue(w.size() > 30);
        for (SeasonTableProvider.Row r : w.values()) {
            assertTrue(r.wikipedia);
            assertTrue(r.source, r.source.startsWith("https://en.wikipedia.org/w/index.php?title=") && r.source.contains("&oldid="));
            assertTrue(SEEDED.contains(r.country));
            for (String n : r.on) assertFalse(r.country + "|" + r.comp + " " + n, n.contains(";") || n.contains("/") || n.contains("("));
        }
        File f = new File("src/main/res/raw/broadcasters_wikipedia.json");
        if (!f.exists()) f = new File("app/src/main/res/raw/broadcasters_wikipedia.json");
        String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(json.contains("CC BY-SA 4.0"));
        assertTrue(SeasonTableProvider.titles(json).contains("List of Serie A broadcasters"));
    }

    // ---------- the TV's own guide ----------
    static TvGuideProvider.Programme programme(String title, String when) {
        TvGuideProvider.Programme p = new TvGuideProvider.Programme();
        p.title = title; p.text = ""; p.start = Sources.iso(when); p.channel = 1;
        return p;
    }

    @Test public void tvGuideNeedsBothTeamsNearKickOff() {
        Match m = match("eng.1", "Arsenal", "Manchester City", "359", true, "2026-11-29T16:30Z");
        List<TvGuideProvider.Programme> ps = Arrays.asList(
            programme("Premier League Live: Arsenal v Man City", "2026-11-29T16:00Z"),  // "Man City": no full name, no distinctive word
            programme("Arsenal v Manchester City", "2026-11-29T18:00Z"),             // too late
            programme("Live Premier League: Arsenal v Manchester City", "2026-11-29T16:15Z"));
        assertEquals("2026-11-29T16:15Z", "Live Premier League: Arsenal v Manchester City", TvGuideProvider.find(ps, m).title);
        Match derby = match("eng.1", "Manchester United", "Manchester City", "360", true, "2026-11-29T16:30Z");
        assertNull("one Manchester is not the other", TvGuideProvider.find(Arrays.asList(
            programme("Manchester City v Liverpool", "2026-11-29T16:30Z")), derby));
        Match real = match("esp.1", "Real Madrid", "Atlético Madrid", "86", true, "2026-11-29T20:00Z");
        assertNotNull("accents folded", TvGuideProvider.find(Arrays.asList(programme("LaLiga: Real Madrid - Atletico Madrid", "2026-11-29T20:00Z")), real));
        assertEquals(101, TvGuideProvider.number("101"));
        assertEquals(4, TvGuideProvider.number("4-1"));
        assertEquals(0, TvGuideProvider.number("BBC"));
    }
}
