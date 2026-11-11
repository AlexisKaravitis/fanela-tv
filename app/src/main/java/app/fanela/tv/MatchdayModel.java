package app.fanela.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the Matchday layout lists (SPEC-APP 4.2), chosen once per load from the HomeModel: row 1 "Next & latest"
 * across every followed club (live first, then each club's next match, then the results of the last days, filled to a
 * row with what comes after), then one row per club: its latest results (left of today), the next match, the later
 * ones. The row a card is in and the card the row opens on are decided here; the stage always shows the focused card.
 * Plain Java on Match, unit-tested.
 *
 * Row 1 keeps a club's latest result for a week (the references list a four-day-old derby next to Tuesday's game;
 * SPEC-APP's "48 h" would drop it), and a club playing now is represented by its live card alone.
 */
final class MatchdayModel {
    private MatchdayModel() { }

    /** Results in row 1: each club's latest, for a week (last weekend's derby is still news on Thursday). */
    static final long RECENT = 7 * 86400000L;
    /** Per club row: up to three results before today, and eight matches from the next one on. */
    static final int RESULTS = 3, COMING = 8;
    /** Row 1 is filled with what comes after the next matches until it holds a row of cards. */
    static final int ROW1 = 5;
    static final String FIRST = "next";

    /** One card: a club's match, seen from that club's side. */
    static final class Item {
        final HomeModel.ClubData club;
        final Match m;
        /** The club's next match (the stage says "Up next"; later ones say "Later"). */
        boolean next;

        Item(HomeModel.ClubData club, Match m) { this.club = club; this.m = m; }

        /** Stable across loads: the club and the match. */
        String key() { return club.club.id + "|" + m.key; }
        boolean result() { return "post".equals(m.state); }
        boolean live() { return "in".equals(m.state); }
        boolean off() { return "off".equals(m.state); }
        HomeModel.Kind kind() { return HomeModel.kind(m); }
    }

    /** A row of cards: row 1 (club null) or one club's. */
    static final class Row {
        /** "next" for row 1, else the club's id. */
        final String id;
        final HomeModel.ClubData club;
        final List<Item> items = new ArrayList<>();
        /** The card the row opens on: the next match (results stay to its left). */
        int start;
        /** Still loading: grey card slots. */
        boolean loading;

        Row(String id, HomeModel.ClubData club) { this.id = id; this.club = club; }

