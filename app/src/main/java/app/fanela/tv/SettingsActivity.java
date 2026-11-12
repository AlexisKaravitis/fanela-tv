package app.fanela.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings (SPEC-APP 5), built for the remote, and the first start ("setup" extra): language and country, then clubs,
 * Done / Skip. Your clubs: the league chips (the viewer's own first, follow counts) over that league's club tiles; OK
 * on a tile follows or unfollows at once. Options: language and favourite cycle in place, country opens its list,
 * switches toggle, Appearance opens its page, the About texts open in a panel. Every change is saved at once and the
 * data refreshes in the background. Screens stack on top of each other (Appearance, Country, the text panel) and Back
 * takes them off one at a time; the D-pad walks each screen's lines of items (SettingsViews.Page).
 */
public class SettingsActivity extends Activity implements AppearancePage.Host {
    private static final int COLS = 7;
    private static final float TILE_STEP_X = SettingsViews.ClubTile.W + 30, TILE_STEP_Y = SettingsViews.ClubTile.H + 28;
    private final Handler ui = new Handler(Looper.getMainLooper());
    // work: quick row/provider actions in order; images: the club tiles' crests (decoded); fetches: those not on the TV
    // yet (downloads, apart so a slow one never holds up the rest); previews: Appearance's card previews, apart so
    // they never wait behind crests; refresher, lists: downloads; rows: republishing
    private ExecutorService work, images, fetches, previews, refresher, lists, rows;
    private SettingsLogic.Coalescer republisher;
    private final ArrayDeque<long[]> prompts = new ArrayDeque<>();  // {channel id} waiting for the system prompt
    private final Map<Long, String> labels = new HashMap<>();
    private SharedPreferences settings;
    private float s;
    private boolean resumed, setup, leaving;
    private Object backCallback;
    private final Runnable poll = new Runnable() { public void run() { refreshStatus(); tickClocks(); ui.postDelayed(this, 5000); } };

    // ---------- the screens ----------
    private FrameLayout root;
    private AppViews.FocusLayer focusLayer;
    private SettingsViews.Page main;
    /** Screens over the main page, top last, and the focus each one returns to when it closes. */
    private final List<View> stack = new ArrayList<>();
    private final List<View> returns = new ArrayList<>();
    private AppearancePage appearance;
    private SettingsViews.TextPanel panel;
    /** Which text the panel shows ("credits", "privacy"), to open it again after the system re-creates the screen. */
    private String panelId;

    // ---------- the main page ----------
    private String league;  // the league the grid shows, or a sport's chip ("sport:f1": SportsSettings)
    private SportsSettings sportPanel;
    private SettingsViews.Text clubsHint;
    private SettingsViews.Block hintBlock;
    private SettingsViews.ChipBar chipBar;
    private final List<SettingsViews.Chip> chips = new ArrayList<>();
    private FrameLayout grid;
    private SettingsViews.Block gridBlock, hiddenBlock, statusBlock, favBlock;
    private final List<SettingsViews.ClubTile> tiles = new ArrayList<>();
    private View clubsAnchor, optionsAnchor, aboutAnchor;
    private SettingsViews.OptionRow langRow, favRow, countryRow, basketRow, rowsRow, appearanceRow, statusRow;
    private final List<SettingsViews.OptionRow> options = new ArrayList<>(), about = new ArrayList<>(), hidden = new ArrayList<>();
    private final Map<View, SettingsViews.Block> rowBlocks = new HashMap<>();
    private FrameLayout hiddenBox;
    private SettingsViews.Pill skip, done;
    private String shownHidden = "";  // the rows listed under "Home-screen rows", to skip a rebuild that would move focus

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);  // the page rises in itself (220 ms)
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        // the first start until it is done or skipped once; after that "Choose clubs" (the empty state) gets Settings
        setup = b != null ? b.getBoolean("setup") : SettingsLogic.firstStart(getIntent().getBooleanExtra("setup", false), Teams.chosen(this));
        I18n.load(this);
        Ui.fonts(this);
        Ui.theme(this);
        SettingsViews.theme(this);
        s = Ui.scale(this);
        work = Executors.newSingleThreadExecutor();
        images = Executors.newFixedThreadPool(2);
        fetches = Executors.newFixedThreadPool(3);
        refresher = Executors.newSingleThreadExecutor();
        lists = Executors.newSingleThreadExecutor();
        rows = Executors.newSingleThreadExecutor();
        final Context app = getApplicationContext();
        republisher = new SettingsLogic.Coalescer(rows, new Runnable() { public void run() { republishNow(app); } });
        league = b != null && b.getString("league") != null ? b.getString("league") : getIntent().getStringExtra("league");
        if (league == null || Leagues.get(league) == null && !(BuildConfig.SPORTS && SportsSettings.isChip(league)))
            league = Leagues.first(Leagues.country(this));
        sportPanel = new SportsSettings(this, sportHost());
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.APP_BG);
        focusLayer = new AppViews.FocusLayer(this);
        root.addView(focusLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildMain();
        setContentView(root);
        View first = setup ? langRow : !tiles.isEmpty() ? tiles.get(0) : sportPanel.sport() != null ? main.findViewWithTag("sporttile:" + sportPanel.sport()) : selectedChip();
        if (first == null) first = selectedChip();
        View was = b == null ? null : tagged(main, b.getString("focus"));  // re-created by the system: where the viewer was
        if (was != null) first = was;
        // a sport's tiles arrive after the screen is built (the standings, the stored cards): the focus goes back then
        else if (b != null && b.getString("focus") != null && sportPanel.sport() != null) sportPanel.pendingFocus = b.getString("focus");
        if (first != null) { first.requestFocus(); main.reveal(first, false); }
        if (b != null) reopen(b.getString("screen"), b.getString("screenFocus"));
        // the shown chip in view even when the focus is elsewhere (a sport's chip sits at the far end of the row)
        final SettingsViews.Chip shownChip = selectedChip();
        if (shownChip != null && !shownChip.isFocused())
            chipBar.post(new Runnable() { public void run() { if (!gone() && chipBar.getWidth() > 0) chipBar.reveal(shownChip, false); } });
        tickClocks();
        root.setAlpha(0);
        root.setTranslationY(Ui.PUSH_RISE * s);
        root.animate().alpha(1).translationY(0).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN).start();
        updateBack();
    }

    /**
     * The system may end the app while it is in the background and re-create this screen when the viewer comes back:
     * it comes back as it was left. A first start stays one, even after a club was followed (which marks it done); the
     * league shown, the screen on top (Appearance, Country, a text) and the focused item on each are kept.
     */
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("setup", setup);
        out.putString("league", league);
        View f = getCurrentFocus();
        out.putString("focus", key(stack.isEmpty() ? f : returns.get(0)));
        String screen = screenId();
        if (screen != null) {
            out.putString("screen", screen);
            out.putString("screenFocus", key(f));
        }
    }

    /** The screen on top, as reopen() knows it; null for the main page. */
    private String screenId() {
        View top = top();
        if (stack.isEmpty() || leaving) return null;
        if (appearance != null && top == appearance.page) return "appearance";
        if (panel != null && top == panel.getParent()) return panelId == null ? null : "text:" + panelId;
        return "country";  // the only other screen
    }

    /** Opens the screen that was on top again, its focus where it was (else where the screen puts it). */
    private void reopen(String screen, String focus) {
        if (screen == null) return;
        SettingsViews.Page page = null;
        if (screen.equals("appearance") && appearanceRow != null) { openAppearance(); page = appearance.page; }
        else if (screen.equals("country") && countryRow != null) { openCountry(); page = (SettingsViews.Page) top(); }
        else if (screen.equals("text:credits") && !setup) openCredits();
        else if (screen.equals("text:privacy") && !setup) openPrivacy();
        View f = page == null ? null : tagged(page, focus);
        if (f != null) { f.requestFocus(); page.reveal(f, false); }
    }

    /** A view's focus key (its tag): "lang", "club:435", "chip:eng.1", "style:kit"...; null when it has none. */
    private static String key(View v) { return v != null && v.getTag() instanceof String ? (String) v.getTag() : null; }

    private static View tagged(View in, String key) { return key == null || in == null ? null : in.findViewWithTag(key); }

    @Override protected void onStart() { super.onStart(); Brand.shown(); }

    @Override protected void onStop() { super.onStop(); Brand.hidden(this); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ui.post(poll);
        resumePrompts();
        if (!prompts.isEmpty()) askNext();  // the next queued "show this row?" question, after the previous one closed
        checkHiddenRows();
    }

    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (appearance != null) appearance.release();
        if (main != null) main.release();
        if (sportPanel != null) sportPanel.clear();
        if (work != null) work.shutdown();          // let queued saves / row changes finish
        skeletons(false);
        if (images != null) images.shutdownNow();   // images are not needed any more
        if (fetches != null) fetches.shutdownNow();
        if (previews != null) previews.shutdownNow();
        if (refresher != null) refresher.shutdown();
        if (lists != null) lists.shutdownNow();
        if (rows != null) rows.shutdown();          // a republish that was asked for still happens
        if (backCallback != null) backCallback = Ui.onBack(this, backCallback, null);
        super.onDestroy();
    }

    public boolean gone() { return isFinishing() || isDestroyed(); }

    // ---------- keys ----------
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        int dir = code == KeyEvent.KEYCODE_DPAD_UP ? SettingsLogic.UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? SettingsLogic.DOWN
            : code == KeyEvent.KEYCODE_DPAD_LEFT ? SettingsLogic.LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? SettingsLogic.RIGHT : -1;
        if (dir < 0 || leaving) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) move(dir);
        return true;  // the screens decide every D-pad move: nothing escapes to a view the spec doesn't list
    }

    private void move(int dir) {
        View top = top();
        if (top != null && panel != null && top == panel.getParent()) {
            if (dir == SettingsLogic.UP || dir == SettingsLogic.DOWN) panel.scrollBy(dir == SettingsLogic.UP ? -SettingsViews.TextPanel.STEP : SettingsViews.TextPanel.STEP);
            return;
        }
        SettingsViews.Page page = top instanceof SettingsViews.Page ? (SettingsViews.Page) top : main;
        View f = getCurrentFocus();
        View next = page.move(f, dir);
        if (next == null) return;
        next.requestFocus();
        page.reveal(next, true);
    }

    // ---------- Back ----------
    /**
     * Back on Android 8-12: the activity's own Back (on the key's release, as the callback below on 13+). Both go
     * through handleBack, so Back does the same on every version. Lint's GestureBackNavigation is about 13+, where
     * this is never called (enableOnBackInvokedCallback: the callback takes Back, key and gesture alike).
     */
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    /**
     * Back (SettingsLogic.back): takes the top screen off, ends the first start (Back = Skip), or lets the last page
     * fall away; false once it is already falling away, when the system closes the screen at once.
     */
    private boolean handleBack() {
        switch (SettingsLogic.back(stack.size(), setup, leaving)) {
            case SettingsLogic.BACK_POP: pop(); return true;
            case SettingsLogic.BACK_FINISH_SETUP: finishSetup(); return true;
            case SettingsLogic.BACK_LEAVE: leave(); return true;
            default: return false;
        }
    }

    /**
     * Android 13+: Back reaches the app only through an OnBackInvokedCallback, registered while Settings handles Back
     * itself (every screen: Back reverses its push) and unregistered once the last page is falling away. Called
     * whenever a screen opens or closes.
     */
    private void updateBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = SettingsLogic.back(stack.size(), setup, leaving) != SettingsLogic.BACK_SYSTEM;
        if (need == (backCallback != null)) return;
        backCallback = Ui.onBack(this, backCallback, !need ? null : new Runnable() { public void run() { if (!handleBack()) finish(); } });
    }

    private void finishSetup() {
        settings.edit().putBoolean("onboarded", true).apply();
        leave();
    }

    /** Back on the last screen: the page falls away (220 ms), then the activity closes without a system animation. */
    private void leave() {
        if (leaving) return;
        leaving = true;
        updateBack();
        root.animate().alpha(0).translationY(Ui.PUSH_RISE * s).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN)
            .withEndAction(new Runnable() { public void run() { finish(); overridePendingTransition(0, 0); } }).start();
    }

    // ---------- the screen stack ----------
    private View top() { return stack.isEmpty() ? main : stack.get(stack.size() - 1); }

    /** Puts a screen on top (220 ms fade + 16 px rise); the one under it keeps still and takes no focus. */
    private void push(final View screen, View focus, final boolean opaque) {
        final View under = top();
        returns.add(getCurrentFocus());
        stack.add(screen);
        if (under instanceof ViewGroup) ((ViewGroup) under).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(screen, root.indexOfChild(focusLayer), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.setAlpha(0);
        screen.setTranslationY(Ui.PUSH_RISE * s);
        screen.animate().alpha(1).translationY(0).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN)
            .withEndAction(new Runnable() { public void run() { if (opaque && stack.contains(screen)) under.setVisibility(View.INVISIBLE); } }).start();
        if (focus != null) focus.requestFocus();
        tickClocks();
        updateBack();
    }

    /** Takes the top screen off (the reverse motion) and gives the focus back to where it was. */
    private void pop() {
        if (stack.isEmpty()) return;
        final View screen = stack.remove(stack.size() - 1);
        View back = returns.remove(returns.size() - 1);
        View under = top();
        under.setVisibility(View.VISIBLE);
        if (under instanceof ViewGroup) ((ViewGroup) under).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (screen instanceof ViewGroup) ((ViewGroup) screen).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (appearance != null && screen == appearance.page) {
            appearance.release();
            appearance = null;
            showAppearanceValue();
        }
        if (panel != null && screen == panel.getParent()) panel = null;
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
        screen.animate().alpha(0).translationY(Ui.PUSH_RISE * s).setDuration(Ui.PUSH_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN)
            .withEndAction(new Runnable() { public void run() { root.removeView(screen); } }).start();
        updateBack();
    }

    private void tickClocks() {
        String t = AppText.clock(System.currentTimeMillis());
        if (main != null) main.clock.set(t);
        for (View v : stack) if (v instanceof SettingsViews.Page) ((SettingsViews.Page) v).clock.set(t);
    }

    // ---------- the main page ----------
    /** Builds (or rebuilds: language, country) the main page: Settings, or the first start. */
    private void buildMain() {
        if (main != null) { main.release(); root.removeView(main); }
        chips.clear(); tiles.clear(); options.clear(); about.clear(); hidden.clear(); rowBlocks.clear();
        langRow = favRow = countryRow = basketRow = rowsRow = appearanceRow = statusRow = null;
        skip = done = null;
        hiddenBox = null;
        hiddenBlock = statusBlock = favBlock = null;
        clubsAnchor = optionsAnchor = aboutAnchor = null;
        shownHidden = "";
        main = new SettingsViews.Page(this, !setup);
        SettingsViews.Page p = main;
        float W = 1728;
        if (setup) {
            String skipL = AppText.t("skip_short"), doneL = I18n.t("done");
            float dw = SettingsViews.Pill.width(doneL), sw = SettingsViews.Pill.width(skipL), room = W - dw - sw - 16 - 32;
            SettingsViews.Text title = new SettingsViews.Text(this, I18n.t("welcome_title"), Ui.BOLD, 56, Ui.TXT, room, 56 * 1.1f, 1).heading();
            p.title(I18n.t("welcome_title"));
            SettingsViews.Block tb = p.add(title, 96, room, title.height(), 0);
            done = new SettingsViews.Pill(this, doneL, Ui.S3);
            skip = new SettingsViews.Pill(this, skipL, Ui.alpha(0xFFFFFFFF, 0.14f));
            done.setTag("done");
            skip.setTag("skip");
            View.OnClickListener end = new View.OnClickListener() { public void onClick(View v) { finishSetup(); } };
            done.setOnClickListener(end);
            skip.setOnClickListener(end);
            p.beside(done, tb, 1824 - dw - 6, dw + 12, 76, -6);
            p.beside(skip, tb, 1824 - dw - 16 - sw - 6, sw + 12, 76, -6);
            SettingsViews.Text intro = new SettingsViews.Text(this, I18n.t("welcome_text"), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), W, 0, 3);
            p.add(intro, 96, W, intro.height(), 14);
            SettingsViews.Text lc = heading(I18n.t("language_country_title"));
            p.add(lc, 96, W, lc.height(), 36);
            langRow = languageRow();
            countryRow = countryRow();
            addRow(p, langRow, 20);
            addRow(p, countryRow, 14);
        } else {
            SettingsViews.Text title = new SettingsViews.Text(this, I18n.t("settings"), Ui.BOLD, 56, Ui.TXT, 1100, 56 * 1.1f, 1).heading();
            p.title(I18n.t("settings"));
            p.add(title, 96, 1100, title.height(), 0);
        }

        // ---------- clubs ----------
        // with Formula 1 and UFC: "Your clubs and sports" (SPEC-F1-UFC 10), its hint line following the chip
        SettingsViews.Text clubs = heading(I18n.t(BuildConfig.SPORTS ? "clubs_sports" : "clubs"));
        p.add(clubs, 96, W, clubs.height(), setup ? 40 : 44);
        clubsAnchor = setup ? clubs : null;  // Settings: the clubs are the page's top; first start: under their heading
        clubsHint = new SettingsViews.Text(this, chipHint(), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), W, 0, 2);
        hintBlock = p.add(clubsHint, 96, W, clubsHint.height(), 8);
        chipBar = new SettingsViews.ChipBar(this);
        p.add(chipBar, 96, W, 60, setup ? 22 : 28);
        buildChips();
        grid = new FrameLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        gridBlock = p.add(grid, 96, COLS * TILE_STEP_X - 30, 60, setup ? 26 : 30);

        if (!setup) {
            // ---------- options ----------
            SettingsViews.Text opt = heading(I18n.t("options"));
            p.add(opt, 96, W, opt.height(), 44);
            optionsAnchor = opt;
            langRow = languageRow();
            addRow(p, langRow, 20);
            favRow = favouriteRow();
            favBlock = addRow(p, favRow, 14);
            showFavourite();
            countryRow = countryRow();
            addRow(p, countryRow, 14);
            basketRow = row("basket", I18n.t("basketball"), I18n.t("basketball_hint"));
            showSwitch(basketRow, Teams.basketball(this));
            basketRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                settings.edit().putBoolean("basket", !Teams.basketball(SettingsActivity.this)).commit();
                showSwitch(basketRow, Teams.basketball(SettingsActivity.this));
                changed();
            }});
            addRow(p, basketRow, 14);
            if (HomeRows.supported(this)) {
                // QAS-12: a followed sport has its row too (nothing followed: the clubs' words, as before)
                rowsRow = row("rows", I18n.t("rows"), I18n.t(Sports.followed(this).isEmpty() ? "rows_hint" : "rows_hint_sports"));
                showSwitch(rowsRow, settings.getBoolean("rows", true));
                rowsRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    boolean on = !settings.getBoolean("rows", true);
                    settings.edit().putBoolean("rows", on).commit();
                    if (on) for (String id : Teams.followed(SettingsActivity.this)) { Teams.Club k = Teams.get(SettingsActivity.this, id); if (k != null) addRow(k); }
                    if (on) for (String sp : Sports.followed(SettingsActivity.this)) addSportRow(sp);
                    showSwitch(rowsRow, on);
                    changed();
                    checkHiddenRows();
                }});
                addRow(p, rowsRow, 14);
                // rows the TV is hiding, right under the switch they belong to and set in from it
                hiddenBox = new FrameLayout(this);
                hiddenBox.setClipChildren(false);
                hiddenBlock = p.add(hiddenBox, 96, W, 0, 0);
                hiddenBox.setVisibility(View.GONE);
            } else {
                rowsRow = row("rows", I18n.t("rows"), I18n.t("rows_unsupported"));
                addRow(p, rowsRow, 14);
            }
            appearanceRow = row("appearance", AppText.t("appearance"), AppText.t("appearance_hint"));
            appearanceRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openAppearance(); } });
            showAppearanceValue();
            addRow(p, appearanceRow, 14);

            // ---------- about ----------
            SettingsViews.Text ab = heading(I18n.t("about"));
            p.add(ab, 96, W, ab.height(), 40);
            aboutAnchor = ab;
            SettingsViews.OptionRow ind = row("independent", I18n.t("independent_title"), I18n.t("independent"));
            addAbout(p, ind, 20);
            SettingsViews.OptionRow credits = row("credits", I18n.t("credits"), I18n.t("credits_short"));
            credits.value(I18n.t("read"), false, false);
            credits.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openCredits(); } });
            addAbout(p, credits, 14);
            SettingsViews.OptionRow privacy = row("privacy", I18n.t("privacy"), I18n.t("privacy_short"));
            privacy.value(I18n.t("read"), false, false);
            privacy.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openPrivacy(); } });
            addAbout(p, privacy, 14);
            addAbout(p, versionRow(), 14);
            statusRow = row("status", I18n.t("status"), "");
            statusBlock = addAbout(p, statusRow, 14);
            refreshStatus();
        }
        p.relayout();
        showLeague(league);  // fills the grid, lays the page out again and lists its lines
        root.addView(main, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!stack.isEmpty()) {
            main.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            main.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Rebuilds the main page (new words or leagues) and puts the focus back on the item with this key. The new page
     * starts with no hidden-row list, and nothing pauses the screen here (unlike 1.1's recreate()), so the rows the TV
     * is hiding are looked up again at once.
     */
    private void rebuild(String key) {
        boolean hidden = !stack.isEmpty();
        buildMain();
        checkHiddenRows();
        tickClocks();
        View f = main.findViewWithTag(key);
        if (f == null) f = main.first();
        if (hidden) {  // restored when the screen on top closes
            if (!returns.isEmpty()) returns.set(0, f);
            return;
        }
        if (f != null) { f.requestFocus(); main.reveal(f, false); }
    }

    private SettingsViews.Text heading(String t) {
        return new SettingsViews.Text(this, t, Ui.MEDIUM, 40, Ui.TXT, 1728, 40 * 1.2f, 1).heading();
    }

    private SettingsViews.OptionRow row(String key, String title, String hint) {
        SettingsViews.OptionRow r = new SettingsViews.OptionRow(this, key, title, hint, 1728);
        r.setTag(key);
        return r;
    }

    private SettingsViews.Block addRow(SettingsViews.Page p, SettingsViews.OptionRow r, float mt) {
        SettingsViews.Block b = p.add(r, 96, 1728, r.height(), mt);
        rowBlocks.put(r, b);
        options.add(r);
        return b;
    }

    private SettingsViews.Block addAbout(SettingsViews.Page p, SettingsViews.OptionRow r, float mt) {
        SettingsViews.Block b = p.add(r, 96, 1728, r.height(), mt);
        rowBlocks.put(r, b);
        about.add(r);
        return b;
    }

    /** A row's height may have changed (a value, a wrapped hint): lays the page out again. */
    private void resized(SettingsViews.OptionRow r) {
        SettingsViews.Block b = rowBlocks.get(r);
        if (b == null || b.h == r.height()) return;
        b.h = r.height();
        main.relayout();
    }

    private void showSwitch(SettingsViews.OptionRow r, boolean on) {
        r.value(I18n.t(on ? "yes" : "no"), true, on);
        resized(r);
    }

    /** The D-pad's lines, top to bottom (after anything is added, removed or refilled). */
    private void relines() {
        List<SettingsViews.Line> l = main.lines;
        l.clear();
        if (setup) {
            l.add(new SettingsViews.Line(null, Arrays.asList(skip, done)));
            l.add(new SettingsViews.Line(null, Arrays.asList(langRow)));
            l.add(new SettingsViews.Line(null, Arrays.asList(countryRow)));
        }
        SettingsViews.Line cl = new SettingsViews.Line(clubsAnchor, chips);
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).id.equals(league)) cl.preferred = i;
        l.add(cl);
        for (int r = 0; r * COLS < tiles.size(); r++)
            l.add(new SettingsViews.Line(clubsAnchor, tiles.subList(r * COLS, Math.min(tiles.size(), (r + 1) * COLS))));
        for (List<View> items : sportPanel.lines()) l.add(new SettingsViews.Line(clubsAnchor, items));
        if (setup) { main.fitAnchors(); return; }
        for (SettingsViews.OptionRow o : options) {
            if (o.getVisibility() != View.VISIBLE) continue;
            l.add(new SettingsViews.Line(optionsAnchor, Arrays.asList(o)));
            if (o == rowsRow) for (SettingsViews.OptionRow h : hidden) l.add(new SettingsViews.Line(optionsAnchor, Arrays.asList(h)));
        }
        for (SettingsViews.OptionRow o : about) l.add(new SettingsViews.Line(aboutAnchor, Arrays.asList(o)));
        main.fitAnchors();
    }

    // ---------- leagues and clubs ----------
    /**
     * One chip per league, the viewer's own first, under "Football" and "Basketball". The shown league is in the club
     * colour; resting on a chip for a moment, or OK, shows its clubs. A follow count sits in a badge of its own.
     */
    private void buildChips() {
        boolean basketShown = false;
        int i = 0;
        for (final Leagues.League l : Leagues.ordered(Leagues.country(this))) {
            if (i++ == 0 || l.basketball() && !basketShown) {
                basketShown |= l.basketball();
                chipBar.add(new SettingsViews.GroupLabel(this, I18n.t(l.basketball() ? "basketball_group" : "football_group")));
            }
            final SettingsViews.Chip chip = new SettingsViews.Chip(this, l.id, l.label());
            chip.setTag("chip:" + l.id);
            final Runnable pick = new Runnable() { public void run() {
                if (!chip.isFocused() || l.id.equals(league) || gone()) return;
                select(l.id);
            }};
            chip.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                ui.removeCallbacks(pick);
                if (has) ui.postDelayed(pick, 300);  // a short rest on a league shows it, no OK needed
            }});
            chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                ui.removeCallbacks(pick);
                if (!l.id.equals(league)) select(l.id);
            }});
            chips.add(chip);
            chipBar.add(chip);
        }
        if (BuildConfig.SPORTS) {  // "More sports": Formula 1 and UFC, their panels in the grid (SportsSettings)
            chipBar.add(new SettingsViews.GroupLabel(this, I18n.t("sports_group")));
            for (String sp : Sports.ALL) {
                final String id = SportsSettings.chipId(sp);
                final SettingsViews.Chip chip = new SettingsViews.Chip(this, id, SportsSettings.sportName(sp));
                chip.setTag("chip:" + id);
                final Runnable pick = new Runnable() { public void run() {
                    if (!chip.isFocused() || id.equals(league) || gone()) return;
                    select(id);
                }};
                chip.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                    ui.removeCallbacks(pick);
                    if (has) ui.postDelayed(pick, 300);
                }});
                chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    ui.removeCallbacks(pick);
                    if (!id.equals(league)) select(id);
                }});
                chips.add(chip);
                chipBar.add(chip);
            }
        }
        recount();
    }

    /** Follow counts and the shown league on the chips (again each time: a list may have arrived since). */
    private void recount() {
        Set<String> follow = Teams.followed(this);
        for (SettingsViews.Chip c : chips) {
            String sp = SportsSettings.sportOfChip(c.id);  // a sport's chip counts 1 while it is followed
            c.count = sp != null ? (Sports.follows(this, sp) ? 1 : 0) : followedIn(c.id, follow);
            c.selected = c.id.equals(league);
            c.setContentDescription(c.label + (c.count > 0 ? ", " + c.count + " " + I18n.t("following").toLowerCase(I18n.locale()) : ""));
        }
        chipBar.place();
    }

    private SettingsViews.Chip selectedChip() {
        for (SettingsViews.Chip c : chips) if (c.id.equals(league)) return c;
        return chips.isEmpty() ? null : chips.get(0);
    }

    /** Shows a league's clubs; the chips stay put, so the focus doesn't jump. */
    private void select(String id) {
        league = id;
        getIntent().putExtra("league", league);
        setHint(null);
        showLeague(league);
    }

    /** The hint under "Your clubs and sports": the clubs' own, or the shown sport's. */
    private String chipHint() {
        String sp = SportsSettings.sportOfChip(league);
        return sp != null ? SportsSettings.hint(sp) : I18n.t("clubs_hint");
    }

    /** Sets the hint line (null: the shown chip's); the page moves when it takes another line. */
    private void setHint(String text) {
        if (clubsHint == null) return;
        clubsHint.set(text == null ? chipHint() : text);
        if (hintBlock != null && hintBlock.h != clubsHint.height()) {
            hintBlock.h = clubsHint.height();
            main.relayout();
        }
    }

    /** Followed clubs in a league: those picked from it, and those in its list (a EuroLeague club picked as football). */
    private int followedIn(String leagueId, Set<String> follow) {
        if (Leagues.get(leagueId) == null) return 0;
        List<Teams.Club> list = "gre.1".equals(leagueId) ? Teams.greek(this) : Teams.league(this, leagueId);
        Set<String> in = new java.util.HashSet<>();
        if (list != null) for (Teams.Club k : list) in.add(k.id);
        int n = 0;
        for (String id : follow) {
            Teams.Club k = Teams.get(this, id);
            if (in.contains(id) || k != null && leagueId.equals(k.league)) n++;
        }
        return n;
    }

    /** Fills the grid with a league's clubs (downloaded first if this TV has never listed them). */
    private void showLeague(final String lg) {
        View had = getCurrentFocus();
        boolean focusInGrid = had != null && (tiles.contains(had) || sportPanel.contains(had));
        grid.removeAllViews();
        tiles.clear();
        sportPanel.clear();
        recount();
        skeletons(false);
        String sport = SportsSettings.sportOfChip(lg);
        if (sport != null) {  // Formula 1 or UFC: the sport's panel in the grid
            grid.setContentDescription(null);
            sportPanel.show(sport);
            if (focusInGrid) { View c = selectedChip(); if (c != null) { c.requestFocus(); main.reveal(c, true); } }
            return;
        }
        List<Teams.Club> clubs = Teams.sorted(this, lg);
        if (clubs == null) {
            // the list is on its way: two rows of skeleton tiles in the grid's slots, pulsing (never a bare line of text)
            for (int i = 0; i < 2 * COLS; i++)
                grid.addView(new SettingsViews.SkeletonTile(this), AppViews.at(this, (i % COLS) * TILE_STEP_X, (i / COLS) * TILE_STEP_Y,
                    SettingsViews.ClubTile.W, SettingsViews.ClubTile.H));
            grid.setContentDescription(I18n.t("loading_clubs"));
            grid.announceForAccessibility(I18n.t("loading_clubs"));
            gridBlock.h = 2 * TILE_STEP_Y - 28;
            main.relayout();
            relines();
            skeletons(true);
            final Context app = getApplicationContext();
            lists.execute(new Runnable() { public void run() {
                Engine.background();
                final boolean ok = Teams.refreshLeague(app, lg, false);
                ui.post(new Runnable() { public void run() {
                    if (gone() || !lg.equals(league)) return;
                    if (ok && Teams.sorted(SettingsActivity.this, lg) != null) {
                        View now = getCurrentFocus();
                        showLeague(lg);
                        if ((now == null || !now.isAttachedToWindow()) && !tiles.isEmpty()) tiles.get(0).requestFocus();
                    } else {  // the skeletons give way to the reason
                        skeletons(false);
                        grid.removeAllViews();
                        grid.setContentDescription(null);
                        SettingsViews.Text failed = new SettingsViews.Text(SettingsActivity.this, I18n.t("clubs_failed"), Ui.REGULAR, 30,
                            Ui.alpha(Ui.TXT, Ui.A2), 1728, 0, 2);
                        grid.addView(failed, AppViews.at(SettingsActivity.this, 0, 0, 1728, 60));
                        failed.announceForAccessibility(I18n.t("clubs_failed"));
                        gridBlock.h = 60;
                        main.relayout();
                        relines();
                    }
                }});
            }});
            if (focusInGrid) { View c = selectedChip(); if (c != null) { c.requestFocus(); main.reveal(c, true); } }
            return;
        }
        if (!"gre.1".equals(lg)) {  // weekly check for promoted clubs; shown next time the grid opens
            final Context app = getApplicationContext();
            lists.execute(new Runnable() { public void run() { Engine.background(); Teams.refreshLeague(app, lg, false); } });
        }
        grid.setContentDescription(null);
        sortByShownName(clubs);
        Set<String> follow = Teams.followed(this);
        for (int i = 0; i < clubs.size(); i++) {
            SettingsViews.ClubTile t = clubTile(clubs.get(i), follow.contains(clubs.get(i).id));
            grid.addView(t, AppViews.at(this, (i % COLS) * TILE_STEP_X, (i / COLS) * TILE_STEP_Y, SettingsViews.ClubTile.W, SettingsViews.ClubTile.H));
            tiles.add(t);
        }
        int rowsN = Math.max(1, (clubs.size() + COLS - 1) / COLS);
        gridBlock.h = rowsN * TILE_STEP_Y - 28;
        main.relayout();
        relines();
        if (focusInGrid) { View c = selectedChip(); if (c != null) { c.requestFocus(); main.reveal(c, true); } }
    }

    private android.animation.ValueAnimator skeletonPulse;

    /** The grid's skeleton tiles pulse (.55 -> 1 -> .55, 1.2 s, as the home screen's) while a league's list loads. */
    private void skeletons(boolean on) {
        if (!on) { if (skeletonPulse != null) { skeletonPulse.cancel(); skeletonPulse = null; } return; }
        if (skeletonPulse != null) return;
        skeletonPulse = android.animation.ValueAnimator.ofFloat(0.55f, 1f);
        skeletonPulse.setDuration(Ui.SKELETON_MS / 2);
        skeletonPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        skeletonPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        skeletonPulse.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View t = grid.getChildAt(i);
                    if (t instanceof SettingsViews.SkeletonTile) { ((SettingsViews.SkeletonTile) t).pulse = v; t.invalidate(); }
                }
            }
        });
        skeletonPulse.start();
    }

    /** Followed first, then by the name each tile shows, ignoring a leading "FC", "CD", "AS" and the like. */
    private void sortByShownName(List<Teams.Club> clubs) {
        final SettingsLogic.Measure m = SettingsViews.measurer(SettingsViews.textPaint(Ui.MEDIUM, 28, 0));
        final Set<String> f = Teams.followed(this);
        final java.text.Collator col = java.text.Collator.getInstance(I18n.locale());
        final Map<String, String> key = new HashMap<>();
        for (Teams.Club k : clubs)
            key.put(k.id, SettingsLogic.tileName(k.label(), I18n.shortName(k.label()), I18n.el(), m, SettingsViews.ClubTile.W - 28)
                .replaceFirst("^(?i)(C[.]D[.]|CD|1[.] ?FC|1[.] FSV|AFC|FC|SC|CF|AC|AS|RB|SV|TSG|VfB|VfL) ", ""));
        java.util.Collections.sort(clubs, new java.util.Comparator<Teams.Club>() {
            public int compare(Teams.Club a, Teams.Club b) {
                boolean fa = f.contains(a.id), fb = f.contains(b.id);
                if (fa != fb) return fa ? -1 : 1;
                return col.compare(key.get(a.id), key.get(b.id));
            }
        });
    }

    private SettingsViews.ClubTile clubTile(final Teams.Club k, boolean followed) {
        final SettingsViews.ClubTile t = new SettingsViews.ClubTile(this, k, followed);
        t.setTag("club:" + k.id);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Set<String> follow = new LinkedHashSet<>(Teams.followed(SettingsActivity.this));
            boolean wasEmpty = follow.isEmpty();
            t.on = !follow.contains(k.id);
            if (t.on) follow.add(k.id); else follow.remove(k.id);
            Teams.setFollowed(SettingsActivity.this, follow, t.on ? k : null);
            settings.edit().putBoolean("onboarded", true).apply();
            Ui.theme(SettingsActivity.this);  // the favourite club (the first followed until one is chosen) sets the colours
            SettingsViews.theme(SettingsActivity.this);
            t.describe();
            t.announceForAccessibility(t.getContentDescription());
            recount();
            if (wasEmpty != follow.isEmpty() || favRow != null) showFavourite();
            repaint(root);
            if (t.on && HomeRows.enabled(SettingsActivity.this)) addRow(k);
            changed();
            refreshStatus();
        }});
        // the crest (cached on disk after the first time) is decoded in the background; one not on the TV is downloaded
        // on a pool of its own, so a slow download never holds up the crests that are here. Meanwhile the tile shows
        // the club's monogram on its squircle.
        final Context app = getApplicationContext();
        final int size = Math.round(88 * Ui.SCALE_TILE * s);
        images.execute(new Runnable() { public void run() {
            Engine.background();
            if (k.crest == null || k.crest.isEmpty()) return;  // the monogram stays
            if (Cards.have(app, k.crest)) { post(t, crest(app, k, size)); return; }  // on the TV (or shipped)
            try {
                fetches.execute(new Runnable() { public void run() {
                    Engine.background();
                    Net.fetchImage(app, k.crest);  // then the crest, when it arrives
                    if (Net.imageFile(app, k.crest).exists()) post(t, crest(app, k, size));
                }});
            } catch (java.util.concurrent.RejectedExecutionException ignored) { }  // the screen is closing
        }});
        return t;
    }

    private void post(final SettingsViews.ClubTile t, final Bitmap b) {
        if (b == null) return;
        ui.post(new Runnable() { public void run() { if (!gone()) t.crest(b); } });
    }

    /** A crest at the tile's size (sharp at the focused scale); null when the TV has no readable crest. */
    private static Bitmap crest(Context c, Teams.Club k, int size) {
        Bitmap raw = Net.image(c, k.crest);
        if (raw == null) return null;
        Bitmap src = CrestFill.filled(raw);  // an outline crest as the club prints it (white inside: Olympiacos)
        if (src != raw) raw.recycle();
        float scale = Math.min((float) size / src.getWidth(), (float) size / src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * scale)), h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap half = Cards.smooth(src, w);
        Bitmap out = Bitmap.createScaledBitmap(half, w, h, true);
        if (half != src && half != out) half.recycle();
        if (src != out) src.recycle();
        return out;
    }

    /** Everything drawn from the theme (a new favourite club): redrawn in place. */
    private static void repaint(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) repaint(((ViewGroup) v).getChildAt(i));
    }

    /** "Last checked against the official pages: Premier League 11 Nov 2026, ..." for the Sources and credits page. */
    private String checkedList() {
        Map<String, String[]> ch = SeasonTableProvider.checked();
        StringBuilder b = new StringBuilder();
        java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("d MMM yyyy", I18n.locale());
        for (Leagues.League l : Leagues.ALL) {
            String[] c = ch.get(l.id);
            if (c == null || c[0].isEmpty()) continue;
            String date = c[0];
            try { date = out.format(in.parse(c[0])); } catch (Exception ignored) { }
            b.append("\n").append("• ").append(l.label()).append(": ").append(date);
            if ("none".equals(c[1])) b.append(" · ").append(I18n.t("no_official_list"));
        }
        return b.length() == 0 ? "" : "\n\n" + I18n.t("checked_title") + b;
    }

    /**
     * The fonts' licences in full, from the files that ship with them (res/raw): Google Sans (SIL OFL 1.1) and Roboto
     * Condensed (Apache License 2.0, which asks for a copy of it to go with the font). At the end of Sources and
     * credits, in English as written.
     */
    private String fontLicences() {
        StringBuilder b = new StringBuilder("\n\n").append(AppText.t("font_licences"));
        for (int id : new int[]{R.raw.ofl_google_sans, R.raw.apache_roboto, R.raw.circuits_license}) {
            try (java.io.InputStream in = getResources().openRawResource(id)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                b.append("\n\n").append(SettingsLogic.reflow(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception ignored) { }
        }
        return b.toString();
    }

    // ---------- options ----------
    /** Language: OK switches between English and Greek in place (the screen is redrawn in the new words). */
    private SettingsViews.OptionRow languageRow() {
        // the Greek word only where a Greek speaker may be looking for it: a Greek TV, or a viewer in Greece or Cyprus
        String cc = Leagues.country(this);
        boolean both = I18n.el() || "el".equals(java.util.Locale.getDefault().getLanguage()) || "GR".equals(cc) || "CY".equals(cc);
        SettingsViews.OptionRow r = row("lang", I18n.t(both ? "language" : "language_plain"), I18n.t("language_hint"));
        r.value(I18n.t("lang_value"), false, false);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String next = SettingsLogic.next(Arrays.asList("en", "el"), I18n.lang());
            settings.edit().putString("lang", next).commit();
            I18n.load(SettingsActivity.this);
            final Context app = getApplicationContext();
            work.execute(new Runnable() { public void run() {  // only text changes: redraw, no downloads
                Engine.background();
                try { Engine.draw(app); } catch (Throwable ignored) { }
            }});
            rebuild("lang");
            View f = getCurrentFocus();
            if (f != null) f.announceForAccessibility(I18n.t("lang_value"));
        }});
        return r;
    }

    /** Country: opens the list (SPEC-APP 5.6). */
    private SettingsViews.OptionRow countryRow() {
        SettingsViews.OptionRow r = row("country", I18n.t("country"), I18n.t("country_hint"));
        r.value(Leagues.countryName(Leagues.country(this)), false, false);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openCountry(); } });
        return r;
    }

    /** The club whose colours the app wears (accents and the logo's shirt): OK cycles through the followed clubs. */
    private SettingsViews.OptionRow favouriteRow() {
        final SettingsViews.OptionRow r = row("fav", I18n.t("favourite"), I18n.t("favourite_hint"));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            List<String> ids = new ArrayList<>();
            for (String id : Teams.followed(SettingsActivity.this)) if (Teams.get(SettingsActivity.this, id) != null) ids.add(id);
            if (ids.size() < 2) return;
            Teams.setFavourite(SettingsActivity.this, SettingsLogic.next(ids, Teams.favourite(SettingsActivity.this)));
            Ui.theme(SettingsActivity.this);  // the TV's app tile follows when Settings closes (Brand)
            SettingsViews.theme(SettingsActivity.this);
            showFavourite();
            repaint(root);  // the new colours everywhere on this screen
            r.announceForAccessibility(r.value);
        }});
        return r;
    }

    /** The favourite row: only with a followed club; its value is the club. */
    private void showFavourite() {
        if (favRow == null) return;
        String id = Teams.favourite(this);
        Teams.Club k = id == null ? null : Teams.get(this, id);
        favRow.value(k == null ? "–" : k.label(), false, false);
        // clubs only, also with a sport followed: the favourite gives the app its colours and cycles through clubs
        // (SPEC-F1-UFC 10), so a viewer who follows only Formula 1 or UFC has no row to cycle
        int vis = Teams.followed(this).isEmpty() ? View.GONE : View.VISIBLE;
        if (favRow.getVisibility() != vis) {
            favRow.setVisibility(vis);
            main.relayout();
            relines();
        } else resized(favRow);
    }

    /** "Signature · Club": the card style and the app layout. */
    private void showAppearanceValue() {
        if (appearanceRow == null) return;
        String style = Appearance.cardStyle(this), layout = Appearance.layout(this);
        int i = Arrays.asList(Appearance.STYLES).indexOf(style);
        appearanceRow.value(AppText.t(AppearancePage.STYLE_WORDS[Math.max(0, i)]) + " · "
            + AppText.t(Appearance.MATCHDAY.equals(layout) ? "layout_matchday" : "layout_club"), false, false);
        resized(appearanceRow);
    }

    /** The version, and where new ones come from (or the updater when this build updates itself). */
    private SettingsViews.OptionRow versionRow() {
        if (!AppUpdate.enabled()) return row("version", "Fanela " + BuildConfig.VERSION_NAME, I18n.t("releases_info"));
        final SettingsViews.OptionRow r = row("version", I18n.t("updates"), I18n.f("version", BuildConfig.VERSION_NAME));
        final Runnable show = new Runnable() { public void run() {
            AppUpdate.Release rel = AppUpdate.available(SettingsActivity.this);
            r.value(rel == null ? I18n.t("up_to_date") : I18n.f("install_version", rel.version), false, false);
        }};
        show.run();
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final AppUpdate.Release rel = AppUpdate.available(SettingsActivity.this);
            if (rel == null) return;
            Toast.makeText(SettingsActivity.this, I18n.t("update_downloading"), Toast.LENGTH_SHORT).show();
            final Context app = getApplicationContext();
            refresher.execute(new Runnable() { public void run() {
                final String err = AppUpdate.install(app, rel);
                if (err != null) ui.post(new Runnable() { public void run() { if (!gone()) Toast.makeText(SettingsActivity.this, I18n.t(err), Toast.LENGTH_LONG).show(); } });
            }});
        }});
        return r;
    }

    private void refreshStatus() {
        if (statusRow == null) return;
        StringBuilder sb = new StringBuilder();
        Set<String> follow = Teams.followed(this), sportsOn = Sports.followed(this);
        if (follow.isEmpty() && sportsOn.isEmpty()) sb.append(I18n.t("no_clubs")).append('\n');
        boolean basket = Teams.basketball(this);
        // one line when all is well; a club is named only when its data is old (offline, or a source is down)
        long latest = Math.max(Store.lastUpdateAll(this), SportEngine.lastOk(this));  // the same value as Home's "Updated …"
        StringBuilder old = new StringBuilder();
        for (String id : follow) {
            Teams.Club k = Teams.get(this, id);
            if (k == null) continue;
            for (String team : k.teams(basket)) {
                long t = Store.lastUpdate(this, id, team);
                if (t <= 0 || Store.stale(this, id, team))  // never loaded, or old
                    old.append("⚠ ").append(k.label()).append(" · ").append(I18n.t("FC".equals(team) ? "src_espn" : "src_basket"))
                        .append(": ").append(when(t)).append('\n');
            }
        }
        SharedPreferences eng = getSharedPreferences(Sports.ENGINE, MODE_PRIVATE);
        for (String sp : sportsOn) {  // a sport: named when its feed has not answered yet or not for a while
            long t = eng.getLong("ok_" + sp, 0);
            if (t <= 0 || SportEngine.stale(this, sp))
                old.append("⚠ ").append(SportsSettings.sportName(sp)).append(" · ESPN: ").append(when(t)).append('\n');
        }
        if (!follow.isEmpty() || !sportsOn.isEmpty())
            sb.append(latest <= 0 ? I18n.t("never_updated") : I18n.f("updated", I18n.day(latest).toLowerCase(I18n.locale()) + " " + I18n.time(latest))).append('\n');
        sb.append(old);
        String text = sb.toString().replaceAll("\n+$", "");
        if (text.equals(statusRow.hint)) return;
        statusRow.hint(text);
        resized(statusRow);
    }

    private String when(long t) { return t <= 0 ? I18n.t("never") : I18n.day(t) + " " + I18n.time(t); }

    // ---------- Appearance ----------
    private void openAppearance() {
        if (previews == null) previews = Executors.newSingleThreadExecutor();  // made the first time Appearance opens
        appearance = new AppearancePage(this, this, previews, ui);
        View f = appearance.launchFocus();
        push(appearance.page, f, true);
        appearance.page.reveal(f, false);
    }

    @Override public void republish() { republisher.request(); }

    /**
     * The home-screen rows in the new card style or score setting: drawn again now in the background (each card's
     * signature includes both, so every card is redrawn and gets a new image URI). Rows off or not shown by this TV:
     * nothing to do (a debug build can force it on a Google TV, SettingsDebug).
     */
    private static void republishNow(Context app) {
        Engine.background();
        long t0 = System.currentTimeMillis();
        try {
            if (HomeRows.enabled(app)) Engine.draw(app);
            else if (SettingsDebug.forceRows(app)) {
                I18n.load(app);
                Where.load(app);
                List<Match> all = Store.load(app);
                Engine.timeStates(all, System.currentTimeMillis());
                Cards.publishAll(app, all);
            } else return;
            android.util.Log.i("Fanela", "rows republished: style " + Appearance.cardStyle(app) + ", scores " + Appearance.showScores(app)
                + " (" + (System.currentTimeMillis() - t0) + " ms)");
        } catch (Throwable t) {
            android.util.Log.w("Fanela", "republish failed", t);
        }
    }

    // ---------- Country ----------
    /**
     * The country list (SPEC-APP 5.6): "This TV's country" pinned on a row of its own, a rule, then every country in
     * three columns; the chosen one has the check and the focus. OK chooses and goes back to Options.
     */
    private void openCountry() {
        final String cur = Leagues.country(this), tv = Leagues.tvCountry();
        SettingsViews.Page p = new SettingsViews.Page(this, true);
        SettingsViews.Text title = new SettingsViews.Text(this, I18n.t("country"), Ui.BOLD, 56, Ui.TXT, 1100, 56 * 1.1f, 1).heading();
        p.title(I18n.t("country"));
        p.add(title, 96, 1100, title.height(), 0);
        SettingsViews.Text hint = new SettingsViews.Text(this, I18n.t("country_hint"), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), 1400, 0, 2);
        p.add(hint, 96, 1400, hint.height(), 8);
        View focus = null;
        SettingsViews.CountryRow pinned = null;
        if (tv != null) {
            pinned = countryChoice(tv, I18n.t(Leagues.listed(tv) ? "tv_country" : "tv_country_fixtures_only"), tv.equals(cur));
            p.add(pinned, 96, 560, 110, 34);
            p.add(new SettingsViews.Rule(this), 96, 1728, 2, 28);
        }
        List<String> all = Leagues.countries(cur, null);
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        List<SettingsViews.CountryRow> rowsList = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String cc = all.get(i);
            SettingsViews.CountryRow r = countryChoice(cc, Leagues.listed(cc) ? "" : I18n.t("fixtures_only"), cc.equals(cur));
            box.addView(r, AppViews.at(this, (i % 3) * 584, (i / 3) * 116, 560, 92));
            rowsList.add(r);
            if (r.chosen) focus = r;
        }
        int n = (all.size() + 2) / 3;
        p.add(box, 96, 1728, Math.max(0, n * 116 - 24), tv != null ? 28 : 34);
        p.relayout();
        if (pinned != null) p.lines.add(new SettingsViews.Line(null, Arrays.asList(pinned)));
        for (int r = 0; r * 3 < rowsList.size(); r++) p.lines.add(new SettingsViews.Line(null, rowsList.subList(r * 3, Math.min(rowsList.size(), r * 3 + 3))));
        if (focus == null) focus = pinned != null && pinned.chosen ? pinned : p.first();
        push(p, focus, true);
        if (focus != null) p.reveal(focus, false);
    }

    private SettingsViews.CountryRow countryChoice(final String cc, String sub, boolean chosen) {
        SettingsViews.CountryRow r = new SettingsViews.CountryRow(this, cc, Leagues.countryName(cc), sub, chosen);
        r.setTag("cc:" + cc);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseCountry(cc); } });
        return r;
    }

    private void chooseCountry(String cc) {
        boolean change = !cc.equals(Leagues.country(this));
        if (change) {
            Leagues.setCountry(this, cc);
            changed();
            league = Leagues.first(cc);  // the leagues come in the new country's order
            getIntent().removeExtra("league");
            rebuild("country");
        }
        pop();
    }

    // ---------- the text panel ----------
    private void openCredits() {
        SeasonTableProvider.load(this);
        StringBuilder pages = new StringBuilder();
        for (String t : SeasonTableProvider.wikiTitles()) pages.append(pages.length() > 0 ? ", " : "").append('“').append(t).append('”');
        String sportsCredit = BuildConfig.SPORTS ? "\n\n" + I18n.t("credits_sports") : "";  // ESPN's F1 and UFC data, the circuit outlines
        openText("credits", I18n.t("credits"), I18n.f("credits_full", pages.length() > 0 ? pages.toString() : "-") + sportsCredit + checkedList() + fontLicences());
    }

    private void openPrivacy() { openText("privacy", I18n.t("privacy"), I18n.t("privacy_full")); }

    private void openText(String id, String title, String body) {
        panelId = id;
        FrameLayout overlay = new FrameLayout(this);
        overlay.addView(new SettingsViews.Scrim(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel = new SettingsViews.TextPanel(this, title, body, AppText.t("panel_hint"));
        panel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { handleBack(); } });
        overlay.addView(panel, AppViews.at(this, 260, 54, SettingsViews.TextPanel.W, SettingsViews.TextPanel.H));
        push(overlay, panel, false);
    }

    // ---------- home-screen rows (classic Android TV home only) ----------
    /** Creates (or restores) the club's row now and asks Android to show it if it is hidden. */
    private void addRow(final Teams.Club k) {
        final Context app = getApplicationContext();
        final String label = k.heading();
        work.execute(new Runnable() { public void run() {
            try {
                Net.fetchImage(app, k.crest);
                final long id = HomeRows.channel(app, k.rowKey(), label, Cards.channelLogoLazy(app, k));
                if (!HomeRows.browsable(app, id)) ui.post(new Runnable() { public void run() { requestBrowsable(id, label); } });
            } catch (Throwable ignored) { }
        }});
    }

    /** Creates (or restores) a sport's row now (EventCards.addRow) and asks Android to show it if it is hidden. */
    private void addSportRow(final String sport) {
        final Context app = getApplicationContext();
        work.execute(new Runnable() { public void run() {
            try {
                final long id = EventCards.addRow(app, sport);
                if (id == -1) return;
                final String label = SportsSettings.rowLabel(app, sport);
                if (!HomeRows.browsable(app, id)) ui.post(new Runnable() { public void run() { requestBrowsable(id, label); } });
            } catch (Throwable ignored) { }
        }});
    }

    /**
     * A sport just followed: its own pass, then its row's cards, at once on the work thread (after its row is made),
     * then after on the UI thread (the UFC panel's fighters). SportEngine.follow; the full refresh runs as before.
     */
    private void followSport(final String sport, final Runnable after) {
        final Context app = getApplicationContext();
        work.execute(new Runnable() { public void run() {
            try { SportEngine.follow(app, sport); } catch (Throwable ignored) { }
            if (after != null) ui.post(new Runnable() { public void run() { if (!gone()) after.run(); } });
        }});
    }

    // the system "add this row to the home screen?" prompt, one at a time
    private void requestBrowsable(long id, String label) {
        if (gone() || !resumed) { savePending(id, label); return; }  // asked when the screen is back
        labels.put(id, label);
        for (long[] p : prompts) if (p[0] == id) return;
        prompts.add(new long[]{id});
        savePending();
        if (prompts.size() == 1) askNext();
    }

    private void askNext() {
        long[] p = prompts.peek();
        if (p == null || gone() || !resumed) return;
        try {
            startActivityForResult(new Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContract.EXTRA_CHANNEL_ID, p[0]), 1);
        } catch (Exception e) {  // no such screen on this TV
            prompts.clear();
            savePending();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (prompts.isEmpty()) return;  // a re-created screen: the saved queue is re-checked in onResume
        long[] p = prompts.poll();
        savePending();
        if (res != RESULT_OK) {
            String l = labels.get(p[0]);
            Toast.makeText(this, I18n.f("enable_row", l == null ? "" : l), Toast.LENGTH_LONG).show();
        }
        checkHiddenRows();
        askNext();
    }

    /** Prompts not answered yet survive leaving the screen; they are asked again next time it opens. */
    private void savePending() {
        StringBuilder b = new StringBuilder();
        for (long[] p : prompts) { String l = labels.get(p[0]); b.append(p[0]).append('\t').append(l == null ? "" : l).append('\n'); }
        settings.edit().putString("pendingRows", b.toString()).apply();
    }

    private void savePending(long id, String label) {
        String cur = settings.getString("pendingRows", "");
        if (!cur.contains(id + "\t")) settings.edit().putString("pendingRows", cur + id + "\t" + label + "\n").apply();
    }

    private void resumePrompts() {
        String str = settings.getString("pendingRows", "");
        if (str.isEmpty() || !prompts.isEmpty()) return;
        settings.edit().remove("pendingRows").apply();
        if (!HomeRows.enabled(this)) return;
        final Context app = getApplicationContext();
        for (String line : str.split("\n")) {
            String[] x = line.split("\t", 2);
            try {
                final long id = Long.parseLong(x[0]);
                final String label = x.length > 1 ? x[1] : "";
                work.execute(new Runnable() { public void run() {
                    if (!HomeRows.browsable(app, id)) ui.post(new Runnable() { public void run() { requestBrowsable(id, label); } });
                }});
            } catch (Exception ignored) { }
        }
    }

    /**
     * Rows the user hid on the home screen (declined, or removed there): listed under "Home-screen rows" as
     * "PAOK · not on the home screen · Add", which asks the TV again without touching the follow list.
     */
    private void checkHiddenRows() {
        if (hiddenBox == null) return;
        if (!HomeRows.enabled(this)) { showHidden(new ArrayList<long[]>(), new ArrayList<String>()); return; }
        final Context app = getApplicationContext();
        final String pending = settings.getString("pendingRows", "");
        work.execute(new Runnable() { public void run() {
            SharedPreferences row = app.getSharedPreferences("row", MODE_PRIVATE);
            final List<long[]> ids = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            for (String id : Teams.followed(app)) {
                Teams.Club k = Teams.get(app, id);
                if (k == null) continue;
                long ch = row.getLong(k.rowKey(), -1);
                if (ch != -1 && !pending.contains(ch + "\t") && !HomeRows.browsable(app, ch)) { ids.add(new long[]{ch}); names.add(k.heading()); }
            }
            for (String sp : Sports.followed(app)) {  // the sports' rows ("sport_f1"), after the clubs'
                long ch = row.getLong(Sports.rowKey(sp), -1);
                if (ch != -1 && !pending.contains(ch + "\t") && !HomeRows.browsable(app, ch)) { ids.add(new long[]{ch}); names.add(SportsSettings.rowLabel(app, sp)); }
            }
            ui.post(new Runnable() { public void run() { if (!gone()) showHidden(ids, names); } });
        }});
    }

    private void showHidden(List<long[]> ids, List<String> names) {
        if (hiddenBox == null) return;
        StringBuilder sig = new StringBuilder();
        for (long[] x : ids) sig.append(x[0]).append(',');
        // the same rows as before (back from the TV's "add row" prompt): leave them, so focus stays where it was
        if (sig.toString().equals(shownHidden) && hidden.size() == ids.size()) return;
        shownHidden = sig.toString();
        View had = getCurrentFocus();
        boolean focusHere = had != null && hidden.contains(had);
        hiddenBox.removeAllViews();
        hidden.clear();
        float y = 0;
        for (int i = 0; i < ids.size(); i++) {
            final long ch = ids.get(i)[0];
            final String label = names.get(i);
            SettingsViews.OptionRow r = new SettingsViews.OptionRow(this, "hidden:" + ch, I18n.f("row_hidden", label), "", 1728 - 40, 96);
            r.setTag("hidden:" + ch);
            r.value(I18n.t("row_show"), false, false);
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { requestBrowsable(ch, label); } });
            y += 14;
            hiddenBox.addView(r, AppViews.at(this, 40, y, 1728 - 40, r.height()));
            y += r.height();
            hidden.add(r);
        }
        hiddenBox.setVisibility(hidden.isEmpty() ? View.GONE : View.VISIBLE);
        hiddenBlock.h = y;
        main.relayout();
        relines();
        if (focusHere && rowsRow != null) rowsRow.requestFocus();
        View f = getCurrentFocus();
        if (f != null && stack.isEmpty()) main.reveal(f, true);  // the rows above pushed the focused option down: keep it in view
    }

    /** Settings changed: refresh now (a refresh already running does one more pass with the new settings). */
    private void changed() { changed(null); }

    /** The same, then after on the UI thread (a sport followed: its panel lists what the refresh brought). */
    private void changed(final Runnable after) {
        final Context app = getApplicationContext();
        refresher.execute(new Runnable() { public void run() {  // never blocks row creation or prompts
            Engine.background();
            try { Updater.schedule(app); Engine.refresh(app, false); } catch (Throwable ignored) { }
            if (after != null) ui.post(new Runnable() { public void run() { if (!gone()) after.run(); } });
        }});
    }

    // ---------- Formula 1 and UFC (SportsSettings) ----------
    /** The sport panel's side of the page: the grid, the threads, the hint line, the follow and its row. */
    private SportsSettings.Host sportHost() {
        return new SportsSettings.Host() {
            public FrameLayout grid() { return grid; }
            public Handler ui() { return ui; }
            public ExecutorService lists() { return lists; }
            public ExecutorService images() { return images; }
            public ExecutorService fetches() { return fetches; }
            public boolean gone() { return SettingsActivity.this.gone(); }
            public boolean shows(String sport) { return SportsSettings.chipId(sport).equals(league); }
            public void laidOut(float gridH) {
                if (gridH >= 0) gridBlock.h = gridH;
                main.relayout();
                relines();
            }
            public void hint(String text) { setHint(text); }
            public void followed(String sport, boolean on, Runnable whenRefreshed) {
                settings.edit().putBoolean("onboarded", true).apply();  // a sport followed in the first start ends it too
                recount();
                if (on && HomeRows.enabled(SettingsActivity.this)) addSportRow(sport);
                if (on) followSport(sport, whenRefreshed);  // its row and cards in seconds, not after the clubs' refresh
                SettingsActivity.this.changed(on ? null : whenRefreshed);
                refreshStatus();
                if (!on) checkHiddenRows();
            }
            public void changed() { SettingsActivity.this.changed(); }
            public void skeletons(boolean on) { SettingsActivity.this.skeletons(on); }
            public void focus(View v) { v.requestFocus(); main.reveal(v, true); }
        };
    }
}
