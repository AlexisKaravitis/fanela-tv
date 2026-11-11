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
                else if ("app.fanela.tv.FOLLOW".equals(action)) {  // --es ids 435,605 [--es lang el]: set clubs, then refresh
                    String ids = club == null ? "" : club;
                    if (!ids.matches("[a-z0-9,]*")) return;
                    android.content.SharedPreferences.Editor ed = app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                        .putBoolean("onboarded", true).putString("lang", "el".equals(lang) ? "el" : "en");
                    if (cc != null) ed.putString("country", cc);  // [--es country GB]: a viewer abroad
                    ed.commit();
                    I18n.load(app);
                    follow(app, ids);
                    Engine.refresh(app, true);
                }
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
