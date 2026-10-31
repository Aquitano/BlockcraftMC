package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.cosmetic.CosmeticReward;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.task.LatestRequests;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class ProgressionMenu extends ListMenu<Achievement> {

    enum State { OWNED, NEXT, LOCKED, PENDING }

    private final Profiles profiles;
    private final Player viewer;
    private final List<Achievement> rewards;
    private final Set<String> owned;
    private final Optional<Consumer<Player>> back;
    private final LatestRequests<PlayerSetting> selections = new LatestRequests<>();

    ProgressionMenu(Profiles profiles, Player viewer, List<Achievement> rewards, Set<String> owned,
                    Optional<Consumer<Player>> back) {
        super(Math.min(6, rowsFor(rewards.size()) + 1), profiles.messages().get("level.menu.title"));
        this.profiles = profiles;
        this.viewer = viewer;
        this.rewards = rewards;
        this.owned = Set.copyOf(owned);
        this.back = back;
        var messages = profiles.messages();
        var progress = profiles.xp().levels().progress(profiles.xp().total(viewer));
        toolbar(ToolbarButton.LEFT, MenuIcon.ACHIEVEMENTS.item()
                .name(messages.get("level.menu.level", Formatter.number("level", progress.level())))
                .lore(messages.getLines("level.menu.level-lore")).build(), ignored -> { });
        toolbar(ToolbarButton.MIDDLE, MenuIcon.ENCHANTING.item()
                .name(messages.get("level.menu.xp", Formatter.number("xp", progress.into()),
                        Formatter.number("needed", progress.needed())))
                .lore(messages.getLines("level.menu.xp-lore", Formatter.number("next", progress.level() + 1),
                        Formatter.number("missing", progress.needed() - progress.into()))).build(), ignored -> { });
        var next = nextReward(rewards, owned);
        toolbar(ToolbarButton.RIGHT, MenuIcon.WIN_EFFECTS.item()
                .name(messages.get("level.menu.next"))
                .lore(next.map(reward -> messages.getLines("level.menu.next-lore",
                                Placeholder.component("reward", reward.cosmetic().orElseThrow().name(messages)),
                                Formatter.number("level", reward.threshold())))
                        .orElseGet(() -> List.of(messages.get("level.cosmetics-complete"))))
                .build(), ignored -> { });
        back.ifPresentOrElse(this::backButton, this::closeButton);
        refreshButton();
        show(rewards);
    }

    static Optional<Achievement> nextReward(List<Achievement> rewards, Set<String> owned) {
        return rewards.stream().filter(reward -> reward.cosmetic().filter(cosmetic -> !owned.contains(cosmetic.item())).isPresent())
                .findFirst();
    }

    static State state(Achievement reward, int level, Set<String> owned, Optional<Achievement> next) {
        if (reward.cosmetic().filter(cosmetic -> owned.contains(cosmetic.item())).isPresent()) {
            return State.OWNED;
        }
        if (level >= reward.threshold()) {
            return State.PENDING;
        }
        return next.filter(reward::equals).isPresent() ? State.NEXT : State.LOCKED;
    }

    @Override
    protected ItemStack icon(Achievement milestone) {
        var reward = milestone.cosmetic().orElseThrow();
        var messages = profiles.messages();
        var state = state(milestone, profiles.xp().level(viewer), owned, nextReward(rewards, owned));
        boolean equipped = state == State.OWNED && equipped(reward);
        var lore = new ArrayList<>(messages.getLines("level.menu.reward-lore",
                Formatter.number("level", milestone.threshold()),
                Placeholder.component("kind", messages.get("level.menu.kind." + reward.setting().key()))));
        lore.add(messages.get(equipped ? "level.menu.equipped" : switch (state) {
            case OWNED -> "level.menu.owned";
            case NEXT -> "level.menu.next-locked";
            case LOCKED -> "level.menu.locked";
            case PENDING -> "level.menu.pending";
        }));
        var icon = Items.of(reward.icon()).name(reward.name(messages)).lore(lore)
                .state(equipped ? MenuState.ACTIVE : switch (state) {
                    case OWNED -> MenuState.OWNED;
                    case NEXT, LOCKED -> MenuState.LOCKED;
                    case PENDING -> MenuState.PENDING;
                });
        if (state == State.OWNED) {
            icon.glow();
        }
        return icon.build();
    }

    private boolean equipped(CosmeticReward reward) {
        return BlockcraftApi.get().settings().get(viewer, reward.setting()).filter(reward.value()::equals).isPresent();
    }

    @Override
    protected boolean selectable(Achievement milestone) {
        return milestone.cosmetic().filter(reward -> owned.contains(reward.item()) && !equipped(reward)
                && PlayerSettings.allowed(viewer, reward.setting())).isPresent();
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return slot >= ListLayout.COLUMNS;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return false;
    }

    @Override
    protected void onSelect(Player player, Achievement milestone, InventoryClickEvent event) {
        var reward = milestone.cosmetic().orElseThrow();
        if (equipped(reward)) {
            return;
        }
        var api = BlockcraftApi.get();
        var screen = api.playerTasks().currentScreen(player);
        var uuid = player.getUniqueId();
        var current = beginSelection(selections, reward.setting(),
                () -> screen.getAsBoolean() && profiles.plugin().getServer().getPlayer(uuid) == player);
        api.playerTasks().write(player, () -> api.unlocks().owns(uuid, reward.item()), unlocked -> {
            if (!current.getAsBoolean() || !unlocked || !PlayerSettings.allowed(player, reward.setting()) || equipped(reward)) {
                return;
            }
            api.settings().set(player, reward.setting(), reward.value()).thenAccept(saved -> Tasks.sync(profiles.plugin(), () -> {
                if (saved && current.getAsBoolean() && equipped(reward)) {
                    Sounds.EQUIP.play(player);
                    api.notifications().feedback(player, profiles.messages().get("level.menu.selected",
                            Placeholder.component("reward", reward.name(profiles.messages()))));
                }
            }));
        });
    }

    static BooleanSupplier beginSelection(LatestRequests<PlayerSetting> selections, PlayerSetting setting, BooleanSupplier active) {
        var revision = selections.begin(setting);
        return () -> active.getAsBoolean() && selections.isCurrent(setting, revision);
    }

    @Override
    protected void onSettingChanged(Player player, PlayerSetting setting) {
        if (rewards.stream().flatMap(reward -> reward.cosmetic().stream()).anyMatch(reward -> reward.setting() == setting)) {
            show(rewards);
        }
    }

    @Override
    protected void reload(Player player) {
        profiles.progression(player, back);
    }
}
