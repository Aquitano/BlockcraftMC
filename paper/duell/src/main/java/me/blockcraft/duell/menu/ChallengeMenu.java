package me.blockcraft.duell.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.duel.DuelRules;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public final class ChallengeMenu extends Menu {
    private final Duel duel;
    private final Messages messages;
    private final Consumer<DuelRules> send;
    private DuelMode mode;
    private int map;
    private boolean legacy = true;
    private boolean rated;
    private int bestOf = 1;
    private boolean submitted;

    public ChallengeMenu(Duel duel, Messages messages, ProxyMessage.DuelSetup setup,
                         Consumer<DuelRules> send, Consumer<Player> cancel) {
        super(4, messages.get("challenge.title", Placeholder.unparsed("player", setup.name())));
        this.duel = duel;
        this.messages = messages;
        this.send = send;
        mode = duel.offeredModes().stream().filter(candidate -> !duel.playableMaps(candidate).isEmpty()).findFirst().orElseThrow();
        map = duel.playableMaps(mode).getFirst();
        toolbar(ToolbarButton.LEFT, MenuIcon.BACK.item().name(messages.get("challenge.cancel")).build(), cancel);
        toolbar(ToolbarButton.MIDDLE, Items.head(setup.opponent(), setup.name()).name(messages.get("challenge.opponent",
                Placeholder.unparsed("player", setup.name()))).build(), ignored -> { });
        draw();
    }

    private void draw() {
        set(10, Items.of(Material.DIAMOND_SWORD).name(messages.get("challenge.mode",
                Placeholder.component("mode", messages.get("mode." + mode.configKey())))).lore(messages.get("challenge.choose")).build());
        set(12, Items.of(Material.FILLED_MAP).name(messages.get("challenge.map", Placeholder.unparsed("map", duel.mapName(mode, map))))
                .lore(messages.get("challenge.choose")).build());
        set(14, Items.of(legacy ? Material.IRON_SWORD : Material.SHIELD).name(messages.get(legacy ? "combat.legacy" : "combat.modern"))
                .lore(messages.get(mode == DuelMode.MLGRUSH ? "challenge.legacy-only" : "challenge.toggle")).build());
        set(16, Items.of(rated ? Material.IRON_INGOT : Material.FEATHER).name(messages.get(rated ? "rating.rated" : "rating.unrated"))
                .lore(messages.get(duel.ratedEnabled() ? "challenge.rating" : "challenge.rating-disabled")).build());
        set(21, Items.of(Material.PAPER, bestOf).name(messages.get("challenge.series", Formatter.number("games", bestOf)))
                .lore(messages.get("challenge.toggle")).build());
        set(23, Items.of(Material.LIME_DYE).name(messages.get("challenge.send"))
                .lore(messages.getLines("challenge.summary", Placeholder.unparsed("map", duel.mapName(mode, map)),
                        Placeholder.component("mode", messages.get("mode." + mode.configKey())),
                        Placeholder.component("combat", messages.get(legacy ? "combat.legacy" : "combat.modern")),
                        Placeholder.component("rating", messages.get(rated ? "rating.rated" : "rating.unrated")),
                        Formatter.number("games", bestOf))).build());
    }

    private void reopen(Player player) {
        draw();
        open(player, () -> !submitted && duel.isLobby() && duel.isParticipant(player));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack item, InventoryClickEvent event) {
        if (submitted || !duel.isLobby()) return;
        switch (slot) {
            case 10 -> new ListMenu<DuelMode>(3, messages.get("challenge.modes")) {
                { backButton(ChallengeMenu.this::reopen);
                    show(duel.offeredModes().stream().filter(candidate -> !duel.playableMaps(candidate).isEmpty()).toList()); }
                @Override protected ItemStack icon(DuelMode choice) {
                    return Items.of(Material.DIAMOND_SWORD).name(messages.get("mode." + choice.configKey())).build();
                }
                @Override protected void onSelect(Player viewer, DuelMode choice, InventoryClickEvent click) {
                    mode = choice;
                    map = duel.playableMaps(choice).getFirst();
                    if (mode == DuelMode.MLGRUSH) legacy = true;
                    reopen(viewer);
                }
            }.open(player, duel::isLobby);
            case 12 -> new ListMenu<Integer>(4, messages.get("challenge.maps")) {
                { backButton(ChallengeMenu.this::reopen); show(duel.playableMaps(mode)); }
                @Override protected ItemStack icon(Integer choice) {
                    return Items.of(Material.FILLED_MAP).name(net.kyori.adventure.text.Component.text(duel.mapName(mode, choice))).build();
                }
                @Override protected void onSelect(Player viewer, Integer choice, InventoryClickEvent click) {
                    map = choice;
                    reopen(viewer);
                }
            }.open(player, duel::isLobby);
            case 14 -> { if (mode != DuelMode.MLGRUSH) legacy = !legacy; draw(); }
            case 16 -> { if (duel.ratedEnabled()) rated = !rated; draw(); }
            case 21 -> { bestOf = bestOf == 1 ? 3 : 1; draw(); }
            case 23 -> {
                submitted = true;
                Menu.close(player);
                send.accept(new DuelRules(mode.configKey(), map, legacy, rated, bestOf));
            }
            default -> { }
        }
    }
}
