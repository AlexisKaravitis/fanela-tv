package app.fanela.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** The Club layout's small views: the rail's clubs and gear, the latest-result tiles and the fixture rows. */
final class ClubViews {
    private ClubViews() { }

    /**
     * A followed club in the rail (176 wide): the selected one with its crest at 120, its name in bold and the 5 px
     * marker; the others with the crest at 76, the name and one line of awareness (next kick-off, a fresh result, or
     * the LIVE score in live red).
     */
    static final class RailItem extends AppViews.Cell {
        HomeModel.ClubData data;
        boolean selected;
        AppArt.Decal big, small;
        int ink = Ui.TXT;

        RailItem(Context c) { super(c); }

        static float height(boolean selected, boolean sub) { return selected ? 186.8f : sub ? 177.6f : 142.8f; }

        void bind(HomeModel.ClubData d, boolean selected, AppArt.Decal big, AppArt.Decal small, int ink, boolean onLight) {
            this.data = d; this.selected = selected; this.big = big; this.small = small; this.ink = ink; this.onLight = onLight;
            String sub = d.rail.isEmpty() ? "" : ". " + d.rail;
            setContentDescription(d.club.label() + (selected ? ", " + AppText.t("selected") : "") + sub);
            invalidate();
        }

        @Override RectF shape() { return new RectF(8, 2, 176, h() - 2); }
        @Override float radius() { return 18; }

        @Override void paint(Canvas cv) {
            if (data == null) return;
            fill(cv);
            float cx = 95;
            if (selected) {
                Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
                m.setColor(ink);
                cv.drawRoundRect(new RectF(0, 22, 5, 134), 3, 3, m);
                if (big != null) big.draw(cv, cx, 74, null);
                Paint n = Ui.paint(Ui.BOLD, 24, ink);  // 24 px, the spec's minimum: a longer name is cut, never shrunk
                AppKit.centre(cv, n, AppKit.cut(n, data.club.label(), 170), cx, 166.6f);
                return;
            }
            if (small != null) small.draw(cv, cx, 52, null);
            Paint n = Ui.paint(Ui.MEDIUM, 24, ink);
            AppKit.centre(cv, n, AppKit.cut(n, data.club.label(), 170), cx, 122.56f);
            if (!data.rail.isEmpty()) {  // AppText.railNext keeps it short enough for 172 px at 24
                Paint s = Ui.paint(data.railLive ? Ui.BOLD : Ui.REGULAR, 24, data.railLive ? Ui.LIVE_TEXT : Ui.alpha(ink, Ui.A2));
                AppKit.centre(cv, s, AppKit.cut(s, data.rail, 172), cx, 157.4f);
            }
        }
    }

    /** The Settings gear at the foot of the rail (44 px, 60 %; full when focused). */
    static final class Gear extends AppViews.Cell {
        int ink = Ui.TXT;
        Gear(Context c) { super(c); setContentDescription(I18n.t("settings")); }
        @Override float radius() { return 32; }
        @Override void paint(Canvas cv) {
            fill(cv);
            AppKit.gear(cv, 32, 32, 44, Ui.alpha(ink, 0.6f + 0.4f * focus));
        }
    }

    /**
     * A latest result as an object (84 tall, radius 16, white 7 %): sport glyph, the opponent's crest (flat), the name
     * over the date and competition, the score (home first) and the outcome pill.
     */
    static final class ResultTile extends AppViews.Cell {
        Match m;
        AppArt.Decal crest;
        boolean both, skeleton;
        float pulse = 1, skeletonW;

        ResultTile(Context c) { super(c); }

        @Override float radius() { return Ui.R_TILE; }

        void bind(Match m, AppArt.Decal crest, boolean both) {
            this.m = m; this.crest = crest; this.both = both; this.skeleton = false;
            setFocusable(true);
            setContentDescription(HomeModel.speak(m, HomeModel.Kind.RESULT, 0));
            invalidate();
        }

        void skeleton(float width) {
            this.skeleton = true; this.skeletonW = width; m = null;
            setFocusable(false);
            setContentDescription(null);
            invalidate();
        }

        static String name(Match m) { return I18n.team(m.opponentRaw()); }
        static String meta(Match m) { return AppText.shortDate(m.start) + " · " + I18n.comp(m.comp); }

