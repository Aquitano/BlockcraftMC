package me.blockcraft.bedwars.team;

import me.blockcraft.bedwars.map.BedSpot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;

/**
 * A team's bed: two blocks, made over in the team's colour when the round starts, since the maps come from a time
 * when every bed was red.
 */
public final class TeamBed {

    private final Block foot;
    private final Block head;
    private boolean standing = true;

    private TeamBed(Block foot, Block head) {
        this.foot = foot;
        this.head = head;
    }

    public static TeamBed place(World world, BedSpot spot, TeamColor color) {
        var foot = spot.foot(world);
        var head = foot.getRelative(spot.facing());
        foot.setBlockData(half(color, spot, Bed.Part.FOOT), false);
        head.setBlockData(half(color, spot, Bed.Part.HEAD), false);
        return new TeamBed(foot, head);
    }

    /** Takes away the bed the map ships with where no team plays. */
    public static void remove(World world, BedSpot spot) {
        var foot = spot.foot(world);
        new TeamBed(foot, foot.getRelative(spot.facing())).destroy();
    }

    private static Bed half(TeamColor color, BedSpot spot, Bed.Part part) {
        var bed = (Bed) Bukkit.createBlockData(color.bed());
        bed.setFacing(spot.facing());
        bed.setPart(part);
        return bed;
    }

    public boolean contains(Block block) {
        return foot.equals(block) || head.equals(block);
    }

    public boolean isStanding() {
        return standing;
    }

    /** How close {@code location} is to the nearer half, squared. */
    public double distanceSquared(Location location) {
        return Math.min(foot.getLocation().distanceSquared(location), head.getLocation().distanceSquared(location));
    }

    public Location center() {
        return foot.getLocation().add(head.getLocation()).multiply(0.5).add(0.5, 0.0, 0.5);
    }

    /** Both halves go without dropping anything; a bed only falls once. */
    public void destroy() {
        standing = false;
        head.setType(Material.AIR, false);
        foot.setType(Material.AIR, false);
    }
}
