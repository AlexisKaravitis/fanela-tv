package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** The viewer's appearance choices: the home-screen card style, the app layout, and whether scores show on the TV. */
final class Appearance {
    private Appearance() { }

    /** Card styles. Signature is the default; Kit is also every style's fallback for a club without a sharp crest. */
    static final String SIGNATURE = "signature", CLEAN = "clean", KIT = "kit", TICKET = "ticket", CLASH = "clash";
    static final String[] STYLES = {SIGNATURE, CLEAN, KIT, TICKET, CLASH};

    /** App layouts. */
    static final String CLUB = "club", MATCHDAY = "matchday";
    static final String[] LAYOUTS = {CLUB, MATCHDAY};

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    static String cardStyle(Context c) { return valid(sp(c).getString("card_style", SIGNATURE), STYLES, SIGNATURE); }
    static void setCardStyle(Context c, String s) { sp(c).edit().putString("card_style", valid(s, STYLES, SIGNATURE)).apply(); }

    static String layout(Context c) { return valid(sp(c).getString("app_layout", CLUB), LAYOUTS, CLUB); }
    static void setLayout(Context c, String s) { sp(c).edit().putString("app_layout", valid(s, LAYOUTS, CLUB)).apply(); }

    /** Scores on the TV's home screen (results and live). Off: the cards say LIVE / FINAL without numbers. */
    static boolean showScores(Context c) { return sp(c).getBoolean("show_scores", true); }
    static void setShowScores(Context c, boolean on) { sp(c).edit().putBoolean("show_scores", on).apply(); }

    private static String valid(String v, String[] all, String dflt) {
        for (String a : all) if (a.equals(v)) return v;
        return dflt;
    }
}
