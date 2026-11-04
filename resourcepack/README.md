# Resource pack

How the proxy serves the pack is in the [root README](../README.md#resource-pack). This file is for changing what it draws.

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
