package app.fanela.tv;

import android.content.Context;

/** Release builds: the rows follow the TV (see the debug version). */
final class SettingsDebug {
    private SettingsDebug() { }

    static boolean forceRows(Context c) { return false; }
}
