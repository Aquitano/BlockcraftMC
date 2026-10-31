package me.blockcraft.duell.game;

import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

abstract class MapDuelGame implements DuelGame, Listener {
    protected final DuelGames.Context context;
    protected boolean active;
    protected boolean closed;

    MapDuelGame(DuelGames.Context context) {
        this.context = context;
    }

    protected List<Player> players() {
        return context.participants().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
    }

    protected boolean participant(Player player) {
        return context.participants().contains(player.getUniqueId());
    }

    protected Optional<Location> spawn(Player player) {
        int slot = context.participants().indexOf(player.getUniqueId());
        return slot < 0 ? Optional.empty() : Optional.ofNullable(context.spawns().get(slot));
    }

    protected void equip(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
        player.setVelocity(new Vector());
        context.combat().apply(player);
    }

    final void prepare() {
        clearEntities();
        for (var player : players()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            equip(player);
            spawn(player).ifPresent(location -> {
                player.teleport(location);
                player.setRespawnLocation(location, true);
            });
        }
    }

    @Override
    public void activate() {
        if (!closed) {
            active = true;
        }
    }

    @Override
    public void spectate(Player player) {
        context.spawns().stream().filter(java.util.Objects::nonNull).findFirst()
                .map(location -> location.clone().add(0, 3, 0)).ifPresent(player::teleport);
    }

    @Override
    public int progress(UUID participant) {
        return 0;
    }

    @Override
    public Optional<UUID> timeoutWinner() {
        var first = context.participants().getFirst();
        var second = context.participants().getLast();
        return progress(first) == progress(second) ? Optional.empty()
                : Optional.of(progress(first) > progress(second) ? first : second);
    }

    @EventHandler
    public final void onDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        if (closed || !participant(player)) {
            return;
        }
        if (active) {
            died(event);
        }
        Tasks.later(context.plugin(), 5L, () -> {
            if (!closed && player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    protected abstract void died(PlayerDeathEvent event);

    @Override
    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        spawn(player).ifPresent(event::setRespawnLocation);
        Tasks.sync(context.plugin(), () -> {
            if (!closed && participant(player) && player.isOnline() && !player.isDead()) {
                equip(player);
            }
        });
    }

    protected void complete(Optional<UUID> winner) {
        if (!closed && active) {
            active = false;
            context.events().completed(winner);
        }
    }

    protected void clearEntities() {
        context.spawns().stream().filter(java.util.Objects::nonNull).map(Location::getWorld).distinct().forEach(world -> {
            world.getEntitiesByClass(Projectile.class).forEach(org.bukkit.entity.Entity::remove);
            world.getEntitiesByClass(Item.class).forEach(org.bukkit.entity.Entity::remove);
        });
    }

    @Override
    public void close() {
        closed = true;
        active = false;
        HandlerList.unregisterAll(this);
        clearEntities();
    }
}
