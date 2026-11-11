package app.fanela.tv;

import android.content.Context;
import java.util.List;

/**
 * One source of "where to watch" for a country. Where.providers() picks them; a refresh uses them in order:
 *  guess()   at parse time, from data on the TV (rights lists, season tables): the expected channel;
 *  prepare() once per refresh, for downloads shared by every club (a day's TV guide);
 *  fetch()   per club, network, outside every lock;
 *  apply()   per club, inside the store lock, no network: confirms or corrects the channel of its matches.
 */
interface Watch {
    String id();

    /** Sets the expected channel on m. False when this source has nothing for it (the next one is asked). */
    boolean guess(Match m);

    void prepare(Context c);

    /** The club's listings, or null. */
    Object fetch(Context c, Teams.Club k, List<Match> known, boolean basket);

    void apply(List<Match> all, Teams.Club k, Object listings);
}
