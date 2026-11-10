package app.fanela.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Channel logos and numbers. The MagentaTV line-up (every channel with its number and official logo) ships with the
 * app (res/raw/channels.json) and is refreshed weekly; SPORT FM TV and the "likely" network logos (Magenta Sport,
 * Novasports) are bundled drawables ("res:" images, see Net.image). A match on two channels gets both logos.
 */
final class Channels {
    static final class Logo { String url; int no; Logo(String u, int n) { url = u; no = n; } }

    private static volatile Map<String, Logo> catalog;
    private static volatile long catalogAt;

    /** Matching key: no accents, no spaces or punctuation, no trailing HD ("Novasports 2HD" = "Novasports2 HD"). */
    static String norm(String s) {
        String k = Cards.key(s == null ? "" : s).replaceAll("[^\\p{L}0-9]", "");
        return k.endsWith("HD") && k.length() > 2 ? k.substring(0, k.length() - 2) : k;
    }

    private static Map<String, Logo> catalog(Context c) {
        File f = new File(c.getFilesDir(), "channels.json");
        Map<String, Logo> cur = catalog;
        if (cur != null && f.lastModified() <= catalogAt) return cur;
        long mod = f.exists() ? f.lastModified() : 0;
        Map<String, Logo> out = new HashMap<>();
        try {
            String text;
            if (f.exists()) text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            else try (InputStream in = c.getResources().openRawResource(R.raw.channels)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                text = b.toString("UTF-8");
            }
            JSONArray a = new JSONArray(text);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("l").startsWith("https://")) continue;
                out.put(norm(o.optString("t")), new Logo(o.optString("l"), o.optInt("n")));
            }
        } catch (Exception ignored) { }
        catalog = out;
        catalogAt = mod;
        return out;
    }

    /** Weekly refresh of the MagentaTV line-up (new channels, new logos). Background thread. */
    static void refresh(Context c) {
        if (!BuildConfig.GUIDES) return;
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("channelsAt", 0) < 7 * 86400000L) return;
        try {
            JSONArray chs = new JSONObject(Net.http("https://www.magentatv.gr/api/channels")).getJSONObject("stripes").getJSONArray("channels");
            JSONArray out = new JSONArray();
            for (int i = 0; i < chs.length(); i++) {
                JSONObject ch = chs.getJSONObject(i);
                if (ch.optBoolean("isTestChannel")) continue;
                JSONObject logos = ch.optJSONObject("logos");
                out.put(new JSONObject().put("t", ch.optString("title")).put("n", ch.optInt("number"))
                    .put("l", logos == null ? "" : logos.optString("wide", logos.optString("square", ""))));
            }
            if (out.length() >= 40) {
                Store.writeAtomic(new File(c.getFilesDir(), "channels.json"), out.toString());
                sp.edit().putLong("channelsAt", now).apply();
            }
        } catch (Exception ignored) { }
    }

    /** Logos for a family name used before the exact channel is known, or for channels not on MagentaTV. */
    private static Logo family(String n) {
        java.util.regex.Matcher fm = java.util.regex.Pattern.compile("(ΣΠΟΡ|SPORT)FMTV([0-9]?)").matcher(n);
        if (fm.matches()) return new Logo("res:ch_sportfm", fm.group(2).isEmpty() ? 0 : Integer.parseInt(fm.group(2)));
        if (n.equals("MAGENTASPORT")) return new Logo("res:ch_magenta_sport", 0);
        if (n.equals("NOVASPORTS")) return new Logo("res:ch_novasports", 0);
        return null;
    }

    /** Up to two logos for where the match is on (empty when the channel is not known). */
    static List<Logo> logos(Context c, Match m) {
        List<Logo> out = new ArrayList<>();
        if (!m.channelKnown || m.channel == null || m.channel.trim().isEmpty()) return out;
        String[] parts = m.channel.split("\\s*·\\s*");
        Map<String, Logo> cat = catalog(c);
        for (int i = 0; i < parts.length && out.size() < 2; i++) {
            if (i == 0 && m.channelSure && m.channelLogo != null && !m.channelLogo.isEmpty()) { out.add(new Logo(m.channelLogo, m.channelNo)); continue; }
            String n = norm(parts[i]);
            Logo f = family(n), k = cat.get(n);
            Logo l = f != null ? f : k;
            if (l == null) return new ArrayList<>();  // one unknown name: show the text instead of half the logos
            out.add(m.channelSure || f != null ? l : new Logo(l.url, 0));  // a guess never shows a channel number
        }
        return out;
    }

    /** Number of the first channel, when a guide named it but did not give a number ("Novasports 2HD" -> 323). */
    static int number(Context c, String channel) {
        catalog(c);
        return numberCached(channel);
    }

    /** As number(), from the line-up already loaded (Guide.apply has no Context). 0 until it is loaded. */
    static int numberCached(String channel) {
        Map<String, Logo> cat = catalog;
        if (channel == null) return 0;
        String n = norm(channel.split("\\s*·\\s*")[0]);
        Logo f = family(n);
        if (f != null) return f.no;
        Logo l = cat == null ? null : cat.get(n);
        return l == null ? 0 : l.no;
    }

    /** Loads the line-up (once per refresh, before Guide.apply). */
    static void load(Context c) { catalog(c); }
}
