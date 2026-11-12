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
 * A club unfollowed, or the rows switched off, takes its row off the home screen as soon as the launcher can take it:
 * the row is retired (retire(): renamed out of the app's keys, its tiles left as they are, listed in the "row" prefs as
 * retire_<channel id>) and deleted once no Fanela screen is in front (purgeRetired: a few seconds after the last one
 * stops, or the next pass in the background). QAS-4: a row that leaves the classic launcher's list while the launcher
 * is paused behind Fanela crashes it on its return (HomeListView IndexOutOfBounds when that row had the focus; once it
 * came back with no rows until a reboot). Deleted, or only emptied (the launcher hides an empty channel, so emptying
 * takes the row off its list the same way: tested, it crashed too), and an app may not clear COLUMN_BROWSABLE (the TV
 * provider refuses it); so nothing about the row changes until Fanela is out of the way. Following again or switching
 * back on creates the row anew, with the system's add-row prompt (a retired row is never reused). Only while another
 * home screen is the default are rows emptied and kept for 7 days (rowFate), so a switch back to the Android TV home
 * finds them in place.
 */
final class HomeRows {
    static final String ART = "v3";  // logo art version: a new value makes the launcher reload the row logos
    static final long KEEP_HIDDEN = 7 * 86400000L;
    /** The "row" prefs key of a retired row, with its channel id; the value is when it was retired. */
    static final String RETIRED = "retire_";
    /** A retired channel's internal provider id: the key it had, behind this (no "club_" / "sport_" scan finds it). */
    static final String RETIRED_ID = "retired:";
    /** After the last Fanela screen stops: the wait before the retired rows are deleted (the launcher is in front). */
    static final long LEFT_FRONT_MS = 4000;
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
        if (pkg == null || pkg.isEmpty()) return false;
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

    /**
     * What becomes of a club's row: kept, emptied and kept for KEEP_HIDDEN (hide), or taken off the home screen now
     * (REMOVE: retired at once, its channel deleted later, deleteNow).
     */
    enum Fate { KEEP, HIDE, REMOVE }

    /**
     * A club row's fate: a club no longer followed loses its row at once, and so does every row when the viewer
     * switches rows off; while the classic home screen isn't the default (supported false) a followed club's row is
     * only emptied (hide), as it can't be seen anyway and a switch back restores it. A followed club with rows on and
     * shown keeps its row: an upgrade, a refresh or a language change never deletes it. Pure: unit-tested.
     */
    static Fate rowFate(String club, Set<String> followed, boolean supported, boolean rowsOn) {
        if (club == null || !followed.contains(club)) return Fate.REMOVE;
        if (!rowsOn) return Fate.REMOVE;
        return supported ? Fate.KEEP : Fate.HIDE;
    }

    /**
     * QAS-4: a retired row's channel is deleted now only while no Fanela screen is in front, however long ago it was
     * retired: the launcher, paused behind Fanela, crashed when a row it had focused left its list, deleted or emptied,
     * and the wait does not help (emptied and kept 3 minutes, it crashed the same way). retiredAt 0: not retired, never
     * deleted here. Pure: unit-tested.
     */
    static boolean deleteNow(long retiredAt, long now, boolean fanelaInFront) {
        return retiredAt > 0 && retiredAt <= now + 60000L && !fanelaInFront;
    }

    /** The channel id of a retire_ key ("retire_123" -> 123); -1 for any other key. */
    static long retiredChannel(String key) {
        if (key == null || !key.startsWith(RETIRED)) return -1;
        try { return Long.parseLong(key.substring(RETIRED.length())); } catch (NumberFormatException e) { return -1; }
    }

