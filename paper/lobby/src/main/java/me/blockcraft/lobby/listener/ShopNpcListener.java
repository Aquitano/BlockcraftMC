package me.blockcraft.lobby.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.menu.Menus;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.stream.IntStream;

/** The shop villager has a word for whoever opens the shop, and for whoever hits it. */
public final class ShopNpcListener implements Listener {

    private final Messages messages;
    private final Menus menus;
    private final LobbyHud hud;
    private final int cheapestPrice;

    public ShopNpcListener(Messages messages, Menus menus, LobbyHud hud, LobbyConfig config) {
        this.messages = messages;
        this.menus = menus;
        this.hud = hud;
        cheapestPrice = IntStream.of(config.headPrice(), config.shirtPrice(), config.premiumPrice()).min().orElseThrow();
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                Player player = event.getPlayer();
                Sounds.SHOP_TALK.play(player);
                BlockcraftApi.get().notifications().feedback(player, messages.pick(windowShopping(player) ? "npc.window-shopping" : "npc.talk"));
                menus.openShop(player);
            }
        }
    }

    @EventHandler
    public void onVillagerHit(PrePlayerAttackEntityEvent event) {
        if (event.getAttacked() instanceof Villager villager) {
            event.setCancelled(true);
            villager.playHurtAnimation(0);
            Sounds.SHOP_HURT.play(event.getPlayer());
            BlockcraftApi.get().notifications().feedback(event.getPlayer(), messages.pick("npc.hurt"));
        }
    }

    /** Judged by the balance the sidebar shows, so a click never waits for the database. */
    private boolean windowShopping(Player player) {
        return hud.coins(player).stream().anyMatch(coins -> coins < cheapestPrice);
    }
}
