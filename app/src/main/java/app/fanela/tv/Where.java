package app.fanela.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the where-to-watch sources for the viewer's country.
 *  Greece: the Greek guides and rights table (GreeceProvider), unchanged, then the season table for competitions the
 *          rights table doesn't cover (a Greek viewer following Arsenal).
 *  Elsewhere: Ronin's per-match listings when a key is set, then the TV's own guide when switched on (debug, with
 *          READ_TV_LISTINGS), then the season table ("Usually on Sky Sports"). Listings are applied in that order, so
 *          the TV's own guide, the viewer's real channels, has the last word.
 * The country is per thread like the language (load() at the start of every pass; use() for checks that must not touch
 * the setting).
 */
final class Where {
    private static volatile String global = "GR";
    private static final ThreadLocal<String> COUNTRY = new ThreadLocal<String>() {
        @Override protected String initialValue() { return global; }
    };

    private Where() { }

    static void load(Context c) {
        String cc = Leagues.country(c);
        global = cc;
        COUNTRY.set(cc);
        I18n.region(cc);
        SeasonTableProvider.load(c);
    }

    static void use(String country) { COUNTRY.set(country); I18n.region(country); }

    static String country() { return COUNTRY.get(); }

    static boolean greek(String country) { return "GR".equals(country); }

    /** Provider ids in the order they are asked (pure, for tests). */
    static List<String> chain(String country, boolean ronin, boolean tvGuide) {
        List<String> out = new ArrayList<>();
        if (greek(country)) out.add("gr");
        else {
            if (ronin) out.add("ronin");
            if (tvGuide) out.add("tv");
        }
        out.add("table");
        return out;
    }

    static List<String> chain(Context c, String country) { return chain(country, RoninProvider.enabled(), TvGuideProvider.enabled(c)); }

    static List<Watch> providers(Context c, String country) {
        List<Watch> out = new ArrayList<>();
        for (String id : chain(c, country)) {
            if ("gr".equals(id)) out.add(new GreeceProvider());
            else if ("ronin".equals(id)) out.add(new RoninProvider(country));
            else if ("tv".equals(id)) out.add(new TvGuideProvider());
            else out.add(new SeasonTableProvider(country));
        }
        return out;
    }

    static GreeceProvider greece(List<Watch> ws) {
        for (Watch w : ws) if (w instanceof GreeceProvider) return (GreeceProvider) w;
        return null;
    }

    /** The expected channel, set while a feed is parsed (no network). */
    static void guess(Match m) {
        String cc = country();
        if (!greek(cc)) {  // nothing from the Greek rights table outside Greece
            m.channelSure = false; m.channelKnown = false; m.channel = ""; m.channelApp = null; m.channelNo = 0;
            m.channelLogo = null; m.listedBy = "none";  // until a source below knows it: "check your provider's guide"
        }
        if (greek(cc) && new GreeceProvider().guess(m)) return;
        new SeasonTableProvider(cc).guess(m);  // Ronin has nothing offline: it confirms later, in apply()
    }

    /**
     * After a country change: forget the channel worked out for the old country (kept, live or not refetched matches
     * included) and guess again for the current one. Listings for the new country are applied afterwards.
     */
    static void reguess(Match m) {
        m.channelSure = false; m.channelKnown = false; m.channel = ""; m.channelApp = null; m.channelNo = 0;
        m.channelLogo = null; m.listedBy = "";
        guess(m);
    }

    /** The channel came from somewhere other than the Greek guides and rights table: shown as plain names. */
    static boolean plain(Match m) { return m.listedBy != null && !m.listedBy.isEmpty(); }

    /**
     * Outside Greece, nothing Fanela reads knows this match's channel (no season-table row for the country and
     * competition, no listing): say so plainly instead of promising an announcement.
     */
    static boolean noData(Match m) { return !m.channelKnown && "none".equals(m.listedBy); }
}
