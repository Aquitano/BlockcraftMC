package me.blockcraft.lobby.npc;

import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Floating text as one {@link TextDisplay} on a dark card, with the game's item hovering above it where the name
 * is a {@link MenuIcon} or a {@link Leaderboards} board. A placed hologram is tagged with its name, so placing it
 * again replaces the old one and every refresh rewrites its lines from messages.yml or from the database.
 */
public final class Holograms {

    private static final NamespacedKey NAME = new NamespacedKey("blockcraft", "hologram");
    /** The pack's tooltip card, so the holograms match the menus. */
    private static final Color CARD = Color.fromARGB(0xC8, 0x14, 0x17, 0x20);
    private static final Display.Brightness FULL = new Display.Brightness(15, 15);
    private static final double TEXT_OFFSET = 0.9;
    private static final double LINE_HEIGHT = 0.25;
    private static final double ICON_GAP = 0.8;

    private final Plugin plugin;
    private final Messages messages;
    private final Leaderboards leaderboards;
    private final List<Runnable> afterRefresh = new ArrayList<>();
    private BukkitTask task;

    public Holograms(Plugin plugin, Messages messages, Leaderboards leaderboards) {
        this.plugin = plugin;
        this.messages = messages;
        this.leaderboards = leaderboards;
    }

    /** The places are read off the main thread, the displays are rewritten on it. */
    public void start(long intervalTicks) {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            leaderboards.read();
            Tasks.sync(plugin, () -> {
                Bukkit.getWorlds().forEach(this::refresh);
                afterRefresh.forEach(Runnable::run);
            });
        }, 0L, intervalTicks);
    }

    /** Runs on the main thread after every refresh of the boards, for what shows their freshly read places. */
    public void afterRefresh(Runnable action) {
        afterRefresh.add(action);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public void place(String name, Location base) {
        remove(name, base.getWorld());
        var text = line(base.clone().add(0, TEXT_OFFSET, 0), text(name));
        text.getPersistentDataContainer().set(NAME, PersistentDataType.STRING, name);
        icon(name).ifPresent(icon -> {
            var item = base.getWorld().spawn(base.clone().add(0, iconOffset(name), 0), ItemDisplay.class, display -> {
                display.setItemStack(icon.item().build());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(FULL);
            });
            item.getPersistentDataContainer().set(NAME, PersistentDataType.STRING, name);
        });
    }

    /**
     * Messages and places change, the holograms in the world do not. The icons are rewritten too, so one saved with
     * the map before the pack could draw it on a display picks up the pack's model.
     */
    public void refresh(World world) {
        for (var display : world.getEntitiesByClass(TextDisplay.class)) {
            nameOf(display).ifPresent(name -> display.text(text(name)));
        }
        for (var display : world.getEntitiesByClass(ItemDisplay.class)) {
            nameOf(display).flatMap(this::icon).ifPresent(icon -> display.setItemStack(icon.item().build()));
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

    private Component text(String name) {
        return Component.join(JoinConfiguration.newlines(), lines(name));
    }

    private List<Component> lines(String name) {
        return leaderboards.lines(name).orElseGet(() -> messages.getLines("holo." + name));
    }

    private Optional<MenuIcon> icon(String name) {
        return MenuIcon.byKey(leaderboards.game(name).orElse(name));
    }

    /** The text grows around its anchor, so the icon rises with the lines beneath it. */
    private double iconOffset(String name) {
        int rows = leaderboards.rows(name).orElseGet(() -> lines(name).size());
        return TEXT_OFFSET + rows * LINE_HEIGHT / 2 + ICON_GAP;
    }

    private static void remove(String name, World world) {
        for (var display : world.getEntitiesByClass(Display.class)) {
            if (nameOf(display).filter(name::equals).isPresent()) {
                display.remove();
            }
        }
    }

    /** The name a placed hologram's displays are tagged with. */
    public static Optional<String> nameOf(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(NAME, PersistentDataType.STRING));
    }
}
