package app.fanela.tv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
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
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), splash());
        finish();
        overridePendingTransition(0, 0);
    }

    /**
     * Android 13+: the main screen's system splash shows its icon (the shirt, values-v31) although another activity
     * (this hand-over) starts it, as a launch from the TV's home screen would; values-v33's icon_preferred asks the same
     * from the theme. Android 12 has no way to ask: it gives a hand-over the splash's plain colour (checked on an
     * Android TV 12 emulator, from the real launcher tile too), so there the shirt comes with the app's first frame,
     * which is the brand (HomeActivity.build). Null before 13.
     */
    static Bundle splash() {
        if (Build.VERSION.SDK_INT < 33) return null;
        return ActivityOptions.makeBasic().setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle();
    }
}
