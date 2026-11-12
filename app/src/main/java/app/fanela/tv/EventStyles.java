package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

/** The event styles by id, and the one call that draws an event card. Stream E owns this file. */
final class EventStyles {
    private EventStyles() { }

    static final int W = 960, H = 540;

    /** The style for an Appearance id; unknown -> Kit. */
    static EventStyle get(String id) {
        if (id == null) return new KitEventStyle();
        switch (id) {
            case Appearance.SIGNATURE: return new SignatureEventStyle();
            case Appearance.CLEAN: return new CleanEventStyle();
            case Appearance.TICKET: return new TicketEventStyle();
            case Appearance.CLASH: return new ClashEventStyle();
            case Appearance.KIT:
            default: return new KitEventStyle();
        }
    }

    /**
     * The card's colours, through the football kit algorithm (SPEC-F1-UFC §5.2): the focus team's colour as the main
     * colour and its bundled trim as the second, so a white team (Williams) gets the warm grey and a dark one the
     * charcoal treatment, as clubs do. No focus (and UFC): graphite, which the styles replace with their own fixed
     * tokens (graphite light, red and blue corners). A silver team (Cadillac) lights the card in its silver, not in
     * the white teams' warm light (EventKit.teamPaint: Settings' tiles and Kit's band paint it the same way).
     */
    static ClubTokens tokens(EventCardModel m) {
        int main = m.accent != 0 ? m.accent : EventKit.f1(m) ? EventCardModel.F1_HOUSE : EventCardModel.UFC_HOUSE;
        int trim = m.trim != 0 ? m.trim : EventKit.f1(m) ? EventCardModel.F1_IVORY : EventCardModel.UFC_BLUE;
        ClubTokens t = ClubTokens.of(m.sport, ClubTokens.opaque(main), ClubTokens.opaque(trim), null, null);
        int paint = EventKit.teamPaint(main);
        if (t.kit == ClubTokens.Kit.WHITE && paint != ClubTokens.KIT_WHITE) t.light = paint;
        return t;
    }

    /** A 960x540 card; a style that does not suit the card hands it to Kit. */
    static Bitmap draw(Context c, EventCardModel m, String styleId) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        drawOn(c, new Canvas(b), m, styleId);
        return b;
    }

    /** Draws the card on a canvas (the debug matrix passes its probe); returns the style that drew it. */
    static EventStyle drawOn(Context c, Canvas cv, EventCardModel m, String styleId) {
        if (c != null) Ui.fonts(c);
        EventArt art = new EventArt(c, m);
        try {
            EventStyle s = get(styleId);
            if (!s.suits(m, art)) s = new KitEventStyle();
            s.draw(c, cv, m, tokens(m), art);
            return s;
        } finally {
            art.release();
        }
    }
}
