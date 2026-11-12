package app.fanela.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean names and card words for Formula 1 and UFC (F1-UFC-ARCHITECTURE §6.2, SPEC-F1-UFC §4). Stream D owns this file.
 *
 * Grand prix names come from a curated table keyed by ESPN circuit id, then ESPN event id, then the place phrase of
 * ESPN's name ("… Las Vegas Grand Prix"): never by parsing the sponsor out. English card form "{Place} GP", Greek
 * "GP {Τόπος}"; long form "{Place} Grand Prix" / "Γκραν Πρι {Τόπος}". A grand prix the table doesn't know: the circuit's
 * city + " GP" (ESPN's name minus a leading sponsor is not reliable).
 * People (drivers, fighters) stay in Latin script in both languages; places, sessions, weight classes, methods and day
 * words follow the language. Every name list here is mixed case, longest first; the card styles set them in capitals
 * (Cards.caps), Clean as written. Card words (w) are display capitals, as CardModel.w. Pure: unit-tested.
 */
final class SportNames {
    private SportNames() { }

    // ---------- grand prix table (SPEC-F1-UFC §4.2, §5.3) ----------
    /** One grand prix: keys, English and Greek forms, the place (Clash's top-left), the host flag. */
    static final class Gp {
        final String[] ids;           // ESPN circuit ids and event ids (either matches)
        final String phrase;          // the place phrase in ESPN's name, matching key (Cards.key)
        final String en, enTight, enLong;   // "Las Vegas" (card: "Las Vegas GP"); tight form or null; long or null
        final String el, elTight, elLong;   // "Λας Βέγκας" (card: "GP Λας Βέγκας"); tight form or null; long or null
        final String placeEn, placeEl, placeElTight;
        final String flag;            // flag-icons code ("us")
        final int flagColour;         // the flag's main colour (Clash F1 with no focus)

        Gp(String ids, String phrase, String en, String enTight, String enLong, String el, String elTight, String elLong,
           String placeEn, String placeEl, String placeElTight, String flag, int flagColour) {
            this.ids = ids.isEmpty() ? new String[0] : ids.split(" ");
            this.phrase = Cards.key(phrase);
            this.en = en; this.enTight = enTight; this.enLong = enLong;
            this.el = el; this.elTight = elTight; this.elLong = elLong;
            this.placeEn = placeEn; this.placeEl = placeEl; this.placeElTight = placeElTight;
            this.flag = flag; this.flagColour = flagColour;
        }
    }

    static final int USA = 0xFF3C3B6E, ITALY = 0xFF009246, SPAIN = 0xFFAA151B, BAHRAIN = 0xFFCE1126;

    /**
     * The 2026 calendar plus the two that may return. Ids: ESPN circuit ids where known (603 Sakhir, 619 Shanghai,
     * 617 Interlagos, 4250 Las Vegas), and the 2026 ESPN event ids (a new season's ids fall through to the phrase).
     * "Bahrain Grand Prix in Malaysia" comes before "Bahrain Grand Prix": the first phrase found wins.
     */
    static final Gp[] GPS = {
        new Gp("600060990", "Bahrain Grand Prix in Malaysia", "Bahrain", null, "Bahrain Grand Prix in Malaysia",
            "Μπαχρέιν", null, "Γκραν Πρι Μπαχρέιν στη Μαλαισία", "Sepang", "Σεπάνγκ", null, "bh", BAHRAIN),
        new Gp("600057427", "Australian Grand Prix", "Australian", null, null, "Αυστραλίας", null, null,
            "Melbourne", "Μελβούρνη", null, "au", 0xFF012169),
        new Gp("619 600057428", "Chinese Grand Prix", "Chinese", null, null, "Κίνας", null, null,
            "Shanghai", "Σανγκάη", null, "cn", 0xFFDE2910),
        new Gp("600057429", "Japanese Grand Prix", "Japanese", null, null, "Ιαπωνίας", null, null,
            "Suzuka", "Σουζούκα", null, "jp", 0xFFBC002D),
        new Gp("603 600057430", "Bahrain Grand Prix", "Bahrain", null, null, "Μπαχρέιν", null, null,
            "Sakhir", "Σαχίρ", null, "bh", BAHRAIN),
        new Gp("600057431", "Saudi Arabian Grand Prix", "Saudi Arabian", "Jeddah", null, "Σαουδικής Αραβίας", "Τζέντα", null,
            "Jeddah", "Τζέντα", null, "sa", 0xFF006C35),
        new Gp("600057432", "Miami Grand Prix", "Miami", null, null, "Μαϊάμι", null, null,
            "Miami", "Μαϊάμι", null, "us", USA),
        new Gp("600057433", "Canadian Grand Prix", "Canadian", null, null, "Καναδά", null, null,
            "Montréal", "Μόντρεαλ", null, "ca", 0xFFD80621),
        new Gp("600057434", "Monaco Grand Prix", "Monaco", null, null, "Μονακό", null, null,
            "Monaco", "Μονακό", null, "mc", 0xFFCE1126),
        new Gp("600057435", "Barcelona", "Barcelona", null, "Barcelona-Catalunya Grand Prix", "Βαρκελώνης", null, null,
            "Barcelona", "Βαρκελώνη", null, "es", SPAIN),
        new Gp("600057436", "Austrian Grand Prix", "Austrian", null, null, "Αυστρίας", null, null,
            "Spielberg", "Σπίλμπεργκ", null, "at", 0xFFC8102E),
        new Gp("600057437", "British Grand Prix", "British", null, null, "Βρετανίας", null, null,
            "Silverstone", "Σίλβερστοουν", null, "gb", 0xFF012169),
        new Gp("600057439", "Belgian Grand Prix", "Belgian", null, null, "Βελγίου", null, null,
            "Spa", "Σπα", null, "be", 0xFFFDDA24),
        new Gp("600057440", "Hungarian Grand Prix", "Hungarian", null, null, "Ουγγαρίας", null, null,
            "Budapest", "Βουδαπέστη", null, "hu", 0xFF477050),
        new Gp("600057441", "Dutch Grand Prix", "Dutch", null, null, "Ολλανδίας", null, null,
            "Zandvoort", "Ζάντβορτ", null, "nl", 0xFF21468B),
        new Gp("600057442", "Italian Grand Prix", "Italian", null, null, "Ιταλίας", null, null,
            "Monza", "Μόντσα", null, "it", ITALY),
        new Gp("600057443", "Spanish Grand Prix", "Spanish", null, null, "Ισπανίας", null, null,
            "Madrid", "Μαδρίτη", null, "es", SPAIN),
        new Gp("600057444", "Azerbaijan Grand Prix", "Azerbaijan", "Baku", null, "Αζερμπαϊτζάν", "Μπακού", null,
            "Baku", "Μπακού", null, "az", 0xFF0092BC),
        new Gp("600057445", "Singapore Grand Prix", "Singapore", null, null, "Σιγκαπούρης", null, null,
            "Singapore", "Σιγκαπούρη", null, "sg", 0xFFEE2536),
        new Gp("600057446", "United States Grand Prix", "United States", "US", null, "ΗΠΑ", null, null,
            "Austin", "Όστιν", null, "us", USA),
        new Gp("600057447", "Mexico City Grand Prix", "Mexico City", "Mexico", null, "Μεξικού", null, null,
            "Mexico City", "Πόλη του Μεξικού", "Μεξικό", "mx", 0xFF006847),
        new Gp("617 600057448", "Sao Paulo Grand Prix", "São Paulo", null, null, "Σάο Πάολο", null, null,
            "São Paulo", "Σάο Πάολο", null, "br", 0xFF009C3B),
        new Gp("", "Brazilian Grand Prix", "São Paulo", null, null, "Σάο Πάολο", null, null,
            "São Paulo", "Σάο Πάολο", null, "br", 0xFF009C3B),
        new Gp("4250 600057449", "Las Vegas Grand Prix", "Las Vegas", null, null, "Λας Βέγκας", null, null,
            "Las Vegas", "Λας Βέγκας", null, "us", USA),
        new Gp("600057450", "Qatar Grand Prix", "Qatar", null, null, "Κατάρ", null, null,
            "Lusail", "Λουσαΐλ", null, "qa", 0xFF8A1538),
        new Gp("600057451", "Abu Dhabi Grand Prix", "Abu Dhabi", null, null, "Άμπου Ντάμπι", null, null,
            "Abu Dhabi", "Άμπου Ντάμπι", null, "ae", 0xFF00732F),
        new Gp("", "Emilia Romagna", "Emilia-Romagna", "Imola", null, "Εμίλια-Ρομάνια", "Ίμολα", null,
            "Imola", "Ίμολα", null, "it", ITALY),
    };

    /** The table's grand prix for an event: by circuit id, then event id, then the place phrase; null when none. */
    static Gp gp(SportEvent e) {
        if (e == null) return null;
        for (Gp g : GPS) for (String id : g.ids) if (id.equals(e.circuitId)) return g;
        for (Gp g : GPS) for (String id : g.ids) if (id.equals(e.id)) return g;
        String n = Cards.key(e.name).replace('-', ' ');
        for (Gp g : GPS) if (!g.phrase.isEmpty() && n.contains(g.phrase.replace('-', ' '))) return g;
        return null;
    }

    /** Sponsor prefixes ESPN puts before a grand prix's name; never drawn. */
    static final String[] SPONSORS = {"Heineken Silver", "Heineken", "MSC Cruises", "Qatar Airways", "Gulf Air", "Aramco", "STC",
        "Crypto.com", "Lenovo", "Pirelli", "Moët & Chandon", "Moet & Chandon", "AWS", "Tag Heuer", "TAG Heuer",
        "Singapore Airlines", "Etihad Airways", "Louis Vuitton", "Rolex", "Formula 1", "FORMULA 1", "Polymarket"};

    /** ESPN's name without a leading sponsor ("Heineken Las Vegas Grand Prix" -> "Las Vegas Grand Prix"). */
    static String stripSponsor(String name) {
        if (name == null) return "";
        String n = name.trim();
        for (boolean again = true; again; ) {
            again = false;
            for (String s : SPONSORS)
                if (n.length() > s.length() + 1 && n.startsWith(s + " ")) { n = n.substring(s.length() + 1).trim(); again = true; }
        }
        return n;
    }

    /** The place word of an unknown grand prix: the circuit's city, else ESPN's name less its sponsor and "Grand Prix". */
    private static String fallbackPlace(SportEvent e) {
        if (e.city != null && !e.city.trim().isEmpty()) return e.city.trim();
        String n = stripSponsor(e.name).replaceAll("(?i)\\s*grand prix.*$", "").trim();
        return n.isEmpty() ? stripSponsor(e.shortName).replaceAll("(?i)\\s*GP$", "").trim() : n;
    }

    /** The card form: "Las Vegas GP" / "GP Λας Βέγκας". */
    static String gpCard(SportEvent e) {
        Gp g = gp(e);
        String p = g == null ? fallbackPlace(e) : I18n.el() ? g.el : g.en;
        return I18n.el() ? "GP " + p : p + " GP";
    }

    /** The tight card form where the table has one ("Baku GP" / "GP Μπακού"); null when none. */
    static String gpCardTight(SportEvent e) {
        Gp g = gp(e);
        String t = g == null ? null : I18n.el() ? g.elTight : g.enTight;
        return t == null ? null : I18n.el() ? "GP " + t : t + " GP";
    }

    /** The long form for the launcher and the app: "Las Vegas Grand Prix" / "Γκραν Πρι Λας Βέγκας". */
    static String gpLong(SportEvent e) {
        Gp g = gp(e);
        if (g == null) {
            if (I18n.el()) return "Γκραν Πρι " + fallbackPlace(e);
            String s = stripSponsor(e.name);
            return s.isEmpty() ? fallbackPlace(e) + " Grand Prix" : s;
        }
        if (I18n.el()) return g.elLong != null ? g.elLong : "Γκραν Πρι " + g.el;
        return g.enLong != null ? g.enLong : g.en + " Grand Prix";
    }

    /** The place for Clash's top-left, longest first: ["Las Vegas"], ["Πόλη του Μεξικού", "Μεξικό"]. */
    static List<String> placeNames(SportEvent e) {
        List<String> out = new ArrayList<>();
        Gp g = gp(e);
        if (g == null) { out.add(fallbackPlace(e)); return out; }
        out.add(I18n.el() ? g.placeEl : g.placeEn);
        if (I18n.el() && g.placeElTight != null) out.add(g.placeElTight);
        return out;
    }

    /** ESPN's host countries in Greek, as the Greek sports press writes them (the place line, QAS-11). */
    private static final String[][] COUNTRIES_EL = {
        {"USA", "ΗΠΑ"}, {"United States", "ΗΠΑ"}, {"Australia", "Αυστραλία"}, {"Austria", "Αυστρία"},
        {"Azerbaijan", "Αζερμπαϊτζάν"}, {"Bahrain", "Μπαχρέιν"}, {"Belgium", "Βέλγιο"}, {"Brazil", "Βραζιλία"},
        {"Canada", "Καναδάς"}, {"China", "Κίνα"}, {"Hungary", "Ουγγαρία"}, {"Italy", "Ιταλία"}, {"Japan", "Ιαπωνία"},
        {"Malaysia", "Μαλαισία"}, {"Mexico", "Μεξικό"}, {"Monaco", "Μονακό"}, {"Netherlands", "Ολλανδία"},
        {"Qatar", "Κατάρ"}, {"Saudi Arabia", "Σαουδική Αραβία"}, {"Singapore", "Σιγκαπούρη"}, {"Spain", "Ισπανία"},
        {"UAE", "ΗΑΕ"}, {"United Arab Emirates", "ΗΑΕ"}, {"UK", "Ηνωμένο Βασίλειο"}, {"United Kingdom", "Ηνωμένο Βασίλειο"},
        {"Great Britain", "Ηνωμένο Βασίλειο"}, {"England", "Αγγλία"}, {"Portugal", "Πορτογαλία"}, {"France", "Γαλλία"},
        {"Germany", "Γερμανία"}, {"Turkey", "Τουρκία"}, {"South Africa", "Νότια Αφρική"}, {"Argentina", "Αργεντινή"},
    };

    /** ESPN's country in Greek ("USA" -> "ΗΠΑ"); "" when the table has no Greek form (the place line leaves it out). */
    static String countryEl(String espn) {
        if (espn == null) return "";
        for (String[] c : COUNTRIES_EL) if (c[0].equalsIgnoreCase(espn.trim())) return c[1];
        return "";
    }

    /** The host flag's flag-icons code ("us"); "" when unknown. */
    static String flagCode(SportEvent e) { Gp g = gp(e); return g == null ? "" : g.flag; }

    /** The host flag's main colour; 0 when unknown. */
    static int flagColour(SportEvent e) { Gp g = gp(e); return g == null ? 0 : g.flagColour; }

    // ---------- the event's names (titleNames) ----------
    /** The event's names, longest first: ["Las Vegas Grand Prix", "Las Vegas GP"] / ["UFC 334"] / ["UFC Fight Night"]. */
    static List<String> titleNames(SportEvent e) {
        List<String> out = new ArrayList<>();
        if (e == null) { out.add(""); return out; }
        if (SportEvent.F1.equals(e.sport)) {
            out.add(gpLong(e));
            String card = gpCard(e);
            if (!card.equals(out.get(0))) out.add(card);
        } else out.add(ufcName(e));
        return out;
    }

    /** The event's full clean name in the interface's language. */
    static String title(SportEvent e) { return titleNames(e).get(0); }

    // ---------- F1 sessions and UFC segments (SPEC-F1-UFC §4.2; Greek: design/f1ufc/GREEK-COPY.md) ----------
    /**
     * kind, en, en tight, el, el tight (mixed case; the cards set them in capitals). Greek as the Greek sports press
     * writes it: "ελεύθερες δοκιμές", "κατατακτήριες", "κύρια κάρτα", "προκριματικά". The tight forms are the who
     * tier's and the eyebrow's fallbacks where the long one doesn't fit (Clash's 56 → 40 place and session; Ticket's
     * eyebrow): a graphics abbreviation, as Greek TV graphics shorten, only there.
     */
    private static final String[][] SESSIONS = {
        {"FP1", "Practice 1", "FP1", "Δοκιμές 1", "FP1"},
        {"FP2", "Practice 2", "FP2", "Δοκιμές 2", "FP2"},
        {"FP3", "Practice 3", "FP3", "Δοκιμές 3", "FP3"},
        {"QUAL", "Qualifying", "Quali", "Κατατακτήριες", "Κατατακτ."},
        {"SQ", "Sprint Qualifying", "Sprint Quali", "Κατατακτήριες σπριντ", "Κατατ. σπριντ"},
        {"SPRINT", "Sprint", null, "Σπριντ", null},
        {"RACE", "Race", null, "Αγώνας", null},
        {"MAIN", "Main card", null, "Κύρια κάρτα", null},
        {"PRELIMS", "Prelims", null, "Προκριματικά", null},
        {"EARLY", "Early prelims", "Prelims", "Πρώτα προκριματικά", "Προκριματικά"},
    };

    /** A session's names on a card, longest first: ["Qualifying", "Quali"]. OTHER keeps ESPN's label. */
    static List<String> sessionNames(String kind, String abbr) {
        List<String> out = new ArrayList<>();
        for (String[] r : SESSIONS) {
            if (!r[0].equals(kind)) continue;
            boolean el = I18n.el();
            out.add(el ? r[3] : r[1]);
            String t = el ? r[4] : r[2];
            if (t != null && !t.equals(out.get(0))) out.add(t);
            return out;
        }
        String raw = abbr == null || abbr.trim().isEmpty() ? (kind == null ? "" : kind) : abbr.trim();
        out.add(raw);
        return out;
    }

    static List<String> sessionNames(SportEvent.Session s) { return s == null ? sessionNames("", "") : sessionNames(s.kind, s.typeAbbr); }

    /** The app's long session name: "Practice 1" / "Ελεύθερες δοκιμές 1", "Qualifying" / "Κατατακτήριες". */
    static String sessionLong(String kind, String abbr) {
        if (I18n.el() && kind != null && kind.matches("FP[123]")) return "Ελεύθερες δοκιμές " + kind.charAt(2);
        return sessionNames(kind, abbr).get(0);
    }

    static boolean scoring(String kind) {
        return "RACE".equals(kind) || "SPRINT".equals(kind) || "QUAL".equals(kind) || "SQ".equals(kind);
    }

    // ---------- UFC (SPEC-F1-UFC §4.1, §4.4) ----------
    private static final Pattern NUMBERED = Pattern.compile("^UFC\\s+(\\d+)\\b");

    /** "UFC 334" / "UFC Fight Night" / "Noche UFC"; any other name: its part before ":". */
    static String ufcName(SportEvent e) {
        String n = e.name == null ? "" : e.name.trim();
        Matcher m = NUMBERED.matcher(n);
        if (e.numbered && e.number > 0) return "UFC " + e.number;
        if (m.find()) return "UFC " + m.group(1);
        String k = n.toLowerCase(Locale.ROOT);
        if (k.startsWith("noche")) return "Noche UFC";
        if (k.startsWith("ufc fight night") || k.isEmpty()) return "UFC Fight Night";
        int colon = n.indexOf(':');
        return colon > 0 ? n.substring(0, colon).trim() : n;
    }

    /** The main event as billed in the event's name ("UFC 334: Gane vs. Hokit" -> [Gane, Hokit]); null when not billed. */
    static String[] billing(SportEvent e) {
        String n = e == null || e.name == null ? "" : e.name;
        int colon = n.indexOf(':');
        if (colon < 0) return null;
        String[] p = n.substring(colon + 1).trim().split("(?i)\\s+vs\\.?\\s+");
        if (p.length != 2 || p[0].trim().isEmpty() || p[1].trim().isEmpty()) return null;
        return new String[]{p[0].trim(), p[1].trim()};
    }

    /** Whether a billed surname names this fighter (accents and case ignored). */
    static boolean names(String billed, SportEvent.Fighter f) {
        if (billed == null || f == null) return false;
        String b = Cards.key(billed).trim(), full = Cards.key(f.name).trim(), sur = Cards.key(surname(f)).trim();
        return !b.isEmpty() && (b.equals(sur) || full.endsWith(" " + b) || full.equals(b));
    }

    /** A fighter's surname: the short name without its initial ("W. Cortes Acosta" -> "Cortes Acosta"). */
    static String surname(SportEvent.Fighter f) {
        if (f == null) return "";
        String s = f.shortName == null ? "" : f.shortName.trim();
        Matcher m = INITIAL.matcher(s);
        if (m.matches()) return m.group(1).trim();
        if (!s.isEmpty() && s.indexOf(' ') < 0) return s;
        return lastPart(f.name == null || f.name.trim().isEmpty() ? s : f.name.trim());
    }

    /** A driver's surname, from the short name ("L. Norris") or the full name. */
    static String surname(SportEvent.Entrant x) {
        if (x == null) return "";
        String s = x.shortName == null ? "" : x.shortName.trim();
        Matcher m = INITIAL.matcher(s);
        if (m.matches()) return m.group(1).trim();
        return lastPart(x.name == null || x.name.trim().isEmpty() ? s : x.name.trim());
    }

    private static final Pattern INITIAL = Pattern.compile("^\\p{L}{1,2}\\.\\s+(.+)$");

    /** Everything after the first word ("Ciryl Gane" -> "Gane", "Waldo Cortes Acosta" -> "Cortes Acosta"). */
    private static String lastPart(String full) {
        int sp = full.indexOf(' ');
        return sp < 0 ? full : full.substring(sp + 1).trim();
    }

    /** A name's tight form: its last word ("Della Maddalena" -> "Maddalena"); the same name when one word. */
    static String tight(String surname) {
        if (surname == null) return "";
        String s = surname.trim();
        int sp = s.lastIndexOf(' ');
        return sp < 0 ? s : s.substring(sp + 1);
    }

    /**
     * A bout's two surnames for a line where the names together may take `room` (VS and its gaps already out): both
     * whole when they fit; else the longer one's tight form, then the other's, then both tight (QAS-6: "Cortes Acosta"
     * -> "Acosta", never "Cortes A…"). Each name takes the width it needs. Null when even both tight forms don't fit.
     */
    static String[] boutFit(String a, String b, float room, SettingsLogic.Measure m) {
        String ta = tight(a), tb = tight(b);
        boolean aLonger = m.width(a) >= m.width(b);
        String[][] tries = {{a, b}, aLonger ? new String[]{ta, b} : new String[]{a, tb},
            aLonger ? new String[]{a, tb} : new String[]{ta, b}, {ta, tb}};
        for (String[] t : tries) if (m.width(t[0]) + m.width(t[1]) <= room) return t;
        return null;
    }

    /** A person's names on a card, longest first: [surname] or [surname, last word]. */
    static List<String> personNames(String surname) {
        List<String> out = new ArrayList<>();
        out.add(surname == null ? "" : surname);
        String t = tight(surname);
        if (!t.isEmpty() && !t.equals(out.get(0))) out.add(t);
        return out;
    }

    /**
     * The weight classes, one table for the launcher texts, the app and Settings (design/f1ufc/GREEK-COPY.md). ESPN's
     * class; the Greek as the Greek press writes it: the class word in the genitive; "βάρος" for the four light classes
     * ("Βάρος πετεινού", "Τίτλος βάρους πετεινού"), the word alone for the rest ("Βαρέων βαρών"), or the nominative
     * where it differs (catchweight); the Settings tile's form (194 px of Google Sans 22: "Ημιμεσαίων βαρών" is 200).
     */
    private static final String[][] WEIGHTS = {
        {"strawweight", "αχύρου", "βάρος", "Βάρος αχύρου"}, {"flyweight", "μύγας", "βάρος", "Βάρος μύγας"},
        {"bantamweight", "πετεινού", "βάρος", "Βάρος πετεινού"}, {"featherweight", "φτερού", "βάρος", "Βάρος φτερού"},
        {"lightweight", "ελαφρών βαρών", "", "Ελαφρών βαρών"}, {"welterweight", "ημιμεσαίων βαρών", "", "Ημιμεσαίων"},
        {"middleweight", "μεσαίων βαρών", "", "Μεσαίων βαρών"}, {"light heavyweight", "ημιβαρέων βαρών", "", "Ημιβαρέων βαρών"},
        {"heavyweight", "βαρέων βαρών", "", "Βαρέων βαρών"},
        {"catchweight", "συμφωνημένου βάρους", "Συμφωνημένο βάρος", "Catchweight"},
    };

    /**
     * Greek: {nominative, genitive, tile} of ESPN's class: "Βάρος πετεινού γυναικών", "βάρους πετεινού γυναικών",
     * "Πετεινού γυναικών" (a women's class on a tile: its word alone); null when the table doesn't know it.
     */
    static String[] weightEl(String espn) {
        if (espn == null) return null;
        String k = espn.trim().toLowerCase(Locale.ROOT);
        boolean women = k.startsWith("women's ") || k.startsWith("womens ");
        if (women) k = k.substring(k.indexOf(' ') + 1).trim();
        for (String[] r : WEIGHTS) {
            if (!k.equals(r[0])) continue;
            boolean baros = "βάρος".equals(r[2]);
            String nom = baros ? "Βάρος " + r[1] : !r[2].isEmpty() ? r[2] : upper(r[1]);
            String gen = baros ? "βάρους " + r[1] : r[1];
            if (!women) return new String[]{nom, gen, r[3]};
            String g = " γυναικών";
            return new String[]{nom + g, gen + g, upper(r[1].split(" ")[0]) + g};
        }
        return null;
    }

    private static String upper(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(new Locale("el")) + s.substring(1); }

    /** The weight class in the interface's language: "Heavyweight", "Women's Bantamweight" / "Βαρέων βαρών", "Βάρος πετεινού γυναικών". */
    static String weightClass(String espn) { return weightClass(espn, I18n.el()); }

    static String weightClass(String espn, boolean el) {
        if (espn == null) return "";
        String w = espn.trim();
        String[] g = el ? weightEl(w) : null;
        return g == null ? w : g[0];
    }

    /**
     * A weight class where room is short (Settings' fighter tiles): "Heavyweight", "W. bantamweight" / "Βαρέων βαρών",
     * "Πετεινού γυναικών"; an unknown class as ESPN writes it.
     */
    static String weightShort(String espn, boolean el) {
        if (espn == null) return "";
        String w = espn.trim();
        if (w.isEmpty()) return "";
        if (el) { String[] g = weightEl(w); return g == null ? w : g[2]; }
        if (!w.toLowerCase(Locale.ROOT).startsWith("women's ")) return w;
        String base = w.substring(8).trim();
        return base.isEmpty() ? w : "W. " + base.substring(0, 1).toLowerCase(Locale.ROOT) + base.substring(1);
    }

    /** The bout's billing line: "Heavyweight title" / "Τίτλος βαρέων βαρών"; "Lightweight" / "Ελαφρών βαρών". */
    static String billingLine(SportEvent.Bout b) {
        if (b == null || b.weightClass == null || b.weightClass.trim().isEmpty()) return b != null && b.title ? w2("title") : "";
        if (I18n.el()) {
            String[] g = weightEl(b.weightClass);
            if (g == null) return b.title ? "Τίτλος · " + b.weightClass.trim() : b.weightClass.trim();
            return b.title ? "Τίτλος " + g[1] : g[0];
        }
        String wc = weightClass(b.weightClass);
        return b.title ? wc + " title" : wc;
    }

    /** The segment a bout is on: "MAIN" / "PRELIMS" / "EARLY" (the session's kind, else its id), "" when unknown. */
    static String segment(SportEvent.Session s) {
        if (s == null) return "";
        if (!s.kind.isEmpty()) return s.kind;
        return "main".equals(s.id) ? "MAIN" : "prelims1".equals(s.id) ? "PRELIMS" : "prelims2".equals(s.id) ? "EARLY" : "";
    }

    // ---------- team trims (SPEC-F1-UFC §5.2; bundled, checked each season) ----------
    /** ESPN team id, normalised name, trim colour. */
    private static final Object[][] TRIMS = {
        {"106892", "MCLAREN", 0xFF2B2D33}, {"106893", "MERCEDES", 0xFFC8CCCE}, {"106921", "RED BULL", 0xFFE8002D},
        {"106842", "FERRARI", 0xFFF4F0EA}, {"106967", "WILLIAMS", 0xFF0A3C8C}, {"123986", "ASTON MARTIN", 0xFFCEDC00},
        {"123988", "RACING BULLS", 0xFFF4F0EA}, {"106922", "ALPINE", 0xFF0093CC}, {"111427", "HAAS", 0xFFE6002B},
        {"132212", "AUDI", 0xFFC8CCCE}, {"132211", "CADILLAC", 0xFF1B1B1F},
    };

    /** A team's trim colour by ESPN team id, then by name; 0 when the table doesn't know the team. */
    static int trim(String teamId, String teamName) {
        for (Object[] r : TRIMS) if (r[0].equals(teamId)) return (Integer) r[2];
        String n = Cards.key(teamName).trim();
        if (n.isEmpty()) return 0;
        for (Object[] r : TRIMS) if (n.equals(r[1]) || n.startsWith(r[1] + " ") || n.endsWith(" " + r[1])) return (Integer) r[2];
        return 0;
    }

    // ---------- card words (display capitals; CardModel.w has the shared ones) ----------
    private static final String[][] WORDS = {
        {"f1", "F1", "F1"}, {"formula1", "Formula 1", "Formula 1"}, {"ufc", "UFC", "UFC"},
        {"cancelled", "CANCELLED", "ΑΚΥΡΩΘΗΚΕ"}, {"postponed", "POSTPONED", "ΑΝΑΒΛΗΘΗΚΕ"},
        {"lap", "LAP", "ΓΥΡΟΣ"}, {"of", "OF %d", "ΑΠΟ %d"}, {"next", "NEXT", "ΣΕ ΛΙΓΟ"}, {"season", "SEASON %d", "ΣΕΖΟΝ %d"},
        {"ko", "KO/TKO", "KO/TKO"}, {"sub", "SUB", "ΥΠΟΤΑΓΗ"}, {"dec", "DEC", "ΑΠΟΦΑΣΗ"}, {"dq", "DQ", "DQ"},
        {"draw", "DRAW", "ΙΣΟΠΑΛΙΑ"}, {"nc", "NC", "NC"}, {"awaiting", "NO RESULT YET", "ΣΕ ΑΝΑΜΟΝΗ"},
        {"unanimous", "UNANIMOUS", "ΟΜΟΦΩΝΗ"}, {"split", "SPLIT", "ΔΙΧΑΣΜΕΝΗ"}, {"majority", "MAJORITY", "ΠΛΕΙΟΨΗΦΙΑ"},
        {"dnf", "DNF", "DNF"}, {"dns", "DNS", "DNS"}, {"dsq", "DSQ", "DSQ"},
    };

    /** A card word in the interface's language (display capitals); the key itself when unknown. */
    static String w(String key) {
        for (String[] r : WORDS) if (r[0].equals(key)) return I18n.el() ? r[2] : r[1];
        return key;
    }

    /** Mixed-case words for eyebrows, launcher titles and descriptions. */
    private static final String[][] WORDS2 = {
        {"title", "Title", "Τίτλος"}, {"wins", "%s wins", "Νίκη: %s"}, {"wins_sprint", "%s wins the sprint", "Νίκη στο σπριντ: %s"},
        {"pole", "%s on pole", "Pole position: %s"}, {"sprint_pole", "%s on sprint pole", "Pole στο σπριντ: %s"},
        {"draw", "Draw", "Ισοπαλία"}, {"nc", "No contest", "Άκυρος αγώνας"}, {"lap", "Lap %s", "Γύρος %s"},
        {"round", "Round %d", "Γύρος %d"}, {"by", "%s wins · %s", "Νίκη: %s · %s"}, {"no_result", "No result yet", "Αναμονή αποτελέσματος"},
        {"season", "Season %d", "Σεζόν %d"},
    };

    /** A mixed-case word or pattern in the interface's language. */
    static String w2(String key) {
        for (String[] r : WORDS2) if (r[0].equals(key)) return I18n.el() ? r[2] : r[1];
        return key;
    }

    /**
     * Display capitals for event words: Greek text the Greek way (Cards.caps: accents dropped, dialytika kept), Latin
     * text with its diacritics kept (MEDIĆ, not MEDIC: Cards.caps would drop the acute). Styles use this for people.
     */
    static String caps(String s) {
        if (s == null) return "";
        return greek(s) ? Cards.caps(s) : s.toUpperCase(Locale.ROOT);
    }

    /** True when text has a Greek letter (tests: no Greek in a person's name). */
    static boolean greek(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) { char ch = s.charAt(i); if (ch >= 0x0370 && ch <= 0x03FF || ch >= 0x1F00 && ch <= 0x1FFF) return true; }
        return false;
    }
}
