package me.blockcraft.lobby.listeners;

import me.blockcraft.coins.CAPI;
import me.blockcraft.lobby.Lobby;
import me.blockcraft.perms.MySQL.API;
import me.eventseen.Data.Data;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("UnnecessaryUnicodeEscape")
public class BlazeClick implements Listener {
    public static Lobby main;
    public static File datas;
    public static FileConfiguration DataFiles;

    static {
        BlazeClick.datas = new File("plugins/Lobby", "prem.yml");
        BlazeClick.DataFiles = YamlConfiguration.loadConfiguration(BlazeClick.datas);
    }

    public BlazeClick(final Lobby main) {
        BlazeClick.main = main;
    }

    @EventHandler
    public void onShopClick(final PlayerInteractEntityEvent e) {
        final Player p = e.getPlayer();
        final Entity r = e.getRightClicked();
        if (r instanceof Villager) {
            e.setCancelled(true);
            BlazeClick.main.openShop(p);
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onrightclick(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock().getType() == Material.TRAP_DOOR) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.ANVIL) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.WORKBENCH) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.WOOD_BUTTON) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.FURNACE) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.WOODEN_DOOR) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.SPRUCE_DOOR) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.LEVER) {
                e.setCancelled(true);
            }
            if (e.getClickedBlock().getType() == Material.CHEST) {
                e.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onInvClick(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null) {
            e.getWhoClicked().closeInventory();
            return;
        }
        if (p.getOpenInventory().getTopInventory() == null) {
            return;
        }
        if (e.getClickedInventory() == p.getInventory()) {
            if (p.isOp()) {
                e.setCancelled(false);
                return;
            }
            e.setCancelled(true);
        } else {
            if (e.getClickedInventory() == null) {
                return;
            }
            if (e.getCurrentItem() == null) {
                return;
            }
            if (e.getCurrentItem().getItemMeta() == null) {
                return;
            }
            if (e.getClickedInventory().getName().equals(BlazeClick.main.v)) {
                e.setCancelled(true);
                if (e.getCurrentItem().getType() == Material.SKULL_ITEM) {
                    BlazeClick.main.openHeadInv(p);
                }
                if (e.getCurrentItem().getType() == Material.SPECKLED_MELON) {
                    if (CAPI.getCoins(p.getUniqueId().toString()) > 15000) {
                        API.setPremium(p.getUniqueId().toString(), p.getName());
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        final SimpleDateFormat date = new SimpleDateFormat("dd:MM");
                        final String date2 = date.format(new Date());
                        BlazeClick.DataFiles.set(p.getName(), date2);
                        try {
                            BlazeClick.DataFiles.save(BlazeClick.datas);
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 15000);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r15000 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getType() == Material.LEATHER_CHESTPLATE) {
                    e.getView().close();
                    BlazeClick.main.openChestplateShop(p);
                }
                if (e.getCurrentItem().getType() == Material.SKULL_ITEM) {
                    e.getView().close();
                    BlazeClick.main.openHeadInv(p);
                }
                if (e.getCurrentItem().getType() == Material.STAINED_GLASS_PANE) {
                    e.getView().close();
                }
            }
            if (e.getClickedInventory().getName().equals(BlazeClick.main.f2)) {
                e.setCancelled(true);
                final ItemStack a = BlazeClick.main.Skull("\u00a79Hamburger Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Sloggy_Whopper");
                final ItemStack ab = BlazeClick.main.Skull("\u00a79Kisten Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Zealock");
                final ItemStack abs = BlazeClick.main.Skull("\u00a79Piston Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "SkillOuTes");
                final ItemStack abss = BlazeClick.main.Skull("\u00a79Command Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "ExtrayeaMC");
                final ItemStack dis = BlazeClick.main.Skull("\u00a79Dispenser Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "TreePuncher105");
                final ItemStack diss = BlazeClick.main.Skull("\u00a79Gummi Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Trajan");
                final ItemStack disss = BlazeClick.main.Skull("\u00a79TNT Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Eternal");
                final ItemStack foot = BlazeClick.main.Skull("\u00a79Football Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Smaug");
                final ItemStack foots = BlazeClick.main.Skull("\u00a79Ender Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Edna_I");
                final ItemStack marios = BlazeClick.main.Skull("\u00a79SuperMario Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Mario");
                final ItemStack yoshi = BlazeClick.main.Skull("\u00a79Yoshis Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Yoshi");
                final ItemStack yoshis = BlazeClick.main.Skull("\u00a79Bowsers Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Bowser");
                final ItemStack trooper = BlazeClick.main.Skull("\u00a79Sturmtruppen Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Stormtrooper");
                final ItemStack Boba = BlazeClick.main.Skull("\u00a79Boba's Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "BobaFett");
                final ItemStack Chief = BlazeClick.main.Skull("\u00a79Master Chief's Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "jscherry");
                final ItemStack Iron = BlazeClick.main.Skull("\u00a79Iron Man's Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "iron_man");
                final ItemStack CapAm = BlazeClick.main.Skull("\u00a79Captain America's Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "MURICA");
                final ItemStack Crump = BlazeClick.main.Skull("\u00a79Crumpy Cat's Kopf", Material.SKULL_ITEM,
                        "\u00a78» \u00a7r300 Coins", 1, (short) 3, "GrumpyCat");
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Hamburger Kopf")) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".a")) {
                        e.getView().close();
                        p.getInventory().setHelmet(a);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".a", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(ab.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".ab")) {
                        e.getView().close();
                        p.getInventory().setHelmet(ab);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".ab", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(abs.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".abs")) {
                        e.getView().close();
                        p.getInventory().setHelmet(abs);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".abs", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(abss.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".abss")) {
                        e.getView().close();
                        p.getInventory().setHelmet(abss);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".abss", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(dis.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".dis")) {
                        e.getView().close();
                        p.getInventory().setHelmet(dis);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".dis", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(diss.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".diss")) {
                        e.getView().close();
                        p.getInventory().setHelmet(diss);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".diss", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(disss.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".disss")) {
                        e.getView().close();
                        p.getInventory().setHelmet(disss);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".disss", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(foot.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".foot")) {
                        e.getView().close();
                        p.getInventory().setHelmet(foot);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".foot", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(foots.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".foots")) {
                        e.getView().close();
                        p.getInventory().setHelmet(foots);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".foots", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(marios.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".marios")) {
                        e.getView().close();
                        p.getInventory().setHelmet(marios);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".marios", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(yoshi.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".yoshi")) {
                        e.getView().close();
                        p.getInventory().setHelmet(yoshi);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".yoshi", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(yoshis.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".yoshis")) {
                        e.getView().close();
                        p.getInventory().setHelmet(yoshis);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".yoshis", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(trooper.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".trooper")) {
                        e.getView().close();
                        p.getInventory().setHelmet(trooper);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".trooper", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(Boba.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".boba")) {
                        e.getView().close();
                        p.getInventory().setHelmet(Boba);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".boba", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(Chief.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".chief")) {
                        e.getView().close();
                        p.getInventory().setHelmet(Chief);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".chief", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(Iron.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".iron")) {
                        e.getView().close();
                        p.getInventory().setHelmet(Iron);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".iron", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(CapAm.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".capam")) {
                        e.getView().close();
                        p.getInventory().setHelmet(CapAm);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".capam", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equals(Crump.getItemMeta().getDisplayName())) {
                    if (Lobby.Heads.getBoolean(p.getName() + ".crump")) {
                        e.getView().close();
                        p.getInventory().setHelmet(Crump);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast einen Kopf aufgesetzt!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 300) {
                        Lobby.Heads.set(p.getName() + ".crump", true);
                        try {
                            Lobby.Heads.save(Lobby.Head);
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 300);
                        final int coins = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins);
                    } else {
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r300 Coins \u00a77um dir das zu kaufen.");
                    }
                }
            }
            if (e.getClickedInventory().getName().equals(BlazeClick.main.f)) {
                e.setCancelled(true);
                final ItemStack back = BlazeClick.main.Stack("\u00a78» \u00a7czurück", Material.BARRIER, " ", 1,
                        (short) 0);
                final ItemStack rotc = BlazeClick.main.colorArmor("\u00a79Rotes Hemd", Material.LEATHER_CHESTPLATE, "",
                        1, (short) 0, 255, 0, 0);
                final ItemStack gruenc = BlazeClick.main.colorArmor("\u00a79Grünes Hemd", Material.LEATHER_CHESTPLATE,
                        "", 1, (short) 0, 16, 156, 0);
                final ItemStack blauc = BlazeClick.main.colorArmor("\u00a79Blaues Hemd", Material.LEATHER_CHESTPLATE,
                        "", 1, (short) 0, 19, 79, 218);
                final ItemStack lilac = BlazeClick.main.colorArmor("\u00a79Lila Hemd", Material.LEATHER_CHESTPLATE, "",
                        1, (short) 0, 162, 0, 255);
                final ItemStack orangec = BlazeClick.main.colorArmor("\u00a79Oranges Hemd", Material.LEATHER_CHESTPLATE,
                        "", 1, (short) 0, 255, 162, 0);
                final ItemStack Turkisc = BlazeClick.main.colorArmor("\u00a79Türkises Hemd",
                        Material.LEATHER_CHESTPLATE, "", 1, (short) 0, 0, 100, 142);
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Rotes Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Rot")) {
                        e.getView().close();
                        p.getInventory().setChestplate(rotc);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Rot", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Grünes Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Gruen")) {
                        e.getView().close();
                        p.getInventory().setChestplate(gruenc);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Gruen", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Blaues Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Blau")) {
                        e.getView().close();
                        p.getInventory().setChestplate(blauc);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Blau", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Lila Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Lila")) {
                        e.getView().close();
                        p.getInventory().setChestplate(lilac);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Lila", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Oranges Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Orange")) {
                        e.getView().close();
                        p.getInventory().setChestplate(orangec);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Orange", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a79Türkises Hemd")) {
                    if (Lobby.HemdBank.getBoolean(p.getName() + ".Turkis")) {
                        e.getView().close();
                        p.getInventory().setChestplate(Turkisc);
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du hast ein Hemd angezogen!");
                    } else if (CAPI.getCoins(p.getUniqueId().toString()) >= 500) {
                        Lobby.HemdBank.set(p.getName() + ".Turkis", true);
                        try {
                            Lobby.HemdBank.save(Lobby.Hemd);
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        e.getView().close();
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Dein Kauf war erfolgreich!");
                        CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                        final int coins2 = CAPI.getCoins(p.getUniqueId().toString());

                        p.setLevel(coins2);
                    } else {
                        p.sendMessage(Data.BLOCKCRAFT_PREFIX
                                + "\u00a77Du benötigst \u00a7r500 Coins \u00a77um dir das zu kaufen.");
                        e.getView().close();
                    }
                }
                if (e.getCurrentItem().getType() == Material.BARRIER) {
                    e.getView().close();
                    BlazeClick.main.openShop(p);
                }
            }
        }
    }
}
