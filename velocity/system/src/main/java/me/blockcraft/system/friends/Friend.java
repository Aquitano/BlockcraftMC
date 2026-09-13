package me.blockcraft.system.friends;

import java.util.UUID;

/** The other side of a {@code bc_friends} row: an accepted friend or the sender of an open request. */
public record Friend(UUID uuid, String name) {
}
