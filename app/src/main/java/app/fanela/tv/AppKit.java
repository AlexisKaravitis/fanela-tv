package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The shared components of the app screens (SPEC-APP 2), drawn on a canvas scaled to spec px, the same in every
 * layout: the where-to-watch cell, outcome pills, scores, the OK hint, sport glyphs and icons, skeleton blocks.
 * Sizes and paddings are the spec's; text baselines follow its line boxes (Ui.baseline).
 */
final class AppKit {
    private AppKit() { }

    // ---------- text ----------
    static Paint text(android.graphics.Typeface face, float size, int color) { return Ui.paint(face, size, color); }

    /** Android splits tracking around each glyph, CSS puts it after: left-aligned tracked text starts this much earlier. */
    static float lead(Paint p) { return p.getLetterSpacing() * p.getTextSize() / 2; }

    static Paint caps(android.graphics.Typeface face, float size, int color, float tracking) {
        Paint p = Ui.paint(face, size, color);
        p.setLetterSpacing(tracking);
        return p;
    }

    /** Draws s with its right edge at x; returns its width. */
    static float right(Canvas cv, Paint p, String s, float x, float baseline) {
        float w = p.measureText(s);
        cv.drawText(s, x - w, baseline, p);
        return w;
    }

    static float centre(Canvas cv, Paint p, String s, float cx, float baseline) {
        float w = p.measureText(s);
        cv.drawText(s, cx - w / 2, baseline, p);
        return w;
    }

    /** Shrinks the paint to fit maxW (2 px steps, never below floor); returns the size used. */
    static float fit(Paint p, String s, float maxW, float size, float floor) { return CardKit.fit(p, s, maxW, size, floor); }

    static String cut(Paint p, String s, float maxW) { return Cards.ellipsize(p, s, maxW); }

    // ---------- where to watch ----------
    /** The where-to-watch cell's content for a match, resolved off the UI thread (it loads the logo). */
    static final class Watch {
        static final int SURE = 0, LIKELY = 1, NAMES = 2, GUIDE = 3;
        int kind;
        Bitmap logo;
        /** The channel number ("318"), "" when none. */
        String number = "";
        /** LIKELY / ΠΙΘΑΝΟ (display capitals); the rights holder or channel names when there is no logo. */
        String lead = "", name = "";
        /** GUIDE: the shorter wording for a cell too narrow for name (a fixture row's in Greek); null otherwise. */
        String brief;
        /** The app the channel is watched in ("Magenta TV"), null abroad. */
        String app;
        String key() { return kind + name + number + (logo == null ? "" : logo.getGenerationId()); }
    }

    /** Null when nothing is known (the slot stays empty, nothing shifts). */
    static Watch watch(Context c, Match m) {
        if (!m.channelKnown) {
            if (!Where.noData(m)) return null;
            Watch w = new Watch();
            w.kind = Watch.GUIDE;
            w.name = I18n.t("check_guide");
            w.brief = I18n.t("check_guide_short");
            return w;
        }
        CardModel.Channel ch = CardModel.channel(m);
        if (ch == null) return null;
        Watch w = new Watch();
        w.app = Where.plain(m) ? null : Cards.appName(m.channelApp);
        if (ch.likely) {  // a guess: the rights holder in words, never a logo or a number
            w.kind = Watch.LIKELY;
            w.lead = Cards.caps(I18n.t("likely"));
            w.name = ch.namesOnly ? CardKit.join(ch.names, " · ") : ch.names.get(0);
            return w;
        }
        if (ch.namesOnly) {
            w.kind = Watch.NAMES;
            w.name = CardKit.join(ch.names, " · ");
            w.number = ch.number > 0 ? String.valueOf(ch.number) : "";
            return w;
        }
        w.kind = Watch.SURE;
        java.util.List<Channels.Logo> logos = Channels.logos(c, m);
        if (!logos.isEmpty()) w.logo = AppArt.logo(c, logos.get(0).url);
        int no = !logos.isEmpty() && logos.get(0).no > 0 ? logos.get(0).no : ch.number;
        w.number = no > 0 ? String.valueOf(no) : "";
        w.name = ch.names.get(0);
        return w;
    }

