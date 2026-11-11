package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Clean's two lights (spec §3.2): mine strong, the opponent's always weaker, neutral on a black or white kit. */
public class CleanStyleTest {
    static final int OLY = 0xFFD0102E, ZVEZDA = 0xFFD2232A, IRAKLIS = 0xFF1E5BAA, PAOK = 0xFF111111, REAL = 0xFFF4F4F4,
        WHITE = 0xFFFFFFFF, MILAN = 0xFFC8102E, DUBAI = 0xFF111111;

    static ClubTokens club(int prim, int sec) { return ClubTokens.of("x", prim, sec, null); }

    @Test public void myLightFollowsMyKit() {
        CleanStyle.Light l = CleanStyle.myLight(club(OLY, WHITE));
        assertEquals(OLY, l.color);
        assertEquals(0.70f, l.a1, 1e-6); assertEquals(0.35f, l.a2, 1e-6);
        l = CleanStyle.myLight(club(PAOK, WHITE));
        assertEquals(ClubTokens.LIGHT_BLACK, l.color);
        assertEquals(0.30f, l.a1, 1e-6); assertEquals(0.12f, l.a2, 1e-6);
        l = CleanStyle.myLight(club(REAL, 0xFF2B3990));
        assertEquals(ClubTokens.LIGHT_WHITE, l.color);
        assertEquals(0.33f, l.a1, 1e-6); assertEquals(0.13f, l.a2, 1e-6);
    }

    @Test public void opponentKitColourWhenItDoesNotClash() {
        CleanStyle.Light o = CleanStyle.oppLight(club(OLY, WHITE), IRAKLIS, WHITE);
        assertEquals(IRAKLIS, o.color);
        assertEquals(0.40f, o.a1, 1e-6); assertEquals(0.18f, o.a2, 1e-6);
    }

    @Test public void opponentAchromaticKitsUseTheirGreys() {
        assertEquals(ClubTokens.LIGHT_BLACK, CleanStyle.oppLight(club(OLY, WHITE), DUBAI, 0xFFC9A227).color);
        assertEquals(ClubTokens.LIGHT_WHITE, CleanStyle.oppLight(club(IRAKLIS, WHITE), REAL, 0xFF2B3990).color);
    }

    @Test public void clashUsesTheOpponentsSecond() {
        // Olympiacos v Crvena Zvezda: red on red, so their light is their second colour; white reads as #8A9099
        assertEquals(ClubTokens.CLASH_OPP, CleanStyle.oppLight(club(OLY, WHITE), ZVEZDA, WHITE).color);
        // a dark second colour is used as it is (AC Milan's black)
        assertEquals(0xFF111111, CleanStyle.oppLight(club(OLY, WHITE), MILAN, 0xFF111111).color);
    }

    @Test public void achromaticKitKeepsTheCardMonochrome() {
        CleanStyle.Light o = CleanStyle.oppLight(club(PAOK, WHITE), IRAKLIS, WHITE);
        assertEquals(ClubTokens.NEUTRAL_OPP, o.color);
        assertEquals(0.18f, o.a1, 1e-6); assertEquals(0.08f, o.a2, 1e-6);
        o = CleanStyle.oppLight(club(REAL, 0xFF2B3990), MILAN, 0xFF111111);
        assertEquals(ClubTokens.NEUTRAL_OPP, o.color);
        assertEquals(0.22f, o.a1, 1e-6); assertEquals(0.10f, o.a2, 1e-6);
    }

    /** LAYOUT-CALM: a name is centred under its plate; a long one moves inward to stay inside the safe edges. */
    @Test public void aNameIsCentredUnderItsPlateAndNeverPastTheSafeEdge() {
        // a short name under the left plate (centre 150) stays centred
        assertEquals(100f, CleanStyle.nameLeft(150, 100, 56, 904), 1e-3);
        // Olympiacos (260 px) would start at 20: it starts at the safe edge 56 and lines up with the plate's outer edge
        assertEquals(56f, CleanStyle.nameLeft(150, 260, 56, 904), 1e-3);
        // the same under the right plate (centre 810): it ends at 904
        assertEquals(644f, CleanStyle.nameLeft(810, 260, 56, 904), 1e-3);
        assertEquals(760f, CleanStyle.nameLeft(810, 100, 56, 904), 1e-3);
        // the grid itself: plates 188 at 56 from either edge, the hero's 424 between them
        assertEquals(150f, CleanStyle.PLATE_L_CX, 0); assertEquals(810f, CleanStyle.PLATE_R_CX, 0);
        assertEquals(424f, CleanStyle.HERO_BOX, 0);
        // LAYOUT-COMPETITION §6: the plates 18 px lower (y 134–322), the names 20 under them
        assertEquals(134f, CleanStyle.PLATE_TOP, 0);
        assertEquals(228f, CleanStyle.PLATE_CY, 0);
        assertEquals(342f, CleanStyle.NAME_TOP, 0);
        assertEquals(320f, CleanStyle.LIGHT_CY, 0);
    }

