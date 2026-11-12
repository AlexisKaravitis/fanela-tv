package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

/**
 * Matchday's stage for an F1 session or a UFC card (SPEC-F1-UFC §9.2): the two team rows become two identity rows.
 * F1: the circuit and the long grand prix name, then the host's flag and the session at 72 %; the time, or live the
 * lap, or the podium after the flag. UFC: the two fighters (face 150 + full name) with VS between the rows; the time,
 * or live the round, or the method after the fight (the loser at 50 %). The background is the sport's: graphite with
 * the circuit at 7 %, or the red and blue corners. Compressed below row 1, the 240 px ribbon: one line and the time.
 * MatchdayLayout shows this view instead of its club stage's words while an event's card is focused.
 */
final class EventStage {
    private EventStage() { }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** The stage's background (1920 x 600, half resolution, cached): F1 graphite wrapping the top left, UFC's two corners. Any thread but the UI's. */
    static Bitmap background(Context c, String sport, float[] outline) {
        String key = sport + "|" + (outline == null ? 0 : java.util.Arrays.hashCode(outline));
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        float h = MatchdayStage.HEIGHT, s = Math.min(1f, Ui.scale(c)) / 2;
        b = Bitmap.createBitmap(Math.round(Ui.W * s), Math.round(h * s), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(s, s);
        cv.drawColor(Ui.APP_BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (SportEvent.UFC.equals(sport)) {
            cv.drawColor(SportViews.UFC_BG);
            SportViews.corner(cv, p, 0, 0, 1300, 700, SportViews.RED, new float[]{0.62f, 0.24f, 0}, h);
            SportViews.corner(cv, p, Ui.W, 0, 1100, 600, SportViews.BLUE, new float[]{0.42f, 0.14f, 0}, h);
        } else {
            int g = 0xFF5A5E68;
            p.setShader(MatchdayStage.gradient(100, Ui.W, h, new int[]{g, ClubTokens.mix(g, 0xFF000000, 0.3) | 0xFF000000,
                ClubTokens.mix(g, FieldColors.BG, 0.75) | 0xFF000000, FieldColors.BG}, new float[]{0, 0.28f, 0.6f, 1}));
            cv.drawRect(0, 0, Ui.W, h, p);
            p.setShader(null);
            if (outline != null) SportViews.drawCircuit(cv, outline, new RectF(980, 40, 1560, 560), 40, Ui.alpha(0xFFFFFFFF, 0.07f));
        }
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(0, 380, 0, 600, Ui.APP_BG & 0xFFFFFF, Ui.APP_BG, Shader.TileMode.CLAMP));
        cv.drawRect(0, 380, Ui.W, 600, p);
        CACHE.put(key, b);
        return b;
    }

    /** The stage's images and words for one card, made off the UI thread. */
    static final class Art {
        String sig = "";
        Bitmap flag, headA, headB;
        float[] outline;
        AppKit.Watch watch;
        String hint = "";
        boolean hidden;
    }

    static Art art(Context c, MatchdayModel.EventRef ev) {
        Art a = new Art();
        SportEvent e = ev.e;
        SportEvent.Session s = e.session(ev.sessionId);
        a.sig = e.signature() + ev.sessionId + ev.last;
        a.hidden = ev.sport != null && ev.sport.hidden;
        a.outline = SportViews.outline(c, e);
        if (SportEvent.F1.equals(e.sport)) a.flag = SportViews.image(c, SportPanel.flagUrl(e), true);
        else {
            SportEvent.Bout b = SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs);
            if (b != null) {
                a.headA = b.a == null ? null : SportViews.image(c, b.a.headshot, true);
                a.headB = b.b == null ? null : SportViews.image(c, b.b.headshot, true);
                if (a.headA == null || a.headB == null) { a.headA = null; a.headB = null; }
            }
        }
        HomeModel.Kind k = ev.kind();
        if (s != null && (k == HomeModel.Kind.NEXT || k == HomeModel.Kind.LIVE)) a.watch = SportViews.watch(c, e, s);
        String app = k == HomeModel.Kind.LIVE ? SportViews.installedApp(c, s) : null;
        a.hint = app != null ? I18n.f("open_app", SportWhere.appName(app))
            : SportText.t(SportEvent.F1.equals(e.sport) ? "weekend_details" : "fight_card");
        return a;
    }

