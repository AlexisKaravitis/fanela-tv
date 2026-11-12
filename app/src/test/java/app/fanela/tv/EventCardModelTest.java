package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The F1 / UFC card model (F1-UFC-ARCHITECTURE §5.2, §6.2; SPEC-F1-UFC §2, §4, §6, §12): the whole fixture matrix of
 * §8.3 x scores shown / hidden x Athens, London, New York x English / Greek, then each fixture's words.
 */
public class EventCardModelTest {
    static final String[] ZONES = {"Europe/Athens", "Europe/London", "America/New_York"};
    private TimeZone was;

    @Before public void athens() {
        was = TimeZone.getDefault();
        zone("Europe/Athens");
        I18n.use("en");
        EventCardModel.orderTrusted = false;
        F1Feed.lapTrusted = false;
        UfcFeed.roundTrusted = false;
    }

    @After public void restore() {
        TimeZone.setDefault(was);
        I18n.use("en");
        I18n.clock24(true);
        EventCardModel.orderTrusted = F1Feed.LIVE_ORDER_TRUSTED;
        F1Feed.lapTrusted = F1Feed.LIVE_LAP_TRUSTED;
        UfcFeed.roundTrusted = UfcFeed.LIVE_ROUND_TRUSTED;
    }

    static void zone(String id) { TimeZone.setDefault(TimeZone.getTimeZone(id)); }

    static EventCardModel model(String fixture) { return EventFixtures.named(fixture).model(true); }

    static EventCardModel hidden(String fixture) { return EventFixtures.named(fixture).model(false); }

    private static final Pattern CODE = Pattern.compile("\\b(VER|NOR|ANT|PIA|LEC|RUS)\\b");
    private static final Pattern SPONSOR = Pattern.compile("Heineken|MSC|Qatar Airways|Gulf Air|Crypto|Aramco|Lenovo");

    // ---------- the matrix ----------

    @Test public void everyFixtureInEveryZoneLanguageAndScoresSetting() {
        for (boolean trusted : new boolean[]{false, true}) {
            EventCardModel.orderTrusted = trusted;
            for (String z : ZONES) {
                zone(z);
                for (String lang : new String[]{"en", "el"}) {
                    I18n.use(lang);
                    for (EventFixtures.Fx fx : EventFixtures.all()) {
                        for (boolean shown : new boolean[]{true, false}) {
                            String at = fx.name + " " + z + " " + lang + (shown ? "" : " hidden") + (trusted ? " trusted" : "");
                            EventCardModel m = fx.model(shown);
                            check(m, fx, shown, at);
                        }
                    }
                }
            }
        }
    }

    private static void check(EventCardModel m, EventFixtures.Fx fx, boolean shown, String at) {
        assertEquals(at, fx.state, m.state);
        assertEquals(at, fx.e.sport, m.sport);
        assertEquals(at, !shown, m.scoresHidden);
        assertEquals(at, fx.stale, m.stale);
        assertFalse(at, m.eyebrowNames.isEmpty());
        assertFalse(at, m.compNames.isEmpty());
        assertFalse(at, m.titleNames.get(0).trim().isEmpty());
        assertNotNull(at, m.session);
        assertFalse(at, m.sessionLabel.isEmpty());
        assertEquals(at, SportNames.caps(m.sessionNames.get(0)), m.sessionLabel);
        for (List<EventCardModel.Seg> v : m.eyebrowNames)
            for (EventCardModel.Seg s : v) assertFalse(at + " eyebrow " + s, SPONSOR.matcher(s.text).find());
        assertFalse(at, SPONSOR.matcher(m.title() + m.headline() + EventCards.describe(m, 0)).find());
        // the time: 24-hour in Greek, the session's own time in this zone
        if (I18n.el()) {
            assertEquals(at, "", m.timeSuffix);
            assertFalse(at, (m.time + m.title() + EventCards.describe(m, 0)).matches("(?s).*\\b(AM|PM|π\\.μ\\.|μ\\.μ\\.)\\b.*"));
        }
        if (m.hero == EventCardModel.Hero.TIME && !m.tbd && !m.time.equals(CardModel.w("tba"))) {
            long start = m.session.start;
            assertEquals(at, I18n.time(start), m.time + (m.timeSuffix.isEmpty() ? "" : " " + m.timeSuffix));
        }
        assertEquals(at, m.heroAlpha < 1f, m.heroDim);
        // people stay in Latin script
        for (EventCardModel.Slot s : m.slots) assertFalse(at, SportNames.greek(s.name));
        if (m.bout != null) assertFalse(at, SportNames.greek(m.bout.nameA + m.bout.nameB));
        // the launcher's spoiler-safe title never names a winner
        assertFalse(at, m.title().contains("wins") || m.title().contains("Νίκη") || m.title().contains("KO/TKO"));
        if (!shown) hiddenRules(m, at);
        // UFC: the records drawn are the pre-event ones
        if (m.bout != null && m.bout.a != null && !m.bout.recordA.isEmpty())
            assertEquals(at, m.bout.a.recordBefore.isEmpty() ? m.bout.a.record : m.bout.a.recordBefore, m.bout.recordA);
        if (m.state == CardModel.State.RESULT && m.bout != null && m.bout.a != null && m.bout.a.recordBefore.isEmpty())
            assertEquals(at, "", m.bout.recordA);  // after the fight, never the updated record
    }

