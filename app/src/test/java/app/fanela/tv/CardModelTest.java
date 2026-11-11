package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import org.junit.After;
import org.junit.Test;

/** What every card style draws: one model per match, the same words in every style. */
public class CardModelTest {
    @After public void english() { I18n.use("en"); }

    /** Today at hh:mm local time. */
    static long at(int dayOffset, int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, hh); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static Match next(long start) {
        Match m = StoreTest.match("1", "pre", null, null);
        m.start = start; m.channel = "Novasports Prime"; m.channelNo = 318; m.channelSure = true; m.channelKnown = true;
        m.home = "Olympiacos"; m.away = "Dubai"; m.comp = "euroleague"; m.team = "BC";
        return m;
    }

    @Test public void tomorrowIsTheStateLineWithTheDateBesideIt() {
        long noon = at(0, 12, 0);
        CardModel c = CardModel.of(next(at(1, 21, 15)), true, false, noon);
        assertEquals(CardModel.State.NEXT, c.state);
        assertEquals("TOMORROW", c.stateLine);
        assertTrue("the date isn't repeated beside a soon word", c.dateLine.isEmpty());
        assertTrue(c.soon);
        assertEquals("v", c.home ? "v" : "@");
        assertEquals("Dubai", c.opponent);
        assertNotNull(c.channel);
        assertFalse(c.channel.likely);
        assertEquals(318, c.channel.number);
    }

    @Test public void withACountdownSurfaceTheSoonWordsCountDown() {
        long now = at(0, 19, 10);
        assertEquals("IN 2 H", CardModel.of(next(at(0, 21, 15)), true, false, now, true).stateLine);
        assertEquals("IN 1 H", CardModel.of(next(at(0, 20, 15)), true, false, now, true).stateLine);
        assertEquals("IN 3 H", CardModel.of(next(at(0, 22, 0)), true, false, now, true).stateLine);
        assertEquals("IN 40 MIN", CardModel.of(next(at(0, 19, 50)), true, false, now, true).stateLine);
        assertEquals("TODAY", CardModel.of(next(at(0, 23, 30)), true, false, at(0, 9, 0), true).stateLine);
    }

    /** A bitmap card is redrawn only by the 15-minute job (and not when the screen comes on): no countdown in it. */
    @Test public void bitmapCardsSayTodayNotACountdownThatGoesStale() {
        long now = at(0, 19, 10);
        for (long start : new long[]{at(0, 19, 30), at(0, 20, 15), at(0, 21, 15), at(0, 22, 0)}) {
            CardModel c = CardModel.of(next(start), true, false, now);
            assertEquals("TODAY", c.stateLine);
            assertTrue("the kick-off hero carries the time", c.time.matches("[0-9]{1,2}:[0-9]{2}"));
        }
        assertEquals("TOMORROW", CardModel.of(next(at(1, 0, 30)), true, false, at(0, 23, 50)).stateLine);
    }

    @Test public void theMinuteCountdownNeverReachesSixty() {
        assertEquals(59, CardModel.minutesLeft(59 * 60000L + 45000L));
        assertEquals(59, CardModel.minutesLeft(59 * 60000L + 31000L));
        assertEquals(45, CardModel.minutesLeft(45 * 60000L));
        assertEquals(1, CardModel.minutesLeft(10000L));
        long now = at(0, 12, 0);
        assertEquals("IN 59 MIN", CardModel.of(next(now + 59 * 60000L + 45000L), true, false, now, true).stateLine);
        I18n.use("el");
        assertEquals("ΣΕ 59 Λ", CardModel.of(next(now + 59 * 60000L + 45000L), true, false, now, true).stateLine);
    }

    @Test public void laterMatchesShowTheirDayAndNoSoonFlag() {
        CardModel c = CardModel.of(next(at(5, 19, 30)), true, false, at(0, 12, 0));
        assertFalse(c.soon);
        assertTrue(c.dateLine.isEmpty());
        assertTrue(c.stateLine, c.stateLine.matches("[A-Z]{3} [0-9]{1,2} [A-Z]{3}"));
    }

