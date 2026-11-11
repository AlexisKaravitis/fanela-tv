package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The bundled fonts' licences: the full texts ship (Apache License 2.0 §4(a) asks for a copy of it with Roboto
 * Condensed), release builds keep them, and Sources and credits shows them word for word.
 */
public class FontLicenceTest {
    private static String res(String path) throws Exception {
        File f = new File("src/main/res/" + path);
        if (!f.exists()) f = new File("app/src/main/res/" + path);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : s.trim().split("\\s+")) if (!w.matches("[-=_*]{3,}")) out.add(w);
        return out;
    }

    @Test public void robotoCondensedShipsWithTheWholeApacheLicence() throws Exception {
        String t = res("raw/apache_roboto.txt");
        assertTrue(t.startsWith("Roboto Condensed\nCopyright 2011 Google Inc. All Rights Reserved."));
        assertTrue(t.contains("Version 2.0, January 2004"));
        assertTrue(t.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"));
        assertTrue(t.contains("4. Redistribution."));
        assertTrue(t.contains("END OF TERMS AND CONDITIONS"));
        assertTrue(t.contains("APPENDIX: How to apply the Apache License to your work."));
        assertFalse(t.contains("\r"));
    }

    @Test public void releaseBuildsKeepBothLicences() throws Exception {
        String keep = res("raw/keep.xml");
        assertTrue(keep.contains("@raw/ofl_google_sans"));
        assertTrue(keep.contains("@raw/apache_roboto"));
    }

    @Test public void thePanelShowsTheLicencesWordForWord() throws Exception {
        for (String name : new String[]{"raw/apache_roboto.txt", "raw/ofl_google_sans.txt"}) {
            String t = res(name), r = SettingsLogic.reflow(t);
            assertEquals(name, words(t), words(r));
            for (String para : r.split("\n\n")) assertFalse(name, para.isEmpty() || para.contains("\n") || para.contains("  "));
        }
    }

    @Test public void reflowJoinsAParagraphsLinesAndDropsRules() {
        assertEquals("Apache License Version 2.0\n\n1. Definitions.\n\n\"License\" shall mean the terms",
            SettingsLogic.reflow("   Apache License\n   Version  2.0\n\n  ------\n\n   1. Definitions.\r\n\r\n      \"License\" shall mean\n      the terms\n"));
        assertEquals(Arrays.asList("a", "b"), Arrays.asList(SettingsLogic.reflow("a\n \nb").split("\n\n")));
    }

    @Test public void aHeadingInCapitalsKeepsAParagraphOfItsOwn() throws Exception {
        assertEquals("PREAMBLE\n\nThe goals of the licence", SettingsLogic.reflow("PREAMBLE\nThe goals of\nthe licence"));
        assertEquals("DISCLAIMER\n\nTHE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS",
            SettingsLogic.reflow("DISCLAIMER\nTHE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND,\nEXPRESS"));
        List<String> ofl = Arrays.asList(SettingsLogic.reflow(res("raw/ofl_google_sans.txt")).split("\n\n"));
        for (String h : new String[]{"PREAMBLE", "DEFINITIONS", "PERMISSION & CONDITIONS", "TERMINATION", "DISCLAIMER"})
            assertTrue(h, ofl.contains(h));
    }
}
