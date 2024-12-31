package me.blockcraft.MySQL;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.event.Listener;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Ranking
        implements Listener {
    static final HashMap<Integer, String> rang = new HashMap<>();

    @SuppressWarnings("deprecation")
    public static void set() {
        ResultSet rs = MySQL.getResult("SELECT * FROM CoinsAPI ORDER BY WIN DESC LIMIT 10");
        int in = 0;
        try {
            while (rs.next()) {
                rang.put(++in, rs.getString("UUID"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ArrayList<?> LOC = new ArrayList<>();
        int id;
        int i = 0;
        while (i < LOC.size()) {
            block15:
            {
                id = i + 1;
                try {
                    Skull s;
                    if (rang.get(id) == null) {
                        if (((Location) LOC.get(i)).getBlock().getType() != Material.SKULL) {
                            return;
                        }
                        s = (Skull) ((Location) LOC.get(i)).getBlock().getState();
                        s.setSkullType(SkullType.SKELETON);
                        s.update();
                        Location newloc = new Location(((Location) LOC.get(i)).getWorld(), ((Location) LOC.get(i)).getX(), ((Location) LOC.get(i)).getY() - 1.0, ((Location) LOC.get(i)).getZ());
                        if (!(newloc.getBlock().getState() instanceof Sign)) {
                            return;
                        }
                        if (newloc.getBlock().getType() == Material.WALL_SIGN) {
                            BlockState b = newloc.getBlock().getState();
                            Sign S = (Sign) b;
                            S.setLine(0, "\u00a79Platz \u2013 #" + id);
                            S.setLine(1, "\u00a72\u00a7lKein Spieler");
                            S.setLine(2, "");
                            S.setLine(3, "");
                            S.update();
                        }
                        return;
                    }
                    if (((Location) LOC.get(i)).getBlock().getType() != Material.SKULL) {
                        return;
                    }
                    s = (Skull) ((Location) LOC.get(i)).getBlock().getState();
                    s.setSkullType(SkullType.PLAYER);
                    String name = Bukkit.getOfflinePlayer(UUID.fromString(rang.get(id))).getName();
                    String nameuuid = Bukkit.getOfflinePlayer(rang.get(id)).getName();
                    s.setOwner(name);
                    s.update();
                    Location newloc = new Location(((Location) LOC.get(i)).getWorld(), ((Location) LOC.get(i)).getX(), ((Location) LOC.get(i)).getY() - 1.0, ((Location) LOC.get(i)).getZ());
                    if (!(newloc.getBlock().getState() instanceof Sign)) {
                        newloc.getBlock().setType(Material.WALL_SIGN);
                    }
                    if (newloc.getBlock().getType() == Material.WALL_SIGN) {
                        BlockState b = newloc.getBlock().getState();
                        Sign S = (Sign) b;
                        S.setLine(0, "\u00a79Platz \u2013 #" + id);
                        S.setLine(1, "\u00a72\u00a7l" + name);
                        S.setLine(2, "\u00a79Wins \u2013\u00a77\u00a7l " + StatsAPI.getWins(nameuuid));
                        S.update();
                    }
                } catch (Exception e) {
                    if (((Location) LOC.get(i)).getBlock().getType() != Material.SKULL) {
                        return;
                    }
                    Skull s = (Skull) ((Location) LOC.get(i)).getBlock().getState();
                    s.setSkullType(SkullType.SKELETON);
                    s.update();
                    Location newloc = new Location(((Location) LOC.get(i)).getWorld(), ((Location) LOC.get(i)).getX(), ((Location) LOC.get(i)).getY() - 1.0, ((Location) LOC.get(i)).getZ());
                    if (!(newloc.getBlock().getState() instanceof Sign)) {
                        return;
                    }
                    if (newloc.getBlock().getType() != Material.WALL_SIGN) break block15;
                    BlockState b = newloc.getBlock().getState();
                    Sign S = (Sign) b;
                    S.setLine(0, "\u00a79Platz \u2013 #" + id);
                    S.setLine(1, "\u00a72\u00a7lKein Spieler");
                    S.setLine(2, "");
                    S.setLine(3, "");
                    S.update();
                }
            }
            ++i;
        }
    }
}

