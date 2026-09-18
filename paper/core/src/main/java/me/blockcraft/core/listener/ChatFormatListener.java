package me.blockcraft.core.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.core.nick.Nicks;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Level, rank prefix, optional chat tag, nick-aware name (staff see the real one too) and premium colour codes. */
public final class ChatFormatListener implements Listener {

    private final Messages messages;
    private final Nicks nicks;

    public ChatFormatListener(Messages messages, Nicks nicks) {
        this.messages = messages;
        this.nicks = nicks;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var source = event.getPlayer();
        var api = BlockcraftApi.get();
        var tag = api.settings().get(source, PlayerSetting.CHAT_TAG).flatMap(ChatTag::byKey)
                .map(chosen -> api.messages().get(chosen.messageKey()).append(Component.space()))
                .orElse(Component.empty());
        var rank = Ranks.prefix(source).orElse(Component.empty());
        int level = api.nickStats().level(source, api.xp().level(source));
        var levelBadge = messages.get("chat.level", Formatter.number("level", level));
        // The proxy resolves the nick, so the hover and the /msg it suggests both go by the name others see.
        String shown = nicks.of(source).map(Nicks.Active::nick).orElseGet(source::getName);
        var hover = HoverEvent.showText(Component.join(JoinConfiguration.newlines(), messages.getLines("chat.hover",
                Placeholder.component("rank", rank),
                Placeholder.unparsed("name", shown),
                Formatter.number("level", level))));
        var click = ClickEvent.suggestCommand(PlainTextComponentSerializer.plainText()
                .serialize(messages.get("chat.msg-command", Placeholder.unparsed("name", shown))));
        event.renderer((ChatRenderer) (player, displayName, message, viewer) -> messages.get("chat.format",
                Placeholder.component("level", levelBadge),
                Placeholder.component("tag", tag),
                Placeholder.component("rank", rank.hoverEvent(hover).clickEvent(click)),
                Placeholder.component("name", name(player, displayName, viewer).hoverEvent(hover).clickEvent(click)),
                Placeholder.component("message", colour(player, message))));
    }

    private Component name(Player source, Component displayName, Audience viewer) {
        var active = nicks.of(source);
        if (active.isEmpty()) {
            return displayName;
        }
        var shown = Component.text(active.get().nick());
        boolean seesReal = viewer instanceof Player watcher && watcher.hasPermission(ChatPermissions.NICK_SEE);
        return seesReal
                ? shown.append(messages.get("chat.nick-real", Placeholder.unparsed("real", active.get().realName())))
                : shown;
    }

    private static Component colour(Player source, Component message) {
        if (!source.hasPermission(ChatPermissions.CHAT_COLOR)) {
            return message;
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(PlainTextComponentSerializer.plainText().serialize(message));
    }
}
