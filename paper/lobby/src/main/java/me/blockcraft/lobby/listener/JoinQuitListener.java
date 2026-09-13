package me.blockcraft.lobby.listener;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.rank.TabTeams;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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

    private static final int CHAT_CLEAR_LINES = 121;
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(500));

    private final Plugin plugin;
    private final Messages messages;
    private final CoinsRepository coins;
    private final Locations<LobbyLocation> locations;
    private final LobbyItems items;
    private final TabTeams tabTeams;
    private final Trails trails;
    private final Pets pets;
    private final PlayerHider hider;

    public JoinQuitListener(Plugin plugin, Messages messages, CoinsRepository coins, Locations<LobbyLocation> locations,
                            LobbyItems items, TabTeams tabTeams, Trails trails, Pets pets, PlayerHider hider) {
        this.plugin = plugin;
        this.messages = messages;
        this.coins = coins;
        this.locations = locations;
        this.items = items;
        this.tabTeams = tabTeams;
        this.trails = trails;
        this.pets = pets;
        this.hider = hider;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        for (int i = 0; i < CHAT_CLEAR_LINES; i++) {
            player.sendMessage(Component.empty());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.showTitle(Title.title(messages.get("join.title"), messages.get("join.subtitle"), TITLE_TIMES));
        player.getInventory().setArmorContents(null);
        items.give(player);
        locations.get(LobbyLocation.SPAWN).ifPresent(player::teleport);
        player.setCollidable(false);
        player.showBossBar(BossBar.bossBar(messages.get("join.bossbar"), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        tabTeams.update(player);
        hider.onJoin(player);
        showCoinsAsLevel(player);
    }

    @EventHandler
    public void onNickChange(NickChangeEvent event) {
        tabTeams.update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        tabTeams.remove(player);
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
