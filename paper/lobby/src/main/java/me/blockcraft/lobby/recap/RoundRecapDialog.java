package me.blockcraft.lobby.recap;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.round.RoundRecap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The round the player just came from: the result over the game's icon, the numbers below, and the way back in. */
final class RoundRecapDialog {

    private final Dialog dialog;
    private final BooleanSupplier eligible;

    RoundRecapDialog(Plugin plugin, Messages messages, Player viewer, RoundRecap recap, Component game,
                     BooleanSupplier eligible) {
        this.eligible = eligible;
        var result = messages.get("recap." + resultKey(recap));
        var icon = MenuIcon.byKey(recap.game()).map(MenuIcon::item)
                .orElseGet(() -> Items.of(Material.IRON_SWORD).hideAttributes())
                .name(game)
                .build();
        MenuIcon.style(icon, BlockcraftApi.get().resourcePack().applied(viewer));
        var lines = new ArrayList<Component>();
        lines.add(recap.winners().isEmpty()
                ? messages.get("recap.winner-none")
                : messages.get("recap.winner", Placeholder.unparsed("winner", recap.winners())));
        lines.add(messages.get("recap.kills", Formatter.number("kills", recap.kills())));
        lines.add(messages.get("recap.deaths", Formatter.number("deaths", recap.deaths())));
        if (recap.bestStreak() > 1) {
            lines.add(messages.get("recap.streak", Formatter.number("streak", recap.bestStreak())));
        }
        lines.add(messages.get("recap.coins", Formatter.number("coins", recap.coins())));
        lines.add(messages.get("recap.xp", Formatter.number("xp", recap.xp())));
        dialog = Dialogs.create(messages.get("recap.title", Placeholder.component("game", game)),
                List.of(
                        DialogBody.item(icon).description(DialogBody.plainMessage(result)).build(),
                        DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(), lines))),
                List.of(),
                Dialogs.actions(List.of(Dialogs.button(messages.get("recap.again"),
                                player -> {
                                    if (player == viewer && eligible.getAsBoolean()) {
                                        ProxyMessaging.play(plugin, player, recap.game());
                                    }
                                })),
                        Dialogs.button(messages.get("recap.close"))));
    }

    void open(Player player) {
        Dialogs.show(player, dialog, eligible);
    }

    private static String resultKey(RoundRecap recap) {
        if (recap.won()) {
            return "result.win";
        }
        return recap.winners().isEmpty() ? "result.draw" : "result.lose";
    }
}
