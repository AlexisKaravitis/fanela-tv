package app.fanela.tv;

import static app.fanela.tv.SportTestKit.H;
import static app.fanela.tv.SportTestKit.LV_FP1;
import static app.fanela.tv.SportTestKit.LV_QUAL;
import static app.fanela.tv.SportTestKit.LV_RACE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** SportEvent's helpers (§1.2) and its JSON round-trip (§1.3). */
public class SportEventTest {
    @Test public void keysNameTheEventAndTheSession() {
        SportEvent e = SportTestKit.lasVegas();
        assertEquals("f1|espn:600057449", e.key());
        SportEvent.Session race = e.session("6000574495");
        assertEquals("f1|espn:600057449#6000574495", race.key(e));
        assertEquals("f1|espn:600057449", SportEvent.eventKey("f1|espn:600057449#401839137"));
        assertEquals("401839137", SportEvent.sessionId("f1|espn:600057449#401839137"));
        assertEquals("ufc|espn:600061400", SportEvent.eventKey("ufc|espn:600061400"));
        assertNull(SportEvent.sessionId("ufc|espn:600061400"));
        assertNull(SportEvent.sessionId("ufc|espn:600061400#"));
        assertNull(SportEvent.eventKey(null));
        assertTrue(SportEvent.isEventKey("ufc|espn:600061400#main"));
        assertFalse(SportEvent.isEventKey("435|espn:401896730"));
        assertNull(e.session("nope"));
        assertNull(e.session(null));
    }

    @Test public void nextLiveAndLastDone() {
        SportEvent e = SportTestKit.lasVegas();
        assertEquals("FP1", e.next(LV_FP1 - H).kind);
        assertNull(e.live());
        e.sessions.get(0).state = "in";
        assertEquals("FP1", e.live().kind);
        assertEquals("FP2", e.next(LV_FP1 + 10 * 60000L).kind);  // FP1 started: the next one is FP2
        e.sessions.get(0).state = "post";
        e.sessions.get(1).state = "off";  // cancelled: skipped
        assertEquals("FP3", e.next(LV_FP1 + 2 * H).kind);
        assertEquals("FP1", e.lastDone(null).kind);
        e.sessions.get(3).state = "post";
        assertEquals("QUAL", e.lastDone(null).kind);
        assertEquals("QUAL", e.lastDone(new HashSet<>(Arrays.asList("QUAL", "RACE", "SPRINT", "SQ"))).kind);
        assertEquals("FP1", e.lastDone(new HashSet<>(Arrays.asList("FP1", "FP2"))).kind);
        assertNull(e.lastDone(new HashSet<>(Arrays.asList("RACE"))));
        e.sessions.get(4).state = "post";
        assertNull(e.next(LV_RACE + 3 * H));
        assertEquals("RACE", e.lastDone(new HashSet<>(Arrays.asList("QUAL", "RACE"))).kind);
        assertEquals(LV_QUAL, e.session("6000574494").start);
    }

    @Test public void aTbdSessionStillCountsAsToCome() {
        SportEvent e = SportTestKit.lasVegas();
        e.sessions.get(4).tbd = true;
        e.sessions.get(4).start = LV_FP1;  // a placeholder time in the past
        for (int i = 0; i < 4; i++) e.sessions.get(i).state = "post";
        assertEquals("RACE", e.next(LV_RACE).kind);
    }

    @Test public void theMainEventAndAFightersBout() {
        SportEvent e = SportTestKit.ufc334();
        SportEvent.Bout main = e.mainEvent();
        assertEquals(1, main.order);
        assertEquals("Ciryl Gane", main.a.name);
        assertSame(main, e.boutOf("4049391"));
        assertEquals("main", e.sessionOf(main).id);
        assertEquals(4, e.boutOf("21").order);
        assertNull(e.boutOf("999"));
        assertNull(e.boutOf(""));
        assertNull(SportTestKit.lasVegas().mainEvent());
    }

