package app.fanela.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One followed sport on the app's home screen (F1-UFC-ARCHITECTURE §7): HomeModel gains a list of these, one per
 * followed sport, worked out once per load like a club's ClubData. The hero (the weekend or the event: live, a fresh
 * result for 6 h, else the next), the latest results, what comes next (every session of the next two weekends; this
 * card's segments and the next events) and the rail's line. Pure Java on SportEvent (SportModelTest).
 *
 * The hero is the card's pick (EventCards.pickNow, stream D) when it is about something still to come; a live session
 * and a fresh result come first, as football's hero. Where stream D's picks are missing, the model picks the same way
 * itself, so the screens never depend on the launcher rows.
 */
final class SportModel {
    private SportModel() { }

    /** F1's scoring sessions: a result tile, a rail line, the Matchday "last" card. Practice never. */
    static final Set<String> SCORING = new HashSet<>(Arrays.asList("RACE", "SPRINT", "QUAL", "SQ"));
    /** The sessions whose result is the weekend's news (the hero after the flag, the rail's "NOR won"). */
    static final Set<String> MAIN_RESULTS = new HashSet<>(Arrays.asList("RACE", "SPRINT"));
    /** F1: the weekends listed; UFC: the events after this card. */
    static final int WEEKENDS = 2, NEXT_EVENTS = 3;
    static final long H = 3600000L;

    static final class SportData {
        String sport, label;                              // "f1", "Formula 1" (or "Formula 1 · Norris")
        EventCards.Pick hero;                             // the weekend or event, as the card picks it; null: none
        HomeModel.Kind kind = HomeModel.Kind.NONE;
        final List<EventCards.Pick> latest = new ArrayList<>();    // the last 2 results
        final List<EventCards.Pick> upcoming = new ArrayList<>();  // sessions (F1) or events (UFC) to come
        String railLine = "";                             // QUALI · SAT 04:00, LIVE · LAP 34/50, NORRIS WON
        boolean railLive, stale;
        /** The app hides results for this sport (SportText.hidden): the rail says Final, the panel shows no podium. */
        boolean hidden;
        /** The viewer's choices the picks were made with (the Matchday cards are drawn with them). */
        SportPrefs prefs = SportPrefs.none();
        /** This sport's events (copies; the screens find the opened event here). */
        final List<SportEvent> events = new ArrayList<>();
        /** The layout's prepared images for this sport (HomeLayout.prepare), opaque to the model. */
        Object art;

        /** The row key / rail id: "sport:f1" (never a club id). */
        String id() { return SportModel.id(sport); }

        /** Everything that changes the panel's rows (a change rebuilds them; the same keys update in place). */
        String structure() {
            StringBuilder b = new StringBuilder(sport).append(kind).append(hero == null ? "" : key(hero));
            for (EventCards.Pick p : latest) b.append(',').append(key(p));
            b.append('|');
            for (EventCards.Pick p : upcoming) b.append(',').append(key(p));
            return b.toString();
        }

        SportEvent event(String eventKey) {
            for (SportEvent e : events) if (e.key().equals(eventKey)) return e;
            return null;
        }
    }

    static String id(String sport) { return "sport:" + sport; }

    /** "sport:f1" -> "f1"; null for a club id. */
    static String sportOf(String id) { return id != null && id.startsWith("sport:") ? id.substring(6) : null; }

    /** A pick's identity: the session key, or the event's. */
    static String key(EventCards.Pick p) {
        if (p == null || p.e == null) return "";
        SportEvent.Session s = p.e.session(p.sessionId);
        return (s == null ? p.e.key() : s.key(p.e)) + (p.last ? "~" : "");
    }

    static EventCards.Pick pick(SportEvent e, String sessionId, boolean last) {
        EventCards.Pick p = new EventCards.Pick();
        p.e = e; p.sessionId = sessionId; p.last = last;
        return p;
    }

    static SportData of(List<SportEvent> all, String sport, SportPrefs p, long now) { return of(all, sport, p, now, false, ""); }

    /**
     * The sport's data. hidden: the app hides results (SportText.hidden); focusName: the F1 row's focus name for the
     * label ("Formula 1 · Norris"), "" for none.
     */
    static SportData of(List<SportEvent> all, String sport, SportPrefs p, long now, boolean hidden, String focusName) {
        SportData d = new SportData();
        d.sport = sport;
        d.prefs = p == null ? SportPrefs.none() : p;
        d.hidden = hidden;
        d.label = SportText.sportName(sport) + (focusName == null || focusName.isEmpty() ? "" : " · " + focusName);
        List<SportEvent> mine = shown(all, sport, d.prefs);
        d.events.addAll(mine);
        d.hero = hero(mine, sport, d.prefs, now);
        d.kind = kind(d.hero);
        d.latest.addAll(latest(mine, sport, d.prefs, d.hero, now));
        d.upcoming.addAll(upcoming(mine, sport, d.hero, now));
        rail(d, mine, now);
        return d;
    }

    /** The sport's events the viewer's choices show (Sports.shows), by start. */
    static List<SportEvent> shown(List<SportEvent> all, String sport, SportPrefs p) {
        List<SportEvent> out = new ArrayList<>();
        if (all != null) for (SportEvent e : all) if (sport.equals(e.sport) && Sports.shows(p, e) && !e.sessions.isEmpty()) out.add(e);
        Collections.sort(out, BY_START);
        return out;
    }

    static final Comparator<SportEvent> BY_START = new Comparator<SportEvent>() {
        public int compare(SportEvent a, SportEvent b) { return Long.compare(a.start, b.start); }
    };

    // ---------- the hero ----------
    /** A session being played, else a fresh result (6 h), else the next session (F1) or event (UFC). */
    static EventCards.Pick hero(List<SportEvent> mine, String sport, SportPrefs p, long now) {
        EventCards.Pick live = live(mine, sport, p);
        if (live != null) return live;
        EventCards.Pick fresh = fresh(mine, sport, p, now);
        if (fresh != null) return fresh;
        EventCards.Pick card = null;
        try { card = EventCards.pickNow(mine, sport, p, now); } catch (RuntimeException ignored) { }
        if (card != null && card.e != null && !card.last) {
            if (SportEvent.UFC.equals(sport)) {  // the card picks its own segment (sessionId null): the app names it
                SportEvent.Session main = mainSession(card.e);
                if (main != null && !"post".equals(card.e.state)) return pick(card.e, segmentOf(card.e, main, p), false);
            } else {
                SportEvent.Session s = card.e.session(card.sessionId);
                if (s != null && !"post".equals(s.state)) return card;
            }
        }
        return next(mine, sport, p, now);
    }

    static EventCards.Pick live(List<SportEvent> mine, String sport, SportPrefs p) {
        for (SportEvent e : mine) {
            SportEvent.Session s = e.live();
            if (s == null) continue;
            if (SportEvent.UFC.equals(sport)) {
                SportEvent.Bout b = SportText.headline(e, s, p);
                SportEvent.Session hs = b == null ? null : e.sessionOf(b);
                return pick(e, (hs != null && "in".equals(hs.state) ? hs : s).id, false);
            }
            return pick(e, s.id, false);
        }
        return null;
    }

    /** A result still news: F1's race or sprint, UFC's card, until 6 h after it ended (HomeModel.RESULT_HERO). */
    static EventCards.Pick fresh(List<SportEvent> mine, String sport, SportPrefs p, long now) {
        EventCards.Pick best = null;
        long bestAt = 0;
        for (SportEvent e : mine) {
            if (SportEvent.F1.equals(sport)) {
                SportEvent.Session s = e.lastDone(MAIN_RESULTS);
                if (s == null) continue;
                long end = s.start + SportEngine.typicalMinutes(s.kind) * 60000L;
                if (end <= now && now - end < HomeModel.RESULT_HERO && s.start > bestAt && nothingLater(e, s)) { best = pick(e, s.id, true); bestAt = s.start; }
            } else if ("post".equals(e.state)) {
                SportEvent.Session main = mainSession(e);
                long end = SportEngine.mainStart(e) + 4 * H;
                if (main != null && end <= now + 4 * H && now - end < HomeModel.RESULT_HERO && e.start > bestAt) {
                    best = pick(e, segmentOf(e, main, p), true);
                    bestAt = e.start;
                }
            }
        }
        return best;
    }

    /** No session of the weekend is still to come after s (a sprint's result is not the hero on race day). */
    private static boolean nothingLater(SportEvent e, SportEvent.Session s) {
        for (SportEvent.Session x : e.sessions) if (x.start > s.start && !"post".equals(x.state) && !"off".equals(x.state)) return false;
        return true;
    }

    /** F1: the next session (a cancelled weekend: its race, while it is due); UFC: the next event's main card (or a followed fighter's segment). */
    static EventCards.Pick next(List<SportEvent> mine, String sport, SportPrefs p, long now) {
        for (SportEvent e : mine) {
            if (SportEvent.F1.equals(sport)) {
                SportEvent.Session s = e.next(now);
                if (s != null) return pick(e, s.id, false);
                if ("off".equals(e.state) && e.end > now - 12 * H) {
                    SportEvent.Session r = race(e);
                    return pick(e, r == null ? null : r.id, false);
                }
            } else {
                if ("post".equals(e.state)) continue;
                boolean coming = "in".equals(e.state) || SportEngine.mainStart(e) > now - 4 * H;
                if (!coming && !"off".equals(e.state)) continue;
                if ("off".equals(e.state) && e.end < now - 12 * H) continue;
                SportEvent.Session main = mainSession(e);
                return pick(e, main == null ? null : segmentOf(e, main, p), false);
            }
        }
        return null;
    }

    /** UFC: the segment the headline bout is in (a followed fighter's), else the main card's id. */
    static String segmentOf(SportEvent e, SportEvent.Session main, SportPrefs p) {
        if (p != null) for (String f : p.fighters) {
            SportEvent.Bout b = e.boutOf(f);
            SportEvent.Session s = b == null ? null : e.sessionOf(b);
            if (s != null) return s.id;
        }
        return main.id;
    }

    /** UFC: the MAIN segment, else the last segment. */
    static SportEvent.Session mainSession(SportEvent e) {
        SportEvent.Session last = null;
        for (SportEvent.Session s : e.byStart()) { if ("MAIN".equals(s.kind)) return s; last = s; }
        return last;
    }

    /** F1: the race, else the last session. */
    static SportEvent.Session race(SportEvent e) {
        SportEvent.Session last = null;
        for (SportEvent.Session s : e.byStart()) { if ("RACE".equals(s.kind)) return s; last = s; }
        return last;
    }

    static HomeModel.Kind kind(EventCards.Pick p) {
        if (p == null || p.e == null) return HomeModel.Kind.NONE;
        SportEvent.Session s = p.e.session(p.sessionId);
        if (SportEvent.UFC.equals(p.e.sport) && "in".equals(p.e.state) && !p.last) return HomeModel.Kind.LIVE;
        String st = s == null ? p.e.state : s.state;
        if ("in".equals(st)) return HomeModel.Kind.LIVE;
        if ("post".equals(st) || p.last) return HomeModel.Kind.RESULT;
        if ("off".equals(st)) return HomeModel.Kind.OFF;
        return HomeModel.Kind.NEXT;
    }

    // ---------- latest and upcoming ----------
    /**
     * The two latest results, newest first, the hero's excepted: F1's scoring sessions (the card's "last" pick first),
     * UFC's finished events (their headline bout).
     */
    static List<EventCards.Pick> latest(List<SportEvent> mine, String sport, SportPrefs p, EventCards.Pick hero, long now) {
        List<EventCards.Pick> out = new ArrayList<>();
        String heroKey = hero == null ? "" : key(hero).replace("~", "");
        if (SportEvent.F1.equals(sport)) {
            List<SportEvent.Session> done = new ArrayList<>();
            final java.util.Map<SportEvent.Session, SportEvent> of = new java.util.IdentityHashMap<>();
            for (SportEvent e : mine) for (SportEvent.Session s : e.sessions)
                if ("post".equals(s.state) && SCORING.contains(s.kind) && s.start <= now && !s.order.isEmpty()) { done.add(s); of.put(s, e); }
            Collections.sort(done, new Comparator<SportEvent.Session>() {
                public int compare(SportEvent.Session a, SportEvent.Session b) { return Long.compare(b.start, a.start); }
            });
            // a weekend's qualifying is news only until its next session: the race result takes over
            Set<String> weekends = new HashSet<>();
            EventCards.Pick last = null;  // the card's "last" pick comes first (stream D's rule for qualifying)
            try { last = EventCards.pickLast(mine, sport, p, now); } catch (RuntimeException ignored) { }
            if (last != null && last.e != null && last.e.session(last.sessionId) != null && !SportModel.key(last).replace("~", "").equals(heroKey)) {
                out.add(pick(last.e, last.sessionId, true));
                weekends.add(last.e.id);
            }
            for (SportEvent.Session s : done) {
                SportEvent e = of.get(s);
                if (out.size() >= 2) break;
                if (s.key(e).equals(heroKey)) { weekends.add(e.id); continue; }
                if (weekends.contains(e.id)) continue;
                weekends.add(e.id);
                out.add(pick(e, s.id, true));
            }
        } else {
            for (int i = mine.size() - 1; i >= 0 && out.size() < 2; i--) {
                SportEvent e = mine.get(i);
                if (!"post".equals(e.state)) continue;
                SportEvent.Session main = mainSession(e);
                if (main == null) continue;
                EventCards.Pick x = pick(e, segmentOf(e, main, p), true);
                if (key(x).replace("~", "").equals(heroKey) && hero != null && hero.last) continue;
                out.add(x);
            }
        }
        return out;
    }

    /**
     * What comes next. F1: every session of the next two weekends (the hero's weekend first; finished sessions of the
     * weekend under way stay out). UFC: the hero's card's segments still to come (main card first, the order of interest),
     * then the next events' main cards.
     */
    static List<EventCards.Pick> upcoming(List<SportEvent> mine, String sport, EventCards.Pick hero, long now) {
        List<EventCards.Pick> out = new ArrayList<>();
        if (SportEvent.F1.equals(sport)) {
            int weekends = 0;
            for (SportEvent e : mine) {
                boolean any = false;
                for (SportEvent.Session s : e.byStart()) {
                    boolean coming = "pre".equals(s.state) && (s.tbd || s.start > now - 2 * H) || "in".equals(s.state)
                        || "off".equals(s.state) && s.start > now - 12 * H;
                    if (!coming) continue;
                    out.add(pick(e, s.id, false));
                    any = true;
                }
                if (any && ++weekends >= WEEKENDS) break;
            }
            return out;
        }
        SportEvent card = hero != null && !hero.last && hero.e != null ? hero.e : null;
        if (card != null) {
            List<SportEvent.Session> segs = card.byStart();
            Collections.reverse(segs);  // main card, prelims, early prelims
            for (SportEvent.Session s : segs) if (!"post".equals(s.state)) out.add(pick(card, s.id, false));
        }
        int n = 0;
        for (SportEvent e : mine) {
            if (e == card || "post".equals(e.state) || SportEngine.mainStart(e) < now - 4 * H) continue;
            SportEvent.Session main = mainSession(e);
            if (main == null) continue;
            out.add(pick(e, main.id, false));
            if (++n >= NEXT_EVENTS) break;
        }
        return out;
    }

    // ---------- the rail ----------
    /** The rail's line: LIVE · 34/50 (live red), a result for 24 h ("NOR won"; hidden: "Final"), else the next start. */
    static void rail(SportData d, List<SportEvent> mine, long now) {
        d.railLine = "";
        d.railLive = false;
        EventCards.Pick live = live(mine, d.sport, d.prefs);
        if (live != null) {
            d.railLive = true;
            d.railLine = liveLine(live.e, live.e.session(live.sessionId), d.prefs);
            return;
        }
        // a result for a day after it ended
        if (SportEvent.F1.equals(d.sport)) {
            SportEvent.Session best = null;
            for (SportEvent e : mine) {
                SportEvent.Session s = e.lastDone(MAIN_RESULTS);
                if (s != null && (best == null || s.start > best.start)) best = s;
            }
            if (best != null) {
                long end = best.start + SportEngine.typicalMinutes(best.kind) * 60000L;
                if (end <= now && now - end < HomeModel.RESULT_RAIL) {
                    SportEvent.Entrant w = SportText.winner(best);
                    d.railLine = d.hidden || w == null ? SportText.t("final") : SportText.f("won", w.code.isEmpty() ? SportText.driverSurname(w) : w.code);
                    return;
                }
            }
        } else {
            SportEvent last = null;
            for (SportEvent e : mine) if ("post".equals(e.state)) last = e;
            if (last != null) {
                long end = SportEngine.mainStart(last) + 3 * H;
                if (end <= now + 3 * H && now - end < HomeModel.RESULT_RAIL) {
                    SportEvent.Bout b = SportText.headline(last, null, d.prefs);
                    SportEvent.Fighter w = SportText.winner(b);
                    d.railLine = d.hidden ? SportText.t("final")
                        : w != null ? SportText.f("won", SportText.surname(w)) : b != null && b.outcome == 3 ? SportText.t("draw")
                        : b != null && b.outcome == 4 ? SportText.t("nc") : SportText.t("final");
                    return;
                }
            }
        }
        EventCards.Pick n = next(mine, d.sport, d.prefs, now);  // the next session (F1), the main card (UFC)
        if (n == null || n.e == null) return;
        SportEvent.Session s = n.e.session(n.sessionId);
        if (s == null) return;
        if ("off".equals(s.state)) { d.railLine = SportText.detail(s); return; }
        d.railLine = SportText.railNext(now, s.start, s.tbd);
    }

    /**
     * "LIVE · 34/50", "LIVE · LAP 12", "LIVE · QUALI"; UFC "LIVE · R2". The lap and the round only while
     * F1Feed.LIVE_LAP_TRUSTED / UfcFeed.LIVE_ROUND_TRUSTED: otherwise "LIVE · Race" and "LIVE".
     */
    static String liveLine(SportEvent e, SportEvent.Session s, SportPrefs p) {
        String live = I18n.t("live");
        if (s == null) return live;
        if (SportEvent.F1.equals(e.sport)) {
            if ("RED_FLAG".equals(s.detail) || "SUSPENDED".equals(s.detail)) return live + " · " + SportText.t("red_flag");
            int lap = F1Feed.shownLap(s);
            if (MAIN_RESULTS.contains(s.kind) && lap > 0) return live + " · " + (s.laps > 0 ? lap + "/" + s.laps : SportText.f("lap_n", lap));
            return live + " · " + SportText.sessionShort(s);
        }
        SportEvent.Bout now = inCage(e);
        if (UfcFeed.shownRound(now) > 0) return live + " · R" + UfcFeed.shownRound(now);
        return live;
    }

    /** UFC: the bout in the cage (state in), else null. */
    static SportEvent.Bout inCage(SportEvent e) {
        if (e == null) return null;
        for (SportEvent.Session s : e.sessions) for (SportEvent.Bout b : s.bouts) if ("in".equals(b.state)) return b;
        return null;
    }

    /** UFC between bouts: the next bout to fight (the highest order still pre in the live segment, else in the next one). */
    static SportEvent.Bout nextBout(SportEvent e) {
        if (e == null) return null;
        for (SportEvent.Session s : e.byStart()) {
            SportEvent.Bout best = null;
            for (SportEvent.Bout b : s.bouts) if ("pre".equals(b.state) && (best == null || b.order > best.order)) best = b;
            if (best != null) return best;
        }
        return null;
    }

    /** The bouts of a segment in billing order (main event first). */
    static List<SportEvent.Bout> bouts(SportEvent.Session s) {
        List<SportEvent.Bout> out = new ArrayList<>(s.bouts);
        Collections.sort(out, new Comparator<SportEvent.Bout>() {
            public int compare(SportEvent.Bout a, SportEvent.Bout b) { return Integer.compare(a.order, b.order); }
        });
        return out;
    }

    /** UFC segments in the order of interest: main card, prelims, early prelims. */
    static List<SportEvent.Session> segments(SportEvent e) {
        List<SportEvent.Session> out = e.byStart();
        Collections.reverse(out);
        return out;
    }

    /** F1: the session the weekend screen focuses first: the live one, else the next, else the race. */
    static SportEvent.Session launchSession(SportEvent e, long now) {
        SportEvent.Session s = e.live();
        if (s == null) s = e.next(now);
        if (s == null) s = race(e);
        return s;
    }
}
