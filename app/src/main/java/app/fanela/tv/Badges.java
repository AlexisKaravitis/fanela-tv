package app.fanela.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Club badges drawn from the club's colours and a short name (OLY, PAOK, AEK). Used instead of crests in the Play
 * build (crests are the clubs' trademarks) and wherever a crest has not been downloaded yet.
 */
final class Badges {
    /** English name prefix (accent-free, upper case) -> {fill, text}. Prefix match, so "AEK ATHENS" uses "AEK". */
    private static final Map<String, int[]> COLORS = new LinkedHashMap<>();
    /** Short names that read better than the first letters. */
    private static final Map<String, String[]> SHORT = new LinkedHashMap<>();  // key -> {English, Greek}

    private static void c(String key, String fill, String text) { COLORS.put(key, new int[]{Color.parseColor(fill), Color.parseColor(text)}); }
    private static void s(String key, String en, String gr) { SHORT.put(key, new String[]{en, gr}); }

    static {
        // Greek football and basketball clubs
        c("OLYMPIACOS", "#D0102E", "#FFFFFF"); c("OLYMPIAKOS", "#D0102E", "#FFFFFF"); c("OLYMPIAKOS NICOSIA", "#111111", "#7FBF3F");
        c("AEK ATHENS", "#F5C800", "#111111"); c("AEK BC", "#F5C800", "#111111"); c("ARIS BC", "#FFD200", "#111111"); c("ARIS THESSALONIKI", "#FFD200", "#111111");
        c("OFI CRETE", "#111111", "#FFFFFF"); c("REAL BETIS", "#0BB363", "#FFFFFF"); c("REAL SOCIEDAD", "#0067B1", "#FFFFFF");
        c("MACCABI TEL AVIV", "#FFD400", "#0038A8"); c("MACCABI TELAVIV", "#FFD400", "#0038A8"); c("MACCABI HAIFA", "#00843D", "#FFFFFF");
        c("HAPOEL TEL AVIV", "#D2232A", "#FFFFFF"); c("HAPOEL JERUSALEM", "#D2232A", "#FFFFFF"); c("PARIS BASKETBALL", "#111111", "#FFFFFF");
        c("PARIS SAINTGERMAIN", "#004170", "#FFFFFF"); c("EA7 EMPORIO ARMANI MILAN", "#C8102E", "#FFFFFF"); c("OLIMPIA MILANO", "#C8102E", "#FFFFFF");
        c("PANATHINAIKOS", "#007A3D", "#FFFFFF"); c("PAOK", "#111111", "#FFFFFF"); c("AEK LARNACA", "#00843D", "#FFD100");
        c("AEK", "#F5C800", "#111111"); c("ARIS LIMASSOL", "#0B1F4B", "#FFD100"); c("ARIS", "#FFD200", "#111111");
        c("OFI", "#111111", "#FFFFFF"); c("ATROMITOS", "#1D5FBF", "#FFFFFF"); c("LEVADIAKOS", "#0E8A3E", "#FFFFFF");
        c("KALAMATA", "#111111", "#FFFFFF"); c("ASTERAS", "#F5C400", "#1B3D8F"); c("IRAKLIS", "#1E5BAA", "#FFFFFF");
        c("PANETOLIKOS", "#F2C500", "#1F3B88"); c("VOLOS", "#1E3A8A", "#FFFFFF"); c("KIFISIA", "#1F4E9E", "#FFFFFF");
        c("PANSERRAIKOS", "#C8102E", "#FFFFFF"); c("LAMIA", "#1C2D5A", "#FFFFFF"); c("AEL", "#7A1F2B", "#FFFFFF");
        c("LARISSA", "#7A1F2B", "#FFFFFF"); c("ATHENS KALLITHEA", "#00205B", "#FFFFFF"); c("PAS GIANNINA", "#003C8F", "#FFFFFF");
        c("PANIONIOS", "#B22234", "#003B7A"); c("MAROUSSI", "#1E4FA3", "#FFFFFF"); c("PERISTERI", "#2A2A6E", "#F2C500");
        c("PROMITHEAS", "#2B2B2B", "#F2C500"); c("KOLOSSOS", "#E3E3E3", "#1C3F94"); c("LAVRIO", "#003DA5", "#FFFFFF");
        c("KARDITSA", "#008C45", "#FFFFFF"); c("MYKONOS", "#0D3B66", "#FFFFFF"); c("APOLLON", "#1E3A8A", "#FFFFFF");
        c("APOEL", "#F2C500", "#00338D"); c("OMONIA", "#007A33", "#FFFFFF"); c("PAFOS", "#003B7A", "#FFFFFF");
        // frequent European opponents
        c("REAL MADRID", "#F4F4F4", "#2B3990"); c("REAL", "#F4F4F4", "#2B3990"); c("BARCELONA", "#A50044", "#FFD100"); c("BARCA", "#A50044", "#FFD100");
        c("BAYERN", "#DC052D", "#FFFFFF"); c("FENERBAHCE", "#13235B", "#FFED00"); c("ANADOLU EFES", "#1A3D8F", "#FFFFFF");
        c("EFES", "#1A3D8F", "#FFFFFF"); c("MACCABI", "#FFD400", "#0038A8"); c("PARTIZAN", "#111111", "#FFFFFF");
        c("CRVENA", "#D2232A", "#FFFFFF"); c("RED STAR", "#D2232A", "#FFFFFF"); c("ZVEZDA", "#D2232A", "#FFFFFF");
        c("ZALGIRIS", "#006633", "#FFFFFF"); c("MILAN", "#C8102E", "#111111"); c("AC MILAN", "#C8102E", "#111111");
        c("INTERNAZIONALE", "#0068A8", "#111111"); c("JUVENTUS", "#111111", "#FFFFFF"); c("ARSENAL", "#EF0107", "#FFFFFF");
        c("LIVERPOOL", "#C8102E", "#FFFFFF"); c("CHELSEA", "#034694", "#FFFFFF"); c("MANCHESTER CITY", "#6CABDD", "#1C2C5B");
        c("MANCHESTER UNITED", "#DA291C", "#FBE122"); c("TOTTENHAM", "#F4F4F4", "#132257"); c("BENFICA", "#E30613", "#FFFFFF");
        c("FC PORTO", "#00428C", "#FFFFFF"); c("PORTO", "#00428C", "#FFFFFF"); c("SPORTING", "#008057", "#FFFFFF");
        c("AJAX", "#F4F4F4", "#D2122E"); c("PSV", "#E4002B", "#FFFFFF"); c("GALATASARAY", "#A90432", "#FDB912");
        c("BESIKTAS", "#111111", "#FFFFFF"); c("CELTIC", "#018749", "#FFFFFF"); c("RANGERS", "#1B458F", "#FFFFFF");
        c("PARIS", "#004170", "#FFFFFF"); c("MONACO", "#E7212A", "#FFFFFF"); c("ASVEL", "#111111", "#FFFFFF");
        c("VALENCIA", "#F47A20", "#111111"); c("BASKONIA", "#1D428A", "#C8102E"); c("VIRTUS", "#111111", "#FFFFFF");
        c("DUBAI", "#111111", "#C9A227"); c("HAPOEL", "#D2232A", "#FFFFFF"); c("BORUSSIA DORTMUND", "#FDE100", "#111111");
        c("DORTMUND", "#FDE100", "#111111"); c("NAPOLI", "#12A0D7", "#FFFFFF"); c("ATLETICO", "#CB3524", "#FFFFFF");

        s("OLYMPIACOS", "OLY", "ΟΣΦΠ"); s("OLYMPIAKOS", "OLY", "ΟΣΦΠ"); s("PANATHINAIKOS", "PAO", "ΠΑΟ");
        s("PAOK", "PAOK", "ΠΑΟΚ"); s("AEK", "AEK", "ΑΕΚ"); s("AEK ATHENS", "AEK", "ΑΕΚ"); s("AEK BC", "AEK", "ΑΕΚ");
        s("ARIS", "ARIS", "ΑΡΗΣ"); s("ARIS BC", "ARIS", "ΑΡΗΣ"); s("ARIS THESSALONIKI", "ARIS", "ΑΡΗΣ"); s("OFI", "OFI", "ΟΦΗ"); s("OFI CRETE", "OFI", "ΟΦΗ");
        s("AEK LARNACA", "AEKL", "ΑΕΚΛ"); s("ARIS LIMASSOL", "ARL", "ΑΡΛ"); s("OLYMPIAKOS NICOSIA", "OLN", "ΟΛΛ");
        s("ATROMITOS", "ATR", "ΑΤΡ"); s("LEVADIAKOS", "LEV", "ΛΕΒ"); s("KALAMATA", "KAL", "ΚΑΛ"); s("ASTERAS", "AST", "ΑΣΤ");
        s("IRAKLIS", "IRA", "ΗΡΑ"); s("PANETOLIKOS", "PAN", "ΠΑΝ"); s("VOLOS", "VOL", "ΒΟΛ"); s("KIFISIA", "KIF", "ΚΗΦ");
        s("PANSERRAIKOS", "PSR", "ΠΑΝΣ"); s("AEL", "AEL", "ΑΕΛ"); s("LARISSA", "AEL", "ΑΕΛ"); s("PAS GIANNINA", "PAS", "ΠΑΣ");
        s("REAL MADRID", "RMA", "ΡΕΑΛ"); s("BARCELONA", "BAR", "ΜΠΑΡ"); s("BAYERN", "FCB", "ΜΠΑΓ");
        s("RED STAR", "CZV", "ΕΡΥ"); s("CRVENA", "CZV", "ΕΡΥ"); s("ZVEZDA", "CZV", "ΕΡΥ");
        s("MANCHESTER CITY", "MCI", "ΣΙΤΙ"); s("MANCHESTER UNITED", "MUN", "ΓΙΟΥ");
    }

    /** Fallback fills for clubs not in the table: picked by name, so a club always gets the same colour. */
    private static final int[] PALETTE = {
        0xFF2F4B7C, 0xFF6B3E99, 0xFF0F766E, 0xFF9A3412, 0xFF1E3A8A, 0xFF7C2D12, 0xFF155E75, 0xFF4D7C0F, 0xFF831843, 0xFF374151};

    /** Short keys that are also the first word of other clubs (Real Betis, AEK Larnaca, Aris Limassol): exact only. */
    private static final java.util.Set<String> EXACT = new java.util.HashSet<>(java.util.Arrays.asList("REAL", "AEK", "ARIS", "AEL", "OFI", "MACCABI", "HAPOEL", "PARIS", "MILAN"));

    private static String match(Map<String, ?> map, String key) {
        String best = null;
        for (String k : map.keySet()) {
            boolean hit = key.equals(k) || (!EXACT.contains(k) && key.startsWith(k + " "));
            if (hit && (best == null || k.length() > best.length())) best = k;
        }
        return best;
    }

    /** Accent-free English key for any source name (Greek guide names are mapped back to English first). */
    static String key(String raw) {
        return Cards.key(I18n.enName(raw == null ? "" : raw)).replaceAll("[^A-Z0-9 ]", "").trim();
    }

    /** True when the table has this team's colours (otherwise colors() picks one from the palette). */
    static boolean known(String raw) { return match(COLORS, key(raw)) != null; }

    /** {fill, text} colours for a team. */
    static int[] colors(String raw) {
        String k = key(raw);
        String m = match(COLORS, k);
        if (m != null) return COLORS.get(m);
        int h = Math.abs(k.hashCode() % PALETTE.length);
        return new int[]{PALETTE[h], Color.WHITE};
    }

    /** Short badge text in the current language: OLY / ΟΣΦΠ, PAOK / ΠΑΟΚ, RMA / ΡΕΑΛ; otherwise built from the name. */
    static String initials(String raw) {
        String k = key(raw);
        String m = match(SHORT, k);
        if (m != null) return SHORT.get(m)[I18n.el() ? 1 : 0];
        String shown = Cards.caps(I18n.team(raw)).replaceAll("[^\\p{L}0-9 ]", " ").trim();
        if (shown.isEmpty()) return "?";
        String[] w = shown.split("\\s+");
        if (w[0].length() >= 2 && w[0].length() <= 4 && raw.trim().startsWith(raw.trim().split("\\s+")[0].toUpperCase(Locale.ROOT))) return w[0];
        if (w.length >= 2 && w[1].length() > 1) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(3, w.length); i++) if (!w[i].isEmpty()) b.append(w[i].charAt(0));
            return b.toString();
        }
        return w[0].length() > 3 ? w[0].substring(0, 3) : w[0];
    }

    /** Draws the badge as a disc of radius r centred on (cx, cy). */
    static void draw(Canvas cv, String raw, float cx, float cy, float r) {
        int[] col = colors(raw);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col[0]);
        p.setShadowLayer(r * 0.17f, 0, r * 0.065f, Color.argb(115, 0, 0, 0));
        cv.drawCircle(cx, cy, r, p);
        p.clearShadowLayer();
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);  // keeps dark fills visible on the dark cards
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2, r * 0.05f));
        ring.setColor(Color.luminance(col[0]) < 0.2f ? Color.argb(90, 255, 255, 255) : Color.argb(40, 0, 0, 0));
        cv.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2, ring);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);  // a thin inner line in the text colour, like a kit trim
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Math.max(1.5f, r * 0.03f));
        inner.setColor((col[1] & 0x00FFFFFF) | 0x80000000);
        cv.drawCircle(cx, cy, r * 0.82f, inner);
        String s = initials(raw);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(col[1]);
        t.setTypeface(Ui.BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        float size = r * (s.length() <= 2 ? 0.78f : s.length() == 3 ? 0.62f : 0.5f);
        t.setTextSize(size);
        while (t.measureText(s) > r * 1.36f && size > 8) t.setTextSize(size -= 1);
        Rect b = new Rect();
        t.getTextBounds(s, 0, s.length(), b);
        cv.drawText(s, cx, cy + b.height() / 2f, t);
    }
}
