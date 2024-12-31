package me.blockcraft.rush;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RushManager implements Listener {
    public static HashMap<Player, Integer> Leben;
    public static HashMap<Player, Integer> Coins;

    static {
        RushManager.Leben = new HashMap<>();
        RushManager.Coins = new HashMap<>();
    }

    public static Integer getCoins(final Player p) {
        if (RushManager.Coins.containsKey(p)) {
            return RushManager.Coins.get(p);
        }
        return 0;
    }

    public static void setCoins(final Player p, final Integer coins) {
        RushManager.Coins.remove(p);
        RushManager.Coins.put(p, coins);
    }

    public static void removeCoins(final Player p, final Integer anzahl) {
        int i = 0;
        if (RushManager.Coins.containsKey(p)) {
            i = RushManager.Coins.get(p);
        }
        RushManager.Coins.put(p, i + anzahl);
    }

    public static Integer getLeben(final Player p) {
        if (RushManager.Leben.containsKey(p)) {
            return RushManager.Leben.get(p);
        }
        return 0;
    }

    public static void setleben(final Player p, final Integer coins) {
        RushManager.Leben.remove(p);
        RushManager.Leben.put(p, coins);
    }

    public static void removeLeben(final Player p, final Integer anzahl) {
        int i = 0;
        if (RushManager.Leben.containsKey(p)) {
            i = RushManager.Leben.get(p);
        }
        RushManager.Leben.put(p, i - anzahl);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack Stack(final String Display, final int i, final String lores, final int Anzahl, final short Shorts) {
        final ItemStack istack52 = new ItemStack(i, Anzahl, Shorts);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack IDStack(final String Display, final int id, final String lores, final int Anzahl, final short Shorts) {
        final ItemStack istack52 = new ItemStack(id, Anzahl, Shorts);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack Skull(final String Display, final int i, final String lores, final int Anzahl, final short Shorts, final String Owner) {
        final ItemStack stack = new ItemStack(i, Anzahl, (short) 3);
        final SkullMeta meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.SKULL_ITEM);
        meta.setOwner(Owner);
        meta.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack colorArmor(final String Display, final Material m, final String lores, final int Anzahl, final short Shorts, final int RGB1, final int RGB2, final int RGB3) {
        final ItemStack istack52 = new ItemStack(m, Anzahl, Shorts);
        final LeatherArmorMeta istackMeta52 = (LeatherArmorMeta) istack52.getItemMeta();
        istackMeta52.setColor(Color.fromRGB(RGB1, RGB2, RGB3));
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack Stackench(final String Display, final int i, final String lores, final int Anzahl, final short Shorts) {
        final ItemStack istack52 = new ItemStack(i, Anzahl, Shorts);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        istackMeta52.addEnchant(Enchantment.DURABILITY, 1, true);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    public static Location getLocation(final String LocationName) {
        final double x = Main.getInstance().getConfig().getDouble(LocationName + ".X");
        final double y = Main.getInstance().getConfig().getDouble(LocationName + ".Y");
        final double z = Main.getInstance().getConfig().getDouble(LocationName + ".Z");
        final double yaw = Main.getInstance().getConfig().getDouble(LocationName + ".Yaw");
        final double pitch = Main.getInstance().getConfig().getDouble(LocationName + ".Pitch");
        final String World = Main.getInstance().getConfig().getString(LocationName + ".World");
        final World w = Bukkit.getWorld(World);
        return new Location(w, x, y, z, (short) yaw, (short) pitch);
    }

    public static void setLocation(final String LocationName, final Player p) {
        Main.getInstance().getConfig().set(LocationName + ".X", p.getLocation().getBlockX());
        Main.getInstance().getConfig().set(LocationName + ".Y", p.getLocation().getBlockY());
        Main.getInstance().getConfig().set(LocationName + ".Z", p.getLocation().getBlockZ());
        Main.getInstance().getConfig().set(LocationName + ".Yaw", p.getLocation().getYaw());
        Main.getInstance().getConfig().set(LocationName + ".Pitch", p.getLocation().getPitch());
        Main.getInstance().getConfig().set(LocationName + ".World", p.getWorld().getName());
        Main.getInstance().saveConfig();
    }

    public static void sendTitle(final Player player, final Integer fadeIn, final Integer stay, final Integer fadeOut, String title, String subtitle) {
        final PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
        final PacketPlayOutTitle packetPlayOutTimes = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TIMES, null, fadeIn, stay, fadeOut);
        connection.sendPacket(packetPlayOutTimes);
        if (subtitle != null) {
            subtitle = subtitle.replaceAll("%player%", player.getDisplayName());
            subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
            final IChatBaseComponent titleSub = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + subtitle + "\"}");
            final PacketPlayOutTitle packetPlayOutSubTitle = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE, titleSub);
            connection.sendPacket(packetPlayOutSubTitle);
        }
        if (title != null) {
            title = title.replaceAll("%player%", player.getDisplayName());
            title = ChatColor.translateAlternateColorCodes('&', title);
            final IChatBaseComponent titleMain = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + title + "\"}");
            final PacketPlayOutTitle packetPlayOutTitle = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE, titleMain);
            connection.sendPacket(packetPlayOutTitle);
        }
    }

    public static void sendActionBar(final Player player, final String message) {
        final CraftPlayer p = (CraftPlayer) player;
        final IChatBaseComponent cbc = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + message + "\"}");
        final PacketPlayOutChat ppoc = new PacketPlayOutChat(cbc, (byte) 2);
        p.getHandle().playerConnection.sendPacket(ppoc);
    }

    public void connect(final Player player, final String servername) {
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(servername);
        player.sendPluginMessage(Main.getInstance(), "BungeeCord", out.toByteArray());
    }
}
