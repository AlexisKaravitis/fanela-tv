package app.fanela.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * The Matchday layout's views (SPEC-APP 4): the stage (its pre-rendered colour, crossfading and compressing to a
 * ribbon), the stage's words and crests for the focused card, the card cells of the rows, the row labels, the Settings
 * chip and the empty state's panel. Everything draws in spec px (1920 x 1080).
 */
final class MatchdayViews {
    private MatchdayViews() { }

    // ---------- the stage's colour ----------
    /**
     * The stage bitmap, scaled to the screen, and how much of it shows: 600 px tall, or the 240 px ribbon while the
     * focus is below row 1 (the rest is see-through, so the rows rise under it). A new bitmap crossfades in 300 ms.
     */
    static final class StageView extends View {
        private Bitmap shown, old;
        private float t = 1, height = MatchdayStage.HEIGHT, ribbon;
        private ValueAnimator anim;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), scrim = new Paint(Paint.DITHER_FLAG);
        private final Rect dst = new Rect();
        private final float s;
        /** The ribbon scrim's shader, made when the height or the ribbon changes (not per frame of a still screen). */
        private float scrimFor = -1;

        StageView(Context c) { super(c); s = Ui.scale(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setWillNotDraw(false); }

        void show(Bitmap b, boolean animate) {
            if (b == null || b == shown) return;
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

        /** 0: the full 600 px stage; 1: the 240 px ribbon. */
        void ribbon(float r) {
            if (r == ribbon) return;
            ribbon = r;
            height = MatchdayStage.HEIGHT + (MatchdayLayout.RIBBON - MatchdayStage.HEIGHT) * r;
            invalidate();
        }

        void release() { if (anim != null) anim.cancel(); shown = null; old = null; }

        @Override protected void onDraw(Canvas cv) {
            cv.save();
            cv.clipRect(0, 0, getWidth(), Math.round(height * s));
            dst.set(0, 0, getWidth(), getHeight());
            if (old != null && t < 1) { p.setAlpha(255); cv.drawBitmap(old, null, dst, p); }
            if (shown != null) { p.setAlpha(Math.round(255 * t)); cv.drawBitmap(shown, null, dst, p); }
            if (ribbon > 0) {  // the ribbon's own scrim: its last 90 px fall to the background
                float bot = height * s, top = (height - 90) * s;
                if (scrimFor != ribbon) {
                    scrimFor = ribbon;
                    scrim.setShader(new LinearGradient(0, top, 0, bot, Ui.APP_BG & 0xFFFFFF, Ui.alpha(Ui.APP_BG, ribbon), Shader.TileMode.CLAMP));
                }
                cv.drawRect(0, top, getWidth(), bot, scrim);
            }
            cv.restore();
        }
    }

    // ---------- the stage's words ----------
    /** What the stage shows for the focused card, resolved once (bind) so drawing only reads it. */
    static final class Stage {
        /** Changes when anything shown changes (a live score, a new card): the stage swaps. */
        String key = "";
        HomeModel.Kind kind = HomeModel.Kind.LOADING;
        MatchdayModel.Item item;
        Match m;
        String lead = "", clock = "", home = "", away = "", time = "", suffix = "", rel = "", date = "", hint = "", message = "";
        /** The message's short form for the ribbon's one line ("Couldn't load fixtures. Trying again shortly."). */
        String messageShort = "";
        final List<String> parts = new ArrayList<>();
        String team;
        boolean both;
        /** 1 home leads, -1 away leads, 0 level (live and results: the trailing side at 60 %). */
        int leader;
        AppArt.Decal homeBig, awayBig, homeSmall, awaySmall, mine, mineSmall;
        AppKit.Watch watch;
    }

    /**
     * The stage's words and crests (SPEC-APP 4.2): the eyebrow, the two team rows (home first) with the when column, or
     * live the two big scores on the team rows, the watch slot and the OK hint; compressed, the 240 px ribbon (crests
     * 72, names RC 48, the time RC 96, the slot intact). A new card's words swap at 120 ms, as the colour crossfades.
     */
    static final class StageInfo extends AppViews.Spec {
        private Stage cur = new Stage(), old;
        private long swapAt;
        private float ribbon;
        float pulse = 1;
        /** The LIVE dot's opacity (MatchdayLayout pulses it .35 -> 1 -> .35 while a live match is staged). */
        float dot = 1;

        StageInfo(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        Stage current() { return cur; }

        void show(Stage st, boolean animate) {
            if (st.key.equals(cur.key) && st.kind == cur.kind) { cur = st; invalidate(); return; }
            old = animate ? cur : null;
            cur = st;
            swapAt = animate ? System.currentTimeMillis() : 0;
            invalidate();
        }

        void ribbon(float r) { if (r != ribbon) { ribbon = r; invalidate(); } }

        @Override void paint(Canvas cv) {
            float a = 1;
            Stage st = cur;
            if (swapAt > 0) {
                long dt = System.currentTimeMillis() - swapAt;
                if (dt < Ui.SWAP_MS && old != null) { st = old; a = 1 - dt / (float) Ui.SWAP_MS; }
                else if (dt < Ui.SWITCH_MS) a = (dt - Ui.SWAP_MS) / (float) (Ui.SWITCH_MS - Ui.SWAP_MS);
                else { swapAt = 0; old = null; }
                if (swapAt > 0) postInvalidateOnAnimation();
            }
            a = Ui.FAST_OUT_SLOW_IN.getInterpolation(Math.max(0, Math.min(1, a)));
            if (ribbon < 1) full(cv, st, a * (1 - ribbon));
            if (ribbon > 0) ribbonView(cv, st, a * ribbon);
        }

        // ---------- full ----------
        private void full(Canvas cv, Stage st, float a) {
            if (a <= 0.01f) return;
            switch (st.kind) {
                case LOADING: skeleton(cv, a * pulse); return;
                case NONE: case FAILED: nothing(cv, st, a); return;
                default: break;
            }
            Match m = st.m;
            boolean live = st.kind == HomeModel.Kind.LIVE, scored = live || st.kind == HomeModel.Kind.RESULT;
            eyebrow(cv, st, a);
            // the team rows: crest 150 + name RC 104, home first; the trailing side at 60 % once there is a score
            float nameMax = scored ? 1180 : 1000;
            Paint np = AppKit.text(Ui.COND_BOLD, 104, Ui.TXT);
            np.setLetterSpacing(-0.005f);
            float size = Math.min(AppKit.fit(np, st.home, nameMax, 104, 72), AppKit.fit(np, st.away, nameMax, 104, 72));
            AppKit.vsBetweenRows(cv, 280, (184 + 364) / 2f + 75, size, a);  // the VS mark between the names (owner, fx6)
            for (int i = 0; i < 2; i++) {
                float top = i == 0 ? 184 : 364, cy = top + 75;
                AppArt.Decal d = i == 0 ? st.homeBig : st.awayBig;
                if (d != null) d.draw(cv, 171, cy, alphaPaint(a));
                boolean trailing = scored && st.leader != 0 && (st.leader > 0) == (i == 1);
                np.setTextSize(size);
                np.setColor(Ui.alpha(Ui.TXT, a * (trailing ? 0.6f : 1f)));
                String n = AppKit.cut(np, i == 0 ? st.home : st.away, nameMax);
                cv.drawText(n, 280, Ui.baselineTop(Ui.COND_BOLD, size, cy - size / 2, size), np);
            }
            if (scored) {
                Paint sp = AppKit.text(Ui.COND_BOLD, Ui.T_SCORE_L, Ui.TXT);
                String[] sc = {m.hs, m.as};
                for (int i = 0; i < 2; i++) {
                    boolean trailing = st.leader != 0 && (st.leader > 0) == (i == 1);
                    sp.setColor(Ui.alpha(Ui.TXT, a * (trailing ? 0.6f : 1f)));
                    AppKit.right(cv, sp, sc[i] == null ? "" : sc[i], 1824, Ui.baselineTop(Ui.COND_BOLD, Ui.T_SCORE_L, i == 0 ? 184 : 364, 150));
                }
                if (st.kind == HomeModel.Kind.RESULT) {
                    float w = AppKit.pillWidth(AppKit.outcomeWord(m.result()), false);
                    AppKit.pill(cv, m.result(), 1824 - w, 550, false, a);
                }
            } else when(cv, st, a);
            // the slot and the hint (a result has no slot: the hint takes its place)
            if (st.kind == HomeModel.Kind.RESULT || st.kind == HomeModel.Kind.OFF) {
                if (!st.hint.isEmpty()) okHintLeft(cv, st.hint, 96, 548, a);
            } else {
                slot(cv, st.watch, 96, 546, a, false);
                if (!st.hint.isEmpty()) okHint(cv, st.hint, 1824, 548, a);
            }
        }

        /** "Up next · EuroLeague · (ball) Basketball" (GS 28, the first word bold); live: the LIVE pill and the clock first. */
        private void eyebrow(Canvas cv, Stage st, float a) {
            boolean live = st.kind == HomeModel.Kind.LIVE;
            float x = 96, cy = live ? 153.74f : 147.53f;
            float ea = 0.9f * a;
            Paint reg = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, ea));
            Paint dot = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, ea * 0.45f));
            float bl = Ui.baseline(Ui.REGULAR, 28, cy);
            boolean first = true;
            if (live) {
                x += livePill(cv, x, cy, ea, this.dot) + 16;
                if (!st.clock.isEmpty()) {
                    Paint ck = AppKit.text(Ui.COND_BOLD, 36, Ui.alpha(Ui.TXT, ea));
                    cv.drawText(st.clock, x, Ui.baseline(Ui.COND_BOLD, 36, cy), ck);
                    x += ck.measureText(st.clock) + 16;
                }
                first = st.clock.isEmpty();
                if (!first) { cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16; }
                first = true;
            } else if (!st.lead.isEmpty()) {
                Paint b = AppKit.text(Ui.BOLD, 28, Ui.alpha(Ui.TXT, ea));
                cv.drawText(st.lead, x, bl, b);
                x += b.measureText(st.lead) + 16;
                first = false;
            }
            List<String> parts = new ArrayList<>(st.parts);
            for (String s : parts) {
                if (!first) { cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16; }
                cv.drawText(s, x, bl, reg);
                x += reg.measureText(s) + 16;
                first = false;
            }
            if (st.both && st.team != null) {
                if (!first) { cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 16; }
                AppKit.sport(cv, st.team, x + 15, cy, 30, Ui.alpha(Ui.TXT, ea * 0.9f));
                x += 30 + 16;
                cv.drawText(HomeModel.sport(st.m), x, bl, reg);
            }
        }

        /** The centre of the LIVE pill's dot from the pill's left edge (spec px), and its radius. */
        static final float DOT_X = 16 + 7, DOT_R = 7;

        /**
         * The LIVE pill (46 px, live red, a white dot and LIVE in bold capitals) at opacity a, its dot at a * dot (the
         * pulse; 0 when a view of its own draws it); returns its width.
         */
        static float livePill(Canvas cv, float x, float cy, float a, float dot) {
            Paint t = AppKit.caps(Ui.BOLD, 26, Ui.alpha(0xFFFFFFFF, a), 0.1f);
            String w = I18n.t("live");
            float tw = t.measureText(w), wd = 16 + 14 + 12 + tw + 18 - 2.6f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Ui.alpha(Ui.LIVE, a));
            cv.drawRoundRect(new RectF(x, cy - 23, x + wd, cy + 23), 23, 23, p);
            if (dot > 0) {
                p.setColor(Ui.alpha(0xFFFFFFFF, a * dot));
                cv.drawCircle(x + DOT_X, cy, DOT_R, p);
            }
            cv.drawText(w, x + 16 + 14 + 12 - AppKit.lead(t), Ui.baseline(Ui.BOLD, 26, cy), t);
            return wd;
        }

        /** The when column, right-aligned at 1824: the time RC 224, "Tomorrow · in 26 h" 38 Medium, the date 30 at 70 %. */
        private void when(Canvas cv, Stage st, float a) {
            Paint big = AppKit.text(Ui.COND_BOLD, Ui.T_DISPLAY_XL, Ui.alpha(Ui.TXT, a));
            big.setLetterSpacing(-0.015f);
            float bl = Ui.baselineTop(Ui.COND_BOLD, Ui.T_DISPLAY_XL, 150, 201.6f);
            if (st.kind == HomeModel.Kind.OFF) AppKit.right(cv, big, "—", 1824, bl);
            else if (st.time.isEmpty()) {
                Paint t = AppKit.text(Ui.COND_BOLD, 96, Ui.alpha(Ui.TXT, a));
                AppKit.right(cv, t, AppText.t("time_tba"), 1824, 300);
            } else if (!st.suffix.isEmpty()) {
                Paint suf = AppKit.text(Ui.COND_BOLD, 80, Ui.alpha(Ui.TXT, a * 0.75f));
                float sw = AppKit.right(cv, suf, st.suffix, 1824, bl);
                AppKit.right(cv, big, st.time, 1824 - sw - 16, bl);
            } else AppKit.right(cv, big, st.time, 1824 + 3.4f, bl);
            Paint rel = AppKit.text(Ui.MEDIUM, 38, Ui.alpha(Ui.TXT, a));
            if (!st.rel.isEmpty()) AppKit.right(cv, rel, st.rel, 1824, 402.31f);
            Paint date = AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, a * 0.7f));
            AppKit.right(cv, date, st.date, 1824, st.rel.isEmpty() ? 402.31f : 450.17f);
        }

        /** Where to watch, bottom-left: the trimmed logo 40 + the number RC 52 + "Magenta TV" 30 at 72 %. */
        static float slot(Canvas cv, AppKit.Watch w, float x, float cy, float a, boolean measureOnly) {
            if (w == null) return 0;
            float x0 = x;
            Paint app = AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2 * a));
            if (w.kind == AppKit.Watch.SURE && w.logo != null) {
                float lh = 40, lw = Math.min(300, lh * w.logo.getWidth() / (float) w.logo.getHeight());
                if (!measureOnly) cv.drawBitmap(w.logo, null, new RectF(x, cy - lh / 2, x + lw, cy + lh / 2), alphaPaint(a));
                x += lw;
                if (!w.number.isEmpty()) {
                    Paint n = AppKit.text(Ui.COND_BOLD, 52, Ui.alpha(Ui.TXT, a));
                    x += 20;
                    if (!measureOnly) cv.drawText(w.number, x, Ui.baselineTop(Ui.COND_BOLD, 52, cy - 26, 52), n);
                    x += n.measureText(w.number);
                }
            } else {
                x += measureOnly ? AppKit.width(w, AppKit.Size.MD, 900) : AppKit.cell(cv, w, AppKit.Size.MD, x, cy, false, 900, false, a);
            }
            if (w.app != null && w.kind != AppKit.Watch.GUIDE) {
                x += 20;
                if (!measureOnly) cv.drawText(w.app, x, Ui.baseline(Ui.REGULAR, 30, cy), app);
                x += app.measureText(w.app);
            }
            return x - x0;
        }

        static void okHint(Canvas cv, String label, float right, float cy, float a) {
            if (a < 1) {
                int l = cv.saveLayerAlpha(null, Math.round(255 * a));
                AppKit.okHint(cv, label, right, cy, Ui.TXT);
                cv.restoreToCount(l);
            } else AppKit.okHint(cv, label, right, cy, Ui.TXT);
        }

        /** The hint drawn from its left edge (a result's, in the slot's place). */
        static void okHintLeft(Canvas cv, String label, float x, float cy, float a) {
            Paint t = AppKit.text(Ui.REGULAR, 26, 0);
            Paint k = AppKit.caps(Ui.BOLD, 22, 0, 0.05f);
            float w = k.measureText("OK") + 20 + 12 + t.measureText(label);
            okHint(cv, label, x + w, cy, a);
        }

        // ---------- the ribbon ----------
        private void ribbonView(Canvas cv, Stage st, float a) {
            if (a <= 0.01f) return;
            float cy = 172;
            if (st.kind == HomeModel.Kind.LOADING) {
                float pa = a * pulse;
                AppKit.skeleton(cv, 96, cy - 36, 168, cy + 36, 36, pa);
                AppKit.skeleton(cv, 188, cy - 24, 560, cy + 24, 12, pa);
                AppKit.skeleton(cv, 1544, cy - 40, 1824, cy + 40, 12, pa);
                return;
            }
            if (st.kind == HomeModel.Kind.NONE || st.kind == HomeModel.Kind.FAILED) {
                if (st.mineSmall != null) drawAt(cv, st.mineSmall, 132, cy, 72, a);
                Paint np = AppKit.text(Ui.COND_BOLD, 48, Ui.alpha(Ui.TXT, a));
                cv.drawText(st.home, 188, Ui.baseline(Ui.COND_BOLD, 48, cy), np);
                Paint mp = AppKit.text(Ui.MEDIUM, 30, Ui.alpha(Ui.TXT, a * Ui.A2));
                String msg = st.messageShort.isEmpty() ? st.message : st.messageShort;
                AppKit.right(cv, mp, AppKit.cut(mp, msg, 1000), 1824, Ui.baseline(Ui.MEDIUM, 30, cy));
                return;
            }
            Match m = st.m;
            boolean scored = st.kind == HomeModel.Kind.LIVE || st.kind == HomeModel.Kind.RESULT;
            // right: the time, or the score
            float right = 1824, tw;
            if (scored) {
                tw = AppKit.scoreWidth(m.hs, m.as, 96);
                AppKit.score(cv, m, right - tw, Ui.baseline(Ui.COND_BOLD, 96, cy), 96, Ui.TXT, a);
            } else {
                Paint big = AppKit.text(Ui.COND_BOLD, 96, Ui.alpha(Ui.TXT, a));
                String t = st.kind == HomeModel.Kind.OFF ? "—" : st.time.isEmpty() ? AppText.t("tba") : st.time + (st.suffix.isEmpty() ? "" : " " + st.suffix);
                tw = AppKit.right(cv, big, t, right, Ui.baseline(Ui.COND_BOLD, 96, cy));
            }
            float slotRight = right - tw - 56;
            float sw = scored ? 0 : slot(cv, st.watch, 0, cy, a, true);
            if (sw > 0) slot(cv, st.watch, slotRight - sw, cy, a, false);
            if (st.kind == HomeModel.Kind.LIVE) {
                float px = (sw > 0 ? slotRight - sw : slotRight) - 40;
                Paint t = AppKit.caps(Ui.BOLD, 26, 0, 0.1f);
                float pw = 16 + 14 + 12 + t.measureText(I18n.t("live")) + 18 - 2.6f;
                livePill(cv, px - pw, cy, a, dot);
                sw += pw + 40;
            }
            float room = (sw > 0 ? slotRight - sw : slotRight) - 56 - 96;
            // left: both teams on one line, crest 72 + name RC 48, home first, the VS mark between (24 px of air either side)
            Paint np = AppKit.text(Ui.COND_BOLD, 48, Ui.TXT);
            float vs = AppKit.vsSize(48), vsW = CardKit.vsWidth(CardKit.VS, vs);
            float each = (room - 2 * (72 + 20) - 24 - vsW - 24) / 2;
            float size = Math.min(AppKit.fit(np, st.home, each, 48, 34), AppKit.fit(np, st.away, each, 48, 34));
            np.setTextSize(size);
            float x = 96;
            for (int i = 0; i < 2; i++) {
                AppArt.Decal d = i == 0 ? st.homeSmall : st.awaySmall;
                if (d != null) drawAt(cv, d, x + 36, cy, 72, a);
                x += 72 + 20;
                boolean trailing = scored && st.leader != 0 && (st.leader > 0) == (i == 1);
                np.setColor(Ui.alpha(Ui.TXT, a * (trailing ? 0.6f : 1f)));
                String n = AppKit.cut(np, i == 0 ? st.home : st.away, each);
                cv.drawText(n, x, Ui.baseline(Ui.COND_BOLD, size, cy), np);
                x += np.measureText(n) + 24;
                if (i == 0) x += CardKit.vs(cv, CardKit.VS, x, cy, vs, Ui.alpha(Ui.TXT, CardKit.VS_A * a), false) + 24;
            }
        }

        // ---------- nothing / loading ----------
        private void nothing(Canvas cv, Stage st, float a) {
            Paint b = AppKit.text(Ui.BOLD, 28, Ui.alpha(Ui.TXT, 0.9f * a));
            cv.drawText(st.home, 96, Ui.baseline(Ui.BOLD, 28, 147.53f), b);
            if (st.mine != null) st.mine.draw(cv, 171, 259, alphaPaint(a));
            Paint np = AppKit.text(Ui.COND_BOLD, 104, Ui.alpha(Ui.TXT, a));
            AppKit.fit(np, st.home, 1000, 104, 72);
            cv.drawText(AppKit.cut(np, st.home, 1000), 280, Ui.baselineTop(Ui.COND_BOLD, np.getTextSize(), 259 - np.getTextSize() / 2, np.getTextSize()), np);
            boolean failed = st.kind == HomeModel.Kind.FAILED;
            Paint mp = failed ? AppKit.text(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2 * a)) : AppKit.text(Ui.COND_BOLD, Ui.T_REL, Ui.alpha(Ui.TXT, a));
            float lh = failed ? 42 : 64, y = failed ? 190 : 210;
            for (String line : balanced(mp, st.message, 640)) { AppKit.right(cv, mp, line, 1824, y); y += lh; }
        }

        /**
         * The sentence wrapped at max in as few lines as it needs, and then as evenly as it can, so its last line is
         * not one word ("No fixtures scheduled / right now.", not "No fixtures scheduled right / now.").
         */
        static List<String> balanced(Paint p, String text, float max) {
            List<String> lines = ClubHero.wrap(p, text, max);
            int n = lines.size();
            if (n < 2) return lines;
            float lo = p.measureText(text) / n, hi = max;
            while (hi - lo > 4) {
                float mid = (lo + hi) / 2;
                if (ClubHero.wrap(p, text, mid).size() <= n) hi = mid; else lo = mid;
            }
            return ClubHero.wrap(p, text, hi);
        }

        private void skeleton(Canvas cv, float a) {
            AppKit.skeleton(cv, 96, 130, 516, 156, 12, a);
            AppKit.skeleton(cv, 96, 184, 246, 334, 75, a);
            AppKit.skeleton(cv, 280, 213, 900, 305, 12, a);
            AppKit.skeleton(cv, 96, 364, 246, 514, 75, a);
            AppKit.skeleton(cv, 280, 393, 660, 485, 12, a);
            AppKit.skeleton(cv, 1304, 150, 1824, 340, 12, a);
            AppKit.skeleton(cv, 1464, 364, 1824, 398, 12, a);
            AppKit.skeleton(cv, 96, 520, 436, 564, 12, a);
        }
    }

    /** A crest decal drawn at another box size (the ribbon's 72 from the stage's 150): its rim and shadow scale with it. */
    static void drawAt(Canvas cv, AppArt.Decal d, float cx, float cy, float size, float a) {
        float k = size / d.box, h = (d.box / 2 + d.margin) * k;
        cv.drawBitmap(d.bitmap, null, new RectF(cx - h, cy - h, cx + h, cy + h), alphaPaint(a));
    }

    static Paint alphaPaint(float a) {
        if (a >= 1) return AppArt.FILTER;
        Paint p = new Paint(AppArt.FILTER);
        p.setAlpha(Math.round(255 * a));
        return p;
    }

    // ---------- the Settings chip ----------
    /** "Settings" in the masthead (46 px, a 2 px white 40 % outline, 26 Medium); focused, it inverts like a button. */
    static final class Chip extends AppViews.Cell {
        String label;
        /** Space around the chip inside the view, for the halo. */
        static final float PAD = 8;

        Chip(Context c, String label) { super(c); this.label = label; setContentDescription(label); }

        static float width(String label) { return Ui.paint(Ui.MEDIUM, 26, 0).measureText(label) + 52; }

        @Override boolean ownFocus() { return true; }
        @Override float radius() { return 23; }
        @Override RectF shape() { return new RectF(PAD, PAD, w() - PAD, h() - PAD); }

        @Override void paint(Canvas cv) {
            RectF box = shape();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (focus > 0) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 0.22f * focus));
                RectF halo = new RectF(box);
                halo.inset(-6, -6);
                cv.drawRoundRect(halo, 29, 29, p);
                p.setColor(Ui.alpha(Ui.TXT, focus));
                cv.drawRoundRect(box, 23, 23, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(AppViews.blend(Ui.alpha(0xFFFFFFFF, 0.4f * 0.9f), Ui.TXT, focus));
            RectF in = new RectF(box);
            in.inset(1, 1);
            cv.drawRoundRect(in, 22, 22, p);
            Paint t = Ui.paint(Ui.MEDIUM, 26, AppViews.blend(Ui.alpha(Ui.TXT, 0.9f), 0xFF111111, focus));
            AppKit.centre(cv, t, label, box.centerX(), Ui.baseline(Ui.MEDIUM, 26, box.centerY()));
        }
    }

    // ---------- a card in a row ----------
    /** Where a cell finds its bitmap (the cache, by key). */
    interface Cards { Bitmap get(String key); }

    private static final ColorMatrixColorFilter RESULT;
    static {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.55f);
        ColorMatrix dim = new ColorMatrix();
        dim.setScale(0.82f, 0.82f, 0.82f, 1);
        cm.postConcat(dim);
        RESULT = new ColorMatrixColorFilter(cm);
    }

    /**
     * One card slot (408 x 229.5) inside a margin that leaves room for the live card's red ring and glow: the card
     * bitmap from the cache (results through a desaturating colour matrix at draw time, no second bitmap), a quiet
     * placeholder until it is drawn, or, slim, one quiet line when a club has nothing to list (the row collapses to its
     * label and that line: a row of one grey tile per idle club was most of the screen). A card that changes (a live score,
     * its clock, "IN 44 MIN") keeps showing its previous bitmap until the new one is drawn, so it updates in place and
     * never blanks. Focused: scale 1.05 (150 ms) and the one ring (FocusLayer), which follows the scale through shape().
     */
    static final class CardCell extends AppViews.Cell {
        static final float M = 32;
        MatchdayModel.Item item;
        String cardKey;
        /** The same match's previous card, shown until cardKey's is drawn (null when there is none). */
        String prevKey;
        boolean result, live, skeleton;
        /**
         * A club with nothing to list: the slim line's sentence (28 px at 55 %). Focused, it brightens to 85 % on a
         * subtle fill: no ring and no scale, so it never reads as a button (there is nothing to open).
         */
        String message;
        boolean slim;
        /** The slim line's height and the fill's margin around its words (spec px). */
        static final float LINE_H = 48, LINE_PAD = 18;
        /** The slim line's focused fill: white at 5 %, quieter than a row's 7.5 % (it is not a control). */
        static final float LINE_FILL = 0.05f;
        float pulse = 1;
        private Cards source;
        private float appear = 1;
        private ValueAnimator fade;
        private boolean had;
        private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        CardCell(Context c, Cards source) {
            super(c);
            this.source = source;
            setPivotX(0);
        }

        void bind(MatchdayModel.Item it, String key, long now) {
            boolean sameMatch = item != null && item.key().equals(it.key());
            item = it; message = null; skeleton = false; slim = false;
            boolean changed = cardKey == null || !cardKey.equals(key);
            if (changed) {
                // a new state of the same match: its last drawn card stays up until the new one is ready
                if (!sameMatch) prevKey = null;
                else if (shows(cardKey)) prevKey = cardKey;
                else if (!shows(prevKey)) prevKey = null;
            }
            cardKey = key;
            result = it.result();
            live = it.live();
            bp.setColorFilter(result ? RESULT : null);
            setFocusable(true);
            setContentDescription(HomeModel.speak(it.m, it.kind(), now));
            if (changed) had = shows(key) || shows(prevKey);
            if (shows(key)) prevKey = null;
            invalidate();
        }

        /** The cache holds a drawable bitmap for the key. */
        private boolean shows(String key) {
            Bitmap b = key == null ? null : source.get(key);
            return b != null && !b.isRecycled();
        }

        /**
         * The slim line: a club with nothing to list (focusable, so the stage still shows that club when it is reached).
         * TalkBack hears whose line it is: "AEK Athens. Couldn't load fixtures…" (spoken, the row's label is not).
         */
        void line(String text, String spoken) {
            item = null; cardKey = null; prevKey = null; skeleton = false; message = text; live = false; result = false; slim = true;
            setFocusable(true);
            setContentDescription(spoken);
            invalidate();
        }

        void skeleton() {
            item = null; cardKey = null; prevKey = null; message = null; skeleton = true; live = false; slim = false;
            setFocusable(false);
            setContentDescription(null);
            invalidate();
        }

        /** Its bitmap was drawn: it fades in (160 ms), unless it was already showing one (then it simply replaces it). */
        void ready() {
            prevKey = null;
            if (had) { invalidate(); return; }
            had = true;
            if (!isShown()) { appear = 1; invalidate(); return; }
            if (fade != null) fade.cancel();
            fade = ValueAnimator.ofFloat(0, 1);
            fade.setDuration(Ui.WATCH_MS);
            fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { appear = (float) a.getAnimatedValue(); invalidate(); }
            });
            fade.start();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            setPivotX(w / 2f);
            setPivotY(h / 2f);
        }

        static Paint linePaint(float alpha) { return Ui.paint(Ui.MEDIUM, 28, Ui.alpha(Ui.TXT, alpha)); }

        @Override RectF shape() {
            if (slim) {
                float tw = message == null ? 0 : linePaint(1).measureText(message);
                return new RectF(M - LINE_PAD, M, M + tw + LINE_PAD, M + LINE_H);
            }
            float sx = getScaleX(), sy = getScaleY();
            return new RectF(M * sx, M * sy, (M + MatchdayCards.W) * sx, (M + MatchdayCards.H) * sy);
        }

        @Override float radius() { return slim ? 12 : MatchdayCards.RADIUS * getScaleX(); }

        /** The slim line draws its own quiet focus (the brighter words on a fill): the ring layer leaves it alone. */
        @Override boolean ownFocus() { return slim; }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (slim) return;  // a line: its words brighten on a fill, nothing grows
            animate().scaleX(gain ? Ui.SCALE_CARD : 1f).scaleY(gain ? Ui.SCALE_CARD : 1f).setDuration(Ui.FOCUS_MS)
                .setInterpolator(Ui.FAST_OUT_SLOW_IN).start();
            setTranslationZ(gain ? 1 : 0);
        }

        @Override void paint(Canvas cv) {
            RectF card = new RectF(M, M, M + MatchdayCards.W, M + MatchdayCards.H);
            float r = MatchdayCards.RADIUS;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (skeleton) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 0.07f * pulse));
                cv.drawRoundRect(card, r, r, p);
                return;
            }
            if (slim) {
                if (focus > 0) {
                    p.setColor(Ui.alpha(0xFFFFFFFF, LINE_FILL * focus));
                    cv.drawRoundRect(shape(), radius(), radius(), p);
                }
                if (message != null) cv.drawText(message, M, Ui.baseline(Ui.MEDIUM, 28, M + LINE_H / 2), linePaint(0.55f + 0.3f * focus));
                return;
            }
            if (live) liveRing(cv, card, r);
            Bitmap b = source.get(cardKey);
            if ((b == null || b.isRecycled()) && prevKey != null) b = source.get(prevKey);  // the previous state, in place
            if (b == null || b.isRecycled()) {
                p.setColor(0x12FFFFFF);
                cv.drawRoundRect(card, r, r, p);
                return;
            }
            if (appear < 1) { p.setColor(Ui.alpha(0x12FFFFFF, 1 - appear)); cv.drawRoundRect(card, r, r, p); }
            bp.setAlpha(Math.round(255 * appear));
            cv.drawBitmap(b, null, card, bp);
        }

        /** Unfocused: a 3 px live-red ring with a soft red glow; focused: a 2 px red halo outside the white ring. */
        private void liveRing(Canvas cv, RectF card, float r) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            float f = focus;
            // the glow (box-shadow 0 0 28 px, live 45 %), stacked strokes fading outwards
            for (int i = 0; i < 7; i++) {
                float e = 3 + i * 4;
                p.setStrokeWidth(4);
                p.setColor(Ui.alpha(Ui.LIVE, 0.09f * (1 - i / 7f) * (1 - f)));
                RectF g = new RectF(card);
                g.inset(-e, -e);
                cv.drawRoundRect(g, r + e, r + e, p);
            }
            if (f < 1) {
                p.setStrokeWidth(3);
                p.setColor(Ui.alpha(Ui.LIVE, 1 - f));
                RectF g = new RectF(card);
                g.inset(-1.5f, -1.5f);
                cv.drawRoundRect(g, r + 1.5f, r + 1.5f, p);
            }
            if (f > 0) {
                p.setStrokeWidth(2);
                p.setColor(Ui.alpha(Ui.LIVE, f));
                RectF g = new RectF(card);
                g.inset(-5, -5);
                cv.drawRoundRect(g, r + 5, r + 5, p);
            }
        }
    }

    // ---------- a row's label ----------
    /** "Next & latest" or the club's name with its crest (36, flat), 32 Medium; a grey block while loading. */
    static final class RowLabel extends AppViews.Spec {
        String text = "";
        AppArt.Decal crest;
        boolean skeleton;
        float skeletonW = 260, pulse = 1;

        RowLabel(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        void set(String t, AppArt.Decal crest, boolean skeleton, float skeletonW) {
            this.text = t; this.crest = crest; this.skeleton = skeleton; this.skeletonW = skeletonW;
            invalidate();
        }

        @Override void paint(Canvas cv) {
            if (skeleton) { AppKit.skeleton(cv, 96, 5, 96 + skeletonW, 35, 12, pulse); return; }
            float x = 96;
            if (crest != null) { crest.draw(cv, x + 18, 20, null); x += 36 + 14; }
            cv.drawText(text, x, Ui.baseline(Ui.MEDIUM, 32, 20), Ui.paint(Ui.MEDIUM, 32, Ui.TXT));
        }
    }

    /** The fade over the peeking row (y 1000 -> 1080): there is more below. */
    static final class PeekFade extends AppViews.Spec {
        PeekFade(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) {
            Paint p = new Paint(Paint.DITHER_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, h() * 0.85f, Ui.APP_BG & 0xFFFFFF, Ui.APP_BG, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w(), h(), p);
        }
    }

    /** No clubs yet: the red shirt, "Follow your clubs" and a line of why, on a white 5 % panel (x 96, y 140, 1728 wide). */
    static final class EmptyPanel extends AppViews.Spec {
        EmptyPanel(Context c) { super(c); setContentDescription(I18n.t("empty_title") + ". " + I18n.t("empty_text")); }
        @Override void paint(Canvas cv) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0x0DFFFFFF);
            cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_HERO, Ui.R_HERO, p);
            Bitmap shirt = AppArt.shirt(getContext(), 0xFFE2001A);
            cv.drawBitmap(shirt, null, new RectF(64, 48, 160, 144), AppArt.FILTER);
            Paint t = Ui.paint(Ui.COND_BOLD, 72, Ui.TXT);
            cv.drawText(I18n.t("empty_title"), 64, Ui.baselineTop(Ui.COND_BOLD, 72, 174, 72), t);
            Paint b = Ui.paint(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, 0.75f));
            float y = Ui.baselineTop(Ui.REGULAR, 30, 268, 42);
            for (String line : ClubHero.wrap(b, I18n.t("empty_text"), 900)) { cv.drawText(line, 64, y, b); y += 42; }
        }
    }

    /** A rounded-rect path (clips). */
    static Path round(RectF r, float radius) {
        Path p = new Path();
        p.addRoundRect(r, radius, radius, Path.Direction.CW);
        return p;
    }
}
