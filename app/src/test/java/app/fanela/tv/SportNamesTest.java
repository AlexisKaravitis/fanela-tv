package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.After;
import org.junit.Test;

/** Grand prix, session, event and people names (SPEC-F1-UFC §4). */
public class SportNamesTest {
    @After public void english() { I18n.use("en"); }

    static SportEvent gp(String id, String circuitId, String name, String city) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.F1; e.id = id; e.circuitId = circuitId; e.name = name; e.city = city;
        return e;
    }

    @Test public void lasVegasInBothLanguagesNeverTheSponsor() {
        SportEvent e = gp("600057449", "4250", "Heineken Las Vegas Grand Prix", "Nevada");
        assertEquals("Las Vegas GP", SportNames.gpCard(e));
        assertEquals("Las Vegas Grand Prix", SportNames.gpLong(e));
        assertEquals(Arrays.asList("Las Vegas Grand Prix", "Las Vegas GP"), SportNames.titleNames(e));
        assertNull(SportNames.gpCardTight(e));
        I18n.use("el");
        assertEquals("GP Λας Βέγκας", SportNames.gpCard(e));
        assertEquals("GP ΛΑΣ ΒΕΓΚΑΣ", Cards.caps(SportNames.gpCard(e)));
        assertEquals("Γκραν Πρι Λας Βέγκας", SportNames.gpLong(e));
        assertEquals("Γκραν Πρι Λας Βέγκας", SportNames.title(e));
    }

    @Test public void theTableMatchesByCircuitThenEventThenThePlacePhrase() {
        assertEquals("São Paulo GP", SportNames.gpCard(gp("x", "617", "anything", "")));
        assertEquals("Chinese GP", SportNames.gpCard(gp("600057428", "", "x", "")));
        // next season's ids: the place phrase of ESPN's name, whatever the sponsor
        assertEquals("Italian GP", SportNames.gpCard(gp("700000001", "1", "Pirelli Gran Premio Italian Grand Prix", "Monza")));
        assertEquals("Abu Dhabi GP", SportNames.gpCard(gp("700000002", "2", "Etihad Airways Abu Dhabi Grand Prix", "")));
        assertEquals("São Paulo GP", SportNames.gpCard(gp("700000003", "3", "MSC Cruises São Paulo Grand Prix", "")));
        assertEquals("Barcelona GP", SportNames.gpCard(gp("700000004", "4", "MSC Cruises Barcelona-Catalunya Grand Prix", "")));
        assertEquals("Barcelona-Catalunya Grand Prix", SportNames.gpLong(gp("700000004", "4", "x Barcelona-Catalunya Grand Prix", "")));
        // the Bahrain GP held in Malaysia is not the Bahrain GP, and never says so in full on a card
        SportEvent my = gp("700000005", "5", "Gulf Air Bahrain Grand Prix in Malaysia", "Sepang");
        assertEquals("Bahrain GP", SportNames.gpCard(my));
        assertEquals("Bahrain Grand Prix in Malaysia", SportNames.gpLong(my));
        assertEquals(Arrays.asList("Sepang"), SportNames.placeNames(my));
        I18n.use("el");
        assertEquals("Γκραν Πρι Μπαχρέιν στη Μαλαισία", SportNames.gpLong(my));
        assertEquals("GP Μπαχρέιν", SportNames.gpCard(my));
    }

    @Test public void anUnknownGrandPrixIsItsCityGp() {
        SportEvent e = gp("1", "9999", "Heineken Atlantis Grand Prix", "Atlantis");
        assertEquals("Atlantis GP", SportNames.gpCard(e));
        assertEquals("Atlantis Grand Prix", SportNames.gpLong(e));
        assertEquals("", SportNames.flagCode(e));
        assertEquals(0, SportNames.flagColour(e));
        e.city = "";
        assertEquals("Atlantis GP", SportNames.gpCard(e));
        I18n.use("el");
        assertEquals("GP Atlantis", SportNames.gpCard(e));
        assertEquals("Heineken Atlantis Grand Prix".substring(9), SportNames.stripSponsor("Heineken Atlantis Grand Prix"));
    }

    @Test public void sponsorsComeOffTheFront() {
        assertEquals("Las Vegas Grand Prix", SportNames.stripSponsor("Heineken Las Vegas Grand Prix"));
        assertEquals("Belgian Grand Prix", SportNames.stripSponsor("Moët & Chandon Belgian Grand Prix"));
        assertEquals("Saudi Arabian Grand Prix", SportNames.stripSponsor("STC Saudi Arabian Grand Prix"));
        assertEquals("Monaco Grand Prix", SportNames.stripSponsor("Monaco Grand Prix"));
        assertEquals("", SportNames.stripSponsor(null));
    }

    @Test public void tightFormsAndGreekGenitives() {
        SportEvent us = gp("600057446", "", "MSC Cruises United States Grand Prix", "Austin");
        assertEquals("United States GP", SportNames.gpCard(us));
        assertEquals("US GP", SportNames.gpCardTight(us));
        SportEvent mx = gp("600057447", "", "Mexico City Grand Prix", "Mexico City");
        assertEquals("Mexico GP", SportNames.gpCardTight(mx));
        assertEquals(0xFF006847, SportNames.flagColour(mx));
        assertEquals("mx", SportNames.flagCode(mx));
        I18n.use("el");
        assertEquals("GP ΗΠΑ", SportNames.gpCard(us));
        assertNull(SportNames.gpCardTight(us));
        assertEquals("Γκραν Πρι Ιταλίας", SportNames.gpLong(gp("600057442", "", "Pirelli Italian Grand Prix", "")));
        assertEquals("GP Βρετανίας", SportNames.gpCard(gp("600057437", "", "Pirelli British Grand Prix", "")));
        assertEquals(Arrays.asList("Πόλη του Μεξικού", "Μεξικό"), SportNames.placeNames(mx));
        SportEvent az = gp("600057444", "", "Qatar Airways Azerbaijan Grand Prix", "Baku");
        assertEquals("GP Αζερμπαϊτζάν", SportNames.gpCard(az));
        assertEquals("GP Μπακού", SportNames.gpCardTight(az));
        assertEquals("GP ΑΜΠΟΥ ΝΤΑΜΠΙ", Cards.caps(SportNames.gpCard(gp("600057451", "", "x", ""))));
    }

    @Test public void everyRowOfTheTableHasBothLanguagesAndAFlag() {
        for (SportNames.Gp g : SportNames.GPS) {
            assertFalse(g.en.isEmpty());
            assertTrue(g.el, SportNames.greek(g.el) || g.el.equals("ΗΠΑ"));
            assertTrue(g.placeEl, SportNames.greek(g.placeEl));
            assertEquals(2, g.flag.length());
            assertTrue(g.flagColour != 0);
            assertFalse(SportNames.greek(g.en + g.placeEn + (g.enTight == null ? "" : g.enTight)));
        }
    }

    @Test public void sessions() {
        assertEquals(Arrays.asList("Qualifying", "Quali"), SportNames.sessionNames("QUAL", "Qual"));
        assertEquals(Arrays.asList("Sprint Qualifying", "Sprint Quali"), SportNames.sessionNames("SQ", "SS"));
        assertEquals(Arrays.asList("Sprint"), SportNames.sessionNames("SPRINT", "SR"));
        assertEquals(Arrays.asList("Race"), SportNames.sessionNames("RACE", "Race"));
        assertEquals(Arrays.asList("Practice 2", "FP2"), SportNames.sessionNames("FP2", "FP2"));
        assertEquals(Arrays.asList("Shakedown"), SportNames.sessionNames("OTHER", "Shakedown"));
        assertEquals(Arrays.asList("Main card"), SportNames.sessionNames("MAIN", ""));
        assertEquals("Practice 1", SportNames.sessionLong("FP1", "FP1"));
        assertTrue(SportNames.scoring("SQ"));
        assertFalse(SportNames.scoring("FP3"));
        I18n.use("el");
        assertEquals(Arrays.asList("Αγώνας"), SportNames.sessionNames("RACE", "Race"));
        assertEquals("ΚΑΤΑΤΑΚΤΗΡΙΕΣ ΣΠΡΙΝΤ", Cards.caps(SportNames.sessionNames("SQ", "SS").get(0)));
        // the UFC segments in Greek (design/f1ufc/GREEK-COPY.md); the early prelims' tight form is the eyebrow's fallback
        assertEquals(Arrays.asList("Κύρια κάρτα"), SportNames.sessionNames("MAIN", ""));
        assertEquals(Arrays.asList("Προκριματικά"), SportNames.sessionNames("PRELIMS", ""));
        assertEquals(Arrays.asList("Πρώτα προκριματικά", "Προκριματικά"), SportNames.sessionNames("EARLY", ""));
        assertEquals("ΚΥΡΙΑ ΚΑΡΤΑ", Cards.caps(SportNames.sessionNames("MAIN", "").get(0)));
        assertEquals("Ελεύθερες δοκιμές 1", SportNames.sessionLong("FP1", "FP1"));
        assertEquals("Κατατακτήριες", SportNames.sessionLong("QUAL", "Qual"));
    }

    static SportEvent ufc(String name, boolean numbered, int number) {
        SportEvent e = new SportEvent();
        e.sport = SportEvent.UFC; e.name = name; e.numbered = numbered; e.number = number;
        return e;
    }

    @Test public void ufcEventNames() {
        assertEquals("UFC 334", SportNames.ufcName(ufc("UFC 334: Gane vs. Hokit", true, 334)));
        assertEquals("UFC 335", SportNames.ufcName(ufc("UFC 335: Rakhmonov vs. Della Maddalena", false, 0)));
        assertEquals("UFC Fight Night", SportNames.ufcName(ufc("UFC Fight Night: Cortes Acosta vs. Walker", false, 0)));
        assertEquals("Noche UFC", SportNames.ufcName(ufc("Noche UFC: Lopes vs. Silva", false, 0)));
        assertEquals("UFC Freedom 250", SportNames.ufcName(ufc("UFC Freedom 250: Jones vs. Aspinall", false, 0)));
        assertEquals(Arrays.asList("UFC 334"), SportNames.titleNames(ufc("UFC 334: Gane vs. Hokit", true, 334)));
        assertTrue(Arrays.equals(new String[]{"Gane", "Hokit"}, SportNames.billing(ufc("UFC 334: Gane vs. Hokit", true, 334))));
        assertTrue(Arrays.equals(new String[]{"Cortes Acosta", "Walker"}, SportNames.billing(ufc("UFC Fight Night: Cortes Acosta vs. Walker", false, 0))));
        assertNull(SportNames.billing(ufc("UFC 334", true, 334)));
    }

    @Test public void peopleAreSurnamesInLatinScript() {
        SportEvent.Fighter f = SportTestKit.fighter("1", "Waldo Cortes Acosta", "15-2-0");
        f.shortName = "W. Cortes Acosta";
        assertEquals("Cortes Acosta", SportNames.surname(f));
        assertEquals(Arrays.asList("Cortes Acosta", "Acosta"), SportNames.personNames("Cortes Acosta"));
        assertEquals(Arrays.asList("Gane"), SportNames.personNames("Gane"));
        f.shortName = "";
        assertEquals("Cortes Acosta", SportNames.surname(f));
        f.shortName = "Medić"; f.name = "Uroš Medić";
        assertEquals("Medić", SportNames.surname(f));
        assertEquals("MEDIĆ", SportNames.caps(SportNames.surname(f)));  // Cards.caps would drop the acute
        assertEquals("GP ΛΑΣ ΒΕΓΚΑΣ", SportNames.caps("GP Λας Βέγκας"));
        assertTrue(SportNames.names("Gane", SportTestKit.fighter("2", "Ciryl Gane", "")));
        assertFalse(SportNames.names("Hokit", SportTestKit.fighter("2", "Ciryl Gane", "")));
        SportEvent.Entrant x = EventFixtures.driver("5579", "NOR", "Lando Norris", "McLaren", "106892", 0xFF8700, 1);
        assertEquals("Norris", SportNames.surname(x));
        I18n.use("el");
        assertEquals("Cortes Acosta", SportNames.personNames("Cortes Acosta").get(0));  // never transliterated
    }

    @Test public void weightClassesAndBillingLines() {
        SportEvent.Bout b = SportTestKit.bout("1", 1, null, null);
        assertEquals("Heavyweight title", SportNames.billingLine(b));
        b.title = false; b.weightClass = "Women's Bantamweight";
        assertEquals("Women's Bantamweight", SportNames.billingLine(b));
        I18n.use("el");
        assertEquals("Βάρος πετεινού γυναικών", SportNames.billingLine(b));
        b.title = true;
        assertEquals("Τίτλος βάρους πετεινού γυναικών", SportNames.billingLine(b));
        b.title = true; b.weightClass = "Heavyweight";
        assertEquals("Τίτλος βαρέων βαρών", SportNames.billingLine(b));
        assertEquals("Ημιβαρέων βαρών", SportNames.weightClass("Light Heavyweight"));
        assertEquals("Συμφωνημένο βάρος", SportNames.weightClass("Catchweight"));
        // one table: the cards' texts, the app and Settings' tiles say the same class
        for (String w : new String[]{"Strawweight", "Flyweight", "Bantamweight", "Featherweight", "Lightweight", "Welterweight",
            "Middleweight", "Light Heavyweight", "Heavyweight", "Women's Flyweight"}) {
            String[] g = SportNames.weightEl(w);
            assertTrue(w, g != null && SportNames.greek(g[0]) && SportNames.greek(g[1]));
            assertEquals(w, SportNames.weightClass(w), g[0]);
            assertEquals(w, SportsSettings.weightWord(w, true), g[2]);
        }
        assertEquals("Heavyweight", SportNames.weightClass("Heavyweight", false));
        assertEquals("Openweight", SportNames.weightClass("Openweight"));  // unknown: as ESPN writes it
    }

    @Test public void teamTrimsByIdThenName() {
        assertEquals(0xFF2B2D33, SportNames.trim("106892", ""));
        assertEquals(0xFFE8002D, SportNames.trim("", "Red Bull"));
        assertEquals(0xFF0A3C8C, SportNames.trim("x", "Williams"));
        assertEquals(0, SportNames.trim("x", "Minardi"));
        assertEquals(0, SportNames.trim(null, null));
    }

    @Test public void cardWords() {
        assertEquals("CANCELLED", SportNames.w("cancelled"));
        assertEquals("OF %d", SportNames.w("of"));
        I18n.use("el");
        assertEquals("ΑΚΥΡΩΘΗΚΕ", SportNames.w("cancelled"));
        assertEquals("ΓΥΡΟΣ", SportNames.w("lap"));
        assertEquals("KO/TKO", SportNames.w("ko"));
        assertEquals("Τίτλος", SportNames.w2("title"));
        assertEquals("unknown", SportNames.w("unknown"));
    }
    /** A width of one unit per character: enough to test the order of the fallbacks. */
    static final SettingsLogic.Measure CHARS = new SettingsLogic.Measure() { public float width(String s) { return s.length(); } };

    @Test public void aBoutsNamesShareTheLineByWhatEachNeedsThenGoTight() {
        // QAS-6: never "Cortes A… VS Walker"
        assertEquals(Arrays.asList("Cortes Acosta", "Walker"), Arrays.asList(SportNames.boutFit("Cortes Acosta", "Walker", 19, CHARS)));
        assertEquals(Arrays.asList("Acosta", "Walker"), Arrays.asList(SportNames.boutFit("Cortes Acosta", "Walker", 18, CHARS)));
        assertEquals(Arrays.asList("Della Maddalena", "Acosta"),
            Arrays.asList(SportNames.boutFit("Della Maddalena", "Cortes Acosta", 21, CHARS)));  // the longer one tight does not fit: the other
        assertEquals(Arrays.asList("Maddalena", "Acosta"), Arrays.asList(SportNames.boutFit("Della Maddalena", "Cortes Acosta", 15, CHARS)));
        assertNull(SportNames.boutFit("Della Maddalena", "Cortes Acosta", 14, CHARS));
    }

    @Test public void theWeekendScreensEyebrowAndPlaceLineInBothLanguages() {
        // the eyebrow names the circuit in both languages (Latin in Greek); the place line has its country in both languages
        SportEvent e = gp("600057449", "4250", "Heineken Las Vegas Grand Prix", "Nevada");
        e.circuitName = "Las Vegas Street Circuit"; e.country = "USA";
        assertEquals("Formula 1 · Las Vegas Street Circuit", SportText.weekendEyebrow(e));
        assertEquals("Las Vegas, USA", SportText.place(e));
        SportEvent monaco = gp("600057434", "", "Formula 1 Grand Prix de Monaco", "Monte Carlo");
        monaco.country = "Monaco";
        assertEquals("Monaco", SportText.place(monaco));  // never "Monaco, Monaco"
        I18n.use("el");
        assertEquals("Formula 1 · Las Vegas Street Circuit", SportText.weekendEyebrow(e));  // the circuit, a proper noun
        assertEquals("FORMULA 1 · LAS VEGAS STREET CIRCUIT", Cards.caps(SportText.weekendEyebrow(e)));
        assertEquals("Formula 1", SportText.weekendEyebrow(monaco));  // no circuit: never the title again
        assertEquals("Λας Βέγκας, ΗΠΑ", SportText.place(e));
        assertEquals("Μονακό", SportText.place(monaco));
        e.country = "Atlantis";
        assertEquals("Λας Βέγκας", SportText.place(e));  // no Greek form: the place alone, never English in a Greek line
    }
}
