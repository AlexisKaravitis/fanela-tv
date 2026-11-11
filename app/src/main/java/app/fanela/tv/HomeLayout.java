package app.fanela.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;

/**
 * How the home screen is drawn: the app layout chosen in Settings › Appearance (Appearance.layout()). HomeActivity
 * keeps the data, the refresh cycle, the match screen and focus restoration; a layout turns a HomeModel into views and
 * owns the D-pad inside them. Club is the default; Matchday is the other (create()).
 *
 * Threads: prepare() runs on the display thread (decoding, baking crests and backgrounds); everything else on the UI
 * thread. bind() may be called again at any time with fresh data (a live score, a refresh): it updates in place and
 * never moves focus unless the focused item is gone.
 */
interface HomeLayout {
    /** What the layout asks of its screen. */
    interface Host {
        /** The match screen (the match panel for now). */
        void openMatch(Match m, Teams.Club club);
        /** OK on a live match: the channel's app when it is known and installed, else the match screen. */
        void openLive(Match m, Teams.Club club);
        void openSettings();
        /** The empty state's "Choose clubs": Settings › Your clubs. */
        void chooseClubs();
    }

    /** Display thread: makes ready everything bind() will draw for this model (it may be slow; bind() must not be). */
    void prepare(Context c, HomeModel m);

    /**
     * UI thread, before the first frame (a loading model made from the preferences): the cheap part of prepare(),
     * so the first frame already shows the field and the rail (the selected club's field without its crest watermark,
     * the rail's crests from the TV's cache, a monogram where there is none). bind() follows with the same model.
     */
    void first(Context c, HomeModel m);

    /** Creates the layout's views (once per layout; HomeActivity sets it as the content). */
    View build(Context c, Host host);

    /** Shows the model; the first call also places the launch focus. */
    void bind(HomeModel m);

    /** The focused item as a key that survives a rebind or a rebuild ("hero", "row:<match>", "rail:<club>"); null if none. */
    String focusKey();

    /** Focuses the item with this key, or the nearest sensible one (the launch focus when it is gone). */
    void focus(String key);

    /** The club shown (a rail selection, a re-staged card); null for the default. */
    String selected();
    void select(String clubId);

    /** While another panel is on top: no D-pad here, nothing focusable. */
    void block(boolean blocked);

    /** D-pad and OK inside the layout; true when handled. Back never comes here: see back(). */
    boolean onKey(KeyEvent e);

    /**
     * Back on the home screen (BackKey, every Android version): true when the layout handled it (Matchday returns a
     * moved list to row 1 first); false lets the app close (SPEC-APP 3.6, 4.5).
     */
    boolean back();

    void resume();
    void pause();
    void release();

    /** The layout for an Appearance.layout() id. */
    static HomeLayout create(String id) {
        switch (id) {
            case Appearance.MATCHDAY: return new MatchdayLayout();
            case Appearance.CLUB:
            default:
                return new ClubLayout();
        }
    }
}
