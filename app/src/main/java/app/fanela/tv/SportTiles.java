package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The tiles of Settings › Your clubs and sports for Formula 1 and UFC (SPEC-F1-UFC §10), in the club tile's grammar
 * (SettingsViews.ClubTile: 222 x 250, radius 24, a 116 squircle over the name, followed = the club-tinted tile and the
 * check badge, focused = ring + 1.04):
 *  - SportTile: two tile widths (474 x 250); a graphite disc with the sport's monogram in Fanela's type (no F1 or UFC
 *    logo), the name and a state line ("Following · every race weekend" / "Not followed"). OK toggles the follow.
 *  - TeamTile: the squircle filled with the team's painted colour, nothing on it (no team logos), the name under.
 *  - DriverTile: the squircle in the team's painted colour with the three-letter code (RC Bold 50), the name under.
 *  - FighterTile: the headshot on the ivory squircle (initials until it is on the TV), the name, the weight class.
 * A focus tile stands at 40 % and takes no focus until its sport is followed. A refused pick shakes the tile by 2 px.
 */
final class SportTiles {
    private SportTiles() { }

    static final float W = SettingsViews.ClubTile.W, H = SettingsViews.ClubTile.H, WIDE = 2 * W + 30, SQ = 116;
    /** The F1 / UFC graphite (SPEC-F1-UFC §5.1, app field), the disc behind the sport monogram. */
    static final int GRAPHITE = 0xFF2B2D33, DARK_INK = 0xFF141416;

    /**
     * A team colour as the cards paint it (EventKit.teamPaint: a near-white team gets the warm grey, as clubs do; a
     * silver one, Cadillac, keeps its silver, so Williams and Cadillac never share a tile colour).
     */
    static int painted(int color) {
        if (color == 0) return GRAPHITE;
        return EventKit.teamPaint(color);
    }

    /** The trim edge (6 px inside the squircle) a near-white team's squircle carries, so no two tiles look alike. */
    static final float EDGE = 6;

