package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.dialog.SettingsDialog;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Head;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Shirt;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.friends.FriendEntry;
import me.blockcraft.lobby.gadget.Gadgets;
import me.blockcraft.lobby.network.GroupCounts;
import me.blockcraft.lobby.rank.PremiumRank;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
        Trails trails,
        Gadgets gadgets,
        GroupCounts groupCounts
) {

    private static final int TELEPORT_PARTICLES = 20;

    /** Shows what the click is about to cost before anything is bought. */
    public void purchase(Player player, ItemStack subject, String item, int price, Consumer<Player> onOwned) {
        new ConfirmDialog(subject, buyer -> buy(buyer, item, price, onOwned), Menu::close).open(player);
    }

    /**
     * Buys the item off the main thread; {@code onOwned} runs on it once the player owns the item, whether this
     * click bought it or an earlier one did. The level shows the new balance.
     */
    private void buy(Player player, String item, int price, Consumer<Player> onOwned) {
        Tasks.async(plugin, () -> shop.buy(player.getUniqueId(), BlockcraftApi.get().realName(player), item, price), purchase -> {
            if (!player.isOnline()) {
                return;
            }
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    Sounds.PURCHASE.play(player);
                    player.sendMessage(messages.get("shop.bought"));
                    onOwned.accept(player);
                }
                case ALREADY_OWNED -> onOwned.accept(player);
                case NOT_ENOUGH_COINS -> {
                    Sounds.MENU_DENY.play(player);
                    player.sendMessage(messages.get("shop.too-expensive", Formatter.number("coins", price)));
                }
            }
            player.setLevel(purchase.balance());
        });
    }

    public void warp(Player player, LobbyLocation target) {
        locations.get(target).ifPresent(location -> {
            player.teleport(location);
            Sounds.TELEPORT.play(player);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), TELEPORT_PARTICLES, 0.5, 0.5, 0.5, 0.01);
        });
    }

    public void openNavigator(Player player) {
        new NavigatorMenu(this).open(player);
    }

    public void openSecrets(Player player) {
        Tasks.async(plugin, () -> unlocks.owned(player.getUniqueId(), Secret.ITEM_PREFIX),
                found -> new SecretsMenu(this, found).open(player));
    }

    public void openShop(Player player) {
        new ShopMenu(this).open(player);
    }

    public void openHeads(Player player) {
        Tasks.async(plugin, () -> unlocks.owned(player.getUniqueId(), Head.ITEM_PREFIX),
                owned -> new HeadsMenu(this, owned).open(player));
    }

    public void openShirts(Player player) {
        Tasks.async(plugin, () -> unlocks.owned(player.getUniqueId(), Shirt.ITEM_PREFIX),
                owned -> new ShirtsMenu(this, owned).open(player));
    }

    public void openPremium(Player player) {
        new PremiumMenu(this).open(player);
    }

    public void openPets(Player player) {
        new PetsMenu(this).open(player);
    }

    public void openTrails(Player player) {
        new TrailsMenu(this).open(player);
    }

    /** Drawn from the proxy's answer, not from the database: only it knows who is online and where. */
    public void openFriends(Player player, List<FriendEntry> friends, List<String> requests) {
        new FriendsMenu(this, friends, requests).open(player);
    }

    public void openProfile(Player player) {
        BlockcraftApi.get().profiles().open(player, Optional.of(this::openExtras));
    }

    public void openSettings(Player player) {
        new SettingsDialog(player, Optional.empty()).open(player);
    }

    public void openExtras(Player player) {
        new ExtrasMenu(this).open(player);
    }

    public void openGadgets(Player player) {
        new GadgetsMenu(this, player).open(player);
    }

    public void openKillEffects(Player player) {
        openEffects(player, EffectKind.KILL, config.killEffectPrice());
    }

    public void openWinEffects(Player player) {
        openEffects(player, EffectKind.WIN, config.winEffectPrice());
    }

    private <E extends Enum<E>> void openEffects(Player player, EffectKind<E> kind, int price) {
        Tasks.async(plugin, () -> unlocks.owned(player.getUniqueId(), kind.itemPrefix()),
                owned -> new EffectsMenu<>(this, kind, price, player, owned).open(player));
    }

    public void openChatTags(Player player) {
        if (!player.hasPermission(LobbyPermissions.PREMIUM)) {
            Menu.close(player);
            messages.getLines("premium.required").forEach(player::sendMessage);
            return;
        }
        new ChatTagsMenu(this, player).open(player);
    }

    public void openLobbies(Player player) {
        Tasks.async(plugin, () -> servers.alive(config.serverListStaleSeconds()).stream()
                        .filter(server -> server.group().equals(self.group()))
                        .sorted(Comparator.comparing(ServerRecord::name))
                        .toList(),
                lobbies -> new LobbySelectorMenu(this, lobbies).open(player));
    }
}
