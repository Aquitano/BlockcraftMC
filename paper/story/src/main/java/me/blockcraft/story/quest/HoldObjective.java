package me.blockcraft.story.quest;

import org.betonquest.betonquest.api.DefaultObjective;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.ProfileProvider;
import org.betonquest.betonquest.api.quest.objective.service.ObjectiveService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Optional;

/**
 * {@code holdspot <location> <radius> <seconds>}: the party holds a spot together. The first member who stands within
 * the radius starts the hold for the whole party, also one who already stood there when the objective began; its time
 * only counts while a member stands there, and when it is through the objective completes for every member who has it,
 * its actions acting once for the party. Its {@code percent} property is how far the hold has come.
 */
public final class HoldObjective extends DefaultObjective {

    private static final long CHECK_TICKS = 10;

    private final ProfileProvider profiles;
    private final Fanout fanout;
    private final Argument<Location> spot;
    private final Argument<Number> radius;
    private final Argument<Number> seconds;
    private final BukkitTask check;
    private volatile int percent;

    HoldObjective(Plugin plugin, ObjectiveService service, ProfileProvider profiles, Party party, Fanout fanout,
                  Argument<Location> spot, Argument<Number> radius, Argument<Number> seconds) throws QuestException {
        super(service);
        this.profiles = profiles;
        this.fanout = fanout;
        this.spot = spot;
        this.radius = radius;
        this.seconds = seconds;
        service.getProperties().setProperty("percent", profile -> String.valueOf(percent));
        this.check = Bukkit.getScheduler().runTaskTimer(plugin, () -> holder(party).ifPresent(member -> party.holdReached(this, member)),
                CHECK_TICKS, CHECK_TICKS);
    }

    private Optional<Player> holder(Party party) {
        return profiles.getOnlineProfiles().stream().filter(getService()::containsProfile)
                .filter(profile -> party.playing(profile.getPlayerUUID()))
                .filter(profile -> getService().getExceptionHandler().handle(() -> holding(profile), false))
                .map(OnlineProfile::getPlayer).findFirst();
    }

    public String id() {
        return String.valueOf(getService().getObjectiveID());
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
        var target = spot.getValue(profile);
        double range = radius.getValue(profile).doubleValue();
        return at.getWorld().equals(target.getWorld()) && at.distanceSquared(target) <= range * range;
    }

    public void progress(int percent) {
        this.percent = percent;
    }

    public void completeFor(Collection<? extends Player> members) {
        fanout.acrossLasting("holdspot " + id(), () -> {
            for (var member : members) {
                var profile = profiles.getProfile(member);
                try {
                    if (getService().containsProfile(profile)) getService().complete(profile);
                } catch (RuntimeException failure) {
                    getService().getLogger().error("Completing " + id() + " for " + member.getName() + " failed", failure);
                }
            }
        });
    }

    @Override
    public void close() {
        check.cancel();
    }
}
