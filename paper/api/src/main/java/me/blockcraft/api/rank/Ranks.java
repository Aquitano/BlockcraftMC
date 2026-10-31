package me.blockcraft.api.rank;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.UiFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Rank display comes from LuckPerms group meta; prefixes are typed by humans, hence legacy {@code &} codes. A group's
 * {@code badge} meta is a glyph of the pack's font that stands in for the prefix where the pack is applied. What a
 * player shows is their own rank or, while nicked, the rank of the group the nick wears; what they may do is
 * always their own.
 */
public final class Ranks {

    private static final String BADGE = "badge";

    private Ranks() {
    }

    public static Optional<RankPrefix> prefix(Player player) {
        var luckPerms = LuckPermsProvider.get();
        var meta = BlockcraftApi.get().nickRank(player)
                .map(luckPerms.getGroupManager()::getGroup)
                .map(worn -> worn.getCachedData().getMetaData())
                .orElseGet(() -> luckPerms.getPlayerAdapter(Player.class).getMetaData(player));
        return Optional.ofNullable(meta.getPrefix()).map(prefix -> {
            var text = LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
            var badge = Optional.ofNullable(meta.getMetaValue(BADGE))
                    .<Component>map(glyph -> Component.textOfChildren(
                            Component.text(glyph).font(UiFont.KEY).color(NamedTextColor.WHITE), Component.space()))
                    .orElse(text);
            return new RankPrefix(text, badge);
        });
    }

    /** The account's own primary group, whatever rank a nick shows; for staff eyes. */
    public static String group(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getUser(player).getPrimaryGroup();
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
