package me.blockcraft.endergames.kit;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Who fights the round and with which kit, as far as the abilities need to know. */
public interface KitPlayers {

    /** The kit of a player who fights the running round; empty in the lobby, at the end and for spectators. */
    Optional<Kit> activeKit(Player player);

    List<Player> fighting();
}
