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
 *   ... -a app.fanela.tv.REFRESH  runs one full refresh.
 *   ... -a app.fanela.tv.TVGUIDE --ez on true  switches the TV's own guide on as a where-to-watch source (outside
 *     Greece). It also needs: adb shell pm grant app.fanela.tv.debug android.permission.READ_TV_LISTINGS
 *   ... -a app.fanela.tv.FOLLOW --es club 435,605 [--es lang el]  sets the followed clubs and refreshes.
 *   ... -a app.fanela.tv.AUDIT    checks every Super League club's data (files/audit.txt, see Audit).
 *   ... -a app.fanela.tv.AUDIT --es country GB --es leagues eng.1,uefa.champions [--es max 6]
 *     the same for other leagues and a viewer's country: files/audit_GB.txt.
 */
public class DebugReceiver extends BroadcastReceiver {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();

    @Override public void onReceive(final Context c, Intent i) {
        final String action = i.getAction(), club = i.getStringExtra("club"), lang = i.getStringExtra("lang");
        final String country = i.getStringExtra("country"), leagues = i.getStringExtra("leagues"), max = i.getStringExtra("max");
        final Context app = c.getApplicationContext();
        ONE.execute(new Runnable() { public void run() {
            Engine.background();
            try {
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
                } else if ("app.fanela.tv.REFRESH".equals(action)) Engine.refresh(app, true);
                else if ("app.fanela.tv.TVGUIDE".equals(action)) {
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tvguide", i.getBooleanExtra("on", false)).commit();
                    android.util.Log.i("Fanela", "tv guide " + (TvGuideProvider.enabled(app) ? "on" : "off (switch or permission)"));
                }
                else if ("app.fanela.tv.FOLLOW".equals(action)) {  // --es ids 435,605 [--es lang el]: set clubs, then refresh
                    String ids = club == null ? "" : club;
                    if (!ids.matches("[a-z0-9,]*")) return;
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("follow", ids)
                        .putBoolean("onboarded", true).putString("lang", "el".equals(lang) ? "el" : "en").commit();
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
}
