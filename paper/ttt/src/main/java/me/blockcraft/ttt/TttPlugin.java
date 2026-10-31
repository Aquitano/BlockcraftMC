package me.blockcraft.ttt;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.MapVote;
import me.blockcraft.ttt.arena.TownBuilder;
import me.blockcraft.ttt.arena.TownMaps;
import me.blockcraft.ttt.command.TraitorChatCommand;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.KarmaRepository;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.listener.ArenaListener;
import me.blockcraft.ttt.listener.ChatListener;
import me.blockcraft.ttt.listener.ConnectionListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TttPlugin extends JavaPlugin {

    private static final String NAMESPACE = "ttt";
    private static final String LOBBY = "lobby";

    private TttGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = TttConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "bc_ttt_stats");
        stats.createTable();
        var karma = new KarmaRepository(api.database());
        karma.createTable();
        var lobby = Bukkit.getWorlds().getFirst();
        Worlds.settle(lobby);
        var locations = Locations.load(api.map().file(this, "locations.yml"));
        Supplier<Location> lobbySpawn = () -> locations.get(LOBBY).orElseGet(lobby::getSpawnLocation);
        var vote = new MapVote(messages, choices(config), getLogger());
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new TttGame(this, gameConfig, config, messages, vote, lobbySpawn, stats, statsMenus, karma);
        api.presence().maxPlayers(config.capacity());

        register(game.hotbar());
        register(new ConnectionListener(game));
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(), () -> Optional.of(lobbySpawn.get())));
        register(new ArenaProtection(() -> false));
        register(new ArenaListener(game, messages));
        register(new ChatListener(game, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(ForceMapCommand.create(messages, vote::keys, () -> game.phase() == GamePhase.LOBBY, vote::force));
            commands.register(SetupCommand.locations(messages, List.of(LOBBY), Function.identity(), locations::set));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
            commands.register(TraitorChatCommand.create(game, messages));
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }

    /**
     * Eichenhain and every imported map of the pool. Eichenhain is built now, because building it takes seconds that
     * a round start must not stall for; the imported maps are only copied into place, and loading one takes a
     * fraction of that. A map whose copy fails stays out of the vote.
     */
    private List<MapVote.Choice> choices(TttConfig config) {
        var choices = new ArrayList<MapVote.Choice>();
        var eichenhainKey = new NamespacedKey(NAMESPACE, "eichenhain");
        Worlds.discard(eichenhainKey);
        var eichenhainWorld = Worlds.createArena(eichenhainKey);
        playable(eichenhainWorld, config);
        var eichenhain = TownBuilder.build(eichenhainWorld);
        choices.add(new MapVote.Choice(eichenhainKey.getKey(), eichenhain.name(), List.of(), Material.OAK_SAPLING, () -> eichenhain));
        var pool = new TownMaps(BlockcraftApi.get().map().file(this, "maps"), getLogger());
        var arenas = new TemplateDimensions(NAMESPACE, world -> {
            Worlds.settle(world);
            playable(world, config);
        });
        for (var map : pool.maps()) {
            try {
                arenas.copy(pool.world(map.key()), map.key());
                choices.add(new MapVote.Choice(map.key(), map.name(), map.builders(), map.icon(), () -> map.in(arenas.load(map.key()))));
            } catch (UncheckedIOException | IllegalStateException e) {
                getLogger().warning("Skipping map " + map.key() + ": " + e.getMessage());
            }
        }
        return choices;
    }

    /** Peaceful would take the creeper arrows' creepers away at once. */
    private static void playable(World world, TttConfig config) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, config.regeneration());
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
