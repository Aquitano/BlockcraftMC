package me.blockcraft.reports.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.reports.ReportsPlugin;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentCatalogRepository;
import me.blockcraft.common.network.operation.NetworkRequest.PresetPunishment;
import me.blockcraft.common.network.operation.NetworkRequest.TimedPunishment;
import me.blockcraft.common.report.Report;
import me.blockcraft.common.report.ReportRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens the report menus and routes their actions; {@code reported} filters a list to one accused player. Moving
 * to a player runs {@code /goto} on the proxy, punishing one is a typed request to it, so the proxy answers both.
 */
public final class ReportMenus {

    private final Plugin plugin;
    private final ReportRepository reports;
    private final PunishmentCatalogRepository catalog;
    private final Messages messages;

    public ReportMenus(Plugin plugin, ReportRepository reports, PunishmentCatalogRepository catalog, Messages messages) {
        this.plugin = plugin;
        this.reports = reports;
        this.catalog = catalog;
        this.messages = messages;
    }

    public void openList(Player player, Optional<String> reported, int page) {
        if (!allowed(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> reported.map(reports::byReported).orElseGet(reports::all), list -> {
            if (!allowed(player)) {
                return;
            }
            if (list.isEmpty()) {
                player.sendMessage(reported.map(name -> messages.get("none-for-player", Placeholder.unparsed("player", name)))
                        .orElseGet(() -> messages.pick("none")));
                return;
            }
            new ReportListMenu(this, messages, list, reported, page).open(player);
        });
    }

    public void openReport(Player player, Report report, Optional<String> reported) {
        if (!allowed(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> reports.byId(report.id()), current -> {
            if (!allowed(player)) {
                return;
            }
            current.ifPresentOrElse(updated ->
                            new ReportMenu(this, messages, updated, reported, player.getUniqueId(), BlockcraftApi.get().realName(player)).open(player),
                    () -> openList(player, reported, 1));
        });
    }

    /** The claim is guarded in SQL, so a lost race simply reopens the report with whoever actually holds it. */
    public void toggleClaim(Player player, Report report, Optional<String> reported) {
        if (!allowed(player)) {
            return;
        }
        String staff = BlockcraftApi.get().realName(player);
        BlockcraftApi.get().playerTasks().write(player, () -> {
            if (report.isClaimedBy(player.getUniqueId(), staff)) {
                reports.release(report.id(), player.getUniqueId(), staff);
            } else {
                reports.claim(report.id(), player.getUniqueId(), staff);
            }
            return reports.byId(report.id());
        }, current -> {
            if (!allowed(player)) {
                return;
            }
            current.ifPresentOrElse(updated -> {
                player.sendMessage(messages.get(claimResult(updated, player.getUniqueId(), staff), placeholders(messages, updated)));
                openReport(player, updated, reported);
            }, () -> openList(player, reported, 1));
        });
    }

    private static String claimResult(Report report, UUID staffId, String staff) {
        if (report.isClaimedBy(staffId, staff)) {
            return "claim.taken";
        }
        return report.claimedBy().isPresent() ? "claim.held" : "claim.released";
    }

    public void delete(Player player, Report report, Optional<String> reported) {
        if (!allowed(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().write(player, () -> {
            reports.delete(report.id());
            return report;
        }, deleted -> openList(player, reported, 1));
    }

    public void goTo(Player player, String target) {
        if (!allowed(player)) {
            return;
        }
        Menu.close(player);
        ProxyMessaging.runOnProxy(plugin, player, "goto " + target);
    }

    public void openPunish(Player player, Report report, Optional<String> reported) {
        if (!allowed(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, catalog::all, entries -> {
            if (!allowed(player)) {
                return;
            }
            new PunishDialog(this, messages, report, reported, entries).open(player);
        });
    }

    public void punish(Player player, Report report, PunishmentCatalogRepository.Entry entry) {
        if (!allowed(player)) {
            return;
        }
        BlockcraftApi.get().operations().submit(player, new PresetPunishment(report.target(), entry.number()));
    }

    /** A punishment outside the catalogue; the proxy checks rank and permission and answers, so only the form is checked here. */
    void punish(Player player, Report report, Optional<String> reported, PunishDialog.Custom custom) {
        if (!allowed(player)) {
            return;
        }
        if (custom == null) {
            player.sendMessage(messages.get("punish.invalid"));
            openPunish(player, report, reported);
            return;
        }
        if (custom.reason().isEmpty() || custom.totalHours() <= 0) {
            player.sendMessage(messages.get(custom.reason().isEmpty() ? "punish.reason-missing" : "punish.duration-missing"));
            openPunish(player, report, reported);
            return;
        }
        BlockcraftApi.get().operations().submit(player, new TimedPunishment(report.target(), custom.type(),
                Duration.ofHours(custom.totalHours()).toSeconds(), custom.reason()));
    }

    public void promptStatus(Player player, Report report, Optional<String> reported) {
        if (!allowed(player)) {
            return;
        }
        new StatusDialog(this, messages, report, reported).open(player);
    }

    public void setStatus(Player player, Report report, Optional<String> reported, String status) {
        if (!allowed(player)) {
            return;
        }
        if (status == null || status.isBlank() || status.length() > 100 || status.chars().anyMatch(Character::isISOControl)) {
            player.sendMessage(messages.get("status.cancelled"));
            openReport(player, report, reported);
            return;
        }
        BlockcraftApi.get().playerTasks().write(player, () -> {
            reports.updateStatus(report.id(), status);
            return report.withStatus(status);
        }, updated -> {
            player.sendMessage(messages.get("status.updated", Formatter.number("id", updated.id()),
                    Placeholder.component("status", status(updated))));
            openReport(player, updated, reported);
        });
    }

    private boolean allowed(Player player) {
        if (!player.isOnline()) {
            return false;
        }
        if (player.hasPermission(ReportsPlugin.MENU_PERMISSION)) {
            return true;
        }
        player.sendMessage(messages.get("no-permission"));
        return false;
    }

    /** Statuses were historically typed with {@code &} colour codes, so stored values keep rendering that way. */
    static Component status(Report report) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(report.status());
    }

    static TagResolver placeholders(Messages messages, Report report) {
        return TagResolver.resolver(
                Formatter.number("id", report.id()),
                Placeholder.unparsed("player", report.reported()),
                Placeholder.unparsed("reporter", report.reporter()),
                Placeholder.unparsed("reason", report.reason()),
                Placeholder.unparsed("server", report.server()),
                Placeholder.component("status", status(report)),
                Placeholder.component("age", Age.since(messages, report.createdAt())),
                Placeholder.component("claimer", report.claimedBy()
                        .map(holder -> messages.get("menu.claimed-by", Placeholder.unparsed("staff", holder)))
                        .orElseGet(() -> messages.get("menu.unclaimed"))));
    }

}
