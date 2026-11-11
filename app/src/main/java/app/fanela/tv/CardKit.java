package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.List;
import java.util.Random;

/**
 * Drawing shared by every card style, so a pill, a crest or a channel block looks and behaves the same everywhere.
 * Nothing here recycles a bitmap it is given: CardArt owns the crests and logos and releases them after the card.
 * Sizes are in card pixels (960x540).
 */
final class CardKit {
    private CardKit() { }

    // ---------- the shared state colours (the same words and colours in every style) ----------
    static final int LIVE = 0xFFE8102A, WIN = 0xFF1FA24F, DRAW = 0xFF4A4F57, LOSS = 0xFF7A1F26, LOSS_TEXT = 0xFFFFB4B4,
        NEUTRAL_PILL = 0xFF2B2B30, NEUTRAL_TEXT = 0xFFF2F2F4,
        WHITE = 0xFFFFFFFF, INK = 0xFF111111, SOFT = 0xB3FFFFFF, AMBER = 0xFFF5A623;

    static Paint text(Typeface face, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        p.setFontFeatureSettings("tnum");  // tabular numerals: times and scores don't jump between cards
        return p;
    }

    /** Sets the paint's size to the largest ≤ size that fits maxW, never below floor; returns the size used. */
    static float fit(Paint p, String s, float maxW, float size, float floor) {
        p.setTextSize(size);
        while (p.measureText(s) > maxW && size > floor) p.setTextSize(size -= 2);
        return size;
    }

    /**
     * A club's name set by the shared rule (CardModel.fitName) in display capitals or as written: the paint's size is
     * left at the size chosen. box: the name's box; runOn: the room it may run on to; start, min: the style's sizes;
     * floor: only for a name no real club has.
     */
    static CardModel.NameFit name(final Paint p, List<String> names, boolean caps, float box, float runOn, float start,
                                  float min, float floor) {
        List<String> ns = new java.util.ArrayList<>();
        for (String n : names) ns.add(caps ? Cards.caps(n) : n);
        String ab = names.isEmpty() ? null : CardModel.abbreviation(names.get(0));
        if (ab != null && caps) ab = Cards.caps(ab);
        CardModel.NameFit f = CardModel.fitName((s, size) -> { p.setTextSize(size); return p.measureText(s); },
            ns, ab, box, runOn, start, min, floor);
        p.setTextSize(f.size);
        return f;
    }

    /** The text cut with "…" to fit maxW at the paint's current size. */
    static String cut(Paint p, String s, float maxW) { return Cards.ellipsize(p, s, maxW); }

    static float cap(Paint p) { return Cards.capHeight(p); }

    // ---------- the VS mark (owner, fx6: "VS instead of V is nicer", in every style and in the app) ----------
    /**
     * Between my club and the opponent: VS, or @ when my club plays away (Signature and Kit name only the opponent, so
     * the mark carries home or away; Google Sans's @ is as tall as its capitals, so the two read as one pair). Between
     * two clubs set in match order (Ticket, the app): always VS, the order tells who is at home.
     */
    static final String VS = "VS", AT = "@";
    /**
     * The mark on a card: Google Sans Bold capitals at 44 (the legibility floor: 18 px at the launcher's 0.404),
     * tracked .06 em, at 66 % white (the state line's secondary segment): a label, quieter than the names beside it.
     */
    static final float VS_SIZE = 44, VS_TRACK = 0.06f, VS_A = 0.66f;

    /** The mark's paint: Google Sans Bold, tracked, proportional figures. */
    static Paint vsPaint(float size, int color) {
        Paint p = text(Ui.BOLD, size, color);
        p.setFontFeatureSettings(null);
        p.setLetterSpacing(VS_TRACK);
        return p;
    }

    /** The mark's ink width at a size (the trailing tracking left out, so gaps are measured from the ink). */
    static float vsWidth(String mark, float size) {
        Paint p = vsPaint(size, WHITE);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds(mark, 0, mark.length(), r);
        return r.width();
    }

