package me.blockcraft.sternenfront.game;

/**
 * A command post's letter on a tile, which the top line and the minimap draw from the same sheet: filled, or hollow every
 * other second while the post is contested, so its badge blinks.
 */
record PostBadge(int post, boolean hollow) {

    static PostBadge of(Posts posts, int post, long tick) {
        return new PostBadge(post, posts.contested(post) && tick / SternenfrontGame.TICKS_PER_SECOND % 2 == 0);
    }

    /** This badge in a font whose badges start at {@code first}, each post's filled one before its hollow one. */
    char glyph(char first) {
        return (char) (first + post * 2 + (hollow ? 1 : 0));
    }
}
