package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.util.LinkedHashMap;
import java.util.Map;

/** The card styles, and the one place that turns a match into a card bitmap in the viewer's style. */
final class CardStyles {
    private CardStyles() { }

    private static final Map<String, CardStyle> ALL = new LinkedHashMap<>();
    static void register(CardStyle s) { ALL.put(s.id(), s); }
    static {
        register(new LegacyStyle());
        register(new TicketStyle());
        register(new CleanStyle());
        register(new SignatureStyle());
        register(new KitStyle());
        register(new ClashStyle());
    }

    /** The style for an id; a style not built yet draws as the legacy card. */
    static CardStyle get(String id) {
        CardStyle s = ALL.get(id);
        return s != null ? s : ALL.get(LegacyStyle.ID);
    }

    /** The followed club's colour tokens; the crests' analysis decides the disc rule (the match's crest) and the
     *  ghost (the club's best crest, CardArt.ghostCrest). */
    static ClubTokens tokens(Teams.Club k, CardModel model, CardArt art) {
        return tokens(k, crest(model.myCrest, art.myCrest()), crest(art.ghostCrestUrl(), art.ghostCrest()));
    }

    static ClubTokens tokens(Teams.Club k, ClubTokens.CrestInfo crest, ClubTokens.CrestInfo ghost) {
        int[] col = Badges.colors(k.name);
        int kit = col[0], trim = col[1];
        if (!Badges.known(k.name) && k.color != null && k.color.matches("[0-9a-fA-F]{6}")) kit = 0xFF000000 | Integer.parseInt(k.color, 16);
        return ClubTokens.of(k.id, kit, trim, crest, ghost);
    }

    private static final Map<String, ClubTokens.CrestInfo> CRESTS = new java.util.HashMap<>();

    /** A crest's analysis, once per image URL (it never changes for a URL). */
    static synchronized ClubTokens.CrestInfo crest(String url, Bitmap b) {
        if (b == null) return null;
        String key = url == null ? "" : url;
        ClubTokens.CrestInfo c = CRESTS.get(key);
        if (c == null) {
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            c = ClubTokens.analyse(px, w, h);
            if (CRESTS.size() > 200) CRESTS.clear();
            CRESTS.put(key, c);
        }
        return c;
    }

    /** The opponent's kit (main, second): known clubs from the colour table, others from the crest's dominant hue. */
    static int[] opponentKit(String raw, ClubTokens.CrestInfo crest) {
        if (Badges.known(raw)) return Badges.colors(raw);
        if (crest != null && "chromatic".equals(crest.dominant)) {
            float[] hsv = {(float) crest.hue, 0.78f, 0.72f};
            return new int[]{android.graphics.Color.HSVToColor(hsv), 0xFFFFFFFF};
        }
        return new int[]{crest != null && "light".equals(crest.dominant) ? 0xFFFFFFFF : 0xFF111111, 0xFFFFFFFF};
    }

    /** A 960x540 card for the club's match in the given style (falling back to Kit where the style can't draw it). */
    static Bitmap draw(Context c, Teams.Club k, Match m, boolean stale, String styleId, boolean showScores, long now) {
        Ui.fonts(c);
        CardModel model = CardModel.of(m, showScores, stale, now);
        CardArt art = new CardArt(c, model, k);
        ClubTokens tokens = tokens(k, model, art);
        CardStyle style = get(styleId);
        if (!style.suits(tokens)) style = get(Appearance.KIT);
        Bitmap b = Bitmap.createBitmap(Cards.W, Cards.H, Bitmap.Config.ARGB_8888);
        try {
            style.draw(c, new Canvas(b), model, tokens, art);
        } finally {
            art.release();
        }
        return b;
    }

    /** Today's card (1.1), kept as the reference until every style is drawn. */
    static final class LegacyStyle implements CardStyle {
        static final String ID = "legacy";
        public String id() { return ID; }
        public boolean suits(ClubTokens t) { return true; }
        public void draw(Context c, Canvas cv, CardModel m, ClubTokens t, CardArt art) {
            Bitmap b = Cards.draw(c, m.match, m.stale, true);
            cv.drawBitmap(b, 0, 0, null);
            b.recycle();
        }
    }
}
