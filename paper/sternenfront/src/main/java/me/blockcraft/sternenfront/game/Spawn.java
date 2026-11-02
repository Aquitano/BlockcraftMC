package me.blockcraft.sternenfront.game;

/** Where a dead player deploys: their faction's headquarters, or a command post it holds. */
public sealed interface Spawn {

    record Headquarters() implements Spawn { }

    /** {@code index} into the map's posts, A first. */
    record Post(int index) implements Spawn { }
}
