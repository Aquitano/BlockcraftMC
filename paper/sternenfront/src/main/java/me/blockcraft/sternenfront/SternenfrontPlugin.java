package me.blockcraft.sternenfront;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.BundledDefaults;
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
import me.blockcraft.sternenfront.appearance.BodyRigs;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.debug.BotDebug;
import me.blockcraft.sternenfront.hero.HeroConfig;
import me.blockcraft.sternenfront.listener.ArenaListener;
import me.blockcraft.sternenfront.listener.ChatListener;
import me.blockcraft.sternenfront.listener.CombatListener;
import me.blockcraft.sternenfront.secret.Secrets;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class SternenfrontPlugin extends JavaPlugin {

    private static final double BORDER_MARGIN = 2;

    private SternenfrontGame game;
    private BodyRigs bodyRigs;
    private BotDebug botDebug;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig(), getConfig().getBoolean("bots.enabled", true) ? 1 : 2);
        var config = SternenfrontConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_sternenfront_stats");
        var messages = services.messages();
        var world = Bukkit.getWorlds().getFirst();
        Worlds.settle(world);
        // Hurt players heal through the round's own regeneration and the medics, not through hunger.
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        var map = TessarMap.load(world, api.map().file(this, TessarMap.FILE));
        keepLoaded(map);
        border(map);
        var lobby = api.waitingLobby().map(WaitingLobby::spawn).orElse(map.lobbySpawn());
        lobby.getWorld().setSpawnLocation(lobby);
        game = new SternenfrontGame(this, gameConfig, config, HeroConfig.from(getConfig()), FeelConfig.from(getConfig()),
                messages, map, lobby, services.stats(), services.menus());
        api.presence().maxPlayers(config.capacity());
        if (getConfig().getBoolean("appearance.droids", true) || getConfig().getBoolean("appearance.infantry", true)) {
            bodyRigs = BodyRigs.start(this, game, getConfig().getBoolean("appearance.droids", true), getConfig().getBoolean("appearance.infantry", true));
        }
        Secrets.start(this, game, messages);
        botDebug = new BotDebug(game, bodyRigs);

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> Optional.of(lobby)),
                new ArenaProtection(() -> false),
                new ArenaListener(game, api.buildMode()),
                new CombatListener(game, api.buildMode(), botDebug),
                new ChatListener(game, messages));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(botDebug.command());
            commands.register(game.intro().command());
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
    }

    /** The whole basin stays in memory: bots walk it and posts recolour it wherever the players are. */
    private void keepLoaded(FrontMap map) {
        var area = map.playArea();
        for (int x = (int) area.getMinX() >> 4; x <= (int) area.getMaxX() >> 4; x++) {
            for (int z = (int) area.getMinZ() >> 4; z <= (int) area.getMaxZ() >> 4; z++) {
                map.world().addPluginChunkTicket(x, z, this);
            }
        }
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
        if (botDebug != null) {
            botDebug.close();
        }
        if (bodyRigs != null) {
            bodyRigs.close();
        }
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
