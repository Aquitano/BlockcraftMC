package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.dialog.SettingsDialog;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.common.shop.Shop;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        Shop shop,
        PlayerSettings settings,
        Messages apiMessages,
        ServerRepository servers,
        ServerIdentity self,
        PremiumRank premiumRank,
        Pets pets,
        ServerGroups groups,
        FriendsChannel friends,
        CosmeticPreviews previews
) {

    private static final int TELEPORT_PARTICLES = 20;

    private record PurchaseState(int balance, boolean owned) { }

    /** Shows what the click is about to cost before anything is bought, drawn as the player's client draws it. */
    public void purchase(Player player, ItemStack subject, String item, int price,
                         Consumer<Player> onOwned, Consumer<Player> returnTo) {
        var api = BlockcraftApi.get();
        MenuIcon.style(subject, api.resourcePack().applied(player));
        var id = player.getUniqueId();
        api.playerTasks().latest(player,
                () -> new PurchaseState(api.coins().get(id), unlocks.owns(id, item)), state -> {
            if (state.owned()) {
                onOwned.accept(player);
                returnTo.accept(player);
                return;
            }
            int balance = state.balance();
            var details = List.of(
                    messages.get("shop.confirm-price", Formatter.number("coins", price)),
                    messages.get("shop.confirm-balance", Formatter.number("coins", balance)),
                    messages.get(balance >= price ? "shop.confirm-remaining" : "shop.confirm-missing",
                            Formatter.number("coins", Math.abs(balance - price))));
            Optional<Consumer<Player>> accept = balance >= price
                    ? Optional.of(buyer -> buy(buyer, item, price, onOwned, returnTo)) : Optional.empty();
            new ConfirmDialog(subject, details, accept, returnTo).open(player);
        });
    }

    /**
     * Buys the item off the main thread; {@code onOwned} runs on it once the player owns the item, whether this
     * click bought it or an earlier one did. The balance is labelled separately from network levels.
     */
    private void buy(Player player, String item, int price, Consumer<Player> onOwned, Consumer<Player> returnTo) {
        var api = BlockcraftApi.get();
        api.notifications().feedback(player, messages.get("shop.pending"));
        var current = api.playerTasks().currentScreen(player);
        var id = player.getUniqueId();
        var name = api.realName(player);
        Consumer<Player> returnIfCurrent = buyer -> {
            if (current.getAsBoolean()) {
                returnTo.accept(buyer);
            }
        };
        api.playerTasks().write(player, () -> shop.buy(id, name, item, price), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    api.notifications().rewardSound(player, Sounds.PURCHASE);
                    api.notifications().feedback(player, messages.get("shop.completed"));
                    player.sendMessage(messages.get("shop.bought"));
                    onOwned.accept(player);
                }
                case ALREADY_OWNED -> {
                    api.notifications().feedback(player, messages.get("shop.completed"));
                    onOwned.accept(player);
                }
                case NOT_ENOUGH_COINS -> {
                    Sounds.MENU_DENY.play(player);
                    api.notifications().feedback(player, messages.get("shop.confirm-missing",
                            Formatter.number("coins", price - purchase.balance())));
                    player.sendMessage(messages.get("shop.too-expensive", Formatter.number("coins", price)));
                }
            }
            player.sendMessage(messages.get("shop.balance", Formatter.number("coins", purchase.balance())));
            returnIfCurrent.accept(player);
        }, failed -> {
            api.notifications().feedback(failed, apiMessages.get("operation.failed"));
            returnIfCurrent.accept(failed);
        });
    }

    public void warp(Player player, LobbyLocation target) {
        var destination = locations.get(target);
        if (destination.isEmpty()) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("navigator.place-unavailable"));
            return;
        }
        Menu.close(player);
        player.teleportAsync(destination.get()).whenComplete((teleported, failure) -> {
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
        new ShopMenu(this, player).open(player);
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
        openCosmetics(player, CosmeticKinds.killEffects(this));
    }

    public void openWinEffects(Player player) {
        openCosmetics(player, CosmeticKinds.winEffects(this));
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
        BlockcraftApi.get().playerTasks().latest(player, () -> servers.alive(config.serverListStaleSeconds()).stream()
                        .filter(server -> server.group().equals(self.group()))
                        .sorted(Comparator.comparing(ServerRecord::name))
                        .toList(),
                lobbies -> new LobbySelectorMenu(this, lobbies).open(player));
    }
}
