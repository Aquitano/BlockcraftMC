package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import me.blockcraft.lobby.item.LobbyItem;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class PondFishing implements Listener {

    private final Locations<String> locations;
    private final ActivitiesConfig config;
    private final Messages messages;
    private final Secrets secrets;
    private final Supplier<Optional<ItemStack>> seasonal;

    public PondFishing(Locations<String> locations, ActivitiesConfig config, Messages messages, Secrets secrets,
                       Supplier<Optional<ItemStack>> seasonal) {
        this.seasonal = seasonal;
        this.locations = locations;
        this.config = config;
        this.messages = messages;
        this.secrets = secrets;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        if (BlockcraftApi.get().buildMode().isEnabled(player)) {
            return;
        }
        var current = player.getInventory().getItem(LobbyItem.POND_ROD.slot());
        boolean hasRod = LobbyItem.of(current).filter(LobbyItem.POND_ROD::equals).isPresent();
        boolean free = current == null || current.isEmpty() || LobbyItem.of(current).filter(LobbyItem.THROW::equals).isPresent();
        if (nearPond(event.getTo()) && !hasRod && free) {
            var rod = Items.of(Material.FISHING_ROD).name(messages.get("fishing.rod"))
                    .lore(messages.get("fishing.rod-lore")).build();
            rod.editMeta(meta -> meta.setUnbreakable(true));
            player.getInventory().setItem(LobbyItem.POND_ROD.slot(), LobbyItem.POND_ROD.tag(rod));
            player.sendMessage(messages.get("fishing.arrived"));
        } else if (!nearPond(event.getTo()) && hasRod) {
            player.getInventory().setItem(LobbyItem.POND_ROD.slot(), seasonal.get().orElse(null));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        var player = event.getPlayer();
        if (LobbyItem.of(player.getInventory().getItemInMainHand()).filter(LobbyItem.POND_ROD::equals).isEmpty()) {
            return;
        }
        if (!nearPond(player.getLocation()) || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            event.setCancelled(true);
            event.getHook().remove();
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        event.setCancelled(true);
        if (event.getCaught() != null) {
            event.getCaught().remove();
        }
        event.getHook().remove();
        Sounds.COINS.play(player);
        if (ThreadLocalRandom.current().nextInt(config.goldenCarpOdds()) == 0) {
            secrets.find(player, Secret.GOLDEN_CARP);
        } else {
            player.sendMessage(messages.get("fishing.caught", Placeholder.component("catch", messages.pick("fishing.catches"))));
        }
    }

    private boolean nearPond(Location location) {
        return locations.get("pond").filter(pond -> pond.getWorld().equals(location.getWorld()))
                .filter(pond -> pond.distanceSquared(location) <= config.pondRadius() * config.pondRadius()).isPresent();
    }
}