    /** The cell's sizes: sm (rows, 42), md (52), lg (match screen, 84). */
    enum Size {
        SM(42, 12, 14, 10, 10, 24, 150, 30, 19, 24, 24),
        MD(52, 16, 18, 14, 12, 30, 210, 36, 22, 28, 30),
        LG(84, 24, 28, 22, 16, 50, 360, 64, 28, 40, 36);
        final float h, padL, padR, gap, radius, logoH, logoMax, number, lead, name, plain;
        Size(float h, float padL, float padR, float gap, float radius, float logoH, float logoMax, float number, float lead, float name, float plain) {
            this.h = h; this.padL = padL; this.padR = padR; this.gap = gap; this.radius = radius; this.logoH = logoH;
            this.logoMax = logoMax; this.number = number; this.lead = lead; this.name = name; this.plain = plain;
        }
    }

    /** The gap after LIKELY: 12 in the default cell (the hatched cell's own), the size's gap otherwise. */
    private static float likelyGap(Watch w, Size z) { return w.kind == Watch.LIKELY && z == Size.MD ? 12 : z.gap; }

    private static float logoW(Watch w, Size z) {
        if (w.logo == null) return 0;
        return Math.min(z.logoMax, z.logoH * w.logo.getWidth() / (float) w.logo.getHeight());
    }

    private static Paint numberPaint(Size z, int ink) { return text(Ui.COND_BOLD, z.number, ink); }
    private static Paint leadPaint(Size z, int ink) { Paint p = caps(Ui.BOLD, z.lead, Ui.alpha(ink, 0.85f), Ui.TRACK_LIKELY); return p; }
    private static Paint namePaint(Size z, int ink) { return text(Ui.MEDIUM, z.name, ink); }

    /** Measures text at a size: a Paint in the app, a fixed advance in the tests. */
    interface Measure { float width(String s, float size); }

    static Measure measure(Paint p) {
        return (s, size) -> { p.setTextSize(size); return p.measureText(s); };
    }

    // ---------- club names in a tight place ----------
    /**
     * A club's names from longest to shortest, as the cards fall back (CardModel.names): its name in the app's language
     * (I18n.team), the card name (I18n.shortTeam: "Γκλάντμπαχ"), then the card's tight form (I18n.tightName: "Ερυθρός").
     */
    static java.util.List<String> forms(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(I18n.team(raw));
        String card = I18n.shortTeam(raw);
        if (!out.contains(card)) out.add(card);
        String tight = I18n.tightName(card);
        if (tight != null && !out.contains(tight)) out.add(tight);
        return out;
    }

    // ---------- the VS mark between two clubs (CardKit.vs: the cards' mark, owner fx6) ----------
    /**
     * In a fixture title the mark replaces the dash ("Ολυμπιακός – Ντουμπάι"): Google Sans Bold capitals at .36 of the
     * names' size (29 at 80, 40 at 110; never under 24), tracked as on the cards, 26 % of the names' size of air
     * either side, centred a third of an em over the baseline (between the lower case and the capitals). A word "vs"
     * at the names' size read as a third name; the mark reads as punctuation, as in a broadcast's lower third.
     */
    static final float VS_IN_TITLE = 0.36f, VS_AIR = 0.26f, VS_RISE = 0.33f;
    /** "VS" in Google Sans Bold tracked .06: its ink is 1.333 em wide (measured: 58.6 at 44). */
    static final float VS_INK_EM = 1.34f;
    /** The mark's ink in the app: the cards' 66 % of the text colour. */
    static int vsInk(int ink) { return Ui.alpha(ink, CardKit.VS_A); }