    /** A team's squircle: its painted colour; a near-white one edged in the team's trim colour (none known: plain). */
    static void squircle(Canvas cv, RectF sq, int teamColor, int trim) {
        SettingsViews.shadow(cv, sq, 32, 8, 20, 0.4f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(painted(teamColor));
        cv.drawRoundRect(sq, 32, 32, p);
        if (!EventKit.nearWhite(teamColor) || trim == 0) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(EDGE);
        p.setColor(ClubTokens.opaque(trim));
        RectF in = new RectF(sq.left + EDGE / 2, sq.top + EDGE / 2, sq.right - EDGE / 2, sq.bottom - EDGE / 2);
        cv.drawRoundRect(in, 32 - EDGE / 2, 32 - EDGE / 2, p);
    }

    /** Ink on a coloured squircle: white, or near-black on a light colour (Alpine's yellow, a white team). */
    static int ink(int fill) { return android.graphics.Color.luminance(fill) > 0.45f ? DARK_INK : 0xFFFFFFFF; }

    /** "Kevin Holland" -> "KH"; one word -> its first two letters. */
    static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] w = name.trim().split("\\s+");
        String a = w[0], b = w.length > 1 ? w[w.length - 1] : "";
        if (b.isEmpty()) return a.substring(0, Math.min(2, a.length())).toUpperCase(Locale.ROOT);
        return (a.substring(0, 1) + b.substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** A name in at most two lines of the tile's width (the second cut when it still runs on). */
    static List<String> nameLines(String s, float width) {
        TextPaint p = SettingsViews.textPaint(Ui.MEDIUM, 28, 0);
        SettingsLogic.Measure m = SettingsViews.measurer(p);
        List<String> l = SettingsLogic.wrap(m, s, width, 2);
        if (l == null) {
            l = SettingsLogic.wrap(m, s, width, 99);
            String rest = s.substring(Math.min(s.length(), l.get(0).length())).trim();
            l = new ArrayList<>(Arrays.asList(l.get(0), Cards.ellipsize(p, rest, width)));
        }
        return l;
    }

    /** What every sport tile shares: the surface, the followed tint and badge, the focus growth, the disabled state. */
    abstract static class Tile extends AppViews.Cell {
        final String label;
        final float tw;
        boolean on, usable = true;
        List<String> name = new ArrayList<>();
        String sub = "";

        Tile(Context c, String label, float width) {
            super(c);
            this.label = label == null ? "" : label;
            this.tw = width;
        }

        /** Usable only once the sport is followed (focus tiles): otherwise at 40 % and out of the D-pad's way. */
        void usable(boolean u) {
            usable = u;
            setFocusable(u);
            setFocusableInTouchMode(u);
            setAlpha(u ? 1f : 0.4f);
            describe();
        }

        void describe() {
            setContentDescription(label + (sub.isEmpty() ? "" : ", " + sub) + ", " + I18n.t(on ? "following" : "not_following"));
        }

        /** "Two at most": the tile shakes 2 px (left, right, left, home; 240 ms). */
        void shake() {
            float d = 2 * s;
            animate().cancel();
            setTranslationX(0);
            android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(this, "translationX", 0, -d, d, -d, d, 0);
            a.setDuration(240);
            a.start();
        }

        @Override float radius() { return 24 * getScaleX(); }
        @Override RectF shape() { return new RectF(0, 0, w() * getScaleX(), h() * getScaleY()); }

        @Override protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            SettingsViews.grow(this, gain, Ui.SCALE_TILE);
        }

        /** The mark in its squircle (or disc) box. */
        abstract void mark(Canvas cv, RectF sq);

        /** The most lines the name may take (nameLines: two; the sport tile's name: one). */
        int maxLines() { return 2; }

        /**
         * The squircle's top (QAS-8): fixed for the tile kind, so a grid's marks sit level whatever the name: where a
         * one-line name centres the column, raised only as far as a two-line name (and the sub line) needs to end 8 px
         * above the tile's foot. Names hang below it.
         */
        static float markTop(boolean hasSub, int maxLines) {
            float lh = 28 * 1.15f, sh = hasSub ? 22 * 1.3f + 2 : 0;
            float one = (H - (SQ + 18 + lh + sh)) / 2, most = H - 8 - (SQ + 18 + maxLines * lh + sh);
            return Math.min(one, most);
        }

        @Override void paint(Canvas cv) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(on ? SettingsLogic.followedFill(Ui.S1, SettingsViews.CLUB) : Ui.S1);
            cv.drawRoundRect(new RectF(0, 0, tw, H), 24, 24, p);
            fill(cv);
            float lh = 28 * 1.15f, top = markTop(hasSub(), maxLines());
            mark(cv, new RectF(tw / 2 - SQ / 2, top, tw / 2 + SQ / 2, top + SQ));
            Paint t = Ui.paint(Ui.MEDIUM, 28, Ui.TXT);
            float y = top + SQ + 18;
            for (int i = 0; i < name.size(); i++) AppKit.centre(cv, t, name.get(i), tw / 2, Ui.baselineTop(Ui.MEDIUM, 28, y + i * lh, lh));
            if (!sub.isEmpty()) {
                Paint sp = Ui.paint(Ui.REGULAR, 22, Ui.alpha(Ui.TXT, 0.6f));
                // the sub line on one baseline for the tile kind: under the most lines a name may take, so a fighter's
                // weight class sits level whether his name took one line or two
                AppKit.centre(cv, sp, Cards.ellipsize(sp, sub, tw - 28), tw / 2, Ui.baselineTop(Ui.REGULAR, 22, y + maxLines() * lh + 2, 22 * 1.3f));
            }
            if (on) SettingsViews.checkBadge(cv, tw - 14 - 22, 14 + 22, 44);
        }

