package app.fanela.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The plain rules of the Settings screens (SPEC-APP 5), kept free of Android so they are unit-tested: where the D-pad
 * goes between lines of items, how far the page scrolls to show the focused item under its section heading, how the
 * text panel scrolls, the club tiles' names, the favourite's next value, and background work that coalesces.
 */
final class SettingsLogic {
    private SettingsLogic() { }

    // ---------- D-pad ----------
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    /**
     * The next item from item i of a line. centres[line][k]: each item's centre x (empty lines are skipped).
     * Left / Right stay in the line and stop at its ends. Up / Down go to the next line that has items: on the line's
     * preferred item when it has one (the shown league's chip), else, coming from a single wide item (an option row),
     * where the focus last left that line, else the item nearest in x. Returns {line, item}, or null at an edge.
     */
    static int[] move(float[][] centres, int line, int i, int dir, int[] preferred, int[] remembered) {
        return move(centres, line, i, dir, preferred, remembered, null);
    }

    /**
     * As above; a line marked kept (a row of choices that are not columns of the lines around it, as Appearance's
     * five previews over its two layouts) is always re-entered where the focus last left it, from any line.
     */
    static int[] move(float[][] centres, int line, int i, int dir, int[] preferred, int[] remembered, boolean[] kept) {
        if (line < 0 || line >= centres.length || i < 0 || i >= centres[line].length) return null;
        if (dir == LEFT) return i > 0 ? new int[]{line, i - 1} : null;
        if (dir == RIGHT) return i + 1 < centres[line].length ? new int[]{line, i + 1} : null;
        int step = dir == UP ? -1 : 1;
        for (int t = line + step; t >= 0 && t < centres.length; t += step) {
            int n = centres[t].length;
            if (n == 0) continue;
            int j;
            if (preferred != null && t < preferred.length && preferred[t] >= 0 && preferred[t] < n) j = preferred[t];
            else if ((centres[line].length == 1 || kept != null && t < kept.length && kept[t])
                && remembered != null && t < remembered.length && remembered[t] >= 0 && remembered[t] < n) j = remembered[t];
            else j = nearest(centres[t], centres[line][i]);
            return new int[]{t, j};
        }
        return null;
    }

    /** The index of the value nearest x (the first of equals). */
    static int nearest(float[] xs, float x) {
        int best = 0;
        for (int k = 1; k < xs.length; k++) if (Math.abs(xs[k] - x) < Math.abs(xs[best] - x)) best = k;
        return best;
    }

    // ---------- page scroll ----------
    /** The page's edges (spec px): the title-safe top, and the lowest a focused item may end (ring and glow included). */
    static final float TOP = 54, BOTTOM_ROOM = 74;

    /**
     * The page scroll (spec px) that shows an item [top, bottom] of the content: its section heading (anchorTop) at
     * the title-safe top when the item then fits above the bottom edge, else the item's bottom on that edge; never
     * above the item's own top, never past the content (contentH includes the bottom padding).
     */
    static float scrollTarget(float anchorTop, float top, float bottom, float pageH, float contentH) {
        float t = anchorTop - TOP;
        float limit = pageH - BOTTOM_ROOM;
        if (bottom - t > limit) t = bottom - limit;
        if (top - t < TOP) t = top - TOP;
        return clamp(t, 0, Math.max(0, contentH - pageH));
    }

    /** The scroll of a horizontal strip (the league chips) that shows [left, right] whole, pad clear of each edge. */
    static float revealX(float scroll, float left, float right, float viewW, float contentW, float pad) {
        float t = scroll;
        if (right - t > viewW - pad) t = right - viewW + pad;
        if (left - t < pad) t = left - pad;
        return clamp(t, 0, Math.max(0, contentW - viewW));
    }

    // ---------- the text panel ----------
    /**
     * How far the panel's text scrolls: until its last line sits above the bottom fade (so the last lines are never
     * faded or under the hint below); 0 when it all fits.
     */
    static float panelMax(float contentH, float bodyH, float fadeH) {
        return Math.max(0, contentH - (bodyH - fadeH));
    }

    /** The scroll thumb {top, height} in a track: its share of the track is the share of the text in view. */
    static float[] thumb(float scroll, float max, float trackTop, float trackH, float bodyH) {
        if (max <= 0) return new float[]{trackTop, trackH};
        float h = Math.max(48, Math.min(trackH, trackH * bodyH / (max + bodyH)));
        float y = trackTop + (trackH - h) * clamp(scroll / max, 0, 1);
        return new float[]{y, h};
    }

