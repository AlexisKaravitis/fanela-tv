package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.List;

/**
 * What the sport panel (Club layout), the event screen and the Matchday stage share (SPEC-F1-UFC §7-§9): the sport's
 * monogram disc, the circuit outline and the octagon line, team-colour bars, faces and flags, the hidden-score slot,
 * the TITLE chip, the environments (F1's graphite field with racing stripes, UFC's two corners), the "when" column of
 * a hero, and the Club layout's small views for a sport (rail item, latest tile, timetable row, list header).
 * Everything is drawn in spec px; images are prepared off the UI thread (image()).
 */
final class SportViews {
    private SportViews() { }

    static final int GRAPHITE = 0xFF2B2D33, DISC = 0xFF24262B, IVORY = 0xFFF4F0EA, FACE = 0xFFEDEAE3, INK = 0xFF1B1B1F;
    static final int RED = 0xFFB5121B, BLUE = 0xFF1D4FA8, UFC_BG = 0xFF17171B, GOLD = 0xFFE8C25A, GOLD_INK = 0xFF1B1608;

    // ---------- images ----------
    private static final LruCache<String, Bitmap> IMAGES = new LruCache<String, Bitmap>(10 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** A downloaded image (flag, headshot), decoded once and cached; null when it is not on the TV. Not on the UI thread. */
    static Bitmap image(Context c, String url) {
        if (c == null || url == null || url.isEmpty()) return null;
        Bitmap b = IMAGES.get(url);
        if (b != null && !b.isRecycled()) return b;
        try { b = Net.image(c, url); } catch (Throwable t) { b = null; }
        if (b != null) IMAGES.put(url, b);
        return b;
    }

    private static final java.util.Set<String> TRIED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * The image, downloaded once per run when it is not on the TV yet (fetch; a background thread: the display or the
     * detail thread). The engine's prefetch usually has it already.
     */
    static Bitmap image(Context c, String url, boolean fetch) {
        Bitmap b = image(c, url);
        if (b != null || !fetch || url == null || url.isEmpty() || !TRIED.add(url)) return b;
        try { Net.fetchImage(c, url); } catch (Throwable ignored) { }
        return image(c, url);
    }

    /** The circuit's outline (Circuits, stream E): normalised points, null when there is none. */
    static float[] outline(Context c, SportEvent e) {
        if (e == null || !SportEvent.F1.equals(e.sport) || e.circuitId == null || e.circuitId.isEmpty()) return null;
        try {
            float[] o = Circuits.outline(c, e.circuitId);
            return o != null && o.length >= 6 ? o : null;
        } catch (Throwable t) { return null; }
    }

    // ---------- marks ----------
    /** The sport's monogram disc (§9.1): #24262B, a 3 px ring at white 22 %, "F1" / "UFC" in RC Bold, ivory. */
    static void monogram(Canvas cv, String sport, float cx, float cy, float d, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.alpha(DISC, alpha));
        cv.drawCircle(cx, cy, d / 2, p);
        p.setStyle(Paint.Style.STROKE);
        float ring = Math.max(1.5f, 3 * d / 120f);
        p.setStrokeWidth(ring);
        p.setColor(Ui.alpha(0xFFFFFFFF, 0.22f * alpha));
        cv.drawCircle(cx, cy, d / 2 - ring / 2, p);
        String m = SportText.monogram(sport);
        float size = (SportEvent.UFC.equals(sport) ? 0.34f : 0.46f) * d;
        Paint t = Ui.paint(Ui.COND_BOLD, size, Ui.alpha(IVORY, alpha));
        AppKit.centre(cv, t, m, cx, cy + 0.715f * size / 2);
    }

    private static final LruCache<String, AppArt.Decal> MARKS = new LruCache<>(8);

    /** The monogram as a decal (Matchday's row labels draw decals), size spec px. */
    static AppArt.Decal monogramDecal(Context c, String sport, float size) {
        float s = Ui.scale(c);
        String key = sport + "|" + size + "|" + s;
        AppArt.Decal d = MARKS.get(key);
        if (d != null) return d;
        int px = Math.max(2, Math.round(size * s));
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(s, s);
        Ui.fonts(c);
        monogram(cv, sport, size / 2, size / 2, size, 1);
        d = new AppArt.Decal(b, size, 0);
        MARKS.put(key, d);
        return d;
    }

