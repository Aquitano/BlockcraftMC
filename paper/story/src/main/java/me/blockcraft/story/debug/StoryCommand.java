package me.blockcraft.story.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.story.game.Stage;
import me.blockcraft.story.game.StoryGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /story}, for operators and the console alike, so a person in game and a script over RCON debug the same way:
 * <ul>
 *     <li>{@code state [json]}, {@code log [count]}, {@code trace} and {@code hud} show what the story is doing,</li>
 *     <li>{@code jump <stage> [fact=value ...]} puts the party at a stage, from the waiting lobby too,</li>
 *     <li>{@code fact <name> [value|+n|-n|clear]} reads or changes a fact,</li>
 *     <li>{@code run <action> [player]} runs a package action for the party, or for one member,</li>
 *     <li>{@code scene <name> [from tick]} previews a cutscene, {@code scene stop} ends it,</li>
 *     <li>{@code skip} ends whatever the party waits on, and {@code reload} reads config.yml again.</li>
 * </ul>
 */
public final class StoryCommand {

    private static final String PERMISSION = "blockcraft.story.debug";

    private final StoryGame game;
    private final DebugBoard board;

    public StoryCommand(StoryGame game, DebugBoard board) {
        this.game = game;
        this.board = board;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("story")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> state(context, false))
                .then(Commands.literal("state")
                        .executes(context -> state(context, false))
                        .then(Commands.literal("json").executes(context -> state(context, true))))
                .then(Commands.literal("log")
                        .executes(context -> log(context, 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> log(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("trace").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    reply(context, game.trace().toggle(player) ? "Trace on" : "Trace off");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("hud").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    reply(context, board.toggle(player) ? "Sidebar on" : "Sidebar off");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(jump())
                .then(fact())
                .then(run())
                .then(scene())
                .then(Commands.literal("skip").executes(context -> {
                    var skipped = game.skip();
                    reply(context, skipped.isEmpty() ? "Nothing to skip" : "Skipped the " + skipped);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(context -> {
                    reply(context, "Reloaded " + game.reload() + "; run /bq reload for the quest package");
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> jump() {
        return Commands.literal("jump")
                .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(suggest(() -> game.stages().stream().map(Stage::name).toList()))
                        .executes(context -> jump(context, ""))
                        .then(Commands.argument("facts", StringArgumentType.greedyString())
                                .executes(context -> jump(context, StringArgumentType.getString(context, "facts")))));
    }

    private int jump(CommandContext<CommandSourceStack> context, String text) {
        var overrides = new LinkedHashMap<String, String>();
        for (var pair : text.trim().split("\\s+")) {
            if (pair.isEmpty()) continue;
            var parts = pair.split("=", 2);
            if (parts.length != 2) {
                reply(context, "Facts are written name=value, not " + pair);
                return 0;
            }
            overrides.put(parts[0], parts[1]);
        }
        var stage = StringArgumentType.getString(context, "stage");
        var refused = game.jump(stage, overrides, "/story jump (" + context.getSource().getSender().getName() + ")");
        reply(context, refused.orElse("Jumped to " + stage));
        return refused.isEmpty() ? Command.SINGLE_SUCCESS : 0;
    }

    private LiteralArgumentBuilder<CommandSourceStack> fact() {
        return Commands.literal("fact")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggest(() -> List.copyOf(game.facts().all().keySet())))
                        .executes(context -> {
                            var name = StringArgumentType.getString(context, "name");
                            reply(context, name + " = " + game.facts().get(name).orElse("(not set)"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("value", StringArgumentType.word()).executes(context -> {
                            var name = StringArgumentType.getString(context, "name");
                            var value = StringArgumentType.getString(context, "value");
                            var cause = "/story fact (" + context.getSource().getSender().getName() + ")";
                            if (value.equals("clear")) {
                                game.facts().clear(name, cause);
                            } else if (value.matches("[+-]\\d+")) {
                                game.facts().add(name, Long.parseLong(value), cause);
                            } else {
                                game.facts().set(name, value, cause);
                            }
                            reply(context, name + " = " + game.facts().get(name).orElse("(not set)"));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> run() {
        return Commands.literal("run")
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests(suggest(() -> List.copyOf(game.quests().actions())))
                        .executes(context -> {
                            var action = StringArgumentType.getString(context, "action");
                            if (!game.quests().has(action)) return unknownAction(context, action);
                            game.quests().runParty(action, "/story run (" + context.getSource().getSender().getName() + ")");
                            reply(context, "Ran " + action);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(suggest(() -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()))
                                .executes(context -> {
                                    var action = StringArgumentType.getString(context, "action");
                                    if (!game.quests().has(action)) return unknownAction(context, action);
                                    var player = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
                                    if (player == null) {
                                        reply(context, "That player is not online");
                                        return 0;
                                    }
                                    game.quests().run(player, action, "/story run (" + context.getSource().getSender().getName() + ")");
                                    reply(context, "Ran " + action + " for " + player.getName());
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private int unknownAction(CommandContext<CommandSourceStack> context, String action) {
        reply(context, "The quest package has no action " + action);
        return 0;
    }

    private LiteralArgumentBuilder<CommandSourceStack> scene() {
        return Commands.literal("scene")
                .then(Commands.literal("stop").executes(context -> {
                    game.stopPreviews();
                    reply(context, "Stopped the previews; /story skip ends the party's scene");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggest(() -> List.copyOf(game.cutscenes().keySet())))
                        .executes(context -> scene(context, 0))
                        .then(Commands.argument("from", IntegerArgumentType.integer(0))
                                .executes(context -> scene(context, IntegerArgumentType.getInteger(context, "from")))));
    }

    /** A player watches alone; from the console the whole party watches. */
    private int scene(CommandContext<CommandSourceStack> context, int from) {
        var name = StringArgumentType.getString(context, "name");
        List<Player> viewers = context.getSource().getSender() instanceof Player player ? List.of(player) : game.members();
        Optional<String> refused = viewers.isEmpty() ? Optional.of("Nobody is here to watch") : game.preview(name, from, viewers);
        reply(context, refused.orElse("Playing " + name + " from tick " + from));
        return refused.isEmpty() ? Command.SINGLE_SUCCESS : 0;
    }

    private int state(CommandContext<CommandSourceStack> context, boolean json) {
        var state = StoryState.of(game);
        if (json) {
            context.getSource().getSender().sendMessage(state.json());
        } else {
            state.lines().forEach(line -> reply(context, line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int log(CommandContext<CommandSourceStack> context, int count) {
        var sender = context.getSource().getSender();
        game.trace().last(count).forEach(event -> sender.sendMessage(Component.text(event.line(), NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Supplier<Collection<String>> options) {
        return (context, builder) -> {
            options.get().stream().filter(option -> option.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static void reply(CommandContext<CommandSourceStack> context, String text) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }
}
