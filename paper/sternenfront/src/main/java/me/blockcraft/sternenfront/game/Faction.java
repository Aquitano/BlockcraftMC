package me.blockcraft.sternenfront.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

import java.util.Locale;

/**
 * The two sides of a round: the Freie Kolonien, orange, and the Hegemonie, light blue. {@code armour} dyes their
 * leather, {@code bolt} colours their shots, and {@code blocks} is the colour a command post's coloured blocks take
 * when the faction holds it. Only Bukkit types here, so the map builder can use it without BlockcraftAPI.
 */
public enum Faction {
    KOLONIEN(NamedTextColor.GOLD, Color.fromRGB(0xC8732A), Color.fromRGB(0xFF6A1F), DyeColor.ORANGE),
    HEGEMONIE(NamedTextColor.AQUA, Color.fromRGB(0xE6ECF0), Color.fromRGB(0x45D6FF), DyeColor.LIGHT_BLUE);

    /** What a command post's coloured blocks show while nobody holds it. */
    public static final DyeColor NEUTRAL = DyeColor.WHITE;

    private final NamedTextColor text;
    private final Color armour;
    private final Color bolt;
    private final DyeColor blocks;

    Faction(NamedTextColor text, Color armour, Color bolt, DyeColor blocks) {
        this.text = text;
        this.armour = armour;
        this.bolt = bolt;
        this.blocks = blocks;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public NamedTextColor text() {
        return text;
    }

    public Color armour() {
        return armour;
    }

    public Color bolt() {
        return bolt;
    }

    public DyeColor blocks() {
        return blocks;
    }

    public Faction enemy() {
        return this == KOLONIEN ? HEGEMONIE : KOLONIEN;
    }
}
