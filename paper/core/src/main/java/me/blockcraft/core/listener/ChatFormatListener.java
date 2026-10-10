package me.blockcraft.core.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
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
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Level, rank prefix, optional chat tag, nick-aware name (staff see the real one too) and premium colour codes; a
 * player who ignores the sender does not get the line at all, and one whose name the line mentions sees it
 * highlighted and hears it.
 */
public final class ChatFormatListener implements Listener {

    private final Plugin plugin;
    private final Messages messages;
    private final Nicks nicks;

    public ChatFormatListener(Plugin plugin, Messages messages, Nicks nicks) {
        this.plugin = plugin;
        this.messages = messages;
        this.nicks = nicks;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var source = event.getPlayer();
        var api = BlockcraftApi.get();
        event.viewers().removeIf(viewer -> viewer instanceof Player watcher && api.ignores().ignores(watcher, source));
        var tag = api.settings().get(source, PlayerSetting.CHAT_TAG).flatMap(ChatTag::byKey)
                .map(chosen -> api.messages().get(chosen.messageKey()).append(Component.space()))
                .orElse(Component.empty());
        var rank = Ranks.prefix(source).orElse(Component.empty());
        int level = api.nickStats().level(source, api.xp().level(source));
        var levelBadge = messages.get("chat.level", Formatter.number("level", level));
        // The proxy resolves the nick, so the hover and the /msg it suggests both go by the name others see.
        String shown = shownName(source);
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
                Placeholder.component("message", rendered(player, colour(player, message), viewer))));
    }

    /**
     * The sound goes out from here because Paper renders once per viewer who finally gets the line, after every
     * listener had its chance to cancel it or narrow its audience.
     */
    private Component rendered(Player source, Component message, Audience viewer) {
        if (!(viewer instanceof Player watcher)) {
            return message;
        }
        var highlighted = highlight(source, message, watcher);
        if (highlighted.isEmpty()) {
            return message;
        }
        Tasks.sync(plugin, () -> Sounds.NOTIFY.play(watcher));
        return highlighted.get();
    }

    /** Matches the name others see, so a nicked viewer is never mentioned, and so revealed, by their real one. */
    private Optional<Component> highlight(Player source, Component message, Player viewer) {
        if (viewer.equals(source) || !BlockcraftApi.get().settings().enabled(viewer, PlayerSetting.MENTIONS)) {
            return Optional.empty();
        }
        return Mentions.highlight(message, shownName(viewer),
                written -> messages.get("chat.mention", Placeholder.unparsed("mention", written)));
    }

    private String shownName(Player player) {
        return nicks.of(player).map(Nicks.Active::nick).orElseGet(player::getName);
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
