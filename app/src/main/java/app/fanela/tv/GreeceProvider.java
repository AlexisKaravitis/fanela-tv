package app.fanela.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Greece, exactly as before Fanela went worldwide: the rights table (Cards.channelGuess) for the expected channel,
 * then the MagentaTV and Sport24 guides and SofaScore for the exact channel, number and logo (Guide, Channels, Sofa).
 */
final class GreeceProvider implements Watch {
    private List<Guide.Airing> s24 = new ArrayList<>();

    public String id() { return "gr"; }

    public boolean guess(Match m) {
        Cards.channelGuess(m);
        m.listedBy = "";
        return m.channelKnown;
    }

    public void prepare(Context c) {
        Channels.refresh(c);
        Channels.load(c);
        s24 = Guide.sport24(c);
    }

    /** {MagentaTV airings, SofaScore airings} for the club. */
    public Object fetch(Context c, Teams.Club k, List<Match> known, boolean basket) {
        return new Object[]{Guide.magenta(c, k, known), Sofa.airings(c, k, known, basket)};
    }

    /** Cup and basketball games no fixture feed lists, from Sport24 (null when the guide is empty). */
    List<Match> discover(List<Match> known, Teams.Club k, boolean basket) {
        return s24.isEmpty() ? null : Guide.discover(known, s24, k, basket);
    }

    @SuppressWarnings("unchecked")
    public void apply(List<Match> all, Teams.Club k, Object listings) {
        Object[] l = (Object[]) listings;
        Guide.apply(all, k, (List<Guide.Airing>) l[0], s24, (List<Guide.Airing>) l[1]);
    }

    List<Guide.Airing> sport24() { return s24; }
}
