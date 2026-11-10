package app.fanela.tv;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home-screen rows (Android TV "preview channels"): one row per followed club.
 * Only the classic Android TV home screen shows them; Google TV and most operator launchers do not, so the app's own
 * home screen is the main place for fixtures and the rows are an optional extra (Settings > Home-screen rows).
 * Switching a row off empties it (the home screen hides empty rows) and keeps it for 7 days, so switching it back on
 * restores it in place without asking again; after that it is deleted.
 */
final class HomeRows {
    static final String ART = "v2";  // logo art version: a new value makes the launcher reload the row logos
    static final long KEEP_HIDDEN = 7 * 86400000L;
    static final String CLASSIC_LAUNCHER = "com.google.android.tvlauncher";

    static final class Tile { String id, title, desc, poster, app, match; boolean live; }

    /** A row logo that is only drawn when the stored one is out of date. */
    interface Logo { String tag(); Bitmap get(); }

    /** Tiles carry only a match key or a whitelisted app; OpenActivity resolves them (it never trusts a raw URL). */
    static Intent openIntent(Context c, String app, String matchKey) {
        Intent i = new Intent(c, OpenActivity.class);
        if (app != null) i.putExtra("app", app);
        if (matchKey != null) i.putExtra("match", matchKey);
        return i;
    }

