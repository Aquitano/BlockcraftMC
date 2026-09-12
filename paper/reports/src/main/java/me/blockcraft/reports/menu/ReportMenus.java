package me.blockcraft.reports.menu;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.Report;
import me.blockcraft.common.report.ReportRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opens the report menus and routes their actions; {@code reported} filters a list to one accused player. */
public final class ReportMenus {

    private final Plugin plugin;
    private final ReportRepository reports;
    private final Messages messages;
    private final Map<UUID, StatusPrompt> statusPrompts = new ConcurrentHashMap<>();

    private record StatusPrompt(Report report, Optional<String> reported) {
    }

    public ReportMenus(Plugin plugin, ReportRepository reports, Messages messages) {
        this.plugin = plugin;
        this.reports = reports;
        this.messages = messages;
    }

    public void openList(Player player, Optional<String> reported, int page) {
        Tasks.async(plugin, () -> reported.map(reports::byReported).orElseGet(reports::all), list -> {
            if (list.isEmpty()) {
                player.sendMessage(reported.map(name -> messages.get("none-for-player", Placeholder.unparsed("player", name)))
                        .orElseGet(() -> messages.get("none")));
                return;
            }
            new ReportListMenu(this, messages, list, reported, page).open(player);
        });
    }

    public void openReport(Player player, Report report, Optional<String> reported) {
        new ReportMenu(this, messages, report, reported).open(player);
    }

    public void delete(Player player, Report report, Optional<String> reported) {
        player.closeInventory();
        Tasks.async(plugin, () -> {
            reports.delete(report.id());
            return report;
        }, deleted -> openList(player, reported, 1));
    }

    public void sendFindLink(Player player, String target) {
        player.closeInventory();
        player.sendMessage(messages.get("find", Placeholder.unparsed("player", target))
                .clickEvent(ClickEvent.runCommand("/find " + target)));
    }

    public void promptStatus(Player player, Report report, Optional<String> reported) {
        player.closeInventory();
        statusPrompts.put(player.getUniqueId(), new StatusPrompt(report, reported));
        player.sendMessage(messages.get("status.prompt"));
    }

    /** Runs on the chat thread; returns whether the message was consumed as a status answer. */
    public boolean answerStatusPrompt(Player player, String text) {
        var prompt = statusPrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return false;
        }
        if (text.equalsIgnoreCase("abbrechen")) {
            Tasks.sync(plugin, () -> {
                player.sendMessage(messages.get("status.cancelled"));
                openReport(player, prompt.report(), prompt.reported());
            });
            return true;
        }
        reports.updateStatus(prompt.report().id(), text);
        var updated = prompt.report().withStatus(text);
        Tasks.sync(plugin, () -> {
            player.sendMessage(messages.get("status.updated", Formatter.number("id", updated.id()),
                    Placeholder.component("status", status(updated))));
            openReport(player, updated, prompt.reported());
        });
        return true;
    }

    public void forgetStatusPrompt(Player player) {
        statusPrompts.remove(player.getUniqueId());
    }

    /** Statuses were historically typed with {@code &} colour codes, so stored values keep rendering that way. */
    static Component status(Report report) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(report.status());
    }

    static TagResolver placeholders(Report report) {
        return TagResolver.resolver(
                Formatter.number("id", report.id()),
                Placeholder.unparsed("player", report.reported()),
                Placeholder.unparsed("reporter", report.reporter()),
                Placeholder.unparsed("reason", report.reason()),
                Placeholder.unparsed("server", report.server()),
                Placeholder.component("status", status(report)));
    }

}
