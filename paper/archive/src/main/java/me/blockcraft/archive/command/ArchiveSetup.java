package me.blockcraft.archive.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.world.Point;
import me.blockcraft.archive.exhibit.Checkpoint;
import me.blockcraft.archive.exhibit.Exhibit;
import me.blockcraft.archive.exhibit.ExhibitPool;
import me.blockcraft.archive.exhibit.ExhibitWorlds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The archive's /setup, taken standing in an exhibit: {@code spawn} puts its arrival here, {@code point add} a
 * checkpoint, whose description a dialog asks for, and {@code point remove} takes one away. Every change lands in the
 * exhibit's {@code exhibit.yml} inside the running map bundle at once, and {@code /map save} keeps it.
 */
public final class ArchiveSetup {

    private static final int MAX_NAME_LENGTH = 32;

    private final ExhibitPool pool;
    private final ExhibitWorlds worlds;
    private final Messages messages;

    private ArchiveSetup(ExhibitPool pool, ExhibitWorlds worlds, Messages messages) {
        this.pool = pool;
        this.worlds = worlds;
        this.messages = messages;
    }

    public static LiteralCommandNode<CommandSourceStack> create(ExhibitPool pool, ExhibitWorlds worlds, Messages messages) {
        return new ArchiveSetup(pool, worlds, messages).tree();
    }

    private LiteralCommandNode<CommandSourceStack> tree() {
        return SetupCommand.root()
                .executes(context -> help(player(context)))
                .then(Commands.literal("spawn").executes(context -> spawn(player(context))))
                .then(Commands.literal("point")
                        .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> add(player(context), name(context)))))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    if (context.getSource().getSender() instanceof Player player) {
                                        worlds.exhibitOf(player.getWorld()).ifPresent(exhibit -> exhibit.checkpoints().stream()
                                                .map(Checkpoint::name)
                                                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                                .forEach(builder::suggest));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> remove(player(context), name(context))))))
                .build();
    }

    private int spawn(Player player) {
        var here = Point.of(player.getLocation());
        return change(player, exhibit -> save(player, exhibit.withArrival(here), "setup.spawn-set", exhibit.name()));
    }

    private int add(Player player, String name) {
        if (name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            player.sendMessage(messages.get("setup.invalid-name"));
            return Command.SINGLE_SUCCESS;
        }
        var here = Point.of(player.getLocation());
        return change(player, exhibit -> {
            var draft = exhibit.checkpoint(name).map(Checkpoint::description).orElse("");
            new CheckpointDialog(messages, name, draft, (editor, description) ->
                    pool.exhibit(exhibit.key()).ifPresent(current ->
                            save(editor, current.withCheckpoint(new Checkpoint(name, description, here)), "setup.checkpoint-set", name)))
                    .open(player);
        });
    }

    private int remove(Player player, String name) {
        return change(player, exhibit -> {
            if (exhibit.checkpoint(name).isEmpty()) {
                player.sendMessage(messages.get("setup.unknown-checkpoint", Placeholder.unparsed("checkpoint", name)));
                return;
            }
            save(player, exhibit.withoutCheckpoint(name), "setup.checkpoint-removed", name);
        });
    }

    private int change(Player player, Consumer<Exhibit> edit) {
        worlds.exhibitOf(player.getWorld()).ifPresentOrElse(edit, () -> player.sendMessage(messages.get("setup.not-in-exhibit")));
        return Command.SINGLE_SUCCESS;
    }

    private void save(Player player, Exhibit changed, String messageKey, String what) {
        pool.save(changed);
        player.sendMessage(messages.get(messageKey, Placeholder.unparsed("what", what)));
    }

    private int help(Player player) {
        messages.getLines("setup.help").forEach(player::sendMessage);
        return Command.SINGLE_SUCCESS;
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "name").strip();
    }

    private static Player player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrThrow();
    }
}
