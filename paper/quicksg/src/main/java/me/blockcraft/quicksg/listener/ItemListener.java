package me.blockcraft.quicksg.listener;

import me.blockcraft.api.game.LootChest;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.menu.KitMenus;
import me.blockcraft.quicksg.menu.StatsMenus;
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
import org.bukkit.plugin.Plugin;

public final class ItemListener implements Listener {

    private static final double TRACKER_RANGE_XZ = 100.0;
    private static final double TRACKER_RANGE_Y = 25.0;
    private static final double HEART = 2.0;

    private final Plugin plugin;
    private final Game game;
    private final Messages messages;
    private final LootChests lootChests;
    private final KitMenus kitMenus;
    private final StatsMenus statsMenus;

    public ItemListener(Plugin plugin, Game game, Messages messages, LootChests lootChests,
                        KitMenus kitMenus, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.lootChests = lootChests;
        this.kitMenus = kitMenus;
        this.statsMenus = statsMenus;
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
            case SLIME_BALL -> {
                event.setCancelled(true);
                ProxyMessaging.hub(plugin, player);
            }
            case DIAMOND -> {
                if (player.hasPermission("blockcraft.quicksg.start")) {
                    event.setCancelled(true);
                    player.performCommand("start");
                }
            }
            case PLAYER_HEAD -> {
                event.setCancelled(true);
                statsMenus.openPlayers(player);
            }
            case CHEST -> {
                if (game.isLobby()) {
                    event.setCancelled(true);
                    kitMenus.openSelector(player);
                }
            }
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
                }
            }
            case MUSHROOM_STEW -> {
                if (game.isIngame() && game.isAlive(player)) {
                    event.setCancelled(true);
                    consumeHeldItem(player);
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    maxHealth.setBaseValue(maxHealth.getBaseValue() + HEART);
                    player.setHealth(player.getHealth() + HEART);
                }
            }
            case ENCHANTED_BOOK -> {
                if (game.isIngame() && game.isAlive(player)) {
                    event.setCancelled(true);
                    consumeHeldItem(player);
                    player.setLevel(player.getLevel() + 1);
                }
            }
            case COMPASS -> track(player);
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
        for (var entity : player.getNearbyEntities(TRACKER_RANGE_XZ, TRACKER_RANGE_Y, TRACKER_RANGE_XZ)) {
            if (entity instanceof Player near && !game.isSpectator(near)) {
                player.setCompassTarget(near.getLocation());
                player.sendMessage(messages.get("tracker.found",
                        Placeholder.unparsed("player", near.getName()),
                        Formatter.number("distance", (int) player.getLocation().distance(near.getLocation()))));
            }
        }
    }

    private static void consumeHeldItem(Player player) {
        var inventory = player.getInventory();
        inventory.setItemInMainHand(inventory.getItemInMainHand().subtract());
    }
}
