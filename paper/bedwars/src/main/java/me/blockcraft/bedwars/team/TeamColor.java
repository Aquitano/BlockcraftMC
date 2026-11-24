package me.blockcraft.bedwars.team;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The teams a map can seat, each with its name colour, its dye and the glass and bed blocks it builds with. */
public enum TeamColor {
    RED(NamedTextColor.RED, DyeColor.RED, Material.RED_STAINED_GLASS, Material.RED_BED),
    YELLOW(NamedTextColor.YELLOW, DyeColor.YELLOW, Material.YELLOW_STAINED_GLASS, Material.YELLOW_BED),
    PURPLE(NamedTextColor.DARK_PURPLE, DyeColor.PURPLE, Material.PURPLE_STAINED_GLASS, Material.PURPLE_BED),
    GREEN(NamedTextColor.GREEN, DyeColor.LIME, Material.LIME_STAINED_GLASS, Material.LIME_BED),
    WHITE(NamedTextColor.WHITE, DyeColor.WHITE, Material.WHITE_STAINED_GLASS, Material.WHITE_BED),
    LIGHT_BLUE(NamedTextColor.BLUE, DyeColor.LIGHT_BLUE, Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_BED),
    ORANGE(NamedTextColor.GOLD, DyeColor.ORANGE, Material.ORANGE_STAINED_GLASS, Material.ORANGE_BED),
    BLACK(NamedTextColor.BLACK, DyeColor.BLACK, Material.BLACK_STAINED_GLASS, Material.BLACK_BED),
    MAGENTA(NamedTextColor.LIGHT_PURPLE, DyeColor.MAGENTA, Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_BED),
    PINK(NamedTextColor.LIGHT_PURPLE, DyeColor.PINK, Material.PINK_STAINED_GLASS, Material.PINK_BED),
    GRAY(NamedTextColor.DARK_GRAY, DyeColor.GRAY, Material.GRAY_STAINED_GLASS, Material.GRAY_BED),
    SILVER(NamedTextColor.GRAY, DyeColor.LIGHT_GRAY, Material.LIGHT_GRAY_STAINED_GLASS, Material.LIGHT_GRAY_BED),
    CYAN(NamedTextColor.AQUA, DyeColor.CYAN, Material.CYAN_STAINED_GLASS, Material.CYAN_BED),
    BLUE(NamedTextColor.DARK_BLUE, DyeColor.BLUE, Material.BLUE_STAINED_GLASS, Material.BLUE_BED),
    BROWN(NamedTextColor.DARK_RED, DyeColor.BROWN, Material.BROWN_STAINED_GLASS, Material.BROWN_BED),
    LIME(NamedTextColor.DARK_GREEN, DyeColor.GREEN, Material.GREEN_STAINED_GLASS, Material.GREEN_BED);

    private final NamedTextColor text;
    private final DyeColor dye;
    private final Material glass;
    private final Material bed;

    TeamColor(NamedTextColor text, DyeColor dye, Material glass, Material bed) {
        this.text = text;
        this.dye = dye;
        this.glass = glass;
        this.bed = bed;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public NamedTextColor text() {
        return text;
    }

    public DyeColor dye() {
        return dye;
    }

    public Material glass() {
        return glass;
    }

    public Material bed() {
        return bed;
    }

    /** "Rot", "Grün", ..., in the team's colour. */
    public Component displayName(Messages messages) {
        return messages.get("teams." + key()).colorIfAbsent(text);
    }

    public static Optional<TeamColor> byKey(String key) {
        return Arrays.stream(values()).filter(color -> color.key().equalsIgnoreCase(key)).findFirst();
    }
}
