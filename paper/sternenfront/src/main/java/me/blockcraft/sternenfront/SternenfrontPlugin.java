package me.blockcraft.sternenfront;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.sternenfront.arena.TessarMap;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.listener.ArenaListener;
import me.blockcraft.sternenfront.listener.ChatListener;
import me.blockcraft.sternenfront.listener.CombatListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class SternenfrontPlugin extends JavaPlugin {

    private static final NamespacedKey WORLD = new NamespacedKey("sternenfront", "tessar");
    private static final double BORDER_MARGIN = 2;

    private SternenfrontGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = SternenfrontConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_sternenfront_stats");
        var messages = services.messages();
        Worlds.discard(WORLD);
        var world = Worlds.createArena(WORLD);
        // Hurt players heal through the round's own regeneration and the medics, not through hunger.
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        var map = TessarMap.build(world);
        border(map);
        var lobby = api.waitingLobby().map(WaitingLobby::spawn).orElse(map.lobbySpawn());
        lobby.getWorld().setSpawnLocation(lobby);
        game = new SternenfrontGame(this, gameConfig, config, messages, map, lobby, services.stats(), services.menus());
        api.presence().maxPlayers(config.capacity());

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> Optional.of(lobby)),
                new ArenaProtection(() -> false),
                new ArenaListener(game, api.buildMode()),
                new CombatListener(game, api.buildMode()),
                new ChatListener(game, messages));

        services.commands(commands -> commands.register(StartCommand.of(game.countdown()).build()));

        Bukkit.getOnlinePlayers().forEach(game::join);
    }

    /** A square border round the map's play area; the map's own cliffs are the real edge. */
    private static void border(FrontMap map) {
        var area = map.playArea();
        var border = map.world().getWorldBorder();
        border.setCenter(area.getCenterX(), area.getCenterZ());
        border.setSize(Math.max(area.getWidthX(), area.getWidthZ()) + BORDER_MARGIN);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
