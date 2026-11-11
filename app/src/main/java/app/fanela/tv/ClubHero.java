package app.fanela.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Club layout's hero (SPEC-APP 3.2-3.4): one view, 1524 x 420 at (300, 112), drawing the club's next match (crests,
 * fixture, relative time, the big kick-off, the date and where to watch), a fresh result, a postponement, the live
 * strip (B's grammar: LIVE cell, my club's block, the score well, the opponent), the skeleton while loading, or a
 * sentence when there is nothing to show. A live update only changes what it draws, never the focus.
 */
final class ClubHero extends AppViews.Cell {
    /** The content box inside the hero's padding (28 / 40 / 26 / 36), local spec px. */
    static final float L = 36, R = 1484, T = 28;

    HomeModel.ClubData data;
    HomeModel.Kind kind = HomeModel.Kind.LOADING;
    Match m;
    long now;
    int ink = Ui.TXT;
    AppKit.Watch watch;
    AppArt.Decal home, away, mine;
    /** The live hint: OK opens the channel's app ("opens Magenta TV"), else the match details. */
    String okLabel = "";
    float pulse = 1;
    /** Score change: when it happened (the new digits slide up 180 ms and flash for 600 ms). */
    private long scoreAt;
    private String lastScoreKey;

    ClubHero(Context c) { super(c); }

    @Override float radius() { return Ui.R_HERO; }

    void bind(HomeModel.ClubData d, long now, AppKit.Watch w, AppArt.Decal home, AppArt.Decal away, AppArt.Decal mine, String okLabel) {
        Match prev = m;
        this.data = d;
        this.kind = d.kind;
        this.m = d.hero;
        this.now = now;
        this.watch = w;
        this.home = home;
        this.away = away;
        this.mine = mine;
        this.okLabel = okLabel;
        this.ink = Ui.TXT;  // every field is dark (white kits too: FieldColors)
        this.onLight = false;
        String sk = m == null ? null : m.key + ":" + m.hs + ":" + m.as;
        if (kind == HomeModel.Kind.LIVE && prev != null && m != null && prev.key.equals(m.key) && lastScoreKey != null && !lastScoreKey.equals(sk))
            scoreAt = System.currentTimeMillis();
        lastScoreKey = sk;
        setContentDescription(describe());
        invalidate();
    }

    String describe() {
        if (m == null) return data == null ? "" : data.club.label() + ". " + (kind == HomeModel.Kind.FAILED ? I18n.t("load_failed")
            : kind == HomeModel.Kind.LOADING ? I18n.t("loading_club") : I18n.t("no_fixtures"));
        return HomeModel.speak(m, kind, now);
    }

    @Override void paint(Canvas cv) {
        fill(cv);
        switch (kind) {
            case LOADING: skeleton(cv); break;
            case LIVE: live(cv); break;
            case NONE: case FAILED: nothing(cv); break;
            default: next(cv); break;
        }
    }

    // ---------- next / result / postponed ----------
    /** The eyebrow on the kit field: the text colour at 80 %, lifted where the field can't carry it (FieldColors). */
    private int eyebrowInk() { return data != null && data.colors != null ? data.colors.eyebrow : Ui.alpha(ink, 0.8f); }

