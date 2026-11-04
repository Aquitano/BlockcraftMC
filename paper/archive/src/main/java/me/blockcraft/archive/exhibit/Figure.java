package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.Point;

/**
 * Someone the old server had standing in the map, such as a shopkeeper whose plugin is gone: shown with its old name
 * and skin, but without what it once did. {@code skin} and {@code signature} are the signed textures property of the
 * old profile, so the skin needs no lookup and stays the same when the account behind it changes.
 */
public record Figure(String name, Point at, String skin, String signature) {
}
