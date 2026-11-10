package app.fanela.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's main screen: one row of match cards per followed club (live or next match, the following fixtures, the
 * latest results), with the channel for each match. Works on every TV, including Google TV, which does not show
 * home-screen rows. While it is on screen it refreshes live scores every 30 s during matches; nothing polls once it
 * is closed.
 *
 * Threads: "net" runs refreshes and live ticks (slow, network); "display" draws and decodes the cards; "detail" draws
 * the large card of the match panel. The screen is rebuilt only when its structure changes (clubs or card slots);
 * a new score or clock just swaps the picture of that card, so focus and scrolling never jump.
 */
public class HomeActivity extends Activity {
    private static final long LIVE_EVERY = 30000L, IDLE_EVERY = 5 * 60000L, BUSY_RETRY = 3000L;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService net, display, detail;
    private LinearLayout sections;
    private TextView status;
    /** The details of the focused card: one place under the header, like the TV's own home screen. */
    private TextView detailHead, detailDesc;
    private View settingsButton;
    private FrameLayout root, overlay;
    private String pendingMatch;  // a match to open once the cards are loaded (from a home-screen tile)
    private String builtLang;
    private String builtTheme;
    private int generation;
    private boolean resumed;
    private volatile boolean refreshing, tried;  // tried: a refresh of ours finished since the screen opened
    private volatile String shownStructure = "";  // what the rows show now (UI thread writes, display thread reads)
    private final Map<String, Card> cards = new java.util.LinkedHashMap<>();  // UI thread only, in screen order
    private final Map<String, String> shownUri = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Bitmap> logos = new HashMap<>();  // display thread only: club badge per logo tag
    private Object backCallback;

    /** A card on screen. Its match can change in place (score, clock) without rebuilding the row. */
    static final class Card {
        FrameLayout view; ImageView img; Bitmap bmp; Cards.Ref ref; Teams.Club club; TextView headline, desc;
    }

    static final class Section { Teams.Club club; List<Cards.Ref> refs; boolean everLoaded; Bitmap logo; }

