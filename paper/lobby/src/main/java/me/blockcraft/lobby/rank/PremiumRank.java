package me.blockcraft.lobby.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.UUID;

public final class PremiumRank {

    private static final String GROUP = "premium";

    private final LuckPerms luckPerms;

    public PremiumRank(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public void grant(UUID player) {
        luckPerms.getUserManager().modifyUser(player, user -> user.data().add(InheritanceNode.builder(GROUP).build()));
    }
}