    /** Rows switched on in Settings (whatever the home screen). */
    static boolean rowsOn(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("rows", true);
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

    /**
     * QAS-4: marks a row to go without touching what the launcher shows: its provider id is renamed (channel() never
     * finds it again: a new follow makes a new row, with the add-row prompt; no refresh updates it) and it is listed as
     * retire_<id>; purgeRetired deletes it once Fanela is out of the way. Its tiles stay until then (emptied, the
     * launcher would drop it from its list at once).
     */
    static synchronized void retire(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        long id = sp.getLong(key, -1);
        if (id != -1) retireChannel(c, id, key);
        sp.edit().remove(key).remove("logo_" + key).remove("name_" + key).remove("hidden_" + key).commit();
    }

    /** Retires one channel (ours, by id): renamed out of the app's keys, listed with when; deleted by purgeRetired. */
    static synchronized void retireChannel(Context c, long id, String key) {
        try {
            ContentValues v = new ContentValues();
            v.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, RETIRED_ID + key);
            c.getContentResolver().update(TvContract.buildChannelUri(id), v, null, null);
        } catch (Exception ignored) { }
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        if (sp.getLong(RETIRED + id, 0) == 0) sp.edit().putLong(RETIRED + id, System.currentTimeMillis()).commit();
    }

    /** Some row is retired and waits to be deleted. */
    static boolean hasRetired(Context c) {
        for (String k : c.getSharedPreferences("row", Context.MODE_PRIVATE).getAll().keySet()) if (retiredChannel(k) != -1) return true;
        return false;
    }

