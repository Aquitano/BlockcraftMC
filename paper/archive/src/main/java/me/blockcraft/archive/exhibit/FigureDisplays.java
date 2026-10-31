package me.blockcraft.archive.exhibit;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The figures of the loaded exhibits. Like a note, a figure is never saved into its world, so it stands whenever its
 * chunk is loaded and goes with the chunk.
 */
public final class FigureDisplays implements Listener {

    private final Messages messages;
    private final Map<World, List<Figure>> figures = new HashMap<>();
    private final Map<World, Map<Figure, Mannequin>> standing = new HashMap<>();

    public FigureDisplays(Messages messages) {
        this.messages = messages;
    }

    public void load(World world, List<Figure> list) {
        figures.put(world, list);
        standing.put(world, new HashMap<>());
        list.forEach(figure -> stand(world, figure));
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        in(event.getWorld(), event.getChunk()).forEach(figure -> stand(event.getWorld(), figure));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        var placed = standing.get(event.getWorld());
        if (placed != null) {
            in(event.getWorld(), event.getChunk()).map(placed::remove).filter(Objects::nonNull).forEach(Entity::remove);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        figures.remove(event.getWorld());
        Optional.ofNullable(standing.remove(event.getWorld())).ifPresent(placed -> placed.values().forEach(Entity::remove));
    }

    private Stream<Figure> in(World world, Chunk chunk) {
        return figures.getOrDefault(world, List.of()).stream()
                .filter(figure -> (int) Math.floor(figure.at().x()) >> 4 == chunk.getX()
                        && (int) Math.floor(figure.at().z()) >> 4 == chunk.getZ());
    }

    private void stand(World world, Figure figure) {
        var placed = standing.get(world);
        var at = figure.at().in(world);
        if (placed == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        var previous = placed.get(figure);
        if (previous != null && previous.isValid()) {
            return;
        }
        var skin = ResolvableProfile.resolvableProfile()
                .addProperty(new ProfileProperty("textures", figure.skin(), figure.signature()))
                .build();
        placed.put(figure, world.spawn(at, Mannequin.class, entity -> {
            entity.setProfile(skin);
            entity.setDescription(null);
            entity.customName(messages.get("figure.name", Placeholder.unparsed("name", figure.name())));
            entity.setCustomNameVisible(true);
            entity.setImmovable(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
        }));
    }
}
