package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The selected sport on the Club layout (SPEC-F1-UFC §9.1, F1-UFC-ARCHITECTURE §7): it takes the place of a club's
 * hero, latest tiles and fixtures. The hero is the weekend or the event (live, a fresh result, or next: the circuit,
 * the place and the grand prix, or the two faces and the bout), the latest tiles the last podium (and the drivers'
 * top two) or the last main events, the timetable every session of the next two weekends or this card's segments and
 * the next events, each with its own channel. OK on any of them opens the event screen; OK on a live hero opens the
 * channel's app when it is on the TV. The field behind is the sport's (SportViews.field).
 *
 * ClubLayout owns the rail, the masthead and the field view; it hands the main column's D-pad here while a sport is
 * selected (move) and asks for focus keys that survive a rebind ("sp:hero", "sp:tile:<key>", "sp:row:<key>").
 */
final class SportPanel {
    /** What OK asks of the screen. */
    interface Host {
        void openEvent(SportEvent e, String sessionId);
    }

    /** The images and words one sport needs, made on the display thread (prepare). */
    static final class Art {
        Bitmap field, flag, headA, headB;
        float[] outline;
        AppKit.Watch heroWatch;
        final Map<String, AppKit.Watch> watch = new HashMap<>();
        F1Feed.Standings standings;
        String okLabel = "";
    }

    static final float LIST_TOP = 736.05f, LATEST_TOP = 556, FADE_TOP = 1000, HEADER = 56;

    /** Display thread: the sport's field, the hero's flag or faces, the cells' channels, the standings (cached file). */
    static Art prepare(Context c, SportModel.SportData d) {
        Art a = new Art();
        SportEvent he = d.hero != null ? d.hero.e : d.events.isEmpty() ? null : d.events.get(0);
        a.outline = SportViews.outline(c, he);
        a.field = SportViews.field(c, d.sport, a.outline, (int) KitField.HEIGHT);
        if (d.hero != null) {
            SportEvent.Session s = d.hero.e.session(d.hero.sessionId);
            if (SportEvent.F1.equals(d.sport)) a.flag = SportViews.image(c, flagUrl(d.hero.e), true);
            else {
                SportEvent.Bout b = SportText.staged(d.hero.e, s, d.prefs);
                if (b != null) {
                    a.headA = b.a == null ? null : SportViews.image(c, b.a.headshot, true);
                    a.headB = b.b == null ? null : SportViews.image(c, b.b.headshot, true);
                    if (a.headA == null || a.headB == null) { a.headA = null; a.headB = null; }  // both or neither (§3.6)
                }
            }
            if (d.kind == HomeModel.Kind.NEXT || d.kind == HomeModel.Kind.LIVE) a.heroWatch = SportViews.watch(c, d.hero.e, s);
            if (d.kind == HomeModel.Kind.LIVE) {
                String app = SportViews.installedApp(c, s);
                a.okLabel = app != null ? AppText.f("opens", SportWhere.appName(app)) : "";
            }
        }
        for (EventCards.Pick p : d.upcoming) a.watch.put(SportModel.key(p), SportViews.watch(c, p.e, p.e.session(p.sessionId)));
        if (SportEvent.F1.equals(d.sport) && !d.hidden) {
            try { a.standings = F1Feed.table(c); } catch (Throwable ignored) { }
            if (a.standings != null && a.standings.drivers.size() < 2) a.standings = null;
        }
        return a;
    }

    /**
     * The host's flag: the same source as the cards (SportNames' grand prix table, by circuit id, event id or name,
     * through EventKit.flagUrl to ESPN's country PNG); null when the grand prix is not one the table knows.
     */
    static String flagUrl(SportEvent e) {
        return e == null ? null : EventKit.flagUrl(SportNames.flagCode(e));
    }

    private final Context c;
    private final Host host;
    final FrameLayout root;
    final Hero hero;
    private final AppViews.Label latestLabel, listLabel;
    final SportViews.Tile[] tiles = new SportViews.Tile[2];
    private final ClubLayout.Scroller listScroll;
    private final LinearLayout list;
    final List<SportViews.Row> rows = new ArrayList<>();
    private final View peek;
    private SportModel.SportData data;
    private String rowsKey = "";
    private android.animation.ValueAnimator scrollAnim;

