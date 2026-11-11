package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Match cards: where-to-watch guesses, choosing the cards, drawing them (localised), and the home-screen rows. */
final class Cards {
    static final String MAGENTA = "gr.cosmote.cosmotetv.androidtv", EON = "com.ug.eon.android.tv",
        SPORTFM = "gr.sportfmtv.app", ANT1 = "gr.antenna.ant1.screen";
    static final int CARD_VERSION = 22;  // bump when the drawing changes: every card is redrawn once

    // ---------- where to watch (best guess until a TV guide confirms it) ----------
    /** Greek TV rights. Football / Basket League: 2026-27. EuroLeague + EuroCup: Nova until the end of 2027-28. */
    static final int FOOTBALL_RIGHTS = 2026, GBL_RIGHTS = 2026, EURO_RIGHTS_UNTIL = 2027;
    /** Home-rights holders by ESPN id (never by name): PAOK, Aris, Atromitos, Levadiakos, Kalamata, Asteras Tripoli. */
    static final String[] SL_NOVA = {"605", "11553", "6790", "5276", "132429", "8354"};
    static final String[] SL_SPORTFM = {"443", "2575"};  // Panathinaikos, Iraklis; all other 2026-27 home teams: Magenta Sport

    static boolean in(String[] a, String s) { for (String x : a) if (x.equals(s)) return true; return false; }

    static void channelGuess(Match m) {
        String homeId = m.homeId != null ? m.homeId : m.clubIsHome ? m.club : "";
        m.channelSure = false; m.channelKnown = true; m.channelApp = MAGENTA; m.channelNo = 0; m.channelLogo = null;
        int season = Sources.seasonOf(m.start);
        String lg = m.league == null ? "" : m.league;
        if ("euroleague".equals(lg) || "eurocup".equals(lg)) {
            if (season <= EURO_RIGHTS_UNTIL) { m.channel = "Novasports"; return; }
        } else if ("gbl".equals(lg)) {
            if (season == GBL_RIGHTS) { m.channel = "ΣΠΟΡ FM TV"; m.channelApp = SPORTFM; return; }
        } else if (season == FOOTBALL_RIGHTS) {
            switch (lg) {
                case "gre.1":
                    if (in(SL_NOVA, homeId)) m.channel = "Novasports";
                    else if (in(SL_SPORTFM, homeId)) { m.channel = "ΣΠΟΡ FM TV"; m.channelApp = SPORTFM; }
                    else m.channel = "Magenta Sport";
                    return;
                case "uefa.champions": m.channel = "Magenta Sport · MEGA"; return;
                case "uefa.europa": case "uefa.europa.conf": m.channel = "Magenta Sport · ANT1"; return;
                case "gre.cup": m.channel = "ΣΠΟΡ FM TV"; m.channelApp = SPORTFM; return;
                default: break;
            }
        }
        m.channelKnown = false;
        m.channel = "";
    }

    static String appName(String pkg) { return SPORTFM.equals(pkg) ? "SPORT FM TV" : "Magenta TV"; }

    static String where(Match m) { return where(m, false); }

    /**
     * Where to watch, in words. One pattern: the hedge word for a guess, the channels ("or" between guesses, "/"
     * between confirmed ones), then after a "·" the number and the app: "Novasports 4 · ch. 325 on Magenta TV",
     * "Likely Magenta Sport or MEGA · on Magenta TV"; abroad "Likely on Sky Sports or TNT Sports", "On Sky Sports 402".
     * lineStart: the text starts a line (capital first letter), not mid-sentence.
     */
    static String where(Match m, boolean lineStart) {
        if (!m.channelKnown) return I18n.t(Where.noData(m) ? "check_guide" : "channel_tba");
        WhereParts w = whereParts(m);
        String names = String.join(m.channelSure ? " / " : " " + I18n.t("or") + " ", w.channels);
        if (w.app == null) {  // a season table or a listing: no app of its own
            if (w.no > 0) names += " " + w.no;
            return I18n.f((m.channelSure ? "on_channels" : "usually_on") + (lineStart ? "_cap" : ""), names);
        }
        String tail = (w.no > 0 ? I18n.f("channel_no", w.no) + " " : "") + I18n.t("on") + " " + w.app;
        return (m.channelSure ? "" : I18n.t(lineStart ? "likely_channel_cap" : "likely_channel") + " ") + names + " · " + tail;
    }

    /** The pieces of where(): the hedge word ("" when confirmed), the channels, the channel number (0: none), the app (null abroad). */
    static final class WhereParts {
        String lead; List<String> channels; int no; String app;
    }

    static WhereParts whereParts(Match m) {
        WhereParts w = new WhereParts();
        w.lead = m.channelSure || !m.channelKnown ? "" : I18n.t("likely");
        w.channels = channels(m);
        w.no = m.channelSure && m.channelNo > 0 && (!Where.plain(m) || "tv".equals(m.listedBy)) ? m.channelNo : 0;
        w.app = Where.plain(m) ? null : appName(m.channelApp);
        return w;
    }

    /** The channels of a match, one name each, at most three, in the interface's letters; no numbers. */
    static List<String> channels(Match m) {
        List<String> out = new ArrayList<>();
        if (m.channel == null) return out;
        for (String s : m.channel.split("\\s*·\\s*")) if (!s.trim().isEmpty() && out.size() < 3) out.add(chan(s.trim()));
        return out;
    }

    /** The channels as the match panel's pills show them: the TV's own guide adds its number to the first. */
    static List<String> names(Match m) {
        List<String> out = channels(m);
        if ("tv".equals(m.listedBy) && m.channelNo > 0 && !out.isEmpty()) out.set(0, out.get(0) + " " + m.channelNo);
        return out;
    }

    /** Channel names as the guides write them (Greek), in Latin letters for the English interface. */
    static String chan(String s) {
        if (s == null || I18n.el()) return s == null ? "" : s;
        return s.replace("ΣΠΟΡ FM", "SPORT FM").replace("ΣΚΑΪ", "SKAI").replace("ΕΡΤ2 ΣΠΟΡ", "ERT2 SPORT").replace("ΕΡΤ", "ERT")
            .replace("ΣΠΟΡ", "SPORT");
    }

