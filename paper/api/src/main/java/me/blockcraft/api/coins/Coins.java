package me.blockcraft.api.coins;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Credits an online player: the write goes off the main thread, the coin sound plays right away. */
public final class Coins {

    private Coins() {
    }

    public static void award(Plugin plugin, Player player, int amount) {
        Sounds.COINS.play(player);
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        Tasks.async(plugin, () -> BlockcraftApi.get().coins().add(uuid, name, amount));
    }
}
