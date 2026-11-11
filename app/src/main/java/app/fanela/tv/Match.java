package app.fanela.tv;

/**
 * One match of a followed club (football or basketball).
 * Names are stored as the source gives them (mostly English) and translated when shown (I18n.team);
 * "detail" holds either a language-neutral code (see I18n.detail) or a raw clock like 67' / Q3 04:12.
 */
final class Match {
    String club;        // followed club (ESPN team id) this match belongs to
    String team;        // "FC" or "BC"
    String source;      // espn | el | ec | tsdb | s24 | nba
    String key;         // club|source-id, e.g. 435|espn:401896730
    String comp;        // competition code or label (I18n.comp)
    String league;      // espn slug (gre.1, eng.1, nba), "euroleague", "eurocup", "gbl", "gre.cup"
    String home, away;  // raw names
    boolean clubIsHome; // the followed club plays at home (from ids/codes, never from names)
    String hs, as;      // scores, null before the match
    String state;       // pre | in | post | off
    String detail;      // code (HT, FT, FT_PEN, FT_AET, POSTPONED, CANCELLED, ABANDONED, SUSPENDED, DELAYED,
                        // IN_PROGRESS, AWAITING) or a raw clock
    long start;         // UTC millis
    boolean tbd;        // kick-off time not fixed yet
    int quarter;        // basketball live quarter (5 = OT1 ...)
    int clubWon;        // after the match: 1 win, 0 draw, -1 loss (incl. penalties); 9 = unknown
    String pens;        // shoot-out "4–3" (home–away)
    String homeCrest, awayCrest;
    String channel, channelApp;
    int channelNo;
    String channelLogo;
    boolean channelSure;
    boolean channelKnown = true;
    String listedBy = ""; // where the channel came from: "" Greek rights table / guides, "table" season table, "ronin",
                          // "tv" the TV's own guide; "none": outside Greece and no source knows it
    long seenLiveAt;
    String homeId;      // home team's ESPN id while parsing (channel guess by id); not stored

    Match() { clubWon = 9; }

    static int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    boolean clubHome() { return clubIsHome; }
    String clubScore() { return clubHome() ? hs : as; }
    String oppScore() { return clubHome() ? as : hs; }
    String opponentRaw() { return clubHome() ? away : home; }
    boolean hasScore() { return hs != null && as != null && !hs.isEmpty() && !as.isEmpty() && !"null".equals(hs); }

    String title() { return I18n.team(home) + " – " + I18n.team(away); }
    String scoreLine() {
        if (!hasScore()) return title();
        return I18n.team(home) + " " + hs + "–" + as + " " + I18n.team(away) + (pens != null ? " (" + I18n.t("pens") + " " + pens + ")" : "");
    }

    int result() {
        if (clubWon != 9) return clubWon;
        if (!hasScore()) return 9;
        return Integer.compare(Match.toInt(clubScore()), Match.toInt(oppScore()));
    }

    Match copy() {
        Match m = new Match();
        m.club = club; m.team = team; m.source = source; m.key = key; m.comp = comp; m.league = league;
        m.home = home; m.away = away; m.clubIsHome = clubIsHome; m.hs = hs; m.as = as; m.state = state;
        m.detail = detail; m.start = start; m.tbd = tbd; m.quarter = quarter; m.clubWon = clubWon; m.pens = pens;
        m.homeCrest = homeCrest; m.awayCrest = awayCrest; m.channel = channel; m.channelApp = channelApp;
        m.channelNo = channelNo; m.channelLogo = channelLogo; m.channelSure = channelSure;
        m.channelKnown = channelKnown; m.seenLiveAt = seenLiveAt; m.listedBy = listedBy;
        return m;
    }

    /** Everything that changes how the card looks (identical cards are not redrawn). */
    String signature() {
        return club + team + key + state + detail + hs + as + pens + clubWon + start + tbd + home + away + channel
            + channelNo + channelSure + channelKnown + comp + homeCrest + awayCrest + channelLogo + clubIsHome + listedBy;
    }
}