    // ---------- choosing the cards ----------
    /**
     * The live match, else the next one. A postponed or cancelled match is shown until the evening of its original
     * date (so the change is noticed), but never in place of a later match once that date has passed.
     */
    static Match pickNow(List<Match> all, String club, String team, long now) {
        for (Match m : all) if (club.equals(m.club) && team.equals(m.team) && "in".equals(m.state)) return m;
        Match pre = null, off = null;
        for (Match m : all) {
            if (!club.equals(m.club) || !team.equals(m.team)) continue;
            boolean coming = m.start >= now - 3 * 3600000L || (m.tbd && Store.sameViewerDay(m.start, now));  // a TBA game: all its day
            if ("pre".equals(m.state) && coming && (pre == null || m.start < pre.start)) pre = m;
            if ("off".equals(m.state) && m.start >= now - 12 * 3600000L && (off == null || m.start < off.start)) off = m;
        }
        if (off != null && (pre == null || off.start < pre.start)) return off;
        return pre;
    }

    /** Live on screen: in progress, not suspended, not waiting for a result. */
    static boolean live(Match m) {
        return "in".equals(m.state) && !"AWAITING".equals(m.detail) && !"SUSPENDED".equals(m.detail);
    }

    static Match pickLast(List<Match> all, String club, String team) {
        Match best = null;
        for (Match m : all) {
            if (!club.equals(m.club) || !team.equals(m.team) || !"post".equals(m.state) || !m.hasScore()) continue;
            if (best == null || m.start > best.start) best = m;
        }
        return best;
    }

    // ---------- text ----------
    /** Matching key: no accents or dialytika, upper case (ΠΑΝΑΘΗΝΑΙΚΟΣ). Never shown. */
    static String key(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    /** Display capitals, Greek style: accents dropped, dialytika kept or added (ΠΑΝΑΘΗΝΑΪΚΟΣ, ΣΚΑΪ, ΓΑΛΑΤΑΣΑΡΑΪ). */
    static String caps(String s) {
        if (s == null) return "";
        String x = s.replace("άι", "αϊ").replace("έι", "εϊ").replace("όι", "οϊ").replace("ύι", "υϊ")
            .replace("άυ", "αϋ").replace("έυ", "εϋ").replace("ήυ", "ηϋ")
            .replace("Άι", "Αϊ").replace("Έι", "Εϊ").replace("Όι", "Οϊ").replace("Άυ", "Αϋ").replace("Έυ", "Εϋ");
        x = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD).replaceAll("[\\u0300\\u0301\\u0341\\u0342\\u0343]", "");
        return java.text.Normalizer.normalize(x.toUpperCase(Locale.ROOT), java.text.Normalizer.Form.NFC);
    }

    static String when(Match m) {
        return m.tbd ? I18n.day(m.start) + " · " + I18n.t("time_tba") : I18n.day(m.start) + " " + I18n.time(m.start);
    }

    // ---------- drawing ----------
    static final int W = 960, H = 540;
    /** One red for live and for a loss; the green and grey of the other result words; the card's secondary text. */
    static final int RED = Color.rgb(232, 52, 60), AMBER = Color.rgb(245, 166, 35), GREEN = Color.rgb(61, 220, 132),
        GREY = Color.rgb(176, 182, 192), SECONDARY = Color.rgb(206, 210, 218), LEAD = Color.rgb(214, 218, 226),
        INK = Color.rgb(17, 17, 17), DIM = Color.argb(64, 255, 255, 255);

    /** The app font for a role: regular text, medium labels, bold names / scores / pills. */
    static Typeface face(String family, int style) {
        if (family.endsWith("black") || family.endsWith("condensed") || style == Typeface.BOLD) return Ui.BOLD;
        return family.endsWith("medium") ? Ui.MEDIUM : Ui.REGULAR;
    }

