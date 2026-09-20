package me.blockcraft.lobby.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PremiumRank {

    private static final String GROUP = "premium";

    private final Plugin plugin;
    private final LuckPerms luckPerms;

    public PremiumRank(Plugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /** The purchase is already recorded in bc_unlocks, so a failed grant is logged and healed on the next click. */
    public CompletableFuture<Boolean> grant(UUID player) {
        return luckPerms.getUserManager().modifyUser(player, user -> user.data().add(InheritanceNode.builder(GROUP).build()))
                .handle((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not grant the premium group to " + player, error);
                    }
                    return error == null;
                });
    }
}
