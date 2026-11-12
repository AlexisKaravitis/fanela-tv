package app.fanela.tv;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The app's words for Formula 1 and UFC (SPEC-F1-UFC §4, §7-§9), in I18n's language: session and segment names,
 * places and circuit facts, the relative lines, results and methods, the rail's awareness line, the screens' labels.
 * Plain Java (unit-tested): the card words stay EventCardModel's and SportNames' (stream D); these are the app's own.
 * People are in Latin script in both languages (§4.3); places, sessions, weight classes, methods and days are Greek
 * in Greek, always 24-hour.
 */
final class SportText {
    private SportText() { }

    /**
     * Hide scores inside the app: the owner's decision (SPEC-F1-UFC §13.6, F1-UFC-ARCHITECTURE §9.7) is that for F1 and
     * UFC the switch protects the app too, so every F1/UFC surface of the app (rail, panel, Matchday stage and ribbon,
     * event screens, what TalkBack says) follows Appearance.showScores. Football in the app stays as SPEC-APP 5.3 has
     * it (the switch protects the home-screen cards only).
     */
    static final boolean HIDE_IN_APP = true;

    /** Whether the app hides results for this setting (pure). */
    static boolean hidden(boolean showScores) { return HIDE_IN_APP && !showScores; }

    static boolean hidden(Context c) { return hidden(Appearance.showScores(c)); }

    private static final Map<String, String[]> T = new HashMap<>();
    private static void p(String k, String el, String en) { T.put(k, new String[]{el, en}); }

    static {
        p("f1", "Formula 1", "Formula 1"); p("ufc", "UFC", "UFC");
        p("s_FP1", "FP1", "FP1"); p("s_FP2", "FP2", "FP2"); p("s_FP3", "FP3", "FP3"); p("s_QUAL", "Κατατακτ.", "Quali");
        p("s_SQ", "Κατατ. σπριντ", "Sprint quali"); p("s_SPRINT", "Σπριντ", "Sprint"); p("s_RACE", "Αγώνας", "Race");
        p("laps", "%d γύροι", "%d laps"); p("km", "%s χλμ.", "%s km");
        p("wins", "Νίκη: %s", "%s wins"); p("won", "Νίκη: %s", "%s won"); p("final", "Τελικό", "Final");
        p("draw", "Ισοπαλία", "Draw"); p("nc", "Άκυρος αγώνας", "No contest");
        p("m_KO", "KO/TKO", "KO/TKO"); p("m_SUB", "Υποταγή", "Submission"); p("m_DEC", "Απόφαση", "Decision");
        p("m_DQ", "Αποκλεισμός", "DQ"); p("d_unanimous", "Ομόφωνη απόφαση", "unanimous"); p("d_split", "Διχασμένη απόφαση", "split");
        p("d_majority", "Απόφαση κατά πλειοψηφία", "majority");
        p("round", "Γύρος %d", "Round %d"); p("rounds", "%d γύροι", "%d rounds"); p("of", "από %d", "of %d");
        p("title", "τίτλος", "title"); p("title_chip", "ΤΙΤΛΟΣ", "TITLE");
        p("cancelled", "Ακυρώθηκε", "Cancelled"); p("postponed", "Αναβλήθηκε", "Postponed"); p("delayed", "Καθυστέρηση", "Delayed");
        p("red_flag", "Κόκκινη σημαία", "Red flag"); p("awaiting", "Αναμονή αποτελέσματος", "No result yet");
        p("in_days", "σε %d ημέρες", "in %d days"); p("tomorrow_l", "αύριο", "tomorrow"); p("today_l", "σήμερα", "today");
        p("in_dur", "σε %s", "in %s");
        p("sessions", "Πρόγραμμα", "Sessions"); p("your_time", "Η ώρα σου (%s)", "Your time (%s)"); p("greek_time", "Ώρα Ελλάδας", "Greek time");
        p("local_time", "Τοπική ώρα", "Your time");
        p("weekend", "Το τριήμερο", "The weekend"); p("this_card", "Η κάρτα", "This card");
        p("next_events", "Επόμενες διοργανώσεις", "Next events"); p("next_gp", "Επόμενο Γκραν Πρι", "Next grand prix");
        p("drivers", "Βαθμολογία οδηγών", "Drivers' championship"); p("standings", "Βαθμολογία", "Standings"); p("teams", "Βαθμολογία κατασκευαστών", "Constructors' championship");
        p("live_order", "Κατάταξη · γύρος %d από %d", "Live order · lap %d of %d"); p("live_order_plain", "Κατάταξη", "Live order");
        p("classification", "%s · κατάταξη", "%s · classification"); p("circuit", "Πίστα", "Circuit");
        p("leader", "Πρώτος", "Leader"); p("your_driver", "Ο οδηγός σου: %s", "Your driver: %s"); p("leader_line", "Πρώτος: %s", "Leader: %s");
        p("lap", "Γύρος", "Lap"); p("lap_n", "Γύρος %d", "Lap %d");
        p("weekend_details", "Το πρόγραμμα του τριημέρου", "Weekend details"); p("fight_card", "Όλοι οι αγώνες", "Fight card");
        p("free", "Ελεύθερη λήψη", "Free-to-air"); p("app", "Εφαρμογή", "App");
        p("bouts", "%d αγώνες", "%d bouts"); p("scores_hidden", "κρυφά σκορ", "scores hidden");
        p("season", "Σεζόν %d", "Season %d"); p("at_track", "Στην πίστα: %s", "At the track: %s");
        p("standings_sub", "Οι δύο πρώτοι · %s", "Top two · %s"); p("round_no", "Αγώνας %d", "Round %d");
        p("no_events_f1", "Δεν έχει ανακοινωθεί ακόμη το επόμενο Γκραν Πρι.", "No race weekend listed yet.");
        p("no_events_ufc", "Δεν έχει ανακοινωθεί ακόμη η επόμενη διοργάνωση.", "No event listed yet.");
        p("loading_sport", "Φόρτωση…", "Loading…");
        p("follow_x", "Ακολούθησε: %s", "Follow %s"); p("unfollow_x", "Σταμάτα να ακολουθείς: %s", "Stop following %s");
        p("pick_other", "Αριστερά / δεξιά: ο αντίπαλος", "Left / right: the other fighter");
        p("following_x", "Ακολουθείς: %s", "Following %s"); p("unfollowed_x", "Δεν ακολουθείς πια: %s", "No longer following %s");
    }

