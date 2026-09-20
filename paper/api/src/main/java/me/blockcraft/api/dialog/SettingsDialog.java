package me.blockcraft.api.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One checkbox per on/off setting the viewer may choose; saving applies what changed and goes back to where the
 * dialog was opened from.
 */
public final class SettingsDialog {

    private final Dialog dialog;

    /** {@code back} is the menu this was opened from, or empty when the hotbar item or the pause menu opened it. */
    public SettingsDialog(Player viewer, Optional<Consumer<Player>> back) {
        var api = BlockcraftApi.get();
        var messages = api.messages();
        var toggles = PlayerSetting.toggles().stream()
                .filter(setting -> PlayerSettings.allowed(viewer, setting))
                .toList();
        var inputs = toggles.stream()
                .map(setting -> DialogInput.bool(inputKey(setting), messages.get("settings." + setting.key()))
                        .initial(api.settings().enabled(viewer, setting))
                        .build())
                .toList();
        var buttons = new ArrayList<ActionButton>();
        buttons.add(Dialogs.button(messages.get("settings.save"), (player, view) -> {
            save(player, view, toggles);
            back.ifPresent(previous -> previous.accept(player));
        }));
        buttons.add(Dialogs.button(messages.get("settings.pack-details"), player -> showPack(player, back)));
        dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("settings.title")).inputs(inputs).build())
                .type(DialogType.multiAction(buttons)
                        .exitAction(back.map(previous -> Dialogs.button(messages.get("menu.back"), previous))
                                .orElseGet(() -> Dialogs.button(messages.get("menu.close"))))
                        .build()));
    }

    public void open(Player player) {
        Dialogs.show(player, dialog);
    }

    private static void showPack(Player player, Optional<Consumer<Player>> back) {
        var api = BlockcraftApi.get();
        var messages = api.messages();
        var status = api.resourcePack().status(player);
        var actions = new ArrayList<ActionButton>();
        switch (status) {
            case FAILED, DECLINED, UNAVAILABLE -> actions.add(Dialogs.button(messages.get("settings.pack-retry"), retry -> {
                api.resourcePack().retry(retry);
                retry.sendMessage(messages.get("settings.pack-requested"));
            }));
            default -> { }
        }
        actions.add(Dialogs.button(messages.get("settings.pack-refresh"), refresh -> showPack(refresh, back)));
        var dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("settings.pack-details"))
                        .body(List.of(DialogBody.plainMessage(messages.get("settings.pack-status." + status.name().toLowerCase(java.util.Locale.ROOT)))))
                        .build())
                .type(DialogType.multiAction(actions)
                        .exitAction(Dialogs.button(messages.get("menu.back"), previous -> new SettingsDialog(previous, back).open(previous)))
                        .build()));
        Dialogs.show(player, dialog);
    }

    /** Dialog inputs are named like command macro variables, which allow no hyphen. */
    private static String inputKey(PlayerSetting setting) {
        return setting.key().replace('-', '_');
    }

    private static void save(Player player, DialogResponseView view, List<PlayerSetting> toggles) {
        var api = BlockcraftApi.get();
        var saves = new ArrayList<CompletableFuture<Boolean>>();
        for (var setting : toggles) {
            Boolean chosen = view.getBoolean(inputKey(setting));
            if (chosen == null) {
                continue;
            }
            if (chosen != api.settings().enabled(player, setting)) {
                saves.add(api.settings().set(player, setting, PlayerSetting.value(chosen)));
            }
        }
        Sounds.MENU_CLICK.play(player);
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).thenRun(() -> {
            if (player.isOnline() && saves.stream().allMatch(save -> save.getNow(false))) {
                player.sendMessage(api.messages().get("settings.saved"));
            }
        });
    }
}
