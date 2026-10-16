package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;

/** Puts a player into the LuckPerms premium group. */
public final class AddPremiumCommand {

    private static final String LUCKPERMS_ID = "luckperms";
    private static final String PREMIUM_GROUP = "premium";

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final PlayerLookup lookup;
    private final Messages messages;

    public AddPremiumCommand(CommandSupport support, ProxyServer proxy, PlayerLookup lookup, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.lookup = lookup;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("addpremium")
                .requires(CommandSupport.permission(SystemPermissions.ADD_PREMIUM))
                .executes(support.usage("addpremium.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> add(source, StringArgumentType.getString(context, "player"))))));
    }

    private void add(CommandSource source, String name) {
        if (!proxy.getPluginManager().isLoaded(LUCKPERMS_ID)) {
            source.sendMessage(messages.get("addpremium.unavailable"));
            return;
        }
        lookup.find(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name))).ifPresent(target -> grant(source, target));
    }

    /** Touches LuckPerms classes only here, so the command still loads without the plugin. */
    private void grant(CommandSource source, Target target) {
        LuckPermsProvider.get().getUserManager()
                .modifyUser(target.uuid(), user -> user.data().add(InheritanceNode.builder(PREMIUM_GROUP).build()))
                .thenRun(() -> {
                    source.sendMessage(messages.get("addpremium.done"));
                    boolean granted = target.online().map(player -> player.hasPermission(SystemPermissions.PREMIUM_GROUP)).orElse(true);
                    if (granted) {
                        source.sendMessage(messages.get("addpremium.has-rights", Placeholder.unparsed("player", target.name())));
                    }
                })
                .exceptionally(failure -> {
                    source.sendMessage(messages.get("addpremium.failed", Placeholder.unparsed("player", target.name())));
                    return null;
                });
    }
}
