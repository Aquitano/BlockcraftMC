package me.blockcraft.signs;

import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.IChatBaseComponent.ChatSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class StatusSigns extends JavaPlugin implements Listener {
    private static final String PREFIX = ChatColor.GOLD + "Blockcraft " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    public static StatusSigns instance;
    private final Map<Location, StatusSign> signs = new HashMap<>();

    @SuppressWarnings("deprecation")
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        loadSigns();

        getLogger().info(signs.size() + " signs loaded!");

        Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, new BukkitRunnable() {
            @Override
            public void run() {
                for (StatusSign sign : StatusSigns.this.signs.values()) {
                    sign.updateSigns();
                }
            }
        }, 0, 50L);

        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    private void loadSigns() {
        for (String key : getConfig().getKeys(false)) {
            ConfigurationSection section = getConfig().getConfigurationSection(key);
            if (section == null) continue;

            ConfigurationSection locSection = section.getConfigurationSection("loc");
            if (locSection == null) {
                getLogger().warning("Location section missing for key: " + key);
                getConfig().set(key, null);
                continue;
            }

            String worldName = locSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World not found: " + worldName);
                getConfig().set(key, null);
                continue;
            }

            double x = locSection.getDouble("x");
            double y = locSection.getDouble("y");
            double z = locSection.getDouble("z");
            Location location = new Location(world, x, y, z);

            if (!isSignBlock(location.getBlock())) {
                getLogger().info("Block at " + location + " is not a sign!");
                getConfig().set(key, null);
                continue;
            }

            String name = section.getString("name");
            String ip = section.getString("ip");
            int port = section.getInt("port");

            StatusSign statusSign = new StatusSign(location, name, ip, port);
            signs.put(location, statusSign);
        }

        saveConfig();
    }

    @EventHandler
    public void onPLayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!isSignBlock(block)) {
            return;
        }

        Sign sign = (Sign) event.getClickedBlock().getState();
        String line = sign.getLine(3);
        if (line == null || line.trim().isEmpty()) {
            System.out.println("Line 3 is null");
            event.setCancelled(true);
            return;
        }

        StatusSign statusSign = this.signs.get(block.getLocation());

        if (statusSign != null) {
            if (!statusSign.isOnline) {
                event.getPlayer().sendMessage(PREFIX + ChatColor.RED + "Server ist derzeit offline!");
                return;
            }

            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                 DataOutputStream dataStream = new DataOutputStream(byteStream)) {
                dataStream.writeUTF("Connect");
                dataStream.writeUTF(statusSign.getName());
                event.getPlayer().sendPluginMessage(this, "BungeeCord", byteStream.toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setActionBar(Player player, String message) {
        IChatBaseComponent chatComponent = ChatSerializer.a("{\"text\": \"" + message + "\"}");
        PacketPlayOutChat packet = new PacketPlayOutChat(chatComponent, (byte) 2);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    @SuppressWarnings({"unused", "deprecation"})
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Bukkit.getConsoleSender().sendMessage(PREFIX + "\u00a77Dieser Command ist nur für ein Spieler");
            return true;
        }

        Player player = (Player) sender;
        if (!sender.hasPermission("ss.create")) {
            sender.sendMessage(PREFIX + "\u00a7cDu hast keine Permission für diesen Command.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("createsign")) {
            if (args.length < 3) {
                player.sendMessage(PREFIX + "\u00a7e/createsign \u00a7a<IP> \u00a76<Port> \u00a75<ServerName>");
                return true;
            }

            String ip = args[0];
            int port;
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + ChatColor.RED + "Ungültiger Port.");
                return true;
            }
            String name = args[2];

            Block targetBlock = player.getTargetBlock((HashSet<Byte>) null, 10);
            if (!isSignBlock(targetBlock)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Du schaust nicht auf ein gültiges Schild!");
                return true;
            }

            Location location = targetBlock.getLocation();
            StatusSign statusSign = new StatusSign(location, name, ip, port);
            this.signs.put(location, statusSign);

            saveSign(statusSign);
        }
        return true;
    }

    private void saveSign(StatusSign sign) {
        int nextId = getConfig().getKeys(false).stream()
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;

        String key = String.valueOf(nextId);
        ConfigurationSection locSection = getConfig().createSection(key + ".loc");
        locSection.set("world", sign.getLocation().getWorld().getName());
        locSection.set("x", sign.getLocation().getX());
        locSection.set("y", sign.getLocation().getY());
        locSection.set("z", sign.getLocation().getZ());

        ConfigurationSection signSection = getConfig().createSection(key);
        signSection.set("name", sign.getName());
        signSection.set("ip", sign.getIp());
        signSection.set("port", sign.getPort());

        saveConfig();
    }

    private boolean isSignBlock(Block block) {
        Material type = block.getType();
        return type == Material.SIGN || type == Material.SIGN_POST || type == Material.WALL_SIGN;
    }
}
