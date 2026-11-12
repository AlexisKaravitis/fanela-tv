package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The card bitmaps of Matchday's rows: the launcher's own cards in the viewer's card style (CardStyles.draw), drawn
 * once per card state and kept at the size they are shown (408 x 229.5 spec px, made for the 1.05 focus scale, corners
 * rounded in), so a row scrolls by moving views, never by redrawing or rescaling a 960 x 540 card.
 *
 * The layout says which cards it wants (the rows and slots around the focus, nearest first); they are drawn on one
 * background thread in that order. Cards it no longer wants stay cached until the budget is full, then the least
 * recently used are recycled (a moment later, once no frame can still be drawing them). Thread-safe.
 */
final class MatchdayCards {
    /** The slot (spec px), the focus scale the bitmap is made for, and the slot's corner radius. */
    static final float W = 408, H = 229.5f, RADIUS = 14;
    /** About 30 cards (0.4 MB each at 1080p): the rows in view and their neighbours, both ways (the window is ~25). */
    private static final long BUDGET = 12L << 20;

    /** What to draw for a key: the club, its match, whether its data is old; or a sport's card (ev, club and m null). */
    static final class Job {
        final String key;
        final Teams.Club club;
        final Match m;
        final MatchdayModel.EventRef ev;
        final boolean stale;
        /** The clock the card's words are written for ("TOMORROW"): the screen's. */
        final long now;
        Job(String key, Teams.Club club, Match m, boolean stale, long now) { this.key = key; this.club = club; this.m = m; this.ev = null; this.stale = stale; this.now = now; }
        /** An F1 session's or a UFC event's card (EventStyles.draw, scores shown as on the club rows). */
        Job(String key, MatchdayModel.EventRef ev, boolean stale, long now) { this.key = key; this.club = null; this.m = null; this.ev = ev; this.stale = stale; this.now = now; }
    }

    /** A Matchday row's item as a job. */
    static Job job(String key, MatchdayModel.Item it, long now) {
        if (it.ev != null) return new Job(key, it.ev, it.ev.sport.stale, now);
        return new Job(key, it.club.club, it.m, it.club.stale, now);
    }

