package me.blockcraft.bedwars.lobby;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Catch in the waiting lobby: one player wears a head that changes colour and sets off a firework now and then,
 * and whoever hits them takes it over. It needs two players to be played.
 */
public final class CatchGame implements Listener {

    private static final int MIN_PLAYERS = 2;
    private static final long CYCLE_TICKS = 10L;
    private static final int FIREWORK_EVERY = 30;

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<List<Player>> players;
    private final List<Material> wool = List.copyOf(Tag.WOOL.getValues());
    private UUID holder;
    private BukkitTask task;
    private int cycle;

    public CatchGame(Plugin plugin, Messages messages, Supplier<List<Player>> players) {
        this.plugin = plugin;
        this.messages = messages;
        this.players = players;
    }

    public void open() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, 0L, CYCLE_TICKS);
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        holder().ifPresent(player -> player.getInventory().setHelmet(null));
        holder = null;
    }

    public void joined() {
        if (task != null && holder == null && players.get().size() >= MIN_PLAYERS) {
            passToSomeone(null);
        }
    }

    public void left(Player player) {
        var remaining = players.get().stream().filter(other -> !other.equals(player)).toList();
        if (remaining.size() < MIN_PLAYERS) {
            holder().ifPresent(current -> current.getInventory().setHelmet(null));
            holder = null;
        } else if (player.getUniqueId().equals(holder)) {
            holder = null;
            passToSomeone(player);
        }
    }

    @EventHandler
    public void onCatch(EntityDamageByEntityEvent event) {
        if (task != null && event.getDamager() instanceof Player catcher && event.getEntity() instanceof Player caught
                && caught.getUniqueId().equals(holder) && players.get().contains(catcher)) {
            pass(catcher);
        }
    }

    private void passToSomeone(Player except) {
        players.get().stream().filter(player -> !player.equals(except)).findAny().ifPresent(this::pass);
    }

    private void pass(Player next) {
        holder().ifPresent(previous -> {
            previous.getInventory().setHelmet(null);
            previous.sendMessage(messages.get("lobby.catch.caught", Placeholder.component("player", next.displayName())));
        });
        holder = next.getUniqueId();
        next.sendMessage(messages.get("lobby.catch.yours"));
        Sounds.NOTIFY.play(next);
        next.getInventory().setHelmet(head());
    }

    private void cycle() {
        holder().ifPresent(player -> {
            player.getInventory().setHelmet(head());
            if (++cycle % FIREWORK_EVERY == 0) {
                player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
                    var meta = firework.getFireworkMeta();
                    meta.setPower(1);
                    meta.addEffect(randomEffect());
                    firework.setFireworkMeta(meta);
                });
            }
        });
    }

    private ItemStack head() {
        var random = ThreadLocalRandom.current();
        return Items.of(wool.get(random.nextInt(wool.size()))).name(messages.get("lobby.catch.head")).build();
    }

    private static FireworkEffect randomEffect() {
        var random = ThreadLocalRandom.current();
        var types = FireworkEffect.Type.values();
        return FireworkEffect.builder()
                .withColor(Color.fromRGB(random.nextInt(0x1000000)))
                .with(types[random.nextInt(types.length)])
                .withTrail()
                .flicker(random.nextBoolean())
                .build();
    }

    private Optional<Player> holder() {
        return Optional.ofNullable(holder).map(Bukkit::getPlayer);
    }
}
