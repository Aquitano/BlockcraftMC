package me.blockcraft.core.staff;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.FreezeRepository;
import me.blockcraft.common.player.PlayerReference;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freezes a player in place until staff lift it: no movement, no damage dealt, no commands but private messages.
 * The flag is in {@code bc_frozen} and read at pre-login, so switching servers does not shake it off, and a
 * frozen player who disconnects is reported to the staff.
 */
public final class FreezeService implements Listener {

    public static final String NOTIFY_PERMISSION = "blockcraft.core.freeze.notify";
    private static final long REMINDER_TICKS = 200L;
    private static final Set<String> ALLOWED_COMMANDS = Set.of("msg", "tell", "w", "r", "reply");

    private final Plugin plugin;
    private final FreezeRepository repository;
    private final Messages messages;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private BukkitTask reminder;

    public FreezeService(Plugin plugin, FreezeRepository repository, Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
    }

    public void start() {
        reminder = Bukkit.getScheduler().runTaskTimer(plugin, this::remind, REMINDER_TICKS, REMINDER_TICKS);
    }

    public void stop() {
        if (reminder != null) {
            reminder.cancel();
        }
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    /** Returns whether the target is frozen after the toggle. */
    public boolean toggle(Player target, Player issuer) {
        if (isFrozen(target)) {
            unfreeze(target);
            return false;
        }
        freeze(target, PlayerReference.known(issuer.getUniqueId(), BlockcraftApi.get().realName(issuer)));
        return true;
    }

    private void freeze(Player target, PlayerReference issuer) {
        var uuid = target.getUniqueId();
        frozen.add(uuid);
        Tasks.async(plugin, () -> repository.freeze(uuid, issuer));
        announce(target);
    }

    private void unfreeze(Player target) {
        var uuid = target.getUniqueId();
        frozen.remove(uuid);
        Tasks.async(plugin, () -> repository.unfreeze(uuid));
        target.sendMessage(messages.get("freeze.released"));
    }

    private void announce(Player target) {
        BlockcraftApi.get().notifications().important(target, Title.title(messages.get("freeze.title"), messages.get("freeze.subtitle")));
        messages.getLines("freeze.reminder").forEach(target::sendMessage);
    }

    private void remind() {
        var lines = messages.getLines("freeze.reminder");
        for (UUID id : frozen) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                lines.forEach(player::sendMessage);
            }
        }
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (repository.issuer(event.getUniqueId()).isPresent()) {
            pending.add(event.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (pending.remove(player.getUniqueId())) {
            frozen.add(player.getUniqueId());
            announce(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (!frozen.remove(player.getUniqueId())) {
            return;
        }
        var notice = messages.get("freeze.left-while-frozen", Placeholder.unparsed("player", player.getName()));
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player)).findFirst().ifPresentOrElse(
                carrier -> ProxyMessaging.notifyStaff(plugin, carrier, NOTIFY_PERMISSION, notice),
                () -> plugin.getLogger().warning(player.getName() + " left while frozen and nobody was online to carry the staff notice"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isFrozen(event.getPlayer()) && event.hasChangedPosition()) {
            var to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("freeze.no-commands"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && isFrozen(damager)) {
            event.setCancelled(true);
        }
    }
}
