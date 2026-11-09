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
import me.blockcraft.story.game.ChoiceRepository;
import me.blockcraft.story.game.StoryGame;
import me.blockcraft.story.quest.Quests;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoryPlugin extends JavaPlugin {

    private StoryGame game;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var api = BlockcraftApi.get();
        var gameConfig = GameConfig.from(getConfig(), 1);
        var services = GameServices.load(this, gameConfig, "bc_story_stats");
        var world = Bukkit.getWorlds().getFirst();
        Worlds.settle(world);
        var choices = new ChoiceRepository(api.database());
        choices.createTable();
        game = new StoryGame(this, gameConfig, services.messages(), world, services.stats(), services.menus(), choices);
        game.connect(Quests.register(this, StoryGame.MISSION, game, game.npcs()));
        api.presence().maxPlayers(getConfig().getInt("players.max"));
        services.listen(
                new RoundConnections(game),
                game.lobbyHotbar(),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode()).rescue(game::lobbySpawn),
                new ArenaProtection(() -> false));
        services.commands(commands -> commands.register(StartCommand.of(game.countdown()).build()));
        Bukkit.getOnlinePlayers().forEach(game::join);
    }

    @Override
    public void onDisable() {
        if (game != null) game.stop();
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
