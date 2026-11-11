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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Debug builds only. Runs the full data pipeline for every club of the given leagues (followed or not, nothing is
 * saved), as a viewer in the given country would see it, and checks each match for problems a user would see.
 * Writes files/audit.txt (Greece, Super League: the original check) or files/audit_<country>.txt:
 *   OK/WARN/FAIL lines per club, then every match as the app would show it, in English and Greek, then how many
 *   upcoming matches have a channel, per competition.
 */
final class Audit {
    private static final String GREEK = ".*[\\u0370-\\u03FF\\u1F00-\\u1FFF].*";

    /** The original audit: every Super League club, in Greece. */
    static void run(Context c) throws Exception { run(c, "GR", java.util.Arrays.asList("gre.1"), 0, "audit.txt"); }

    /** max: clubs per league (0 = all). */
    static void run(Context c, String country, List<String> leagues, int max) throws Exception {
        run(c, country, leagues, max, "audit_" + country + ".txt");
    }

    private static void run(Context c, String country, List<String> leagues, int max, String file) throws Exception {
        File out = new File(c.getFilesDir(), file);
        File tmp = new File(c.getFilesDir(), file + ".part");
        try (PrintStream o = new PrintStream(new FileOutputStream(tmp), true, "UTF-8")) {
            long now = System.currentTimeMillis();
            I18n.load(c);
            Where.load(c);
            Where.use(country);  // this thread only: the viewer's setting is not touched
            Teams.refresh(c);
            List<Watch> watch = Where.providers(c, country);
            for (Watch w : watch) w.prepare(c);
            GreeceProvider greece = Where.greece(watch);
            o.println("country=" + country + " providers=" + Where.chain(c, country) + " leagues=" + leagues
                + "  guides: sport24 airings=" + (greece == null ? "-" : String.valueOf(greece.sport24().size()))
                + "  build: guides=" + BuildConfig.GUIDES + " images=" + BuildConfig.REMOTE_IMAGES);
            int fails = 0, warns = 0;
            Map<String, int[]> perComp = new TreeMap<>();  // competition -> {upcoming, with a channel}
            Set<String> done = new HashSet<>();
            for (String lg : leagues) {
                if (!Teams.refreshLeague(c, lg, false)) { o.println("FAIL league " + lg + ": no club list"); fails++; continue; }
                List<Teams.Club> clubs = "gre.1".equals(lg) ? Teams.greek(c) : Teams.league(c, lg);
                if (clubs == null) continue;
                o.println("== " + lg + " (" + I18n.comp(lg) + "): " + clubs.size() + " clubs");
                int n = 0;
                for (Teams.Club k : clubs) {
                    if (max > 0 && n >= max) break;
                    if (!done.add(k.id)) continue;
                    n++;
                    int[] fw = audit(c, o, k, country, watch, greece, now, perComp);
                    fails += fw[0]; warns += fw[1];
                }
            }
            o.println("== upcoming matches (14 days) with a channel, by competition:");
            for (Map.Entry<String, int[]> e : perComp.entrySet())
                o.println(String.format("    %-24s %3d / %3d", e.getKey(), e.getValue()[1], e.getValue()[0]));
            o.println("DONE fails=" + fails + " warnings=" + warns);
        }
        if (!tmp.renameTo(out)) throw new Exception("rename");
    }

