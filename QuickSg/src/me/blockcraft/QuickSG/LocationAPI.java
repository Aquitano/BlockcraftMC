package me.blockcraft.QuickSG;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class LocationAPI
        implements Listener {
    private static Main pl;

    public LocationAPI(Main main) {
        pl = main;
    }

    public static Location getLocation(String LocationName) {
        double x = pl.getConfig().getDouble(LocationName + ".X");
        double y = pl.getConfig().getDouble(LocationName + ".Y");
        double z = pl.getConfig().getDouble(LocationName + ".Z");
        double yaw = pl.getConfig().getDouble(LocationName + ".Yaw");
        double pitch = pl.getConfig().getDouble(LocationName + ".Pitch");
        String World2 = pl.getConfig().getString(LocationName + ".World");
        World w = Bukkit.getWorld(World2);
        return new Location(w, x, y, z, (float) yaw, (float) pitch);
    }

    public static void setLocation(String LocationName, Player p) {
        pl.getConfig().set(LocationName + ".X", p.getLocation().getBlockX());
        pl.getConfig().set(LocationName + ".Y", p.getLocation().getBlockY());
        pl.getConfig().set(LocationName + ".Z", p.getLocation().getBlockZ());
        pl.getConfig().set(LocationName + ".Yaw", p.getLocation().getYaw());
        pl.getConfig().set(LocationName + ".Pitch", p.getLocation().getPitch());
        pl.getConfig().set(LocationName + ".World", p.getWorld().getName());
        pl.saveConfig();
    }
}

