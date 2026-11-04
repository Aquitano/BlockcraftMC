package me.blockcraft.sternenfront.hero;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.Faction;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Who a player can fight as beyond the four classes. Each faction fields a lightsaber hero and a gun hero, bought
 * with battle points in the deploy menu; the champion is whoever carries the relic blade and is never bought.
 */
public enum Hero {
    JEDI(Faction.KOLONIEN, new Weapon.Saber(PackModel.STERNENFRONT_LIGHTSABER_JEDI, PackModel.STERNENFRONT_LIGHTSABER_JEDI_OFF),
            Optional.of(PackModel.STERNENFRONT_HOOD_JEDI), List.of(HeroAbility.FORCE_PUSH, HeroAbility.SABER_THROW)),
    BOUNTY_HUNTER(Faction.KOLONIEN, new Weapon.Gun(Blaster.TWIN_PISTOL),
            Optional.of(PackModel.STERNENFRONT_HELMET_BOUNTY_HUNTER), List.of(HeroAbility.JETPACK, HeroAbility.GRAPPLE)),
    SITH(Faction.HEGEMONIE, new Weapon.Saber(PackModel.STERNENFRONT_LIGHTSABER_SITH, PackModel.STERNENFRONT_LIGHTSABER_SITH_OFF),
            Optional.of(PackModel.STERNENFRONT_MASK_SITH), List.of(HeroAbility.SABER_THROW, HeroAbility.FORCE_GRIP)),
    COMMANDER(Faction.HEGEMONIE, new Weapon.Gun(Blaster.HEAVY_RIFLE),
            Optional.of(PackModel.STERNENFRONT_CAP_COMMANDER), List.of(HeroAbility.ORBITAL_STRIKE, HeroAbility.RALLY)),
    CHAMPION(null, new Weapon.RelicBlade(), Optional.empty(), List.of());

    /** What a hero fights with: a lightsaber, lit or not, a blaster of its own, or the relic blade. */
    public sealed interface Weapon {

        PackModel model();

        record Saber(PackModel lit, PackModel off) implements Weapon {

            @Override
            public PackModel model() {
                return lit;
            }
        }

        record Gun(Blaster blaster) implements Weapon {

            @Override
            public PackModel model() {
                return blaster.model();
            }
        }

        record RelicBlade() implements Weapon {

            @Override
            public PackModel model() {
                return PackModel.STERNENFRONT_RELIC_BLADE;
            }
        }
    }

    private final Faction faction;
    private final Weapon weapon;
    private final Optional<PackModel> head;
    private final List<HeroAbility> abilities;

    Hero(Faction faction, Weapon weapon, Optional<PackModel> head, List<HeroAbility> abilities) {
        this.faction = faction;
        this.weapon = weapon;
        this.head = head;
        this.abilities = abilities;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The faction that can buy this hero; the champion belongs to whoever takes the relic. */
    public Optional<Faction> faction() {
        return Optional.ofNullable(faction);
    }

    public Weapon weapon() {
        return weapon;
    }

    /** Worn instead of the faction's helmet; the champion keeps the helmet they deployed in. */
    public Optional<PackModel> head() {
        return head;
    }

    public List<HeroAbility> abilities() {
        return abilities;
    }

    /** Includes the relic champion in melee, guard and Force-leap controls. */
    public boolean bladed() {
        return !(weapon instanceof Weapon.Gun);
    }

    public static List<Hero> of(Faction faction) {
        return Arrays.stream(values()).filter(hero -> hero.faction == faction).toList();
    }
}
