package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.ignore.IgnoreRepository.Ignored;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.rank.ProxyRanks;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** /ignore and /unignore: whom a player no longer hears from in chat, private messages, invites and requests. */
public final class IgnoreCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PlayerVisibility visibility;
    private final IgnoreList ignores;
    private final SystemConfig config;
    private final Messages messages;

    public IgnoreCommand(CommandSupport support, PlayerLookup lookup, PlayerVisibility visibility, IgnoreList ignores,
                         SystemConfig config, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.visibility = visibility;
        this.ignores = ignores;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand ignore() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("ignore")
                .requires(CommandSupport.permission(SystemPermissions.IGNORE))
                .executes(support.usage("ignore.usage"))
                .then(BrigadierCommand.literalArgumentBuilder("list").executes(support.asPlayer((player, context) -> list(player))))
                .then(BrigadierCommand.literalArgumentBuilder("remove")
                        .executes(support.usage("ignore.usage"))
                        .then(support.playerArgument("player").executes(support.asPlayer((player, context) ->
                                remove(player, StringArgumentType.getString(context, "player"))))))
                .then(support.playerArgument("player").executes(support.asPlayer((player, context) ->
                        add(player, StringArgumentType.getString(context, "player"))))));
    }

    public BrigadierCommand unignore() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("unignore")
                .requires(CommandSupport.permission(SystemPermissions.IGNORE))
                .executes(support.usage("ignore.usage"))
                .then(support.playerArgument("player").executes(support.asPlayer((player, context) ->
                        remove(player, StringArgumentType.getString(context, "player"))))));
    }

    private void add(Player player, String name) {
        var found = lookup.findVisible(player, name);
        if (found.isEmpty()) {
            player.sendMessage(messages.get("ignore.unknown", named(name)));
            return;
        }
        var target = found.get();
        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(messages.get("ignore.self"));
            return;
        }
        String shown = target.online().map(online -> visibility.nameFor(player, online)).orElse(target.name());
        if (isUnnickedStaff(target)) {
            player.sendMessage(messages.get("ignore.staff", named(shown)));
            return;
        }
        var list = ignores.list(player);
        if (list.stream().anyMatch(entry -> entry.uuid().equals(target.uuid()))) {
            player.sendMessage(messages.get("ignore.already", named(shown)));
            return;
        }
        if (list.size() >= config.maxIgnored()) {
            player.sendMessage(messages.get("ignore.full", Formatter.number("max", config.maxIgnored())));
            return;
        }
        ignores.add(player, new Ignored(target.uuid(), shown));
        player.sendMessage(messages.get("ignore.added", named(shown)));
    }

    /** The list keeps the name the player was ignored under, so a nick that is gone by now can still be removed. */
    private void remove(Player player, String name) {
        var account = ignores.list(player).stream()
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .map(Ignored::uuid)
                .findFirst()
                .or(() -> lookup.findVisible(player, name).map(PlayerLookup.Target::uuid));
        if (account.isEmpty() || !ignores.remove(player, account.get())) {
            player.sendMessage(messages.get("ignore.not-ignored", named(name)));
            return;
        }
        player.sendMessage(messages.get("ignore.removed", named(name)));
    }

    private void list(Player player) {
        var list = ignores.list(player);
        if (list.isEmpty()) {
            player.sendMessage(messages.get("ignore.list-empty"));
            return;
        }
        player.sendMessage(messages.get("ignore.list-header", Formatter.number("count", list.size()),
                Formatter.number("max", config.maxIgnored())));
        list.forEach(entry -> player.sendMessage(messages.get("ignore.list-entry", named(entry.name()))));
    }

    /** A nick hides the rank, so a nicked team member is ignored like anyone else rather than given away. */
    private boolean isUnnickedStaff(PlayerLookup.Target target) {
        return target.online()
                .map(online -> visibility.nick(online).isEmpty() && online.hasPermission(SystemPermissions.TEAM))
                .orElseGet(() -> ProxyRanks.hasPermission(target.uuid(), SystemPermissions.TEAM));
    }

    private static TagResolver named(String name) {
        return Placeholder.unparsed("player", name);
    }
}
