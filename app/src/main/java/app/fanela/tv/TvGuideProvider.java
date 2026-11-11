package app.fanela.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import java.util.ArrayList;
import java.util.List;

/**
 * The TV's own programme guide (Android's TvProvider): a match on one of the viewer's own channels, with the channel's
 * real number. Free and on the TV, but only channels whose app fills that guide (usually the tuner's antenna, cable or
 * satellite channels; operator apps rarely do) and only as far ahead as they fill it.
 *
 * Off by default. It needs android.permission.READ_TV_LISTINGS, which only the debug build declares and the viewer must
 * grant, and the "tvguide" switch (DebugReceiver TVGUIDE). Not used in Greece, whose sources stay as they are.
 * The provider doesn't allow a selection for other apps, so programmes are read per channel and time window and
 * filtered here.
 */
final class TvGuideProvider implements Watch {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final long WINDOW = 30 * 60000L;  // kick-off within half an hour of the programme's start
    static final int MAX_MATCHES = 4, MAX_CHANNELS = 400;

    static final class Channel { long id; String name, number; }
    static final class Programme { long channel, start; String title, text; }

    private final List<Channel> channels = new ArrayList<>();

    public String id() { return "tv"; }

    /** Switched on (debug) and the permission granted. */
    static boolean enabled(Context c) {
        if (!BuildConfig.DEBUG) return false;  // never in a release build, whatever the settings say
        if (c == null || !c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("tvguide", false)) return false;
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean guess(Match m) { return false; }

    /** The TV's channel list, once per refresh. */
    public void prepare(Context c) {
        channels.clear();
        if (!enabled(c)) return;
        String[] cols = {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_DISPLAY_NUMBER};
        try (Cursor q = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            while (q != null && q.moveToNext() && channels.size() < MAX_CHANNELS) {
                Channel ch = new Channel();
                ch.id = q.getLong(0); ch.name = q.getString(1); ch.number = q.getString(2);
                if (ch.name != null && !ch.name.trim().isEmpty()) channels.add(ch);
            }
        } catch (Exception ignored) { }  // no guide on this TV, or the permission was taken back
    }

    /** Programmes around the club's next few kick-offs, on every channel. */
    public Object fetch(Context c, Teams.Club k, List<Match> known, boolean basket) {
        List<Programme> out = new ArrayList<>();
        if (channels.isEmpty()) return out;
        long now = System.currentTimeMillis();
        int n = 0;
        String[] cols = {TvContract.Programs.COLUMN_TITLE, TvContract.Programs.COLUMN_EPISODE_TITLE,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS};
        for (Match m : known) {
            if (!k.id.equals(m.club) || !"pre".equals(m.state) || m.tbd || m.start < now - WINDOW || m.start > now + 7 * 86400000L) continue;
            if (n++ >= MAX_MATCHES) break;
            for (Channel ch : channels) {
                try (Cursor q = c.getContentResolver().query(
                        TvContract.buildProgramsUriForChannel(ch.id, m.start - WINDOW, m.start + WINDOW), cols, null, null, null)) {
                    while (q != null && q.moveToNext()) {
                        Programme p = new Programme();
                        p.channel = ch.id; p.title = q.getString(0);
                        p.text = (q.getString(1) == null ? "" : q.getString(1)) + " " + (q.getString(2) == null ? "" : q.getString(2));
                        p.start = q.getLong(3);
                        out.add(p);
                    }
                } catch (Exception ignored) { }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public void apply(List<Match> all, Teams.Club k, Object listings) {
        if (!(listings instanceof List) || ((List<?>) listings).isEmpty()) return;
        List<Programme> ps = (List<Programme>) listings;
        for (Match m : all) {
            if (!k.id.equals(m.club) || !"pre".equals(m.state)) continue;
            Programme p = find(ps, m);
            if (p == null) continue;
            Channel ch = channel(p.channel);
            if (ch == null) continue;
            m.channel = ch.name.trim();
            m.channelNo = number(ch.number);
            m.channelLogo = null; m.channelApp = null;
            m.channelKnown = true; m.channelSure = true;
            m.listedBy = "tv";
        }
    }

    private Channel channel(long id) {
        for (Channel c : channels) if (c.id == id) return c;
        return null;
    }

    /** "101" -> 101, "4-1" or "4.1" -> 4 (the main number), anything else 0. */
    static int number(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("^\\s*(\\d{1,5})").matcher(s);
        return x.find() ? Integer.parseInt(x.group(1)) : 0;
    }

    /** The programme showing the match: both teams named (title, episode or description), starting near kick-off. */
    static Programme find(List<Programme> ps, Match m) {
        Programme best = null;
        for (Programme p : ps) {
            if (Math.abs(p.start - m.start) > WINDOW) continue;
            String text = (p.title == null ? "" : p.title) + " " + (p.text == null ? "" : p.text);
            if (!names(text, m.home) || !names(text, m.away)) continue;
            if (best == null || Math.abs(p.start - m.start) < Math.abs(best.start - m.start)) best = p;
        }
        return best;
    }

    /** The text names the team: its full name, its name without "FC"-type words, or a distinctive word of it. */
    static boolean names(String text, String team) {
        String t = " " + fold(text) + " ";
        for (String alias : aliases(team)) if (!alias.isEmpty() && t.contains(" " + alias + " ")) return true;
        return false;
    }

    static List<String> aliases(String team) {
        List<String> out = new ArrayList<>();
        String full = fold(team);
        out.add(full);
        String bare = (" " + full + " ").replaceAll(" (FC|CF|AFC|SC|AC|CD|SV|FK|SK|BC|CLUB|THE) ", " ").replaceAll("\\s+", " ").trim();
        out.add(bare);
        String gr = fold(I18n.grName(team));  // a Greek guide on a Greek viewer's TV
        if (!gr.equals(full)) out.add(gr);
        // one distinctive word ("Arsenal", "Tottenham"); never a word many clubs share
        for (String w : bare.split(" "))
            if (w.length() >= 5 && !COMMON.contains(w)) { out.add(w); break; }
        return out;
    }

    private static final java.util.Set<String> COMMON = new java.util.HashSet<>(java.util.Arrays.asList(
        "UNITED", "CITY", "REAL", "ATHLETIC", "ATLETICO", "SPORTING", "INTER", "RACING", "OLYMPIQUE", "BORUSSIA", "DYNAMO",
        "DINAMO", "STADE", "ROYAL", "UNION", "CLUB", "SPORT", "SPORTS", "DEPORTIVO", "WANDERERS", "ROVERS", "COUNTY", "TOWN",
        "ALBION", "HOTSPUR", "ATHLETICO", "SAINT", "NORTH", "SOUTH", "WEST", "EAST", "MANCHESTER", "LIVERPOOL", "MADRID",
        "MILAN", "ROMA", "LOS", "ANGELES", "NEW", "YORK", "SAN", "SANTA", "VILLA"));

    /** Accent-free capitals, letters and digits only ("Atlético" -> "ATLETICO"). */
    static String fold(String s) {
        return Cards.key(s == null ? "" : s).replaceAll("[^\\p{L}0-9]+", " ").trim();
    }
}
