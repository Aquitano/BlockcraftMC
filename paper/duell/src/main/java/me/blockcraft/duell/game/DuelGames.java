package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.ThrowFight;
import me.blockcraft.api.game.rush.MapPool;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.arena.SumoArena;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DuelGames {
    public interface Events {
        void changed();
        void completed(Optional<UUID> winner);
    }

    record Context(Plugin plugin, DuellConfig config, Messages messages, GameItems items, RoundSummary summary,
                   List<UUID> participants, List<Location> spawns, Events events) { }

    private final Plugin plugin;
    private final DuellConfig config;
    private final Messages messages;
    private final Spawns spawns;
    private final SumoArena sumo;
    private final RushArena rush;
    private final RushConfig rushConfig;
    private final MapPool maps;
    private final GameItems items;
    private final LootChests chests;

    public DuelGames(Plugin plugin, DuellConfig config, Messages messages, Spawns spawns, SumoArena sumo,
                     RushArena rush, RushConfig rushConfig, MapPool maps, GameItems items, LootChests chests) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.sumo = sumo;
        this.rush = rush;
        this.rushConfig = rushConfig;
        this.maps = maps;
        this.items = items;
        this.chests = chests;
    }

    public List<Integer> rushMaps() {
        return java.util.stream.IntStream.rangeClosed(1, maps.maps().size()).boxed().toList();
    }

    public Map<DuelMode, Integer> mapPresets() {
        return rushMaps().stream().filter(map -> maps.maps().get(map - 1).key().equals(maps.selection()))
                .findFirst().map(map -> Map.of(DuelMode.MLGRUSH, map)).orElseGet(Map::of);
    }

    public String rushMapName(int map) {
        return maps.maps().get(map - 1).name();
    }

    public boolean rushOwns(Location location) {
        return rush.owns(location);
    }

    public DuelGame open(DuelVote.Outcome outcome, List<UUID> participants, RoundSummary summary, Events events) {
        var mode = outcome.mode();
        if (mode == DuelMode.MLGRUSH) {
            rush.selectMap(maps.maps().get(outcome.map() - 1));
            return new MlgRushDuelGame(plugin, rushConfig, messages, rush, participants, summary, events);
        }
        var locations = java.util.stream.IntStream.rangeClosed(1, 2)
                .mapToObj(slot -> mode == DuelMode.SUMO || mode == DuelMode.THROW ? sumo.spawn(slot)
                        : spawns.mapSpawn(mode, outcome.map(), slot).orElse(null)).toList();
        var context = new Context(plugin, config, messages, items, summary, List.copyOf(participants), locations, events);
        MapDuelGame game = switch (mode) {
            case VS -> new VsDuelGame(context);
            case SG -> new SgDuelGame(context, chests);
            case SUPERJUMP -> new SuperJumpDuelGame(context);
            case SUMO -> new SumoDuelGame(context, sumo, Optional.empty());
            case THROW -> new SumoDuelGame(context, sumo, config.throwFight().ammo(BlockcraftApi.get().calendar())
                    .or(() -> Optional.of(ThrowFight.Ammo.SNOWBALL)));
            case MLGRUSH -> throw new IllegalStateException();
        };
        game.prepare();
        Bukkit.getPluginManager().registerEvents(game, plugin);
        return game;
    }
}
