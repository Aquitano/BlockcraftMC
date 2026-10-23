package me.blockcraft.lobby.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.settings.PlayerSettingChangedEvent;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.Head;
import me.blockcraft.lobby.cosmetic.PetType;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Shirt;
import me.blockcraft.lobby.cosmetic.Trail;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.gadget.Gadget;
import me.blockcraft.lobby.gadget.Gadgets;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class CosmeticSettingsListener implements Listener {

    private final Messages messages;
    private final Trails trails;
    private final Pets pets;
    private final Gadgets gadgets;

    public CosmeticSettingsListener(Messages messages, Trails trails, Pets pets, Gadgets gadgets) {
        this.messages = messages;
        this.trails = trails;
        this.pets = pets;
        this.gadgets = gadgets;
    }

    @EventHandler
    public void onLoaded(PlayerSettingsLoadedEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onChanged(PlayerSettingChangedEvent event) {
        apply(event.getPlayer(), event.setting());
    }

    private void restore(Player player) {
        apply(player, PlayerSetting.HEAD);
        apply(player, PlayerSetting.SHIRT);
        apply(player, PlayerSetting.TRAIL);
        apply(player, PlayerSetting.PET);
        apply(player, PlayerSetting.GADGET);
    }

    private void apply(Player player, PlayerSetting setting) {
        var value = BlockcraftApi.get().settings().get(player, setting);
        switch (setting) {
            case HEAD -> player.getInventory().setHelmet(value.flatMap(Head::byKey)
                    .map(head -> Items.head(head.owner()).name(messages.get("heads.name." + head.key())).build())
                    .orElse(null));
            case SHIRT -> player.getInventory().setChestplate(value.flatMap(Shirt::byKey)
                    .map(shirt -> Items.of(Material.LEATHER_CHESTPLATE).color(shirt.color())
                            .name(messages.get("shirts.name." + shirt.key())).build())
                    .orElse(null));
            case TRAIL -> {
                var selected = value.flatMap(Trail::byKey).filter(trail -> !trail.premium() || player.hasPermission(LobbyPermissions.PREMIUM));
                if (!selected.equals(trails.of(player))) {
                    selected.ifPresentOrElse(trail -> trails.select(player, trail), () -> trails.clear(player));
                }
            }
            case PET -> {
                var selected = value.flatMap(PetType::byKey).filter(ignored -> player.hasPermission(LobbyPermissions.PREMIUM));
                if (!selected.equals(pets.of(player))) {
                    pets.release(player.getUniqueId());
                    selected.ifPresent(pet -> pets.spawn(player, pet));
                }
            }
            case GADGET -> value.flatMap(Gadget::byKey)
                    .ifPresentOrElse(gadget -> gadgets.select(player, gadget), () -> gadgets.clear(player));
            default -> {
            }
        }
    }
}
