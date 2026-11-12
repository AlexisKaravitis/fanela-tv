package app.fanela.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The viewer's sport choices as a value object, for pure code (picks, card models, Sports.shows): made by
 * Sports.prefs(Context), or directly in tests. Immutable.
 */
final class SportPrefs {
    /** F1 focus refs, at most 2: "d:<athleteId>" for a driver, "t:<teamId>" for a team, in the viewer's order. */
    final List<String> focus;
    /** UFC events shown: Sports.SCOPE_ALL or Sports.SCOPE_NUMBERED. */
    final String scope;
    /** Followed UFC fighters (ESPN athlete ids). */
    final Set<String> fighters;

    SportPrefs(List<String> focus, String scope, Set<String> fighters) {
        this.focus = Collections.unmodifiableList(new ArrayList<>(focus == null ? Collections.<String>emptyList() : focus));
        this.scope = Sports.SCOPE_NUMBERED.equals(scope) ? Sports.SCOPE_NUMBERED : Sports.SCOPE_ALL;
        this.fighters = Collections.unmodifiableSet(new LinkedHashSet<>(fighters == null ? Collections.<String>emptySet() : fighters));
    }

    /** Nothing chosen: no focus, every UFC event, no fighters. */
    static SportPrefs none() { return new SportPrefs(null, Sports.SCOPE_ALL, null); }

    /** The focused drivers' athlete ids, in order. */
    List<String> focusDrivers() { return ids("d:"); }

    /** The focused teams' ESPN team ids, in order. */
    List<String> focusTeams() { return ids("t:"); }

    boolean focusDriver(String athleteId) { return athleteId != null && focus.contains("d:" + athleteId); }
    boolean focusTeam(String teamId) { return teamId != null && focus.contains("t:" + teamId); }
    boolean followsFighter(String athleteId) { return athleteId != null && fighters.contains(athleteId); }

    private List<String> ids(String prefix) {
        List<String> out = new ArrayList<>();
        for (String r : focus) if (r.startsWith(prefix)) out.add(r.substring(prefix.length()));
        return out;
    }
}
