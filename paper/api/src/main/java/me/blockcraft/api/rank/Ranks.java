package me.blockcraft.api.rank;

import me.blockcraft.api.BlockcraftApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Rank display comes from LuckPerms group meta; prefixes are typed by humans, hence legacy {@code &} codes. What a
 * player shows is their own rank or, while nicked, the rank of the group the nick wears; what they may do is
 * always their own.
 */
public final class Ranks {

    private Ranks() {
    }

    public static Optional<Component> prefix(Player player) {
        var luckPerms = LuckPermsProvider.get();
        var meta = BlockcraftApi.get().nickRank(player)
                .map(luckPerms.getGroupManager()::getGroup)
                .map(worn -> worn.getCachedData().getMetaData())
                .orElseGet(() -> luckPerms.getPlayerAdapter(Player.class).getMetaData(player));
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
