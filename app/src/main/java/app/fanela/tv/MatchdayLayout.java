package app.fanela.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Matchday layout (SPEC-APP 4): a stage for the focused match in my club's colour (the opponent neutral), and rows
 * of cards below it in the viewer's own card style, the very cards the TV's home screen gets. Row 1 "Next & latest"
 * crosses every followed club; then one row per club, opening on its next match with its results to the left.
 *
 * Moving along a row re-stages the match (debounced 120 ms, then the colour crossfades in 300 ms and the words swap at
 * 120 ms); going below row 1 compresses the stage to a 240 px ribbon while the rows rise under it, so the focused card
 * is never more than a row from its stage. Rows scroll by moving their views (200 ms), the cards are bitmaps drawn once
 * per state (MatchdayCards), so nothing heavy happens per frame.
 *
 * A followed sport (F1, UFC) adds a row after the clubs' (MatchdayModel.sport), labelled with its monogram disc; its
 * cards are drawn by EventStyles in the viewer's style, and a focused one is staged by EventStage (the sport's
 * background, two identity rows) instead of the club stage's words. OK on one opens the event screen (a live one:
 * its channel's app when it is on the TV).
 */
final class MatchdayLayout implements HomeLayout {
    /**
     * The ribbon's height, where the focused row's label sits under it, and the rows' geometry (spec px). A club with
     * nothing to list has a slim row (SLIM_PITCH): its label and one quiet line, not a row of one grey tile.
     */
    static final float RIBBON = 240, RIBBON_ROW = 252, ROWS_TOP = 612, PITCH = 311.5f, SLIM_PITCH = 130, CARDS_DY = 52, SLOT = 432;
    /** Rows clip just left of the safe edge (cards scrolled past it are gone, the focused card's scale still shows). */
    static final float CLIP_X = 78;
    /** Cards in view per row: four whole, the fifth bleeding off the right edge. */
    static final int VISIBLE = 4;

    /** The stage's images for one card, made off the UI thread. */
    static final class StageArt {
        String sig;
        /** Which of the match's images were on the TV when it was made (Cards.images); missing: one was not yet. */
        String images = "";
        boolean missing;
        AppArt.Decal homeBig, awayBig, homeSmall, awaySmall;
        AppKit.Watch watch;
        String hint = "";
    }

    /** What prepare() made for a model; bind() of the same model uses it. */
    static final class Prepared {
        HomeModel model;
        List<MatchdayModel.Row> rows = new ArrayList<>();
        final Map<String, String> cardKeys = new HashMap<>();
        final Map<String, Bitmap> stages = new HashMap<>();
        final Map<String, AppArt.Decal> labelCrests = new HashMap<>();
        Bitmap live, neutral;
        /** The stage the loading screen wears (firstStage): a followed club's id, and whether that is the live stage. */
        String firstClub;
        boolean firstLive;
    }

    /** One row on screen: its label, its strip of card cells, the card it holds the focus on and how far it scrolled. */
    static final class RowHolder {
        MatchdayModel.Row row;
        String structure = "";
        FrameLayout box, strip;
        MatchdayViews.RowLabel label;
        final List<MatchdayViews.CardCell> cells = new ArrayList<>();
        int focus, scroll;
        ValueAnimator scrollAnim;
    }

