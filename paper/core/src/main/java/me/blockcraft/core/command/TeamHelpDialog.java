package me.blockcraft.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

final class TeamHelpDialog {

    private static final int PAGE_SIZE = 10;

    private final CommandSourceStack source;
    private final Messages messages;

    TeamHelpDialog(CommandSourceStack source, Messages messages) {
        this.source = source;
        this.messages = messages;
    }

    void open(Player player, int requestedPage) {
        var commands = CommandRegistry.usableBy(source);
        int pages = Math.max(1, (commands.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        var shared = BlockcraftApi.get().messages();
        var buttons = new ArrayList<ActionButton>();
        commands.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE).forEach(entry -> buttons.add(
                ActionButton.builder(Component.text("/" + entry.name())).tooltip(entry.description())
                        .action(DialogAction.staticAction(ClickEvent.suggestCommand("/" + entry.name() + " "))).build()));
        if (page > 0) {
            buttons.add(Dialogs.button(shared.get("menu.previous"), viewer -> open(viewer, page - 1)));
        }
        if (page + 1 < pages) {
            buttons.add(Dialogs.button(shared.get("menu.next"), viewer -> open(viewer, page + 1)));
        }
        var body = List.of(DialogBody.plainMessage(messages.get("team.help-body")),
                DialogBody.plainMessage(shared.get("menu.page", Formatter.number("page", page + 1), Formatter.number("pages", pages))));
        Dialogs.show(player, Dialogs.create(messages.get("team.help-title"), body, List.of(),
                        Dialogs.actions(buttons, Dialogs.button(shared.get("menu.close")))),
                () -> player.hasPermission("blockcraft.core.team"));
    }
}
