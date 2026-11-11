package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/** FB-9: Greek is always 24-hour, whatever the TV's 12/24-hour setting; English follows the setting. */
public class ClockTest {
    @After public void reset() { I18n.use("en"); I18n.clock24(true); }

    @Test public void greekIsAlways24Hour() {
        long t = CardModelTest.at(1, 21, 15);
        I18n.clock24(false);  // the TV is set to 12-hour
        I18n.use("el");
        assertTrue(I18n.clock24());
        assertEquals("21:15", I18n.time(t));
        I18n.use("en");
        assertFalse(I18n.clock24());
        assertEquals("9:15 pm", I18n.time(t).toLowerCase(java.util.Locale.ROOT));
    }

    @Test public void theGreekCardHeroHasNoSuffix() {
        I18n.clock24(false);
        I18n.use("el");
        CardModel c = CardModel.of(CardModelTest.next(CardModelTest.at(2, 21, 15)), true, false, CardModelTest.at(0, 12, 0));
        assertEquals("21:15", c.time);
        assertEquals("", c.timeSuffix);
        I18n.use("en");
        CardModel e = CardModel.of(CardModelTest.next(CardModelTest.at(2, 21, 15)), true, false, CardModelTest.at(0, 12, 0));
        assertEquals("9:15", e.time);
        assertEquals("PM", e.timeSuffix);
    }
}