    interface Listener { void ready(String key); }

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "fanela-cards");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });
    private final LinkedHashMap<String, Bitmap> lru = new LinkedHashMap<>(32, 0.75f, true);
    private final Set<String> wanted = new HashSet<>();
    private final List<Job> queue = new ArrayList<>();
    private long bytes;
    private boolean running, released;
    private volatile String style;
    private Listener listener;
    /** Draw times (ms), for the debug log and the performance check. */
    private long drawn, drawMs, recycled, peak;

    MatchdayCards(Context c) { app = c.getApplicationContext(); }

    void listener(Listener l) { listener = l; }

    /**
     * The card's identity: its style, language, everything that changes it, its state words (they follow the clock) and
     * which of its images are on the TV, the club's own crest included (a card drawn before its crests or logo arrived
     * is drawn again once they have, as the launcher's are). Reads a few file stats: the display thread's.
     */
    static String key(Context c, String style, Teams.Club club, Match m, boolean stale, long now) {
        CardModel cm = CardModel.of(m, true, stale, now);
        String crest = club == null || club.crest == null ? "" : club.crest + Cards.have(c, club.crest);
        return style + "|" + I18n.lang() + "|" + Cards.CARD_VERSION + "|" + stale + "|" + m.signature() + "|" + cm.stateLine + "|" + cm.dateLine + "|" + cm.clock
            + "|" + Cards.images(c, m) + "|" + crest;
    }

    /** An event card's identity: as a match card's, from the event's signature and the card model's state words. */
    static String key(String style, MatchdayModel.EventRef ev, long now) {
        EventCardModel cm = model(ev, now);
        return style + "|" + I18n.lang() + "|ev" + EventCards.CARD_VERSION + "|" + ev.sport.stale + "|" + ev.sport.hidden + "|" + ev.e.signature() + "|" + ev.sessionId
            + "|" + ev.last + "|" + (cm == null ? "" : cm.stateLine + "|" + cm.dateLine + "|" + cm.clock + "|" + cm.time);
    }

    /**
     * The card model of a sport's card (stream D's EventCardModel): scores shown unless the app hides them for F1 / UFC
     * (SportText.HIDE_IN_APP: the sport's data says hidden), then drawn as the home-screen card with scores off.
     */
    static EventCardModel model(MatchdayModel.EventRef ev, long now) { return model(null, ev, now); }

    /** The same, its focus coloured by the roster on the TV as the home-screen rows colour it (c null: the events' own orders). */
    static EventCardModel model(Context c, MatchdayModel.EventRef ev, long now) {
        try { return EventCardModel.of(ev.e, ev.sessionId, ev.last, ev.sport.prefs, !ev.sport.hidden, ev.sport.stale, now, EventCards.rosterFrom(c, ev.sport.events)); }
        catch (RuntimeException t) { return null; }
    }

    void style(String s) { style = s; }
    String style() { return style; }

    synchronized Bitmap get(String key) { return key == null ? null : lru.get(key); }

    synchronized boolean has(String key) { return lru.containsKey(key); }

    /**
     * The cards the screen wants now, nearest first: missing ones are queued in that order (replacing the previous
     * queue), and the budget may now let go of the others.
     */
    synchronized void want(List<Job> jobs) {
        wanted.clear();
        queue.clear();
        for (Job j : jobs) {
            wanted.add(j.key);
            if (!lru.containsKey(j.key)) queue.add(j);
        }
        trim();
        pump();
    }

    /** Draws the given cards now on the calling (background) thread: the first screen shows them at once. */
    void drawNow(List<Job> jobs) {
        for (Job j : jobs) {
            synchronized (this) { if (released || lru.containsKey(j.key)) continue; }
            Bitmap b = render(j);
            if (b == null) continue;
            synchronized (this) {
                if (released || lru.containsKey(j.key)) { b.recycle(); continue; }
                put(j.key, b);
            }
        }
    }

    private void pump() {
        if (running || queue.isEmpty() || released) return;
        running = true;
        final Job j = queue.remove(0);
        try {
            worker.execute(new Runnable() { public void run() {
                Bitmap b = null;
                try { b = render(j); } catch (Throwable t) { android.util.Log.w("Fanela", "card " + j.key, t); }
                final Bitmap out = b;
                synchronized (MatchdayCards.this) {
                    running = false;
                    if (out != null) {
                        if (released || lru.containsKey(j.key)) out.recycle();
                        else put(j.key, out);
                    }
                    pump();
                }
                if (out != null) ui.post(new Runnable() { public void run() { if (listener != null && !released) listener.ready(j.key); } });
            }});
        } catch (Exception e) { running = false; }  // shutting down
    }

    private void put(String key, Bitmap b) {
        Bitmap old = lru.put(key, b);
        bytes += b.getAllocationByteCount();
        peak = Math.max(peak, bytes);
        if (old != null) { bytes -= old.getAllocationByteCount(); later(old); }
        trim();
    }

    /** Over budget: the least recently used cards nobody wants go (recycled a little later). */
    private void trim() {
        Iterator<Map.Entry<String, Bitmap>> it = lru.entrySet().iterator();
        while (bytes > BUDGET && it.hasNext()) {
            Map.Entry<String, Bitmap> e = it.next();
            if (wanted.contains(e.getKey())) continue;
            it.remove();
            bytes -= e.getValue().getAllocationByteCount();
            later(e.getValue());
        }
    }

    /** Recycles once no frame in flight can still draw it (views draw from the cache, so nothing will ask for it again). */
    private void later(final Bitmap b) {
        recycled++;
        ui.postDelayed(new Runnable() { public void run() { b.recycle(); } }, 500);
    }

    /** The full card in the viewer's style, scaled once to its slot with rounded corners; the 960 x 540 one is recycled. */
    private Bitmap render(Job j) {
        String st = style;
        if (st == null) st = Appearance.cardStyle(app);
        long t0 = System.nanoTime();
        Bitmap full;
        if (j.ev != null) {
            EventCardModel cm = model(app, j.ev, j.now);
            if (cm == null) return null;
            full = EventStyles.draw(app, cm, st);
        } else full = CardStyles.draw(app, j.club, j.m, j.stale, st, true, j.now);
        if (full == null) return null;
        float s = Ui.scale(app) * Ui.SCALE_CARD;
        int w = Math.round(W * s), h = Math.round(H * s);
        Bitmap half = Cards.smooth(full, w);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader sh = new BitmapShader(half, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        mx.setScale(w / (float) half.getWidth(), h / (float) half.getHeight());
        sh.setLocalMatrix(mx);
        p.setShader(sh);
        float r = RADIUS * s;
        cv.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        if (half != full) half.recycle();
        full.recycle();
        synchronized (this) { drawn++; drawMs += (System.nanoTime() - t0) / 1000000; }
        return out;
    }

    /** "24 cached, 9792 KB (peak 16 MB); 61 drawn, 48 ms each; 37 recycled" for the debug log. */
    synchronized String stats() {
        return lru.size() + " cached, " + (bytes >> 10) + " KB (peak " + (peak >> 10) + " KB); " + drawn + " drawn"
            + (drawn > 0 ? ", " + drawMs / drawn + " ms each" : "") + "; " + recycled + " recycled";
    }

    /** Drops every card (a new style or language), recycling them once no frame can use them. */
    synchronized void clear() {
        for (Bitmap b : lru.values()) later(b);
        lru.clear();
        bytes = 0;
    }

    synchronized void release() {
        released = true;
        queue.clear();
        worker.shutdownNow();
        clear();
    }
}
