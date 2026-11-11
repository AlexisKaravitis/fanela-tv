package app.fanela.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The leagues whose clubs can be followed, and the viewer's country (which decides where-to-watch and which league the
 * clubs picker opens on). Football leagues are ESPN league slugs; basketball: the NBA from ESPN, EuroLeague / EuroCup
 * from the EuroLeague API. A club's fixtures in every competition (cups, UEFA) come with its own schedule.
 */
final class Leagues {
    static final String FOOTBALL = "soccer", NBA = "nba", EURO = "euro";

    static final class League {
        final String id, sport, country;  // country "" for the European cups
        League(String id, String sport, String country) { this.id = id; this.sport = sport; this.country = country; }
        String label() { return I18n.comp(id); }
        boolean basketball() { return !FOOTBALL.equals(sport); }
    }

    /** In the order the picker shows them after the viewer's own leagues and the UEFA cups: football, then basketball. */
    static final League[] ALL = {
        new League("eng.1", FOOTBALL, "GB"), new League("esp.1", FOOTBALL, "ES"), new League("ita.1", FOOTBALL, "IT"),
        new League("ger.1", FOOTBALL, "DE"), new League("fra.1", FOOTBALL, "FR"),
        new League("uefa.champions", FOOTBALL, ""), new League("uefa.europa", FOOTBALL, ""), new League("uefa.europa.conf", FOOTBALL, ""),
        new League("por.1", FOOTBALL, "PT"), new League("ned.1", FOOTBALL, "NL"), new League("tur.1", FOOTBALL, "TR"),
        new League("usa.1", FOOTBALL, "US"), new League("bra.1", FOOTBALL, "BR"), new League("arg.1", FOOTBALL, "AR"),
        new League("ksa.1", FOOTBALL, "SA"), new League("gre.1", FOOTBALL, "GR"),
        new League("nba", NBA, "US"), new League("euroleague", EURO, ""), new League("eurocup", EURO, "")};

    /** The countries offered: the ones with a where-to-watch table. */
    static final String[] COUNTRIES = {"GR", "GB", "IE", "US", "CA", "ES", "IT", "DE", "AT", "FR", "PT", "NL", "BE", "TR", "CY"};

    /** No country: the TV gave none Fanela can use. Fixtures and scores only, no TV partners. */
    static final String NONE = "ZZ";

    /** Leagues a viewer in this country most likely follows, first in the picker. */
    static List<String> home(String country) {
        switch (country == null ? "" : country) {
            case "GR": case "CY": return Arrays.asList("gre.1");
            case "GB": case "IE": return Arrays.asList("eng.1");
            case "US": return Arrays.asList("usa.1", "nba");
            case "CA": return Arrays.asList("usa.1", "nba");
            case "AT": return Arrays.asList("ger.1");
            case "BE": return Arrays.asList("ned.1", "fra.1");
            default:
                for (League l : ALL) if (!l.country.isEmpty() && l.country.equals(country) && !l.basketball()) return Arrays.asList(l.id);
                return new ArrayList<>();
        }
    }

    /**
     * Every league: the viewer's own first (the Super League straight after them when the app is in Greek), then the
     * European cups, then the rest of football, then basketball.
     */
    static List<League> ordered(String country) { return ordered(country, I18n.el()); }

    static List<League> ordered(String country, boolean greek) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : home(country)) if (!get(id).basketball()) ids.add(id);
        if (greek) ids.add("gre.1");
        for (League l : ALL) if (l.id.startsWith("uefa.")) ids.add(l.id);
        for (League l : ALL) if (!l.basketball()) ids.add(l.id);
        ids.addAll(home(country));  // the viewer's basketball league leads the basketball group
        for (League l : ALL) ids.add(l.id);
        List<League> out = new ArrayList<>();
        for (String id : ids) out.add(get(id));
        return out;
    }

    static League get(String id) {
        for (League l : ALL) if (l.id.equals(id)) return l;
        return null;
    }

    /** The league the picker opens on. */
    static String first(String country) {
        List<String> h = home(country);
        return h.isEmpty() ? "eng.1" : h.get(0);
    }

    // ---------- country ----------
    /**
     * The viewer's country (ISO 3166 code, or NONE), worked out once and saved, so only the viewer changes it
     * (Settings > Country). Someone updating from Fanela 1.0, which was Greek-only, is in Greece; a new install starts
     * from the TV's own country. Following a club never changes it.
     */
    static String country(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String s = sp.getString("country", null);
        if (s != null && !s.isEmpty()) { I18n.region(s); return s; }
        String cc = firstCountry(sp.getBoolean("onboarded", false), sp.getString("lang", null), Locale.getDefault());
        sp.edit().putString("country", cc).apply();
        I18n.region(cc);
        return cc;
    }

    /**
     * The country saved the first time. setUpBefore: the first-run setup was finished with no country saved, which
     * only an update from Fanela 1.0 leaves behind (a new install saves the country while the setup screen opens).
     */
    static String firstCountry(boolean setUpBefore, String lang, Locale tv) {
        return setUpBefore ? "GR" : defaultCountry(lang, tv);
    }

    /**
     * The TV's region first (a Greek-language TV in Cyprus is in Cyprus); with no usable region, Greece for Greek,
     * otherwise NONE, never a guess like the UK.
     */
    static String defaultCountry(String lang, Locale tv) {
        String cc = tv.getCountry() == null ? "" : tv.getCountry().toUpperCase(Locale.ROOT);
        if ("UK".equals(cc)) cc = "GB";
        if (cc.matches("[A-Z]{2}")) return cc;
        if ("el".equals(lang) || "el".equals(tv.getLanguage())) return "GR";
        return NONE;
    }

    /** A country the app has where-to-watch data for. */
    static boolean listed(String cc) { return Arrays.asList(COUNTRIES).contains(cc); }

    /** The TV's own country, as the picker pins it (null when the TV names none). */
    static String tvCountry() {
        Locale tv = Locale.getDefault();
        if (tv.getCountry() == null || tv.getCountry().isEmpty()) return null;
        String cc = defaultCountry(null, tv);
        return NONE.equals(cc) ? null : cc;
    }

    static void setCountry(Context c, String cc) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("country", cc).commit();
    }

    /** "United Kingdom" / "Ηνωμένο Βασίλειο". */
    static String countryName(String cc) {
        if (NONE.equals(cc)) return I18n.t("no_country");
        String n = new Locale("", cc).getDisplayCountry(I18n.locale());
        return n == null || n.isEmpty() || n.equals(cc) ? cc : n;
    }

    /**
     * The countries offered in Settings: the TV's own first (when it has one), then the rest by name; a current
     * country outside the list stays on offer, so it can be kept.
     */
    static List<String> countries(String current, String tvCountry) {
        Set<String> s = new LinkedHashSet<>(Arrays.asList(COUNTRIES));
        if (current != null && !NONE.equals(current)) s.add(current);
        if (tvCountry != null) s.remove(tvCountry);
        List<String> out = new ArrayList<>(s);
        final java.text.Collator col = java.text.Collator.getInstance(I18n.locale());
        java.util.Collections.sort(out, new java.util.Comparator<String>() {
            public int compare(String a, String b) { return col.compare(countryName(a), countryName(b)); }
        });
        if (tvCountry != null && !NONE.equals(tvCountry)) out.add(0, tvCountry);
        return out;
    }
}
