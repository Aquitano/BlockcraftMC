package me.blockcraft.lobby.activity;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.SpotEntities;

/** The prisoner in the castle's dungeon, at the map's {@code prisoner} spot: he complains to whoever visits him. */
public final class Prisoner implements AutoCloseable {

    private static final String SPOT = "prisoner";

    private final Locations<String> locations;
    private final Messages messages;
    private final SpotEntities.Group<String> prisoner;

    public Prisoner(SpotEntities entities, Locations<String> locations, Messages messages) {
        this.locations = locations;
        this.messages = messages;
        prisoner = entities.group((player, spot) -> {
            Sounds.CHAINS.play(player);
            player.sendMessage(messages.pick("prisoner.talk"));
        }, (player, spot) -> player.sendMessage(messages.pick("prisoner.hurt")));
        refresh();
    }

    public void refresh() {
        close();
        locations.get(SPOT).ifPresent(cell -> prisoner.add(SPOT, cell, Figures.spawn(cell, Skins.PRISONER, messages.get("prisoner.name"))));
    }

    @Override
    public void close() {
        prisoner.close();
    }
}
