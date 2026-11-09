package me.blockcraft.story.quest;

import org.betonquest.betonquest.api.DefaultObjective;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.ProfileProvider;
import org.betonquest.betonquest.api.quest.objective.service.ObjectiveService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Collection;

/**
 * {@code holdconsole <location> <radius> <seconds>}: the party holds a console against droids. The first member who
 * reaches it starts the defence for the whole party; the upload only advances while a member stands within the radius,
 * and when it is done the objective completes for every member who has it, not only for whoever stood there.
 */
public final class ConsoleObjective extends DefaultObjective {

    private final ProfileProvider profiles;
    private final Argument<Location> console;
    private final Argument<Number> radius;
    private final Argument<Number> seconds;
    private volatile int percent;

    ConsoleObjective(ObjectiveService service, ProfileProvider profiles, Party party, Argument<Location> console,
                     Argument<Number> radius, Argument<Number> seconds) throws QuestException {
        super(service);
        this.profiles = profiles;
        this.console = console;
        this.radius = radius;
        this.seconds = seconds;
        service.getProperties().setProperty("percent", profile -> String.valueOf(percent));
        service.request(PlayerMoveEvent.class).onlineHandler((event, profile) -> {
            if (holding(profile)) party.consoleReached(this);
        }).player(PlayerMoveEvent::getPlayer).subscribe(true);
        service.request(PlayerTeleportEvent.class).priority(EventPriority.MONITOR).onlineHandler((event, profile) -> {
            if (event.getTo().distanceSquared(console.getValue(profile)) <= Math.pow(radius.getValue(profile).doubleValue(), 2)) {
                party.consoleReached(this);
            }
        }).player(PlayerTeleportEvent::getPlayer).subscribe(true);
    }

    public Location console() throws QuestException {
        return console.getValue(null);
    }

    public int seconds() throws QuestException {
        return seconds.getValue(null).intValue();
    }

    public boolean holding(Player player) throws QuestException {
        var profile = profiles.getProfile(player);
        return getService().containsProfile(profile) && holding(profile);
    }

    private boolean holding(OnlineProfile profile) throws QuestException {
        var at = profile.getPlayer().getLocation();
        var target = console.getValue(profile);
        double range = radius.getValue(profile).doubleValue();
        return at.getWorld().equals(target.getWorld()) && at.distanceSquared(target) <= range * range;
    }

    public void progress(int percent) {
        this.percent = percent;
    }

    public void completeFor(Collection<? extends Player> members) {
        for (var member : members) {
            var profile = profiles.getProfile(member);
            if (getService().containsProfile(profile)) getService().complete(profile);
        }
    }
}
