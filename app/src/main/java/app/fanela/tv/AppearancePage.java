package app.fanela.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Settings › Appearance (SPEC-APP 5.3): the card style, chosen by looking at five live previews of the viewer's own
 * next match (CardStyles.draw, the launcher's renderer); the app layout (Club / Matchday) with mini wireframes; and
 * whether the TV's home screen shows scores. A card style or the scores switch republishes the home-screen rows in
 * the background at once; a layout applies when the home screen shows again (HomeActivity rebuilds on resume). On a TV
 * whose home screen shows no app rows (Google TV) the scores switch would do nothing: its row says so instead, as
 * Options' "Home-screen rows" does, and the previews show scores, as the app's own cards always do.
 */
final class AppearancePage {
    /** What the page needs from Settings. */
    interface Host {
        /** Redraws the home-screen rows in the background (coalesced). */
        void republish();
        boolean gone();
    }

    static final String[] STYLE_WORDS = {"style_signature", "style_clean", "style_kit", "style_ticket", "style_clash"};
    /** The styles on the page, one preview each (Appearance.STYLES order). */
    static final int N = Appearance.STYLES.length;
    /**
     * Preview slots: the five styles in one row across the page's 1728 px column from x 96, 24 apart (326.4 x 183.6,
     * 16:9), so every look is seen at once and side by side; the app layout and the scores switch stay on the screen.
     */
    static final float PGAP = 24, PW = (1728 - (N - 1) * PGAP) / N, PH = PW * 540 / 960;

    final SettingsViews.Page page;
    final Preview[] previews = new Preview[N];
    final Caption[] captions = new Caption[N];
    final LayoutChoice[] layouts = new LayoutChoice[2];
    final SettingsViews.OptionRow scores;
    private final SettingsViews.Text styleHint;
    private final SettingsViews.Block hintBlock, scoresBlock;
    private final Context c;
    private final Host host;
    private final ExecutorService work;
    private final Handler ui;
    /** The TV's home screen shows the app's rows (or a debug build draws them anyway): the scores switch works. */
    private final boolean rows;
    private int generation;

    AppearancePage(Context c, Host host, ExecutorService work, Handler ui) {
        this.c = c;
        this.host = host;
        this.work = work;
        this.ui = ui;
        rows = HomeRows.supported(c) || SettingsDebug.forceRows(c);
        page = new SettingsViews.Page(c, true);
        SettingsViews.Text title = new SettingsViews.Text(c, AppText.t("appearance"), Ui.BOLD, 56, Ui.TXT, 1100, 56 * 1.1f, 1).heading();
        page.title(AppText.t("appearance"));
        page.add(title, 96, 1100, title.height(), 0);
        SettingsViews.Text cs = new SettingsViews.Text(c, AppText.t("card_style"), Ui.MEDIUM, 34, Ui.TXT, 1728, 34 * 1.2f, 1).heading();
        page.add(cs, 96, 1728, cs.height(), 36);
        styleHint = new SettingsViews.Text(c, AppText.t("card_style_hint"), Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f), 1728, 0, 2);
        hintBlock = page.add(styleHint, 96, 1728, styleHint.height(), 8);
        String style = Appearance.cardStyle(c);
        SettingsViews.Block first = null;
        for (int i = 0; i < N; i++) {
            final String id = Appearance.STYLES[i];
            final Preview p = new Preview(c, id, AppText.t(STYLE_WORDS[i]));
            p.setTag("style:" + id);  // focus keys: Settings puts the focus back after the system re-creates it
            p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseStyle(id); } });
            final int k = i;
            p.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) { if (has) looked = k; } });
            previews[i] = p;
            captions[i] = new Caption(c, AppText.t(STYLE_WORDS[i]), i == 0 ? AppText.t("style_default") : "");
            float x = 96 + i * (PW + PGAP);
            SettingsViews.Block b = first == null ? page.add(p, x, PW, PH, 22) : page.beside(p, first, x, PW, PH, 0);
            if (first == null) first = b;
            page.beside(captions[i], first, x, PW, 36, PH + 14);
        }
        // the captions sit beside the previews: a spacer keeps the column's place for them
        View capSpace = new View(c);
        page.add(capSpace, 96, 10, 36, 14);
        SettingsViews.Text al = new SettingsViews.Text(c, AppText.t("app_layout"), Ui.MEDIUM, 34, Ui.TXT, 1728, 34 * 1.2f, 1).heading();
        page.add(al, 96, 1728, al.height(), 40);
        SettingsViews.Block lb = null;
        for (int i = 0; i < 2; i++) {
            final String id = Appearance.LAYOUTS[i];
            LayoutChoice l = new LayoutChoice(c, id, AppText.t(i == 0 ? "layout_club" : "layout_matchday"),
                AppText.t(i == 0 ? "layout_club_hint" : "layout_matchday_hint"));
            l.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseLayout(id); } });
            l.setTag("layout:" + id);
            layouts[i] = l;
            float x = 96 + i * (852 + 24);
            if (lb == null) lb = page.add(l, x, 852, 170, 18);
            else page.beside(l, lb, x, 852, 170, 0);
        }
        scores = new SettingsViews.OptionRow(c, "scores", AppText.t("show_scores"), AppText.t(rows ? "show_scores_hint" : "rows_unsupported"), 1728);
        scores.setTag("scores");
        if (rows) scores.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleScores(); } });
        scoresBlock = page.add(scores, 96, 1728, 110, 24);
        showScores();
        page.relayout();
        SettingsViews.Line styles = new SettingsViews.Line(null, Arrays.asList(previews));
        styles.kept = true;  // Up from the layouts or the switch: back to the style last looked at, not the nearest
        page.lines.add(styles);
        page.lines.add(new SettingsViews.Line(null, Arrays.asList(layouts)));
        page.lines.add(new SettingsViews.Line(null, Arrays.asList(scores)));
        marks(style, Appearance.layout(c));
        render();
    }

    /** The preview of the chosen style takes the focus when the page opens. */
    View launchFocus() {
        String style = Appearance.cardStyle(c);
        for (Preview p : previews) if (p.style.equals(style)) return p;
        return previews[0];
    }

    private void marks(String style, String layout) {
        for (int i = 0; i < N; i++) {
            boolean on = Appearance.STYLES[i].equals(style);
            captions[i].selected = on;
            captions[i].invalidate();
            previews[i].setContentDescription(captions[i].name + (i == 0 ? ", " + captions[i].note : "") + (on ? ", " + AppText.t("selected_style") : ""));
        }
        for (LayoutChoice l : layouts) {
            l.selected = l.id.equals(layout);
            l.setContentDescription(l.title + ". " + l.desc + (l.selected ? ", " + AppText.t("selected_style") : ""));
            l.invalidate();
        }
    }

    private void chooseStyle(String id) {
        if (id.equals(Appearance.cardStyle(c))) return;
        Appearance.setCardStyle(c, id);
        marks(id, Appearance.layout(c));
        host.republish();
    }

    private void chooseLayout(String id) {
        if (id.equals(Appearance.layout(c))) return;
        Appearance.setLayout(c, id);  // the home screen re-lays itself out when it shows again
        marks(Appearance.cardStyle(c), id);
    }

    private void toggleScores() {
        Appearance.setShowScores(c, !Appearance.showScores(c));
        showScores();
        host.republish();
        if (previewHasScore) render();  // a live or finished match's previews show or hide its score
    }

    /** The switch's value (none where it can't work); the row grows with a wrapped hint. */
    private void showScores() {
        boolean on = Appearance.showScores(c);
        boolean grew = rows ? scores.value(I18n.t(on ? "yes" : "no"), true, on) : scores.value("", false, false);
        if (grew || scoresBlock.h != scores.height()) {
            scoresBlock.h = scores.height();
            page.relayout();
        }
    }

    // ---------- the previews ----------
    /** The previews' match has a score the cards draw (live, a result...): the scores switch changes them. */
    private volatile boolean previewHasScore;

    /** Whether the "Show scores" setting changes this match's card (one rule, CardModel's). */
    static boolean showsScore(Match m) { return m != null && CardModel.showsScore(m); }

    /**
     * The previews last shown, one per style (in Appearance.STYLES order), and the card each was drawn for: the page
     * opens with them at once and draws again only a style whose card has changed since (another match, a new state
     * or time word, the scores setting, the language, the screen's size). UI thread only.
     */
    private static final String[] shownKeys = new String[N];
    private static final Bitmap[] shownBitmaps = new Bitmap[N];
    private boolean released;
    private ValueAnimator pulse;

    /** What a preview shows: the card's identity (MatchdayCards.key: style, language, match, words, images), the
     *  scores setting and the bitmap's width. */
    static String previewKey(String card, boolean show, int width) { return card + "|" + show + "|" + width; }

    /** The order the previews are drawn in: the focused (or chosen) style first, then the nearest ones. */
    static int[] order(int first) {
        int[] out = new int[N];
        int n = 0;
        if (first < 0 || first > N - 1) first = 0;
        out[n++] = first;
        for (int d = 1; d < N; d++) {
            if (first - d >= 0) out[n++] = first - d;
            if (first + d <= N - 1) out[n++] = first + d;
        }
        return out;
    }

    /**
     * The preview the viewer is looking at, read by the drawing thread once it has the match: the focused one (kept
     * by the previews' focus), else the chosen style's.
     */
    private volatile int looked = -1;

    private int lookedAt() {
        int f = looked;
        if (f >= 0 && f < N && previews[f].isFocused()) return f;
        return Math.max(0, Arrays.asList(Appearance.STYLES).indexOf(Appearance.cardStyle(c)));
    }

    /**
     * Shows the previews last drawn at once, then checks them in the background against the viewer's next match (a
     * sample when there is none) and draws again, the looked-at style first, only those whose card has changed. A
     * slot with nothing yet pulses as a skeleton (SPEC-APP 1.5).
     */
    void render() {
        final int gen = ++generation;
        final Context app = c.getApplicationContext();
        final float s = Ui.scale(c);
        final boolean show = !rows || Appearance.showScores(c);
        for (int i = 0; i < N; i++) if (previews[i].bitmap == null && shownBitmaps[i] != null) previews[i].set(shownBitmaps[i], false);
        pulse();
        final String[] had = shownKeys.clone();
        final long t0 = System.nanoTime();
        looked = lookedAt();
        // at once, not after the UI thread is free: a Settings re-created after the process died is busy for most of
        // a second putting its pages back, and the match takes as long again to read in a new process
        try { work.execute(drawing(gen, app, s, show, had, t0)); } catch (Exception ignored) { }  // Settings is closing
    }

    /**
     * The background half of render(): the match, then each preview whose card has changed, the looked-at one first
     * (the focus as it is once the match is read: a re-created page has put it back by then).
     */
    private Runnable drawing(final int gen, final Context app, final float s, final boolean show, final String[] had, final long t0) {
        return new Runnable() { public void run() {
            // what the viewer is looking at, not background work: just under the UI thread, ahead of the crests the
            // Settings page behind is still decoding at background priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT + android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE);
            Pick pick;
            try { pick = pick(app); } catch (Throwable t) { pick = null; }
            if (pick == null) return;
            long now = System.currentTimeMillis();
            final EventCardModel model = pick.event == null ? null : eventModel(app, pick.event, show, now);
            previewHasScore = model != null ? eventShowsScore(model) : showsScore(pick.match);
            final boolean sample = pick.sample, event = model != null;
            ui.post(new Runnable() { public void run() {
                if (host.gone() || gen != generation) return;
                styleHint.set(event ? I18n.t("card_style_hint_event") : AppText.t(sample ? "card_style_hint_sample" : "card_style_hint"));
                if (hintBlock.h != styleHint.height()) { hintBlock.h = styleHint.height(); page.relayout(); }
            }});
            final int[] order = order(looked);
            if (model != null) { drawEvents(gen, app, s, show, had, t0, model, order); return; }
            boolean stale = !pick.sample && Store.stale(app, pick.club.id, pick.match.team);
            // drawn at the focused size (x 1.04), so the scaled preview stays sharp
            int tw = Math.round(PW * Ui.SCALE_TILE * s);
            for (final int k : order) {
                if (gen != generation) return;
                String style = Appearance.STYLES[k];
                final String key = previewKey(MatchdayCards.key(app, style, pick.club, pick.match, stale, now), show, tw);
                if (key.equals(had[k])) continue;  // it already shows this card
                final Bitmap small;
                try { small = draw(app, pick.club, pick.match, stale, style, show, now, tw); } catch (Throwable t) { continue; }
                if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "appearance preview " + style + " drawn " + (System.nanoTime() - t0) / 1000000 + " ms after opening");
                ui.post(new Runnable() { public void run() {
                    if (gen != generation && !released) return;  // drawn again for newer settings meanwhile
                    shownKeys[k] = key;  // kept for the next time the page opens, even if it closed meanwhile
                    shownBitmaps[k] = small;
                    if (released || host.gone()) return;
                    previews[k].set(small, true);
                    pulse();
                }});
            }
        }};
    }

    /** A preview at its slot's width: the full card in a style, made smaller in halves (sharp, no shimmer). */
    static Bitmap draw(Context app, Teams.Club club, Match m, boolean stale, String style, boolean show, long now, int tw) {
        return smaller(CardStyles.draw(app, club, m, stale, style, show, now), tw);
    }

    private static Bitmap smaller(Bitmap big, int tw) {
        Bitmap half = Cards.smooth(big, tw);
        Bitmap small = Bitmap.createScaledBitmap(half, tw, Math.round(tw * 540f / 960f), true);
        if (half != big && half != small) half.recycle();
        if (big != small) big.recycle();
        return small;
    }

    // ---------- the previews of an event (no club match: the next Formula 1 or UFC event) ----------
    /** The event's card as the home-screen row draws it (EventCardModel), the scores as the switch says. */
    static EventCardModel eventModel(Context app, EventCards.Pick p, boolean show, long now) {
        List<SportEvent> one = new java.util.ArrayList<>();
        one.add(p.e);
        return EventCardModel.of(p.e, p.sessionId, p.last, Sports.prefs(app), show, SportEngine.stale(app, p.e.sport), now,
            EventCards.rosterFrom(app, one));
    }

    /** The scores switch changes this event's card: live or finished (a podium, a method). */
    static boolean eventShowsScore(EventCardModel m) {
        return m.state == CardModel.State.LIVE || m.state == CardModel.State.RESULT;
    }

    /** What an event preview shows, as previewKey's card part: style, language, the event and the card's words. */
    static String eventKey(String style, EventCardModel m) {
        return "event|" + style + "|" + I18n.lang() + "|" + m.event.key() + "|" + (m.session == null ? "" : m.session.id) + "|"
            + m.event.signature() + "|" + m.state + "|" + m.stateLine + "|" + m.dateLine + "|" + m.time + "|" + m.clock + "|" + m.stale;
    }

    /** As drawing(), for an event: each style whose card changed, the looked-at one first. */
    private void drawEvents(int gen, Context app, float s, boolean show, String[] had, long t0, EventCardModel model, int[] order) {
        int tw = Math.round(PW * Ui.SCALE_TILE * s);
        for (final int k : order) {
            if (gen != generation) return;
            String style = Appearance.STYLES[k];
            final String key = previewKey(eventKey(style, model), show, tw);
            if (key.equals(had[k])) continue;
            final Bitmap small;
            try { small = smaller(EventStyles.draw(app, model, style), tw); } catch (Throwable t) { continue; }
            if (BuildConfig.DEBUG) android.util.Log.i("Fanela", "appearance preview " + style + " (event) drawn " + (System.nanoTime() - t0) / 1000000 + " ms after opening");
            final int g = gen;
            ui.post(new Runnable() { public void run() {
                if (g != generation && !released) return;
                shownKeys[k] = key;
                shownBitmaps[k] = small;
                if (released || host.gone()) return;
                previews[k].set(small, true);
                pulse();
            }});
        }
    }

    /**
     * The followed sports' next card when no club has a match to show: a live one first, else the soonest
     * (EventCards.pickNow, the home-screen row's own pick); null when no sport is followed or none has an event.
     */
    static EventCards.Pick nextEvent(Context c, long now) {
        if (!BuildConfig.SPORTS) return null;
        List<SportEvent> all = SportEngine.events(c, now);
        if (all.isEmpty()) return null;
        SportPrefs prefs = Sports.prefs(c);
        EventCards.Pick best = null;
        long bestAt = Long.MAX_VALUE;
        boolean bestLive = false;
        for (String sport : Sports.followed(c)) {
            EventCards.Pick p = EventCards.pickNow(all, sport, prefs, now);
            if (p == null || p.e == null) continue;
            SportEvent.Session ses = p.e.session(p.sessionId);
            boolean live = "in".equals(p.e.state) || ses != null && "in".equals(ses.state);
            long at = ses != null ? ses.start : p.e.start;
            if (best == null || live && !bestLive || live == bestLive && at < bestAt) { best = p; bestAt = at; bestLive = live; }
        }
        return best;
    }

    /** Empty slots pulse (.55 -> 1 -> .55, 1.2 s, as the home screen's skeletons) until every preview is drawn. */
    private void pulse() {
        boolean empty = false;
        for (Preview p : previews) empty |= p.bitmap == null;
        if (!empty || released) {
            if (pulse != null) { pulse.cancel(); pulse = null; }
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
                for (Preview p : previews) if (p.bitmap == null) { p.pulse = v; p.invalidate(); }
            }
        });
        pulse.start();
    }

    static final class Pick {
        Teams.Club club;
        Match match;
        boolean sample;
        /** No club match, a sport followed: its next event (club and match null). */
        EventCards.Pick event;
    }

    /**
     * The match the previews show: the favourite's live or next match (either sport, the sooner), else another
     * followed club's, else the next Formula 1 or UFC event when one is followed, else a sample: the favourite (or
     * Olympiacos) at home tomorrow at 21:15 against a rival.
     */
    static Pick pick(Context c) {
        I18n.load(c);
        Where.load(c);
        long now = System.currentTimeMillis();
        List<Match> all = Store.load(c);
        Engine.timeStates(all, now);
        List<String> order = new ArrayList<>();
        String fav = Teams.favourite(c);
        if (fav != null) order.add(fav);
        for (String id : Teams.followed(c)) if (!order.contains(id)) order.add(id);
        boolean basket = Teams.basketball(c);
        for (String id : order) {
            Teams.Club k = Teams.find(c, id);  // as the home screen and the refresh find it
            if (k == null) continue;
            Match best = null;
            for (String team : k.teams(basket)) {
                Match m = Cards.pickNow(all, id, team, now);
                if (m == null) continue;
                if (best == null || Cards.live(m) && !Cards.live(best) || !Cards.live(best) && m.start < best.start) best = m;
            }
            if (best != null) {
                Pick p = new Pick();
                p.club = k;
                p.match = best;
                return p;
            }
        }
        EventCards.Pick ev = nextEvent(c, now);
        if (ev != null) {
            Pick p = new Pick();
            p.event = ev;
            return p;
        }
        return sample(c, fav);
    }

    static Pick sample(Context c, String fav) {
        Teams.Club k = fav == null ? null : Teams.find(c, fav);
        if (k == null) k = Teams.get(c, "435");
        if (k == null) {
            List<Teams.Club> gr = Teams.greek(c);
            if (gr == null || gr.isEmpty()) return null;
            k = gr.get(0);
        }
        List<Teams.Club> pool = Teams.sorted(c, k.league);
        if (pool == null || pool.size() < 2) pool = Teams.greek(c);
        Teams.Club opp = null;
        if (pool != null) {
            for (Teams.Club o : pool) if (!o.id.equals(k.id) && ("Panathinaikos".equals(o.name) || "Olympiacos".equals(o.name))) { opp = o; break; }
            if (opp == null) for (Teams.Club o : pool) if (!o.id.equals(k.id)) { opp = o; break; }
        }
        Match m = new Match();
        m.club = k.id;
        m.team = k.basketOnly() ? "BC" : "FC";
        m.source = "sample";
        m.key = k.id + "|sample";
        m.comp = k.league;
        m.league = k.league;
        m.home = k.name;
        m.away = opp == null ? "Panathinaikos" : opp.name;
        m.clubIsHome = true;
        m.homeCrest = k.crest;
        m.awayCrest = opp == null ? null : opp.crest;
        m.state = "pre";
        m.detail = "";
        m.listedBy = "";
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 21); cal.set(Calendar.MINUTE, 15); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        m.start = cal.getTimeInMillis();
        Pick p = new Pick();
        p.club = k;
        p.match = m;
        p.sample = true;
        return p;
    }

    void release() {
        generation++;
        released = true;
        if (pulse != null) { pulse.cancel(); pulse = null; }
        page.release();
    }

    // ---------- views ----------
    /**
     * A preview slot: the card bitmap with the slot's 14 px corners, a pulsing skeleton block until it is drawn;
     * focused: ring + 1.04 (the FocusLayer's ring).
     */
    static final class Preview extends AppViews.Cell {
        final String style;
        Bitmap bitmap;
        float pulse = 1;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private float fade = 1;

        Preview(Context c, String style, String name) { super(c); this.style = style; setContentDescription(name); }

        /** Shows a card: the first one fades in over the skeleton (animate), one kept from before is there at once. */
        void set(Bitmap b, boolean animate) {
            boolean first = bitmap == null;
            bitmap = b;
            paint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            if (first && animate) {  // fades in over the skeleton (160 ms, alpha only)
                ValueAnimator a = ValueAnimator.ofFloat(0, 1);
                a.setDuration(Ui.WATCH_MS);
                a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator x) { fade = (float) x.getAnimatedValue(); invalidate(); }
                });
                a.start();
            }
            invalidate();
        }

        @Override float radius() { return 14 * getScaleX(); }
        @Override RectF shape() { return new RectF(0, 0, w() * getScaleX(), h() * getScaleY()); }

        @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            SettingsViews.grow(this, gain, Ui.SCALE_TILE);
        }

        @Override void paint(Canvas cv) {
            RectF r = new RectF(0, 0, PW, PH);
            if (bitmap == null || fade < 1) AppKit.skeleton(cv, 0, 0, PW, PH, 14, pulse);
            if (bitmap == null) return;
            Matrix m = new Matrix();
            m.setScale(PW / bitmap.getWidth(), PH / bitmap.getHeight());
            paint.getShader().setLocalMatrix(m);
            paint.setAlpha(Math.round(255 * fade));
            cv.drawRoundRect(r, 14, 14, paint);
        }
    }

    /**
     * Under a preview: the style's name (28 Medium), "Default" (22 at 60 %), the check (36) on the chosen one. Only
     * the chosen one keeps room for the check; there "Default" may go down to 20 rather than be cut ("Υπογραφή
     * Προεπιλογή" and the check fill a 326 px slot).
     */
    static final class Caption extends AppViews.Spec {
        final String name, note;
        boolean selected;
        /** The check's room at the right (36 and a gap of 8), and the gap between the name and "Default". */
        static final float CHECK = 44, GAP = 8;
        Caption(Context c, String name, String note) { super(c); this.name = name; this.note = note; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) {
            float cy = 18, right = PW - 6 - (selected ? CHECK : 0);
            Paint t = Ui.paint(Ui.MEDIUM, 28, Ui.TXT);
            String n = Cards.ellipsize(t, name, right - 6);
            cv.drawText(n, 6, Ui.baseline(Ui.MEDIUM, 28, cy), t);
            if (!note.isEmpty()) {
                Paint d = Ui.paint(Ui.REGULAR, 22, Ui.alpha(Ui.TXT, 0.6f));
                float x = 6 + t.measureText(n) + GAP, room = right - x, w = d.measureText(note);
                if (w > room && room > 0) d.setTextSize(Math.max(20, 22 * room / w));
                cv.drawText(Cards.ellipsize(d, note, room), x, Ui.baseline(Ui.MEDIUM, 28, cy), d);
            }
            if (selected) SettingsViews.checkBadge(cv, PW - 6 - 18, cy, 36);
        }
    }

    /**
     * An app layout choice (852 x 170, radius 20, surface1): its mini wireframe (224 x 126) in the favourite club's
     * colours, the title (32 Medium) and a line of description (26 at 62 %), the check or an empty ring (44).
     */
    static final class LayoutChoice extends AppViews.Cell {
        final String id, title, desc;
        boolean selected;
        private final List<String> lines;

        LayoutChoice(Context c, String id, String title, String desc) {
            super(c);
            this.id = id; this.title = title; this.desc = desc;
            lines = SettingsViews.lines(desc, SettingsViews.textPaint(Ui.REGULAR, 26, 0), 852 - 280 - 28 - 44 - 24);
        }

        @Override float radius() { return 20; }

        @Override void paint(Canvas cv) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(SettingsViews.rowFill(focus));
            cv.drawRoundRect(new RectF(0, 0, 852, 170), 20, 20, bg);
            mini(cv, 28, 22, Appearance.CLUB.equals(id));
            float lt = Ui.lineHeight(Ui.MEDIUM, 32), ld = 26 * 1.3f;
            int n = Math.min(3, lines.size());
            float block = lt + 6 + n * ld, top = (170 - block) / 2;
            Paint t = Ui.paint(Ui.MEDIUM, 32, Ui.TXT);
            cv.drawText(title, 280, Ui.baselineTop(Ui.MEDIUM, 32, top, 0), t);
            Paint d = Ui.paint(Ui.REGULAR, 26, Ui.alpha(Ui.TXT, 0.62f));
            for (int i = 0; i < n; i++) cv.drawText(lines.get(i), 280, Ui.baselineTop(Ui.REGULAR, 26, top + lt + 6 + i * ld, ld), d);
            if (selected) SettingsViews.checkBadge(cv, 852 - 28 - 22, 85, 44);
            else SettingsViews.emptyRing(cv, 852 - 28 - 22, 85, 44);
        }

        /** The layout in miniature: Club = rail + hero + rows; Matchday = stage + a row of cards. */
        static void mini(Canvas cv, float x0, float y0, boolean club) {
            cv.save();
            cv.translate(x0, y0);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new RectF(0, 0, 224, 126), 10, 10, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            int kit = SettingsViews.CLUB;
            FieldColors f = FieldColors.of(kit, 0xFFFFFFFF);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            double a = Math.toRadians(club ? 160 : 100);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a), len = Math.abs(224 * dx) + Math.abs(126 * dy);
            float cx = 112, cy = 63;
            p.setShader(new LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
                new int[]{f.field, club ? f.field2 : ClubTokens.mix(f.field2, Ui.APP_BG, 0.5f), Ui.APP_BG}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, 224, 126, p);
            p.setShader(null);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(1);
            ring.setColor(0xFFF4F0EA);
            if (club) {
                p.setColor(f.primary);
                cv.drawCircle(21, 25, 11, p);
                cv.drawCircle(21, 25, 11.5f, ring);
                p.setColor(0xFFDDDDDD);
                cv.drawCircle(18, 52, 8, p);
                RectF hero = new RectF(40, 12, 210, 64);
                p.setColor(0x14FFFFFF);
                cv.drawRoundRect(hero, 6, 6, p);
                ring.setStrokeWidth(2);
                ring.setColor(Ui.TXT);
                cv.drawRoundRect(hero, 6, 6, ring);
                p.setColor(0xBFFFFFFF);
                cv.drawRoundRect(new RectF(52, 26, 112, 40), 3, 3, p);
                p.setColor(Ui.TXT);
                cv.drawRoundRect(new RectF(160, 20, 200, 46), 3, 3, p);
                p.setColor(0x38FFFFFF);
                for (int i = 0; i < 4; i++) cv.drawRoundRect(new RectF(40, 74 + i * 14, 210, 82 + i * 14), 2, 2, p);
            } else {
                p.setColor(f.primary);
                cv.drawCircle(20, 22, 10, p);
                cv.drawCircle(20, 22, 10.5f, ring);
                p.setColor(0xBFFFFFFF);
                cv.drawRoundRect(new RectF(36, 14, 106, 28), 3, 3, p);
                p.setColor(0xFFDDDDDD);
                cv.drawCircle(20, 48, 10, p);
                p.setColor(0x8CFFFFFF);
                cv.drawRoundRect(new RectF(36, 40, 86, 54), 3, 3, p);
                p.setColor(Ui.TXT);
                cv.drawRoundRect(new RectF(224 - 12 - 54, 12, 224 - 12, 48), 3, 3, p);
                for (int i = 0; i < 4; i++) {
                    RectF card = new RectF(10 + i * 54, 76, 58 + i * 54, 106);
                    p.setColor(i == 0 ? 0xE6FFFFFF : 0x38FFFFFF);
                    cv.drawRoundRect(card, 4, 4, p);
                    if (i == 0) { ring.setStrokeWidth(2); ring.setColor(Ui.TXT); cv.drawRoundRect(card, 4, 4, ring); }
                    p.setColor(0x2EFFFFFF);
                    cv.drawRoundRect(new RectF(10 + i * 54, 114, 58 + i * 54, 130), 4, 4, p);
                }
            }
            cv.restore();
        }
    }
}
