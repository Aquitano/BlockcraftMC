package me.blockcraft.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;


public class StatusSign {
    public boolean isOnline;
    private final Location location;
    private final Sign sign;
    private final String name;
    private final String ip;
    private final int port;

    public StatusSign(Location location, String name, String ip, int port) {
        this.location = location;
        this.sign = ((Sign) location.getBlock().getState());
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    @SuppressWarnings("unused")
    public void updateSigns() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(this.ip, this.port));

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(254);
            String response = readResponse(in);
            String[] data = response.split("§");

            if (data.length < 3) {
                System.out.println("Invalid response from server: " + response);
                handleOfflineStatus();
                return;
            }

            String motd = data[0].trim();
            int onlinePlayers = Integer.parseInt(data[1]);
            int maxPlayers = Integer.parseInt(data[2]);

            updateSignLines(motd, onlinePlayers, maxPlayers);
            updateAttachedBlock(motd);

            this.isOnline = true;

            out.close();
            in.close();

        } catch (IOException e) {
            handleOfflineStatus();
        }
    }

    private String readResponse(InputStream in) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();
        int byteRead;
        while ((byteRead = in.read()) != -1) {
            if (byteRead > 16 && byteRead != 255 && byteRead != 23 && byteRead != 24) {
                responseBuilder.append((char) byteRead);
            }
        }
        return responseBuilder.toString();
    }

    private void updateSignLines(String motd, int onlinePlayers, int maxPlayers) {
        switch (motd.toUpperCase()) {
            case "LOBBY":
                sign.setLine(1, ChatColor.GREEN + motd);
                break;
            case "INGAME":
                sign.setLine(1, ChatColor.RED + motd);
                break;
            case "RESTART":
                sign.setLine(1, ChatColor.AQUA + motd);
                break;
            default:
                sign.setLine(1, ChatColor.GRAY + "[" + ChatColor.GREEN + "Join" + ChatColor.GRAY + "]");
                break;
        }

        sign.setLine(0, "- " + name + " -");
        sign.setLine(2, onlinePlayers + "/" + maxPlayers);
        sign.setLine(3, ChatColor.DARK_GRAY + "*Klick*");
        sign.update();
    }

    private void updateAttachedBlock(String motd) {
        Block attachedBlock = getAttachedBlock();
        if (attachedBlock == null) {
            System.out.println("Attached block is null");
            return;
        }

        byte data;
        switch (motd.toUpperCase()) {
            case "INGAME":
                data = 14;
                break;
            case "RESTART":
                data = 4;
                break;
            default:
                data = 5;
                break;
        }

        attachedBlock.setTypeIdAndData(159, data, true);
    }

    private Block getAttachedBlock() {
        org.bukkit.material.Sign s = (org.bukkit.material.Sign) sign.getData();
        Block signBlock = sign.getBlock();

        return signBlock.getRelative(s.getAttachedFace());
    }

    private void handleOfflineStatus() {
        sign.setLine(0, ChatColor.RED + "§c█ █ █§c§l✖ §c█ █ █");
        sign.setLine(1, ChatColor.GRAY + "Offline");
        sign.setLine(2, "§a ");
        sign.setLine(3, ChatColor.RED + "§c█ █ █§c§l✖ §c█ █ █");
        sign.update();

        Block attachedBlock = getAttachedBlock();
        if (attachedBlock != null) {
            attachedBlock.setTypeIdAndData(159, (byte) 14, true);
        }

        isOnline = false;
    }

    public Location getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