    /**
     * True when the current home screen can show app rows: the classic Android TV launcher is the default home app
     * and the device is not Google TV.
     */
    static boolean supported(Context c) {
        PackageManager pm = c.getPackageManager();
        if (pm.hasSystemFeature("com.google.android.feature.AMATI_EXPERIENCE")) return false;  // Google TV
        try {
            android.content.pm.ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
            return home != null && home.activityInfo != null && CLASSIC_LAUNCHER.equals(home.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** Rows are on: supported here and not switched off in Settings. */
    static boolean enabled(Context c) {
        return supported(c) && c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("rows", true);
    }

    static boolean installed(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    /** Finds our row (by stored id, then by its internal key or name) or creates it once; returns its channel id. */
    static synchronized long channel(Context c, String key, String name, Logo logo) {
        ContentResolver cr = c.getContentResolver();
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        sp.edit().remove("hidden_" + key).apply();  // in use again
        long id = sp.getLong(key, -1);
        if (id != -1) {
            Cursor q = cr.query(TvContract.buildChannelUri(id), new String[]{TvContract.Channels._ID}, null, null, null);
            if (q == null) throw new IllegalStateException("TV provider unavailable");  // never create a duplicate row
            boolean ok = q.moveToFirst();
            q.close();
            if (ok) { syncName(c, id, key, name); logoOnce(c, id, key, logo); return id; }
        }
        // Lost the id (data cleared, old version): look for our existing channel before creating a new one.
        Cursor all = cr.query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, TvContract.Channels.COLUMN_DISPLAY_NAME}, null, null, null);
        if (all == null) throw new IllegalStateException("TV provider unavailable");
        long found = -1;
        while (all.moveToNext()) {
            if (key.equals(all.getString(1)) || (all.getString(1) == null && name.equals(all.getString(2)))) { found = all.getLong(0); break; }
        }
        all.close();
        if (found != -1) {
            sp.edit().putLong(key, found).commit();
            ContentValues v = new ContentValues();
            v.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, key);
            cr.update(TvContract.buildChannelUri(found), v, null, null);
            syncName(c, found, key, name);
            logoOnce(c, found, key, logo);
            return found;
        }
        ContentValues ch = new ContentValues();
        ch.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW);
        ch.put(TvContract.Channels.COLUMN_DISPLAY_NAME, name);
        ch.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, key);
        ch.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, new Intent(c, HomeActivity.class).toUri(Intent.URI_INTENT_SCHEME));
        id = ContentUris.parseId(cr.insert(TvContract.Channels.CONTENT_URI, ch));
        sp.edit().putLong(key, id).putString("name_" + key, name).commit();
        logoOnce(c, id, key, logo);
        try { TvContract.requestChannelBrowsable(c, id); } catch (Exception ignored) { }
        return id;
    }

    /** Renames the row when the language changes. */
    static void syncName(Context c, long id, String key, String name) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        if (name.equals(sp.getString("name_" + key, null))) return;
        ContentValues v = new ContentValues();
        v.put(TvContract.Channels.COLUMN_DISPLAY_NAME, name);
        try { c.getContentResolver().update(TvContract.buildChannelUri(id), v, null, null); sp.edit().putString("name_" + key, name).apply(); }
        catch (Exception ignored) { }
    }

    /** Empties a row and remembers when; it is deleted only after KEEP_HIDDEN. */
    static synchronized void hide(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        long id = sp.getLong(key, -1);
        if (id == -1) return;
        long since = sp.getLong("hidden_" + key, 0), now = System.currentTimeMillis();
        if (since == 0) {
            try { setTiles(c, id, Collections.<Tile>emptyList()); } catch (Exception ignored) { }
            sp.edit().putLong("hidden_" + key, now).apply();
        } else if (now - since > KEEP_HIDDEN) removeChannel(c, key);
    }

    /** Deletes a row for good. */
    static synchronized void removeChannel(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        long id = sp.getLong(key, -1);
        if (id != -1) { try { c.getContentResolver().delete(TvContract.buildChannelUri(id), null, null); } catch (Exception ignored) { } }
        sp.edit().remove(key).remove("logo_" + key).remove("name_" + key).remove("hidden_" + key).commit();
    }

    static String clubOf(String key) {
        return key == null ? null : key.startsWith("club_") ? key.substring(5) : null;
    }

    /**
     * Hides the rows of clubs that are no longer followed (reads the follow list itself, under the same lock as channel(),
     * so a row created a moment ago for a new follow is never touched). Also finds club rows the settings lost track of.
     */
    static synchronized void removeUnfollowed(Context c) {
        Set<String> follow = enabled(c) ? Teams.followed(c) : java.util.Collections.<String>emptySet();
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        Map<String, Long> rows = new HashMap<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if (clubOf(e.getKey()) != null && e.getValue() instanceof Long) rows.put(e.getKey(), (Long) e.getValue());
        try (Cursor q = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                String key = q.getString(1);
                if (clubOf(key) == null || follow.contains(clubOf(key))) continue;
                if (!rows.containsKey(key) || rows.get(key) != q.getLong(0)) {  // not ours in the settings: an orphan
                    try { c.getContentResolver().delete(TvContract.buildChannelUri(q.getLong(0)), null, null); } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
        for (String key : rows.keySet()) {
            String club = clubOf(key);
            if (follow.contains(club)) continue;
            boolean first = sp.getLong("hidden_" + key, 0) == 0;
            hide(c, key);
            if (first && !Teams.followed(c).contains(club)) Store.forgetClub(c, club);  // cards, guide cache, timestamps
        }
    }

    /** Writes the row logo only when it is new or out of date. */
    static void logoOnce(Context c, long id, String key, Logo logo) {
        if (logo == null) return;
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        String tag = id + ":" + ART + ":" + logo.tag();
        if (tag.equals(sp.getString("logo_" + key, null))) return;
        Bitmap bmp = logo.get();
        if (bmp == null) return;
        try (OutputStream os = c.getContentResolver().openOutputStream(TvContract.buildChannelLogoUri(id))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            sp.edit().putString("logo_" + key, tag).apply();
        } catch (Exception ignored) {
        } finally {
            bmp.recycle();
        }
    }

    static boolean browsable(Context c, long id) {
        try (Cursor q = c.getContentResolver().query(TvContract.buildChannelUri(id),
                new String[]{TvContract.Channels.COLUMN_BROWSABLE}, null, null, null)) {
            return q != null && q.moveToFirst() && q.getInt(0) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Puts these tiles in the row: updates existing ones in place, adds new ones, removes the rest. */
    static synchronized void setTiles(Context c, long channelId, List<Tile> tiles) {
        ContentResolver cr = c.getContentResolver();
        Map<String, Long> existing = new HashMap<>();
        Cursor q = cr.query(TvContract.buildPreviewProgramsUriForChannel(channelId),
            new String[]{TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID},
            null, null, null);
        if (q != null) {
            while (q.moveToNext()) {
                String pid = q.getString(1);
                if (pid == null || existing.containsKey(pid)) cr.delete(TvContract.buildPreviewProgramUri(q.getLong(0)), null, null);
                else existing.put(pid, q.getLong(0));
            }
            q.close();
        }
        int weight = tiles.size();
        for (Tile t : tiles) {
            ContentValues p = new ContentValues();
            p.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId);
            p.put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_CHANNEL);
            p.put(TvContract.PreviewPrograms.COLUMN_TITLE, t.title);
            p.put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, t.desc);
            p.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, t.poster);
            p.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
            p.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, openIntent(c, t.app, t.match).toUri(Intent.URI_INTENT_SCHEME));
            p.put(TvContract.PreviewPrograms.COLUMN_LIVE, t.live ? 1 : 0);  // the launcher draws its own LIVE badge
            p.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, t.id);
            p.put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight--);
            Long pid = existing.remove(t.id);
            if (pid != null) cr.update(TvContract.buildPreviewProgramUri(pid), p, null, null);
            else cr.insert(TvContract.PreviewPrograms.CONTENT_URI, p);
        }
        for (long pid : existing.values()) cr.delete(TvContract.buildPreviewProgramUri(pid), null, null);
    }

}
