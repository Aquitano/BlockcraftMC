package me.blockcraft.storyexample;

import me.blockcraft.api.item.Items;
import me.blockcraft.story.Story;
import me.blockcraft.story.game.Spots;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** A whole story game on the core: the mission is in this module's resources and quest package, and this class only equips its players. */
public final class StoryExamplePlugin extends JavaPlugin {

    private Story story;

    @Override
    public void onEnable() {
        story = Story.start(this, this::equip);
    }

    private void equip(Player member) {
        var signal = Spots.parse(member.getWorld(), getConfig().getString("signal"));
        member.getInventory().setItem(0, Items.of(Material.COMPASS).name(story.messages().get("item.compass"))
                .edit(meta -> {
                    var compass = (CompassMeta) meta;
                    compass.setLodestone(signal);
                    compass.setLodestoneTracked(false);
                }).build());
    }

    @Override
    public void onDisable() {
        if (story != null) story.stop();
    }
}