    /** SPEC-F1-UFC §6: nothing that tells a result, on the card or in the launcher's words. */
    private static void hiddenRules(EventCardModel m, String at) {
        assertTrue(at, m.slots.isEmpty());
        assertTrue(at, m.focusSlots.isEmpty());
        assertNull(at, m.focus);
        assertEquals(at, m.title(), m.headline());
        String desc = EventCards.describe(m, 0);
        assertFalse(at + ": " + desc, CODE.matcher(desc.replace("ANT1", "")).find());
        assertFalse(at + ": " + desc, desc.contains("KO/TKO") || desc.contains("wins") || desc.contains("Νίκη"));
        if (m.state == CardModel.State.RESULT || m.state == CardModel.State.LIVE || m.state == CardModel.State.SUSPENDED) {
            assertTrue(at + " " + m.hero, m.hero == EventCardModel.Hero.SLOTS || m.hero == EventCardModel.Hero.TIME
                || m.hero == EventCardModel.Hero.WORD);
        }
        if (m.hero == EventCardModel.Hero.SLOTS) assertTrue(at, m.heroRows.isEmpty());
        if (m.bout != null) {
            assertEquals(at, 0, m.bout.winner);
            assertEquals(at, "", m.bout.method);
            assertEquals(at, "", m.bout.methodWord);
            assertEquals(at, "", m.bout.round);
            assertEquals(at, "", m.bout.clock);
            assertEquals(at, "", m.bout.outcomeWord);
        }
    }

    // ---------- the three canonical times (§3.3) ----------

    @Test public void lasVegasFp1IsFridayHalfPastTwoInAthens() {
        String[][] want = {  // zone, day word, time
            {"Europe/Athens", "TOMORROW", "02:30"}, {"Europe/London", "TOMORROW", "00:30"}, {"America/New_York", "TODAY", "19:30"}};
        for (String[] w : want) {
            zone(w[0]);
            EventCardModel m = model("f1_tomorrow_fp1");
            assertEquals(w[0], "FP1", m.session.kind);
            assertEquals(w[0], "PRACTICE 1", m.sessionLabel);
            assertEquals(w[0], Arrays.asList("Practice 1", "FP1"), m.sessionNames);
            assertEquals(w[0], w[1], m.stateLine);
            assertEquals(w[0], w[2], m.time);
            assertTrue(m.soon);
        }
        zone("Europe/Athens");
        assertEquals("FRI 20 NOV", CardModel.cardDate(SportTestKit.LV_FP1));
        I18n.use("el");
        EventCardModel m = model("f1_tomorrow_fp1");
        assertEquals("ΑΥΡΙΟ", m.stateLine);
        assertEquals(Arrays.asList("Δοκιμές 1", "FP1"), m.sessionNames);
    }

    @Test public void theLasVegasRaceIsSundaySixInAthens() {
        String[][] want = {
            {"Europe/Athens", "SUN 22 NOV", "06:00"}, {"Europe/London", "SUN 22 NOV", "04:00"}, {"America/New_York", "SAT 21 NOV", "23:00"}};
        for (String[] w : want) {
            zone(w[0]);
            EventCardModel m = model("f1_week");
            assertEquals(w[0], "RACE", m.sessionLabel);  // before the weekend the card is about the race
            assertEquals(w[0], w[1], m.stateLine);
            assertEquals(w[0], w[2], m.time);
            assertFalse(m.soon);
        }
        zone("Europe/Athens");
        I18n.use("el");
        EventCardModel m = model("f1_week");
        assertEquals("ΚΥΡ 22 ΝΟΕ", m.stateLine);
        assertEquals("ΑΓΩΝΑΣ", m.sessionLabel);
        assertEquals("06:00", m.time);
    }

    @Test public void ufc334MainCardIsSundayFourInAthensAndItsEarlyPrelimsMidnight() {
        String[][] want = {
            {"Europe/Athens", "SUN 15 NOV", "04:00"}, {"Europe/London", "SUN 15 NOV", "02:00"}, {"America/New_York", "SAT 14 NOV", "21:00"}};
        for (String[] w : want) {
            zone(w[0]);
            EventCardModel m = model("ufc_next_numbered");
            assertEquals(w[0], "MAIN CARD", m.sessionLabel);
            assertEquals(w[0], w[1], m.stateLine);
            assertEquals(w[0], w[2], m.time);
        }
        zone("Europe/Athens");
        EventCardModel early = model("ufc_today_prelims_soon");  // a followed fighter on the early prelims
        assertEquals("EARLY PRELIMS", early.sessionLabel);
        assertEquals("00:00", early.time);
        assertEquals("TOMORROW", early.stateLine);  // Saturday 23:00 in Athens: the early prelims are Sunday 00:00
        zone("America/New_York");
        assertEquals("TODAY", model("ufc_today_prelims_soon").stateLine);
        assertEquals("17:00", model("ufc_today_prelims_soon").time);
        I18n.use("el");
        zone("Europe/Athens");
        assertEquals("ΚΥΡ 15 ΝΟΕ", model("ufc_next_numbered").stateLine);
    }