    /**
     * Draws the mark with its ink centred on cy, its ink's left edge at x (or its right edge, alignRight), and returns
     * its ink width. Centred by the ink, so VS and @ (which sits 2 px lower in Google Sans) share one centre line.
     */
    static float vs(Canvas cv, String mark, float x, float cy, float size, int color, boolean alignRight) {
        Paint p = vsPaint(size, color);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds(mark, 0, mark.length(), r);
        float left = alignRight ? x - r.width() : x;
        cv.drawText(mark, left - r.left, cy - (r.top + r.bottom) / 2f, p);
        return r.width();
    }

    /** Draws text with its right edge at x. */
    static void right(Canvas cv, Paint p, String s, float x, float baseline) {
        cv.drawText(s, x - p.measureText(s), baseline, p);
    }

    /** Draws text centred on cx. */
    static void centre(Canvas cv, Paint p, String s, float cx, float baseline) {
        cv.drawText(s, cx - p.measureText(s) / 2, baseline, p);
    }

    /**
     * The words on a pill: GS Bold caps, tracking .04 em, proportional figures (as the references set them: Google
     * Sans's tabular set widens "LIVE Q3 5:12" by ~20 px and "LIVE 67′" by ~9).
     */
    static Paint pillText(Typeface face, float size, int color) {
        Paint p = text(face, size, color);
        p.setFontFeatureSettings(null);
        p.setLetterSpacing(0.04f);
        return p;
    }

    /** Google Sans's vertical metrics as the references' browser lays a line out (typo ascender / descender, em). */
    static final float GS_ASC = 0.966f, GS_DESC = 0.286f;

    /**
     * A pill's baseline for centre line cy: the font's line box centred on the pill, as the references set it
     * (CSS inline-flex, align-items centre). This sits the capitals' band ~1 px above the pill's middle, which is
     * where the references have it; centring the bare cap height put the words ~2 px low.
     */
    static float pillBaseline(Paint p, float cy) { return cy + (GS_ASC - GS_DESC) / 2 * p.getTextSize(); }

    /**
     * A pill (spec §1.3): height h, text in caps at size, tracking .04 em, padding 0.6 em; with a live dot the left
     * padding is 0.48 em, the dot 0.42 em wide and 0.32 em from the text. alignRight: x is its right edge. Returns
     * its width.
     */
    static float pill(Canvas cv, String s, float x, float cy, float h, Typeface face, float size, int bg, int fg,
                      boolean dot, boolean alignRight) {
        Paint p = pillText(face, size, fg);
        float padR = 0.60f * size, padL = dot ? 0.48f * size : padR, dotD = dot ? 0.42f * size : 0, gap = dot ? 0.32f * size : 0;
        float w = padL + dotD + gap + p.measureText(s) + padR;
        if (alignRight) x -= w;
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setColor(bg);
        cv.drawRoundRect(new RectF(x, cy - h / 2, x + w, cy + h / 2), h / 2, h / 2, b);
        if (dot) cv.drawCircle(x + padL + dotD / 2, cy, dotD / 2, p);
        cv.drawText(s, x + padL + dotD + gap, pillBaseline(p, cy), p);
        return w;
    }

    /** The pill's width without drawing it. */
    static float pillWidth(String s, Typeface face, float size, boolean dot) {
        Paint p = pillText(face, size, WHITE);
        float padR = 0.60f * size, padL = dot ? 0.48f * size : padR;
        return padL + (dot ? 0.74f * size : 0) + p.measureText(s) + padR;
    }

    /** The live pill text: "LIVE 67′" / "LIVE HT" / "LIVE Q3 5:12" (just "LIVE" without a clock). */
    static String liveText(CardModel m) { return m.clock.isEmpty() ? m.stateLine : m.stateLine + " " + m.clock; }

    /** The live pill (spec: #E8102A, 66 h, 48 px, a white dot). */
    static float livePill(Canvas cv, CardModel m, float x, float cy, float h, float size, boolean alignRight) {
        return pill(cv, liveText(m), x, cy, h, Ui.BOLD, size, LIVE, WHITE, true, alignRight);
    }

