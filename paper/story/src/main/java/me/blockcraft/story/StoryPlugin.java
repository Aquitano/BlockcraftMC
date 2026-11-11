package me.blockcraft.story;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.story.campaign.Campaigns;
import me.blockcraft.story.cutscene.SeenScenes;
import me.blockcraft.story.debug.DebugBoard;
import me.blockcraft.story.debug.StoryCommand;
import me.blockcraft.story.game.StoryGame;
import me.blockcraft.story.quest.Quests;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoryPlugin extends JavaPlugin {

    private StoryGame game;
    private DebugBoard board;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        if (!getConfig().isConfigurationSection("mission")) {
            getSLF4JLogger().warn("config.yml has no mission to play, so the story stays off");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var api = BlockcraftApi.get();
        var gameConfig = GameConfig.from(getConfig(), 1);
        var services = GameServices.load(this, gameConfig, "bc_story_stats");
        var world = Bukkit.getWorlds().getFirst();
        Worlds.settle(world);
        // A peaceful world removes the droids, which are monsters, in the tick they spawn.
        world.setDifficulty(Difficulty.NORMAL);
        // A demolition's fire would spread to blocks the round's end does not put back.
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        var mission = getConfig().getString("mission.name", "mission");
        game = new StoryGame(this, gameConfig, services.messages(), world, services.stats(), services.menus(),
                new Campaigns(api.database(), mission),
                new SeenScenes(this, api.database(), mission));
        game.connect(Quests.register(this, game.mission(), game, game.trace(), game::members, game.npcs(), game.dialogue(), game.voices()));
        board = new DebugBoard(this, game);
        api.presence().maxPlayers(getConfig().getInt("players.max"));
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
    }

    @Override
    public void onDisable() {
        if (board != null) board.close();
        if (game != null) game.stop();
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