    @Test public void englishFollowsTheTvClockGreekIsAlways24Hour() {
        zone("America/New_York");
        I18n.clock24(false);
        EventCardModel m = model("f1_week");
        assertEquals("11:00", m.time);
        assertEquals("PM", m.timeSuffix);
        I18n.use("el");
        m = model("f1_week");
        assertEquals("23:00", m.time);
        assertEquals("", m.timeSuffix);
    }

    // ---------- Formula 1 ----------

    @Test public void aWeekendAheadIsTheRaceOnItsChannelInGraphite() {
        EventCardModel m = model("f1_week");
        assertEquals(EventCardModel.Hero.TIME, m.hero);
        assertEquals(Arrays.asList("Race"), m.sessionNames);
        assertEquals(Arrays.asList("Formula 1"), m.compNames);
        assertEquals(Arrays.asList("Las Vegas Grand Prix", "Las Vegas GP"), m.titleNames);
        assertEquals("[[F1, Las Vegas GP], [Las Vegas GP]]", m.eyebrowNames.toString());
        assertEquals(Arrays.asList("Las Vegas"), m.placeNames);
        assertEquals("4250", m.circuitId);
        assertEquals("us", m.flagCode);
        assertEquals(0xFF3C3B6E, m.flagColour);
        assertEquals(EventCardModel.F1_GRAPHITE, m.accent);
        assertEquals(EventCardModel.F1_IVORY, m.trim);
        assertFalse(m.themed);
        assertNotNull(m.channel);
        assertTrue(m.channel.likely);
        assertEquals(Arrays.asList("ANT1"), m.channel.names);
        assertEquals("Las Vegas Grand Prix", m.title());
        assertEquals("", m.dateLine);
        I18n.use("el");
        m = model("f1_week");
        assertEquals("[[F1, GP Λας Βέγκας], [GP Λας Βέγκας]]", m.eyebrowNames.toString());
        assertEquals(Arrays.asList("Γκραν Πρι Λας Βέγκας", "GP Λας Βέγκας"), m.titleNames);
        assertEquals(Arrays.asList("Λας Βέγκας"), m.placeNames);
    }

    @Test public void duringTheWeekendTheCardIsAboutTheNextSession() {
        EventCardModel q = model("f1_weekend_qual_next");
        assertEquals("QUALIFYING", q.sessionLabel);
        assertEquals(Arrays.asList("Qualifying", "Quali"), q.sessionNames);
        assertEquals(Arrays.asList("ANT1+"), q.channel.names);
        EventCardModel s = model("f1_sprint_next");
        assertEquals("SPRINT", s.sessionLabel);
        assertEquals("[[F1, Chinese GP], [Chinese GP]]", s.eyebrowNames.toString());
        I18n.use("el");
        assertEquals(Arrays.asList("Κατατακτήριες", "Κατατακτ."), model("f1_weekend_qual_next").sessionNames);
        assertEquals("[[F1, GP Κίνας], [GP Κίνας]]", model("f1_sprint_next").eyebrowNames.toString());
        SportEvent sq = EventFixtures.sprintWeekend();
        EventFixtures.done(sq, "FP1");
        EventCardModel m = EventCardModel.of(EventFixtures.derived(sq), "c2", false, SportPrefs.none(), true, false, SportTestKit.LV_FP1 + 2 * SportTestKit.H);
        assertEquals("ΚΑΤΑΤΑΚΤΗΡΙΕΣ ΣΠΡΙΝΤ", m.sessionLabel);
        assertEquals(Arrays.asList("Κατατακτήριες σπριντ", "Κατατ. σπριντ"), m.sessionNames);
    }

    @Test public void aLiveRaceShowsTheLapAndTheOrderOnlyOnceTrusted() {
        F1Feed.lapTrusted = true;  // 1.3.1's cards; the safe mode: LiveSafeModeTest
        EventCardModel m = model("f1_live_race");
        assertEquals("LIVE", m.stateLine);
        assertEquals("34/50", m.clock);
        assertEquals("LAP 34/50", m.lapLine);
        assertEquals(EventCardModel.Hero.TIME, m.hero);  // not trusted: the time it started, dimmed
        assertEquals(0.4f, m.heroAlpha, 0.001);
        assertEquals("06:00", m.time);
        assertTrue(m.slots.isEmpty());
        assertNotNull(m.channel);
        I18n.use("el");
        assertEquals("ΓΥΡΟΣ 34/50", model("f1_live_race").lapLine);
        I18n.use("en");
        EventCardModel.orderTrusted = true;
        m = model("f1_live_race");
        assertEquals(EventCardModel.Hero.ORDER, m.hero);
        assertEquals(3, m.slots.size());
        assertEquals("ANT", m.slots.get(0).code);
        assertEquals("VER", m.slots.get(1).code);
        assertEquals(2, m.slots.get(1).pos);
        assertEquals("NOR", m.slots.get(2).code);
        assertEquals("Antonelli", m.slots.get(0).name);
        assertEquals("LIVE · Las Vegas Grand Prix", m.title());
        // scores hidden: the pill and the lap stay, the order becomes one slot
        m = hidden("f1_live_race");
        assertEquals("34/50", m.clock);
        assertEquals(EventCardModel.Hero.SLOTS, m.hero);
    }

