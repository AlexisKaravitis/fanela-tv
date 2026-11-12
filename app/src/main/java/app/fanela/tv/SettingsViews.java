package app.fanela.tv;

import android.animation.ValueAnimator;
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
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/**
 * The views of Settings, Appearance, the first start, the country list and the text panel (SPEC-APP 5), built from
 * the 1.2 design system: every view draws in spec px (AppViews.Spec), every focusable item is an AppViews.Cell so the
 * one FocusLayer rings it, the tokens are Ui's. A Page lays its blocks out in a column, scrolls it, and walks the D-pad
 * through its lines of items (SettingsLogic).
 */
final class SettingsViews {
    private SettingsViews() { }

    static final int IVORY = 0xFFF3F1EC;

    /**
     * The favourite club's colour as Settings wears it (`clubPrimary`: the selected chip, the corner light, followed
     * tiles): the kit's main colour as-is, so Olympiacos is its own red; a black kit (PAOK) takes the accent (its
     * white), and no club the app's own blue. ON_CLUB: text on it.
     */
    static volatile int CLUB = Ui.ACCENT, ON_CLUB = 0xFFFFFFFF;

    static void theme(Context c) {
        int col = Ui.ACCENT;
        String fav = Teams.favourite(c);
        Teams.Club k = fav == null ? null : Teams.get(c, fav);
        if (k != null) {
            int kit = Badges.colors(k.name)[0];
            if (!Badges.known(k.name) && k.color != null && k.color.matches("[0-9a-fA-F]{6}")) kit = 0xFF000000 | Integer.parseInt(k.color, 16);
            kit |= 0xFF000000;
            if (android.graphics.Color.luminance(kit) >= 0.03f) col = kit;
        }
        CLUB = col;
        ON_CLUB = android.graphics.Color.luminance(col) > 0.45f ? Ui.INK : 0xFFFFFFFF;
    }

    // ---------- text ----------
    static TextPaint textPaint(Typeface face, float size, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        p.setFontFeatureSettings("tnum");
        return p;
    }

    static SettingsLogic.Measure measurer(final Paint p) {
        return new SettingsLogic.Measure() { public float width(String s) { return p.measureText(s); } };
    }

