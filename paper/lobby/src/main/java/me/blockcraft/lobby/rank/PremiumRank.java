package me.blockcraft.lobby.rank;

import me.blockcraft.common.shop.Premium;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PremiumRank {

    private final Plugin plugin;
    private final LuckPerms luckPerms;

    public PremiumRank(Plugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /**
     * The purchase is already recorded in bc_unlocks, so a failed grant is logged and healed on the next click. The
     * proxy and the other servers keep their copy of the player until they hear of the change.
     */
    public CompletableFuture<Boolean> grant(UUID player) {
        var users = luckPerms.getUserManager();
        return users.loadUser(player)
                .thenCompose(user -> {
                    user.data().add(InheritanceNode.builder(Premium.GROUP).build());
                    return users.saveUser(user).thenRun(() -> luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user)));
                })
                .handle((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not grant the premium group to " + player, error);
                    }
                    return error == null;
                });
    }
}
