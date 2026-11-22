package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.DuelSetupEvent;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.lobby.item.LobbyItem;
import me.blockcraft.lobby.menu.DuelChallengeMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.List;

/**
 * In the Duell area, the box between the spots {@link #CORNERS}, everyone holds a challenge sword. Striking another
 * player there opens the rules for a challenge, and striking back the one who challenged you accepts it; the proxy
 * decides which of the two it was.
 */
public final class DuelZone implements Listener {

    public static final List<String> CORNERS = List.of("duel-zone-a", "duel-zone-b");

    public record Settings(int strikeCooldownTicks, List<String> modes) {
        public static Settings from(StrictConfig section) {
            return new Settings(section.integer("strike-cooldown-ticks"), section.texts("modes"));
        }
    }

    private final Plugin plugin;
    private final Locations<String> spots;
    private final Messages messages;
    private final Settings settings;

    public DuelZone(Plugin plugin, Locations<String> spots, Messages messages, Settings settings) {
        this.plugin = plugin;
        this.spots = spots;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        var player = event.getPlayer();
        if (BlockcraftApi.get().buildMode().isEnabled(player)) {
            return;
        }
        var current = player.getInventory().getItem(LobbyItem.DUEL_SWORD.slot());
        boolean armed = LobbyItem.of(current).filter(LobbyItem.DUEL_SWORD::equals).isPresent();
        boolean inside = inside(event.getTo());
        if (inside && !armed && (current == null || current.isEmpty())) {
            var sword = Items.of(Material.IRON_SWORD).name(messages.get("duel-zone.sword")).lore(messages.get("duel-zone.sword-lore")).build();
            sword.editMeta(meta -> meta.setUnbreakable(true));
            player.getInventory().setItem(LobbyItem.DUEL_SWORD.slot(), LobbyItem.DUEL_SWORD.tag(sword));
            BlockcraftApi.get().notifications().feedback(player, messages.get("duel-zone.arrived"));
        } else if (!inside && armed) {
            player.getInventory().setItem(LobbyItem.DUEL_SWORD.slot(), null);
        }
    }

    @EventHandler
    public void onStrike(PrePlayerAttackEntityEvent event) {
        var player = event.getPlayer();
        if (!(event.getAttacked() instanceof Player target)
                || LobbyItem.of(player.getInventory().getItemInMainHand()).filter(LobbyItem.DUEL_SWORD::equals).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (player.hasCooldown(Material.IRON_SWORD) || !inside(player.getLocation()) || !inside(target.getLocation())) {
            return;
        }
        player.setCooldown(Material.IRON_SWORD, settings.strikeCooldownTicks());
        player.playSound(player, Sound.ITEM_ARMOR_EQUIP_IRON, 1, 1.2f);
        ProxyMessaging.send(plugin, player, new ProxyMessage.DuelRequest(target.getUniqueId()));
    }

    @EventHandler
    public void onSetup(DuelSetupEvent event) {
        new DuelChallengeMenu(plugin, messages, event.setup(), settings.modes()).open(event.getPlayer());
    }

    private boolean inside(Location location) {
        var a = spots.get(CORNERS.getFirst());
        var b = spots.get(CORNERS.getLast());
        if (a.isEmpty() || b.isEmpty() || !a.get().getWorld().equals(location.getWorld())) {
            return false;
        }
        return BoundingBox.of(a.get().getBlock(), b.get().getBlock()).contains(location.toVector());
    }
}