    /** The outline fitted (contain) and centred in box, closed. */
    static Path circuit(float[] pts, RectF box) {
        if (pts == null || pts.length < 6) return null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            minX = Math.min(minX, pts[i]); maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]); maxY = Math.max(maxY, pts[i + 1]);
        }
        float w = Math.max(1e-4f, maxX - minX), h = Math.max(1e-4f, maxY - minY);
        float k = Math.min(box.width() / w, box.height() / h);
        float ox = box.centerX() - (minX + w / 2) * k, oy = box.centerY() - (minY + h / 2) * k;
        Path p = new Path();
        p.moveTo(pts[0] * k + ox, pts[1] * k + oy);
        for (int i = 2; i + 1 < pts.length; i += 2) p.lineTo(pts[i] * k + ox, pts[i + 1] * k + oy);
        p.close();
        return p;
    }

    /** The outline as a stroke (round caps and joins), inset by half the stroke so it never leaves the box. */
    static void drawCircuit(Canvas cv, float[] pts, RectF box, float stroke, int color) {
        RectF in = new RectF(box);
        in.inset(stroke / 2, stroke / 2);
        Path p = circuit(pts, in);
        if (p == null) return;
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(stroke);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        sp.setColor(color);
        cv.drawPath(p, sp);
    }

    /** A regular octagon, flat side up (UFC's watermark line; never the row mark). */
    static Path octagon(float cx, float cy, float r) {
        Path p = new Path();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    // ---------- colour, bars, slots, chips ----------
    /** A team colour that reads on the dark field: under luminance .22, 35 % white mixed in (§2.2). */
    static int lift(int color) {
        if ((color >>> 24) == 0) color |= 0xFF000000;
        if (color == 0xFF000000 || color == 0) return 0xFF8A8F98;
        return ClubTokens.luminance(color) < 0.22 ? ClubTokens.mix(color, 0xFFFFFFFF, 0.35) | 0xFF000000 : color;
    }

    /** The team-colour bar left of a code: w wide, the code's capital height, radius 4 (§2.2). */
    static void bar(Canvas cv, float x, float baseline, float size, float w, int color, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.alpha(lift(color), alpha));
        float cap = 0.715f * size;
        cv.drawRoundRect(new RectF(x, baseline - cap, x + w, baseline), Math.min(4, w / 2), Math.min(4, w / 2), p);
    }

    /** "▌NOR": the bar, its gap and the code from x on a baseline; returns the width. */
    static float code(Canvas cv, SportEvent.Entrant e, float x, float baseline, float size, float barW, float gap, int ink) {
        bar(cv, x, baseline, size, barW, e.color, (ink >>> 24) / 255f);
        Paint t = Ui.paint(Ui.COND_BOLD, size, ink);
        String code = code(e);
        cv.drawText(code, x + barW + gap, baseline, t);
        return barW + gap + t.measureText(code);
    }

    static float codeWidth(SportEvent.Entrant e, float size, float barW, float gap) {
        return barW + gap + Ui.paint(Ui.COND_BOLD, size, 0).measureText(code(e));
    }

    /** A driver's three-letter code (from the name when the feed gives none). */
    static String code(SportEvent.Entrant e) {
        if (e == null) return "";
        if (e.code != null && !e.code.isEmpty()) return e.code;
        String s = SportText.driverSurname(e);
        return Cards.caps(s.length() > 3 ? s.substring(0, 3) : s);
    }

    /** The hidden-score slot: one rounded bar, white 18 % (§2.3, §6). */
    static void slot(Canvas cv, float l, float t, float r, float b, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.alpha(0xFFFFFFFF, 0.18f * alpha));
        float rad = Math.min(14, (b - t) / 2);
        cv.drawRoundRect(new RectF(l, t, r, b), rad, rad, p);
    }

    /** The gold TITLE chip (34 tall, GS Bold 20 +10 %) from x, centred on cy; returns its width. */
    static float titleChip(Canvas cv, float x, float cy, float h, float alpha) {
        Paint t = AppKit.caps(Ui.BOLD, h * 20 / 34f, Ui.alpha(GOLD_INK, alpha), 0.1f);
        String w = SportText.t("title_chip");
        float pad = h * 14 / 34f, wd = t.measureText(w) + 2 * pad;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.alpha(GOLD, alpha));
        cv.drawRoundRect(new RectF(x, cy - h / 2, x + wd, cy + h / 2), h / 2, h / 2, p);
        cv.drawText(w, x + pad - AppKit.lead(t), Ui.baseline(Ui.BOLD, t.getTextSize(), cy), t);
        return wd;
    }

    /** A flag in its box (corner r), drawn to cover it. */
    static void flag(Canvas cv, Bitmap b, RectF box, float r, float alpha) {
        if (b == null || b.isRecycled()) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.round(255 * alpha));
        BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float k = Math.max(box.width() / b.getWidth(), box.height() / b.getHeight());
        m.setScale(k, k);
        m.postTranslate(box.centerX() - b.getWidth() * k / 2, box.centerY() - b.getHeight() * k / 2);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        cv.drawRoundRect(box, r, r, p);
    }

    /** Two initials (given name, surname): "CG". */
    static String initials(SportEvent.Fighter f) {
        if (f == null) return "";
        String[] w = (f.name == null ? "" : f.name.trim()).split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return "";
        String a = w[0].substring(0, 1), b = w.length > 1 ? w[w.length - 1].substring(0, 1) : "";
        return Cards.caps(a + b);
    }

    /**
     * A face disc (§8): ivory, the headshot drawn to cover it, top-aligned (the face in the upper two-thirds), a soft
     * shadow; the initials when there is no photo (both or neither: the caller passes null for both).
     */
    static void face(Canvas cv, Bitmap head, SportEvent.Fighter f, float cx, float cy, float d, float alpha, boolean shadow) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (shadow) {
            for (int i = 0; i < 6; i++) {
                float e = 2 + i * 2.5f;
                p.setColor(Ui.alpha(0xFF000000, 0.07f * alpha));
                cv.drawCircle(cx, cy + 10, d / 2 + e - 8, p);
            }
        }
        p.setColor(Ui.alpha(FACE, alpha));
        cv.drawCircle(cx, cy, d / 2, p);
        if (head != null && !head.isRecycled()) {
            Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            hp.setAlpha(Math.round(255 * alpha));
            BitmapShader sh = new BitmapShader(head, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float k = Math.max(d / head.getWidth(), d / head.getHeight());
            m.setScale(k, k);
            m.postTranslate(cx - head.getWidth() * k / 2, cy - d / 2);
            sh.setLocalMatrix(m);
            hp.setShader(sh);
            cv.drawCircle(cx, cy, d / 2, hp);
            return;
        }
        String in = initials(f);
        if (in.isEmpty()) return;
        float size = 0.46f * d * (in.length() > 1 ? 0.8f : 1);
        Paint t = Ui.paint(Ui.COND_BOLD, size, Ui.alpha(INK, alpha));
        AppKit.centre(cv, t, in, cx, cy + 0.715f * size / 2);
    }

    // ---------- environments ----------
    private static final LruCache<String, Bitmap> FIELDS = new LruCache<String, Bitmap>(16 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    static String fieldKey(String sport, float[] outline, int height) {
        return sport + "|" + height + "|" + (outline == null ? 0 : java.util.Arrays.hashCode(outline));
    }

    static Bitmap cachedField(String key) { return FIELDS.get(key); }

    /**
     * The sport's environment (§7, §8), 1920 wide and height tall, falling to the background from y 420: F1's
     * graphite with ivory racing stripes and the circuit as a faint watermark bleeding right and up; UFC's red corner
     * at the top left and blue at the top right, a faint octagon line. Drawn at half resolution, cached (any thread but
     * the UI's).
     */
    static Bitmap field(Context c, String sport, float[] outline, int height) {
        String key = fieldKey(sport, outline, height);
        Bitmap b = FIELDS.get(key);
        if (b != null) return b;
        float s = Math.min(1f, Ui.scale(c)) / 2;
        b = Bitmap.createBitmap(Math.round(Ui.W * s), Math.round(height * s), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.scale(s, s);
        cv.drawColor(Ui.APP_BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (SportEvent.UFC.equals(sport)) {
            cv.drawColor(UFC_BG);
            corner(cv, p, 0, 0, 1100, 760, RED, new float[]{0.72f, 0.25f, 0}, height);
            corner(cv, p, Ui.W, 0, 1100, 760, BLUE, new float[]{0.60f, 0.20f, 0}, height);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(40);
            p.setColor(Ui.alpha(0xFFFFFFFF, 0.06f));
            cv.drawPath(octagon(1500, 250, 520), p);
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setShader(new LinearGradient(0, 0, Ui.W, 0, GRAPHITE, ClubTokens.mix(GRAPHITE, 0xFF000000, 0.55) | 0xFF000000, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, Ui.W, height, p);
            p.setShader(null);
            p.setColor(Ui.alpha(IVORY, 0.035f));
            for (float x = 0; x < Ui.W; x += 112) cv.drawRect(x, 0, x + 56, Math.min(560, height), p);
            if (outline != null) drawCircuit(cv, outline, new RectF(1180, -120, 2100, 640), 44, Ui.alpha(0xFFFFFFFF, 0.07f));
        }
        p.setColor(0xFF000000);  // the shader's alpha is the paint's: opaque again after the stripes and the octagon
        p.setShader(new LinearGradient(0, 420, 0, height, Ui.APP_BG & 0xFFFFFF, Ui.APP_BG, Shader.TileMode.CLAMP));
        cv.drawRect(0, 420, Ui.W, height, p);
        p.setShader(null);
        FIELDS.put(key, b);
        return b;
    }

    /** A corner light: an rx x ry ellipse at (x, y), the colour at the given alphas at 0, 60 % and 100 %. */
    static void corner(Canvas cv, Paint p, float x, float y, float rx, float ry, int color, float[] a, float h) {
        RadialGradient g = new RadialGradient(0, 0, 1, new int[]{Ui.alpha(color, a[0]), Ui.alpha(color, a[1]), Ui.alpha(color, a[2])},
            new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(rx, ry);
        m.postTranslate(x, y);
        g.setLocalMatrix(m);
        p.setShader(g);
        cv.drawRect(0, 0, Ui.W, h, p);
    }

    // ---------- where to watch ----------
    /** A session's where-to-watch cell content (AppKit.watch through SessionWatch.asMatch); null when nothing is known. */
    static AppKit.Watch watch(Context c, SportEvent e, SportEvent.Session s) {
        if (s == null || s.watch == null) return null;
        AppKit.Watch w;
        try { w = AppKit.watch(c, s.watch.asMatch(s.start, e == null ? "" : e.sport)); } catch (Throwable t) { return null; }
        if (w != null && w.app != null) w.app = s.watch.app == null ? null : SportWhere.appName(s.watch.app);
        return w;
    }

    /** The session's channel app, when it is known and on the TV. */
    static String installedApp(Context c, SportEvent.Session s) {
        if (s == null || s.watch == null || !s.watch.known || s.watch.app == null) return null;
        return HomeRows.installed(c, s.watch.app) ? s.watch.app : null;
    }

    // ---------- a hero's "when" column ----------
    /**
     * What a hero's right column shows (§7, §8, §9.1), decided once: the relative line (or the LIVE pill and its word),
     * the big numeral (a time, LAP 34/50, R2, the method, the winner's code with the next two, a dash or the hidden
     * slot), the line under it and where to watch.
     */
    static final class When {
        String rel = "";
        boolean live;
        /** TIME, LAP, ROUND, METHOD, PODIUM, DASH, TBA, SLOT, NONE. */
        String kind = "NONE";
        String numeral = "", label = "", sub = "";
        boolean dim;
        final List<SportEvent.Entrant> podium = new ArrayList<>();
        AppKit.Watch watch;
        String watchLead = "";
        String okHint = "";
    }

    /** The column for an event's session (s: the session or segment the hero is about). */
    static When when(SportEvent e, SportEvent.Session s, HomeModel.Kind kind, long now, boolean hidden, SportPrefs p, boolean clubHero) {
        When w = new When();
        if (e == null || s == null) return w;
        boolean f1 = SportEvent.F1.equals(e.sport);
        String lead = SportText.part(e, s);
        if (kind == HomeModel.Kind.LIVE) {
            w.live = true;
            w.rel = f1 ? lead : SportText.part(e, liveSegment(e, s));
            if (f1) {
                int lap = F1Feed.shownLap(s);  // 0 while F1Feed.LIVE_LAP_TRUSTED is false: the time it started, dimmed
                if (SportModel.MAIN_RESULTS.contains(s.kind) && lap > 0) {
                    w.kind = "LAP";
                    w.numeral = s.laps > 0 ? lap + "/" + s.laps : String.valueOf(lap);
                    w.label = Cards.caps(SportText.t("lap"));
                } else { w.kind = "TIME"; w.numeral = I18n.time(s.start); w.dim = true; }
                if (F1Feed.LIVE_ORDER_TRUSTED && !hidden && !s.order.isEmpty()) {
                    SportEvent.Entrant focus = null;
                    if (p != null) for (SportEvent.Entrant x : s.order) if (p.focusDriver(x.id) || p.focusTeam(x.teamId)) { focus = x; break; }
                    SportEvent.Entrant lead1 = SportText.winner(s);
                    w.sub = focus != null ? SportText.f("your_driver", code(focus) + " P" + focus.pos + (focus.gap.isEmpty() ? "" : " · " + focus.gap))
                        : lead1 != null ? SportText.f("leader_line", code(lead1)) : "";
                }
                if ("RED_FLAG".equals(s.detail) || "SUSPENDED".equals(s.detail)) w.sub = SportText.t("red_flag");
            } else {
                SportEvent.Bout b = SportModel.inCage(e);
                int round = UfcFeed.shownRound(b);  // 0 while UfcFeed.LIVE_ROUND_TRUSTED is false
                if (b != null && round > 0) {
                    w.kind = "ROUND";
                    w.numeral = "R" + round;
                    w.label = b.rounds > 0 ? SportText.f("of", b.rounds) : "";
                    w.sub = SportText.bout(b);
                } else {
                    // no round to show: the segment's start, dimmed, and the bout in the cage (else the next to fight)
                    SportEvent.Bout nb = b != null ? b : SportModel.nextBout(e);
                    w.kind = "TIME";
                    w.numeral = I18n.time(liveSegment(e, s).start);
                    w.dim = true;
                    w.sub = nb == null ? "" : SportText.bout(nb);
                }
            }
            return w;
        }
        if (kind == HomeModel.Kind.RESULT) {
            if (f1) {
                SportEvent.Entrant win = SportText.winner(s);
                if (hidden || win == null) {
                    w.rel = SportText.t("final");
                    w.kind = hidden ? "SLOT" : "DASH";
                } else {
                    w.rel = SportText.wins(s);
                    w.kind = "PODIUM";
                    for (SportEvent.Entrant x : s.order) if (w.podium.size() < 3) w.podium.add(x);
                }
                w.sub = SportText.whenLine(lead, s.start);
            } else {
                SportEvent.Bout b = SportText.headline(e, s, p);
                if (hidden || b == null || SportText.method(b).isEmpty() && b.outcome == 0) {
                    w.rel = SportText.t("final");
                    w.kind = hidden ? "SLOT" : "DASH";
                    w.sub = SportText.whenLine(hidden ? SportText.t("scores_hidden") : lead, s.start);
                } else {
                    w.rel = SportText.wins(b);
                    w.kind = "METHOD";
                    w.numeral = SportText.methodShort(b);
                    String rl = SportText.roundLine(b);
                    w.sub = rl.isEmpty() ? AppText.longDate(s.start) : rl + " · " + AppText.longDate(s.start);
                }
            }
            return w;
        }
        if (kind == HomeModel.Kind.OFF) {
            w.rel = SportText.detail(s);
            w.kind = "DASH";
            w.sub = SportText.whenLine(lead, s.start);
            return w;
        }
        boolean sessionLeads = clubHero && f1;  // §9.1: F1's Club hero leads with the session, UFC's with the day
        w.rel = sessionLeads ? SportText.sessionRelative(lead, now, s.start) : SportText.relative(now, s.start);
        if (s.tbd) { w.kind = "TBA"; w.numeral = AppText.t("time_tba"); }
        else { w.kind = "TIME"; w.numeral = I18n.time(s.start); }
        w.sub = sessionLeads ? AppText.longDate(s.start) : SportText.whenLine(lead, s.start);
        return w;
    }

    /** UFC: the segment being fought (state in), else the given one. */
    static SportEvent.Session liveSegment(SportEvent e, SportEvent.Session s) {
        for (SportEvent.Session x : e.sessions) if ("in".equals(x.state)) return x;
        return s;
    }

    /**
     * Draws the column right-aligned at `right`: the relative line (RC Bold 58; live: the word and the LIVE pill), the
     * numeral (big), the line under it (32 at 72 %), and the watch line centred on watchCy (a cell, its app before it).
     */
    static void drawWhen(Canvas cv, When w, float right, float relBl, float bigBl, float big, float subBl, float watchCy, int ink) {
        Paint rel = AppKit.text(Ui.COND_BOLD, Ui.T_REL, ink);
        if (w.live) {
            Paint t = AppKit.caps(Ui.BOLD, 26, 0, 0.1f);
            float pw = 16 + 14 + 12 + t.measureText(I18n.t("live")) + 18 - 2.6f;
            float cy = relBl - 0.715f * Ui.T_REL / 2;
            MatchdayViews.StageInfo.livePill(cv, right - pw, cy, 1, 1);
            if (!w.rel.isEmpty()) AppKit.right(cv, rel, w.rel, right - pw - 22, relBl);
        } else if (!w.rel.isEmpty()) AppKit.right(cv, rel, w.rel, right, relBl);
        Paint num = AppKit.text(Ui.COND_BOLD, big, Ui.alpha(ink, w.dim ? 0.4f : 1f));
        num.setLetterSpacing(-0.015f);
        switch (w.kind) {
            case "TIME": time(cv, num, w.numeral, right, bigBl, ink, big, w.dim); break;
            case "TBA": AppKit.right(cv, rel, w.numeral, right, bigBl - big * 0.25f); break;
            case "DASH": AppKit.right(cv, num, "—", right, bigBl); break;
            case "SLOT": slot(cv, right - 1.55f * big * 0.715f * 2.6f, bigBl - 0.715f * big, right, bigBl, 1); break;
            case "LAP": case "ROUND": {
                float nw = AppKit.right(cv, num, w.numeral, right, bigBl);
                if (!w.label.isEmpty()) {
                    Paint lp = AppKit.text(Ui.COND_BOLD, Ui.T_REL, Ui.alpha(ink, 0.6f));
                    if ("LAP".equals(w.kind)) AppKit.right(cv, lp, w.label, right - nw - 24, bigBl);
                    else { AppKit.right(cv, lp, w.label, right - nw - 24, bigBl); }
                }
                break;
            }
            case "METHOD": {
                Paint mp = AppKit.text(Ui.COND_BOLD, big * 0.95f, ink);
                AppKit.fit(mp, w.numeral, 760, big * 0.95f, 100);
                AppKit.right(cv, mp, w.numeral, right, bigBl);
                break;
            }
            case "PODIUM": podium(cv, w.podium, right, bigBl, big * 0.85f, ink); break;
            default: break;
        }
        Paint sub = AppKit.text(Ui.REGULAR, 32, Ui.alpha(ink, Ui.A2));
        if (!w.sub.isEmpty()) AppKit.right(cv, sub, AppKit.cut(sub, w.sub, 900), right, subBl);
        float x = right;
        if (w.watch != null) {
            float cw = AppKit.cell(cv, w.watch, AppKit.Size.MD, right, watchCy, true, 560, false, 1);
            x = right - cw - 16;
            String lead = w.watch.app != null && w.watch.kind != AppKit.Watch.GUIDE ? w.watch.app : w.watchLead;
            if (lead != null && !lead.isEmpty()) {
                Paint t = AppKit.text(Ui.REGULAR, 30, Ui.alpha(ink, Ui.A3));
                float bl = Ui.baseline(Ui.REGULAR, 30, watchCy);
                float dw = AppKit.right(cv, t, "·", x, bl);
                t.setColor(Ui.alpha(ink, Ui.A2));
                x -= dw + 16;
                x -= AppKit.right(cv, t, lead, x, bl) + 24;
            }
        }
        if (!w.okHint.isEmpty()) AppKit.okHint(cv, w.okHint, x, watchCy, ink);
    }

    /**
     * A row's time as the fixture rows draw it (ClubViews.FixtureRow): RC Bold at size from x, its capitals centred on
     * cy; a 12-hour clock's "pm" at 24 (70 %) on the same baseline, 3 px after the hour, only while both fit the room,
     * otherwise the hour alone (never a shrunk "pm"). plain: a word or a dash (TBA, cancelled), fitted to the room.
     */
    static void rowTime(Canvas cv, String ts, boolean plain, float x, float cy, float size, float room, int ink) {
        Paint tm = Ui.paint(Ui.COND_BOLD, size, ink);
        int sp = plain ? -1 : ClubViews.FixtureRow.suffixAt(ts);
        if (sp > 0) {
            Paint suf = Ui.paint(Ui.COND_BOLD, 24, Ui.alpha(ink, 0.7f));
            String d = ts.substring(0, sp).trim(), a = ts.substring(sp + 1).trim();
            boolean keep = ClubViews.FixtureRow.keepSuffix(tm.measureText(d), suf.measureText(a), room);
            if (!keep) AppKit.fit(tm, d, room, size, 24);
            float bl = Ui.baseline(Ui.COND_BOLD, tm.getTextSize(), cy);
            cv.drawText(d, x, bl, tm);
            if (keep) cv.drawText(a, x + tm.measureText(d) + ClubViews.FixtureRow.SUFFIX_GAP, bl, suf);
        } else {
            AppKit.fit(tm, ts, room, size, 24);
            cv.drawText(ts, x, Ui.baseline(Ui.COND_BOLD, tm.getTextSize(), cy), tm);
        }
    }

    /** A time at a display size; 12-hour clocks put "PM" beside it. */
    static void time(Canvas cv, Paint big, String t, float right, float bl, int ink, float size, boolean dim) {
        int sp = ClubViews.FixtureRow.suffixAt(t);
        if (sp > 0) {
            Paint suf = AppKit.text(Ui.COND_BOLD, size * 0.36f, Ui.alpha(ink, dim ? 0.4f : 0.75f));
            float sw = AppKit.right(cv, suf, t.substring(sp + 1).trim(), right, bl);
            AppKit.right(cv, big, t.substring(0, sp), right - sw - 14, bl);
        } else AppKit.right(cv, big, t, right, bl);
    }

    /**
     * The podium (§2.3): P1's bar and code at size, then 26 px, then two small rows (RC 0.365 em: place at 60 %, bar 8,
     * code at 88 %) spanning the hero's capital height; right-aligned at right. Returns its left edge.
     */
    static float podium(Canvas cv, List<SportEvent.Entrant> pod, float right, float bl, float size, int ink) {
        if (pod.isEmpty()) return right;
        float rs = 0.365f * size, cap = 0.715f * size;
        float rowsW = 0;
        Paint num = Ui.paint(Ui.COND, rs, Ui.alpha(ink, 0.6f));
        for (int i = 1; i < pod.size(); i++)
            rowsW = Math.max(rowsW, num.measureText(String.valueOf(i + 1)) + 14 + codeWidth(pod.get(i), rs, 8, 12));
        float rowsX = right - rowsW;
        for (int i = 1; i < pod.size(); i++) {
            float rbl = i == 1 ? bl - cap + 0.715f * rs : bl;
            cv.drawText(String.valueOf(i + 1), rowsX, rbl, num);
            code(cv, pod.get(i), rowsX + num.measureText(String.valueOf(i + 1)) + 14, rbl, rs, 8, 12, Ui.alpha(ink, 0.88f));
        }
        float heroW = codeWidth(pod.get(0), size, 14, 18);
        float x = (pod.size() > 1 ? rowsX - 26 : right) - heroW;
        code(cv, pod.get(0), x, bl, size, 14, 18, ink);
        return x;
    }

    // ---------- the Club layout's small views ----------
    /** A followed sport in the rail (as a club's, ClubViews.RailItem): the monogram disc instead of a crest. */
    static final class RailItem extends AppViews.Cell {
        SportModel.SportData data;
        boolean selected;
        int ink = Ui.TXT;

        RailItem(Context c) { super(c); }

        void bind(SportModel.SportData d, boolean selected, int ink) {
            this.data = d; this.selected = selected; this.ink = ink;
            setContentDescription(d.label + (selected ? ", " + AppText.t("selected") : "") + (d.railLine.isEmpty() ? "" : ". " + d.railLine));
            invalidate();
        }

        @Override RectF shape() { return new RectF(8, 2, 176, h() - 2); }
        @Override float radius() { return 18; }

        @Override void paint(Canvas cv) {
            if (data == null) return;
            fill(cv);
            float cx = 95;
            String name = SportText.sportName(data.sport);
            if (selected) {
                Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
                m.setColor(ink);
                cv.drawRoundRect(new RectF(0, 22, 5, 134), 3, 3, m);
                monogram(cv, data.sport, cx, 74, 120, 1);
                Paint n = Ui.paint(Ui.BOLD, 24, ink);
                AppKit.centre(cv, n, AppKit.cut(n, name, 170), cx, 166.6f);
                return;
            }
            monogram(cv, data.sport, cx, 52, 76, 1);
            Paint n = Ui.paint(Ui.MEDIUM, 24, ink);
            AppKit.centre(cv, n, AppKit.cut(n, name, 170), cx, 122.56f);
            if (!data.railLine.isEmpty()) {
                Paint s = Ui.paint(data.railLive ? Ui.BOLD : Ui.REGULAR, 24, data.railLive ? Ui.LIVE_TEXT : Ui.alpha(ink, Ui.A2));
                AppKit.centre(cv, s, AppKit.cut(s, data.railLine, 172), cx, 157.4f);
            }
        }
    }

    /**
     * A latest tile (84 tall, radius 16, white 7 %; §9.1): F1 a scoring session's podium ("GP São Paulo" over "Sun 8
     * Nov · Race", then 1 ▌NOR 2 ▌VER 3 ▌ANT) or the drivers' table's top two; UFC a card's headline bout (loser at
     * 50 %) and its method. Hidden: Final and nothing else.
     */
    static final class Tile extends AppViews.Cell {
        EventCards.Pick pick;
        F1Feed.Standings standings;
        boolean hidden, skeleton;
        float pulse = 1;
        String key = "";

        Tile(Context c) { super(c); }

        @Override float radius() { return Ui.R_TILE; }

        void bind(EventCards.Pick p, boolean hidden) {
            this.pick = p; this.standings = null; this.hidden = hidden; this.skeleton = false;
            key = "pick:" + SportModel.key(p);
            setFocusable(true);
            SportEvent.Session s = p.e.session(p.sessionId);
            setContentDescription(SportText.speak(p.e, s, 0, hidden));
            invalidate();
        }

        void bind(F1Feed.Standings st) {
            this.pick = null; this.standings = st; this.hidden = false; this.skeleton = false;
            key = "standings";
            setFocusable(false);  // nothing to open: the weekend screen has the table
            StringBuilder b = new StringBuilder(SportText.t("drivers"));  // spoken in full; drawn as "Standings" (title())
            for (int i = 0; i < Math.min(2, st.drivers.size()); i++) b.append(". ").append(st.drivers.get(i).name).append(' ').append(points(st.drivers.get(i).points));
            setContentDescription(b.toString());
            invalidate();
        }

        void skeleton() { pick = null; standings = null; skeleton = true; key = ""; setFocusable(false); setContentDescription(null); invalidate(); }

        static String points(float p) { return p == Math.round(p) ? String.valueOf(Math.round(p)) : String.valueOf(p); }

        String title() {
            if (standings != null) return SportText.t("standings");  // QAS-6: "Drivers' championship" didn't fit
            if (pick == null) return "";
            if (SportEvent.F1.equals(pick.e.sport)) return SportText.shortTitle(pick.e);
            SportEvent.Bout b = SportText.headline(pick.e, pick.e.session(pick.sessionId), null);
            return b == null ? SportText.ufcEvent(pick.e) : SportText.bout(b);
        }

        String meta() {
            if (standings != null) return SportText.f("season", SportText.year(standings.at > 0 ? standings.at : System.currentTimeMillis()));
            if (pick == null) return "";
            SportEvent.Session s = pick.e.session(pick.sessionId);
            String d = AppText.shortDate(s == null ? pick.e.start : s.start);
            return SportEvent.F1.equals(pick.e.sport) ? d + " · " + SportText.session(s) : d + " · " + SportText.ufcEvent(pick.e);
        }

        /**
         * The tile's width for its content (maxText: at most this for the title and meta; a bout's names up to 460, past
         * that to their tight forms). Rounded up, so a title as wide as the text column is never cut by a fraction of a pixel.
         */
        float width(float maxText) {
            Paint n = Ui.paint(Ui.MEDIUM, 30, 0), m = Ui.paint(Ui.REGULAR, 24, 0);
            float t = n.measureText(title());
            SportEvent.Bout b = boutShown();
            if (b != null) {  // a bout's names take what they need, up to 460 ("Cortes Acosta VS Walker"), then go tight
                t = boutWidth(n, SportText.surname(b.a), SportText.surname(b.b));
                maxText = Math.max(maxText, 460);
            }
            float text = Math.min(maxText, Math.max(t, m.measureText(meta())));
            return (float) Math.ceil(24 + text + 32 + rightWidth() + 24) + 2;
        }

        /** UFC with scores shown: the headline bout the tile draws name by name; null otherwise. */
        private SportEvent.Bout boutShown() {
            if (pick == null || standings != null || hidden || !SportEvent.UFC.equals(pick.e.sport)) return null;
            return SportText.headline(pick.e, pick.e.session(pick.sessionId), null);
        }

        private static float boutWidth(Paint n, String a, String b) {
            return n.measureText(a) + 10 + Ui.paint(Ui.COND, 24, 0).measureText(CardKit.VS) + 10 + n.measureText(b);
        }

        private float rightWidth() {
            if (hidden) return Ui.paint(Ui.MEDIUM, 30, 0).measureText(SportText.t("final"));
            if (standings != null) {
                float w = 0;
                for (int i = 0; i < Math.min(2, standings.drivers.size()); i++) {
                    SportEvent.Entrant x = standings.drivers.get(i);
                    w += (i > 0 ? 36 : 0) + Ui.paint(Ui.COND, 36, 0).measureText(String.valueOf(i + 1)) + 6
                        + codeWidth(x, 42, 8, 8) + 12 + Ui.paint(Ui.COND, 36, 0).measureText(points(x.points));
                }
                return w;
            }
            if (pick == null) return 0;
            SportEvent.Session s = pick.e.session(pick.sessionId);
            if (SportEvent.F1.equals(pick.e.sport)) {
                float w = 0;
                for (int i = 0; i < Math.min(3, s == null ? 0 : s.order.size()); i++)
                    w += (i > 0 ? 36 : 0) + Ui.paint(Ui.COND, 36, 0).measureText(String.valueOf(i + 1)) + 6 + codeWidth(s.order.get(i), 42, 8, 8);
                return w;
            }
            SportEvent.Bout b = SportText.headline(pick.e, s, null);
            String m = SportText.methodShort(b), rc = SportText.roundClock(b);
            return Ui.paint(Ui.COND_BOLD, 46, 0).measureText(m) + (rc.isEmpty() ? 0 : 16 + Ui.paint(Ui.COND, 40, 0).measureText(rc));
        }

        @Override void paint(Canvas cv) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (skeleton) {
                bg.setColor(Ui.alpha(0xFFFFFFFF, 0.06f * pulse));
                cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_TILE, Ui.R_TILE, bg);
                return;
            }
            if (pick == null && standings == null) return;
            bg.setColor(Ui.TONAL);
            cv.drawRoundRect(new RectF(0, 0, w(), h()), Ui.R_TILE, Ui.R_TILE, bg);
            fill(cv);
            float cy = 42, x = 24, rw = rightWidth(), textW = w() - 24 - 32 - rw - 24;
            Paint n = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            boolean ufc = pick != null && SportEvent.UFC.equals(pick.e.sport);
            SportEvent.Session s = pick == null ? null : pick.e.session(pick.sessionId);
            if (ufc && !hidden) {  // the headline bout: the loser at 50 %, VS at 60 %
                SportEvent.Bout b = SportText.headline(pick.e, s, null);
                if (b != null) {
                    String a = SportText.surname(b.a), bb = SportText.surname(b.b);
                    final Paint nn = n;
                    // QAS-6: each name the width it needs, then the tight forms; never "Cortes A…"
                    String[] fit = SportNames.boutFit(a, bb, textW - boutWidth(n, "", ""),
                        new SettingsLogic.Measure() { public float width(String s) { return nn.measureText(s); } });
                    if (fit != null) { a = fit[0]; bb = fit[1]; }
                    float xx = x;
                    n.setColor(Ui.alpha(Ui.TXT, b.outcome == 2 ? 0.5f : 1f));
                    String ac = fit != null ? a : AppKit.cut(n, a, textW / 2);
                    cv.drawText(ac, xx, 36.15f, n);
                    xx += n.measureText(ac) + 10;
                    Paint vs = Ui.paint(Ui.COND, 24, Ui.alpha(Ui.TXT, 0.6f));
                    cv.drawText(CardKit.VS, xx, 36.15f, vs);
                    xx += vs.measureText(CardKit.VS) + 10;
                    n.setColor(Ui.alpha(Ui.TXT, b.outcome == 1 ? 0.5f : 1f));
                    cv.drawText(AppKit.cut(n, bb, x + textW - xx), xx, 36.15f, n);
                } else cv.drawText(AppKit.cut(n, title(), textW), x, 36.15f, n);
            } else cv.drawText(AppKit.cut(n, title(), textW), x, 36.15f, n);
            Paint mt = Ui.paint(Ui.REGULAR, 24, Ui.alpha(Ui.TXT, 0.6f));
            cv.drawText(AppKit.cut(mt, meta(), textW), x, 69.95f, mt);
            float rx = w() - 24 - rw;
            if (hidden) {
                Paint f = Ui.paint(Ui.MEDIUM, 30, Ui.alpha(Ui.TXT, Ui.A2));
                cv.drawText(SportText.t("final"), rx, Ui.baseline(Ui.MEDIUM, 30, cy), f);
                return;
            }
            float bl = Ui.baseline(Ui.COND_BOLD, 42, cy);
            if (standings != null) {
                for (int i = 0; i < Math.min(2, standings.drivers.size()); i++) {
                    SportEvent.Entrant e = standings.drivers.get(i);
                    if (i > 0) rx += 36;
                    Paint num = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.6f));
                    cv.drawText(String.valueOf(i + 1), rx, bl, num);
                    rx += num.measureText(String.valueOf(i + 1)) + 6;
                    rx += code(cv, e, rx, bl, 42, 8, 8, Ui.TXT) + 12;
                    Paint pts = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.8f));
                    cv.drawText(points(e.points), rx, bl, pts);
                    rx += pts.measureText(points(e.points));
                }
                return;
            }
            if (!ufc) {
                for (int i = 0; i < Math.min(3, s == null ? 0 : s.order.size()); i++) {
                    if (i > 0) rx += 36;
                    int ink = Ui.alpha(Ui.TXT, i == 0 ? 1f : 0.8f);
                    Paint num = Ui.paint(Ui.COND, 36, Ui.alpha(Ui.TXT, 0.6f));
                    cv.drawText(String.valueOf(i + 1), rx, bl, num);
                    rx += num.measureText(String.valueOf(i + 1)) + 6;
                    rx += code(cv, s.order.get(i), rx, bl, 42, 8, 8, ink);
                }
                return;
            }
            SportEvent.Bout b = SportText.headline(pick.e, s, null);
            Paint mp = Ui.paint(Ui.COND_BOLD, 46, Ui.TXT);
            String m = SportText.methodShort(b);
            cv.drawText(m, rx, Ui.baseline(Ui.COND_BOLD, 46, cy), mp);
            String rc = SportText.roundClock(b);
            if (!rc.isEmpty()) cv.drawText(rc, rx + mp.measureText(m) + 16, Ui.baseline(Ui.COND_BOLD, 46, cy), Ui.paint(Ui.COND, 40, Ui.alpha(Ui.TXT, Ui.A2)));
        }
    }

    /**
     * One row of the sport's timetable (60 tall, as a fixture row): day · time · session (or segment, or event) · the
     * grand prix / the headline bout at 72 % · where to watch (sm) or the LIVE pill. OK opens the event screen.
     */
    static final class Row extends AppViews.Cell {
        EventCards.Pick pick;
        AppKit.Watch watch;
        boolean first, skeleton, nextEvent;
        float pulse = 1;

        Row(Context c) { super(c); }

        @Override float radius() { return Ui.R_ROW; }

        /** hidden: the app hides results (SportText.hidden): a finished segment's row never says who won (QAS-3). */
        void bind(EventCards.Pick p, AppKit.Watch w, boolean first, boolean nextEvent, long now, boolean hidden) {
            this.pick = p; this.watch = w; this.first = first; this.nextEvent = nextEvent; this.skeleton = false;
            setFocusable(true);
            setContentDescription(SportText.speak(p.e, p.e.session(p.sessionId), now, hidden));
            invalidate();
        }

        void skeleton(boolean first) { pick = null; skeleton = true; this.first = first; setFocusable(false); setContentDescription(null); invalidate(); }

        String key() { return pick == null ? "" : SportModel.key(pick); }

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
                AppKit.skeleton(cv, 360, cy - 13, 620, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 876, cy - 13, 1076, cy + 13, 13, pulse);
                AppKit.skeleton(cv, 1356, cy - 21, 1506, cy + 21, 12, pulse);
                return;
            }
            if (pick == null) return;
            fill(cv);
            SportEvent e = pick.e;
            SportEvent.Session s = e.session(pick.sessionId);
            if (s == null) return;
            boolean off = "off".equals(s.state), live = "in".equals(s.state);
            Paint day = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            String ds = AppText.shortDate(s.start);
            AppKit.fit(day, ds, 180, 30, 24);
            cv.drawText(ds, 18, Ui.baseline(Ui.MEDIUM, day.getTextSize(), cy), day);
            String ts = off ? "—" : s.tbd ? AppText.t("tba") : I18n.time(s.start);
            rowTime(cv, ts, off || s.tbd, 204, cy, 34, ClubViews.FixtureRow.timeRoom(false, !I18n.clock24()),
                Ui.alpha(Ui.TXT, off || s.tbd ? 0.6f : 1f));
            boolean f1 = SportEvent.F1.equals(e.sport);
            String what = nextEvent ? SportText.ufcEvent(e) : SportText.part(e, s);
            String meta;
            if (f1) meta = SportText.shortTitle(e);
            else if (nextEvent) { SportEvent.Bout b = e.mainEvent(); meta = b == null ? "" : SportText.bout(b); }
            else {
                SportEvent.Bout b = SportText.headline(e, s, null);
                int more = Math.max(0, s.bouts.size() - 1);
                meta = b == null ? "" : "MAIN".equals(s.kind) || "PRELIMS".equals(s.kind) ? SportText.bout(b) + (more > 0 ? " +" + more : "")
                    : SportText.f("bouts", s.bouts.size());
            }
            if (off) meta = SportText.detail(s);
            Paint nm = Ui.paint(Ui.MEDIUM, 30, Ui.TXT);
            // UFC: the segment's room for "Πρώτα προκριματικά" (310 px), the bout after it
            cv.drawText(AppKit.cut(nm, what, f1 ? 500 : 310), 360, Ui.baseline(Ui.MEDIUM, 30, cy), nm);
            Paint mp = Ui.paint(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, Ui.A2));
            float mx = f1 ? 876 : 690;
            cv.drawText(AppKit.cut(mp, meta, 1130 - mx), mx, Ui.baseline(Ui.REGULAR, 30, cy), mp);
            if (live) {
                Paint t = AppKit.caps(Ui.BOLD, 26, 0, 0.1f);
                float pw = 16 + 14 + 12 + t.measureText(I18n.t("live")) + 18 - 2.6f;
                MatchdayViews.StageInfo.livePill(cv, 1506 - pw, cy, 1, 1);
            } else if (watch != null && !off) AppKit.cell(cv, watch, AppKit.Size.SM, 1506, cy, true, 330, false, 1);
        }
    }

    /** A label inside the timetable ("NEXT EVENTS", "NEXT GRAND PRIX"): not focusable. */
    static final class Header extends AppViews.Spec {
        String text = "";
        Header(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(String t) { text = t; invalidate(); }
        @Override void paint(Canvas cv) {
            Paint p = AppKit.caps(Ui.MEDIUM, Ui.T_LABEL, Ui.alpha(Ui.TXT, 0.62f), Ui.TRACK_EYEBROW);
            cv.drawText(Cards.caps(text), 18 - AppKit.lead(p), h() - 14, p);
        }
    }
}
