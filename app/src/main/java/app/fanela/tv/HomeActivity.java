package app.fanela.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's main screen. HomeActivity keeps the data (followed clubs, their matches, the status), the refresh cycle,
 * the match screen and focus restoration; how the screen looks is the app layout chosen in Settings › Appearance
 * (HomeLayout: Club by default). Works on every TV, including Google TV, which does not show home-screen rows. While
 * it is on screen it refreshes live scores every 30 s during matches; nothing polls once it is closed.
 *
 * Threads: "net" runs refreshes and live ticks (slow, network); "display" builds the HomeModel and lets the layout
 * prepare its images; "detail" prepares the match screen's images. A new score or clock only rebinds the layout,
 * which updates in place, so focus and scrolling never jump; an open match screen is prepared again and updated in
 * place too (its score, its clock, its button once the channel's app is installed). The saved data is read again at
 * every minute, so the clock and "in 2 h 15 min" never lag (no network for that).
 *
 * Start (SPEC-APP 3.4): the window opens on the brand (near-black with the Fanela shirt, styles.xml; on Android 12+
 * the system's splash shows the same shirt, LaunchActivity), and the app's own first frame is that brand again (the
 * root wears launch.xml, the layout is laid out but not drawn yet), so the shirt stays on screen however long the
 * first real frame takes. The next frame already wears the favourite club: the layout is bound at once to a loading
 * model made from the preferences alone (the followed clubs and their kit colours; first()), so the field, the rail
 * with its crests from the TV's cache and the skeletons in their final slots are drawn before anything is loaded. The
 * saved data and the crest watermark are prepared alongside, at a low priority; the network waits until that frame
 * is on screen (afterFirstDraw).
 *
 * Back (BackKey, the same on Android 8-14): closes the match screen, then lets the layout go back (Matchday's moved
 * list returns to row 1), else the app closes.
 *
 * Followed sports (F1, UFC; F1-UFC-ARCHITECTURE §7): the model gains one SportData per sport, read from the event
 * store with its clock rules (SportEngine.events); OK on a sport's hero, tile, row or card opens the event screen
 * (EventScreen, in the match screen's place: prepared on the detail thread, updated in place, kept across the app being
 * ended in the background). A home-screen tile's "f1|..." / "ufc|..." key opens it too (openPending).
 */
public class HomeActivity extends Activity implements HomeLayout.Host {
    private static final long LIVE_EVERY = 30000L, IDLE_EVERY = 5 * 60000L, BUSY_RETRY = 3000L;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService net, display, detail;
    private FrameLayout root, overlay;
    private volatile HomeLayout layout;
    private HomeModel shown;
    private String pendingMatch;  // a match to open once it is on screen (from a home-screen tile)
    private String builtLang, builtTheme, builtLayout;
    /** The focus to restore: across a rebuild (language, layout) and after the match screen. */
    private String restoreKey, panelReturn;
    /** The club shown when the system ended the app (onSaveInstanceState), for the first build. */
    private String restoreSelected;
    private int generation;
    private boolean resumed;
    private volatile boolean refreshing, tried;  // tried: a refresh of ours finished since the screen opened
    private BackKey back;
    /** The match screen's match and club, to prepare it again when the data changes (null while it is closed). */
    private Match detailMatch;
    private Teams.Club detailClub;
    /** The event screen's event and the session it opened on (null while it is closed). */
    private SportEvent detailEvent;
    private String detailSession;
    /** QAS-5: the event screen's focused row and right-panel page as the system kept them, for its first push. */
    private String restoreEventFocus;
    private int restoreEventPage;
    private volatile long lastTick;     // net thread (onResume resets it): when the live scores were last polled
    private volatile boolean lastLive;  // what that poll found
    /** The current content has been drawn once: the network may start (afterFirstDraw). */
    private boolean drawn;

    /** How long until the clock's next minute (a little after it, so the minute has turned when the screen reads it). */
    static long untilNextMinute(long now) { return 60000L - now % 60000L + 200L; }

    /** Live scores are polled at most every 30 s, however often the cycle runs (a 3 s retry while another refresh is busy). */
    static boolean tickDue(long now, long lastTick) { return now - lastTick >= LIVE_EVERY - 1000L; }

    /** The next cycle: soon while another refresh is busy, 30 s during a live match, else at the next clock change or 5 min. */
    static long nextCycle(boolean live, boolean busy, long untilChange) {
        if (busy) return BUSY_RETRY;
        if (live) return LIVE_EVERY;
        return untilChange >= IDLE_EVERY ? IDLE_EVERY : Math.max(1000L, untilChange + 1000L);
    }

    private final Runnable cycle = new Runnable() {
        public void run() {
            if (!resumed) return;
            final int gen = generation;
            final Context app = getApplicationContext();
            if (Scenario.active(app) != null) {  // a debug scenario: its own data, no network (read again like a live poll)
                loadLater();
                ui.removeCallbacks(cycle);
                ui.postDelayed(cycle, LIVE_EVERY);
                return;
            }
            net.execute(new Runnable() { public void run() {
                Engine.background();
                boolean live = false, busy = false;
                long change = Long.MAX_VALUE;
                try { AppUpdate.check(app); } catch (Throwable ignored) { }
                refreshing = true;
                try {
                    long now = System.currentTimeMillis();
                    if (tickDue(now, lastTick)) {
                        lastTick = now;
                        lastLive = Engine.live(app);
                        if (lastLive) { Engine.tick(app); loadLater(); }  // scores first
                    }
                    live = lastLive;
                    busy = Engine.busy();  // a refresh started elsewhere (Settings, the job): ask again in a moment
                    if (!busy) Engine.refresh(app, false);  // cheap when nothing is due: clubs have their own tiers
                    change = Engine.nextChangeAll(app, System.currentTimeMillis());  // a game (or a session) without a live feed
                } catch (Throwable ignored) {
                } finally {
                    refreshing = false;
                    if (!busy) tried = true;
                }
                loadLater();
                final long next = nextCycle(live, busy, change == Long.MAX_VALUE ? Long.MAX_VALUE : change - System.currentTimeMillis());
                ui.post(new Runnable() { public void run() {
                    if (!resumed || gen != generation) return;
                    ui.removeCallbacks(cycle);
                    ui.postDelayed(cycle, next);
                }});
            }});
        }
    };

    /** Every minute while the screen is on: the saved data again, so the clock and the relative times are current. */
    private final Runnable minute = new Runnable() {
        public void run() {
            if (!resumed) return;
            loadLater();
            ui.removeCallbacks(minute);
            ui.postDelayed(minute, untilNextMinute(System.currentTimeMillis()));
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        back = BackKey.attach(this, new BackKey.Handler() { public boolean back() { return onBack(); } });
        I18n.load(this);
        Scenario.apply(this);
        Ui.theme(this);
        Ui.fonts(this);
        Net.init(this);
        net = Executors.newSingleThreadExecutor();
        display = Executors.newSingleThreadExecutor();
        detail = Executors.newSingleThreadExecutor();
        // re-created by the system (the app was ended in the background): the screen as it was left, not the tile's
        // match again (the intent still carries it, though it was opened, and maybe closed, long ago)
        pendingMatch = b != null ? b.getString("match") : getIntent().getStringExtra("match");
        restoreKey = b != null ? b.getString("focus") : null;
        restoreSelected = b != null ? b.getString("selected") : null;
        restoreEventFocus = b != null ? b.getString("eventFocus") : null;
        restoreEventPage = b != null ? b.getInt("eventPage", 0) : 0;
        build(true);
        if (!Teams.chosen(this) && Sports.nothingFollowed(this) && Scenario.active(this) == null) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra("setup", true));
        }
    }

    /**
     * What the system keeps if it ends the app in the background: the open match screen's match, the focused item (the
     * one the match screen returns to) and the club shown. onCreate gives them to the usual paths: the match opens
     * once it is on screen (openPending), the focus goes back after the first load (restoreKey).
     */
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("match", overlay != null && detailMatch != null ? detailMatch.key
            : overlay != null && detailEvent != null ? eventKey() : pendingMatch);
        out.putString("focus", layout == null ? null : overlay != null ? panelReturn : layout.focusKey());
        out.putString("selected", layout == null ? null : layout.selected());
        // the event screen: the opened session is in "match"; the focused row and the panel's page apart (QAS-5)
        out.putString("eventFocus", overlay != null && detailEvent != null ? EventScreen.focused(overlay) : null);
        out.putInt("eventPage", overlay != null && detailEvent != null ? EventScreen.page(overlay) : 0);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i.getStringExtra("match") != null) {
            pendingMatch = i.getStringExtra("match");
            if (!openPending()) loadLater();  // not on screen yet: opened after the next load
        }
    }

    @Override protected void onStart() { super.onStart(); Brand.shown(); }

    @Override protected void onStop() { super.onStop(); Brand.hidden(this); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        generation++;
        lastTick = 0;  // scores at once
        Net.reset();  // the user is here now: retry every source at once
        I18n.load(this);
        Scenario.apply(this);
        Ui.theme(this);
        if (!I18n.lang().equals(builtLang) || !Ui.key().equals(builtTheme) || !Appearance.layout(this).equals(builtLayout)) {
            restoreKey = layout == null ? null : layout.focusKey();
            build(false);  // language, favourite club or app layout changed in Settings
        }
        if (layout != null) layout.resume();
        loadLater();  // saved data at once, in the background (an open match screen too: its app may be installed now)
        if (drawn) startCycle();  // the network: once the first frame is on screen (afterFirstDraw)
        ui.removeCallbacks(minute);
        ui.postDelayed(minute, untilNextMinute(System.currentTimeMillis()));
    }

    @Override protected void onPause() {
        resumed = false;
        generation++;
        ui.removeCallbacks(cycle);
        ui.removeCallbacks(minute);
        if (layout != null) layout.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        net.shutdownNow();
        display.shutdownNow();
        detail.shutdownNow();
        if (layout != null) layout.release();
        back.detach();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (overlay == null && layout != null && layout.onKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (back.onKeyDown(code, e)) return true;  // Android 8-12; 13+ calls BackKey's callback
        if (code == KeyEvent.KEYCODE_MENU) { openSettings(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (back.onKeyUp(code, e)) return true;
        return super.onKeyUp(code, e);
    }

    /** Back: the match screen closes, else the layout goes back (Matchday's moved list), else the app closes. */
    private boolean onBack() {
        if (overlay != null && EventScreen.back(overlay)) return true;  // a fighter being chosen: only the choice goes
        if (overlay != null) { closeDetail(); return true; }
        return layout != null && layout.back();
    }

    private boolean gone() { return isFinishing() || isDestroyed(); }

    private int dp(float v) { return Ui.dp(this, v); }

    /** The refresh cycle (network), a moment after the screen is up. */
    private void startCycle() {
        ui.removeCallbacks(cycle);
        ui.postDelayed(cycle, 300);
    }

    /**
     * Once the new content's first frame is drawn (it already shows the field, the rail and the skeletons), the refresh
     * cycle starts: no download competes with that frame. The saved data is read meanwhile on the display thread (at
     * a low priority), so the screen fills in as soon as it can; were it ready first, the first frame shows it.
     * brand: the layout, kept out of the first frame (the brand is drawn alone, QA3A-1); shown once that frame is up.
     */
    private void afterFirstDraw(final View content, final View brand) {
        drawn = false;
        final android.view.ViewTreeObserver.OnDrawListener[] self = new android.view.ViewTreeObserver.OnDrawListener[1];
        self[0] = new android.view.ViewTreeObserver.OnDrawListener() {
            boolean done;
            public void onDraw() {
                if (done) return;
                done = true;
                ui.post(new Runnable() { public void run() {  // a listener may not be removed while it is called
                    content.getViewTreeObserver().removeOnDrawListener(self[0]);
                    if (content != root || gone()) return;  // rebuilt meanwhile: the new content has its own
                    if (brand != null) {  // the shirt is on screen: now the club (the shirt stays until it is drawn)
                        if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "brand frame drawn");
                        brand.setVisibility(View.VISIBLE);
                        root.setBackgroundColor(Ui.APP_BG);
                        if (overlay == null && layout != null && root.findFocus() == null) { layout.focus(restoreKey); if (shown != null) restoreKey = null; }
                        afterFirstDraw(content, null);
                        return;
                    }
                    drawn = true;
                    if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "first frame drawn");
                    if (resumed) startCycle();
                }});
            }
        };
        content.getViewTreeObserver().addOnDrawListener(self[0]);
    }

    /**
     * The first frame, from the preferences alone: every followed club as loading, with its kit colours, so the
     * layout draws its field, its rail and the skeletons in their final slots before anything is read or fetched.
     */
    private void first(HomeLayout l) {
        try {
            long t0 = System.nanoTime();
            HomeModel h = new HomeModel();
            Scenario.Snapshot sc = Scenario.active(this);
            h.now = sc != null ? sc.now : System.currentTimeMillis();
            h.favourite = sc != null ? sc.favourite : Teams.favourite(this);
            boolean basket = Teams.basketball(this);
            for (String id : sc != null ? sc.follow : Teams.followed(this)) {
                Teams.Club k = Teams.get(this, id);  // saved with the follow list: no league file is read here
                if (k == null) continue;
                HomeModel.ClubData d = new HomeModel.ClubData();
                d.club = k;
                d.colors = ClubLayout.colors(k);
                d.kind = HomeModel.Kind.LOADING;
                h.teams.put(id, k.teams(basket));
                h.clubs.add(d);
            }
            for (String sp : sc != null ? sc.sports : Sports.followed(this)) {  // each followed sport as loading
                SportModel.SportData d = new SportModel.SportData();
                d.sport = sp;
                d.label = SportText.sportName(sp);
                d.kind = HomeModel.Kind.LOADING;
                h.sports.add(d);
            }
            h.status = HomeModel.Status.UPDATING;
            h.statusText = AppText.t("updating");
            l.first(this, h);
            l.bind(h);
            if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "first frame bound " + (System.nanoTime() - t0) / 1000000 + " ms");
        } catch (Throwable t) {  // the first load draws everything anyway
            android.util.Log.w("Fanela", "first frame", t);
        }
    }

    // ---------- layout ----------
    /**
     * The screen in the chosen layout. brand (the activity's first build): the root wears the window's brand
     * (launch.xml, the shirt on near-black) and the layout stays undrawn for one frame, so the app's first frame is the
     * brand the window and the splash showed, never a plain colour while the first real frame is drawn (QA3A-1).
     */
    private void build(boolean brand) {
        String sel = layout == null ? restoreSelected : layout.selected();
        restoreSelected = null;
        if (layout != null) layout.release();
        HomeLayout l = HomeLayout.create(Appearance.layout(this));
        if (sel != null) l.select(sel);
        root = new FrameLayout(this);
        View content = l.build(this, this);
        if (brand) {
            root.setBackground(getDrawable(R.drawable.launch));
            content.setVisibility(View.INVISIBLE);
        } else root.setBackgroundColor(Ui.APP_BG);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        first(l);
        afterFirstDraw(root, brand ? content : null);
        setContentView(root);
        layout = l;
        overlay = null;  // the match screen went with the old content
        detailMatch = null;
        detailClub = null;
        detailEvent = null;
        shown = null;
        builtLang = I18n.lang();
        builtTheme = Ui.key();
        builtLayout = Appearance.layout(this);
    }

    @Override public void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    @Override public void chooseClubs() { startActivity(new Intent(this, SettingsActivity.class).putExtra("setup", true)); }

    @Override public void openMatch(Match m, Teams.Club club) {
        if (m == null || club == null) return;
        showMatch(m, club);
    }

    @Override public void openLive(Match m, Teams.Club club) {
        if (m.channelKnown && m.channelApp != null && HomeRows.installed(this, m.channelApp) && OpenActivity.launch(this, m.channelApp)) return;
        openMatch(m, club);
    }

    /** The app's page on Google Play (the TV's store), or its web page when there's no store app. */
    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception ignored) {
                android.widget.Toast.makeText(this, I18n.f("app_missing", SportWhere.appName(pkg)), android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- data ----------
    private void loadLater() {
        try { display.execute(new Runnable() { public void run() { Engine.background(); load(); } }); }
        catch (Exception ignored) { }  // shutting down
    }

    /** Builds the model from the saved matches, lets the layout prepare its images, then shows it. Display thread. */
    private void load() {
        try {
            final Context app = getApplicationContext();
            final HomeLayout l = layout;
            if (l == null) return;
            I18n.load(app);
            Scenario.apply(app);
            Where.load(app);
            Scenario.Snapshot sc = Scenario.active(app);
            if (sc != null && sc.lang != null) I18n.use(sc.lang);
            long now = sc != null ? sc.now : System.currentTimeMillis();
            List<Match> all = sc != null ? sc.matches : Store.load(app);
            if (sc == null) Engine.timeStates(all, now);
            long t0 = System.nanoTime();
            final HomeModel m = model(app, all, now, sc);
            l.prepare(app, m);
            if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "home model + prepare " + (System.nanoTime() - t0) / 1000000 + " ms");
            ui.post(new Runnable() { public void run() {
                if (gone()) return;
                if (l != layout) { loadLater(); return; }  // rebuilt meanwhile: the new layout prepares its own
                boolean first = shown == null;
                shown = m;
                l.bind(m);
                if (first && restoreKey != null && overlay == null && root.findFocus() != null) { l.focus(restoreKey); restoreKey = null; }
                if (overlay != null) { refreshMatch(m); refreshEvent(m); }
                openPending();
            }});
        } catch (Throwable t) {  // out of memory, say: the screen keeps what it shows until the next load
            android.util.Log.w("Fanela", "home load", t);
        }
    }

    /** Every followed club's hero, latest results, fixtures and rail line, and the status slot. */
    private HomeModel model(Context app, List<Match> all, long now, Scenario.Snapshot sc) {
        HomeModel h = new HomeModel();
        h.now = now;
        h.all = all;
        boolean basket = Teams.basketball(app), waiting = !tried || refreshing;
        Iterable<String> ids = sc != null ? sc.follow : Teams.followed(app);
        h.favourite = sc != null ? sc.favourite : Teams.favourite(app);
        boolean loading = false, stale = false;
        for (String id : ids) {
            Teams.Club k = Teams.get(app, id);
            if (k == null) k = Teams.find(app, id);
            if (k == null) continue;
            HomeModel.ClubData d = new HomeModel.ClubData();
            d.club = k;
            d.colors = ClubLayout.colors(k);
            String[] teams = k.teams(basket);
            h.teams.put(id, teams);
            d.bothSports = teams.length > 1;
            d.hero = HomeModel.hero(all, id, teams, now);
            d.kind = HomeModel.kind(d.hero);
            d.latest.addAll(HomeModel.latest(all, id, teams, d.hero));
            d.fixtures.addAll(HomeModel.fixtures(all, id, teams, d.hero, now));
            HomeModel.rail(d, all, teams, now);
            boolean ever = sc != null || Store.lastUpdate(app, id, k.mainTeam()) > 0;
            if (sc != null && "loading".equals(sc.mode) && id.equals(sc.favourite)) ever = false;
            boolean failedDemo = sc != null && "failed".equals(sc.mode) && d.hero == null && d.fixtures.isEmpty();  // debug scenario
            if (failedDemo) ever = false;
            if (!ever && (d.hero == null || sc != null)) {
                d.kind = failedDemo ? HomeModel.Kind.FAILED : waiting || sc != null ? HomeModel.Kind.LOADING : HomeModel.Kind.FAILED;
                d.hero = null;
                d.latest.clear();
                d.fixtures.clear();
            }
            d.stale = sc == null && Store.stale(app, id, k.mainTeam());
            loading |= d.kind == HomeModel.Kind.LOADING;
            stale |= d.stale;
            h.clubs.add(d);
        }
        // the followed sports, after the clubs
        List<SportEvent> events = sc != null ? sc.events : SportEngine.events(app, now);
        SportPrefs prefs = sc != null && sc.prefs != null ? sc.prefs : Sports.prefs(app);
        boolean hidden = SportText.hidden(app);
        android.content.SharedPreferences eng = app.getSharedPreferences(Sports.ENGINE, Context.MODE_PRIVATE);
        for (String sp : sc != null ? sc.sports : Sports.followed(app)) {
            SportModel.SportData d = SportModel.of(events, sp, prefs, now, hidden, focusName(app, sp, prefs));
            d.stale = sc == null && SportEngine.stale(app, sp);
            boolean ever = sc != null || eng.getLong("ok_" + sp, 0) > 0 || !d.events.isEmpty();
            if (!ever && d.hero == null) d.kind = waiting ? HomeModel.Kind.LOADING : HomeModel.Kind.NONE;
            loading |= d.kind == HomeModel.Kind.LOADING;
            stale |= d.stale;
            h.sports.add(d);
        }
        long last = sc != null ? sc.savedAt : Math.max(Store.lastUpdateAll(app), SportEngine.lastOk(app));
        long failed = sc != null ? 0 : Math.max(Engine.failedAt(app), SportEngine.failedAt(app));
        boolean offline = sc != null ? "offline".equals(sc.mode) : HomeModel.offline(tried, h.empty(), last, stale, failed, now);
        if (loading) { h.status = HomeModel.Status.UPDATING; h.statusText = AppText.t("updating"); }
        else if (offline) {
            h.status = HomeModel.Status.OFFLINE;
            h.statusText = last == 0 ? AppText.t("offline_never")
                : AppText.f("offline", AppText.days(now, last) == 0 ? I18n.time(last) : AppText.shortDate(last) + " " + I18n.time(last));
        } else {
            h.status = HomeModel.Status.CLOCK;
            h.statusText = AppText.clock(now) + (sc == null && AppUpdate.available(app) != null ? " · " + AppText.t("update_ready") : "");
        }
        return h;
    }

    /** The F1 row's focus name for its label ("Formula 1 · Norris"): the first focus's surname; "" for none. */
    static String focusName(Context c, String sport, SportPrefs p) {
        if (!SportEvent.F1.equals(sport) || p == null || p.focus.isEmpty()) return "";
        String n = Sports.name(c, p.focus.get(0)).trim();
        if (p.focus.get(0).startsWith("d:") && n.indexOf(' ') > 0) n = n.substring(n.lastIndexOf(' ') + 1);
        return n;
    }

    /** Opens the match a home-screen tile asked for, once it is on screen (its club is selected first). */
    private boolean openPending() {
        if (pendingMatch == null || shown == null) return false;
        if (SportEvent.isEventKey(pendingMatch)) {  // an F1 session or a UFC card: the event screen, its sport selected
            String ek = SportEvent.eventKey(pendingMatch), sid = SportEvent.sessionId(pendingMatch);
            for (SportModel.SportData d : shown.sports) {
                SportEvent e = d.event(ek);
                if (e == null) continue;
                pendingMatch = null;
                if (!d.id().equals(layout.selected()) && Appearance.CLUB.equals(builtLayout)) { layout.select(d.id()); layout.bind(shown); }
                openEvent(e, sid);
                return true;
            }
            for (SportModel.SportData d : shown.sports) if (d.kind == HomeModel.Kind.LOADING) return false;  // not read yet
            pendingMatch = null;
            return false;
        }
        for (HomeModel.ClubData d : shown.clubs) {
            List<Match> ms = new ArrayList<>(d.latest);
            ms.addAll(d.fixtures);
            if (d.hero != null) ms.add(0, d.hero);
            for (Match m : ms) if (pendingMatch.equals(m.key)) {
                pendingMatch = null;
                if (!d.club.id.equals(layout.selected())) { layout.select(d.club.id); layout.bind(shown); }
                openMatch(m, d.club);
                return true;
            }
        }
        pendingMatch = null;  // the match is no longer listed
        return false;
    }

    // ---------- the event screen ----------
    @Override public void openEvent(SportEvent e, String sessionId) {
        if (e == null) return;
        showEvent(e, sessionId);
    }

    /**
     * The event screen's key for the state the system keeps: the event and the session it was opened on (the hero's),
     * never the focused row, which is kept apart with the panel's page (QAS-5).
     */
    private String eventKey() {
        return detailEvent.key() + (detailSession == null ? "" : "#" + detailSession);
    }

    /** The sport's choices and the in-app hide-scores switch, as the model was built with them. */
    private SportPrefs prefsFor(HomeModel h, String sport) {
        if (h != null) for (SportModel.SportData d : h.sports) if (d.sport.equals(sport)) return d.prefs;
        Scenario.Snapshot sc = Scenario.active(this);
        return sc != null && sc.prefs != null ? sc.prefs : Sports.prefs(this);
    }

    /** Opens the event screen: prepared on the detail thread, then pushed in as the match screen is. */
    private void showEvent(final SportEvent e, final String sessionId) {
        final HomeModel h = shown;
        final Context app = this;
        final int gen = ++matchRequest;
        final boolean hidden = SportText.hidden(this);
        final SportPrefs prefs = prefsFor(h, e.sport);
        detail.execute(new Runnable() { public void run() {
            final EventScreen.Data d;
            try { d = EventScreen.prepare(app, e, sessionId, h, hidden, prefs); }
            catch (Throwable t) { android.util.Log.w("Fanela", "event screen", t); return; }
            ui.post(new Runnable() { public void run() {
                if (gone() || gen != matchRequest || !resumed && !hasWindowFocus()) return;
                pushEvent(d);
                detailEvent = e;
                detailSession = sessionId;
            }});
        }});
    }

    /**
     * A fighter followed or unfollowed from his bout row (long-press OK): saved as Settings saves it (his name with
     * him), then a refresh redraws the cards and the home screen in the background (a scenario is left alone).
     */
    private void followFighter(SportEvent.Fighter f, boolean on) {
        if (f == null || f.id == null || f.id.isEmpty()) return;
        if (Scenario.active(this) != null) return;
        Sports.setUfcFighter(this, f.id, SportText.name(f), on);
        final Context app = getApplicationContext();
        try {
            net.execute(new Runnable() { public void run() {
                Engine.background();
                try { Engine.refresh(app, false); } catch (Throwable ignored) { }
                loadLater();
            }});
        } catch (Exception ignored) { }  // shutting down
    }

    private void pushEvent(EventScreen.Data d) {
        closeDetail();
        panelReturn = layout.focusKey();
        overlay = EventScreen.build(this, d, new EventScreen.Actions() {
            public void open(String pkg) { OpenActivity.launch(HomeActivity.this, pkg); }
            public void get(String pkg) { openStore(pkg); }
            public void follow(SportEvent.Fighter f, boolean on) { followFighter(f, on); }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setAlpha(0f);
        overlay.setTranslationY(Ui.px(this, Ui.PUSH_RISE));
        overlay.animate().alpha(1f).translationY(0).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN).start();
        layout.block(true);
        View f = EventScreen.launchFocus(overlay);
        if (f != null) f.requestFocus();
        if (restoreEventFocus != null || restoreEventPage != 0) {  // back after the system ended the app: as it was left
            EventScreen.restore(overlay, restoreEventFocus, restoreEventPage);
            restoreEventFocus = null;
            restoreEventPage = 0;
        }
        overlay.announceForAccessibility(overlay.getContentDescription());
    }

    /** New data while the event screen is open: the same event from it, prepared again and swapped in place. */
    private void refreshEvent(final HomeModel h) {
        final SportEvent was = detailEvent;
        final FrameLayout o = overlay;
        if (was == null || o == null) return;
        SportEvent found = was;
        for (SportModel.SportData d : h.sports) { SportEvent x = d.event(was.key()); if (x != null) { found = x; break; } }
        final SportEvent e = found;
        final String sid = detailSession;
        final Context app = this;
        final int gen = matchRequest;
        final boolean hidden = SportText.hidden(this);
        final SportPrefs prefs = prefsFor(h, e.sport);
        try {
            detail.execute(new Runnable() { public void run() {
                final EventScreen.Data d;
                try { d = EventScreen.prepare(app, e, sid, h, hidden, prefs); }
                catch (Throwable t) { android.util.Log.w("Fanela", "event screen update", t); return; }
                ui.post(new Runnable() { public void run() {
                    if (gone() || o != overlay || gen != matchRequest) return;
                    detailEvent = e;
                    EventScreen.update(o, d);
                }});
            }});
        } catch (Exception ignored) { }  // shutting down
    }

    // ---------- the match screen ----------
    /**
     * Opens the match screen in the layout's own look (MatchScreen): its images are made on the detail thread, then it
     * pushes in (220 ms fade and a 16 px rise) with its button focused. Back returns to the item that opened it.
     */
    private void showMatch(final Match m, final Teams.Club club) {
        final HomeModel h = shown;
        final String lay = builtLayout == null ? Appearance.layout(this) : builtLayout;
        final Context app = this;
        final int gen = ++matchRequest;
        detail.execute(new Runnable() { public void run() {
            final MatchScreen.Data d;
            try { d = MatchScreen.prepare(app, lay, m, club, h); }
            catch (Throwable t) { android.util.Log.w("Fanela", "match screen", t); return; }  // out of memory, say: the home screen stays
            ui.post(new Runnable() { public void run() {
                if (gone() || gen != matchRequest || !resumed && !hasWindowFocus()) return;
                pushMatch(d);
                detailMatch = m;
                detailClub = club;
            }});
        }});
    }

    private int matchRequest;

    /**
     * New data while the match screen is open: the same match from it (its score, clock and state), the status slot,
     * whether its channel's app is on the TV now, all prepared again on the detail thread and swapped in place (the
     * button keeps the focus).
     */
    private void refreshMatch(final HomeModel h) {
        final Match was = detailMatch;
        final Teams.Club club = detailClub;
        final FrameLayout o = overlay;
        if (was == null || club == null || o == null) return;
        Match found = was;
        for (Match x : h.all) if (x.key.equals(was.key)) { found = x; break; }
        final Match m = found;
        final String lay = builtLayout == null ? Appearance.layout(this) : builtLayout;
        final Context app = this;
        final int gen = matchRequest;
        try {
            detail.execute(new Runnable() { public void run() {
                final MatchScreen.Data d;
                try { d = MatchScreen.prepare(app, lay, m, club, h); }
                catch (Throwable t) { android.util.Log.w("Fanela", "match screen update", t); return; }  // it keeps what it shows
                ui.post(new Runnable() { public void run() {
                    if (gone() || o != overlay || gen != matchRequest) return;  // closed, or another match opened meanwhile
                    detailMatch = m;
                    MatchScreen.update(o, d);
                }});
            }});
        } catch (Exception ignored) { }  // shutting down
    }

    private void pushMatch(MatchScreen.Data d) {
        closeDetail();
        panelReturn = layout.focusKey();
        overlay = MatchScreen.build(this, d, new MatchScreen.Actions() {
            public void act(MatchFacts.Action a, Match m) {
                if (a == MatchFacts.Action.OPEN) OpenActivity.launch(HomeActivity.this, m.channelApp);
                else if (a == MatchFacts.Action.GET) openStore(m.channelApp);
                else closeDetail();
            }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setAlpha(0f);
        overlay.setTranslationY(Ui.px(this, Ui.PUSH_RISE));
        overlay.animate().alpha(1f).translationY(0).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN).start();
        layout.block(true);  // keep D-pad focus on the screen's button
        View b = MatchScreen.button(overlay);
        if (b != null) b.requestFocus();
        overlay.announceForAccessibility(overlay.getContentDescription());
    }

    /** Back from the match screen: the home screen with the focus it had (Back reverses the push). */
    private void closeDetail() {
        if (overlay == null) return;
        final FrameLayout o = overlay;
        overlay = null;
        detailMatch = null;
        detailClub = null;
        detailEvent = null;
        detailSession = null;
        o.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        layout.block(false);
        layout.focus(panelReturn);
        o.animate().alpha(0f).translationY(Ui.px(this, Ui.PUSH_RISE)).setDuration(Ui.PUSH_MS).withEndAction(new Runnable() { public void run() {
            root.removeView(o);
        }}).start();
    }
}
