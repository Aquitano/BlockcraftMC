package me.blockcraft.quicksg.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.LootChest;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.config.QuickSGConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.MenuType;

import java.util.Comparator;

public final class ItemListener implements Listener {

    private static final double HEART = 2.0;

    private final Game game;
    private final Messages messages;
    private final LootChests lootChests;
    private final QuickSGConfig.Tracker tracker;

    public ItemListener(Game game, Messages messages, LootChests lootChests, QuickSGConfig.Tracker tracker) {
        this.game = game;
        this.messages = messages;
        this.lootChests = lootChests;
        this.tracker = tracker;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.CHEST
                && !game.isLobby() && !game.isSpectator(player)) {
            event.setCancelled(true);
            Sounds.MENU_OPEN.play(player);
            player.openInventory(lootChests.open(block.getLocation()));
            return;
        }
        var item = event.getItem();
        if (item == null) {
            return;
        }
        switch (item.getType()) {
            case ENCHANTING_TABLE -> {
                if (game.isIngame() && game.isAlive(player)) {
                    player.openInventory(MenuType.ENCHANTMENT.builder().location(player.getLocation()).checkReachable(false).build(player));
                }
            }
            case CRAFTING_TABLE -> {
                if (game.isIngame() && game.isAlive(player)) {
                    player.openInventory(MenuType.CRAFTING.builder().location(player.getLocation()).checkReachable(false).build(player));
                }
            }
            case CAKE -> {
                if (game.isIngame() && game.isAlive(player)) {
                    event.setCancelled(true);
                    consumeHeldItem(player);
                    player.setFoodLevel(20);
                    Sounds.HEAL.play(player);
                    BlockcraftApi.get().notifications().feedback(player, messages.pick("consume.food"));
                }
            }
            case MUSHROOM_STEW -> {
                if (game.isIngame() && game.isAlive(player)) {
                    event.setCancelled(true);
                    consumeHeldItem(player);
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    maxHealth.setBaseValue(maxHealth.getBaseValue() + HEART);
                    player.setHealth(player.getHealth() + HEART);
                    Sounds.HEAL.play(player);
                    BlockcraftApi.get().notifications().feedback(player, messages.get("consume.heart"));
                }
            }
            case ENCHANTED_BOOK -> {
                if (game.isIngame() && game.isAlive(player)) {
                    event.setCancelled(true);
                    consumeHeldItem(player);
                    player.setLevel(player.getLevel() + 1);
                    BlockcraftApi.get().notifications().rewardSound(player, Sounds.LEVEL_UP);
                    BlockcraftApi.get().notifications().feedback(player, messages.get("consume.level"));
                }
            }
            case COMPASS -> {
                event.setCancelled(true);
                track(player);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof LootChest && event.getPlayer() instanceof Player player) {
            Sounds.MENU_CLICK.play(player);
        }
    }

    private void track(Player player) {
        if (!game.isIngame() || !game.isAlive(player)) {
            return;
        }
        var compass = player.getInventory().getItemInMainHand();
        if (player.hasCooldown(compass)) {
            return;
        }
        player.setCooldown(compass, tracker.cooldownTicks());
        var origin = player.getLocation();
        var target = game.alivePlayers().stream()
                .filter(other -> !other.equals(player) && !other.isDead() && player.canSee(other))
                .filter(other -> other.getWorld().equals(origin.getWorld()))
                .filter(other -> {
                    var location = other.getLocation();
                    return Math.abs(location.getX() - origin.getX()) <= tracker.rangeHorizontal()
                            && Math.abs(location.getZ() - origin.getZ()) <= tracker.rangeHorizontal()
                            && Math.abs(location.getY() - origin.getY()) <= tracker.rangeVertical();
                })
                .min(Comparator.comparingDouble(other -> origin.distanceSquared(other.getLocation())));
        target.ifPresentOrElse(near -> {
            player.setCompassTarget(near.getLocation());
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.found",
                    Placeholder.unparsed("player", near.getName()),
                    Formatter.number("distance", (int) origin.distance(near.getLocation()))));
            Sounds.NOTIFY.play(player);
        }, () -> {
            player.setCompassTarget(origin);
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.empty"));
            Sounds.MENU_DENY.play(player);
        });
    }

    private static void consumeHeldItem(Player player) {
        var inventory = player.getInventory();
        inventory.setItemInMainHand(inventory.getItemInMainHand().subtract());
    }
}