    private void next(Canvas cv) {
        boolean both = data.bothSports;
        Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, eyebrowInk(), Ui.TRACK_EYEBROW);
        cv.drawText(AppKit.cut(eb, HomeModel.eyebrow(kind, m, both), 860), L - AppKit.lead(eb), 53.1f, eb);
        crestsAndTitle(cv, home, away, m.home, m.away);
        // the when column, right-aligned at R
        Paint rel = AppKit.text(Ui.COND_BOLD, Ui.T_REL, ink);
        Paint big = AppKit.text(Ui.COND_BOLD, Ui.T_DISPLAY, ink);
        big.setLetterSpacing(-0.015f);
        Paint date = AppKit.text(Ui.REGULAR, 32, Ui.alpha(ink, Ui.A2));
        if (kind == HomeModel.Kind.RESULT) {
            AppKit.right(cv, rel, AppKit.outcomeWord(m.result()).isEmpty() ? AppText.t("full_time")
                : I18n.t(m.result() > 0 ? "win" : m.result() == 0 ? "draw" : "loss"), R, 89.5f);
            float w = AppKit.scoreWidth(m.hs, m.as, Ui.T_DISPLAY);
            AppKit.score(cv, m, R - w, 269.7f, Ui.T_DISPLAY, ink, 1);
            AppKit.right(cv, date, AppText.longDate(m.start), R, 322.9f);
            return;
        }
        AppKit.right(cv, rel, AppText.relative(now, m.start), R, 89.5f);
        if (kind == HomeModel.Kind.OFF) {
            AppKit.right(cv, big, "—", R, 269.7f);
        } else if (m.tbd) {
            AppKit.right(cv, rel, AppText.t("time_tba"), R, 230f);
        } else {
            String t = I18n.time(m.start);
            int sp = t.indexOf(' ');
            if (sp > 0) {  // 12-hour clocks: "9:15" big, "PM" beside it on the same baseline
                Paint suf = AppKit.text(Ui.COND_BOLD, 72, Ui.alpha(ink, 0.75f));
                float sw = AppKit.right(cv, suf, t.substring(sp + 1), R, 269.7f);
                AppKit.right(cv, big, t.substring(0, sp), R - sw - 14, 269.7f);
            } else AppKit.right(cv, big, t, R, 269.7f);
        }
        AppKit.right(cv, date, AppText.longDate(m.start), R, 322.9f);
        if (kind == HomeModel.Kind.NEXT && watch != null) {
            float cy = 378.06f;
            float cw = AppKit.cell(cv, watch, AppKit.Size.MD, R, cy, true, 560, onLight, 1);
            if (watch.app != null && watch.kind != AppKit.Watch.GUIDE) {
                Paint t = AppKit.text(Ui.REGULAR, 30, Ui.alpha(ink, Ui.A3));
                float bl = Ui.baseline(Ui.REGULAR, 30, cy);
                float dw = AppKit.right(cv, t, "·", R - cw - 16, bl);
                t.setColor(Ui.alpha(ink, Ui.A2));
                AppKit.right(cv, t, watch.app, R - cw - 16 - dw - 16, bl);
            }
        }
    }

    /**
     * Both crests (196, home first) with the VS mark between (AppKit.vs), and the fixture under them (RC Bold 80 down
     * to 60 in 860, the same mark between the names: AppKit.drawTitle): a long name gives way to the short forms the
     * cards use before anything is cut (AppKit.title), so both clubs are always named ("Μπορούσια Μένχενγκλαντμπαχ"
     * -> "Γκλάντμπαχ").
     */
    private void crestsAndTitle(Canvas cv, AppArt.Decal h, AppArt.Decal a, String homeRaw, String awayRaw) {
        float top = 82.55f, size = 196, cy = top + size / 2;
        if (h != null) h.draw(cv, L + size / 2, cy, null);
        float vs = AppKit.vsForCrests(size), vx = L + size + AppKit.VS_CREST_AIR;
        float ax = vx + CardKit.vs(cv, CardKit.VS, vx, cy, vs, AppKit.vsInk(ink), false) + AppKit.VS_CREST_AIR;
        if (a != null) a.draw(cv, ax + size / 2, cy, null);
        Paint tp = AppKit.text(Ui.COND_BOLD, Ui.T_TITLE, ink);
        tp.setLetterSpacing(-0.005f);
        AppKit.Title t = AppKit.title(AppKit.measure(tp), AppKit.forms(homeRaw), AppKit.forms(awayRaw), 860, Ui.T_TITLE, Ui.T_TITLE_MIN);
        AppKit.drawTitle(cv, tp, t, L, 304.55f + 0.8635f * t.size, 860);
    }

    // ---------- live: the strip ----------
    private void live(Canvas cv) {
        boolean both = data.bothSports;
        Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, Ui.alpha(Ui.TXT, 0.8f), Ui.TRACK_EYEBROW);
        cv.drawText(AppKit.cut(eb, HomeModel.liveEyebrow(m, both), 1440), L - AppKit.lead(eb), 59.8f, eb);
        RectF strip = new RectF(L, 104, R, 304);
        float cy = strip.centerY();
        cv.save();
        Path clip = new Path();
        clip.addRoundRect(strip, Ui.R_STRIP, Ui.R_STRIP, Path.Direction.CW);
        cv.clipPath(clip);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x0FFFFFFF);
        cv.drawRect(strip, p);
        // the LIVE cell: LIVE over the phase (Q3, 67′, HT)
        float dcW = 136;
        float[] fit = m.hasScore() ? scoreFit(AppKit.scoreWidth(m.hs, m.as, Ui.T_SCORE_STRIP)) : new float[]{Ui.T_SCORE_STRIP, WELL_W};
        float scoreSize = fit[0], wellW = fit[1];
        p.setColor(Ui.LIVE);
        cv.drawRect(strip.left, strip.top, strip.left + dcW, strip.bottom, p);
        Paint lv = AppKit.caps(Ui.BOLD, 24, Ui.TXT, Ui.TRACK_EYEBROW);
        String phase = HomeModel.phase(m);
        float dcx = strip.left + dcW / 2;
        if (phase.isEmpty()) AppKit.centre(cv, lv, I18n.t("live"), dcx - AppKit.lead(lv), Ui.baseline(Ui.BOLD, 24, cy));
        else {
            AppKit.centre(cv, lv, I18n.t("live"), dcx - AppKit.lead(lv), 182.2f);
            Paint ph = AppKit.text(Ui.COND_BOLD, 56, Ui.TXT);
            AppKit.fit(ph, phase, dcW - 16, 56, 34);
            AppKit.centre(cv, ph, AppKit.cut(ph, phase, dcW - 16), dcx, 242.8f);  // never out of the red cell
        }
        // the two clubs: mine on its gradient, the opponent neutral; home on the left
        boolean mineLeft = m.clubHome();
        Paint np = AppKit.text(Ui.COND_BOLD, Ui.T_STRIP_NAME, Ui.TXT);
        float room = (R - L) - dcW - wellW;  // both blocks
        float crest = 120, pad = 30, gap = 26;
        // two names too long for the blocks even at 44 take the cards' short forms first (the longer name first)
        String[] names = AppKit.pair(AppKit.measure(np), AppKit.forms(mineLeft ? m.home : m.away), AppKit.forms(mineLeft ? m.away : m.home),
            room - 2 * (pad + crest + gap + pad), 44);
        String myName = names[0], opName = names[1];
        np.setTextSize(Ui.T_STRIP_NAME);
        float leftNameW = np.measureText(mineLeft ? myName : opName), rightNameW = np.measureText(mineLeft ? opName : myName);
        float leftW = Math.min(560, pad + crest + gap + leftNameW + pad);
        float rightNeed = pad + crest + gap + rightNameW + pad;
        if (leftW + rightNeed > room) {  // long names: both shrink together
            float k = (room - 2 * (pad + crest + gap + pad) - 16) / Math.max(1, leftNameW + rightNameW);  // 16: text is not quite linear in size
            np.setTextSize(Math.max(44, (float) Math.floor(Ui.T_STRIP_NAME * Math.min(1, k))));  // whole px: both names fit, never cut by a hair
            leftNameW = np.measureText(mineLeft ? myName : opName);
            leftW = Math.min(560, pad + crest + gap + leftNameW + pad);
        }
        float lx = strip.left + dcW, wellX = lx + leftW, rx = wellX + wellW;
        FieldColors fc = data.colors;
        RectF mineBox = mineLeft ? new RectF(lx, strip.top, wellX, strip.bottom) : new RectF(rx, strip.top, strip.right, strip.bottom);
        if (fc != null) {
            p.setShader(new LinearGradient(mineBox.left, 0, mineBox.right, 0, fc.white ? 0xFF5A5650 : fc.black ? 0xFF3A3A40 : FieldColors.hsv(fc.primary, 0.92, 0.89),
                fc.field, Shader.TileMode.CLAMP));
            cv.drawRect(mineBox, p);
            p.setShader(null);
        }
        int mineInk = Ui.TXT;
        p.setColor(0x47000000);
        cv.drawRect(wellX, strip.top, rx, strip.bottom, p);
        cv.restore();
        float nameBl = Ui.baseline(Ui.COND_BOLD, np.getTextSize(), cy);
        // left block: crest then name; right block: name then crest (crests at the outer edges)
        AppArt.Decal leftCrest = home, rightCrest = away;
        if (leftCrest != null) leftCrest.draw(cv, lx + pad + crest / 2, cy, null);
        np.setColor(mineLeft ? mineInk : Ui.TXT);
        float maxL = leftW - pad - crest - gap - pad;
        cv.drawText(AppKit.cut(np, mineLeft ? myName : opName, maxL), lx + pad + crest + gap, nameBl, np);
        if (rightCrest != null) rightCrest.draw(cv, strip.right - pad - crest / 2, cy, null);
        np.setColor(mineLeft ? Ui.TXT : mineInk);
        float maxR = strip.right - rx - pad - crest - gap - pad;
        String rn = AppKit.cut(np, mineLeft ? opName : myName, maxR);
        AppKit.right(cv, np, rn, strip.right - pad - crest - gap, nameBl);
        // the well: the score (home first, mine full) and the clock in live red
        float wcx = wellX + wellW / 2;
        if (m.hasScore()) {
            float sw = AppKit.scoreWidth(m.hs, m.as, scoreSize);
            float slide = 0, flash = 0;
            long dt = System.currentTimeMillis() - scoreAt;
            if (scoreAt > 0 && dt < Ui.FLASH_MS) {
                float t = Math.min(1, dt / (float) Ui.SCORE_MS);
                slide = (1 - Ui.DECELERATE.getInterpolation(t)) * 40;
                flash = 1 - dt / (float) Ui.FLASH_MS;
                postInvalidateOnAnimation();
            }
            cv.save();
            cv.clipRect(wellX, strip.top, rx, strip.bottom);
            AppKit.score(cv, m, wcx - sw / 2, 235.8f + slide, scoreSize, Ui.TXT, 1);
            if (flash > 0) {  // the new number flashes to full white
                Paint f = AppKit.text(Ui.COND_BOLD, scoreSize, Ui.alpha(0xFFFFFFFF, flash * 0.5f));
                cv.drawText(m.hs + "–" + m.as, wcx - sw / 2, 235.8f + slide, f);
            }
            cv.restore();
        }
        String clock = HomeModel.clock(m);
        if (!clock.isEmpty()) {
            Paint ck = AppKit.text(Ui.COND_BOLD, 34, Ui.LIVE_TEXT);
            ck.setLetterSpacing(0.02f);
            AppKit.centre(cv, ck, clock, wcx, m.hasScore() ? 296.2f : Ui.baseline(Ui.COND_BOLD, 34, cy));
        }
        // the sub-line: who leads, the phase and clock, the channel; the OK hint at the right
        float scy = 351;
        Paint sub = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.9f));
        float bl = Ui.baseline(Ui.REGULAR, 28, scy), x = L;
        float hintW = okLabel.isEmpty() ? 0 : AppKit.okHint(cv, okLabel, R, scy, Ui.TXT);
        String line = HomeModel.liveLine(m);
        List<String> parts = new ArrayList<>();
        for (String s : line.split(" · ", 2)) if (!s.isEmpty()) parts.add(s);
        Paint dot = AppKit.text(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.36f));
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) { x += 14; cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 14; }
            String s = AppKit.cut(sub, parts.get(i), R - hintW - 40 - x - 340);
            cv.drawText(s, x, bl, sub);
            x += sub.measureText(s);
        }
        if (watch != null) {
            if (!parts.isEmpty()) { x += 14; cv.drawText("·", x, bl, dot); x += dot.measureText("·") + 14; }
            AppKit.cell(cv, watch, AppKit.Size.SM, x, scy, false, Math.max(120, R - hintW - 40 - x), false, 1);
        }
    }

    /** The score well's width with a two-digit score, its margin each side, and the score's smallest size. */
    static final float WELL_W = 400, WELL_PAD = 28, SCORE_MIN = 120;

    /**
     * The live score's size and the well's width (QA3A-3), from the score's width at 150: 150 in the 400 well while it
     * fits with 28 either side ("61–58"); a wider score (basketball's "110–108") steps down to fit, to 120 at the least,
     * and the well widens by whatever it still needs, so the score is never clipped.
     */
    static float[] scoreFit(float widthAt150) {
        float size = Ui.T_SCORE_STRIP, room = WELL_W - 2 * WELL_PAD;
        if (widthAt150 <= room) return new float[]{size, WELL_W};
        size = Math.max(SCORE_MIN, (float) Math.floor(size * room / widthAt150));
        float w = widthAt150 * size / Ui.T_SCORE_STRIP;
        return new float[]{size, Math.max(WELL_W, (float) Math.ceil(w + 2 * WELL_PAD))};
    }

    // ---------- nothing to show ----------
    private void nothing(Canvas cv) {
        Paint eb = AppKit.caps(Ui.MEDIUM, Ui.T_EYEBROW, eyebrowInk(), Ui.TRACK_EYEBROW);
        cv.drawText(Cards.caps(data.club.heading()), L - AppKit.lead(eb), 53.1f, eb);
        if (mine != null) mine.draw(cv, L + 98, 82.55f + 98, null);
        Paint tp = AppKit.text(Ui.COND_BOLD, Ui.T_TITLE, ink);
        String title = data.club.label();
        float sz = AppKit.fit(tp, title, 860, Ui.T_TITLE, Ui.T_TITLE_MIN);
        cv.drawText(AppKit.cut(tp, title, 860), L, 304.55f + 0.8635f * sz, tp);
        boolean failed = kind == HomeModel.Kind.FAILED;
        String msg = I18n.t(failed ? "load_failed" : "no_fixtures");
        Paint mp = failed ? AppKit.text(Ui.REGULAR, 30, Ui.alpha(ink, Ui.A2)) : AppKit.text(Ui.COND_BOLD, Ui.T_REL, ink);
        float lh = failed ? 42 : 64, y = failed ? 80 : 89.5f;
        for (String line : wrap(mp, msg, 560)) { AppKit.right(cv, mp, line, R, y); y += lh; }
    }

    static List<String> wrap(Paint p, String s, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ---------- loading ----------
    private void skeleton(Canvas cv) {
        float a = pulse;
        AppKit.skeleton(cv, L, 28, L + 420, 52, 12, a);
        // the who column's slots (its first line is the 24 px block, so they sit 8.55 px higher than the loaded crests)
        AppKit.skeleton(cv, L, 74, L + 196, 270, 98, a);
        float vx = L + 196 + AppKit.VS_CREST_AIR;  // the VS mark between the two crest slots, as loaded (crestsAndTitle)
        float ax = vx + CardKit.vs(cv, CardKit.VS, vx, 172, AppKit.vsForCrests(196), Ui.alpha(Ui.TXT, 0.2f), false) + AppKit.VS_CREST_AIR;
        AppKit.skeleton(cv, ax, 74, ax + 196, 270, 98, a);
        AppKit.skeleton(cv, L, 304, L + 760, 392, 12, a);
        AppKit.skeleton(cv, R - 260, 42, R, 90, 12, a);
        AppKit.skeleton(cv, R - 440, 106, R, 276, 12, a);
        AppKit.skeleton(cv, R - 330, 292, R, 320, 12, a);
        AppKit.skeleton(cv, R - 300, 342, R, 394, 12, a);
    }
}
