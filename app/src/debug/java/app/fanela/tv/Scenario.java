package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Debug only: the home screen in a fixed state, so every state of SPEC-APP can be screenshotted and compared with the
 * reference renders. The scenario is the spec's: Olympiacos (favourite) and PAOK followed, Olympiacos–Dubai tomorrow
 * at 21:15 on Novasports Prime 318, their real fixtures after it, and the clock at 18:30 today. Modes:
 *   home, live (Olympiacos–Dubai 61–58, Q3 5:12, at 22:20 tomorrow), loading, offline (saved at 17:55), empty,
 *   many (five clubs: AEK, Panathinaikos and Aris added, with no fixtures in the scenario), failed (the same five,
 *   the three added ones never loaded: "Couldn't load fixtures"), ticking (live, but the score and the clock move
 *   with the real clock: a new state at every load, every 30 s, to watch live updates).
 * Set with DebugReceiver SCENARIO (--es name <mode>|off [--es lang el]); "off" gives the real data back. While one is
 * on, HomeActivity reads it instead of the saved matches and runs no refresh. Release builds have a stub (src/release).
 */
final class Scenario {
    private Scenario() { }

    static final class Snapshot {
        List<Match> matches;
        List<String> follow;
        String favourite, mode, lang;
        long now, savedAt;
    }