    /**
     * A plain-text licence as the panel's paragraphs: the lines of each paragraph joined into one (the panel wraps it
     * to its width), runs of spaces made one, rule lines (----, ====) left out, and a short heading in capitals on the
     * first line ("PREAMBLE", "DISCLAIMER") kept as a paragraph of its own. Only the spacing changes, never a word.
     */
    static String reflow(String text) {
        List<String> paras = new ArrayList<>();
        for (String para : text.replace("\r\n", "\n").split("\n[ \t]*\n")) {
            StringBuilder p = new StringBuilder();
            for (String line : para.split("\n")) {
                String l = line.trim().replaceAll("[ \t]+", " ");
                if (l.isEmpty() || l.matches("[-=_*]{3,}")) continue;
                if (p.length() > 0) p.append(' ');
                p.append(l);
                if (p.length() == l.length() && l.length() <= 40 && l.matches("[^a-z]*[A-Z][^a-z]*")) {
                    paras.add(l);  // a heading over the text under it
                    p.setLength(0);
                }
            }
            if (p.length() > 0) paras.add(p.toString());
        }
        return String.join("\n\n", paras);
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- values ----------
    /** The value after cur in a cycle (OK on Language, Favourite club); the first when cur is not in it. */
    static String next(List<String> values, String cur) {
        if (values.isEmpty()) return cur;
        int i = values.indexOf(cur);
        return values.get(i < 0 ? 0 : (i + 1) % values.size());
    }

    /** A followed club's tile: the neutral tile with a little of the club's colour in it. */
    static int followedFill(int surface, int accent) { return ClubTokens.mix(surface, accent, 0.17f); }

    // ---------- tile names ----------
    /** Text widths (a Paint in the app, a stand-in in tests). */
    interface Measure { float width(String s); }

    /**
     * The words on lines no wider than width (a word wider than the line keeps a line of its own); null when it takes
     * more than maxLines.
     */
    static List<String> wrap(Measure m, String s, float width, int maxLines) {
        List<String> out = new ArrayList<>();
        String line = "";
        for (String w : s.trim().split("\\s+")) {
            String cand = line.isEmpty() ? w : line + " " + w;
            if (line.isEmpty() || m.width(cand) <= width) line = cand;
            else { out.add(line); line = w; }
        }
        if (!line.isEmpty()) out.add(line);
        return out.size() <= maxLines ? out : null;
    }

    /** The widest single word. */
    static float widestWord(Measure m, String s) {
        float max = 0;
        for (String w : s.trim().split("\\s+")) max = Math.max(max, m.width(w));
        return max;
    }

    /**
     * The name a club tile shows: English tiles the short form ("Man City", as the cards say); Greek tiles the full
     * name when it fits on two lines with every word whole, else the short form.
     */
    static String tileName(String full, String shortName, boolean greek, Measure m, float width) {
        String s = greek ? full : shortName;
        if (widestWord(m, s) <= width && wrap(m, s, width, 2) != null) return s;
        return shortName;
    }

    // ---------- first start ----------
    /**
     * Whether Settings opens as the first start ("Welcome to Fanela", Skip / Done, SPEC-APP 5.4): when asked for and
     * the first start was never done or skipped. Asked for again later (the empty state's "Choose clubs" after every
     * club was unfollowed), Settings opens on Your clubs instead (SPEC-APP 3.4).
     */
    static boolean firstStart(boolean asked, boolean doneBefore) { return asked && !doneBefore; }

    // ---------- Back ----------
    static final int BACK_POP = 0, BACK_FINISH_SETUP = 1, BACK_LEAVE = 2, BACK_SYSTEM = 3;

    /**
     * What Back does in Settings, on every Android version: with screens over the main page (Appearance, Country, a
     * text panel) the top one comes off; on the first start's page it ends the first start (Back = Skip); on the
     * Settings page the page falls away and the activity closes. Once it is falling away (leaving), the system's own
     * Back closes it at once.
     */
    static int back(int screensOnTop, boolean setup, boolean leaving) {
        if (leaving) return BACK_SYSTEM;
        if (screensOnTop > 0) return BACK_POP;
        return setup ? BACK_FINISH_SETUP : BACK_LEAVE;
    }

    // ---------- background work ----------
    /**
     * Work that only needs to run once for any number of requests made before it starts (republishing the home-screen
     * rows after the card style changes three times in a second). A request made while it runs runs it once more.
     */
    static final class Coalescer {
        private final Executor ex;
        private final Runnable job;
        private final AtomicBoolean queued = new AtomicBoolean();

        Coalescer(Executor ex, Runnable job) { this.ex = ex; this.job = job; }

        void request() {
            if (!queued.compareAndSet(false, true)) return;
            ex.execute(new Runnable() { public void run() { queued.set(false); job.run(); } });
        }
    }
}