    /** The stage's words and marks for an event card (full stage and ribbon). */
    static final class Info extends AppViews.Spec {
        MatchdayModel.EventRef ev;
        Art art;
        long now;
        private float ribbon;
        String key = "";

        Info(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        void show(MatchdayModel.EventRef ev, Art a, long now) {
            this.ev = ev; this.art = a; this.now = now;
            key = ev == null ? "" : ev.key();
            invalidate();
        }

        void ribbon(float r) { if (r != ribbon) { ribbon = r; invalidate(); } }

        @Override void paint(Canvas cv) {
            if (ev == null || art == null) return;
            if (ribbon < 1) full(cv, 1 - ribbon);
            if (ribbon > 0) ribbonView(cv, ribbon);
        }

        private boolean f1() { return SportEvent.F1.equals(ev.e.sport); }

        private void full(Canvas cv, float a) {
            if (a <= 0.01f) return;
            SportEvent e = ev.e;
            SportEvent.Session s = e.session(ev.sessionId);
            HomeModel.Kind k = ev.kind();
            boolean hidden = art.hidden;
            // the eyebrow: "Up next · Formula 1"; live: the pill and the lap or round; a result: "Final · date"
            float x = 96, cy = k == HomeModel.Kind.LIVE ? 153.74f : 147.53f;
            Paint reg = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.9f * a)), bold = AppKit.text(Ui.BOLD, 28, Ui.alpha(Ui.TXT, 0.9f * a));
            Paint dot = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.9f * 0.45f * a));
            float bl = Ui.baseline(Ui.REGULAR, 28, cy);
            String lead = k == HomeModel.Kind.LIVE ? "" : k == HomeModel.Kind.RESULT ? SportText.t("final") : k == HomeModel.Kind.OFF ? SportText.detail(s)
                : AppText.t(ev.next ? "up_next" : "later");
            if (k == HomeModel.Kind.LIVE) x += MatchdayViews.StageInfo.livePill(cv, x, cy, 0.9f * a, 1) + 16;
            else if (!lead.isEmpty()) { cv.drawText(lead, x, bl, bold); x += bold.measureText(lead) + 16; cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16; }
            String rest = f1() ? SportText.t("f1") : SportText.ufcEvent(e);
            if (k == HomeModel.Kind.RESULT && s != null) rest = AppText.shortDate(s.start) + " · " + rest;
            cv.drawText(rest, x, bl, reg);
            x += reg.measureText(rest);
            if (!f1()) {
                SportEvent.Bout b = SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs);
                if (b != null && b.title) {
                    x += 16; cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16;
                    cv.drawText(SportText.billing(b), x, bl, AppKit.text(Ui.REGULAR, 28, Ui.alpha(SportViews.GOLD, a)));
                }
            }
            Paint np = AppKit.text(Ui.COND_BOLD, 104, Ui.alpha(Ui.TXT, a));
            np.setLetterSpacing(-0.005f);
            float nameMax = 1000;
            if (f1()) {
                if (art.outline != null) SportViews.drawCircuit(cv, art.outline, new RectF(69, 184, 273, 334), 9, Ui.alpha(SportViews.IVORY, 0.95f * a));
                String title = SportText.title(e);
                AppKit.fit(np, title, nameMax, 104, 72);
                cv.drawText(AppKit.cut(np, title, nameMax), 280, Ui.baselineTop(Ui.COND_BOLD, np.getTextSize(), 259 - np.getTextSize() / 2, np.getTextSize()), np);
                if (art.flag != null) SportViews.flag(cv, art.flag, new RectF(171 - 70, 439 - 52.5f, 171 + 70, 439 + 52.5f), 10, a);
                float rbl = Ui.baselineTop(Ui.COND_BOLD, 104, 439 - 52, 104);
                if (k == HomeModel.Kind.RESULT && !hidden && s != null && !s.order.isEmpty()) {
                    java.util.List<SportEvent.Entrant> pod = new java.util.ArrayList<>();
                    for (SportEvent.Entrant xx : s.order) if (pod.size() < 3) pod.add(xx);
                    float px = 280;
                    px += SportViews.code(cv, pod.get(0), px, rbl, 104, 14, 18, Ui.alpha(Ui.TXT, a)) + 40;
                    for (int i = 1; i < pod.size(); i++) {
                        Paint num = Ui.paint(Ui.COND, 38, Ui.alpha(Ui.TXT, 0.6f * a));
                        cv.drawText(String.valueOf(i + 1), px, rbl, num);
                        px += num.measureText(String.valueOf(i + 1)) + 10;
                        px += SportViews.code(cv, pod.get(i), px, rbl, 38, 8, 10, Ui.alpha(Ui.TXT, 0.88f * a)) + 28;
                    }
                } else {
                    Paint sp = AppKit.text(Ui.COND_BOLD, 104, Ui.alpha(Ui.TXT, 0.72f * a));
                    String sn = SportText.session(s);
                    AppKit.fit(sp, sn, nameMax, 104, 72);
                    cv.drawText(AppKit.cut(sp, sn, nameMax), 280, rbl, sp);
                }
            } else {
                SportEvent.Bout b = SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs);
                if (b != null) {
                    boolean res = k == HomeModel.Kind.RESULT && !hidden;
                    String an = SportText.name(b.a), bn = SportText.name(b.b);
                    float size = Math.min(AppKit.fit(np, an, nameMax, 104, 72), AppKit.fit(np, bn, nameMax, 104, 72));
                    AppKit.vsBetweenRows(cv, 284, (184 + 364) / 2f + 75, size, a);
                    for (int i = 0; i < 2; i++) {
                        float rcy = (i == 0 ? 184 : 364) + 75;
                        SportViews.face(cv, i == 0 ? art.headA : art.headB, i == 0 ? b.a : b.b, 171, rcy, 150, a, true);
                        boolean loser = res && b.outcome == (i == 0 ? 2 : 1);
                        np.setTextSize(size);
                        np.setColor(Ui.alpha(Ui.TXT, a * (loser ? 0.5f : 1f)));
                        cv.drawText(AppKit.cut(np, i == 0 ? an : bn, nameMax), 280, Ui.baselineTop(Ui.COND_BOLD, size, rcy - size / 2, size), np);
                    }
                }
            }
            when(cv, e, s, k, hidden, a);
            if (k == HomeModel.Kind.RESULT || k == HomeModel.Kind.OFF) {
                if (!art.hint.isEmpty()) MatchdayViews.StageInfo.okHintLeft(cv, art.hint, 96, 548, a);
            } else {
                MatchdayViews.StageInfo.slot(cv, art.watch, 96, 546, a, false);
                if (!art.hint.isEmpty()) MatchdayViews.StageInfo.okHint(cv, art.hint, 1824, 548, a);
            }
        }

        /** The when column (right edge 1824): the time RC 224, or the lap / round, or the method; the relative line and the date. */
        private void when(Canvas cv, SportEvent e, SportEvent.Session s, HomeModel.Kind k, boolean hidden, float a) {
            if (s == null) return;
            Paint big = AppKit.text(Ui.COND_BOLD, Ui.T_DISPLAY_XL, Ui.alpha(Ui.TXT, a));
            big.setLetterSpacing(-0.015f);
            float bl = Ui.baselineTop(Ui.COND_BOLD, Ui.T_DISPLAY_XL, 150, 201.6f);
            Paint rel = AppKit.text(Ui.MEDIUM, 38, Ui.alpha(Ui.TXT, a));
            Paint date = AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, a * 0.7f));
            String lead = SportText.part(e, s);
            if (k == HomeModel.Kind.LIVE) {
                if (f1()) {
                    int lap = F1Feed.shownLap(s);  // 0 while F1Feed.LIVE_LAP_TRUSTED is false: the start time at 40 %
                    if (SportModel.MAIN_RESULTS.contains(s.kind) && lap > 0) {
                        float w = AppKit.right(cv, big, s.laps > 0 ? lap + "/" + s.laps : String.valueOf(lap), 1824, bl);
                        AppKit.right(cv, AppKit.text(Ui.COND_BOLD, 58, Ui.alpha(Ui.TXT, 0.6f * a)), Cards.caps(SportText.t("lap")), 1824 - w - 20, bl);
                    } else {
                        big.setColor(Ui.alpha(Ui.TXT, 0.4f * a));
                        SportViews.time(cv, big, I18n.time(s.start), 1824, bl, Ui.TXT, Ui.T_DISPLAY_XL, true);
                    }
                    AppKit.right(cv, rel, lead, 1824, 402.31f);
                } else {
                    SportEvent.Bout b = SportModel.inCage(e);
                    if (UfcFeed.shownRound(b) > 0) {  // the round only while UfcFeed.LIVE_ROUND_TRUSTED
                        AppKit.right(cv, big, "R" + UfcFeed.shownRound(b), 1824, bl);
                        if (b.rounds > 0) AppKit.right(cv, rel, SportText.f("of", b.rounds), 1824, 402.31f);
                    } else {
                        big.setColor(Ui.alpha(Ui.TXT, 0.4f * a));
                        SportViews.time(cv, big, I18n.time(SportViews.liveSegment(e, s).start), 1824, bl, Ui.TXT, Ui.T_DISPLAY_XL, true);
                    }
                    AppKit.right(cv, date, SportText.session(SportViews.liveSegment(e, s)), 1824, 450.17f);
                }
                return;
            }
            if (k == HomeModel.Kind.RESULT) {
                if (!f1() && !hidden) {
                    SportEvent.Bout b = SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs);
                    String m = SportText.methodShort(b);
                    if (!m.isEmpty()) {
                        Paint mp = AppKit.text(Ui.COND_BOLD, 190, Ui.alpha(Ui.TXT, a));
                        AppKit.fit(mp, m, 700, 190, 100);
                        AppKit.right(cv, mp, m, 1824, bl);
                        String rl = SportText.roundLine(b);
                        AppKit.right(cv, date, rl.isEmpty() ? AppText.longDate(s.start) : rl, 1824, 402.31f);
                        return;
                    }
                }
                AppKit.right(cv, AppKit.text(Ui.COND_BOLD, Ui.T_REL, Ui.alpha(Ui.TXT, a)), SportText.t("final"), 1824, 300);
                AppKit.right(cv, date, AppText.longDate(s.start), 1824, 402.31f);
                return;
            }
            if (k == HomeModel.Kind.OFF) AppKit.right(cv, big, "—", 1824, bl);
            else if (s.tbd) AppKit.right(cv, AppKit.text(Ui.COND_BOLD, 96, Ui.alpha(Ui.TXT, a)), AppText.t("time_tba"), 1824, 300);
            else SportViews.time(cv, big, I18n.time(s.start), 1824, bl, Ui.TXT, Ui.T_DISPLAY_XL, false);
            String r = k == HomeModel.Kind.OFF ? SportText.detail(s) : SportText.relative(now, s.start);
            AppKit.right(cv, rel, r, 1824, 402.31f);
            AppKit.right(cv, date, SportText.whenLine(f1() ? "" : lead, s.start), 1824, 450.17f);
        }

        /**
         * The ribbon's podium, right-aligned to right on baseline bl: the winner's bar and code (RC Bold 64), then P2
         * and P3 (the place in RC 38 at 60 %, the bar and code at 38), as the full stage's result line; returns its width.
         */
        private static float podium(Canvas cv, SportEvent.Session s, float right, float bl, float a) {
            java.util.List<SportEvent.Entrant> pod = new java.util.ArrayList<>();
            for (SportEvent.Entrant xx : s.order) if (pod.size() < 3) pod.add(xx);
            Paint num = Ui.paint(Ui.COND, 38, Ui.alpha(Ui.TXT, 0.6f * a));
            float w = SportViews.codeWidth(pod.get(0), 64, 10, 14);
            for (int i = 1; i < pod.size(); i++)
                w += (i == 1 ? 40 : 28) + num.measureText(String.valueOf(i + 1)) + 10 + SportViews.codeWidth(pod.get(i), 38, 8, 10);
            float px = right - w;
            px += SportViews.code(cv, pod.get(0), px, bl, 64, 10, 14, Ui.alpha(Ui.TXT, a));
            for (int i = 1; i < pod.size(); i++) {
                px += i == 1 ? 40 : 28;
                cv.drawText(String.valueOf(i + 1), px, bl, num);
                px += num.measureText(String.valueOf(i + 1)) + 10;
                px += SportViews.code(cv, pod.get(i), px, bl, 38, 8, 10, Ui.alpha(Ui.TXT, 0.88f * a));
            }
            return w;
        }

        /** The ribbon (cy 172): the mark 72 and one line at RC 48, the time RC 96 at the right with the watch slot. */
        private void ribbonView(Canvas cv, float a) {
            if (a <= 0.01f) return;
            float cy = 172;
            SportEvent e = ev.e;
            SportEvent.Session s = e.session(ev.sessionId);
            HomeModel.Kind k = ev.kind();
            float right = 1824, tw;
            Paint big = AppKit.text(Ui.COND_BOLD, 96, Ui.alpha(Ui.TXT, a));
            String t;
            if (k == HomeModel.Kind.LIVE) t = I18n.t("live");
            else if (k == HomeModel.Kind.RESULT) t = SportText.t("final");
            else if (k == HomeModel.Kind.OFF) t = "—";
            else t = s == null || s.tbd ? AppText.t("tba") : I18n.time(s.start);
            if (k == HomeModel.Kind.LIVE || k == HomeModel.Kind.RESULT) big = AppKit.text(Ui.COND_BOLD, 64, Ui.alpha(Ui.TXT, a));
            // an F1 result with scores shown: the podium, as the full stage has it (▌NOR  2 ▌VER  3 ▌HAD); hidden: Final
            if (k == HomeModel.Kind.RESULT && f1() && !art.hidden && s != null && !s.order.isEmpty())
                tw = podium(cv, s, right, Ui.baseline(Ui.COND_BOLD, 64, cy), a);
            else tw = AppKit.right(cv, big, t, right, Ui.baseline(Ui.COND_BOLD, big.getTextSize(), cy));
            float slotRight = right - tw - 56;
            float sw = k == HomeModel.Kind.NEXT ? MatchdayViews.StageInfo.slot(cv, art.watch, 0, cy, a, true) : 0;
            if (sw > 0) MatchdayViews.StageInfo.slot(cv, art.watch, slotRight - sw, cy, a, false);
            float room = (sw > 0 ? slotRight - sw : slotRight) - 56 - 96 - 92;
            if (f1()) {
                if (art.outline != null) SportViews.drawCircuit(cv, art.outline, new RectF(96, cy - 36, 168, cy + 36), 5, Ui.alpha(SportViews.IVORY, 0.95f * a));
                else SportViews.monogram(cv, SportEvent.F1, 132, cy, 72, a);
            } else {
                SportEvent.Bout b = SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs);
                SportViews.face(cv, art.headA, b == null ? null : b.a, 132, cy, 72, a, false);
            }
            Paint np = AppKit.text(Ui.COND_BOLD, 48, Ui.alpha(Ui.TXT, a));
            String line = f1() ? SportText.shortTitle(e) + " · " + SportText.session(s)
                : SportText.bout(SportText.staged(e, s, ev.sport == null ? null : ev.sport.prefs));
            AppKit.fit(np, line, room, 48, 34);
            cv.drawText(AppKit.cut(np, line, room), 188, Ui.baseline(Ui.COND_BOLD, np.getTextSize(), cy), np);
        }
    }
}