    @Test public void aLapWithNoTotalSaysLap() {
        F1Feed.lapTrusted = true;
        SportEvent e = EventFixtures.liveRace("");
        EventFixtures.kind(e, "RACE").laps = 0;
        e.laps = 0;
        EventCardModel m = EventCardModel.of(e, null, false, SportPrefs.none(), true, false, SportTestKit.LV_RACE + SportTestKit.H);
        assertEquals("LAP 34", m.clock);
        assertEquals("LAP 34", m.lapLine);
    }

    @Test public void aFocusDriverIsTheHeroWithTheTwoBestOthersAndColoursTheCard() {
        EventCardModel.orderTrusted = true;
        EventCardModel m = model("f1_live_race_focus");
        assertEquals(EventCardModel.Hero.FOCUS, m.hero);
        assertEquals("NOR", m.focus.code);
        assertEquals("P3", m.focus.place);
        assertTrue(m.focus.focus);
        assertEquals(1, m.focusSlots.size());
        assertEquals("ANT", m.slots.get(0).code);
        assertEquals("VER", m.slots.get(1).code);
        assertEquals(0xFFFF8700, m.accent);
        assertEquals(0xFF2B2D33, m.trim);
        assertTrue(m.themed);
        EventCardModel r = model("f1_result_focus_p5");
        assertEquals(EventCardModel.Hero.FOCUS, r.hero);
        assertEquals("P5", r.focus.place);
        assertEquals("VER", r.slots.get(0).code);
        assertEquals("PIA", r.slots.get(1).code);
        assertEquals("Verstappen wins · Las Vegas Grand Prix", r.headline());
    }

    @Test public void aFocusColoursTheCardBeforeTheSessionHasAnOrder() {
        SportEvent e = EventFixtures.raceDone("VER", "NOR", "ANT");
        SportEvent next = EventFixtures.vegas();  // the next weekend: no order yet anywhere in it
        next.id = "600057450";
        EventCardModel m = EventCardModel.of(next, null, false, EventFixtures.focus("d:" + EventFixtures.NOR), true, false,
            SportTestKit.LV_FP1 - SportTestKit.DAY, EventCards.rosterFrom(Arrays.asList(e, next)));
        assertEquals(0xFFFF8700, m.accent);
        assertTrue(m.themed);
        EventCardModel t = EventCardModel.of(next, null, false, EventFixtures.focus("t:" + EventFixtures.REDBULL), true, false,
            SportTestKit.LV_FP1 - SportTestKit.DAY, EventCards.rosterFrom(Arrays.asList(e, next)));
        assertEquals(0xFF00327D, t.accent);
        assertEquals(0xFFE8002D, t.trim);
        EventCardModel none = EventCardModel.of(next, null, false, EventFixtures.focus("d:" + EventFixtures.NOR), true, false,
            SportTestKit.LV_FP1 - SportTestKit.DAY);
        assertEquals("no roster, no order: graphite", EventCardModel.F1_GRAPHITE, none.accent);
    }

    @Test public void aTeamFocusIsTwoCarsAndAStatusIsDimmed() {
        SportEvent e = EventFixtures.raceDone("VER", "NOR", "ANT", "PIA", "LEC");
        EventCardModel m = EventCardModel.of(e, null, true, EventFixtures.focus("t:" + EventFixtures.MCLAREN), true, false, SportTestKit.LV_RACE + 5 * SportTestKit.H);
        assertEquals(EventCardModel.Hero.TEAM, m.hero);
        assertEquals(2, m.focusSlots.size());
        assertEquals("NOR", m.focusSlots.get(0).code);
        assertEquals("P2", m.focusSlots.get(0).place);
        assertEquals("PIA", m.focusSlots.get(1).code);
        assertEquals("P4", m.focusSlots.get(1).place);
        assertTrue(m.slots.isEmpty());
        assertEquals("Verstappen wins · Las Vegas Grand Prix", m.headline());
        EventFixtures.kind(e, "RACE").order.get(1).gap = "DNF";
        m = EventCardModel.of(e, null, true, EventFixtures.focus("d:" + EventFixtures.NOR), true, false, SportTestKit.LV_RACE + 5 * SportTestKit.H);
        assertEquals("DNF", m.focus.place);
        assertTrue(m.focus.placeDim);
    }

    @Test public void aResultIsTheWinnerWithTwoRowsAndItsDate() {
        EventCardModel m = model("f1_result");
        assertEquals("FINAL", m.stateLine);
        assertEquals(EventCardModel.Hero.PODIUM, m.hero);
        assertEquals("VER", m.slots.get(0).code);
        assertEquals("P1", m.slots.get(0).place);
        assertEquals("NOR", m.slots.get(1).code);
        assertEquals("ANT", m.slots.get(2).code);
        assertEquals(0x00327D, m.slots.get(0).color);  // as the feed gives it; the bar's lift rule is the style's
        assertEquals(CardModel.resultDate(SportTestKit.LV_RACE, SportTestKit.LV_RACE + 5 * SportTestKit.H), m.dateLine);
        assertNull(m.channel);
        assertEquals("Verstappen wins · Las Vegas Grand Prix", m.headline());
        assertEquals("Final · Las Vegas Grand Prix", m.title());
        I18n.use("el");
        m = model("f1_result");
        assertEquals("ΤΕΛΙΚΟ", m.stateLine);
        assertEquals("Νίκη: Verstappen · Γκραν Πρι Λας Βέγκας", m.headline());
        assertEquals("Τελικό · Γκραν Πρι Λας Βέγκας", m.title());
    }

