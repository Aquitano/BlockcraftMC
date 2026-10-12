package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.cosmetic.PetType;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import me.blockcraft.lobby.npc.Holograms;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LobbyQuirks implements Listener, AutoCloseable {

    private record Egg(Item item, UUID owner, boolean golden, long expires) { }

    private final Plugin plugin;
    private final ActivitiesConfig config;
    private final Messages messages;
    private final Pets pets;
    private final Secrets secrets;
    private final Map<UUID, TextDisplay> sleeping = new HashMap<>();
    private final Map<UUID, Long> nextEgg = new HashMap<>();
    private final Map<UUID, Egg> eggs = new HashMap<>();

    public LobbyQuirks(Plugin plugin, ActivitiesConfig config, Messages messages, Pets pets, Secrets secrets) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.pets = pets;
        this.secrets = secrets;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.nanoTime();
        eggs.values().removeIf(egg -> {
            if (now < egg.expires() && egg.item().isValid()) {
                return false;
            }
            egg.item().remove();
            return true;
        });
        var api = BlockcraftApi.get();
        for (var player : Bukkit.getOnlinePlayers()) {
            var id = player.getUniqueId();
            if (api.isAfk(player) && api.afk().awayFor(player).compareTo(config.sleepAfter()) >= 0) {
                var display = sleeping.computeIfAbsent(id, ignored -> {
                    var text = Holograms.line(player.getLocation().add(0, 2.5, 0), messages.get("quirks.sleep"));
                    text.setPersistent(false);
                    text.setVisibleByDefault(false);
                    text.setViewRange(0.5f);
                    return text;
                });
                display.teleport(player.getLocation().add(0, 2.5 + Math.sin(now / 1_000_000_000.0) * 0.15, 0));
                for (var viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.canSee(player) && viewer.getWorld().equals(player.getWorld())
                            && api.settings().enabled(viewer, PlayerSetting.EFFECTS)) {
                        viewer.showEntity(plugin, display);
                    } else {
                        viewer.hideEntity(plugin, display);
                    }
                }
            } else {
                var display = sleeping.remove(id);
                if (display != null) {
                    display.remove();
                }
            }
            if (pets.of(player).filter(PetType.CHICKEN::equals).isEmpty()) {
                nextEgg.remove(id);
                continue;
            }
            long next = nextEgg.computeIfAbsent(id, ignored -> now + config.chickenInterval().toNanos());
            if (now >= next) {
                nextEgg.put(id, now + config.chickenInterval().toNanos());
                lay(player, now);
            }
        }
    }

    private void lay(Player player, long now) {
        boolean golden = ThreadLocalRandom.current().nextInt(config.goldenEggOdds()) == 0;
        var location = pets.location(player).orElseGet(player::getLocation);
        var item = location.getWorld().dropItem(location, ItemStack.of(golden ? Material.GOLD_NUGGET : Material.EGG), egg -> {
            egg.setOwner(player.getUniqueId());
            egg.setCanMobPickup(false);
            egg.setPersistent(false);
            egg.setPickupDelay(20);
            egg.customName(messages.get(golden ? "quirks.golden-egg" : "quirks.egg"));
            egg.setCustomNameVisible(true);
        });
        eggs.put(item.getUniqueId(), new Egg(item, player.getUniqueId(), golden, now + config.eggLifetime().toNanos()));
        Sounds.EASTER_EGG.play(player);
        if (golden) {
            player.sendMessage(messages.get("quirks.golden-laid"));
        }
    }

    public boolean owns(Entity entity) {
        return eggs.containsKey(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        var egg = eggs.get(event.getItem().getUniqueId());
        if (egg == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player && player.getUniqueId().equals(egg.owner())) {
            eggs.remove(egg.item().getUniqueId());
            egg.item().remove();
            if (egg.golden()) {
                secrets.find(player, Secret.GOLDEN_EGG);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        nextEgg.remove(id);
        var display = sleeping.remove(id);
        if (display != null) {
            display.remove();
        }
        eggs.values().removeIf(egg -> {
            if (!egg.owner().equals(id)) {
                return false;
            }
            egg.item().remove();
            return true;
        });
    }

    @Override
    public void close() {
        sleeping.values().forEach(TextDisplay::remove);
        eggs.values().forEach(egg -> egg.item().remove());
        sleeping.clear();
        eggs.clear();
        nextEgg.clear();
    }
}
