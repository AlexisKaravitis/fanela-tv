package app.fanela.tv;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Greek / English text for cards, rows, banners and settings. Team names: Greek map or the source's English name.
 * The language is per thread (load() at the start of every background pass), so a change made in Settings while a
 * draw is running can never mix two languages on one row; the next pass picks it up.
 */
final class I18n {
    private static volatile String global = "en";
    private static final ThreadLocal<String> LANG = new ThreadLocal<String>() {
        @Override protected String initialValue() { return global; }
    };
    private static final Map<String, String[]> T = new HashMap<>();

    static void load(Context c) {
        String l = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "en");
        global = l;
        LANG.set(l);
        try { h24 = android.text.format.DateFormat.is24HourFormat(c); } catch (Throwable ignored) { }
    }

    /** This thread only (design checks): draw in another language without touching the setting. */
    static void use(String l) { LANG.set("el".equals(l) ? "el" : "en"); }

    static String lang() { return LANG.get(); }
    static boolean el() { return "el".equals(LANG.get()); }

    static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        return el() ? v[0] : v[1];
    }

    static boolean has(String key) { return T.containsKey(key); }

    static String f(String key, Object... a) { return String.format(Locale.ROOT, t(key), a); }

    static Locale locale() { return el() ? new Locale("el", "GR") : Locale.UK; }

    // Also published as docs/PRIVACY.md (linked from the release page). Keep both in step.
    private static final String PRIVACY_EN =
        "Fanela has no accounts, no ads and no analytics, and it doesn't collect personal information.\n\n"
        + "What stays on your TV: the clubs you follow, your language and other settings, and saved fixtures and images. "
        + "Clearing the app's data or uninstalling Fanela deletes them.\n\n"
        + "What goes over the internet: to show fixtures, scores and channels, Fanela downloads public sports data from the services "
        + "listed under \"Where the information comes from\", and to check for app updates it asks GitHub. Like any web request, these services can see your TV's IP address. "
        + "Fanela doesn't send them anything about you, and it doesn't use your location.\n\n"
        + "Home-screen rows (Android TV only): if you turn them on, Fanela gives the TV's home screen pictures of match cards to display. "
        + "They stay on the TV.\n\n"
        + "Children: Fanela is a general-audience app and doesn't knowingly collect information from anyone.\n\n"
        + "Changes: if this policy changes, the new version will be in the app and on its release page.";
    private static final String PRIVACY_EL =
        "Το Fanela δεν έχει λογαριασμούς, διαφημίσεις ή στατιστικά χρήσης και δεν συλλέγει προσωπικά στοιχεία.\n\n"
        + "Τι μένει στην τηλεόρασή σου: οι ομάδες που ακολουθείς, η γλώσσα και οι υπόλοιπες ρυθμίσεις, καθώς και το αποθηκευμένο πρόγραμμα "
        + "και οι εικόνες. Διαγράφονται αν καθαρίσεις τα δεδομένα της εφαρμογής ή την απεγκαταστήσεις.\n\n"
        + "Τι περνά από το διαδίκτυο: για να δείξει πρόγραμμα, σκορ και κανάλια, το Fanela κατεβάζει δημόσια αθλητικά στοιχεία από τις υπηρεσίες "
        + "της ενότητας «Από πού έρχονται τα στοιχεία», και για ενημερώσεις της εφαρμογής ρωτά το GitHub. Όπως σε κάθε σύνδεση, οι υπηρεσίες αυτές βλέπουν τη διεύθυνση IP της τηλεόρασης. "
        + "Το Fanela δεν τους στέλνει τίποτα για σένα και δεν χρησιμοποιεί την τοποθεσία σου.\n\n"
        + "Σειρές στην αρχική οθόνη (μόνο Android TV): αν τις ενεργοποιήσεις, το Fanela δίνει στην αρχική οθόνη εικόνες με τους αγώνες. "
        + "Μένουν στην τηλεόραση.\n\n"
        + "Παιδιά: το Fanela απευθύνεται σε όλους και δεν συλλέγει εν γνώσει του στοιχεία από κανέναν.\n\n"
        + "Αλλαγές: αν αλλάξει η πολιτική, η νέα έκδοση θα υπάρχει στην εφαρμογή και στη σελίδα των εκδόσεών της.";

    private static void p(String k, String gr, String en) { T.put(k, new String[]{gr, en}); }
    static {
        // days and pills
        p("today", "Σήμερα", "Today"); p("tomorrow", "Αύριο", "Tomorrow"); p("yesterday", "Χθες", "Yesterday");
        p("final", "Τελικό", "Final"); p("win", "Νίκη", "Win"); p("draw", "Ισοπαλία", "Draw"); p("loss", "Ήττα", "Loss");
        p("pens", "πέν.", "pens"); p("penalties", "Πέναλτι", "Penalties"); p("time_tba", "Ώρα: θα οριστεί", "Time TBA");
        p("channel_tba", "Κανάλι: θα ανακοινωθεί", "Channel: to be announced"); p("likely", "Πιθανό", "Likely");
        p("not_updated", "⚠ Χωρίς ενημέρωση", "⚠ Not updated"); p("last_update", "τελευταία ενημέρωση", "last updated");
        p("on", "στο", "on"); p("likely_channel", "πιθανό", "likely"); p("likely_channel_cap", "Πιθανό", "Likely"); p("live", "LIVE", "LIVE");
        // detail codes
        p("HT", "Ημίχρονο", "Half-time"); p("FT", "Τελικό", "Full time"); p("FT_PEN", "Τελικό (πέναλτι)", "Full time (pens)");
        p("FT_AET", "Τελικό (παράταση)", "Full time (AET)"); p("POSTPONED", "Αναβολή", "Postponed");
        p("CANCELLED", "Ακύρωση", "Cancelled"); p("ABANDONED", "Οριστική διακοπή", "Abandoned");
        p("SUSPENDED", "Διακοπή", "Suspended"); p("DELAYED", "Καθυστέρηση", "Delayed");
        p("IN_PROGRESS", "Σε εξέλιξη", "In progress"); p("AWAITING", "Αναμονή αποτελέσματος", "Awaiting result");
        p("BREAK", "Διάλειμμα", "Break"); p("ET_HT", "Ημίχρονο παράτασης", "Extra-time break");
        p("SHOOTOUT", "Πέναλτι", "Penalty shoot-out"); p("END_90", "Τέλος κανονικής διάρκειας", "End of normal time");
        p("END_120", "Τέλος παράτασης", "End of extra time");
        // competitions
        p("gre.1", "Super League", "Super League"); p("gre.cup", "Κύπελλο Ελλάδας", "Greek Cup");
        p("gre.super_cup", "Super Cup Ελλάδας", "Greek Super Cup"); p("uefa.champions", "Champions League", "Champions League");
        p("uefa.champions_qual", "Champions League · προκρ.", "Champions League · qual."); p("uefa.europa", "Europa League", "Europa League");
        p("uefa.europa_qual", "Europa League · προκρ.", "Europa League · qual."); p("uefa.europa.conf", "Conference League", "Conference League");
        p("uefa.europa.conf_qual", "Conference League · προκρ.", "Conference League · qual."); p("uefa.super_cup", "UEFA Super Cup", "UEFA Super Cup");
        p("club.friendly", "Φιλικό", "Friendly"); p("euroleague", "EuroLeague", "EuroLeague"); p("euroleague.po", "EuroLeague · Playoffs", "EuroLeague · Playoffs");
        p("euroleague.ff", "EuroLeague · Final Four", "EuroLeague · Final Four"); p("euroleague.pi", "EuroLeague · Play-In", "EuroLeague · Play-In");
        p("eurocup", "EuroCup", "EuroCup"); p("gbl", "Basket League", "Basket League"); p("gbl.cup", "Κύπελλο μπάσκετ", "Basketball Cup");
        p("bcl", "Basketball Champions League", "Basketball Champions League"); p("fiba.europe_cup", "FIBA Europe Cup", "FIBA Europe Cup");
        // home screen
        p("app", "Fanela", "Fanela"); p("settings", "Ρυθμίσεις", "Settings");
        p("updated", "Ενημέρωση: %s", "Updated %s"); p("updating", "Ενημέρωση…", "Updating…");
        p("offline", "Χωρίς σύνδεση με τον διακομιστή αποτελεσμάτων. Βλέπεις τα αποθηκευμένα.", "Can't reach the score service. Showing saved fixtures.");
        p("empty_title", "Ακολούθησε τις ομάδες σου", "Follow your clubs");
        p("empty_text", "Διάλεξε τις ομάδες που υποστηρίζεις και θα βλέπεις εδώ τον επόμενο αγώνα, το σκορ και σε ποιο κανάλι παίζει.",
            "Pick the clubs you support. You'll see their next match, the score and which channel it's on, right here.");
        p("choose_clubs", "Επιλογή ομάδων", "Choose clubs");
        p("loading_club", "Φόρτωση αγώνων…", "Getting fixtures…"); p("load_failed", "Δεν φόρτωσαν οι αγώνες. Έλεγξε τη σύνδεση της τηλεόρασης στο διαδίκτυο. Θα γίνει νέα προσπάθεια σε λίγο.",
            "Couldn't load fixtures. Check the TV's internet connection. Fanela will try again shortly.");
        p("no_connection", "Χωρίς σύνδεση", "No connection");
        p("no_fixtures", "Δεν υπάρχουν αγώνες στο πρόγραμμα αυτή τη στιγμή.", "No fixtures scheduled right now.");
        p("open_app", "Άνοιγμα %s", "Open %s"); p("get_app", "Λήψη %s", "Get %s"); p("app_missing", "Το %s δεν είναι εγκατεστημένο σε αυτή την τηλεόραση.", "%s isn't installed on this TV.");
        p("close", "Κλείσιμο", "Close");
        p("channel_note", "Αναμενόμενο κανάλι, με βάση τα τηλεοπτικά δικαιώματα της γηπεδούχου. Επιβεβαίωσέ το στον οδηγό του παρόχου σου.",
            "Expected channel, based on the home team's TV rights. Check your provider's guide to be sure.");
        // settings
        p("welcome_title", "Καλώς ήρθες στο Fanela", "Welcome to Fanela");
        p("welcome_text", "Διάλεξε γλώσσα και τις ομάδες που ακολουθείς. Μπορείς να τα αλλάξεις όποτε θέλεις από τις Ρυθμίσεις.",
            "Choose a language and the clubs you follow. You can change both later in Settings.");
        p("language_title", "Γλώσσα", "Language"); p("done", "Τέλος", "Done");
        p("favourite", "Αγαπημένη ομάδα", "Favourite team");
        p("favourite_hint", "Το Fanela παίρνει τα χρώματά της", "Fanela takes on its colours");
        p("saved_hint", "Οι αλλαγές αποθηκεύονται αμέσως.", "Changes are saved straight away.");
        p("clubs", "Οι ομάδες σου", "Your clubs");
        p("clubs_hint", "Πάτα OK σε μια ομάδα για να την ακολουθήσεις ή να την αφήσεις.", "Press OK on a club to follow or unfollow it.");
        p("options", "Επιλογές", "Options"); p("language", "Γλώσσα · Language", "Language · Γλώσσα"); p("lang_value", "Ελληνικά", "English");
        p("basketball", "Μπάσκετ", "Basketball"); p("basketball_hint", "Δείξε και τους αγώνες μπάσκετ των ομάδων", "Also show the clubs' basketball games");
        p("rows", "Σειρές στην αρχική οθόνη", "Home-screen rows");
        p("rows_hint", "Μια σειρά για κάθε ομάδα στην αρχική οθόνη της τηλεόρασης", "A row for each club on the TV's home screen");
        p("rows_unsupported", "Η αρχική οθόνη αυτής της τηλεόρασης δεν δείχνει σειρές από εφαρμογές (π.χ. Google TV). Άνοιξε το Fanela για το πρόγραμμα.",
            "This TV's home screen doesn't show rows from apps (Google TV, for example). Open Fanela to see your fixtures.");
        p("about", "Σχετικά", "About");
        p("independent_title", "Ανεξάρτητη εφαρμογή", "Independent app");
        p("independent", "Το Fanela δεν συνδέεται με καμία ομάδα, διοργάνωση ή κανάλι και δεν έχει την έγκρισή τους. Τα ονόματα και τα σήματα των ομάδων ανήκουν στις ομάδες και εμφανίζονται μόνο για να φαίνεται ποια ομάδα παίζει.",
            "Fanela isn't affiliated with or endorsed by any club, league or broadcaster. Club names and crests belong to the clubs and are shown only to identify who is playing.");
        p("sources_title", "Από πού έρχονται τα στοιχεία", "Where the information comes from");
        p("sources_full", "Πρόγραμμα και σκορ: ESPN, EuroLeague, TheSportsDB. Κανάλια και σήματα: οδηγοί προγράμματος MagentaTV και Sport24, SofaScore. Οι ώρες και τα κανάλια μπορεί να αλλάξουν. Γραμματοσειρά: Google Sans (SIL Open Font License).",
            "Fixtures and scores: ESPN, EuroLeague, TheSportsDB. Channels and crests: the MagentaTV and Sport24 TV guides, SofaScore. Times and channels can change. Font: Google Sans (SIL Open Font License).");
        p("sources_store", "Πρόγραμμα και σκορ: ESPN, EuroLeague, TheSportsDB. Κανάλια: τα τηλεοπτικά δικαιώματα κάθε ομάδας για τη σεζόν. Οι ώρες και τα κανάλια μπορεί να αλλάξουν.",
            "Fixtures and scores: ESPN, EuroLeague, TheSportsDB. Channels: each club's TV rights for the season. Times and channels can change.");
        p("privacy", "Απόρρητο", "Privacy"); p("read", "Ανάγνωση", "Read");
        p("privacy_short", "Χωρίς λογαριασμό, διαφημίσεις ή παρακολούθηση. Οι επιλογές σου μένουν σε αυτή την τηλεόραση.",
            "No account, no ads, no tracking. Your choices stay on this TV.");
        p("privacy_full", PRIVACY_EL, PRIVACY_EN);
        p("back_to_close", "Πάτα Πίσω για κλείσιμο.", "Press Back to close.");
        p("version", "Έκδοση %s", "Version %s"); p("in_days", "σε %d ημέρες", "in %d days"); p("in_hours", "σε %d ώρες", "in %d hours"); p("in_minutes", "σε %d λεπτά", "in %d minutes"); p("in_minute", "σε 1 λεπτό", "in 1 minute");
        p("updates", "Ενημερώσεις εφαρμογής", "App updates"); p("up_to_date", "Ενημερωμένη", "Up to date");
        p("install_version", "Εγκατάσταση %s", "Install %s"); p("update_available", "Νέα έκδοση στις Ρυθμίσεις", "Update ready in Settings");
        p("update_downloading", "Λήψη της νέας έκδοσης…", "Downloading the update…");
        p("update_failed", "Η ενημέρωση δεν ολοκληρώθηκε. Δοκίμασε ξανά αργότερα.", "The update didn't finish. Try again later.");
        p("update_bad_sig", "Η ενημέρωση απορρίφθηκε: δεν έχει την υπογραφή του Fanela.", "Update rejected: it isn't signed by Fanela.");
        p("yes", "Ενεργό", "On"); p("no", "Ανενεργό", "Off"); p("status", "Τελευταία ενημέρωση", "Last updated"); p("never", "ποτέ", "never"); p("never_updated", "Δεν έχει ενημερωθεί ακόμη", "Not updated yet");
        p("following", "Ακολουθείς", "Following"); p("not_following", "Δεν ακολουθείς", "Not following");
        p("enable_row", "Για να δεις τη σειρά «%s»: Αρχική οθόνη > Προσαρμογή καναλιών", "To see the \"%s\" row: Home screen > Customise channels");
        p("row_hidden", "%s · δεν είναι στην αρχική οθόνη", "%s · not on the home screen");
        p("row_show", "Προσθήκη", "Add");
        p("src_espn", "Ποδόσφαιρο", "Football"); p("src_basket", "Μπάσκετ", "Basketball");
        p("no_clubs", "Δεν ακολουθείς καμία ομάδα.", "You're not following any clubs yet.");
    }

    private static final Pattern QUARTER = Pattern.compile("Q(\\d)(\\s+.*)?"), OVERTIME = Pattern.compile("OT(\\d*)(\\s+.*)?");

    /** Detail code -> text; raw clocks (67', 90'+3') pass through; basketball clocks are translated. */
    static String detail(String d) {
        if (d == null) return "";
        if (T.containsKey(d)) return t(d);
        if (el()) {
            Matcher q = QUARTER.matcher(d);
            if (q.matches()) return q.group(1) + "ο δεκ." + (q.group(2) == null ? "" : q.group(2));
            Matcher o = OVERTIME.matcher(d);
            if (o.matches()) return "Παράταση" + (o.group(1).isEmpty() ? "" : " " + o.group(1)) + (o.group(2) == null ? "" : o.group(2));
        }
        return d;
    }

    static String comp(String c) { return c == null ? "" : T.containsKey(c) ? t(c) : c; }

    // ---------- team names ----------
    private static final Map<String, String> GR = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();  // Greek name (as a key) -> English, for Greek-only sources
    static {
        String[][] n = {{"Olympiacos", "Ολυμπιακός"}, {"Olympiacos Piraeus", "Ολυμπιακός"}, {"Olympiacos BC", "Ολυμπιακός"},
            {"PAOK", "ΠΑΟΚ"}, {"PAOK BC", "ΠΑΟΚ"}, {"PAOK Thessaloniki", "ΠΑΟΚ"}, {"AEK Athens", "ΑΕΚ"}, {"AEK BC", "ΑΕΚ"}, {"AEK", "ΑΕΚ"},
            {"Panathinaikos", "Παναθηναϊκός"}, {"Panathinaikos BC", "Παναθηναϊκός"}, {"Panathinaikos AKTOR Athens", "Παναθηναϊκός"},
            {"Aris", "Άρης"}, {"Aris BC", "Άρης"}, {"Aris Thessaloniki", "Άρης"}, {"OFI Crete", "ΟΦΗ"}, {"OFI", "ΟΦΗ"}, {"Atromitos", "Ατρόμητος"},
            {"Levadiakos", "Λεβαδειακός"}, {"Kalamata", "Καλαμάτα"}, {"Asteras Tripoli", "Αστέρας Τρ."}, {"Asteras Trip", "Αστέρας Τρ."},
            {"Iraklis", "Ηρακλής"}, {"Panetolikos", "Παναιτωλικός"}, {"Volos NFC", "Βόλος"}, {"Volos", "Βόλος"},
            {"Kifisia", "Κηφισιά"}, {"Panserraikos", "Πανσερραϊκός"}, {"Lamia", "Λαμία"}, {"Athens Kallithea", "Καλλιθέα"},
            {"Larissa", "Λάρισα"}, {"AEL Larissa", "ΑΕΛ"}, {"AEL", "ΑΕΛ"}, {"Maroussi", "Μαρούσι"}, {"Peristeri", "Περιστέρι"},
            {"Promitheas Patras", "Προμηθέας"}, {"Promitheas", "Προμηθέας"}, {"Kolossos Rodou", "Κολοσσός"}, {"Kolossos", "Κολοσσός"},
            {"Lavrio", "Λαύριο"}, {"Karditsa", "Καρδίτσα"}, {"Panionios", "Πανιώνιος"}, {"Apollon Patras", "Απόλλων Π."},
            {"Mykonos", "Μύκονος"}, {"Vikos Falcons", "Βίκος"}, {"Doxa Lefkadas", "Δόξα Λευκάδας"}, {"Psychiko", "Ψυχικό"},
            {"Panathinaikos AKTOR", "Παναθηναϊκός"}, {"Aris Midea", "Άρης"}, {"Kolossos H Hotels", "Κολοσσός"}, {"Elefsina", "Ελευσίνα"},
            {"Rethymno", "Ρέθυμνο"}, {"Ifaistos Limnou", "Ήφαιστος Λήμνου"}, {"Iraklis BC", "Ηρακλής"}, {"PAS Giannina", "ΠΑΣ Γιάννινα"}, {"Ionikos", "Ιωνικός"},
            {"Apollon Smyrnis", "Απόλλων Σμ."}, {"Xanthi", "Ξάνθη"}, {"Ergotelis", "Εργοτέλης"}, {"Chania", "Χανιά"},
            {"Stade Rennais", "Ρεν"}, {"AC Milan", "Μίλαν"}, {"NK Celje", "Τσέλιε"}, {"Real Madrid", "Ρεάλ"}, {"Barcelona", "Μπαρτσελόνα"},
            {"FC Barcelona", "Μπαρτσελόνα"}, {"Bayern Munich", "Μπάγερν"}, {"Liverpool", "Λίβερπουλ"}, {"Arsenal", "Άρσεναλ"},
            {"Manchester City", "Μάντσεστερ Σίτι"}, {"Manchester United", "Μάντσεστερ Γ."}, {"Chelsea", "Τσέλσι"},
            {"Tottenham Hotspur", "Τότεναμ"}, {"Juventus", "Γιουβέντους"}, {"Internazionale", "Ίντερ"}, {"Napoli", "Νάπολι"},
            {"AS Roma", "Ρόμα"}, {"Lazio", "Λάτσιο"}, {"Atalanta", "Αταλάντα"}, {"Atletico Madrid", "Ατλέτικο"}, {"Sevilla", "Σεβίλλη"},
            {"Villarreal", "Βιγιαρεάλ"}, {"Real Betis", "Μπέτις"}, {"Paris Saint-Germain", "Παρί Σ.Ζ."}, {"Marseille", "Μαρσέιγ"},
            {"Lyon", "Λιόν"}, {"Monaco", "Μονακό"}, {"Lille", "Λιλ"}, {"Borussia Dortmund", "Ντόρτμουντ"}, {"Bayer Leverkusen", "Λεβερκούζεν"},
            {"RB Leipzig", "Λειψία"}, {"Benfica", "Μπενφίκα"}, {"FC Porto", "Πόρτο"}, {"Sporting CP", "Σπόρτινγκ"},
            {"Ajax Amsterdam", "Άγιαξ"}, {"PSV Eindhoven", "Αϊντχόφεν"}, {"Feyenoord Rotterdam", "Φέγενορντ"}, {"Galatasaray", "Γαλατασαράι"},
            {"Fenerbahce", "Φενέρμπαχτσε"}, {"Besiktas", "Μπεσίκτας"}, {"Celtic", "Σέλτικ"}, {"Rangers", "Ρέιντζερς"},
            {"Club Brugge", "Μπριζ"}, {"Anderlecht", "Άντερλεχτ"}, {"Red Star Belgrade", "Ερυθρός Αστέρας"}, {"Crvena Zvezda", "Ερυθρός Αστέρας"},
            {"Dinamo Zagreb", "Ντιναμό Ζ."}, {"Shakhtar Donetsk", "Σαχτάρ"}, {"Slavia Prague", "Σλάβια Πράγας"},
            {"Sparta Prague", "Σπάρτα Πράγας"}, {"Ferencvaros", "Φερεντσβάρος"}, {"Young Boys", "Γιουνγκ Μπόις"},
            {"FC Copenhagen", "Κοπεγχάγη"}, {"Maccabi Tel-Aviv", "Μακάμπι"}, {"APOEL Nicosia", "ΑΠΟΕΛ"}, {"Omonia Nicosia", "Ομόνοια"},
            {"Pafos FC", "Πάφος"}, {"AEK Larnaca", "ΑΕΚ Λάρνακας"},
            // EuroLeague / EuroCup short names (abbreviatedName)
            {"Milan", "Αρμάνι Μιλάνο"}, {"EA7 Emporio Armani Milan", "Αρμάνι Μιλάνο"}, {"Dubai", "Ντουμπάι"}, {"Dubai Basketball", "Ντουμπάι"},
            {"Maccabi", "Μακάμπι"}, {"Maccabi Tel Aviv", "Μακάμπι"}, {"Anadolu Efes", "Εφές"}, {"Efes", "Εφές"}, {"Partizan", "Παρτίζαν"},
            {"Zalgiris", "Ζαλγκίρις"}, {"Zalgiris Kaunas", "Ζαλγκίρις"}, {"Bayern", "Μπάγερν"}, {"Virtus", "Βίρτους"},
            {"Virtus Bologna", "Βίρτους"}, {"Baskonia", "Μπασκόνια"}, {"Valencia", "Βαλένθια"}, {"Valencia Basket", "Βαλένθια"},
            {"ASVEL", "Βιλερμπάν"}, {"LDLC ASVEL", "Βιλερμπάν"}, {"Paris", "Παρί"}, {"Paris Basketball", "Παρί"}, {"Hapoel", "Χάποελ"},
            {"Hapoel Tel Aviv", "Χάποελ Τελ Αβίβ"}, {"Hapoel TLV", "Χάποελ Τελ Αβίβ"}, {"Hapoel Jerusalem", "Χάποελ Ιερ."},
            {"Hapoel JLM", "Χάποελ Ιερ."}, {"Monaco Basket", "Μονακό"}, {"AS Monaco", "Μονακό"}, {"Fenerbahce Beko", "Φενέρμπαχτσε"},
            {"Real", "Ρεάλ"}, {"Barca", "Μπαρτσελόνα"}, {"Crvena zvezda", "Ερυθρός Αστέρας"}, {"Zvezda", "Ερυθρός Αστέρας"},
            {"Riga Zelli", "Ρίγα Ζέλι"}, {"Balkan", "Μπότεβγκραντ"}, {"Turk Telekom", "Τουρκ Τέλεκομ"}, {"Bahcesehir", "Μπαχτσεσεχίρ"},
            {"Cedevita Olimpija", "Τσεντεβίτα"}, {"Ulm", "Ουλμ"}, {"Ratiopharm Ulm", "Ουλμ"}, {"Trento", "Τρέντο"}, {"Joventut", "Μπανταλόνα"},
            {"Gran Canaria", "Γκραν Κανάρια"}, {"Lietkabelis", "Λιετκαμπέλις"}, {"Hamburg", "Αμβούργο"}, {"Bourg", "Μπουρ"},
            {"Buducnost", "Μπούντουτσνοστ"}, {"U-BT Cluj", "Κλουζ"}, {"Venezia", "Βενέτσια"}, {"Reyer Venezia", "Βενέτσια"},
            {"Beşiktaş", "Μπεσίκτας"}, {"Besiktas Fibabanka", "Μπεσίκτας"}, {"Manresa", "Μανρέσα"}, {"Wolves", "Γουλβς"},
            {"Slask", "Σλασκ"}, {"Ankara", "Άγκυρα"}, {"Neptunas", "Νεπτούνας"}, {"Trapani", "Τράπανι"}, {"Tenerife", "Τενερίφη"}};
        for (String[] x : n) {
            GR.put(x[0], x[1]);
            String k = Cards.key(x[1]);
            if (!EN.containsKey(k)) EN.put(k, x[0]);
        }
        EN.put(Cards.key("Αστέρας"), "Asteras Tripoli");
        // full Greek names the TV guides use for basketball clubs
        String[][] full = {{"Βίκος Ιωαννίνων", "Vikos Falcons"}, {"ΑΣΚ Καρδίτσας", "Karditsa"}, {"Καρδίτσα", "Karditsa"},
            {"Κολοσσός Ρόδου", "Kolossos Rodou"}, {"Προμηθέας Πάτρας", "Promitheas Patras"}, {"Απόλλων Πάτρας", "Apollon Patras"},
            {"Ιωνικός Νικαίας", "Ionikos"}, {"Δόξα Λευκάδας", "Doxa Lefkadas"}, {"Ψυχικό", "Psychiko"}, {"Ελευσίνα", "Elefsina"},
            {"Πανιώνιος", "Panionios"}, {"Λαύριο", "Lavrio"}, {"Περιστέρι", "Peristeri"}, {"Μαρούσι", "Maroussi"}, {"Μύκονος", "Mykonos"}};
        for (String[] f : full) EN.put(Cards.key(f[0]), f[1]);
        EN.put(Cards.key("Ηρακλής"), "Iraklis"); EN.put(Cards.key("ΑΕΚ"), "AEK"); EN.put(Cards.key("ΠΑΟΚ"), "PAOK");
        EN.put(Cards.key("Ολυμπιακός"), "Olympiacos"); EN.put(Cards.key("Παναθηναϊκός"), "Panathinaikos"); EN.put(Cards.key("Άρης"), "Aris");
    }

    private static final Pattern SUFFIX = Pattern.compile("(?i)\\s+(B\\.?C\\.?|F\\.?C\\.?|Athens|Piraeus|Thessaloniki|Basket(ball)?)$");

    /** Greek name for a raw (English) team name; already-Greek names and unknown names pass through. */
    static String grName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "?";
        String r = raw.trim();
        String g = GR.get(r);
        if (g != null) return g;
        String bare = r;
        for (int i = 0; i < 3; i++) { String b = SUFFIX.matcher(bare).replaceAll("").trim(); if (b.equals(bare)) break; bare = b; }
        g = GR.get(bare);
        if (g != null) return g;
        if (r.startsWith("Olympiacos") || r.startsWith("Olympiakos")) return "Ολυμπιακός";
        if (r.startsWith("Panathinaikos")) return "Παναθηναϊκός";
        if (r.startsWith("Asteras")) return "Αστέρας Τρ.";
        return r;
    }

    /** English name for a Greek-only name (Sport24 titles); English names pass through. */
    static String enName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "?";
        String r = raw.trim();
        if (!r.matches(".*[\\u0370-\\u03FF].*")) return r;
        String e = EN.get(Cards.key(r));
        if (e != null) return e;
        // "ΟΛΥΜΠΙΑΚΟΣ ΣΦΠ" is Olympiacos, but "ΟΛΥΜΠΙΑΚΟΣ ΒΟΛΟΥ" is another club: the first word only counts
        // when every other word just describes the same club
        String[] w = Cards.key(r).split("\\s+");
        e = EN.get(w[0]);
        if (e == null) return r;
        for (int i = 1; i < w.length; i++) if (!SAME_CLUB.contains(w[i])) return r;
        return e;
    }

    private static final java.util.Set<String> SAME_CLUB = new java.util.HashSet<>(java.util.Arrays.asList(
        "ΣΦΠ", "ΠΕΙΡΑΙΩΣ", "ΘΕΣΣΑΛΟΝΙΚΗΣ", "ΑΘΗΝΩΝ", "ΤΡΙΠΟΛΗΣ", "ΚΑΕ", "ΠΑΕ", "ΒΣ", "FC", "BC", "ΠΓΣ", "ΓΣ", "ΑΟ", "ΑΣ"));

    private static final Pattern BC = Pattern.compile("(?i)\\s+B\\.?C\\.?$");

    static String team(String raw) { return BC.matcher(el() ? grName(raw) : enName(raw)).replaceAll(""); }

    /**
     * The name on a match card, where space is tight: the form fans use ("AEK", "Man City"), so one club reads the same
     * on its football and basketball cards. Everywhere else (titles, Settings) the full name stays.
     */
    static String shortTeam(String raw) {
        String n = team(raw);
        String s = SHORT.get(n);
        return s != null ? s : n;
    }

    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        String[][] pairs = {
            {"AEK Athens", "AEK"}, {"Olympiacos Piraeus", "Olympiacos"}, {"PAOK Thessaloniki", "PAOK"},
            {"Panathinaikos Athens", "Panathinaikos"}, {"Aris Thessaloniki", "Aris"},
            {"Manchester United", "Man United"}, {"Manchester City", "Man City"}, {"Tottenham Hotspur", "Spurs"},
            {"Newcastle United", "Newcastle"}, {"Nottingham Forest", "Nott'm Forest"}, {"Brighton & Hove Albion", "Brighton"},
            {"AFC Bournemouth", "Bournemouth"}, {"Wolverhampton Wanderers", "Wolves"}, {"West Ham United", "West Ham"},
            {"Leeds United", "Leeds"}, {"Sheffield United", "Sheffield Utd"}, {"Paris Saint-Germain", "PSG"},
            {"Bayern Munich", "Bayern"}, {"Borussia Dortmund", "Dortmund"}, {"Bayer Leverkusen", "Leverkusen"},
            {"Atletico Madrid", "Atlético"}, {"Atlético Madrid", "Atlético"}, {"Internazionale", "Inter"},
            {"Dubai Basketball", "Dubai"}, {"Maccabi Tel Aviv", "Maccabi"},
            {"Μάντσεστερ Γιουνάιτεντ", "Μάντσεστερ Γ."}, {"Μάντσεστερ Σίτι", "Μάντσεστερ Σ."}};
        for (String[] p : pairs) SHORT.put(p[0], p[1]);
    }

    // ---------- dates ----------
    static long startOfDay(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    static String day(long t) {
        Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
        c.setTimeInMillis(t);
        int days = (int) Math.round((startOfDay(c) - startOfDay(now)) / 86400000.0);
        return days == 0 ? t("today") : days == 1 ? t("tomorrow") : days == -1 ? t("yesterday")
            : new SimpleDateFormat(el() ? "EEE d/M" : "EEE d MMM", locale()).format(c.getTime());
    }

    private static volatile boolean h24 = true;

    /** Follows the TV's 12/24-hour setting (read with the language). */
    /** "Saturday 8 November" / "Σάββατο 8 Νοεμβρίου" (today / tomorrow / yesterday when close). */
    static String longDate(long t) {
        String d = day(t);
        if (d.equals(t("today")) || d.equals(t("tomorrow")) || d.equals(t("yesterday"))) return d;
        return new SimpleDateFormat(el() ? "EEEE d MMMM" : "EEEE d MMMM", locale()).format(new java.util.Date(t));
    }

    static String time(long t) {
        return new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", h24 ? Locale.US : locale()).format(new java.util.Date(t));
    }
}
