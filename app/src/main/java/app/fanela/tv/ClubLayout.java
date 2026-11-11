package app.fanela.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Club layout (SPEC-APP 3, the default): one club at a time and the app wears its shirt. The rail of followed
 * clubs at the left (with each one's next kick-off, fresh result or live score), the hero with the selected club's
 * next match or its live strip, the latest results as objects, and the fixtures timetable with where to watch.
 * Behind the hero, the club's pre-rendered kit field; moving through the rail re-themes the screen (300 ms).
 */
final class ClubLayout implements HomeLayout {
    /** The images one club needs, made on the display thread (prepare). */
    static final class Art {
        AppArt.Decal railBig, railSmall, heroHome, heroAway, mine;
        final Map<String, AppArt.Decal> crests = new HashMap<>();
        final Map<String, AppKit.Watch> watch = new HashMap<>();
        AppKit.Watch heroWatch;
        Bitmap field;
        String okLabel = "";
    }

    private static final float LIST_TOP = 736.05f, LATEST_TOP = 556, FADE_TOP = 1000;

    private Context c;
    private Host host;
    private FrameLayout stage, content;
    private FieldView field;
    private AppViews.Masthead mast;
    private ScrollView railScroll;
    private LinearLayout railList;
    private final List<ClubViews.RailItem> rail = new ArrayList<>();
    private ClubViews.Gear gear;
    /**
     * An invisible focusable, the stage's first child: it holds the focus for a moment while the views that had it are
     * replaced (a fixture list rebuilt, a tile hidden), so the framework never hands it to the first rail club (which
     * would switch the club). The focus then goes back to the same item, or the hero.
     */
    private View park;
    /** A D-pad move is placing the focus: only then does a rail club that gains it become the selection. */
    private boolean keyMove;
    private ClubHero hero;
    private AppViews.Label latestLabel, fixturesLabel;
    private final ClubViews.ResultTile[] tiles = new ClubViews.ResultTile[2];
    private ScrollView listScroll;
    private LinearLayout list;
    private final List<ClubViews.FixtureRow> rows = new ArrayList<>();
    private View peek;
    private EmptyPanel emptyPanel;
    private AppViews.Button choose;
    private AppViews.FocusLayer focusLayer;

    private HomeModel model;
    private volatile String selected;
    private String lastMain, railKey = "", rowsKey = "";
    private boolean blocked, placed, empty, loadingShown;
    private ValueAnimator pulse, scrollAnim, swap;

    // ---------- prepare (display thread) ----------
    @Override public void prepare(Context c, HomeModel m) {
        Ui.fonts(c);
        AppArt.shirt(c, Ui.TXT);
        AppArt.shirt(c, FieldColors.INK_DARK);
        if (m.empty()) { KitField.get(c, "", null, null, false, KitField.Mode.NEUTRAL); return; }
        String sel = selected != null && m.club(selected) != null ? selected : m.favourite;
        List<HomeModel.ClubData> order = new ArrayList<>(m.clubs);
        for (HomeModel.ClubData d : m.clubs) if (d.club.id.equals(sel)) { order.remove(d); order.add(0, d); }
        for (HomeModel.ClubData d : order) d.art = art(c, d, m);
    }

    /**
     * The first frame (UI thread): the rail's crests, read from the TV's cache (a monogram only for a crest never
     * downloaded), and the selected club's field without its watermark. The hero, tiles and rows are skeletons.
     */
    @Override public void first(Context c, HomeModel m) {
        Ui.fonts(c);
        if (m.empty()) { KitField.get(c, "", null, null, false, KitField.Mode.NEUTRAL); return; }
        String sel = selected != null && m.club(selected) != null ? selected : m.favourite;
        if (m.club(sel) == null) sel = m.clubs.get(0).club.id;
        for (HomeModel.ClubData d : m.clubs) {
            Art a = new Art();
            Teams.Club k = d.club;
            if (k.id.equals(sel)) a.railBig = AppArt.crest(c, k.crest, k.name, 120, false);
            else a.railSmall = AppArt.crest(c, k.crest, k.name, 76, false);
            if (k.id.equals(sel)) {
                boolean stripes = ClubTokens.of(k.id, d.colors.primary, d.colors.primary, null).pattern == ClubTokens.Pattern.STRIPES;
                a.field = KitField.get(c, k.id, d.colors, k.crest, stripes, KitField.Mode.KIT, false);
            }
            d.art = a;
        }
    }

    private static Art art(Context c, HomeModel.ClubData d, HomeModel model) {
        Art a = new Art();
        Teams.Club k = d.club;
        a.railBig = AppArt.crest(c, k.crest, k.name, 120, false);
        a.railSmall = AppArt.crest(c, k.crest, k.name, 76, false);
        Match h = d.hero;
        if (h != null) {
            // the hero's crests: the match's files, else the cached crest the rail shows (P2f)
            float size = d.kind == HomeModel.Kind.LIVE ? 120 : 196;
            a.heroHome = AppArt.crest(c, h.homeCrest, crestOf(model, k, h, true), h.home, size, false);
            a.heroAway = AppArt.crest(c, h.awayCrest, crestOf(model, k, h, false), h.away, size, false);
            a.heroWatch = AppKit.watch(c, h);
            if (d.kind == HomeModel.Kind.LIVE) {
                boolean app = h.channelKnown && h.channelApp != null && HomeRows.installed(c, h.channelApp);
                a.okLabel = app ? AppText.f("opens", Cards.appName(h.channelApp)) : AppText.t("details");
            }
        } else a.mine = AppArt.crest(c, k.crest, k.name, 196, false);
        for (Match m : d.latest) a.crests.put(m.key, AppArt.crest(c, m.clubHome() ? m.awayCrest : m.homeCrest, m.opponentRaw(), 52, true));
        for (Match m : d.fixtures) {
            a.crests.put(m.key, AppArt.crest(c, m.clubHome() ? m.awayCrest : m.homeCrest, m.opponentRaw(), 42, true));
            a.watch.put(m.key, AppKit.watch(c, m));
        }
        boolean stripes = ClubTokens.of(k.id, d.colors.primary, d.colors.primary, null).pattern == ClubTokens.Pattern.STRIPES;
        a.field = KitField.get(c, k.id, d.colors, k.crest, stripes, d.kind == HomeModel.Kind.LIVE ? KitField.Mode.LIVE : KitField.Mode.KIT);
        return a;
    }

    /**
     * A side of a match's own crest, as the rail has it cached: my club's crest for my side, a followed club's for an
     * opponent that is followed too; null otherwise.
     */
    static String crestOf(HomeModel model, Teams.Club mine, Match m, boolean home) {
        if (home == m.clubHome()) return mine.crest;
        String raw = home ? m.home : m.away, key = Cards.key(I18n.grName(raw));
        if (model != null) for (HomeModel.ClubData o : model.clubs)
            if (o.club != mine && Cards.key(I18n.grName(o.club.name)).equals(key)) return o.club.crest;
        return null;
    }

    /** The club's kit colours, as the cards resolve them (Badges, else the feed's colour). */
    static FieldColors colors(Teams.Club k) {
        int[] col = Badges.colors(k.name);
        int kit = col[0], trim = col[1];
        if (!Badges.known(k.name) && k.color != null && k.color.matches("[0-9a-fA-F]{6}")) kit = 0xFF000000 | Integer.parseInt(k.color, 16);
        return FieldColors.of(kit, trim);
    }

    // ---------- views ----------
    @Override public View build(Context c, Host host) {
        this.c = c;
        this.host = host;
        Ui.fonts(c);
        stage = new FrameLayout(c);
        stage.setBackgroundColor(Ui.APP_BG);
        park = new View(c);
        park.setFocusable(true);
        park.setFocusableInTouchMode(true);
        park.setDefaultFocusHighlightEnabled(false);
        park.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        stage.addView(park, new FrameLayout.LayoutParams(1, 1));
        field = new FieldView(c);
        stage.addView(field, AppViews.at(c, 0, 0, Ui.W, KitField.HEIGHT));
        mast = new AppViews.Masthead(c);
        stage.addView(mast, AppViews.at(c, 0, 0, Ui.W, 110));

        railScroll = new Scroller(c);
        railScroll.setVerticalScrollBarEnabled(false);
        railScroll.setFocusable(false);
        railScroll.setClipChildren(false);
        railList = new LinearLayout(c);
        railList.setOrientation(LinearLayout.VERTICAL);
        railScroll.addView(railList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stage.addView(railScroll, AppViews.at(c, 96, 130, 184, 830));
        gear = new ClubViews.Gear(c);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ClubLayout.this.host.openSettings(); } });
        stage.addView(gear, AppViews.at(c, 152, 972, 64, 64));

        content = new FrameLayout(c);  // clips: the lists scroll inside their own bounds (the ring lives in FocusLayer)
        stage.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hero = new ClubHero(c);
        hero.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { okHero(); } });
        content.addView(hero, AppViews.at(c, 300, 112, 1524, 420));
        latestLabel = label(c);
        fixturesLabel = label(c);
        content.addView(latestLabel, AppViews.at(c, 336, LATEST_TOP, 900, 34));
        content.addView(fixturesLabel, AppViews.at(c, 336, 696, 900, 34));
        for (int i = 0; i < 2; i++) {
            final ClubViews.ResultTile t = new ClubViews.ResultTile(c);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (t.m != null) ClubLayout.this.host.openMatch(t.m, club()); } });
            tiles[i] = t;
            content.addView(t, AppViews.at(c, 336, 598.05f, 600, 84));
        }
        listScroll = new Scroller(c);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setFocusable(false);
        listScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(listScroll, AppViews.at(c, 318, LIST_TOP, 1524, Ui.H - LIST_TOP));
        peek = new PeekFade(c);
        content.addView(peek, AppViews.at(c, 300, FADE_TOP, 1560, Ui.H - FADE_TOP));

        emptyPanel = new EmptyPanel(c);
        stage.addView(emptyPanel, AppViews.at(c, 300, 112, 1524, 520));
        choose = new AppViews.Button(c, I18n.t("choose_clubs"));
        choose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ClubLayout.this.host.chooseClubs(); } });
        stage.addView(choose, AppViews.at(c, 352, 500, AppViews.Button.width(I18n.t("choose_clubs")) + 24, 88));
        emptyPanel.setVisibility(View.GONE);
        choose.setVisibility(View.GONE);

        focusLayer = new AppViews.FocusLayer(c);
        stage.addView(focusLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // until the data is bound the hero holds the focus (as its skeleton), never the gear
        hero.requestFocus();
        return stage;
    }

    private static AppViews.Label label(Context c) {
        return new AppViews.Label(c, Ui.MEDIUM, Ui.T_LABEL, Ui.alpha(Ui.TXT, 0.62f), Ui.TRACK_EYEBROW, 23.18f);
    }

    private Teams.Club club() {
        HomeModel.ClubData d = data();
        return d == null ? null : d.club;
    }

    private HomeModel.ClubData data() {
        if (model == null || model.empty()) return null;
        HomeModel.ClubData d = selected == null ? null : model.club(selected);
        if (d == null) d = model.club(model.favourite);
        return d != null ? d : model.clubs.get(0);
    }

    // ---------- binding ----------
    @Override public void bind(HomeModel m) {
        String was = focusKey();
        model = m;
        boolean nowEmpty = m.empty();
        if (nowEmpty != empty || !placed) {
            if (placed && !blocked && stage.hasFocus()) park.requestFocus();  // the views that have it are hidden now
            showEmpty(nowEmpty);
        }
        empty = nowEmpty;
        if (empty) {
            mast.shirt = AppArt.shirt(c, Ui.TXT);
            mast.ink = Ui.TXT;
            mast.statusInk = Ui.TXT;
            mast.set(m.status, m.statusText);
            mast.invalidate();
            setField(KitField.cached(KitField.key("", null, KitField.Mode.NEUTRAL)), false);
            rebuildRail(m);
            View f = stage.findFocus();
            if (!placed || (!blocked && f != gear && f != choose)) choose.requestFocus();
            placed = true;
            return;
        }
        HomeModel.ClubData d = data();
        selected = d.club.id;
        rebuildRail(m);
        bindMain(d);
        if (!placed) { placed = true; if (!blocked) hero.requestFocus(); }
        else if (!blocked && (stage.findFocus() == null || park.isFocused())) focus(was);
    }

    private void showEmpty(boolean on) {
        int main = on ? View.GONE : View.VISIBLE, panel = on ? View.VISIBLE : View.GONE;
        content.setVisibility(main);
        railScroll.setVisibility(main);
        emptyPanel.setVisibility(panel);
        choose.setVisibility(panel);
        choose.setFocusable(on);
    }

    /**
     * The rail: its items are made once per list of clubs (focus stays on them while the selection moves); a new
     * selection or awareness line only changes their heights and what they draw.
     */
    private void rebuildRail(HomeModel m) {
        StringBuilder key = new StringBuilder();
        for (HomeModel.ClubData d : m.clubs) key.append(',').append(d.club.id);
        if (!key.toString().equals(railKey)) {
            railKey = key.toString();
            if (!blocked && railList.hasFocus()) park.requestFocus();  // bind() puts it back on the same club, or the hero
            railList.removeAllViews();
            rail.clear();
            for (final HomeModel.ClubData d : m.clubs) {
                final ClubViews.RailItem it = new ClubViews.RailItem(c);
                it.setTag(d.club.id);
                it.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    public void onFocusChange(View v, boolean has) {
                        if (!has) return;
                        ensureRailVisible(it);
                        if (keyMove) railFocused((String) it.getTag());  // never when the framework moves the focus
                    }
                });
                rail.add(it);
                railList.addView(it, new LinearLayout.LayoutParams(Ui.px(c, 184), Ui.px(c, 100)));
            }
        }
        HomeModel.ClubData shown = data();
        FieldColors fc = shown == null ? null : shown.colors;
        boolean liveEnv = shown != null && shown.kind == HomeModel.Kind.LIVE;
        float top = 130 - railScroll.getScrollY() / Ui.scale(c);
        for (ClubViews.RailItem it : rail) {
            HomeModel.ClubData d = m.club((String) it.getTag());
            if (d == null) continue;
            boolean sel = d.club.id.equals(selected);
            float h = ClubViews.RailItem.height(sel, !d.rail.isEmpty() || d.kind == HomeModel.Kind.LOADING);  // loading: its line's room kept
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) it.getLayoutParams();
            if (lp.height != Ui.px(c, h)) { lp.height = Ui.px(c, h); it.setLayoutParams(lp); }
            Art a = (Art) d.art;
            float mid = top + (sel ? 74 : 52);
            int ink = fc == null || liveEnv ? Ui.TXT : fc.inkAt(mid);
            it.bind(d, sel, a == null ? null : a.railBig, a == null ? null : a.railSmall, ink, fc != null && !liveEnv && fc.lightAt(mid));
            top += h;
        }
        gear.ink = Ui.TXT;
        gear.invalidate();
    }

    private void bindMain(HomeModel.ClubData d) {
        String refocus = null;  // the focus parked while its view is replaced: put back once the new views are bound
        Art a = d.art instanceof Art ? (Art) d.art : new Art();
        FieldColors fc = d.colors;
        boolean liveEnv = d.kind == HomeModel.Kind.LIVE;
        setField(a.field, true);
        mast.shirt = AppArt.shirt(c, liveEnv ? Ui.TXT : fc.inkAt(78));
        mast.ink = liveEnv ? Ui.TXT : fc.inkAt(78);
        mast.statusInk = mast.ink;
        mast.set(model.status, model.statusText);
        mast.invalidate();
        hero.bind(d, model.now, a.heroWatch, a.heroHome, a.heroAway, a.mine, a.okLabel);
        // latest
        boolean loading = d.kind == HomeModel.Kind.LOADING;
        boolean hasLatest = loading || !d.latest.isEmpty();
        latestLabel.set(Cards.caps(AppText.t("latest")));
        latestLabel.setVisibility(hasLatest ? View.VISIBLE : View.GONE);
        float x = 336;
        for (int i = 0; i < 2; i++) {
            ClubViews.ResultTile t = tiles[i];
            if (loading) {
                float w = i == 0 ? 560 : 620;
                t.skeleton(w);
                place(t, x, 598.05f, w, 84);
                t.setVisibility(View.VISIBLE);
                x += w + 24;
            } else if (i < d.latest.size()) {
                Match m = d.latest.get(i);
                float maxText = i == 0 ? 520 : 1824 - x - (ClubViews.ResultTile.width(m, d.bothSports, 0));
                float w = ClubViews.ResultTile.width(m, d.bothSports, Math.max(160, Math.min(520, maxText)));
                t.bind(m, a.crests.get(m.key), d.bothSports);
                place(t, x, 598.05f, w, 84);
                t.setVisibility(View.VISIBLE);
                x += w + 24;
            } else {
                if (t.hasFocus() && !blocked) { refocus = focusKey(); park.requestFocus(); }  // a focused tile goes away
                t.setVisibility(View.GONE);
                t.setFocusable(false);
            }
        }
        // fixtures
        float listTop = hasLatest ? LIST_TOP : LIST_TOP - 140;
        fixturesLabel.set(Cards.caps(AppText.t("fixtures")));
        place(fixturesLabel, 336, listTop - 40.05f, 900, 34);
        place(listScroll, 318, listTop, 1524, Ui.H - listTop);
        int foot = Ui.px(c, listFoot(Ui.H - listTop, FADE_TOP - listTop));
        if (list.getPaddingBottom() != foot) list.setPadding(0, 0, 0, foot);
        fixturesLabel.setVisibility(loading || !d.fixtures.isEmpty() ? View.VISIBLE : View.GONE);
        StringBuilder rk = new StringBuilder(d.club.id).append(loading);
        for (Match m : d.fixtures) rk.append(',').append(m.key);
        int n = loading ? 6 : d.fixtures.size();
        if (!rk.toString().equals(rowsKey)) {
            rowsKey = rk.toString();
            if (list.hasFocus() && !blocked) { refocus = focusKey(); park.requestFocus(); }  // never to the rail
            list.removeAllViews();
            rows.clear();
            for (int i = 0; i < n; i++) {
                final ClubViews.FixtureRow r = new ClubViews.FixtureRow(c);
                r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (r.m != null) host.openMatch(r.m, club()); } });
                r.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) { if (has) ensureRowVisible(r); } });
                rows.add(r);
                list.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 60)));
            }
            listScroll.scrollTo(0, 0);
        }
        for (int i = 0; i < n; i++) {
            if (loading) rows.get(i).skeleton(i == 0);
            else {
                Match m = d.fixtures.get(i);
                rows.get(i).bind(m, a.crests.get(m.key), a.watch.get(m.key), d.bothSports, i == 0, model.now);
            }
        }
        peek.setVisibility(n * 60 > FADE_TOP - listTop ? View.VISIBLE : View.GONE);
        pulse(loading);
        if (refocus != null && !blocked && park.isFocused()) focus(refocus);  // the same match's row, else the hero
    }

    private void place(View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = AppViews.at(c, x, y, w, h);
        ViewGroup.LayoutParams cur = v.getLayoutParams();
        if (cur instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) cur;
            if (f.leftMargin == lp.leftMargin && f.topMargin == lp.topMargin && f.width == lp.width && f.height == lp.height) return;
        }
        v.setLayoutParams(lp);
    }

    private void setField(Bitmap b, boolean animate) { if (b != null) field.show(b, animate); }

    /** Skeleton blocks pulse (.5 -> .9 -> .5, 1.2 s) while anything loads; nothing animates otherwise. */
    private void pulse(boolean on) {
        loadingShown = on;
        if (!on) { if (pulse != null) { pulse.cancel(); pulse = null; } return; }
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0.55f, 1f);
        pulse.setDuration(Ui.SKELETON_MS / 2);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                hero.pulse = v; hero.invalidate();
                for (ClubViews.ResultTile t : tiles) { t.pulse = v; t.invalidate(); }
                for (ClubViews.FixtureRow r : rows) { r.pulse = v; r.invalidate(); }
            }
        });
        pulse.start();
    }

    // ---------- selection ----------
    @Override public String selected() { return selected; }
    @Override public void select(String clubId) { selected = clubId; }

    /** Rail focus moves the selection: the screen re-themes (field 300 ms) and the main column swaps at 120 ms. */
    private void railFocused(final String id) {
        if (id.equals(selected) || model == null) return;
        selected = id;
        lastMain = null;
        final HomeModel.ClubData d = model.club(id);
        if (d == null) return;
        if (swap != null) swap.cancel();
        swap = ValueAnimator.ofFloat(0, 1);
        swap.setDuration(Ui.SWITCH_MS);
        swap.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean swapped;
            public void onAnimationUpdate(ValueAnimator a) {
                float t = a.getAnimatedFraction() * Ui.SWITCH_MS;
                if (t < Ui.SWAP_MS) content.setAlpha(1 - t / Ui.SWAP_MS);
                else {
                    if (!swapped) {
                        swapped = true;
                        rowsKey = "";
                        bindMain(d);
                        rebuildRail(model);
                        listScroll.scrollTo(0, 0);
                    }
                    content.setAlpha(Math.min(1, (t - Ui.SWAP_MS) / (Ui.SWITCH_MS - Ui.SWAP_MS)));
                }
            }
        });
        swap.start();
    }

    // ---------- focus ----------
    @Override public String focusKey() {
        View f = stage == null ? null : stage.findFocus();
        if (f == null || f == park) return null;
        if (f == hero) return "hero";
        if (f == gear) return "gear";
        if (f == choose) return "choose";
        for (ClubViews.ResultTile t : tiles) if (f == t && t.m != null) return "tile:" + t.m.key;
        for (ClubViews.FixtureRow r : rows) if (f == r && r.m != null) return "row:" + r.m.key;
        if (f instanceof ClubViews.RailItem) return "rail:" + f.getTag();
        return null;
    }

    @Override public void focus(String key) {
        if (blocked) return;
        if (empty) { (key != null && key.equals("gear") ? gear : choose).requestFocus(); return; }
        if (key == null) { hero.requestFocus(); return; }
        if (key.equals("gear")) { gear.requestFocus(); return; }
        if (key.startsWith("rail:")) {
            for (ClubViews.RailItem it : rail) if (key.substring(5).equals(it.getTag())) { it.requestFocus(); return; }
        }
        if (key.startsWith("tile:")) for (ClubViews.ResultTile t : tiles) if (t.m != null && key.substring(5).equals(t.m.key) && t.getVisibility() == View.VISIBLE) { t.requestFocus(); return; }
        if (key.startsWith("row:")) for (ClubViews.FixtureRow r : rows) if (r.m != null && key.substring(4).equals(r.m.key)) { r.requestFocus(); return; }
        hero.requestFocus();
    }

    @Override public void block(boolean b) {
        blocked = b;
        if (stage != null) stage.setDescendantFocusability(b ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private List<ClubViews.ResultTile> shownTiles() {
        List<ClubViews.ResultTile> out = new ArrayList<>();
        for (ClubViews.ResultTile t : tiles) if (t.getVisibility() == View.VISIBLE && t.m != null) out.add(t);
        return out;
    }

    private List<ClubViews.FixtureRow> shownRows() {
        List<ClubViews.FixtureRow> out = new ArrayList<>();
        for (ClubViews.FixtureRow r : rows) if (r.m != null) out.add(r);
        return out;
    }

    private ClubViews.RailItem railSelected() {
        for (ClubViews.RailItem it : rail) if (it.getTag().equals(selected)) return it;
        return rail.isEmpty() ? null : rail.get(0);
    }

    /**
     * The D-pad map (SPEC-APP 3.6): hero -> latest tiles -> rows down the list; Left from the main column to the rail's
     * selected club, Right from the rail back to the item left; Up/Down in the rail through the clubs, then the gear.
     */
    @Override public boolean onKey(KeyEvent e) {
        if (blocked || stage == null) return false;
        int code = e.getKeyCode();
        boolean dir = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!dir) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        View f = stage.findFocus();
        keyMove = true;
        try {
            View next = move(f, code);
            if (next != null && next != f) next.requestFocus();
        } finally { keyMove = false; }
        return true;
    }

    /** Back on the home screen leaves the app (SPEC-APP 3.6): nothing to undo first. */
    @Override public boolean back() { return false; }

    private View move(View f, int code) {
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (empty) {
            if (f == choose && left) return gear;
            if (f == gear && (right || up)) return choose;
            return f == null ? choose : null;
        }
        List<ClubViews.ResultTile> ts = shownTiles();
        List<ClubViews.FixtureRow> rs = shownRows();
        if (f == null || f == park) return hero;
        if (f instanceof ClubViews.RailItem || f == gear) {
            int i = rail.indexOf(f);
            if (up) return f == gear ? (rail.isEmpty() ? null : rail.get(rail.size() - 1)) : i > 0 ? rail.get(i - 1) : null;
            if (down) return f == gear ? null : i < rail.size() - 1 ? rail.get(i + 1) : gear;
            if (right) {
                String k = lastMain;
                lastMain = null;
                if (k != null) { focus(k); return null; }
                return hero;
            }
            return null;
        }
        // the main column
        lastMain = focusKey();
        if (left) {
            int ti = ts.indexOf(f);
            if (ti > 0) return ts.get(ti - 1);
            return railSelected();
        }
        if (f == hero) {
            if (down) return !ts.isEmpty() ? ts.get(0) : !rs.isEmpty() ? rs.get(0) : null;
            return null;
        }
        int ti = ts.indexOf(f);
        if (ti >= 0) {
            if (right) return ti + 1 < ts.size() ? ts.get(ti + 1) : null;
            if (up) return hero;
            if (down) return rs.isEmpty() ? null : rs.get(0);
            return null;
        }
        int ri = rs.indexOf(f);
        if (ri >= 0) {
            if (up) return ri > 0 ? rs.get(ri - 1) : !ts.isEmpty() ? ts.get(0) : hero;
            if (down) return ri + 1 < rs.size() ? rs.get(ri + 1) : null;
        }
        return null;
    }

    /** A fixture row's height (spec px). */
    static final float ROW = 60;

    /**
     * The room under the last fixture row (spec px), for a list view h tall whose fade starts `fade` into it: scrolled
     * to its end, the list starts on a whole row and its last row sits clear above the fade (QA3A-4).
     */
    static float listFoot(float h, float fade) { return h - (float) Math.floor(fade / ROW + 1e-3) * ROW; }

    /**
     * Where the list scrolls to keep row i (of n) one row clear of the fade (the last row: above it), from scroll sy,
     * with the fade's top `fade` px into the list: always a whole number of rows (QA3A-4), so the row under the
     * FIXTURES label is never cut in half.
     */
    static float rowScroll(int i, int n, float sy, float fade) {
        float top = i * ROW, bottom = top + ROW;
        float room = i < n - 1 ? fade - ROW : fade;
        float target = sy;
        if (bottom - sy > room) target = (float) Math.ceil((bottom - room) / ROW - 1e-3) * ROW;
        if (top - sy < (i > 0 ? ROW : 0)) target = Math.max(0, top - (i > 0 ? ROW : 0));
        return target;
    }

    /** Keeps the focused row one row clear of the fade (the last row: above it), on whole rows; 200 ms, decelerating. */
    private void ensureRowVisible(ClubViews.FixtureRow r) {
        float s = Ui.scale(c);
        int i = rows.indexOf(r);
        float viewTop = listScroll.getTop() / s;
        float sy = listScroll.getScrollY() / s;
        float target = rowScroll(i, rows.size(), sy, FADE_TOP - viewTop);
        if (target == sy) return;
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = ValueAnimator.ofInt(listScroll.getScrollY(), Math.round(target * s));
        scrollAnim.setDuration(Ui.SCROLL_MS);
        scrollAnim.setInterpolator(Ui.DECELERATE);
        scrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { listScroll.scrollTo(0, (int) a.getAnimatedValue()); }
        });
        scrollAnim.start();
    }

    private void ensureRailVisible(View it) {
        int top = it.getTop(), bottom = it.getBottom(), h = railScroll.getHeight(), sy = railScroll.getScrollY();
        if (bottom > sy + h) railScroll.smoothScrollTo(0, bottom - h);
        else if (top < sy) railScroll.smoothScrollTo(0, top);
    }

    private void okHero() {
        HomeModel.ClubData d = data();
        if (d == null || d.hero == null) return;
        if (d.kind == HomeModel.Kind.LIVE) host.openLive(d.hero, d.club);
        else host.openMatch(d.hero, d.club);
    }

    @Override public void resume() { if (pulse != null) pulse.resume(); }
    @Override public void pause() { if (pulse != null) pulse.pause(); }

    @Override public void release() {
        if (pulse != null) pulse.cancel();
        if (scrollAnim != null) scrollAnim.cancel();
        if (swap != null) swap.cancel();
        field.release();
    }

    // ---------- small views ----------
    /**
     * The pre-rendered field, scaled to the screen: a new bitmap crossfades over the old one in 300 ms (two bitmap
     * draws with one alpha; nothing is blended or blurred per frame).
     */
    static final class FieldView extends View {
        private Bitmap shown, old;
        private float t = 1;
        private ValueAnimator anim;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final android.graphics.Rect dst = new android.graphics.Rect();

        FieldView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setWillNotDraw(false); }

        void show(Bitmap b, boolean animate) {
            if (b == shown) return;
            if (anim != null) anim.cancel();
            old = animate && shown != null ? shown : null;
            shown = b;
            t = old == null ? 1 : 0;
            if (old != null) {
                anim = ValueAnimator.ofFloat(0, 1);
                anim.setDuration(Ui.SWITCH_MS);
                anim.setInterpolator(Ui.FAST_OUT_SLOW_IN);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator a) { t = (float) a.getAnimatedValue(); if (t >= 1) old = null; invalidate(); }
                });
                anim.start();
            }
            invalidate();
        }

        void release() { if (anim != null) anim.cancel(); shown = null; old = null; }

        @Override protected void onDraw(Canvas cv) {
            dst.set(0, 0, getWidth(), getHeight());
            if (old != null && t < 1) { p.setAlpha(255); cv.drawBitmap(old, null, dst, p); }
            if (shown != null) { p.setAlpha(Math.round(255 * t)); cv.drawBitmap(shown, null, dst, p); }
        }
    }

    /** A ScrollView that never scrolls by itself to a focused child: the layout scrolls it (200 ms, one row ahead). */
    static final class Scroller extends ScrollView {
        Scroller(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }

    /** The bottom fade over the sixth row (y 1000 -> 1080): there is more below. */
    static final class PeekFade extends AppViews.Spec {
        PeekFade(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) {
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 0, h(), Ui.APP_BG & 0xFFFFFF, Ui.alpha(Ui.APP_BG, 0.8f), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w(), h(), p);
        }
    }

    /** No clubs yet: the red shirt, "Follow your clubs" and a line of why, on a white 5 % panel at the hero's place. */
    static final class EmptyPanel extends AppViews.Spec {
        EmptyPanel(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); setContentDescription(I18n.t("empty_title") + ". " + I18n.t("empty_text")); }
        @Override void paint(Canvas cv) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0x0DFFFFFF);
            cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_HERO, Ui.R_HERO, p);
            Bitmap shirt = AppArt.shirt(getContext(), 0xFFE2001A);
            cv.drawBitmap(shirt, null, new RectF(64, 56, 160, 152), AppArt.FILTER);
            Paint t = Ui.paint(Ui.COND_BOLD, 72, Ui.TXT);
            cv.drawText(I18n.t("empty_title"), 64, 245.97f, t);
            Paint b = Ui.paint(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, 0.75f));
            float y = 307.2f;
            for (String line : ClubHero.wrap(b, I18n.t("empty_text"), 900)) { cv.drawText(line, 64, y, b); y += 42; }
        }
    }
}
