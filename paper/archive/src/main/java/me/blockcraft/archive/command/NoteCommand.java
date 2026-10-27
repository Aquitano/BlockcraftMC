package me.blockcraft.archive.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.world.Point;
import me.blockcraft.archive.exhibit.ExhibitWorlds;
import me.blockcraft.archive.note.NoteDialog;
import me.blockcraft.archive.note.NoteRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.player.PlayerReference;
import org.bukkit.entity.Player;

/** {@code /archive note}: staff leave a note where they stand in an exhibit, for everyone who comes later. */
public final class NoteCommand {

    public static final String PERMISSION = "blockcraft.archive.note";

    private NoteCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, ExhibitWorlds worlds, NoteRepository notes,
                                                                int maxLength) {
        return Commands.literal("archive")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(PERMISSION))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("note.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("note").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    write(player, messages, worlds, notes, maxLength);
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private static void write(Player player, Messages messages, ExhibitWorlds worlds, NoteRepository notes, int maxLength) {
        var exhibit = worlds.exhibitOf(player.getWorld());
        if (exhibit.isEmpty()) {
            player.sendMessage(messages.get("note.not-in-exhibit"));
            return;
        }
        var key = exhibit.get().key();
        var at = Point.of(player.getLocation());
        var author = PlayerReference.known(player.getUniqueId(), BlockcraftApi.get().realName(player));
        new NoteDialog(messages, exhibit.get().name(), maxLength, "", (writer, text) ->
                BlockcraftApi.get().playerTasks().write(writer, () -> notes.add(key, at, author, text), note -> {
                    worlds.show(note);
                    Sounds.NOTIFY.play(writer);
                    writer.sendMessage(messages.get("note.left"));
                })).open(player);
    }
}
