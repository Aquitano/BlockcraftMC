package me.blockcraft.lobby.daily;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Today's daily reward: the streak, what today pays and what the next days pay, with a button that claims it. Once
 * claimed it says how long until the next one.
 */
public final class DailyRewardDialog {

    private final Messages messages;
    private final DailyRewards rewards;
    private final DailyRewards.Today today;
    private final Duration untilNext;
    private final Consumer<Player> claim;

    public DailyRewardDialog(Messages messages, DailyRewards rewards, DailyRewards.Today today, Duration untilNext,
                             Consumer<Player> claim) {
        this.messages = messages;
        this.rewards = rewards;
        this.today = today;
        this.untilNext = untilNext;
        this.claim = claim;
    }

    public void open(Player player) {
        var streak = Formatter.number("streak", today.streak());
        var coins = Formatter.number("coins", today.coins());
        var body = new ArrayList<DialogBody>();
        if (today.claimed()) {
            body.add(DialogBody.plainMessage(messages.get("daily.streak-claimed", streak)));
            body.add(DialogBody.plainMessage(messages.get("daily.next-in",
                    Formatter.number("hours", untilNext.toHours()), Formatter.number("minutes", untilNext.toMinutesPart()))));
        } else {
            body.add(DialogBody.plainMessage(messages.get("daily.streak-open", streak)));
            body.add(DialogBody.plainMessage(messages.get("daily.today", coins)));
        }
        if (rewards.previewDays() > 0) {
            body.add(DialogBody.plainMessage(Component.empty()));
            body.add(DialogBody.plainMessage(messages.get("daily.upcoming")));
            for (int day = today.streak() + 1; day <= today.streak() + rewards.previewDays(); day++) {
                body.add(DialogBody.plainMessage(messages.get("daily.upcoming-day",
                        Formatter.number("day", day), Formatter.number("coins", rewards.coins(day)))));
            }
        }
        body.add(DialogBody.plainMessage(messages.get("daily.missed")));
        List<ActionButton> buttons = today.claimed() ? List.of() : List.of(Dialogs.button(messages.get("daily.claim", coins), claim));
        var dialog = Dialogs.create(messages.get("daily.title"), body, List.of(),
                Dialogs.actions(buttons, Dialogs.button(BlockcraftApi.get().messages().get("menu.close"))));
        Dialogs.show(player, dialog);
    }
}
