package me.blockcraft.bedwars.item;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.shop.Dealers;
import me.blockcraft.bedwars.team.Team;
import me.blockcraft.bedwars.team.Teams;
import me.blockcraft.common.message.Messages;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Right-clicks with the shop's special items, for players in the round; everyone else's click does nothing. */
public final class SpecialItems implements Listener {

    private final Messages messages;
    private final Teams teams;
    private final Predicate<Player> playing;
    private final Teleporter teleporter;
    private final Parachute parachute;
    private final RescuePlatform platform;
    private final MobileShop mobileShop;

    public SpecialItems(Plugin plugin, Messages messages, BedWarsConfig config, Dealers dealers, Teams teams,
                        Predicate<Player> playing, Predicate<Block> replaceable, Consumer<Block> placed) {
        this.messages = messages;
        this.teams = teams;
        this.playing = playing;
        this.teleporter = new Teleporter(plugin, messages, config.teleporterSeconds(), config.teleporterHeight(),
                player -> teams.of(player).flatMap(Team::spawn));
        this.parachute = new Parachute(plugin, messages, config.parachuteSeconds());
        this.platform = new RescuePlatform(config.rescuePlatformRadius(), replaceable, placed);
        this.mobileShop = new MobileShop(plugin, messages, dealers, config.mobileShopSeconds());
    }

    /** The round is over: the mobile dealers go, and no teleporter or parachute carries anyone on. */
    public void clear() {
        mobileShop.clear();
        teleporter.clear();
        parachute.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        HotbarItem.of(event.getItem(), SpecialItem.class).ifPresent(item -> {
            event.setCancelled(true);
            var player = event.getPlayer();
            var team = teams.of(player);
            if (!playing.test(player) || team.isEmpty()) {
                return;
            }
            boolean used = switch (item) {
                case TELEPORTER -> {
                    teleporter.use(player);
                    yield false;
                }
                case PARACHUTE -> parachute.use(player);
                case RESCUE_PLATFORM -> {
                    boolean spread = platform.spread(player, team.get().color());
                    if (!spread) {
                        player.sendMessage(messages.get("items.rescue-platform.grounded"));
                    }
                    yield spread;
                }
                case MOBILE_SHOP -> mobileShop.use(player, event.getClickedBlock(), event.getBlockFace());
            };
            if (used) {
                var hand = player.getInventory().getItemInMainHand();
                hand.subtract();
                player.getInventory().setItemInMainHand(hand.isEmpty() ? null : hand);
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        teleporter.moved(event.getPlayer(), event.getFrom(), event.getTo());
        parachute.moved(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        teleporter.forget(event.getPlayer());
        parachute.close(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleporter.forget(event.getPlayer());
        parachute.close(event.getPlayer());
    }

    @EventHandler
    public void onParachuteShot(EntityDeathEvent event) {
        if (parachute.shotDown(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
}