    SportPanel(Context c, Host host) {
        this.c = c;
        this.host = host;
        root = new FrameLayout(c);
        hero = new Hero(c);
        hero.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { okHero(); } });
        root.addView(hero, AppViews.at(c, 300, 112, 1524, 420));
        latestLabel = label(c);
        listLabel = label(c);
        root.addView(latestLabel, AppViews.at(c, 336, LATEST_TOP, 900, 34));
        root.addView(listLabel, AppViews.at(c, 336, 696, 1100, 34));
        for (int i = 0; i < 2; i++) {
            final SportViews.Tile t = new SportViews.Tile(c);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (t.pick != null) SportPanel.this.host.openEvent(t.pick.e, t.pick.sessionId); } });
            tiles[i] = t;
            root.addView(t, AppViews.at(c, 336, 598.05f, 600, 84));
        }
        listScroll = new ClubLayout.Scroller(c);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setFocusable(false);
        listScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, AppViews.at(c, 318, LIST_TOP, 1524, Ui.H - LIST_TOP));
        peek = new ClubLayout.PeekFade(c);
        root.addView(peek, AppViews.at(c, 300, FADE_TOP, 1560, Ui.H - FADE_TOP));
    }

    private static AppViews.Label label(Context c) {
        return new AppViews.Label(c, Ui.MEDIUM, Ui.T_LABEL, Ui.alpha(Ui.TXT, 0.62f), Ui.TRACK_EYEBROW, 23.18f);
    }

    View view() { return root; }

    SportModel.SportData data() { return data; }

    /**
     * Shows a sport's data (UI thread). Returns a focus key to restore when a focused view was replaced (the caller
     * parked the focus first: see wantsPark), else null.
     */
    void bind(SportModel.SportData d, long now) {
        data = d;
        Art a = d.art instanceof Art ? (Art) d.art : new Art();
        boolean loading = d.kind == HomeModel.Kind.LOADING;
        hero.bind(d, a, now);
        // latest: the newest result, then the drivers' top two (F1) or the result before
        List<Object> shown = new ArrayList<>();
        if (!loading) {
            if (!d.latest.isEmpty()) shown.add(d.latest.get(0));
            if (a.standings != null && !d.hidden) shown.add(a.standings);
            else if (d.latest.size() > 1) shown.add(d.latest.get(1));
        }
        boolean hasLatest = loading || !shown.isEmpty();
        latestLabel.set(Cards.caps(AppText.t("latest")));
        latestLabel.setVisibility(hasLatest ? View.VISIBLE : View.GONE);
        float x = 336;
        for (int i = 0; i < 2; i++) {
            SportViews.Tile t = tiles[i];
            if (loading) {
                float w = i == 0 ? 560 : 620;
                t.skeleton();
                place(t, x, 598.05f, w, 84);
                t.setVisibility(View.VISIBLE);
                x += w + 24;
            } else if (i < shown.size()) {
                Object o = shown.get(i);
                if (o instanceof F1Feed.Standings) t.bind((F1Feed.Standings) o); else t.bind((EventCards.Pick) o, d.hidden);
                float w = Math.min(i == 0 ? 760 : 1824 - x, t.width(i == 0 ? 320 : 460));
                place(t, x, 598.05f, w, 84);
                t.setVisibility(View.VISIBLE);
                x += w + 24;
            } else {
                t.setVisibility(View.GONE);
                t.setFocusable(false);
            }
        }
        // the timetable
        float listTop = hasLatest ? LIST_TOP : LIST_TOP - 140;
        listLabel.set(Cards.caps(listLabel(d)));
        place(listLabel, 336, listTop - 40.05f, 1100, 34);
        place(listScroll, 318, listTop, 1524, Ui.H - listTop);
        listLabel.setVisibility(loading || !d.upcoming.isEmpty() ? View.VISIBLE : View.GONE);
        StringBuilder rk = new StringBuilder(d.sport).append(loading);
        for (EventCards.Pick p : d.upcoming) rk.append(',').append(SportModel.key(p));
        int n = loading ? 6 : d.upcoming.size();
        float contentH = 0;
        if (!rk.toString().equals(rowsKey)) {
            rowsKey = rk.toString();
            list.removeAllViews();
            rows.clear();
            SportEvent firstEvent = d.upcoming.isEmpty() ? null : d.upcoming.get(0).e;
            for (int i = 0; i < n; i++) {
                if (!loading && i > 0 && d.upcoming.get(i).e != d.upcoming.get(i - 1).e && (SportEvent.F1.equals(d.sport) || d.upcoming.get(i - 1).e == firstEvent)) {
                    SportViews.Header hd = new SportViews.Header(c);
                    hd.set(SportEvent.F1.equals(d.sport) ? SportText.t("next_gp") : SportText.t("next_events"));
                    list.addView(hd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, HEADER)));
                }
                final SportViews.Row r = new SportViews.Row(c);
                r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (r.pick != null) host.openEvent(r.pick.e, r.pick.sessionId); } });
                r.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) { if (has) ensureRowVisible(r); } });
                rows.add(r);
                list.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, ClubLayout.ROW)));
            }
            listScroll.scrollTo(0, 0);
        }
        SportEvent cardEvent = d.hero != null && !d.hero.last ? d.hero.e : null;
        for (int i = 0; i < n; i++) {
            SportViews.Row r = rows.get(i);
            if (loading) { r.skeleton(i == 0); continue; }
            EventCards.Pick p = d.upcoming.get(i);
            boolean nextEvent = SportEvent.UFC.equals(d.sport) && p.e != cardEvent;
            boolean first = i == 0 || p.e != d.upcoming.get(i - 1).e && SportEvent.F1.equals(d.sport) || nextEvent && d.upcoming.get(i - 1).e == cardEvent;
            r.bind(p, a.watch.get(SportModel.key(p)), first, nextEvent, now, d.hidden);
            contentH += ClubLayout.ROW;
        }
        int foot = Ui.px(c, ClubLayout.listFoot(Ui.H - listTop, FADE_TOP - listTop));
        if (list.getPaddingBottom() != foot) list.setPadding(0, 0, 0, foot);
        peek.setVisibility(list.getChildCount() * ClubLayout.ROW > FADE_TOP - listTop ? View.VISIBLE : View.GONE);
    }

    static String listLabel(SportModel.SportData d) {
        if (SportEvent.F1.equals(d.sport)) return SportText.t("weekend") + " · " + SportText.zone();
        boolean card = d.hero != null && !d.hero.last;
        return card ? SportText.t("this_card") + " · " + SportText.zone() : SportText.t("next_events");
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

    /** Skeleton pulse (ClubLayout's animator drives it). */
    void pulse(float v) {
        hero.pulse = v; hero.invalidate();
        for (SportViews.Tile t : tiles) { t.pulse = v; t.invalidate(); }
        for (SportViews.Row r : rows) { r.pulse = v; r.invalidate(); }
    }

    // ---------- focus ----------
    boolean owns(View f) {
        if (f == null) return false;
        for (View p = f; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == root) return true;
        return false;
    }

    /** A key for the focused view that survives a rebind; null when the panel does not hold the focus. */
    String focusKey(View f) {
        if (f == hero) return "sp:hero";
        for (SportViews.Tile t : tiles) if (f == t && !t.key.isEmpty()) return "sp:tile:" + t.key;
        for (SportViews.Row r : rows) if (f == r && r.pick != null) return "sp:row:" + r.key();
        return null;
    }

    /** Focuses the item with this key, or the hero. */
    void focus(String key) {
        if (key != null && key.startsWith("sp:tile:"))
            for (SportViews.Tile t : tiles) if (t.getVisibility() == View.VISIBLE && t.isFocusable() && key.substring(8).equals(t.key)) { t.requestFocus(); return; }
        if (key != null && key.startsWith("sp:row:"))
            for (SportViews.Row r : rows) if (r.pick != null && key.substring(7).equals(r.key())) { r.requestFocus(); return; }
        hero.requestFocus();
    }

    private List<SportViews.Tile> shownTiles() {
        List<SportViews.Tile> out = new ArrayList<>();
        for (SportViews.Tile t : tiles) if (t.getVisibility() == View.VISIBLE && t.isFocusable()) out.add(t);
        return out;
    }

    private List<SportViews.Row> shownRows() {
        List<SportViews.Row> out = new ArrayList<>();
        for (SportViews.Row r : rows) if (r.pick != null) out.add(r);
        return out;
    }

    /** The D-pad inside the panel (as the club's column, SPEC-APP 3.6); Left at the column's edge returns rail. */
    View move(View f, int code, View rail) {
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        List<SportViews.Tile> ts = shownTiles();
        List<SportViews.Row> rs = shownRows();
        if (left) {
            int ti = ts.indexOf(f);
            if (ti > 0) return ts.get(ti - 1);
            return rail;
        }
        if (f == hero) return down ? (!ts.isEmpty() ? ts.get(0) : !rs.isEmpty() ? rs.get(0) : null) : null;
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

    /** Keeps the focused row clear of the fade, on whole rows (as the club's list; headers count as rows' room). */
    private void ensureRowVisible(View r) {
        float s = Ui.scale(c);
        float viewTop = listScroll.getTop() / s, sy = listScroll.getScrollY() / s;
        float top = r.getTop() / s, bottom = r.getBottom() / s, fade = FADE_TOP - viewTop;
        float target = sy;
        if (bottom - sy > fade - ClubLayout.ROW && bottom < list.getHeight() / s - 1) target = bottom - (fade - ClubLayout.ROW);
        else if (bottom - sy > fade) target = bottom - fade;
        if (top - sy < ClubLayout.ROW) target = Math.max(0, top - ClubLayout.ROW);
        if (target == sy) return;
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = android.animation.ValueAnimator.ofInt(listScroll.getScrollY(), Math.round(target * s));
        scrollAnim.setDuration(Ui.SCROLL_MS);
        scrollAnim.setInterpolator(Ui.DECELERATE);
        scrollAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) { listScroll.scrollTo(0, (int) a.getAnimatedValue()); }
        });
        scrollAnim.start();
    }

    void scrollTop() { listScroll.scrollTo(0, 0); }

    /** OK on the hero: a live session's app when it is on the TV, else the event screen. */
    private void okHero() {
        if (data == null || data.hero == null) return;
        SportEvent.Session s = data.hero.e.session(data.hero.sessionId);
        if (data.kind == HomeModel.Kind.LIVE) {
            String app = SportViews.installedApp(c, s);
            if (app != null && OpenActivity.launch(c, app)) return;
        }
        host.openEvent(data.hero.e, data.hero.sessionId);
    }

    void release() { if (scrollAnim != null) scrollAnim.cancel(); }

    // ---------- the hero ----------
    /**
     * The sport's hero (§9.1), 1524 x 420 at (300, 112) like a club's: the eyebrow; F1 the circuit, the flag and the
     * place, the laps and length, the long grand prix name; UFC the two faces with VS and the bout; the when column at
     * the right (the session leads F1's relative line), the watch line. Live: the LIVE pill and the session, LAP 34/50
     * or R2. A result: who won, the podium or the method.
     */
    static final class Hero extends AppViews.Cell {
        static final float L = 36, R = 1484;
        SportModel.SportData d;
        Art a = new Art();
        long now;
        float pulse = 1;
        private SportViews.When when;

        Hero(Context c) { super(c); }

        @Override float radius() { return Ui.R_HERO; }

        void bind(SportModel.SportData d, Art a, long now) {
            this.d = d; this.a = a; this.now = now;
            when = null;
            if (d.hero != null) {
                SportEvent.Session s = d.hero.e.session(d.hero.sessionId);
                when = SportViews.when(d.hero.e, s, d.kind, now, d.hidden, d.prefs, true);
                when.watch = a.heroWatch;
                when.okHint = a.okLabel;
            }
            setContentDescription(describe());
            invalidate();
        }

        String describe() {
            if (d == null) return "";
            if (d.hero == null) return d.label + ". " + (d.kind == HomeModel.Kind.LOADING ? SportText.t("loading_sport") : none(d.sport));
            return SportText.speak(d.hero.e, d.hero.e.session(d.hero.sessionId), now, d.hidden);
        }

        static String none(String sport) { return SportText.t(SportEvent.UFC.equals(sport) ? "no_events_ufc" : "no_events_f1"); }

        @Override void paint(Canvas cv) {
            fill(cv);
            if (d == null) return;
            if (d.kind == HomeModel.Kind.LOADING) {
                AppKit.skeleton(cv, L, 30, 560, 60, 12, pulse);
                AppKit.skeleton(cv, L, 90, 320, 290, 24, pulse);
                AppKit.skeleton(cv, L, 320, 900, 400, 16, pulse);
                AppKit.skeleton(cv, 1100, 40, R, 100, 16, pulse);
                AppKit.skeleton(cv, 1050, 130, R, 290, 16, pulse);
                return;
            }
            if (d.hero == null) {
                Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, Ui.alpha(Ui.TXT, 0.8f), Ui.TRACK_EYEBROW);
                cv.drawText(Cards.caps(d.label), L - AppKit.lead(eb), 53.1f, eb);
                Paint t = AppKit.text(Ui.COND_BOLD, 72, Ui.TXT);
                cv.drawText(none(d.sport), L, 230, t);
                return;
            }
            SportEvent e = d.hero.e;
            SportEvent.Session s = e.session(d.hero.sessionId);
            eyebrow(cv, e, s);
            if (SportEvent.F1.equals(e.sport)) f1Left(cv, e); else ufcLeft(cv, e, s);
            if (when != null) SportViews.drawWhen(cv, when, R, 89.5f, 269.7f, Ui.T_DISPLAY, 322.9f, 378.06f, Ui.TXT);
        }

        /** "NEXT · FORMULA 1"; "NEXT · UFC 334 · HEAVYWEIGHT TITLE" (the title segment gold); a result: "FINAL · …". */
        private void eyebrow(Canvas cv, SportEvent e, SportEvent.Session s) {
            String lead = d.kind == HomeModel.Kind.RESULT ? SportText.t("final") : d.kind == HomeModel.Kind.OFF ? SportText.detail(s)
                : d.kind == HomeModel.Kind.LIVE ? "" : AppText.t("next");
            List<String> parts = new ArrayList<>();
            if (!lead.isEmpty()) parts.add(lead);
            String gold = "";
            if (SportEvent.F1.equals(e.sport)) parts.add(SportText.t("f1"));
            else {
                parts.add(SportText.ufcEvent(e));
                gold = goldBilling(e, s, d.prefs);
            }
            Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, Ui.alpha(Ui.TXT, 0.8f), Ui.TRACK_EYEBROW);
            String text = Cards.caps(CardKit.join(parts, " · "));
            float x = L - AppKit.lead(eb);
            cv.drawText(text, x, 53.1f, eb);
            if (!gold.isEmpty()) {
                x += eb.measureText(text);
                cv.drawText(" · ", x, 53.1f, eb);
                x += eb.measureText(" · ");
                eb.setColor(SportViews.GOLD);
                cv.drawText(AppKit.cut(eb, Cards.caps(gold), 900 - x), x, 53.1f, eb);
            }
        }

        private void f1Left(Canvas cv, SportEvent e) {
            float x = L;
            if (a.outline != null) {
                SportViews.drawCircuit(cv, a.outline, new RectF(40, 84, 320, 280), 11, Ui.alpha(SportViews.IVORY, 0.95f));
                x = 352;
            }
            float tx = x;
            if (a.flag != null) {
                SportViews.flag(cv, a.flag, new RectF(x, 100, x + 64, 148), 8, 1);
                tx = x + 64 + 16;
            }
            Paint place = AppKit.text(Ui.MEDIUM, 30, Ui.TXT);
            cv.drawText(AppKit.cut(place, SportText.place(e), 1000 - tx), tx, Ui.baseline(Ui.MEDIUM, 30, 124), place);
            String facts = SportText.facts(e);
            if (!facts.isEmpty()) cv.drawText(facts, x, 190, AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2)));
            Paint tp = AppKit.text(Ui.COND_BOLD, 104, Ui.TXT);
            tp.setLetterSpacing(-0.005f);
            String title = SportText.title(e);
            AppKit.fit(tp, title, 860, 104, 80);
            cv.drawText(AppKit.cut(tp, title, 860), L, 382, tp);
        }

        private void ufcLeft(Canvas cv, SportEvent e, SportEvent.Session s) {
            SportEvent.Bout b = SportText.staged(e, s, d.prefs);
            if (b == null) {
                Paint tp = AppKit.text(Ui.COND_BOLD, 104, Ui.TXT);
                cv.drawText(SportText.ufcEvent(e), L, 382, tp);
                return;
            }
            SportViews.face(cv, a.headA, b.a, 138, 182, 196, 1, true);
            SportViews.face(cv, a.headB, b.b, 446, 182, 196, 1, true);
            Paint vs = AppKit.text(Ui.COND, 52, Ui.alpha(Ui.TXT, 0.6f));
            AppKit.centre(cv, vs, CardKit.VS, 292, 182 + 0.715f * 52 / 2);
            boolean result = d.kind == HomeModel.Kind.RESULT && !d.hidden;
            boundTitle(cv, b, L, 382, 860, 104, 80, result);
        }
    }

    /**
     * The hero eyebrow's gold segment ("HEAVYWEIGHT TITLE"): the billing of the bout on stage (SportText.staged: while
     * the card is live, the bout in the cage, as ufcLeft draws it), and only when that bout is a title fight (QAS-2).
     */
    static String goldBilling(SportEvent e, SportEvent.Session s, SportPrefs p) {
        SportEvent.Bout b = SportText.staged(e, s, p);
        return b != null && b.title ? SportText.billing(b) : "";
    }

    /**
     * "Gane VS Hokit" at RC Bold size (down to min in maxW), the loser at 50 % on a result; VS RC Regular at 55 %. Never
     * an ellipsis (QAS-6): each name takes the width it needs; at min, the longer name's tight form, then both
     * (SportNames.boutFit), at the largest size that fits.
     */
    static void boundTitle(Canvas cv, SportEvent.Bout b, float x, float bl, float maxW, float size, float min, boolean result) {
        String an = SportText.surname(b.a), bn = SportText.surname(b.b);
        final Paint tp = AppKit.text(Ui.COND_BOLD, size, Ui.TXT);
        tp.setLetterSpacing(-0.005f);
        Paint vp = AppKit.text(Ui.COND, size * 0.56f, 0);
        SettingsLogic.Measure m = new SettingsLogic.Measure() { public float width(String s) { return tp.measureText(s); } };
        float z = min;
        String[] names = null;
        for (float t = size; t >= min && names == null; t -= 2) {  // the largest size where the names fit, tight if need be
            tp.setTextSize(t); vp.setTextSize(t * 0.56f);
            names = SportNames.boutFit(an, bn, maxW - vp.measureText(CardKit.VS) - 0.5f * t, m);
            if (names != null) z = t;
        }
        if (names != null) { an = names[0]; bn = names[1]; } else { an = SportNames.tight(an); bn = SportNames.tight(bn); }
        tp.setTextSize(z);
        vp.setTextSize(z * 0.56f);
        vp.setColor(Ui.alpha(Ui.TXT, 0.55f));
        tp.setColor(Ui.alpha(Ui.TXT, result && b.outcome == 2 ? 0.5f : 1f));
        String ac = names != null ? an : AppKit.cut(tp, an, maxW / 2);
        cv.drawText(ac, x, bl, tp);
        float xx = x + tp.measureText(ac) + 0.25f * z;
        cv.drawText(CardKit.VS, xx, bl, vp);
        xx += vp.measureText(CardKit.VS) + 0.25f * z;
        tp.setColor(Ui.alpha(Ui.TXT, result && b.outcome == 1 ? 0.5f : 1f));
        cv.drawText(AppKit.cut(tp, bn, x + maxW - xx), xx, bl, tp);
    }
}
