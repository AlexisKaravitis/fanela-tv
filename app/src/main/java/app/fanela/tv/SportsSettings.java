package app.fanela.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Settings › Your clubs and sports, the "More sports" chips (SPEC-F1-UFC §10, F1-UFC-ARCHITECTURE §2.2): choosing the
 * Formula 1 or UFC chip fills the grid, where a league's club tiles go, with the sport's panel.
 *  - Formula 1: the sport tile (474 x 250, OK follows), the 11 team tiles flowing beside it, then "Drivers · your
 *    focus (optional, up to 2)" and the driver tiles, in championship order (F1Feed.standings, colours from
 *    F1Feed.roster). Drivers and teams mix in the focus; a third pick is refused (the tile shakes, the hint says so).
 *  - UFC: the sport tile, the Events option (All / Numbered only, cycling on OK) beside it, then the fighters on the
 *    next cards (UfcFeed.upcomingFighters), followed ones first; a followed fighter with no booked bout stays listed.
 * The focus tiles, the Events option and the fighters stand at 40 % and take no focus until the sport is followed.
 * Every change is saved at once (Sports) and the activity refreshes in the background. The pure rules (the focus
 * limit, the scope cycle, the fighter order, the grid cells) are static and unit-tested.
 */
final class SportsSettings {
    /** The activity's side of the panel: its grid, threads and the page around the grid. UI thread only. */
    interface Host {
        FrameLayout grid();
        Handler ui();
        ExecutorService lists();
        ExecutorService images();
        ExecutorService fetches();
        boolean gone();
        /** The grid still shows this sport's panel. */
        boolean shows(String sport);
        /** The panel's height changed or its items did: lay the page out and list the D-pad's lines again. */
        void laidOut(float gridH);
        /** The hint line under the heading (null: the chip's own hint again). */
        void hint(String text);
        /** A sport was followed or unfollowed: chips, home-screen row, status, refresh (then whenRefreshed, on the UI thread). */
        void followed(String sport, boolean on, Runnable whenRefreshed);
        /** The focus, the scope or a fighter changed: refresh and redraw in the background. */
        void changed();
        /** The grid's skeleton tiles pulse (on) or stop. */
        void skeletons(boolean on);
        /** Gives a view the focus and scrolls the page to it. */
        void focus(View v);
    }

    static final String CHIP = "sport:";
    static final float STEP_X = SportTiles.W + 30, STEP_Y = SportTiles.H + 28;
    static final int COLS = 7;
    /** The Events option beside the UFC tile (x 600-1824 on the screen; the grid starts at 96). */
    static final float SCOPE_X = 504, SCOPE_W = 1224;
    /** Under a row of tiles: the label 22 below it, the next tiles 64 below it (SPEC-F1-UFC §10: label y 660, tiles 684). */
    static final float LABEL_GAP = 22, LABEL_H = 36, BELOW_LABEL = 64;

    // ---------- pure rules ----------
    static boolean isChip(String id) { return id != null && id.startsWith(CHIP) && Sports.known(id.substring(CHIP.length())); }

    static String chipId(String sport) { return CHIP + sport; }

    static String sportOfChip(String id) { return isChip(id) ? id.substring(CHIP.length()) : null; }

    /**
     * The F1 focus after OK on a tile: a picked ref leaves, a new one joins at the end; null when it would be a third
     * (Sports.MAX_FOCUS), or the ref is not a driver or team ref. The input list is never changed.
     */
    static List<String> toggleFocus(List<String> cur, String ref) {
        if (!Sports.validRef(ref)) return null;
        List<String> next = new ArrayList<>();
        if (cur != null) for (String r : cur) if (Sports.validRef(r) && !next.contains(r)) next.add(r);
        if (next.remove(ref)) return next;
        if (next.size() >= Sports.MAX_FOCUS) return null;
        next.add(ref);
        return next;
    }

    /** The Events option cycles All -> Numbered only -> All; anything unknown counts as All. */
    static String nextScope(String cur) { return Sports.SCOPE_NUMBERED.equals(cur) ? Sports.SCOPE_ALL : Sports.SCOPE_NUMBERED; }

    /**
     * The fighter tiles' order: followed fighters first (in the order they were followed), then the next cards' fighters
     * in billing order, each once.
     */
    static List<String> fighterOrder(List<String> upcoming, Collection<String> followed) {
        List<String> out = new ArrayList<>();
        if (followed != null) for (String f : followed) if (f != null && !f.isEmpty() && !out.contains(f)) out.add(f);
        if (upcoming != null) for (String f : upcoming) if (f != null && !f.isEmpty() && !out.contains(f)) out.add(f);
        return out;
    }

