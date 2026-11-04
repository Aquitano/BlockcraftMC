package me.blockcraft.bedwars.resource;

import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The three currencies of the shop, each dropped by its own spawners and named in its own colour; the shop counts
 * them by material alone.
 */
public enum Resource {
    BRONZE(Material.BRICK, NamedTextColor.RED, Optional.empty()),
    IRON(Material.IRON_INGOT, NamedTextColor.GRAY, Optional.of(Color.SILVER)),
    GOLD(Material.GOLD_INGOT, NamedTextColor.GOLD, Optional.of(Color.YELLOW));

    private final Material material;
    private final NamedTextColor text;
    private final Optional<Color> sparkle;

    Resource(Material material, NamedTextColor text, Optional<Color> sparkle) {
        this.material = material;
        this.text = text;
        this.sparkle = sparkle;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Material material() {
        return material;
    }

    /** The colour of the spiral a drop of this resource appears in; bronze drops too often to have one. */
    public Optional<Color> sparkle() {
        return sparkle;
    }

    public Component displayName(Messages messages) {
        return messages.get("resources." + key() + ".name").colorIfAbsent(text);
    }

    /** "3 Eisen" in the resource's colour, as the shop writes a price. */
    public Component amount(Messages messages, int amount) {
        return messages.get("resources." + key() + ".amount", Formatter.number("amount", amount)).colorIfAbsent(text);
    }

    public ItemStack item(Messages messages) {
        return Items.of(material).name(displayName(messages)).build();
    }

    public static Optional<Resource> byKey(String key) {
        return Arrays.stream(values()).filter(resource -> resource.key().equalsIgnoreCase(key)).findFirst();
    }
}
