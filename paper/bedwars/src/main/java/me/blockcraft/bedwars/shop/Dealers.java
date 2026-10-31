package me.blockcraft.bedwars.shop;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.bedwars.lobby.Figures;
import me.blockcraft.common.message.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** The figures that open the shop when a player in the round right-clicks them, wearing the original dealer's skin. */
public final class Dealers implements Listener {

    private static final ResolvableProfile SKIN = ResolvableProfile.resolvableProfile()
            .name("Dealer")
            .addProperty(new ProfileProperty("textures",
                    "ewogICJ0aW1lc3RhbXAiIDogMTcwOTI4ODE5Mzc1NiwKICAicHJvZmlsZUlkIiA6ICJmNjgwMmEzZTkyNjk0YzI1ODk5YzE2MjMxZTlkYjMyYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzY2hvZW5zdGVuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzZlYzc2YzVlMDllYzc3NzY0YzFmNTcxZjk5NmIxYzE4NzU1ZjgyYmQwMDIzM2MxYTUwNGFhODk2MDIwMmNmNTkiCiAgICB9CiAgfQp9",
                    "Dy6YAyn5flLEta6kWc7UPevtc2cFvCu7QWS3T0uiIuC3KDTM7N7AGMos7W9OZYbYj405pnF2fmJX8ipT/P2bxe/HsuUcheUor7U/xDJzfTvt5zVSvdUNtzZ+crTGnUQFH7MkBPuxfe8NbvpAz0loTiMpPChVtSjKOnggcIv8uycKuNFcKPzWNK8PoUFMIqwwTnGkLbiXicMxHv6/PIr5MEVrKyX2T1xbWiFF9x7wqmVFnYwnfM7DXuJP8itMnJcCOIT9VSrRN1o1fMscvG8F8ZNKxWFIz4aCFkG3soawN0DI6ExP+C4kzgv7WjEvd3q3AylydK1XeGnsY76UwnLiwHMwgakKhodPQ1hQ6v3lE6Ex79KRvdOzVFc3SOQavBTEJ8Yf9SR0VMC9IaqeSzKkM6r93A4uAqaZicoU/4ZnxCIjgAmqkZjpW5idTDyqyWfLZbgAOrxaZJ0qqA5070RW7dATez+nd6sBpF1acSG7eiSdoUq3flOzkqRExKsRY3umioF5/D/whHnai7pNzYV6WmbOfpcnQDhNXlyhWeGR9id6lu4ZsCd3IB76Oh+uH13K66oYNQ73QTXPul0hvSlsLACKapGiQqKi8js42HYPWOecc3n/ooj9o6hnHuNFZ4gf9GXzBx7lWOLhdWobxb/BMmjGObHKL4iT4OqEQnYpbew="))
            .build();

    private final Messages messages;
    private final Shop shop;
    private final Predicate<Player> mayShop;
    private final Set<UUID> dealers = new HashSet<>();

    public Dealers(Messages messages, Shop shop, Predicate<Player> mayShop) {
        this.messages = messages;
        this.shop = shop;
        this.mayShop = mayShop;
    }

    public Mannequin spawn(Location location) {
        var dealer = Figures.spawn(location, SKIN, messages.get("dealer.name"));
        dealers.add(dealer.getUniqueId());
        return dealer;
    }

    public void remove(Entity dealer) {
        dealers.remove(dealer.getUniqueId());
        dealer.remove();
    }

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent event) {
        if (!dealers.contains(event.getRightClicked().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.HAND && mayShop.test(player)) {
            Sounds.SHOP_TALK.play(player);
            shop.open(player);
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (dealers.contains(event.getAttacked().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
