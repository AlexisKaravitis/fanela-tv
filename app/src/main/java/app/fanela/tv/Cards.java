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
    static final int CARD_VERSION = 10;  // bump when the drawing changes: every card is redrawn once

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

    /** Where to watch. lineStart: the text starts a line (capital first letter), not mid-sentence. */
    static String where(Match m, boolean lineStart) {
        if (!m.channelKnown) return I18n.t(Where.noData(m) ? "check_guide" : "channel_tba");
        if (Where.plain(m)) {  // "Usually on Sky Sports / TNT Sports" (season table), "On Sky Sports Main Event" (a listing)
            String names = String.join(" / ", names(m));
            return I18n.f((m.channelSure ? "on_channels" : "usually_on") + (lineStart ? "_cap" : ""), names);
        }
        // one pattern everywhere: "Novasports Prime 318 · Magenta TV", or "Likely: Magenta Sport · Magenta TV"
        if (m.channelSure) return chan(m.channel) + (m.channelNo > 0 ? " " + m.channelNo : "") + " · " + appName(m.channelApp);
        return I18n.t(lineStart ? "likely_channel_cap" : "likely_channel") + ": " + chan(m.channel) + " · " + appName(m.channelApp);
    }

    /** The channels of a match, one name each ("Sky Sports · TNT Sports"), at most three; the TV's own guide adds the number. */
    static List<String> names(Match m) {
        List<String> out = new ArrayList<>();
        if (m.channel == null) return out;
        for (String s : m.channel.split("\\s*·\\s*")) if (!s.trim().isEmpty() && out.size() < 3) out.add(chan(s.trim()));
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
    static final int LIVE_RED = Color.rgb(227, 38, 47), AMBER = Color.rgb(245, 166, 35);

    /** The app font for a role: regular text, medium labels, bold names / scores / pills. */
    static Typeface face(String family, int style) {
        if (family.endsWith("black") || family.endsWith("condensed") || style == Typeface.BOLD) return Ui.BOLD;
        return family.endsWith("medium") ? Ui.MEDIUM : Ui.REGULAR;
    }

    static Bitmap appArt(Context c, String pkg) {
        try {
            android.graphics.drawable.Drawable d = c.getPackageManager().getApplicationBanner(pkg);
            if (d == null) d = c.getPackageManager().getApplicationIcon(pkg);
            int w = Math.max(1, d.getIntrinsicWidth()), h = Math.max(1, d.getIntrinsicHeight());
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(b));
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

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

    static Bitmap smooth(Bitmap b, float targetW) {
        while (b.getWidth() / 2f >= targetW && b.getWidth() > 2) b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, Math.max(1, b.getHeight() / 2), true);
        return b;
    }

    static void drawBitmap(Canvas cv, Bitmap b, RectF r) {
        cv.drawBitmap(smooth(b, r.width()), null, r, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
    }

    static void badge(Canvas cv, Bitmap crest, String raw, float cx, float cy) { badge(cv, crest, raw, cx, cy, 92); }

    static void badge(Canvas cv, Bitmap crest, String raw, float cx, float cy, float r) {
        if (crest == null) { Badges.draw(cv, raw, cx, cy, r); return; }
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(Color.WHITE);
        sh.setShadowLayer(16, 0, 6, Color.argb(115, 0, 0, 0));
        cv.drawCircle(cx, cy, r, sh);
        float box = r * 132 / 92, sc = Math.min(box / crest.getWidth(), box / crest.getHeight());
        float w = crest.getWidth() * sc, h = crest.getHeight() * sc;
        drawBitmap(cv, crest, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2));
    }

    static float pill(Canvas cv, float x, float y, String text, int bg, int fg, boolean dot, boolean alignRight, float maxW) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face("sans-serif", Typeface.BOLD));
        float size = 40, pad = 26, dotw = dot ? 30 : 0, h = 66;
        p.setTextSize(size);
        while (p.measureText(text) + 2 * pad + dotw > maxW && size > 24) p.setTextSize(size -= 2);
        float w = p.measureText(text) + 2 * pad + dotw;
        if (alignRight) x = x - w;
        Paint bgp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgp.setColor(bg);
        cv.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, bgp);
        p.setColor(fg);
        if (dot) cv.drawCircle(x + pad + 7, y + h / 2, 7, p);
        Rect r = new Rect();
        p.getTextBounds("Α", 0, 1, r);
        cv.drawText(text, x + pad + dotw, y + h / 2 + r.height() / 2f, p);
        return w;
    }

    /**
     * Both club names in one size, the largest both fit at, never below 30 px: a longer name goes onto two lines
     * instead of shrinking out of reach from the sofa.
     */
    static void drawNames(Canvas cv, Paint p, String home, String away, float y, float maxW, float size) {
        p.setTextSize(size);
        while (Math.max(p.measureText(home), p.measureText(away)) > maxW && size > 30) p.setTextSize(size -= 2);
        p.setTextAlign(Paint.Align.CENTER);
        String[][] both = {{home, "190"}, {away, "770"}};
        for (String[] n : both) {
            float cx = Float.parseFloat(n[1]);
            int sp = n[0].indexOf(' ', n[0].length() / 2) >= 0 ? n[0].indexOf(' ', n[0].length() / 2) : n[0].lastIndexOf(' ');
            if (p.measureText(n[0]) <= maxW || sp < 0) { fitText(cv, p, n[0], cx, y, maxW, size); p.setTextAlign(Paint.Align.CENTER); continue; }
            float lh = size * 1.02f;  // two lines, the first a little higher
            fitText(cv, p, n[0].substring(0, sp), cx, y - lh / 2, maxW, size);
            fitText(cv, p, n[0].substring(sp + 1), cx, y + lh / 2, maxW, size);
            p.setTextAlign(Paint.Align.CENTER);
        }
        p.setTextAlign(Paint.Align.LEFT);
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
    static void drawTime(Canvas cv, Paint p, String t, float cx, float y) { drawTime(cv, p, t, cx, y, 112); }

    static void drawTime(Canvas cv, Paint p, String t, float cx, float y, float big) {
        int sp = t.indexOf(' ');
        if (sp < 0) { fitText(cv, p, t, cx, y, 340, big); return; }
        String digits = t.substring(0, sp), suffix = t.substring(sp + 1);
        float small = big * 0.46f, gap = 10;
        p.setTextSize(big);
        float wd = p.measureText(digits);
        p.setTextSize(small);
        float ws = p.measureText(suffix), total = wd + gap + ws;
        float k = total > 340 ? 340 / total : 1;
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
        saveAtomic(c, draw(c, m, stale, name.contains("_tv")), name);
    }

    /** The card at full size (960x540), e.g. for the match screen. */
    static Bitmap draw(Context c, Match m, boolean stale) { return draw(c, m, stale, false); }

    /**
     * tv: the card for the TV's home screen, where it is shown about 290 px wide: no competition line (the tile's own
     * text has it), and larger crests, names, time and channels, so it reads from the sofa.
     */
    static Bitmap draw(Context c, Match m, boolean stale, boolean tv) {
        Ui.fonts(c);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean awaiting = "AWAITING".equals(m.detail);  // no live feed and past the usual end: not shown as live
        boolean live = live(m), done = "post".equals(m.state), off = "off".equals(m.state) || "SUSPENDED".equals(m.detail);

        cv.drawColor(done ? Color.rgb(26, 28, 33) : Color.rgb(32, 35, 41));
        p.setShader(new LinearGradient(0, 0, 0, H * 0.6f, Color.argb(20, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        if (live) {
            p.setShader(new android.graphics.RadialGradient(480, 250, 420, Color.argb(140, 200, 16, 46), Color.argb(0, 200, 16, 46), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, p);
        }
        p.setShader(null);

        String status;
        int sBg, sFg;
        boolean dot = false;
        if (off) { status = caps(I18n.detail(m.detail)); sBg = AMBER; sFg = Color.rgb(17, 17, 17); }
        else if (awaiting) { status = caps(I18n.detail(m.detail)); sBg = Color.argb(46, 255, 255, 255); sFg = Color.rgb(230, 232, 236); }
        else if (live) { status = m.detail == null || m.detail.isEmpty() ? "LIVE" : caps(I18n.detail(m.detail)); sBg = LIVE_RED; sFg = Color.WHITE; dot = true; }
        else if (done) { status = caps(finalWord(m)) + " · " + caps(I18n.day(m.start)); sBg = Color.argb(46, 255, 255, 255); sFg = Color.rgb(230, 232, 236); }
        else if ("DELAYED".equals(m.detail)) { status = caps(I18n.detail(m.detail)); sBg = AMBER; sFg = Color.rgb(17, 17, 17); }
        else {
            // a white chip only when the match is today or tomorrow; later dates are quiet, so the time stays the main thing
            String day = I18n.day(m.start);
            boolean soon = !m.tbd && (day.equals(I18n.t("today")) || day.equals(I18n.t("tomorrow")));
            status = m.tbd ? caps(I18n.t("time_tba")) : caps(day);
            sBg = soon ? Color.WHITE : Color.argb(46, 255, 255, 255);
            sFg = soon ? Color.rgb(17, 17, 17) : Color.rgb(230, 232, 236);
        }
        float pw = pill(cv, W - 32, 32, status, sBg, sFg, dot, true, 520);
        String label = stale ? caps(I18n.t("not_updated")) : caps(I18n.comp(m.comp)) + (off || m.tbd ? " · " + caps(I18n.day(m.start)) : "");
        p.setColor(stale ? AMBER : Color.rgb(174, 180, 191));
        p.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(34);
        float lmax = W - 32 - pw - 36 - 24;
        while (p.measureText(label) > lmax && p.getTextSize() > 20) p.setTextSize(p.getTextSize() - 2);
        if (!tv || stale || off || m.tbd) cv.drawText(label, 36, 78, p);  // postponed / TBA: the line carries the date

        float cr = tv ? 106 : 92;
        badge(cv, Net.image(c, m.homeCrest), m.home, 190, 228, cr);
        badge(cv, Net.image(c, m.awayCrest), m.away, 770, 228, cr);
        p.setColor(Color.WHITE);
        p.setTypeface(face("sans-serif-condensed", Typeface.BOLD));
        if (Where.greek(Where.country())) {  // Greece: exactly as Fanela 1.0 draws them
            fitText(cv, p, caps(I18n.shortTeam(m.home)), 190, tv ? 392 : 372, tv ? 350 : 300, tv ? 54 : 38);
            fitText(cv, p, caps(I18n.shortTeam(m.away)), 770, tv ? 392 : 372, tv ? 350 : 300, tv ? 54 : 38);
        } else drawNames(cv, p, caps(I18n.shortTeam(m.home)), caps(I18n.shortTeam(m.away)), tv ? 392 : 372, tv ? 350 : 300, tv ? 54 : 38);

        float cy = 228;
        if (m.hasScore() && (live || done)) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setTypeface(face("sans-serif-black", Typeface.NORMAL));
            float size = tv ? 150 : 132;
            s.setTextSize(size);
            while (Math.max(s.measureText(m.hs), s.measureText(m.as)) > (tv ? 120 : 128) && size > 60) s.setTextSize(size -= 4);  // clear of the crests
            Rect r = new Rect();
            s.getTextBounds("0", 0, 1, r);
            float base = cy + r.height() / 2f;
            Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
            dash.setColor(Color.argb(200, 255, 255, 255));
            cv.drawRect(468, cy - 5, 492, cy + 5, dash);
            int res = m.result();
            boolean dimHome = done && res != 9 && (m.clubHome() ? res < 0 : res > 0);
            boolean dimAway = done && res != 9 && (m.clubHome() ? res > 0 : res < 0);
            s.setColor(Color.argb(dimHome ? 140 : 255, 255, 255, 255));
            s.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(m.hs, 480 - 40, base, s);
            s.setColor(Color.argb(dimAway ? 140 : 255, 255, 255, 255));
            s.setTextAlign(Paint.Align.LEFT);
            cv.drawText(m.as, 480 + 40, base, s);
        } else if (live) {
            p.setTypeface(face("sans-serif-black", Typeface.NORMAL));
            p.setColor(Color.WHITE);
            fitText(cv, p, "LIVE", W / 2f, cy + 36, 300, 100);
        } else {
            p.setTypeface(face("sans-serif", Typeface.NORMAL));
            p.setColor(off ? Color.argb(110, 255, 255, 255) : Color.WHITE);
            drawTime(cv, p, m.tbd ? "–:–" : I18n.time(m.start), W / 2f, cy + (tv ? 46 : 40), tv ? 130 : 112);
            if (off) { Paint st = new Paint(); st.setColor(AMBER); cv.drawRect(330, cy - 2, 630, cy + 6, st); }
        }

        if (done) {
            int r = m.result();
            if (r != 9) {
                p.setTypeface(face("sans-serif-black", Typeface.NORMAL));
                p.setColor(r > 0 ? Color.rgb(61, 220, 132) : r == 0 ? Color.rgb(176, 182, 192) : Color.rgb(255, 90, 95));
                String word = caps(I18n.t(r > 0 ? "win" : r == 0 ? "draw" : "loss")) + (m.pens != null ? " · " + caps(I18n.t("penalties")) + " " + m.pens : "");
                fitText(cv, p, word, W / 2f, tv ? 484 : 472, 700, tv ? 56 : 42);
            }
        } else if (!off && !awaiting) {
            drawWhere(c, cv, m, tv);
        }
        return b;
    }

    /**
     * The bottom line of an upcoming card: the channel logo(s) with their numbers ("Magenta Sport 1 League 301",
     * "ΣΠΟΡ FM TV 1 · ΣΚΑΪ 110"), "Likely" in front when it is a guess; text when no logo is available yet.
     */
    static void drawWhere(Context c, Canvas cv, Match m) { drawWhere(c, cv, m, false); }

    static void drawWhere(Context c, Canvas cv, Match m, boolean tv) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!m.channelKnown) {
            t.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
            t.setColor(Color.rgb(150, 156, 168));
            boolean gr = Where.greek(Where.country());  // Greece: "to be announced" as Fanela 1.0 draws it
            fitText(cv, t, caps(I18n.t(Where.noData(m) ? "check_guide" : "channel_tba")), W / 2f, tv ? 490 : gr ? 482 : 486, 860, tv ? 46 : gr ? 34 : 42);
            return;
        }
        List<Channels.Logo> logos = Channels.logos(c, m);
        if (Where.plain(m) && logos.isEmpty()) { drawPills(cv, m); return; }
        List<Bitmap> imgs = new ArrayList<>();
        for (Channels.Logo l : logos) {
            Bitmap b = trim(Net.image(c, l.url));
            if (b == null) { imgs.clear(); break; }  // not downloaded yet: text for now, redrawn when it arrives
            imgs.add(b);
        }
        if (!imgs.isEmpty()) {
            String likely = m.channelSure ? "" : caps(I18n.t("likely"));
            Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
            num.setTypeface(face("sans-serif", Typeface.BOLD));
            num.setTextSize(44);
            num.setColor(Color.WHITE);
            t.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
            t.setTextSize(30);
            t.setColor(Color.rgb(160, 166, 178));
            float maxLogoW = imgs.size() > 1 ? 230 : 320, h0 = imgs.size() > 1 ? 50 : 58, gap = 34;
            float[] w = new float[imgs.size()], h = new float[imgs.size()], bw = new float[imgs.size()];
            float total = likely.isEmpty() ? 0 : t.measureText(likely) + 22;
            for (int i = 0; i < imgs.size(); i++) {
                float ratio = (float) imgs.get(i).getWidth() / imgs.get(i).getHeight();
                h[i] = ratio < 2.2f ? h0 * 1.35f : h0; w[i] = h[i] * ratio;  // squarer logos (a shield) need more height to read
                if (w[i] > maxLogoW) { w[i] = maxLogoW; h[i] = Math.max(30, w[i] / ratio); w[i] = h[i] * ratio; }
                int no = logos.get(i).no;
                bw[i] = no > 0 ? num.measureText(String.valueOf(no)) + 26 : 0;
                total += w[i] + (no > 0 ? 16 + bw[i] : 0) + (i > 0 ? gap : 0);
            }
            float cap = tv ? 700 : 880, scale = total > cap ? cap / total : 1f;  // never wider than the card
            if (tv) scale *= 1.26f;  // the TV tile: the same strip, larger
            cv.save();
            cv.scale(scale, scale, W / 2f, 470);
            float x = W / 2f - total / 2;
            if (!likely.isEmpty()) { cv.drawText(likely, x, 481, t); x += t.measureText(likely) + 22; }
            Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
            sep.setColor(Color.argb(90, 255, 255, 255));
            for (int i = 0; i < imgs.size(); i++) {
                if (i > 0) { cv.drawCircle(x + gap / 2, 470, 4, sep); x += gap; }
                Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                if (!m.channelSure) lp.setAlpha(200);  // a guess is shown a little softer
                cv.drawBitmap(smooth(imgs.get(i), w[i]), null, new RectF(x, 470 - h[i] / 2, x + w[i], 470 + h[i] / 2), lp);
                x += w[i];
                int no = logos.get(i).no;
                if (no > 0) {
                    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bg.setColor(Color.argb(46, 255, 255, 255));
                    x += 16;
                    cv.drawRoundRect(new RectF(x, 440, x + bw[i], 500), 12, 12, bg);
                    cv.drawText(String.valueOf(no), x + 13, 486, num);
                    x += bw[i];
                }
            }
            cv.restore();
            return;
        }
        Bitmap art = appArt(c, m.channelApp);
        String label = (m.channelSure ? "" : caps(I18n.t("likely")) + ": ") + caps(chan(m.channel));
        t.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(tv ? 44 : 34);
        t.setColor(m.channelSure ? Color.rgb(230, 232, 236) : Color.rgb(160, 166, 178));
        float h = 56, w = art == null ? 0 : Math.min(140, h * art.getWidth() / art.getHeight());
        h = art == null ? 0 : w * art.getHeight() / art.getWidth();
        while (w + 20 + t.measureText(label) > 820 && t.getTextSize() > 22) t.setTextSize(t.getTextSize() - 2);
        float total = w + (w > 0 ? 20 : 0) + t.measureText(label), x0 = 480 - total / 2;
        if (art != null) {
            Bitmap r = Bitmap.createBitmap((int) Math.max(1, w), (int) Math.max(1, h), Bitmap.Config.ARGB_8888);
            Canvas rc = new Canvas(r);
            Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
            rc.drawRoundRect(new RectF(0, 0, w, h), 10, 10, rp);
            rp.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            rp.setFilterBitmap(true);
            rc.drawBitmap(smooth(art, w), null, new RectF(0, 0, w, h), rp);
            cv.drawBitmap(r, x0, 470 - h / 2, null);
        }
        cv.drawText(label, x0 + w + (w > 0 ? 20 : 0), 482, t);
    }

    /**
     * Channel names as text pills ("USUALLY ON  [SKY SPORTS]  [TNT SPORTS]"), for countries where Fanela has no logos.
     * A season-table guess is drawn a little softer, like the Greek "likely" logos.
     */
    static void drawPills(Canvas cv, Match m) {
        List<String> names = names(m);
        if (names.isEmpty()) return;
        String lead = m.channelSure ? "" : caps(I18n.t("usually"));
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(face("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(36);
        t.setColor(Color.rgb(160, 166, 178));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face("sans-serif", Typeface.BOLD));
        p.setTextSize(34);
        p.setColor(m.channelSure ? Color.WHITE : Color.rgb(222, 225, 231));
        float pad = 24, h = 60, gap = 14, y = 470;
        float[] w = new float[names.size()];
        float total = lead.isEmpty() ? 0 : t.measureText(lead) + 22;
        for (int i = 0; i < names.size(); i++) {
            w[i] = p.measureText(caps(names.get(i))) + 2 * pad;
            total += w[i] + (i > 0 ? gap : 0);
        }
        float scale = total > 880 ? 880 / total : 1f;  // never wider than the card
        cv.save();
        cv.scale(scale, scale, W / 2f, y);
        float x = W / 2f - total / 2;
        Rect r = new Rect();
        p.getTextBounds("A", 0, 1, r);
        if (!lead.isEmpty()) { cv.drawText(lead, x, y + r.height() / 2f, t); x += t.measureText(lead) + 22; }
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(m.channelSure ? 56 : 40, 255, 255, 255));
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) x += gap;
            cv.drawRoundRect(new RectF(x, y - h / 2, x + w[i], y + h / 2), h / 2, h / 2, bg);
            cv.drawText(caps(names.get(i)), x + pad, y + r.height() / 2f, p);
            x += w[i];
        }
        cv.restore();
    }

    static void saveAtomic(Context c, Bitmap big, String name) throws Exception {
        Bitmap b = Bitmap.createScaledBitmap(big, W / 2, H / 2, true);
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
     * The logo next to each row on the TV's home screen: the Fanela shirt in the favourite club's colours (Logo.rowLogo),
     * the same for every row. The row's title already names the club and its cards show the crests; the logo says which
     * app the row comes from. Drawn only when the colours change.
     */
    static HomeRows.Logo channelLogoLazy(final Context c) {
        return new HomeRows.Logo() {
            public String tag() { Ui.theme(c); return Logo.rowLogoTag(); }
            public Bitmap get() { Ui.theme(c); return Logo.rowLogo(c); }
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

    /** A card ready to show: the match, its image file and content URI. */
    static final class Ref {
        Match m; String name, uri; boolean last, stale;
    }

    /** Draws the card unless an identical one is already on disk. Returns null if it could not be drawn. */
    static synchronized Ref ensure(Context c, Teams.Club k, Match m, String name, boolean last, long now) {  // one writer per card file
        SharedPreferences sp = c.getSharedPreferences("cards", Context.MODE_PRIVATE);
        boolean stale = Store.stale(c, k.id, m.team);
        String sig = CARD_VERSION + I18n.lang() + m.signature() + stale + I18n.day(m.start) + I18n.time(m.start)
            + images(c, m);  // redraw when images arrive
        String uri = sp.getString("uri_" + name, null);
        File f = new File(c.getFilesDir(), name + ".png");
        if (uri == null || !sig.equals(sp.getString("sig_" + name, null)) || !f.exists()) {
            try {
                render(c, m, stale, name);
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
    static List<Ref> forApp(Context c, Teams.Club k, List<Match> all) {
        long now = System.currentTimeMillis();
        String[] teams = k.teams(Teams.basketball(c));
        List<Ref> coming = new ArrayList<>(), done = new ArrayList<>();
        for (String team : teams) {
            String t = "c" + k.id + "_" + team.toLowerCase(Locale.US);
            Match next = pickNow(all, k.id, team, now);
            if (next != null) add(coming, ensure(c, k, next, t + "_now", false, now));
            int n = 0;
            for (Match m : upcoming(all, k.id, team, next, now, 3)) add(coming, ensure(c, k, m, t + "_up" + (n++), false, now));
            n = 0;
            for (Match m : results(all, k.id, team, 2)) add(done, ensure(c, k, m, t + (n++ == 0 ? "_last" : "_res" + n), true, now));
        }
        java.util.Collections.sort(coming, COMING);
        java.util.Collections.sort(done, new java.util.Comparator<Ref>() {
            public int compare(Ref a, Ref b) { return Long.compare(b.m.start, a.m.start); }
        });
        coming.addAll(done);
        return coming;
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
        String state = live || "off".equals(m.state) ? I18n.detail(m.detail)
            : when(m) + ("DELAYED".equals(m.detail) ? " · " + I18n.detail(m.detail) : "");
        return I18n.comp(m.comp) + " · " + state + " · " + where(m)
            + (r.stale ? " · " + I18n.t("not_updated") + " (" + I18n.t("last_update") + " " + I18n.day(Store.lastUpdate(c, k.id, m.team))
            + " " + I18n.time(Store.lastUpdate(c, k.id, m.team)) + ")" : "");
    }

    static String headline(Ref r) {
        Match m = r.m;
        boolean live = live(m);
        if (r.last) return finalWord(m) + ": " + m.scoreLine();
        return (live && m.hasScore() ? I18n.t("live") + " " + m.hs + "–" + m.as + " · " : live ? I18n.t("live") + " · " : "") + m.title();
    }

    static void publish(Context c, Teams.Club k, List<Match> all) throws Exception {
        long now = System.currentTimeMillis();
        long id = HomeRows.channel(c, k.rowKey(), k.label(), channelLogoLazy(c));
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
                t.title = headline(r);
                t.desc = describe(c, k, r);
                t.live = !last && live(m);
                tiles.add(t);
                rowSig.append(t.id).append(t.poster).append(t.title).append(t.desc).append(t.live).append(t.app);
            }
        }
        String rs = id + ":" + rowSig;
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
                    render(c, next, false, prefix + t + "_pre");
                    Match lv = next.copy();
                    lv.state = "in"; lv.hs = "FC".equals(team) ? "2" : "101"; lv.as = "FC".equals(team) ? "1" : "99";
                    lv.detail = "FC".equals(team) ? "90'+3'" : "OT 00:41";
                    render(c, lv, false, prefix + t + "_live");
                    Match pp = next.copy(); pp.state = "off"; pp.detail = "POSTPONED";
                    render(c, pp, false, prefix + t + "_off");
                    render(c, next.copy(), true, prefix + t + "_stale");
                    Match nl = next.copy(); nl.state = "in"; nl.hs = null; nl.as = null; nl.detail = "IN_PROGRESS";
                    render(c, nl, false, prefix + t + "_noscore");
                    Match uk = next.copy(); uk.channelKnown = false; uk.channelSure = false; uk.tbd = true;
                    render(c, uk, false, prefix + t + "_tba");
                }
                if (last != null) {
                    render(c, last, false, prefix + t + "_post");
                    Match pen = last.copy(); pen.hs = "1"; pen.as = "1"; pen.pens = "4–3"; pen.clubWon = pen.clubHome() ? 1 : -1;
                    render(c, pen, false, prefix + t + "_pens");
                }
            } catch (Throwable ignored) { }
        }
    }
}
