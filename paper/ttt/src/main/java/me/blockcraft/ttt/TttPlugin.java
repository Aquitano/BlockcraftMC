package me.blockcraft.ttt;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.ttt.arena.MapVote;
import me.blockcraft.ttt.arena.TownBuilder;
import me.blockcraft.ttt.arena.TownMaps;
import me.blockcraft.ttt.command.TraitorChatCommand;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.KarmaRepository;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.listener.ArenaListener;
import me.blockcraft.ttt.listener.ChatListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
        var services = GameServices.load(this, gameConfig, "bc_ttt_stats");
        var messages = services.messages();
        var karma = new KarmaRepository(api.database());
        karma.createTable();
        var lobby = Bukkit.getWorlds().getFirst();
        Worlds.settle(lobby);
        var locations = Locations.load(api.map().file(this, "locations.yml"));
        Supplier<Location> lobbySpawn = () -> api.waitingLobby().map(WaitingLobby::spawn)
                .or(() -> locations.get(LOBBY)).orElseGet(lobby::getSpawnLocation);
        var vote = new MapVote(messages, choices(config), getLogger());
        game = new TttGame(this, gameConfig, config, messages, vote, lobbySpawn, services.stats(), services.menus(), karma);
        api.presence().maxPlayers(config.capacity());

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> Optional.of(lobbySpawn.get())),
                new ArenaProtection(() -> false),
                new ArenaListener(game, messages),
                new ChatListener(game, messages));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(ForceMapCommand.create(messages, vote::keys, () -> game.phase() == GamePhase.LOBBY, vote::force));
            if (api.waitingLobby().isEmpty()) {
                commands.register(SetupCommand.locations(messages, List.of(LOBBY), Function.identity(), locations::set));
            }
            commands.register(TraitorChatCommand.create(game, messages));
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
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
}
