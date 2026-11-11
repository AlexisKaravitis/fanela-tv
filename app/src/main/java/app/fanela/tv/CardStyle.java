package app.fanela.tv;

import android.content.Context;
import android.graphics.Canvas;

/**
 * One look for the match cards. A style only decides how the card model is drawn on a 960x540 canvas: the words,
 * numbers, colours and rules come from CardModel and ClubTokens, so every style means the same thing.
 */
interface CardStyle {
    String id();

    /** Draws the card. tokens: the followed club's colours; art: crests and channel logos, loaded on demand. */
    void draw(Context c, Canvas cv, CardModel m, ClubTokens tokens, CardArt art);

    /** Whether this style can draw the club well (e.g. it needs a sharp crest); otherwise the Kit style stands in. */
    boolean suits(ClubTokens tokens);
}