        /** The tile's width for its content (maxText: the name and meta line are cut to it). */
        static float width(Match m, boolean both, float maxText) {
            float text = Math.min(maxText, Math.max(Ui.paint(Ui.MEDIUM, 30, 0).measureText(name(m)), Ui.paint(Ui.REGULAR, 24, 0).measureText(meta(m))));
            return 18 + (both ? 26 + 18 : 0) + 52 + 18 + text + 18 + 16 + AppKit.scoreWidth(m.hs, m.as, 58) + 10 + 18
                + AppKit.pillWidth(AppKit.outcomeWord(m.result()), true) + 22;
        }

        @Override void paint(Canvas cv) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (skeleton) {
                bg.setColor(Ui.alpha(0xFFFFFFFF, 0.06f * pulse));
                cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_TILE, Ui.R_TILE, bg);
                return;
            }
            if (m == null) return;
            bg.setColor(Ui.TONAL);
            cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_TILE, Ui.R_TILE, bg);
            fill(cv);
            float cy = 42, x = 18;
            if (both) { AppKit.sport(cv, m.team, x + 13, cy, 26, Ui.alpha(Ui.TXT, 0.7f)); x += 26 + 18; }
            if (crest != null) crest.draw(cv, x + 26, cy, null);
            x += 52 + 18;
            float pillW = AppKit.pillWidth(AppKit.outcomeWord(m.result()), true), scoreW = AppKit.scoreWidth(m.hs, m.as, 58);
            float textW = w() - x - 18 - 16 - scoreW - 10 - 18 - pillW - 22;
            Paint n = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            cv.drawText(AppKit.cut(n, name(m), textW), x, 634.2f - 598.05f, n);
            Paint mt = Ui.paint(Ui.REGULAR, 24, Ui.alpha(Ui.TXT, 0.6f));
            cv.drawText(AppKit.cut(mt, meta(m), textW), x, 668f - 598.05f, mt);
            x += textW + 18 + 16;
            AppKit.score(cv, m, x, Ui.baseline(Ui.COND_BOLD, 58, cy), 58, Ui.TXT, 1);
            x += scoreW + 10 + 18;
            AppKit.pill(cv, m.result(), x, cy, true, 1);
        }
    }

    /**
     * One fixture (60 tall, a 1 px divider above all but the first): day · time · sport glyph · crest, opponent and
     * home/away · competition · where to watch (sm, right-aligned). Columns 186 / 108 / 48 / 1fr / 300 / 330.
     */
    static final class FixtureRow extends AppViews.Cell {
        Match m;
        AppArt.Decal crest;
        AppKit.Watch watch;
        boolean both, first, skeleton;
        float pulse = 1;

        FixtureRow(Context c) { super(c); }

        /** Between "10:00" and its "pm", as at every hour. */
        static final float SUFFIX_GAP = 3;

        /**
         * The sport glyph's left edge in its 48 px column (x 312 → 360): at the column's start, as the spec draws it,
         * but centred (x 322) on a 12-hour clock, where "10:00 pm" needs those 10 px.
         */
        static float glyphLeft(boolean twelve) { return twelve ? 322 : 312; }

        /**
         * The time column's room from x 204: up to the sport glyph when the club plays both sports, else up to the
         * crest (x 360), less an 8 px gap. "10:00 pm" (RC Bold 34 + 24) is about 110 px: it fits either way.
         */
        static float timeRoom(boolean both, boolean twelve) { return (both ? glyphLeft(twelve) : 360) - 204 - 8; }

        /**
         * A 12-hour time keeps its "pm" only when the hour at RC Bold 34 and the suffix at 24 (the minimum) both fit;
         * otherwise the row shows the hour alone, never a shrunk hour or a suffix under 24 px (the hero and the match
         * screen carry am / pm).
         */
        static boolean keepSuffix(float hourW, float suffixW, float room) { return hourW + SUFFIX_GAP + suffixW <= room; }

        /**
         * Where a 12-hour time splits into the hour and its "pm": the first space, a no-break or a narrow no-break
         * space included (newer ICU puts U+202F before "PM"); -1 for a 24-hour time.
         */
        static int suffixAt(String t) {
            for (int i = 1; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == ' ' || c == '\u00A0' || c == '\u202F') return i;
            }
            return -1;
        }

        @Override float radius() { return Ui.R_ROW; }

        void bind(Match m, AppArt.Decal crest, AppKit.Watch w, boolean both, boolean first, long now) {
            this.m = m; this.crest = crest; this.watch = w; this.both = both; this.first = first; this.skeleton = false;
            setFocusable(true);
            setContentDescription(HomeModel.speak(m, "off".equals(m.state) ? HomeModel.Kind.OFF : HomeModel.Kind.NEXT, now));
            invalidate();
        }

        void skeleton(boolean first) {
            this.skeleton = true; this.first = first; m = null;
            setFocusable(false);
            setContentDescription(null);
            invalidate();
        }

        @Override void paint(Canvas cv) {
            if (!first) {
                Paint d = new Paint();
                d.setColor(0x1AFFFFFF);
                cv.drawRect(0, 0, w(), 1, d);
            }
            float cy = 30;
            if (skeleton) {
                AppKit.skeleton(cv, 18, cy - 13, 128, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 204, cy - 13, 274, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 360, cy - 13, 720, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 876, cy - 13, 1076, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 1356, cy - 21, 1506, cy + 21, 12, pulse);
                return;
            }
            if (m == null) return;
            fill(cv);
            Paint day = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            String ds = AppText.shortDate(m.start);
            AppKit.fit(day, ds, 180, 30, 24);
            cv.drawText(ds, 18, Ui.baseline(Ui.MEDIUM, day.getTextSize(), cy), day);
            boolean off = "off".equals(m.state);
            Paint tm = Ui.paint(Ui.COND_BOLD, 34, Ui.alpha(Ui.TXT, off || m.tbd ? 0.6f : 1f));
            String ts = off ? AppText.t("ppd") : m.tbd ? AppText.t("tba") : I18n.time(m.start);
            int sp = suffixAt(ts);
            float tb = Ui.baseline(Ui.COND_BOLD, 34, cy);
            boolean twelve = !I18n.clock24();
            float timeW = timeRoom(both, twelve);
            if (!off && !m.tbd && sp > 0) {  // 12-hour clocks: "5:30" at 34 with "pm" at 24 on its baseline
                Paint suf = Ui.paint(Ui.COND_BOLD, 24, Ui.alpha(Ui.TXT, 0.7f));
                String d = ts.substring(0, sp).trim(), a = ts.substring(sp + 1).trim();
                boolean keep = keepSuffix(tm.measureText(d), suf.measureText(a), timeW);
                if (!keep) AppKit.fit(tm, d, timeW, 34, 24);  // the hour alone: always fits at 34 in practice
                cv.drawText(d, 204, Ui.baseline(Ui.COND_BOLD, tm.getTextSize(), cy), tm);
                if (keep) cv.drawText(a, 204 + tm.measureText(d) + SUFFIX_GAP, tb, suf);
            } else {
                AppKit.fit(tm, ts, timeW, 34, 24);
                cv.drawText(ts, 204, Ui.baseline(Ui.COND_BOLD, tm.getTextSize(), cy), tm);
            }
            if (both) AppKit.sport(cv, m.team, glyphLeft(twelve) + 14, cy, 28, Ui.alpha(Ui.TXT, 0.8f));
            if (crest != null) crest.draw(cv, 360 + 21, cy, null);
            Paint ha = Ui.paint(Ui.REGULAR, 26, Ui.alpha(Ui.TXT, 0.55f));
            String has = AppText.t(m.clubHome() ? "home" : "away");
            Paint nm = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            float nx = 360 + 42 + 16, room = 876 - nx - 18 - ha.measureText(has) - 16;
            String name = AppKit.cut(nm, I18n.team(m.opponentRaw()), room);
            cv.drawText(name, nx, Ui.baseline(Ui.MEDIUM, 30, cy), nm);
            cv.drawText(has, nx + nm.measureText(name) + 18, Ui.baseline(Ui.REGULAR, 26, cy), ha);
            Paint cp = Ui.paint(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, Ui.A2));
            cv.drawText(AppKit.cut(cp, I18n.comp(m.comp), 290), 876, Ui.baseline(Ui.REGULAR, 28, cy), cp);
            if (watch != null && !off) AppKit.cell(cv, watch, AppKit.Size.SM, 1506, cy, true, 330, false, 1);
        }
    }
}
