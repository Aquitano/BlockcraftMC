package me.blockcraft.bedwars.team;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * One team of a round: who is in it, its bed and spawn once the map is loaded, and the inventory every one of its
 * team chests opens. A team is out once nobody is left in it; until its bed is broken, its players come back.
 */
public final class Team {

    private static final int CHEST_SIZE = 27;

    private final TeamColor color;
    private final int capacity;
    // Team chat reads members off the main thread; copy-on-write keeps the order they joined in.
    private final Set<UUID> members = new CopyOnWriteArraySet<>();
    private final Inventory chest;
    private Location spawn;
    private TeamBed bed;

    Team(TeamColor color, int capacity, Component chestTitle) {
        this.color = color;
        this.capacity = capacity;
        this.chest = Bukkit.createInventory(null, CHEST_SIZE, chestTitle);
    }

    public TeamColor color() {
        return color;
    }

    public List<Player> members() {
        return members.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    public boolean isMember(Player player) {
        return members.contains(player.getUniqueId());
    }

    public int size() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= capacity;
    }

    boolean add(Player player) {
        return members.add(player.getUniqueId());
    }

    boolean remove(Player player) {
        return members.remove(player.getUniqueId());
    }

    public Inventory chest() {
        return chest;
    }

    public Optional<Location> spawn() {
        return Optional.ofNullable(spawn).map(Location::clone);
    }

    public Optional<TeamBed> bed() {
        return Optional.ofNullable(bed);
    }

    public boolean hasBed() {
        return bed != null && bed.isStanding();
    }

    /** The team's place on the round's map. */
    public void settle(Location spawn, TeamBed bed) {
        this.spawn = spawn;
        this.bed = bed;
    }

    /** Boots, leggings and chestplate in the team's colour, as the lobby dresses its members and their figures. */
    public ItemStack[] armor() {
        return new ItemStack[] {
                dyed(Material.LEATHER_BOOTS), dyed(Material.LEATHER_LEGGINGS), dyed(Material.LEATHER_CHESTPLATE), null};
    }

    private ItemStack dyed(Material material) {
        return Items.of(material).color(color.dye().getColor()).build();
    }
}
