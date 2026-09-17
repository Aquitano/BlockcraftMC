package me.blockcraft.lobby.npc;

import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Floating text as one {@link TextDisplay} on a dark card, with the game's item hovering above it where the name
 * is a {@link MenuIcon}. A placed hologram is tagged with its name, so placing it again replaces the old one and
 * every start rewrites its lines from messages.yml.
 */
public final class Holograms {

    private static final NamespacedKey NAME = new NamespacedKey("blockcraft", "hologram");
    /** The pack's tooltip card, so the holograms match the menus. */
    private static final Color CARD = Color.fromARGB(0xC8, 0x14, 0x17, 0x20);
    private static final Display.Brightness FULL = new Display.Brightness(15, 15);
    private static final double TEXT_OFFSET = 0.9;
    private static final double ICON_OFFSET = 2.1;

    private final Messages messages;

    public Holograms(Messages messages) {
        this.messages = messages;
    }

    public void place(String name, Location base) {
        remove(name, base.getWorld());
        var text = line(base.clone().add(0, TEXT_OFFSET, 0), lines(name));
        text.getPersistentDataContainer().set(NAME, PersistentDataType.STRING, name);
        MenuIcon.byKey(name).ifPresent(icon -> {
            var item = base.getWorld().spawn(base.clone().add(0, ICON_OFFSET, 0), ItemDisplay.class, display -> {
                display.setItemStack(icon.item().build());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(FULL);
            });
            item.getPersistentDataContainer().set(NAME, PersistentDataType.STRING, name);
        });
    }

    /** Messages change between deploys, the holograms in the world do not. */
    public void refresh(World world) {
        for (var display : world.getEntitiesByClass(TextDisplay.class)) {
            nameOf(display).ifPresent(name -> display.text(lines(name)));
        }
    }

    /** One line of floating text; the caller decides whether it survives a restart. */
    public static TextDisplay line(Location location, Component text) {
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(CARD);
            display.setShadowed(true);
            display.setBrightness(FULL);
        });
    }

    private Component lines(String name) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines("holo." + name));
    }

    private static void remove(String name, World world) {
        for (var display : world.getEntitiesByClass(Display.class)) {
            if (nameOf(display).filter(name::equals).isPresent()) {
                display.remove();
            }
        }
    }

    private static Optional<String> nameOf(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(NAME, PersistentDataType.STRING));
    }
}
