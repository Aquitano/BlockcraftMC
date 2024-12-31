package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;

public class SpeedCommand implements CommandExecutor {
    public final ArrayList<Player> speed;

    public SpeedCommand() {
        this.speed = new ArrayList<>();
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 0 && p.hasPermission("bs.speed")) {
            if (this.speed.contains(p)) {
                this.speed.remove(p);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.sendMessage(Main.Prefix + "\u00a77Speed wurde \u00a7fdeaktiviert\u00a77!");
            } else {
                this.speed.add(p);
                p.sendMessage(Main.Prefix + "\u00a77Speed wurde \u00a7faktivert\u00a77!");
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20000000, 3));
            }
        }
        return false;
    }
}
