package app.fanela.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * The views both home layouts are built from. Every view draws on a canvas scaled to spec px (1920x1080), so the
 * spec's numbers are used as they are. Focus is one FocusLayer on top of the screen: it draws the single ring, glow and
 * shadow outside whichever Cell has focus and slides between them (150 ms); the Cell itself only lifts its tonal fill.
 */
final class AppViews {
    private AppViews() { }

    /** Layout params placing a child at (x, y, w, h) in spec px inside a FrameLayout. */
    static FrameLayout.LayoutParams at(Context c, float x, float y, float w, float h) {
        float s = Ui.scale(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w * s), Math.round(h * s));
        lp.leftMargin = Math.round(x * s);
        lp.topMargin = Math.round(y * s);
        return lp;
    }

    /** A view drawn in spec px. */
    abstract static class Spec extends View {
        final float s;
        Spec(Context c) { super(c); s = Ui.scale(c); setWillNotDraw(false); }

        @Override protected final void onDraw(Canvas cv) {
            cv.save();
            cv.scale(s, s);
            paint(cv);
            cv.restore();
        }

        abstract void paint(Canvas cv);

        float w() { return getWidth() / s; }
        float h() { return getHeight() / s; }
    }

    /** A focusable item: its shape (spec px, local) is what the FocusLayer rings; the fill fades in with focus. */
    abstract static class Cell extends Spec {
        float focus;
        private ValueAnimator anim;
        /** The item sits on a light field: the ring and the fill turn dark. */
        boolean onLight;

        Cell(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);  // some TV boxes report a touchscreen: focus must still work
            setClickable(true);
            setDefaultFocusHighlightEnabled(false);
        }

        RectF shape() { return new RectF(0, 0, w(), h()); }
        float radius() { return Ui.R_ROW; }
        /** Draws its own focus (an inverted button): the layer leaves it alone. */
        boolean ownFocus() { return false; }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
            anim.setDuration(Ui.FOCUS_MS);
            anim.setInterpolator(Ui.FAST_OUT_SLOW_IN);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { focus = (float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        /** The tonal fill under the content (7.5 % white, or black on a light field). */
        void fill(Canvas cv) {
            if (focus <= 0 || ownFocus()) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Ui.alpha(onLight ? 0xFF000000 : 0xFFFFFFFF, 0.075f * focus));
            cv.drawRoundRect(shape(), radius(), radius(), p);
        }
    }

    /**
     * The one focus ring on screen: 4 px ring + 10 px glow outside the focused Cell's shape, and its shadow (0 20 50,
     * black 35 %, painted outside the shape only, as CSS does). It follows the focus with a 150 ms slide, and the
     * focused view's movement (a list scrolling, a crossfade) frame by frame.
     */
    static final class FocusLayer extends Spec implements ViewTreeObserver.OnGlobalFocusChangeListener, ViewTreeObserver.OnPreDrawListener {
        private Cell target;
        private final RectF from = new RectF(), to = new RectF(), cur = new RectF();
        private float rFrom, rTo, alpha, shownAlpha;
        private long start;
        private boolean light;
        private final int[] a = new int[2], b = new int[2];

        FocusLayer(Context c) { super(c); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnGlobalFocusChangeListener(this);
            getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
            getViewTreeObserver().removeOnPreDrawListener(this);
            super.onDetachedFromWindow();
        }

        public void onGlobalFocusChanged(View old, View now) {
            Cell next = now instanceof Cell ? (Cell) now : null;
            if (next == target) return;
            RectF r = new RectF();
            float ra = next == null ? 0 : place(next, r);
            if (target == null || next == null || shownAlpha <= 0.01f) {
                cur.set(r); from.set(r); to.set(r); rFrom = rTo = ra; start = 0;
            } else {
                from.set(cur); rFrom = current(); to.set(r); rTo = ra; start = System.currentTimeMillis();
            }
            target = next;
            invalidate();
        }

        /** The focused cell's shape in this layer's spec px; returns its corner radius. */
        private float place(Cell c, RectF out) {
            c.getLocationInWindow(a);
            getLocationInWindow(b);
            RectF sh = c.shape();
            float dx = (a[0] - b[0]) / s, dy = (a[1] - b[1]) / s;
            out.set(sh.left + dx, sh.top + dy, sh.right + dx, sh.bottom + dy);
            return c.radius();
        }

        private float current() {
            float t = progress();
            return rFrom + (rTo - rFrom) * t;
        }

        private float progress() {
            if (start == 0) return 1;
            float t = Math.min(1, (System.currentTimeMillis() - start) / (float) Ui.FOCUS_MS);
            return Ui.FAST_OUT_SLOW_IN.getInterpolation(t);
        }

        public boolean onPreDraw() {
            if (target == null) {  // focus given before this layer was attached: adopt it
                View f = getRootView().findFocus();
                if (f instanceof Cell) onGlobalFocusChanged(null, f);
                else if (shownAlpha != 0) invalidate();
                return true;
            }
            RectF r = new RectF();
            rTo = place(target, r);
            float al = target.isShown() ? effectiveAlpha(target) : 0;
            boolean lt = target.onLight;
            if (!r.equals(to) || al != alpha || lt != light) {
                if (start == 0 || progress() >= 1) { from.set(r); }
                to.set(r);
                alpha = al;
                light = lt;
                invalidate();
            }
            return true;
        }

        private float effectiveAlpha(View v) {
            float al = 1;
            for (View x = v; x != null && x != getParent(); ) {
                al *= x.getAlpha();
                ViewParent p = x.getParent();
                x = p instanceof View ? (View) p : null;
            }
            return al;
        }

        @Override void paint(Canvas cv) {
            if (target == null || target.ownFocus() || !target.isFocused()) { shownAlpha = 0; return; }
            float t = progress();
            cur.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t);
            float r = current();
            shownAlpha = alpha;
            if (alpha <= 0.01f) return;
            Path shape = new Path();
            shape.addRoundRect(cur, r, r, Path.Direction.CW);
            cv.save();
            cv.clipOutPath(shape);
            // the shadow, blurred by stacking: eight expanding layers under the shape, offset 20 px down
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(Ui.alpha(0xFF000000, 0.052f * alpha));
            for (int i = 0; i < 8; i++) {
                float e = -24 + i * 48 / 7f;
                RectF l = new RectF(cur.left - e, cur.top - e + 20, cur.right + e, cur.bottom + e + 20);
                if (l.width() > 0 && l.height() > 0) cv.drawRoundRect(l, Math.max(0, r + e), Math.max(0, r + e), sp);
            }
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(Ui.GLOW);
            g.setColor(Ui.alpha(light ? 0xFF000000 : 0xFFFFFFFF, 0.06f * alpha));
            RectF gr = new RectF(cur);
            gr.inset(-Ui.GLOW / 2, -Ui.GLOW / 2);
            cv.drawRoundRect(gr, r + Ui.GLOW / 2, r + Ui.GLOW / 2, g);
            g.setStrokeWidth(Ui.RING);
            g.setColor(Ui.alpha(light ? FieldColors.INK_DARK : Ui.RING_COLOR, alpha));
            RectF rr = new RectF(cur);
            rr.inset(-Ui.RING / 2, -Ui.RING / 2);
            cv.drawRoundRect(rr, r + Ui.RING / 2, r + Ui.RING / 2, g);
            cv.restore();
            if (t < 1) postInvalidateOnAnimation();
        }
    }

    /** One line of text (section labels): face, size, colour, tracking, drawn at a baseline in local spec px. */
    static final class Label extends Spec {
        String text = "";
        final Paint p;
        float baseline;
        Label(Context c, android.graphics.Typeface face, float size, int color, float tracking, float baseline) {
            super(c);
            p = AppKit.caps(face, size, color, tracking);
            this.baseline = baseline;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void set(String s) { if (!s.equals(text)) { text = s; invalidate(); } }
        @Override void paint(Canvas cv) { cv.drawText(text, -AppKit.lead(p), baseline, p); }
    }

    /** A pill button (64 px, radius 999, 30 Medium, white 14 %): focused, it inverts with a 6 px halo. */
    static final class Button extends Cell {
        String label;
        Button(Context c, String label) { super(c); this.label = label; setContentDescription(label); }

        static float width(String label) { return Ui.paint(Ui.MEDIUM, 30, 0).measureText(label) + 72; }

        @Override boolean ownFocus() { return true; }
        @Override float radius() { return 32; }

        @Override void paint(Canvas cv) {
            float pad = (w() - width(label)) / 2, hh = 64, top = (h() - hh) / 2;
            RectF box = new RectF(pad, top, w() - pad, top + hh);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (focus > 0) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 0.22f * focus));
                RectF halo = new RectF(box);
                halo.inset(-6, -6);
                cv.drawRoundRect(halo, 38, 38, p);
            }
            p.setColor(blend(Ui.alpha(0xFFFFFFFF, 0.14f), Ui.TXT, focus));
            cv.drawRoundRect(box, 32, 32, p);
            Paint t = Ui.paint(Ui.MEDIUM, 30, blend(Ui.TXT, 0xFF111111, focus));
            AppKit.centre(cv, t, label, w() / 2, Ui.baseline(Ui.MEDIUM, 30, top + hh / 2));
        }
    }

    /**
     * The LIVE pill's dot as a view of its own (white at the given opacity), laid over a pill drawn without it: it
     * pulses (opacity .35 -> 1 -> .35, 1.2 s) through its view alpha, so nothing is redrawn, and only while it is on
     * screen (it stops when it is hidden, the screen is left or the view goes).
     */
    static final class LiveDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private android.animation.ObjectAnimator anim;

        LiveDot(Context c, float opacity) {
            super(c);
            p.setColor(Ui.alpha(0xFFFFFFFF, opacity));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setWillNotDraw(false);
        }

        @Override protected void onDraw(Canvas cv) {
            cv.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, p);
        }

        @Override public boolean hasOverlappingRendering() { return false; }

        @Override public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            run(visible);
        }

        @Override protected void onDetachedFromWindow() {
            run(false);
            super.onDetachedFromWindow();
        }

        private void run(boolean on) {
            if (on == (anim != null)) return;
            if (!on) { anim.cancel(); anim = null; setAlpha(1); return; }
            anim = android.animation.ObjectAnimator.ofFloat(this, View.ALPHA, 0.35f, 1f);
            anim.setDuration(Ui.PULSE_MS / 2);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(Ui.FAST_OUT_SLOW_IN);
            anim.start();
        }
    }

    /** a to b by t, alpha included. */
    static int blend(int a, int b, float t) {
        int aa = a >>> 24, ab = b >>> 24;
        int al = Math.round(aa + (ab - aa) * t);
        return (al << 24) | (ClubTokens.mix(a, b, t) & 0xFFFFFF);
    }

    /**
     * The masthead (y 54-102): the brand (shirt mark in the field's ink + "Fanela") at the left, the status slot at the
     * right: the clock, "Updating…", or the offline banner. Only "Updating…" and the offline banner are announced as
     * they appear (a polite live region); the clock is read when the viewer asks for it, never every minute.
     */
    static final class Masthead extends Spec {
        HomeModel.Status status = HomeModel.Status.CLOCK;
        String text = "";
        int ink = Ui.TXT, statusInk = Ui.TXT;
        android.graphics.Bitmap shirt;
        float right = 1824;
        String brand = "Fanela";

        Masthead(Context c) { super(c); setAccessibilityLiveRegion(liveRegion(status)); }

        /** The status slot's live region: none for the clock (it changes every minute), polite for the rest. */
        static int liveRegion(HomeModel.Status st) {
            return st == HomeModel.Status.CLOCK ? ACCESSIBILITY_LIVE_REGION_NONE : ACCESSIBILITY_LIVE_REGION_POLITE;
        }

        void set(HomeModel.Status st, String t) {
            if (st == status && t.equals(text)) return;
            status = st;
            text = t;
            setAccessibilityLiveRegion(liveRegion(st));  // before the description: a clock change is never announced
            setContentDescription(t);
            invalidate();
        }

        @Override void paint(Canvas cv) {
            float cy = 78;
            if (shirt != null) {
                Paint sp = new Paint(AppArt.FILTER);
                sp.setAlpha(Math.round(255 * 0.85f));
                cv.drawBitmap(shirt, null, new RectF(96, cy - 16, 128, cy + 16), sp);
            }
            Paint b = Ui.paint(Ui.MEDIUM, 26, Ui.alpha(ink, 0.85f));
            cv.drawText(brand, 140, Ui.baseline(Ui.MEDIUM, 26, cy), b);
            if (text.isEmpty()) return;
            if (status == HomeModel.Status.OFFLINE) {
                Paint t = Ui.paint(Ui.REGULAR, 26, Ui.TXT);
                float tw = t.measureText(text), w = 2 + 16 + 28 + 14 + tw + 22 + 2;
                RectF box = new RectF(right - w, cy - 24, right, cy + 24);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0x1AFFFFFF);
                cv.drawRoundRect(box, 24, 24, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(0x38FFFFFF);
                RectF in = new RectF(box);
                in.inset(1, 1);
                cv.drawRoundRect(in, 23, 23, p);
                AppKit.cloudOff(cv, box.left + 18 + 14, cy, 28, Ui.alpha(Ui.TXT, 0.8f));
                cv.drawText(text, box.left + 18 + 28 + 14, Ui.baseline(Ui.REGULAR, 26, cy), t);
            } else {
                Paint t = Ui.paint(Ui.REGULAR, 28, Ui.alpha(statusInk, 0.68f));
                AppKit.right(cv, t, text, right, Ui.baseline(Ui.REGULAR, 28, cy));
            }
        }
    }
}
