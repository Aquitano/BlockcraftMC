# Resource pack layers

The builder packages existing generated assets. It does not run art generators or change the proxy runtime.
The base archive carries the pack's credits. The two archives use the following stacks, listed from lowest to highest
client priority.

| Destination | Archives |
| --- | --- |
| Lobby and the other games | `blockcraft-base.zip` |
| Sternenfront and story games | `blockcraft-base.zip`, `blockcraft-starwars.zip` |

A client moving between the lobby and the other games keeps its pack, so only entering or leaving a Sternenfront or story
game reloads the client's resources.
Minecraft replaces item definition files as a whole. The Star Wars archive's item definitions contain the base selector
cases and its own. The original vanilla fallback and item definition options survive in both.
The base archive resolves without the Star Wars archive.
Leaving a Star Wars game must remove its archive. Keeping it loaded can retain its shaders and item routes.

## Build and check

Run `./gradlew :resourcepack:build` with Java 25 to check and package this checkout.
The archives are in `resourcepack/build/split`.
Root `stagePlugins` stages both ZIPs into `build/docker/velocity/blockcraft-system`.
The split report stays in the build directory.

To check another generated source tree without modifying it:

```sh
python3 resourcepack/split_pack.py --source /absolute/path/resourcepack/src --check
python3 resourcepack/split_pack.py --source /absolute/path/resourcepack/src --output /tmp/blockcraft-split
```

Gradle also accepts `-PpackSource=/absolute/path/resourcepack/src` for packaging.
The focused Python unit tests use this checkout's source. `verifySplitPack` uses the selected `packSource`.
Generation remains a separate explicit operation.

## Content boundaries

Base contains network icons, cosmetics, menus, shared presentation assets, UI sounds, the shared item and text effects,
and the text shader's corner HUD panels, so every stack can draw a `HudPanel`.
Menu atlases and mode preview tiles stay shared because network menus use them.
These images can include Sternenfront previews. They do not activate its HUD.
Base also contains the furniture under `props`, including imported market, Halloween, and quest sets, since the shared
waiting lobby places some of it, and TTT, Zombies and the other gameplay weapons.

The `props/starwars` subtree belongs to Star Wars.
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
They reject missing custom dependencies, forbidden profile files, changed vanilla item fallbacks, lost selector cases, and boss-bar blanks or rigs in base.
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
The proxy maps server groups to addons in `resource-pack.groups`, where a group not listed loads base alone, and offers every archive at
`resource-pack.base-url` plus its file name; `BLOCKCRAFT_RESOURCE_PACK_BASE_URL` overrides the base URL.
Startup needs base and every addon the groups name.
The base keeps the shared readiness UUID. Addons have separate UUIDs; configuration waits for every selected pack,
and successful arrival removes obsolete addons. A failed download is retried up to `resource-pack.download-retries` times, once by default. Retries retain the original
loading deadline. Refusal, exhausted retries, a failed reload, or timeout blocks entry. Automatic lobby recovery is not implemented.
Replay transfers carry the recording's original game to the proxy. The replay backend requests that game's profile
and waits for acknowledgement before playback, including when switching recordings on the same backend.
A replay whose origin is unknown loads base alone.

## Production rollout

The bundled `resource-pack` section is the production configuration; the proxy needs only
`RESOURCE_PACK_BASE_URL` in `.env`. Both archives must be reachable under it before players reconnect.
The existing Caddy reverse proxy forwards every pack path to the proxy's HTTP port.

Stage a complete candidate with `./gradlew stagePlugins -PstageDirectory=/absolute/path/to/candidate -PnexoJar=/path/to/Nexo.jar`.
The default staging directory remains `build/docker`. Validate the candidate before replacing deployed staging.
Keep matching Paper and Velocity jars, rigs, furniture definitions and packs together. Restart running backends and
the proxy after replacement. Stopped on-demand modes pick up the same files on their next start. Recreate existing
on-demand containers when their plugin source lists or pinned runtime settings change. Starting a stopped container
does not refresh those settings.
