package app.fanela.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debug builds only (not in release): design and data checks without touching the screen. One action at a time.
 *   adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.DEMO --es club 605 --es lang en
 *     renders every card state into files/demo/605_en/ and writes DONE there when finished (read with run-as).
 *     [--es country GB] draws it as a viewer in that country would see it (where-to-watch), without changing the setting.
 *   ... -a app.fanela.tv.MATRIX --es style signature|clean|kit|ticket|clash|legacy|all --es lang en [--ez scores false]
 *     draws the 1.2 card state matrix (Fixtures: every state and edge state) in that style into
 *     files/matrix/<lang>/<style>/, then DONE. Its crests and logos are fetched first (Fixtures.prefetch). Beside
 *     each card: its probe (.tsv), its .meta (competition names, state, line 4's date) and, in nocomp/, the same card
 *     drawn without its competition (the audit checks nothing else moves).
 *   ... -a app.fanela.tv.SCENARIO --es name home|live|loading|offline|empty|many|off [--es lang el] [--es layout matchday|club]
 *     shows the home screen in a fixed state of the app spec (Scenario), its images fetched first; then DONE in
 *     files/scenario. Restart the app to see it (am force-stop, am start). "off" gives the real data back.
 *     --es layout: the app layout (Settings › Appearance) to show it in; it stays until changed.
 *   ... -a app.fanela.tv.REFRESH  runs one full refresh.
 *   ... -a app.fanela.tv.TVGUIDE --ez on true  switches the TV's own guide on as a where-to-watch source (outside
 *     Greece). It also needs: adb shell pm grant app.fanela.tv.debug android.permission.READ_TV_LISTINGS
 *   ... -a app.fanela.tv.FOLLOW --es club 435,605 [--es lang el] [--es country GB] [--es layout matchday|club]  sets
 *     the followed clubs as Settings does (their names, crests and leagues saved with them; a league list the TV
 *     never downloaded is fetched to find a club), the viewer's country and the app layout, then refreshes.
 *   ... -a app.fanela.tv.MATRIX --es sport f1|ufc|all --es style signature|clean|kit|ticket|clash|all --es lang en [--ez scores false]
 *     the F1 / UFC event cards instead (stream E's SportFixtures.matrix: files/matrix/<lang>/<style>/sport/).
 *   ... -a app.fanela.tv.FOLLOW --es sports f1,ufc|none [--es focus d:5579,t:106892] [--es scope all|numbered]
 *     [--es fighters 4426000,4049391|none]  sets the followed sports, the F1 focus (at most 2), the UFC events and the
 *     followed fighters as Settings does (names from the stored events / standings when known). With --es club too,
 *     both; without it the clubs are left as they are. Then refreshes.
 *   ... -a app.fanela.tv.SPORT_DUMP  logs (tag Fanela, "sport_dump") the sport prefs, the engine's timestamps and a
 *     summary of files/events1.json: one line per event, one per session or bout; also written to files/sport_dump.txt.
 *   ... -a app.fanela.tv.AUDIT    checks every Super League club's data (files/audit.txt, see Audit).
 *   ... -a app.fanela.tv.AUDIT --es country GB --es leagues eng.1,uefa.champions [--es max 6]
 *     the same for other leagues and a viewer's country: files/audit_GB.txt.
 */
public class DebugReceiver extends BroadcastReceiver {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();

    @Override public void onReceive(final Context c, Intent i) {
        final String action = i.getAction(), club = i.getStringExtra("club"), lang = i.getStringExtra("lang");
        final String country = i.getStringExtra("country"), leagues = i.getStringExtra("leagues"), max = i.getStringExtra("max");
        final String layout = i.getStringExtra("layout");
        final Context app = c.getApplicationContext();
        ONE.execute(new Runnable() { public void run() {
            Engine.background();
            try {
                if (layout != null && java.util.Arrays.asList(Appearance.LAYOUTS).contains(layout))  // written now: the app is restarted next
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("app_layout", layout).commit();
                String cc = country != null && country.matches("[A-Z]{2}") ? country : null;
                if ("app.fanela.tv.DEMO".equals(action) && club != null && club.matches("[a-z0-9]{1,12}")) {
                    String l = "el".equals(lang) ? "el" : "en";
                    String name = club + "_" + l + (cc == null ? "" : "_" + cc);
                    File dir = new File(app.getFilesDir(), "demo/" + name);
                    dir.mkdirs();
                    File[] old = dir.listFiles();
                    for (int k = 0; old != null && k < old.length; k++) old[k].delete();
                    Cards.demo(app, club, l, cc, "demo/" + name + "/");
                    new File(dir, "DONE").createNewFile();
                } else if ("app.fanela.tv.MATRIX".equals(action) && i.getStringExtra("sport") != null) {
                    String sport = i.getStringExtra("sport"), style = i.getStringExtra("style");
                    if (!sport.matches("f1|ufc|all")) return;
                    String l = "el".equals(lang) ? "el" : "en";
                    boolean scores = i.getBooleanExtra("scores", true);
                    String[] styles = style == null || "all".equals(style) ? Appearance.STYLES : new String[]{style};
                    for (String st : styles) if (st.matches("[a-z]{1,16}")) sportMatrix(app, st, sport, l, scores);
                } else if ("app.fanela.tv.MATRIX".equals(action)) {
                    String style = i.getStringExtra("style");
                    String l = "el".equals(lang) ? "el" : "en";
                    boolean scores = i.getBooleanExtra("scores", true);
                    String[] styles = style == null || "all".equals(style)
                        ? new String[]{Appearance.SIGNATURE, Appearance.CLEAN, Appearance.KIT, Appearance.TICKET, Appearance.CLASH, "legacy"} : new String[]{style};
                    Fixtures.prefetch(app);  // the crests and logos it draws, so it is complete on a fresh device
                    for (String st : styles) if (st.matches("[a-z]{1,16}")) Fixtures.render(app, st, l, scores);
                } else if ("app.fanela.tv.SCENARIO".equals(action)) {
                    String name = i.getStringExtra("name");
                    File done = new File(app.getFilesDir(), "scenario/DONE");
                    done.delete();
                    Scenario.set(app, name, lang);
                    Scenario.Snapshot s = Scenario.active(app);
                    if (s != null) {
                        Channels.load(app);
                        Engine.prefetchImages(app, s.matches, new java.util.HashSet<String>(s.follow));
                        for (String id : s.follow) { Teams.Club k = Teams.find(app, id); if (k != null) Net.fetchImage(app, k.crest); }
                    }
                    done.getParentFile().mkdirs();
                    done.createNewFile();
                } else if ("app.fanela.tv.REFRESH".equals(action)) Engine.refresh(app, true);
                else if ("app.fanela.tv.TVGUIDE".equals(action)) {
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tvguide", i.getBooleanExtra("on", false)).commit();
                    android.util.Log.i("Fanela", "tv guide " + (TvGuideProvider.enabled(app) ? "on" : "off (switch or permission)"));
                }
                else if ("app.fanela.tv.FOLLOW".equals(action)) {  // --es club 435,605 [--es lang el]: set clubs, then refresh
                    String ids = club == null ? "" : club;
                    if (!ids.matches("[a-z0-9,]*")) return;
                    String sports = i.getStringExtra("sports");
                    boolean sportsOnly = club == null && (sports != null || i.getStringExtra("focus") != null
                        || i.getStringExtra("scope") != null || i.getStringExtra("fighters") != null);
                    android.content.SharedPreferences.Editor ed = app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                        .putBoolean("onboarded", true).putString("lang", "el".equals(lang) ? "el" : "en");
                    if (cc != null) ed.putString("country", cc);  // [--es country GB]: a viewer abroad
                    ed.commit();
                    I18n.load(app);
                    if (!sportsOnly) follow(app, ids);  // the sports alone leave the clubs as they are
                    followSports(app, sports, i.getStringExtra("focus"), i.getStringExtra("scope"), i.getStringExtra("fighters"));
                    Engine.refresh(app, true);
                }
                else if ("app.fanela.tv.SPORT_DUMP".equals(action)) sportDump(app);
                else if ("app.fanela.tv.AUDIT".equals(action)) {
                    if (cc == null && leagues == null) Audit.run(app);
                    else Audit.run(app, cc == null ? "GR" : cc, leagues == null || !leagues.matches("[a-z0-9.,_]+")
                        ? java.util.Arrays.asList("gre.1") : java.util.Arrays.asList(leagues.split(",")),
                        max != null && max.matches("[0-9]{1,3}") ? Integer.parseInt(max) : 0);
                }
            } catch (Throwable t) {
                android.util.Log.e("Fanela", "debug action failed", t);
            }
        }});
    }

    /**
     * Stream E's sport card matrix (SportFixtures.matrix in debug), reached by name so this file builds before E's class
     * is merged; logged when it is not there yet.
     */
    private static void sportMatrix(Context app, String style, String sport, String lang, boolean scores) throws Exception {
        java.lang.reflect.Method m;
        try {
            m = Class.forName("app.fanela.tv.SportFixtures").getDeclaredMethod("matrix", Context.class, String.class, String.class, String.class, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            android.util.Log.w("Fanela", "matrix: SportFixtures.matrix(Context, style, sport, lang, scores) is not in this build (stream E)");
            return;
        }
        m.setAccessible(true);
        try {
            m.invoke(null, app, style, sport, lang, scores);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
        android.util.Log.i("Fanela", "matrix sport " + sport + " " + lang + "/" + style + (scores ? "" : " hidden") + " done");
    }

    /**
     * FOLLOW's sport half, as Settings does it (Sports): --es sports "f1,ufc" ("none" or "": none), --es focus
     * "d:5579,t:106892" (at most 2; "none" clears), --es scope all|numbered, --es fighters "id,id" ("none": none; the
     * exact set). Names are saved from the stored events and the standings when they know the ids.
     */
    static void followSports(Context app, String sports, String focus, String scope, String fighters) {
        if (sports != null) {
            java.util.List<String> on = "none".equals(sports) ? new java.util.ArrayList<String>() : Sports.csv(sports);
            for (String s : Sports.ALL) Sports.setFollowed(app, s, on.contains(s));
        }
        java.util.Map<String, String> names = new java.util.HashMap<>();
        java.util.List<SportEvent> stored = EventStore.load(app);
        for (SportEvent e : stored)
            for (SportEvent.Session s : e.sessions) {
                for (SportEvent.Entrant x : s.order) {
                    if (!x.id.isEmpty()) names.put("d:" + x.id, x.name);
                    if (!x.teamId.isEmpty()) names.put("t:" + x.teamId, x.team);
                }
                for (SportEvent.Bout b : s.bouts)
                    for (SportEvent.Fighter f : new SportEvent.Fighter[]{b.a, b.b}) if (f != null && !f.id.isEmpty()) names.put("f:" + f.id, f.name);
            }
        if (focus != null) {
            java.util.List<String> refs = "none".equals(focus) ? new java.util.ArrayList<String>() : Sports.csv(focus);
            boolean need = false;
            for (String r : refs) need |= !names.containsKey(r);
            if (need) {
                try {
                    F1Feed.Standings st = F1Feed.standings(app, false);
                    if (st != null) {
                        for (SportEvent.Entrant d : st.drivers) names.put("d:" + d.id, d.name);
                        for (F1Feed.Team t : st.teams) names.put("t:" + t.id, t.name);
                    }
                } catch (Throwable ignored) { }
            }
            if (!Sports.setF1Focus(app, refs, names)) android.util.Log.w("Fanela", "follow: focus refused (at most 2 d:/t: refs): " + focus);
        }
        if (scope != null) Sports.setUfcScope(app, scope);
        if (fighters != null) {
            java.util.List<String> want = "none".equals(fighters) ? new java.util.ArrayList<String>() : Sports.csv(fighters);
            for (String id : Sports.ufcFighters(app)) if (!want.contains(id)) Sports.setUfcFighter(app, id, null, false);
            for (String id : want) Sports.setUfcFighter(app, id, names.get("f:" + id), true);
        }
        android.util.Log.i("Fanela", "follow: sports " + Sports.followed(app) + ", focus " + Sports.f1Focus(app) + ", scope "
            + Sports.ufcScope(app) + ", fighters " + Sports.ufcFighters(app));
    }

    /** SPORT_DUMP: the sport prefs, the engine's timestamps and events1.json, one line each (logcat and a file). */
    static void sportDump(Context app) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        android.content.SharedPreferences sp = app.getSharedPreferences("settings", Context.MODE_PRIVATE);
        b.append("prefs sports=").append(sp.getString("sports", "")).append(" followed=").append(Sports.followed(app))
            .append(" f1_focus=").append(sp.getString("f1_focus", "")).append(" ufc_scope=").append(sp.getString("ufc_scope", "(all)"))
            .append(" ufc_fighters=").append(sp.getString("ufc_fighters", "")).append(" onboarded=").append(sp.getBoolean("onboarded", false))
            .append(" follow=").append(sp.getString("follow", "")).append(" lang=").append(sp.getString("lang", "")).append('\n');
        for (java.util.Map.Entry<String, ?> e : new java.util.TreeMap<>(sp.getAll()).entrySet())
            if (e.getKey().startsWith("sportmeta_")) b.append("meta ").append(e.getKey().substring(10)).append(" = ").append(e.getValue()).append('\n');
        android.content.SharedPreferences eng = app.getSharedPreferences(Sports.ENGINE, Context.MODE_PRIVATE);
        for (java.util.Map.Entry<String, ?> e : new java.util.TreeMap<>(eng.getAll()).entrySet()) {
            Object v = e.getValue();
            b.append("engine ").append(e.getKey()).append(" = ").append(v);
            if (v instanceof Long && (Long) v > 0) b.append(" (").append(new java.util.Date((Long) v)).append(')');
            b.append('\n');
        }
        File f = new File(app.getFilesDir(), EventStore.FILE);
        b.append("file ").append(EventStore.FILE).append(f.exists() ? " " + f.length() + " bytes, " + new java.util.Date(f.lastModified()) : " missing").append('\n');
        java.util.List<SportEvent> all = EventStore.load(app);
        b.append("events ").append(all.size()).append('\n');
        java.text.SimpleDateFormat t = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.US);
        t.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        for (SportEvent e : all) {
            b.append("event ").append(e.key()).append(" \"").append(e.name).append("\" ").append(e.state).append(' ').append(t.format(new java.util.Date(e.start)))
                .append(" sessions=").append(e.sessions.size()).append(e.numbered ? " numbered " + e.number : "").append(" misses=").append(e.misses).append('\n');
            for (SportEvent.Session s : e.sessions) {
                b.append("  session ").append(s.id).append(' ').append(s.kind).append(' ').append(s.state).append(s.detail.isEmpty() ? "" : "/" + s.detail)
                    .append(' ').append(s.tbd ? "TBD" : t.format(new java.util.Date(s.start))).append(" order=").append(s.order.size())
                    .append(" bouts=").append(s.bouts.size()).append(" watch=").append(s.watch == null ? "-" : s.watch.channel + "/" + s.watch.listedBy).append('\n');
                for (SportEvent.Bout x : s.bouts)
                    b.append("    bout ").append(x.order).append(' ').append(x.a == null ? "?" : x.a.name).append(" vs ").append(x.b == null ? "?" : x.b.name)
                        .append(" (").append(x.weightClass).append(") ").append(x.state).append(x.outcome == 0 ? "" : " outcome " + x.outcome).append('\n');
            }
        }
        for (String line : b.toString().split("\n")) android.util.Log.i("Fanela", "sport_dump " + line);
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(app.getFilesDir(), "sport_dump.txt"))) { o.write(b.toString().getBytes("UTF-8")); }
    }

    /**
     * Follows these clubs as Settings does (Teams.setFollowed): each one's name, crest, league and colour are saved
     * with the list, so every screen and the refresh know it, whichever league list it came from, and each is due for
     * a refresh at once. A club in no list on the TV yet has the league lists downloaded until one has it; a club
     * found in none is followed by id only (logged).
     */
    private static void follow(Context app, String ids) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String id : ids.split(",")) if (!id.isEmpty()) set.add(id);
        android.content.SharedPreferences sp = app.getSharedPreferences("settings", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor ed = sp.edit().putString("follow", String.join(",", set));
        android.content.SharedPreferences.Editor eng = app.getSharedPreferences("engine", Context.MODE_PRIVATE).edit();
        for (String id : set) {
            Teams.Club k = Teams.find(app, id);
            for (int n = 0; k == null && n < Leagues.ALL.length; n++)
                if (Teams.refreshLeague(app, Leagues.ALL[n].id, false)) k = Teams.find(app, id);
            if (k == null) { android.util.Log.w("Fanela", "follow: club " + id + " is in no league list"); continue; }
            ed.putString("meta_name_" + k.id, k.name).putString("meta_crest_" + k.id, k.crest)
                .putString("meta_league_" + k.id, k.league).putString("meta_color_" + k.id, k.color == null ? "" : k.color);
            eng.remove("lastFull_" + k.id).remove("sig_" + k.id).remove("res_" + k.id);
        }
        // Teams keeps its clubs until the follow list changes: the same list again is cleared first, so it reads them anew
        sp.edit().putString("follow", "").commit();
        Teams.get(app, "");
        ed.commit();
        eng.commit();
    }
}
