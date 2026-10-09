package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.LobbyPermissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class CosmeticMenu<C> extends Menu {

    private final Menus menus;
    private final CosmeticKind<C> kind;
    private final Set<String> owned;

    CosmeticMenu(Menus menus, CosmeticKind<C> kind, Player viewer, Set<String> owned) {
        super(kind.layout().rows(), kind.title());
        this.menus = menus;
        this.kind = kind;
        this.owned = new HashSet<>(owned);
        refresh(viewer);
        backButton(kind.layout().backSlot(), kind.back());
    }

    private void refresh(Player player) {
        var messages = menus.messages();
        var choices = kind.choices();
        for (int slot = 0; slot < choices.size(); slot++) {
            var choice = choices.get(slot);
            boolean equipped = equipped(player, choice);
            var lore = new ArrayList<>(kind.description().apply(choice));
            lore.add(owns(choice)
                    ? messages.get(equipped ? "cosmetics.equipped" : "cosmetics.owned")
                    : messages.get("cosmetics.locked", Formatter.number("coins", kind.price().orElseThrow().coins())));
            kind.preview().ifPresent(preview -> lore.add(messages.get(menus.settings().enabled(player, PlayerSetting.EFFECTS)
                    ? preview.loreKey() : "cosmetics.preview-disabled")));
            var icon = Items.of(kind.picture().apply(choice)).name(kind.name().apply(choice)).lore(lore);
            if (equipped) {
                icon.glow();
            }
            set(slot, icon.build());
        }
        set(kind.layout().removeSlot(), Items.of(Material.BARRIER).name(messages.get("cosmetics.remove"))
                .lore(messages.get(selected(player).isPresent() ? "cosmetics.remove-lore" : "cosmetics.empty-remove")).build());
    }

    private Optional<String> selected(Player player) {
        return menus.settings().get(player, kind.setting());
    }

    private boolean equipped(Player player, C choice) {
        return selected(player).filter(kind.key().apply(choice)::equals).isPresent()
                && kind.live().map(live -> live.present().test(player, choice)).orElse(true);
    }

    private boolean owns(C choice) {
        return kind.price().map(price -> owned.contains(price.item().apply(choice))).orElse(true);
    }

    private Optional<C> choiceAt(int slot) {
        return slot >= 0 && slot < kind.choices().size() ? Optional.of(kind.choices().get(slot)) : Optional.empty();
    }

    private boolean previews(InventoryClickEvent event) {
        return event.isRightClick() && kind.preview().isPresent();
    }

    @Override
    protected void onSettingChanged(Player player, PlayerSetting setting) {
        if (setting == kind.setting() || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == kind.layout().removeSlot()) {
            return selected(player).isPresent();
        }
        return choiceAt(slot).map(choice -> {
            if (kind.premium() && !player.hasPermission(LobbyPermissions.PREMIUM)) {
                return false;
            }
            return previews(event) ? menus.settings().enabled(player, PlayerSetting.EFFECTS) : !equipped(player, choice);
        }).orElse(true);
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return slot == kind.layout().removeSlot() || !previews(event) && choiceAt(slot).filter(this::owns).isPresent()
                ? Sounds.EQUIP : Sounds.MENU_CLICK;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == kind.layout().removeSlot()) {
            menus.settings().clear(player, kind.setting());
            BlockcraftApi.get().notifications().feedback(player, kind.removed());
            return;
        }
        var target = choiceAt(slot);
        if (target.isEmpty()) {
            return;
        }
        var choice = target.get();
        if (previews(event)) {
            kind.preview().orElseThrow().action().show(player, choice, this::reopen);
            return;
        }
        if (owns(choice)) {
            select(player, choice);
            return;
        }
        var price = kind.price().orElseThrow();
        String item = price.item().apply(choice);
        menus.purchase(player, clicked, item, price.coins(), buyer -> owned.add(item), buyer -> {
            if (owned.contains(item)) {
                select(buyer, choice);
            }
            reopen(buyer);
        });
    }

    private void reopen(Player player) {
        refresh(player);
        open(player);
    }

    private void select(Player player, C choice) {
        var live = kind.live().filter(ignored -> selected(player).filter(kind.key().apply(choice)::equals).isPresent());
        if (live.isPresent()) {
            live.get().restore().accept(player, choice);
            refresh(player);
        } else {
            menus.settings().set(player, kind.setting(), kind.key().apply(choice));
        }
        BlockcraftApi.get().notifications().feedback(player, kind.selected().apply(choice));
    }
}
