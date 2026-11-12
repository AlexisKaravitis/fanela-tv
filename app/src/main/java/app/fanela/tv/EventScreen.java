package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
 * The event screen (SPEC-F1-UFC §7, §8; F1-UFC-ARCHITECTURE §7), the counterpart of MatchScreen, one level under the
 * home screen in either layout. An F1 weekend: the circuit, the place and its facts, the grand prix, the hero's
 * session in the when column, the session list in the viewer's zone (each with its channel), and a right panel that
 * follows the focused session: the drivers' standings (Right: the constructors), the live order (only while
 * F1Feed.LIVE_ORDER_TRUSTED) or the classification. A UFC event: the main event's faces and billing, its time or
 * result, and the card in three segments (main card, prelims, early prelims) of bout rows centred on VS.
 *
 * prepare() resolves everything (faces, flags, channels, standings) on a background thread; the views only draw it.
 * While the screen is open, new data is prepared the same way and swapped in place (update): the focus stays on the
 * same session or bout. OK on a session or a bout opens its channel's app (or its store page); Back closes.
 */
final class EventScreen {
    private EventScreen() { }

    /** What the screen shows, resolved off the UI thread. */
    static final class Data {
        SportEvent e;
        /** The session (F1) or segment (UFC) the hero is about. */
        String heroSession;
        /** The session or bout to focus first (null: the launch rule). */
        String focus;
        HomeModel.Kind kind = HomeModel.Kind.NEXT;
        long now;
        boolean hidden;
        SportPrefs prefs = SportPrefs.none();
        HomeModel.Status status = HomeModel.Status.CLOCK;
        String statusText = "";
        Bitmap env, flag, headA, headB;
        float[] outline;
        SportViews.When when;
        final Map<String, AppKit.Watch> watch = new HashMap<>();
        /** A session's channel app: installed (true) or only known (false); absent: none. */
        final Map<String, Boolean> apps = new HashMap<>();
        final Map<String, Bitmap> flags = new HashMap<>();
        F1Feed.Standings standings;
        String trackTime = "";

        boolean f1() { return SportEvent.F1.equals(e.sport); }
        /** Everything that changes the rows (a change rebuilds them; the same keys update in place). */
        String structure() {
            StringBuilder b = new StringBuilder(e.key());
            for (SportEvent.Session s : e.sessions) {
                b.append('|').append(s.id);
                if (!f1()) for (SportEvent.Bout x : s.bouts) b.append(',').append(x.id);
            }
            return b.toString();
        }
    }

    /** Background thread: the screen for an event (sessionId: the session the hero and the focus are about; null: the launch rule). */
    static Data prepare(Context c, SportEvent e, String sessionId, HomeModel h, boolean hidden, SportPrefs prefs) {
        Ui.fonts(c);
        Data d = new Data();
        d.e = e;
        d.now = h != null ? h.now : System.currentTimeMillis();
        if (h != null) { d.status = h.status; d.statusText = h.statusText; }
        d.hidden = hidden;
        d.prefs = prefs == null ? SportPrefs.none() : prefs;
        boolean f1 = d.f1();
        d.outline = SportViews.outline(c, e);
        d.env = SportViews.field(c, e.sport, d.outline, 760);
        SportEvent.Session hs = e.session(sessionId);
        if (f1) {
            if (hs == null) hs = SportModel.launchSession(e, d.now);
            d.flag = SportViews.image(c, SportPanel.flagUrl(e), true);
            d.trackTime = "";
            try { if (!hidden) d.standings = F1Feed.table(c); } catch (Throwable ignored) { }
            d.focus = sessionId != null && e.session(sessionId) != null ? sessionId : null;
        } else {
            SportEvent.Session main = SportModel.mainSession(e);
            if (main != null) hs = e.session(SportModel.segmentOf(e, main, d.prefs));
            d.focus = sessionId != null && e.session(sessionId) != null && !"main".equals(sessionId) ? sessionId : null;
        }
        d.heroSession = hs == null ? null : hs.id;
        d.kind = heroKind(e, hs);
        d.when = SportViews.when(e, hs, d.kind, d.now, hidden, d.prefs, false);
        if (hs != null && (d.kind == HomeModel.Kind.NEXT || d.kind == HomeModel.Kind.LIVE)) d.when.watch = SportViews.watch(c, e, hs);
        if (hs != null && d.kind == HomeModel.Kind.LIVE) {
            String app = SportViews.installedApp(c, hs);
            if (app != null) d.when.okHint = I18n.f("open_app", SportWhere.appName(app));
        }
        for (SportEvent.Session s : e.sessions) {
            d.watch.put(s.id, SportViews.watch(c, e, s));
            if (s.watch != null && s.watch.known && s.watch.app != null) d.apps.put(s.id, HomeRows.installed(c, s.watch.app));
        }
        if (!f1) {
            SportEvent.Bout b = SportText.headline(e, hs, d.prefs);
            if (b != null) {
                d.headA = b.a == null ? null : SportViews.image(c, b.a.headshot, true);
                d.headB = b.b == null ? null : SportViews.image(c, b.b.headshot, true);
                if (d.headA == null || d.headB == null) { d.headA = null; d.headB = null; }
            }
            for (SportEvent.Session s : e.sessions) for (SportEvent.Bout x : s.bouts)
                for (SportEvent.Fighter f : new SportEvent.Fighter[]{x.a, x.b})
                    if (f != null && !f.flag.isEmpty() && !d.flags.containsKey(f.flag)) d.flags.put(f.flag, SportViews.image(c, f.flag, true));
        }
        AppArt.shirt(c, Ui.TXT);
        return d;
    }

    /** The hero's state: F1 by its session; UFC by the event (live while any segment is, a result once the card is done). */
    static HomeModel.Kind heroKind(SportEvent e, SportEvent.Session s) {
        if (SportEvent.UFC.equals(e.sport)) {
            if ("in".equals(e.state) || e.live() != null) return HomeModel.Kind.LIVE;
            if ("post".equals(e.state)) return HomeModel.Kind.RESULT;
            if ("off".equals(e.state) || s != null && "off".equals(s.state)) return HomeModel.Kind.OFF;
            return HomeModel.Kind.NEXT;
        }
        return SportModel.kind(SportModel.pick(e, s == null ? null : s.id, false));
    }

    /** What OK on a row asks of the host. */
    interface Actions {
        void open(String pkg);
        void get(String pkg);
        /** Long-press OK on a bout row, then OK (SPEC-F1-UFC §10): follow or unfollow a fighter, as Settings does. */
        void follow(SportEvent.Fighter f, boolean on);
    }