    /** The grid cell {row, column} of the i-th team: five beside the two-wide sport tile, then rows of seven. */
    static int[] teamCell(int i) {
        if (i < COLS - 2) return new int[]{0, i + 2};
        int j = i - (COLS - 2);
        return new int[]{1 + j / COLS, j % COLS};
    }

    /** Rows the sport tile and n teams take (at least the sport tile's). */
    static int teamRows(int n) { return n <= 0 ? 1 : teamCell(n - 1)[0] + 1; }

    /**
     * A weight class for a fighter tile, from SportNames' one table: "Women's Bantamweight" -> "W. bantamweight" (EN) /
     * "Πετεινού γυναικών" (EL); an unknown class as ESPN writes it.
     */
    static String weightWord(String espn, boolean el) { return SportNames.weightShort(espn, el); }

    // ---------- words ----------
    static String sportName(String sport) { return I18n.t(SportEvent.F1.equals(sport) ? "sport_f1" : "sport_ufc"); }

    /** The sport tile's state line. */
    static String stateLine(String sport, boolean followed) {
        if (!followed) return I18n.t("sport_not_followed");
        return I18n.t(SportEvent.F1.equals(sport) ? "f1_following" : "ufc_following");
    }

    /** The hint line under the heading while the sport's chip is shown. */
    static String hint(String sport) { return I18n.t(SportEvent.F1.equals(sport) ? "f1_hint" : "ufc_hint"); }

    /** The sport's home-screen row as the TV names it ("Formula 1 · Norris"): stream D's name (EventCards.rowName). */
    static String rowLabel(Context c, String sport) { return EventCards.rowName(c, sport); }

    /** ESPN's headshot for a fighter id: for a followed fighter not on a card the app has stored. */
    static String headshot(String id) { return "https://a.espncdn.com/i/headshots/mma/players/full/" + id + ".png"; }

    // ---------- the panel ----------
    private final Activity a;
    private final Host host;
    private String sport;
    private int gen;
    private SportTiles.SportTile sportTile;
    private SettingsViews.OptionRow scopeRow;
    private final List<SportTiles.TeamTile> teams = new ArrayList<>();
    private final List<SportTiles.DriverTile> drivers = new ArrayList<>();
    private final List<SportTiles.FighterTile> fighters = new ArrayList<>();
    private SettingsViews.Text label, note;
    private boolean loading;
    /** The focus key to put back once the panel's tiles arrive (a Settings re-created by the system). */
    String pendingFocus;
    private final Runnable hintBack = new Runnable() { public void run() {
        if (host.gone() || sport == null || !host.shows(sport)) return;
        host.hint(null);
        if (label != null && SportEvent.F1.equals(sport)) label.set(I18n.t("f1_focus_label"));
    }};
    /** Followed UFC with no fighters yet: the stored cards are read again a few times (the refresh brings them). */
    private int fighterTries;
    static final int FIGHTER_TRIES = 24;
    static final long FIGHTER_RETRY_MS = 5000;

    SportsSettings(Activity a, Host host) { this.a = a; this.host = host; }

    /** The sport shown, or null. */
    String sport() { return sport; }

    /** The panel leaves the grid (another chip): later answers for it are dropped. */
    void clear() {
        gen++;
        sport = null;
        sportTile = null;
        scopeRow = null;
        teams.clear(); drivers.clear(); fighters.clear();
        label = note = null;
        loading = false;
        fighterTries = 0;
        host.ui().removeCallbacks(hintBack);
    }

    boolean contains(View v) {
        return v != null && (v == sportTile || v == scopeRow || teams.contains(v) || drivers.contains(v) || fighters.contains(v));
    }

