package me.blockcraft.archive;

import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.player.Arrival;
import me.blockcraft.archive.exhibit.ExhibitWorlds;
import me.blockcraft.archive.hall.EntranceHall;
import me.blockcraft.common.message.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Every visitor arrives in the entrance hall with the guide in hand and may fly wherever they go. Whoever drops below
 * a map is put back where they arrived in it.
 */
final class VisitorListener implements Listener {

    /** How far below a world's lowest block a visitor falls before being put back. */
    private static final int FALL_DEPTH = 16;

    private final Messages messages;
    private final EntranceHall hall;
    private final ExhibitWorlds worlds;
    private final ArchiveHotbar hotbar;

    VisitorListener(Messages messages, EntranceHall hall, ExhibitWorlds worlds, ArchiveHotbar hotbar) {
        this.messages = messages;
        this.hall = hall;
        this.worlds = worlds;
        this.hotbar = hotbar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        var player = event.getPlayer();
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.teleport(hall.spawn());
        allowFlight(player);
        hotbar.give(player);
        Arrival.show(player, messages.get("arrival"));
        player.sendMessage(messages.get("join.introduction"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    /** A change of worlds may reset flight to what the game mode allows, so it is given again on arrival. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        allowFlight(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        var to = event.getTo();
        var world = to.getWorld();
        if (to.getY() >= world.getMinHeight() - FALL_DEPTH) {
            return;
        }
        var player = event.getPlayer();
        player.setFallDistance(0.0f);
        player.teleport(worlds.exhibitOf(world).map(exhibit -> worlds.arrival(exhibit, world)).orElseGet(hall::spawn));
    }

    private static void allowFlight(Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SURVIVAL) {
            player.setAllowFlight(true);
        }
    }
}
