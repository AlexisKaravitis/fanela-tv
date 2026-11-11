package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Debug only: the card state matrix of the 1.2 spec, as synthetic matches with real names, crests and channels, so
 * every style can be drawn in every state on a device and compared with the reference renders (DebugReceiver MATRIX).
 * Dates are relative to the device's clock, so "tomorrow" and "in 2 h" stay true whenever it runs.
 */
final class Fixtures {
    private Fixtures() { }

    static final String OLY_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/435.png",
        OLY_BC = "https://media-cdn.incrowdsports.com/789423ac-3cdf-4b89-b11c-b458aa5f59a6.png",
        DUBAI = "https://media-cdn.incrowdsports.com/1efae090-16e2-4963-ae47-4b94f249c244.png",
        IRAKLIS = "https://a.espncdn.com/i/teamlogos/soccer/500/2575.png",
        ZVEZDA = "https://media-cdn.incrowdsports.com/d2eef4a8-62df-4fdd-9076-276004268515.png",
        PAOK_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/605.png",
        PAOK_BC = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/0506e7c4-ec4b-460a-bc59-b033ad9e336c.png",
        KIFISIA = "https://a.espncdn.com/i/teamlogos/soccer/500/21969.png",
        BALKAN = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjCgOCpC/ba6e2d99-3ea9-4730-a0ce-830cccca4657.png",
        AEK = "https://a.espncdn.com/i/teamlogos/soccer/500/887.png",
        PAO_BC = "https://media-cdn.incrowdsports.com/e3dff28a-9ec6-4faf-9d96-ecbc68f75780.png",
        REAL = "https://a.espncdn.com/i/teamlogos/soccer/500/86.png",
        MILAN = "https://a.espncdn.com/i/teamlogos/soccer/500/103.png",
        FRANKFURT = "https://a.espncdn.com/i/teamlogos/soccer/500/125.png",
        ARIS = "https://a.espncdn.com/i/teamlogos/soccer/500/11553.png",
        PAO = "https://a.espncdn.com/i/teamlogos/soccer/500/443.png",
        OFI = "https://a.espncdn.com/i/teamlogos/soccer/500/1010.png",
        SPURS = "https://a.espncdn.com/i/teamlogos/soccer/500/367.png";

    static final class Fx {
        final String name, club; final Match m;
        /** Drawn as not updated for a while (the warning in place of the competition). */
        boolean stale;
        Fx(String name, String club, Match m) { this.name = name; this.club = club; this.m = m; }
        Fx stale() { stale = true; return this; }
    }