    /** The outcome pill: WIN green, DRAW grey, LOSS wine (never club red). Returns 0 when there is no outcome. */
    static float outcomePill(Canvas cv, CardModel m, float x, float cy, float h, float size, boolean alignRight) {
        if (m.outcomeWord.isEmpty()) return 0;
        int bg = m.outcome > 0 ? WIN : m.outcome == 0 ? DRAW : LOSS;
        int fg = m.outcome < 0 ? LOSS_TEXT : WHITE;
        return pill(cv, m.outcomeWord, x, cy, h, Ui.BOLD, size, bg, fg, false, alignRight);
    }

    /**
     * The smallest a score hero may get, past each style's own minimum (spec: 140, Clean 130), so a three-digit
     * basketball score with its outcome pill (100–105 LOSS) stays inside the style's box instead of running into the
     * crests, plates or ghost or past the safe edge. Two-digit scores never reach it.
     */
    static final float SCORE_FLOOR = 110;

    // ---------- scores hidden (Settings: "Show scores on the home screen" off) ----------

    /** A blank numeral slot: half the numerals' size wide, as tall as their capitals, 18 % white (14 read as a gap at 0.404). */
    static final float SLOT_W = 0.50f, SLOT_R = 16f / 165f, SLOT_GAP = 0.03f;
    static final int SLOT = 0x2EFFFFFF;

    /**
     * The score's place when the viewer hides scores, the same in every style: two blank rounded slots (14 % white)
     * either side of the en dash (45 %), on the score's baseline at the score's size, so a result never leaks and the
     * card keeps its shape. No outcome pill goes with it; the style's own state element (LIVE with the clock, FINAL)
     * says what happened. align: x is the left edge, the right edge or the centre. Returns the width.
     */
    static float hiddenScore(Canvas cv, float x, float baseline, float size, Paint.Align align) {
        Paint dash = text(Ui.COND_BOLD, size, alpha(WHITE, 0.45f));
        dash.setLetterSpacing(-0.01f);
        float w = hiddenScoreWidth(dash, size), cap = cap(dash), slot = SLOT_W * size, r = SLOT_R * size;
        float l = align == Paint.Align.RIGHT ? x - w : align == Paint.Align.CENTER ? x - w / 2 : x;
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setColor(SLOT);
        cv.drawRoundRect(new RectF(l, baseline - cap, l + slot, baseline), r, r, b);
        float dx = l + slot + SLOT_GAP * size;
        cv.drawText("–", dx, baseline, dash);
        float r0 = dx + dash.measureText("–") + SLOT_GAP * size;
        cv.drawRoundRect(new RectF(r0, baseline - cap, r0 + slot, baseline), r, r, b);
        return w;
    }

    /** The hidden score's width at a numeral size. */
    static float hiddenScoreWidth(float size) {
        Paint dash = text(Ui.COND_BOLD, size, WHITE);
        dash.setLetterSpacing(-0.01f);
        return hiddenScoreWidth(dash, size);
    }

    private static float hiddenScoreWidth(Paint dash, float size) {
        return 2 * SLOT_W * size + 2 * SLOT_GAP * size + dash.measureText("–");
    }