    /**
     * QAS-4: deletes the retired rows once no Fanela screen is in front (deleteNow); while one is, nothing. Retired
     * channels the prefs lost (data cleared) are listed again from their provider id.
     */
    static synchronized void purgeRetired(Context c) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean front = Brand.open();
        if (front) return;
        Map<Long, Long> listed = new HashMap<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            long id = retiredChannel(e.getKey());
            if (id != -1 && e.getValue() instanceof Long) listed.put(id, (Long) e.getValue());
        }
        try (Cursor q = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                String pid = q.getString(1);
                if (pid != null && pid.startsWith(RETIRED_ID) && !listed.containsKey(q.getLong(0))) {
                    sp.edit().putLong(RETIRED + q.getLong(0), now).commit();
                    listed.put(q.getLong(0), now);
                }
            }
        } catch (Exception ignored) { }
        for (Map.Entry<Long, Long> e : listed.entrySet()) {
            if (!deleteNow(e.getValue(), now, front)) continue;
            try { c.getContentResolver().delete(TvContract.buildChannelUri(e.getKey()), null, null); } catch (Exception ignored) { }
            sp.edit().remove(RETIRED + e.getKey()).commit();
        }
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * The last Fanela screen stopped (Brand.hidden): a few seconds on, with the launcher in front and no Fanela screen
     * back, the retired rows are deleted (on a thread of their own: the TV provider is a database).
     */
    static void leftFront(Context c) {
        final Context app = c.getApplicationContext();
        if (!hasRetired(app)) return;
        MAIN.postDelayed(new Runnable() { public void run() {
            if (Brand.open()) return;
            new Thread(new Runnable() { public void run() {
                try { purgeRetired(app); } catch (Throwable ignored) { }
            }}, "fanela-rows").start();
        }}, LEFT_FRONT_MS);
    }

    /** Deletes a row for good (only while another home screen is the default: hide(); else retire()). */
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
     * Deletes the rows of clubs that are no longer followed, and every row when rows are switched off; empties them
     * (hide) while another home screen is the default (rowFate). Reads the follow list itself, under the same lock as
     * channel(), so a row created a moment ago for a new follow is never touched. Also deletes club rows the settings
     * lost track of (orphans) unless their club keeps its row.
     */
    static synchronized void removeUnfollowed(Context c) {
        Set<String> follow = Teams.followed(c);
        boolean supported = supported(c), on = rowsOn(c);
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        Map<String, Long> rows = new HashMap<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if (clubOf(e.getKey()) != null && e.getValue() instanceof Long) rows.put(e.getKey(), (Long) e.getValue());
        try (Cursor q = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                String key = q.getString(1);
                if (clubOf(key) == null || rowFate(clubOf(key), follow, supported, on) == Fate.KEEP) continue;
                if (!rows.containsKey(key) || rows.get(key) != q.getLong(0)) retireChannel(c, q.getLong(0), key);  // an orphan
            }
        } catch (Exception ignored) { }
        for (String key : rows.keySet()) {
            String club = clubOf(key);
            switch (rowFate(club, follow, supported, on)) {
                case KEEP: break;
                case HIDE: hide(c, key); break;
                default:
                    retire(c, key);  // QAS-4: deleted once Fanela is out of the way (purgeRetired)
                    // a club unfollowed (still, not followed again meanwhile): its cards, guide cache and timestamps go too
                    if (!follow.contains(club) && !Teams.followed(c).contains(club)) Store.forgetClub(c, club);
            }
        }
        purgeRetired(c);
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

    /**
     * A background refresh has something to do: a followed club or sport has no row yet (the refresh creates it), or one
     * of our rows (club_ or sport_) is on the home screen. False when every stored row is hidden.
     */
    static boolean worthRefreshing(Context c) {
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        if (hasRetired(c)) return true;  // a retired row to delete (QAS-4)
        for (String id : Teams.followed(c)) if (sp.getLong("club_" + id, -1) == -1) return true;
        for (String sport : Sports.followed(c)) if (sp.getLong(Sports.rowKey(sport), -1) == -1) return true;  // a sport's row to create
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if ((clubOf(e.getKey()) != null || sportOf(e.getKey()) != null) && e.getValue() instanceof Long && browsable(c, (Long) e.getValue())) return true;
        return false;
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

    // ---------- sport rows (F1 / UFC, stream D) ----------
    /** "sport_f1" -> "f1"; null for any other key (club rows, unknown sports). */
    static String sportOf(String key) {
        if (key == null || !key.startsWith("sport_")) return null;
        String s = key.substring(6);
        return Sports.known(s) ? s : null;
    }

    /**
     * Deletes the rows of sports no longer followed, and every sport row when rows are switched off; empties them
     * (hide) while another home screen is the default: the clubs' rowFate with Sports.followed. Under the same lock as
     * channel(), so a row created a moment ago for a new follow is never touched. Also deletes sport rows the settings
     * lost track of (orphans) unless their sport keeps its row. A sport unfollowed also loses its card files
     * (EventStore.forgetSport).
     */
    static synchronized void removeUnfollowedSports(Context c) {
        Set<String> follow = Sports.followed(c);
        boolean supported = supported(c), on = rowsOn(c);
        SharedPreferences sp = c.getSharedPreferences("row", Context.MODE_PRIVATE);
        Map<String, Long> rows = new HashMap<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if (sportOf(e.getKey()) != null && e.getValue() instanceof Long) rows.put(e.getKey(), (Long) e.getValue());
        try (Cursor q = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                String key = q.getString(1);
                if (sportOf(key) == null || rowFate(sportOf(key), follow, supported, on) == Fate.KEEP) continue;
                if (!rows.containsKey(key) || rows.get(key) != q.getLong(0)) retireChannel(c, q.getLong(0), key);  // an orphan
            }
        } catch (Exception ignored) { }
        for (String key : rows.keySet()) {
            String sport = sportOf(key);
            switch (rowFate(sport, follow, supported, on)) {
                case KEEP: break;
                case HIDE: hide(c, key); break;
                default:
                    retire(c, key);  // QAS-4: deleted once Fanela is out of the way (purgeRetired)
                    // a sport unfollowed (still, not followed again meanwhile): its cards and row signature go too
                    if (!follow.contains(sport) && !Sports.followed(c).contains(sport)) EventStore.forgetSport(c, sport);
            }
        }
        purgeRetired(c);
    }
}