    @Test public void hiddenAResultIsOneSlotAndTheSameWhoeverWon() {
        EventCardModel m = hidden("f1_result_hidden");
        assertEquals(EventCardModel.Hero.SLOTS, m.hero);
        assertEquals("Final · Las Vegas Grand Prix", m.headline());
        String a = snapshot(m);
        String b = snapshot(EventCardModel.of(EventFixtures.raceDone("NOR", "ANT", "VER"), null, true, SportPrefs.none(), false, false,
            SportTestKit.LV_RACE + 5 * SportTestKit.H));
        assertEquals(a, b);
        // with a focus too: the card's colour never depends on the result
        SportPrefs nor = EventFixtures.focus("d:" + EventFixtures.NOR);
        assertEquals(snapshot(EventCardModel.of(EventFixtures.raceDone("NOR", "VER", "ANT"), null, true, nor, false, false, SportTestKit.LV_RACE + 5 * SportTestKit.H)),
            snapshot(EventCardModel.of(EventFixtures.raceDone("VER", "ANT", "PIA", "LEC", "NOR"), null, true, nor, false, false, SportTestKit.LV_RACE + 5 * SportTestKit.H)));
    }

    @Test public void aQualifyingResultSaysPole() {
        SportEvent e = EventFixtures.vegas();
        EventFixtures.done(e, "FP1", "FP2", "FP3", "QUAL");
        EventFixtures.order(EventFixtures.kind(e, "QUAL"), "NOR", "VER", "ANT");
        EventCardModel m = EventCardModel.of(EventFixtures.derived(e), "6000574494", true, SportPrefs.none(), true, false, SportTestKit.LV_QUAL + 2 * SportTestKit.H);
        assertEquals("Norris on pole · Las Vegas Grand Prix", m.headline());
        assertEquals("QUALIFYING", m.sessionLabel);
    }

    @Test public void redFlagCancelledDelayedAwaiting() {
        EventCardModel r = model("f1_red_flag");
        assertEquals("SUSPENDED", r.stateLine);
        assertNull(r.channel);
        assertEquals("", r.clock);
        assertEquals(0.4f, r.heroAlpha, 0.001);
        EventCardModel c = model("f1_cancelled");
        assertEquals("CANCELLED", c.stateLine);
        assertEquals("RACE", c.sessionLabel);
        assertEquals(0.4f, c.heroAlpha, 0.001);
        assertEquals(CardModel.cardDate(SportTestKit.LV_RACE), c.dateLine);
        assertTrue(c.dateDim);
        assertNull(c.channel);
        assertEquals("[[F1, Bahrain GP], [Bahrain GP]]", c.eyebrowNames.toString());
        assertEquals("Bahrain Grand Prix · Cancelled", c.title());
        assertEquals("DELAYED", model("f1_delayed").stateLine);
        EventCardModel a = model("f1_awaiting");
        assertEquals("NO RESULT YET", a.stateLine);
        assertEquals(EventCardModel.Hero.SLOTS, a.hero);
        I18n.use("el");
        assertEquals("ΔΙΑΚΟΠΗ", model("f1_red_flag").stateLine);
        assertEquals("ΑΚΥΡΩΘΗΚΕ", model("f1_cancelled").stateLine);
        assertEquals("[[F1, GP Μπαχρέιν], [GP Μπαχρέιν]]", model("f1_cancelled").eyebrowNames.toString());
        assertEquals("ΣΕ ΑΝΑΜΟΝΗ", model("f1_awaiting").stateLine);
        // postponed: TBA at 60 %, the original date dimmed
        SportEvent p = EventFixtures.vegas();
        for (SportEvent.Session s : p.sessions) { s.state = "off"; s.detail = "POSTPONED"; }
        EventCardModel pm = EventCardModel.of(EventFixtures.derived(p), null, false, SportPrefs.none(), true, false, SportTestKit.LV_FP1 - SportTestKit.DAY);
        assertEquals("ΑΝΑΒΛΗΘΗΚΕ", pm.stateLine);
        assertEquals("TBA", pm.time);
        assertEquals(0.6f, pm.heroAlpha, 0.001);
    }

    @Test public void theOffSeasonCardNamesTheSeasonAndTheMonth() {
        EventCardModel m = model("f1_offseason");
        assertEquals("SEASON 2027", m.stateLine);
        assertEquals(EventCardModel.Hero.WORD, m.hero);
        assertEquals("MARCH", m.heroWord);
        assertNull(m.channel);
        assertEquals("[[F1, Australian GP], [Australian GP]]", m.eyebrowNames.toString());
        I18n.use("el");
        m = model("f1_offseason");
        assertEquals("ΣΕΖΟΝ 2027", m.stateLine);
        assertEquals("ΜΑΡΤΙΟΣ", m.heroWord);
    }