    /** LAYOUT-COMPETITION §6: the header's two ends are the plates' outer edges; a 66 px pill keeps 24 px over a plate. */
    @Test public void theHeaderRunsFromPlateEdgeToPlateEdge() {
        assertEquals(CleanStyle.PLATE_IN, CleanStyle.COMP_X, 0);
        assertEquals(Cards.W - CleanStyle.PLATE_IN, CleanStyle.PILL_R, 0);
        assertEquals(24f, CleanStyle.PLATE_TOP - (CleanStyle.PILL_CY + CleanStyle.PILL_H / 2), 0);
        assertEquals(44f, CleanStyle.PILL_MIN, 0);
        assertEquals(32f, CleanStyle.COMP_GAP, 0);
        // the competition at 66 %; 80 % under a bright (yellow) light, the same on every card of the club's row
        assertEquals(0.66f, CleanStyle.compAlpha(club(OLY, WHITE)), 0);
        assertEquals(0.66f, CleanStyle.compAlpha(club(PAOK, WHITE)), 0);
        assertEquals(0.66f, CleanStyle.compAlpha(club(REAL, 0xFF2B3990)), 0);
        assertEquals(0.66f, CleanStyle.compAlpha(club(0xFF007A3D, WHITE)), 0);
        assertEquals(0.80f, CleanStyle.compAlpha(club(0xFFF5C800, 0xFF111111)), 0);
    }

    /** The one pill says the day, LIVE with the clock, the outcome (FINAL when hidden), or the state word. */
    @Test public void thePillSaysOneThingPerState() {
        CardModel m = new CardModel();
        m.state = CardModel.State.NEXT; m.stateLine = "TOMORROW";
        assertEquals("TOMORROW", CleanStyle.pillWords(m, false));
        m.stateLine = "DELAYED"; m.dateLine = "TODAY";
        assertEquals("the day of a delayed match is in the launcher's description", "DELAYED", CleanStyle.pillWords(m, false));
        m = new CardModel();
        m.state = CardModel.State.LIVE; m.stateLine = "LIVE"; m.clock = "67′";
        assertEquals("LIVE 67′", CleanStyle.pillWords(m, false));
        m = new CardModel();
        m.state = CardModel.State.RESULT; m.stateLine = "FINAL"; m.outcomeWord = "WIN"; m.dateLine = "FRI 6 NOV";
        assertEquals("WIN", CleanStyle.pillWords(m, false));
        assertEquals("the date goes to the foot, never the pill", "FINAL", CleanStyle.pillWords(m, true));
        m.outcomeWord = "";
        assertEquals("FINAL", CleanStyle.pillWords(m, false));
        m = new CardModel();
        m.state = CardModel.State.OFF; m.stateLine = "POSTPONED"; m.dateLine = "SUN 22 NOV"; m.dateDim = true;
        assertEquals("POSTPONED", CleanStyle.pillWords(m, false));
    }

    @Test public void theOpponentIsAlwaysWeakerThanMe() {
        int[] kits = {OLY, ZVEZDA, IRAKLIS, PAOK, REAL, MILAN, 0xFFF5C800, 0xFF007A3D, 0xFF1F4E9E};
        for (int mine : kits) for (int theirs : kits) {
            ClubTokens t = club(mine, WHITE);
            CleanStyle.Light me = CleanStyle.myLight(t), them = CleanStyle.oppLight(t, theirs, WHITE);
            assertTrue(them.a1 < me.a1);
            assertTrue(them.a2 < me.a2);
        }
    }
}
