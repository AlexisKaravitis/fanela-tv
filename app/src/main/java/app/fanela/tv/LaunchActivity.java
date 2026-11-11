package app.fanela.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Where the app's tile on the TV leads (every .Launch* alias in the manifest). It opens the main screen in a task of its
 * own and closes at once. Brand switches the tile by disabling one alias and enabling another, and Android removes the
 * tasks started from a disabled alias; the main screen's task starts here from an explicit intent instead, so a tile
 * switch can never close the app while someone is using it.
 */
@android.annotation.SuppressLint("CustomSplashScreen")  // not a splash screen: it shows nothing and hands over at once
public class LaunchActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }
}
