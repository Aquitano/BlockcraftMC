package me.blockcraft.system.rank;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;

import java.util.Optional;
import java.util.UUID;

/** Rank display on the proxy comes from LuckPerms group meta; prefixes are typed by humans, hence legacy {@code &} codes. */
public final class ProxyRanks {

    private ProxyRanks() {
    }

    /** Prefix plus name as chat shows it, or just the name without LuckPerms. */
    public static Component displayName(Player player) {
        var name = Component.text(player.getUsername());
        try {
            String prefix = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
            return prefix == null ? name : LegacyComponentSerializer.legacyAmpersand().deserialize(prefix).append(name);
        } catch (IllegalStateException | NoClassDefFoundError noLuckPerms) {
            return name;
        }
    }

    /** The primary group, for players who are offline too; empty without LuckPerms. */
    public static Optional<String> primaryGroup(UUID player) {
        try {
            return Optional.of(LuckPermsProvider.get().getUserManager().loadUser(player).join().getPrimaryGroup());
        } catch (IllegalStateException | NoClassDefFoundError noLuckPerms) {
            return Optional.empty();
        }
    }

    /** The heaviest inherited group decides, like the tab list sorting; equal weight does not outrank. */
    public static boolean outranks(Player actor, UUID target) {
        try {
            return weight(actor.getUniqueId()) > weight(target);
        } catch (IllegalStateException | NoClassDefFoundError noLuckPerms) {
            return true;
        }
    }

    private static int weight(UUID player) {
        var user = LuckPermsProvider.get().getUserManager().loadUser(player).join();
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .mapToInt(group -> group.getWeight().orElse(0))
                .max()
                .orElse(0);
    }
}
