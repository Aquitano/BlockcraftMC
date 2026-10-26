package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ThrowFight;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.item.LobbyItem;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The season's throwing fight as a lobby toy: while one of its occasions lasts, everyone has the season's throwable
 * in the pond rod's slot and pushes whoever they hit. Hidden, vanished and racing players are left alone, and a
 * player's hits are counted until they leave.
 */
public final class LobbyThrows implements Listener, AutoCloseable {

    private static final long CHECK_TICKS = 20L;

    private final Messages messages;
    private final NetworkCalendar calendar;
    private final ThrowFight.Settings config;
    private final ThrowFight fight;
    private final Map<UUID, Integer> hits = new HashMap<>();
    private final BukkitTask check;
    private Optional<ThrowFight.Ammo> ammo = Optional.empty();

    public LobbyThrows(Plugin plugin, Messages messages, NetworkCalendar calendar, ThrowFight.Settings config,
                       Predicate<Player> racing) {
        this.messages = messages;
        this.calendar = calendar;
        this.config = config;
        var api = BlockcraftApi.get();
        this.fight = new ThrowFight((shooter, victim) -> ammo.isPresent() && shooter.getWorld().equals(victim.getWorld())
                && shooter.canSee(victim) && !api.isVanished(victim) && !racing.test(victim), this::hit,
                config.knockback(), config.cooldownTicks());
        Bukkit.getPluginManager().registerEvents(fight, plugin);
        ammo = current();
        this.check = Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    /** The throwable that belongs in the slot right now, if the season has one. */
    public Optional<ItemStack> item() {
        return ammo.map(season -> LobbyItem.THROW.tag(ThrowFight.item(season, messages.get("throw." + season.key()))));
    }

    private Optional<ThrowFight.Ammo> current() {
        return config.ammo(calendar);
    }

    private void check() {
        var now = current();
        if (now.equals(ammo)) {
            return;
        }
        ammo = now;
        for (var player : Bukkit.getOnlinePlayers()) {
            var slot = player.getInventory().getItem(LobbyItem.THROW.slot());
            boolean free = slot == null || slot.isEmpty() || LobbyItem.of(slot).filter(LobbyItem.THROW::equals).isPresent();
            if (free) {
                player.getInventory().setItem(LobbyItem.THROW.slot(), item().orElse(null));
            }
        }
    }

    private void hit(Player shooter, Player victim) {
        int count = hits.merge(shooter.getUniqueId(), 1, Integer::sum);
        var notifications = BlockcraftApi.get().notifications();
        notifications.feedback(shooter, messages.get("throw.hit", Placeholder.component("player", victim.displayName()),
                Formatter.number("hits", count)));
        notifications.feedback(victim, messages.get("throw.hit-by", Placeholder.component("player", shooter.displayName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hits.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        check.cancel();
    }
}
