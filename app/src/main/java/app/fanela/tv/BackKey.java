package app.fanela.tv;

import android.app.Activity;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Back, the same on every Android version the app runs on (8 to 14 and later). The screen answers one question when
 * Back is pressed: did it handle it (closed a panel, went back a level)? If not, the system does what it always does
 * (the activity closes, or a launcher task moves to the back).
 *
 * Android 13+ (the manifest opts in to OnBackInvokedCallback) no longer delivers KEYCODE_BACK to the views or to
 * onKeyDown: one callback is registered for the activity's whole life, so nothing has to be registered or
 * unregistered as the screen changes and no callback can outlive the state it was made for. Android 8-12 get the key:
 * the activity forwards onKeyDown and onKeyUp here. Both act when the key is released, as the system's own Back does.
 *
 * Use: {@code back = BackKey.attach(this, this::onBack)} in onCreate, {@code back.onKeyDown(code, e)} /
 * {@code back.onKeyUp(code, e)} first in onKeyDown / onKeyUp (return true when they do), {@code back.detach()} in
 * onDestroy.
 */
final class BackKey {
    /** What the screen does on Back. */
    interface Handler {
        /** True when the screen handled Back; false lets the system go back (close the activity). UI thread. */
        boolean back();
    }

    private final Activity activity;
    private final Handler handler;
    private Object callback;  // android.window.OnBackInvokedCallback on 33+ (kept as Object: the class is API 33)

    private BackKey(Activity a, Handler h) { activity = a; handler = h; }

    /** Starts handling Back for the activity (onCreate). */
    static BackKey attach(Activity a, Handler h) {
        BackKey k = new BackKey(a, h);
        if (Build.VERSION.SDK_INT >= 33) k.callback = Api33.register(a, k);
        return k;
    }

    /** Android 8-12: the Back key going down. True when it is ours (always, for BACK: the action runs on release). */
    boolean onKeyDown(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_BACK || Build.VERSION.SDK_INT >= 33) return false;
        e.startTracking();
        return true;
    }

    /** Android 8-12: the Back key released: the screen's Back, or the system's. */
    boolean onKeyUp(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_BACK || Build.VERSION.SDK_INT >= 33) return false;
        if (e.isTracking() && !e.isCanceled()) press();
        return true;
    }

    /** Stops handling Back (onDestroy). */
    void detach() {
        if (callback != null && Build.VERSION.SDK_INT >= 33) Api33.unregister(activity, callback);
        callback = null;
    }

    /** One Back press, whatever delivered it. */
    @SuppressWarnings("deprecation")
    void press() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (handler.back()) return;
        // the system's own Back: on 33+ this asks the system exactly as its default callback would (it does not go
        // through the dispatcher again); below 33 it is the classic onBackPressed (finish, or move a root task back)
        activity.onBackPressed();
    }

    /** The API 33 classes, kept out of BackKey so older versions never load them. */
    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static Object register(Activity a, final BackKey k) {
            android.window.OnBackInvokedCallback cb = new android.window.OnBackInvokedCallback() {
                public void onBackInvoked() { k.press(); }
            };
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            return cb;
        }

        static void unregister(Activity a, Object cb) {
            a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) cb);
        }
    }
}
