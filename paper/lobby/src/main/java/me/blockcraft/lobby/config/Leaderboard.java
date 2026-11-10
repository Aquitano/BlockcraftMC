package me.blockcraft.lobby.config;

import me.blockcraft.api.game.Stat;

/** One leaderboard hologram: the game whose name and icon it wears, and the stats table it ranks. */
public record Leaderboard(String game, String table, Stat stat) {
}