    @Test public void longPlacesTightFormsAbroadAndUnknownCircuits() {
        I18n.use("el");
        EventCardModel m = model("f1_long_name");
        assertEquals("[[F1, GP Εμίλια-Ρομάνια], [F1, GP Ίμολα], [GP Εμίλια-Ρομάνια], [GP Ίμολα]]", m.eyebrowNames.toString());
        I18n.use("en");
        assertEquals("Emilia-Romagna Grand Prix", model("f1_long_name").title());
        EventCardModel gb = model("f1_abroad_gb");
        assertTrue(gb.channel.namesOnly);
        assertEquals(Arrays.asList("Sky Sports F1"), gb.channel.names);
        EventCardModel no = model("f1_no_outline");
        assertEquals("9999", no.circuitId);
        assertEquals("[[F1, Atlantis GP], [Atlantis GP]]", no.eyebrowNames.toString());
        assertEquals("", no.flagCode);
        assertTrue(model("f1_stale").stale);
    }

    // ---------- UFC ----------

    @Test public void aNumberedEventFollowsTheBillingInFixedPositions() {
        EventCardModel m = model("ufc_next_numbered");
        assertEquals(EventCardModel.Hero.TIME, m.hero);
        assertEquals("Gane", m.bout.nameA);  // ESPN's order 1 is Hokit; the billing says Gane first
        assertEquals("Hokit", m.bout.nameB);
        assertTrue(m.bout.swapped);
        assertEquals("14-2-0", m.bout.recordA);
        assertEquals("3104622", m.bout.a.id);
        assertTrue(m.bout.title);
        assertEquals("Heavyweight title", m.bout.label);
        assertEquals("OF 5", m.bout.roundsWord);
        assertEquals("", m.bout.segment);
        assertTrue(m.facesOk);
        assertEquals("[[UFC 334, Title], [UFC 334]]", m.eyebrowNames.toString());
        assertTrue(m.eyebrowNames.get(0).get(1).gold);
        assertFalse(m.eyebrowNames.get(0).get(0).gold);
        assertEquals(Arrays.asList("UFC"), m.compNames);
        assertEquals(Arrays.asList("UFC 334"), m.titleNames);
        assertEquals(EventCardModel.UFC_RED, m.accent);
        assertEquals(EventCardModel.UFC_BLUE, m.trim);
        assertEquals(Arrays.asList("Magenta Sport 6"), m.channel.names);
        assertEquals(310, m.channel.number);
        assertFalse(m.channel.likely);
        assertEquals("Gane VS Hokit", m.title());
        I18n.use("el");
        m = model("ufc_next_numbered");
        assertEquals("[[UFC 334, Τίτλος], [UFC 334]]", m.eyebrowNames.toString());
        assertEquals("Τίτλος βαρέων βαρών", m.bout.label);
        assertEquals("ΑΠΟ 5", m.bout.roundsWord);
        assertEquals("Gane", m.bout.nameA);
    }

    @Test public void fightNightsAndLongNames() {
        EventCardModel m = model("ufc_next_fightnight");
        assertEquals("[[UFC Fight Night]]", m.eyebrowNames.toString());
        assertEquals("Cortes Acosta", m.bout.nameA);
        assertEquals(Arrays.asList("Cortes Acosta", "Acosta"), m.bout.namesA);
        assertEquals("Walker", m.bout.nameB);
        assertEquals(Arrays.asList("UFC Fight Night"), m.titleNames);
        EventCardModel l = model("ufc_long_names");
        assertEquals("Rakhmonov", l.bout.nameA);
        assertEquals(Arrays.asList("Della Maddalena", "Maddalena"), l.bout.namesB);
        assertEquals("[[UFC 335, Title], [UFC 335]]", l.eyebrowNames.toString());
        EventCardModel r = model("ufc_renamed");
        assertEquals("Gane", r.bout.nameA);
        assertEquals("Volkov", r.bout.nameB);
        assertEquals("[[UFC 334, Title], [UFC 334]]", r.eyebrowNames.toString());
        assertFalse(model("ufc_no_headshots").facesOk);
        assertTrue(model("ufc_abroad_gb").channel.namesOnly);
        assertTrue(model("ufc_stale").stale);
    }

    @Test public void aFollowedFighterHeadlinesFromHisSegment() {
        EventCardModel m = model("ufc_next_followed_fighter");
        assertEquals("Three", m.bout.nameA);
        assertEquals("Four", m.bout.nameB);
        assertEquals(1, m.bout.followed);
        assertEquals("PRELIMS", m.bout.segment);
        assertEquals("PRELIMS", m.sessionLabel);
        assertEquals("02:00", m.time);
        assertEquals("[[UFC 334, Prelims], [UFC 334]]", m.eyebrowNames.toString());
        assertEquals(Arrays.asList("UFC Fight Pass"), m.channel.names);
        assertEquals("Three VS Four", m.title());
        // the eyebrow's longest segment, then the shorter (Ticket's 500 px), then the event alone
        assertEquals("[[UFC 334, Early prelims], [UFC 334, Prelims], [UFC 334]]", model("ufc_today_prelims_soon").eyebrowNames.toString());
        I18n.use("el");
        assertEquals("[[UFC 334, Προκριματικά], [UFC 334]]", model("ufc_next_followed_fighter").eyebrowNames.toString());
        assertEquals("[[UFC 334, Πρώτα προκριματικά], [UFC 334, Προκριματικά], [UFC 334]]",
            model("ufc_today_prelims_soon").eyebrowNames.toString());
        I18n.use("en");
    }