    private final Runnable cycle = new Runnable() {
        public void run() {
            if (!resumed) return;
            final int gen = generation;
            final Context app = getApplicationContext();
            net.execute(new Runnable() { public void run() {
                Engine.background();
                boolean live = false, busy = false;
                try { AppUpdate.check(app); } catch (Throwable ignored) { }
                refreshing = true;
                try {
                    live = Engine.live(app);
                    if (live) { Engine.tick(app); loadLater(); }  // scores first
                    busy = Engine.busy();  // a refresh started elsewhere (Settings, the job): ask again in a moment
                    if (!busy) Engine.refresh(app, false);  // cheap when nothing is due: clubs have their own tiers
                } catch (Throwable ignored) {
                } finally {
                    refreshing = false;
                    if (!busy) tried = true;
                }
                loadLater();
                final long next = busy ? BUSY_RETRY : live ? LIVE_EVERY : IDLE_EVERY;
                ui.post(new Runnable() { public void run() {
                    if (!resumed || gen != generation) return;
                    ui.removeCallbacks(cycle);
                    ui.postDelayed(cycle, next);
                }});
            }});
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        I18n.load(this);
        Ui.theme(this);
        net = Executors.newSingleThreadExecutor();
        display = Executors.newSingleThreadExecutor();
        detail = Executors.newSingleThreadExecutor();
        pendingMatch = getIntent().getStringExtra("match");
        build();
        if (!Teams.chosen(this) && Teams.followed(this).isEmpty()) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra("setup", true));
        }
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
        Net.reset();  // the user is here now: retry every source at once
        I18n.load(this);
        Ui.theme(this);
        if (!I18n.lang().equals(builtLang) || !Ui.key().equals(builtTheme)) build();  // language or favourite club changed in Settings
        else updateHeader();
        loadLater();  // saved data at once
        ui.removeCallbacks(cycle);
        ui.postDelayed(cycle, 300);  // then a refresh in the background
    }

    @Override protected void onPause() {
        resumed = false;
        generation++;
        ui.removeCallbacks(cycle);
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        net.shutdownNow();
        display.shutdownNow();
        detail.shutdownNow();
        for (Card c : cards.values()) if (c.bmp != null) { c.img.setImageDrawable(null); c.bmp.recycle(); }
        cards.clear();
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && overlay != null) { closeDetail(); return true; }  // Android 8-12
        if (code == KeyEvent.KEYCODE_MENU) { openSettings(); return true; }
        return super.onKeyDown(code, e);
    }

    private boolean gone() { return isFinishing() || isDestroyed(); }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------- layout ----------
    private void build() {
        for (Card c : cards.values()) if (c.bmp != null) { c.img.setImageDrawable(null); c.bmp.recycle(); }
        cards.clear();
        shownUri.clear();
        shownStructure = "";
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(40));
        scroll.setSmoothScrollingEnabled(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(dp(Ui.SIDE), 0, dp(Ui.SIDE), dp(Ui.TOP));
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // the header and the details of the focused card stay put; the club rows scroll under them
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setClipChildren(false);  // the focused Settings button grows: never cut it
        top.setClipToPadding(false);
        top.setPadding(dp(Ui.SIDE), dp(Ui.TOP), dp(Ui.SIDE), 0);
        column.addView(top);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(Logo.wordmark(this));  // the shirt in the favourite club's colours
        logo.setAdjustViewBounds(true);
        logo.setContentDescription(getString(R.string.app_name));
        head.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
        status = Ui.text(this, "", 14, Ui.SUB, false);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sp.setMargins(dp(24), 0, dp(16), 0);
        head.addView(status, sp);
        TextView set = Ui.button(this, I18n.t("settings"));
        set.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        settingsButton = set;
        head.addView(set);
        top.addView(head);
        detailHead = Ui.text(this, "", 18, Ui.TEXT, true);
        detailHead.setSingleLine(true);
        detailHead.setEllipsize(TextUtils.TruncateAt.END);
        detailDesc = Ui.text(this, "", 14, Ui.SUB, false);
        detailDesc.setSingleLine(true);
        detailDesc.setEllipsize(TextUtils.TruncateAt.END);
        detailDesc.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(detailHead);
        details.addView(detailDesc);
        details.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, dp(14), 0, 0);
        top.addView(details, dlp);

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        sections.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        page.addView(sections, lp);
        setContentView(root);
        overlay = null;
        builtLang = I18n.lang();
        builtTheme = Ui.key();
        updateHeader();
    }

    /** How soon a match starts: "in 40 minutes", "in 3 hours", "in 2 days"; empty when it is past or a day off (the date says it). */
    static String until(long start) {
        long ms = start - System.currentTimeMillis();
        if (ms <= 0) return "";
        long min = ms / 60000;
        if (min < 2) return I18n.t("in_minute");
        if (min < 90) return I18n.f("in_minutes", (int) min);
        if (min < 12 * 60) return I18n.f("in_hours", Math.round(min / 60f));  // 90 minutes and up: 2 hours or more
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        b.setTimeInMillis(start);
        int days = (int) Math.round((I18n.startOfDay(b) - I18n.startOfDay(a)) / 86400000.0);
        return days >= 2 ? I18n.f("in_days", days) : "";  // today and tomorrow are already in the date
    }

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    /** The app's page on Google Play (the TV's store), or its web page when there's no store app. */
    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception ignored) {
                android.widget.Toast.makeText(this, I18n.f("app_missing", Cards.appName(pkg)), android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateHeader() {
        if (status == null) return;
        long last = 0;
        boolean stale = false;
        for (String id : Teams.followed(this)) {
            last = Math.max(last, Store.lastUpdate(this, id, "FC"));
            stale |= Store.stale(this, id, "FC");
        }
        String s;
        if (Teams.followed(this).isEmpty()) s = "";
        else if (last == 0 && !tried) s = I18n.t("updating");
        else if (last == 0) s = I18n.t("no_connection");
        else if (stale) s = I18n.t("offline");
        else s = I18n.f("updated", I18n.day(last).toLowerCase(I18n.locale()) + " " + I18n.time(last));
        if (AppUpdate.available(this) != null) s = (s.isEmpty() ? "" : s + " · ") + I18n.t("update_available");
        if (!s.contentEquals(status.getText())) status.setText(s);
    }

    // ---------- data ----------
    private void loadLater() {
        try { display.execute(new Runnable() { public void run() { Engine.background(); load(); } }); }
        catch (Exception ignored) { }  // shutting down
    }

    /** Draws what changed for every followed club and shows it. Display thread. */
    private void load() {
        final Context app = getApplicationContext();
        I18n.load(app);
        List<Match> all = Store.load(app);
        Engine.timeStates(all, System.currentTimeMillis());
        final List<Section> out = new ArrayList<>();
        boolean waiting = !tried || refreshing;
        StringBuilder structure = new StringBuilder(I18n.lang()).append(Ui.ACCENT);
        for (String id : Teams.followed(app)) {
            Teams.Club k = Teams.get(app, id);
            if (k == null) continue;
            Section s = new Section();
            s.club = k;
            s.refs = Cards.forApp(app, k, all);
            s.everLoaded = Store.lastUpdate(app, id, "FC") > 0;
            String tag = Cards.rowLogoLazy(app, k).tag();
            s.logo = logos.get(id + tag);
            if (s.logo == null) { s.logo = Cards.rowLogo(app, k); logos.put(id + tag, s.logo); }
            out.add(s);
            structure.append('|').append(id).append(tag);
            if (s.refs.isEmpty()) structure.append(':').append(s.everLoaded ? "none" : waiting ? "wait" : "fail");
            for (Cards.Ref r : s.refs) structure.append(',').append(r.name);
        }
        final String newStructure = structure.toString();
        final boolean rebuild = !newStructure.equals(shownStructure);
        // decode only what is new: every card on a rebuild, otherwise the cards whose picture changed
        final Map<String, Bitmap> decoded = new HashMap<>();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;  // the cards are opaque (saved without alpha): half the memory
        for (Section s : out) for (Cards.Ref r : s.refs)
            if (rebuild || !r.uri.equals(shownUri.get(r.name)))
                decoded.put(r.name, BitmapFactory.decodeFile(new File(app.getFilesDir(), r.name + ".png").getPath(), o));
        ui.post(new Runnable() { public void run() {
            if (gone()) { recycleAll(decoded); return; }
            updateHeader();
            if (rebuild || !newStructure.equals(shownStructure)) {
                if (!rebuild) { recycleAll(decoded); loadLater(); return; }  // the screen changed meanwhile: start over
                show(out, decoded);
                shownStructure = newStructure;
            } else {
                update(out, decoded);
            }
            openPending();
        }});
    }

    private static void recycleAll(Map<String, Bitmap> m) { for (Bitmap b : m.values()) if (b != null) b.recycle(); }

    /** Same cards as before: swap the pictures that changed and refresh the texts. No focus or scroll change. */
    private void update(List<Section> list, Map<String, Bitmap> decoded) {
        boolean focusedCard = false;
        for (Section s : list) for (Cards.Ref r : s.refs) {
            Card c = cards.get(r.name);
            Bitmap b = decoded.remove(r.name);
            if (c == null) { if (b != null) b.recycle(); continue; }
            c.ref = r;
            c.club = s.club;
            if (b != null) {
                Bitmap old = c.bmp;
                c.img.setImageBitmap(b);
                c.bmp = b;
                if (old != null) old.recycle();
            }
            shownUri.put(r.name, r.uri);
            c.view.setContentDescription(Cards.headline(r) + ". " + Cards.describe(this, s.club, r));
            if (c.view.isFocused()) { describe(c); focusedCard = true; }
        }
        // no card has focus (Settings has): the details keep describing the first card, with its latest score
        if (!focusedCard && !list.isEmpty() && !list.get(0).refs.isEmpty()) describe(cards.get(list.get(0).refs.get(0).name));
        recycleAll(decoded);
    }

    private void describe(Card c) {
        if (c == null) return;
        c.headline.setText(Cards.headline(c.ref));
        c.desc.setText(Cards.describe(this, c.club, c.ref));
    }

    // ---------- rows of cards ----------
    private void show(List<Section> list, Map<String, Bitmap> decoded) {
        // where the user was: the card, its match, its club's row and position, and every row's scroll
        View f = getCurrentFocus();
        Card was = f != null && f.getTag() instanceof String ? cards.get(f.getTag()) : null;
        String wasClub = was == null ? null : was.club.id, wasMatch = was == null ? null : was.ref.m.key;
        int wasIndex = was == null ? 0 : ((ViewGroup) was.view.getParent()).indexOfChild(was.view);
        boolean hadCards = !cards.isEmpty();
        Map<String, Integer> scrollX = new HashMap<>();
        for (int i = 0; i < sections.getChildCount(); i++)
            if (sections.getChildAt(i) instanceof HorizontalScrollView && sections.getChildAt(i).getTag() != null)
                scrollX.put((String) sections.getChildAt(i).getTag(), sections.getChildAt(i).getScrollX());

        sections.removeAllViews();
        for (Card c : cards.values()) if (c.bmp != null) { c.img.setImageDrawable(null); c.bmp.recycle(); }
        cards.clear();
        shownUri.clear();
        View first = null, byTag = null, byMatch = null, byClub = null;
        if (list.isEmpty()) sections.addView(emptyState());
        for (final Section s : list) {
            LinearLayout title = new LinearLayout(this);
            title.setOrientation(LinearLayout.HORIZONTAL);
            title.setGravity(Gravity.CENTER_VERTICAL);
            ImageView badge = new ImageView(this);
            badge.setImageBitmap(s.logo);
            title.addView(badge, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView name = Ui.text(this, s.club.label(), 20, Ui.TEXT, true);
            name.setPadding(dp(12), 0, 0, 0);
            title.addView(name);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(0, dp(12), 0, dp(6));
            sections.addView(title, tp);

            if (s.refs.isEmpty()) {
                boolean waiting = !tried || refreshing;
                TextView none = Ui.text(this, I18n.t(s.everLoaded ? "no_fixtures" : waiting ? "loading_club" : "load_failed"), 15, Ui.SUB, false);
                none.setPadding(0, dp(4), 0, dp(8));
                sections.addView(none);
                continue;
            }
            final TextView headline = detailHead, desc = detailDesc;
            final HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setTag(s.club.id);
            hs.setHorizontalScrollBarEnabled(false);  // the row clips its own cards, so the fading edge has no seam
            hs.setHorizontalFadingEdgeEnabled(false);  // like the TV's own home screen: rows run off the right edge
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(dp(18), dp(9), dp(Ui.SIDE), dp(9));  // room for the focused card to grow (1.1x); the last card stops inside the safe area
            hs.addView(row);
            List<View> rowCards = new ArrayList<>();
            for (final Cards.Ref r : s.refs) {
                Card c = card(decoded.remove(r.name), r, s.club, headline, desc);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(240), dp(135));
                cp.setMargins(0, 0, dp(18), 0);
                row.addView(c.view, cp);
                rowCards.add(c.view);
                if (first == null) first = c.view;
                if (r.name.equals(f != null ? f.getTag() : null)) byTag = c.view;
                if (r.m.key.equals(wasMatch) && byMatch == null) byMatch = c.view;
            }
            // left / right stop at the ends of a row instead of jumping to another row or to Settings
            View head = rowCards.get(0), tail = rowCards.get(rowCards.size() - 1);
            head.setNextFocusLeftId(head.getId());
            tail.setNextFocusRightId(tail.getId());
            if (s.club.id.equals(wasClub)) byClub = rowCards.get(Math.min(wasIndex, rowCards.size() - 1));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.setMargins(-dp(18), -dp(4), -dp(Ui.SIDE), 0);  // cards line up with the club name and run to the screen edge
            sections.addView(hs, hp);
            final Integer sx = scrollX.get(s.club.id);
            if (sx != null) hs.post(new Runnable() { public void run() { hs.scrollTo(sx, 0); } });
        }
        // until a card has focus, the details show the first club's next match
        if (!list.isEmpty() && !list.get(0).refs.isEmpty()) describe(cards.get(list.get(0).refs.get(0).name));
        else if (detailHead != null) { detailHead.setText(""); detailDesc.setText(""); }
        recycleAll(decoded);
        View target = byTag != null ? byTag : byMatch != null ? byMatch : byClub;
        if (overlay != null) { /* the match panel keeps focus */ }
        else if (target != null) target.requestFocus();
        else if (first != null && (was != null || f == null || !hadCards)) first.requestFocus();  // first open: start on the matches
        if (overlay == null && getCurrentFocus() == null && settingsButton != null) settingsButton.requestFocus();  // never leave the remote without focus
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(60), 0, 0);
        box.addView(Ui.text(this, I18n.t("empty_title"), 28, Ui.TEXT, true));
        TextView t = Ui.text(this, I18n.t("empty_text"), 17, Ui.SUB, false);
        t.setPadding(0, dp(10), 0, dp(24));
        t.setMaxWidth(dp(620));
        box.addView(t);
        TextView b = Ui.button(this, I18n.t("choose_clubs"));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(HomeActivity.this, SettingsActivity.class).putExtra("setup", true));
        }});
        box.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.requestFocus();
        return box;
    }

    private Card card(Bitmap bmp, final Cards.Ref r, final Teams.Club club, final TextView headline, final TextView desc) {
        final Card c = new Card();
        final FrameLayout f = new FrameLayout(this);
        c.view = f; c.ref = r; c.club = club; c.headline = headline; c.desc = desc; c.bmp = bmp;
        f.setId(View.generateViewId());
        f.setTag(r.name);
        f.setFocusable(true);
        f.setFocusableInTouchMode(true);
        f.setClickable(true);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10)); }
        });
        f.setBackgroundColor(Ui.SURFACE);
        c.img = new ImageView(this);
        c.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (bmp != null) c.img.setImageBitmap(bmp);
        f.addView(c.img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), Color.WHITE);
        f.setContentDescription(Cards.headline(r) + ". " + Cards.describe(this, club, r));
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(140).start();
                v.setElevation(has ? dp(10) : 0);
                f.setForeground(has ? ring : null);
                if (has) describe(c);
            }
        });
        f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showDetail(c.ref, c.club, r.name); } });
        cards.put(r.name, c);
        shownUri.put(r.name, r.uri);
        return c;
    }

    /** Opens the match a home-screen tile asked for, once its card is on screen. */
    private boolean openPending() {
        if (pendingMatch == null) return false;
        for (Card c : cards.values()) if (pendingMatch.equals(c.ref.m.key)) {
            pendingMatch = null;
            showDetail(c.ref, c.club, c.ref.name);
            return true;
        }
        if (!shownStructure.isEmpty()) pendingMatch = null;  // the match is no longer listed
        return false;
    }

    // ---------- match details ----------
    private void showDetail(final Cards.Ref r, final Teams.Club club, String cardName) {
        closeDetail();
        final Match m = r.m;
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.rgb(14, 15, 19));  // the rows behind never show through the text
        overlay.setClickable(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setClipChildren(false);
        panel.setPadding(dp(Ui.SIDE + 12), dp(Ui.TOP), dp(Ui.SIDE + 12), dp(Ui.TOP));
        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setClipToOutline(true);
        img.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
        });
        Card shown = cards.get(cardName);
        if (shown != null && shown.bmp != null && !shown.bmp.isRecycled()) {  // a copy of the thumbnail until the sharp one is drawn
            Bitmap thumb = shown.bmp.copy(shown.bmp.getConfig(), false);
            img.setImageBitmap(thumb);
            img.setTag(thumb);
        }
        panel.addView(img, new LinearLayout.LayoutParams(dp(480), dp(270)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(36), 0, 0, 0);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        StringBuilder spoken = new StringBuilder();
        TextView h = Ui.text(this, Cards.headline(r), 24, Ui.TEXT, true);
        col.addView(h);
        spoken.append(h.getText());
        TextView comp = Ui.text(this, I18n.comp(m.comp) + " · " + (m.team.equals("BC") ? I18n.t("src_basket") : I18n.t("src_espn")), 16, Ui.SUB, false);
        comp.setPadding(0, dp(6), 0, 0);
        col.addView(comp);
        spoken.append(". ").append(comp.getText());
        String soon = !r.last && "pre".equals(m.state) && !m.tbd ? until(m.start) : "";
        TextView when = Ui.text(this, I18n.longDate(m.start) + (m.tbd ? " · " + I18n.t("time_tba") : " · " + I18n.time(m.start)), 18, Ui.TEXT, false);
        when.setPadding(0, dp(14), 0, 0);
        col.addView(when);
        spoken.append(". ").append(when.getText());
        if (!soon.isEmpty()) {  // how soon, on its own line: "in 5 days"
            TextView inTime = Ui.text(this, soon, 16, Ui.SUB, false);
            inTime.setPadding(0, dp(2), 0, 0);
            col.addView(inTime);
            spoken.append(", ").append(soon);
        }
        boolean upcoming = !r.last && !"off".equals(m.state);
        if (upcoming) {
            TextView where = Ui.text(this, Cards.where(m, true), 18, m.channelSure ? Ui.TEXT : Ui.SUB, false);
            where.setPadding(0, dp(8), 0, 0);
            col.addView(where);
            spoken.append(". ").append(where.getText());
            if (m.channelKnown && !m.channelSure) {
                TextView note = Ui.text(this, I18n.t("channel_note"), 14, Ui.SUB, false);
                note.setPadding(0, dp(6), 0, 0);
                col.addView(note);
                spoken.append(". ").append(note.getText());
            }
        } else if ("off".equals(m.state)) {
            TextView st = Ui.text(this, I18n.detail(m.detail), 18, Ui.AMBER, true);
            st.setPadding(0, dp(8), 0, 0);
            col.addView(st);
            spoken.append(". ").append(st.getText());
        }
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(28), 0, dp(4));
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        View firstButton = null;
        if (upcoming && m.channelKnown && m.channelApp != null) {
            if (HomeRows.installed(this, m.channelApp)) {
                TextView open = Ui.button(this, I18n.f("open_app", Cards.appName(m.channelApp)));
                open.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { OpenActivity.launch(HomeActivity.this, m.channelApp); } });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.setMargins(0, 0, dp(14), 0);
                buttons.addView(open, bp);
                firstButton = open;
            } else {  // the channel's app isn't on this TV: offer to get it, instead of a dead end
                TextView get = Ui.button(this, I18n.f("get_app", Cards.appName(m.channelApp)));
                get.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openStore(m.channelApp); } });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.setMargins(0, 0, dp(14), 0);
                buttons.addView(get, bp);
                firstButton = get;
                spoken.append(". ").append(I18n.f("app_missing", Cards.appName(m.channelApp)));
            }
        }
        TextView close = Ui.button(this, I18n.t("close"));
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeDetail(); } });
        buttons.addView(close);
        if (firstButton == null) firstButton = close;
        col.addView(buttons);
        panel.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        overlay.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setTag(cardName);  // the card to go back to (by name: the row may be rebuilt meanwhile)
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // keep D-pad focus inside the panel
        sections.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (settingsButton != null) settingsButton.setFocusable(false);
        firstButton.requestFocus();
        overlay.announceForAccessibility(spoken);
        backCallback = Ui.onBack(this, backCallback, new Runnable() { public void run() { closeDetail(); } });
        final Context app = getApplicationContext();
        final FrameLayout mine = overlay;
        detail.execute(new Runnable() { public void run() {  // full-size card, sharper than the row thumbnail
            I18n.load(app);
            final Bitmap big = Cards.draw(app, m, r.stale);
            ui.post(new Runnable() { public void run() {
                if (gone() || overlay != mine) { big.recycle(); return; }
                Bitmap thumb = img.getTag() instanceof Bitmap ? (Bitmap) img.getTag() : null;
                img.setImageBitmap(big);
                img.setTag(big);
                if (thumb != null) thumb.recycle();
            }});
        }});
    }

    private void closeDetail() {
        if (overlay == null) return;
        String back = (String) overlay.getTag();
        ImageView img = findBig(overlay);
        sections.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (settingsButton != null) settingsButton.setFocusable(true);
        root.removeView(overlay);
        overlay = null;
        backCallback = Ui.onBack(this, backCallback, null);
        if (img != null) { Bitmap b = (Bitmap) img.getTag(); img.setImageDrawable(null); b.recycle(); }
        Card c = back == null ? null : cards.get(back);
        if (c != null) c.view.requestFocus();
        else if (!cards.isEmpty()) {
            View first = sections.findViewWithTag(cards.keySet().iterator().next());
            if (first != null) first.requestFocus();
        }
        if (getCurrentFocus() == null && settingsButton != null) settingsButton.requestFocus();
    }

    private static ImageView findBig(View v) {
        if (v instanceof ImageView && v.getTag() instanceof Bitmap) return (ImageView) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            ImageView x = findBig(((ViewGroup) v).getChildAt(i));
            if (x != null) return x;
        }
        return null;
    }
}
