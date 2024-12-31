package me.blockcraft.lobby.utils;

import me.blockcraft.lobby.Lobby;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

public class PetManager implements Listener {
    public static Lobby main;

    public PetManager(final Lobby main) {
        PetManager.main = main;
    }

    public static void spawnRabbitPet(final Player p) {
        final Rabbit pet = (Rabbit) p.getWorld().spawnEntity(p.getLocation(), EntityType.RABBIT);
        pet.setCustomName(p.getName() + "'s Haustier");
        pet.setCustomNameVisible(true);
        pet.setLeashHolder(p);
        pet.setRemoveWhenFarAway(true);
        pet.setBaby();
    }

    public static void spawnWolfPet(final Player p) {
        final Wolf pet = (Wolf) p.getWorld().spawnEntity(p.getLocation(), EntityType.WOLF);
        pet.setCustomName(p.getName() + "'s Haustier");
        pet.setCustomNameVisible(true);
        pet.setLeashHolder(p);
        pet.setRemoveWhenFarAway(true);
        pet.setBaby();
    }

    public static void spawnPigPet(final Player p) {
        final Pig pet = (Pig) p.getWorld().spawnEntity(p.getLocation(), EntityType.PIG);
        pet.setCustomName(p.getName() + "'s Haustier");
        pet.setCustomNameVisible(true);
        pet.setLeashHolder(p);
        pet.setRemoveWhenFarAway(true);
        pet.setBaby();
    }

    public static void spawnChickenPet(final Player p) {
        final Chicken pet = (Chicken) p.getWorld().spawnEntity(p.getLocation(), EntityType.CHICKEN);
        pet.setCustomName(p.getName() + "'s Haustier");
        pet.setCustomNameVisible(true);
        pet.setLeashHolder(p);
        pet.setRemoveWhenFarAway(true);
    }

    @EventHandler
    public void unleash(final EntityUnleashEvent e) {
        final Entity z = e.getEntity();
        z.remove();
        final String name = z.getCustomName().replace("'s Haustier", "");
        final Player holder = Bukkit.getPlayer(name);
        Lobby.PetOwner.remove(holder);
    }

    @EventHandler
    public void ond(final EntityDeathEvent e) {
        final Entity z = e.getEntity();
        if (e.getEntityType() == EntityType.WOLF) {
            e.getDrops().clear();
            final String name = z.getCustomName().replace("'s Haustier", "");
            final Player holder = Bukkit.getPlayer(name);
            Lobby.PetOwner.remove(holder);
        }
        if (e.getEntityType() == EntityType.PIG) {
            e.getDrops().clear();
            final String name = z.getCustomName().replace("'s Haustier", "");
            final Player holder = Bukkit.getPlayer(name);
            Lobby.PetOwner.remove(holder);
        }
        if (e.getEntityType() == EntityType.RABBIT) {
            e.getDrops().clear();
            final String name = z.getCustomName().replace("'s Haustier", "");
            final Player holder = Bukkit.getPlayer(name);
            Lobby.PetOwner.remove(holder);
        }
        if (e.getEntityType() == EntityType.CHICKEN) {
            e.getDrops().clear();
            final String name = z.getCustomName().replace("'s Haustier", "");
            final Player holder = Bukkit.getPlayer(name);
            Lobby.PetOwner.remove(holder);
        }
    }

    @EventHandler
    public void onspawn(final ItemSpawnEvent e) {
        if (e.getEntityType() == EntityType.LEASH_HITCH) {
            e.getEntity().remove();
        }
        if (e.getEntity().getItemStack().getType() == Material.LEASH) {
            final ItemStack i = e.getEntity().getItemStack();
            i.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onInteract(final PlayerInteractEntityEvent e) {
        final Entity en = e.getRightClicked();
        final Player p = e.getPlayer();
        if (en instanceof Rabbit) {
            if (((Rabbit) en).isLeashed()) {
                if (((Rabbit) en).getLeashHolder() == p) {
                    ((Rabbit) en).setLeashHolder(null);
                    en.remove();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein \u00a7rHasen\u00a77 Haustier wurde entfernt.");
                    Lobby.PetOwner.remove(p);
                }
            } else {
                en.remove();
            }
        }
        if (en instanceof Wolf) {
            if (((Wolf) en).isLeashed()) {
                if (((Wolf) en).getLeashHolder() == p) {
                    ((Wolf) en).setLeashHolder(null);
                    en.remove();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein \u00a7rWolf\u00a77 Haustier wurde entfernt.");
                    Lobby.PetOwner.remove(p);
                }
            } else {
                en.remove();
            }
        }
        if (en instanceof Pig) {
            if (((Pig) en).isLeashed()) {
                if (((Pig) en).getLeashHolder() == p) {
                    ((Pig) en).setLeashHolder(null);
                    en.remove();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein \u00a7rSchwein\u00a77 Haustier wurde entfernt.");
                    Lobby.PetOwner.remove(p);
                }
            } else {
                en.remove();
            }
        }
        if (en instanceof Chicken) {
            if (((Chicken) en).isLeashed()) {
                if (((Chicken) en).getLeashHolder() == p) {
                    ((Chicken) en).setLeashHolder(null);
                    en.remove();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein \u00a7rChicken\u00a77 Haustier wurde entfernt.");
                    Lobby.PetOwner.remove(p);
                }
            } else {
                en.remove();
            }
        }
    }
}
