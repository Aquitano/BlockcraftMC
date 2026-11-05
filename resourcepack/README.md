# Resource pack

How the proxy serves the pack is in the [root README](../README.md#resource-pack). This file is for changing what it draws.

The [presentation test guide](../paper/api/PRESENTATION.md) covers dialogue portraits, animated materials and text,
screen effects, and the Sternenfront intro.

## Constraints from the client

- **Plain fallback.** Shared code draws a vanilla version for a client without the pack, because a client can be in the
  world before it loads. `UiFont.overlaid` has no fallback, and Sternenfront's class and deploy menus use it.
- **Slot highlight.** The pack blanks `container/slot_highlight_back` and `_front`, because the highlight would mark one
  square of a card or toolbar button. The client has no per-screen switch, so this holds in every inventory.
- **Items other players can see.** A held or worn item or a display keeps its vanilla item and asks for the pack's model
  through custom_model_data. The pack overrides the vanilla item in `assets/minecraft/items/<item>.json` with a `select`
  on that string, whose fallback is the item's own vanilla definition. So a client without the pack draws the vanilla
  item. `item_model` set for one viewer is only for menus, which nobody else sees.
- **Title lettering.** `<display>` is only for titles, because `Notifications` turns it into bold text for a client
  without the pack. At title scale about thirteen letters fit a 1366x768 screen.
- **Sounds.** World sounds must be mono, because the client places only mono sounds in the world. Each script replaces
  only its own entries in `sounds.json`.
- **Portraits.** The Sternenfront role cards come from `resourcepack/art/sternenfront`, rendered once from the class
  models. `PackArt` only copies them in, so a changed class model needs a new render.

## Making a model

The hand-made 3D models live in `assets/blockcraft/models/item/<set>/`, each named by its `PackModel`. They are cuboids
textured with vanilla block textures, which keeps the pack small and the look vanilla. Made in Blockbench, a model is
exported with "Java Block/Item".

- Elements must stay between -16 and 32 on every axis, so a larger set piece is scaled up by its display.
- Textures stay at 16 to 64 pixels, because every change makes every client download the whole pack again.
- A model that needs its own texture puts it under `textures/item/<set>/`, or under `textures/block/<set>/` when it also
  draws vanilla block textures, because a model draws from one atlas.
- A held model points along +z and borrows the Zombies guns' display transforms. Their `fixed` transform turns them
  side-on into the plane where a vanilla item sprite lies on an item display, so a display shows the same profile with or
  without the pack.
- A hat stands on the head's 0..16 cube and is worn on an item that is neither armour nor a skull, because the client
  draws a worn skull as a skull whatever its model.
- A tinted `PackModel` takes the colour of its `tintindex: 0` faces from the item's first custom_model_data colour, so
  one model draws every blade colour.
- Item icons use a native 16-pixel grid, transparent margins and upper-left lighting. HUD symbols use seven pixels of ink
  in each eight-pixel tile to align with the text baseline.

## Presentation assets

`DialogueArt.java` generates the original portraits, panel, fonts, and matching Java glyph metrics.
`PresentationArt.java` generates three effect models and the named post-processing chains. Run
`:resourcepack:generateDialogue` and `:resourcepack:generatePresentation` to regenerate only those assets.

The item and text shader overrides preserve Minecraft 26.3's ordinary rendering branches. Exact color markers opt
into effects. Item markers use RGB `(233, mode, 197)`, where modes 1, 2, and 3 select hologram, plasma, and pulse.
Each affected model face needs `tintindex: 0`, and each element needs `shade: false` to preserve the marker bytes.
`VisualEffects.model` supplies that color through the first custom-model-data color. These models are viewer-only.
Their textures live under `textures/item/presentation`, which Minecraft 26.3 includes in its item atlas.

Text markers use RGB `(231, mode, 197)`. `TextMotion` selects float, shake, brightness pulse, rainbow, or shine.
Marked text shadows are suppressed. Ordinary text must avoid these reserved colors and their darkened shadow colors.

Sternenfront target outlines reserve scoreboard colors `DARK_AQUA` and `DARK_RED`. Its server sends those colors only
to combat viewers with the pack applied. The outline shader stores logarithmic reversed-Z depth in the RGB bytes and
uses alpha bytes 1 and 2 to identify the factions. The post-effect compares each silhouette pixel with the main scene's
depth, including the neighboring pixel receiving the outline. Covered pixels stay clear, and other glow colors retain
Minecraft's original outline and blur. Do not use the reserved colors for ordinary glowing entities.
The native outline buffer has no depth attachment. Exactly overlapping outlined front surfaces can suppress an
edge when a later surface overwrites its depth. The comparison stays conservative rather than exposing covered pixels.

`ScreenEffects` uses Paper 26.3's named post-effects API. Cinematic fades have 33 levels, with optional letterbox bars.
Atmospheric presets change screen color and distortion. They do not add world lighting, weather simulation, or video
playback. Cleanup removes only Blockcraft's presentation post-effects and preserves other namespaces.
Gameplay accents use eight intensity levels under `presentation/accent`. `VisualEffects.accent` controls their
duration, fade, and cooldown without suspending normal HUD updates. It clears them when a cinematic or dialogue starts.

`verify_shaders.py --client <26.3-client.jar> --glslang <validator>` compiles the core variants and post-effect pairs.
It needs Python 3 and glslangValidator. It does not download dependencies or change the pack. A new Minecraft version
needs validation against that version's vanilla shaders and a real client rendering check.

`verify_target_outlines.py --client <26.3-client.jar>` renders the actual outline shaders with ModernGL, EGL, and
OpenGL 4.5. It checks full and partial cover, thin cover at up to 256 blocks, edge clipping, a rotated cube, and preservation
of ordinary vanilla glow. The fixture uses Minecraft's reversed-Z range from zero to one. Software Mesa can run it on a
machine without a display. These fixtures do not replace a check with the player's client or shader mod.