    @Test public void aCopyIsDeepAndSignsTheSame() {
        SportEvent e = SportTestKit.ufc334();
        e.sessions.get(2).watch.channel = "Magenta Sport 6";
        SportEvent c = e.copy();
        assertEquals(e.signature(), c.signature());
        assertNotSame(e.sessions.get(2), c.sessions.get(2));
        assertNotSame(e.mainEvent().a, c.mainEvent().a);
        c.mainEvent().a.recordBefore = "13-2-0";
        assertEquals("", e.mainEvent().a.recordBefore);
        assertNotEquals(e.signature(), c.signature());
        c = e.copy();
        c.sessions.get(2).watch.channel = "ANT1";
        assertEquals("Magenta Sport 6", e.sessions.get(2).watch.channel);
        assertNotEquals(e.signature(), c.signature());  // the channel changes the card
        c = e.copy();
        c.fetchedAt = 99; c.misses = 2; c.sessions.get(0).seenLiveAt = 5; c.sessions.get(0).statusName = "STATUS_X";
        assertEquals(e.signature(), c.signature());  // bookkeeping does not
    }

    @Test public void theSignatureSeesLiveFields() {
        SportEvent e = SportTestKit.lasVegas(), c = e.copy();
        c.sessions.get(4).lap = 12;
        assertNotEquals(e.signature(), c.signature());
        c = e.copy();
        c.sessions.get(4).order.add(SportTestKit.entrant("5579", "NOR", 1));
        assertNotEquals(e.signature(), c.signature());
        c = e.copy();
        c.name = "Las Vegas Grand Prix";
        assertNotEquals(e.signature(), c.signature());
    }

    @Test public void recordsBeforeABoutAreRebuiltFromTheResult() {
        assertEquals("13-2-0", SportEvent.recordBefore("14-2-0", 1, true));   // a won
        assertEquals("9-0-0", SportEvent.recordBefore("9-1-0", 1, false));    // b lost
        assertEquals("9-0-0", SportEvent.recordBefore("10-0-0", 2, false));   // b won
        assertEquals("14-1-0", SportEvent.recordBefore("14-2-0", 2, true));   // a lost
        assertEquals("14-2-0", SportEvent.recordBefore("14-2-1", 3, true));   // draw: D-1 for both
        assertEquals("9-4-0", SportEvent.recordBefore("9-4-1", 3, false));
        assertEquals("14-2-0", SportEvent.recordBefore("14-2-0", 4, true));   // no contest: unchanged
        assertEquals("14-2-0", SportEvent.recordBefore("14-2-0", 0, true));   // unknown: unchanged
        assertEquals("0-2-0", SportEvent.recordBefore("0-2-0", 1, true));     // nothing to take back
        assertEquals("", SportEvent.recordBefore("", 1, true));
        assertEquals("n/a", SportEvent.recordBefore("n/a", 1, true));
        assertNull(SportEvent.recordBefore(null, 1, true));
    }

    @Test public void asMatchCarriesOnlyTheChannel() {
        SportEvent.SessionWatch w = new SportEvent.SessionWatch();
        w.channel = "ANT1"; w.app = Cards.MAGENTA; w.no = 107; w.sure = true; w.known = true;
        Match m = w.asMatch(LV_RACE, SportEvent.F1);
        assertEquals("ANT1", m.channel);
        assertEquals(107, m.channelNo);
        assertEquals(LV_RACE, m.start);
        assertEquals("f1", m.league);
        assertTrue(m.channelSure);
        assertEquals("", m.listedBy);
        CardModel.Channel ch = CardModel.channel(m);
        assertEquals(107, ch.number);
        assertFalse(ch.likely);
    }

