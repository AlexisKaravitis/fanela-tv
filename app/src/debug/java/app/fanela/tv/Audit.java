package app.fanela.tv;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Debug builds only. Runs the full data pipeline for every club in the league (followed or not, nothing is saved)
 * and checks each match for problems a user would see. Writes files/audit.txt:
 *   OK/WARN/FAIL lines per club, then every match as the app would show it, in English and Greek.
 */
final class Audit {
    private static final String GREEK = ".*[\\u0370-\\u03FF\\u1F00-\\u1FFF].*";

    static void run(Context c) throws Exception {
        File out = new File(c.getFilesDir(), "audit.txt");
        try (PrintStream o = new PrintStream(new FileOutputStream(out), true, "UTF-8")) {
            long now = System.currentTimeMillis();
            Teams.refresh(c);
            Channels.refresh(c);
            Channels.load(c);
            List<Guide.Airing> s24 = Guide.sport24(c);
            o.println("guides: sport24 airings=" + s24.size() + "  build: guides=" + BuildConfig.GUIDES + " images=" + BuildConfig.REMOTE_IMAGES);
            int fails = 0, warns = 0;
            for (Teams.Club k : Teams.all(c)) {
                List<String> problems = new ArrayList<>();
                List<Match> all = new ArrayList<>();
                boolean anyOk = false;
                try {
                    Teams.resolveEuro(c, k);
                    Teams.lookupBasket(c, k);
                    Teams.Club kk = Teams.get(c, k.id);
                    if (kk != null) k = kk;
                    for (Sources.Result r : Sources.fetch(c, k, true)) {
                        all.addAll(r.list);
                        anyOk |= r.ok;
                        o.println("  source " + r.source + " ok=" + r.ok + " matches=" + r.list.size());
                    }
                    all.addAll(Guide.discover(all, s24, k, true));
                    Guide.apply(all, k, Guide.magenta(c, k, all), s24, Sofa.airings(c, k, all, true));
                    Engine.shareCrests(all);
                    Crests.resolve(c, all);
                    Crests.fill(c, all);
                    Engine.timeStates(all, now);
                } catch (Throwable t) {
                    problems.add("FAIL pipeline threw " + t);
                }
                if (!anyOk) problems.add("FAIL no source answered");
                Collections.sort(all, new Comparator<Match>() { public int compare(Match a, Match b) { return Long.compare(a.start, b.start); } });

                boolean nextFc = false;
                Set<String> seen = new HashSet<>();
                for (Match m : all) {
                    String id = m.team + " " + m.home + " v " + m.away + " @" + m.start;
                    if (m.home == null || m.home.trim().isEmpty() || m.away == null || m.away.trim().isEmpty()) problems.add("FAIL empty team name: " + id);
                    if (m.start <= 0) problems.add("FAIL no start time: " + id);
                    if (m.comp == null || m.comp.isEmpty()) problems.add("WARN no competition: " + id);
                    else if (!I18n.has(m.comp)) problems.add("WARN competition not translated: '" + m.comp + "' in " + id);
                    if ("FC".equals(m.team) && "pre".equals(m.state) && m.start > now && m.start < now + 30L * 86400000L) nextFc = true;
                    // the followed club must be on the side the match says it is
                    String mine = Badges.key(m.clubHome() ? m.home : m.away), club = Badges.key(k.name);
                    String first = club.split(" ")[0];
                    if (!mine.startsWith(first) && !mine.contains(first)) problems.add("WARN club side? " + k.name + " not '" + (m.clubHome() ? m.home : m.away) + "': " + id);
                    // English screens must not show Greek text, Greek screens should show Greek names for Greek clubs
                    I18n.use("en");
                    String en = m.title() + " " + I18n.comp(m.comp) + " " + Cards.where(m) + " " + I18n.detail(m.detail);
                    if (en.matches(GREEK)) problems.add("WARN Greek text in English: '" + en + "'");
                    I18n.use("el");
                    String el = m.title();
                    if (!el.matches(GREEK)) problems.add("WARN no Greek name in Greek: '" + el + "'");
                    // upcoming Super League / cup games should always have a channel
                    if ("FC".equals(m.team) && "pre".equals(m.state) && m.start > now && m.start < now + 14L * 86400000L
                        && ("gre.1".equals(m.league) || "gre.cup".equals(m.comp)) && !m.channelKnown) problems.add("WARN no channel: " + id);
                    // the same game twice
                    String dup = m.team + "|" + Badges.key(m.home) + "|" + Badges.key(m.away) + "|" + (m.start / (6 * 3600000L));
                    if (!seen.add(dup)) problems.add("FAIL duplicate: " + id + " (" + m.source + ")");
                    // pictures: both crests within a month, and a logo for every known channel of an upcoming match
                    if (Math.abs(m.start - now) < 30L * 86400000L) {
                        if (m.homeCrest == null || m.homeCrest.isEmpty()) problems.add("WARN no crest: " + m.home + " in " + id);
                        if (m.awayCrest == null || m.awayCrest.isEmpty()) problems.add("WARN no crest: " + m.away + " in " + id);
                    }
                    if ("pre".equals(m.state) && m.start > now && m.start < now + 14L * 86400000L && m.channelKnown
                        && Channels.logos(c, m).isEmpty()) problems.add("WARN no channel logo for '" + m.channel + "': " + id);
                    // finished games need a score, live ones a state
                    if ("post".equals(m.state) && !m.hasScore() && !"tsdb".equals(m.source)) problems.add("WARN final without score: " + id);
                    if (m.start > now + 2 * 3600000L && ("post".equals(m.state) || "in".equals(m.state))) problems.add("FAIL future match marked " + m.state + ": " + id);
                }
                if (!nextFc) problems.add("WARN no football match in the next 30 days");
                for (String p : problems) { if (p.startsWith("FAIL")) fails++; else warns++; }
                o.println((problems.isEmpty() ? "OK   " : problems.toString().contains("FAIL") ? "FAIL " : "WARN ") + k.id + " " + k.name + " / " + k.gr
                    + "  matches=" + all.size() + " basket=" + k.hasBasket + (k.bcComp != null ? " " + k.bcComp : ""));
                for (String p : problems) o.println("    " + p);
                for (Match m : all) {
                    I18n.use("en");
                    String e = String.format("    %-2s %-5s %s | %s | %s | %s | %s", m.team, m.state, I18n.day(m.start) + " " + I18n.time(m.start),
                        m.scoreLine(), I18n.comp(m.comp), m.state.equals("pre") ? Cards.where(m) : I18n.detail(m.detail), m.source);
                    I18n.use("el");
                    o.println(e + "  ||  " + m.scoreLine() + " | " + I18n.comp(m.comp));
                }
                o.flush();
            }
            o.println("DONE fails=" + fails + " warnings=" + warns);
        }
    }
}