    static long at(int dayOffset, int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static Match m(String club, String team, String comp, String home, String away, boolean clubHome, String hc, String ac) {
        Match m = new Match();
        m.club = club; m.team = team; m.comp = comp; m.league = comp; m.source = "fx";
        m.home = home; m.away = away; m.clubIsHome = clubHome; m.homeCrest = hc; m.awayCrest = ac;
        m.key = club + "|fx:" + home + away; m.state = "pre"; m.detail = ""; m.listedBy = "";
        return m;
    }

    static Match channel(Match m, String ch, int no, boolean sure, String app) {
        m.channel = ch; m.channelNo = no; m.channelSure = sure; m.channelKnown = true; m.channelApp = app; return m;
    }

    static List<Fx> all(long now) {
        List<Fx> out = new ArrayList<>();
        Match x;
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = at(1, 21, 15); out.add(new Fx("next", "435", x));
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = now + 2 * 3600000L + 5 * 60000L; out.add(new Fx("today", "435", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now - 70 * 60000L; x.state = "in"; x.hs = "2"; x.as = "0"; x.detail = "67'"; out.add(new Fx("live_fc", "435", x));
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = now - 75 * 60000L; x.state = "in"; x.hs = "61"; x.as = "58"; x.detail = "Q3 5:12"; out.add(new Fx("live_bc", "435", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now - 50 * 60000L; x.state = "in"; x.hs = "1"; x.as = "0"; x.detail = "HT"; out.add(new Fx("halftime", "435", x));
        x = m("435", "BC", "euroleague", "Olympiacos", "Crvena Zvezda", true, OLY_BC, ZVEZDA);
        x.start = at(-5, 21, 15); x.state = "post"; x.hs = "57"; x.as = "62"; x.clubWon = -1; out.add(new Fx("result_loss", "435", x));
        x = m("435", "FC", "gre.1", "Olympiacos", "PAOK", true, OLY_FC, PAOK_FC);
        x.start = at(-3, 19, 0); x.state = "post"; x.hs = "1"; x.as = "1"; x.clubWon = 0; out.add(new Fx("result_draw", "435", x));
        x = m("605", "BC", "eurocup", "Balkan", "PAOK", false, BALKAN, PAOK_BC);
        x.start = at(-1, 19, 0); x.state = "post"; x.hs = "56"; x.as = "79"; x.clubWon = 1; out.add(new Fx("result_win_away", "605", x));
        // PAOK's row as the launcher shows it (owner, fx6): an away kick-off and an away draw beside the home cards
        x = channel(m("605", "FC", "gre.1", "Aris", "PAOK", false, ARIS, PAOK_FC), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(3, 17, 30); out.add(new Fx("paok_away_next", "605", x));
        x = m("605", "FC", "gre.1", "Olympiacos", "PAOK", false, OLY_FC, PAOK_FC);
        x.start = at(-3, 19, 0); x.state = "post"; x.hs = "1"; x.as = "1"; x.clubWon = 0; out.add(new Fx("paok_away_draw", "605", x));
        x = channel(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(11, 19, 30); out.add(new Fx("next_paok_likely", "605", x));
        x = channel(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(11, 19, 30); x.state = "off"; x.detail = "POSTPONED"; out.add(new Fx("postponed", "605", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "AEK Athens", true, OLY_FC, AEK), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = at(24, 12, 0); x.tbd = true; out.add(new Fx("time_tba", "435", x));
        x = channel(m("86", "FC", "uefa.champions", "Real Madrid", "AC Milan", true, REAL, MILAN), "TNT Sports · Prime Video", 0, false, null);
        x.listedBy = "table"; x.start = at(13, 22, 0); out.add(new Fx("abroad", "86", x));
        x = channel(m("435", "BC", "euroleague", "Panathinaikos", "Olympiacos", false, PAO_BC, OLY_BC), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = at(30, 21, 15); out.add(new Fx("away_derby", "435", x));
        x = channel(m("443", "BC", "euroleague", "Panathinaikos", "Crvena Zvezda", true, PAO_BC, ZVEZDA), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(17, 21, 15); out.add(new Fx("long_names", "443", x));
        x = channel(m("zvezda", "BC", "euroleague", "Crvena Zvezda", "Partizan", true, ZVEZDA, PAO_BC), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(18, 20, 0); out.add(new Fx("fallback", "zvezda", x));
        // long opponents: a name with a short form (Partizan Belgrade -> Partizan, in both languages), and Hapoel Tel Aviv
        x = channel(m("605", "FC", "club.friendly", "PAOK", "Partizan Belgrade", true, PAOK_FC, PAO_BC), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(3, 17, 45); out.add(new Fx("long_partizan", "605", x));
        x = channel(m("435", "BC", "euroleague", "Hapoel Tel Aviv", "Olympiacos", false, ZVEZDA, OLY_BC), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = at(8, 20, 30); out.add(new Fx("long_hapoel", "435", x));
        // robustness: three-digit and overtime basketball, extra time and penalties, a very long name, an away lead
        x = m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI);
        x.start = at(-1, 21, 15); x.state = "post"; x.hs = "101"; x.as = "98"; x.clubWon = 1; out.add(new Fx("bc_101_98", "435", x));
        x = m("435", "BC", "euroleague", "Olympiacos", "Crvena Zvezda", true, OLY_BC, ZVEZDA);
        x.start = at(-4, 20, 30); x.state = "post"; x.hs = "100"; x.as = "105"; x.clubWon = -1; out.add(new Fx("bc_100_105", "435", x));
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = now - 140 * 60000L; x.state = "in"; x.hs = "99"; x.as = "99"; x.detail = "OT 2:10"; out.add(new Fx("live_ot", "435", x));
        x = m("435", "FC", "gre.cup", "Olympiacos", "PAOK", true, OLY_FC, PAOK_FC);
        x.start = at(-2, 20, 0); x.state = "post"; x.hs = "2"; x.as = "1"; x.detail = "FT_AET"; x.clubWon = 1; out.add(new Fx("result_aet", "435", x));
        x = m("435", "FC", "gre.cup", "AEK Athens", "Olympiacos", false, AEK, OLY_FC);
        x.start = at(-6, 20, 0); x.state = "post"; x.hs = "1"; x.as = "1"; x.pens = "3–4"; x.detail = "FT_PEN"; x.clubWon = 1;
        out.add(new Fx("result_pens", "435", x));
        x = channel(m("435", "FC", "uefa.europa", "Olympiacos", "Eintracht Frankfurt", true, OLY_FC, FRANKFURT), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = at(9, 22, 0); out.add(new Fx("long_greek", "435", x));
        x = channel(m("435", "FC", "gre.1", "Aris", "Olympiacos", false, ARIS, OLY_FC), "Novasports Prime", 0, false, Cards.MAGENTA);
        x.start = now - 85 * 60000L; x.state = "in"; x.hs = "1"; x.as = "3"; x.detail = "78'"; out.add(new Fx("live_away_lead", "435", x));

        // edge states: past the usual end without a result, live without a score, put back, stopped, abandoned
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now - 150 * 60000L; x.state = "in"; x.detail = "AWAITING"; out.add(new Fx("awaiting", "435", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now - 20 * 60000L; x.state = "in"; x.detail = "IN_PROGRESS"; out.add(new Fx("live_noscore", "435", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now + 15 * 60000L; x.detail = "DELAYED"; out.add(new Fx("delayed", "435", x));
        x = channel(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = now - 80 * 60000L; x.state = "in"; x.hs = "1"; x.as = "0"; x.detail = "SUSPENDED"; out.add(new Fx("suspended", "435", x));
        x = channel(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA), "Novasports", 0, false, Cards.MAGENTA);
        x.start = now - 4 * 3600000L; x.state = "off"; x.detail = "ABANDONED"; out.add(new Fx("abandoned", "605", x));
        x = channel(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA), "Novasports", 0, false, Cards.MAGENTA);
        x.start = now - 4 * 3600000L; x.state = "off"; x.hs = "0"; x.as = "1"; x.detail = "ABANDONED";
        out.add(new Fx("abandoned_score", "605", x));
        // extra time and penalties as the football feed codes them; basketball between quarters and in a second overtime
        String[][] et = {{"live_et_ht", "ET_HT", "1", "1"}, {"live_end90", "END_90", "1", "1"}, {"live_end120", "END_120", "2", "2"},
            {"live_shootout", "SHOOTOUT", "2", "2"}};
        for (String[] e : et) {
            x = channel(m("435", "FC", "gre.cup", "Olympiacos", "PAOK", true, OLY_FC, PAOK_FC), "ΣΠΟΡ FM TV", 0, false, Cards.SPORTFM);
            x.start = now - 115 * 60000L; x.state = "in"; x.hs = e[2]; x.as = e[3]; x.detail = e[1]; out.add(new Fx(e[0], "435", x));
        }
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = now - 60 * 60000L; x.state = "in"; x.hs = "45"; x.as = "41"; x.detail = "BREAK"; out.add(new Fx("live_bc_break", "435", x));
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = now - 150 * 60000L; x.state = "in"; x.hs = "110"; x.as = "108"; x.detail = "OT2 1:00"; out.add(new Fx("live_ot2", "435", x));
        // a yellow kit (Aris), and the not-updated warning on a yellow ticket stock (AEK)
        x = channel(m("11553", "FC", "gre.1", "Aris", "PAOK", true, ARIS, PAOK_FC), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(1, 19, 30); out.add(new Fx("aris_next", "11553", x));
        x = channel(m("887", "FC", "gre.1", "AEK Athens", "Panathinaikos", true, AEK, PAO), "Magenta Sport", 0, false, Cards.MAGENTA);
        x.start = at(2, 21, 0); out.add(new Fx("aek_stale", "887", x).stale());
        // two channels, one of them free-to-air (the Champions League in Greece); abroad, a guide's confirmed channel
        // with its number, and a confirmed listing without one (names joined by "/", not "or")
        x = channel(m("435", "FC", "uefa.champions", "Olympiacos", "Real Madrid", true, OLY_FC, REAL), "Magenta Sport · MEGA", 0, false, Cards.MAGENTA);
        x.start = at(6, 22, 0); out.add(new Fx("two_channels", "435", x));
        x = channel(m("86", "FC", "uefa.champions", "Real Madrid", "AC Milan", true, REAL, MILAN), "Sky Sports Main Event", 401, true, null);
        x.listedBy = "tv"; x.start = at(13, 20, 0); out.add(new Fx("abroad_tv_401", "86", x));
        x = channel(m("86", "FC", "uefa.champions", "Real Madrid", "AC Milan", true, REAL, MILAN), "Sky Sports · NOW", 0, true, null);
        x.listedBy = "ronin"; x.start = at(13, 20, 0); out.add(new Fx("abroad_confirmed", "86", x));
        // a near-square logo (ERT's, about 1.5:1), confirmed with its number: set in type on every style
        x = channel(m("605", "FC", "gre.cup", "PAOK", "Aris", true, PAOK_FC, ARIS), "ΕΡΤ1", 0, true, Cards.MAGENTA);
        x.start = at(2, 18, 0); out.add(new Fx("ert", "605", x));
        // same-colour clashes (Clash's fields): red v red upcoming, black v black, white v white
        x = channel(m("435", "BC", "euroleague", "Olympiacos", "Crvena Zvezda", true, OLY_BC, ZVEZDA), "Novasports Prime", 318, true, Cards.MAGENTA);
        x.start = at(4, 20, 30); out.add(new Fx("clash_red", "435", x));
        x = channel(m("605", "FC", "gre.1", "PAOK", "OFI Crete", true, PAOK_FC, OFI), "Novasports", 0, false, Cards.MAGENTA);
        x.start = at(5, 17, 0); out.add(new Fx("clash_black", "605", x));
        x = m("86", "FC", "uefa.champions", "Tottenham", "Real Madrid", false, SPURS, REAL);
        x.start = at(-2, 22, 0); x.state = "post"; x.hs = "1"; x.as = "3"; x.clubWon = 1; out.add(new Fx("clash_white", "86", x));
        return out;
    }

    /** The club a fixture's card is for, with its own crest (ESPN, 500 px) when the catalogue gives none. */
    static Teams.Club club(Context c, Fx f) {
        Teams.Club k = Teams.get(c, f.club);
        if (k == null) { k = new Teams.Club(); k.id = f.club; k.name = f.m.clubHome() ? f.m.home : f.m.away; k.gr = k.name; }
        // the club's own crest beside the match's: Olympiacos basketball's EuroLeague crest is 192 px, so its cards
        // exercise the ghost's "club's best crest" (Crvena Zvezda has none here: it still falls back)
        if ((k.crest == null || k.crest.isEmpty()) && f.club.matches("[0-9]+")) k.crest = Teams.crestUrl(f.club);
        return k;
    }

    /**
     * Fetches every image the matrix draws (both crests, every channel logo, each club's own crest), so a device that
     * has never shown these clubs draws the real crests rather than badges, and Signature isn't pushed to Kit.
     */
    static void prefetch(Context c) {
        I18n.load(c);
        Channels.load(c);
        List<Match> ms = new ArrayList<>();
        for (Fx f : all(System.currentTimeMillis())) {
            ms.add(f.m);
            Net.fetchImage(c, club(c, f).crest);
        }
        Engine.prefetchImages(c, ms, new java.util.HashSet<String>());
        for (Match m : ms) for (Channels.Logo l : Channels.logos(c, m)) Net.fetchImage(c, l.url);  // past matches' too
    }

    /** Renders every fixture in one style and language into files/matrix/<lang>/<style>/<name>.png, then DONE. */
    static void render(Context c, String style, String lang, boolean scores) throws Exception {
        I18n.load(c);
        I18n.use(lang);
        File dir = new File(c.getFilesDir(), "matrix/" + lang + "/" + style + (scores ? "" : "_hidden"));
        dir.mkdirs();
        File[] old = dir.listFiles();
        for (int i = 0; old != null && i < old.length; i++) old[i].delete();
        long now = System.currentTimeMillis();
        for (Fx f : all(now)) {
            Teams.Club k = club(c, f);
            long t0 = System.nanoTime();
            StringBuilder log = new StringBuilder();
            Bitmap b = probed(c, k, f.m, f.stale, style, scores, now, log);
            long ms = (System.nanoTime() - t0) / 1000000;
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, f.name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, f.name + ".tsv"))) { o.write(log.toString().getBytes("UTF-8")); }
            b.recycle();
            android.util.Log.i("Fanela", "matrix " + lang + "/" + style + "/" + f.name + " " + ms + " ms");
        }
        new File(dir, "DONE").createNewFile();
    }

    /**
     * CardStyles.draw, the same card, drawn through a Probe that writes every element's box (card px) to log: one
     * line a draw call, "kind  left top right bottom  alpha  shader  text", for the layout audit (design/qa).
     */
    static Bitmap probed(Context c, Teams.Club k, Match m, boolean stale, String styleId, boolean showScores, long now, StringBuilder log) {
        Ui.fonts(c);
        CardModel model = CardModel.of(m, showScores, stale, now);
        CardArt art = new CardArt(c, model, k);
        ClubTokens tokens = CardStyles.tokens(k, model, art);
        CardStyle st = CardStyles.get(styleId);
        if (!st.suits(tokens)) st = CardStyles.get(Appearance.KIT);
        log.append("STYLE\t").append(st.id()).append('\n');
        Bitmap b = Bitmap.createBitmap(Cards.W, Cards.H, Bitmap.Config.ARGB_8888);
        try {
            st.draw(c, new Probe(b, log), model, tokens, art);
        } finally {
            art.release();
        }
        return b;
    }

    /** A canvas that draws as usual and logs each element's bounds, clipped to the canvas's clip at the time. */
    static final class Probe extends android.graphics.Canvas {
        private final StringBuilder log;
        Probe(Bitmap b, StringBuilder log) { super(b); this.log = log; }

        private void rec(String kind, float l, float t, float r, float bo, android.graphics.Paint p, String text) {
            android.graphics.Rect clip = new android.graphics.Rect();
            if (getClipBounds(clip)) { l = Math.max(l, clip.left); t = Math.max(t, clip.top); r = Math.min(r, clip.right); bo = Math.min(bo, clip.bottom); }
            if (r <= l || bo <= t) return;
            log.append(kind).append('\t').append(Math.round(l)).append('\t').append(Math.round(t)).append('\t')
                .append(Math.round(r)).append('\t').append(Math.round(bo)).append('\t')
                .append(p == null ? 255 : p.getAlpha()).append('\t').append(p != null && p.getShader() != null ? 1 : 0).append('\t')
                .append(p == null ? 0 : Math.round(p.getTextSize())).append('\t')
                .append(text == null ? "" : text.replace('\t', ' ').replace('\n', ' ')).append('\n');
        }

        @Override public void drawText(String s, float x, float y, android.graphics.Paint p) {
            super.drawText(s, x, y, p);
            android.graphics.Rect r = new android.graphics.Rect();
            p.getTextBounds(s, 0, s.length(), r);
            float w = p.measureText(s), dx = p.getTextAlign() == android.graphics.Paint.Align.CENTER ? -w / 2 : p.getTextAlign() == android.graphics.Paint.Align.RIGHT ? -w : 0;
            rec("T", x + dx + r.left, y + r.top, x + dx + r.right, y + r.bottom, p, s);
        }
        @Override public void drawRect(float l, float t, float r, float b, android.graphics.Paint p) { super.drawRect(l, t, r, b, p); rec("R", l, t, r, b, p, null); }
        @Override public void drawRect(android.graphics.RectF r, android.graphics.Paint p) { super.drawRect(r, p); rec("R", r.left, r.top, r.right, r.bottom, p, null); }
        @Override public void drawRoundRect(android.graphics.RectF r, float rx, float ry, android.graphics.Paint p) { super.drawRoundRect(r, rx, ry, p); rec("RR", r.left, r.top, r.right, r.bottom, p, null); }
        @Override public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, android.graphics.Paint p) { super.drawRoundRect(l, t, r, b, rx, ry, p); rec("RR", l, t, r, b, p, null); }
        @Override public void drawCircle(float cx, float cy, float rad, android.graphics.Paint p) { super.drawCircle(cx, cy, rad, p); rec("C", cx - rad, cy - rad, cx + rad, cy + rad, p, null); }
        @Override public void drawOval(android.graphics.RectF r, android.graphics.Paint p) { super.drawOval(r, p); rec("C", r.left, r.top, r.right, r.bottom, p, null); }
        @Override public void drawPath(android.graphics.Path path, android.graphics.Paint p) {
            super.drawPath(path, p);
            android.graphics.RectF r = new android.graphics.RectF();
            path.computeBounds(r, true);
            rec("P", r.left, r.top, r.right, r.bottom, p, null);
        }
        @Override public void drawBitmap(Bitmap bm, android.graphics.Rect src, android.graphics.RectF dst, android.graphics.Paint p) {
            super.drawBitmap(bm, src, dst, p);
            rec("B", dst.left, dst.top, dst.right, dst.bottom, p, bm.getWidth() + "x" + bm.getHeight());
        }
        @Override public void drawBitmap(Bitmap bm, android.graphics.Rect src, android.graphics.Rect dst, android.graphics.Paint p) {
            super.drawBitmap(bm, src, dst, p);
            rec("B", dst.left, dst.top, dst.right, dst.bottom, p, bm.getWidth() + "x" + bm.getHeight());
        }
        @Override public void drawBitmap(Bitmap bm, float l, float t, android.graphics.Paint p) {
            super.drawBitmap(bm, l, t, p);
            rec("B", l, t, l + bm.getWidth(), t + bm.getHeight(), p, bm.getWidth() + "x" + bm.getHeight());
        }
    }
}
