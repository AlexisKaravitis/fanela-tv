package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The images an event card draws, loaded on demand on the drawing thread from the local cache (Net.image: drawing
 * never waits for the network) and owned here, like CardArt: styles never recycle what they are given, and release()
 * frees it all once the card is drawn. The channel logos go through a CardArt over the card's shim CardModel, so they
 * are found, trimmed and owned exactly as football's are. Stream E owns this file.
 */
final class EventArt {
    final Context c;
    final EventCardModel m;
    private final List<Bitmap> owned = new ArrayList<>();
    private final Map<String, Bitmap> images = new HashMap<>();
    private CardArt cardArt;
    private CardModel shim;

    EventArt(Context c, EventCardModel m) { this.c = c; this.m = m; }

    /** Under this a headshot is a smudge on a 148-px disc: the card shows initials instead (spec §3.6). */
    static final int FACE_MIN = 120;

    /** ESPN's square head-and-shoulders headshot; null when it isn't cached. */
    Bitmap headshot(SportEvent.Fighter f) { return f == null ? null : image(f.headshot); }

    /** A stance cut-out (A facing right: left = true); the app's event screen only, never a card. */
    Bitmap stance(SportEvent.Fighter f, boolean left) { return f == null ? null : image(left ? f.stanceLeft : f.stanceRight); }

    /** A country flag (ESPN's 500 px PNG), its transparent margin trimmed. */
    Bitmap flag(String url) {
        if (url == null || url.isEmpty()) return null;
        String key = "trim:" + url;
        if (images.containsKey(key)) return images.get(key);
        Bitmap src = image(url), t = src == null ? null : keep(Cards.trim(src));
        images.put(key, t);
        return t;
    }

    /**
     * Both fighters' faces or neither (spec §3.6, §12.6: symmetry beats one face): true when both headshots are cached
     * and at least FACE_MIN px.
     */
    boolean faces(SportEvent.Fighter a, SportEvent.Fighter b) {
        Bitmap x = headshot(a), y = headshot(b);
        return big(x) && big(y);
    }

    private static boolean big(Bitmap b) { return b != null && Math.min(b.getWidth(), b.getHeight()) >= FACE_MIN; }

    /** The circuit outline (Circuits) fitted to the box and centred, as a closed path; null when there is none. */
    Path circuit(String espnCircuitId, RectF box) {
        float[] pts = Circuits.fit(Circuits.outline(c, espnCircuitId), box.left, box.top, box.right, box.bottom);
        if (pts == null) return null;
        Path p = new Path();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i + 1 < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
        return p;
    }

    /** The throwaway CardModel the shared football lines read (EventKit.shim). */
    CardModel shim() { if (shim == null) shim = EventKit.shim(m); return shim; }

    /** A CardArt over the shim: the channel logos, found and trimmed as football's (CardKit.channel reads it). */
    CardArt cardArt() { if (cardArt == null) cardArt = new CardArt(c, shim()); return cardArt; }

    /** The channel logos of the card's session (Channels.logos over SessionWatch.asMatch). */
    List<Channels.Logo> logos() {
        if (c == null || shim().match == null) return new ArrayList<>();
        return cardArt().logos();
    }

    /** A logo trimmed of transparent padding, once per card. */
    Bitmap trimmed(String url) { return c == null ? null : cardArt().trimmed(url); }

    private Bitmap image(String url) {
        if (c == null || url == null || url.isEmpty()) return null;
        if (images.containsKey(url)) return images.get(url);
        Bitmap b = keep(Net.image(c, url));
        images.put(url, b);
        return b;
    }

    private Bitmap keep(Bitmap b) { if (b != null && !owned.contains(b)) owned.add(b); return b; }

    /** Frees every bitmap this card loaded. */
    void release() {
        for (Bitmap b : owned) if (!b.isRecycled()) b.recycle();
        owned.clear();
        images.clear();
        if (cardArt != null) cardArt.release();
    }

    // ---------- the row's logo ----------
    /** The home-screen row's glyph disc (spec §9.1): 256 px. */
    static final int ROW_LOGO = 256;
    static final int DISC = 0xFF24262B, GLYPH = 0xFFF4F0EA;

    /**
     * The sport row's 256 px mark: a monogram disc, #24262B with a ring of white 22 % (3 px at 120, so 6.4 at 256), and
     * "F1" (RC Bold 0.46 x the disc) or "UFC" (0.34 x) in #F4F0EA, capitals centred. No F1 or UFC logo, no octagon:
     * Fanela's own type. null without a context.
     */
    static Bitmap rowLogo(Context c, String sport) {
        if (c == null) return null;
        Ui.fonts(c);
        Bitmap b = Bitmap.createBitmap(ROW_LOGO, ROW_LOGO, Bitmap.Config.ARGB_8888);
        drawMonogram(new Canvas(b), sport, ROW_LOGO / 2f, ROW_LOGO / 2f, ROW_LOGO);
        return b;
    }

    /** The monogram disc of diameter d centred at (cx, cy) (the row logo; the app's rail draws the same at 76 / 120). */
    static void drawMonogram(Canvas cv, String sport, float cx, float cy, float d) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(DISC);
        cv.drawCircle(cx, cy, d / 2, p);
        float ring = d * 3f / 120f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(ring);
        p.setColor(CardKit.alpha(CardKit.WHITE, 0.22f));
        cv.drawCircle(cx, cy, d / 2 - ring / 2, p);
        boolean ufc = SportEvent.UFC.equals(sport);
        Paint t = EventKit.numerals((ufc ? 0.34f : 0.46f) * d, GLYPH, 0);
        t.setFontFeatureSettings(null);
        String s = ufc ? "UFC" : "F1";
        EventKit.text(cv, t, s, cx, cy + EventKit.cap(t) / 2, 0);
    }
}
