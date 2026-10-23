package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.dialog.SettingsDialog;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.CosmeticPreviews;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.friends.FriendsChannel;
import me.blockcraft.api.network.ServerGroups;
import me.blockcraft.lobby.rank.PremiumRank;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Everything the chest GUIs need, so they can open one another without re-wiring. */
public record Menus(
        Plugin plugin,
        Messages messages,
        LobbyConfig config,
        Locations<LobbyLocation> locations,
        Unlocks unlocks,
        PlayerSettings settings,
        Messages apiMessages,
        ServerRepository servers,
        ServerIdentity self,
        PremiumRank premiumRank,
        Pets pets,
        ServerGroups groups,
        FriendsChannel friends,
        CosmeticPreviews previews,
        NavigatorTeleports teleports
) {

    private static final int TELEPORT_PARTICLES = 20;

    public Menus {
        BlockcraftApi.get().profiles().shop(this::openShop);
    }

    public void warp(Player player, LobbyLocation target) {
        var destination = locations.get(target);
        if (destination.isEmpty()) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("navigator.place-unavailable"));
            return;
        }
        var teleport = teleports.start(player, destination.get());
        if (teleport.isEmpty()) {
            return;
        }
        Menu.close(player);
        teleport.get().whenComplete((teleported, failure) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Tasks.sync(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) {
                    return;
                }
                if (failure != null || !Boolean.TRUE.equals(teleported)) {
                    Sounds.MENU_DENY.play(player);
                    player.sendMessage(messages.get("navigator.teleport-failed"));
                    return;
                }
                Sounds.TELEPORT.play(player);
                if (settings.enabled(player, PlayerSetting.EFFECTS)) {
                    player.spawnParticle(Particle.FLAME, player.getLocation(), TELEPORT_PARTICLES, 0.5, 0.5, 0.5, 0.01);
                }
            });
        });
    }

    public void openNavigator(Player player) {
        new NavigatorMenu(this).open(player);
    }

    public void openSecrets(Player player) {
        BlockcraftApi.get().playerTasks().latest(player, () -> unlocks.owned(player.getUniqueId(), Secret.ITEM_PREFIX),
                found -> new SecretsMenu(this, found).open(player));
    }

    public void openShop(Player player) {
        BlockcraftApi.get().playerTasks().latest(player, () -> BlockcraftApi.get().coins().get(player.getUniqueId()),
                coins -> new ShopMenu(this, player, coins).open(player));
    }

    public void openHeads(Player player) {
        openCosmetics(player, CosmeticKinds.heads(this));
    }

    public void openShirts(Player player) {
        openCosmetics(player, CosmeticKinds.shirts(this));
    }

    public void openPremium(Player player) {
        new PremiumMenu(this).open(player);
    }

    public void openPets(Player player) {
        openCosmetics(player, CosmeticKinds.pets(this));
    }

    public void openTrails(Player player) {
        openCosmetics(player, CosmeticKinds.trails(this));
    }

    public void openFriends(Player player) {
        new FriendsMenu(this).open(player);
    }

    public void openProfile(Player player) {
        BlockcraftApi.get().profiles().open(player, Optional.of(this::openExtras));
    }

    public void openSettings(Player player) {
        new SettingsDialog(player, Optional.empty()).open(player);
    }

    public void openExtras(Player player) {
        new ExtrasMenu(this, player).open(player);
    }

    public void openGadgets(Player player) {
        openCosmetics(player, CosmeticKinds.gadgets(this));
    }

    public void openKillEffects(Player player) {
        openKillEffects(player, this::openExtras);
    }

    void openKillEffects(Player player, Consumer<Player> back) {
        openCosmetics(player, CosmeticKinds.killEffects(this, back));
    }

    public void openWinEffects(Player player) {
        openWinEffects(player, this::openExtras);
    }

    void openWinEffects(Player player, Consumer<Player> back) {
        openCosmetics(player, CosmeticKinds.winEffects(this, back));
    }

    private <C> void openCosmetics(Player player, CosmeticKind<C> kind) {
        kind.ownedPrefix().ifPresentOrElse(
                prefix -> BlockcraftApi.get().playerTasks().latest(player,
                        () -> unlocks.owned(player.getUniqueId(), prefix),
                        owned -> new CosmeticMenu<>(this, kind, player, owned).open(player)),
                () -> new CosmeticMenu<>(this, kind, player, Set.of()).open(player));
    }

    /** Premium players choose among the premium tags; anyone wears the tags they earned. */
    public void openChatTags(Player player) {
        boolean premium = player.hasPermission(LobbyPermissions.PREMIUM);
        BlockcraftApi.get().playerTasks().latest(player, () -> unlocks.owned(player.getUniqueId(), ChatTagChoice.ITEM_PREFIX), owned -> {
            var choices = ChatTagChoice.available(premium, owned);
            if (choices.isEmpty()) {
                Menu.close(player);
                messages.getLines("premium.required").forEach(player::sendMessage);
                return;
            }
            openCosmetics(player, CosmeticKinds.chatTags(this, choices));
        });
    }

    public void openLobbies(Player player) {
        BlockcraftApi.get().playerTasks().latest(player, this::lobbies,
                lobbies -> new LobbySelectorMenu(this, lobbies).open(player));
    }

    List<ServerRecord> lobbies() {
        return servers.alive(config.serverListStaleSeconds()).stream()
                .filter(server -> server.group().equals(self.group()))
                .sorted(Comparator.comparing((ServerRecord server) -> !server.name().equals(self.name()))
                        .thenComparing(server -> !server.joinable())
                        .thenComparing(ServerRecord::name))
                .toList();
    }
}
