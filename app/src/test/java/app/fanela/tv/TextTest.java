package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/** Names, capitals, translations and guide-title matching: the text a user sees on every card. */
public class TextTest {
    @After public void english() { I18n.use("en"); }

    @Test public void greekCapitalsKeepDialytika() {
        assertEquals("ΠΑΝΑΘΗΝΑΪΚΟΣ", Cards.caps("Παναθηναϊκός"));
        assertEquals("ΣΚΑΪ", Cards.caps("ΣΚΑΪ"));
        assertEquals("ΓΑΛΑΤΑΣΑΡΑΪ", Cards.caps("Γαλατασαράι"));
        assertEquals("ΗΡΑΚΛΗΣ", Cards.caps("Ηρακλής"));
    }

    @Test public void matchingKeysHaveNoAccents() {
        assertEquals("ΠΑΝΑΘΗΝΑΙΚΟΣ", Cards.key("Παναθηναϊκός"));
        assertEquals("FENERBAHCE", Cards.key("Fenerbahçe"));
    }

    @Test public void englishNames() {
        I18n.use("en");
        assertEquals("Olympiacos", I18n.team("Olympiacos BC"));
        assertEquals("Maroussi", I18n.team("Μαρούσι"));
        assertEquals("PAOK", I18n.team("ΠΑΟΚ"));
        assertEquals("AEK", I18n.team("AEK BC"));
    }

    @Test public void greekNames() {
        I18n.use("el");
        assertEquals("ΠΑΟΚ", I18n.team("PAOK"));
        assertEquals("Ολυμπιακός", I18n.team("Olympiacos Piraeus"));
        assertEquals("Αστέρας Τρ.", I18n.team("Asteras Tripoli"));
        assertEquals("Ερυθρός Αστέρας", I18n.team("Crvena Zvezda"));
        assertEquals("Δόξα Λευκάδας", I18n.team("Doxa Lefkadas BC"));
        assertEquals("Antwerp Giants", I18n.team("Antwerp Giants BC"));
    }

    @Test public void basketballClocks() {
        I18n.use("el");
        assertEquals("3ο δεκ. 04:12", I18n.detail("Q3 04:12"));
        assertEquals("Παράταση 00:41", I18n.detail("OT 00:41"));
        assertEquals("67'", I18n.detail("67'"));
        I18n.use("en");
        assertEquals("Q3 04:12", I18n.detail("Q3 04:12"));
        assertEquals("Half-time", I18n.detail("HT"));
    }

    @Test public void englishIsTheDefault() {
        I18n.use("anything");
        assertFalse(I18n.el());
        assertEquals("Final", I18n.t("final"));
    }

    @Test public void everyKeyHasBothLanguages() {
        for (String k : new String[]{"today", "final", "channel_tba", "updated", "empty_title", "rows_unsupported", "privacy_full",
                "independent", "sources_store", "channel_note", "open_app", "app_missing"}) {
            I18n.use("en");
            String en = I18n.t(k);
            I18n.use("el");
            String el = I18n.t(k);
            assertTrue(k, !en.equals(k) && !el.equals(k));
            assertFalse("Greek letters in English text: " + k, en.matches(".*[\\u0370-\\u03FF].*") && !k.equals("lang_value"));
        }
    }

    @Test public void badgeInitials() {
        I18n.use("en");
        assertEquals("OLY", Badges.initials("Olympiacos"));
        assertEquals("AEK", Badges.initials("AEK Athens"));
        assertEquals("PAOK", Badges.initials("PAOK"));
        assertEquals("RMA", Badges.initials("Real Madrid"));
        assertEquals("KAL", Badges.initials("Kalamata"));
        assertEquals("SR", Badges.initials("Stade Rennais"));
        I18n.use("el");
        assertEquals("ΟΣΦΠ", Badges.initials("Olympiacos"));
        assertEquals("ΠΑΟ", Badges.initials("Panathinaikos BC"));
    }

    @Test public void guideTitlesRejectNamesakes() {
        assertTrue(Guide.firstTeam("Super League: ΑΕΚ – Άρης (Ζ)", "ΑΕΚ"));
        assertTrue(Guide.firstTeam("Super League: ΑΕΚ – Άρης (Ζ)", "Άρης"));
        assertFalse(Guide.firstTeam("ΑΠΟΕΛ - Άρης Λεμεσού", "Άρης"));
        assertFalse(Guide.firstTeam("ΑΕΚ Λάρνακας - Ομόνοια", "ΑΕΚ"));
        assertFalse(Guide.firstTeam("Ολυμπιακός Β - Καλλιθέα", "Ολυμπιακός"));
    }

    @Test public void namesakesStayThemselves() {
        I18n.use("en");
        assertEquals("Olympiacos", I18n.enName("Ολυμπιακός ΣΦΠ"));
        assertEquals("Ολυμπιακός Βόλου", I18n.enName("Ολυμπιακός Βόλου"));
        assertEquals("Άρης Αβάτου", I18n.enName("Άρης Αβάτου"));
        assertFalse("RMA".equals(Badges.initials("Real Betis")));
        assertEquals("RMA", Badges.initials("Real Madrid"));
        assertEquals("AEKL", Badges.initials("AEK Larnaca"));
        assertEquals("ARL", Badges.initials("Aris Limassol"));
        assertEquals("ARIS", Badges.initials("Aris"));
    }

    @Test public void sponsorWordsDropped() {
        assertEquals("Μύκονος", Guide.plainName("Μύκονος Betsson BC"));
    }
}
