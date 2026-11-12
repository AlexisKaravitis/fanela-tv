package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.List;

/**
 * Clean for events (SPEC-F1-UFC §3.2), on CleanStyle's bands: the eyebrow at x 56 (GS Medium, mixed case) and the one
 * pill ending at x 904, both centred on y 77; two plates 188 at x 56 and 716 (y 134–322); the hero's capitals centred
 * on y 228 between them (424 box); the band under the plates (baseline 396); the foot centred on y 464. F1: the
 * circuit on the left plate and the host's flag on the right, the session (or P2, P3 as two small rows) on the band;
 * UFC: the two headshots (initials when either is missing) and the two surnames under them, the loser at 45 %. Stream
 * E owns this file.
 */
final class CleanEventStyle implements EventStyle {
    public String id() { return Appearance.CLEAN; }

    public boolean suits(EventCardModel m, EventArt art) { return true; }

    static final float CX = CleanStyle.HERO_CX, PLATE = CleanStyle.PLATE, PLATE_TOP = CleanStyle.PLATE_TOP,
        PLATE_CY = CleanStyle.PLATE_CY, BAND_BASE = 396, BOX = CleanStyle.HERO_BOX;

    public void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art) {
        EventKit.View v = EventKit.view(m);
        field(cv, v, t);
        float pillW = pill(cv, v);
        EventKit.eyebrow(cv, v, Ui.MEDIUM, false, CleanStyle.COMP_A, CardKit.WHITE, EventKit.steps(CleanStyle.COMP_TRACK),
            CleanStyle.COMP_X, Float.NaN, CleanStyle.PILL_CY, (CleanStyle.PILL_R - CleanStyle.COMP_X) - CleanStyle.COMP_GAP - pillW, -1);
        plates(cv, v, art);
        hero(cv, v);
        band(cv, v);
        foot(cv, v, art);
    }

    /** The base gradient, the lights (F1: one, graphite or the team's; UFC: red left, blue right), grain, vignette. */
    private static void field(Canvas cv, EventKit.View v, ClubTokens t) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, Cards.H, CleanStyle.BASE_TOP, CleanStyle.BASE_BOTTOM, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, bg);
        if (v.f1) {
            if (v.team1 != 0) CleanStyle.light(cv, 134, CleanStyle.LIGHT_CY, new CleanStyle.Light(t.light, 0.50f, 0.22f));
            else CleanStyle.light(cv, 134, CleanStyle.LIGHT_CY, new CleanStyle.Light(EventKit.GRAPHITE, 0.20f, 0.08f));
        } else {
            CleanStyle.light(cv, 826, CleanStyle.LIGHT_CY, new CleanStyle.Light(EventKit.BLUE_C, 0.62f, 0.26f));
            CleanStyle.light(cv, 134, CleanStyle.LIGHT_CY, new CleanStyle.Light(EventKit.RED_C, 0.62f, 0.26f));
        }
        CardKit.grain(cv, 10);
        Paint vg = new Paint();
        vg.setShader(new LinearGradient(0, 0, 0, Cards.H, new int[]{0x40000000, 0, 0, 0x59000000},
            new float[]{0, 0.30f, 0.70f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, Cards.W, Cards.H, vg);
    }

    /** The one pill: the day / FINAL / CANCELLED (neutral), LIVE (red), a followed fighter's WIN / DRAW / LOSS. */
    private static float pill(Canvas cv, EventKit.View v) {
        CleanStyle.Pill p = new CleanStyle.Pill();
        p.h = CleanStyle.PILL_H; p.size = CleanStyle.PILL_SIZE; p.cy = CleanStyle.PILL_CY;
        p.bg = CardKit.NEUTRAL_PILL; p.fg = CardKit.NEUTRAL_TEXT;
        p.s = v.stateWords;
        if (v.live) { p.s = v.pill; p.bg = CardKit.LIVE; p.fg = CardKit.WHITE; p.dot = true; }
        else if (!v.outcomeWord.isEmpty()) {
            p.s = v.outcomeWord;
            p.bg = v.outcome > 0 ? CardKit.WIN : v.outcome == 0 ? CardKit.DRAW : CardKit.LOSS;
            p.fg = v.outcome < 0 ? CardKit.LOSS_TEXT : CardKit.WHITE;
        }
        if (p.s == null || p.s.isEmpty()) return 0;
        while (CardKit.pillWidth(p.s, Ui.BOLD, p.size, p.dot) > CleanStyle.PILL_MAX && p.size > CleanStyle.PILL_MIN) p.size -= 2;
        float w = CardKit.pillWidth(p.s, Ui.BOLD, p.size, p.dot);
        p.left = CleanStyle.PILL_R - w; p.right = CleanStyle.PILL_R; p.top = p.cy - p.h / 2; p.bottom = p.cy + p.h / 2;
        p.draw(cv);
        return w;
    }

    /** The plates: F1 the circuit (left) and the flag (right); UFC the two faces, both or neither. */
    private static void plates(Canvas cv, EventKit.View v, EventArt art) {
        boolean faces = !v.f1 && KitEventStyle.faces(v, art);
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? CleanStyle.PLATE_L_CX : CleanStyle.PLATE_R_CX;
            RectF r = new RectF(cx - PLATE / 2, PLATE_TOP, cx + PLATE / 2, PLATE_TOP + PLATE);
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            pp.setColor(ClubTokens.IVORY_PLATE);
            pp.setShadowLayer(25, 0, 14, 0x73000000);
            cv.drawRoundRect(r, CleanStyle.PLATE_R, CleanStyle.PLATE_R, pp);
            if (v.f1) {
                if (i == 0) {  // the circuit; without one the plate stays plain (the flag is already on the right plate)
                    RectF box = new RectF(cx - 74, PLATE_CY - 74, cx + 74, PLATE_CY + 74);
                    EventKit.circuit(cv, art, v.circuitId, box, 9, EventKit.INK_DISC);
                } else EventKit.flagMark(cv, v, art, cx, PLATE_CY, 148, 14);
            } else {
                Bitmap h = faces ? art.headshot(i == 0 ? v.fa : v.fb) : null;
                EventKit.face(cv, h, i == 0 ? v.initialsA : v.initialsB, r, CleanStyle.PLATE_R);
            }
        }
    }

    /** The hero's baseline for a size: its capitals centred on the plates' centre line (y 228). */
    private static float base(float size) { return PLATE_CY + EventKit.capOf(size) / 2; }

    /** The hero between the plates: time 176; the leader 176 / the focus 150 (no rows: they go on the band); the method 150; the slot. */
    private static void hero(Canvas cv, EventKit.View v) {
        switch (v.hero) {
            case TIME: EventKit.drawTime(cv, v, CX, base(176), 0, BOX, 176, 130); break;
            case WORD: {
                EventKit.drawWordAlone(cv, v, CX, base(124), 0, BOX, 124, 72);
                break;
            }
            case CODE: {
                float start = v.pos.isEmpty() ? 176 : 150, min = v.pos.isEmpty() ? 130 : 110;
                EventKit.HeroFit f = EventKit.fitHero(v, BOX, start, min, min, false);
                EventKit.drawCode(cv, v, f, CX, base(f.size), 0);
                break;
            }
            case TEAM: EventKit.drawTeam(cv, v, CX, base(96) + (EventKit.capOf(96) + EventKit.TEAM_GAP) / 2, 0, BOX, 96, 80); break;
            case METHOD: case ROUND: {
                String keep = v.outcomeWord;
                v.outcomeWord = "";  // the outcome is the header's pill here
                EventKit.HeroFit f = EventKit.fitHero(v, BOX, 150, 96, 96, false);
                f.rows = false;
                EventKit.drawWord(cv, v, f, CX, base(f.size), 0);
                v.outcomeWord = keep;
                break;
            }
            case SLOT: EventKit.slot(cv, CX, base(176), 176, 0); break;
            default: break;
        }
    }

    /**
     * The band under the plates (baseline 396): F1 the session (GS Bold 56, mixed case) before and while untrusted
     * live, the two small rows (2 ▌NOR   3 ▌ANT) for a podium or a focus, nothing for a slot; UFC the surnames, each
     * centred under its plate (CleanStyle.nameLeft), the loser at 45 %.
     */
    private static void band(Canvas cv, EventKit.View v) {
        if (v.f1) {
            if (v.hero == EventKit.HeroKind.CODE && !v.rows.isEmpty()) { rows(cv, v.rows); return; }
            if (v.hero == EventKit.HeroKind.SLOT || v.hero == EventKit.HeroKind.TEAM || v.session.isEmpty()) return;
            Paint p = EventKit.paint(Ui.BOLD, 56, CardKit.WHITE, 0.01f);
            String s = EventKit.fitList(p, v.session, 840, 56, 44);
            EventKit.text(cv, p, s, CX, BAND_BASE, 0);
            return;
        }
        // the pair at one size on one baseline: each fits its box (56 -> 44), then both take the smaller size
        Paint pa = EventKit.paint(Ui.BOLD, 56, CardKit.alpha(CardKit.WHITE, v.aA), 0.01f);
        Paint pb = EventKit.paint(Ui.BOLD, 56, CardKit.alpha(CardKit.WHITE, v.bA), 0.01f);
        String a = v.aMixed.isEmpty() ? "" : EventKit.fitList(pa, v.aMixed, CleanStyle.NAME_BOX, 56, 44);
        String b = v.bMixed.isEmpty() ? "" : EventKit.fitList(pb, v.bMixed, CleanStyle.NAME_BOX, 56, 44);
        float size = Math.min(a.isEmpty() ? 56 : pa.getTextSize(), b.isEmpty() ? 56 : pb.getTextSize());
        pa.setTextSize(size);
        pb.setTextSize(size);
        float base = CleanStyle.NAME_TOP + CleanStyle.GS_ASC * size;
        name(cv, pa, a, CleanStyle.PLATE_L_CX, base);
        name(cv, pb, b, CleanStyle.PLATE_R_CX, base);
    }

    private static void name(Canvas cv, Paint p, String s, float cx, float base) {
        if (s.isEmpty()) return;
        float w = p.measureText(s);
        cv.save();
        cv.clipRect(CleanStyle.SAFE_L, 0, CleanStyle.SAFE_R, Cards.H);
        cv.drawText(s, CleanStyle.nameLeft(cx, w, CleanStyle.SAFE_L, CleanStyle.SAFE_R), base, p);
        cv.restore();
    }

    /** P2, P3 on one line: the place (GS Medium 52 at 60 %), an 8 px bar, the code (GS Bold 56 at 92 %), 44 px apart. */
    private static void rows(Canvas cv, List<EventKit.Row> rows) {
        Paint num = EventKit.paint(Ui.MEDIUM, 52, CardKit.alpha(CardKit.WHITE, 0.60f), 0);
        Paint code = EventKit.paint(Ui.BOLD, 56, CardKit.alpha(CardKit.WHITE, 0.92f), 0.01f);
        float c = EventKit.cap(code), w = 0;
        for (EventKit.Row r : rows) w += num.measureText(r.pos) + 12 + 8 + 12 + code.measureText(r.code);
        w += 44 * (rows.size() - 1);
        float x = CX - w / 2;
        for (EventKit.Row r : rows) {
            x += EventKit.text(cv, num, r.pos, x, BAND_BASE, -1) + 12;
            EventKit.bar(cv, x, BAND_BASE, c, 8, r.color);
            x += 20;
            x += EventKit.text(cv, code, r.code, x, BAND_BASE, -1) + 44;
        }
    }

    // ---------- the foot (centred on y 464) ----------

    /**
     * NEXT / LIVE: where to watch (Clean's channel line, as CleanStyle draws it). RESULT / OFF: F1 `Race · Sun 22 Nov`
     * (the session moves here because the band holds P2, P3); UFC `R3 2:27 · Sun 15 Nov`, the date alone when
     * hidden; GS Medium 48 at 55 % (45 % dimmed). Stale: the amber warning.
     */
    private static void foot(Canvas cv, EventKit.View v, EventArt art) {
        if (v.stale) { stale(cv); return; }
        if (v.date) {
            String d = mixed(v.dateText), lead = "";
            if (v.f1 && !v.session.isEmpty()) lead = v.session.get(0);
            else if (!v.f1 && !v.hidden && v.result && !v.caption.isEmpty()) lead = v.caption.replace(" · ", " ");
            String s = lead.isEmpty() ? d : d.isEmpty() ? lead : lead + " · " + d;
            if (s.isEmpty()) return;
            Paint p = EventKit.paint(Ui.MEDIUM, CleanStyle.FOOT_SIZE,
                CardKit.alpha(CardKit.WHITE, v.dateDim ? CleanStyle.WHEN_DIM_A : CleanStyle.WHEN_A), 0.02f);
            CardKit.fit(p, s, CleanStyle.FOOT_BOX, CleanStyle.FOOT_SIZE, CleanStyle.FOOT_MIN);
            CardKit.centre(cv, p, s, CX, footBaseline(p.getTextSize()));
            return;
        }
        if (v.channel && art != null) channel(cv, v.shim, art.cardArt());
    }

    /** "SUN 22 NOV" → "Sun 22 Nov" (Clean writes mixed case): each word's first letter kept, the rest lower case. */
    static String mixed(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(w.isEmpty() ? w : w.substring(0, 1) + w.substring(1).toLowerCase(I18n.locale()));
        }
        return b.toString();
    }

    private static float footBaseline(float size) { return CleanStyle.FOOT_CY - 1.252f * size / 2 + CleanStyle.GS_ASC * size; }

    private static void stale(Canvas cv) {
        String s = I18n.t("not_updated").replace("⚠", "").trim();
        Paint p = EventKit.paint(Ui.BOLD, CleanStyle.FOOT_SIZE, CardKit.AMBER, 0);
        float cap = CardKit.cap(p), sign = cap * 1.12f + 16, w = sign + p.measureText(s), x = CX - w / 2;
        float base = footBaseline(CleanStyle.FOOT_SIZE);
        Cards.warning(cv, x, base, cap, p);
        cv.drawText(s, x + sign, base, p);
    }

    // CleanStyle's channel line (its foot()/fitFoot()/channel() are private to it): the same layout, copied.

    private static final class Foot {
        float size, total;
        String likely = "", words;
        Paint lead, word, num;
        Bitmap[] img;
        float[] w, h;
    }

    private static Foot foot(CardModel m, List<Channels.Logo> logos, Bitmap[] src, int n, float size, float logoW, int form) {
        CardModel.Channel ch = m.channel;
        Foot f = new Foot();
        f.size = size;
        f.lead = CleanStyle.words(Ui.MEDIUM, size, CardKit.alpha(CardKit.WHITE, CleanStyle.LIKELY_A));
        f.lead.setLetterSpacing(form == CardKit.TIGHT ? 0 : 0.04f);
        f.likely = ch.likely && form == CardKit.FULL ? CardModel.w("likely") : "";
        if (ch.namesOnly) {
            f.words = (f.likely.isEmpty() ? "" : f.likely + " ") + (form == CardKit.FULL ? ch.words() : ch.bareWords());
            f.total = f.lead.measureText(f.words);
            return f;
        }
        f.word = CardKit.text(Ui.BOLD, size, CardKit.WHITE);
        f.num = CardKit.text(Ui.BOLD, size * CleanStyle.CHIP_SIZE / CleanStyle.FOOT_SIZE, CardKit.WHITE);
        f.img = src.clone();
        f.w = new float[n];
        f.h = new float[n];
        float fixed = f.likely.isEmpty() ? 0 : f.lead.measureText(f.likely) + 18;
        for (int i = 0; i < n; i++) {
            fixed += (i > 0 ? 18 : 0) + (logos.get(i).no > 0 ? 18 + f.num.measureText(String.valueOf(logos.get(i).no)) + 32 : 0);
            if (f.img[i] != null) {
                float sc = Math.min(logoW / f.img[i].getWidth(), CleanStyle.LOGO_H / f.img[i].getHeight());
                f.w[i] = f.img[i].getWidth() * sc; f.h[i] = f.img[i].getHeight() * sc;
            } else f.w[i] = f.word.measureText(Cards.caps(ch.names.get(i)));
        }
        for (int i = 0; i < n; i++) {
            if (f.img[i] == null) continue;
            float others = fixed;
            for (int j = 0; j < n; j++) if (j != i) others += f.w[j];
            float[] lf = CardKit.logoFit(f.img[i].getWidth() / (float) f.img[i].getHeight(), logoW, CleanStyle.LOGO_H, CleanStyle.FOOT_BOX - others);
            if (lf == null) { f.img[i] = null; f.w[i] = f.word.measureText(Cards.caps(ch.names.get(i))); }
            else { f.w[i] = lf[0]; f.h[i] = lf[1]; }
        }
        f.total = fixed;
        for (int i = 0; i < n; i++) f.total += f.w[i];
        return f;
    }

    private static Foot fitFoot(CardModel m, List<Channels.Logo> logos, Bitmap[] img, int n) {
        Foot f = null;
        for (int form = CardKit.FULL; form <= CardKit.TIGHT; form++) {
            float cap = 280;
            for (float c : new float[]{280, 200, 180}) {
                f = foot(m, logos, img, n, CleanStyle.FOOT_SIZE, cap = c, form);
                if (f.total <= CleanStyle.FOOT_BOX) return f;
                if (m.channel.namesOnly) break;
            }
            for (float size = CleanStyle.FOOT_SIZE - 2; size >= CleanStyle.FOOT_MIN; size -= 2) {
                f = foot(m, logos, img, n, size, cap, form);
                if (f.total <= CleanStyle.FOOT_BOX) return f;
            }
        }
        return f;
    }

    private static void channel(Canvas cv, CardModel m, CardArt art) {
        CardModel.Channel ch = m.channel;
        if (ch == null) return;
        List<Channels.Logo> logos = ch.namesOnly || m.match == null ? new java.util.ArrayList<Channels.Logo>() : art.logos();
        int n = Math.min(Math.min(ch.names.size(), logos.size()), 2);
        if (n == 0 && !ch.namesOnly) return;
        Bitmap[] img = new Bitmap[n];
        for (int i = 0; i < n; i++) img[i] = art.trimmed(logos.get(i).url);
        Foot f = fitFoot(m, logos, img, n);
        float gap = 18, base = footBaseline(f.size), cy = CleanStyle.FOOT_CY;
        if (f.words != null) {
            CardKit.centre(cv, f.lead, CardKit.cut(f.lead, f.words, CleanStyle.FOOT_BOX), CX, base);
            return;
        }
        cv.save();
        cv.clipRect(CleanStyle.SAFE_L, 0, CleanStyle.SAFE_R, Cards.H);
        float x = CX - f.total / 2;
        if (!f.likely.isEmpty()) { cv.drawText(f.likely, x, base, f.lead); x += f.lead.measureText(f.likely) + gap; }
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < n; i++) {
            if (i > 0) x += gap;
            if (f.img[i] != null) {
                float w = f.w[i], h = f.h[i];
                Bitmap sm = Cards.smooth(f.img[i], w);
                lp.setAlpha(242);
                cv.drawBitmap(sm, null, new RectF(x, cy - h / 2, x + w, cy + h / 2), lp);
                if (sm != f.img[i]) sm.recycle();
                x += w;
            } else {
                String t = Cards.caps(ch.names.get(i));
                cv.drawText(t, x, base, f.word);
                x += f.word.measureText(t);
            }
            int no = logos.get(i).no;
            if (no > 0) {
                x += gap;
                String t = String.valueOf(no);
                float bw = f.num.measureText(t) + 32, bh = 1.25f * f.num.getTextSize() + 3, top = cy - bh / 2;
                Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
                chip.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
                cv.drawRoundRect(new RectF(x, top, x + bw, top + bh), 12, 12, chip);
                float ts = f.num.getTextSize();
                cv.drawText(t, x + 16, top + 3 + (1.25f * ts - 1.252f * ts) / 2 + CleanStyle.GS_ASC * ts, f.num);
                x += bw;
            }
        }
        cv.restore();
    }
}