    /** The platform's line breaking (Greek included); each line is then drawn on its own CSS baseline. */
    static List<String> lines(String text, TextPaint p, float width) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.trim().isEmpty()) { out.add(""); continue; }
            StaticLayout l = StaticLayout.Builder.obtain(para, 0, para.length(), p, Math.max(1, (int) Math.floor(width))).build();
            for (int i = 0; i < l.getLineCount(); i++) out.add(para.substring(l.getLineStart(i), l.getLineEnd(i)).replaceAll("\\s+$", ""));
        }
        return out;
    }

    /**
     * Lines of text in a box: a face, a size, a colour, a line height (0: the face's normal one). Screen readers read
     * the whole text (never the cut one); a title or section heading is marked as a heading.
     */
    static final class Text extends AppViews.Spec {
        private List<String> lines = new ArrayList<>();
        private final TextPaint p;
        private final Typeface face;
        private final float size, lh, width;
        private final int maxLines;

        Text(Context c, String s, Typeface face, float size, int color, float width, float lineHeight, int maxLines) {
            super(c);
            this.face = face;
            this.size = size;
            this.width = width;
            this.maxLines = maxLines;
            p = textPaint(face, size, color);
            lh = lineHeight > 0 ? lineHeight : Ui.lineHeight(face, size);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            set(s);
        }

        /** A page title or section heading (screen readers can jump between headings; Android 9+). */
        Text heading() {
            if (android.os.Build.VERSION.SDK_INT >= 28) setAccessibilityHeading(true);
            return this;
        }

        void set(String s) {
            setContentDescription(s);
            List<String> l = lines(s, p, width);
            if (l.size() > maxLines) {
                l = new ArrayList<>(l.subList(0, maxLines));
                l.set(maxLines - 1, Cards.ellipsize(p, l.get(maxLines - 1) + "…", width));
            }
            lines = l;
            invalidate();
        }

        float height() { return Math.max(1, lines.size()) * lh; }

        @Override void paint(Canvas cv) {
            for (int i = 0; i < lines.size(); i++) cv.drawText(lines.get(i), 0, Ui.baselineTop(face, size, i * lh, lh), p);
        }
    }

    // ---------- environment ----------
    /** bg with the favourite club's faint light from the top-left corner (radial 1200x700, 16 % to 0 at 70 %). */
    static final class Backdrop extends AppViews.Spec {
        Backdrop(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        @Override void paint(Canvas cv) {
            cv.drawColor(Ui.APP_BG);
            int tint = CLUB;
            RadialGradient g = new RadialGradient(0, 0, 1200, new int[]{Ui.alpha(tint, 0.16f), Ui.alpha(tint, 0.08f), Ui.alpha(tint, 0f)},
                new float[]{0, 0.35f, 0.7f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(1, 700f / 1200f);
            g.setLocalMatrix(m);
            Paint p = new Paint(Paint.DITHER_FLAG);
            p.setShader(g);
            cv.drawRect(0, 0, w(), h(), p);
        }
    }

    /** The status slot's clock, right-aligned ("Wed 11 Nov · 18:30", 28 px at 68 %). */
    static final class Clock extends AppViews.Spec {
        private String text = "";
        Clock(Context c) { super(c); }
        void set(String s) { if (!s.equals(text)) { text = s; setContentDescription(s); invalidate(); } }
        @Override void paint(Canvas cv) {
            AppKit.right(cv, Ui.paint(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f)), text, w(), Ui.baselineTop(Ui.REGULAR, 28, 0, 0));
        }
    }

    /** The page's top edge once it scrolls: bg fading out over 48 px, so nothing is cut by the screen's edge. */
    static final class TopFade extends AppViews.Spec {
        private final float solid;
        TopFade(Context c, float solid) { super(c); this.solid = solid; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) {
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 0, h(), new int[]{Ui.APP_BG, Ui.APP_BG, Ui.alpha(Ui.APP_BG, 0)}, new float[]{0, solid, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w(), h(), p);
        }
    }

    // ---------- small drawings ----------
    /** A soft shadow under a rounded rect (stacked layers; no blur per frame): dy down, spread px, alpha at its core. */
    static void shadow(Canvas cv, RectF r, float radius, float dy, float spread, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n = 6;
        p.setColor(Ui.alpha(0xFF000000, alpha / n * 1.6f));
        for (int i = 0; i < n; i++) {
            float e = -spread / 2 + spread * i / (n - 1f);
            RectF l = new RectF(r.left - e, r.top - e + dy, r.right + e, r.bottom + e + dy);
            if (l.width() > 0 && l.height() > 0) cv.drawRoundRect(l, Math.max(0, radius + e), Math.max(0, radius + e), p);
        }
    }

    /** The check mark of the spec (a 24-unit path, stroke 3.2) in a box of size centred on (cx, cy). */
    static void check(Canvas cv, float cx, float cy, float size, int color) {
        cv.save();
        cv.translate(cx - size / 2, cy - size / 2);
        cv.scale(size / 24f, size / 24f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        Path a = new Path();
        a.moveTo(5, 12.5f); a.lineTo(9.5f, 17); a.lineTo(19, 7.5f);
        cv.drawPath(a, p);
        cv.restore();
    }

    /** The selected mark: a `live` disc with a white check (44 px tiles and rows, 36 px previews). */
    static void checkBadge(Canvas cv, float cx, float cy, float d) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.LIVE);
        cv.drawCircle(cx, cy, d / 2, p);
        check(cv, cx, cy, d * 26f / 44f, 0xFFFFFFFF);
    }

    /** The unselected mark: an empty ring (44 px, 3 px white 30 %). */
    static void emptyRing(Canvas cv, float cx, float cy, float d) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0x4DFFFFFF);
        cv.drawCircle(cx, cy, d / 2 - 1.5f, p);
    }

    /** A row's surface: surface1, surface2 as it takes the focus (the ring is the FocusLayer's). */
    static int rowFill(float focus) { return AppViews.blend(Ui.S1, Ui.S2, focus); }

    /** Grows a view to scale with the focus (150 ms), back to 1 without it. */
    static void grow(View v, boolean has, float scale) {
        v.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(Ui.FOCUS_MS).setInterpolator(Ui.FAST_OUT_SLOW_IN).start();
    }

    // ---------- option rows ----------
    /**
     * An option (SPEC-APP 5.2): 110 px, radius 20, surface1 (surface2 focused); title 34 Medium over a hint 26 at 62 %;
     * the value chip at the right (60 px, surface3, 28 Medium; "On" in `live`, "Off" at 70 %). A long hint wraps and
     * the row grows (Greek, the About texts): it never overlaps the value.
     */
    static final class OptionRow extends AppViews.Cell {
        static final float H = 110, PAD = 40, TITLE_LH = 34 * 1.15f, HINT_LH = 26 * 1.3f;
        String key, title, hint = "", value = "";
        boolean toggle, on;
        private List<String> hintLines = new ArrayList<>();
        private final float width, minH;
        private float height;
        /** The chip room kept whatever the value (reserve()): 0 for none, the chip's own width. */
        private float reserved;

        OptionRow(Context c, String key, String title, String hint, float width) { this(c, key, title, hint, width, H); }

        OptionRow(Context c, String key, String title, String hint, float width, float minH) {
            super(c);
            this.key = key;
            this.title = title;
            this.width = width;
            this.minH = minH;
            this.hint = hint == null ? "" : hint;
            relayout();
        }

        /** Sets the value; toggle: an On/Off switch (on: the `live` chip). Returns true when the height changed. */
        boolean value(String v, boolean toggle, boolean on) {
            this.value = v == null ? "" : v;
            this.toggle = toggle;
            this.on = on;
            return relayout();
        }

        boolean hint(String h) {
            hint = h == null ? "" : h;
            return relayout();
        }

        private float chipW() { return value.isEmpty() ? 0 : Ui.paint(Ui.MEDIUM, 28, 0).measureText(value) + 56; }

        /**
         * Keeps the room of the widest of these values for the chip (QAS-7: a value cycled from "All" to "Numbered
         * only" no longer narrows the hint, so the row never grows or shrinks). Returns true when the height changed.
         */
        boolean reserve(String... values) {
            float w = 0;
            for (String v : values) if (v != null && !v.isEmpty()) w = Math.max(w, Ui.paint(Ui.MEDIUM, 28, 0).measureText(v) + 56);
            reserved = w;
            return relayout();
        }

        /** The chip's room: its own width, or the reserved one when wider. */
        private float chipRoom() { return value.isEmpty() ? 0 : Math.max(chipW(), reserved); }

        private boolean relayout() {
            float room = width - 2 * PAD - (value.isEmpty() ? 0 : chipRoom() + 40);
            hintLines = hint.isEmpty() ? new ArrayList<String>() : lines(hint, textPaint(Ui.REGULAR, 26, 0), room);
            float was = height;
            height = Math.max(minH, hintLines.size() <= 1 ? minH : 2 * 26 + TITLE_LH + 4 + hintLines.size() * HINT_LH);
            setContentDescription(title + (value.isEmpty() ? "" : ", " + value) + (hint.isEmpty() ? "" : ". " + hint));
            invalidate();
            return was != height;
        }

        float height() { return height; }

        @Override float radius() { return 20; }

        @Override void paint(Canvas cv) {
            float w = w(), h = h();
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(rowFill(focus));
            cv.drawRoundRect(new RectF(0, 0, w, h), 20, 20, bg);
            float hl = hintLines.size() <= 1 ? Ui.lineHeight(Ui.REGULAR, 26) : HINT_LH;
            float block = TITLE_LH + (hintLines.isEmpty() ? 0 : 4 + hintLines.size() * hl);
            float top = (h - block) / 2;
            float room = w - 2 * PAD - (value.isEmpty() ? 0 : chipRoom() + 40);
            Paint t = Ui.paint(Ui.MEDIUM, 34, Ui.TXT);
            cv.drawText(Cards.ellipsize(t, title, room), PAD, Ui.baselineTop(Ui.MEDIUM, 34, top, TITLE_LH), t);
            Paint s = Ui.paint(Ui.REGULAR, 26, Ui.alpha(Ui.TXT, 0.62f));
            for (int i = 0; i < hintLines.size(); i++)
                cv.drawText(hintLines.get(i), PAD, Ui.baselineTop(Ui.REGULAR, 26, top + TITLE_LH + 4 + i * hl, hl), s);
            if (value.isEmpty()) return;
            float cw = chipW(), cy = h / 2;
            RectF chip = new RectF(w - PAD - cw, cy - 30, w - PAD, cy + 30);
            float a = toggle && !on ? 0.7f : 1f;
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(Ui.alpha(toggle && on ? Ui.LIVE : Ui.S3, a));
            cv.drawRoundRect(chip, 30, 30, cp);
            AppKit.centre(cv, Ui.paint(Ui.MEDIUM, 28, Ui.alpha(0xFFFFFFFF, a)), value, chip.centerX(), Ui.baseline(Ui.MEDIUM, 28, cy));
        }
    }

    // ---------- league chips ----------
    /** A league chip: 60 px, surface2, 30 Medium; the shown league in the club colour; the follow count in a badge. */
    static final class Chip extends AppViews.Cell {
        final String id, label;
        int count;
        boolean selected;

        Chip(Context c, String id, String label) { super(c); this.id = id; this.label = label; }

        static float badgeW(int n) { return Math.max(34, Ui.paint(Ui.BOLD, 22, 0).measureText(String.valueOf(n)) + 18); }

        float width() { return Ui.paint(Ui.MEDIUM, 30, 0).measureText(label) + 60 + (count > 0 ? 14 + badgeW(count) : 0); }

        @Override float radius() { return 30; }

        @Override void paint(Canvas cv) {
            float w = w();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(selected ? CLUB : Ui.S2);
            cv.drawRoundRect(new RectF(0, 0, w, 60), 30, 30, p);
            if (!selected) fill(cv);  // the shown league keeps its pure club colour under the ring
            int ink = selected ? ON_CLUB : Ui.TXT;
            Paint t = Ui.paint(Ui.MEDIUM, 30, ink);
            cv.drawText(label, 30, Ui.baseline(Ui.MEDIUM, 30, 30), t);
            if (count <= 0) return;
            float bw = badgeW(count), x = 30 + t.measureText(label) + 14;
            p.setColor(selected ? ON_CLUB : Ui.LIVE);
            cv.drawRoundRect(new RectF(x, 13, x + bw, 47), 17, 17, p);
            AppKit.centre(cv, Ui.paint(Ui.BOLD, 22, selected ? CLUB : 0xFFFFFFFF), String.valueOf(count), x + bw / 2, Ui.baseline(Ui.BOLD, 22, 30));
        }
    }

    /** "Football" / "Basketball" before their chips: 28 px at 68 %. */
    static final class GroupLabel extends AppViews.Spec {
        final String text;
        GroupLabel(Context c, String text) { super(c); this.text = text; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        float width() { return Ui.paint(Ui.REGULAR, 28, 0).measureText(text); }
        @Override void paint(Canvas cv) { cv.drawText(text, 0, Ui.baseline(Ui.REGULAR, 28, 30), Ui.paint(Ui.REGULAR, 28, Ui.alpha(Ui.TXT, 0.68f))); }
    }

    /**
     * The chips row (y 280, 60 px): group labels and chips with 20 px gaps, clipped at the page's right edge (the last
     * chip bleeds) and slid sideways (200 ms) to keep the focused chip whole.
     */
    static final class ChipBar extends FrameLayout {
        final FrameLayout row;
        final List<View> items = new ArrayList<>();
        private final float s;
        private float contentW, scroll;
        private ValueAnimator anim;

        ChipBar(Context c) {
            super(c);
            s = Ui.scale(c);
            setClipChildren(true);
            row = new FrameLayout(c);
            row.setClipChildren(false);
            addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void add(View v) { items.add(v); row.addView(v); }

        /** Places the items left to right (call after a count changes a chip's width). */
        void place() {
            float x = 0;
            for (int i = 0; i < items.size(); i++) {
                View v = items.get(i);
                float w = v instanceof Chip ? ((Chip) v).width() : ((GroupLabel) v).width();
                v.setLayoutParams(AppViews.at(getContext(), x, 0, w, 60));
                x += w + (v instanceof GroupLabel ? 24 : 20);
                if (v instanceof Chip) v.invalidate();
            }
            contentW = x - 20;
            // the row as wide as its chips: a clipping parent clips a child to the child's own bounds, so a row only as
            // wide as the bar cut off the chips scrolled in from the right (Serie A and beyond: an empty focus ring)
            ViewGroup.LayoutParams lp = row.getLayoutParams();
            int w = Math.round((contentW + 40) * s);
            if (lp.width != w) { lp.width = w; row.setLayoutParams(lp); }
        }

        void reveal(Chip c, boolean animate) {
            float left = 0;
            for (View v : items) {
                if (v == c) break;
                left += (v instanceof Chip ? ((Chip) v).width() : ((GroupLabel) v).width()) + (v instanceof GroupLabel ? 24 : 20);
            }
            float target = SettingsLogic.revealX(scroll, left, left + c.width(), getWidth() / s, contentW, 0);
            if (target == scroll) return;
            if (anim != null) anim.cancel();
            if (!animate) { scroll = target; row.setTranslationX(-scroll * s); return; }
            anim = ValueAnimator.ofFloat(scroll, target);
            anim.setDuration(Ui.SCROLL_MS);
            anim.setInterpolator(Ui.DECELERATE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { scroll = (float) a.getAnimatedValue(); row.setTranslationX(-scroll * s); }
            });
            anim.start();
        }
    }

    // ---------- club tiles ----------
    /**
     * A club tile (222 x 250, radius 24): the crest on an ivory squircle (116, radius 32; crest 88) over the name (28
     * Medium, two lines). Followed: a tile tinted with the club colour and the check badge. Focused: ring + 1.04.
     * Until its crest is on the TV the squircle carries the club's monogram (AppArt.monogram: the one placeholder,
     * never an empty squircle); the crest crossfades in over it (160 ms) when it arrives.
     */
    static final class ClubTile extends AppViews.Cell {
        static final float W = 222, H = 250;
        final Teams.Club club;
        boolean on;
        Bitmap crest;
        /** The crest's crossfade over the monogram (0: the monogram, 1: the crest). */
        private float appear = 1;
        private android.animation.ValueAnimator fade;
        private List<String> name = new ArrayList<>();

        /** The crest has arrived: it fades in over the monogram (at once when the tile is not on screen). */
        void crest(Bitmap b) {
            boolean had = crest != null;
            crest = b;
            if (fade != null) fade.cancel();
            if (had || !isShown()) { appear = 1; invalidate(); return; }
            appear = 0;
            fade = android.animation.ValueAnimator.ofFloat(0, 1);
            fade.setDuration(Ui.WATCH_MS);
            fade.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator a) { appear = (float) a.getAnimatedValue(); invalidate(); }
            });
            fade.start();
        }

        ClubTile(Context c, Teams.Club k, boolean on) {
            super(c);
            club = k;
            this.on = on;
            TextPaint p = textPaint(Ui.MEDIUM, 28, 0);
            SettingsLogic.Measure m = measurer(p);
            String s = SettingsLogic.tileName(k.label(), I18n.shortName(k.label()), I18n.el(), m, W - 28);
            List<String> l = SettingsLogic.wrap(m, s, W - 28, 2);
            if (l == null) {  // still three lines: the first line, then the rest cut
                l = SettingsLogic.wrap(m, s, W - 28, 99);
                String rest = s.substring(Math.min(s.length(), l.get(0).length())).trim();
                l = new ArrayList<>(java.util.Arrays.asList(l.get(0), Cards.ellipsize(p, rest, W - 28)));
            }
            name = l;
            describe();
        }

        void describe() { setContentDescription(club.label() + ", " + I18n.t(on ? "following" : "not_following")); }

        @Override float radius() { return 24 * getScaleX(); }
        @Override RectF shape() { return new RectF(0, 0, w() * getScaleX(), h() * getScaleY()); }

        @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            grow(this, gain, Ui.SCALE_TILE);
        }

        @Override void paint(Canvas cv) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(on ? SettingsLogic.followedFill(Ui.S1, CLUB) : Ui.S1);
            cv.drawRoundRect(new RectF(0, 0, W, H), 24, 24, p);
            fill(cv);
            float lh = 28 * 1.15f, col = 116 + 18 + name.size() * lh, top = (H - col) / 2;
            RectF sq = new RectF(W / 2 - 58, top, W / 2 + 58, top + 116);
            shadow(cv, sq, 32, 8, 20, 0.4f);
            p.setColor(IVORY);
            cv.drawRoundRect(sq, 32, 32, p);
            float shown = crest == null ? 0 : appear;
            if (shown < 1) AppArt.monogram(cv, club.name, sq.centerX(), sq.centerY(), 116, false, 1 - shown);
            if (crest != null) {
                float k = Math.min(88f / crest.getWidth(), 88f / crest.getHeight());
                float cw = crest.getWidth() * k, ch = crest.getHeight() * k;
                Paint cp = new Paint(AppArt.FILTER);
                cp.setAlpha(Math.round(255 * shown));
                cv.drawBitmap(crest, null, new RectF(sq.centerX() - cw / 2, sq.centerY() - ch / 2, sq.centerX() + cw / 2, sq.centerY() + ch / 2), cp);
            }
            Paint t = Ui.paint(Ui.MEDIUM, 28, Ui.TXT);
            for (int i = 0; i < name.size(); i++)
                AppKit.centre(cv, t, name.get(i), W / 2, Ui.baselineTop(Ui.MEDIUM, 28, top + 116 + 18 + i * lh, lh));
            if (on) checkBadge(cv, W - 14 - 22, 14 + 22, 44);
        }
    }

    /**
     * A club tile's slot while its league's list is on its way (SPEC-APP 2 skeleton: white 9 % blocks in the exact
     * slots of the loaded tile): the tile, its squircle and the two name lines, pulsing with the screen's skeletons.
     */
    static final class SkeletonTile extends AppViews.Spec {
        float pulse = 1;
        SkeletonTile(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) {
            float W = ClubTile.W, H = ClubTile.H;
            AppKit.skeleton(cv, 0, 0, W, H, 24, 0.45f * pulse);
            float lh = 28 * 1.15f, col = 116 + 18 + 2 * lh, top = (H - col) / 2;
            AppKit.skeleton(cv, W / 2 - 58, top, W / 2 + 58, top + 116, 32, pulse);
            float y = top + 116 + 18 + (lh - 22) / 2;
            AppKit.skeleton(cv, W / 2 - 70, y, W / 2 + 70, y + 22, 11, pulse);
            AppKit.skeleton(cv, W / 2 - 44, y + lh, W / 2 + 44, y + lh + 22, 11, pulse);
        }
    }

    // ---------- buttons ----------
    /** A pill button (64 px, 30 Medium) on a fill of its own (Skip white 14 %, Done surface3); focused, it inverts. */
    static final class Pill extends AppViews.Cell {
        final String label;
        final int base;
        Pill(Context c, String label, int base) { super(c); this.label = label; this.base = base; setContentDescription(label); }
        static float width(String label) { return Ui.paint(Ui.MEDIUM, 30, 0).measureText(label) + 72; }
        @Override boolean ownFocus() { return true; }
        @Override float radius() { return 32; }
        @Override void paint(Canvas cv) {
            float bw = width(label), h = 64, top = (h() - h) / 2, left = (w() - bw) / 2;
            RectF box = new RectF(left, top, left + bw, top + h);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (focus > 0) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 0.22f * focus));
                RectF halo = new RectF(box);
                halo.inset(-6, -6);
                cv.drawRoundRect(halo, 38, 38, p);
            }
            p.setColor(AppViews.blend(base, Ui.TXT, focus));
            cv.drawRoundRect(box, 32, 32, p);
            AppKit.centre(cv, Ui.paint(Ui.MEDIUM, 30, AppViews.blend(Ui.TXT, 0xFF111111, focus)), label, box.centerX(), Ui.baseline(Ui.MEDIUM, 30, top + h / 2));
        }
    }

    // ---------- the country list ----------
    /** A country (92 px, radius 18, 32 Medium; the pinned TV country 110 with its line): the check on the chosen one. */
    static final class CountryRow extends AppViews.Cell {
        final String cc, name, sub;
        final boolean chosen;
        CountryRow(Context c, String cc, String name, String sub, boolean chosen) {
            super(c);
            this.cc = cc; this.name = name; this.sub = sub == null ? "" : sub; this.chosen = chosen;
            setContentDescription(name + (this.sub.isEmpty() ? "" : ", " + this.sub) + (chosen ? ", " + I18n.t("selected") : ""));
        }
        @Override float radius() { return 18; }
        @Override void paint(Canvas cv) {
            float w = w(), h = h();
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(rowFill(focus));
            cv.drawRoundRect(new RectF(0, 0, w, h), 18, 18, bg);
            float room = w - 36 - (chosen ? 36 + 44 + 16 : 36);
            Paint t = Ui.paint(Ui.MEDIUM, 32, Ui.TXT);
            if (sub.isEmpty()) {
                cv.drawText(Cards.ellipsize(t, name, room), 36, Ui.baseline(Ui.MEDIUM, 32, h / 2), t);
            } else {
                float lt = Ui.lineHeight(Ui.MEDIUM, 32), ls = Ui.lineHeight(Ui.REGULAR, 24), top = (h - lt - 2 - ls) / 2;
                cv.drawText(Cards.ellipsize(t, name, room), 36, Ui.baselineTop(Ui.MEDIUM, 32, top, 0), t);
                Paint s = Ui.paint(Ui.REGULAR, 24, Ui.alpha(Ui.TXT, 0.6f));
                cv.drawText(Cards.ellipsize(s, sub, room), 36, Ui.baselineTop(Ui.REGULAR, 24, top + lt + 2, 0), s);
            }
            if (chosen) checkBadge(cv, w - 36 - 22, h / 2, 44);
        }
    }

    /** A 2 px rule (white 14 %). */
    static final class Rule extends AppViews.Spec {
        Rule(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override void paint(Canvas cv) { Paint p = new Paint(); p.setColor(0x24FFFFFF); cv.drawRect(0, 0, w(), h(), p); }
    }

    // ---------- the text panel ----------
    /**
     * Sources and credits / Privacy (SPEC-APP 5.5): a panel (x 260-1660, y 54-1026, surface1, radius 28) that is
     * itself the focused view. Title 48 Bold; paragraphs 30 at 86 %, line height 1.5, 26 apart; the body is clipped
     * with a bottom fade and scrolls 240 px per Up / Down (200 ms) until its last line sits clear of the fade, so the
     * last lines are never faded, cut or under the hint "▲▼ scroll · Back closes" below. A 6 px track at the right.
     */
    static final class TextPanel extends AppViews.Cell {
        static final float W = 1400, H = 972, PAD_L = 64, PAD_T = 52, LH = 45, GAP = 26, FADE = 92, STEP = 240;
        private final String title, hint;
        private final List<String> lines = new ArrayList<>();
        private final List<Float> tops = new ArrayList<>();
        private float contentH, scroll;
        private ValueAnimator anim;

        TextPanel(Context c, String title, String body, String hint) {
            super(c);
            this.title = title;
            this.hint = hint;
            TextPaint p = textPaint(Ui.REGULAR, 30, 0);
            float y = 0, width = W - 2 * PAD_L;
            for (String para : body.trim().split("\n\\s*\n")) {
                y += GAP;
                for (String l : lines(para.trim(), p, width)) { lines.add(l); tops.add(y); y += LH; }
            }
            contentH = y;
            setContentDescription(title + ". " + body);
        }

        @Override float radius() { return 28; }

        private float titleBottom() { return PAD_T + 48 * 1.1f; }
        private float hintTop() { return H - 34 - Ui.lineHeight(Ui.REGULAR, 24); }
        /** The body: from under the title to 24 px above the hint (never shared with it). */
        float bodyH() { return Math.min(770, hintTop() - 24 - titleBottom()); }
        float max() { return SettingsLogic.panelMax(contentH, bodyH(), FADE); }

        /** Scrolls by dy (spec px), animated; false at the end that way. */
        boolean scrollBy(float dy) {
            float target = SettingsLogic.clamp(scroll + dy, 0, max());
            if (anim != null && anim.isRunning()) target = SettingsLogic.clamp((Float) anim.getAnimatedValue() + dy, 0, max());
            if (target == scroll && (anim == null || !anim.isRunning())) return false;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(scroll, target);
            anim.setDuration(Ui.SCROLL_MS);
            anim.setInterpolator(Ui.DECELERATE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { scroll = (float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
            return true;
        }

        @Override void paint(Canvas cv) {
            RectF box = new RectF(0, 0, W, H);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(Ui.S1);
            cv.drawRoundRect(box, 28, 28, bg);
            Paint tp = Ui.paint(Ui.BOLD, 48, Ui.TXT);
            cv.drawText(Cards.ellipsize(tp, title, W - 2 * PAD_L), PAD_L, Ui.baselineTop(Ui.BOLD, 48, PAD_T, 48 * 1.1f), tp);
            float top = titleBottom(), bh = bodyH();
            // the body in a layer, so the fade takes the text out (DST_OUT) rather than painting over it
            int save = cv.saveLayer(PAD_L - 8, top, W - PAD_L + 8, top + bh, null);
            Paint p = Ui.paint(Ui.REGULAR, 30, Ui.alpha(Ui.TXT, 0.86f));
            for (int i = 0; i < lines.size(); i++) {
                float y = top + tops.get(i) - scroll;
                if (y + LH < top || y > top + bh) continue;
                cv.drawText(lines.get(i), PAD_L, Ui.baselineTop(Ui.REGULAR, 30, y, LH), p);
            }
            Paint fade = new Paint();
            float f0 = top + bh - FADE;
            fade.setShader(new LinearGradient(0, f0, 0, top + bh, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
            if (max() > 0 && scroll < max() - 0.5f) cv.drawRect(PAD_L - 8, f0, W - PAD_L + 8, top + bh, fade);
            if (scroll > 0) {  // lines leaving at the top fade out under the title too
                fade.setShader(new LinearGradient(0, top, 0, top + 40, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
                cv.drawRect(PAD_L - 8, top, W - PAD_L + 8, top + 40, fade);
            }
            cv.restoreToCount(save);
            // the track and its thumb
            float trackTop = 64, trackH = H - 128;
            Paint tr = new Paint(Paint.ANTI_ALIAS_FLAG);
            tr.setColor(0x1AFFFFFF);
            cv.drawRoundRect(new RectF(W - 28, trackTop, W - 22, trackTop + trackH), 3, 3, tr);
            float[] th = SettingsLogic.thumb(scroll, max(), trackTop, trackH, bh);
            tr.setColor(0x80FFFFFF);
            cv.drawRoundRect(new RectF(W - 28, th[0], W - 22, th[0] + th[1]), 3, 3, tr);
            cv.drawText(hint, PAD_L, Ui.baselineTop(Ui.REGULAR, 24, hintTop(), 0), Ui.paint(Ui.REGULAR, 24, Ui.alpha(Ui.TXT, 0.55f)));
        }
    }

    /** The dimmed screen behind a panel (Settings at 35 % under a 72 % scrim: bg at 90 %). */
    static final class Scrim extends AppViews.Spec {
        Scrim(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setClickable(true); }
        @Override void paint(Canvas cv) { cv.drawColor(Ui.alpha(Ui.APP_BG, 0.9f)); }
    }

    // ---------- the page ----------
    /** A ScrollView that never scrolls by itself to a focused child: the page scrolls it (200 ms, by section). */
    static final class Scroller extends ScrollView {
        Scroller(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }

    /** A view in the page's column, under the one before it (mt: the gap), or beside another (rel, dy). */
    static final class Block {
        final View v;
        float x, w, h, mt, y, dy;
        Block rel;
        Block(View v, float x, float w, float h, float mt) { this.v = v; this.x = x; this.w = w; this.h = h; this.mt = mt; }
    }

    /** A line of items the D-pad walks: Left / Right inside it, Up / Down to the next line. */
    static final class Line {
        final List<View> items = new ArrayList<>();
        /** The section heading the page scrolls to when an item of this line takes the focus (null: the page's top). */
        View anchor;
        /** The item the line is entered on (the shown league's chip), -1 for none. */
        int preferred = -1, remembered = -1;
        /** Re-entered where the focus last left it, from any line (SettingsLogic.move). */
        boolean kept;
        Line(View anchor, List<? extends View> items) { this.anchor = anchor; this.items.addAll(items); }
    }

    /**
     * A screen of Settings: an opaque backdrop, a column of blocks in a vertical scroll, the clock fixed at the top
     * right, and the lines of items the D-pad walks. The page scrolls by section (SettingsLogic.scrollTarget).
     */
    static class Page extends FrameLayout {
        final float s;
        final Scroller scroll;
        final FrameLayout col;
        final List<Block> blocks = new ArrayList<>();
        final List<Line> lines = new ArrayList<>();
        final Clock clock;
        private final TopFade fade, band;
        private float contentH;
        private ValueAnimator anim;
        /** Restores the focus here when the page is shown again (a key of the activity's). */
        String returnKey;

        /** The screen's title, which screen readers announce when the page opens or closes (Android 9+). */
        void title(String t) {
            if (android.os.Build.VERSION.SDK_INT >= 28) setAccessibilityPaneTitle(t);
        }

        Page(Context c, boolean withClock) {
            super(c);
            s = Ui.scale(c);
            addView(new Backdrop(c), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scroll = new Scroller(c);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setFocusable(false);
            scroll.setClipChildren(false);
            col = new FrameLayout(c);
            col.setClipChildren(false);
            col.setClipToPadding(false);
            scroll.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fade = new TopFade(c, 0);
            fade.setAlpha(0);
            addView(fade, AppViews.at(c, 0, 0, Ui.W, 48));
            // behind the clock when rows (not a heading) scroll under it: the status slot never sits on text
            band = new TopFade(c, 0.75f);
            band.setAlpha(0);
            addView(band, AppViews.at(c, 0, 0, Ui.W, 128));
            clock = new Clock(c);
            if (withClock) addView(clock, AppViews.at(c, 1224, 54, 600, 40));
        }

        Block add(View v, float x, float w, float h, float mt) {
            Block b = new Block(v, x, w, h, mt);
            blocks.add(b);
            col.addView(v);
            return b;
        }

        /** A block placed beside another (dy below its top), not taking a place in the column. */
        Block beside(View v, Block rel, float x, float w, float h, float dy) {
            Block b = add(v, x, w, h, 0);
            b.rel = rel;
            b.dy = dy;
            return b;
        }

        /** Places the blocks top to bottom (54 px title-safe padding at both ends). */
        void relayout() {
            float y = SettingsLogic.TOP;
            for (Block b : blocks) {
                if (b.v.getVisibility() == View.GONE) continue;
                if (b.rel != null) { b.y = b.rel.y + b.dy; b.v.setLayoutParams(AppViews.at(getContext(), b.x, b.y, b.w, b.h)); continue; }
                y += b.mt;
                b.y = y;
                b.v.setLayoutParams(AppViews.at(getContext(), b.x, y, b.w, b.h));
                y += b.h;
            }
            contentH = y + SettingsLogic.TOP;
            fitAnchors();
        }

        /**
         * Room at the end, so the last section's heading can still rise to the top (About is a page of its own, as
         * Options is): the page never stops half-way with the section above it under the clock.
         */
        void fitAnchors() {
            float need = 0;
            for (Line l : lines) {
                if (l.anchor == null) continue;
                for (Block b : blocks) if (b.v == l.anchor && b.v.getVisibility() != View.GONE) need = Math.max(need, b.y - SettingsLogic.TOP + Ui.H);
            }
            float end = 0;
            for (Block b : blocks) if (b.v.getVisibility() != View.GONE) end = Math.max(end, b.y + b.h);
            contentH = Math.max(end + SettingsLogic.TOP, need);
            col.setMinimumHeight(Math.round(contentH * s));
            col.setPadding(0, 0, 0, Math.round(SettingsLogic.TOP * s));
            col.requestLayout();
        }

        float contentH() { return contentH; }

        // ---------- the D-pad ----------
        int[] find(View f) {
            for (int li = 0; li < lines.size(); li++) {
                int i = lines.get(li).items.indexOf(f);
                if (i >= 0) return new int[]{li, i};
            }
            return null;
        }

        View first() {
            for (Line l : lines) if (!l.items.isEmpty()) return l.items.get(0);
            return null;
        }

        /** The item a direction leads to from f (null: nothing that way). */
        View move(View f, int dir) {
            int[] at = find(f);
            if (at == null) return first();
            float[][] centres = new float[lines.size()][];
            int[] pref = new int[lines.size()], rem = new int[lines.size()];
            boolean[] kept = new boolean[lines.size()];
            int[] loc = new int[2];
            for (int li = 0; li < lines.size(); li++) {
                Line l = lines.get(li);
                centres[li] = new float[l.items.size()];
                for (int i = 0; i < l.items.size(); i++) {
                    View v = l.items.get(i);
                    v.getLocationInWindow(loc);
                    centres[li][i] = loc[0] + v.getWidth() * v.getScaleX() / 2f;
                }
                pref[li] = l.preferred;
                rem[li] = l.remembered;
                kept[li] = l.kept;
            }
            int[] to = SettingsLogic.move(centres, at[0], at[1], dir, pref, rem, kept);
            if (to == null) return null;
            if (to[0] != at[0]) lines.get(at[0]).remembered = at[1];
            return lines.get(to[0]).items.get(to[1]);
        }

        /** The top of a view in the column (spec px). */
        float top(View v) {
            float y = 0;
            for (View x = v; x != null && x != col; ) {
                y += x.getTop();
                x = x.getParent() instanceof View ? (View) x.getParent() : null;
            }
            return y / s;
        }

        /** Scrolls so the focused item shows under its section heading (200 ms, decelerating). */
        void reveal(final View v, final boolean animate) {
            if (v.isLayoutRequested() || col.isLayoutRequested()) {  // placed but not laid out yet: once it is
                post(new Runnable() { public void run() { if (v.isAttachedToWindow() && !v.isLayoutRequested()) reveal(v, animate); } });
                return;
            }
            int[] at = find(v);
            View anchor = at == null ? null : lines.get(at[0]).anchor;
            float top = top(v), bottom = top + v.getHeight() / s;
            float anchorTop = anchor == null ? SettingsLogic.TOP : top(anchor);
            float target = SettingsLogic.scrollTarget(anchorTop, top, bottom, Ui.H, contentH);
            float bandTo = target > 0 && Math.abs(target - (anchorTop - SettingsLogic.TOP)) > 1 ? 1 : 0;
            if (animate) band.animate().alpha(bandTo).setDuration(Ui.SCROLL_MS).start(); else band.setAlpha(bandTo);
            scrollTo(target, animate);
            if (v instanceof Chip && v.getParent() != null && v.getParent().getParent() instanceof ChipBar)
                ((ChipBar) v.getParent().getParent()).reveal((Chip) v, animate);
        }

        void scrollTo(float target, boolean animate) {
            int to = Math.round(target * s);
            if (anim != null) anim.cancel();
            if (!animate || to == scroll.getScrollY()) { scroll.scrollTo(0, to); fade.setAlpha(to > 0 ? 1 : 0); return; }
            anim = ValueAnimator.ofInt(scroll.getScrollY(), to);
            anim.setDuration(Ui.SCROLL_MS);
            anim.setInterpolator(Ui.DECELERATE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    int y = (int) a.getAnimatedValue();
                    scroll.scrollTo(0, y);
                    fade.setAlpha(Math.min(1, y / (24 * s)));
                }
            });
            anim.start();
        }

        void release() { if (anim != null) anim.cancel(); }
    }
}
