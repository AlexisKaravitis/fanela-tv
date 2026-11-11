package app.fanela.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the match screen says beyond the match itself (SPEC-APP 3.5, 4.4), from the season's saved matches: the last
 * five results in the same competition, the other leg against the same opponent, which button it offers (open the
 * channel's app, get it, or close) and the sentences under the where-to-watch cell. Plain Java, unit-tested.
 */
final class MatchFacts {
    private MatchFacts() { }

    enum Action { OPEN, GET, CLOSE }

    /** A form cell: the result and its outcome letter (W / D / L, Greek Ν / Ι / Η). */
    static final class Form {
        final Match m;
        final int outcome;
        Form(Match m) { this.m = m; this.outcome = m.result(); }
        String letter() {
            if (I18n.el()) return outcome > 0 ? "Ν" : outcome < 0 ? "Η" : "Ι";
            return outcome > 0 ? "W" : outcome < 0 ? "L" : "D";
        }
    }

    /**
     * The form guide as a screen reader says it: each result in words from the club's side with its opponent,
     * "FC Barcelona, Won 105–100; Fenerbahce, Lost 61–64" (never a bare W / L letter, which is read as a letter name).
     */
    static String speak(List<Form> cells) {
        StringBuilder b = new StringBuilder();
        for (Form f : cells) {
            if (b.length() > 0) b.append("; ");
            String score = HomeModel.mineFirst(f.m);
            String words = f.outcome == 9 ? score : AppText.f(f.outcome > 0 ? "won" : f.outcome < 0 ? "lost" : "drew", score);
            b.append(I18n.team(f.m.opponentRaw())).append(", ").append(words);
        }
        return b.toString();
    }

    /** True when the five come from the match's own competition (else from any of the sport's, labelled "Recent"). */
    static final class Recent {
        final List<Form> cells = new ArrayList<>();
        boolean sameCompetition;
    }

    static boolean sameComp(Match a, Match b) {
        String x = a.comp == null ? "" : a.comp, y = b.comp == null ? "" : b.comp;
        return x.equals(y) || (a.league != null && !a.league.isEmpty() && a.league.equals(b.league));
    }

    /**
     * The club's last five results before the match (oldest first), in the same competition; when it has none yet
     * (the first round), the sport's last five in any competition.
     */
    static Recent recent(List<Match> all, Match m) {
        Recent r = new Recent();
        List<Match> same = new ArrayList<>(), any = new ArrayList<>();
        for (Match x : all) {
            if (x == m || x.key.equals(m.key) || !m.club.equals(x.club) || !m.team.equals(x.team)) continue;
            if (!"post".equals(x.state) || !x.hasScore() || x.start >= m.start) continue;
            any.add(x);
            if (sameComp(x, m)) same.add(x);
        }
        r.sameCompetition = !same.isEmpty();
        List<Match> pick = r.sameCompetition ? same : any;
        Collections.sort(pick, MatchdayModel.NEWEST);
        if (pick.size() > 5) pick = new ArrayList<>(pick.subList(0, 5));
        Collections.reverse(pick);
        for (Match x : pick) r.cells.add(new Form(x));
        return r;
    }

    /**
     * The first form cell to draw when the row has room for only some of them: widths are the cells' own (oldest
     * first, as recent() orders them), gap the space between two cells. Counts back from the newest, so the cells
     * left out are the oldest and the latest result is always shown. Returns widths.length when not even the newest
     * fits.
     */
    static int firstShown(float[] widths, float gap, float room) {
        int first = widths.length;
        float used = 0;
        for (int i = widths.length - 1; i >= 0; i--) {
            float next = used + (first == widths.length ? 0 : gap) + widths[i];
            if (next > room) break;
            used = next;
            first = i;
        }
        return first;
    }

    /** The two matches are against the same opponent (by name, or by crest when the feeds spell it differently). */
    static boolean sameOpponent(Match a, Match b) {
        if (Cards.key(a.opponentRaw()).equals(Cards.key(b.opponentRaw()))) return true;
        String ca = a.clubHome() ? a.awayCrest : a.homeCrest, cb = b.clubHome() ? b.awayCrest : b.homeCrest;
        return ca != null && !ca.isEmpty() && ca.equals(cb);
    }

    /**
     * The other leg of the tie: the same competition and opponent, the other ground, within the season (300 days).
     * The nearest one when there are several (a cup tie and a league game).
     */
    static Match otherLeg(List<Match> all, Match m) {
        Match best = null;
        for (Match x : all) {
            if (x.key.equals(m.key) || !m.club.equals(x.club) || !m.team.equals(x.team) || !sameComp(x, m)) continue;
            if (x.clubHome() == m.clubHome() || !sameOpponent(x, m) || Math.abs(x.start - m.start) > 300 * 86400000L) continue;
            if (best == null || Math.abs(x.start - m.start) < Math.abs(best.start - m.start)) best = x;
        }
        return best;
    }

    /** "Fri 5 Mar · 18:00 · at Dubai" (a leg to come) or "Thu 16 Oct · 100–105 · at Barcelona" (one played). */
    static String legLine(Match leg) {
        String opp = I18n.team(leg.opponentRaw());
        String when = AppText.shortDate(leg.start);
        String mid = "post".equals(leg.state) && leg.hasScore() ? leg.hs + "–" + leg.as : leg.tbd ? AppText.t("time_tba") : I18n.time(leg.start);
        return when + " · " + mid + " · " + AppText.venue(leg.clubHome(), opp);
    }

    /** The label over legLine: "Return leg" when it is still to come, "First leg" once played. */
    static String legLabel(Match leg, Match m) {
        return AppText.t(leg.start > m.start ? "return_leg" : "first_leg");
    }

    /** The match is still to be watched (live or to come): where to watch and the channel's app matter. */
    static boolean watchable(Match m) { return "pre".equals(m.state) || "in".equals(m.state); }

    /**
     * The button: open the channel's app when it is on the TV, get it from the store when not, close when there is no
     * app to offer (abroad, no channel known, a match already played).
     */
    static Action action(Match m, boolean installed) {
        if (!watchable(m) || !m.channelKnown || m.channelApp == null || m.channelApp.isEmpty() || Where.plain(m)) return Action.CLOSE;
        return installed ? Action.OPEN : Action.GET;
    }

    static String buttonLabel(Action a, Match m) {
        switch (a) {
            case OPEN: return I18n.f("open_app", Cards.appName(m.channelApp));
            case GET: return I18n.f("get_app", Cards.appName(m.channelApp));
            default: return I18n.t("close");
        }
    }

    /**
     * The sentences under the cell: why the channel is what it says (a guide listing, the home team's rights, the
     * competition's season list, nothing known abroad), then whether the channel's app is on this TV.
     */
    static List<String> why(Match m, boolean installed) {
        List<String> out = new ArrayList<>();
        if (!watchable(m)) return out;
        boolean plain = Where.plain(m);
        if (!m.channelKnown) {
            if (Where.noData(m)) out.add(I18n.t("no_table_note"));
            return out;
        }
        if ("tv".equals(m.listedBy)) out.add(AppText.t("tv_why"));
        else if (plain) { if (!m.channelSure) out.add(I18n.t("table_note")); }
        else if (m.channelSure) out.add(AppText.f("sure_why", Cards.appName(m.channelApp)));
        else out.add(I18n.t("channel_note"));
        if (!plain && m.channelApp != null && !m.channelApp.isEmpty())
            out.add(installed ? AppText.f("installed", Cards.appName(m.channelApp)) : I18n.f("app_missing", Cards.appName(m.channelApp)));
        return out;
    }

    /** The bold line of the where column: "Novasports Prime · Magenta TV" (abroad: the channel names alone). */
    static String channelLine(Match m) {
        if (!m.channelKnown) return "";
        List<String> ch = Cards.channels(m);
        String names = CardKit.join(ch, " · ");
        if (Where.plain(m) || m.channelApp == null) return names;
        return names.isEmpty() ? Cards.appName(m.channelApp) : names + " · " + Cards.appName(m.channelApp);
    }
}
