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
 *   ... -a app.fanela.tv.REFRESH  runs one full refresh.
 *   ... -a app.fanela.tv.FOLLOW --es club 435,605 [--es lang el]  sets the followed clubs and refreshes.
 *   ... -a app.fanela.tv.AUDIT    checks every club's data (files/audit.txt, see Audit).
 */
public class DebugReceiver extends BroadcastReceiver {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();

    @Override public void onReceive(final Context c, Intent i) {
        final String action = i.getAction(), club = i.getStringExtra("club"), lang = i.getStringExtra("lang");
        final Context app = c.getApplicationContext();
        ONE.execute(new Runnable() { public void run() {
            Engine.background();
            try {
                if ("app.fanela.tv.DEMO".equals(action) && club != null && club.matches("[0-9]{1,9}")) {
                    String l = "el".equals(lang) ? "el" : "en";
                    File dir = new File(app.getFilesDir(), "demo/" + club + "_" + l);
                    dir.mkdirs();
                    File[] old = dir.listFiles();
                    for (int k = 0; old != null && k < old.length; k++) old[k].delete();
                    Cards.demo(app, club, l, "demo/" + club + "_" + l + "/");
                    new File(dir, "DONE").createNewFile();
                } else if ("app.fanela.tv.REFRESH".equals(action)) Engine.refresh(app, true);
                else if ("app.fanela.tv.FOLLOW".equals(action)) {  // --es ids 435,605 [--es lang el]: set clubs, then refresh
                    String ids = club == null ? "" : club;
                    if (!ids.matches("[0-9,]*")) return;
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("follow", ids)
                        .putBoolean("onboarded", true).putString("lang", "el".equals(lang) ? "el" : "en").commit();
                    Engine.refresh(app, true);
                }
                else if ("app.fanela.tv.AUDIT".equals(action)) Audit.run(app);
            } catch (Throwable t) {
                android.util.Log.e("Fanela", "debug action failed", t);
            }
        }});
    }
}