    static float vsSize(float size) { return Math.max(Ui.T_MIN, Math.round(VS_IN_TITLE * size)); }

    /** The room the mark takes in a title at size, its air included. */
    static float vsRoom(float size) { return VS_INK_EM * vsSize(size) + 2 * VS_AIR * size; }

    /** The mark between two crests: .17 of the crest (33 between the hero's 196, 42 between the match screen's 250). */
    static float vsForCrests(float crest) { return Math.round(0.17f * crest); }

    /** The air between a crest and the mark (the dash had 32 either side). */
    static final float VS_CREST_AIR = 32;

    /**
     * Two clubs stacked as team rows (the Matchday stage, its match screen): the mark at the names' left edge x,
     * centred between the two names (cy), at the title's proportion of their size, so the rows read "Ολυμπιακός / VS /
     * Ντουμπάι". alpha: the screen's fade.
     */
    static void vsBetweenRows(Canvas cv, float x, float cy, float nameSize, float alpha) {
        CardKit.vs(cv, CardKit.VS, x, cy, vsSize(nameSize), Ui.alpha(Ui.TXT, CardKit.VS_A * alpha), false);
    }

    /**
     * A fixture title set for its box: home and away names, the size, and whether one name still has to be cut with
     * "…" at that size. text: the two names around the mark, as plain text ("Olympiacos VS Dubai").
     */
    static final class Title {
        final String home, away, text;
        final float size;
        final boolean cut;
        Title(String home, String away, float size, boolean cut) {
            this.home = home; this.away = away; this.size = size; this.cut = cut; text = home + " " + CardKit.VS + " " + away;
        }
    }

    /** The title's width at a size: both names and the mark between them. */
    static float titleWidth(Measure m, String home, String away, float size) {
        return m.width(home, size) + vsRoom(size) + m.width(away, size);
    }

    /**
     * "home VS away" in maxW (P2e): both names as they are, from size down to min (2 px steps); where that fails, the
     * longer of the two steps to its next shorter form (forms()), then again, so both clubs are always named; only a
     * title too long even in its shortest forms is cut, at min.
     */
    static Title title(Measure m, java.util.List<String> home, java.util.List<String> away, float maxW, float size, float min) {
        int h = 0, a = 0;
        while (true) {
            String hn = home.get(h), an = away.get(a);
            for (float z = size; z >= min; z -= 2) if (titleWidth(m, hn, an, z) <= maxW) return new Title(hn, an, z, false);
            boolean hMore = h + 1 < home.size(), aMore = a + 1 < away.size();
            if (!hMore && !aMore) return new Title(hn, an, min, true);
            if (hMore && (!aMore || m.width(hn, min) >= m.width(an, min))) h++; else a++;
        }
    }

    /**
     * Draws a title from x on a baseline with the paint at the title's size: the home name, the mark (vsInk of the
     * paint's colour), the away name. A title that is cut gives the longer name the "…", so both are still named.
     */
    static void drawTitle(Canvas cv, Paint tp, Title t, float x, float baseline, float maxW) {
        tp.setTextSize(t.size);
        String hn = t.home, an = t.away;
        if (t.cut) {
            float room = maxW - vsRoom(t.size);
            float hw = tp.measureText(hn), aw = tp.measureText(an);
            if (hw >= aw) hn = cut(tp, hn, Math.max(room - aw, room / 2)); else an = cut(tp, an, Math.max(room - hw, room / 2));
            if (tp.measureText(hn) + tp.measureText(an) > room) { hn = cut(tp, hn, room / 2); an = cut(tp, an, room / 2); }
        }
        cv.drawText(hn, x, baseline, tp);
        float vx = x + tp.measureText(hn) + VS_AIR * t.size, vs = vsSize(t.size);
        CardKit.vs(cv, CardKit.VS, vx, baseline - VS_RISE * t.size, vs, vsInk(tp.getColor()), false);
        cv.drawText(an, vx + VS_INK_EM * vs + VS_AIR * t.size, baseline, tp);
    }

