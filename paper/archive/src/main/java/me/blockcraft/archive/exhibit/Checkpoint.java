package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.Point;

/** A spot of an exhibit worth seeing, with what a visitor should know about it. */
public record Checkpoint(String name, String description, Point at) {
}
