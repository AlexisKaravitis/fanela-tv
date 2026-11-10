package app.fanela.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings, built for the remote. Also the first-run setup ("setup" extra): language, then clubs, then Done.
 * Every change is saved at once and the data refreshes in the background.
 */
public class SettingsActivity extends Activity {
    private static final int COLS = 7;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService work, images, refresher;  // work: quick row/provider actions in order; images; refresher: downloads
    private final ArrayDeque<long[]> prompts = new ArrayDeque<>();  // {channel id} waiting for the system prompt
    private final Map<Long, String> labels = new HashMap<>();
    private SharedPreferences settings;
    private TextView status;
    private View langOption, favouriteOption;
    private FrameLayout root, dialog;
    private ScrollView pageScroll;
    private String shownHidden = "";  // the rows listed in hiddenRows, to skip a rebuild that would move focus
    private LinearLayout hiddenRows;  // "the X row is hidden: Show" options, filled by checkHiddenRows
    private int avail;
    private Object backCallback;
    private boolean resumed, setup;
    private final Runnable poll = new Runnable() { public void run() { refreshStatus(); ui.postDelayed(this, 5000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        setup = getIntent().getBooleanExtra("setup", false);
        I18n.load(this);
        Ui.theme(this);
        work = Executors.newSingleThreadExecutor();
        images = Executors.newFixedThreadPool(2);
        refresher = Executors.newSingleThreadExecutor();
        build();
        backCallback = setup ? Ui.onBack(this, backCallback, new Runnable() { public void run() { onBackKey(); } }) : null;
    }

    @Override protected void onStart() { super.onStart(); Brand.shown(); }

    @Override protected void onStop() { super.onStop(); Brand.hidden(this); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ui.post(poll);
        resumePrompts();
        if (!prompts.isEmpty()) askNext();  // the next queued "show this row?" question, after the previous one closed
        checkHiddenRows();
    }

    @Override protected void onPause() {
        resumed = false;
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (work != null) work.shutdown();          // let queued saves / row changes finish
        if (images != null) images.shutdownNow();   // images are not needed any more
        if (refresher != null) refresher.shutdown();
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {  // Android 8-12; 13+ uses Ui.onBack
        if (code == KeyEvent.KEYCODE_BACK && (dialog != null || setup)) { onBackKey(); return true; }
        return super.onKeyDown(code, e);
    }

    private void onBackKey() {
        if (dialog != null) closeDialog();
        else if (setup) finishSetup();
        else finish();
    }

    private void finishSetup() {
        settings.edit().putBoolean("onboarded", true).apply();
        finish();
    }

    private boolean gone() { return isFinishing() || isDestroyed(); }

    private int dp(float v) { return Ui.dp(this, v); }

    private TextView text(String s, float sp, int color, boolean bold) { return Ui.text(this, s, sp, color, bold); }

    private TextView heading(String s) {
        TextView t = text(s, 20, Ui.TEXT, true);
        t.setPadding(0, dp(18), 0, dp(8));
        return t;
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(56));  // also keeps the focused option clear of the bottom edge
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        int side = dp(Ui.SIDE), top = dp(Ui.TOP);
        page.setPadding(side, top, side, top);  // TV overscan-safe area
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        avail = getResources().getDisplayMetrics().widthPixels - 2 * side;
        int gap = dp(12), tileW = (avail - (COLS - 1) * gap) / COLS;

        page.addView(text(I18n.t(setup ? "welcome_title" : "settings"), 28, Ui.TEXT, true));
        if (setup) {
            TextView sub = text(I18n.t("welcome_text"), 15, Ui.SUB, false);
            sub.setPadding(0, dp(4), 0, 0);
            page.addView(sub);
        }

        if (setup) {  // language first, so the rest of the setup reads in the right language
            page.addView(heading(I18n.t("language_title")));
            page.addView(langOption = languageOption(avail));
        }

        // ---------- clubs ----------
        page.addView(heading(I18n.t("clubs")));
        TextView hint = text(I18n.t("clubs_hint"), 14, Ui.SUB, false);
        hint.setPadding(0, 0, 0, dp(6));
        page.addView(hint);
        List<Teams.Club> clubs = Teams.sorted(this);
        Set<String> follow = Teams.followed(this);
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < clubs.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                page.addView(row);
            }
            View tile = clubTile(clubs.get(i), follow.contains(clubs.get(i).id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, dp(128));
            lp.setMargins(0, dp(5), i % COLS == COLS - 1 ? 0 : gap, dp(7));
            row.addView(tile, lp);
            if (first == null) first = tile;
        }

        if (setup) {
            TextView done = Ui.button(this, I18n.t("done"));
            done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finishSetup(); } });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.setMargins(0, dp(16), 0, dp(8));
            page.addView(done, dl);
        } else {
            // ---------- options ----------
            page.addView(heading(I18n.t("options")));
            page.addView(langOption = languageOption(avail));
            if (!Teams.followed(this).isEmpty()) page.addView(favouriteOption = favouriteOption(avail));
            page.addView(option(I18n.t("basketball"), I18n.t("basketball_hint"), avail, new Value() {
                public boolean on() { return Teams.basketball(SettingsActivity.this); }
                public String get() { return I18n.t(on() ? "yes" : "no"); }
                public void toggle() { settings.edit().putBoolean("basket", !on()).commit(); changed(); }
            }));
            if (HomeRows.supported(this)) {
                page.addView(option(I18n.t("rows"), I18n.t("rows_hint"), avail, new Value() {
                    public boolean on() { return settings.getBoolean("rows", true); }
                    public String get() { return I18n.t(on() ? "yes" : "no"); }
                    public void toggle() {
                        settings.edit().putBoolean("rows", !on()).commit();
                        if (on()) for (String id : Teams.followed(SettingsActivity.this)) { Teams.Club k = Teams.get(SettingsActivity.this, id); if (k != null) addRow(k); }
                        changed();
                        checkHiddenRows();
                    }
                }));
                // rows the TV is hiding, right under the switch they belong to and set in from it
                hiddenRows = new LinearLayout(this);
                hiddenRows.setOrientation(LinearLayout.VERTICAL);
                hiddenRows.setPadding(dp(24), 0, 0, 0);
                page.addView(hiddenRows);
            } else {
                page.addView(info(I18n.t("rows"), I18n.t("rows_unsupported"), avail));
            }

            // ---------- about ----------
            page.addView(heading(I18n.t("about")));
            page.addView(info(I18n.t("independent_title"), I18n.t("independent"), avail));
            page.addView(info(I18n.t("sources_title"), I18n.t(BuildConfig.GUIDES ? "sources_full" : "sources_store"), avail));
            page.addView(option(I18n.t("privacy"), I18n.t("privacy_short"), avail, new Value() {
                public boolean on() { return false; }
                public String get() { return I18n.t("read"); }
                public void toggle() { showText(I18n.t("privacy"), I18n.t("privacy_full")); }
            }));

            if (AppUpdate.enabled()) {
                page.addView(option(I18n.t("updates"), I18n.f("version", BuildConfig.VERSION_NAME), avail, new Value() {
                    public boolean on() { return AppUpdate.available(SettingsActivity.this) != null; }
                    public String get() { AppUpdate.Release r = AppUpdate.available(SettingsActivity.this); return r == null ? I18n.t("up_to_date") : I18n.f("install_version", r.version); }
                    public void toggle() {
                        final AppUpdate.Release r = AppUpdate.available(SettingsActivity.this);
                        if (r == null) return;
                        Toast.makeText(SettingsActivity.this, I18n.t("update_downloading"), Toast.LENGTH_SHORT).show();
                        final Context app = getApplicationContext();
                        refresher.execute(new Runnable() { public void run() {
                            final String err = AppUpdate.install(app, r);
                            if (err != null) ui.post(new Runnable() { public void run() { if (!gone()) Toast.makeText(SettingsActivity.this, I18n.t(err), Toast.LENGTH_LONG).show(); } });
                        }});
                    }
                }));
            }

            // ---------- status ----------
            page.addView(heading(I18n.t("status")));
            status = text("", 14, Ui.SUB, false);
            page.addView(status);
            refreshStatus();
        }

        setContentView(root);
        String focus = getIntent().getStringExtra("focus");
        if ("lang".equals(focus) && langOption != null) langOption.requestFocus();
        else if ("favourite".equals(focus) && favouriteOption != null) favouriteOption.requestFocus();
        else if (first != null) first.requestFocus();
    }

    // ---------- favourite club ----------
    /** The club whose colours the app wears (accents and the logo's shirt), among the followed ones. */
    private View favouriteOption(int avail) {
        return option(I18n.t("favourite"), I18n.t("favourite_hint"), avail, new Value() {
            public String get() {
                String id = Teams.favourite(SettingsActivity.this);
                Teams.Club k = id == null ? null : Teams.get(SettingsActivity.this, id);
                return k == null ? "–" : k.label();
            }
            public boolean on() { return false; }
            public void toggle() {
                List<String> ids = new java.util.ArrayList<>(), names = new java.util.ArrayList<>();
                for (String id : Teams.followed(SettingsActivity.this)) {
                    Teams.Club k = Teams.get(SettingsActivity.this, id);
                    if (k != null) { ids.add(id); names.add(k.label()); }
                }
                if (ids.isEmpty()) return;
                showChoice(I18n.t("favourite"), I18n.t("favourite_hint"), ids, names, Teams.favourite(SettingsActivity.this), new Chosen() {
                    public void chosen(String id) {
                        Teams.setFavourite(SettingsActivity.this, id);
                        Ui.theme(SettingsActivity.this);
                        getIntent().putExtra("focus", "favourite");
                        recreate();  // the new colours everywhere on this screen
                    }
                });
            }
        });
    }

    interface Chosen { void chosen(String id); }

    /** A full-screen list to pick one from; the current one has a tick and the focus. OK picks, Back closes. */
    private void showChoice(String title, String hint, List<String> ids, List<String> names, final String current, final Chosen then) {
        closeDialog();
        dialog = new FrameLayout(this);
        dialog.setBackgroundColor(Color.argb(240, 12, 13, 17));
        dialog.setClickable(true);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(dp(40));
        sv.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(Ui.SIDE + 80), dp(Ui.TOP + 10), dp(Ui.SIDE + 80), dp(Ui.TOP + 10));
        col.addView(text(title, 26, Ui.TEXT, true));
        TextView sub = text(hint, 15, Ui.SUB, false);
        sub.setPadding(0, dp(4), 0, dp(14));
        col.addView(sub);
        int w = getResources().getDisplayMetrics().widthPixels - 2 * dp(Ui.SIDE + 80);
        View focus = null;
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            final boolean on = id.equals(current);
            View o = option(names.get(i), "", w, new Value() {
                public String get() { return on ? "✓" : ""; }
                public boolean on() { return on; }
                public void toggle() {
                    closeDialog();
                    if (!on) then.chosen(id);
                }
            });
            col.addView(o);
            if (on) focus = o;
            if (focus == null && col.getChildCount() == 3) focus = o;
        }
        sv.addView(col);
        dialog.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(dialog, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setTag(getCurrentFocus());
        pageScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (focus != null) focus.requestFocus();
        backCallback = Ui.onBack(this, backCallback, new Runnable() { public void run() { onBackKey(); } });
    }

    private View languageOption(int avail) {
        return option(I18n.t("language"), "", avail, new Value() {
            public String get() { return I18n.t("lang_value"); }
            public boolean on() { return false; }
            public void toggle() {
                settings.edit().putString("lang", I18n.el() ? "en" : "el").commit();
                final Context app = getApplicationContext();
                work.execute(new Runnable() { public void run() {  // only text changes: redraw, no downloads
                    Engine.background();
                    try { Engine.draw(app); } catch (Throwable ignored) { }
                }});
                getIntent().putExtra("focus", "lang");
                recreate();
            }
        });
    }

    private void refreshStatus() {
        if (status == null) return;
        StringBuilder s = new StringBuilder();
        Set<String> follow = Teams.followed(this);
        if (follow.isEmpty()) s.append(I18n.t("no_clubs")).append('\n');
        boolean basket = Teams.basketball(this);
        // one line when all is well; a club is named only when its data is old (offline, or a source is down)
        long latest = 0;
        StringBuilder old = new StringBuilder();
        for (String id : follow) {
            Teams.Club k = Teams.get(this, id);
            if (k == null) continue;
            for (String team : k.hasBasket && basket ? new String[]{"FC", "BC"} : new String[]{"FC"}) {
                long t = Store.lastUpdate(this, id, team);
                latest = Math.max(latest, t);
                if (t <= 0 || Store.stale(this, id, team))  // never loaded, or old
                    old.append("⚠ ").append(k.label()).append(" · ").append(I18n.t("FC".equals(team) ? "src_espn" : "src_basket"))
                        .append(": ").append(when(t)).append('\n');
            }
        }
        if (!follow.isEmpty())
            s.append(latest <= 0 ? I18n.t("never_updated") : I18n.f("updated", I18n.day(latest).toLowerCase(I18n.locale()) + " " + I18n.time(latest))).append('\n');
        s.append(old);
        s.append('\n').append(I18n.f("version", BuildConfig.VERSION_NAME));
        if (!s.toString().equals(String.valueOf(status.getText()))) status.setText(s.toString());
    }

    private String when(long t) { return t <= 0 ? I18n.t("never") : I18n.day(t) + " " + I18n.time(t); }

    interface Value { String get(); boolean on(); void toggle(); }

    /** A row with a title, an optional explanation and a value pill; OK changes it. */
    private View option(String title, String sub, int width, final Value v) {
        final LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.HORIZONTAL);
        o.setGravity(Gravity.CENTER_VERTICAL);
        o.setPadding(dp(20), dp(10), dp(20), dp(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 18, Ui.TEXT, true));
        if (!sub.isEmpty()) texts.addView(text(sub, 13, Ui.SUB, false));
        o.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView val = text(v.get(), 17, Ui.INK, true);
        val.setPadding(dp(16), dp(6), dp(16), dp(6));
        val.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(dp(16), 0, 0, 0);
        o.addView(val, vp);
        final boolean[] off = {false};  // option rows keep the neutral surface; the pill shows the state
        Ui.focusable(o, 1.0f, off);
        final String langValue = I18n.t("lang_value"), read = I18n.t("read"), showRow = I18n.t("row_show");
        final Runnable show = new Runnable() { public void run() {
            String now = v.get();
            boolean toggle = !now.equals(langValue) && !now.equals(read) && !now.equals(showRow) && !title.equals(I18n.t("favourite"))
                && !"✓".equals(now) && !now.isEmpty();
            val.setText(now);
            val.setVisibility(now.isEmpty() ? View.GONE : View.VISIBLE);
            if (toggle) {
                val.setTextColor(v.on() ? Ui.ON_ACCENT : Ui.TEXT);
                val.setBackground(Ui.rounded(SettingsActivity.this, v.on() ? Ui.ACCENT : Color.rgb(70, 75, 86), 20, 0));
            } else {  // a value to open or change: grey, white only on the focused row, so the focus is never in doubt
                android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_focused}, Ui.rounded(SettingsActivity.this, Color.WHITE, 20, 0));
                bg.addState(new int[]{}, Ui.rounded(SettingsActivity.this, Color.rgb(58, 62, 72), 20, 0));
                val.setBackground(bg);
                val.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}}, new int[]{Ui.INK, Ui.TEXT}));
            }
            o.setContentDescription(title + ", " + now);
        }};
        show.run();
        o.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
            v.toggle();
            if (gone()) return;
            show.run();
            o.announceForAccessibility(v.get());
        }});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        o.setLayoutParams(lp);
        return o;
    }

    /** A read-only block of text. Focusable, so the remote can scroll to it and screen readers read it. */
    private View info(String title, String body, int width) {
        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setPadding(dp(20), dp(10), dp(20), dp(12));
        o.addView(text(title, 18, Ui.TEXT, true));
        TextView b = text(body, 14, Ui.SUB, false);
        b.setPadding(0, dp(2), 0, 0);
        o.addView(b);
        o.setFocusable(true);
        o.setFocusableInTouchMode(true);
        o.setBackground(Ui.rounded(this, Ui.SURFACE, 14, 0));
        o.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) { x.setBackground(Ui.rounded(x.getContext(), has ? Ui.lighten(Ui.SURFACE) : Ui.SURFACE, 14, 0)); }
        });
        o.setContentDescription(title + ". " + body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        o.setLayoutParams(lp);
        return o;
    }

    private View clubTile(final Teams.Club k, boolean followed) {
        final boolean[] on = {followed};
        final FrameLayout f = new FrameLayout(this);
        f.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(6), dp(14), dp(6), dp(10));
        final ImageView crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(crest, new LinearLayout.LayoutParams(dp(70), dp(70)));
        TextView name = text(k.label(), 14, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(6), 0, 0);
        col.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView state = text(I18n.t("following"), 13, Ui.TEXT, false);
        state.setGravity(Gravity.CENTER);
        state.setSingleLine(true);
        col.addView(state, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        f.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final TextView check = text("✓", 14, Ui.ON_ACCENT, true);
        check.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        kp.setMargins(0, dp(8), dp(8), 0);
        f.addView(check, kp);
        final Runnable show = new Runnable() { public void run() {
            check.setTextColor(Ui.ON_ACCENT);
            check.setBackground(Ui.rounded(SettingsActivity.this, Ui.ACCENT, 12, 0));
            check.setVisibility(on[0] ? View.VISIBLE : View.GONE);
            state.setVisibility(View.GONE);  // the ring and the tick show it; the word stays for screen readers
            f.setContentDescription(k.label() + ", " + I18n.t(on[0] ? "following" : "not_following"));
            Ui.paint(f, f.hasFocus() || f.isFocused(), on[0]);
        }};
        Ui.focusable(f, 1.06f, on);
        show.run();
        f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Set<String> follow = new LinkedHashSet<>(Teams.followed(SettingsActivity.this));
            on[0] = !follow.contains(k.id);
            if (on[0]) follow.add(k.id); else follow.remove(k.id);
            Teams.setFollowed(SettingsActivity.this, follow, on[0] ? k : null);
            settings.edit().putBoolean("onboarded", true).apply();
            Ui.theme(SettingsActivity.this);  // the favourite club (the first followed until one is chosen) sets the colours
            show.run();
            f.announceForAccessibility(f.getContentDescription());
            if (on[0] && HomeRows.enabled(SettingsActivity.this)) addRow(k);
            changed();
            refreshStatus();
        }});
        // badge or crest: drawn / loaded in the background (crests are cached on disk after the first time)
        final Context app = getApplicationContext();
        images.execute(new Runnable() { public void run() {
            Engine.background();
            I18n.load(app);
            boolean had = k.crest == null || Net.imageFile(app, k.crest).exists();
            final Bitmap first = Cards.rowLogo(app, k);  // the crest if cached, else the badge: shown at once
            ui.post(new Runnable() { public void run() { if (!gone()) crest.setImageBitmap(first); } });
            if (had) return;
            Net.fetchImage(app, k.crest);  // then the crest, when it arrives
            if (!Net.imageFile(app, k.crest).exists()) return;
            final Bitmap b = Cards.rowLogo(app, k);
            ui.post(new Runnable() { public void run() { if (!gone()) crest.setImageBitmap(b); } });
        }});
        return f;
    }

    // ---------- a full-screen text panel (privacy policy) ----------
    private void showText(String title, String body) {
        closeDialog();
        dialog = new FrameLayout(this);
        dialog.setBackgroundColor(Color.argb(240, 12, 13, 17));
        dialog.setClickable(true);
        final ScrollView sv = new ScrollView(this);
        sv.setFocusable(true);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(dp(40));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(Ui.SIDE + 80), dp(Ui.TOP + 10), dp(Ui.SIDE + 80), dp(Ui.TOP + 10));
        col.addView(text(title, 26, Ui.TEXT, true));
        TextView t = text(body, 16, Color.rgb(214, 218, 226), false);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(0, dp(14), 0, dp(14));
        col.addView(t);
        TextView hint = text(I18n.t("back_to_close"), 13, Ui.SUB, false);
        col.addView(hint);
        sv.addView(col);
        dialog.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(dialog, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setTag(getCurrentFocus());
        pageScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);  // the remote stays inside the panel
        sv.requestFocus();  // up/down scroll the text; Back closes
        backCallback = Ui.onBack(this, backCallback, new Runnable() { public void run() { onBackKey(); } });
    }

    private void closeDialog() {
        if (dialog == null) return;
        View back = (View) dialog.getTag();
        pageScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        root.removeView(dialog);
        dialog = null;
        backCallback = Ui.onBack(this, backCallback, setup ? new Runnable() { public void run() { onBackKey(); } } : null);
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
    }

    // ---------- home-screen rows (classic Android TV home only) ----------
    /** Creates (or restores) the club's row now and asks Android to show it if it is hidden. */
    private void addRow(final Teams.Club k) {
        final Context app = getApplicationContext();
        final String label = k.label();
        work.execute(new Runnable() { public void run() {
            try {
                Net.fetchImage(app, k.crest);
                final long id = HomeRows.channel(app, k.rowKey(), label, Cards.channelLogoLazy(app));
                if (!HomeRows.browsable(app, id)) ui.post(new Runnable() { public void run() { requestBrowsable(id, label); } });
            } catch (Throwable ignored) { }
        }});
    }

    // the system "add this row to the home screen?" prompt, one at a time
    private void requestBrowsable(long id, String label) {
        if (gone() || !resumed) { savePending(id, label); return; }  // asked when the screen is back
        labels.put(id, label);
        for (long[] p : prompts) if (p[0] == id) return;
        prompts.add(new long[]{id});
        savePending();
        if (prompts.size() == 1) askNext();
    }

    private void askNext() {
        long[] p = prompts.peek();
        if (p == null || gone() || !resumed) return;
        try {
            startActivityForResult(new Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContract.EXTRA_CHANNEL_ID, p[0]), 1);
        } catch (Exception e) {  // no such screen on this TV
            prompts.clear();
            savePending();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (prompts.isEmpty()) return;  // a re-created screen: the saved queue is re-checked in onResume
        long[] p = prompts.poll();
        savePending();
        if (res != RESULT_OK) {
            String l = labels.get(p[0]);
            Toast.makeText(this, I18n.f("enable_row", l == null ? "" : l), Toast.LENGTH_LONG).show();
        }
        checkHiddenRows();
        askNext();
    }

    /** Prompts not answered yet survive leaving the screen; they are asked again next time it opens. */
    private void savePending() {
        StringBuilder b = new StringBuilder();
        for (long[] p : prompts) { String l = labels.get(p[0]); b.append(p[0]).append('\t').append(l == null ? "" : l).append('\n'); }
        settings.edit().putString("pendingRows", b.toString()).apply();
    }

    private void savePending(long id, String label) {
        String cur = settings.getString("pendingRows", "");
        if (!cur.contains(id + "\t")) settings.edit().putString("pendingRows", cur + id + "\t" + label + "\n").apply();
    }

    private void resumePrompts() {
        String s = settings.getString("pendingRows", "");
        if (s.isEmpty() || !prompts.isEmpty()) return;
        settings.edit().remove("pendingRows").apply();
        if (!HomeRows.enabled(this)) return;
        final Context app = getApplicationContext();
        for (String line : s.split("\n")) {
            String[] x = line.split("\t", 2);
            try {
                final long id = Long.parseLong(x[0]);
                final String label = x.length > 1 ? x[1] : "";
                work.execute(new Runnable() { public void run() {
                    if (!HomeRows.browsable(app, id)) ui.post(new Runnable() { public void run() { requestBrowsable(id, label); } });
                }});
            } catch (Exception ignored) { }
        }
    }

    /**
     * Rows the user hid on the home screen (declined, or removed there): offered under Options as
     * "The X row is hidden on the home screen · Show", which asks the TV again without touching the follow list.
     */
    private void checkHiddenRows() {
        if (hiddenRows == null) return;
        if (!HomeRows.enabled(this)) { hiddenRows.removeAllViews(); shownHidden = ""; return; }
        final Context app = getApplicationContext();
        final String pending = settings.getString("pendingRows", "");
        work.execute(new Runnable() { public void run() {
            SharedPreferences row = app.getSharedPreferences("row", MODE_PRIVATE);
            final java.util.List<long[]> ids = new java.util.ArrayList<>();
            final java.util.List<String> names = new java.util.ArrayList<>();
            for (String id : Teams.followed(app)) {
                Teams.Club k = Teams.get(app, id);
                if (k == null) continue;
                long ch = row.getLong(k.rowKey(), -1);
                if (ch != -1 && !pending.contains(ch + "	") && !HomeRows.browsable(app, ch)) { ids.add(new long[]{ch}); names.add(k.label()); }
            }
            ui.post(new Runnable() { public void run() {
                if (gone() || hiddenRows == null) return;
                StringBuilder sig = new StringBuilder();
                for (long[] x : ids) sig.append(x[0]).append(',');
                // the same rows as before (back from the TV's "add row" prompt): leave them, so focus stays where it was
                if (sig.toString().equals(shownHidden) && hiddenRows.getChildCount() == ids.size()) return;
                shownHidden = sig.toString();
                hiddenRows.removeAllViews();
                for (int i = 0; i < ids.size(); i++) {
                    final long ch = ids.get(i)[0];
                    final String label = names.get(i);
                    hiddenRows.addView(option(I18n.f("row_hidden", label), "", avail - dp(24), new Value() {
                        public boolean on() { return false; }
                        public String get() { return I18n.t("row_show"); }
                        public void toggle() { requestBrowsable(ch, label); }
                    }));
                }
                final View f = getCurrentFocus();  // the rows above pushed the focused option down: keep it in view
                if (f != null && !ids.isEmpty()) f.post(new Runnable() { public void run() {
                    android.graphics.Rect r = new android.graphics.Rect();
                    f.getDrawingRect(r);
                    f.requestRectangleOnScreen(r);
                }});
            }});
        }});
    }

    /** Settings changed: refresh now (a refresh already running does one more pass with the new settings). */
    private void changed() {
        final Context app = getApplicationContext();
        refresher.execute(new Runnable() { public void run() {  // never blocks row creation or prompts
            Engine.background();
            try { Updater.schedule(app); Engine.refresh(app, false); } catch (Throwable ignored) { }
        }});
    }
}