    /**
     * Two names that share room (the live strip's blocks) at the smallest size they may take: the longer one steps to
     * its next shorter form until both fit, or neither has one left. Returns {left, right}.
     */
    static String[] pair(Measure m, java.util.List<String> left, java.util.List<String> right, float room, float min) {
        int l = 0, r = 0;
        while (m.width(left.get(l), min) + m.width(right.get(r), min) > room) {
            boolean lMore = l + 1 < left.size(), rMore = r + 1 < right.size();
            if (!lMore && !rMore) break;
            if (lMore && (!rMore || m.width(left.get(l), min) >= m.width(right.get(r), min))) l++; else r++;
        }
        return new String[]{left.get(l), right.get(r)};
    }

    /** A name fitted to its room: the text to draw and its size. */
    static final class Fitted {
        final String text;
        final float size;
        Fitted(String text, float size) { this.text = text; this.size = size; }
    }

    /** No text smaller than this anywhere (SPEC-APP 1.1). */
    static final float MIN_TEXT = 24;

    /** Between the rights holders in a names cell ("Sky Sports · TNT Sports"). */
    static final String HOLDERS = " · ";

    /**
     * Fits a cell's name to room, keeping it whole as long as it can: at the cell's size, then 2 px smaller down to
     * 24; then, for a list of rights holders, whole names dropped from the end with a count of the ones left out
     * ("Sky Sports +1", at the largest size that takes it); then the first name alone; an ellipsis only when it still overflows.
     */
    static Fitted fitName(String name, float size, float room, Measure m) {
        float s = fitSize(name, size, room, m);
        if (s > 0) return new Fitted(name, s);
        float floor = Math.min(size, MIN_TEXT);
        String[] parts = name.split(java.util.regex.Pattern.quote(HOLDERS));
        if (parts.length < 2) return new Fitted(ellipsize(name, room, floor, m), floor);
        for (int k = parts.length - 1; k >= 1; k--) {
            StringBuilder b = new StringBuilder(parts[0]);
            for (int i = 1; i < k; i++) b.append(HOLDERS).append(parts[i]);
            String t = b.append(" +").append(parts.length - k).toString();
            s = fitSize(t, size, room, m);
            if (s > 0) return new Fitted(t, s);
        }
        // the first holder whole beats a cut one with a count (the hero and the match screen list them all)
        s = fitSize(parts[0], size, room, m);
        if (s > 0) return new Fitted(parts[0], s);
        return new Fitted(ellipsize(parts[0], room, floor, m), floor);
    }

    /** The largest size from size down to 24 (2 px steps) at which s fits room; 0 when it never does. */
    private static float fitSize(String s, float size, float room, Measure m) {
        float floor = Math.min(size, MIN_TEXT);
        for (float z = size; z >= floor; z -= 2) if (m.width(s, z) <= room) return z;
        return 0;
    }

    /** s cut with "…" to fit room at size (Cards.ellipsize, with a Measure). */
    static String ellipsize(String s, float room, float size, Measure m) {
        if (m.width(s, size) <= room) return s;
        int n = s.length();
        while (n > 1 && m.width(s.substring(0, n).trim() + "…", size) > room) n--;
        return s.substring(0, n).trim() + "…";
    }

    /** A GUIDE cell's words: the full phrase when it fits room, else the brief one when that does, else cut. */
    static String guideText(String full, String brief, float room, float size, Measure m) {
        if (m.width(full, size) <= room || brief == null) return ellipsize(full, room, size, m);
        return ellipsize(brief, room, size, m);
    }

    /** The name cell's fixed part: the paddings, LIKELY and its gap, the number and its gap. */
    private static float nameChrome(Watch w, Size z) {
        return z.padL + z.padR + (w.lead.isEmpty() ? 0 : leadPaint(z, 0).measureText(w.lead) + likelyGap(w, z))
            + (w.number.isEmpty() ? 0 : z.gap + numberPaint(z, 0).measureText(w.number));
    }

