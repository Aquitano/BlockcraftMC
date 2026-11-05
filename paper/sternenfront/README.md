# Sternenfront

The displayed Clone Wars mode retains the `sternenfront` plugin, server, permission, and storage identities.

## Compatibility

`kolonien` identifies the Republic and `hegemonie` the Separatists in arena metadata, configuration, and player
settings. The hero keys `jedi`, `commander`, `sith`, and `bounty-hunter` identify Obi-Wan, Rex, Dooku, and Jango.
Changing display names does not rename those persisted keys.

## Arena template

`worlds/sternenfront` is restored before each server start. Positions and bot routes come from
`blockcraft/Sternenfront/arena.yml` inside that template. A map edit needs both the saved blocks and updated metadata.

- A post's listed colour blocks must remain recolourable. The round replaces their material for the current owner.
- Door panels start as air. The round opens any panels left closed in a saved template.
- Landing grounds lie at x -22 and x 22 beside each post. Each needs a flat 7-by-7 surface, open sky, and 6.5 blocks
  of clearance around its centre for the assault craft.
- A vehicle two blocks wide that steps one block must reach every landing ground from the headquarters pads.
- Replays include the play area. The far mesas outside it are scenery and are absent from a replay.

## Client constraints

Body rigs are cosmetic. Combat uses the underlying fighter's hitbox, including the larger-looking B2. Fighters see
other deployed bodies as rigs; the owner keeps their normal first-person and F5 body. Spectators see the original
entities so the kill camera remains usable. Hiding a player's body also removes them from that viewer's tab list.

Scope zoom uses movement slowdown. Clients with FOV effects disabled see the slowdown without a narrower view.
Music and cantina audio use the Music category. Combat effects use the Players category.

Launched rockets and orbital strikes continue after their owner falls. Lightning channels end with the caster.
Both powers use the existing damage path for spawn protection and kill credit. Rockets also use `blastAround` for
cover, teammate safety, and self-damage. Lightning uses the saber guard without reflecting the attack.

## Generated assets

Edit `resourcepack/SternenfrontArt.java` for bodies, hero bodies, helmets, blasters, sabers, vehicles, and effect
models. It writes the model JSON, item definitions, texture atlases, and class portraits. Editing its generated JSON
or PNG files directly loses the change on regeneration. Body dimensions and pivots must agree with `RigBody`.

`PackArt.java` turns the portraits into menu cards. `BattleArt.py` writes faction emblems, shield grids, and firing
poses. The Gradle dependencies run the body and equipment generator before both consumers:

```sh
./gradlew :resourcepack:generateTextures :resourcepack:generateBattle
```

Held and worn equipment uses `PackModel` and the vanilla item's custom-model-data override. Adding one requires
`:paper:api:packOverrides -PclientJar=/path/to/minecraft-client.jar`. Body parts, vehicles, and the wrist rocket
instead use direct resource keys such as `sternenfront/body/hero_jedi/torso`; keep those keys aligned with their
call sites. The large block-built ships belong to the saved world, while assault craft are display models.

Sound provenance and regeneration instructions live in [SABER_SOURCES.md](../../resourcepack/SABER_SOURCES.md) and
[BATTLE_AUDIO.md](../../resourcepack/BATTLE_AUDIO.md). The pack includes the [credits](../../CREDITS.md).