    static final String[] MODES = {"home", "live", "loading", "offline", "empty", "many", "failed", "ticking"};

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("scenario", Context.MODE_PRIVATE); }

    /**
     * Turns a mode on (lang en / el written to the app's language setting, the country set to Greece as in the spec) or
     * off ("off": the language and country from before).
     */
    static void set(Context c, String mode, String lang) {
        SharedPreferences settings = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (mode == null || !Arrays.asList(MODES).contains(mode)) {
            String was = sp(c).getString("lang_before", null), cc = sp(c).getString("country_before", null);
            if (was != null) settings.edit().putString("lang", was).commit();
            if (cc != null) settings.edit().putString("country", cc).commit();
            sp(c).edit().clear().commit();
            return;
        }
        if (!sp(c).contains("lang_before")) sp(c).edit().putString("lang_before", settings.getString("lang", "en"))
            .putString("country_before", settings.getString("country", "GR")).commit();
        settings.edit().putString("lang", "el".equals(lang) ? "el" : "en").putString("country", "GR").commit();
        sp(c).edit().putString("mode", mode).commit();
    }

    /**
     * After I18n.load: a scenario reads in 24-hour time like the references, whatever the emulator's clock format
     * (I18n follows the TV's setting; the setting itself is left alone).
     */
    static void apply(Context c) {
        if (sp(c).getString("mode", null) == null) return;
        try {
            java.lang.reflect.Field f = I18n.class.getDeclaredField("h24");
            f.setAccessible(true);
            f.setBoolean(null, true);
        } catch (Exception ignored) { }
    }

    static Snapshot active(Context c) {
        String mode = sp(c).getString("mode", null);
        if (mode == null) return null;
        Snapshot s = new Snapshot();
        s.mode = mode;
        s.lang = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "en");
        s.favourite = "435";
        s.follow = "empty".equals(mode) ? Collections.<String>emptyList()
            : "many".equals(mode) || "failed".equals(mode) ? Arrays.asList("435", "605", "887", "443", "11553") : Arrays.asList("435", "605");
        boolean ticking = "ticking".equals(mode), live = ticking || "live".equals(mode);
        s.now = live ? at(1, 22, 20) : at(0, 18, 30);
        s.savedAt = at(0, 17, 55);
        s.matches = matches(live, s.now);
        if (ticking) tick(s.matches, System.currentTimeMillis());
        return s;
    }

    /**
     * The ticking mode: the live match's score and clock from the real clock (a new state every 30 s), and every other
     * 30 s PAOK has one fixture more (its fixture list is rebuilt while it may hold the focus).
     */
    static void tick(List<Match> all, long wall) {
        int k = (int) ((wall / 30000L) % 40);
        long left = 600 - (wall / 1000L) % 600;
        for (Match m : all) if ("in".equals(m.state)) {
            m.hs = String.valueOf(61 + k);
            m.as = String.valueOf(58 + k / 2 + (k % 3 == 0 ? 1 : 0));
            m.detail = "Q3 " + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60;
        }
        if (k % 2 == 1) all.add(ch(m("605", "FC", "gre.1", "Volos", "PAOK", false, VOLOS, PAOK_FC, at(18, 17, 0)), "Novasports", 0, false));
    }

    static final String VOLOS = "https://a.espncdn.com/i/teamlogos/soccer/500/11745.png";

    /** Today's date plus days, at hh:mm (today = Wednesday 11 November in the spec). */
    static long at(int days, int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static final String OLY_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/435.png",
        OLY_BC = "https://media-cdn.incrowdsports.com/789423ac-3cdf-4b89-b11c-b458aa5f59a6.png",
        DUBAI = "https://media-cdn.incrowdsports.com/1efae090-16e2-4963-ae47-4b94f249c244.png",
        ZVEZDA = "https://media-cdn.incrowdsports.com/d2eef4a8-62df-4fdd-9076-276004268515.png",
        PAOK_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/605.png",
        PAOK_BC = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/0506e7c4-ec4b-460a-bc59-b033ad9e336c.png",
        PAOK_GBL = "https://r2.thesportsdb.com/images/media/team/badge/xojprw1792765762.png",
        KARDITSA = "https://r2.thesportsdb.com/images/media/team/badge/jbnxms1757948183.png",
        BALKAN = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjCgOCpC/ba6e2d99-3ea9-4730-a0ce-830cccca4657.png",
        ARIS_BC = "https://img.sofascore.com/api/v1/team/3505/image",
        MACCABI = "https://media-cdn.cortextech.io/1b533342-78f5-4932-b714-a7d80b5826b5.png",
        REAL = "https://media-cdn.incrowdsports.com/371b0d9b-9250-4c09-bda7-0686cf024657.png",
        IRAKLIS = "https://a.espncdn.com/i/teamlogos/soccer/500/2575.png",
        HAPOEL = "https://media-cdn.incrowdsports.com/cbb1c3ad-03d5-426a-b5ef-2832a4eee484.png",
        MILAN = "https://a.espncdn.com/i/teamlogos/soccer/500/103.png",
        ARIS_FC = "https://a.espncdn.com/i/teamlogos/soccer/500/11553.png",
        KIFISIA = "https://a.espncdn.com/i/teamlogos/soccer/500/21969.png",
        BARCA = "https://media-cdn.incrowdsports.com/35dfa503-e417-481f-963a-bdf6f013763e.png",
        FENER = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjCiezrk/aa39750a-6203-49ee-a87d-edd0c6f0397d.png",
        BESIKTAS = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/b05e3c54-e672-4b31-9f86-c91e9de7715c.png",
        MILAN_BC = "https://media-cdn.cortextech.io/1dU3kpCqReRp93/1BSdBWIjChWbHH/5c2f0ab6-f86a-4df0-b2f3-f9ab92f7267b.png";

    private static Match m(String club, String team, String league, String home, String away, boolean clubHome, String hc, String ac, long start) {
        Match m = new Match();
        m.club = club; m.team = team; m.comp = league; m.league = league; m.source = "fx";
        m.home = home; m.away = away; m.clubIsHome = clubHome; m.homeCrest = hc; m.awayCrest = ac; m.start = start;
        m.key = club + "|fx:" + team + home + away + start; m.state = "pre"; m.detail = ""; m.listedBy = "";
        m.channelKnown = false; m.channel = "";
        return m;
    }

    private static Match ch(Match m, String channel, int no, boolean sure) {
        m.channel = channel; m.channelNo = no; m.channelSure = sure; m.channelKnown = true; m.channelApp = Cards.MAGENTA;
        return m;
    }

    private static Match result(Match m, String hs, String as, int won) { m.state = "post"; m.hs = hs; m.as = as; m.clubWon = won; m.detail = "FT"; return m; }

    static List<Match> matches(boolean live, long now) {
        List<Match> out = new ArrayList<>();
        Match dubai = ch(m("435", "BC", "euroleague", "Olympiacos", "Dubai", true, OLY_BC, DUBAI, at(1, 21, 15)), "Novasports Prime", 318, true);
        if (live) { dubai.state = "in"; dubai.hs = "61"; dubai.as = "58"; dubai.detail = "Q3 5:12"; dubai.seenLiveAt = now; }
        out.add(dubai);
        out.add(ch(m("435", "BC", "gbl", "Aris", "Olympiacos", false, ARIS_BC, OLY_BC, at(4, 17, 30)), "ΣΚΑΪ", 110, true));
        out.add(ch(m("435", "BC", "euroleague", "Maccabi", "Olympiacos", false, MACCABI, OLY_BC, at(6, 19, 0)), "Novasports Prime", 318, true));
        out.add(ch(m("435", "BC", "euroleague", "Olympiacos", "Real Madrid", true, OLY_BC, REAL, at(9, 21, 15)), "Novasports", 0, false));
        out.add(ch(m("435", "FC", "gre.1", "Olympiacos", "Iraklis", true, OLY_FC, IRAKLIS, at(11, 19, 0)), "Magenta Sport", 0, false));
        out.add(ch(m("435", "BC", "euroleague", "Olympiacos", "Hapoel Tel Aviv", true, OLY_BC, HAPOEL, at(14, 21, 15)), "Novasports", 0, false));
        out.add(ch(m("435", "FC", "uefa.europa", "Olympiacos", "AC Milan", true, OLY_FC, MILAN, at(15, 19, 45)), "Magenta Sport · ANT1", 0, false));
        out.add(ch(m("435", "FC", "gre.1", "Aris", "Olympiacos", false, ARIS_FC, OLY_FC, at(18, 19, 0)), "Novasports", 0, false));
        out.add(result(m("435", "FC", "gre.1", "Olympiacos", "PAOK", true, OLY_FC, PAOK_FC, at(-3, 21, 0)), "1", "1", 0));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Crvena Zvezda", true, OLY_BC, ZVEZDA, at(-5, 21, 15)), "57", "62", -1));
        // the season's EuroLeague form before it (the match screen's last five) and the return legs (real dates)
        out.add(result(m("435", "BC", "euroleague", "FC Barcelona", "Olympiacos", false, BARCA, OLY_BC, at(-26, 21, 0)), "100", "105", 1));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Fenerbahce", true, OLY_BC, FENER, at(-19, 21, 15)), "61", "64", -1));
        out.add(result(m("435", "BC", "euroleague", "Besiktas", "Olympiacos", false, BESIKTAS, OLY_BC, at(-14, 19, 0)), "73", "81", 1));
        out.add(result(m("435", "BC", "euroleague", "Olympiacos", "Milan", true, OLY_BC, MILAN_BC, at(-12, 21, 15)), "84", "78", 1));
        out.add(ch(m("435", "BC", "euroleague", "Real Madrid", "Olympiacos", false, REAL, OLY_BC, at(57, 21, 45)), "Novasports", 0, false));
        out.add(ch(m("435", "BC", "euroleague", "Dubai", "Olympiacos", false, DUBAI, OLY_BC, at(114, 18, 0)), "Novasports", 0, false));
        // PAOK: its next game on Saturday; in the live state, a win that finished last night
        out.add(result(m("605", "FC", "gre.1", "Olympiacos", "PAOK", false, OLY_FC, PAOK_FC, at(-3, 21, 0)), "1", "1", 0));
        out.add(result(m("605", "BC", "eurocup", "Balkan", "PAOK", false, BALKAN, PAOK_BC, live ? now - 20 * 3600000L : at(-1, 15, 0)), "56", "79", 1));
        out.add(ch(m("605", "BC", "gbl", "Karditsa", "PAOK", false, KARDITSA, PAOK_GBL, at(3, 17, 30)), "ΣΚΑΪ", 110, true));
        out.add(ch(m("605", "FC", "gre.1", "PAOK", "Kifisia", true, PAOK_FC, KIFISIA, at(11, 19, 30)), "Novasports", 0, false));
        return out;
    }
}