    private static Fitted fittedName(Watch w, Size z, float maxW) {
        return fitName(w.name, z.name, maxW - nameChrome(w, z), measure(namePaint(z, 0)));
    }

    /** The cell's width (maxW: the name is fitted to it, see fitName). */
    static float width(Watch w, Size z, float maxW) {
        if (w == null) return 0;
        if (w.kind == Watch.GUIDE) {
            Paint p = text(Ui.REGULAR, z.plain, 0);
            return Math.min(maxW, p.measureText(guideText(w.name, w.brief, maxW, z.plain, measure(p))));
        }
        if (w.kind == Watch.SURE && w.logo != null) {
            float inner = logoW(w, z) + (w.number.isEmpty() ? 0 : z.gap + numberPaint(z, 0).measureText(w.number));
            return Math.min(maxW, z.padL + inner + z.padR);
        }
        Fitted f = fittedName(w, z, maxW);
        return Math.min(maxW, nameChrome(w, z) + measure(namePaint(z, 0)).width(f.text, f.size));
    }

    /**
     * Draws the cell from x (alignRight: x is its right edge), centred on cy; returns its width. Confirmed: a tonal
     * block with the logo and the number. Likely: no fill, a dashed border, a hatch, LIKELY and the rights holder.
     * onLight: the field behind is light (the block turns dark so white logos still read).
     */
    static float cell(Canvas cv, Watch w, Size z, float x, float cy, boolean alignRight, float maxW, boolean onLight, float alpha) {
        if (w == null) return 0;
        int ink = onLight ? FieldColors.INK_DARK : Ui.TXT;
        float wd = width(w, z, maxW);
        float x0 = alignRight ? x - wd : x;
        if (w.kind == Watch.GUIDE) {
            Paint p = text(Ui.REGULAR, z.plain, Ui.alpha(ink, Ui.A2 * alpha));
            cv.drawText(guideText(w.name, w.brief, wd, z.plain, measure(p)), x0, Ui.baseline(Ui.REGULAR, z.plain, cy), p);
            return wd;
        }
        RectF box = new RectF(x0, cy - z.h / 2, x0 + wd, cy + z.h / 2);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (w.kind == Watch.LIKELY) {
            hatch(cv, box, z.radius, onLight ? Ui.alpha(0xFF000000, 0.09f * alpha) : Ui.alpha(0xFFFFFFFF, 0.09f * alpha));
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(2);
            bg.setColor(onLight ? Ui.alpha(0xFF000000, 0.38f * alpha) : Ui.alpha(0xFFFFFFFF, 0.38f * alpha));
            bg.setPathEffect(new DashPathEffect(new float[]{6, 4}, 0));
            RectF in = new RectF(box);
            in.inset(1, 1);
            cv.drawRoundRect(in, z.radius - 1, z.radius - 1, bg);
        } else {
            bg.setColor(onLight ? Ui.alpha(0xFF141416, 0.86f * alpha) : Ui.alpha(0xFFFFFFFF, 0.14f * alpha));
            cv.drawRoundRect(box, z.radius, z.radius, bg);
            if (onLight) ink = Ui.TXT;
        }
        float xx = x0 + z.padL, right = box.right - z.padR;
        if (w.kind == Watch.SURE && w.logo != null) {
            float lw = logoW(w, z), lh = lw * w.logo.getHeight() / w.logo.getWidth();
            Paint lp = new Paint(AppArt.FILTER);
            lp.setAlpha(Math.round(255 * alpha));
            cv.drawBitmap(w.logo, null, new RectF(xx, cy - lh / 2, xx + lw, cy + lh / 2), lp);
            xx += lw + z.gap;
        } else {
            if (!w.lead.isEmpty()) {
                Paint lp = leadPaint(z, Ui.alpha(ink, alpha));
                cv.drawText(w.lead, xx - lead(lp), Ui.baseline(Ui.BOLD, z.lead, cy), lp);
                xx += lp.measureText(w.lead) + likelyGap(w, z);
            }
            Paint np = namePaint(z, Ui.alpha(ink, alpha));
            Fitted f = fittedName(w, z, maxW);
            np.setTextSize(f.size);
            cv.drawText(f.text, xx, Ui.baseline(Ui.MEDIUM, f.size, cy), np);
            xx += np.measureText(f.text) + z.gap;
        }
        if (!w.number.isEmpty()) {
            Paint num = numberPaint(z, Ui.alpha(ink, alpha));
            // a line-height-1 box centred in the cell, as the reference's
            cv.drawText(w.number, xx, cy + z.number * (Ui.RC_ASC - (Ui.RC_ASC + Ui.RC_DESC) / 2), num);
        }
        return wd;
    }

