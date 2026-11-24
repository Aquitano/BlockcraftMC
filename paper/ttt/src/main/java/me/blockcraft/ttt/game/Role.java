package me.blockcraft.ttt.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum Role {
    TRAITOR(Faction.TRAITORS, NamedTextColor.RED),
    DETECTIVE(Faction.INNOCENTS, NamedTextColor.BLUE),
    INNOCENT(Faction.INNOCENTS, NamedTextColor.GREEN);

    private final Faction faction;
    private final NamedTextColor color;

    Role(Faction faction, NamedTextColor color) {
        this.faction = faction;
        this.color = color;
    }

    public Faction faction() {
        return faction;
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean hasShop() {
        return this != INNOCENT;
    }

    public boolean sameSide(Role other) {
        return faction == other.faction;
    }

    /** A traitor earns a point for every innocent or detective, a detective for every traitor; nobody else earns any. */
    public int shopPointsFor(Role victim) {
        return switch (this) {
            case TRAITOR -> victim.faction == Faction.INNOCENTS ? 1 : 0;
            case DETECTIVE -> victim == TRAITOR ? 1 : 0;
            case INNOCENT -> 0;
        };
    }

    public String key() {
        return name().toLowerCase();
    }

    /** "Traitor", "Detective" or "Innocent", in the role's colour. */
    public Component displayName(Messages messages) {
        return messages.get("roles." + key()).colorIfAbsent(color);
    }
}
