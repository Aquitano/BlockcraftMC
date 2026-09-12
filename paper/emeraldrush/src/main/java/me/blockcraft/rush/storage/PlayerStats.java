package me.blockcraft.rush.storage;

public record PlayerStats(int kills, int deaths, int wins, int loses, int points) {

    public static final PlayerStats EMPTY = new PlayerStats(0, 0, 0, 0, 0);
}
