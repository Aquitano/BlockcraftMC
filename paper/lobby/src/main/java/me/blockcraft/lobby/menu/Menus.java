package me.blockcraft.lobby.menu;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.rank.PremiumRank;
import me.blockcraft.lobby.storage.UnlockStore;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Everything the chest GUIs need, so they can open one another without re-wiring. */
public record Menus(
        Messages messages,
        LobbyConfig config,
        Locations<LobbyLocation> locations,
        CoinsRepository coins,
        UnlockStore heads,
        UnlockStore shirts,
        UnlockStore secrets,
        UnlockStore premiumPurchases,
        PremiumRank premiumRank,
        Pets pets,
        Trails trails
) {

    private static final float TELEPORT_VOLUME = 4.0f;
    private static final float TELEPORT_PITCH = 4.0f;
    private static final int TELEPORT_PARTICLES = 20;

    public void warp(Player player, LobbyLocation target) {
        locations.get(target).ifPresent(location -> {
            player.teleport(location);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, TELEPORT_VOLUME, TELEPORT_PITCH);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), TELEPORT_PARTICLES, 0.5, 0.5, 0.5, 0.01);
        });
    }

    public void openNavigator(Player player) {
        new NavigatorMenu(this).open(player);
    }

    public void openSecrets(Player player) {
        new SecretsMenu(this, player).open(player);
    }

    public void openShop(Player player) {
        new ShopMenu(this).open(player);
    }

    public void openHeads(Player player) {
        new HeadsMenu(this, player).open(player);
    }

    public void openShirts(Player player) {
        new ShirtsMenu(this, player).open(player);
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
}