    @Test public void liveCarriesTheClockAndScoresInBothOrders() {
        Match m = StoreTest.match("2", "in", "2", "0");
        m.detail = "67'"; m.clubIsHome = false;  // the followed club away
        CardModel c = CardModel.of(m, true, false, System.currentTimeMillis());
        assertEquals(CardModel.State.LIVE, c.state);
        assertEquals("67′", c.clock);
        assertEquals("@", c.vs);
        m.clubIsHome = true;  // at home: the VS mark (owner, fx6: "VS instead of V")
        assertEquals("VS", CardModel.of(m, true, false, System.currentTimeMillis()).vs);
        assertEquals("Olympiacos vs PAOK", StoreTest.match("2", "in", "2", "0").title());
        assertEquals("2", c.homeScore);
        assertEquals("0", c.myScore);
        assertEquals("2", c.oppScore);
        assertFalse(c.home);
    }

    @Test public void resultsSayTheOutcomeAndDropTheChannel() {
        Match m = StoreTest.match("3", "post", "57", "62");
        m.clubWon = -1;
        CardModel c = CardModel.of(m, true, false, System.currentTimeMillis());
        assertEquals(CardModel.State.RESULT, c.state);
        assertEquals("LOSS", c.outcomeWord);
        assertEquals(-1, c.outcome);
        assertNull(c.channel);
        Match p = StoreTest.match("4", "post", "1", "1");
        p.clubWon = 1; p.pens = "4–3";
        CardModel w = CardModel.of(p, true, false, System.currentTimeMillis());
        assertEquals("WIN", w.outcomeWord);
        assertEquals("PENS", w.stateExtra);
        assertEquals("FINAL", w.stateLine);
    }

    @Test public void anUnknownTimeIsADimTba() {
        Match m = next(at(18, 12, 0));
        m.tbd = true;
        CardModel c = CardModel.of(m, true, false, at(0, 12, 0));
        assertEquals("TBA", c.time);
        assertTrue(c.heroDim);
    }

    @Test public void hiddenScoresKeepTheStateButNotTheNumbersOrOutcome() {
        Match m = StoreTest.match("5", "post", "3", "1");
        m.clubWon = 1;
        CardModel c = CardModel.of(m, false, false, System.currentTimeMillis());
        assertTrue(c.scoresHidden);
        assertEquals("", c.outcomeWord);
        assertEquals("Olympiacos vs PAOK", c.title());
    }

    @Test public void theLauncherTitleDoesNotSpoilWhenScoresAreHidden() {
        Match m = StoreTest.match("6", "post", "3", "1");
        m.clubWon = 1;
        Cards.Ref r = new Cards.Ref();
        r.m = m; r.last = true;
        assertFalse(Cards.headlineNoScore(r).contains("3"));
        assertTrue(Cards.headlineNoScore(r).contains("Olympiacos vs PAOK"));
        Match l = StoreTest.match("7", "in", "2", "0");
        Cards.Ref lr = new Cards.Ref();
        lr.m = l;
        assertFalse(Cards.headlineNoScore(lr).contains("2"));
    }

    @Test public void aGuessIsLikely() {
        Match m = next(at(3, 19, 30));
        m.channelSure = false; m.channelNo = 0; m.channel = "Novasports";
        CardModel.Channel ch = CardModel.of(m, true, false, at(0, 12, 0)).channel;
        assertTrue(ch.likely);
        assertEquals(0, ch.number);
    }

    @Test public void postponedKeepsItsDate() {
        Match m = next(at(3, 19, 30));
        m.state = "off"; m.detail = "POSTPONED";
        CardModel c = CardModel.of(m, true, false, at(0, 12, 0));
        assertEquals(CardModel.State.OFF, c.state);
        assertEquals("POSTPONED", c.stateLine);
        assertFalse(c.dateLine.isEmpty());
        assertTrue(c.dateDim);
        assertEquals("TBA", c.time);
        assertTrue(c.heroDim);
        assertNull("no channel on a postponed card", c.channel);
    }

