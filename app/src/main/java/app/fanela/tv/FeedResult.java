package app.fanela.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One sport's answer from its feed (F1Feed.fetch / UfcFeed.fetch), merged by EventStore.merge. ok only when the
 * answer parsed: a failed fetch (ok false) keeps that sport's last good events. Stream B owns this file.
 */
final class FeedResult {
    final String sport;
    /** The feed answered and parsed. */
    boolean ok;
    /** Results were asked for this time (standings and roster for F1): SportEngine stamps res_<sport>. */
    boolean results;
    /** Events with their sessions, fully parsed (a fetch may return only the weekends or cards that were due). */
    final List<SportEvent> events = new ArrayList<>();
    /**
     * Event ids the feed asked for in detail and did not get back: their misses count goes up (EventStore.merge);
     * an id the fetch returned resets it. Empty when nothing was missing.
     */
    final Set<String> missed = new java.util.HashSet<>();
    /**
     * The ids the sport's calendar lists (B may give its cached calendar every pass); null when unknown. An event the
     * calendar no longer lists and that 3 detail fetches in a row did not return is dropped.
     */
    Set<String> listed;

    FeedResult(String sport) { this.sport = sport; }

    /** A fetch that failed: ok false, nothing to merge. */
    static FeedResult failed(String sport) { return new FeedResult(sport); }

    /** The network behind the feeds' refreshes (F1Feed.fetch, UfcFeed.fetch, standings, roster, circuits). */
    interface Http { String get(String url) throws java.io.IOException; }

    /** Net.http in the app; the unit tests put the saved samples behind it, so a fetch runs end to end offline. */
    static Http net = Net::http;
}
