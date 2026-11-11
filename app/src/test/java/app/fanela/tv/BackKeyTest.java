package app.fanela.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import org.junit.Test;

/** Back: the screen decides when it is pressed; what it does not handle goes to the system's Back, once. */
public class BackKeyTest {
    static final class Screen extends Activity {
        int system;
        @SuppressWarnings("deprecation") @Override public void onBackPressed() { system++; }
    }

    @Test public void whatTheScreenDoesNotHandleIsTheSystemsBack() {
        Screen a = new Screen();
        final boolean[] handles = {true};
        final int[] asked = {0};
        BackKey k = BackKey.attach(a, new BackKey.Handler() { public boolean back() { asked[0]++; return handles[0]; } });
        k.press();
        assertEquals(1, asked[0]);
        assertEquals("handled by the screen (a panel closed)", 0, a.system);
        handles[0] = false;
        k.press();
        assertEquals("nothing to undo: the system's Back", 1, a.system);
        k.detach();
    }

    @Test public void onlyTheBackKeyIsTaken() {
        BackKey k = BackKey.attach(new Screen(), new BackKey.Handler() { public boolean back() { return true; } });
        assertFalse(k.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, null));
        assertFalse(k.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_CENTER, null));
    }
}
