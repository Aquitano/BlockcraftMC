package me.blockcraft.system;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Locale;

/** The follow-ups staff click on a line about a player; all of them are proxy commands, so a click works on any server. */
public final class StaffButtons {

    public enum Button { GOTO, WHOIS, ALTS }

    private final Messages messages;

    public StaffButtons(Messages messages) {
        this.messages = messages;
    }

    public Component name(String player) {
        return Component.text(player)
                .clickEvent(ClickEvent.runCommand("/whois " + player))
                .hoverEvent(HoverEvent.showText(messages.get("staff-buttons.name-hover", Placeholder.unparsed("player", player))));
    }

    /** Each button is led by a space, so the result goes straight after the notice. */
    public Component of(String player, Button... buttons) {
        var name = Placeholder.unparsed("player", player);
        var row = Component.text();
        for (var button : buttons) {
            String command = button.name().toLowerCase(Locale.ROOT);
            row.append(Component.space(), messages.get("staff-buttons." + command)
                    .clickEvent(ClickEvent.runCommand("/" + command + " " + player))
                    .hoverEvent(HoverEvent.showText(messages.get("staff-buttons." + command + "-hover", name))));
        }
        return row.build();
    }
}
