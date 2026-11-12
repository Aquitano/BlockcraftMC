# Asset work

Read the workflow at the start of [README.md](README.md) before changing assets.

## Choose the authoritative input

- Finished imports are edited in `src/assets`, `bettermodel`, and `../nexo/items`. Their inventories are in `catalogs`.
- Weapons in `finished-weapons.txt`, including their pose variants, are authoritative native models. Generation preserves them.
- For an authored family, edit the generator shown by `uv run --locked python assets.py list`. Change shared raster and geometry code in `NativeArt.py`, `KitPixels.py`, or `ModelSurfaces.py`.
- Put accepted map positions and seasonal settings in that map's tracked world bundle. Scratch worlds under `run` are temporary.

## Complete a change

1. Edit finished imports directly. For authored assets, generate only the affected family in its scratch workspace.
2. Inspect finished models with `assets.py preview`. For generated assets, also inspect `asset-report.json` and the workspace previews. Every changed accepted file must belong to the requested change.
3. Apply generated results with `assets.py apply`. It rejects changed inputs, changed output, and concurrent edits.
4. Run `assets.py check` and the root Gradle build. A new backend `PackModel` also needs the existing `:paper:api:packOverrides` task with the matching client JAR.
5. Use `dev/verify` for new geometry, materials, poses, or physical behavior. Software previews cannot prove Minecraft translucency, held-item framing, or movement collision. State what stayed unseen.

Preserve item identifiers, units, anchors and orientation unless the task changes them. Validate native bounds, UVs,
texture alpha, surface overlap and references. Substantial furniture needs a deliberate solid body; decorative
fixtures can remain passable. Emission pixels and world lighting are separate. Keep ordinary and Star Wars pack ownership intact.

Build and generation do not deploy. Stage matching plugin jars, packs, rigs and definitions together when a release is authorized.
