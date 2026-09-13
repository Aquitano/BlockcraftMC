package me.blockcraft.lobby.listener;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItem;
import me.blockcraft.lobby.item.LobbyItems;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

public final class JoinQuitListener implements Listener {

    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(500));

    private final Plugin plugin;
    private final Messages messages;
    private final CoinsRepository coins;
    private final Locations<LobbyLocation> locations;
    private final LobbyItems items;
    private final Trails trails;
    private final Pets pets;
    private final PlayerHider hider;

    public JoinQuitListener(Plugin plugin, Messages messages, CoinsRepository coins, Locations<LobbyLocation> locations,
                            LobbyItems items, Trails trails, Pets pets, PlayerHider hider) {
        this.plugin = plugin;
        this.messages = messages;
        this.coins = coins;
        this.locations = locations;
        this.items = items;
        this.trails = trails;
        this.pets = pets;
        this.hider = hider;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.showTitle(Title.title(messages.get("join.title"), messages.get("join.subtitle"), TITLE_TIMES));
        player.getInventory().setArmorContents(null);
        items.give(player, true);
        locations.get(LobbyLocation.SPAWN).ifPresent(player::teleport);
        player.setCollidable(false);
        player.showBossBar(BossBar.bossBar(messages.get("join.bossbar"), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        Sounds.JOIN.play(player);
        hider.onJoin(player);
        showCoinsAsLevel(player);
    }

    /** The hider choice is stored, so it is applied once the settings arrived rather than on join. */
    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        Player player = event.getPlayer();
        if (hider.applyStored(player)) {
            player.getInventory().setItem(LobbyItem.PLAYER_HIDER.slot(), items.hiderItem(false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        trails.clear(player);
        hider.forget(player);
        pets.release(player.getUniqueId());
    }

    private void showCoinsAsLevel(Player player) {
        Tasks.async(plugin, () -> coins.get(player.getUniqueId()), balance -> {
            if (player.isOnline()) {
                player.setLevel(balance);
            }
        });
    }
}
