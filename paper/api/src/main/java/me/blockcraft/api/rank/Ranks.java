package me.blockcraft.api.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Rank display comes from LuckPerms group meta; prefixes are typed by humans, hence legacy {@code &} codes. */
public final class Ranks {

    private Ranks() {
    }

    public static Optional<Component> prefix(Player player) {
        var meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
        return Optional.ofNullable(meta.getPrefix()).map(LegacyComponentSerializer.legacyAmpersand()::deserialize);
    }

    /** The heaviest inherited group decides, like the tab list sorting; equal weight does not outrank. */
    public static boolean outranks(Player actor, Player target) {
        return weight(actor) > weight(target);
    }

    private static int weight(Player player) {
        var user = LuckPermsProvider.get().getPlayerAdapter(Player.class).getUser(player);
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .mapToInt(group -> group.getWeight().orElse(0))
                .max()
                .orElse(0);
    }
}
