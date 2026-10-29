package me.blockcraft.replay;

import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.util.List;

final class ReplayMenu extends ListMenu<ReplayRepository.Entry> {

    private final ReplayService service;
    private final Messages messages;

    ReplayMenu(ReplayService service, Messages messages, List<ReplayRepository.Entry> entries) {
        super(rowsFor(entries.size()), messages.get("menu.title"));
        this.service = service;
        this.messages = messages;
        refreshButton();
        closeButton();
        show(entries);
    }

    @Override
    protected ItemStack icon(ReplayRepository.Entry entry) {
        var tags = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                Placeholder.unparsed("id", entry.id().toString()), Placeholder.unparsed("game", entry.game()),
                Placeholder.unparsed("map", entry.map()), Formatter.number("seconds", entry.durationTicks() / 20),
                Formatter.date("date", entry.startedAt().atZone(ZoneId.systemDefault())),
                Placeholder.component("result", messages.get(entry.endReason().equals("MATCH_END") ? "menu.complete" : "menu.partial")));
        return MenuIcon.RECAP.item().name(messages.get("menu.entry", tags)).lore(messages.getLines("menu.lore", tags)).build();
    }

    @Override
    protected Component empty() {
        return messages.get("menu.empty");
    }

    @Override
    protected void onSelect(Player player, ReplayRepository.Entry entry, InventoryClickEvent event) {
        service.play(player, entry.id());
    }

    @Override
    protected void reload(Player player) {
        loading();
        service.history(player, this::show, this::failed);
    }
}
