package me.blockcraft.story.quest;

import me.blockcraft.story.debug.Trace;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.sound.Soundtrack;
import me.blockcraft.story.state.Facts;
import net.kyori.adventure.key.Key;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.BetonQuestApi;
import org.betonquest.betonquest.api.BetonQuestApiService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.common.function.QuestFunction;
import org.betonquest.betonquest.api.identifier.ActionIdentifier;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.OnlineAction;
import org.betonquest.betonquest.api.quest.action.OnlineActionAdapter;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;
import org.betonquest.betonquest.api.quest.action.PlayerlessAction;
import org.betonquest.betonquest.api.quest.action.PlayerlessActionFactory;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;
import org.betonquest.betonquest.api.quest.condition.PlayerlessCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerlessConditionFactory;
import org.betonquest.betonquest.api.quest.objective.ObjectiveFactory;
import org.betonquest.betonquest.api.service.action.ActionRegistry;
import org.betonquest.betonquest.api.service.condition.ConditionRegistry;
import org.betonquest.betonquest.kernel.registry.feature.ConversationIORegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * What this plugin adds to BetonQuest and how it calls back into it. The quest package {@code pack} drives the
 * mission; the party's facts stay here. Party actions act once for the whole party, whichever member runs them:
 * <ul>
 *     <li>{@code storyfact <name> <value|+n|-n|clear>} sets a fact; as a condition, {@code storyfact <name> <expected>}
 *     compares it, as {@link Facts#test(String, String)} reads {@code expected},</li>
 *     <li>{@code storystage <stage>} moves the party on; as a condition it asks for the current stage,</li>
 *     <li>{@code storyvote <topic> <choice>} casts this member's vote, and {@code storyvoted <topic>} asks for it,</li>
 *     <li>{@code storyscene <scene> [then:<actions>]} plays a cutscene, {@code storyafter <ticks> <actions>} schedules,</li>
 *     <li>{@code storymusic} and {@code storyambience <track|stop>}, {@code storysound <sound> [volume:] [pitch:] [at:]},</li>
 *     <li>{@code storyend <ending>} ends the mission,</li>
 *     <li>{@code holdspot <location> <radius> <seconds>} as an objective, {@code blockcraft <key>} as an NPC, and
 *     {@code blockhud} as the conversation IO, which shows conversations in the {@link DialogueHud}.</li>
 * </ul>
 * The game itself runs {@code begin} once when the mission starts, {@code enter_<stage>} once when the party enters a
 * stage, {@code stage_<stage>} for every member then and for a member who rejoins, and {@code decided_<topic>} once a
 * vote is decided, each only where the package has it. Within {@code stage_<stage>} and a held spot's actions, a
 * party action acts once for the party however many members run it, as {@link Fanout} says.
 */
public final class Quests {

    private final Plugin plugin;
    private final BetonQuestApi api;
    private final String pack;
    private final Trace trace;
    private final Supplier<List<Player>> members;
    private final Fanout fanout;

    private Quests(Plugin plugin, BetonQuestApi api, String pack, Trace trace, Supplier<List<Player>> members, Fanout fanout) {
        this.plugin = plugin;
        this.api = api;
        this.pack = pack;
        this.trace = trace;
        this.members = members;
        this.fanout = fanout;
    }

    @FunctionalInterface
    private interface PartyAction {
        void run(@Nullable Profile who) throws QuestException;
    }

    @FunctionalInterface
    private interface PartyCondition {
        boolean test(@Nullable Profile who) throws QuestException;
    }

    public static Quests register(Plugin plugin, String pack, Party party, Trace trace, Supplier<List<Player>> members,
                                  StoryNpcs npcs, DialogueHud hud, Voices voices) {
        var api = BetonQuestApiService.get().orElseThrow(() -> new IllegalStateException("BetonQuest is not running")).api(plugin);
        var fanout = new Fanout();
        var once = new Once(fanout, trace, party);
        var conditions = api.conditions().registry();
        condition(conditions, "storyfact", instruction -> {
            var name = instruction.string().get();
            var expected = instruction.string().get();
            return who -> party.facts().test(name.getValue(who), expected.getValue(who));
        });
        condition(conditions, "storystage", instruction -> {
            var stage = instruction.string().get();
            return who -> party.stage().equals(stage.getValue(who));
        });
        conditions.register("storyvoted", (PlayerConditionFactory) instruction -> {
            var topic = instruction.string().get();
            return mainThread(profile -> party.voted(profile.getPlayerUUID(), topic.getValue(profile)));
        });

        var actions = api.actions().registry();
        once.action(actions, "storyfact", instruction -> {
            var name = instruction.string().get();
            var value = instruction.string().get();
            return who -> {
                var fact = name.getValue(who);
                var to = value.getValue(who);
                var cause = cause("storyfact", who);
                if (to.equals("clear")) {
                    party.facts().clear(fact, cause);
                } else if (to.matches("[+-]\\d+")) {
                    party.facts().add(fact, Long.parseLong(to), cause);
                } else {
                    party.facts().set(fact, to, cause);
                }
            };
        });
        once.action(actions, "storystage", instruction -> {
            var stage = instruction.string().get();
            return who -> party.advance(stage.getValue(who), cause("storystage", who));
        });
        once.action(actions, "storyscene", instruction -> {
            var scene = instruction.string().get();
            var then = instruction.string().list().get("then");
            return who -> party.scene(scene.getValue(who), values(then, who), cause("storyscene", who));
        });
        once.action(actions, "storyafter", instruction -> {
            var ticks = instruction.number().get();
            var then = instruction.string().list().get();
            return who -> party.after(ticks.getValue(who).intValue(), then.getValue(who), cause("storyafter", who));
        });
        for (var channel : Soundtrack.Channel.values()) {
            once.action(actions, "story" + channel.key(), instruction -> {
                var track = instruction.string().get();
                return who -> party.soundtrack(channel, track.getValue(who), cause("story" + channel.key(), who));
            });
        }
        once.action(actions, "storysound", instruction -> {
            var sound = instruction.string().get();
            var volume = instruction.number().get("volume", 1);
            var pitch = instruction.number().get("pitch", 1);
            var at = instruction.location().get("at");
            return who -> {
                Optional<Location> where = at.isPresent() ? Optional.of(at.get().getValue(who)) : Optional.empty();
                party.sound(Key.key(sound.getValue(who)), volume.getValue(who).floatValue(), pitch.getValue(who).floatValue(), where);
            };
        });
        once.action(actions, "storyend", instruction -> {
            var ending = instruction.string().get();
            return who -> party.end(ending.getValue(who), cause("storyend", who));
        });
        actions.register("storyvote", (PlayerActionFactory) instruction -> {
            var topic = instruction.string().get();
            var choice = instruction.string().get();
            return new OnlineActionAdapter(onMainThread(profile ->
                    party.vote(profile.getPlayer(), topic.getValue(profile), choice.getValue(profile))));
        });

        api.objectives().registry().register("holdspot", (ObjectiveFactory) (instruction, service) ->
                new HoldObjective(plugin, service, api.profiles(), party, fanout, instruction.location().get(),
                        instruction.number().get(), instruction.number().get()));
        var registry = api.npcs().registry();
        registry.register(StoryNpcs.TYPE, instruction -> new StoryNpcs.Lookup(npcs, instruction.string().get()));
        registry.registerIdentifier(npcs.new Identifier());
        api.bukkit().registerEvents(npcs.new Clicks(api.profiles(), registry));
        BetonQuest.getInstance().getComponentLoader().get(ConversationIORegistry.class).register(HudConversationIO.NAME,
                (conversation, profile) -> new HudConversationIO(plugin, conversation, profile, hud, voices, trace));
        return new Quests(plugin, api, pack, trace, members, fanout);
    }

    private static String cause(String action, @Nullable Profile who) {
        return who == null ? action : action + " (" + who.getProfileName() + ")";
    }

    private static List<String> values(Optional<Argument<List<String>>> list, @Nullable Profile who) throws QuestException {
        return list.isPresent() ? list.get().getValue(who) : List.of();
    }

    /** Registers the actions that act for the whole party, run by a member or by the game itself, once per {@link Fanout}. */
    private record Once(Fanout fanout, Trace trace, Party party) {

        void action(ActionRegistry registry, String name, QuestFunction<Instruction, PartyAction> parse) {
            registry.register(name, (PlayerActionFactory) instruction -> once(name, parse.apply(instruction)),
                    (PlayerlessActionFactory) instruction -> {
                        var action = once(name, parse.apply(instruction));
                        return new PlayerlessAction() {
                            @Override
                            public void execute() throws QuestException {
                                action.execute(null);
                            }

                            @Override
                            public boolean isPrimaryThreadEnforced() {
                                return true;
                            }
                        };
                    });
        }

        private PlayerAction once(String name, PartyAction action) {
            return new PlayerAction() {
                @Override
                public void execute(@Nullable Profile profile) throws QuestException {
                    if (profile != null && !party.playing(profile.getPlayerUUID())) {
                        trace.event("ignored", name + " by " + profile.getProfileName() + ", who does not play");
                    } else if (fanout.first(this)) {
                        action.run(profile);
                    } else {
                        trace.event("once", name + " already acted for the party in " + fanout.trigger());
                    }
                }

                @Override
                public boolean isPrimaryThreadEnforced() {
                    return true;
                }
            };
        }
    }

    private static void condition(ConditionRegistry registry, String name, QuestFunction<Instruction, PartyCondition> parse) {
        registry.register(name, (PlayerConditionFactory) instruction -> {
            var condition = parse.apply(instruction);
            return mainThread(condition::test);
        }, (PlayerlessConditionFactory) instruction -> {
            var condition = parse.apply(instruction);
            return new PlayerlessCondition() {
                @Override
                public boolean check() throws QuestException {
                    return condition.test(null);
                }

                @Override
                public boolean isPrimaryThreadEnforced() {
                    return true;
                }
            };
        });
    }

    private static PlayerCondition mainThread(PartyCondition condition) {
        return new PlayerCondition() {
            @Override
            public boolean check(Profile profile) throws QuestException {
                return condition.test(profile);
            }

            @Override
            public boolean isPrimaryThreadEnforced() {
                return true;
            }
        };
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

    /** The package's actions by name, for the debug command's suggestions. */
    public Set<String> actions() {
        var section = api.packages().getPackage(pack).getConfig().getConfigurationSection("actions");
        return section == null ? Set.of() : new TreeSet<>(section.getKeys(false));
    }

    public boolean has(String action) {
        return api.packages().getPackage(pack).getConfig().contains("actions." + action);
    }

    /** Runs a package action for {@code player} alone, as {@code /story run <action> <player>} does. */
    public void run(Player player, String action, String cause) {
        if (!has(action)) return;
        trace.event("action", action + " for " + player.getName() + " by " + cause);
        run(api.profiles().getProfile(player), action);
    }

    /** Runs a package action for each of {@code players} as one trigger, in which every party action acts once. */
    public void runEach(List<Player> players, String action, String cause) {
        fanout.across(action, () -> players.forEach(player -> run(player, action, cause)));
    }

    /** The party enters another stage, so the triggers of the last one may act again. */
    public void nextStage() {
        fanout.reset();
    }

    /** A jump: every trigger may act again, a held spot's too. */
    public void forget() {
        fanout.clear();
    }

    /** Runs a package action once for the party: as its first member who is here, or as nobody if none is. */
    public void runParty(String action, String cause) {
        if (!has(action)) {
            if (!action.startsWith("enter_") && !action.startsWith("decided_")) trace.event("missing", "action " + action + " by " + cause);
            return;
        }
        trace.event("action", action + " by " + cause);
        fanout.forParty(() -> run(members.get().stream().findFirst().map(api.profiles()::getProfile).orElse(null), action));
    }

    private void run(@Nullable Profile profile, String action) {
        try {
            var id = api.identifiers().getFactory(ActionIdentifier.class).parseIdentifier(null, pack + ">" + action);
            if (!api.actions().manager().run(profile, id)) trace.event("failed", "action " + action);
        } catch (QuestException | RuntimeException failure) {
            // A broken action must not keep the other members or enter_<stage> from running.
            trace.event("failed", "action " + action + ": " + failure.getMessage());
            plugin.getSLF4JLogger().warn("BetonQuest action {}>{} failed", pack, action, failure);
        }
    }
}
