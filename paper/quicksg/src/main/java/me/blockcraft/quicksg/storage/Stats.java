package me.blockcraft.quicksg.storage;

public record Stats(int kills, int deaths, int wins, int losses, int points) {

    public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);
}
