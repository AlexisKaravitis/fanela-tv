package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * The match screen (SPEC-APP 3.5 Club, 4.4 Matchday), one level under the home screen: the match in the layout's own
 * environment (the club's kit field, or the full-height stage), where to watch it in the large cell with why that is
 * the channel and whether its app is on the TV, the one button (open the channel's app, get it, or close), the club's
 * last five results in the competition and the other leg of the tie. The button is the only focusable; Back closes.
 *
 * prepare() resolves everything (crests, the logo, the background) on a background thread; the views only draw it.
 * While the screen is open, new data is prepared the same way and swapped in place (update): a live score and clock,
 * the status slot, the button once the channel's app is installed.
 */
final class MatchScreen {
    private MatchScreen() { }

    /** What the screen shows, resolved off the UI thread. */
    static final class Data {
        String layout;
        Match m;
        Teams.Club club;
        FieldColors colors;
        boolean both, installed;
        long now;
        HomeModel.Status status = HomeModel.Status.CLOCK;
        String statusText = "";
        Bitmap env;
        AppArt.Decal home, away;
        AppKit.Watch watch;
        MatchFacts.Recent recent;
        final List<AppArt.Decal> form = new ArrayList<>();
        Match leg;
        MatchFacts.Action action = MatchFacts.Action.CLOSE;
        String button = "", channelLine = "";
        final List<String> why = new ArrayList<>();
        boolean matchday() { return Appearance.MATCHDAY.equals(layout); }
    }

    /** Background thread: the facts, the images and the words for a match. */
    static Data prepare(Context c, String layout, Match m, Teams.Club k, HomeModel h) {
        Ui.fonts(c);
        Data d = new Data();
        d.layout = layout;
        d.m = m;
        d.club = k;
        d.colors = ClubLayout.colors(k);
        HomeModel.ClubData cd = h == null ? null : h.club(k.id);
        d.both = cd != null ? cd.bothSports : k.teams(Teams.basketball(c)).length > 1;
        d.now = h != null ? h.now : System.currentTimeMillis();
        if (h != null) { d.status = h.status; d.statusText = h.statusText; }
        List<Match> all = h != null ? h.all : new ArrayList<Match>();
        boolean live = "in".equals(m.state);
        if (d.matchday()) {
            d.env = MatchdayStage.get(c, k.id, d.colors, live ? MatchdayStage.Mode.FULL_LIVE : MatchdayStage.Mode.FULL);
            d.home = AppArt.crest(c, m.homeCrest, ClubLayout.crestOf(h, k, m, true), m.home, 150, false);
            d.away = AppArt.crest(c, m.awayCrest, ClubLayout.crestOf(h, k, m, false), m.away, 150, false);
        } else {
            boolean stripes = ClubTokens.of(k.id, d.colors.primary, d.colors.primary, null).pattern == ClubTokens.Pattern.STRIPES;
            d.env = KitField.get(c, k.id, d.colors, k.crest, stripes, live ? KitField.Mode.LIVE : KitField.Mode.KIT);
            // the cached crest the rail shows when the match's file isn't on the TV (P2f)
            d.home = AppArt.crest(c, m.homeCrest, ClubLayout.crestOf(h, k, m, true), m.home, 250, false);
            d.away = AppArt.crest(c, m.awayCrest, ClubLayout.crestOf(h, k, m, false), m.away, 250, false);
        }
        if (MatchFacts.watchable(m)) d.watch = AppKit.watch(c, m);
        d.installed = m.channelApp != null && HomeRows.installed(c, m.channelApp);
        d.action = MatchFacts.action(m, d.installed);
        d.button = MatchFacts.buttonLabel(d.action, m);
        d.why.addAll(MatchFacts.why(m, d.installed));
        d.channelLine = MatchFacts.channelLine(m);
        d.recent = MatchFacts.recent(all, m);
        for (MatchFacts.Form f : d.recent.cells) {
            Match x = f.m;
            // trimmed to the shield, so it fills the cell's 52 px (Matchday 44) box rather than a padded file's 40
            d.form.add(AppArt.crest(c, x.clubHome() ? x.awayCrest : x.homeCrest, x.opponentRaw(), d.matchday() ? 44 : 52, true, true));
        }
        d.leg = MatchFacts.otherLeg(all, m);
        AppArt.shirt(c, Ui.TXT);
        AppArt.shirt(c, FieldColors.INK_DARK);
        return d;
    }

    /** The Club screen's crests' centre line (spec px). */
    static final float CREST_CY = 178.55f + 125;

    /** Whatever the button does: the host opens the app or the store, or closes the screen. */
    interface Actions { void act(MatchFacts.Action a, Match m); }

    /** The screen's views: the background, the drawn screen, the LIVE dot (Matchday) and the focused button. */
    static FrameLayout build(Context c, Data d, final Actions actions) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(Ui.APP_BG);
        f.setClickable(true);
        f.setClipChildren(false);
        final Body body = new Body(c, d);
        body.setTag("body");
        f.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        AppViews.LiveDot dot = new AppViews.LiveDot(c, 0.9f);
        dot.setTag("dot");
        float dr = MatchdayViews.StageInfo.DOT_R;
        f.addView(dot, AppViews.at(c, LIVE_X + MatchdayViews.StageInfo.DOT_X - dr, LIVE_CY - dr, 2 * dr, 2 * dr));
        Button b = new Button(c, d.button);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { actions.act(body.d.action, body.d.m); } });
        b.setTag("button");
        f.addView(b);
        show(f, d);
        return f;
    }

    /** New data for an open screen (a live score, the app installed): drawn in place, the button keeps the focus. */
    static void update(FrameLayout screen, Data d) {
        Body body = (Body) screen.findViewWithTag("body");
        if (body == null) return;
        body.d = d;
        body.invalidate();
        show(screen, d);
    }

    /** The parts that follow the data: the button's words and place, the LIVE dot, what a screen reader says. */
    private static void show(FrameLayout screen, Data d) {
        Context c = screen.getContext();
        Button b = (Button) screen.findViewWithTag("button");
        b.label = d.button;
        b.setContentDescription(d.button);
        float bw = AppViews.Button.width(d.button) + 24;
        if (d.matchday()) b.setLayoutParams(AppViews.at(c, 768 - 12, 692 - 12, 420 + 24, 88));  // the column's width (stretched)
        else b.setLayoutParams(AppViews.at(c, 1824 - bw + 12, 656 - 12, bw, 88));
        b.invalidate();
        screen.findViewWithTag("dot").setVisibility(d.matchday() && "in".equals(d.m.state) ? View.VISIBLE : View.GONE);
        screen.setContentDescription(describe(d));
    }

    /** Where the Matchday screen's LIVE pill sits (its left edge and centre line, spec px). */
    static final float LIVE_X = 96, LIVE_CY = 153.74f;

    /** The focusable button inside a screen built by build(). */
    static View button(FrameLayout screen) { return screen.findViewWithTag("button"); }

    /** What a screen reader says on entry. */
    static String describe(Data d) {
        StringBuilder b = new StringBuilder(HomeModel.speak(d.m, HomeModel.kind(d.m), d.now));
        for (String s : d.why) b.append(". ").append(s);
        if (!d.recent.cells.isEmpty()) b.append(". ").append(recentLabel(d)).append(": ").append(MatchFacts.speak(d.recent.cells));
        if (d.leg != null) b.append(". ").append(MatchFacts.legLabel(d.leg, d.m)).append(": ").append(MatchFacts.legLine(d.leg));
        return b.toString();
    }

    static String recentLabel(Data d) {
        String comp = I18n.comp(d.m.comp);
        if (d.matchday()) return d.recent.sameCompetition ? AppText.t("last_five") + " · " + comp : AppText.t("last_five");
        return d.recent.sameCompetition ? AppText.t("recent") + " · " + comp : AppText.t("recent");
    }

    /** "EuroLeague · Basketball · Home". */
    static List<String> eyebrow(Data d) {
        List<String> out = new ArrayList<>();
        out.add(I18n.comp(d.m.comp));
        if (d.both) out.add(HomeModel.sport(d.m));
        out.add(AppText.t(d.m.clubHome() ? "home_cap" : "away_cap"));
        return out;
    }

    /**
     * The screen's one button (64 px pill, 30 Medium, white 14 %; focused it inverts with a 6 px halo), as wide as its
     * view less the halo's room: the Matchday sheet stretches it over its column, the Club screen sizes the view to it.
     */
    static final class Button extends AppViews.Cell {
        String label;
        Button(Context c, String label) { super(c); this.label = label; setContentDescription(label); }
        @Override boolean ownFocus() { return true; }
        @Override float radius() { return 32; }
        @Override RectF shape() { return new RectF(12, (h() - 64) / 2, w() - 12, (h() + 64) / 2); }
        @Override void paint(Canvas cv) {
            RectF box = shape();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (focus > 0) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 0.22f * focus));
                RectF halo = new RectF(box);
                halo.inset(-6, -6);
                cv.drawRoundRect(halo, 38, 38, p);
            }
            p.setColor(AppViews.blend(Ui.alpha(0xFFFFFFFF, 0.14f), Ui.TXT, focus));
            cv.drawRoundRect(box, 32, 32, p);
            Paint t = Ui.paint(Ui.MEDIUM, 30, AppViews.blend(Ui.TXT, 0xFF111111, focus));
            AppKit.centre(cv, t, AppKit.cut(t, label, box.width() - 48), box.centerX(), Ui.baseline(Ui.MEDIUM, 30, box.centerY()));
        }
    }

    // ---------- drawing ----------
    /** The whole screen but the button, in spec px. */
    static final class Body extends AppViews.Spec {
        Data d;
        private final Rect dst = new Rect();
        private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        Body(Context c, Data d) { super(c); this.d = d; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        @Override void paint(Canvas cv) {
            if (d.env != null) {
                float h = d.matchday() ? Ui.H : KitField.HEIGHT;
                cv.drawBitmap(d.env, null, new RectF(0, 0, Ui.W, h), bp);
            }
            if (d.matchday()) matchday(cv); else club(cv);
        }

        // ---------- Club (3.5) ----------
        private void club(Canvas cv) {
            FieldColors fc = d.colors;
            boolean live = "in".equals(d.m.state);
            int ink = Ui.TXT;  // every field is dark (white kits too: FieldColors)
            // the masthead: the brand with the club's name after it, the clock at the right
            int mInk = live ? Ui.TXT : fc.inkAt(78);
            Bitmap shirt = AppArt.shirt(getContext(), mInk == Ui.TXT ? Ui.TXT : FieldColors.INK_DARK);
            Paint sp = new Paint(AppArt.FILTER);
            sp.setAlpha(Math.round(255 * 0.85f));
            cv.drawBitmap(shirt, null, new RectF(96, 62, 128, 94), sp);
            Paint br = Ui.paint(Ui.MEDIUM, 26, Ui.alpha(mInk, 0.85f));
            float bl = Ui.baseline(Ui.MEDIUM, 26, 78);
            cv.drawText("Fanela", 140, bl, br);
            Paint sub = Ui.paint(Ui.MEDIUM, 26, Ui.alpha(mInk, 0.85f * 0.6f));
            cv.drawText("· " + d.club.label(), 140 + br.measureText("Fanela") + 18, bl, sub);
            status(cv, 1824, mInk);
            // the hero: eyebrow, the two crests (250) with the VS mark, the fixture (RC 110, fit 1140, the same mark)
            Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, live ? Ui.alpha(ink, 0.8f) : fc.eyebrow, Ui.TRACK_EYEBROW);
            cv.drawText(Cards.caps(CardKit.join(eyebrow(d), " · ")), 96 - AppKit.lead(eb), 149.1f, eb);
            float cy = CREST_CY;
            if (d.home != null) d.home.draw(cv, 96 + 125, cy, null);
            float vx = 96 + 250 + AppKit.VS_CREST_AIR;
            float ax = vx + CardKit.vs(cv, CardKit.VS, vx, cy, AppKit.vsForCrests(250), AppKit.vsInk(ink), false) + AppKit.VS_CREST_AIR;
            if (d.away != null) d.away.draw(cv, ax + 125, cy, null);
            Paint tp = AppKit.text(Ui.COND_BOLD, 110, ink);
            tp.setLetterSpacing(-0.005f);
            AppKit.Title t = AppKit.title(AppKit.measure(tp), AppKit.forms(d.m.home), AppKit.forms(d.m.away), 1140, 110, 80);  // P2e
            float sz = t.size;
            AppKit.drawTitle(cv, tp, t, 96, Ui.baselineTop(Ui.COND_BOLD, sz, 454.55f + (104.5f - 0.95f * sz) / 2, 0.95f * sz), 1140);
            // the when column, right-aligned: relative time RC 58, the time RC 214, the date 32 at 72 %
            Paint rel = AppKit.text(Ui.COND_BOLD, Ui.T_REL, ink);
            Paint big = AppKit.text(Ui.COND_BOLD, 214, ink);
            big.setLetterSpacing(-0.015f);
            Paint date = AppKit.text(Ui.REGULAR, 32, Ui.alpha(ink, Ui.A2));
            float relBl = 185.5f, bigBl = 377.4f, dateBl = 431.5f;
            Match m = d.m;
            if (live || "post".equals(m.state)) {
                String top = live ? MatchdayModel.liveClock(m) : AppKit.outcomeWord(m.result()).isEmpty() ? AppText.t("full_time")
                    : I18n.t(m.result() > 0 ? "win" : m.result() == 0 ? "draw" : "loss");
                if (live) rel.setColor(Ui.LIVE_TEXT);
                AppKit.right(cv, rel, top, 1824, relBl);
                if (m.hasScore()) {
                    float w = AppKit.scoreWidth(m.hs, m.as, 214);
                    AppKit.score(cv, m, 1824 - w, bigBl, 214, ink, 1);
                }
            } else {
                AppKit.right(cv, rel, "off".equals(m.state) ? I18n.detail(m.detail) : AppText.relative(d.now, m.start), 1824, relBl);
                if ("off".equals(m.state)) AppKit.right(cv, big, "—", 1824, bigBl);
                else if (m.tbd) AppKit.right(cv, rel, AppText.t("time_tba"), 1824, 330);
                else time(cv, big, I18n.time(m.start), 1824, bigBl, ink, 214);
            }
            AppKit.right(cv, date, AppText.longDate(m.start), 1824, dateBl);
            // where to watch: the rule, the label, the large cell with the channel line, the reasons
            rule(cv, 612);
            label(cv, MatchFacts.watchable(m) ? AppText.t("where") : AppText.t("full_time"), 96, 665.2f, false);
            float wy = 730.05f;
            if (MatchFacts.watchable(m)) {
                float cw = cell(cv, 96, wy, 1300);
                String line = d.watch != null && d.watch.kind == AppKit.Watch.LIKELY ? (d.watch.app == null ? "" : "· " + d.watch.app) : d.channelLine;
                if (d.watch != null && d.watch.kind == AppKit.Watch.GUIDE) line = "";
                if (!line.isEmpty()) {
                    Paint lp = AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2));
                    cv.drawText(AppKit.cut(lp, line, 1824 - 400 - (96 + cw + 24)), 96 + cw + 24, Ui.baseline(Ui.REGULAR, 30, wy), lp);
                }
                Paint wp = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2));
                float y = 819.2f;
                for (String s : d.why) for (String l : ClubHero.wrap(wp, s, 1340)) { if (y > 880) break; cv.drawText(l, 96, y, wp); y += 39.2f; }
            } else result(cv, 96, wy);
            // recent results and the other leg
            if (d.recent.cells.isEmpty() && d.leg == null) return;
            rule(cv, 900);
            float x = 96;
            if (!d.recent.cells.isEmpty()) {
                label(cv, recentLabel(d), 96, 949.2f, false);
                float ccy = 996.05f;
                // the other leg keeps the room it needs at its smallest size (24; it grows back to 30 in what is left);
                // when the five still don't fit, the oldest give way, never the latest result
                float legW = d.leg == null ? 0 : Math.min(560, AppKit.text(Ui.MEDIUM, 24, 0).measureText(MatchFacts.legLine(d.leg)));
                int n = d.recent.cells.size();
                float[] cellW = new float[n];
                for (int i = 0; i < n; i++) {
                    MatchFacts.Form f = d.recent.cells.get(i);
                    cellW[i] = 52 + 14 + AppKit.scoreWidth(f.m.hs, f.m.as, 34) + 14 + 36;
                }
                for (int i = MatchFacts.firstShown(cellW, 36, 1824 - legW - 48 - x); i < n; i++) {
                    MatchFacts.Form f = d.recent.cells.get(i);
                    AppArt.Decal cr = d.form.get(i);
                    if (cr != null) cr.draw(cv, x + 26, ccy, null);
                    x += 52 + 14;
                    x += AppKit.score(cv, f.m, x, Ui.baseline(Ui.COND_BOLD, 34, ccy), 34, Ui.TXT, 1) + 14;
                    tile(cv, f, x, ccy);
                    x += 36 + 36;
                }
            }
            if (d.leg != null) {
                label(cv, MatchFacts.legLabel(d.leg, d.m), 1824, 949.2f, true);
                Paint lp = AppKit.text(Ui.MEDIUM, 30, Ui.TXT);
                String line = MatchFacts.legLine(d.leg);
                float room = 1824 - Math.max(96, x - 36 + 48);
                AppKit.fit(lp, line, room, 30, 24);
                AppKit.right(cv, lp, AppKit.cut(lp, line, room), 1824, 1001f);
            }
        }

        // ---------- Matchday (4.4) ----------
        private void matchday(Canvas cv) {
            Match m = d.m;
            boolean scored = "in".equals(m.state) || "post".equals(m.state);
            // the masthead (the Settings chip drawn, not focusable here)
            Paint sp = new Paint(AppArt.FILTER);
            sp.setAlpha(Math.round(255 * 0.85f));
            cv.drawBitmap(AppArt.shirt(getContext(), Ui.TXT), null, new RectF(96, 62, 128, 94), sp);
            cv.drawText("Fanela", 140, Ui.baseline(Ui.MEDIUM, 26, 78), Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.85f)));
            String settings = I18n.t("settings");
            float cw = MatchdayViews.Chip.width(settings);
            RectF chip = new RectF(1824 - cw, 55, 1824, 101);
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setStyle(Paint.Style.STROKE);
            cp.setStrokeWidth(2);
            cp.setColor(Ui.alpha(0xFFFFFFFF, 0.36f));
            RectF in = new RectF(chip);
            in.inset(1, 1);
            cv.drawRoundRect(in, 22, 22, cp);
            AppKit.centre(cv, Ui.paint(Ui.MEDIUM, 26, Ui.alpha(Ui.TXT, 0.9f)), settings, chip.centerX(), Ui.baseline(Ui.MEDIUM, 26, 78));
            status(cv, chip.left - 28, Ui.TXT);
            // eyebrow: the competition bold, the sport, home or away
            float x = 96, cy = 147.53f, bl = Ui.baseline(Ui.REGULAR, 28, cy);
            Paint b = AppKit.text(Ui.BOLD, 28, Ui.alpha(Ui.TXT, 0.9f)), reg = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.9f));
            Paint dot = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.9f * 0.45f));
            if ("in".equals(m.state)) {  // live: the pill and the clock lead, as on the stage (its dot is a view: it pulses)
                cy = LIVE_CY;
                bl = Ui.baseline(Ui.REGULAR, 28, cy);
                x += MatchdayViews.StageInfo.livePill(cv, LIVE_X, cy, 0.9f, 0) + 16;
                String clock = MatchdayModel.liveClock(m);
                if (!clock.isEmpty()) {
                    Paint ck = AppKit.text(Ui.COND_BOLD, 36, Ui.alpha(Ui.TXT, 0.9f));
                    cv.drawText(clock, x, Ui.baseline(Ui.COND_BOLD, 36, cy), ck);
                    x += ck.measureText(clock) + 16;
                    cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16;
                }
            }
            String comp = I18n.comp(m.comp);
            cv.drawText(comp, x, bl, b);
            x += b.measureText(comp) + 16;
            if (d.both) {
                cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16;
                AppKit.sport(cv, m.team, x + 15, cy, 30, Ui.alpha(Ui.TXT, 0.81f));
                x += 46;
                cv.drawText(HomeModel.sport(m), x, bl, reg);
                x += reg.measureText(HomeModel.sport(m)) + 16;
            }
            cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16;
            cv.drawText(AppText.t(m.clubHome() ? "home_cap" : "away_cap"), x, bl, reg);
            // the team rows at y 190 (crests 150, names RC 104, fit 900)
            Paint np = AppKit.text(Ui.COND_BOLD, 104, Ui.TXT);
            np.setLetterSpacing(-0.005f);
            String hn = I18n.team(m.home), an = I18n.team(m.away);
            float size = Math.min(AppKit.fit(np, hn, 900, 104, 72), AppKit.fit(np, an, 900, 104, 72));
            int lead = MatchdayModel.leader(m);
            AppKit.vsBetweenRows(cv, 280, (190 + 364) / 2f + 75, size, 1);  // the VS mark between the names, as on the stage
            for (int i = 0; i < 2; i++) {
                float top = i == 0 ? 190 : 364, ccy = top + 75;
                AppArt.Decal cr = i == 0 ? d.home : d.away;
                if (cr != null) cr.draw(cv, 171, ccy, null);
                boolean trailing = scored && lead != 0 && (lead > 0) == (i == 1);
                np.setTextSize(size);
                np.setColor(Ui.alpha(Ui.TXT, trailing ? 0.6f : 1f));
                cv.drawText(AppKit.cut(np, i == 0 ? hn : an, 900), 280, Ui.baselineTop(Ui.COND_BOLD, size, ccy - size / 2, size), np);
                if (scored && m.hasScore()) {
                    Paint sc = AppKit.text(Ui.COND_BOLD, Ui.T_SCORE_L, Ui.alpha(Ui.TXT, trailing ? 0.6f : 1f));
                    AppKit.right(cv, sc, i == 0 ? m.hs : m.as, 1824, Ui.baselineTop(Ui.COND_BOLD, Ui.T_SCORE_L, top, 150));
                }
            }
            if (!scored) {  // the when column at y 140
                Paint big = AppKit.text(Ui.COND_BOLD, Ui.T_DISPLAY_XL, Ui.TXT);
                big.setLetterSpacing(-0.015f);
                float tb = Ui.baselineTop(Ui.COND_BOLD, Ui.T_DISPLAY_XL, 140, 201.6f);
                if ("off".equals(m.state)) AppKit.right(cv, big, "—", 1824, tb);
                else if (m.tbd) AppKit.right(cv, AppKit.text(Ui.COND_BOLD, 96, Ui.TXT), AppText.t("time_tba"), 1824, 290);
                else time(cv, big, I18n.time(m.start), 1824, tb, Ui.TXT, Ui.T_DISPLAY_XL);
                String rel = "off".equals(m.state) ? I18n.detail(m.detail) : AppText.relativeHours(d.now, m.start);
                if (rel.isEmpty()) rel = AppText.relative(d.now, m.start);
                AppKit.right(cv, AppKit.text(Ui.MEDIUM, 38, Ui.TXT), rel, 1824, 392.3f);
                AppKit.right(cv, AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, 0.7f)), AppText.longDate(m.start), 1824, 440.2f);
            }
            // the sheet: where to watch | the button and the other leg | last five
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(Ui.alpha(Ui.S1, 0.92f));
            cv.drawRoundRect(new RectF(96, 612, 1824, 1026), 28, 28, sh);
            float c1 = 144, c2 = 768, c3 = 1236;
            label(cv, MatchFacts.watchable(m) ? AppText.t("where") : AppText.t("full_time"), c1, 675.2f, false);
            if (MatchFacts.watchable(m)) {
                cell(cv, c1, 742.05f, 576);
                List<String> lines = new ArrayList<>();
                Paint first = AppKit.text(Ui.MEDIUM, 28, Ui.alpha(Ui.TXT, Ui.A2));
                Paint wp = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2));
                float y = 831.2f;
                if (!d.channelLine.isEmpty()) { cv.drawText(AppKit.cut(first, d.channelLine, 576), c1, y, first); y += 39.2f; }
                for (String s : d.why) lines.addAll(ClubHero.wrap(wp, s, 576));
                for (String l : lines) { if (y > 1000) break; cv.drawText(l, c1, y, wp); y += 39.2f; }
            } else result(cv, c1, 742.05f);
            if (d.leg != null) {
                label(cv, MatchFacts.legLabel(d.leg, m), c2 + 210, 811.2f, true, true);
                Paint lp = AppKit.text(Ui.MEDIUM, 28, Ui.TXT);
                String line = MatchFacts.legLine(d.leg);
                AppKit.fit(lp, line, 460, 28, 22);  // the column and a little of its gaps, never into the text beside it
                AppKit.centre(cv, lp, AppKit.cut(lp, line, 460), c2 + 210, 855.1f);
            }
            if (!d.recent.cells.isEmpty()) {
                label(cv, recentLabel(d), c3, 675.2f, false);
                Paint nm = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.85f));
                for (int i = 0; i < d.recent.cells.size(); i++) {
                    MatchFacts.Form f = d.recent.cells.get(i);
                    float ccy = 720.05f + i * 58;
                    AppArt.Decal cr = d.form.get(i);
                    if (cr != null) cr.draw(cv, c3 + 22, ccy, null);
                    float xx = c3 + 44 + 16;
                    cv.drawText(AppKit.cut(nm, I18n.team(f.m.opponentRaw()), 200), xx, Ui.baseline(Ui.REGULAR, 28, ccy), nm);
                    xx += 200 + 16;
                    xx += AppKit.score(cv, f.m, xx, Ui.baseline(Ui.COND_BOLD, 34, ccy), 34, Ui.TXT, 1) + 16;
                    tile(cv, f, xx, ccy);
                }
            }
        }

        // ---------- pieces ----------
        /** The status slot: the clock, "Updating…" or the offline banner, right edge at x. */
        private void status(Canvas cv, float right, int ink) {
            if (d.statusText.isEmpty()) return;
            if (d.status == HomeModel.Status.OFFLINE) {
                Paint t = Ui.paint(Ui.REGULAR, 26, Ui.TXT);
                float w = 2 + 16 + 28 + 14 + t.measureText(d.statusText) + 22 + 2;
                RectF box = new RectF(right - w, 54, right, 102);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0x1AFFFFFF);
                cv.drawRoundRect(box, 24, 24, p);
                AppKit.cloudOff(cv, box.left + 32, 78, 28, Ui.alpha(Ui.TXT, 0.8f));
                cv.drawText(d.statusText, box.left + 60, Ui.baseline(Ui.REGULAR, 26, 78), t);
                return;
            }
            AppKit.right(cv, Ui.paint(Ui.REGULAR, 28, Ui.alpha(ink, 0.68f)), d.statusText, right, Ui.baseline(Ui.REGULAR, 28, 78));
        }

        /** A kick-off at a display size; 12-hour clocks put "PM" beside it on the same baseline. */
        private static void time(Canvas cv, Paint big, String t, float right, float bl, int ink, float size) {
            int sp = t.indexOf(' ');
            if (sp > 0) {
                Paint suf = AppKit.text(Ui.COND_BOLD, size * 0.36f, Ui.alpha(ink, 0.75f));
                float sw = AppKit.right(cv, suf, t.substring(sp + 1), right, bl);
                AppKit.right(cv, big, t.substring(0, sp), right - sw - 14, bl);
            } else AppKit.right(cv, big, t, right - big.getLetterSpacing() * size, bl);  // CSS tracks after the last digit too
        }

        private static void rule(Canvas cv, float y) {
            Paint p = new Paint();
            p.setColor(0x29FFFFFF);
            cv.drawRect(96, y, 1824, y + 2, p);
        }

        private static void label(Canvas cv, String s, float x, float bl, boolean right) { label(cv, s, x, bl, right, false); }

        /** A section label: 24 Medium capitals, +14 %, at 62 %. */
        private static void label(Canvas cv, String s, float x, float bl, boolean right, boolean centre) {
            Paint p = AppKit.caps(Ui.MEDIUM, Ui.T_LABEL, Ui.alpha(Ui.TXT, 0.62f), Ui.TRACK_EYEBROW);
            String t = Cards.caps(s);
            if (centre) AppKit.centre(cv, p, t, x, bl);
            else if (right) AppKit.right(cv, p, t, x + AppKit.lead(p), bl);
            else cv.drawText(t, x - AppKit.lead(p), bl, p);
        }

        /** The large where-to-watch cell (84 px); the channel to be announced in words. Returns its width. */
        private float cell(Canvas cv, float x, float cy, float maxW) {
            if (d.watch != null) return AppKit.cell(cv, d.watch, AppKit.Size.LG, x, cy, false, maxW, false, 1);
            Paint p = AppKit.text(Ui.REGULAR, 36, Ui.alpha(Ui.TXT, Ui.A2));
            String s = I18n.t("channel_tba");
            cv.drawText(s, x, Ui.baseline(Ui.REGULAR, 36, cy), p);
            return p.measureText(s);
        }

        /** A match already played: its score (home first) at 64 and the outcome pill, the date under them. */
        private void result(Canvas cv, float x, float cy) {
            Match m = d.m;
            if (m.hasScore()) {
                float w = AppKit.score(cv, m, x, Ui.baseline(Ui.COND_BOLD, 64, cy), 64, Ui.TXT, 1);
                AppKit.pill(cv, m.result(), x + w + 24, cy, false, 1);
            } else if ("off".equals(m.state)) {
                cv.drawText(I18n.detail(m.detail), x, Ui.baseline(Ui.MEDIUM, 36, cy), AppKit.text(Ui.MEDIUM, 36, Ui.TXT));
            }
            Paint p = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2));
            cv.drawText(AppText.longDate(m.start), x, cy + 42 + 47, p);
        }

        /** The W / D / L tile (36 px, radius 8): win green, loss wine, draw white 18 %. */
        private static void tile(Canvas cv, MatchFacts.Form f, float x, float cy) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(f.outcome > 0 ? Ui.WIN : f.outcome < 0 ? Ui.LOSS : 0x2EFFFFFF);
            cv.drawRoundRect(new RectF(x, cy - 18, x + 36, cy + 18), 8, 8, p);
            Paint t = Ui.paint(Ui.BOLD, 22, f.outcome < 0 ? Ui.LOSS_TEXT : 0xFFFFFFFF);
            AppKit.centre(cv, t, f.letter(), x + 18, Ui.baseline(Ui.BOLD, 22, cy));
        }
    }
}
