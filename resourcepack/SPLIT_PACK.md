# Resource pack layers

The builder packages existing generated assets. It does not run art generators or change the proxy runtime.
`blockcraft.zip` keeps the existing combined contents and credits for rollout compatibility.
The four additional archives use the following stacks, listed from lowest to highest client priority.

| Destination | Archives |
| --- | --- |
| Lobby | `blockcraft-base.zip`, `blockcraft-lobby.zip` |
| Other games | `blockcraft-base.zip`, `blockcraft-modes.zip` |
| Sternenfront | `blockcraft-base.zip`, `blockcraft-starwars.zip` |

Minecraft replaces item definition files as a whole. Each profile contains the base selector cases and only its own model cases.
The original vanilla fallback and item definition options survive in every profile.
The lobby archive resolves without the Star Wars archive.
Loading modes together with lobby or Star Wars is unsupported.
A transition must remove the previous profile packs. Keeping an old pack loaded can retain its shaders and item routes.

## Build and check

Run `./gradlew :resourcepack:build` with Java 25 to check and package this checkout.
The archives are in `resourcepack/build/split`; the combined archive is in `resourcepack/build`.
Root `stagePlugins` stages all five ZIPs into `build/docker/velocity/blockcraft-system`.
The split report stays in the build directory.

To check another generated source tree without modifying it:

```sh
python3 resourcepack/split_pack.py --source /absolute/path/resourcepack/src --check
python3 resourcepack/split_pack.py --source /absolute/path/resourcepack/src --output /tmp/blockcraft-split
```

Gradle also accepts `-PpackSource=/absolute/path/resourcepack/src` for both combined and split packaging.
The focused Python unit tests use this checkout's source. `verifySplitPack` uses the selected `packSource`.
Generation remains a separate explicit operation.

## Content boundaries

Base contains network icons, cosmetics, menus, shared presentation assets, UI sounds, the shared item and text effects,
and the text shader's corner HUD panels, so every stack can draw a `HudPanel`.
Menu atlases and mode preview tiles stay shared because network menus use them.
These images can include Sternenfront previews. They do not activate its HUD.

Lobby contains furniture under `props`, including imported market, Halloween, and quest sets.
The `props/starwars` subtree belongs to Star Wars.
Modes contains TTT, Zombies, and other gameplay weapons under `weapons`, and the fonts and font textures under a `zombies` directory.
Star Wars contains all `sternenfront`, `deploy`, and `battle` subtrees, imported Star Wars props, and BetterModel assets.
New `sternenfront/objectives` models, item routes, and textures follow this rule without enumeration.
All current BetterModel output is Sternenfront or imported Star Wars creatures. A future non-Star-Wars rig needs a new ownership rule.

The builder partitions mixed `sounds.json` events and shared bitmap font providers.
Star Wars font definitions preserve the base providers. Base providers exclude combat, minimap, ability, and deployment textures.
Star Wars owns the boss-bar blanks, trigger-cell line shader, target outline pipeline and Sternenfront's panel fonts.
Shared inventory slot highlight blanks remain in base.

## Validation and rollout limits

Every source file must occur in at least one split archive.
Checks resolve item models, model parents and textures, bitmap and reference fonts, sound files, shader includes, post-effect shaders, and texture metadata against each effective stack.
They reject missing custom dependencies, forbidden profile files, changed vanilla item fallbacks, lost selector cases, and boss-bar blanks or rigs in base or modes.
`split-report.json` lists unresolved vanilla resources separately.
Model texture variables and renderer semantics still depend on Minecraft.

The builder classifies the current accepted layout. New namespaces fail; new asset categories inside existing namespaces require ownership review.
It does not discover references made only by server code or plugin-generated assets outside `resourcepack/src`.
All current BetterModel bone routes are already in the source pack. `verifyBetterModel` checks every renderable bone
before packaging. Ordinary staging copies those routes and the matching `.bbmodel` files without starting a game server.
For additional external exports, assemble them before splitting and select that tree with `--source` or `-PpackSource`.
Nexo and BetterModel server configuration remain staged through their existing tasks.
No art, imported source files, or rig files are regenerated.

The lobby and ordinary games receive none of the Star Wars boss-bar blanks.
The proxy implements these stacks behind `resource-pack.profiles.enabled` in its configuration. Each archive needs
its own reachable URL under `resource-pack.profiles.archives`. The combined `BLOCKCRAFT_RESOURCE_PACK_URL` override
does not replace those archive URLs. Enabling profiles requires the four core archives and every configured archive to be present.
The base keeps the shared readiness UUID. Addons have separate UUIDs; configuration waits for every selected pack,
and successful arrival removes obsolete addons. Profile delivery is required even if the legacy `required` flag is false.
A failed download is retried up to `resource-pack.download-retries` times, once by default. Retries retain the original
loading deadline. Refusal, exhausted retries, a failed reload, or timeout blocks entry. Automatic lobby recovery is not implemented.
Replay transfers carry the recording's original game to the proxy. The replay backend requests that game's profile
and waits for acknowledgement before playback, including when switching recordings on the same backend.
An unknown replay origin remains blocked.
The combined archive remains the default while profile delivery is disabled.

## Production rollout

`prod/resource-pack.yml` contains the production pack overrides. Merge its `resource-pack` section into the proxy's
operator configuration, preserving other overrides. All four HTTPS endpoints must serve the staged ZIPs before
players reconnect. The existing Caddy reverse proxy forwards every pack path to the proxy's HTTP port.

Stage a complete candidate with `./gradlew stagePlugins -PstageDirectory=/absolute/path/to/candidate -PnexoJar=/path/to/Nexo.jar`.
The default staging directory remains `build/docker`. Validate the candidate before replacing deployed staging.
Keep matching Paper and Velocity jars, rigs, furniture definitions and packs together. Restart running backends and
the proxy after replacement. Stopped on-demand modes pick up the same files on their next start. Recreate existing
on-demand containers when their plugin source lists or pinned runtime settings change. Starting a stopped container
does not refresh those settings.
