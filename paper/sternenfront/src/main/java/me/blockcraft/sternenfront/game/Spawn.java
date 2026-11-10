package me.blockcraft.sternenfront.game;

/** Where a dead player deploys: their faction's headquarters, a command post it holds, or the trailing faction's landing. */
public sealed interface Spawn {

    record Headquarters() implements Spawn { }

    /** {@code index} into the map's posts, A first. */
    record Post(int index) implements Spawn { }

    record Dropship() implements Spawn { }
}
