package me.blockcraft.api.game;

import java.util.Optional;
import java.util.UUID;

/** A place on a stats table's board: whose it is, when the row's UUID reads as one, under the name it was won with. */
public record Leader(Optional<UUID> player, String name, int value) {
}
