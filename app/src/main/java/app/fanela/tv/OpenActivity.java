package app.fanela.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;

/**
 * Opens a home-screen tile: the channel's app when it is installed, otherwise the match in Fanela.
 * Only a fixed list of apps is accepted, so other apps cannot use this to open arbitrary apps.
 */
public class OpenActivity extends Activity {
    static final List<String> APPS = Arrays.asList(Cards.MAGENTA, Cards.SPORTFM, Cards.EON, Cards.ANT1);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        String app = in.getStringExtra("app"), match = in.getStringExtra("match");
        if (app == null || !launch(this, app)) {
            Intent home = new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (match != null && match.length() < 200) home.putExtra("match", match);
            startActivity(home);
        }
        finish();
    }

    /** Starts a whitelisted channel app. Returns false (after a short message) if it is not installed. */
    static boolean launch(Context c, String pkg) {
        if (!APPS.contains(pkg)) return false;
        Intent l = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (l == null) l = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (l != null) {
            try { c.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; } catch (Exception ignored) { }
        }
        I18n.load(c);
        Toast.makeText(c, I18n.f("app_missing", SportWhere.appName(pkg)), Toast.LENGTH_LONG).show();
        return false;
    }
}
