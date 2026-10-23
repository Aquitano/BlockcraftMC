package me.blockcraft.quicksg.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.LootChest;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ItemListener implements Listener {

    private static final double HEART = 2.0;

    private final Game game;
    private final Messages messages;
    private final LootChests lootChests;
    private final CompassTracker compass;

    public ItemListener(Game game, Messages messages, LootChests lootChests, CompassTracker compass) {
        this.game = game;
        this.messages = messages;
        this.lootChests = lootChests;
        this.compass = compass;
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
                if (game.isIngame() && game.isAlive(player)) {
                    compass.track(player, game.alivePlayers());
                }
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

    private static void consumeHeldItem(Player player) {
        var inventory = player.getInventory();
        inventory.setItemInMainHand(inventory.getItemInMainHand().subtract());
    }
}