    @Test public void greekWordsAreCapitalsWithoutAccents() {
        I18n.use("el");
        CardModel c = CardModel.of(next(at(1, 21, 15)), true, false, at(0, 12, 0));
        assertEquals("ΑΥΡΙΟ", c.stateLine);
        assertEquals("ΣΕ 2 Ω", CardModel.of(next(at(0, 21, 15)), true, false, at(0, 19, 10), true).stateLine);
        assertEquals("ΗΜ.", CardModel.liveClock("HT"));
    }

    /** One hedge word everywhere: the cards say ΠΙΘΑΝΟ as the app does (I18n "likely" = Πιθανό). */
    @Test public void greekLikelyMatchesTheApp() {
        I18n.use("el");
        assertEquals("ΠΙΘΑΝΟ", CardModel.w("likely"));
        assertEquals(Cards.caps(I18n.t("likely")), CardModel.w("likely"));
        assertEquals("TBA", CardModel.w("tba"));  // the card hero for an unknown time stays TBA in both languages
    }

    @Test public void theFeedsPhaseCodesBecomeCardWords() {
        assertEquals("ET HT", CardModel.liveClock("ET_HT"));
        assertEquals("ET", CardModel.liveClock("END_90"));
        assertEquals("PENS", CardModel.liveClock("END_120"));
        assertEquals("PENS", CardModel.liveClock("SHOOTOUT"));
        assertEquals("BREAK", CardModel.liveClock("BREAK"));
        assertEquals("", CardModel.liveClock("END_OF_SOMETHING"));  // a code without a card word is never shown raw
        assertEquals("", CardModel.liveClock("IN_PROGRESS"));
        assertEquals("67′", CardModel.liveClock("67'"));
        assertEquals("90′+3′", CardModel.liveClock("90'+3'"));
        assertEquals("Q3 5:12", CardModel.liveClock("Q3 5:12"));
        assertEquals("OT 2:10", CardModel.liveClock("OT 2:10"));
        assertEquals("OT2 1:00", CardModel.liveClock("OT2 1:00"));
        Match m = StoreTest.match("et", "in", "1", "1");
        m.detail = "ET_HT";
        CardModel c = CardModel.of(m, true, false, System.currentTimeMillis());
        assertEquals(CardModel.State.LIVE, c.state);
        assertEquals("LIVE", c.stateLine);
        assertEquals("ET HT", c.clock);
        I18n.use("el");
        assertEquals("ΗΜ. ΠΑΡ.", CardModel.liveClock("ET_HT"));
        assertEquals("ΠΑΡ.", CardModel.liveClock("END_90"));
        assertEquals("ΠΕΝ.", CardModel.liveClock("END_120"));
        assertEquals("ΠΕΝ.", CardModel.liveClock("SHOOTOUT"));
        assertEquals("ΔΙΑΛΕΙΜΜΑ", CardModel.liveClock("BREAK"));
        assertEquals("ΠΑΡ. 2:10", CardModel.liveClock("OT 2:10"));
        assertEquals("2Η ΠΑΡ. 1:00", CardModel.liveClock("OT2 1:00"));
        assertEquals("3Η ΠΑΡ.", CardModel.liveClock("OT3"));
        assertEquals("ΠΑΡ.", CardModel.liveClock("OT"));
    }

    @Test public void awaitingAResultIsNotLive() {
        Match m = StoreTest.match("aw", "in", null, null);
        m.detail = "AWAITING";
        CardModel c = CardModel.of(m, true, false, System.currentTimeMillis());
        assertEquals(CardModel.State.AWAITING, c.state);
        assertEquals("NO RESULT YET", c.stateLine);
        assertEquals("", c.clock);
        assertTrue("the score's place (blank slots: none yet)", c.scoreHero);
        assertNull(c.homeScore);
        assertNull(c.channel);
        Match s = StoreTest.match("aw2", "in", "2", "1");
        s.detail = "AWAITING";
        assertEquals("2", CardModel.of(s, true, false, System.currentTimeMillis()).homeScore);  // a last score is kept
        I18n.use("el");
        assertEquals("ΑΝΑΜΟΝΗ ΣΚΟΡ", CardModel.of(m, true, false, System.currentTimeMillis()).stateLine);
    }

