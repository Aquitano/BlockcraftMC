package me.blockcraft.lobby.cosmetic;

import org.bukkit.Color;

public enum Shirt {
    RED("red", Color.fromRGB(255, 0, 0)),
    GREEN("green", Color.fromRGB(16, 156, 0)),
    BLUE("blue", Color.fromRGB(19, 79, 218)),
    PURPLE("purple", Color.fromRGB(162, 0, 255)),
    ORANGE("orange", Color.fromRGB(255, 162, 0)),
    TURQUOISE("turquoise", Color.fromRGB(0, 100, 142));

    private final String key;
    private final Color color;

    Shirt(String key, Color color) {
        this.key = key;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public Color color() {
        return color;
    }
}