    /** The Likely hatch: 5 px bands every 13 px at 135°, clipped to the cell. */
    static void hatch(Canvas cv, RectF box, float radius, int color) {
        cv.save();
        Path clip = new Path();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        cv.clipPath(clip);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float h = box.height(), k = (float) Math.sqrt(2);
        Path band = new Path();
        for (float a = box.left - h; a < box.right + h; a += 13 * k) {
            band.moveTo(a, box.top); band.lineTo(a + 5 * k, box.top);
            band.lineTo(a + 5 * k - h, box.bottom); band.lineTo(a - h, box.bottom); band.close();
        }
        cv.drawPath(band, p);
        cv.restore();
    }

    // ---------- outcome and score ----------
    /** WIN / DRAW / LOSS (Greek ΝΙΚΗ / ΙΣΟΠΑΛΙΑ / ΗΤΤΑ); "" without a result. */
    static String outcomeWord(int r) { return r == 9 ? "" : Cards.caps(I18n.t(r > 0 ? "win" : r == 0 ? "draw" : "loss")); }

    static float pillWidth(String word, boolean sm) {
        return caps(Ui.BOLD, sm ? 20 : 24, 0, Ui.TRACK_PILL).measureText(word) + 2 * (sm ? 14 : 20);
    }

    /** The outcome pill (sm 34 px, md 44 px) from x, centred on cy; returns its width. */
    static float pill(Canvas cv, int r, float x, float cy, boolean sm, float alpha) {
        String word = outcomeWord(r);
        if (word.isEmpty()) return 0;
        float h = sm ? 34 : 44, size = sm ? 20 : 24, w = pillWidth(word, sm);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Ui.alpha(r > 0 ? Ui.WIN : r == 0 ? Ui.DRAW : Ui.LOSS, alpha));
        cv.drawRoundRect(new RectF(x, cy - h / 2, x + w, cy + h / 2), h / 2, h / 2, bg);
        Paint p = caps(Ui.BOLD, size, Ui.alpha(r < 0 ? Ui.LOSS_TEXT : 0xFFFFFFFF, alpha), Ui.TRACK_PILL);
        cv.drawText(word, x + (sm ? 14 : 20) - lead(p), cy + 0.34f * size, p);
        return w;
    }

    /** A score's width at a size (home first, the dash with .04 em either side). */
    static float scoreWidth(String hs, String as, float size) {
        Paint p = text(Ui.COND_BOLD, size, 0);
        return p.measureText(hs) + p.measureText("–") + 0.08f * size + p.measureText(as);
    }

    /** The score, home first: my number at 100 %, the opponent's at 60 %, the dash at 42 %. Returns its width. */
    static float score(Canvas cv, Match m, float x, float baseline, float size, int color, float alpha) {
        Paint p = text(Ui.COND_BOLD, size, color);
        boolean mineHome = m.clubHome();
        float xx = x;
        p.setColor(Ui.alpha(color, (mineHome ? 1f : 0.6f) * alpha));
        cv.drawText(m.hs, xx, baseline, p);
        xx += p.measureText(m.hs) + 0.04f * size;
        p.setColor(Ui.alpha(color, 0.42f * alpha));
        cv.drawText("–", xx, baseline, p);
        xx += p.measureText("–") + 0.04f * size;
        p.setColor(Ui.alpha(color, (mineHome ? 0.6f : 1f) * alpha));
        cv.drawText(m.as, xx, baseline, p);
        return xx + p.measureText(m.as) - x;
    }

    // ---------- small things ----------
    /** "[OK] opens Magenta TV" with its right edge at x, centred on cy; returns its width. */
    static float okHint(Canvas cv, String label, float x, float cy, int ink) {
        Paint t = text(Ui.REGULAR, 26, Ui.alpha(ink, Ui.A2));
        Paint k = caps(Ui.BOLD, 22, Ui.alpha(ink, Ui.A2), 0.05f);
        float kw = k.measureText("OK") + 20, tw = t.measureText(label), w = kw + 12 + tw;
        float x0 = x - w;
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(2);
        b.setColor(Ui.alpha(ink, 0.6f * Ui.A2));
        cv.drawRoundRect(new RectF(x0 + 1, cy - 17, x0 + kw - 1, cy + 17), 7, 7, b);
        cv.drawText("OK", x0 + 10, cy + 0.34f * 22, k);
        cv.drawText(label, x0 + kw + 12, Ui.baseline(Ui.REGULAR, 26, cy), t);
        return w;
    }

    /** A skeleton block: white 9 % in the exact slot of what is loading. */
    static void skeleton(Canvas cv, float l, float t, float r, float b, float radius, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.alpha(0xFFFFFFFF, 0.09f * alpha));
        cv.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
    }

    private static Paint stroke(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    /** The sport glyph (a 28-unit line icon: ball or basketball) in a box of size centred on (cx, cy). */
    static void sport(Canvas cv, String team, float cx, float cy, float size, int color) {
        cv.save();
        cv.translate(cx - size / 2, cy - size / 2);
        cv.scale(size / 28f, size / 28f);
        Paint p = stroke(color, 2);
        p.setStrokeCap(Paint.Cap.BUTT);
        cv.drawCircle(14, 14, 11.5f, p);
        Path a = new Path();
        if ("FC".equals(team)) {
            Path pent = new Path();
            pent.moveTo(14, 8.6f); pent.lineTo(19.1f, 12.3f); pent.lineTo(17.2f, 18.3f); pent.lineTo(10.8f, 18.3f); pent.lineTo(8.9f, 12.3f); pent.close();
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(color);
            cv.drawPath(pent, f);
            a.moveTo(14, 8.6f); a.lineTo(14, 3.2f);
            a.moveTo(19.1f, 12.3f); a.lineTo(24.4f, 10.6f);
            a.moveTo(17.2f, 18.3f); a.lineTo(20.5f, 22.9f);
            a.moveTo(10.8f, 18.3f); a.lineTo(7.5f, 22.9f);
            a.moveTo(8.9f, 12.3f); a.lineTo(3.6f, 10.6f);
        } else {
            a.moveTo(14, 2.5f); a.lineTo(14, 25.5f);
            a.moveTo(2.5f, 14); a.lineTo(25.5f, 14);
            a.moveTo(5.9f, 5.9f); a.cubicTo(10.4f, 10.4f, 10.4f, 17.6f, 5.9f, 22.1f);
            a.moveTo(22.1f, 5.9f); a.cubicTo(17.6f, 10.4f, 17.6f, 17.6f, 22.1f, 22.1f);
        }
        cv.drawPath(a, p);
        cv.restore();
    }

    /**
     * The Settings gear of the rail: Android TV's own settings icon (Material Icons "settings", Apache License 2.0,
     * credited in Sources), drawn as an outline like the TV's home screen draws it, in a box of size centred on (cx, cy).
     */
    static void gear(Canvas cv, float cx, float cy, float size, int color) {
        cv.save();
        cv.translate(cx - size / 2, cy - size / 2);
        cv.scale(size / 24f, size / 24f);
        Paint p = stroke(color, 1.6f);
        p.setStrokeJoin(Paint.Join.ROUND);
        cv.drawPath(GEAR, p);
        cv.restore();
    }

    /** Material Icons "settings" (24 units): the cog and its centre hole. */
    static final String GEAR_SVG = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58"
        + "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94"
        + "L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29"
        + "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12"
        + "s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96"
        + "c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54"
        + "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z"
        + "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z";

    private static final Path GEAR = svg(GEAR_SVG);

    /** An SVG path (M L H V C S Z, absolute and relative) as a Path: enough for the icons drawn here. */
    static Path svg(String d) {
        Path p = new Path();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z]|-?[0-9]*\\.?[0-9]+").matcher(d);
        java.util.List<String> t = new java.util.ArrayList<>();
        while (m.find()) t.add(m.group());
        float x = 0, y = 0, sx = 0, sy = 0, cx2 = 0, cy2 = 0;
        char cmd = 'M', prev = ' ';
        int i = 0;
        while (i < t.size()) {
            String s = t.get(i);
            if (Character.isLetter(s.charAt(0))) { cmd = s.charAt(0); i++; if (cmd == 'z' || cmd == 'Z') { p.close(); x = sx; y = sy; prev = cmd; continue; } }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M': x = ox + f(t, i); y = oy + f(t, i + 1); i += 2; p.moveTo(x, y); sx = x; sy = y; cmd = rel ? 'l' : 'L'; break;
                case 'L': x = ox + f(t, i); y = oy + f(t, i + 1); i += 2; p.lineTo(x, y); break;
                case 'H': x = ox + f(t, i); i++; p.lineTo(x, y); break;
                case 'V': y = oy + f(t, i); i++; p.lineTo(x, y); break;
                case 'C': {
                    float x1 = ox + f(t, i), y1 = oy + f(t, i + 1);
                    cx2 = ox + f(t, i + 2); cy2 = oy + f(t, i + 3); x = ox + f(t, i + 4); y = oy + f(t, i + 5); i += 6;
                    p.cubicTo(x1, y1, cx2, cy2, x, y); break;
                }
                case 'S': {
                    boolean after = "CcSs".indexOf(prev) >= 0;
                    float x1 = after ? 2 * x - cx2 : x, y1 = after ? 2 * y - cy2 : y;
                    cx2 = ox + f(t, i); cy2 = oy + f(t, i + 1); x = ox + f(t, i + 2); y = oy + f(t, i + 3); i += 4;
                    p.cubicTo(x1, y1, cx2, cy2, x, y); break;
                }
                default: i++;
            }
            prev = cmd;
        }
        return p;
    }

    private static float f(java.util.List<String> t, int i) { return Float.parseFloat(t.get(i)); }

    /** The offline banner's cloud with a slash (a 32-unit line icon). */
    static void cloudOff(Canvas cv, float cx, float cy, float size, int color) {
        cv.save();
        cv.translate(cx - size / 2, cy - size / 2);
        cv.scale(size / 32f, size / 32f);
        Paint p = stroke(color, 2.4f);
        Path a = new Path();
        a.moveTo(9, 24); a.lineTo(23, 24);
        a.arcTo(new RectF(18.4f, 14.05f, 28.4f, 24.05f), 90, -180, false);
        a.arcTo(new RectF(10.2f, 6.4f, 24.2f, 20.4f), 6, -174, false);
        a.moveTo(6.5f, 14.2f);
        a.arcTo(new RectF(2.75f, 14.1f, 12.75f, 24.1f), 256, -180, false);
        cv.drawPath(a, p);
        cv.drawLine(4, 4, 28, 28, p);
        cv.restore();
    }
}
