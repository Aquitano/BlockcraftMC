package me.blockcraft.core.Commands;

import me.blockcraft.core.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class FlySpeedCommand implements CommandExecutor {
    public final ArrayList<Player> speed;

    public FlySpeedCommand() {
        this.speed = new ArrayList<>();
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (p.hasPermission("bs.fly") && args.length == 0) {
            if (this.speed.contains(p)) {
                p.setFlySpeed(0.1f);
                p.sendMessage(Main.Prefix + "\u00a77Dein Flyspeed wurde \u00a7fdeaktiviert\u00a77!");
                this.speed.remove(p);
            } else {
                p.setFlySpeed(0.5f);
                p.sendMessage(Main.Prefix + "\u00a77Dein Flyspeed wurde \u00a7faktiviert\u00a77!");
                this.speed.add(p);
            }
        }
        return false;
    }
}