        /** The tile kind has a sub line (a fighter's weight class, the sport's state): fighters' tiles always do. */
        boolean hasSub() { return !sub.isEmpty(); }
    }

    /** The sport itself (474 x 250): OK follows or unfollows it, as a club tile does. */
    static final class SportTile extends Tile {
        final String sport;

        SportTile(Context c, String sport, boolean on) {
            super(c, SportsSettings.sportName(sport), WIDE);
            this.sport = sport;
            name = Arrays.asList(label);
            set(on);
        }

        /** The state line already says whether it is followed. */
        @Override void describe() { setContentDescription(label + ", " + sub); }

        @Override int maxLines() { return 1; }

        void set(boolean followed) {
            on = followed;
            sub = SportsSettings.stateLine(sport, followed);
            describe();
            invalidate();
        }

        @Override void mark(Canvas cv, RectF sq) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            SettingsViews.shadow(cv, sq, SQ / 2, 8, 20, 0.4f);
            p.setColor(GRAPHITE);
            cv.drawCircle(sq.centerX(), sq.centerY(), SQ / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(0x33FFFFFF);
            cv.drawCircle(sq.centerX(), sq.centerY(), SQ / 2 - 1, p);
            String mono = SportEvent.F1.equals(sport) ? "F1" : "UFC";
            Paint t = Ui.paint(Ui.COND_BOLD, 50, 0xFFFFFFFF);
            float w = t.measureText(mono), room = SQ * 0.74f;
            if (w > room) t.setTextSize(50 * room / w);
            AppKit.centre(cv, t, mono, sq.centerX(), Ui.baseline(Ui.COND_BOLD, t.getTextSize(), sq.centerY()));
        }
    }

    /** A constructor: its colour on the squircle (a near-white one edged in its trim), nothing on it; the name under. */
    static final class TeamTile extends Tile {
        final String ref;
        final int color, trim;

        TeamTile(Context c, String teamId, String teamName, int color, boolean on) {
            super(c, teamName, W);
            this.ref = "t:" + teamId;
            this.color = color;
            this.trim = SportNames.trim(teamId, teamName);
            this.on = on;
            name = nameLines(label, W - 28);
            describe();
        }

        @Override void mark(Canvas cv, RectF sq) { squircle(cv, sq, color, trim); }
    }

    /** A driver: his three-letter code on his team's colour, the full name under. */
    static final class DriverTile extends Tile {
        final String ref, code;
        int color, trim;

        /** teamColor: the team's colour (0 until known); trim: its trim colour (SportNames.trim, 0 when unknown). */
        DriverTile(Context c, String driverId, String code, String fullName, int teamColor, int trim, boolean on) {
            super(c, fullName, W);
            this.ref = "d:" + driverId;
            this.code = code == null || code.isEmpty() ? initials(fullName) : code.toUpperCase(Locale.ROOT);
            this.color = teamColor;
            this.trim = trim;
            this.on = on;
            name = nameLines(label, W - 28);
            describe();
        }

        /** The team's colour arrived (the roster comes after the standings). */
        void color(int teamColor, int trim) { color = teamColor; this.trim = trim; invalidate(); }

        @Override void mark(Canvas cv, RectF sq) {
            squircle(cv, sq, color, trim);
            Paint t = Ui.paint(Ui.COND_BOLD, 50, ink(painted(color)));
            float w = t.measureText(code), room = SQ - 16;
            if (w > room) t.setTextSize(50 * room / w);
            AppKit.centre(cv, t, code, sq.centerX(), Ui.baseline(Ui.COND_BOLD, t.getTextSize(), sq.centerY()));
        }
    }

    /** A fighter: the headshot on the ivory squircle (initials meanwhile), the name, the weight class. */
    static final class FighterTile extends Tile {
        final String id, headshotUrl;
        Bitmap face;

        FighterTile(Context c, String id, String fullName, String weight, String headshotUrl, boolean on) {
            super(c, fullName, W);
            this.id = id;
            this.headshotUrl = headshotUrl;
            this.on = on;
            sub = weight == null ? "" : weight;
            name = nameLines(label, W - 28);
            describe();
        }

        void face(Bitmap b) { face = b; invalidate(); }

        /** Every fighter's tile keeps the weight line's room, so a fighter without a class sits level too. */
        @Override boolean hasSub() { return true; }

        @Override void mark(Canvas cv, RectF sq) {
            SettingsViews.shadow(cv, sq, 32, 8, 20, 0.4f);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(SettingsViews.IVORY);
            cv.drawRoundRect(sq, 32, 32, p);
            if (face == null) {
                Paint t = Ui.paint(Ui.BOLD, SQ * 0.31f, Ui.alpha(DARK_INK, 0.6f));
                AppKit.centre(cv, t, initials(label), sq.centerX(), Ui.baseline(Ui.BOLD, SQ * 0.31f, sq.centerY()));
                return;
            }
            // the head and shoulders: the photo as wide as the squircle, from its top, clipped to the squircle
            cv.save();
            Path clip = new Path();
            clip.addRoundRect(sq, 32, 32, Path.Direction.CW);
            cv.clipPath(clip);
            float k = sq.width() / face.getWidth(), fh = face.getHeight() * k;
            float top = fh > sq.height() ? sq.top : sq.bottom - fh;
            cv.drawBitmap(face, null, new RectF(sq.left, top, sq.right, top + fh), AppArt.FILTER);
            cv.restore();
        }
    }
}
