package me.blockcraft.lobby.cookies;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Cards;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.SpotEntities;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class CookieBlock implements Listener, AutoCloseable {

    public static final String SPOT = "cookie-clicker";

    private final Locations<String> spots;
    private final CookieClicker cookies;
    private final Messages messages;
    private final SpotEntities.Group<String> stand;
    private Block counter;

    public CookieBlock(Locations<String> spots, CookieClicker cookies, Messages messages, SpotEntities entities) {
        this.spots = spots;
        this.cookies = cookies;
        this.messages = messages;
        stand = entities.group((player, spot) -> cookies.open(player), (player, spot) -> cookies.hit(player));
        refresh();
    }

    public void refresh() {
        close();
        spots.get(SPOT).ifPresent(spot -> {
            counter = spot.getBlock();
            var top = spot.toBlockLocation().add(0.5, 0, 0.5);
            top.setY(counter.getBoundingBox().getMaxY());
            var display = top.getWorld().spawn(top.clone().add(0, 0.5, 0), ItemDisplay.class, cookie -> {
                cookie.setItemStack(ItemStack.of(Material.COOKIE));
                cookie.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                cookie.setRotation(spot.getYaw(), 0);
                cookie.setViewRange(0.5f);
                cookie.setPersistent(false);
            });
            var box = top.getWorld().spawn(top, Interaction.class, target -> {
                target.setInteractionWidth(1);
                target.setInteractionHeight(1);
                target.setResponsive(true);
                target.setPersistent(false);
            });
            var label = Cards.line(top.clone().add(0, 1.35, 0),
                    Component.join(JoinConfiguration.newlines(), messages.getLines("cookies.block-label")));
            label.setViewRange(0.15f);
            label.setPersistent(false);
            stand.add(SPOT, spot, display, box, label);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockClick(PlayerInteractEvent event) {
        if (counter == null || !counter.equals(event.getClickedBlock())
                || event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            cookies.hit(event.getPlayer());
        } else {
            cookies.open(event.getPlayer());
        }
    }

    @Override
    public void close() {
        stand.close();
        counter = null;
    }
}
