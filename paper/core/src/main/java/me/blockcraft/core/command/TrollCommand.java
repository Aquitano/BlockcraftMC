package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.staff.TrollMode;
import me.blockcraft.core.staff.TrollMode.Trick;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public final class TrollCommand {

    private final TrollMode trolls;
    private final StaffAudit audit;
    private final Messages messages;

    public TrollCommand(TrollMode trolls, StaffAudit audit, Messages messages) {
        this.trolls = trolls;
        this.audit = audit;
        this.messages = messages;
    }

    public LiteralCommandNode<CommandSourceStack> create() {
        var target = Commands.argument("player", ArgumentTypes.player())
                .executes(context -> {
                    open(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("stop").executes(context -> {
                    stop(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"));
                    return Command.SINGLE_SUCCESS;
                }));
        for (var trick : Trick.values()) {
            target.then(Commands.literal(trick.key()).executes(context -> {
                apply(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"), trick);
                return Command.SINGLE_SUCCESS;
            }));
        }
        return Commands.literal("troll")
                .requires(CorePermission.player("troll"))
                .executes(context -> {
                    players(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .then(target)
                .build();
    }

    private boolean permitted(Player actor, Player target) {
        if (!actor.hasPermission("blockcraft.core.troll")) {
            actor.sendMessage(messages.get("troll.denied"));
            return false;
        }
        if (!target.isOnline() || !actor.canSee(target)) {
            actor.sendMessage(messages.get("troll.offline"));
            return false;
        }
        return TargetGuard.mayTarget(actor, target, messages);
    }

    private void apply(Player actor, Player target, Trick trick) {
        if (!permitted(actor, target)) return;
        if (trolls.coolingDown(target)) {
            actor.sendMessage(messages.get("troll.cooldown"));
            return;
        }
        if (!trolls.apply(actor, target, trick)) {
            actor.sendMessage(messages.get("troll.effect-present"));
            return;
        }
        audit.record(actor, "troll." + trick.key(), target);
        actor.sendMessage(messages.get("troll.applied", Placeholder.unparsed("player", target.getName()),
                Placeholder.component("effect", messages.get("troll.effects." + trick.key() + ".name"))));
    }

    private void stop(Player actor, Player target) {
        if (!permitted(actor, target)) return;
        trolls.stop(target);
        audit.record(actor, "troll.stop", target);
        actor.sendMessage(messages.get("troll.stopped", Placeholder.unparsed("player", target.getName())));
    }

    private void players(Player actor) {
        if (!actor.hasPermission("blockcraft.core.troll")) return;
        new ListMenu<Player>(4, messages.get("troll.players-title")) {
            { closeButton(); refreshButton(); reload(actor); }

            @Override protected void reload(Player viewer) {
                var online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
                online.removeIf(target -> !viewer.canSee(target));
                online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
                show(online);
            }

            @Override protected ItemStack icon(Player entry) {
                return Items.head(entry.getUniqueId(), entry.getName()).name(messages.get("troll.player",
                                Placeholder.unparsed("player", entry.getName())))
                        .lore(messages.get("troll.choose")).build();
            }

            @Override protected void onSelect(Player viewer, Player entry, InventoryClickEvent event) {
                TrollCommand.this.open(viewer, entry);
            }
        }.open(actor, () -> actor.hasPermission("blockcraft.core.troll"));
    }

    private void open(Player actor, Player target) {
        if (!permitted(actor, target)) return;
        var tricks = Arrays.asList(Trick.values());
        new ListMenu<Trick>(4, messages.get("troll.title", Placeholder.unparsed("player", target.getName()))) {
            {
                backButton(TrollCommand.this::players);
                set(lastRowSlot(), Items.of(Material.MILK_BUCKET).name(messages.get("troll.stop"))
                        .lore(messages.get("troll.stop-lore")).build());
                show(tricks);
            }

            @Override protected ItemStack icon(Trick entry) {
                return Items.of(entry.material()).name(messages.get("troll.effects." + entry.key() + ".name"))
                        .lore(messages.get("troll.effects." + entry.key() + ".lore"), messages.get("troll.trigger")).build();
            }

            @Override protected void onSelect(Player viewer, Trick entry, InventoryClickEvent event) {
                apply(viewer, target, entry);
            }

            @Override protected void onButton(Player viewer, int slot, InventoryClickEvent event) {
                if (slot == lastRowSlot()) stop(viewer, target);
            }
        }.open(actor, () -> actor.hasPermission("blockcraft.core.troll") && target.isOnline() && actor.canSee(target));
    }
}
