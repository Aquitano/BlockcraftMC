package me.blockcraft.system;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** The {@code [Annehmen] [Ablehnen]} pair under a friend, party or duel request; both are proxy commands. */
public final class RequestButtons {

    private RequestButtons() {
    }

    /** {@code line} is a key whose {@code <buttons>} the pair fills, so each request keeps its own prefix. */
    public static Component line(Messages messages, String line, Answer accept, Answer decline) {
        return messages.get(line, Placeholder.component("buttons", Component.join(JoinConfiguration.spaces(),
                accept.button(messages.get("request.accept")),
                decline.button(messages.get("request.decline")))));
    }

    public record Answer(String command, Component hover) {

        private Component button(Component label) {
            return label.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(hover));
        }
    }
}
