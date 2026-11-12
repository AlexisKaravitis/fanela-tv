package app.fanela.tv;

import android.content.Context;
import android.graphics.Canvas;

/**
 * An event card style (F1-UFC-ARCHITECTURE §6.1): the event sibling of a CardStyle, drawing an EventCardModel on the
 * parent style's grid at 960x540. Stream E owns this file.
 */
interface EventStyle {
    /** The same ids as Appearance.STYLES. */
    String id();

    void draw(Context c, Canvas cv, EventCardModel m, ClubTokens t, EventArt art);

    /**
     * Whether this style can draw the card well; otherwise Kit stands in. Every event style draws every card today
     * (SPEC-F1-UFC §3.6: Signature needs no images); Clash would say false for F1 if the owner chooses Kit there.
     */
    boolean suits(EventCardModel m, EventArt art);
}