    static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return AppText.t(key);
        return I18n.el() ? v[0] : v[1];
    }

    static String f(String key, Object... a) { return String.format(Locale.ROOT, t(key), a); }

    // ---------- names ----------
    static String sportName(String sport) { return SportEvent.UFC.equals(sport) ? t("ufc") : t("f1"); }

    /** The rail's and the rows' monogram: F1 / UFC (Fanela's own type, never a logo). */
    static String monogram(String sport) { return SportEvent.UFC.equals(sport) ? "UFC" : "F1"; }

    /** An F1 session in the app's mixed case ("Practice 1", "Κατατακτήριες"); OTHER keeps ESPN's label. */
    static String session(SportEvent.Session s) {
        if (s == null) return "";
        return SportNames.sessionLong(s.kind, s.typeAbbr);
    }

    /** The tight session word (the rail, Clash): "Quali", "FP1". */
    static String sessionShort(SportEvent.Session s) {
        if (s == null) return "";
        return T.containsKey("s_" + s.kind) ? t("s_" + s.kind) : session(s);
    }

    /** A session (F1) or a card segment (UFC). */
    static String part(SportEvent e, SportEvent.Session s) { return s == null ? "" : session(s); }

    /** The event's clean title (SportNames, stream D): "Las Vegas Grand Prix", "UFC 334". */
    static String title(SportEvent e) {
        if (e == null) return "";
        return SportEvent.F1.equals(e.sport) ? SportNames.gpLong(e) : SportNames.ufcName(e);
    }

    /** The shortest of SportNames' forms ("Las Vegas GP", "UFC 334"). */
    static String shortTitle(SportEvent e) {
        if (e == null) return "";
        return SportEvent.F1.equals(e.sport) ? SportNames.gpCard(e) : SportNames.ufcName(e);
    }

    /** UFC: "UFC 334", "UFC Fight Night" (the event, never the main event's names). */
    static String ufcEvent(SportEvent e) { return e == null ? "" : SportNames.ufcName(e); }

    /**
     * "Las Vegas, USA" / "Λας Βέγκας, ΗΠΑ" (whatever of city and country is known): the same line in both languages
     * (QAS-11), the country in Greek from SportNames.countryEl (left out when it has no Greek form) and never twice
     * ("Monaco", not "Monaco, Monaco").
     */
    static String place(SportEvent e) {
        if (e == null) return "";
        java.util.List<String> curated = SportNames.gp(e) == null ? null : SportNames.placeNames(e);
        if (curated != null && !curated.isEmpty()) {  // the curated place (SportNames): ESPN's "city" can be a state (Nevada)
            String cc = e.country == null ? "" : e.country.trim(), p = curated.get(0);
            if (I18n.el()) cc = SportNames.countryEl(cc);
            return cc.isEmpty() || cc.equalsIgnoreCase(p) ? p : p + ", " + cc;
        }
        String city = e.city == null ? "" : e.city.trim(), cc = e.country == null ? "" : e.country.trim();
        if (city.isEmpty()) return cc;
        if (cc.isEmpty() || cc.equalsIgnoreCase(city)) return city;
        return city + ", " + cc;
    }

    /**
     * The weekend screen's eyebrow: "FORMULA 1 · LAS VEGAS STREET CIRCUIT", ESPN's circuit in both languages (a proper
     * noun, Latin in Greek too: the Greek grand prix name repeated the title under it). No circuit: "FORMULA 1" alone.
     */
    static String weekendEyebrow(SportEvent e) {
        if (e == null) return t("f1");
        String where = e.circuitName == null ? "" : e.circuitName.trim();
        return t("f1") + (where.isEmpty() ? "" : " · " + where);
    }

    /** "50 laps · 6.201 km" / "50 γύροι · 6,201 χλμ"; "" when nothing is known. */
    static String facts(SportEvent e) {
        if (e == null) return "";
        StringBuilder b = new StringBuilder();
        if (e.laps > 0) b.append(f("laps", e.laps));
        if (e.lengthKm > 0) {
            if (b.length() > 0) b.append(" · ");
            b.append(f("km", km(e.lengthKm)));
        }
        return b.toString();
    }

    /** 6.201 / 6,201 (Greek decimal comma). */
    static String km(float v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        return I18n.el() ? s.replace('.', ',') : s;
    }

    /** A fighter's surname as billed: the last word of the name ("Cortes Acosta" keeps both when ESPN's short has them). */
    static String surname(SportEvent.Fighter f) { return f == null ? "" : SportNames.surname(f); }

    static String name(SportEvent.Fighter f) { return f == null ? "" : f.name == null || f.name.isEmpty() ? surname(f) : f.name; }

    /** "Gane VS Hokit" (the owner's VS, never "vs."). */
    static String bout(SportEvent.Bout b) {
        if (b == null) return "";
        return surname(b.a) + " " + CardKit.VS + " " + surname(b.b);
    }

    /** A weight class in the interface's language (SportNames, capitalised for a row's start). */
    static String weight(String w) { return cap(SportNames.weightClass(w)); }

    /** "Heavyweight title" / "Τίτλος βαρέων βαρών" (SportNames.billingLine). */
    static String billing(SportEvent.Bout b) { return b == null ? "" : SportNames.billingLine(b); }

    static String rounds(SportEvent.Bout b) { return b == null || b.rounds <= 0 ? "" : f("rounds", b.rounds); }

    static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---------- results ----------
    /** The method's word: "KO/TKO", "Submission", "Decision"; ESPN's own word otherwise; "" before a result. */
    static String method(SportEvent.Bout b) {
        if (b == null) return "";
        if (b.outcome == 3) return t("draw");
        if (b.outcome == 4) return t("nc");
        String m = b.method == null ? "" : b.method.trim();
        if (m.isEmpty()) return "";
        String k = methodKey(m);
        return k == null ? m : t(k);
    }

    /** The card's short form: KO/TKO, SUB, DEC, DQ (the big numeral of the result hero). */
    static String methodShort(SportEvent.Bout b) {
        if (b == null) return "";
        if (b.outcome == 3) return t("draw").toUpperCase(I18n.locale());
        if (b.outcome == 4) return "NC";
        String m = b.method == null ? "" : b.method.trim();
        String k = methodKey(m);
        if ("m_SUB".equals(k)) return SportNames.w("sub");
        if ("m_DEC".equals(k)) return SportNames.w("dec");
        if ("m_DQ".equals(k)) return "DQ";
        if ("m_KO".equals(k)) return "KO/TKO";
        return Cards.caps(m);
    }

    private static String methodKey(String m) {
        String l = m.toLowerCase(Locale.ROOT);
        if (l.contains("ko") || l.contains("tko")) return "m_KO";
        if (l.startsWith("sub")) return "m_SUB";
        if (l.startsWith("dec")) return "m_DEC";
        if (l.equals("dq") || l.contains("disq")) return "m_DQ";
        return null;
    }

    /** "Decision · split" (the decision's kind when ESPN gives it); the method alone otherwise. */
    static String methodLine(SportEvent.Bout b) {
        String m = method(b);
        if (m.isEmpty() || b == null) return m;
        String d = b.methodDetail == null ? "" : b.methodDetail.toLowerCase(Locale.ROOT);
        if ("m_DEC".equals(methodKey(b.method == null ? "" : b.method)) || b.method != null && b.method.toLowerCase(Locale.ROOT).startsWith("dec")) {
            // English "Decision · split"; Greek the whole phrase: "Διχασμένη απόφαση", "Απόφαση κατά πλειοψηφία"
            for (String k : new String[]{"unanimous", "split", "majority"})
                if (d.contains(k) || b.method.toLowerCase(Locale.ROOT).contains(k)) return I18n.el() ? t("d_" + k) : m + " · " + t("d_" + k);
        }
        return m;
    }

    /** "R3 2:27" (the round alone when the clock is unknown); "" before a result. */
    static String roundClock(SportEvent.Bout b) {
        int r = UfcFeed.shownRound(b);  // a live bout's only while UfcFeed.LIVE_ROUND_TRUSTED
        if (r <= 0) return "";
        String c = UfcFeed.shownClock(b);
        return "R" + r + (c.isEmpty() ? "" : " " + c);
    }

    /** "Round 3 · 2:27". */
    static String roundLine(SportEvent.Bout b) {
        int r = UfcFeed.shownRound(b);
        if (r <= 0) return "";
        String c = UfcFeed.shownClock(b);
        return f("round", r) + (c.isEmpty() ? "" : " · " + c);
    }

    /** The bout's winner, or null (none yet, a draw, a no contest). */
    static SportEvent.Fighter winner(SportEvent.Bout b) {
        if (b == null) return null;
        return b.outcome == 1 ? b.a : b.outcome == 2 ? b.b : null;
    }

    /** "Gane wins" / "Νίκη Gane"; "Draw", "No contest"; "" before a result. */
    static String wins(SportEvent.Bout b) {
        SportEvent.Fighter w = winner(b);
        if (w != null) return f("wins", surname(w));
        if (b != null && b.outcome == 3) return t("draw");
        if (b != null && b.outcome == 4) return t("nc");
        return "";
    }

    /** F1: the session's winner (position 1), or null. */
    static SportEvent.Entrant winner(SportEvent.Session s) {
        if (s == null) return null;
        for (SportEvent.Entrant x : s.order) if (x.pos == 1 || x.winner) return x;
        return s.order.isEmpty() ? null : s.order.get(0);
    }

    /** "Norris wins" (the winner's surname from his name, else his code). */
    static String wins(SportEvent.Session s) {
        SportEvent.Entrant w = winner(s);
        if (w == null) return "";
        return f("wins", driverSurname(w));
    }

    static String driverSurname(SportEvent.Entrant x) {
        if (x == null) return "";
        String n = x.name == null ? "" : x.name.trim();
        int sp = n.lastIndexOf(' ');
        if (sp > 0) return n.substring(sp + 1);
        return n.isEmpty() ? x.code : n;
    }

    /** A session's state word when it isn't simply to come: Cancelled, Postponed, Delayed, Red flag; "" otherwise. */
    static String detail(SportEvent.Session s) {
        if (s == null || s.detail == null) return "";
        switch (s.detail) {
            case "CANCELLED": return t("cancelled");
            case "POSTPONED": return t("postponed");
            case "DELAYED": return t("delayed");
            case "RED_FLAG": case "SUSPENDED": return t("red_flag");
            case "AWAITING": return t("awaiting");
            default: return "off".equals(s.state) ? t("cancelled") : "";
        }
    }

    // ---------- times ----------
    /** "Sunday" / "Κυριακή". */
    static String weekdayLong(long t) { return new SimpleDateFormat("EEEE", I18n.locale()).format(new java.util.Date(t)); }

    /** The relative part without the day: "in 10 days", "tomorrow", "today", "in 2 h 15 min". */
    static String soon(long now, long start) {
        int d = AppText.days(now, start);
        if (d == 0) return start > now ? AppText.duration(start - now) : t("today_l");
        if (d == 1) return t("tomorrow_l");
        if (d > 1) return f("in_days", d);
        return AppText.shortDate(start);
    }

    /** The hero's relative line (§7): "Sunday · in 10 days", "Tomorrow", "Today · in 2 h 15 min". */
    static String relative(long now, long start) {
        int d = AppText.days(now, start);
        if (d <= 1) return AppText.relative(now, start);
        return weekdayLong(start) + " · " + f("in_days", d);
    }

    /** The Club hero's (§9.1): the session leads, "Qualifying · in 9 days", "Race · today · in 2 h". */
    static String sessionRelative(String lead, long now, long start) {
        int d = AppText.days(now, start);
        String when = d == 0 && start > now ? t("today_l") + " · " + f("in_dur", AppText.duration(start - now)) : soon(now, start);
        return lead.isEmpty() ? cap(when) : lead + " · " + when;
    }

    /** "Race · Sunday 22 November". */
    static String whenLine(String lead, long start) {
        String d = AppText.longDate(start);
        return lead.isEmpty() ? d : lead + " · " + d;
    }

    /** The rail's next start, as football's (AppText.railNext): "Sat 06:00", "Today 21:15", a week away the date. */
    static String railNext(long now, long start, boolean tbd) {
        int d = AppText.days(now, start);
        if (d >= 7 || d < 0) return AppText.shortDate(start);
        String day = d == 0 ? AppText.t("today") : d == 1 && I18n.el() ? AppText.t("tomorrow") : AppText.weekday(start);
        return tbd ? day : day + " " + I18n.time(start);
    }

    /** The viewer's zone as the session list labels it: "Your time (Athens)" / "Ώρα Ελλάδας". */
    static String zone() {
        String id = TimeZone.getDefault().getID();
        if (I18n.el()) return "Europe/Athens".equals(id) ? t("greek_time") : t("local_time");
        int slash = id.lastIndexOf('/');
        String city = slash >= 0 ? id.substring(slash + 1).replace('_', ' ') : "";
        return city.isEmpty() || id.startsWith("GMT") || id.startsWith("UTC") ? t("local_time") : f("your_time", city);
    }

    /** "SESSIONS · YOUR TIME (ATHENS)". */
    static String sessionsLabel() { return t("sessions") + " · " + zone(); }

    /** The track's own clock, when it differs from the viewer's day or hour ("Sat 21 Nov, 20:00"); "" otherwise. */
    static String trackTime(long start, String tzId) {
        if (tzId == null || tzId.isEmpty()) return "";
        TimeZone tz = TimeZone.getTimeZone(tzId), mine = TimeZone.getDefault();
        if (tz.getOffset(start) == mine.getOffset(start)) return "";
        SimpleDateFormat f = new SimpleDateFormat(I18n.el() ? "EEE d MMM, HH:mm" : I18n.clock24() ? "EEE d MMM, HH:mm" : "EEE d MMM, h:mm a", I18n.locale());
        f.setTimeZone(tz);
        return f("at_track", f.format(new java.util.Date(start)));
    }

    /** The year of a time, in the viewer's zone. */
    static int year(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.YEAR);
    }

    // ---------- the screen reader ----------
    /** One sentence for a session or a UFC card (rows, the hero). */
    static String speak(SportEvent e, SportEvent.Session s, long now, boolean hidden) {
        if (e == null) return "";
        StringBuilder b = new StringBuilder(SportEvent.UFC.equals(e.sport) ? ufcEvent(e) : title(e));
        if (s != null) b.append(". ").append(session(s));
        if (s == null) return b.toString();
        if ("in".equals(s.state)) b.append(". ").append(I18n.t("live"));
        else if ("post".equals(s.state)) {
            b.append(". ").append(t("final"));
            if (!hidden) {
                String w = SportEvent.F1.equals(e.sport) ? wins(s) : wins(headline(e, s, null));
                if (!w.isEmpty()) b.append(". ").append(w);
            }
        } else if ("off".equals(s.state)) b.append(". ").append(detail(s));
        else b.append(". ").append(AppText.relative(now, s.start)).append(", ").append(AppText.longDate(s.start))
            .append(s.tbd ? "" : ", " + I18n.time(s.start));
        return b.toString();
    }

    /** UFC: the bout on stage: while the card is live the bout in the cage (else the next to fight), otherwise headline(). */
    static SportEvent.Bout staged(SportEvent e, SportEvent.Session s, SportPrefs p) {
        if (e != null && ("in".equals(e.state) || e.live() != null)) {
            SportEvent.Bout b = SportModel.inCage(e);
            if (b == null) b = SportModel.nextBout(e);
            if (b != null) return b;
        }
        return headline(e, s, p);
    }

    /** UFC: the bout a segment is about: a followed fighter's, else the segment's top bout (the main event on the main card). */
    static SportEvent.Bout headline(SportEvent e, SportEvent.Session s, SportPrefs p) {
        if (e == null) return null;
        if (p != null) for (String f : p.fighters) {
            SportEvent.Bout b = e.boutOf(f);
            if (b != null && (s == null || s.bouts.contains(b))) return b;
        }
        if (s == null || "MAIN".equals(s.kind)) {
            SportEvent.Bout m = e.mainEvent();
            if (m != null) return m;
        }
        SportEvent.Bout best = null;
        if (s != null) for (SportEvent.Bout b : s.bouts) if (best == null || b.order < best.order) best = b;
        return best;
    }
}
