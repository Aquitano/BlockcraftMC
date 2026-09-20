package me.blockcraft.api.coins;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Credits a player off the main thread and acknowledges a successful write. */
public final class Coins {

    private Coins() {
    }

    public static void award(Plugin plugin, Player player, int amount) {
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        var api = BlockcraftApi.get();
        Tasks.async(plugin, () -> {
            api.coins().add(uuid, name, amount);
            return amount;
        }, credited -> {
            if (player.isOnline()) {
                api.notifications().rewardSound(player, Sounds.COINS);
            }
        });
    }
}
