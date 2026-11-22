# Star Wars models

The three source scenes provide five creatures and nine weapons. The separate Nexu file is a smaller, different model,
retained as `nexu_cub`. The monster scene contains `acklay`, `rancor`, `nexu`, and `reek`.
The weapons are `ca87`, `dl44`, `e11`, `ee3`, `ee4`, `amban`, `nn14`, `rocket`, and `thermal`.

## Use the models

Install `StarWarsModels`, BlockcraftAPI, LuckPerms, and BetterModel 3.6.0 on Paper 26.3.
Stage the five `resourcepack/bettermodel/sw_*.bbmodel` files in `BetterModel/models` and distribute the network resource pack.
Nexo is optional for the runtime plugin. It provides placeable display versions through `nexo/items/blockcraft_starwars.yml`.

An operator can use these commands:

```text
/arsenal give e11
/arsenal spawn nexu
/arsenal clear
```

`give` uses the fifth hotbar slot and refuses to replace an unrelated item.
Right click fires. Sneaking reduces spread. Heat and cooldowns prevent unrestricted firing.
The CA-87 fires eight pellets. The EE-3 fires a three-shot burst. Rockets travel before impact, and thermal detonators
bounce before their fuse expires. Explosions damage imported creatures without changing blocks or damaging players.
The practice weapons target creatures created by this plugin. Sternenfront separately uses the EE-3 for its carbine,
the DL-44 for its pistol, and the thermal detonator for its grenade. Its existing combat, stats, heat, aiming,
recoil, sprint poses, and droid weapons remain in use. The practice firing code does not run those loadouts.

Spawned creatures use Husk pursuit and melee AI with their configured health, damage, and speed.
BetterModel supplies their five animation clips and hitboxes. They do not drop vanilla zombie loot.
World protections still control damage. The lobby therefore cannot serve as a hostile combat arena.
The command-created creatures are temporary and are removed when the plugin stops.

## Configure a practice gallery

Set `practice.enabled`, its world and bounds, and an `exhibits` list in the plugin's `config.yml`.
Each exhibit specifies `kind`, `x`, `y`, and `z`. All exhibits must be inside the practice area.
They are stationary animated targets, with separate practice health that resets after defeat.

Inside that area, visitors can run `/arsenal test e11` or right-click a weapon's Nexo display to borrow it.
Loans cannot be dropped or stored and are removed on exit or logout. They only score hits against practice targets.
The ordinary furniture gallery and lobby protections remain active.

## Configure passive kit actors

Stage `resourcepack/bettermodel/kit_*.bbmodel` in `BetterModel/models`. Keep the generated
`assets/bettermodel/items/model/kit_*.json` routes after merging BetterModel's pack. These routes use the optimized
native cuboid parts from `KitActors.py`.

Set `scenery.world` and add entries to `scenery.exhibits` in `config.yml`:

```yaml
scenery:
  world: world
  exhibits:
    - kind: astromech
      x: 0.5
      y: 64
      z: 0.5
      yaw: 180
      animation: idle
```

Kinds are `astromech`, `gonk`, `mouse`, `bantha`, `dewback` and `womp_rat`. Animation is `idle` or `walk`.
These are stationary, invulnerable exhibition carriers without AI, damage hitboxes, movement collision or riding.
Their chunks stay loaded until the plugin stops. Carriers are temporary and are recreated from configuration.

## Edit and validate the assets

The imported creatures and blasters are authoritative native Minecraft models, textures and BetterModel rigs.
Their identifiers and bone names are in `resourcepack/catalogs/starwars.json`. Edit the finished files directly;
the original GLBs and one-time converters are retired. Preserve the optimized bone item routes when importing
another BetterModel export.

Validate and package them with:

```sh
./gradlew :resourcepack:verifyAssets :resourcepack:pack
```

To regenerate the authored bodies and their matching rigs, use `:resourcepack:generateRigModels`.
It runs the body generator first in an isolated workspace, validates the complete result, and applies changed files.
The native weapons in `resourcepack/finished-weapons.txt` and their pose variants are preserved.
For a preview before applying, run `uv run --locked python assets.py generate rigs` from `resourcepack`.
The [asset workflow](../../resourcepack/README.md#edit-and-validate-assets) describes review and application.

BetterModel plays the saved animation clips. The static Nexo displays use one item display each.
The temporary exhibition is retired. Production loads this plugin in Sternenfront, with practice and scenery exhibits disabled by default.


## Verification status

The full Gradle build passed. A Minecraft 26.3 client rendered all five corrected creatures, the Amban and NN-14,
and a borrowed E-11. The Nexu practice target took damage, reached zero health, reset, and accepted another hit.
The Sternenfront soldier and medic equipped the EE-3 and DL-44 in real rounds. Ready and aimed views, firing,
heat feedback, and the imported bot weapon were checked. The droid weapons remain unchanged. The later reference batch updates the B2 body.
Hostile AI and projectile behavior still need gameplay verification. This batch is deployed to the private test hub only.

The reference batch adds `/arsenal test a280`, `/arsenal test e11d`, and `/arsenal test dc17m` in the test gallery. These remain practice weapons; Sternenfront uses its own combat profiles and controls.