    @Test public void aSuspendedMatchKeepsItsScoreAndNoKickOff() {
        Match m = StoreTest.match("su", "in", "1", "0");
        m.detail = "SUSPENDED";
        CardModel c = CardModel.of(m, true, false, System.currentTimeMillis());
        assertEquals(CardModel.State.SUSPENDED, c.state);
        assertEquals("SUSPENDED", c.stateLine);
        assertTrue(c.scoreHero);
        assertEquals("1", c.homeScore);
        assertEquals("0", c.awayScore);
        assertEquals("no TBA", "", c.time);
        assertEquals("no dimmed original date", "", c.dateLine);
        assertFalse(c.heroDim);
        assertTrue(CardModel.of(m, false, false, System.currentTimeMillis()).scoresHidden);
    }

    @Test public void anAbandonedMatchIsNotPostponed() {
        long now = at(0, 22, 0);
        Match m = next(at(0, 19, 30));
        m.state = "off"; m.detail = "ABANDONED";
        CardModel c = CardModel.of(m, true, false, now);
        assertEquals(CardModel.State.OFF, c.state);
        assertEquals("ABANDONED", c.stateLine);
        assertFalse("its own date, not an original one", c.dateDim);
        assertFalse(c.dateLine.isEmpty());
        assertFalse("no TBA: it won't be rescheduled", "TBA".equals(c.time));
        assertTrue(c.time.matches("[0-9]{1,2}:[0-9]{2}"));
        assertFalse(c.scoreHero);
        Match s = next(at(0, 19, 30));
        s.state = "off"; s.detail = "ABANDONED"; s.hs = "0"; s.as = "1";
        CardModel cs = CardModel.of(s, true, false, now);
        assertTrue("stopped at a score: the score", cs.scoreHero);
        assertEquals("1", cs.awayScore);
        assertEquals("", cs.outcomeWord);
        I18n.use("el");
        assertEquals("ΟΡΙΣΤ. ΔΙΑΚΟΠΗ", CardModel.of(m, true, false, now).stateLine);
    }

    @Test public void aDelayedKickOffSaysSo() {
        long now = at(0, 19, 0);
        Match m = next(at(0, 19, 15));
        m.detail = "DELAYED";
        CardModel c = CardModel.of(m, true, false, now);
        assertEquals(CardModel.State.NEXT, c.state);
        assertEquals("DELAYED", c.stateLine);
        assertEquals("TODAY", c.dateLine);
        assertTrue(c.soon);
        assertTrue(c.time.matches("[0-9]{1,2}:[0-9]{2}"));
        assertNotNull(c.channel);
        assertEquals("DELAYED", CardModel.of(m, true, false, now, true).stateLine);  // never a countdown either
        I18n.use("el");
        assertEquals("ΚΑΘΥΣΤΕΡΗΣΗ", CardModel.of(m, true, false, now).stateLine);
    }

    @Test public void hiddenScoresDontTellThatTheMatchWentToExtraTimeOrPenalties() {
        Match p = StoreTest.match("hp", "post", "1", "1");
        p.clubWon = 1; p.pens = "4–3"; p.detail = "FT_PEN";
        assertEquals("", CardModel.of(p, false, false, System.currentTimeMillis()).stateExtra);
        assertEquals("PENS", CardModel.of(p, true, false, System.currentTimeMillis()).stateExtra);
        Match a = StoreTest.match("ha", "post", "2", "1");
        a.clubWon = 1; a.detail = "FT_AET";
        assertEquals("", CardModel.of(a, false, false, System.currentTimeMillis()).stateExtra);
        assertEquals("AET", CardModel.of(a, true, false, System.currentTimeMillis()).stateExtra);
        Cards.Ref r = new Cards.Ref();
        r.m = p; r.last = true;
        assertEquals("Final: Olympiacos vs PAOK", Cards.headlineNoScore(r));
        r.m = a;
        assertEquals("Final: Olympiacos vs PAOK", Cards.headlineNoScore(r));
    }

