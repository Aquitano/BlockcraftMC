package me.blockcraft.lobby.cosmetic;

import org.bukkit.Color;

import java.util.Arrays;
import java.util.Optional;

public enum Shirt {
    RED("red", Color.fromRGB(255, 0, 0)),
    GREEN("green", Color.fromRGB(16, 156, 0)),
    BLUE("blue", Color.fromRGB(19, 79, 218)),
    PURPLE("purple", Color.fromRGB(162, 0, 255)),
    ORANGE("orange", Color.fromRGB(255, 162, 0)),
    TURQUOISE("turquoise", Color.fromRGB(0, 100, 142));

    public static final String ITEM_PREFIX = "lobby.shirt.";

    private final String key;
    private final Color color;

    Shirt(String key, Color color) {
        this.key = key;
        this.color = color;
    }

    public static Optional<Shirt> byKey(String key) {
        return Arrays.stream(values()).filter(shirt -> shirt.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public String item() {
        return ITEM_PREFIX + key;
    }

    public Color color() {
        return color;
    }
}