    /** The logo without its transparent margins: the same bitmap when there are none, else a new one (the caller recycles both). */
    static Bitmap trim(Bitmap b) {
        if (b == null || !b.hasAlpha()) return b;
        int w = b.getWidth(), h = b.getHeight(), l = w, t = h, r = -1, bo = -1;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if ((px[y * w + x] >>> 24) > 16) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bo) bo = y; }
        if (r < 0) return b;
        return Bitmap.createBitmap(b, l, t, r - l + 1, bo - t + 1);
    }

    /** Halves a bitmap until it is near its drawn width (a cleaner downscale); the steps between are recycled, the source is not. */
    static Bitmap smooth(Bitmap b, float targetW) {
        Bitmap cur = b;
        while (cur.getWidth() / 2f >= targetW && cur.getWidth() > 2) {
            Bitmap next = Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, Math.max(1, cur.getHeight() / 2), true);
            if (cur != b) cur.recycle();
            cur = next;
        }
        return cur;
    }

    /** Draws a bitmap scaled into r, leaving no temporary bitmap behind (the source stays the caller's). */
    static void drawBitmap(Canvas cv, Bitmap b, RectF r, Paint paint) {
        Bitmap s = smooth(b, r.width());
        cv.drawBitmap(s, null, r, paint);
        if (s != b) s.recycle();
    }

    static void drawBitmap(Canvas cv, Bitmap b, RectF r) {
        drawBitmap(cv, b, r, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
    }

    /** The crest on a white disc with a soft shadow and a faint rim; the crest bitmap is recycled once drawn. */
    static void badge(Canvas cv, Bitmap crest, String raw, float cx, float cy, float r) {
        if (crest == null) { Badges.draw(cv, raw, cx, cy, r); return; }
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(Color.WHITE);
        sh.setShadowLayer(16, 0, 6, Color.argb(115, 0, 0, 0));
        cv.drawCircle(cx, cy, r, sh);
        float box = r * 132 / 92, sc = Math.min(box / crest.getWidth(), box / crest.getHeight());
        float w = crest.getWidth() * sc, h = crest.getHeight() * sc;
        drawBitmap(cv, crest, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2));
        crest.recycle();
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2);
        rim.setColor(Color.argb(36, 0, 0, 0));
        cv.drawCircle(cx, cy, r - 1, rim);
    }

    /** A rounded chip; its text shrinks to fit maxW (never below 28 px). Returns the chip's width. */
    static float pill(Canvas cv, float x, float y, float h, float size, String text, int bg, int fg, boolean dot, boolean alignRight, float maxW) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face("sans-serif", Typeface.BOLD));
        float pad = 28, dotw = dot ? 30 : 0;
        p.setTextSize(size);
        while (p.measureText(text) + 2 * pad + dotw > maxW && size > 28) p.setTextSize(size -= 2);
        float w = p.measureText(text) + 2 * pad + dotw;
        if (alignRight) x = x - w;
        Paint bgp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgp.setColor(bg);
        cv.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, bgp);
        p.setColor(fg);
        if (dot) cv.drawCircle(x + pad + 7, y + h / 2, 7, p);
        cv.drawText(text, x + pad + dotw, y + h / 2 + capHeight(p) / 2, p);
        return w;
    }

    /** A warning sign the height of a capital: a filled amber triangle with an ink "!", drawn at (x, baseline). */
    static void warning(Canvas cv, float x, float baseline, float cap, Paint like) {
        float w = cap * 1.12f, top = baseline - cap;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(AMBER);
        android.graphics.Path t = new android.graphics.Path();
        t.moveTo(x + w / 2, top); t.lineTo(x + w, baseline); t.lineTo(x, baseline); t.close();
        cv.drawPath(t, p);
        p.setColor(INK);
        float bx = x + w / 2, bw = Math.max(2, cap * 0.14f);
        cv.drawRect(bx - bw / 2, top + cap * 0.34f, bx + bw / 2, baseline - cap * 0.30f, p);
        cv.drawCircle(bx, baseline - cap * 0.17f, bw * 0.72f, p);
    }

    /** The height of a capital in the paint's size: text is centred on its caps, not on its descenders. */
    static float capHeight(Paint p) {
        Rect r = new Rect();
        p.getTextBounds("H", 0, 1, r);
        return r.height();
    }

    /**
     * Both club names, always in the same size. At the base size a name that fits stays on one line. A longer name
     * first takes both names down together, by 2 px steps, to the floor (one calm line beats two); only then
     * does it break into two lines at the space nearest its middle, the second line hanging below the first so the
     * first lines of both names stay aligned. Below 30 px nothing shrinks further: a single word is squeezed alone.
     */
    static void drawNames(Canvas cv, Paint p, String home, String away, float y, float maxW, float size, float floor) {
        String[][] lines;
        for (;; size -= 2) {
            p.setTextSize(size);
            lines = new String[][]{split(p, home, maxW, size <= floor), split(p, away, maxW, size <= floor)};
            boolean fit = true;
            for (String[] n : lines) for (String l : n) if (p.measureText(l) > maxW) fit = false;
            if (fit || size <= 30) break;
        }
        float[] cx = {190, 770};
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < lines[i].length; j++) fitText(cv, p, lines[i][j], cx[i], y + j * size * 0.88f, maxW, size);  // caps: tight lines
    }

    /** One line if it fits (or wrapping is off), else two at the space nearest the middle; a single word stays one line. */
    static String[] split(Paint p, String s, float maxW, boolean wrap) {
        if (!wrap || p.measureText(s) <= maxW) return new String[]{s};
        int best = -1;
        for (int i = s.indexOf(' '); i >= 0; i = s.indexOf(' ', i + 1))
            if (best < 0 || Math.abs(i - s.length() / 2) < Math.abs(best - s.length() / 2)) best = i;
        if (best < 0) return new String[]{s};
        return new String[]{s.substring(0, best), s.substring(best + 1)};
    }

    /** The text that fits in maxW, cut with an ellipsis when it does not (the paint's size is kept). */
    static String ellipsize(Paint p, String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s.substring(0, n).trim() + "…") > maxW) n--;
        return s.substring(0, n).trim() + "…";
    }

    static void fitText(Canvas cv, Paint p, String s, float cx, float y, float maxW, float size) {
        p.setTextSize(size);
        while (p.measureText(s) > maxW && size > 16) p.setTextSize(size -= 2);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(s, cx, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * The kick-off time between the crests: the digits large, "pm" / "μ.μ." half their size on the same baseline, so the
     * time never has to shrink to fit (it stays clear of both crests).
     */
    static void drawTime(Canvas cv, Paint p, String t, float cx, float y, float big) {
        int sp = t.indexOf(' ');
        if (sp < 0) { fitText(cv, p, t, cx, y, 316, big); return; }
        String digits = t.substring(0, sp), suffix = t.substring(sp + 1);
        float small = big * 0.46f, gap = 10;
        p.setTextSize(big);
        float wd = p.measureText(digits);
        p.setTextSize(small);
        float ws = p.measureText(suffix), total = wd + gap + ws;
        float k = total > 316 ? 316 / total : 1;
        float x = cx - total * k / 2;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(big * k);
        cv.drawText(digits, x, y, p);
        int was = p.getColor();
        p.setColor(Color.argb(Math.round(Color.alpha(was) * 0.75f), Color.red(was), Color.green(was), Color.blue(was)));
        p.setTextSize(small * k);
        cv.drawText(suffix, x + (wd + gap) * k, y, p);
        p.setColor(was);
    }

    static void render(Context c, Match m, boolean stale, String name) throws Exception {
        saveAtomic(c, draw(c, m, stale, name.contains("_tv")), name, false);
    }

    /** A card for the TV's home screen: in the viewer's card style, saved at full size (the launcher shows 640 px). */
    static void renderTv(Context c, Teams.Club k, Match m, boolean stale, String name, long now) throws Exception {
        saveAtomic(c, CardStyles.draw(c, k, m, stale, Appearance.cardStyle(c), Appearance.showScores(c), now), name, true);
    }

    /** The card at full size (960x540), e.g. for the match screen. */
    static Bitmap draw(Context c, Match m, boolean stale) { return draw(c, m, stale, false); }

    /** The word in the chip of a result: FINAL, or AET / PENS when the match went that far (the result line has the score). */
    static String chipFinal(Match m) {
        return "FT_AET".equals(m.detail) ? I18n.t("chip_aet") : "FT_PEN".equals(m.detail) ? I18n.t("chip_pens") : I18n.t("final");
    }

    /**
     * One design in two sizes. The card is drawn at 960x540 and shown at 480x270 in the app's own home screen and at
     * about 408x230 on the TV's home screen (tv). Both carry the same layout: competition top left, date / status chip
     * top right, the crests with the time or score between them, both team names in one size, the channel strip or
     * the result word. The TV tile is 15 % smaller on screen, so its type and crests are set larger by about as much
     * (x1.18 for the secondary text, a little less for the names, which should not shout); nothing else differs.
     */
    static Bitmap draw(Context c, Match m, boolean stale, boolean tv) {
        Ui.fonts(c);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean awaiting = "AWAITING".equals(m.detail);  // no live feed and past the usual end: not shown as live
        boolean live = live(m), done = "post".equals(m.state), off = "off".equals(m.state) || "SUSPENDED".equals(m.detail);

        // the surface: a shade lighter than the launchers behind it, a faint rim, a top highlight on upcoming cards only
        cv.drawColor(done ? Color.rgb(38, 41, 48) : Color.rgb(44, 48, 56));
        if (!done) {
            p.setShader(new LinearGradient(0, 0, 0, H * 0.6f, Color.argb(32, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, p);
        }
        if (live) {
            p.setShader(new android.graphics.RadialGradient(480, 250, 420, Color.argb(140, 200, 16, 46), Color.argb(0, 200, 16, 46), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, p);
        }
        p.setShader(null);
        Paint rim = new Paint();
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2);
        rim.setColor(Color.argb(28, 255, 255, 255));
        cv.drawRect(1, 1, W - 1, H - 1, rim);

        // top right: the date or the state. White for today and tomorrow, red with a dot while live; a match that is
        // off keeps its date here and says why at the bottom, where a result would be
        String status;
        int sBg, sFg;
        boolean dot = false;
        if (off) { status = caps(I18n.day(m.start)); sBg = DIM; sFg = SECONDARY; }
        else if (awaiting) { status = caps(I18n.detail(m.detail)); sBg = DIM; sFg = SECONDARY; }
        else if (live) {
            // the clock or the phase; "LIVE" itself only when the feed gave neither (on the TV tile the launcher's own
            // badge already says LIVE, so the chip says "in progress" there)
            status = m.detail == null || m.detail.isEmpty() ? (tv ? caps(I18n.detail("IN_PROGRESS")) : "LIVE") : caps(I18n.detail(m.detail));
            sBg = RED; sFg = Color.WHITE; dot = true;
        }
        else if (done) { status = caps(chipFinal(m)) + " · " + caps(I18n.day(m.start)); sBg = DIM; sFg = SECONDARY; }
        else if ("DELAYED".equals(m.detail)) { status = caps(I18n.detail(m.detail)); sBg = AMBER; sFg = INK; }
        else {
            String day = I18n.day(m.start);
            boolean soon = !m.tbd && (day.equals(I18n.t("today")) || day.equals(I18n.t("tomorrow")));
            status = m.tbd ? caps(I18n.t("chip_tba")) : caps(day);
            sBg = soon ? Color.WHITE : DIM;
            sFg = soon ? INK : SECONDARY;
        }
        float ph = 72;
        float pw = pill(cv, W - 36, 30, ph, tv ? 46 : 44, status, sBg, sFg, dot, true, 520);

        // top left: the competition, on both variants; TBA: with the date, which the chip gave up. Stale: an amber
        // warning sign (drawn, not a fallback-font glyph) and "not updated" instead
        String label = stale ? caps(I18n.t("not_updated").replace("\u26a0", "").trim()) : caps(I18n.comp(m.comp)) + (m.tbd ? " · " + caps(I18n.day(m.start)) : "");
        p.setColor(stale ? AMBER : SECONDARY);
        p.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(tv ? 44 : 38);
        float lx = 36, lmax = W - 36 - pw - 36 - 28;
        while (p.measureText(label) > lmax && p.getTextSize() > (tv ? 36 : 32)) p.setTextSize(p.getTextSize() - 2);  // a little smaller, then cut
        float cap = capHeight(p), ly = 30 + ph / 2 + cap / 2;  // its caps centred on the chip
        if (stale) { warning(cv, lx, ly, cap, p); lx += cap * 1.12f + 14; lmax -= cap * 1.12f + 14; }
        cv.drawText(ellipsize(p, label, lmax), lx, ly, p);

        float cy = 228, cr = tv ? 96 : 92;
        badge(cv, Net.image(c, m.homeCrest), m.home, 190, cy, cr);
        badge(cv, Net.image(c, m.awayCrest), m.away, 770, cy, cr);
        p.setColor(Color.WHITE);
        p.setTypeface(face("sans-serif-condensed", Typeface.BOLD));
        // one rule in both sizes: a name may fill about 8.4 em at the floor size before it breaks into two lines
        drawNames(cv, p, caps(I18n.shortTeam(m.home)), caps(I18n.shortTeam(m.away)), tv ? 376 : 368, tv ? 320 : 300, tv ? 46 : 40, tv ? 38 : 36);

        if (m.hasScore() && (live || done)) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setTypeface(face("sans-serif-black", Typeface.NORMAL));
            float size = tv ? 156 : 132;
            s.setTextSize(size);
            boolean wide = Math.max(m.hs.length(), m.as.length()) >= 3;  // basketball: closer to the dash, less shrinking
            float half = wide ? 30 : 40, limit = wide ? 150 : 128;
            while (Math.max(s.measureText(m.hs), s.measureText(m.as)) > limit && size > 60) s.setTextSize(size -= 4);  // clear of the crests
            float base = cy + capHeight(s) / 2;
            Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
            dash.setColor(Color.argb(done ? 150 : 200, 255, 255, 255));  // never outranking a dimmed loser's score
            cv.drawRect(468, cy - 5, 492, cy + 5, dash);
            int res = m.result();
            boolean dimHome = done && res != 9 && (m.clubHome() ? res < 0 : res > 0);
            boolean dimAway = done && res != 9 && (m.clubHome() ? res > 0 : res < 0);
            s.setColor(Color.argb(dimHome ? 160 : 255, 255, 255, 255));
            s.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(m.hs, 480 - half, base, s);
            s.setColor(Color.argb(dimAway ? 160 : 255, 255, 255, 255));
            s.setTextAlign(Paint.Align.LEFT);
            cv.drawText(m.as, 480 + half, base, s);
        } else if (live) {  // in play, no score from the feed: the word stands in for the score
            p.setTypeface(face("sans-serif-black", Typeface.NORMAL));
            p.setColor(Color.WHITE);
            fitText(cv, p, "LIVE", W / 2f, cy + 36, 300, tv ? 112 : 100);
        } else {  // the kick-off time: faded when the match is off, "–:–" softened while the time is unknown
            p.setTypeface(face("sans-serif", Typeface.NORMAL));
            p.setColor(off ? Color.argb(90, 255, 255, 255) : m.tbd ? Color.argb(153, 255, 255, 255) : Color.WHITE);
            drawTime(cv, p, m.tbd ? "–:–" : I18n.time(m.start), W / 2f, cy + (tv ? 46 : 40), tv ? 132 : 112);
        }

        if (done) {
            int r = m.result();
            if (r != 9) {
                p.setTypeface(face("sans-serif-black", Typeface.NORMAL));
                p.setColor(r > 0 ? GREEN : r == 0 ? GREY : RED);
                p.setLetterSpacing(0.04f);
                String word = caps(I18n.t(r > 0 ? "win" : r == 0 ? "draw" : "loss")) + (m.pens != null ? " · " + caps(I18n.t("penalties")) + " " + m.pens : "");
                fitText(cv, p, word, W / 2f, tv ? 489 : 487, 760, tv ? 54 : 48);  // on the channel strips' centre line
                p.setLetterSpacing(0);
            }
        } else if (off) {  // POSTPONED, CANCELLED, SUSPENDED: in amber where a result would be
            p.setTypeface(face("sans-serif-black", Typeface.NORMAL));
            p.setColor(AMBER);
            p.setLetterSpacing(0.04f);
            fitText(cv, p, caps(I18n.detail(m.detail)), W / 2f, tv ? 489 : 487, 760, tv ? 54 : 48);
            p.setLetterSpacing(0);
        } else if (!awaiting) {
            drawWhere(c, cv, m, tv);
        }
        return b;
    }

    /**
     * The bottom strip of an upcoming card, one grammar for every source: [LIKELY] [channel]+ [number]. A channel is
     * its logo when Fanela has one (Greek guides and networks, listings that gave one), else its name in a text pill;
     * a known channel number follows in a box. A guess is led by the hedge word and drawn a little softer. Without a
     * channel: the honest text ("to be announced" in Greece, "check your guide" where Fanela has no data).
     */
    static void drawWhere(Context c, Canvas cv, Match m, boolean tv) {
        float k = tv ? 1.18f : 1f;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
        if (!m.channelKnown) {
            t.setColor(SECONDARY);
            fitText(cv, t, caps(I18n.t(Where.noData(m) ? "check_guide" : "channel_tba")), W / 2f, 484, 860, tv ? 40 : 36);
            return;
        }
        List<String> names = channels(m);
        List<Channels.Logo> logos = Channels.logos(c, m);
        int n = Math.min(names.size(), logos.size());
        if (n == 0) return;
        String lead = m.channelSure ? "" : caps(I18n.t("likely"));
        t.setTextSize(40);
        t.setColor(LEAD);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);  // a channel name in a pill
        pt.setTypeface(face("sans-serif", Typeface.BOLD));
        pt.setTextSize(40);
        pt.setColor(LEAD);
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);  // the channel number in its box
        num.setTypeface(face("sans-serif", Typeface.BOLD));
        num.setTextSize(44);
        num.setColor(Color.WHITE);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(DIM);
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (!m.channelSure) lp.setAlpha(217);  // a guess is shown a little softer
        float logoH = n > 1 ? 50 : 58, maxLogoW = n > 1 ? 230 : 320, pillH = 64, pad = 24, gap = 34, y = 470;
        Bitmap[] img = new Bitmap[n];
        float[] w = new float[n], h = new float[n], bw = new float[n];
        float total = lead.isEmpty() ? 0 : t.measureText(lead) + 22;
        for (int i = 0; i < n; i++) {
            Bitmap src = Net.image(c, logos.get(i).url);
            if (src != null) {
                img[i] = trim(src);
                if (img[i] != src) src.recycle();
                float ratio = (float) img[i].getWidth() / img[i].getHeight();
                h[i] = ratio < 2.2f ? logoH * (n > 1 ? 1.2f : 1.3f) : logoH; w[i] = h[i] * ratio;  // squarer logos (a shield) need more height to read
                if (w[i] > maxLogoW) { w[i] = maxLogoW; h[i] = Math.max(30, w[i] / ratio); w[i] = h[i] * ratio; }
            } else { w[i] = pt.measureText(caps(names.get(i))) + 2 * pad; h[i] = pillH; }
            int no = logos.get(i).no;
            bw[i] = no > 0 ? num.measureText(String.valueOf(no)) + 26 : 0;
            total += w[i] + (no > 0 ? 16 + bw[i] : 0) + (i > 0 ? gap : 0);
        }
        float cap = 880 / k, scale = (total > cap ? cap / total : 1f) * k;  // never wider than the card; the TV tile larger
        cv.save();
        cv.scale(scale, scale, W / 2f, y);
        float x = W / 2f - total / 2;
        if (!lead.isEmpty()) { cv.drawText(lead, x, y + capHeight(t) / 2, t); x += t.measureText(lead) + 22; }
        Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
        sep.setColor(Color.argb(90, 255, 255, 255));
        for (int i = 0; i < n; i++) {
            if (i > 0) { cv.drawCircle(x + gap / 2, y, 4, sep); x += gap; }
            if (img[i] != null) {
                drawBitmap(cv, img[i], new RectF(x, y - h[i] / 2, x + w[i], y + h[i] / 2), lp);
                img[i].recycle();
            } else {
                cv.drawRoundRect(new RectF(x, y - h[i] / 2, x + w[i], y + h[i] / 2), h[i] / 2, h[i] / 2, bg);
                cv.drawText(caps(names.get(i)), x + pad, y + capHeight(pt) / 2, pt);
            }
            x += w[i];
            int no = logos.get(i).no;
            if (no > 0) {
                x += 16;
                cv.drawRoundRect(new RectF(x, y - 30, x + bw[i], y + 30), 12, 12, bg);
                cv.drawText(String.valueOf(no), x + 13, y + capHeight(num) / 2, num);
                x += bw[i];
            }
        }
        cv.restore();
    }

    static void saveAtomic(Context c, Bitmap big, String name) throws Exception { saveAtomic(c, big, name, false); }

    /** full: keep 960x540 (home-screen cards, shown at 544-640 px); else half size (the app's own rows). */
    static void saveAtomic(Context c, Bitmap big, String name, boolean full) throws Exception {
        Bitmap b = full ? big.copy(Bitmap.Config.ARGB_8888, false) : Bitmap.createScaledBitmap(big, W / 2, H / 2, true);
        b.setHasAlpha(false);  // the card is opaque: an RGB file, decoded at half the memory (RGB_565)
        File f = new File(c.getFilesDir(), name + ".png");
        File tmp = new File(c.getFilesDir(), name + ".png." + Thread.currentThread().getId() + ".tmp");
        try {
            try (FileOutputStream o = new FileOutputStream(tmp)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
            if (!tmp.renameTo(f)) throw new Exception("rename");
        } finally {
            tmp.delete();
            big.recycle();
            b.recycle();
        }
    }

    static boolean have(Context c, String url) {
        return url != null && !url.isEmpty() && (url.startsWith("res:") || Net.imageFile(c, url).exists());
    }

    /** Which of the match's images are on the TV now (a card is redrawn when one arrives). */
    static String images(Context c, Match m) {
        StringBuilder b = new StringBuilder().append(have(c, m.homeCrest)).append(have(c, m.awayCrest));
        for (Channels.Logo l : Channels.logos(c, m)) b.append(have(c, l.url)).append(l.url).append(l.no);
        return b.toString();
    }

    static String finalWord(Match m) {
        return "FT_AET".equals(m.detail) || "FT_PEN".equals(m.detail) ? I18n.detail(m.detail) : I18n.t("final");
    }

    /** Row logo: the club crest on a white disc (256 px), or the club badge when there is no crest. */
    static Bitmap rowLogo(Context c, Teams.Club k) {
        Ui.fonts(c);
        Bitmap crest = Net.image(c, k.crest);
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        if (crest == null) {
            Badges.draw(cv, k.name, 128, 128, 120);
            return b;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        cv.drawCircle(128, 128, 128, p);
        float s = Math.min(180f / crest.getWidth(), 180f / crest.getHeight());
        float w = crest.getWidth() * s, h = crest.getHeight() * s;
        drawBitmap(cv, crest, new RectF(128 - w / 2, 128 - h / 2, 128 + w / 2, 128 + h / 2));
        crest.recycle();
        return b;
    }

    /** Row logo, built only when the stored one is out of date (tag: crest URL + whether it is downloaded). */
    static HomeRows.Logo rowLogoLazy(final Context c, final Teams.Club k) {
        return new HomeRows.Logo() {
            public String tag() {
                boolean have = Net.imageFile(c, k.crest == null ? "" : k.crest).exists();
                return k.crest + ":" + have + (have ? "" : ":" + Badges.initials(k.name));  // badge text follows the language
            }
            public Bitmap get() { return rowLogo(c, k); }
        };
    }

    /**
     * The logo next to a club's row on the TV's home screen: the Fanela shirt in that club's kit on the cards' dark disc
     * (Brand.rowLogo), so each row says whose it is and which app it comes from. Stored when the row is created (before
     * the add-row prompt) and redrawn only when the club's colours change.
     */
    static HomeRows.Logo channelLogoLazy(final Context c, final Teams.Club k) {
        final ClubTokens t = CardStyles.tokens(k, (ClubTokens.CrestInfo) null, null);
        return new HomeRows.Logo() {
            public String tag() { return Brand.rowLogoTag(t); }
            public Bitmap get() { return Brand.rowLogo(c, t); }
        };
    }

    // ---------- the home-screen rows ----------
    static synchronized void publishAll(Context c, List<Match> all) throws Exception {
        try { Brand.applyIfIdle(c); } catch (Exception ignored) { }  // a tile switch that waited (only when no screen is open)
        for (String id : Teams.followed(c)) {
            Teams.Club k = Teams.get(c, id);
            if (k == null) continue;
            try { publish(c, k, all); } catch (Throwable ignored) { }
        }
        HomeRows.removeUnfollowed(c);
    }

    /** A short stand-in for a signature (hundreds of characters of URLs): what the "cards" preferences store. */
    static String digest(String sig) { return Integer.toHexString(sig.hashCode()) + ":" + sig.length(); }

    /** A card ready to show: the match, its image file and content URI. */
    static final class Ref {
        Match m; String name, uri; boolean last, stale;
    }

    /** Draws the card unless an identical one is already on disk. Returns null if it could not be drawn. */
    static synchronized Ref ensure(Context c, Teams.Club k, Match m, String name, boolean last, long now) {  // one writer per card file
        SharedPreferences sp = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        boolean stale = Store.stale(c, k.id, m.team);
        boolean tv = name.contains("_tv");
        String sig = digest(CARD_VERSION + I18n.lang() + m.signature() + stale + I18n.day(m.start) + I18n.time(m.start)
            + images(c, m)  // redraw when images arrive
            // the TV card: its style, the scores setting, its state words, and the club's own crest (the ghost, and
            // Signature's sharp-crest test: a card drawn before it was cached is redrawn once it is)
            + (tv ? Appearance.cardStyle(c) + Appearance.showScores(c) + CardModel.of(m, true, stale, now).stateLine
                + k.crest + have(c, k.crest) : ""));
        String uri = sp.getString("uri_" + name, null);
        File f = new File(c.getFilesDir(), name + ".png");
        if (uri == null || !sig.equals(sp.getString("sig_" + name, null)) || !f.exists()) {
            try {
                if (tv) renderTv(c, k, m, stale, name, now); else render(c, m, stale, name);
                uri = "content://" + ArtProvider.authority(c) + "/" + name + ".png?v=" + now;
                sp.edit().putString("sig_" + name, sig).putString("uri_" + name, uri).apply();
            } catch (Throwable t) {
                if (uri == null || !f.exists()) return null;
            }
        }
        Ref r = new Ref();
        r.m = m; r.name = name; r.uri = uri; r.last = last; r.stale = stale;
        return r;
    }

    /** Upcoming matches of one team after the next one, soonest first (for the app's own screen). */
    static List<Match> upcoming(List<Match> all, String club, String team, Match skip, long now, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : all)
            if (club.equals(m.club) && team.equals(m.team) && "pre".equals(m.state) && (m.start > now || (m.tbd && Store.sameViewerDay(m.start, now)))
                && (skip == null || !m.key.equals(skip.key))) out.add(m);
        java.util.Collections.sort(out, new java.util.Comparator<Match>() {
            public int compare(Match a, Match b) { return Long.compare(a.start, b.start); }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Recent results of one team, newest first. */
    static List<Match> results(List<Match> all, String club, String team, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) if (club.equals(m.club) && team.equals(m.team) && "post".equals(m.state) && m.hasScore()) out.add(m);
        java.util.Collections.sort(out, new java.util.Comparator<Match>() {
            public int compare(Match a, Match b) { return Long.compare(b.start, a.start); }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /**
     * The cards for the app's own screen, in reading order: a live match first, then the coming matches of both sports
     * by date, then the latest results, newest first. Draws only what changed. Card files start with "c" so the home
     * screen may read them too (ArtProvider).
     */
    static List<Ref> forApp(Context c, Teams.Club k, List<Match> all) { return forApp(c, k, all, true); }

    /** render false: the same slots from the card files already on the TV, nothing drawn (a card not drawn yet has URI ""). */
    static List<Ref> forApp(Context c, Teams.Club k, List<Match> all, boolean render) {
        long now = System.currentTimeMillis();
        String[] teams = k.teams(Teams.basketball(c));
        List<Ref> coming = new ArrayList<>(), done = new ArrayList<>();
        for (String team : teams) {
            String t = "c" + k.id + "_" + team.toLowerCase(Locale.US);
            Match next = pickNow(all, k.id, team, now);
            if (next != null) add(coming, slot(c, k, next, t + "_now", false, now, render));
            int n = 0;
            for (Match m : upcoming(all, k.id, team, next, now, 3)) add(coming, slot(c, k, m, t + "_up" + (n++), false, now, render));
            n = 0;
            for (Match m : results(all, k.id, team, 2)) add(done, slot(c, k, m, t + (n++ == 0 ? "_last" : "_res" + n), true, now, render));
        }
        java.util.Collections.sort(coming, COMING);
        java.util.Collections.sort(done, new java.util.Comparator<Ref>() {
            public int compare(Ref a, Ref b) { return Long.compare(b.m.start, a.m.start); }
        });
        coming.addAll(done);
        return coming;
    }

    /** The card drawn (ensure), or, without rendering, whatever is on disk for its name; the latter skips ensure's lock on purpose. */
    private static Ref slot(Context c, Teams.Club k, Match m, String name, boolean last, long now, boolean render) {
        if (render) return ensure(c, k, m, name, last, now);
        Ref r = new Ref();
        r.m = m; r.name = name; r.last = last; r.stale = Store.stale(c, k.id, m.team);
        String uri = c.getSharedPreferences("cards", Context.MODE_PRIVATE).getString("uri_" + name, null);
        r.uri = uri != null && new File(c.getFilesDir(), name + ".png").exists() ? uri : "";
        return r;
    }

    /** Coming matches: whatever is live first, then by kick-off. */
    static final java.util.Comparator<Ref> COMING = new java.util.Comparator<Ref>() {
        public int compare(Ref a, Ref b) {
            boolean la = live(a.m), lb = live(b.m);
            if (la != lb) return la ? -1 : 1;
            return Long.compare(a.m.start, b.m.start);
        }
    };

    private static void add(List<Ref> out, Ref r) { if (r != null) out.add(r); }

    /** One-line description of a card (the focus line in the app, the tile description on the home screen). */
    static String describe(Context c, Teams.Club k, Ref r) {
        Match m = r.m;
        boolean live = live(m) || "SUSPENDED".equals(m.detail);
        if (r.last) return I18n.comp(m.comp) + " · " + when(m);
        String stale = r.stale ? " · " + I18n.t("not_updated") + " (" + I18n.t("last_update") + " " + I18n.day(Store.lastUpdate(c, k.id, m.team))
            + " " + I18n.time(Store.lastUpdate(c, k.id, m.team)) + ")" : "";
        if ("off".equals(m.state)) return I18n.comp(m.comp) + " · " + I18n.detail(m.detail) + " · " + I18n.day(m.start) + stale;  // no channel for a match that is off
        // scores hidden: an extra-time, penalties or overtime phase would tell that the match is level
        boolean quiet = live && !Appearance.showScores(c) && CardModel.revealsLevel(m.detail);
        String state = quiet ? I18n.t("live") : live ? I18n.detail(m.detail)
            : when(m) + ("DELAYED".equals(m.detail) ? " · " + I18n.detail(m.detail) : "");
        return I18n.comp(m.comp) + " · " + state + " · " + where(m) + stale;
    }

    /**
     * The title when scores are hidden on the TV: the state and the teams, never the numbers, and never "(pens)" or
     * "(AET)": extra time or penalties would tell that the match was level.
     */
    static String headlineNoScore(Ref r) {
        Match m = r.m;
        if (r.last) return I18n.t("final") + ": " + m.title();
        return live(m) ? I18n.t("live") + " · " + m.title() : m.title();
    }

    static String headline(Ref r) {
        Match m = r.m;
        boolean live = live(m);
        if (r.last) return finalWord(m) + ": " + m.scoreLine();
        return (live && m.hasScore() ? I18n.t("live") + " " + m.hs + "–" + m.as + " · " : live ? I18n.t("live") + " · " : "") + m.title();
    }

    static void publish(Context c, Teams.Club k, List<Match> all) throws Exception {
        long now = System.currentTimeMillis();
        long id = HomeRows.channel(c, k.rowKey(), k.heading(), channelLogoLazy(c, k));
        SharedPreferences sp = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        List<HomeRows.Tile> tiles = new ArrayList<>();
        StringBuilder rowSig = new StringBuilder();
        boolean basket = Teams.basketball(c);
        for (int pass = 0; pass < 2; pass++) {
            boolean last = pass == 1;
            List<Ref> refs = new ArrayList<>();
            for (String team : new String[]{"FC", "BC"}) {
                if (!k.shows(team, basket)) continue;
                Match m = last ? pickLast(all, k.id, team) : pickNow(all, k.id, team, now);
                if (m == null) continue;
                add(refs, ensure(c, k, m, "c" + k.id + "_" + team.toLowerCase(Locale.US) + (last ? "_tvlast" : "_tvnow"), last, now));
            }
            if (last) java.util.Collections.sort(refs, new java.util.Comparator<Ref>() {
                public int compare(Ref a, Ref b) { return Long.compare(b.m.start, a.m.start); }
            });
            else java.util.Collections.sort(refs, COMING);
            for (Ref r : refs) {
                Match m = r.m;
                HomeRows.Tile t = new HomeRows.Tile();
                t.id = r.name;
                t.poster = r.uri;
                t.app = !last && m.channelKnown && HomeRows.installed(c, m.channelApp) ? m.channelApp : null;
                t.match = m.key;
                t.title = Appearance.showScores(c) ? headline(r) : headlineNoScore(r);  // the launcher's title must not spoil
                t.desc = describe(c, k, r);
                t.live = !last && live(m);
                tiles.add(t);
                rowSig.append(t.id).append(t.poster).append(t.title).append(t.desc).append(t.live).append(t.app);
            }
        }
        String rs = digest(id + ":" + rowSig);
        if (rs.equals(sp.getString("row_sig_" + k.id, null))) return;
        HomeRows.setTiles(c, id, tiles);
        sp.edit().putString("row_sig_" + k.id, rs).apply();
    }

    /** Sample cards for every state (design checks), for the first followed club: demo_*.png. */
    static void demo(Context c) { demo(c, null, null, null, "demo_"); }

    /**
     * Sample cards for any club and language without changing the user's settings (design checks):
     * a club that is not followed is fetched once for the demo only (nothing is stored).
     */
    static void demo(Context c, String clubId, String lang, String country, String prefix) {
        I18n.load(c);
        List<Match> all = Engine.refresh(c, false);
        if (lang != null) I18n.use(lang);
        if (country != null) Where.use(country);  // this thread only: the setting is not touched
        long now = System.currentTimeMillis();
        String club = clubId;
        if (club == null) {
            if (Teams.followed(c).isEmpty()) return;
            club = Teams.followed(c).iterator().next();
        } else if (!Teams.followed(c).contains(club) || country != null) {
            Teams.Club k = Teams.find(c, club);
            if (k == null) return;
            all = new ArrayList<>();
            for (Sources.Result r : Sources.fetch(c, k, true)) all.addAll(r.list);
            Channels.load(c);
            List<Watch> ws = Where.providers(c, Where.country());
            for (Watch w : ws) w.prepare(c);
            GreeceProvider g = Where.greece(ws);
            List<Match> found = g == null ? null : g.discover(all, k, true);
            if (found != null) all.addAll(found);
            for (Watch w : ws) w.apply(all, k, w.fetch(c, k, all, true));
            Crests.resolve(c, all);
            Crests.fill(c, all);
            Engine.timeStates(all, now);
            Engine.prefetchImages(c, all, new java.util.HashSet<String>());
            Net.fetchImage(c, k.crest);
        }
        for (String team : new String[]{"FC", "BC"}) {
            String t = team.toLowerCase(Locale.US);
            try {
                Match next = pickNow(all, club, team, now), last = pickLast(all, club, team);
                if (next != null) {
                    both(c, next, false, prefix + t + "_pre");
                    Match lv = next.copy();
                    lv.state = "in"; lv.hs = "FC".equals(team) ? "2" : "101"; lv.as = "FC".equals(team) ? "1" : "99";
                    lv.detail = "FC".equals(team) ? "90'+3'" : "OT 00:41";
                    both(c, lv, false, prefix + t + "_live");
                    Match ht = next.copy(); ht.state = "in"; ht.hs = "1"; ht.as = "0"; ht.detail = "HT";
                    both(c, ht, false, prefix + t + "_ht");
                    Match pp = next.copy(); pp.state = "off"; pp.detail = "POSTPONED";
                    both(c, pp, false, prefix + t + "_off");
                    both(c, next.copy(), true, prefix + t + "_stale");
                    Match nl = next.copy(); nl.state = "in"; nl.hs = null; nl.as = null; nl.detail = "IN_PROGRESS";
                    both(c, nl, false, prefix + t + "_noscore");
                    Match uk = next.copy(); uk.channelKnown = false; uk.channelSure = false; uk.tbd = true;
                    both(c, uk, false, prefix + t + "_tba");
                    Match ln = next.copy(); ln.home = "Nottingham Forest"; ln.away = "Crvena Zvezda"; ln.homeCrest = null; ln.awayCrest = null;
                    both(c, ln, false, prefix + t + "_long");
                }
                if (last != null) {
                    both(c, last, false, prefix + t + "_post");
                    Match pen = last.copy(); pen.hs = "1"; pen.as = "1"; pen.pens = "4–3"; pen.detail = "FT_PEN"; pen.clubWon = pen.clubHome() ? 1 : -1;
                    both(c, pen, false, prefix + t + "_pens");
                    Match aet = last.copy(); aet.hs = "2"; aet.as = "2"; aet.detail = "FT_AET"; aet.clubWon = 0;
                    both(c, aet, false, prefix + t + "_aet");
                }
            } catch (Throwable ignored) { }
        }
    }

    /** A demo card in both variants: the app's (name.png) and the TV home screen's (name_tv.png). */
    private static void both(Context c, Match m, boolean stale, String name) throws Exception {
        render(c, m, stale, name);
        render(c, m, stale, name + "_tv");
    }
}