    /** The screen's views: the drawn body, and the focusable rows (F1 sessions; UFC bouts in a scrolling list). */
    static FrameLayout build(Context c, Data d, Actions actions) {
        Screen f = new Screen(c, actions);
        f.setBackgroundColor(Ui.APP_BG);
        f.setClickable(true);
        f.body = new Body(c, f);
        f.addView(f.body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.focusLayer = new AppViews.FocusLayer(c);
        f.addView(f.focusLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.notice = new Notice(c);
        f.addView(f.notice, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.show(d);
        return f;
    }

    /** New data for an open screen: drawn in place, the focus stays on the same row. */
    static void update(FrameLayout screen, Data d) {
        if (screen instanceof Screen) ((Screen) screen).show(d);
    }

    /** The row to focus when the screen opens. */
    static View launchFocus(FrameLayout screen) {
        return screen instanceof Screen ? ((Screen) screen).launch() : null;
    }

    /** The focused row's session or bout id (kept when the system ends the app), or null. */
    static String focused(FrameLayout screen) {
        return screen instanceof Screen ? ((Screen) screen).focusedId() : null;
    }

    /** F1: the right panel's page (0 drivers, 1 constructors), kept when the system ends the app. */
    static int page(FrameLayout screen) {
        return screen instanceof Screen ? ((Screen) screen).page : 0;
    }

    /** After the system ended the app: the focused row, then the right panel's page (the focus resets it to 0). */
    static void restore(FrameLayout screen, String focus, int page) {
        if (!(screen instanceof Screen)) return;
        Screen s = (Screen) screen;
        s.focus(focus);
        if (page == 1 && s.d.f1() && "STANDINGS".equals(s.panelKind()) && s.d.standings != null && !s.d.standings.teams.isEmpty()) {
            s.page = 1;
            s.body.invalidate();
        }
    }

    static Data data(FrameLayout screen) { return screen instanceof Screen ? ((Screen) screen).d : null; }

    /** Back while a fighter is being chosen (long-press OK on a bout row): the choice is dropped. True when it was. */
    static boolean back(FrameLayout screen) {
        if (!(screen instanceof Screen) || ((Screen) screen).picking == null) return false;
        ((Screen) screen).endPick(true);
        return true;
    }

    // ---------- the screen ----------
    static final class Screen extends FrameLayout {
        Data d;
        Body body;
        AppViews.FocusLayer focusLayer;
        final Actions actions;
        /** F1: the session the right panel follows, and its page (0 drivers, 1 constructors). */
        String panelSession;
        int page;
        final List<SessionRow> sessions = new ArrayList<>();
        final List<BoutRow> bouts = new ArrayList<>();
        BoutScroll scroll;
        LinearLayout list;
        View fade;
        /** UFC: the scrolled list's segment header, pinned under the rule (QAS-9). */
        PinnedHeader pinned;
        /** The line at the foot: the fighter choice, then its confirmation. */
        Notice notice;
        /** The bout row whose fighter is being chosen (long-press OK), or null. */
        BoutRow picking;
        /** The prefs as followed on this screen, until a new model carries them (show()). */
        SportPrefs followedHere;
        private String structure = "";

        Screen(Context c, Actions actions) { super(c); this.actions = actions; }

        void show(Data nd) {
            if (followedHere != null) {  // a follow made here a moment ago: kept until the model has it too
                if (nd.prefs.fighters.equals(followedHere.fighters)) followedHere = null;
                else nd.prefs = followedHere;
            }
            String was = focusedId();
            boolean had = findFocus() != null;
            d = nd;
            if (!nd.structure().equals(structure)) {
                structure = nd.structure();
                rebuild();
                if (had && was != null) focus(was);
            }
            bindRows();
            body.invalidate();
            setContentDescription(describe(nd));
        }

        private void rebuild() {
            Context c = getContext();
            endPick(true);
            for (SessionRow r : sessions) removeView(r);
            sessions.clear();
            if (scroll != null) { removeView(scroll); scroll = null; }
            if (pinned != null) { removeView(pinned); pinned = null; }
            if (fade != null) { removeView(fade); fade = null; }
            bouts.clear();
            if (d.f1()) {
                List<SportEvent.Session> ss = d.e.byStart();
                float rowH = ss.isEmpty() ? 70 : Math.min(70, (1064 - 680) / (float) ss.size());
                for (int i = 0; i < ss.size(); i++) {
                    final SessionRow r = new SessionRow(c, this, ss.get(i).id, rowH);
                    r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ok(r.id); } });
                    r.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                        r.invalidate();  // the OK hint is the focused row's
                        if (has && !r.id.equals(panelSession)) { panelSession = r.id; page = 0; body.invalidate(); }
                    }});
                    r.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View v, int code, KeyEvent e) { return panelKey(code, e); } });
                    sessions.add(r);
                    addRow(r, AppViews.at(c, 84, 680 + i * rowH, 928, rowH));
                }
                return;
            }
            scroll = new BoutScroll(c, this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setFocusable(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setVerticalFadingEdgeEnabled(true);  // the foot only (BoutScroll): a focused row stays a row clear of it
            scroll.setFadingEdgeLength(Ui.px(c, 80));
            list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, 0, 0, Ui.px(c, 140));
            scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (SportEvent.Session s : SportModel.segments(d.e)) {
                SegmentHeader h = new SegmentHeader(c, this, s.id);
                list.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 56)));
                boolean first = true;
                for (SportEvent.Bout b : SportModel.bouts(s)) {
                    final BoutRow r = new BoutRow(c, this, s.id, b.id, first);
                    first = false;
                    r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (picking == r) confirmPick(); else ok(r.segment); } });
                    r.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { return startPick(r); } });
                    r.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View v, int code, KeyEvent e) { return pickKey(r, code, e); } });
                    bouts.add(r);
                    list.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 64)));
                }
            }
            addRow(scroll, AppViews.at(c, 0, 600, Ui.W, Ui.H - 600));
            pinned = new PinnedHeader(c, this);
            pinned.setVisibility(View.GONE);
            addRow(pinned, AppViews.at(c, 0, 600, Ui.W, BoutScroll.PIN));
            fade = new ClubLayout.PeekFade(c);
            addRow(fade, AppViews.at(c, 0, 1000, Ui.W, Ui.H - 1000));
        }

        /** Rows go under the focus ring's layer. */
        private void addRow(View v, FrameLayout.LayoutParams lp) {
            int at = focusLayer == null ? -1 : indexOfChild(focusLayer);
            addView(v, at, lp);
        }

        private void bindRows() {
            for (SessionRow r : sessions) r.invalidate();
            for (BoutRow r : bouts) r.bind();
            if (list != null) for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate();
            if (pinned != null) pinned.invalidate();
        }

        // ---------- following a fighter from his bout row (SPEC-F1-UFC §10) ----------
        /**
         * Long-press OK on a bout row: choose one of its two fighters (Left / Right, the side he stands on), OK follows or
         * unfollows him, Back or leaving the row drops the choice. Starts on a followed fighter (the likely unfollow),
         * else on the first-billed. False when the bout has no fighter with an id.
         */
        boolean startPick(BoutRow r) {
            SportEvent.Bout b = r.find();
            if (b == null || !has(b.a) && !has(b.b)) return false;
            if (picking != null && picking != r) picking.pick(0);
            picking = r;
            int side = has(b.a) ? 1 : 2;
            if (has(b.b) && d.prefs.followsFighter(b.b.id) && !(has(b.a) && d.prefs.followsFighter(b.a.id))) side = 2;
            r.pick(side);
            pickLine();
            return true;
        }

        private static boolean has(SportEvent.Fighter f) { return f != null && f.id != null && !f.id.isEmpty(); }

        /** Left / Right move the choice; Back drops it (Android 8-12 deliver it here; 13+ through back()). */
        private boolean pickKey(BoutRow r, int code, KeyEvent e) {
            if (picking != r) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    SportEvent.Bout b = r.find();
                    int side = code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : 2;
                    if (b != null && has(side == 1 ? b.a : b.b) && side != r.pick) { r.pick(side); pickLine(); }
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) endPick(true);
                return true;
            }
            return false;
        }

        /** The chosen fighter, or null. */
        private SportEvent.Fighter picked() {
            if (picking == null) return null;
            SportEvent.Bout b = picking.find();
            return b == null ? null : picking.pick == 1 ? b.a : picking.pick == 2 ? b.b : null;
        }

        /** The foot line while choosing: "[OK] Follow Kevin Holland", and how to choose the other one. */
        private void pickLine() {
            SportEvent.Fighter f = picked();
            if (f == null) { endPick(true); return; }
            boolean on = d.prefs.followsFighter(f.id);
            String what = SportText.f(on ? "unfollow_x" : "follow_x", SportText.name(f));
            notice.show(what, SportText.t("pick_other"), 0);
            picking.setContentDescription(what + ". " + SportText.t("pick_other"));
            picking.announceForAccessibility(picking.getContentDescription());
        }

        /** OK on the chosen fighter: followed or unfollowed at once (the host saves it), and a short line says so. */
        void confirmPick() {
            SportEvent.Fighter f = picked();
            if (f == null) { endPick(true); return; }
            boolean on = !d.prefs.followsFighter(f.id);
            java.util.Set<String> fs = new java.util.LinkedHashSet<>(d.prefs.fighters);
            if (on) fs.add(f.id); else fs.remove(f.id);
            d.prefs = followedHere = new SportPrefs(d.prefs.focus, d.prefs.scope, fs);
            actions.follow(f, on);
            endPick(false);
            String said = SportText.f(on ? "following_x" : "unfollowed_x", SportText.name(f));
            notice.show(said, "", 3500);
            announceForAccessibility(said);
            bindRows();
        }

        /** Drops the choice; the foot line goes too when hide. */
        void endPick(boolean hide) {
            BoutRow r = picking;
            picking = null;
            if (r != null) { r.pick(0); r.bind(); }
            if (hide && notice != null) notice.hide();
        }

        /** Right pages the standings to the constructors, Left back to the drivers (only while the panel shows them). */
        private boolean panelKey(int code, KeyEvent e) {
            if (code != KeyEvent.KEYCODE_DPAD_RIGHT && code != KeyEvent.KEYCODE_DPAD_LEFT) return false;
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (!"STANDINGS".equals(panelKind())) return true;
            int next = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (next != page && (next == 0 || !d.standings.teams.isEmpty())) { page = next; body.invalidate(); }
            return true;
        }

        /** What the right panel shows for the focused session (§7). */
        String panelKind() {
            if (d.hidden) return "CIRCUIT";
            SportEvent.Session s = d.e.session(panelSession != null ? panelSession : d.heroSession);
            if (s != null && "in".equals(s.state) && F1Feed.LIVE_ORDER_TRUSTED && !s.order.isEmpty()) return "LIVE";
            if (s != null && "post".equals(s.state) && !s.order.isEmpty()) return "CLASSIFICATION";
            if (d.standings != null && !d.standings.drivers.isEmpty()) return "STANDINGS";
            return "CIRCUIT";
        }

        /** OK on a session or a segment's bout: its channel's app (or its store page) when one is known. */
        private void ok(String sessionId) {
            SportEvent.Session s = d.e.session(sessionId);
            if (okLabel(d, sessionId).isEmpty()) return;
            Boolean installed = d.apps.get(sessionId);
            if (installed != null && installed) actions.open(s.watch.app); else actions.get(s.watch.app);
        }

        String focusedId() {
            View f = findFocus();
            if (f instanceof SessionRow) return ((SessionRow) f).id;
            if (f instanceof BoutRow) return "bout:" + ((BoutRow) f).bout;
            return null;
        }

        void focus(String id) {
            if (id == null) return;
            for (SessionRow r : sessions) if (r.id.equals(id)) { r.requestFocus(); return; }
            for (BoutRow r : bouts) if (id.equals("bout:" + r.bout) || id.equals(r.segment)) { r.requestFocus(); return; }
        }

        /** F1: the opened session, else live, else next, else the race. UFC: the bout in the cage, else a followed fighter's, else the main event. */
        View launch() {
            if (d.f1()) {
                String id = d.focus;
                if (id == null) { SportEvent.Session s = SportModel.launchSession(d.e, d.now); id = s == null ? null : s.id; }
                for (SessionRow r : sessions) if (r.id.equals(id)) return r;
                return sessions.isEmpty() ? null : sessions.get(0);
            }
            if (d.focus != null) for (BoutRow r : bouts) if (r.segment.equals(d.focus)) return r;
            SportEvent.Bout in = SportModel.inCage(d.e);
            if (in != null) for (BoutRow r : bouts) if (r.bout.equals(in.id)) return r;
            for (String f : d.prefs.fighters) {
                SportEvent.Bout b = d.e.boutOf(f);
                if (b != null) for (BoutRow r : bouts) if (r.bout.equals(b.id)) return r;
            }
            SportEvent.Bout m = d.e.mainEvent();
            if (m != null) for (BoutRow r : bouts) if (r.bout.equals(m.id)) return r;
            return bouts.isEmpty() ? null : bouts.get(0);
        }
    }

    /**
     * What OK does on a session row, in words: "Open ANT1+" (its app on the TV) or "Get ANT1+" (its store page), as
     * the match screen's button says it; "" when OK does nothing there (done, called off, no known channel).
     */
    static String okLabel(Data d, String sessionId) {
        SportEvent.Session s = d == null ? null : d.e.session(sessionId);
        if (s == null || "post".equals(s.state) || "off".equals(s.state) || s.watch == null || s.watch.app == null || !s.watch.known) return "";
        Boolean installed = d.apps.get(sessionId);
        return I18n.f(installed != null && installed ? "open_app" : "get_app", SportWhere.appName(s.watch.app));
    }

    /** What a screen reader says on entry. */
    static String describe(Data d) {
        StringBuilder b = new StringBuilder(SportText.speak(d.e, d.e.session(d.heroSession), d.now, d.hidden));
        String place = d.f1() ? SportText.place(d.e) : d.e.venue;
        if (place != null && !place.isEmpty()) b.append(". ").append(place);
        return b.toString();
    }

    // ---------- drawing ----------
    /** Everything but the rows: the environment, the masthead, the hero, the labels, F1's right panel. */
    static final class Body extends AppViews.Spec {
        /** The drivers' table: the names at x 1254 (the panel's 1084 + 170, past the widest code), the points' room. */
        static final float STANDINGS_NAME_DX = 170, STANDINGS_POINTS_W = 100;
        final Screen screen;
        Body(Context c, Screen s) { super(c); screen = s; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        @Override void paint(Canvas cv) {
            Data d = screen.d;
            if (d == null) return;
            if (d.env != null) cv.drawBitmap(d.env, null, new RectF(0, 0, Ui.W, 760), AppArt.FILTER);
            masthead(cv, d);
            if (d.f1()) f1(cv, d); else ufc(cv, d);
        }

        private void masthead(Canvas cv, Data d) {
            Paint sp = new Paint(AppArt.FILTER);
            sp.setAlpha(Math.round(255 * 0.85f));
            cv.drawBitmap(AppArt.shirt(getContext(), Ui.TXT), null, new RectF(96, 62, 128, 94), sp);
            Paint br = Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.85f));
            float bl = Ui.baseline(Ui.MEDIUM, 26, 78);
            cv.drawText("Fanela", 140, bl, br);
            cv.drawText("· " + SportText.sportName(d.e.sport), 140 + br.measureText("Fanela") + 18, bl, Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.85f * 0.6f)));
            if (!d.statusText.isEmpty())
                AppKit.right(cv, Ui.paint(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f)), d.statusText, 1824, Ui.baseline(Ui.REGULAR, 28, 78));
        }

        private static void eyebrow(Canvas cv, String text) {
            Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, Ui.alpha(Ui.TXT, 0.8f), Ui.TRACK_EYEBROW);
            cv.drawText(AppKit.cut(eb, Cards.caps(text), 1180), 96 - AppKit.lead(eb), 152, eb);
        }

        static void label(Canvas cv, String s, float x, float bl) {
            Paint p = AppKit.caps(Ui.MEDIUM, Ui.T_LABEL, Ui.alpha(Ui.TXT, 0.62f), Ui.TRACK_EYEBROW);
            cv.drawText(AppKit.cut(p, Cards.caps(s), 900), x - AppKit.lead(p), bl, p);
        }

        private static void rule(Canvas cv, float y) {
            Paint p = new Paint();
            p.setColor(0x29FFFFFF);
            cv.drawRect(96, y, 1824, y + 2, p);
        }

        // ---------- F1 (§7) ----------
        private void f1(Canvas cv, Data d) {
            SportEvent e = d.e;
            eyebrow(cv, SportText.weekendEyebrow(e));
            float x = 96;
            if (d.outline != null) {
                SportViews.drawCircuit(cv, d.outline, new RectF(96, 186, 436, 416), 12, Ui.alpha(SportViews.IVORY, 0.95f));
                x = 476;
            }
            float tx = x;
            if (d.flag != null) { SportViews.flag(cv, d.flag, new RectF(x, 204, x + 72, 258), 8, 1); tx = x + 90; }
            Paint place = AppKit.text(Ui.MEDIUM, 32, Ui.TXT);
            cv.drawText(AppKit.cut(place, SportText.place(e), 1180 - tx), tx, 244, place);
            String facts = SportText.facts(e);
            if (!facts.isEmpty()) cv.drawText(facts, x, 306, AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2)));
            if (!d.trackTime.isEmpty()) cv.drawText(d.trackTime, x, 350, AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.62f)));
            Paint tp = AppKit.text(Ui.COND_BOLD, 110, Ui.TXT);
            tp.setLetterSpacing(-0.005f);
            String title = SportText.title(e);
            AppKit.fit(tp, title, 1140, 110, 80);
            cv.drawText(AppKit.cut(tp, title, 1140), 96, 532, tp);
            if (d.when != null) SportViews.drawWhen(cv, d.when, 1824, 176, 372, 214, 426, 492, Ui.TXT);
            rule(cv, 611);
            label(cv, SportText.sessionsLabel(), 96, 656);
            panel(cv, d);
        }

        /** The right panel (x 1084-1824), by the focused session. */
        private void panel(Canvas cv, Data d) {
            String kind = screen.panelKind();
            SportEvent.Session s = d.e.session(screen.panelSession != null ? screen.panelSession : d.heroSession);
            float x0 = 1084, right = 1824;
            switch (kind) {
                case "LIVE": {
                    int lap = F1Feed.shownLap(s);
                    label(cv, s.laps > 0 && lap > 0 ? SportText.f("live_order", lap, s.laps) : SportText.t("live_order_plain"), x0, 656);
                    grid(cv, d, s, true);
                    return;
                }
                case "CLASSIFICATION": {
                    label(cv, SportText.f("classification", SportText.session(s)), x0, 656);
                    grid(cv, d, s, false);
                    return;
                }
                case "STANDINGS": {
                    boolean teams = screen.page == 1 && !d.standings.teams.isEmpty();
                    label(cv, SportText.t(teams ? "teams" : "drivers"), x0, 656);
                    float y = 680;
                    if (teams) {
                        for (int i = 0; i < Math.min(5, d.standings.teams.size()); i++, y += 62) {
                            F1Feed.Team t = d.standings.teams.get(i);
                            float cy = y + 31, bl = Ui.baseline(Ui.COND_BOLD, 36, cy);
                            Paint num = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.6f));
                            cv.drawText(String.valueOf(t.rank > 0 ? t.rank : i + 1), x0 + 16, bl, num);
                            SportViews.bar(cv, x0 + 68, bl, 36, 8, t.color, 1);
                            Paint nm = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
                            cv.drawText(AppKit.cut(nm, t.name, 520), x0 + 92, Ui.baseline(Ui.MEDIUM, 30, cy), nm);
                            AppKit.right(cv, Ui.paint(Ui.COND_BOLD, 36, Ui.TXT), SportViews.Tile.points(t.points), right, bl);
                        }
                        return;
                    }
                    List<SportEvent.Entrant> top = new ArrayList<>();
                    for (int i = 0; i < Math.min(5, d.standings.drivers.size()); i++) top.add(d.standings.drivers.get(i));
                    SportEvent.Entrant focus = null;
                    for (SportEvent.Entrant x : d.standings.drivers) if (d.prefs.focusDriver(x.id)) { focus = x; break; }
                    if (focus != null && !top.contains(focus) && top.size() == 5) top.set(4, focus);
                    // a table: the names at one x whatever the code's width, the teams in one column after the
                    // longest name (as far left as the longest team allows before the points), the points right
                    Paint nm = Ui.paint(Ui.MEDIUM, 30, Ui.TXT), tm = Ui.paint(Ui.REGULAR, 27, Ui.alpha(Ui.TXT, 0.55f));
                    float nameX = x0 + STANDINGS_NAME_DX, teamEnd = right - STANDINGS_POINTS_W, nameW = 0, teamW = 0;
                    for (SportEvent.Entrant x : top) {
                        nameW = Math.max(nameW, nm.measureText(x.name == null ? "" : x.name));
                        teamW = Math.max(teamW, tm.measureText(x.team == null ? "" : x.team));
                    }
                    float teamX = Math.max(nameX + 200, Math.min(nameX + nameW + 28, teamEnd - teamW));
                    for (SportEvent.Entrant x : top) {
                        float cy = y + 31, bl = Ui.baseline(Ui.COND_BOLD, 36, cy);
                        Paint num = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.6f));
                        cv.drawText(String.valueOf(x.pos > 0 ? x.pos : top.indexOf(x) + 1), x0 + 16, bl, num);
                        SportViews.code(cv, x, x0 + 52, bl, 36, 8, 12, Ui.TXT);
                        cv.drawText(AppKit.cut(nm, x.name == null ? "" : x.name, teamX - 24 - nameX), nameX, Ui.baseline(Ui.MEDIUM, 30, cy), nm);
                        cv.drawText(AppKit.cut(tm, x.team == null ? "" : x.team, Math.max(0, teamEnd - teamX)), teamX, Ui.baseline(Ui.REGULAR, 27, cy), tm);
                        AppKit.right(cv, Ui.paint(Ui.COND_BOLD, 36, Ui.TXT), SportViews.Tile.points(x.points), right, bl);
                        y += 62;
                    }
                    return;
                }
                default: {
                    label(cv, SportText.t("circuit"), x0, 656);
                    Paint p = AppKit.text(Ui.MEDIUM, 32, Ui.TXT);
                    float y = 720;
                    String name = d.e.circuitName.isEmpty() ? SportText.place(d.e) : d.e.circuitName;
                    if (!name.isEmpty()) { cv.drawText(AppKit.cut(p, name, 740), x0, y, p); y += 52; }
                    Paint q = AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2));
                    if (d.e.laps > 0) { cv.drawText(SportText.f("laps", d.e.laps), x0, y, q); y += 46; }
                    if (d.e.lengthKm > 0) cv.drawText(SportText.f("km", SportText.km(d.e.lengthKm)), x0, y, q);
                }
            }
        }

        /** Ten rows in two columns (x 1084, 1464): place, bar, code, gap (the leader says LEADER; the focus driver lit). */
        private void grid(Canvas cv, Data d, SportEvent.Session s, boolean live) {
            for (int i = 0; i < Math.min(10, s.order.size()); i++) {
                SportEvent.Entrant x = s.order.get(i);
                float cx = i < 5 ? 1084 : 1464, cy = 680 + (i % 5) * 62 + 31, bl = Ui.baseline(Ui.COND_BOLD, 38, cy);
                if (d.prefs.focusDriver(x.id) || d.prefs.focusTeam(x.teamId)) {
                    Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
                    hl.setColor(0x1AFFFFFF);
                    cv.drawRoundRect(new RectF(cx - 8, cy - 27, cx + 348, cy + 27), 12, 12, hl);
                }
                Paint num = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.6f));
                AppKit.right(cv, num, String.valueOf(x.pos > 0 ? x.pos : i + 1), cx + 34, bl);
                SportViews.code(cv, x, cx + 48, bl, 38, 8, 12, Ui.TXT);
                String gap = i == 0 && (x.gap == null || x.gap.isEmpty()) ? Cards.caps(SportText.t("leader")) : x.gap == null ? "" : x.gap;
                AppKit.right(cv, Ui.paint(Ui.COND_BOLD, 34, Ui.alpha(Ui.TXT, 0.8f)), gap, cx + 336, bl);
            }
        }

        // ---------- UFC (§8) ----------
        private void ufc(Canvas cv, Data d) {
            SportEvent e = d.e;
            String venue = e.venue == null ? "" : e.venue;
            eyebrow(cv, SportText.ufcEvent(e) + (venue.isEmpty() ? "" : " · " + venue));
            SportEvent.Session hs = e.session(d.heroSession);
            SportEvent.Bout b = SportText.headline(e, hs, d.prefs);
            if (b != null) {
                SportViews.face(cv, d.headA, b.a, 196, 286, 200, 1, true);
                SportViews.face(cv, d.headB, b.b, 566, 286, 200, 1, true);
                AppKit.centre(cv, AppKit.text(Ui.COND, 56, Ui.alpha(Ui.TXT, 0.6f)), CardKit.VS, 381, 286 + 0.715f * 56 / 2);
                SportPanel.boundTitle(cv, b, 96, 500, 1140, 110, 80, d.kind == HomeModel.Kind.RESULT && !d.hidden);
                String bill = SportText.billing(b), rounds = SportText.rounds(b);
                Paint bp = AppKit.text(Ui.MEDIUM, 32, b.title ? SportViews.GOLD : Ui.alpha(Ui.TXT, Ui.A2));
                cv.drawText(bill, 96, 556, bp);
                if (!rounds.isEmpty()) cv.drawText((bill.isEmpty() ? "" : " · ") + rounds, 96 + bp.measureText(bill), 556, AppKit.text(Ui.REGULAR, 32, Ui.alpha(Ui.TXT, Ui.A2)));
            } else {
                Paint tp = AppKit.text(Ui.COND_BOLD, 110, Ui.TXT);
                cv.drawText(SportText.ufcEvent(e), 96, 500, tp);
            }
            if (d.when != null) SportViews.drawWhen(cv, d.when, 1824, 176, 372, 214, 426, 492, Ui.TXT);
            rule(cv, 589);
        }
    }

    // ---------- F1 session rows ----------
    /**
     * One session (70 tall, x 84-1012): the day, the time, the session; at the right its channel (sm), the LIVE pill,
     * or for a finished one "P1 ▌NOR" (hidden: Final); cancelled: the time struck to a dash and the word.
     */
    static final class SessionRow extends AppViews.Cell {
        final Screen screen;
        final String id;
        final float rowH;

        SessionRow(Context c, Screen s, String id, float rowH) { super(c); this.screen = s; this.id = id; this.rowH = rowH; }

        /** The time column's left edge. */
        static final float TIME_X = 216;

        /**
         * The session name's left edge: x 336 on a 24-hour clock, 360 on a 12-hour one, where "10:30 am" (RC Bold 36 +
         * 24, about 115 px) needs the room (the fixture rows' rule, ClubViews.FixtureRow.timeRoom).
         */
        static float nameX(boolean twelve) { return twelve ? 360 : 336; }

        /** The time column's room: up to the session name, less an 8 px gap. */
        static float timeRoom(boolean twelve) { return nameX(twelve) - TIME_X - 8; }

        /** The session name's right edge stays at x 736 whatever the clock. */
        static float nameRoom(boolean twelve) { return 736 - nameX(twelve); }

        @Override float radius() { return Ui.R_ROW; }

        @Override void paint(Canvas cv) {
            Data d = screen.d;
            SportEvent.Session s = d == null ? null : d.e.session(id);
            if (s == null) return;
            String ok = okLabel(d, id);
            setContentDescription(SportText.speak(d.e, s, d.now, d.hidden) + (ok.isEmpty() ? "" : ". " + ok));
            fill(cv);
            float cy = h() / 2;
            boolean done = "post".equals(s.state), off = "off".equals(s.state), live = "in".equals(s.state);
            float a = done || off ? 0.6f : 1f;
            Paint day = Ui.paint(Ui.MEDIUM, 30, Ui.alpha(Ui.TXT, a));
            cv.drawText(AppText.shortDate(s.start), 24, Ui.baseline(Ui.MEDIUM, 30, cy), day);
            String ts = off ? "—" : s.tbd ? AppText.t("tba") : I18n.time(s.start);
            boolean twelve = !I18n.clock24();
            SportViews.rowTime(cv, ts, off || s.tbd, TIME_X, cy, 36, timeRoom(twelve), Ui.alpha(Ui.TXT, a));
            Paint nm = Ui.paint(Ui.MEDIUM, 32, Ui.alpha(Ui.TXT, a));
            cv.drawText(AppKit.cut(nm, SportText.session(s), nameRoom(twelve)), nameX(twelve), Ui.baseline(Ui.MEDIUM, 32, cy), nm);
            float right = 916;
            if (live) {
                Paint t = AppKit.caps(Ui.BOLD, 26, 0, 0.1f);
                float pw = 16 + 14 + 12 + t.measureText(I18n.t("live")) + 18 - 2.6f;
                MatchdayViews.StageInfo.livePill(cv, right - pw, cy, 1, 1);
            } else if (off) {
                AppKit.right(cv, Ui.paint(Ui.MEDIUM, 28, Ui.alpha(Ui.TXT, 0.6f)), SportText.detail(s), right, Ui.baseline(Ui.MEDIUM, 28, cy));
            } else if (done) {
                SportEvent.Entrant w = SportText.winner(s);
                if (d.hidden || w == null) AppKit.right(cv, Ui.paint(Ui.MEDIUM, 28, Ui.alpha(Ui.TXT, 0.6f)), SportText.t("final"), right, Ui.baseline(Ui.MEDIUM, 28, cy));
                else {
                    float bl = Ui.baseline(Ui.COND_BOLD, 32, cy);
                    float cw = SportViews.codeWidth(w, 32, 8, 10);
                    SportViews.code(cv, w, right - cw, bl, 32, 8, 10, Ui.TXT);
                    AppKit.right(cv, Ui.paint(Ui.COND, 30, Ui.alpha(Ui.TXT, 0.6f)), "P1", right - cw - 14, bl);
                }
            } else if (isFocused() && !ok.isEmpty()) {
                // focused, the row says what OK does ("[OK] Get ANT1+"), as the match screen's button does
                AppKit.okHint(cv, ok, right, cy, Ui.TXT);
            } else {
                AppKit.Watch w = d.watch.get(s.id);
                if (w != null) AppKit.cell(cv, w, AppKit.Size.SM, right, cy, true, 360, false, 1);
            }
        }
    }

    // ---------- UFC rows ----------
    /**
     * The UFC bout list (QAS-9): no fade at its top, so no half-faded glyphs under the rule; a moved focus scrolls to a
     * stop: the top, a segment header's top (it leads the list), or a bout row's top less the pinned header's height,
     * so the row under the pinned header always starts whole. Once scrolled, the segment's header is pinned (pin()).
     */
    static final class BoutScroll extends ScrollView {
        /** The pinned header's height, spec px (a segment header's). */
        static final int PIN = 56;
        final Screen screen;

        BoutScroll(Context c, Screen s) { super(c); screen = s; }

        @Override protected float getTopFadingEdgeStrength() { return 0; }

        private int pin() { return Ui.px(getContext(), PIN); }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            if (getChildCount() == 0) return 0;
            int h = getHeight(), y = getScrollY(), foot = getVerticalFadingEdgeLength();
            int max = Math.max(0, getChildAt(0).getHeight() - h);
            int target = y;
            if (rect.bottom > y + h - foot) target = rect.bottom - (h - foot);
            if (rect.top < target + pin()) target = rect.top - pin();
            target = Math.max(0, Math.min(max, target));
            List<Integer> stops = stops(max);
            int to = target;
            if (target > y) {  // down: the first stop at or past the target (the end when none)
                to = max;
                for (int s : stops) if (s >= target) { to = s; break; }
            } else if (target < y) {  // up: the last stop at or before it
                to = 0;
                for (int s : stops) if (s <= target) to = s;
            }
            return to - y;
        }

        /** The scroll stops, ascending: 0, each header's top, each row's top less the pinned header. */
        private List<Integer> stops(int max) {
            java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
            out.add(0);
            ViewGroup list = (ViewGroup) getChildAt(0);
            for (int i = 0; i < list.getChildCount(); i++) {
                View ch = list.getChildAt(i);
                int s = ch instanceof SegmentHeader ? ch.getTop() : ch.getTop() - pin();
                if (s > 0 && s <= max) out.add(s);
            }
            return new ArrayList<>(out);
        }

        @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            pinUpdate();
        }

        /** The pinned header: hidden at the top and where a header leads the list; else the segment of the row under it. */
        void pinUpdate() {
            PinnedHeader p = screen.pinned;
            if (p == null || getChildCount() == 0) return;
            int y = getScrollY();
            ViewGroup list = (ViewGroup) getChildAt(0);
            String seg = null;
            boolean headerLeads = y <= 0;
            for (int i = 0; i < list.getChildCount() && !headerLeads && seg == null; i++) {
                View ch = list.getChildAt(i);
                if (ch instanceof SegmentHeader && Math.abs(ch.getTop() - y) <= 1) headerLeads = true;
                else if (ch.getBottom() > y + pin())
                    seg = ch instanceof SegmentHeader ? ((SegmentHeader) ch).id : ch instanceof BoutRow ? ((BoutRow) ch).segment : null;
            }
            if (headerLeads || seg == null) { p.setVisibility(View.GONE); return; }
            if (!seg.equals(p.id)) { p.id = seg; p.invalidate(); }
            p.setVisibility(View.VISIBLE);
        }
    }

    /** The segment header pinned at the top of the scrolled bout list, on the screen's own background (opaque). */
    static final class PinnedHeader extends SegmentHeader {
        PinnedHeader(Context c, Screen s) { super(c, s, ""); }

        @Override void paint(Canvas cv) {
            Data d = screen.d;
            cv.drawColor(Ui.APP_BG);
            if (d != null && d.env != null) cv.drawBitmap(d.env, null, new RectF(0, -600, Ui.W, 160), AppArt.FILTER);
            super.paint(cv);
        }
    }

    /** A segment's header (56): MAIN CARD + its start (RC Bold 30), its channel at the right. */
    static class SegmentHeader extends AppViews.Spec {
        final Screen screen;
        String id;
        SegmentHeader(Context c, Screen s, String id) { super(c); screen = s; this.id = id; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        @Override void paint(Canvas cv) {
            Data d = screen.d;
            SportEvent.Session s = d == null ? null : d.e.session(id);
            if (s == null) return;
            float cy = 30;
            Paint p = AppKit.caps(Ui.MEDIUM, 24, Ui.alpha(Ui.TXT, 0.9f), Ui.TRACK_EYEBROW);
            String w = Cards.caps(SportText.session(s));
            float bl = Ui.baseline(Ui.MEDIUM, 24, cy);
            cv.drawText(w, 96 - AppKit.lead(p), bl, p);
            String t = "off".equals(s.state) ? SportText.detail(s) : s.tbd ? AppText.t("tba") : I18n.time(s.start);
            cv.drawText(t, 96 + p.measureText(w) + 16, Ui.baseline(Ui.COND_BOLD, 30, cy), Ui.paint(Ui.COND_BOLD, 30, Ui.TXT));
            AppKit.Watch wt = d.watch.get(s.id);
            if (wt != null && !"post".equals(d.e.state) && !"post".equals(s.state)) AppKit.cell(cv, wt, AppKit.Size.SM, 1824, cy, true, 420, false, 1);
        }
    }

    /**
     * A bout (64, centred on x 960): the weight class (and TITLE), fighter A right-aligned to 900 with flag and record,
     * VS, fighter B from 1020; the result at the right (method · R3 2:27). Live: the LIVE pill and the round, on white
     * 7 %. Finished: the loser at 50 %, records gone. Hidden: both at 100 %, pre-event records, a slot. A followed
     * fighter's row has the ivory marker at x 96.
     */
    static final class BoutRow extends AppViews.Cell {
        final Screen screen;
        final String segment, bout;
        final boolean first;

        /** The fighter being chosen (long-press OK): 0 none, 1 A (left), 2 B (right). */
        int pick;
        /** The names' spans as last drawn (x from, x to), for the choice's underline. */
        private float aFrom, aTo, bFrom, bTo;

        BoutRow(Context c, Screen s, String segment, String bout, boolean first) {
            super(c); screen = s; this.segment = segment; this.bout = bout; this.first = first;
        }

        void pick(int side) { pick = side; invalidate(); }

        @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (!gain && screen.picking == this) screen.endPick(true);
        }

        @Override RectF shape() { return new RectF(84, 1, 1836, h() - 1); }
        @Override float radius() { return Ui.R_ROW; }

        SportEvent.Bout find() {
            Data d = screen.d;
            if (d == null) return null;
            SportEvent.Session s = d.e.session(segment);
            if (s != null) for (SportEvent.Bout b : s.bouts) if (bout.equals(b.id)) return b;
            return null;
        }

        void bind() {
            SportEvent.Bout b = find();
            Data d = screen.d;
            if (b == null) return;
            StringBuilder t = new StringBuilder(SportText.name(b.a)).append(' ').append(CardKit.VS).append(' ').append(SportText.name(b.b));
            t.append(". ").append(SportText.billing(b));
            if ("post".equals(b.state) && !d.hidden) {
                String w = SportText.wins(b), m = SportText.methodLine(b), r = SportText.roundLine(b);
                if (!w.isEmpty()) t.append(". ").append(w);
                if (!m.isEmpty()) t.append(", ").append(m);
                if (!r.isEmpty()) t.append(", ").append(r);
            } else if ("in".equals(b.state)) t.append(". ").append(I18n.t("live"));
            setContentDescription(t.toString());
            invalidate();
        }

        @Override void paint(Canvas cv) {
            SportEvent.Bout b = find();
            Data d = screen.d;
            if (b == null || d == null) return;
            if (!first) {
                Paint dv = new Paint();
                dv.setColor(0x1AFFFFFF);
                cv.drawRect(96, 0, 1824, 1, dv);
            }
            boolean live = "in".equals(b.state), done = "post".equals(b.state), off = "off".equals(b.state) || "CANCELLED".equals(b.detail);
            if (live) {
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(0x12FFFFFF);
                cv.drawRoundRect(shape(), radius(), radius(), bg);
            }
            fill(cv);
            float cy = h() / 2;
            if (followed(d, b)) {
                Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
                m.setColor(SportViews.IVORY);
                cv.drawRect(96, 4, 101, h() - 4, m);
            }
            // weight class and TITLE
            Paint wc = Ui.paint(Ui.REGULAR, 26, Ui.alpha(Ui.TXT, 0.66f));
            String w = AppKit.cut(wc, SportText.weight(b.weightClass), 330);
            cv.drawText(w, 108, Ui.baseline(Ui.REGULAR, 26, cy), wc);
            float wx = 108 + wc.measureText(w) + 14;
            if (b.title) wx += SportViews.titleChip(cv, wx, cy, 34, 1) + 14;
            boolean shown = done && !d.hidden;
            float aA = off ? 0.5f : shown && b.outcome == 2 ? 0.5f : 1f, aB = off ? 0.5f : shown && b.outcome == 1 ? 0.5f : 1f;
            // A: right-aligned to 900, its flag and record leftwards, 24 clear of the weight class (and its TITLE):
            // where the three don't fit (a long Greek class with TITLE), the record goes before the name is cut
            Paint np = Ui.paint(Ui.COND_BOLD, 40, 0);
            float bl = Ui.baseline(Ui.COND_BOLD, 40, cy);
            String aFull = SportText.name(b.a), recA = shown ? "" : record(b.a);
            float flagA = b.a != null && d.flags.get(b.a.flag) != null ? 40 + 18 : 0;
            float recW = recA.isEmpty() ? 0 : Ui.paint(Ui.REGULAR, 24, 0).measureText(recA);
            float clear = wx + 10;  // wx is 14 past the class: 24 in all
            boolean recOk = 900 - np.measureText(aFull) - 18 - flagA - recW >= clear;
            float minA = Math.max(clear + 18 + flagA + (recOk ? recW : 0), 560);
            np.setColor(Ui.alpha(Ui.TXT, aA));
            String an = AppKit.cut(np, aFull, 900 - minA);
            float ax = 900 - np.measureText(an);
            cv.drawText(an, ax, bl, np);
            aFrom = ax; aTo = 900;
            float fx = ax - 18;
            fx = side(cv, d, b.a, fx, cy, true, shown || !recOk, aA);
            // VS
            Paint vs = Ui.paint(Ui.COND, 28, Ui.alpha(Ui.TXT, 0.55f));
            AppKit.centre(cv, vs, CardKit.VS, 960, Ui.baseline(Ui.COND, 28, cy));
            // B from 1020
            np.setColor(Ui.alpha(Ui.TXT, aB));
            String bn = AppKit.cut(np, SportText.name(b.b), 420);
            cv.drawText(bn, 1020, bl, np);
            bFrom = 1020; bTo = 1020 + np.measureText(bn);
            if (pick != 0) {  // the fighter being chosen: an ivory bar under his name
                Paint u = new Paint(Paint.ANTI_ALIAS_FLAG);
                u.setColor(SportViews.IVORY);
                float l = pick == 1 ? aFrom : bFrom, r = pick == 1 ? aTo : bTo;
                cv.drawRoundRect(new RectF(l, bl + 8, r, bl + 13), 2.5f, 2.5f, u);
            }
            side(cv, d, b.b, 1020 + np.measureText(bn) + 18, cy, false, shown, aB);
            // the result cell, right-aligned to 1812
            float right = 1812;
            if (off) {
                AppKit.right(cv, Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.6f)), SportText.t("cancelled"), right, Ui.baseline(Ui.MEDIUM, 26, cy));
            } else if (live) {
                Paint rp = Ui.paint(Ui.COND_BOLD, 30, Ui.TXT);
                String r = SportText.roundClock(b);  // "" while UfcFeed.LIVE_ROUND_TRUSTED is false: the pill alone
                float rw = r.isEmpty() ? 0 : AppKit.right(cv, rp, r, right, Ui.baseline(Ui.COND_BOLD, 30, cy)) + 16;
                Paint t = AppKit.caps(Ui.BOLD, 26, 0, 0.1f);
                float pw = 16 + 14 + 12 + t.measureText(I18n.t("live")) + 18 - 2.6f;
                MatchdayViews.StageInfo.livePill(cv, right - rw - pw, cy, 1, 1);
            } else if (done && d.hidden) {
                SportViews.slot(cv, right - 150, cy - 13, right, cy + 13, 1);
            } else if (done) {
                Paint rp = Ui.paint(Ui.COND_BOLD, 30, Ui.TXT);
                String rc = SportText.roundClock(b);
                float rw = rc.isEmpty() ? 0 : AppKit.right(cv, rp, rc, right, Ui.baseline(Ui.COND_BOLD, 30, cy));
                String m = SportText.methodLine(b);
                if (!m.isEmpty()) AppKit.right(cv, Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.8f)), m + (rc.isEmpty() ? "" : " · "), right - rw - (rc.isEmpty() ? 0 : 8), Ui.baseline(Ui.MEDIUM, 26, cy));
            }
        }

        /** A fighter's flag (40 x 30) and record beside the name, away from the centre; returns the far edge. */
        private float side(Canvas cv, Data d, SportEvent.Fighter f, float x, float cy, boolean leftward, boolean shown, float a) {
            if (f == null) return x;
            Bitmap flag = d.flags.get(f.flag);
            if (flag != null) {
                RectF box = leftward ? new RectF(x - 40, cy - 15, x, cy + 15) : new RectF(x, cy - 15, x + 40, cy + 15);
                SportViews.flag(cv, flag, box, 4, a);
                x = leftward ? x - 40 - 18 : x + 40 + 18;
            }
            // records: before and during the fight (the pre-event record, never a post-fight one); hidden: pre-event
            if (shown) return x;
            String rec = record(f);
            if (rec.isEmpty()) return x;
            Paint rp = Ui.paint(Ui.REGULAR, 24, Ui.alpha(Ui.TXT, 0.5f * a));
            if (leftward) { AppKit.right(cv, rp, rec, x, Ui.baseline(Ui.REGULAR, 24, cy)); return x - rp.measureText(rec); }
            cv.drawText(rec, x, Ui.baseline(Ui.REGULAR, 24, cy), rp);
            return x + rp.measureText(rec);
        }

        /** The record a row shows: the pre-event one when known ("" for none). */
        private static String record(SportEvent.Fighter f) {
            if (f == null) return "";
            String rec = f.recordBefore != null && !f.recordBefore.isEmpty() ? f.recordBefore : f.record;
            return rec == null ? "" : rec;
        }

        private static boolean followed(Data d, SportEvent.Bout b) {
            return (b.a != null && d.prefs.followsFighter(b.a.id)) || (b.b != null && d.prefs.followsFighter(b.b.id));
        }
    }

    /**
     * The event screen's foot line, in the app's own look (no system toast): a graphite capsule centred at the foot
     * over the list's fade, the OK keycap (as AppKit.okHint draws it) and the words and, after a "·", a quieter second
     * part. It stays while a fighter is chosen (ms 0) and fades after a confirmation.
     */
    static final class Notice extends AppViews.Spec {
        private String main = "", sub = "";
        private boolean ok;
        private final Runnable fade = new Runnable() { public void run() { hide(); } };

        Notice(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setAlpha(0f);
        }

        /** Shows the line; ms > 0 fades it after that long. With a second part it is a choice: the OK keycap leads. */
        void show(String text, String second, long ms) {
            main = text == null ? "" : text;
            sub = second == null ? "" : second;
            ok = !sub.isEmpty();
            removeCallbacks(fade);
            animate().cancel();
            animate().alpha(1f).setDuration(Ui.FOCUS_MS).start();
            invalidate();
            if (ms > 0) postDelayed(fade, ms);
        }

        void hide() {
            removeCallbacks(fade);
            animate().cancel();
            animate().alpha(0f).setDuration(Ui.PUSH_MS).start();
        }

        @Override void paint(Canvas cv) {
            if (main.isEmpty()) return;
            Paint t = AppKit.text(Ui.MEDIUM, 30, SportViews.IVORY);
            Paint s = AppKit.text(Ui.REGULAR, 26, Ui.alpha(SportViews.IVORY, Ui.A2));
            Paint k = AppKit.caps(Ui.BOLD, 22, Ui.alpha(SportViews.IVORY, Ui.A2), 0.05f);
            float kw = ok ? k.measureText("OK") + 20 + 14 : 0;
            String dot = sub.isEmpty() ? "" : "  ·  ";
            float w = kw + t.measureText(main) + s.measureText(dot + sub);
            float pad = 36, h = 72, cy = 1010, x0 = (Ui.W - w) / 2;
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(Ui.alpha(SportViews.GRAPHITE, 0.96f));
            bg.setShadowLayer(14, 0, 8, 0x73000000);
            RectF box = new RectF(x0 - pad, cy - h / 2, x0 + w + pad, cy + h / 2);
            cv.drawRoundRect(box, h / 2, h / 2, bg);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2);
            ring.setColor(0x24FFFFFF);
            cv.drawRoundRect(box, h / 2, h / 2, ring);
            float x = x0;
            if (ok) {
                Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                b.setStyle(Paint.Style.STROKE);
                b.setStrokeWidth(2);
                b.setColor(Ui.alpha(SportViews.IVORY, 0.6f * Ui.A2));
                cv.drawRoundRect(new RectF(x + 1, cy - 17, x + kw - 15, cy + 17), 7, 7, b);
                cv.drawText("OK", x + 10, cy + 0.34f * 22, k);
                x += kw;
            }
            cv.drawText(main, x, Ui.baseline(Ui.MEDIUM, 30, cy), t);
            x += t.measureText(main);
            if (!sub.isEmpty()) cv.drawText(dot + sub, x, Ui.baseline(Ui.REGULAR, 26, cy), s);
        }
    }
}
