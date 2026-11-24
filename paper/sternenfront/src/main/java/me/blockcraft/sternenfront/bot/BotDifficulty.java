package me.blockcraft.sternenfront.bot;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum BotDifficulty {
    EASY(Material.WOODEN_SWORD), NORMAL(Material.IRON_SWORD), VETERAN(Material.NETHERITE_SWORD);

    private final Material icon;

    BotDifficulty(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BotDifficulty> named(String name) {
        return Arrays.stream(values()).filter(value -> value.key().equalsIgnoreCase(name)).findFirst();
    }
}
