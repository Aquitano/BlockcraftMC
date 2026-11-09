package me.blockcraft.story.quest;

import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.game.Choice;
import me.blockcraft.story.game.Stage;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.BetonQuestApi;
import org.betonquest.betonquest.api.BetonQuestApiService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.identifier.ActionIdentifier;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.quest.action.OnlineAction;
import org.betonquest.betonquest.api.quest.action.OnlineActionAdapter;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;
import org.betonquest.betonquest.api.quest.objective.ObjectiveFactory;
import org.betonquest.betonquest.kernel.registry.feature.ConversationIORegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * What this plugin adds to BetonQuest and how it calls back into it. The quest package {@code pack} drives
 * conversations and per-player objectives; the party's stage and decision stay here, read through these conditions:
 * <ul>
 *     <li>{@code storystage <stage>}, {@code storychoice <choice>} and {@code storyvoted} as conditions,</li>
 *     <li>{@code storystage <stage>} and {@code storyvote <choice>} as actions,</li>
 *     <li>{@code holdconsole <location> <radius> <seconds>} as an objective,</li>
 *     <li>{@code blockcraft <key>} as an NPC,</li>
 *     <li>{@code blockhud} as the conversation IO, which shows conversations in the {@link DialogueHud}.</li>
 * </ul>
 * When the party enters a stage, every member runs the package's action {@code stage_<stage>}, which hands out that
 * stage's objectives; a member who rejoins runs it again.
 */
public final class Quests {

    private final Plugin plugin;
    private final BetonQuestApi api;
    private final String pack;

    private Quests(Plugin plugin, BetonQuestApi api, String pack) {
        this.plugin = plugin;
        this.api = api;
        this.pack = pack;
    }

    public static Quests register(Plugin plugin, String pack, Party party, StoryNpcs npcs, DialogueHud hud, Voices voices) {
        var api = BetonQuestApiService.get().orElseThrow(() -> new IllegalStateException("BetonQuest is not running")).api(plugin);
        var conditions = api.conditions().registry();
        conditions.register("storystage", (PlayerConditionFactory) instruction -> {
            var stage = instruction.enumeration(Stage.class).get();
            return profile -> party.stage() == stage.getValue(profile);
        });
        conditions.register("storychoice", (PlayerConditionFactory) instruction -> {
            var choice = instruction.enumeration(Choice.class).get();
            return profile -> party.choice().equals(Optional.of(choice.getValue(profile)));
        });
        conditions.register("storyvoted", (PlayerConditionFactory) instruction -> profile -> party.voted(profile.getPlayerUUID()));
        var actions = api.actions().registry();
        actions.register("storystage", (PlayerActionFactory) instruction -> {
            var stage = instruction.enumeration(Stage.class).get();
            return new OnlineActionAdapter(onMainThread(profile -> party.advance(stage.getValue(profile))));
        });
        actions.register("storyvote", (PlayerActionFactory) instruction -> {
            var choice = instruction.enumeration(Choice.class).get();
            return new OnlineActionAdapter(onMainThread(profile -> party.vote(profile.getPlayer(), choice.getValue(profile))));
        });
        api.objectives().registry().register("holdconsole", (ObjectiveFactory) (instruction, service) ->
                new ConsoleObjective(service, api.profiles(), party, instruction.location().get(),
                        instruction.number().get(), instruction.number().get()));
        var registry = api.npcs().registry();
        registry.register(StoryNpcs.TYPE, instruction -> new StoryNpcs.Lookup(npcs, instruction.string().get()));
        registry.registerIdentifier(npcs.new Identifier());
        api.bukkit().registerEvents(npcs.new Clicks(api.profiles(), registry));
        BetonQuest.getInstance().getComponentLoader().get(ConversationIORegistry.class).register(HudConversationIO.NAME,
                (conversation, profile) -> new HudConversationIO(plugin, conversation, profile, hud, voices));
        return new Quests(plugin, api, pack);
    }

    private static OnlineAction onMainThread(OnlineAction action) {
        return new OnlineAction() {
            @Override
            public void execute(OnlineProfile profile) throws QuestException {
                action.execute(profile);
            }

            @Override
            public boolean isPrimaryThreadEnforced() {
                return true;
            }
        };
    }

    public void enter(Player player, Stage stage) {
        run(player, "stage_" + stage.key());
    }

    public void run(Player player, String action) {
        try {
            var id = api.identifiers().getFactory(ActionIdentifier.class).parseIdentifier(null, pack + ">" + action);
            api.actions().manager().run(api.profiles().getProfile(player), id);
        } catch (QuestException failure) {
            plugin.getSLF4JLogger().warn("BetonQuest action {}>{} failed for {}", pack, action, player.getName(), failure);
        }
    }
}