    @Test public void liveTheRoundOfTheBoutInTheCageNeverItsClock() {
        UfcFeed.roundTrusted = true;  // 1.3.1's cards; the safe mode: LiveSafeModeTest
        EventCardModel m = model("ufc_live_prelims");
        assertEquals("LIVE", m.stateLine);
        assertEquals("", m.clock);
        assertEquals(EventCardModel.Hero.ROUND, m.hero);
        assertEquals("R2", m.heroWord);
        assertEquals(Arrays.asList("OF 3"), m.heroRows);
        assertEquals("Three", m.bout.nameA);
        assertTrue(m.bout.live);
        assertEquals("[[UFC 334, Prelims], [UFC 334]]", m.eyebrowNames.toString());
        assertEquals(Arrays.asList("UFC Fight Pass"), m.channel.names);
        assertEquals(EventCardModel.Hero.SLOTS, hidden("ufc_live_prelims").hero);
        EventCardModel main = model("ufc_live_main_r2");
        assertEquals("R2", main.heroWord);
        assertEquals(Arrays.asList("OF 5"), main.heroRows);
        assertEquals("Gane", main.bout.nameA);
        assertEquals(Arrays.asList("Magenta Sport 6"), main.channel.names);
        assertEquals("LIVE · Gane VS Hokit", main.title());
        I18n.use("el");
        assertEquals(Arrays.asList("ΑΠΟ 5"), model("ufc_live_main_r2").heroRows);
    }

    @Test public void betweenSegmentsTheNextBoutAndItsSegmentsStart() {
        EventCardModel m = model("ufc_between_segments");
        assertEquals(CardModel.State.LIVE, m.state);
        assertEquals("Five", m.bout.nameA);  // the co-main (bout 2) comes before the main event
        assertEquals(EventCardModel.Hero.TIME, m.hero);
        assertEquals("04:00", m.time);
        assertEquals(Arrays.asList("Magenta Sport 6"), m.channel.names);
        // once the main card has begun, between its bouts: NEXT at 60 %
        EventFixtures.Fx fx = EventFixtures.named("ufc_between_segments");
        EventCardModel later = EventCardModel.of(fx.e, null, false, SportPrefs.none(), true, false, SportTestKit.U334_MAIN + 10 * SportTestKit.MIN);
        assertEquals(EventCardModel.Hero.WORD, later.hero);
        assertEquals("NEXT", later.heroWord);
        assertEquals(0.6f, later.heroAlpha, 0.001);
    }

    @Test public void aResultIsTheMethodWithTheRoundAndTheClock() {
        EventCardModel m = model("ufc_result");
        assertEquals("FINAL", m.stateLine);
        assertEquals(EventCardModel.Hero.METHOD, m.hero);
        assertEquals("KO/TKO", m.heroWord);
        assertEquals(Arrays.asList("R3", "2:27"), m.heroRows);
        assertEquals(1, m.bout.winner);  // Gane, first-billed, on the left
        assertEquals("KO/TKO · R3 2:27", m.bout.method);
        assertEquals("14-2-0", m.bout.recordA);  // the record before the fight, never 15-2-0
        assertEquals("", m.bout.outcomeWord);    // nobody followed: no pill
        assertNull(m.channel);
        assertEquals(CardModel.resultDate(SportTestKit.U334_MAIN, SportTestKit.U334_MAIN + 6 * SportTestKit.H), m.dateLine);
        assertEquals("Gane VS Hokit · KO/TKO R3", m.headline());
        assertEquals("Final · Gane VS Hokit", m.title());
        EventCardModel h = hidden("ufc_result_hidden");
        assertEquals(EventCardModel.Hero.SLOTS, h.hero);
        assertEquals("Final · Gane VS Hokit", h.headline());
        assertEquals("[[UFC 334, Title], [UFC 334]]", h.eyebrowNames.toString());  // TITLE was true before the fight
        // hidden, a win and the reverse result draw the same card
        assertEquals(snapshot(h), snapshot(EventCardModel.of(EventFixtures.u334Result(1, "SUB", "Rear Naked Choke", 1, "0:44"), null, true,
            SportPrefs.none(), false, false, SportTestKit.U334_MAIN + 6 * SportTestKit.H)));
    }