    private Context c;
    private Host host;
    private float s;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root, rowsClip, rowsInner;
    /** An invisible focusable that holds the focus while nothing on screen can take it (before the data, loading). */
    private View park;
    private MatchdayViews.StageView stageView;
    private MatchdayViews.StageInfo info;
    /** The stage's words for an F1 session or a UFC card (shown instead of info while one is focused). */
    private EventStage.Info eventInfo;
    /** EventStage's images for the event cards visited lately. */
    private final Map<String, EventStage.Art> evArt = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, EventStage.Art>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, EventStage.Art> e) { return size() > 16; }
    });
    private AppViews.Masthead mast;
    private MatchdayViews.Chip chip;
    private MatchdayViews.PeekFade peek;
    private MatchdayViews.EmptyPanel emptyPanel;
    private AppViews.Button choose;
    private AppViews.FocusLayer focusLayer;
    private final List<RowHolder> rows = new ArrayList<>();

    private volatile MatchdayCards cards;
    private volatile Prepared pending;
    private Prepared shown;
    /** Stage images of the cards visited lately (a bounded LRU, so AppArt's own cache can let older crests go). */
    private final Map<String, StageArt> art = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, StageArt>(48, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, StageArt> e) { return size() > 32; }
    });
    private final Set<String> artWanted = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ExecutorService artWorker = Executors.newSingleThreadExecutor();
    /** The focused card's item key, read by prepare() on the display thread (its stage art comes first). */
    private volatile String focusedItem;

    private int focusRow = -1;
    private float ribbon, rowsTy;
    private ValueAnimator vAnim, pulse, livePulse;
    private boolean placed, blocked, empty, paused;
    private final Runnable restage = new Runnable() { public void run() { stage(true); } };

    // ---------- prepare (display thread) ----------
    private MatchdayCards cards(Context c) {
        MatchdayCards k = cards;
        if (k == null) synchronized (this) { if (cards == null) cards = new MatchdayCards(c); k = cards; }
        return k;
    }

    @Override public void prepare(Context c, HomeModel m) {
        Ui.fonts(c);
        AppArt.shirt(c, Ui.TXT);
        Prepared p = new Prepared();
        p.model = m;
        if (m.empty()) {
            p.neutral = MatchdayStage.get(c, "", null, MatchdayStage.Mode.NEUTRAL);
            pending = p;
            return;
        }
        MatchdayCards k = cards(c);
        String style = Appearance.cardStyle(c);
        if (!style.equals(k.style())) { k.clear(); k.style(style); }
        p.rows = MatchdayModel.rows(m);
        boolean anyLive = false;
        for (HomeModel.ClubData d : m.clubs) {
            p.stages.put(d.club.id, MatchdayStage.get(c, d.club.id, d.colors, MatchdayStage.Mode.CLUB));
            p.labelCrests.put(d.club.id, AppArt.crest(c, d.club.crest, d.club.name, 36, true));
        }
        for (SportModel.SportData d : m.sports) {
            p.stages.put(d.id(), sportStage(c, d));
            p.labelCrests.put(d.id(), SportViews.monogramDecal(c, d.sport, 36));
        }
        for (MatchdayModel.Row r : p.rows) for (MatchdayModel.Item it : r.items) {
            p.cardKeys.put(it.key(), cardKey(c, style, it, m.now));
            anyLive |= it.live() && it.ev == null;
        }
        if (anyLive) p.live = MatchdayStage.get(c, "", null, MatchdayStage.Mode.LIVE);
        firstStage(c, p);
        // what the first screen shows: row 1's cards now (the peeking row's follow under the fade), and their stages
        List<MatchdayCards.Job> jobs = new ArrayList<>();
        List<MatchdayModel.Item> staged = new ArrayList<>();
        for (int r = 0; r < Math.min(2, p.rows.size()); r++) {
            MatchdayModel.Row row = p.rows.get(r);
            int from = initialScroll(row);
            for (int i = from; i < Math.min(row.items.size(), from + VISIBLE + 1); i++) {
                MatchdayModel.Item it = row.items.get(i);
                if (r == 0 || (p.rows.get(0).items.isEmpty() && r == 1))
                    jobs.add(MatchdayCards.job(p.cardKeys.get(it.key()), it, m.now));
                if (r == 0 || i == row.start) staged.add(it);
            }
        }
        String f = focusedItem;
        for (MatchdayModel.Row r : p.rows) for (MatchdayModel.Item it : r.items) if (it.key().equals(f)) staged.add(0, it);
        for (MatchdayModel.Item it : staged) { if (it.ev != null) eventArt(c, it); else makeArt(c, it, m.now); }
        k.drawNow(jobs);
        pending = p;
    }

    /**
     * The first frame (UI thread): the loading rows (skeleton labels and card slots) on the stage the launch card had
     * last time (firstStage), so the colour does not jump when the data comes. The cards, the rows' crests and the
     * other stages come with prepare().
     */
    @Override public void first(Context c, HomeModel m) {
        Ui.fonts(c);
        Prepared p = new Prepared();
        p.model = m;
        if (m.empty()) {
            p.neutral = MatchdayStage.get(c, "", null, MatchdayStage.Mode.NEUTRAL);
            pending = p;
            return;
        }
        p.rows = MatchdayModel.rows(m);
        firstStage(c, p);
        pending = p;
    }

    // ---------- the first stage ----------
    /** Where the launch card's stage is kept between starts. */
    private static SharedPreferences stagePrefs(Context c) { return c.getSharedPreferences("matchday", Context.MODE_PRIVATE); }

    /** A saved live stage is believed for this long: a match is over by then, and its club's colour is the better guess. */
    static final long LIVE_KEPT = 3 * 3600000L;

    /**
     * The stage the loading screen wears: the launch card's (row 1's first card, where the focus lands once the data
     * comes) as it was last time, so a cold start does not open on the favourite's colour and then jump to another
     * club's; the favourite's the first time, or when that club is no longer followed. Live: the one live stage.
     */
    private void firstStage(Context c, Prepared p) {
        SharedPreferences sp = stagePrefs(c);
        String saved = sp.getString("club", null);
        String id = firstClub(saved, p.model);
        p.firstClub = id;
        p.firstLive = id.equals(saved) && firstLive(sp.getLong("live_at", 0), p.model.now) && p.model.club(id) != null;
        HomeModel.ClubData d = p.model.club(id);
        if (!p.stages.containsKey(id)) {
            SportModel.SportData sd = p.model.sport(id);
            if (d != null) p.stages.put(id, MatchdayStage.get(c, id, d.colors, MatchdayStage.Mode.CLUB));
            else if (sd != null) p.stages.put(id, sportStage(c, sd));
        }
        if (p.firstLive && p.live == null) p.live = MatchdayStage.get(c, "", null, MatchdayStage.Mode.LIVE);
    }

    /** The club whose stage opens: the saved launch card's club while it is followed, else the favourite, else the first. */
    static String firstClub(String saved, HomeModel m) {
        if (saved != null && (m.club(saved) != null || m.sport(saved) != null)) return saved;
        HomeModel.ClubData fav = m.club(m.favourite);
        if (fav == null && m.clubs.isEmpty()) return m.sports.get(0).id();  // sports alone
        return (fav != null ? fav : m.clubs.get(0)).club.id;
    }

    /** A launch card saved live at liveAt still opens on the live stage now. */
    static boolean firstLive(long liveAt, long now) { return liveAt > 0 && now >= liveAt && now - liveAt < LIVE_KEPT; }

    /**
     * Keeps the launch card's stage for the next start: its club, and since when it is live (0: not live). Written
     * only when that changes, not on every refresh.
     */
    private void saveFirstStage(Prepared p) {
        int[] at = MatchdayModel.launch(p.rows);
        if (at == null || c == null) return;
        MatchdayModel.Item it = p.rows.get(at[0]).items.get(at[1]);
        SharedPreferences sp = stagePrefs(c);
        String id = it.stageId();
        boolean same = id.equals(sp.getString("club", null));
        long was = sp.getLong("live_at", 0);
        long liveAt = !it.live() ? 0 : same && was > 0 ? was : p.model.now;
        if (same && liveAt == was) return;
        sp.edit().putString("club", id).putLong("live_at", liveAt).apply();
    }

    /** The stage's crests (150 and the ribbon's 72), its where-to-watch slot and the OK hint for one card. Any thread. */
    private StageArt makeArt(Context c, MatchdayModel.Item it, long now) {
        String sig = it.m.signature(), images = Cards.images(c, it.m);
        StageArt a = art.get(it.key());
        if (a != null && sig.equals(a.sig) && images.equals(a.images)) return a;
        a = new StageArt();
        a.sig = sig;
        a.images = images;
        a.missing = images.contains("false");
        Match m = it.m;
        a.homeBig = AppArt.crest(c, m.homeCrest, m.home, 150, false);
        a.awayBig = AppArt.crest(c, m.awayCrest, m.away, 150, false);
        a.homeSmall = a.homeBig;  // the ribbon draws them at 72
        a.awaySmall = a.awayBig;
        if (MatchFacts.watchable(m)) a.watch = AppKit.watch(c, m);
        boolean app = it.live() && m.channelKnown && m.channelApp != null && HomeRows.installed(c, m.channelApp);
        a.hint = app ? I18n.f("open_app", Cards.appName(m.channelApp)) : AppText.t("details");
        art.put(it.key(), a);
        return a;
    }

    /** A card's cache key: a club's match card, or a sport's event card. */
    static String cardKey(Context c, String style, MatchdayModel.Item it, long now) {
        if (it.ev != null) return MatchdayCards.key(style, it.ev, now);
        return MatchdayCards.key(c, style, it.club.club, it.m, it.club.stale, now);
    }

    /** A sport's stage background (its hero's circuit as the watermark). */
    private static Bitmap sportStage(Context c, SportModel.SportData d) {
        SportEvent e = d.hero != null ? d.hero.e : d.events.isEmpty() ? null : d.events.get(0);
        return EventStage.background(c, d.sport, SportViews.outline(c, e));
    }

    /** EventStage's images for an event card (made again when the event changes). Any thread. */
    private EventStage.Art eventArt(Context c, MatchdayModel.Item it) {
        EventStage.Art a = evArt.get(it.key());
        String sig = it.ev.e.signature() + it.ev.sessionId + it.ev.last;
        if (a != null && sig.equals(a.sig)) return a;
        a = EventStage.art(c, it.ev);
        evArt.put(it.key(), a);
        return a;
    }

    /** The scroll a row opens with: its opening card in the first slot, unless that leaves the row half empty. */
    static int initialScroll(MatchdayModel.Row r) {
        return Math.max(0, Math.min(r.start, r.items.size() - VISIBLE));
    }

    // ---------- views ----------
    @Override public View build(Context c, Host host) {
        this.c = c;
        this.host = host;
        this.s = Ui.scale(c);
        Ui.fonts(c);
        cards(c).listener(new MatchdayCards.Listener() { public void ready(String key) { cardReady(key); } });
        root = new FrameLayout(c);
        root.setBackgroundColor(Ui.APP_BG);
        park = new View(c);
        park.setFocusable(true);
        park.setFocusableInTouchMode(true);
        park.setDefaultFocusHighlightEnabled(false);
        park.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(park, new FrameLayout.LayoutParams(1, 1));
        // the rows first: the stage covers them as they rise into the ribbon
        rowsClip = new FrameLayout(c);
        rowsClip.setClipChildren(true);
        root.addView(rowsClip, AppViews.at(c, CLIP_X, 0, Ui.W - CLIP_X, Ui.H));
        rowsInner = new FrameLayout(c);
        rowsInner.setClipChildren(false);
        rowsInner.setTranslationX(-CLIP_X * s);
        rowsClip.addView(rowsInner, new FrameLayout.LayoutParams(Math.round(Ui.W * s), ViewGroup.LayoutParams.MATCH_PARENT));
        peek = new MatchdayViews.PeekFade(c);
        root.addView(peek, AppViews.at(c, 0, 1000, Ui.W, 80));
        stageView = new MatchdayViews.StageView(c);
        root.addView(stageView, AppViews.at(c, 0, 0, Ui.W, MatchdayStage.HEIGHT));
        info = new MatchdayViews.StageInfo(c);
        root.addView(info, AppViews.at(c, 0, 0, Ui.W, MatchdayStage.HEIGHT));
        eventInfo = new EventStage.Info(c);
        eventInfo.setVisibility(View.GONE);
        root.addView(eventInfo, AppViews.at(c, 0, 0, Ui.W, MatchdayStage.HEIGHT));
        emptyPanel = new MatchdayViews.EmptyPanel(c);
        root.addView(emptyPanel, AppViews.at(c, 96, 140, 1728, 504));
        choose = new AppViews.Button(c, I18n.t("choose_clubs"));
        choose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { MatchdayLayout.this.host.chooseClubs(); } });
        root.addView(choose, AppViews.at(c, 148, 520, AppViews.Button.width(I18n.t("choose_clubs")) + 24, 88));
        emptyPanel.setVisibility(View.GONE);
        choose.setVisibility(View.GONE);
        mast = new AppViews.Masthead(c);
        root.addView(mast, AppViews.at(c, 0, 0, Ui.W, 110));
        String settings = I18n.t("settings");
        float cw = MatchdayViews.Chip.width(settings);
        chip = new MatchdayViews.Chip(c, settings);
        chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { MatchdayLayout.this.host.openSettings(); } });
        root.addView(chip, AppViews.at(c, 1824 - cw - MatchdayViews.Chip.PAD, 55 - MatchdayViews.Chip.PAD, cw + 2 * MatchdayViews.Chip.PAD, 46 + 2 * MatchdayViews.Chip.PAD));
        mast.right = 1824 - cw - 28;
        focusLayer = new AppViews.FocusLayer(c);
        root.addView(focusLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        park.requestFocus();
        return root;
    }

    // ---------- binding ----------
    @Override public void bind(HomeModel m) {
        Prepared p = pending;
        if (p == null || p.model != m) {  // not prepared (it always is): the rows without images
            p = new Prepared();
            p.model = m;
            p.rows = MatchdayModel.rows(m);
            String style = cards(c).style() == null ? Appearance.cardStyle(c) : cards.style();
            for (MatchdayModel.Row r : p.rows) for (MatchdayModel.Item it : r.items) p.cardKeys.put(it.key(), cardKey(c, style, it, m.now));
        }
        String was = focusKey();
        shown = p;
        mast.shirt = AppArt.shirt(c, Ui.TXT);
        mast.ink = Ui.TXT;
        mast.statusInk = Ui.TXT;
        mast.set(m.status, m.statusText);
        mast.invalidate();
        boolean nowEmpty = m.empty();
        if (nowEmpty != empty || !placed) showEmpty(nowEmpty);
        empty = nowEmpty;
        if (empty) {
            stageView.show(p.neutral != null ? p.neutral : MatchdayStage.cached(MatchdayStage.key("", null, MatchdayStage.Mode.NEUTRAL)), false);
            if (!blocked && (was == null || !"chip".equals(was))) choose.requestFocus();
            placed = true;
            livePulse();
            return;
        }
        rebuildRows(p);
        boolean loading = !p.rows.isEmpty() && p.rows.get(0).loading;
        if (!loading) saveFirstStage(p);
        pulse(anyLoading());
        if (blocked) { stage(false); updateWindow(); return; }
        View f = root.findFocus();
        if (f instanceof MatchdayViews.CardCell && holderOf(f) != null && f.isFocusable()) { /* kept: nothing moves */ }
        else if (was != null && !was.equals("park") && !was.equals("choose")) focus(was);
        else if (!loading) {
            int[] at = MatchdayModel.launch(p.rows);
            if (at != null) focusCell(at[0], at[1], false);
            else if (!rows.isEmpty() && !rows.get(0).cells.isEmpty() && rows.get(0).cells.get(0).isFocusable()) focusCell(0, 0, false);
            else chip.requestFocus();
        } else park.requestFocus();
        placed = true;
        stage(false);
        updateWindow();
    }

    private boolean anyLoading() {
        for (RowHolder h : rows) if (h.row.loading) return true;
        return false;
    }

    private void showEmpty(boolean on) {
        int main = on ? View.GONE : View.VISIBLE, panel = on ? View.VISIBLE : View.GONE;
        rowsClip.setVisibility(main);
        info.setVisibility(main);
        if (on) eventStage(false);
        peek.setVisibility(main);
        emptyPanel.setVisibility(panel);
        choose.setVisibility(panel);
        choose.setFocusable(on);
        if (on) { setRibbon(0, false); focusRow = -1; }
    }

    /** Rows whose cards changed are rebuilt; the others keep their views, focus and scroll, and update in place. */
    private void rebuildRows(Prepared p) {
        Map<String, RowHolder> old = new HashMap<>();
        for (RowHolder h : rows) old.put(h.row.id, h);
        List<RowHolder> next = new ArrayList<>();
        boolean changed = rows.size() != p.rows.size();
        for (int r = 0; r < p.rows.size(); r++) {
            MatchdayModel.Row row = p.rows.get(r);
            RowHolder h = old.remove(row.id);
            String st = row.structure();
            if (h == null || !h.structure.equals(st)) {
                RowHolder fresh = makeRow(row);
                if (h != null) {  // the same row with other cards: keep the focus on the same card if it is still there
                    String fk = h.focus < h.row.items.size() ? h.row.items.get(h.focus).key() : null;
                    int i = fk == null ? -1 : row.indexOf(fk);
                    fresh.focus = i >= 0 ? i : row.start;
                    fresh.scroll = Math.max(0, Math.min(fresh.focus, Math.max(h.scroll, fresh.focus - VISIBLE + 1)));
                    // a row the viewer never scrolled opens where its new cards open (so Back still sees it at home)
                    int init = initialScroll(row);
                    boolean wasHome = h.scroll == initialScroll(h.row);
                    if (i < 0 || (wasHome && fresh.focus >= init && fresh.focus < init + VISIBLE)) fresh.scroll = init;
                }
                h = fresh;
                changed = true;
            }
            h.row = row;
            h.structure = st;
            next.add(h);
            if (r < rows.size() && rows.get(r) != h) changed = true;
        }
        changed |= !old.isEmpty();
        if (changed) {
            rowsInner.removeAllViews();
            rows.clear();
            rows.addAll(next);
            for (int r = 0; r < rows.size(); r++) {
                RowHolder h = rows.get(r);
                rowsInner.addView(h.box, AppViews.at(c, 0, rowTop(r) - 20, Ui.W, pitch(h.row) + 40));
                h.strip.setTranslationX(stripX(h.scroll));
            }
            // as tall as its rows: a clipping parent clips a child to its own bounds, however far it is translated
            ViewGroup.LayoutParams lp = rowsInner.getLayoutParams();
            lp.height = Math.round((rowTop(rows.size()) + 120) * s);
            rowsInner.setLayoutParams(lp);
        }
        for (int r = 0; r < rows.size(); r++) bindRow(rows.get(r), p);
    }

    private RowHolder makeRow(MatchdayModel.Row row) {
        final RowHolder h = new RowHolder();
        h.row = row;
        h.box = new FrameLayout(c);
        h.box.setClipChildren(false);
        h.box.setClipToPadding(false);
        h.label = new MatchdayViews.RowLabel(c);
        h.box.addView(h.label, AppViews.at(c, 0, 20, Ui.W, 40));
        h.strip = new FrameLayout(c);
        h.strip.setClipChildren(false);
        int n = row.loading ? VISIBLE + 1 : Math.max(1, row.items.size());
        float m = MatchdayViews.CardCell.M;
        boolean slim = slim(row);
        float cellW = slim ? 1728 : MatchdayCards.W, cellH = slim ? MatchdayViews.CardCell.LINE_H : MatchdayCards.H;
        h.box.addView(h.strip, AppViews.at(c, -m, 20 + CARDS_DY - m, slim ? cellW + 2 * m : n * SLOT + 2 * m, cellH + 2 * m));
        for (int i = 0; i < n; i++) {
            final MatchdayViews.CardCell cell = new MatchdayViews.CardCell(c, new MatchdayViews.Cards() {
                public Bitmap get(String key) { MatchdayCards k = cards; return k == null ? null : k.get(key); }
            });
            cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { open(cell); } });
            cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) { if (has) focused(h, cell); }
            });
            h.cells.add(cell);
            h.strip.addView(cell, AppViews.at(c, i * SLOT, 0, cellW + 2 * m, cellH + 2 * m));
        }
        h.focus = row.start;
        h.scroll = initialScroll(row);
        return h;
    }

    private void bindRow(RowHolder h, Prepared p) {
        MatchdayModel.Row row = h.row;
        if (row.sport != null) h.label.set(row.sport.label, p.labelCrests.get(row.id), row.loading, 200);
        else if (row.club == null) h.label.set(AppText.t("next_latest"), null, row.loading, 260);
        else h.label.set(row.club.club.label(), p.labelCrests.get(row.club.club.id), row.loading, 200);
        for (int i = 0; i < h.cells.size(); i++) {
            MatchdayViews.CardCell cell = h.cells.get(i);
            if (row.loading) cell.skeleton();
            else if (row.items.isEmpty()) {
                String word = emptyWord(row, p.model);
                cell.line(I18n.t(lineWord(word)), spoken(row.sport != null ? row.sport.label : row.club == null ? AppText.t("next_latest") : row.club.club.label(), I18n.t(word)));
            }
            else {
                MatchdayModel.Item it = row.items.get(i);
                String key = p.cardKeys.get(it.key());
                cell.bind(it, key, p.model.now);
                if (it.ev != null) cell.setContentDescription(SportText.speak(it.ev.e, it.ev.session(), p.model.now, it.ev.sport.hidden));
            }
        }
    }

    /**
     * What an empty row says (its cells, and the stage when one is focused): "Couldn't load…" when its data never
     * arrived, else "No fixtures…", as the Club hero says it. A club's row: that club failed. Row 1: no followed club
     * loaded (every one failed), so it can't say there is nothing scheduled.
     */
    static String emptyWord(MatchdayModel.Row row, HomeModel m) {
        if (row.club != null) return row.club.kind == HomeModel.Kind.FAILED ? "load_failed" : "no_fixtures";
        if (row.sport != null) return "no_fixtures";
        boolean failed = m != null && !m.clubs.isEmpty();
        if (failed) for (HomeModel.ClubData d : m.clubs) failed &= d.kind == HomeModel.Kind.FAILED;
        return failed ? "load_failed" : "no_fixtures";
    }

    /** What TalkBack says on an empty row's line: whose row it is, then the whole sentence ("AEK Athens. Couldn't load..."). */
    static String spoken(String label, String sentence) { return label == null || label.isEmpty() ? sentence : label + ". " + sentence; }

    /** The short form of an empty row's words for its slim line ("Couldn't load fixtures. Trying again shortly."). */
    static String lineWord(String word) { return "load_failed".equals(word) ? "load_failed_short" : word; }

    /** A row with nothing to list, collapsed to its label and one line (loading rows keep their card slots). */
    static boolean slim(MatchdayModel.Row row) { return !row.loading && row.items.isEmpty(); }

    static float pitch(MatchdayModel.Row row) { return slim(row) ? SLIM_PITCH : PITCH; }

    /** Where row r starts (spec px, before any rise): the rows above it, slim or not. */
    private float rowTop(int r) {
        float y = ROWS_TOP;
        for (int i = 0; i < r && i < rows.size(); i++) y += pitch(rows.get(i).row);
        return y;
    }

    /** The strip's translation for a scroll: the scroll-th card in the first slot (x 96). */
    private float stripX(int scroll) { return (96 - scroll * SLOT) * s; }

    // ---------- focus ----------
    private void focused(RowHolder h, MatchdayViews.CardCell cell) {
        int r = rows.indexOf(h), i = h.cells.indexOf(cell);
        if (r < 0 || i < 0) return;
        h.focus = i;
        boolean moved = r != focusRow;
        ensureVisible(h, i, true);
        if (moved) setRowFocus(r, true);
        focusedItem = cell.item == null ? null : cell.item.key();
        ui.removeCallbacks(restage);
        ui.postDelayed(restage, 120);  // the stage follows once the focus rests (holding a key never re-stages per card)
        updateWindow();
    }

    private void focusCell(int r, int i, boolean animate) {
        if (r < 0 || r >= rows.size()) return;
        RowHolder h = rows.get(r);
        if (h.cells.isEmpty()) return;
        i = Math.max(0, Math.min(i, h.cells.size() - 1));
        MatchdayViews.CardCell cell = h.cells.get(i);
        if (!cell.isFocusable()) return;
        h.focus = i;
        ensureVisible(h, i, animate);
        setRowFocus(r, animate);
        cell.requestFocus();
    }

    /** Scrolls the row when the card would leave x 96 -> 1824 (200 ms, decelerating): the fifth card then enters. */
    private void ensureVisible(final RowHolder h, int i, boolean animate) {
        int scroll = h.scroll;
        if (i < scroll) scroll = i;
        else if (i > scroll + VISIBLE - 1) scroll = i - VISIBLE + 1;
        if (scroll == h.scroll && Math.abs(h.strip.getTranslationX() - stripX(scroll)) < 1) return;
        h.scroll = scroll;
        if (h.scrollAnim != null) h.scrollAnim.cancel();
        if (!animate) { h.strip.setTranslationX(stripX(scroll)); return; }
        h.scrollAnim = ValueAnimator.ofFloat(h.strip.getTranslationX(), stripX(scroll));
        h.scrollAnim.setDuration(Ui.SCROLL_MS);
        h.scrollAnim.setInterpolator(Ui.DECELERATE);
        h.scrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { h.strip.setTranslationX((float) a.getAnimatedValue()); }
        });
        h.scrollAnim.start();
    }

    /**
     * Row 1 under the full stage; below it, the ribbon, with the focused row right under it (300 ms). A club's slim
     * row (nothing to list) keeps the full stage, which shows that club and its sentence, with the row under it where
     * row 1 sits: a ribbon over one line of words left most of the screen empty.
     */
    private void setRowFocus(int r, boolean animate) {
        focusRow = r;
        boolean ribbon = ribbon(r, slim(rows.get(r).row));
        setRibbon(ribbon ? 1 : 0, (ribbon ? RIBBON_ROW : ROWS_TOP) - rowTop(r), animate);
    }

    /** The stage is the ribbon for row r: below row 1, unless the row is slim (then it stays full height). */
    static boolean ribbon(int r, boolean slim) { return r >= 1 && !slim; }

    private void setRibbon(float target, boolean animate) { setRibbon(target, target > 0 ? rowsTy : 0, animate); }

    private void setRibbon(final float target, final float ty, boolean animate) {
        if (vAnim != null) vAnim.cancel();
        if (!animate || (target == ribbon && ty == rowsTy)) { applyRibbon(target, ty); return; }
        final float r0 = ribbon, t0 = rowsTy;
        vAnim = ValueAnimator.ofFloat(0, 1);
        vAnim.setDuration(Ui.SWITCH_MS);
        vAnim.setInterpolator(Ui.FAST_OUT_SLOW_IN);
        vAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float k = (float) a.getAnimatedValue();
                applyRibbon(r0 + (target - r0) * k, t0 + (ty - t0) * k);
            }
        });
        vAnim.start();
    }

    private void applyRibbon(float r, float ty) {
        ribbon = r;
        rowsTy = ty;
        stageView.ribbon(r);
        info.ribbon(r);
        if (eventInfo != null) eventInfo.ribbon(r);
        rowsInner.setTranslationY(ty * s);
    }

    /** OK on a card: the match screen; a live card opens the channel's app when it is on the TV. */
    private void open(MatchdayViews.CardCell cell) {
        if (cell.item == null) return;
        MatchdayModel.Item it = cell.item;
        if (it.ev != null) {  // a sport's card: its live session's app when it is on the TV, else the event screen
            String app = it.live() ? SportViews.installedApp(c, it.ev.session()) : null;
            if (app != null && OpenActivity.launch(c, app)) return;
            host.openEvent(it.ev.e, it.ev.sessionId);
            return;
        }
        if (it.live()) host.openLive(it.m, it.club.club);
        else host.openMatch(it.m, it.club.club);
    }

    private MatchdayViews.CardCell focusedCell() {
        View f = root == null ? null : root.findFocus();
        return f instanceof MatchdayViews.CardCell ? (MatchdayViews.CardCell) f : null;
    }

    private RowHolder holderOf(View cell) {
        for (RowHolder h : rows) if (h.cells.contains(cell)) return h;
        return null;
    }

    // ---------- the stage ----------
    /** Shows the focused card's match on the stage (the colour crossfades, the words swap); the LIVE dot follows. */
    private void stage(boolean animate) {
        stageWords(animate);
        livePulse();
    }

    private void stageWords(boolean animate) {
        if (shown == null || empty) return;
        MatchdayViews.CardCell cell = focusedCell();
        RowHolder h = cell == null ? null : holderOf(cell);
        if (cell == null) {  // the chip or nothing focused: the stage keeps its card; while loading, the skeleton
            boolean loading = !rows.isEmpty() && rows.get(0).row.loading;
            eventStage(false);
            if (loading) {
                MatchdayViews.Stage st = new MatchdayViews.Stage();
                st.kind = HomeModel.Kind.LOADING;
                st.key = "loading";
                info.show(st, animate);
                Bitmap bg = shown.firstLive && shown.live != null ? shown.live : shown.stages.get(shown.firstClub);
                if (bg == null) bg = shown.stages.get(firstClub(null, shown.model));
                stageView.show(bg, animate);
            } else if (info.current().item != null || eventInfo.getVisibility() == View.VISIBLE) refresh();
            else {
                int[] at = MatchdayModel.launch(shown.rows);
                if (at != null) showItem(shown.rows.get(at[0]).items.get(at[1]), animate);
            }
            return;
        }
        if (cell.item == null) {  // a club with nothing to list (row 1 too, when no club has anything)
            eventStage(false);
            MatchdayModel.Row row = h == null ? null : h.row;
            if (row != null && row.sport != null) {  // a sport with nothing listed: its mark and the sentence
                MatchdayViews.Stage st = new MatchdayViews.Stage();
                st.kind = HomeModel.Kind.NONE;
                st.key = "none:" + row.id;
                st.home = row.sport.label;
                st.message = I18n.t(emptyWord(row, shown.model));
                st.messageShort = I18n.t(lineWord(emptyWord(row, shown.model)));
                st.mine = SportViews.monogramDecal(c, row.sport.sport, 150);
                st.mineSmall = st.mine;
                info.show(st, animate);
                stageView.show(shown.stages.get(row.id), animate);
                return;
            }
            HomeModel.ClubData cd = row == null ? null : row.club != null ? row.club : shown.model.club(shown.model.favourite);
            if (cd == null && !shown.model.clubs.isEmpty()) cd = shown.model.clubs.get(0);
            if (cd == null) return;
            MatchdayViews.Stage st = new MatchdayViews.Stage();
            st.kind = cd.kind == HomeModel.Kind.FAILED ? HomeModel.Kind.FAILED : HomeModel.Kind.NONE;
            st.key = "none:" + cd.club.id;
            st.home = cd.club.label();
            String word = row == null ? null : emptyWord(row, shown.model);
            st.message = word == null ? (cell.message == null ? "" : cell.message) : I18n.t(word);  // the full stage: the whole sentence
            st.messageShort = word == null ? st.message : I18n.t(lineWord(word));                 // the ribbon: one line
            StageArt a = clubArt(cd.club);
            if (a != null) { st.mine = a.homeBig; st.mineSmall = a.homeSmall; }
            info.show(st, animate);
            stageView.show(shown.stages.get(cd.club.id), animate);
            return;
        }
        showItem(cell.item, animate);
    }

    /** The club's own crest (150 and the ribbon's 72) for a club with nothing listed; made in the background the first time. */
    private StageArt clubArt(final Teams.Club k) {
        final String key = "club:" + k.id;
        final StageArt a = art.get(key);
        if (a != null && !a.missing) return a;
        if (artWanted.add(key)) background(new Runnable() { public void run() {  // made, or its crest has arrived since
            boolean have = k.crest == null || Cards.have(c, k.crest);
            if (a == null || have) {
                StageArt x = new StageArt();
                x.homeBig = AppArt.crest(c, k.crest, k.name, 150, false);
                x.homeSmall = x.homeBig;
                x.missing = !have;
                art.put(key, x);
                ui.post(new Runnable() { public void run() { stage(false); } });
            }
            artWanted.remove(key);
        }});
        return a;
    }

    /** Shows the event stage's words (an F1 session, a UFC card) instead of the club stage's, or back. */
    private void eventStage(boolean on) {
        if (eventInfo == null) return;
        eventInfo.setVisibility(on ? View.VISIBLE : View.GONE);
        info.setVisibility(on || empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) info.setVisibility(View.GONE);
    }

    /** Stages a sport's card: its background and EventStage's words; the images are made in the background first. */
    private void showEvent(final MatchdayModel.Item it, boolean animate) {
        final EventStage.Art a = evArt.get(it.key());
        String sig = it.ev.e.signature() + it.ev.sessionId + it.ev.last;
        if (a == null || !sig.equals(a.sig)) {
            if (artWanted.add(it.key())) {
                background(new Runnable() { public void run() {
                    try { eventArt(c, it); } catch (Throwable t) { android.util.Log.w("Fanela", "event stage art", t); }
                    artWanted.remove(it.key());
                    ui.post(new Runnable() { public void run() {
                        MatchdayViews.CardCell f = focusedCell();
                        if (f != null && f.item != null && f.item.key().equals(it.key())) stage(true);
                    }});
                }});
            }
            if (a == null) return;
        }
        eventStage(true);
        eventInfo.show(it.ev, a, shown.model.now);
        stageView.show(shown.stages.get(it.stageId()), animate);
        livePulse();
    }

    private void showItem(final MatchdayModel.Item it, boolean animate) {
        if (it.ev != null) { showEvent(it, animate); return; }
        eventStage(false);
        final StageArt a = art.get(it.key());
        // not made yet, or made before a crest or logo was on the TV: made (again) in the background, then staged
        if (a == null || !it.m.signature().equals(a.sig) || a.missing) {
            final long now = shown.model.now;
            if (artWanted.add(it.key())) {
                background(new Runnable() { public void run() {
                    StageArt made = a;
                    try { made = makeArt(c, it, now); } catch (Throwable t) { android.util.Log.w("Fanela", "stage art", t); }
                    artWanted.remove(it.key());
                    if (made == a) return;  // nothing new has arrived
                    ui.post(new Runnable() { public void run() {
                        MatchdayViews.CardCell f = focusedCell();
                        if (f != null && f.item != null && f.item.key().equals(it.key())) stage(true);
                    }});
                }});
            }
            if (a == null) return;
        }
        MatchdayViews.Stage st = words(it, a, shown.model);
        info.show(st, animate);
        Bitmap bg = it.live() && shown.live != null ? shown.live : shown.stages.get(it.club.club.id);
        stageView.show(bg, animate);
        livePulse();
    }

    /** Runs on the stage-art thread; nothing once the layout is released (a rebuild, the screen closing). */
    private void background(Runnable r) {
        try { artWorker.execute(r); } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    /** A live update or a refresh: the same card's stage with its new words, in place. */
    private void refresh() {
        if (eventInfo != null && eventInfo.getVisibility() == View.VISIBLE) {
            for (MatchdayModel.Row r : shown.rows) for (MatchdayModel.Item it : r.items) if (it.key().equals(eventInfo.key)) { showItem(it, false); return; }
            return;
        }
        MatchdayViews.Stage cur = info.current();
        if (cur.item == null) return;
        for (MatchdayModel.Row r : shown.rows) for (MatchdayModel.Item it : r.items) if (it.key().equals(cur.item.key())) { showItem(it, false); return; }
    }

    /** The stage's words for a card. */
    static MatchdayViews.Stage words(MatchdayModel.Item it, StageArt a, HomeModel h) {
        MatchdayViews.Stage st = new MatchdayViews.Stage();
        Match m = it.m;
        st.item = it;
        st.m = m;
        st.key = it.key();
        st.kind = it.kind();
        st.lead = MatchdayModel.lead(it);
        st.parts.addAll(MatchdayModel.eyebrow(it));
        st.team = m.team;
        st.both = it.club.bothSports;
        st.clock = it.live() ? MatchdayModel.liveClock(m) : "";
        st.home = I18n.team(m.home);
        st.away = I18n.team(m.away);
        st.leader = MatchdayModel.leader(m);
        st.homeBig = a.homeBig; st.awayBig = a.awayBig; st.homeSmall = a.homeSmall; st.awaySmall = a.awaySmall;
        st.watch = a.watch;
        st.hint = a.hint;
        if (!m.tbd) {
            String t = I18n.time(m.start);
            int sp = t.indexOf(' ');
            st.time = sp > 0 ? t.substring(0, sp) : t;
            st.suffix = sp > 0 ? t.substring(sp + 1) : "";
        }
        st.rel = it.off() ? "" : AppText.relativeHours(h.now, m.start);
        st.date = AppText.longDate(m.start);
        return st;
    }

    // ---------- the cards wanted ----------
    /**
     * Tells the card cache what is on screen and near it, nearest first: the focused row's cards in view, the next
     * row's, then a card more on each side, then the rows beyond. Everything else may be recycled.
     */
    private void updateWindow() {
        MatchdayCards k = cards;
        if (k == null || shown == null) return;
        List<MatchdayCards.Job> jobs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int fr = Math.max(0, focusRow);
        int[][] passes = {{fr, 0, 0}, {fr + 1, 0, 0}, {fr, 2, 2}, {fr + 1, 1, 1}, {fr - 1, 0, 0}, {fr + 2, 0, 0}};
        for (int[] p : passes) {
            if (p[0] < 0 || p[0] >= rows.size()) continue;
            RowHolder h = rows.get(p[0]);
            int from = h.scroll - p[1], to = h.scroll + VISIBLE + p[2];
            for (int i = Math.max(0, from); i <= Math.min(to, h.row.items.size() - 1); i++) {
                MatchdayModel.Item it = h.row.items.get(i);
                String key = shown.cardKeys.get(it.key());
                if (key == null || !seen.add(key)) continue;
                jobs.add(MatchdayCards.job(key, it, shown.model.now));
            }
        }
        k.want(jobs);
    }

    private void cardReady(String key) {
        for (RowHolder h : rows) for (MatchdayViews.CardCell cell : h.cells) if (key.equals(cell.cardKey)) cell.ready();
    }

    /** Skeleton blocks pulse (.5 -> .9 -> .5, 1.2 s) while anything loads; nothing animates otherwise. */
    private void pulse(boolean on) {
        if (!on) {
            if (pulse != null) { pulse.cancel(); pulse = null; }
            info.pulse = 1;
            return;
        }
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0.55f, 1f);
        pulse.setDuration(Ui.SKELETON_MS / 2);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                info.pulse = v;
                if (info.current().kind == HomeModel.Kind.LOADING) info.invalidate();
                for (RowHolder h : rows) {
                    if (!h.row.loading) continue;
                    h.label.pulse = v;
                    h.label.invalidate();
                    for (MatchdayViews.CardCell cell : h.cells) { cell.pulse = v; cell.invalidate(); }
                }
            }
        });
        pulse.start();
    }

    // ---------- HomeLayout ----------
    @Override public String focusKey() {
        View f = root == null ? null : root.findFocus();
        if (f == null) return null;
        if (f == chip) return "chip";
        if (f == choose) return "choose";
        if (f == park) return "park";
        if (f instanceof MatchdayViews.CardCell) {
            RowHolder h = holderOf(f);
            MatchdayViews.CardCell cell = (MatchdayViews.CardCell) f;
            if (h != null) return "card:" + h.row.id + "/" + (cell.item == null ? "#" + h.cells.indexOf(cell) : cell.item.key());
        }
        return null;
    }

    @Override public void focus(String key) {
        if (blocked || root == null) return;
        if (empty) { (key != null && key.equals("chip") ? chip : choose).requestFocus(); return; }
        if ("chip".equals(key)) { chip.requestFocus(); return; }
        List<MatchdayModel.Row> rs = new ArrayList<>();
        for (RowHolder h : rows) rs.add(h.row);
        int[] at = null;
        if (key != null && key.startsWith("card:")) {
            int slash = key.indexOf('/');
            String rowId = key.substring(5, slash), item = key.substring(slash + 1);
            if (item.startsWith("#")) {
                for (int r = 0; r < rows.size(); r++) if (rows.get(r).row.id.equals(rowId)) at = new int[]{r, 0};
            } else at = MatchdayModel.find(rs, rowId, item);
        }
        if (at == null) at = MatchdayModel.launch(rs);
        if (at == null) {
            if (!rows.isEmpty() && rows.get(0).row.loading) park.requestFocus(); else chip.requestFocus();
            return;
        }
        RowHolder h = rows.get(at[0]);
        if (h.row.items.isEmpty()) at[1] = 0;
        focusCell(at[0], at[1], false);
    }

    @Override public String selected() {
        MatchdayViews.CardCell f = focusedCell();
        return f != null && f.item != null && f.item.club != null ? f.item.club.club.id : null;
    }

    @Override public void select(String clubId) { }

    @Override public void block(boolean b) {
        blocked = b;
        if (root != null) root.setDescendantFocusability(b ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    /**
     * The D-pad map (SPEC-APP 4.5): Left/Right along a row; Up from row 1 to the Settings chip; Down to the next row
     * (on the card it last had, or the one it opens on). Back: see back().
     */
    @Override public boolean onKey(KeyEvent e) {
        if (blocked || root == null) return false;
        int code = e.getKeyCode();
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!(up || down || left || right)) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        View f = root.findFocus();
        if (empty) {
            if (f == chip && (down || left)) choose.requestFocus();
            else if (f == choose && (up || right)) chip.requestFocus();
            else if (f == null || f == park) choose.requestFocus();
            return true;
        }
        if (f == chip) {
            if (down) { int r = nextRow(-1, 1); if (r >= 0) focusCell(r, rows.get(r).focus, true); }
            return true;
        }
        if (!(f instanceof MatchdayViews.CardCell)) {
            if (up) chip.requestFocus();
            return true;
        }
        RowHolder h = holderOf(f);
        if (h == null) return true;
        int r = rows.indexOf(h), i = h.cells.indexOf(f);
        if (left && i > 0) focusCell(r, i - 1, true);
        else if (right && i < h.cells.size() - 1 && h.cells.get(i + 1).isFocusable()) focusCell(r, i + 1, true);
        else if (up) {
            int p = nextRow(r, -1);
            if (p < 0) chip.requestFocus();
            else focusCell(p, rows.get(p).focus, true);
        } else if (down) {
            int n = nextRow(r, 1);
            if (n >= 0) focusCell(n, rows.get(n).focus, true);
        }
        return true;
    }

    /** The next row up or down that can take the focus (a loading row cannot); -1 when there is none. */
    private int nextRow(int from, int dir) {
        for (int r = from + dir; r >= 0 && r < rows.size(); r += dir) {
            RowHolder h = rows.get(r);
            if (!h.cells.isEmpty() && h.cells.get(0).isFocusable()) return r;
        }
        return -1;
    }

    /** Back on a moved list returns it to row 1's first card; Back again (or on an unmoved list) leaves the app. */
    @Override public boolean back() {
        if (blocked || root == null || atHome()) return false;
        goHome();
        return true;
    }

    /**
     * The list is where it opened: every row at its opening scroll, and the focus on row 1's opening card, on the
     * Settings chip above it, or nowhere yet (loading). Back may leave the app from here.
     */
    private boolean atHome() {
        if (empty || shown == null) return true;
        int[] at = MatchdayModel.launch(shown.rows);
        if (at == null) return true;
        for (RowHolder x : rows) if (x.scroll != initialScroll(x.row)) return false;
        View f = root.findFocus();
        if (!(f instanceof MatchdayViews.CardCell)) return true;  // the chip, the park: the rows have not moved
        RowHolder h = holderOf(f);
        return h != null && rows.indexOf(h) == at[0] && h.cells.indexOf(f) == at[1];
    }

    /** Back on a moved list: every row scrolls back to where it opened and row 1's first card takes the focus. */
    private void goHome() {
        for (RowHolder h : rows) { h.focus = h.row.start; h.scroll = initialScroll(h.row); ensureScroll(h); }
        int[] at = MatchdayModel.launch(shown.rows);
        if (at != null) focusCell(at[0], at[1], true);
    }

    private void ensureScroll(final RowHolder h) {
        float target = stripX(h.scroll);
        if (Math.abs(h.strip.getTranslationX() - target) < 1) return;
        if (h.scrollAnim != null) h.scrollAnim.cancel();
        h.scrollAnim = ValueAnimator.ofFloat(h.strip.getTranslationX(), target);
        h.scrollAnim.setDuration(Ui.SCROLL_MS);
        h.scrollAnim.setInterpolator(Ui.DECELERATE);
        h.scrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { h.strip.setTranslationX((float) a.getAnimatedValue()); }
        });
        h.scrollAnim.start();
    }

    /**
     * The LIVE pill's dot pulses (opacity .35 -> 1 -> .35, 1.2 s) while the stage shows a live match and the screen is
     * on: one paint alpha, and only the stage's words are redrawn (in 40 steps, not at every frame).
     */
    private void livePulse() {
        boolean on = !paused && !empty && info != null && info.getVisibility() == View.VISIBLE && info.current().kind == HomeModel.Kind.LIVE;
        if (!on) {
            if (livePulse != null) { livePulse.cancel(); livePulse = null; }
            if (info != null && info.dot != 1) { info.dot = 1; info.invalidate(); }
            return;
        }
        if (livePulse != null) return;
        livePulse = ValueAnimator.ofFloat(0.35f, 1f);
        livePulse.setDuration(Ui.PULSE_MS / 2);
        livePulse.setRepeatMode(ValueAnimator.REVERSE);
        livePulse.setRepeatCount(ValueAnimator.INFINITE);
        livePulse.setInterpolator(Ui.FAST_OUT_SLOW_IN);
        livePulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float v = Math.round((float) a.getAnimatedValue() * 40) / 40f;
                if (v != info.dot) { info.dot = v; info.invalidate(); }
            }
        });
        livePulse.start();
    }

    @Override public void resume() {
        paused = false;
        if (pulse != null) pulse.resume();
        livePulse();
    }

    @Override public void pause() {
        paused = true;
        livePulse();
        if (pulse != null) pulse.pause();
        ui.removeCallbacks(restage);
        MatchdayCards k = cards;
        if (k != null && BuildConfig.DEBUG) android.util.Log.i("Fanela", "matchday cards: " + k.stats());
    }

    @Override public void release() {
        ui.removeCallbacksAndMessages(null);
        if (pulse != null) pulse.cancel();
        if (livePulse != null) { livePulse.cancel(); livePulse = null; }
        if (vAnim != null) vAnim.cancel();
        for (RowHolder h : rows) if (h.scrollAnim != null) h.scrollAnim.cancel();
        if (stageView != null) stageView.release();
        artWorker.shutdownNow();
        MatchdayCards k = cards;
        if (k != null) k.release();
    }
}
