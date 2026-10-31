package me.blockcraft.bedwars.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.bedwars.team.Team;
import me.blockcraft.bedwars.team.TeamColor;
import me.blockcraft.bedwars.team.Teams;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The lobby's way into a team: a stand in the team's armour that joins it on a right-click, and next to it a figure
 * for every member, wearing their skin and the team's armour. Their chunks stay loaded, because a figure that is not
 * saved with the map is gone with its chunk.
 */
public final class TeamSelector implements Listener {

    private final Plugin plugin;
    private final LobbyLayout layout;
    private final Teams teams;
    private final Messages messages;
    private final BiConsumer<Player, TeamColor> onChoose;
    private final Map<UUID, TeamColor> stands = new HashMap<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<TeamColor, List<Entity>> figures = new EnumMap<>(TeamColor.class);
    private boolean open;

    public TeamSelector(Plugin plugin, LobbyLayout layout, Teams teams, Messages messages, BiConsumer<Player, TeamColor> onChoose) {
        this.plugin = plugin;
        this.layout = layout;
        this.teams = teams;
        this.messages = messages;
        this.onChoose = onChoose;
    }

    public void open() {
        open = true;
        for (var team : teams.all()) {
            layout.joiner(team.color()).ifPresent(spot -> spot.getChunk().addPluginChunkTicket(plugin));
            layout.displays(team.color()).forEach(spot -> spot.getChunk().addPluginChunkTicket(plugin));
            layout.joiner(team.color()).ifPresent(spot -> {
                var stand = spot.getWorld().spawn(spot, ArmorStand.class, created -> {
                    created.customName(messages.get("lobby.team-stand",
                            Placeholder.component("team", team.color().displayName(messages))));
                    created.setCustomNameVisible(true);
                    created.getEquipment().setArmorContents(team.armor());
                    created.setGravity(false);
                    created.setBasePlate(false);
                    created.setArms(false);
                    created.setInvulnerable(true);
                    created.setPersistent(false);
                });
                stands.put(stand.getUniqueId(), team.color());
                spawned.add(stand);
            });
            show(team);
        }
    }

    /** Draws the team's members again, after someone joined or left it. */
    public void show(Team team) {
        if (!open) {
            return;
        }
        figures.getOrDefault(team.color(), List.of()).forEach(Entity::remove);
        var shown = new ArrayList<Entity>();
        var spots = layout.displays(team.color());
        var members = team.members();
        for (int index = 0; index < Math.min(spots.size(), members.size()); index++) {
            var member = members.get(index);
            shown.add(Figures.spawn(spots.get(index), ResolvableProfile.resolvableProfile(member.getPlayerProfile()),
                    member.displayName(), team.armor()));
        }
        figures.put(team.color(), shown);
    }

    public void close() {
        open = false;
        figures.values().forEach(list -> list.forEach(Entity::remove));
        figures.clear();
        spawned.forEach(Entity::remove);
        spawned.clear();
        stands.clear();
    }

    @EventHandler
    public void onChoose(PlayerInteractAtEntityEvent event) {
        var team = stands.get(event.getRightClicked().getUniqueId());
        if (team != null) {
            event.setCancelled(true);
            onChoose.accept(event.getPlayer(), team);
        }
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (stands.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (stands.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