        int indexOf(String itemKey) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(itemKey)) return i;
            return -1;
        }

        /** Everything that changes the row's cards (a change rebuilds them; the same keys update in place). */
        String structure() {
            StringBuilder b = new StringBuilder(id).append(loading);
            for (Item it : items) b.append(',').append(it.key());
            return b.toString();
        }
    }

    /** Every row, row 1 first (none without clubs). */
    static List<Row> rows(HomeModel h) {
        List<Row> out = new ArrayList<>();
        if (h.empty()) return out;
        List<Row> clubs = new ArrayList<>();
        for (HomeModel.ClubData d : h.clubs) clubs.add(club(d, h.all, h.teams(d), h.now));
        out.add(first(h, clubs));
        out.addAll(clubs);
        return out;
    }

    /** One club's row: its latest results (oldest first), then the live or next match, then the later ones. */
    static Row club(HomeModel.ClubData d, List<Match> all, String[] teams, long now) {
        Row r = new Row(d.club.id, d);
        if (d.kind == HomeModel.Kind.LOADING) { r.loading = true; return r; }
        List<Match> results = new ArrayList<>();
        for (Match m : all) if (HomeModel.of(m, d.club.id, teams) && "post".equals(m.state) && m.hasScore() && m.start <= now) results.add(m);
        Collections.sort(results, NEWEST);
        if (results.size() > RESULTS) results = new ArrayList<>(results.subList(0, RESULTS));
        Collections.reverse(results);
        for (Match m : results) r.items.add(new Item(d, m));
        List<Match> coming = coming(d);
        if (coming.size() > COMING) coming = new ArrayList<>(coming.subList(0, COMING));
        boolean first = true;
        for (Match m : coming) {
            Item it = new Item(d, m);
            it.next = first && !"in".equals(m.state);
            if (!"in".equals(m.state)) first = false;
            r.items.add(it);
        }
        r.start = coming.isEmpty() ? Math.max(0, r.items.size() - 1) : results.size();
        return r;
    }

    /** The club's live match and coming matches (postponed ones keep their place), live first, then by kick-off. */
    static List<Match> coming(HomeModel.ClubData d) {
        List<Match> out = new ArrayList<>();
        if (d.hero != null && !"post".equals(d.hero.state)) out.add(d.hero);
        for (Match m : d.fixtures) if (!contains(out, m)) out.add(m);
        Collections.sort(out, LIVE_FIRST);
        return out;
    }

    /**
     * Row 1, "Next & latest": live matches first, each club's next match by kick-off (a club playing now: its live
     * card only), then each club's latest result of the last week (newest first), then what comes after, soonest
     * first, until the row is full. A match between two followed clubs is listed once (the favourite's).
     */
    static Row first(HomeModel h, List<Row> clubRows) {
        Row r = new Row(FIRST, null);
        List<HomeModel.ClubData> order = new ArrayList<>(h.clubs);
        for (HomeModel.ClubData d : h.clubs) if (d.club.id.equals(h.favourite)) { order.remove(d); order.add(0, d); }
        for (HomeModel.ClubData d : order) if (d.kind == HomeModel.Kind.LOADING) { r.loading = true; return r; }
        List<Item> live = new ArrayList<>(), next = new ArrayList<>(), latest = new ArrayList<>(), rest = new ArrayList<>();
        for (HomeModel.ClubData d : order) {
            Row cr = rowOf(clubRows, d.club.id);
            if (cr == null) continue;
            Item newest = null;
            boolean nextTaken = false;
            for (Item it : cr.items) {  // results (oldest first), then live, then the coming ones
                if (it.result()) { newest = it; continue; }
                if (it.live()) { live.add(it); nextTaken = true; }
                else if (!nextTaken) { next.add(it); nextTaken = true; }
                else rest.add(it);
            }
            if (newest != null && h.now - newest.m.start <= RECENT) latest.add(newest);
        }
        Collections.sort(live, ITEM_KICKOFF);
        Collections.sort(next, ITEM_KICKOFF);
        Collections.sort(latest, Collections.reverseOrder(ITEM_KICKOFF));
        Collections.sort(rest, ITEM_KICKOFF);
        for (List<Item> group : java.util.Arrays.asList(live, next, latest)) for (Item it : group) add(r.items, it);
        for (Item it : rest) { if (r.items.size() >= ROW1) break; add(r.items, it); }
        r.start = 0;
        return r;
    }

    private static Row rowOf(List<Row> rows, String id) {
        for (Row r : rows) if (r.id.equals(id)) return r;
        return null;
    }

    private static void add(List<Item> items, Item it) {
        for (Item x : items) if (x.key().equals(it.key()) || same(x.m, it.m)) return;
        items.add(it);
    }

    private static boolean contains(List<Match> l, Match m) {
        for (Match x : l) if (x.key.equals(m.key)) return true;
        return false;
    }

    /** The same fixture listed for two followed clubs (a derby): same sport, same kick-off, same teams. */
    static boolean same(Match a, Match b) {
        if (a.club != null && a.club.equals(b.club)) return false;
        if (!a.team.equals(b.team) || Math.abs(a.start - b.start) > 3 * 3600000L) return false;
        boolean names = Cards.key(a.home).equals(Cards.key(b.home)) && Cards.key(a.away).equals(Cards.key(b.away));
        boolean crests = a.homeCrest != null && a.homeCrest.equals(b.homeCrest) && a.awayCrest != null && a.awayCrest.equals(b.awayCrest);
        return names || crests;
    }

    static final Comparator<Match> NEWEST = new Comparator<Match>() {
        public int compare(Match a, Match b) { return Long.compare(b.start, a.start); }
    };
    static final Comparator<Match> LIVE_FIRST = new Comparator<Match>() {
        public int compare(Match a, Match b) {
            boolean la = "in".equals(a.state), lb = "in".equals(b.state);
            if (la != lb) return la ? -1 : 1;
            return Long.compare(a.start, b.start);
        }
    };
    static final Comparator<Item> ITEM_KICKOFF = new Comparator<Item>() {
        public int compare(Item a, Item b) { return Long.compare(a.m.start, b.m.start); }
    };

    // ---------- focus ----------
    /** Where focus lands at launch: the first card of row 1 (the live card when there is one). */
    static int[] launch(List<Row> rows) {
        for (int r = 0; r < rows.size(); r++) if (!rows.get(r).items.isEmpty()) return new int[]{r, rows.get(r).start};
        return null;
    }

    /** The row and card of an item key ("<row>/<club>|<match>"), or the nearest sensible card; null without cards. */
    static int[] find(List<Row> rows, String rowId, String itemKey) {
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (!row.id.equals(rowId)) continue;
            int i = row.indexOf(itemKey);
            if (i >= 0) return new int[]{r, i};
            if (!row.items.isEmpty()) return new int[]{r, row.start};
        }
        for (int r = 0; r < rows.size(); r++) {  // the row is gone (a club unfollowed): the item in another row
            int i = rows.get(r).indexOf(itemKey);
            if (i >= 0) return new int[]{r, i};
        }
        return launch(rows);
    }

    // ---------- the stage's words ----------
    /** The eyebrow's lead word: "Up next", "Later", "Full time", "Postponed"; "" live (the LIVE pill leads). */
    static String lead(Item it) {
        if (it.live()) return "";
        if (it.result()) return AppText.t("full_time");
        if (it.off()) return I18n.detail(it.m.detail);
        return AppText.t(it.next ? "up_next" : "later");
    }

    /** The eyebrow after the lead: "EuroLeague", plus the sport where the club plays both, a result's date first. */
    static List<String> eyebrow(Item it) {
        List<String> out = new ArrayList<>();
        if (it.result()) out.add(AppText.shortDate(it.m.start));
        out.add(I18n.comp(it.m.comp));
        return out;
    }

    /** The live line beside the pill: "Q3 · 5:12", "OT · 2:10" / "ΠΑΡ. · 2:10", "67′", "HT" / "ΗΜ.". */
    static String liveClock(Match m) {
        String d = m.detail == null ? "" : m.detail.trim();
        if (d.matches("(Q\\d|OT\\d*)\\s+\\d{1,2}:\\d{2}")) {
            String[] p = d.split("\\s+");
            String c = p[1].startsWith("0") && p[1].length() > 4 ? p[1].substring(1) : p[1];
            return HomeModel.periodWord(p[0]) + " · " + c;
        }
        return HomeModel.phase(m);
    }

    /** The trailing side of a live or finished match (its name and score at 60 %): 1 home, -1 away, 0 level. */
    static int leader(Match m) {
        if (!m.hasScore()) return 0;
        return Integer.compare(Match.toInt(m.hs), Match.toInt(m.as));
    }
}
