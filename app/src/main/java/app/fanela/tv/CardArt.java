package app.fanela.tv;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/**
 * The images a card needs, loaded on demand on the drawing thread: both crests on the match, the followed club's own
 * crest (for the ghost) and the channel logos. CardArt owns every bitmap it hands out (styles must not recycle them)
 * and releases them all once the card is drawn.
 */
final class CardArt {
    private final Context c;
    private final CardModel m;
    private final Teams.Club club;
    private Bitmap mine, opp, mineRaw, oppRaw, clubCrest;
    private boolean mineDone, oppDone, mineRawDone, oppRawDone, clubDone, ghostDone;
    private String ghostUrl;
    private Bitmap ghost;
    private List<Channels.Logo> logos;
    private final List<Bitmap> owned = new ArrayList<>();

    CardArt(Context c, CardModel m) { this(c, m, null); }

    /** club: the followed club the card is for (its crest is the ghost's candidate); null for none. */
    CardArt(Context c, CardModel m, Teams.Club club) { this.c = c; this.m = m; this.club = club; }

    /**
     * My crest as the match's feed gives it, as the club shows it (discs, plates, the ticket body): an outline crest
     * gets the white inside it is printed with (CrestFill: Olympiacos).
     */
    Bitmap myCrest() { if (!mineDone) { mine = keep(CrestFill.filled(myCrestRaw())); mineDone = true; } return mine; }
    Bitmap oppCrest() { if (!oppDone) { opp = keep(CrestFill.filled(oppCrestRaw())); oppDone = true; } return opp; }

    /** The crests as downloaded (CrestFill.filled gives them the white they are printed with). */
    private Bitmap myCrestRaw() { if (!mineRawDone) { mineRaw = keep(Net.image(c, m.myCrest)); mineRawDone = true; } return mineRaw; }
    private Bitmap oppCrestRaw() { if (!oppRawDone) { oppRaw = keep(Net.image(c, m.oppCrest)); oppRawDone = true; } return oppRaw; }


    /**
     * The club's own crest (Teams.Club.crest: ESPN's 500 px club crest) as the club prints it (CrestFill); null when it
     * has none or it isn't cached.
     */
    Bitmap clubCrest() {
        if (!clubDone) {
            clubDone = true;
            String u = clubCrestUrl();
            if (u != null) clubCrest = u.equals(m.myCrest) ? myCrest() : keep(CrestFill.filled(keep(Net.image(c, u))));
        }
        return clubCrest;
    }

    private String clubCrestUrl() { return club == null || club.crest == null || club.crest.isEmpty() ? null : club.crest; }

    /**
     * The club's best crest, for the ghost: whichever of the club's own crest and the match's crest is larger (alpha
     * bounding box; a tie keeps the match's). The ghost stands for the club, so a basketball feed's small crest
     * (EuroLeague's Olympiacos is 192 px) doesn't push Signature to its fallback. It is the crest as the club prints
     * it (CrestFill: Olympiacos white inside its red outline), as on the plates, never the bare line art: red lines on
     * the club's red light read red on red (owner, fx6). Its analysis is the filled crest's, keyed by URL like the
     * plates' (the alpha follows its brightness: ClubTokens.ghostAlpha).
     */
    Bitmap ghostCrest() {
        if (!ghostDone) {
            ghostDone = true;
            Bitmap a = myCrest(), b = clubCrest();
            ClubTokens.CrestInfo ia = CardStyles.crest(m.myCrest, a), ib = b == a ? null : CardStyles.crest(clubCrestUrl(), b);
            boolean useClub = ClubTokens.clubCrestIsBetter(ia, ib);
            ghost = useClub ? b : a;
            ghostUrl = useClub ? clubCrestUrl() : m.myCrest;
        }
        return ghost;
    }

    /** The URL of ghostCrest() (its analysis is cached by URL). */
    String ghostCrestUrl() { ghostCrest(); return ghostUrl; }

    /** The channel logos in the match's order (Channels.logos): logo URL or null, and the number, per channel. */
    List<Channels.Logo> logos() { if (logos == null) logos = Channels.logos(c, m.match); return logos; }

    private final java.util.Map<String, Bitmap> trims = new java.util.HashMap<>();

    /** A logo trimmed of transparent padding, once per card (measure and draw passes share it). */
    Bitmap trimmed(String url) {
        if (url == null) return null;
        if (trims.containsKey(url)) return trims.get(url);
        Bitmap src = image(url), t = src == null ? null : keep(Cards.trim(src));
        trims.put(url, t);
        return t;
    }

    /** Any image by URL (a channel logo); owned like the crests. */
    Bitmap image(String url) { return keep(Net.image(c, url)); }

    private Bitmap keep(Bitmap b) { if (b != null && !owned.contains(b)) owned.add(b); return b; }

    /** Frees every bitmap this card loaded. */
    void release() {
        for (Bitmap b : owned) if (!b.isRecycled()) b.recycle();
        owned.clear();
    }
}