    @Test public void drawsNoContestsDecisionsAndGreekMethods() {
        EventCardModel d = model("ufc_result_draw");
        assertEquals("DRAW", d.heroWord);
        assertTrue("a draw stands alone", d.heroRows.isEmpty());
        assertEquals(0, d.bout.winner);
        EventCardModel nc = model("ufc_result_nc");
        assertEquals("NC", nc.heroWord);
        assertEquals(0, nc.bout.winner);
        I18n.use("el");
        assertEquals("ΙΣΟΠΑΛΙΑ", model("ufc_result_draw").heroWord);
        assertTrue(model("ufc_result_draw").heroRows.isEmpty());
        assertEquals("ΔΙΧΑΣΜΕΝΗ", model("ufc_result_draw").bout.methodDetail);
        EventCardModel sub = EventCardModel.of(EventFixtures.u334Result(2, "SUB", "Rear Naked Choke", 2, "4:11"), null, true,
            SportPrefs.none(), true, false, SportTestKit.U334_MAIN + 6 * SportTestKit.H);
        assertEquals("ΥΠΟΤΑΓΗ", sub.heroWord);
        EventCardModel dec = EventCardModel.of(EventFixtures.u334Result(2, "DEC", "Unanimous Decision", 5, "5:00"), null, true,
            SportPrefs.none(), true, false, SportTestKit.U334_MAIN + 6 * SportTestKit.H);
        assertEquals("ΑΠΟΦΑΣΗ", dec.heroWord);
        assertEquals("a decision's kind alone: it is always the last round", Arrays.asList("ΟΜΟΦΩΝΗ"), dec.heroRows);
        EventCardModel ko = EventCardModel.of(EventFixtures.u334Result(2, "KO/TKO", "Punch", 3, "2:27"), null, true,
            SportPrefs.none(), true, false, SportTestKit.U334_MAIN + 6 * SportTestKit.H);
        assertEquals("a finish keeps its round and time", Arrays.asList("R3", "2:27"), ko.heroRows);
    }

    @Test public void aFollowedFightersResultCarriesWinOrLoss() {
        SportEvent e = EventFixtures.u334Result(2, "KO/TKO", "Punch", 3, "2:27");  // every other bout: a won by decision
        long now = SportTestKit.U334_MAIN + 6 * SportTestKit.H;
        EventCardModel win = EventCardModel.of(e, null, true, EventFixtures.fighters("21"), true, false, now);
        assertEquals("Three", win.bout.nameA);
        assertEquals(1, win.bout.outcome);
        assertEquals("WIN", win.bout.outcomeWord);
        assertEquals("[[UFC 334, Prelims], [UFC 334]]", win.eyebrowNames.toString());
        assertEquals(Arrays.asList("UNANIMOUS"), win.heroRows);
        EventCardModel loss = EventCardModel.of(e, null, true, EventFixtures.fighters("22"), true, false, now);
        assertEquals(-1, loss.bout.outcome);
        assertEquals("LOSS", loss.bout.outcomeWord);
        assertEquals(2, loss.bout.followed);
        I18n.use("el");
        assertEquals("ΝΙΚΗ", EventCardModel.of(e, null, true, EventFixtures.fighters("21"), true, false, now).bout.outcomeWord);
        EventCardModel hid = EventCardModel.of(e, null, true, EventFixtures.fighters("21"), false, false, now);
        assertEquals("", hid.bout.outcomeWord);
        assertEquals(9, hid.bout.outcome);
    }

    @Test public void aCancelledEventIsItsPlannedTimeDimmed() {
        EventCardModel m = model("ufc_cancelled");
        assertEquals("CANCELLED", m.stateLine);
        assertEquals("04:00", m.time);
        assertEquals(0.4f, m.heroAlpha, 0.001);
        assertTrue(m.dateDim);
        assertEquals("Gane", m.bout.nameA);
        assertEquals("Gane VS Hokit · Cancelled", m.title());
    }

    @Test public void theContractCallWithoutARosterStillWorks() {
        EventCardModel m = EventCardModel.of(EventFixtures.vegas(), null, false, null, true, false, SportTestKit.LV_FP1 - SportTestKit.DAY);
        assertEquals("PRACTICE 1", m.sessionLabel);
        assertEquals(Collections.<EventCardModel.Slot>emptyList(), m.slots);
    }

    /** Everything a style draws, as text: two models that give the same snapshot draw the same bitmap. */
    static String snapshot(EventCardModel m) {
        StringBuilder b = new StringBuilder();
        b.append(m.state).append('|').append(m.stateLine).append('|').append(m.dateLine).append(m.dateDim).append('|').append(m.clock)
            .append(m.lapLine).append('|').append(m.eyebrowNames).append('|').append(m.sessionNames).append('|').append(m.hero)
            .append(m.time).append(m.timeSuffix).append(m.heroAlpha).append(m.heroWord).append(m.heroRows).append('|')
            .append(m.slots.size()).append(m.focusSlots.size()).append(m.focus == null).append('|').append(m.accent).append(m.trim)
            .append(m.themed).append('|').append(m.title()).append('|').append(m.headline()).append('|').append(m.placeNames);
        for (EventCardModel.Slot s : m.slots) b.append(s.code).append(s.place).append(s.color);
        if (m.bout != null) {
            EventCardModel.BoutView v = m.bout;
            b.append('|').append(v.nameA).append(v.nameB).append(v.recordA).append(v.recordB).append(v.winner).append(v.method)
                .append(v.methodWord).append(v.round).append(v.clock).append(v.outcomeWord).append(v.title).append(v.segment)
                .append(v.facesOk);
        }
        return b.toString();
    }
}