    private static int[] audit(Context c, PrintStream o, Teams.Club k, String country, List<Watch> watch, GreeceProvider greece,
                               long now, Map<String, int[]> perComp) {
        List<String> problems = new ArrayList<>();
        List<Match> all = new ArrayList<>();
        boolean anyOk = false;
        boolean gr = Where.greek(country);
        try {
            Teams.resolveEuro(c, k);
            Teams.lookupBasket(c, k);
            Teams.Club kk = Teams.find(c, k.id);
            if (kk != null) k = kk;
            for (Sources.Result r : Sources.fetch(c, k, true)) {
                all.addAll(r.list);
                anyOk |= r.ok;
                o.println("  source " + r.source + " ok=" + r.ok + " matches=" + r.list.size());
            }
            if (greece != null) { List<Match> found = greece.discover(all, k, true); if (found != null) all.addAll(found); }
            for (Watch w : watch) w.apply(all, k, w.fetch(c, k, all, true));
            Engine.shareCrests(all);
            Crests.resolve(c, all);
            Crests.fill(c, all);
            Engine.timeStates(all, now);
        } catch (Throwable t) {
            problems.add("FAIL pipeline threw " + t);
        }
        if (!anyOk) problems.add("FAIL no source answered");
        Collections.sort(all, new Comparator<Match>() { public int compare(Match a, Match b) { return Long.compare(a.start, b.start); } });

        boolean next = false;
        Set<String> seen = new HashSet<>();
        for (Match m : all) {
            String id = m.team + " " + m.home + " v " + m.away + " @" + m.start;
            if (m.home == null || m.home.trim().isEmpty() || m.away == null || m.away.trim().isEmpty()) problems.add("FAIL empty team name: " + id);
            if (m.start <= 0) problems.add("FAIL no start time: " + id);
            if (m.comp == null || m.comp.isEmpty()) problems.add("WARN no competition: " + id);
            else if (!I18n.has(m.comp)) problems.add("WARN competition not translated: '" + m.comp + "' in " + id);
            boolean soon = "pre".equals(m.state) && m.start > now && m.start < now + 14L * 86400000L;
            if (k.mainTeam().equals(m.team) && "pre".equals(m.state) && m.start > now && m.start < now + 30L * 86400000L) next = true;
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
            if (k.greek() && !el.matches(GREEK)) problems.add("WARN no Greek name in Greek: '" + el + "'");
            // upcoming games should have a channel: in Greece the Super League and cup always; elsewhere what the
            // season table covers (the rest is counted below)
            if (gr && "FC".equals(m.team) && soon && ("gre.1".equals(m.league) || "gre.cup".equals(m.comp)) && !m.channelKnown)
                problems.add("WARN no channel: " + id);
            if (soon) {
                int[] pc = perComp.get(m.league == null ? "?" : m.league);
                if (pc == null) perComp.put(m.league == null ? "?" : m.league, pc = new int[2]);
                pc[0]++;
                if (m.channelKnown) pc[1]++;
            }
            // the same game twice
            String dup = m.team + "|" + Badges.key(m.home) + "|" + Badges.key(m.away) + "|" + (m.start / (6 * 3600000L));
            if (!seen.add(dup)) problems.add("FAIL duplicate: " + id + " (" + m.source + ")");
            // pictures: both crests within a month, and a logo for every known Greek channel of an upcoming match
            if (Math.abs(m.start - now) < 30L * 86400000L) {
                if (m.homeCrest == null || m.homeCrest.isEmpty()) problems.add("WARN no crest: " + m.home + " in " + id);
                if (m.awayCrest == null || m.awayCrest.isEmpty()) problems.add("WARN no crest: " + m.away + " in " + id);
            }
            if (soon && m.channelKnown && !Where.plain(m) && Channels.logos(c, m).isEmpty())
                problems.add("WARN no channel logo for '" + m.channel + "': " + id);
            // finished games need a score, live ones a state
            if ("post".equals(m.state) && !m.hasScore() && !"tsdb".equals(m.source)) problems.add("WARN final without score: " + id);
            if (m.start > now + 2 * 3600000L && ("post".equals(m.state) || "in".equals(m.state))) problems.add("FAIL future match marked " + m.state + ": " + id);
        }
        if (!next) problems.add("WARN no " + ("BC".equals(k.mainTeam()) ? "basketball" : "football") + " match in the next 30 days");
        int fails = 0, warns = 0;
        for (String p : problems) { if (p.startsWith("FAIL")) fails++; else warns++; }
        o.println((problems.isEmpty() ? "OK   " : problems.toString().contains("FAIL") ? "FAIL " : "WARN ") + k.id + " " + k.name + " / " + k.gr
            + "  matches=" + all.size() + " basket=" + k.hasBasket + (k.bcComp != null ? " " + k.bcComp : ""));
        for (String p : problems) o.println("    " + p);
        for (Match m : all) {
            I18n.use("en");
            String e = String.format("    %-2s %-5s %s | %s | %s | %s | %s", m.team, m.state, I18n.day(m.start) + " " + I18n.time(m.start),
                m.scoreLine(), I18n.comp(m.comp), m.state.equals("pre") ? Cards.where(m) : I18n.detail(m.detail), m.source);
            I18n.use("el");
            o.println(e + "  ||  " + m.scoreLine() + " | " + I18n.comp(m.comp) + (m.state.equals("pre") ? " | " + Cards.where(m) : ""));
        }
        o.flush();
        return new int[]{fails, warns};
    }
}