    /** Live, scores hidden: a phase that says the match is level (extra time, penalties, overtime) isn't drawn. */
    @Test public void hiddenScoresDontTellALiveMatchIsLevel() {
        assertTrue(CardModel.revealsLevel("ET_HT"));
        assertTrue(CardModel.revealsLevel("END_90"));
        assertTrue(CardModel.revealsLevel("SHOOTOUT"));
        assertTrue(CardModel.revealsLevel("105'"));
        assertTrue(CardModel.revealsLevel("120'+2'"));
        assertTrue(CardModel.revealsLevel("OT 2:10"));
        assertTrue(CardModel.revealsLevel("OT2 1:00"));
        assertFalse(CardModel.revealsLevel("90'+3'"));  // stoppage time
        assertFalse(CardModel.revealsLevel("67'"));
        assertFalse(CardModel.revealsLevel("HT"));
        assertFalse(CardModel.revealsLevel("Q4 0:30"));
        assertFalse(CardModel.revealsLevel(null));
        Match m = StoreTest.match("hs", "in", "2", "2");
        m.detail = "SHOOTOUT";
        assertEquals("", CardModel.of(m, false, false, System.currentTimeMillis()).clock);
        assertEquals("PENS", CardModel.of(m, true, false, System.currentTimeMillis()).clock);
        m.detail = "67'";
        assertEquals("67′", CardModel.of(m, false, false, System.currentTimeMillis()).clock);
    }

    /** Settings redraws its previews when "Show scores" flips only if the card would draw a score. */
    @Test public void showsScoreSaysWhenTheScoresSettingChangesTheCard() {
        Match live = StoreTest.match("l", "in", "2", "0");
        live.detail = "67'";
        assertTrue(CardModel.showsScore(live));
        assertTrue(CardModel.showsScore(StoreTest.match("r", "post", "1", "0")));
        Match sus = StoreTest.match("s", "in", "1", "0");
        sus.detail = "SUSPENDED";
        assertTrue(CardModel.showsScore(sus));
        assertFalse(CardModel.showsScore(next(at(1, 21, 0))));
        Match noScore = StoreTest.match("n", "in", null, null);
        noScore.detail = "IN_PROGRESS";
        assertFalse(CardModel.showsScore(noScore));
        assertFalse(CardModel.showsScore(null));
    }

    @Test public void confirmedNamesAreJoinedBySlashGuessesByOr() {
        Match m = next(at(5, 20, 0));
        m.channel = "Sky Sports · NOW"; m.channelSure = true; m.channelNo = 0; m.listedBy = "ronin"; m.channelApp = null;
        CardModel.Channel ch = CardModel.of(m, true, false, at(0, 12, 0)).channel;
        assertTrue(ch.namesOnly);
        assertFalse(ch.likely);
        assertEquals("SKY SPORTS / NOW", ch.words());
        m.channel = "TNT Sports · Prime Video"; m.channelSure = false; m.listedBy = "table";
        CardModel.Channel g = CardModel.of(m, true, false, at(0, 12, 0)).channel;
        assertTrue(g.likely);
        assertEquals("TNT SPORTS OR PRIME VIDEO", g.words());
        // the line a card falls back to before going under 44: no LIKELY, the guesses joined by "/" as well
        assertEquals("TNT SPORTS / PRIME VIDEO", g.bareWords());
        assertEquals("SKY SPORTS / NOW", ch.bareWords());
        // the launcher's description keeps the hedge word
        assertTrue(Cards.where(m).startsWith("likely on"));
    }
}
