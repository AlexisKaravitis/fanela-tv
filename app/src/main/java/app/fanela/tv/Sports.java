package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the viewer follows besides clubs: Formula 1 and UFC (F1-UFC-ARCHITECTURE §2.1). New keys in the existing
 * "settings" preferences, so existing users need no migration (an absent key: not followed):
 *  sports        CSV "f1,ufc", in the order of ALL
 *  f1_focus      CSV of "d:<athleteId>" / "t:<teamId>", at most 2, drivers and teams mixed
 *  ufc_scope     "all" (default) / "numbered"
 *  ufc_fighters  CSV of ESPN athlete ids
 *  sportmeta_<ref> a display name saved when chosen ("Lando Norris", "McLaren"): refs "d:<id>", "t:<id>", and "f:<id>"
 *                for a fighter, so Settings and cards can name them offline
 * The engine's timestamps live in the "sports_engine" preferences (SportEngine). In a build without
 * BuildConfig.SPORTS nothing is followed, whatever the preferences say.
 */
final class Sports {
    private Sports() { }

    static final String F1 = SportEvent.F1, UFC = SportEvent.UFC;
    static final String[] ALL = {F1, UFC};
    static final String SCOPE_ALL = "all", SCOPE_NUMBERED = "numbered";
    static final int MAX_FOCUS = 2;
    static final String ENGINE = "sports_engine";

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String x : s.split(",")) { x = x.trim(); if (!x.isEmpty() && !out.contains(x)) out.add(x); }
        return out;
    }

    static String csv(Collection<String> xs) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) { if (x == null || x.trim().isEmpty()) continue; if (b.length() > 0) b.append(','); b.append(x.trim()); }
        return b.toString();
    }

    static boolean known(String sport) { return F1.equals(sport) || UFC.equals(sport); }

    /** The followed sports, ordered as ALL; empty in a build without sports. */
    static Set<String> followed(Context c) {
        Set<String> out = new LinkedHashSet<>();
        if (!BuildConfig.SPORTS) return out;
        List<String> on = csv(sp(c).getString("sports", ""));
        for (String s : ALL) if (on.contains(s)) out.add(s);
        return out;
    }

    static boolean follows(Context c, String sport) { return followed(c).contains(sport); }

    /**
     * Follows or unfollows a sport. Following clears its engine timestamps, so a sport followed (again) is due at
     * once. Unfollowing leaves a mark ("forget_<sport>" in the engine's preferences) that the next pass honours whatever
     * else is left (SportEngine.pass: its events, card files, card preferences and side files go, then the row).
     */
    static void setFollowed(Context c, String sport, boolean on) {
        if (!known(sport)) return;
        List<String> cur = csv(sp(c).getString("sports", ""));
        Set<String> next = new LinkedHashSet<>();
        for (String s : ALL) if (s.equals(sport) ? on : cur.contains(s)) next.add(s);
        sp(c).edit().putString("sports", csv(next)).apply();
        if (on) clearTimestamps(c, sport);
        else if (cur.contains(sport)) c.getSharedPreferences(ENGINE, Context.MODE_PRIVATE).edit().putBoolean(FORGET + sport, true).apply();
    }

    /** The engine's mark of a sport unfollowed and not yet forgotten (EventStore.forgetSport removes it). */
    static final String FORGET = "forget_";

    /**
     * The engine's timestamps of one sport (lastFull_, lastCal_, res_, ok_) and its forget mark: its next pass fetches
     * everything.
     */
    static void clearTimestamps(Context c, String sport) {
        SharedPreferences e = c.getSharedPreferences(ENGINE, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = e.edit();
        for (String k : new String[]{"lastFull_", "lastCal_", "res_", "ok_", FORGET}) ed.remove(k + sport);
        ed.apply();
    }

    /** The F1 focus refs ("d:5579", "t:106892"), at most 2. */
    static List<String> f1Focus(Context c) {
        List<String> out = new ArrayList<>();
        for (String r : csv(sp(c).getString("f1_focus", ""))) if (validRef(r) && out.size() < MAX_FOCUS) out.add(r);
        return out;
    }

    static boolean validRef(String ref) {
        return ref != null && (ref.startsWith("d:") || ref.startsWith("t:")) && ref.length() > 2 && ref.indexOf(',') < 0;
    }

    /**
     * Sets the F1 focus. False, and nothing changed, when more than 2 refs are given or one is not "d:"/"t:".
     * names: display names by ref (may be null or partial), saved as sportmeta_<ref>.
     */
    static boolean setF1Focus(Context c, List<String> refs, Map<String, String> names) {
        List<String> clean = new ArrayList<>();
        if (refs != null) for (String r : refs) { if (!validRef(r)) return false; if (!clean.contains(r)) clean.add(r); }
        if (clean.size() > MAX_FOCUS) return false;
        SharedPreferences.Editor ed = sp(c).edit().putString("f1_focus", csv(clean));
        if (names != null)
            for (Map.Entry<String, String> n : names.entrySet())
                if (clean.contains(n.getKey()) && n.getValue() != null && !n.getValue().isEmpty()) ed.putString("sportmeta_" + n.getKey(), n.getValue());
        ed.apply();
        return true;
    }

    static String ufcScope(Context c) {
        return SCOPE_NUMBERED.equals(sp(c).getString("ufc_scope", SCOPE_ALL)) ? SCOPE_NUMBERED : SCOPE_ALL;
    }

    /** "all" or "numbered"; anything else is ignored. */
    static void setUfcScope(Context c, String scope) {
        if (SCOPE_ALL.equals(scope) || SCOPE_NUMBERED.equals(scope)) sp(c).edit().putString("ufc_scope", scope).apply();
    }

    static Set<String> ufcFighters(Context c) { return new LinkedHashSet<>(csv(sp(c).getString("ufc_fighters", ""))); }

    /** Follows or unfollows a fighter; the name is saved as sportmeta_f:<id> (kept after an unfollow: harmless). */
    static void setUfcFighter(Context c, String id, String name, boolean on) {
        if (id == null || id.trim().isEmpty() || id.indexOf(',') >= 0) return;
        Set<String> cur = ufcFighters(c);
        if (on) cur.add(id.trim()); else cur.remove(id.trim());
        SharedPreferences.Editor ed = sp(c).edit().putString("ufc_fighters", csv(cur));
        if (on && name != null && !name.isEmpty()) ed.putString("sportmeta_" + fighterRef(id.trim()), name);
        ed.apply();
    }

    /** The sportmeta ref of a fighter. */
    static String fighterRef(String id) { return "f:" + id; }

    /** The saved display name of a ref ("d:5579" -> "Lando Norris"); "" when unknown. */
    static String name(Context c, String ref) { return ref == null ? "" : sp(c).getString("sportmeta_" + ref, ""); }

    static SportPrefs prefs(Context c) { return new SportPrefs(f1Focus(c), ufcScope(c), ufcFighters(c)); }

    /**
     * Whether an event belongs in the viewer's lists (pure). F1: every event. UFC: never the Contender Series (also
     * removed when parsed); with scope "all" every event; with "numbered" a numbered event, or any event where a
     * followed fighter has a bout.
     */
    static boolean shows(SportPrefs p, SportEvent e) {
        if (e == null) return false;
        if (F1.equals(e.sport)) return true;
        if (!UFC.equals(e.sport)) return false;
        if (contender(e.name)) return false;
        if (p == null || !SCOPE_NUMBERED.equals(p.scope)) return true;
        if (e.numbered) return true;
        for (String f : p.fighters) if (e.boutOf(f) != null) return true;
        return false;
    }

    static boolean contender(String name) { return name != null && name.trim().toLowerCase(java.util.Locale.ROOT).startsWith("dana white's contender series"); }

    /** No club and no sport followed: nothing to fetch, no job to run. */
    static boolean nothingFollowed(Context c) { return Teams.followed(c).isEmpty() && followed(c).isEmpty(); }

    /** The home-screen row key of a sport ("sport_f1"): never "club_", so club-row housekeeping never touches it. */
    static String rowKey(String sport) { return "sport_" + sport; }

    /** The sports in ALL order (for loops that want a list). */
    static List<String> all() { return Arrays.asList(ALL); }
}
