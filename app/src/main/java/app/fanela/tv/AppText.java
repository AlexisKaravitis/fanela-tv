package app.fanela.tv;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The words of the 1.2 home screen that the app had no string for yet (English and Greek, in I18n's language), and
 * the dates it writes relative to a given "now", so a screen and its tests agree on what "tomorrow" is.
 */
final class AppText {
    private AppText() { }

    private static final Map<String, String[]> T = new HashMap<>();
    private static void p(String k, String el, String en) { T.put(k, new String[]{el, en}); }

    static {
        p("next", "Επόμενο", "Next"); p("latest", "Τελευταία", "Latest"); p("fixtures", "Πρόγραμμα", "Fixtures");
        p("home", "εντός", "home"); p("away", "εκτός", "away"); p("full_time", "Τελικό", "Full time");
        p("tomorrow", "Αύριο", "Tomorrow"); p("today", "Σήμερα", "Today");
        p("in_days", "Σε %d ημέρες", "In %d days"); p("today_in", "Σήμερα · σε %s", "Today · in %s");
        p("dur_hm", "%d ώρ. %d λεπ.", "%d h %d min"); p("dur_h", "%d ώρ.", "%d h"); p("dur_m", "%d λεπ.", "%d min");
        p("won", "Νίκη %s", "Won %s"); p("lost", "Ήττα %s", "Lost %s"); p("drew", "Ισοπαλία %s", "Drew %s");
        p("lead", "%s μπροστά με %d", "%s lead by %d"); p("level", "Ισόπαλο", "All square");
        p("left", "%s για το τέλος", "%s left"); p("opens", "ανοίγει το %s", "opens %s"); p("details", "Λεπτομέρειες", "Match details");
        p("updating", "Ενημέρωση…", "Updating…");
        p("offline", "Χωρίς σύνδεση με τον διακομιστή αποτελεσμάτων · βλέπεις τα αποθηκευμένα (%s)",
            "Can't reach the score service · showing fixtures saved at %s");
        p("offline_never", "Χωρίς σύνδεση με τον διακομιστή αποτελεσμάτων", "Can't reach the score service");
        // a time still to be set: "TBA" in the compact numeral slots (fixture rows, the ribbon), as on the cards;
        // "Θα οριστεί" where there is room for words (the hero, the stage, the match screen, the other leg)
        p("tba", "TBA", "TBA"); p("time_tba", "Θα οριστεί", "Time TBA");
        p("ppd", "ΑΝΑΒ.", "PPD"); p("half_time", "Ημίχρονο", "Half-time");
        // the live cell's short phase words (136 px wide: never the long detail sentence)
        p("ph_BREAK", "ΔΙΑΛ.", "BREAK"); p("ph_ET_HT", "ΔΙΑΛ.", "ET HT"); p("ph_SHOOTOUT", "ΠΕΝ.", "PENS");
        p("ph_END_90", "90′", "90′"); p("ph_END_120", "120′", "120′"); p("ph_SUSPENDED", "ΔΙΑΚ.", "SUSP.");
        p("ot", "Παρ.", "OT"); p("update_ready", "Νέα έκδοση στις Ρυθμίσεις", "Update ready in Settings");
        p("live_now", "Ζωντανά", "Live now"); p("selected", "επιλεγμένη", "selected");
        p("opens_channel", "Πάτα OK για το %s", "Press OK to open %s");
    }

    static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return I18n.t(key);
        return I18n.el() ? v[0] : v[1];
    }

    static String f(String key, Object... a) { return String.format(Locale.ROOT, t(key), a); }

    // ---------- dates relative to a given now ----------
    /** Whole calendar days from now's day to t's day (0 today, 1 tomorrow, -1 yesterday). */
    static int days(long now, long t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(now);
        b.setTimeInMillis(t);
        return (int) Math.round((I18n.startOfDay(b) - I18n.startOfDay(a)) / 86400000.0);
    }

    private static Locale loc() { return I18n.locale(); }

    /** "Sun 15 Nov" / "Κυρ 15 Νοε" (a fixture row's day, the date of a result). */
    static String shortDate(long t) {
        return new SimpleDateFormat(usFormat() ? "EEE, MMM d" : "EEE d MMM", usFormat() ? Locale.US : loc()).format(new java.util.Date(t));
    }

    /** "Thursday 12 November" / "Πέμπτη 12 Νοεμβρίου" (never today / tomorrow: the line above says that). */
    static String longDate(long t) {
        return new SimpleDateFormat(usFormat() ? "EEEE, MMMM d" : "EEEE d MMMM",
            usFormat() ? Locale.US : loc()).format(new java.util.Date(t));
    }

    /** "Sat" / "Σάβ". */
    static String weekday(long t) { return new SimpleDateFormat("EEE", usFormat() ? Locale.US : loc()).format(new java.util.Date(t)); }

    /** The viewer reads US dates ("Sat, Nov 21"): I18n decides that from the country. */
    private static boolean usFormat() { return !I18n.el() && I18n.weekdayDate(0).contains(","); }

    /** How soon, the way a person says it: "2 h 15 min", "40 min", "3 h". */
    static String duration(long ms) {
        long min = Math.max(1, Math.round(ms / 60000.0));
        if (min < 60) return f("dur_m", (int) min);
        long h = min / 60, m = min % 60;
        return m == 0 ? f("dur_h", (int) h) : f("dur_hm", (int) h, (int) m);
    }

    /** The hero's relative line: "Tomorrow", "In 9 days", "Today · in 2 h 15 min"; a past kick-off today: "Today". */
    static String relative(long now, long start) {
        int d = days(now, start);
        if (d == 0) return start > now ? f("today_in", duration(start - now)) : t("today");
        if (d == 1) return t("tomorrow");
        if (d > 1) return f("in_days", d);
        return shortDate(start);
    }

    /**
     * The rail's next kick-off, short enough for its 172 px at 24 px (SPEC-APP 3.2): "Today 17:30", "Sat 17:30" (the
     * day after tomorrow too, and tomorrow in English, where "Tomorrow 17:30" would not fit; Greek "Αύριο 17:30" does),
     * a week or more away the date alone ("Sun 22 Nov").
     */
    static String railNext(long now, Match m) {
        int d = days(now, m.start);
        if (d >= 7 || d < 0) return shortDate(m.start);
        String day = d == 0 ? t("today") : d == 1 && I18n.el() ? t("tomorrow") : weekday(m.start);
        return m.tbd ? day : day + " " + I18n.time(m.start);
    }

    /** The status slot's clock: "Wed 11 Nov · 18:30". */
    static String clock(long now) { return shortDate(now) + " · " + I18n.time(now); }

    // ---- wave C settings ----
    // The words of Settings › Appearance, the first start and the text panel (SPEC-APP 5).
    static {
        p("appearance", "Εμφάνιση", "Appearance");
        p("appearance_hint", "Στυλ καρτών, διάταξη εφαρμογής και σκορ στην αρχική οθόνη", "Card style, app layout and scores on the home screen");
        p("card_style", "Στυλ καρτών", "Card style");
        p("card_style_hint", "Πώς σχεδιάζει το Fanela τις κάρτες των ομάδων σου στην αρχική οθόνη της τηλεόρασης και στην εφαρμογή. Οι προεπισκοπήσεις δείχνουν τον επόμενο αγώνα σου.",
            "How Fanela draws your clubs' cards on the TV's home screen and in the app. Previews use your next match.");
        p("card_style_hint_sample", "Πώς σχεδιάζει το Fanela τις κάρτες των ομάδων σου στην αρχική οθόνη της τηλεόρασης και στην εφαρμογή. Οι προεπισκοπήσεις δείχνουν έναν ενδεικτικό αγώνα.",
            "How Fanela draws your clubs' cards on the TV's home screen and in the app. Previews use a sample match.");
        p("style_signature", "Υπογραφή", "Signature"); p("style_clean", "Λιτό", "Clean"); p("style_kit", "Φανέλα", "Kit");
        p("style_ticket", "Εισιτήριο", "Ticket"); p("style_clash", "Μονομαχία", "Clash"); p("style_default", "Προεπιλογή", "Default");
        p("app_layout", "Διάταξη εφαρμογής", "App layout");
        p("layout_club", "Ομάδα", "Club");
        p("layout_club_hint", "Μία ομάδα κάθε φορά, με τα χρώματά της. Αλλάζεις ομάδα από την αριστερή στήλη.",
            "One club at a time, wearing its colours. Switch clubs in the left rail.");
        p("layout_matchday", "Αγωνιστική", "Matchday");
        p("layout_matchday_hint", "Ο επόμενος αγώνας σε πρώτο πλάνο, από κάτω σειρές με κάρτες για κάθε ομάδα.",
            "The next match on a stage, rows of cards for every club below.");
        p("show_scores", "Σκορ στην αρχική οθόνη", "Show scores on the home screen");
        p("show_scores_hint", "Ανενεργό: οι κάρτες της αρχικής οθόνης δεν δείχνουν αποτελέσματα μέχρι να ανοίξεις το Fanela (χωρίς spoilers)",
            "Off hides results on the TV's home-screen cards until you open Fanela (no spoilers)");
        p("panel_hint", "▲▼ κύλιση · Πίσω για κλείσιμο", "▲▼ scroll · Back closes");
        p("skip_short", "Παράλειψη", "Skip");
        p("selected_style", "επιλεγμένο", "selected");
        p("font_licences", "Άδειες των γραμματοσειρών (το πλήρες κείμενο, στα αγγλικά)", "Font licences (in full)");
    }

    // ---- wave C home ----
    static {
        p("up_next", "Επόμενο", "Up next"); p("later", "Αργότερα", "Later"); p("next_latest", "Επόμενα & τελευταία", "Next & latest");
        p("in_h", "σε %d ώρες", "in %d h"); p("in_1h", "σε 1 ώρα", "in 1 h");
        p("home_cap", "Εντός", "Home"); p("away_cap", "Εκτός", "Away");
        p("where", "Πού παίζει", "Where to watch"); p("recent", "Πρόσφατα", "Recent"); p("last_five", "Τελευταία πέντε", "Last five");
        p("return_leg", "Ρεβάνς", "Return leg"); p("first_leg", "Πρώτος αγώνας", "First leg");
        p("at_home", "εντός έδρας", "at home"); p("at_away", "εκτός έδρας", "at %s");
        p("sure_why", "Από τον οδηγό του %s για αυτή την ημέρα.", "From the %s guide for this day.");
        p("tv_why", "Από τον οδηγό προγράμματος αυτής της τηλεόρασης.", "From this TV's programme guide.");
        p("installed", "Το %s είναι εγκατεστημένο σε αυτή την τηλεόραση.", "%s is installed on this TV.");
        p("ft_label", "Τελικό σκορ", "Full time");
    }

    /** The Matchday stage's relative line: "Today · in 2 h 15 min", "Tomorrow · in 26 h", "In 9 days"; "" for the past. */
    static String relativeHours(long now, long start) {
        int d = days(now, start);
        if (start <= now) return d == 0 ? t("today") : "";
        if (d == 0) return f("today_in", duration(start - now));
        if (d == 1) {
            long h = Math.max(1, (start - now) / 3600000L);  // whole hours, as a person counts them down
            return t("tomorrow") + " · " + (h == 1 ? t("in_1h") : f("in_h", (int) h));
        }
        return f("in_days", d);
    }

    /** "at Dubai" / "at home" (Greek: εκτός έδρας / εντός έδρας, which need no article). */
    static String venue(boolean home, String opponent) {
        if (home) return t("at_home");
        return I18n.el() ? t("at_away") : f("at_away", opponent);
    }
}