    /** The D-pad's lines of the panel, top to bottom. */
    List<List<View>> lines() {
        List<List<View>> out = new ArrayList<>();
        if (sportTile == null) return out;
        boolean on = Sports.follows(a, sport);
        List<View> first = new ArrayList<>();
        first.add(sportTile);
        if (SportEvent.F1.equals(sport)) {
            List<List<View>> rows = new ArrayList<>();
            rows.add(first);
            for (int i = 0; on && i < teams.size(); i++) {
                int r = teamCell(i)[0];
                while (rows.size() <= r) rows.add(new ArrayList<View>());
                rows.get(r).add(teams.get(i));
            }
            out.addAll(rows);
            if (on) for (int r = 0; r * COLS < drivers.size(); r++)
                out.add(new ArrayList<View>(drivers.subList(r * COLS, Math.min(drivers.size(), (r + 1) * COLS))));
        } else {
            if (on && scopeRow != null) first.add(scopeRow);
            out.add(first);
            if (on) for (int r = 0; r * COLS < fighters.size(); r++)
                out.add(new ArrayList<View>(fighters.subList(r * COLS, Math.min(fighters.size(), (r + 1) * COLS))));
        }
        return out;
    }

    /** Fills the grid with a sport's panel (the grid was emptied by the caller). */
    void show(String sp) {
        clear();
        sport = sp;
        final FrameLayout grid = host.grid();
        sportTile = new SportTiles.SportTile(a, sp, Sports.follows(a, sp));
        sportTile.setTag("sporttile:" + sp);
        sportTile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleSport(); } });
        grid.addView(sportTile, AppViews.at(a, 0, 0, SportTiles.WIDE, SportTiles.H));
        if (SportEvent.F1.equals(sp)) showF1(); else showUfc();
    }

    // ---------- Formula 1 ----------
    private void showF1() {
        final int g = gen;
        loading = true;
        // the slots the teams and the first drivers will take, pulsing until the standings are read
        FrameLayout grid = host.grid();
        for (int i = 0; i < COLS - 2 + COLS; i++) {
            int[] cell = teamCell(i);
            grid.addView(new SettingsViews.SkeletonTile(a), AppViews.at(a, cell[1] * STEP_X, cell[0] * STEP_Y, SportTiles.W, SportTiles.H));
        }
        host.laidOut(2 * STEP_Y - 28);
        host.skeletons(true);
        final Context app = a.getApplicationContext();
        host.lists().execute(new Runnable() { public void run() {
            Engine.background();
            F1Feed.Standings st = null;
            try { st = F1Feed.standings(app, false); } catch (Throwable ignored) { }
            final F1Feed.Standings standings = st;
            host.ui().post(new Runnable() { public void run() { if (g == gen && !host.gone()) fillF1(standings); } });
            if (st == null) return;
            F1Feed.Roster ro = null;
            try { ro = F1Feed.roster(app, false); } catch (Throwable ignored) { }  // one request per driver, weekly: after the tiles
            final F1Feed.Roster roster = ro;
            if (roster != null) host.ui().post(new Runnable() { public void run() { if (g == gen && !host.gone()) colour(roster); } });
        }});
    }

    private void fillF1(F1Feed.Standings st) {
        loading = false;
        host.skeletons(false);
        FrameLayout grid = host.grid();
        for (int i = grid.getChildCount() - 1; i >= 0; i--) if (grid.getChildAt(i) != sportTile) grid.removeViewAt(i);
        teams.clear();
        drivers.clear();
        if (st == null || st.teams.isEmpty() && st.drivers.isEmpty()) {
            note = new SettingsViews.Text(a, I18n.t("f1_failed"), Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2), 1728, 0, 2);
            grid.addView(note, AppViews.at(a, 0, SportTiles.H + LABEL_GAP, 1728, 80));
            host.laidOut(SportTiles.H + LABEL_GAP + note.height());
            restorePending();
            return;
        }
        List<String> focus = Sports.f1Focus(a);
        List<F1Feed.Team> ts = new ArrayList<>(st.teams);
        Collections.sort(ts, new Comparator<F1Feed.Team>() {
            public int compare(F1Feed.Team x, F1Feed.Team y) { return rank(x.rank) - rank(y.rank); }
        });
        final Map<String, Integer> teamColor = new HashMap<>();
        for (F1Feed.Team t : ts) teamColor.put(t.id, t.color);
        for (final F1Feed.Team t : ts) {
            final SportTiles.TeamTile tile = new SportTiles.TeamTile(a, t.id, t.name, t.color, focus.contains("t:" + t.id));
            tile.setTag("f1:t:" + t.id);
            tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(tile, tile.ref); } });
            teams.add(tile);
        }
        List<SportEvent.Entrant> ds = new ArrayList<>(st.drivers);
        Collections.sort(ds, new Comparator<SportEvent.Entrant>() {
            public int compare(SportEvent.Entrant x, SportEvent.Entrant y) { return rank(x.pos) - rank(y.pos); }
        });
        for (SportEvent.Entrant d : ds) {
            if (d.id.isEmpty()) continue;
            int col = d.color != 0 ? d.color : teamColor.containsKey(d.teamId) ? teamColor.get(d.teamId) : 0;
            final SportTiles.DriverTile tile = new SportTiles.DriverTile(a, d.id, d.code, d.name, col,
                SportNames.trim(d.teamId, d.team), focus.contains("d:" + d.id));
            tile.setTag("f1:d:" + d.id);
            tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(tile, tile.ref); } });
            drivers.add(tile);
        }
        label = new SettingsViews.Text(a, I18n.t("f1_focus_label"), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), 1728, LABEL_H, 1);
        placeF1();
        restorePending();
    }

    private static int rank(int r) { return r <= 0 ? Integer.MAX_VALUE / 2 : r; }

    /** Places the teams beside and under the sport tile, then the label and the drivers; the focus tiles' state. */
    private void placeF1() {
        FrameLayout grid = host.grid();
        boolean on = Sports.follows(a, sport);
        for (int i = 0; i < teams.size(); i++) {
            int[] cell = teamCell(i);
            put(grid, teams.get(i), cell[1] * STEP_X, cell[0] * STEP_Y, SportTiles.W, SportTiles.H);
            teams.get(i).usable(on);
        }
        float bottom = teamRows(teams.size()) * STEP_Y - 28;
        float y0 = bottom;
        if (!drivers.isEmpty()) {
            put(grid, label, 0, bottom + LABEL_GAP, 1728, LABEL_H);
            y0 = bottom + BELOW_LABEL;
            for (int i = 0; i < drivers.size(); i++) {
                put(grid, drivers.get(i), (i % COLS) * STEP_X, y0 + (i / COLS) * STEP_Y, SportTiles.W, SportTiles.H);
                drivers.get(i).usable(on);
            }
            y0 += ((drivers.size() + COLS - 1) / COLS) * STEP_Y - 28;
        }
        host.laidOut(y0);
    }

    /** The roster arrived: each driver's tile takes his team's colour. */
    private void colour(F1Feed.Roster ro) {
        for (SportTiles.DriverTile t : drivers) {
            SportEvent.Entrant e = ro.byDriver.get(t.ref.substring(2));
            if (e == null) continue;
            int col = e.color;
            if (col == 0 && ro.teams.containsKey(e.teamId)) col = ro.teams.get(e.teamId).color;
            if (col != 0) t.color(col, SportNames.trim(e.teamId, e.team));
        }
    }

    /** OK on a team or driver: in or out of the focus; a third is refused. */
    private void pick(SportTiles.Tile t, String ref) {
        List<String> next = toggleFocus(Sports.f1Focus(a), ref);
        if (next == null) {
            t.shake();
            host.hint(I18n.t("focus_max"));
            if (label != null) label.set(I18n.t("focus_max"));  // the line over the drivers: in view when the hint line is not
            t.announceForAccessibility(I18n.t("focus_max"));
            host.ui().removeCallbacks(hintBack);
            host.ui().postDelayed(hintBack, 4000);
            return;
        }
        Map<String, String> names = new HashMap<>();
        names.put(ref, t.label);
        if (!Sports.setF1Focus(a, next, names)) return;
        t.on = next.contains(ref);
        t.describe();
        t.invalidate();
        t.announceForAccessibility(t.getContentDescription());
        host.ui().removeCallbacks(hintBack);
        hintBack.run();
        host.changed();
    }

    // ---------- UFC ----------
    private void showUfc() {
        final int g = gen;
        scopeRow = new SettingsViews.OptionRow(a, "ufc:scope", I18n.t("ufc_events"), I18n.t("ufc_events_hint"), SCOPE_W);
        scopeRow.setTag("ufc:scope");
        scopeRow.reserve(scopeWord(Sports.SCOPE_ALL), scopeWord(Sports.SCOPE_NUMBERED));  // the row keeps its height when cycled
        scopeRow.value(scopeWord(Sports.ufcScope(a)), false, false);
        scopeRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Sports.setUfcScope(a, nextScope(Sports.ufcScope(a)));
            boolean grew = scopeRow.value(scopeWord(Sports.ufcScope(a)), false, false);
            scopeRow.announceForAccessibility(scopeRow.value);
            if (grew) placeUfc();
            host.changed();
        }});
        label = new SettingsViews.Text(a, I18n.t("ufc_fighters_label"), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), 1728, LABEL_H, 1);
        placeUfc();
        loadFighters(g);
    }

    private static String scopeWord(String scope) { return I18n.t(Sports.SCOPE_NUMBERED.equals(scope) ? "scope_numbered" : "scope_all"); }

    /** Reads the fighters from the stored cards (no network) and lists them. */
    private void loadFighters(final int g) {
        loading = true;
        final Context app = a.getApplicationContext();
        final boolean el = I18n.el();
        host.lists().execute(new Runnable() { public void run() {
            Engine.background();
            final List<String[]> rows = new ArrayList<>();  // id, name, weight, headshot
            try {
                long now = System.currentTimeMillis();
                List<SportEvent> ufc = SportEngine.ofSport(EventStore.load(app), SportEvent.UFC);
                Map<String, SportEvent.Fighter> byId = new LinkedHashMap<>();
                for (SportEvent.Fighter f : UfcFeed.upcomingFighters(ufc, now)) if (f != null && !f.id.isEmpty() && !byId.containsKey(f.id)) byId.put(f.id, f);
                Map<String, String> weight = new HashMap<>();
                for (SportEvent e : ufc)
                    for (SportEvent.Session s : e.sessions)
                        for (SportEvent.Bout b : s.bouts)
                            for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b})
                                if (f != null && !weight.containsKey(f.id) && !b.weightClass.isEmpty()) weight.put(f.id, b.weightClass);
                Set<String> followed = Sports.ufcFighters(app);
                for (String id : fighterOrder(new ArrayList<>(byId.keySet()), followed)) {
                    SportEvent.Fighter f = byId.get(id);
                    String name = f != null ? f.name : Sports.name(app, Sports.fighterRef(id));
                    if (name == null || name.isEmpty()) name = id;
                    String shot = f != null && !f.headshot.isEmpty() ? f.headshot : headshot(id);
                    rows.add(new String[]{id, name, weightWord(weight.get(id), el), shot});
                }
            } catch (Throwable t) {
                android.util.Log.w("Fanela", "settings: UFC fighters", t);
            }
            host.ui().post(new Runnable() { public void run() { if (g == gen && !host.gone()) fillFighters(rows); } });
        }});
    }

    private void fillFighters(List<String[]> rows) {
        loading = false;
        if (rows.isEmpty() && Sports.follows(a, SportEvent.UFC) && fighterTries++ < FIGHTER_TRIES) {
            final int g = gen;
            host.ui().postDelayed(new Runnable() { public void run() {
                if (g == gen && !host.gone() && fighters.isEmpty() && SportEvent.UFC.equals(sport)) loadFighters(g);
            }}, FIGHTER_RETRY_MS);
        }
        FrameLayout grid = host.grid();
        for (SportTiles.FighterTile t : fighters) grid.removeView(t);
        fighters.clear();
        Set<String> followed = Sports.ufcFighters(a);
        for (String[] r : rows) {
            final SportTiles.FighterTile tile = new SportTiles.FighterTile(a, r[0], r[1], r[2], r[3], followed.contains(r[0]));
            tile.setTag("ufc:f:" + r[0]);
            tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                boolean on = !Sports.ufcFighters(a).contains(tile.id);
                Sports.setUfcFighter(a, tile.id, tile.label, on);
                tile.on = on;
                tile.describe();
                tile.invalidate();
                tile.announceForAccessibility(tile.getContentDescription());
                host.changed();
            }});
            fighters.add(tile);
            face(tile);
        }
        placeUfc();
        restorePending();
    }

    /** The headshot: decoded from the TV's copy, else downloaded on its own pool (initials meanwhile). */
    private void face(final SportTiles.FighterTile t) {
        final String url = t.headshotUrl;
        if (url == null || !url.startsWith("https://")) return;
        final Context app = a.getApplicationContext();
        final int size = Math.round(SportTiles.SQ * Ui.SCALE_TILE * Ui.scale(a) * 1.2f);
        try {
            host.images().execute(new Runnable() { public void run() {
                Engine.background();
                if (Cards.have(app, url)) { post(t, scaled(app, url, size)); return; }
                try {
                    host.fetches().execute(new Runnable() { public void run() {
                        Engine.background();
                        Net.fetchImage(app, url);
                        if (Net.imageFile(app, url).exists()) post(t, scaled(app, url, size));
                    }});
                } catch (java.util.concurrent.RejectedExecutionException ignored) { }  // Settings is closing
            }});
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    private void post(final SportTiles.FighterTile t, final Bitmap b) {
        if (b == null) return;
        host.ui().post(new Runnable() { public void run() { if (!host.gone() && fighters.contains(t)) t.face(b); } });
    }

    private static Bitmap scaled(Context c, String url, int width) {
        Bitmap raw = Net.image(c, url);
        if (raw == null) return null;
        if (raw.getWidth() <= width) return raw;
        Bitmap half = Cards.smooth(raw, width);
        Bitmap out = Bitmap.createScaledBitmap(half, width, Math.max(1, Math.round(raw.getHeight() * (float) width / raw.getWidth())), true);
        if (half != raw && half != out) half.recycle();
        if (raw != out) raw.recycle();
        return out;
    }

    /** The Events option beside the tile, the label and the fighters (or a line saying why there are none). */
    private void placeUfc() {
        FrameLayout grid = host.grid();
        boolean on = Sports.follows(a, sport);
        put(grid, scopeRow, SCOPE_X, 0, SCOPE_W, scopeRow.height());
        usable(scopeRow, on);
        float bottom = Math.max(SportTiles.H, scopeRow.height());
        put(grid, label, 0, bottom + LABEL_GAP, 1728, LABEL_H);
        float y0 = bottom + BELOW_LABEL;
        if (note != null) { grid.removeView(note); note = null; }
        if (fighters.isEmpty()) {
            if (loading) { host.laidOut(bottom + LABEL_GAP + LABEL_H); return; }
            note = new SettingsViews.Text(a, I18n.t(on ? "ufc_fighters_none" : "ufc_fighters_follow_first"), Ui.REGULAR, 28,
                Ui.alpha(Ui.TXT, 0.55f), 1728, 0, 2);
            float ny = bottom + LABEL_GAP + LABEL_H + 6;
            put(grid, note, 0, ny, 1728, note.height());
            host.laidOut(ny + note.height());
            return;
        }
        for (int i = 0; i < fighters.size(); i++) {
            put(grid, fighters.get(i), (i % COLS) * STEP_X, y0 + (i / COLS) * STEP_Y, SportTiles.W, SportTiles.H);
            fighters.get(i).usable(on);
        }
        host.laidOut(y0 + ((fighters.size() + COLS - 1) / COLS) * STEP_Y - 28);
    }

    private static void usable(SettingsViews.OptionRow r, boolean u) {
        r.setFocusable(u);
        r.setFocusableInTouchMode(u);
        r.setAlpha(u ? 1f : 0.4f);
    }

    private void put(FrameLayout grid, View v, float x, float y, float w, float h) {
        if (v.getParent() == null) grid.addView(v, AppViews.at(a, x, y, w, h));
        else v.setLayoutParams(AppViews.at(a, x, y, w, h));
    }

    // ---------- the follow ----------
    /** OK on the sport tile: follows or unfollows the sport; the focus tiles wake or sleep with it. */
    private void toggleSport() {
        final String sp = sport;
        boolean on = !Sports.follows(a, sp);
        Sports.setFollowed(a, sp, on);
        sportTile.set(on);
        sportTile.announceForAccessibility(sportTile.getContentDescription());
        if (SportEvent.F1.equals(sp)) { if (!loading && !teams.isEmpty()) placeF1(); else host.laidOut(-1); }
        else placeUfc();
        final int g = gen;
        // a UFC followed now: its cards arrive with the refresh, and with them the fighters
        host.followed(sp, on, !on || !SportEvent.UFC.equals(sp) ? null : new Runnable() { public void run() {
            if (g == gen && !host.gone() && SportEvent.UFC.equals(sport)) loadFighters(g);
        }});
    }

    /** A focus key saved before the system ended the app: given back once the tiles exist. */
    private void restorePending() {
        String k = pendingFocus;
        pendingFocus = null;
        if (k == null) return;
        View v = host.grid().findViewWithTag(k);
        if (v != null && v.isFocusable()) host.focus(v);
    }

    /** Followed fighters' names for screens that know only ids (SPORT_DUMP). */
    static Map<String, String> fighterNames(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : Sports.ufcFighters(c)) out.put(id, Sports.name(c, Sports.fighterRef(id)));
        return out;
    }
}
