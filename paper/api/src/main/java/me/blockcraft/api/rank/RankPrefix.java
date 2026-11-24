package me.blockcraft.api.rank;

import me.blockcraft.api.BlockcraftApi;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** A rank in both forms: the LuckPerms prefix text, and the group's badge glyph, which only the pack draws. */
public record RankPrefix(Component text, Component badge) {

    public Component shownTo(Audience viewer) {
        return viewer instanceof Player player && BlockcraftApi.get().resourcePack().applied(player) ? badge : text;
    }
}
