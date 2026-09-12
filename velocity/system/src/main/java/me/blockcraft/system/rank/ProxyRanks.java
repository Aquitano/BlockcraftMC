package me.blockcraft.system.rank;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;

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
}
