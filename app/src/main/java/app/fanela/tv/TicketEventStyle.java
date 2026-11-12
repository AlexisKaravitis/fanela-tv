package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.List;

/**
 * Ticket for events (SPEC-F1-UFC §3.4), on TicketStyle's body (x 0–600) and stub (inner 624–936, centre 780). The
 * body is the stock (the focus team's colour deepened 24 %, else the black-kit stock #1E2126) with the eyebrow over it
 * and a 14 px edge (the team's trim or warm grey; UFC red then blue). F1's body holds the circuit in ink and the
 * session under it; UFC's the two face discs with VS between and the surnames under them. The stub keeps football's
 * three tiers: the state word or pill, the hero, and where to watch, a caption, or the date. Stream E owns this file.
 */
final class TicketEventStyle implements EventStyle {
    public String id() { return Appearance.TICKET; }

    public boolean suits(EventCardModel m, EventArt art) { return true; }

    static final float CX = TicketStyle.STUB_CX, IN = TicketStyle.STUB_IN, BODY_CX = TicketStyle.BODY_W / 2;
    static final float GS_ASC = TicketStyle.GS_ASC, GS_DESC = TicketStyle.GS_DESC;
    /** The stub: the word on y 73; the hero's box top 188 alone (time), or its baseline 268 with a caption at 350. */
    static final float WORD_CY = 73, CODE_BASE = 268, CAPTION_BASE = 350, DATE_TOP = 392, DATE_TOP_ALONE = 306;
    /** UFC's discs: 176 at (154, 228) and (446, 228); the surnames' baseline 410. */
    static final float FACE = 176, FACE_CY = 228, FACE_A_CX = 154, FACE_B_CX = 446, NAME_BASE = 410;

