package me.blockcraft.lobby.listeners;

import de.robingrether.idisguise.disguise.ArmorStandDisguise;
import de.robingrether.idisguise.iDisguise;
import me.blockcraft.lobby.Lobby;
import me.blockcraft.lobby.utils.Inventar;
import me.blockcraft.lobby.utils.Particles;
import me.blockcraft.lobby.utils.PetManager;
import me.eventseen.Data.Data;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PremiumInvListener implements Listener {
    public static Lobby main;

    public PremiumInvListener(final Lobby main) {
        PremiumInvListener.main = main;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void on(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("Premium")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78● \u00a7bArmorStand")) {
                    if (iDisguise.getInstance().getAPI().isDisguised(p)) {
                        e.getView().close();
                        final Inventory inv = Bukkit.createInventory(null, 9, "Premium");
                        final ItemStack stack = Inventar.Stack("\u00a78● \u00a7cDEAKTIVIEREN \u00a78●", Material.INK_SACK, "\u00a77Klicke, zum deaktivieren!", 1, (short) 8);
                        final ItemStack istack541 = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
                        final ItemMeta istackMeta541 = istack541.getItemMeta();
                        istackMeta541.setDisplayName(" ");
                        istack541.setItemMeta(istackMeta541);
                        inv.setItem(4, stack);
                        inv.setItem(0, istack541);
                        inv.setItem(1, istack541);
                        inv.setItem(2, istack541);
                        inv.setItem(3, istack541);
                        inv.setItem(5, istack541);
                        inv.setItem(6, istack541);
                        inv.setItem(7, istack541);
                        inv.setItem(8, istack541);
                        p.openInventory(inv);
                    } else {
                        iDisguise.getInstance().getAPI().disguise(p, new ArmorStandDisguise());
                        final ItemStack skull = Inventar.Skull("", Material.SKULL_ITEM, "", 1, (short) 3, p.getName());
                        p.getInventory().setHelmet(skull);
                        p.closeInventory();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast dich in einen \u00a7rArmorstand \u00a77verwandelt.");
                        final Particles packet = new Particles(EnumParticle.FIREWORKS_SPARK, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
                        packet.sendToPlayer(p);
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78● \u00a76Zum Premiumbereich")) {
                    p.teleport(PremiumInvListener.main.getPremium());
                    p.playSound(p.getLocation(), Sound.ENDERMAN_TELEPORT, 4.0f, 4.0f);
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78● \u00a7bHaustiere")) {
                    final Inventory inv = Bukkit.createInventory(null, 9, "Pets");
                    e.getView().close();
                    final ItemStack wolf = Inventar.Stack("\u00a79Wolf", Material.BONE, "\u00a77Spawne deinen Wolf", 1, (short) 0);
                    final ItemStack chicken = Inventar.Stack("\u00a79Huhn", Material.EGG, "\u00a77Spawne dein Hühnchen", 1, (short) 0);
                    final ItemStack pig = Inventar.Stack("\u00a79Schwein", Material.PORK, "\u00a77Spawne dein Schwein", 1, (short) 0);
                    final ItemStack rabbit = Inventar.Stack("\u00a79Hase ", Material.CARROT_ITEM, "\u00a77Spawne deinen Hasen", 1, (short) 0);
                    final ItemStack back = Inventar.Stack("\u00a7cschließen", Material.BARRIER, "", 1, (short) 0);
                    inv.setItem(0, wolf);
                    inv.setItem(1, chicken);
                    inv.setItem(2, pig);
                    inv.setItem(3, rabbit);
                    inv.setItem(8, back);
                    p.openInventory(inv);
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78● \u00a7cDEAKTIVIEREN \u00a78●")) {
                    e.getView().close();
                    p.getInventory().setArmorContents(null);
                    if (iDisguise.getInstance().getAPI().isDisguised(p)) {
                        iDisguise.getInstance().getAPI().undisguise(p);
                    }
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du wurdest zurück verwandelt.");
                    final Particles packet2 = new Particles(EnumParticle.EXPLOSION_HUGE, p.getLocation(), 1.0f, 1.0f, 1.0f, 0.05f, 1);
                    packet2.sendToPlayer(p);
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78● \u00a7bSpuren")) {
                    e.getView().close();
                    Inventar.openTrailsMenu(p);
                } else {
                    p.closeInventory();
                }
            } catch (Exception ex) {
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Ein Fehler ist aufgetreten.");
            }
        } else if (e.getInventory().getName().equalsIgnoreCase("Pets")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem().getType() == Material.EGG) {
                    e.getView().close();
                    if (Lobby.PetOwner.contains(p)) {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Entferne vorerst dein jetziges Pet.");
                    } else {
                        Lobby.PetOwner.add(p);
                        PetManager.spawnChickenPet(p);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast das \u00a7fHuhn \u00a77Pet gewählt.");
                    }
                } else if (e.getCurrentItem().getType() == Material.BONE) {
                    e.getView().close();
                    if (Lobby.PetOwner.contains(p)) {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Entferne vorerst dein jetziges Pet.");
                    } else {
                        Lobby.PetOwner.add(p);
                        PetManager.spawnWolfPet(p);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast das \u00a7fWolf \u00a77Pet gewählt.");
                    }
                } else if (e.getCurrentItem().getType() == Material.PORK) {
                    e.getView().close();
                    if (Lobby.PetOwner.contains(p)) {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Entferne vorerst dein jetziges Pet.");
                    } else {
                        Lobby.PetOwner.add(p);
                        PetManager.spawnPigPet(p);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast das \u00a7fPig \u00a77Pet gewählt.");
                    }
                } else if (e.getCurrentItem().getType() == Material.CARROT_ITEM) {
                    e.getView().close();
                    if (Lobby.PetOwner.contains(p)) {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Entferne vorerst dein jetziges Pet.");
                    } else {
                        Lobby.PetOwner.add(p);
                        PetManager.spawnRabbitPet(p);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast das \u00a7fHasen \u00a77Pet gewählt.");
                    }
                } else if (e.getCurrentItem().getType() == Material.BARRIER) {
                    e.getView().close();
                } else {
                    p.closeInventory();
                }
            } catch (Exception ex2) {
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Ein Fehler ist aufgetreten.");
            }
        } else if (e.getInventory().getName().equalsIgnoreCase("Trails")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem().getType() == Material.BOOK) {
                    Lobby.Zauberei.remove(p);
                    Lobby.Ender.remove(p);
                    Lobby.Feuerwerk.remove(p);
                    Lobby.Feuer.remove(p);
                    Lobby.Zauberei.add(p);
                    p.closeInventory();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast den \u00a7rZauber \u00a77Effekt ausgewählt.");
                } else if (e.getCurrentItem().getType() == Material.NOTE_BLOCK) {
                    Lobby.Zauberei.remove(p);
                    Lobby.Ender.remove(p);
                    Lobby.Feuerwerk.remove(p);
                    Lobby.Feuer.remove(p);
                    Lobby.Ender.add(p);
                    p.closeInventory();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast den \u00a7rMusik \u00a77Effekt ausgewählt.");
                } else if (e.getCurrentItem().getType() == Material.FIREBALL) {
                    Lobby.Zauberei.remove(p);
                    Lobby.Ender.remove(p);
                    Lobby.Feuerwerk.remove(p);
                    Lobby.Feuer.remove(p);
                    Lobby.Feuer.add(p);
                    p.closeInventory();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast den \u00a7rFeuer \u00a77Effekt ausgewählt.");
                } else if (e.getCurrentItem().getType() == Material.FIREWORK) {
                    Lobby.Zauberei.remove(p);
                    Lobby.Ender.remove(p);
                    Lobby.Feuerwerk.remove(p);
                    Lobby.Feuer.remove(p);
                    Lobby.Feuerwerk.add(p);
                    p.closeInventory();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast den \u00a7rFeuerwerk \u00a77Effekt ausgewählt.");
                } else if (e.getCurrentItem().getType() == Material.BARRIER) {
                    Lobby.Zauberei.remove(p);
                    Lobby.Ender.remove(p);
                    Lobby.Feuerwerk.remove(p);
                    Lobby.Feuer.remove(p);
                    e.getView().close();
                    p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Deine Effekte wurden entfernt.");
                } else {
                    p.closeInventory();
                }
            } catch (Exception ex3) {
                p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Ein Fehler ist aufgetreten.");
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void on(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        p.getInventory().setArmorContents(null);
        if (iDisguise.getInstance().getAPI().isDisguised(p)) {
            iDisguise.getInstance().getAPI().undisguise(p);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void on(final PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        if (iDisguise.getInstance().getAPI().isDisguised(p)) {
            iDisguise.getInstance().getAPI().undisguise(p);
        }
    }
}
