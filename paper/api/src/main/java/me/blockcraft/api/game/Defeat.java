package me.blockcraft.api.game;

/**
 * What a kill cost the victim, which the title they see names: the round, one of their lives, a point, or nothing
 * but the way back from their respawn point.
 */
public enum Defeat {
    OUT,
    LIFE,
    POINT,
    RESPAWN
}
