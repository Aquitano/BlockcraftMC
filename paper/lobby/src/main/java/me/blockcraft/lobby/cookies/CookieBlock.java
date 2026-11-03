package me.blockcraft.lobby.cookies;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Cards;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CookieBlock implements Listener, AutoCloseable {

    public static final String SPOT = "cookie-clicker";

    private final Locations<String> spots;
    private final CookieClicker cookies;
    private final Messages messages;
    private final ChunkTickets.Owner held;
    private final List<Entity> spawned = new ArrayList<>();
    private Block counter;
    private Interaction box;

    public CookieBlock(Locations<String> spots, CookieClicker cookies, Messages messages, ChunkTickets chunks) {
        this.spots = spots;
        this.cookies = cookies;
        this.messages = messages;
        held = chunks.owner();
        refresh();
    }

    public void refresh() {
        close();
        spots.get(SPOT).ifPresent(spot -> {
            held.add(spot.getChunk());
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
            spawned.add(display);
            box = top.getWorld().spawn(top, Interaction.class, target -> {
                target.setInteractionWidth(1);
                target.setInteractionHeight(1);
                target.setResponsive(true);
                target.setPersistent(false);
            });
            spawned.add(box);
            var label = Cards.line(top.clone().add(0, 1.35, 0),
                    Component.join(JoinConfiguration.newlines(), messages.getLines("cookies.block-label")));
            label.setViewRange(0.15f);
            label.setPersistent(false);
            spawned.add(label);
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

    @EventHandler
    public void onCookieClick(PlayerInteractEntityEvent event) {
        if (box != null && box.equals(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                cookies.open(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onCookieHit(PrePlayerAttackEntityEvent event) {
        if (box != null && box.equals(event.getAttacked())) {
            event.setCancelled(true);
            cookies.hit(event.getPlayer());
        }
    }

    @Override
    public void close() {
        spawned.forEach(Entity::remove);
        spawned.clear();
        counter = null;
        box = null;
        held.clear();
    }
}