    public void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art) {
        EventKit.View v = EventKit.view(m);
        int stock = v.f1 && v.team1 != 0 ? t.stock : EventKit.GRAPHITE_STOCK;
        int ink = v.f1 && v.team1 != 0 ? t.stockInk : CardKit.WHITE;
        body(cv, v, art, stock, ink);
        stub(cv, v, art);
        perforation(cv, stock);
    }

    // ================================================================ body
    private static void body(Canvas cv, EventKit.View v, EventArt art, int stock, int ink) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(stock);
        cv.drawRect(0, 0, TicketStyle.BODY_W, Cards.H, p);
        // the eyebrow: GS Bold caps, LAYOUT-COMPETITION §8's steps, max 500, 80 % ink (the gold segment at 100 %)
        if (v.stale) staleEyebrow(cv, ink);
        else  // its box top 36 (TicketStyle.EYEBROW_TOP): the baseline of a 46 px line; a 44 px step sits 2 px higher
            EventKit.eyebrow(cv, v, Ui.BOLD, true, 0.80f, ink, TicketStyle.EYEBROW_STEPS, 58,
                TicketStyle.baselineNormal(TicketStyle.EYEBROW_TOP, 46, GS_ASC), Float.NaN, 500, -1);
        if (v.f1) {
            if (!EventKit.circuit(cv, art, v.circuitId, new RectF(150, 118, 450, 348), 13, CardKit.alpha(ink, 0.95f)))
                EventKit.flagMark(cv, v, art, BODY_CX, 233, 240, 14);
            Paint s = EventKit.numerals(72, ink, 0);
            s.setFontFeatureSettings(null);
            String w = EventKit.fitList(s, EventKit.caps(v.session), 480, 72, 46);
            EventKit.text(cv, s, w, BODY_CX, 446, 0);
        } else {
            boolean faces = KitEventStyle.faces(v, art);
            for (int i = 0; i < 2; i++) {
                float cx = i == 0 ? FACE_A_CX : FACE_B_CX;
                RectF r = new RectF(cx - FACE / 2, FACE_CY - FACE / 2, cx + FACE / 2, FACE_CY + FACE / 2);
                EventKit.plate(cv, r, 0, ClubTokens.IVORY_TICKET, 10, 8, 0.35f);
                Bitmap h = faces ? art.headshot(i == 0 ? v.fa : v.fb) : null;
                EventKit.face(cv, h, i == 0 ? v.initialsA : v.initialsB, r, 0);
            }
            names(cv, v, ink);
            Paint vs = EventKit.paint(Ui.COND, 60, CardKit.alpha(ink, EventKit.VS_A), 0);
            EventKit.text(cv, vs, CardKit.VS, BODY_CX, FACE_CY + EventKit.cap(vs) / 2, 0);
        }
        // the edge
        if (v.f1) {
            p.setColor(v.trim != 0 ? v.trim : EventKit.IVORY_EDGE);
            cv.drawRect(0, Cards.H - TicketStyle.EDGE_H, TicketStyle.BODY_W, Cards.H, p);
        } else {
            p.setColor(EventKit.RED_C);
            cv.drawRect(0, Cards.H - TicketStyle.EDGE_H, BODY_CX, Cards.H, p);
            p.setColor(EventKit.BLUE_C);
            cv.drawRect(BODY_CX, Cards.H - TicketStyle.EDGE_H, TicketStyle.BODY_W, Cards.H, p);
        }
    }

    /**
     * The surnames: RC Bold 64 → 44, each fit to its half of the body (250 of it), then both at the smaller size (one
     * size for the pair), centred under their discs and moved inward when wider than a disc's centred room
     * (CleanStyle.nameLeft: never past the body's 40 px margins). A name wider than its half even at 44
     * (KONDRATAVIČIUS) borrows from the other half, the two kept 24 apart, rather than run under the stub.
     */
    private static void names(Canvas cv, EventKit.View v, int ink) {
        Paint pa = EventKit.numerals(64, CardKit.alpha(ink, v.aA), 0), pb = EventKit.numerals(64, CardKit.alpha(ink, v.bA), 0);
        pa.setFontFeatureSettings(null);
        pb.setFontFeatureSettings(null);
        String a = EventKit.fitList(pa, v.a, 250, 64, 44), b = EventKit.fitList(pb, v.b, 250, 64, 44);
        float size = Math.min(a.isEmpty() ? 64 : pa.getTextSize(), b.isEmpty() ? 64 : pb.getTextSize());
        pa.setTextSize(size);
        pb.setTextSize(size);
        float wa = pa.measureText(a), wb = pb.measureText(b), half = TicketStyle.BODY_W / 2, l = 40, r = TicketStyle.BODY_W - 40;
        float la = CleanStyle.nameLeft(FACE_A_CX, wa, l, half - 12), lb = CleanStyle.nameLeft(FACE_B_CX, wb, half + 12, r);
        if (la + wa > half - 12 || lb + wb > r) {  // the long pair shares the body: each as close to its disc as fits
            la = CleanStyle.nameLeft(FACE_A_CX, wa, l, r - wb - 24);
            lb = CleanStyle.nameLeft(FACE_B_CX, wb, Math.min(la + wa + 24, r - wb), r);
        }
        cv.save();
        cv.clipRect(l, 0, r, Cards.H);  // whatever the feed sends, never under the stub
        if (!a.isEmpty()) cv.drawText(a, la, NAME_BASE, pa);
        if (!b.isEmpty()) cv.drawText(b, lb, NAME_BASE, pb);
        cv.restore();
    }

    /** The stale warning's floor (the 44 px text rule). */
    static final float STALE_MIN = 44;

    /** Stale: the not-updated warning in the eyebrow's place (amber), as football's Ticket. */
    private static void staleEyebrow(Canvas cv, int ink) {
        String label = Cards.caps(I18n.t("not_updated").replace("⚠", "").trim());
        // never under 44 (ΧΩΡΙΣ ΕΝΗΜΕΡΩΣΗ was 40): the eyebrow's tracking eases (.12, .06, 0) before the size goes down
        Paint p = EventKit.paint(Ui.BOLD, TicketStyle.EYEBROW, CardKit.AMBER, 0.12f);
        float s = TicketStyle.EYEBROW;
        for (float track : new float[]{0.12f, 0.06f, 0f}) {
            p.setLetterSpacing(track);
            s = CardKit.fit(p, label, 450, TicketStyle.EYEBROW, STALE_MIN);
            if (p.measureText(label) <= 450) break;
        }
        if (p.measureText(label) > 450) s = CardKit.fit(p, label, 450, TicketStyle.EYEBROW, TicketStyle.EYEBROW_MIN);
        float cap = CardKit.cap(p);
        float base = TicketStyle.baselineNormal(TicketStyle.EYEBROW_TOP, s, GS_ASC);
        Cards.warning(cv, 58, base, cap, p);
        cv.drawText(CardKit.cut(p, label, 450), 58 + cap * 1.12f + 14, base, p);
    }

    // ================================================================ stub
    private static void stub(Canvas cv, EventKit.View v, EventArt art) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ClubTokens.INK);
        cv.drawRect(TicketStyle.STUB_X, 0, Cards.W, Cards.H, p);
        // tier 1: the pill (LIVE alone: the lap is the caption) or a followed fighter's outcome, else the state word
        if (v.live) {
            String s = CardModel.w("live");
            float w = CardKit.pillWidth(s, Ui.BOLD, 48, true);
            CardKit.pill(cv, s, CX - w / 2, WORD_CY, 66, Ui.BOLD, 48, CardKit.LIVE, CardKit.WHITE, true, false);
        } else if (!v.outcomeWord.isEmpty()) {
            float w = CardKit.pillWidth(v.outcomeWord, Ui.BOLD, 48, false);
            EventKit.outcomePill(cv, v, CX - w / 2, WORD_CY, 66, 48, false);
        } else word(cv, v.stateWords);
        // tier 2: the hero; tier 3: the caption, the date or where to watch
        String caption = "";
        boolean rows = false;
        switch (v.hero) {
            case TIME: {
                if (v.live && !v.caption.isEmpty()) {  // the lap under it: the time on the code's baseline, at its size
                    float size = EventKit.timeSize(v, IN, 124, 56);
                    EventKit.drawTime(cv, v, CX, CODE_BASE, 0, IN, size, size);
                    caption = v.caption;
                    break;
                }
                // alone: its box top 188 (TicketStyle.HERO_TOP_ALONE); over a date (cancelled, postponed): 140, as football's OFF
                float size = EventKit.timeSize(v, IN, 150, 56);
                float top = v.date ? 140 : TicketStyle.HERO_TOP_ALONE;
                EventKit.drawTime(cv, v, CX, TicketStyle.baseline(top, size, size, TicketStyle.RC_ASC, TicketStyle.RC_DESC), 0, IN, size, size);
                break;
            }
            case WORD: {
                EventKit.drawWordAlone(cv, v, CX, CODE_BASE, 0, IN, 110, 64);
                break;
            }
            case CODE: {
                EventKit.HeroFit f = EventKit.fitHero(v, IN, 124, 64, 64, false);  // LEC DNF needs 76
                EventKit.drawCode(cv, v, f, CX, CODE_BASE, 0);
                rows = !v.live && !v.rows.isEmpty();  // live: the lap is the caption (LAP 34/50), never the order
                if (v.live) caption = v.caption;
                break;
            }
            case TEAM: EventKit.drawTeam(cv, v, CX, CODE_BASE + 30, 0, IN, 80, 56); rows = false; caption = " "; break;
            case METHOD: case ROUND: {
                String keep = v.outcomeWord;
                v.outcomeWord = "";  // the outcome is the stub's pill here
                EventKit.HeroFit f = EventKit.fitHero(v, IN, 124, 64, 64, false);
                f.rows = false;
                f.width = EventKit.wordWidth(v, f.size, false, false);
                EventKit.drawWord(cv, v, f, CX, CODE_BASE, 0);
                v.outcomeWord = keep;
                caption = v.caption;
                break;
            }
            case SLOT: EventKit.slot(cv, CX, CODE_BASE, 124, 0); break;
            default: break;
        }
        if (rows) captionRows(cv, v.rows);
        else if (!caption.trim().isEmpty()) caption(cv, caption, CAPTION_BASE, 0.90f);
        boolean below = rows || !caption.isEmpty();
        if (v.stale) return;  // the eyebrow says it
        if (v.date) {
            if (!v.dateText.isEmpty()) caption(cv, v.dateText, TicketStyle.baselineNormal(below ? DATE_TOP : DATE_TOP_ALONE, 50, GS_ASC),
                v.dateDim ? 0.40f : 0.72f);
            return;
        }
        if (v.channel && art != null) channel(cv, v, art);
    }

    /** The state word: TicketStyle's (GS Bold 48, tracking .06, 75 %, centred on y 73; two lines under 40). */
    private static void word(Canvas cv, String s) {
        if (s == null || s.isEmpty()) return;
        final Paint p = EventKit.paint(Ui.BOLD, TicketStyle.WORD, CardKit.alpha(CardKit.WHITE, 0.75f), 0);
        TicketStyle.Word w = TicketStyle.fitWord(s, (x, size, track) -> { p.setTextSize(size); p.setLetterSpacing(track); return p.measureText(x); });
        p.setTextSize(w.size);
        p.setLetterSpacing(w.track);
        for (int i = 0; i < w.lines.length; i++) {
            float cy = i == 0 ? TicketStyle.WORD_CY : TicketStyle.WORD_CY2;
            EventKit.text(cv, p, w.lines[i], CX, cy + (GS_ASC - GS_DESC) / 2 * w.size, 0);
        }
    }

    /** A caption line: GS Bold 50 → 32, tracking .06, centred on the stub, on baseline. */
    private static void caption(Canvas cv, String s, float base, float alpha) {
        Paint p = EventKit.paint(Ui.BOLD, 50, CardKit.alpha(CardKit.WHITE, alpha), 0.06f);
        CardKit.fit(p, s, IN, 50, 32);
        EventKit.text(cv, p, s, CX, base, 0);
    }

    /** P2, P3 under the hero: `2 ▌NOR  3 ▌ANT` (RC 50: the place Regular at 60 %, the code Bold at 90 %). */
    private static void captionRows(Canvas cv, List<EventKit.Row> rows) {
        Paint num = EventKit.paint(Ui.COND, 50, CardKit.alpha(CardKit.WHITE, 0.60f), 0);
        Paint code = EventKit.numerals(50, CardKit.alpha(CardKit.WHITE, 0.90f), 0);
        float size = 50;
        while (size > 40 && rowsWidth(rows, num, code) > IN) { num.setTextSize(size -= 2); code.setTextSize(size); }
        float c = EventKit.cap(code), x = CX - rowsWidth(rows, num, code) / 2;
        for (EventKit.Row r : rows) {
            x += EventKit.text(cv, num, r.pos, x, CAPTION_BASE, -1) + 10;
            EventKit.bar(cv, x, CAPTION_BASE, c, 8, r.color);
            x += 18;
            x += EventKit.text(cv, code, r.code, x, CAPTION_BASE, -1) + 30;
        }
    }

    private static float rowsWidth(List<EventKit.Row> rows, Paint num, Paint code) {
        float w = 0;
        for (EventKit.Row r : rows) w += num.measureText(r.pos) + 10 + 8 + 10 + code.measureText(r.code);
        return w + 30 * (rows.size() - 1);
    }

    /**
     * Where to watch at the stub's foot (bottom 36): the shared channel line (CardKit) when it fits the stub's 312 at
     * 44 or more; else the channel's name over two lines of words (GS Bold 44 → 32, tracking .04), the number chip on
     * the last.
     */
    private static void channel(Canvas cv, EventKit.View v, EventArt art) {
        CardModel.Channel ch = v.shim.channel;
        if (ch == null || ch.names.isEmpty()) return;
        if (!ch.namesOnly) {  // the logo and the number chip, as football's Ticket shows them, when they fit the stub
            CardKit.ChannelSet s = CardKit.fitChannel(v.shim, art.cardArt(), 44, IN, 56);
            if (s.fits && s.form == CardKit.FULL) {
                float w = CardKit.channel(null, v.shim, art.cardArt(), 0, 0, s, 56, IN, false, 0);
                CardKit.channel(cv, v.shim, art.cardArt(), CX - w / 2, Cards.H - 36 - 28, s, 56, IN, false, CardKit.alpha(CardKit.WHITE, 0.92f));
                return;
            }
        }
        String name = ch.namesOnly ? ch.bareWords() : Cards.caps(ch.names.get(0));
        int no = ch.namesOnly ? 0 : ch.number;
        Paint p = EventKit.paint(Ui.BOLD, 44, CardKit.alpha(CardKit.WHITE, 0.92f), 0.04f);
        Paint num = EventKit.paint(Ui.BOLD, 38, CardKit.WHITE, 0);
        String chip = no > 0 ? String.valueOf(no) : "";
        String[] lines = {name};
        float size = 44;
        if (lineWidth(p, num, name, chip) > IN) {
            int sp = name.lastIndexOf(' ');
            if (sp > 0) lines = new String[]{name.substring(0, sp), name.substring(sp + 1)};
        }
        while (size > 32 && maxWidth(p, num, lines, chip) > IN) p.setTextSize(size -= 2);
        float base = Cards.H - 36, pitch = size * 1.1f + (chip.isEmpty() ? 0 : 14);
        float y = base - (lines.length - 1) * pitch;
        for (int i = 0; i < lines.length; i++, y += pitch) {
            boolean last = i == lines.length - 1;
            String c = last ? chip : "";
            float w = lineWidth(p, num, lines[i], c), x = CX - w / 2;
            x += EventKit.text(cv, p, lines[i], x, y, -1);
            if (!c.isEmpty()) {
                x += 12;
                float cw = num.measureText(c) + 32;
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(CardKit.alpha(CardKit.WHITE, 0.16f));
                cv.drawRoundRect(new RectF(x, y - size * 0.82f, x + cw, y + size * 0.16f), 10, 10, bg);
                cv.drawText(c, x + 16, y, num);
            }
        }
    }

    private static float lineWidth(Paint p, Paint num, String s, String chip) {
        return p.measureText(s) + (chip.isEmpty() ? 0 : 12 + num.measureText(chip) + 32);
    }

    private static float maxWidth(Paint p, Paint num, String[] lines, String chip) {
        float w = 0;
        for (int i = 0; i < lines.length; i++) w = Math.max(w, lineWidth(p, num, lines[i], i == lines.length - 1 ? chip : ""));
        return w;
    }

    // ================================================================ perforation (TicketStyle's, in the ticket's inks)
    private static void perforation(Canvas cv, int stock) {
        int body = TicketStyle.perfBody(stock), stub = TicketStyle.perfStub();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int side = 0; side < 2; side++) {
            cv.save();
            if (side == 0) cv.clipRect(0, 0, TicketStyle.STUB_X, Cards.H); else cv.clipRect(TicketStyle.STUB_X, 0, Cards.W, Cards.H);
            p.setColor(side == 0 ? body : stub);
            for (float y = 0; y < Cards.H; y += TicketStyle.DASH + TicketStyle.DASH_GAP)
                cv.drawRect(TicketStyle.PERF_X, y, TicketStyle.PERF_X + TicketStyle.PERF_W, Math.min(y + TicketStyle.DASH, Cards.H), p);
            cv.drawCircle(TicketStyle.STUB_X, 0, TicketStyle.NOTCH_R, p);
            cv.drawCircle(TicketStyle.STUB_X, Cards.H, TicketStyle.NOTCH_R, p);
            cv.restore();
        }
    }
}
