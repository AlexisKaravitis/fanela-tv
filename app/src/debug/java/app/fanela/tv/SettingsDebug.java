package app.fanela.tv;

import android.content.Context;
import java.io.File;

/**
 * Debug builds only: lets a check on a Google TV emulator (which shows no app rows) republish the home-screen rows
 * anyway when the card style changes, so the redrawn cards can be read from the app's files. On with
 * `adb shell run-as app.fanela.tv.debug touch files/debug_force_rows`.
 */
final class SettingsDebug {
    private SettingsDebug() { }

    static boolean forceRows(Context c) { return new File(c.getFilesDir(), "debug_force_rows").exists(); }
}