    /** The crest centred in a box, scaled to fit (no disc). */
    static void crest(Canvas cv, Bitmap crest, float cx, float cy, float box, Paint paint) {
        if (crest == null) return;
        float sc = Math.min(box / crest.getWidth(), box / crest.getHeight());
        float w = crest.getWidth() * sc, h = crest.getHeight() * sc;
        Bitmap s = Cards.smooth(crest, w);
        cv.drawBitmap(s, null, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
            paint != null ? paint : new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        if (s != crest) s.recycle();
    }

    /**
     * The crest on an ivory plate: a disc (radius = size / 2) or a squircle (corner = size * 0.28), with a soft shadow.
     * crestRatio: the crest's box as a share of the plate. Without a crest, the club badge (initials on colours).
     */
    static void crestOnPlate(Canvas cv, Bitmap crest, String raw, float cx, float cy, float size, boolean squircle,
                             int plate, float crestRatio) {
        if (crest == null) { Badges.draw(cv, raw, cx, cy, size / 2); return; }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(plate);
        p.setShadowLayer(size * 0.07f, 0, size * 0.03f, 0x66000000);
        RectF r = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        if (squircle) cv.drawRoundRect(r, size * 0.28f, size * 0.28f, p); else cv.drawOval(r, p);
        crest(cv, crest, cx, cy, size * crestRatio, null);
    }

    /** The club's crest as a tonal ghost: drawn into dst at an alpha, its brightness clamped (white shields never glare). */
    static void ghostCrest(Canvas cv, Bitmap crest, RectF dst, float alpha, float brightness) {
        if (crest == null) return;
        ColorMatrix cm = new ColorMatrix(new float[]{
            brightness, 0, 0, 0, 0,
            0, brightness, 0, 0, 0,
            0, 0, brightness, 0, 0,
            0, 0, 0, alpha, 0});
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        float sc = Math.min(dst.width() / crest.getWidth(), dst.height() / crest.getHeight());
        float w = crest.getWidth() * sc, h = crest.getHeight() * sc;
        RectF r = new RectF(dst.centerX() - w / 2, dst.centerY() - h / 2, dst.centerX() + w / 2, dst.centerY() + h / 2);
        Bitmap s = Cards.smooth(crest, w);
        cv.drawBitmap(s, null, r, p);
        if (s != crest) s.recycle();
    }

    /** A radial light (glow) of a colour at an alpha, fading to nothing at radius. */
    static void glow(Canvas cv, float cx, float cy, float radius, int color, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int c0 = (alpha << 24) | (color & 0xFFFFFF);
        p.setShader(new RadialGradient(cx, cy, radius, c0, color & 0xFFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, cv.getWidth(), cv.getHeight(), p);
    }

    private static Bitmap noise;

    /** Fine grain over the card (alpha 0–255), so gradients don't band on older panels. One 128-px tile, made once. */
    static synchronized void grain(Canvas cv, int alpha) {
        if (noise == null) {
            noise = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Random r = new Random(7);
            int[] px = new int[128 * 128];
            for (int i = 0; i < px.length; i++) { int v = r.nextInt(256); px[i] = 0xFF000000 | (v << 16) | (v << 8) | v; }
            noise.setPixels(px, 0, 128, 0, 0, 128, 128);
        }
        Paint p = new Paint();
        p.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setAlpha(alpha);
        cv.drawRect(0, 0, cv.getWidth(), cv.getHeight(), p);
    }

    // ---------- where to watch: one shared line (Signature's and Kit's line 4, Clash's row C) ----------

    /**
     * A channel logo is drawn only where it reads at the launcher's unfocused 0.404: one nearer square than 2.5:1
     * (ERT's, SPORT FM's) is a smudge on a one-line foot, and one under 44 px tall (Novasports Prime's wordmark capped
     * at 240 wide is 21 px, ~9 on the TV) is a smear. A wide logo may take the line's free width to reach 44 px; past
     * that, or nearer square, the channel's name is set in type at the line's size instead.
     */
    static final float LOGO_MIN_ASPECT = 2.5f, LOGO_MIN_H = 44;

    /**
     * A logo's drawn {width, height} for its aspect ratio (width / height): contained in capW x boxH; under 44 px tall
     * it is widened to 44 px when the line's free width for it (freeW) allows; null when the name is set in type
     * instead (nearer square than 2.5:1, or too wide for 44 px in the room). Pure arithmetic: unit-tested.
     */
    static float[] logoFit(float aspect, float capW, float boxH, float freeW) {
        if (!(aspect >= LOGO_MIN_ASPECT)) return null;
        float h = Math.min(boxH, capW / aspect);
        if (h >= LOGO_MIN_H) return new float[]{h * aspect, h};
        float need = Math.min(boxH, LOGO_MIN_H);
        return need * aspect <= freeW ? new float[]{need * aspect, need} : null;
    }

    /**
     * The forms of a channel line, tried in turn before any of it goes under 44 px: FULL as the spec sets it (LIKELY
     * first, guesses joined by OR, names tracked .05); BARE without the hedge word and with the names joined by " / "
     * (the launcher's description keeps "Likely …"); TIGHT the bare line untracked.
     */
    static final int FULL = 0, BARE = 1, TIGHT = 2;

    /** No channel line goes under this (LAYOUT-CALM §1.4: nothing a viewer needs under 44 px). */
    static final float CHANNEL_FLOOR = 44;

    /** How a channel line is set: the size, the logos' width cap, the form; fits false when even TIGHT at 44 overflows. */
    static final class ChannelSet {
        final float size, logoCap;
        final int form;
        final boolean fits;
        ChannelSet(float size, float logoCap, int form, boolean fits) { this.size = size; this.logoCap = logoCap; this.form = form; this.fits = fits; }
    }

    /** A channel line's width at a size, logo cap and form (CardKit.channel measuring, or a fake in tests). */
    interface ChannelWidth { float at(float size, float logoCap, int form); }

    /**
     * Fits a channel line into max: each form in turn (FULL, BARE, TIGHT) through the shared cascade (spec §1.6: the
     * size to the midpoint, the logos capped 200 then 180, then the size to the 44 px floor); the first that fits wins.
     * None fits: TIGHT at 44, fits false (the style clips it at its edge). Pure arithmetic over w: unit-tested.
     */
    static ChannelSet fitChannel(ChannelWidth w, float start, float max, boolean logo) {
        SignatureStyle.Fit f = null;
        for (int form = FULL; form <= TIGHT; form++) {
            final int fm = form;
            f = SignatureStyle.Fit.cascade((size, cap, comp) -> w.at(size, cap, fm), start, CHANNEL_FLOOR, max, logo, false);
            if (w.at(f.size, f.logoCap, form) <= max) return new ChannelSet(f.size, f.logoCap, form, true);
        }
        return new ChannelSet(f.size, f.logoCap, TIGHT, false);
    }

    /** fitChannel for a card's own channel line, its logos logoH tall at most. */
    static ChannelSet fitChannel(final CardModel m, final CardArt art, float start, final float max, final float logoH) {
        return fitChannel((size, cap, form) -> channel(null, m, art, 0, 0, size, cap, logoH, max, form, false, 0),
            start, max, m.channel != null && !m.channel.namesOnly);
    }

    /** Draws a channel line set by fitChannel on centre line cy (alignRight: x is its right edge); returns its width. */
    static float channel(Canvas cv, CardModel m, CardArt art, float x, float cy, ChannelSet s, float logoH, float max,
                         boolean alignRight, int textColor) {
        return channel(cv, m, art, x, cy, s.size, s.logoCap, logoH, max, s.form, alignRight, textColor);
    }

    /**
     * Where to watch (spec §1.5), drawn on centre line cy from x (alignRight: x is the right edge), or only measured
     * when cv is null. size: the line's text size; logoW x logoH: the logos' box; max: the line's width (a short logo
     * may widen into what the rest leaves of it, logoFit); form: FULL, BARE or TIGHT. Confirmed = trimmed logo + number
     * chip (GS Bold 0.87 em on 16 % white, radius 12); Likely = LIKELY (RC Regular, 72 % white) before the logo; abroad =
     * LIKELY + the names in words ("OR" between guesses, "/" between confirmed names); a logo that wouldn't read
     * (logoFit) = the channel's name in the line's type (ERT1, NOVASPORTS PRIME), its chip kept. Returns the width (0
     * when nothing is known). textColor: names set in words.
     */
    static float channel(Canvas cv, CardModel m, CardArt art, float x, float cy, float size, float logoW, float logoH,
                         float max, int form, boolean alignRight, int textColor) {
        CardModel.Channel ch = m.channel;
        if (ch == null) return 0;
        Paint lead = text(Ui.COND, size, alpha(WHITE, 0.72f));
        lead.setLetterSpacing(0.05f);  // spec §3.1 / §3.3: LIKELY RC Regular, tracking .05 em, 18 px (at 46) before the logo
        String likely = ch.likely && form == FULL ? CardModel.w("likely") : "";
        Paint np = text(Ui.COND, size, textColor);  // names in words: the line's own type
        np.setLetterSpacing(form == TIGHT ? 0 : 0.05f);
        if (ch.namesOnly) {  // abroad: words only, one run of text with LIKELY (a word space between)
            float space = lead.measureText(" ");
            String names = form == FULL ? ch.words() : ch.bareWords();
            float w = (likely.isEmpty() ? 0 : lead.measureText(likely) + space) + np.measureText(names);
            if (cv == null) return w;
            float x0 = alignRight ? x - w : x;
            if (!likely.isEmpty()) { cv.drawText(likely, x0, cy + cap(np) / 2, lead); x0 += lead.measureText(likely) + space; }
            cv.drawText(names, x0, cy + cap(np) / 2, np);
            return w;
        }
        List<Channels.Logo> logos = art.logos();
        int n = Math.min(Math.min(ch.names.size(), logos.size()), 2);
        if (n == 0) return 0;
        Paint num = text(Ui.BOLD, size * 0.87f, WHITE);
        float space = size * 18f / 46f, gap = size * 0.5f;
        Bitmap[] img = new Bitmap[n];
        float[] w = new float[n], h = new float[n];
        String[] word = new String[n];
        float fixed = likely.isEmpty() ? 0 : lead.measureText(likely) + space;
        for (int i = 0; i < n; i++) {
            word[i] = Cards.caps(ch.names.get(i));
            fixed += (i > 0 ? gap : 0) + chipWidth(num, size, logos.get(i).no);
            img[i] = trimmed(art, logos.get(i).url);
            if (img[i] != null) {  // its box first; legibility (logoFit) once the line's other widths are known
                float sc = Math.min(logoW / img[i].getWidth(), logoH / img[i].getHeight());
                w[i] = img[i].getWidth() * sc; h[i] = img[i].getHeight() * sc;
            } else w[i] = np.measureText(word[i]);
        }
        for (int i = 0; i < n; i++) {
            if (img[i] == null) continue;
            float others = fixed;
            for (int j = 0; j < n; j++) if (j != i) others += w[j];
            float[] f = logoFit(img[i].getWidth() / (float) img[i].getHeight(), logoW, logoH, max - others);
            if (f == null) { img[i] = null; w[i] = np.measureText(word[i]); }
            else { w[i] = f[0]; h[i] = f[1]; }
        }
        float total = fixed;
        for (int i = 0; i < n; i++) total += w[i];
        if (cv == null) return total;
        float xx = alignRight ? x - total : x;
        if (!likely.isEmpty()) { cv.drawText(likely, xx, cy + cap(lead) / 2, lead); xx += lead.measureText(likely) + space; }
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < n; i++) {
            if (i > 0) xx += gap;
            if (img[i] != null) {
                Bitmap sm = Cards.smooth(img[i], w[i]);
                cv.drawBitmap(sm, null, new RectF(xx, cy - h[i] / 2, xx + w[i], cy + h[i] / 2), lp);
                if (sm != img[i]) sm.recycle();
            } else cv.drawText(word[i], xx, cy + cap(np) / 2, np);
            xx += w[i];
            int no = logos.get(i).no;
            if (no > 0) {
                xx += size * 0.30f;
                String t = String.valueOf(no);
                float bw = num.measureText(t) + 32, c = cap(num), bh = Math.min(c + 22, logoH);  // never taller than the logo box
                Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
                chip.setColor(alpha(WHITE, 0.16f));
                cv.drawRoundRect(new RectF(xx, cy - bh / 2, xx + bw, cy + bh / 2), 12, 12, chip);
                cv.drawText(t, xx + 16, cy + c / 2, num);
                xx += bw;
            }
        }
        return total;
    }

    /** A number chip's width with the space before it (0 without a number). */
    private static float chipWidth(Paint num, float size, int no) {
        return no > 0 ? size * 0.30f + num.measureText(String.valueOf(no)) + 32 : 0;
    }

    /** A channel logo trimmed of transparent padding (cached per card by CardArt). */
    static Bitmap trimmed(CardArt art, String url) { return art.trimmed(url); }

    static int alpha(int c, float a) { return ((int) (a * 255) << 24) | (c & 0xFFFFFF); }

    static String join(List<String> parts, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : parts) { if (b.length() > 0) b.append(sep); b.append(s); }
        return b.toString();
    }
}
