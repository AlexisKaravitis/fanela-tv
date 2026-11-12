package app.fanela.tv;

import android.content.Context;
import java.util.List;

/**
 * Release builds: no scenarios. The debug build's Scenario (src/debug) lets the home screen show a fixed state for
 * screenshots; here the hook is always off, so the home screen only ever shows the saved data.
 */
final class Scenario {
    private Scenario() { }

    static final class Snapshot {
        List<Match> matches;
        List<String> follow;
        /** The followed sports' events, the sports and their choices (F1 / UFC). */
        List<SportEvent> events;
        List<String> sports;
        SportPrefs prefs;
        String favourite, mode, lang;
        long now, savedAt;
    }

    static Snapshot active(Context c) { return null; }

    static void apply(Context c) { }
}