    @Test public void jsonRoundTripKeepsEveryField() throws Exception {
        SportEvent f1 = SportTestKit.lasVegas();
        SportEvent.Session race = f1.sessions.get(4);
        race.state = "post"; race.detail = ""; race.statusName = "STATUS_FINAL"; race.lap = 50; race.laps = 50; race.seenLiveAt = 7;
        race.order.add(SportTestKit.entrant("5579", "NOR", 1));
        race.order.get(0).gap = "+1.2s"; race.order.get(0).flag = "https://a.espncdn.com/i/teamlogos/countries/500/gbr.png";
        race.watch.channel = "ANT1"; race.watch.app = Cards.MAGENTA; race.watch.no = 107; race.watch.sure = true; race.watch.known = true;
        f1.sessions.get(0).watch.channel = "ANT1+"; f1.sessions.get(0).watch.app = Cards.ANT1; f1.sessions.get(0).watch.listedBy = "table";
        f1.sessions.get(0).watch.known = true; f1.sessions.get(0).tbd = true;
        f1.fetchedAt = 123; f1.misses = 1; f1.state = "post";
        SportEvent ufc = SportTestKit.ufc334();
        SportEvent.Bout main = ufc.mainEvent();
        main.state = "post"; main.outcome = 1; main.method = "KO/TKO"; main.methodDetail = "Punch"; main.round = 3; main.clock = "2:27";
        main.a.recordBefore = "13-2-0"; main.a.stanceLeft = "l.png"; main.a.stanceRight = "r.png"; main.a.country = "France";
        ufc.sessions.get(2).watch.logo = "https://x/logo.png";
        SportEvent.Entrant st = SportTestKit.entrant("1", "VER", 2);
        st.points = 341.5f;
        f1.sessions.get(3).order.add(st);

        List<SportEvent> back = EventStore.fromJson(EventStore.toJson(Arrays.asList(f1, ufc), 1000));
        assertEquals(2, back.size());
        assertEquals(f1.signature(), back.get(0).signature());
        assertEquals(ufc.signature(), back.get(1).signature());
        SportEvent b = back.get(0);
        assertEquals(123, b.fetchedAt);
        assertEquals(1, b.misses);
        assertEquals(7, b.sessions.get(4).seenLiveAt);
        assertEquals("STATUS_FINAL", b.sessions.get(4).statusName);
        assertEquals(0xFFFF8000, b.sessions.get(4).order.get(0).color);
        assertEquals(341.5f, b.sessions.get(3).order.get(0).points, 0.001);
        assertEquals(Cards.MAGENTA, b.sessions.get(4).watch.app);
        assertNull(b.sessions.get(4).watch.logo);
        assertNull(b.sessions.get(1).watch.app);  // never set: still null, not ""
        assertEquals(6.116f, b.lengthKm, 0.0001);
        assertEquals("https://x/logo.png", back.get(1).sessions.get(2).watch.logo);
        assertEquals("13-2-0", back.get(1).mainEvent().a.recordBefore);
        assertTrue(back.get(1).numbered);
        assertEquals(334, back.get(1).number);
    }

    @Test public void aBrokenFileOrEventIsSkipped() {
        assertTrue(EventStore.fromJson("not json").isEmpty());
        assertTrue(EventStore.fromJson("{\"v\":2,\"events\":[{\"sport\":\"f1\",\"id\":\"1\"}]}").isEmpty());  // another version
        List<SportEvent> l = EventStore.fromJson("{\"v\":1,\"events\":[{\"sport\":\"f1\",\"id\":\"1\"},{\"sport\":\"golf\",\"id\":\"2\"},"
            + "{\"sport\":\"ufc\"},5,{\"sport\":\"ufc\",\"id\":\"3\",\"sessions\":[{\"kind\":\"MAIN\"},{\"id\":\"main\",\"kind\":\"MAIN\"}]}]}");
        assertEquals(2, l.size());
        assertEquals("1", l.get(0).id);
        assertEquals(1, l.get(1).sessions.size());  // a session without an id is dropped
    }

    @Test public void colours() {
        assertEquals(0xFFFF8000, EventStore.color("FF8000"));
        assertEquals(0xFF00A19C, EventStore.color("#00a19c"));
        assertEquals(0, EventStore.color("red"));
        assertEquals(0, EventStore.color(null));
        assertEquals("FF8000", EventStore.hex(0xFFFF8000));
        assertEquals("", EventStore.hex(0));
    }
}
