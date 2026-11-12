package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clash for events (SPEC-F1-UFC §3.5), on ClashStyle's geometry: two wedges and the ink gap between them (its axis
 * 590 − 0.407 y), the rows A (cy 77), the competition (cy 166), the hero (cy 270), the clock row under it and C
 * (cy 463), everything in the gap 24 px square from both colour edges (ClashStyle.room). UFC is native: the red corner
 * left, the blue right, the first-billed fighter top-left and the other bottom-right with a face disc each; the gap is
 * the "versus", so there is no VS glyph. F1 is an adaptation that says where and what, never who against whom: the
 * place top-left, the session bottom-right, the circuit and the flag (or the focus driver's number) on the discs, the
 * left wedge graphite or the team's, the right the host flag's colour or the team's trim. Stream E owns this file.
 */
final class ClashEventStyle implements EventStyle {
    public String id() { return Appearance.CLASH; }

    /** Clash draws both sports (the owner may prefer Kit for F1: SPEC §13 choice 2; then this says false for F1). */
    public boolean suits(EventCardModel m, EventArt art) { return true; }

    static final float ROW_A = ClashStyle.ROW_A, ROW_C = ClashStyle.ROW_C, MID = ClashStyle.MID, COMP_CY = ClashStyle.COMP_CY;
    /**
     * The discs: 168, at (140, 284) and (820, 256): symmetric about the card's centre, 47 px square from the gap. The
     * spec's 134 / 826 put a disc's edge at x 50, inside the 56 px safe inset: moved in to the inset, as football
     * Clash's crests sit (SAFE_L + 84).
     */
    static final float DISC = 168, A_CX = ClashStyle.SAFE_L + DISC / 2, A_CY = 284, B_CX = ClashStyle.SAFE_R - DISC / 2, B_CY = 256;
    /** The names: RC Bold caps 56 → 40 (Clash's documented floor). */
    static final float NAME = 56, NAME_MIN = 40, NAME_FLOOR = 36;

    /** The channel line's start where it has a number chip (0.87 em: 45 px), and a chip's floor (the 44 px rule). */
    static final float CHIP_FOOT = 52, CHIP_MIN = 44;

    static float axis(float y) { return ClashStyle.axis(y); }
    static float room(float h) { return ClashStyle.room(h); }

    public void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art) {
        EventKit.View v = EventKit.view(m);
        int[] f = fields(v);
        cv.drawColor(ClubTokens.INK);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(f[0]);
        cv.drawPath(quad(0, ClashStyle.leftEdge(0), ClashStyle.leftEdge(Cards.H), 0), p);
        p.setColor(f[1]);
        cv.drawPath(quad(ClashStyle.rightEdge(0), Cards.W, Cards.W, ClashStyle.rightEdge(Cards.H)), p);
        discs(cv, v, art);
        names(cv, v, ClashStyle.ink(f[0]), ClashStyle.ink(f[1]));
        float aBottom = rowA(cv, v);
        competition(cv, v, aBottom);
        float above = hero(cv, v);
        above = clockRow(cv, v, above);
        rowC(cv, v, art, above);
    }

    /**
     * The two wedges: UFC red / blue; F1 graphite (or the focus team's colour deepened 10 %) on the left and the host
     * flag's colour (or the team's trim) on the right, each through ClashStyle.field (a white kit's capped grey, a
     * near-black lifted); a right field too close to the left one (ClashStyle.DISTINCT) is ivory's grey.
     */
    static int[] fields(EventKit.View v) {
        if (!v.f1) return new int[]{EventKit.RED_C, EventKit.BLUE_C};
        int left = v.team1 != 0 ? ClashStyle.field(ClubTokens.mix(v.team1, 0xFF000000, 0.10)) : EventKit.GRAPHITE_CLASH;
        int want = v.team1 != 0 ? v.trim : v.flagColour;
        int right = want == 0 ? ClashStyle.GREY : ClashStyle.field(want);
        if (ClashStyle.dist(left, right) <= ClashStyle.DISTINCT) right = ClashStyle.GREY;
        if (ClashStyle.dist(left, right) <= ClashStyle.DISTINCT) right = ClashStyle.SLATE;
        return new int[]{left, right};
    }

    private static Path quad(float tl, float tr, float br, float bl) {
        Path q = new Path();
        q.moveTo(tl, 0); q.lineTo(tr, 0); q.lineTo(br, Cards.H); q.lineTo(bl, Cards.H); q.close();
        return q;
    }

    /** The discs (#EDEAE3, flat): UFC the faces (both or neither); F1 the circuit left, the flag or the race number right. */
    private static void discs(Canvas cv, EventKit.View v, EventArt art) {
        boolean faces = !v.f1 && KitEventStyle.faces(v, art);
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? A_CX : B_CX, cy = i == 0 ? A_CY : B_CY;
            RectF r = new RectF(cx - DISC / 2, cy - DISC / 2, cx + DISC / 2, cy + DISC / 2);
            EventKit.plate(cv, r, 0, ClubTokens.IVORY_KIT, 0, 0, 0);
            if (v.f1) {
                if (i == 0) EventKit.f1Mark(cv, v, art, cx, cy, 124, 8, 104, 112, 10, false);
                else if (!v.number.isEmpty()) EventKit.number(cv, v.number, cx, cy, 104);
                else EventKit.flagMark(cv, v, art, cx, cy, 112, 10);
            } else {
                Bitmap h = faces ? art.headshot(i == 0 ? v.fa : v.fb) : null;
                EventKit.face(cv, h, i == 0 ? v.initialsA : v.initialsB, r, 0);
            }
        }
    }

    /** A's name top-left on row A, B's bottom-right on row C (F1: the place, the session), one size for both, 56 → 40. */
    private static void names(Canvas cv, EventKit.View v, int inkA, int inkB) {
        List<String> a = v.f1 ? EventKit.caps(v.place) : v.a, b = v.f1 ? EventKit.caps(v.session) : v.b;
        float aA = v.f1 ? 1 : v.aA, bA = v.f1 ? 1 : v.bA;
        Paint pa = EventKit.numerals(NAME, CardKit.alpha(inkA, aA), 0), pb = EventKit.numerals(NAME, CardKit.alpha(inkB, bA), 0);
        pa.setFontFeatureSettings(null); pb.setFontFeatureSettings(null);
        String sa = fitName(pa, a, true), sb = fitName(pb, b, false);
        float size = Math.min(pa.getTextSize(), pb.getTextSize());  // one size for both: they face off as equals
        pa.setTextSize(size); pb.setTextSize(size);
        if (!sa.isEmpty()) EventKit.text(cv, pa, sa, ClashStyle.SAFE_L, ROW_A + EventKit.cap(pa) / 2, -1);
        if (!sb.isEmpty()) EventKit.text(cv, pb, sb, ClashStyle.SAFE_R, ROW_C + EventKit.cap(pb) / 2, 1);
    }

    /** A name's room at a size: from the safe edge to 24 px square from its colour edge, at the name's own height. */
    static float nameRoom(float size, boolean top) {
        float c = EventKit.capOf(size);
        return top ? ClashStyle.leftEdge(ROW_A + c / 2) - ClashStyle.CLEAR_X - ClashStyle.SAFE_L
            : ClashStyle.SAFE_R - (ClashStyle.rightEdge(ROW_C - c / 2) + ClashStyle.CLEAR_X);
    }

    /**
     * A name's form and size: each form (the card name, then the tight one) from 56 down to 40; a single word that is
     * wider than the room even at 40 (KONDRATAVIČIUS, 14 letters) goes on to 36 rather than touch the colour edge.
     */
    private static String fitName(Paint p, List<String> names, boolean top) {
        if (names.isEmpty()) return "";
        for (String n : names)
            for (float s = NAME; s >= NAME_MIN; s -= 2) { p.setTextSize(s); if (p.measureText(n) <= nameRoom(s, top)) return n; }
        String n = names.get(names.size() - 1);
        float s = NAME_MIN;
        p.setTextSize(s);
        while (s > NAME_FLOOR && p.measureText(n) > nameRoom(s, top)) p.setTextSize(s -= 2);
        return n;
    }

    /** Row A in the gap: the LIVE pill (● 34/50 when the whole doesn't fit), a followed fighter's outcome pill, else the state words. */
    private static float rowA(Canvas cv, EventKit.View v) {
        float max = room(ClashStyle.PILL_H);
        if (v.live) {
            String s = v.pill;
            if (CardKit.pillWidth(s, Ui.BOLD, ClashStyle.PILL_MIN, true) > max) {
                String clock = s.startsWith(v.stateWords + " ") ? s.substring(v.stateWords.length() + 1) : "";
                s = clock.isEmpty() || CardKit.pillWidth(clock, Ui.BOLD, ClashStyle.PILL_MIN, true) > max ? v.stateWords : clock;
            }
            float size = pillSize(s, true, max), w = CardKit.pillWidth(s, Ui.BOLD, size, true);
            CardKit.pill(cv, s, axis(ROW_A) - w / 2, ROW_A, ClashStyle.PILL_H, Ui.BOLD, size, CardKit.LIVE, CardKit.WHITE, true, false);
            return ROW_A + ClashStyle.PILL_H / 2;
        }
        if (!v.outcomeWord.isEmpty()) {
            float size = pillSize(v.outcomeWord, false, max), w = CardKit.pillWidth(v.outcomeWord, Ui.BOLD, size, false);
            EventKit.outcomePill(cv, v, axis(ROW_A) - w / 2, ROW_A, ClashStyle.PILL_H, size, false);
            return ROW_A + ClashStyle.PILL_H / 2;
        }
        return words(cv, v.stateWords, ROW_A, CardKit.alpha(CardKit.WHITE, ClashStyle.WORD_A), ClashStyle.WORD, ClashStyle.WORD_FLOOR);
    }

    private static float pillSize(String s, boolean dot, float max) {
        float size = ClashStyle.PILL;
        while (CardKit.pillWidth(s, Ui.BOLD, size, dot) > max && size > ClashStyle.PILL_MIN) size -= 2;
        return size;
    }

    /**
     * Words centred on the axis at cy (GS Bold caps, tracking .06), fit the room from start to floor on one line, else
     * over two lines (row A downwards, row C upwards); returns their foot.
     */
    private static float words(Canvas cv, String s, float cy, int color, float start, float floor) {
        if (s == null || s.isEmpty()) return cy;
        Paint p = EventKit.paint(Ui.BOLD, start, color, ClashStyle.WORD_TRACK);
        float c = EventKit.cap(p);
        CardKit.fit(p, s, room(c), start, floor);
        c = EventKit.cap(p);
        int sp = middleSpace(s);
        if (p.measureText(s) > room(c) && sp > 0) {  // two lines: row A downwards, row C upwards
            String l1 = s.substring(0, sp), l2 = s.substring(sp + 1);
            float size = start;
            p.setTextSize(size);
            while (size > floor && Math.max(p.measureText(l1), p.measureText(l2)) > room(EventKit.cap(p))) p.setTextSize(size -= 2);
            c = EventKit.cap(p);
            float pitch = 1.24f * size, y1 = cy < MID ? cy : cy - pitch, y2 = y1 + pitch;  // the lines 24 apart (NOT / UPDATED)
            EventKit.text(cv, p, l1, axis(y1), y1 + c / 2, 0);
            EventKit.text(cv, p, l2, axis(y2), y2 + c / 2, 0);
            return y2 + c / 2;
        }
        EventKit.text(cv, p, s, axis(cy), cy + c / 2, 0);
        return cy + c / 2;
    }

    /** The space nearest the middle of s; -1 when none. */
    static int middleSpace(String s) {
        int best = -1;
        for (int i = s.indexOf(' '); i >= 0; i = s.indexOf(' ', i + 1))
            if (best < 0 || Math.abs(i - s.length() / 2) < Math.abs(best - s.length() / 2)) best = i;
        return best;
    }

    /**
     * The competition line (cy 166): F1 spells out FORMULA 1 (the one style with the room); UFC the eyebrow (UFC 334 ·
     * TITLE → UFC 334): RC Regular caps at 72 %, ClashStyle's steps, centred on the axis, never under 44, never cut.
     */
    private static void competition(Canvas cv, EventKit.View v, float above) {
        Paint probe = EventKit.paint(Ui.COND, ClashStyle.COMP, CardKit.WHITE, 0);
        float c = EventKit.cap(probe);
        if (COMP_CY - c / 2 - above < ClashStyle.CLEAR) return;
        EventKit.View w = v;
        if (v.f1) {
            w = new EventKit.View();
            w.eyebrow.add(Collections.singletonList(new EventCardModel.Seg(SportNames.w("formula1"), false)));
            w.eyebrow.add(Collections.singletonList(new EventCardModel.Seg(SportNames.w("f1"), false)));
        }
        EventKit.eyebrow(cv, w, Ui.COND, true, ClashStyle.COMP_A, CardKit.WHITE, ClashStyle.COMP_STEPS, axis(COMP_CY),
            Float.NaN, COMP_CY, room(c), 0);
    }

    /** The hero, its capitals centred on the middle line and on the axis there, fit to the room; returns its foot. */
    private static float hero(Canvas cv, EventKit.View v) {
        switch (v.hero) {
            case TIME: {
                float size = 136;
                while (size > 100 && EventKit.timeSize(v, room(EventKit.capOf(size)), size, size - 2) < size) size -= 2;
                EventKit.drawTime(cv, v, axis(MID), base(size), 0, room(EventKit.capOf(size)), size, size);
                return base(size);
            }
            case WORD: {
                float size = fitIn(v.word, 110, 76, CardKit.WHITE);
                Paint p = EventKit.numerals(size, CardKit.alpha(CardKit.WHITE, v.wordA), 0);
                p.setFontFeatureSettings(null);
                EventKit.text(cv, p, v.word, axis(MID), base(size), 0);
                return base(size);
            }
            case CODE: {
                float start = v.pos.isEmpty() ? 136 : 118, min = v.pos.isEmpty() ? 100 : 84;
                EventKit.HeroFit f = fitCode(v, start, min);
                EventKit.drawCode(cv, v, f, axis(MID), base(f.size), 0);
                return base(f.size);
            }
            case TEAM: {
                float c = EventKit.capOf(80), b = MID + c + EventKit.TEAM_GAP / 2;
                EventKit.drawTeam(cv, v, axis(MID), b, 0, room(2 * c + EventKit.TEAM_GAP), 80, 56);
                return b;
            }
            case METHOD: case ROUND: {
                EventKit.HeroFit f = new EventKit.HeroFit();
                f.rows = false;
                String keep = v.outcomeWord;
                v.outcomeWord = "";  // the outcome is row A's pill here
                for (f.size = 118; f.size > 76 && EventKit.wordWidth(v, f.size, false, false) > room(EventKit.capOf(f.size)); f.size -= 2) { }
                f.width = EventKit.wordWidth(v, f.size, false, false);
                EventKit.drawWord(cv, v, f, axis(MID), base(f.size), 0);
                v.outcomeWord = keep;
                return base(f.size);
            }
            case SLOT: {
                float size = 118;
                while (1.55f * size > room(EventKit.capOf(size)) && size > 76) size -= 2;
                EventKit.slot(cv, axis(MID), base(size), size, 0);
                return base(size);
            }
            default: return MID;
        }
    }

    private static float base(float size) { return MID + EventKit.capOf(size) / 2; }

    private static float fitIn(String s, float start, float min, int color) {
        Paint p = EventKit.numerals(start, color, -0.005f);
        float size = start;
        while (size > min && p.measureText(s) > room(EventKit.cap(p))) { p.setTextSize(size -= 2); }
        return size;
    }

    private static EventKit.HeroFit fitCode(EventKit.View v, float start, float min) {
        EventKit.HeroFit f = new EventKit.HeroFit();
        f.rows = false;
        f.size = start;
        while (f.size > min && EventKit.codeWidth(v, f.size, false) > room(EventKit.capOf(f.size))) f.size -= 2;
        f.width = EventKit.codeWidth(v, f.size, false);
        return f;
    }

    /**
     * The clock row, 24 px under the hero (ClashStyle.clock's rule): F1's P2, P3 (RC 46: `2 ▌NOR  3 ▌ANT`) for a podium
     * or a focus; UFC's `R3 · 2:27` / `OF 5` (RC Bold 48 at 88 %). Returns the foot of what was drawn.
     */
    private static float clockRow(Canvas cv, EventKit.View v, float above) {
        if (v.f1 && v.hero == EventKit.HeroKind.CODE && !v.rows.isEmpty()) {
            Paint num = EventKit.paint(Ui.COND, 46, CardKit.alpha(CardKit.WHITE, 0.60f), 0);
            Paint code = EventKit.numerals(46, CardKit.alpha(CardKit.WHITE, 0.90f), 0);
            float c = EventKit.cap(code), cy = above + ClashStyle.CLEAR + 3 + c / 2, b = cy + c / 2;
            float w = 0;
            for (EventKit.Row r : v.rows) w += num.measureText(r.pos) + 8 + 8 + 8 + code.measureText(r.code);
            w += 22 * (v.rows.size() - 1);
            if (w > room(c)) return above;
            float x = axis(cy) - w / 2;
            for (EventKit.Row r : v.rows) {
                x += EventKit.text(cv, num, r.pos, x, b, -1) + 8;
                EventKit.bar(cv, x, b, c, 8, r.color);
                x += 16;
                x += EventKit.text(cv, code, r.code, x, b, -1) + 22;
            }
            return b;
        }
        if (!v.f1 && !v.caption.isEmpty()) {
            Paint p = EventKit.numerals(48, CardKit.alpha(CardKit.WHITE, 0.88f), 0.02f);
            p.setFontFeatureSettings(null);
            float c = EventKit.cap(p);
            CardKit.fit(p, v.caption, room(c), 48, 40);
            android.graphics.Rect ink = new android.graphics.Rect();
            p.getTextBounds(v.caption, 0, v.caption.length(), ink);
            float base = above + ClashStyle.CLEAR + 2 - ink.top;  // its ink's top 26 under the hero's baseline
            EventKit.text(cv, p, v.caption, axis(base - EventKit.cap(p) / 2), base, 0);
            return base + Math.max(0, ink.bottom);
        }
        return above;
    }

    /**
     * Row C in the gap (cy 463): the amber warning when stale; where to watch before and during (the shared channel
     * line when it fits the room, else the channel's name as words over two lines); a result's or an off event's date
     * (RC Regular 48, 60 %; 45 % dimmed). above: the foot of what is over it (kept 24 px clear).
     */
    private static void rowC(Canvas cv, EventKit.View v, EventArt art, float above) {
        if (v.stale) {  // never under 44: NOT / UPDATED over two lines sooner than NOT UPDATED at 42
            words(cv, Cards.caps(I18n.t("not_updated").replace("⚠", "").trim()), ROW_C, CardKit.AMBER, ClashStyle.WORD, ClashStyle.WORD_MIN);
            return;
        }
        if (v.date) {
            if (v.dateText.isEmpty()) return;
            Paint p = EventKit.paint(Ui.COND, ClashStyle.FOOT, CardKit.alpha(CardKit.WHITE, v.dateDim ? ClashStyle.DATE_DIM_A : ClashStyle.DATE_A), 0.05f);
            float c = EventKit.cap(p);
            CardKit.fit(p, v.dateText, room(c), ClashStyle.FOOT, ClashStyle.FOOT_MIN);
            EventKit.text(cv, p, v.dateText, axis(ROW_C), ROW_C + EventKit.cap(p) / 2, 0);
            return;
        }
        if (!v.channel || art == null) return;
        CardModel.Channel ch = v.shim.channel;
        float max = room(ClashStyle.LOGO_H);
        if (!ch.namesOnly) {
            // a number chip is GS Bold 0.87 em of the line (CardKit.channel, shared with football's Clash, left as it
            // is): this style starts its line at CHIP_FOOT where there is a chip, so the number is 45, not 42
            float start = ClashStyle.FOOT;
            for (Channels.Logo lg : art.logos()) if (lg.no > 0) start = CHIP_FOOT;
            CardKit.ChannelSet s = CardKit.fitChannel(v.shim, art.cardArt(), start, max, ClashStyle.LOGO_H);
            if (s.fits) {
                float w = CardKit.channel(null, v.shim, art.cardArt(), 0, 0, s, ClashStyle.LOGO_H, max, false, 0);
                CardKit.channel(cv, v.shim, art.cardArt(), axis(ROW_C) - w / 2, ROW_C, s, ClashStyle.LOGO_H, max, false,
                    CardKit.alpha(CardKit.WHITE, ClashStyle.WORDS_A));
                return;
            }
        }
        // words over (at most) two lines, each centred on the axis at its own height, the number chip on the last
        String name = ch.namesOnly ? ch.bareWords() : Cards.caps(ch.names.get(0));
        String chip = !ch.namesOnly && ch.number > 0 ? String.valueOf(ch.number) : "";
        Paint p = EventKit.paint(Ui.COND, ClashStyle.FOOT, CardKit.alpha(CardKit.WHITE, ClashStyle.WORDS_A), 0.05f);
        Paint num = EventKit.paint(Ui.BOLD, Math.max(CHIP_MIN, ClashStyle.FOOT * 0.87f), CardKit.WHITE, 0);
        List<String> lines = new ArrayList<>();
        int sp = name.lastIndexOf(' ');
        if (sp > 0) { lines.add(name.substring(0, sp)); lines.add(name.substring(sp + 1)); } else lines.add(name);
        float c = EventKit.cap(p), pitch = 60;
        float y0 = lines.size() > 1 ? ROW_C - pitch / 2 : ROW_C;
        for (int i = 0; i < lines.size(); i++) {
            float cy = y0 + i * pitch;
            boolean last = i == lines.size() - 1;
            float cw = last && !chip.isEmpty() ? 16 + num.measureText(chip) + 32 : 0;
            CardKit.fit(p, lines.get(i), room(c) - cw, ClashStyle.FOOT, ClashStyle.FOOT_MIN);
            float w = p.measureText(lines.get(i)) + cw, x = axis(cy) - w / 2;
            x += EventKit.text(cv, p, lines.get(i), x, cy + c / 2, -1);
            if (cw > 0) {
                x += 16;
                float bw = num.measureText(chip) + 32, bh = EventKit.cap(num) + 22;
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
                cv.drawRoundRect(new RectF(x, cy - bh / 2, x + bw, cy + bh / 2), 12, 12, bg);
                cv.drawText(chip, x + 16, cy + EventKit.cap(num) / 2, num);
            }
        }
    }
}
