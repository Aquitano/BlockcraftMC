package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.Main;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class Spectator
        implements Listener {
    public static void add(Player p) {
        Main.online.remove(p);
        Main.spectator.add(p);
        p.setExp(0.0f);
        p.setLevel(0);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().setHelmet(new ItemStack(Material.AIR));
        p.getInventory().setChestplate(new ItemStack(Material.AIR));
        p.getInventory().setLeggings(new ItemStack(Material.AIR));
        p.getInventory().setBoots(new ItemStack(Material.AIR));
        p.setGameMode(GameMode.SPECTATOR);
        p.setGameMode(GameMode.SPECTATOR);
        p.setAllowFlight(true);
        p.setFlying(true);
    }
}

