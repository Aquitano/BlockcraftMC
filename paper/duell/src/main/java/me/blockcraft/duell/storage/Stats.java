package me.blockcraft.duell.storage;

public record Stats(int kills, int deaths, int wins, int losses) {

    public static final Stats EMPTY = new Stats(0, 0, 0, 0);
}
