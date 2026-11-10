package app.fanela.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After install, an update, a TV restart, or when the home screen asks for our rows (INITIALIZE_PROGRAMS):
 * reschedule the background job (only used with home-screen rows) and refresh once soon.
 */
public class InitReceiver extends BroadcastReceiver {
    private static long last;

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
            && !"android.media.tv.action.INITIALIZE_PROGRAMS".equals(a)) return;
        long now = System.currentTimeMillis();
        if (now - last < 60000) return;  // at most once a minute
        last = now;
        try { Updater.schedule(c); } catch (Exception ignored) { }
        try { Brand.applyIfIdle(c); } catch (Exception ignored) { }  // the app tile in the favourite club's kit, before the launcher redraws
        if (HomeRows.enabled(c)) { try { Updater.soon(c); } catch (Exception ignored) { } }
    }
}
