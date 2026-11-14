package me.blockcraft.story;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.story.campaign.Campaigns;
import me.blockcraft.story.cutscene.SeenScenes;
import me.blockcraft.story.debug.DebugBoard;
import me.blockcraft.story.debug.StoryCommand;
import me.blockcraft.story.game.StoryGame;
import me.blockcraft.story.quest.Quests;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * One story game on this server, started by the plugin that brings its mission. That plugin depends on this one and
 * on BetonQuest, and holds the mission in its own jar: {@code mission}, {@code stages} and {@code cutscenes.scenes} in
 * config.yml, the mission's lines in messages.yml, each lying on the core's as {@link CoreDefaults} says, and
 * {@code voices.properties} and {@code soundtrack.properties} with its recordings' lengths. Its quest package is named
 * like the mission. Whatever else the game needs, enemies or weapons, it adds itself: its own BetonQuest actions and
 * objectives, which reach the party through {@link #game()}.
 */
public final class Story {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final StoryGame game;
    private final DebugBoard board;

    private Story(JavaPlugin plugin, Messages messages, StoryGame game, DebugBoard board) {
        this.plugin = plugin;
        this.messages = messages;
        this.game = game;
        this.board = board;
    }

    /**
     * Call from {@code onEnable}. {@code loadout} equips a member whenever the mission puts them on their feet, with
     * an empty inventory: at its start and after a rejoin. The world is left peaceful with time, weather and mobs
     * standing still; a game with enemies sets the difficulty it needs afterwards.
     */
    public static Story start(JavaPlugin plugin, Consumer<Player> loadout) {
        CoreDefaults.underConfig(plugin);
        var config = plugin.getConfig();
        if (!config.isConfigurationSection("mission")) {
            throw new IllegalStateException(plugin.getName() + "'s config.yml has no mission to play");
        }
        var api = BlockcraftApi.get();
        var gameConfig = GameConfig.from(config, 1);
        var services = GameServices.load(plugin, gameConfig, "bc_story_stats", CoreDefaults.underMessages(plugin, api.tags()));
        var world = Bukkit.getWorlds().getFirst();
        Worlds.settle(world);
        // A demolition's fire would spread to blocks the round's end does not put back.
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        var mission = config.getString("mission.name", "mission");
        var game = new StoryGame(plugin, gameConfig, services.messages(), world, services.stats(), services.menus(),
                new Campaigns(api.database(), mission), new SeenScenes(plugin, api.database(), mission), loadout);
        game.connect(Quests.register(plugin, game.mission(), game, game.trace(), game::members, game.npcs(), game.dialogue(), game.voices()));
        var board = new DebugBoard(plugin, game);
        api.presence().maxPlayers(config.getInt("players.max"));
        services.listen(
                new RoundConnections(game),
                game.lobbyHotbar(),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode()).rescue(game::lobbySpawn),
                WorldProtection.during(() -> game.phase() == GamePhase.INGAME, api.buildMode())
                        .withDamage().withHeldItems().usable(WorldProtection::passage),
                new ArenaProtection(() -> false));
        services.commands(commands -> {
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(new StoryCommand(game, board).node());
        });
        Bukkit.getOnlinePlayers().forEach(game::join);
        return new Story(plugin, services.messages(), game, board);
    }

    public StoryGame game() {
        return game;
    }

    /** The game's texts on the core's, for whatever the game says itself. */
    public Messages messages() {
        return messages;
    }

    /** Call from {@code onDisable}. */
    public void stop() {
        board.close();
        game.stop();
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
