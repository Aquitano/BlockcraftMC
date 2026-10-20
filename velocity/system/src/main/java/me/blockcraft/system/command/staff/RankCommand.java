package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.common.shop.Premium;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.Durations;
import me.blockcraft.system.rank.ProxyRanks;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * {@code /rank <player> [rank] [duration]}: shows or sets a player's LuckPerms group. A rank for good replaces every
 * group the player had, like {@code /lp user <player> parent set}, except premium bought in the shop when the new rank
 * lacks it; a rank on time sits on top of the one for good until LuckPerms lets it run out. Staff change only players
 * below them, and only to ranks below their own. Construct only while LuckPerms runs.
 */
public final class RankCommand {

    private static final String CONSOLE_SERVER = "proxy";

    private record Term(Duration length, String typed) {
    }

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final StaffActionRepository actions;
    private final Premium premium;
    private final Messages messages;
    private final Logger logger;
    private final LuckPerms luckPerms = LuckPermsProvider.get();
    private final DateTimeFormatter dates;

    public RankCommand(CommandSupport support, PlayerLookup lookup, StaffActionRepository actions, Premium premium, ZoneId zone,
                       Messages messages, Logger logger) {
        this.support = support;
        this.lookup = lookup;
        this.actions = actions;
        this.premium = premium;
        this.messages = messages;
        this.logger = logger;
        this.dates = DateTimeFormatter.ofPattern(messages.raw("rank.date-format")).withZone(zone);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("rank")
                .requires(CommandSupport.permission(SystemPermissions.RANK))
                .executes(support.usage("rank.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> target(source, context).ifPresent(target -> show(source, target))))
                        .then(BrigadierCommand.requiredArgumentBuilder("rank", StringArgumentType.word())
                                .suggests((context, builder) -> suggestRanks(context.getSource(), builder))
                                .executes(support.run((source, context) -> set(source, context, Optional.empty())))
                                .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.word())
                                        .executes(support.run((source, context) -> {
                                            String typed = StringArgumentType.getString(context, "duration");
                                            length(source, typed).ifPresent(length -> set(source, context, Optional.of(new Term(length, typed))));
                                        }))))));
    }

    private Optional<Target> target(CommandSource source, CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        return lookup.find(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name)));
    }

    private Optional<Duration> length(CommandSource source, String typed) {
        var length = Durations.timed(typed);
        if (length.isEmpty()) {
            messages.getLines("rank.units").forEach(source::sendMessage);
        } else if (!Durations.fits(length.get())) {
            source.sendMessage(messages.get("rank.too-long", Formatter.number("days", Durations.longest().toDays())));
            return Optional.empty();
        }
        return length;
    }

    private void show(CommandSource source, Target target) {
        var player = Placeholder.unparsed("player", target.name());
        User user;
        try {
            user = luckPerms.getUserManager().loadUser(target.uuid()).join();
        } catch (CompletionException e) {
            logger.warn("Could not load the rank of {}", target.name(), e);
            source.sendMessage(messages.get("rank.failed", player));
            return;
        }
        String permanent = permanentRank(user);
        heaviest(parents(user)).filter(Node::hasExpiry).ifPresentOrElse(
                temporary -> source.sendMessage(messages.get("rank.current-temporary", player,
                        Placeholder.unparsed("rank", temporary.getGroupName()),
                        Placeholder.unparsed("until", dates.format(temporary.getExpiry())),
                        Placeholder.unparsed("fallback", permanent))),
                () -> source.sendMessage(messages.get("rank.current", player, Placeholder.unparsed("rank", permanent))));
    }

    private void set(CommandSource source, CommandContext<CommandSource> context, Optional<Term> term) {
        String name = StringArgumentType.getString(context, "rank").toLowerCase(Locale.ROOT);
        var group = Optional.ofNullable(luckPerms.getGroupManager().getGroup(name));
        if (group.isEmpty()) {
            source.sendMessage(messages.get("rank.unknown", Placeholder.unparsed("rank", name)));
            return;
        }
        target(source, context).ifPresent(target -> change(source, target, group.get(), term));
    }

    private void change(CommandSource source, Target target, Group group, Optional<Term> term) {
        var player = Placeholder.unparsed("player", target.name());
        var rank = Placeholder.unparsed("rank", group.getName());
        var until = term.map(chosen -> Instant.now().plus(chosen.length()));
        String permanent;
        boolean keptPremium = false;
        try {
            if (!mayChange(source, target, group)) {
                return;
            }
            var users = luckPerms.getUserManager();
            var user = users.loadUser(target.uuid()).join();
            if (until.isPresent()) {
                permanent = permanentRank(user);
                if (weight(group) <= weight(permanent)) {
                    source.sendMessage(messages.get("rank.not-above", Placeholder.unparsed("fallback", permanent)));
                    return;
                }
                user.data().clear(node -> node instanceof InheritanceNode && node.hasExpiry());
                user.data().add(InheritanceNode.builder(group).expiry(until.get().getEpochSecond()).build());
            } else {
                keptPremium = !includes(group, Premium.GROUP) && premium.bought(target.uuid());
                user.data().clear(NodeType.INHERITANCE::matches);
                user.data().add(InheritanceNode.builder(group).build());
                if (keptPremium) {
                    user.data().add(InheritanceNode.builder(Premium.GROUP).build());
                }
                permanent = permanentRank(user);
            }
            users.saveUser(user).join();
            luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));
        } catch (CompletionException | DatabaseException e) {
            logger.warn("Could not set the rank of {} to {}", target.name(), group.getName(), e);
            source.sendMessage(messages.get("rank.failed", player));
            return;
        }
        if (until.isPresent()) {
            var end = Placeholder.unparsed("until", dates.format(until.get()));
            source.sendMessage(messages.get("rank.done-temporary", player, rank, end, Placeholder.unparsed("fallback", permanent)));
            target.online().ifPresent(online -> online.sendMessage(messages.get("rank.received-temporary", rank, end)));
        } else {
            var held = Placeholder.unparsed("rank", permanent);
            source.sendMessage(messages.get("rank.done", player, held));
            if (keptPremium) {
                source.sendMessage(messages.get("rank.premium-kept", player));
            }
            target.online().ifPresent(online -> online.sendMessage(messages.get("rank.received", held)));
        }
        audit(source, target, group.getName() + term.map(chosen -> " " + chosen.typed()).orElse(""));
    }

    /** Rank changes hand out permissions, so unlike punishments no bypass node lifts the ladder; the console always may. */
    private boolean mayChange(CommandSource source, Target target, Group group) {
        if (!(source instanceof Player actor)) {
            return true;
        }
        if (actor.getUniqueId().equals(target.uuid())) {
            source.sendMessage(messages.get("moderation.self"));
            return false;
        }
        if (!ProxyRanks.outranks(actor, target.uuid())) {
            source.sendMessage(messages.get("moderation.protected"));
            return false;
        }
        if (weight(group) >= ProxyRanks.weight(luckPerms.getPlayerAdapter(Player.class).getUser(actor))) {
            source.sendMessage(messages.get("rank.above-own"));
            return false;
        }
        return true;
    }

    private void audit(CommandSource source, Target target, String detail) {
        try {
            actions.record(CommandSupport.sender(source), "rank", Optional.of(PlayerReference.known(target.uuid(), target.name())),
                    Optional.of(detail), source instanceof Player player ? CommandSupport.currentServer(player) : CONSOLE_SERVER);
        } catch (DatabaseException e) {
            logger.warn("Could not log the rank change of {} to {}", target.name(), detail, e);
        }
    }

    /** The heaviest group the player holds for good; LuckPerms' primary group when they hold none. */
    private String permanentRank(User user) {
        return heaviest(parents(user).filter(node -> !node.hasExpiry())).map(InheritanceNode::getGroupName).orElseGet(user::getPrimaryGroup);
    }

    private static boolean includes(Group group, String name) {
        return group.getName().equals(name)
                || group.getInheritedGroups(group.getQueryOptions()).stream().anyMatch(parent -> parent.getName().equals(name));
    }

    private static Stream<InheritanceNode> parents(User user) {
        return user.getNodes(NodeType.INHERITANCE).stream();
    }

    private Optional<InheritanceNode> heaviest(Stream<InheritanceNode> parents) {
        return parents.max(Comparator.comparingInt(node -> weight(node.getGroupName())));
    }

    private int weight(String groupName) {
        return Optional.ofNullable(luckPerms.getGroupManager().getGroup(groupName)).map(RankCommand::weight).orElse(0);
    }

    private static int weight(Group group) {
        return group.getWeight().orElse(0);
    }

    /** Offers the ranks the source may hand out, lowest first. */
    private CompletableFuture<Suggestions> suggestRanks(CommandSource source, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        int ceiling = source instanceof Player actor
                ? ProxyRanks.weight(luckPerms.getPlayerAdapter(Player.class).getUser(actor))
                : Integer.MAX_VALUE;
        luckPerms.getGroupManager().getLoadedGroups().stream()
                .filter(group -> weight(group) < ceiling)
                .sorted(Comparator.comparingInt(RankCommand::weight))
                .map(Group::getName)
                .filter(name -> name.startsWith(typed))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
