package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.KnownPlayer;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.moderation.Punishment;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.common.moderation.WarnRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.Playtime;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentService;
import me.blockcraft.system.rank.ProxyRanks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class WhoisCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PlayerRepository players;
    private final StrikeRepository strikes;
    private final WarnRepository warns;
    private final PunishmentService punishments;
    private final Playtime playtime;
    private final CoinsRepository coins;
    private final PlayerAddressRepository addresses;
    private final Duration addressRetention;
    private final Messages messages;
    private final DurationText durations;

    public WhoisCommand(CommandSupport support, PlayerLookup lookup, PlayerRepository players, StrikeRepository strikes,
                        WarnRepository warns, PunishmentService punishments, Playtime playtime, CoinsRepository coins,
                        PlayerAddressRepository addresses, Duration addressRetention, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.players = players;
        this.strikes = strikes;
        this.warns = warns;
        this.punishments = punishments;
        this.playtime = playtime;
        this.coins = coins;
        this.addresses = addresses;
        this.addressRetention = addressRetention;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("whois")
                .requires(CommandSupport.permission(SystemPermissions.TEAM))
                .executes(support.usage("whois.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> whois(source, StringArgumentType.getString(context, "player"))))));
    }

    private void whois(CommandSource source, String name) {
        var target = lookup.find(name);
        var known = target.flatMap(found -> players.byUuid(found.uuid()));
        if (target.isEmpty() || known.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        messages.getLines("whois.lines", placeholders(target.get(), known.get())).forEach(source::sendMessage);
    }

    private TagResolver placeholders(Target target, KnownPlayer known) {
        var history = punishments.history(target);
        return TagResolver.resolver(
                Placeholder.unparsed("player", known.name()),
                Placeholder.unparsed("uuid", known.uuid().toString()),
                Placeholder.unparsed("rank", ProxyRanks.primaryGroup(known.uuid()).orElseGet(() -> messages.raw("whois.unknown-rank"))),
                Placeholder.component("first", ago(known.firstSeen())),
                Placeholder.component("last", target.online().isPresent() ? messages.get("whois.now") : ago(known.lastSeen())),
                Placeholder.unparsed("playtime", durations.format(playtime.total(known))),
                Placeholder.component("server", target.online()
                        .<Component>map(player -> Component.text(CommandSupport.currentServer(player)))
                        .orElseGet(() -> messages.get("whois.offline"))),
                Placeholder.component("ban", punishment(known.name(), punishments.activeBan(target))),
                Placeholder.component("mute", punishment(known.name(), punishments.activeMute(target))),
                Formatter.number("bans", history.bans()),
                Formatter.number("mutes", history.mutes()),
                Formatter.number("strikes", strikes.count(known.uuid())),
                Formatter.number("warns", warns.count(known.uuid())),
                Placeholder.component("ping", target.online()
                        .map(player -> messages.get("whois.ping", Formatter.number("ping", player.getPing())))
                        .orElseGet(() -> messages.get("whois.offline"))),
                Placeholder.component("client", target.online()
                        .map(Player::getProtocolVersion)
                        .map(this::client)
                        .orElseGet(() -> messages.get("whois.offline"))),
                Formatter.number("coins", coins.get(known.uuid())),
                Placeholder.component("alts", alts(known)));
    }

    private Component client(ProtocolVersion version) {
        String first = version.getVersionIntroducedIn();
        String last = version.getMostRecentSupportedVersion();
        var name = first.equals(last) ? Component.text(first)
                : messages.get("whois.client-range", Placeholder.unparsed("first", first), Placeholder.unparsed("last", last));
        return messages.get("whois.client", Placeholder.component("version", name), Formatter.number("protocol", version.getProtocol()));
    }

    private Component alts(KnownPlayer known) {
        var count = Formatter.number("count", addresses.alts(known.uuid(), addressRetention).size());
        return messages.get("whois.alts", count)
                .clickEvent(ClickEvent.runCommand("/alts " + known.name()))
                .hoverEvent(HoverEvent.showText(messages.get("whois.alts-hover", Placeholder.unparsed("player", known.name()))));
    }

    private Component punishment(String name, Optional<Punishment> active) {
        return active.map(entry -> messages.get("whois.punishment", punishments.placeholders(name, entry)))
                .orElseGet(() -> messages.get("whois.none"));
    }

    private Component ago(Instant moment) {
        return messages.get("whois.ago", Placeholder.unparsed("time", durations.format(Duration.between(moment, Instant.now()))));
    }
}
